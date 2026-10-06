"""Synthetic build-gate tests; never execute OEM code or claim device validation."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

HERE = Path(__file__).resolve().parents[1] / 'integration'
sys.path.insert(0, str(HERE))
import build_unsigned as build


class UnsignedBuildGates(unittest.TestCase):
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
