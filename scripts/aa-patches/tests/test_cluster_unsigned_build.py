"""Synthetic build-gate tests; never execute OEM code or claim device validation."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

HERE = Path(__file__).resolve().parents[1] / 'integration'
sys.path.insert(0, str(HERE))
import build_unsigned as build


class UnsignedBuildGates(unittest.TestCase):
    def test_assembly_accepts_reviewed_java21_property(self):
        result = mock.Mock(stdout="", stderr="    java.specification.version = 21\n")
        with mock.patch.object(build.shutil, "which", return_value="java"), \
             mock.patch.object(build.subprocess, "run", return_value=result) as run:
            build.require_assembly_java()
        run.assert_called_once_with(["java", "-XshowSettings:properties", "-version"],
                                    capture_output=True, text=True, check=True, timeout=15)

    def test_assembly_refuses_other_or_unrecognized_java_profile(self):
        for text in ["java.specification.version = 17", "java.specification.version = 25",
                     "java.specification.version = unknown", "",
                     "java.specification.version = 21\njava.specification.version = 21"]:
            with self.subTest(text=text), mock.patch.object(build.shutil, "which", return_value="java"), \
                 mock.patch.object(build.subprocess, "run", return_value=mock.Mock(stdout="", stderr=text)):
                with self.assertRaisesRegex(ValueError, "requires Java 21"):
                    build.require_assembly_java()

    def test_assembly_refuses_missing_java(self):
        with mock.patch.object(build.shutil, "which", return_value=None):
            with self.assertRaisesRegex(ValueError, "requires the reviewed Java 21"):
                build.require_assembly_java()

    def test_assembly_runtime_gate_precedes_compilation_and_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "out"
            with mock.patch.object(build, "pinned", side_effect=lambda path, expected: path), \
                 mock.patch.object(build, "require_assembly_java", side_effect=ValueError("Java 21 required")), \
                 mock.patch.object(build, "compile_sources") as compile_sources:
                self.assertEqual(2, build.main(["--android-jar", "android.jar", "--apktool", "apktool.jar",
                                               "--r8", "r8.jar", "--source-apk", "source.apk", "--output", str(output)]))
            compile_sources.assert_not_called()
            self.assertFalse(output.exists())
            self.assertEqual([], list(Path(tmp).iterdir()))

    def test_compile_only_does_not_require_assembly_java_profile(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "out"
            with mock.patch.object(build, "pinned", side_effect=lambda path, expected: path), \
                 mock.patch.object(build, "require_assembly_java") as runtime_gate, \
                 mock.patch.object(build, "compile_sources") as compile_sources:
                self.assertEqual(0, build.main(["--compile-only", "--android-jar", "android.jar", "--output", str(output)]))
            runtime_gate.assert_not_called()
            compile_sources.assert_called_once()
            self.assertTrue((output / "report.json").is_file())

    def test_default_trust_is_fail_closed_without_dead_code_constant(self):
        source = build.trust_source([], False)
        self.assertIn('Boolean.parseBoolean("false")', source)
        self.assertIn('new String[]{}', source)
        self.assertNotIn('HANDOFF_ENABLED=false', source)

    def test_enabled_requires_explicit_public_pins(self):
        for pins, enabled in [([], True), (['a'*64], False), (['0'*64], True), (['A'*64], True), (['secret'], True)]:
            with self.subTest(pins=pins, enabled=enabled), self.assertRaises(ValueError):
                build.trust_source(pins, enabled)
        source = build.trust_source(['a'*64, 'a'*64], True)
        self.assertEqual(source.count('a'*64), 1)
        self.assertIn('Boolean.parseBoolean("true")', source)

    def test_tool_hash_mismatch_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/'tool.jar'
            path.write_bytes(b'not the pinned tool')
            with self.assertRaises(ValueError):
                build.pinned(path, '0'*64)

    def test_default_false_normalization_does_not_touch_code_or_other_fields(self):
        self.assertEqual(build.canonical_stock('.field private static final x:Z = false'), '.field private static final x:Z')
        for line in ['.field public x:Z = false', '.field static x:I = 0', '    const-string v0, "false"']:
            self.assertEqual(build.canonical_stock(line), line)

    def make_zip(self, path, content):
        with zipfile.ZipFile(path, 'w') as archive:
            for name, value in content.items():
                archive.writestr(name, value)

    def test_only_dex_change_and_signature_removal_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = Path(tmp)/'a.apk', Path(tmp)/'b.apk'
            common = {'AndroidManifest.xml': b'manifest', 'resources.arsc': b'resources'}
            original = dict(common, **{'classes.dex': b'old'})
            original.update({name:b'signature' for name in build.SIGNATURE_FILES})
            self.make_zip(a, original)
            valid = dict(common, **{'classes.dex':b'new'})
            self.make_zip(b, valid)
            report = build.verify_zip(a,b)
            self.assertTrue(report['manifest_and_resources_byte_identical'])
            for bad in [dict(valid, **{'resources.arsc':b'changed'}), dict(valid, **{'unexpected':b'added'}), dict(valid, **{'META-INF/CERT.RSA':b'old'})]:
                self.make_zip(b,bad)
                with self.assertRaises(ValueError):build.verify_zip(a,b)

if __name__ == '__main__':unittest.main()
