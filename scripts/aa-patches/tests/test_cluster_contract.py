"""Synthetic declaration tests. These fixtures do not contain OEM implementation."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "patch_android_auto_service_cluster.py"
spec = importlib.util.spec_from_file_location("cluster_preflight", SCRIPT)
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class ClusterContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.paths = {}
        for descriptor in preflight.REQUIRED_METHODS.keys() | preflight.REQUIRED_FIELDS.keys():
            path = self.root / "smali" / (descriptor[1:-1] + ".smali")
            path.parent.mkdir(parents=True, exist_ok=True)
            text = f"# Synthetic API declarations, not an executable class\n\n.class public {descriptor}\n.super Ljava/lang/Object;\n"
            for signature in preflight.REQUIRED_METHODS.get(descriptor, ()):
                text += f".method public {signature}\n.end method\n"
            for signature in preflight.REQUIRED_FIELDS.get(descriptor, ()):
                text += f".field public static final enum {signature}\n"
            path.write_text(text, encoding="utf-8")
            self.paths[descriptor] = path

    def snapshot(self):
        return {p.relative_to(self.root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in self.root.rglob("*") if p.is_file()}

    def edit(self, descriptor, old, new):
        path = self.paths[descriptor]
        path.write_text(path.read_text().replace(old, new), encoding="utf-8")

    def check(self):
        return preflight.inspect_contract(self.root)

    def test_known_subset_matches_without_set_surface_but_never_ready(self):
        before = self.snapshot()
        report = self.check()
        self.assertTrue(report["known_api_subset_matches"])
        self.assertFalse(report["cluster_implemented"])
        self.assertFalse(report["deployment_ready"])
        self.assertEqual(report["video_sink_surface_declarations"], [])
        self.assertEqual(before, self.snapshot())

    def test_default_patch_command_refuses_and_leaves_tree_unchanged(self):
        helper = self.root / "smali/com/ts/androidauto/impulse/ImpulseAaClusterAdvertise.smali"
        helper.parent.mkdir(parents=True)
        helper.write_text("existing helper must remain untouched")
        before = self.snapshot()
        result = subprocess.run([sys.executable, str(SCRIPT), str(self.root)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn("patching is disabled", result.stderr)
        self.assertNotIn("Patched", result.stdout)
        self.assertEqual(before, self.snapshot())

    def test_no_partial_helper_or_directory_is_created(self):
        before = sorted(p.relative_to(self.root) for p in self.root.rglob("*"))
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(preflight.main([str(self.root)]), 2)
        self.assertEqual(before, sorted(p.relative_to(self.root) for p in self.root.rglob("*")))

    def test_wrong_constructor_is_not_a_match(self):
        self.edit(preflight.VIDEO, "ProjectionListener;ZI)V", "ProjectionListener;)V")
        report = self.check()
        self.assertFalse(report["known_api_subset_matches"])
        self.assertIn("<init>", "\n".join(report["errors"]))

    def test_method_name_in_comment_string_or_call_is_not_a_declaration(self):
        signature = preflight.REQUIRED_METHODS[preflight.VIDEO][1]
        self.edit(preflight.VIDEO, f".method public {signature}",
                  f'# .method public {signature}\n    const-string v0, "{signature}"\n    invoke-virtual {{v0}}, {preflight.VIDEO}->{signature}')
        self.assertFalse(self.check()["known_api_subset_matches"])

    def test_same_name_wrong_parameter_type_is_rejected(self):
        self.edit(preflight.VIDEO, "setDisplayIdAndType(ILcom/google/android/projection/proto/Protos$DisplayType;)V",
                  "setDisplayIdAndType(II)V")
        self.assertFalse(self.check()["known_api_subset_matches"])

    def test_private_or_static_method_is_rejected(self):
        for modifier in ("private", "public static"):
            with self.subTest(modifier=modifier):
                path = self.paths[preflight.VIDEO]
                original = path.read_text()
                path.write_text(original.replace(".method public setVideoFocus", f".method {modifier} setVideoFocus"))
                self.assertFalse(self.check()["known_api_subset_matches"])
                path.write_text(original)

    def test_wrong_class_descriptor_and_missing_class_are_rejected(self):
        self.edit(preflight.VIDEO, f".class public {preflight.VIDEO}", ".class public Lother/VideoSink;")
        self.assertFalse(self.check()["known_api_subset_matches"])
        self.paths[preflight.VIDEO].unlink()
        self.assertFalse(self.check()["known_api_subset_matches"])

    def test_multidex_supported_and_ambiguous_class_rejected(self):
        original = self.paths[preflight.VIDEO]
        content = original.read_text()
        for root_name in ("smali_classes2", "smali_classes10", "smali_classes20"):
            path = self.root / root_name / original.relative_to(self.root / "smali")
            path.parent.mkdir(parents=True)
            original.rename(path)
            self.assertTrue(self.check()["known_api_subset_matches"])
            original.write_text(content)
            self.assertFalse(self.check()["known_api_subset_matches"])
            path.unlink()

    def test_duplicate_required_method_rejected(self):
        path = self.paths[preflight.VIDEO]
        path.write_text(path.read_text() + ".method public setVideoFocus(IIZ)V\n.end method\n")
        self.assertFalse(self.check()["known_api_subset_matches"])

    def test_unrelated_video_sink_filename_not_used(self):
        self.paths[preflight.VIDEO].unlink()
        path = self.root / "smali/unrelated/VideoSink.smali"
        path.parent.mkdir(parents=True)
        path.write_text(".class public Lunrelated/VideoSink;\n")
        self.assertFalse(self.check()["known_api_subset_matches"])

    def test_surface_string_does_not_report_video_sink_surface_api(self):
        path = self.paths[preflight.VIDEO]
        path.write_text(path.read_text() + '# .method public setSurface(Landroid/view/Surface;)V\n')
        self.assertEqual(self.check()["video_sink_surface_declarations"], [])

    def test_even_a_surface_declaration_never_enables_patching(self):
        path = self.paths[preflight.VIDEO]
        path.write_text(path.read_text() + '.method public setSurface(Landroid/view/Surface;)V\n.end method\n')
        report = self.check()
        self.assertEqual(len(report["video_sink_surface_declarations"]), 1)
        self.assertFalse(report["deployment_ready"])
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(preflight.main([str(self.root)]), 2)

    def test_cluster_enum_requires_public_static_field(self):
        descriptor = next(iter(preflight.REQUIRED_FIELDS))
        self.edit(descriptor, ".field public static", ".field private")
        self.assertFalse(self.check()["known_api_subset_matches"])

    def test_enum_name_in_unrelated_string_initializer_is_not_a_field(self):
        descriptor = next(iter(preflight.REQUIRED_FIELDS))
        signature = preflight.REQUIRED_FIELDS[descriptor][0]
        self.edit(descriptor, f".field public static final enum {signature}",
                  f'.field public static debugMessage:Ljava/lang/String; = "missing {signature} text"')
        self.assertFalse(self.check()["known_api_subset_matches"])

    def test_field_initializer_is_not_part_of_declared_signature(self):
        descriptor = next(iter(preflight.REQUIRED_FIELDS))
        signature = preflight.REQUIRED_FIELDS[descriptor][0]
        self.edit(descriptor, signature, signature + " = null")
        self.assertTrue(self.check()["known_api_subset_matches"])

    def test_json_report_and_exit_code(self):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            code = preflight.main([str(self.root), "--check-contract", "--json"])
        self.assertEqual(code, 0)
        self.assertFalse(json.loads(out.getvalue())["deployment_ready"])
        self.paths[preflight.VIDEO].unlink()
        with contextlib.redirect_stdout(io.StringIO()) as out:
            code = preflight.main([str(self.root), "--check-contract", "--json"])
        self.assertEqual(code, 1)
        self.assertTrue(json.loads(out.getvalue())["errors"])

    def test_missing_directory_and_invalid_utf8_fail_cleanly(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(preflight.main([str(self.root / "missing"), "--check-contract"]), 1)
        self.paths[preflight.VIDEO].write_bytes(b"\xff")
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(preflight.main([str(self.root), "--check-contract"]), 1)


if __name__ == "__main__":
    unittest.main()
