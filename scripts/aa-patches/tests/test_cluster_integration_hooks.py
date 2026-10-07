"""Synthetic hook transformations; no OEM implementation or binary in fixtures."""
import importlib.util
import hashlib
from pathlib import Path, PurePosixPath, PureWindowsPath
import sys
import tempfile
import unittest
from unittest import mock

SCRIPT = Path(__file__).resolve().parents[1] / "integration" / "patch_service_hooks.py"
spec = importlib.util.spec_from_file_location("cluster_integration_hooks", SCRIPT)
hooks = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = hooks
spec.loader.exec_module(hooks)


def method(signature, body="    return-void", flags="public", locals_=0):
    return f".method {flags} {signature}\n    .locals {locals_}\n{body}\n.end method\n"


def synthetic_files():
    """Minimal manufactured declarations and hook anchors, never a stock method."""
    register = []
    for number in hooks.LEGACY_IDS:
        register.extend([f"    const/16 v2, 0x{number:x}", f"    invoke-virtual {{v0, v2, v1}}, {hooks.GAL}->registerCarService(I{hooks.PROVIDER})Z"])
    register.append("    return-void")
    dispatch = "    const v1, 0x5f4e5446\n    packed-switch p1, :table\n    const/4 v0, 0x0\n    return v0\n    :table\n    .packed-switch 0x1\n"
    dispatch += "\n".join(f"        :case_{i}" for i in range(54)) + "\n    .end packed-switch"
    return {
        hooks.INTEGRATION: ".class public " + hooks.INTEGRATION + "\n.super Ljava/lang/Object;\n.field private mViewingDistance:I\n" + method("registerCarService()V", "\n".join(register), locals_=7) + method("destroy()V", f"    invoke-virtual {{v0}}, {hooks.GAL}->destroy()V\n    return-void", locals_=2),
        hooks.GAL: ".class public " + hooks.GAL + "\n.super Ljava/lang/Object;\n.field private final mRegisteredServices:Landroid/util/SparseArray;\n.field private mNativeGalReceiver:J\n.field private volatile mStopping:Z\n",
        hooks.BINDER: ".class " + hooks.BINDER + "\n.super " + hooks.STUB + "\n.field final synthetic this$0:" + hooks.SERVICE + "\n",
        hooks.STUB: ".class public abstract " + hooks.STUB + "\n.super Landroid/os/Binder;\n" + method(hooks.ON_TRANSACT, dispatch, locals_=5),
        hooks.VIDEO: ".class public " + hooks.VIDEO + "\n.super Ljava/lang/Object;\n" + method("create(IJ)Z", "    const/4 v0, 0x1\n    return v0", locals_=1) + method("destroy()V") + method("getNativeInstance()J", "    const-wide/16 v0, 0x0\n    return-wide v0", locals_=2),
        hooks.INPUT: ".class public " + hooks.INPUT + "\n.super Ljava/lang/Object;\n" + method("create(IJ)Z", "    const/4 v0, 0x1\n    return v0", locals_=1) + method("destroy()V") + method("getNativeInstance()J", "    const-wide/16 v0, 0x0\n    return-wide v0", locals_=2),
    }


class IntegrationHooksTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="cluster-hook-tests-")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.source = self.base / "input"
        self.source.mkdir()
        self.paths = {}
        for name, text in synthetic_files().items():
            path = self.source / "smali" / (name[1:-1] + ".smali")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8", newline="\n")
            self.paths[name] = path
        (self.source / "AndroidManifest.xml").write_bytes(b"synthetic unchanged manifest")
        self.profile = self.profile_for_source()

    def profile_for_source(self):
        classes, tree = hooks.inventory(self.source)
        return hooks.Profile(tree, {name: hooks.digest(text.encode()) for name, (_, text) in classes.items()})

    def replace(self, name, old, new):
        path = self.paths[name]
        self.assertIn(old, path.read_text())
        path.write_text(path.read_text(encoding="utf-8").replace(old, new), encoding="utf-8", newline="\n")

    def snapshot(self, root):
        return {p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob("*") if p.is_file()}

    def use_crlf(self):
        for path in self.paths.values():
            raw = path.read_bytes()
            self.assertNotIn(b"\r", raw)
            path.write_bytes(raw.replace(b"\n", b"\r\n"))

    def refuse(self, profile=None):
        before = self.snapshot(self.source)
        output = self.base / "out"
        with self.assertRaises(hooks.Refusal):
            hooks.patch_tree(self.source, output, _profile=profile or self.profile)
        self.assertFalse(output.exists())
        self.assertEqual(before, self.snapshot(self.source))
        self.assertEqual([], list(self.base.glob(".cluster-hooks-*")))

    def test_exact_synthetic_profile_hooks_only_three_files_and_preserves_input(self):
        before = self.snapshot(self.source)
        out = self.base / "out"
        report = hooks.patch_tree(self.source, out, _profile=self.profile)
        self.assertEqual(before, self.snapshot(self.source))
        after = self.snapshot(out)
        changed = {p for p in before if before[p] != after[p]}
        self.assertEqual(changed, set(report["changed_files"]))
        self.assertEqual(3, len(changed))
        self.assertFalse(report["deployment_ready"])
        self.assertFalse(report["helpers_included"])
        self.assertEqual([21, 22], report["service_ids"])
        self.assertEqual(before[self.paths[hooks.STUB].relative_to(self.source).as_posix()], after[self.paths[hooks.STUB].relative_to(self.source).as_posix()])

    def test_path_order_matches_posix_components_on_both_path_flavours(self):
        names = ["smali/a.smali", "smali/B.smali", "smali/B/Child.smali"]
        expected = ["smali/B/Child.smali", "smali/B.smali", "smali/a.smali"]
        for flavour in (PurePosixPath, PureWindowsPath):
            with self.subTest(flavour=flavour.__name__):
                root = flavour("decode")
                paths = [root / name for name in names]
                ordered = sorted(paths, key=lambda p: hooks.relative_path_key(p, root))
                self.assertEqual(expected, [p.relative_to(root).as_posix() for p in ordered])

    def test_inventory_hash_uses_case_sensitive_component_order_and_raw_bytes(self):
        root = self.base / "ordering"
        expected = hashlib.sha256()
        # Directory B sorts before B.smali under the original POSIX algorithm;
        # sorting full POSIX strings or case-folded Windows paths is different.
        names = ["smali/B/Child.smali", "smali/B.smali", "smali/a.smali"]
        paths = []
        for index, name in enumerate(names):
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            raw = f".class public Lsynthetic/Order{index};\r\n".encode("utf-8")
            path.write_bytes(raw)
            expected.update(name.encode("utf-8") + b"\0" + hashlib.sha256(raw).digest())
            paths.append(path)
        with mock.patch.object(Path, "rglob", return_value=iter(reversed(paths))):
            classes, tree = hooks.inventory(root)
        self.assertEqual(expected.hexdigest(), tree)
        self.assertEqual(names, [p.relative_to(root).as_posix() for p, _ in classes.values()])

    def test_crlf_exact_fixture_profile_parses_and_writes_only_hooks_as_lf(self):
        expected = hooks.plan_patch(self.source, _profile=self.profile)
        self.use_crlf()
        profile = self.profile_for_source()
        before = self.snapshot(self.source)
        out = self.base / "out"
        report = hooks.patch_tree(self.source, out, _profile=profile)
        self.assertEqual(before, self.snapshot(self.source))
        after = self.snapshot(out)
        for name, raw in before.items():
            if name in report["changed_files"]:
                self.assertEqual(expected[self.source / name].encode("utf-8"), after[name])
                self.assertNotIn(b"\r", after[name])
            else:
                self.assertEqual(raw, after[name])

    def test_crlf_conversion_cannot_pass_original_lf_tree_fingerprint(self):
        self.use_crlf()
        self.assertNotEqual(self.profile.tree_sha256, self.profile_for_source().tree_sha256)
        self.refuse()

    def test_crlf_class_fingerprints_are_not_normalized_even_with_matching_tree(self):
        self.use_crlf()
        profile = hooks.Profile(self.profile_for_source().tree_sha256, self.profile.class_hashes)
        with self.assertRaisesRegex(hooks.Refusal, "Class bytes differ"):
            hooks.plan_patch(self.source, _profile=profile)
        self.refuse(profile)

    def test_crlf_changed_instruction_is_not_accepted_by_original_profile(self):
        self.use_crlf()
        profile = self.profile_for_source()
        path = self.paths[hooks.INPUT]
        path.write_bytes(path.read_bytes().replace(b"const/4 v0, 0x1", b"const/4 v0, 0x0"))
        self.refuse(profile)

    def test_crlf_malformed_method_refused_even_with_new_fixture_fingerprint(self):
        self.use_crlf()
        path = self.paths[hooks.VIDEO]
        path.write_bytes(path.read_bytes() + b".method public broken()V\r\n")
        self.refuse(self.profile_for_source())

    def test_crlf_duplicate_class_declaration_refused(self):
        self.use_crlf()
        path = self.paths[hooks.VIDEO]
        path.write_bytes(path.read_bytes() + f".class public {hooks.VIDEO}\r\n".encode("utf-8"))
        self.refuse()

    def test_class_declaration_cannot_consume_a_newline_as_whitespace(self):
        path = self.paths[hooks.INPUT]
        path.write_bytes(path.read_bytes().replace(b".class public ", b".class\npublic "))
        self.refuse()

    def test_missing_target_class_refused(self):
        self.paths[hooks.INPUT].unlink(); self.refuse()

    def test_duplicate_class_in_multidex_refused(self):
        p = self.source / "smali_classes2" / "Duplicate.smali"; p.parent.mkdir()
        p.write_text(self.paths[hooks.INPUT].read_text()); self.refuse()

    def test_filename_is_not_class_identity(self):
        self.replace(hooks.INPUT, hooks.INPUT, "Lsynthetic/Other;"); self.refuse()

    def test_missing_declared_method_refused_even_with_new_fixture_fingerprint(self):
        self.replace(hooks.INTEGRATION, "registerCarService()V", "other()V")
        self.refuse(self.profile_for_source())

    def test_duplicate_declared_method_refused(self):
        p = self.paths[hooks.VIDEO]; p.write_text(p.read_text() + method("destroy()V")); self.refuse()

    def test_already_patched_tree_refused(self):
        out = self.base / "patched"
        hooks.patch_tree(self.source, out, _profile=self.profile)
        with self.assertRaises(hooks.Refusal): hooks.plan_patch(out, _profile=self.profile)

    def test_existing_pair_accessor_refused(self):
        p = self.paths[hooks.GAL]; p.write_text(p.read_text() + method(hooks.PAIR_METHOD, "    return p1")); self.refuse()

    def test_existing_concrete_ontransact_refused(self):
        p = self.paths[hooks.BINDER]; p.write_text(p.read_text() + method(hooks.ON_TRANSACT, "    return p1")); self.refuse()

    def test_future_transaction_55_collision_refused(self):
        self.replace(hooks.STUB, "    .end packed-switch", "        :future_55\n    .end packed-switch")
        self.refuse(self.profile_for_source())

    def test_reserved_id_collision_refused(self):
        self.replace(hooks.INTEGRATION, "const/16 v2, 0x14", "const/16 v2, 0x15")
        self.refuse(self.profile_for_source())

    def test_future_registration_control_flow_refused(self):
        self.replace(hooks.INTEGRATION, ".locals 7", ".locals 8")
        self.refuse(self.profile_for_source())

    def test_body_change_is_not_accepted_by_signature_alone(self):
        self.replace(hooks.INPUT, "const/4 v0, 0x1", "const/4 v0, 0x0"); self.refuse()

    def test_unrelated_smali_mutation_refused_by_complete_tree_fingerprint(self):
        (self.source / "smali" / "Extra.smali").write_text(".class public Lsynthetic/Extra;\n.super Ljava/lang/Object;\n")
        self.refuse()

    def test_field_contract_mismatch_refused(self):
        self.replace(hooks.GAL, "mNativeGalReceiver:J", "mNativeGalReceiver:I")
        self.refuse(self.profile_for_source())

    def test_registration_passes_configured_vehicle_viewing_distance(self):
        changed = hooks.plan_patch(self.source, _profile=self.profile)[self.paths[hooks.INTEGRATION]]
        register = hooks.methods(changed)["registerCarService()V"]
        self.assertIn("iget v1, p0, " + hooks.INTEGRATION + "->mViewingDistance:I", register)
        self.assertIn("invoke-static {v0, v1}, " + hooks.HELPER + "->register(" + hooks.GAL + "I)V", register)

    def test_missing_viewing_distance_field_refused(self):
        self.replace(hooks.INTEGRATION, "mViewingDistance:I", "mViewingDistance:J")
        self.refuse(self.profile_for_source())

    def test_teardown_hook_precedes_original_provider_destroy(self):
        changed = hooks.plan_patch(self.source, _profile=self.profile)[self.paths[hooks.INTEGRATION]]
        destroy = hooks.methods(changed)["destroy()V"]
        self.assertLess(destroy.index("->retire("), destroy.index(hooks.GAL + "->destroy()V"))
        self.assertEqual(1, destroy.count(hooks.GAL + "->destroy()V"))

    def test_pair_wrapper_uses_receiver_monitor_and_exact_private_fields(self):
        changed = hooks.plan_patch(self.source, _profile=self.profile)[self.paths[hooks.GAL]]
        body = hooks.methods(changed)[hooks.PAIR_METHOD]
        self.assertLess(body.index("monitor-enter p0"), body.index("mRegisteredServices:"))
        self.assertIn("mNativeGalReceiver:J", body)
        self.assertIn("mStopping:Z", body)
        self.assertIn(hooks.PAIR_HELPER, body)
        self.assertIn(".catchall", body)
        self.assertNotIn("nativeRegister", body)
        self.assertNotIn("reflect", body)

    def test_binder_extension_preserves_all_legacy_codes_and_context_path(self):
        changed = hooks.plan_patch(self.source, _profile=self.profile)[self.paths[hooks.BINDER]]
        body = hooks.methods(changed)[hooks.ON_TRANSACT]
        self.assertIn("const/16 v0, 0x37", body)
        self.assertIn("if-ne p1, v0, :impulse_legacy_transaction", body)
        self.assertIn("this$0:" + hooks.SERVICE, body)
        self.assertIn("invoke-super {p0, p1, p2, p3, p4}, " + hooks.STUB + "->" + hooks.ON_TRANSACT, body)
        self.assertNotIn("clearCallingIdentity", body)

    def test_existing_output_and_inplace_refused(self):
        with self.assertRaises(hooks.Refusal): hooks.patch_tree(self.source, self.source, _profile=self.profile)
        out = self.base / "out"; out.mkdir(); (out / "keep").write_text("keep")
        with self.assertRaises(hooks.Refusal): hooks.patch_tree(self.source, out, _profile=self.profile)
        self.assertEqual("keep", (out / "keep").read_text())

    def test_output_inside_source_refused(self):
        with self.assertRaises(hooks.Refusal): hooks.patch_tree(self.source, self.source / "out", _profile=self.profile)
        self.assertFalse((self.source / "out").exists())

    def test_copy_failure_leaves_no_partial_output(self):
        before = self.snapshot(self.source)
        with mock.patch.object(hooks.shutil, "copytree", side_effect=OSError("synthetic copy failure")):
            with self.assertRaises(OSError): hooks.patch_tree(self.source, self.base / "out", _profile=self.profile)
        self.assertEqual(before, self.snapshot(self.source))
        self.assertFalse((self.base / "out").exists())
        self.assertEqual([], list(self.base.glob(".cluster-hooks-*")))

    def test_staged_mutation_is_rechecked_before_publication(self):
        original_copy = hooks.shutil.copytree
        def mutate_copy(source, target, *args, **kwargs):
            result = original_copy(source, target, *args, **kwargs)
            if Path(source) == self.source:
                path = Path(target) / self.paths[hooks.VIDEO].relative_to(self.source)
                path.write_text(path.read_text() + "# synthetic concurrent alteration\n")
            return result
        before = self.snapshot(self.source)
        with mock.patch.object(hooks.shutil, "copytree", side_effect=mutate_copy):
            with self.assertRaises(hooks.Refusal):
                hooks.patch_tree(self.source, self.base / "out", _profile=self.profile)
        self.assertEqual(before, self.snapshot(self.source))
        self.assertFalse((self.base / "out").exists())
        self.assertEqual([], list(self.base.glob(".cluster-hooks-*")))

    def test_mid_write_failure_does_not_publish_partial_patch(self):
        original_write = Path.write_text
        writes = []
        def fail_second(path, data, **kwargs):
            writes.append(path)
            if len(writes) == 2:
                raise OSError("synthetic second-file write failure")
            return original_write(path, data, **kwargs)
        before = self.snapshot(self.source)
        with mock.patch.object(Path, "write_text", autospec=True, side_effect=fail_second):
            with self.assertRaises(OSError):
                hooks.patch_tree(self.source, self.base / "out", _profile=self.profile)
        self.assertEqual(before, self.snapshot(self.source))
        self.assertFalse((self.base / "out").exists())
        self.assertEqual([], list(self.base.glob(".cluster-hooks-*")))

    def test_symlink_input_refused(self):
        try:
            (self.source / "escape").symlink_to(self.base / "outside")
        except OSError as exc:
            if sys.platform == "win32" and getattr(exc, "winerror", None) == 1314:
                self.skipTest("Windows account lacks symlink privilege (WinError 1314)")
            raise
        with self.assertRaises(hooks.Refusal): hooks.plan_patch(self.source, _profile=self.profile)

    def test_symlink_refusal_branch_without_os_symlink_privilege(self):
        for target in (self.source, self.paths[hooks.INPUT]):
            with self.subTest(target=target), mock.patch.object(
                    Path, "is_symlink", autospec=True, side_effect=lambda p: p == target):
                self.refuse()

    def test_synthetic_profile_cannot_pass_production_profile(self):
        with self.assertRaises(hooks.Refusal): hooks.plan_patch(self.source)


if __name__ == "__main__":
    unittest.main()
