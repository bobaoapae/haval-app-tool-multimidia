"""Synthetic build-gate tests; never execute OEM code or claim device validation."""
import importlib.util
from pathlib import Path
import sys
import struct
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

    def make_zip(self, path, content, methods=None, align=True):
        with zipfile.ZipFile(path, 'w') as archive:
            for name, value in content.items():
                info = zipfile.ZipInfo(name)
                info.compress_type = (methods or {}).get(name, zipfile.ZIP_STORED)
                if align and info.compress_type == zipfile.ZIP_STORED:
                    info.extra = b'\0' * (-(archive.fp.tell() + 30 + len(name.encode('utf-8'))) % 4)
                archive.writestr(info, value)

    def zip_pair(self, root, methods=None, resources=b'resources'):
        a, b = Path(root)/'source.apk', Path(root)/'output.apk'
        common = {'AndroidManifest.xml': b'manifest', 'resources.arsc': resources}
        original = dict(common, **{'classes.dex': b'old'})
        original.update({name: b'signature' for name in sorted(build.SIGNATURE_FILES)})
        self.make_zip(a, original, methods)
        self.make_zip(b, dict(common, **{'classes.dex': b'new'}), methods)
        return a, b

    def locations(self, path, name='resources.arsc'):
        raw = path.read_bytes()
        with zipfile.ZipFile(path) as archive:
            info = archive.getinfo(name)
            cursor = archive.start_dir
            for item in archive.infolist():
                if item.filename == name:
                    local = info.header_offset
                    name_size, extra_size = struct.unpack_from('<HH', raw, local + 26)
                    return local, cursor, local + 30 + name_size + extra_size, info
                sizes = struct.unpack_from('<HHH', raw, cursor + 28)
                cursor += 46 + sum(sizes)
        self.fail('missing fixture entry')

    def insert_bytes(self, path, offset, value):
        raw = bytearray(path.read_bytes())
        with zipfile.ZipFile(path) as archive:
            central = archive.start_dir
            items = archive.infolist()
        cursor = central
        for info in items:
            if info.header_offset >= offset:
                struct.pack_into('<I', raw, cursor + 42, info.header_offset + len(value))
            cursor += 46 + sum(struct.unpack_from('<HHH', raw, cursor + 28))
        struct.pack_into('<I', raw, cursor + 16, central + len(value))
        raw[offset:offset] = value
        path.write_bytes(raw)

    def descriptor(self, path, signature=True, zero_local=True, name='resources.arsc'):
        local, central, data, info = self.locations(path, name)
        raw = bytearray(path.read_bytes())
        struct.pack_into('<H', raw, local + 6, info.flag_bits | 8)
        struct.pack_into('<H', raw, central + 8, info.flag_bits | 8)
        if zero_local:
            struct.pack_into('<3I', raw, local + 14, 0, 0, 0)
        path.write_bytes(raw)
        value = (b'PK\x07\x08' if signature else b'') + struct.pack('<3I', info.CRC, info.compress_size, info.file_size)
        self.insert_bytes(path, data + info.compress_size, value)

    def mutate(self, path, relative, fmt, value, central=False):
        local, directory, _, _ = self.locations(path)
        raw = bytearray(path.read_bytes())
        struct.pack_into(fmt, raw, (directory if central else local) + relative, value)
        path.write_bytes(raw)

    def assert_zip_readable(self, path):
        with zipfile.ZipFile(path) as archive:
            self.assertIsNone(archive.testzip())

    def test_missing_descriptor_evades_zipfile_but_is_refused(self):
        for target in ('source', 'output'):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as tmp:
                a, b = self.zip_pair(tmp)
                path = a if target == 'source' else b
                self.mutate(path, 6, '<H', 8)
                self.mutate(path, 8, '<H', 8, central=True)
                self.assert_zip_readable(path)
                with self.assertRaisesRegex(ValueError, 'data descriptor'):
                    build.verify_zip(a, b)

    def test_valid_descriptor_forms_and_local_zero_or_matching_values(self):
        for method in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
            for signature in (False, True):
                for zero_local in (False, True):
                    with self.subTest(method=method, signature=signature, zero=zero_local), tempfile.TemporaryDirectory() as tmp:
                        a, b = self.zip_pair(tmp, {'resources.arsc': method})
                        for path in (a, b):
                            self.descriptor(path, signature, zero_local)
                            self.assert_zip_readable(path)
                        report = build.verify_zip(a, b)
                        self.assertEqual(1, report['structure']['source']['data_descriptors'])
                        self.assertEqual(1, report['structure']['output']['data_descriptors'])

    def test_descriptor_crc_equal_to_optional_signature(self):
        # This four-byte payload has a real CRC32 of 0x08074b50.
        for signature in (False, True):
            with self.subTest(signature=signature), tempfile.TemporaryDirectory() as tmp:
                a, b = self.zip_pair(tmp, resources=bytes.fromhex('ac0a7ad5'))
                self.assertEqual(0x08074b50, self.locations(b)[3].CRC)
                self.descriptor(b, signature)
                self.assert_zip_readable(b)
                build.verify_zip(a, b)

    def test_corrupt_descriptor_fields_are_refused(self):
        for signature in (False, True):
            for field in range(3):
                with self.subTest(signature=signature, field=field), tempfile.TemporaryDirectory() as tmp:
                    a, b = self.zip_pair(tmp)
                    self.descriptor(b, signature)
                    _, _, data, info = self.locations(b)
                    raw = bytearray(b.read_bytes())
                    offset = data + info.compress_size + (4 if signature else 0) + field * 4
                    struct.pack_into('<I', raw, offset, struct.unpack_from('<I', raw, offset)[0] ^ 1)
                    b.write_bytes(raw)
                    self.assert_zip_readable(b)
                    with self.assertRaisesRegex(ValueError, 'data descriptor'):
                        build.verify_zip(a, b)

    def test_truncated_descriptor_at_central_boundary_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp)
            self.descriptor(b, name='classes.dex')
            _, _, data, info = self.locations(b, 'classes.dex')
            raw = bytearray(b.read_bytes())
            cut = data + info.compress_size + 8
            del raw[cut:cut + 8]
            end = raw.rfind(b'PK\x05\x06')
            struct.pack_into('<I', raw, end + 16, struct.unpack_from('<I', raw, end + 16)[0] - 8)
            b.write_bytes(raw)
            self.assert_zip_readable(b)
            with self.assertRaisesRegex(ValueError, 'data descriptor'):
                build.verify_zip(a, b)

    def test_unflagged_descriptor_or_displaced_descriptor_is_refused(self):
        for flagged in (False, True):
            with self.subTest(flagged=flagged), tempfile.TemporaryDirectory() as tmp:
                a, b = self.zip_pair(tmp)
                if flagged:
                    self.descriptor(b)
                _, _, data, info = self.locations(b)
                self.insert_bytes(b, data + info.compress_size, b'junk')
                self.assert_zip_readable(b)
                with self.assertRaisesRegex(ValueError, 'local records|data descriptor'):
                    build.verify_zip(a, b)

    def test_local_header_fields_cannot_disagree_with_central(self):
        for offset, fmt, value, message in [(6, '<H', 8, 'flags'), (8, '<H', 8, 'method'),
                                           (14, '<I', 123, 'CRC'), (18, '<I', 123, 'size'),
                                           (22, '<I', 123, 'size'), (30, '<B', ord('X'), 'filename')]:
            for target in ('source', 'output'):
                with self.subTest(offset=offset, target=target), tempfile.TemporaryDirectory() as tmp:
                    a, b = self.zip_pair(tmp)
                    self.mutate(a if target == 'source' else b, offset, fmt, value)
                    with self.assertRaisesRegex(ValueError, message):
                        build.verify_zip(a, b)

    def test_bit3_conflicting_nonzero_local_crc_or_size_is_refused(self):
        for field in (14, 18, 22):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as tmp:
                a, b = self.zip_pair(tmp)
                self.descriptor(b)
                self.mutate(b, field, '<I', 123)
                self.assert_zip_readable(b)
                with self.assertRaisesRegex(ValueError, 'mismatch with bit 3'):
                    build.verify_zip(a, b)

    def test_source_compression_method_is_preserved_including_dex(self):
        for name in ('resources.arsc', 'classes.dex'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                a, b = self.zip_pair(tmp)
                with zipfile.ZipFile(b) as archive:
                    values = {n: archive.read(n) for n in archive.namelist()}
                self.make_zip(b, values, {name: zipfile.ZIP_DEFLATED})
                with self.assertRaisesRegex(ValueError, 'compression method changed'):
                    build.verify_zip(a, b)

    def test_same_deflated_method_is_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp, {'resources.arsc': zipfile.ZIP_DEFLATED})
            build.verify_zip(a, b)

    def test_unaligned_stored_payload_is_refused_even_when_bytes_match(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp, {'AndroidManifest.xml': zipfile.ZIP_DEFLATED})
            with zipfile.ZipFile(b) as archive:
                values = {n: archive.read(n) for n in archive.namelist()}
            self.make_zip(b, values, {'AndroidManifest.xml': zipfile.ZIP_DEFLATED}, align=False)
            self.assert_zip_readable(b)
            with self.assertRaisesRegex(ValueError, '4-byte aligned'):
                build.verify_zip(a, b)

    def test_alignment_exempts_only_empty_directory_records(self):
        for content, accepted in [(b'', True), (b'nonempty', False)]:
            with self.subTest(content=content), tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp)/'directory.zip'
                self.make_zip(path, {'xy/': content}, align=False)
                if accepted:
                    build.zip_structure(path.read_bytes(), 'fixture')
                else:
                    with self.assertRaisesRegex(ValueError, '4-byte aligned'):
                        build.zip_structure(path.read_bytes(), 'fixture')

    def test_zero_byte_regular_file_requires_alignment(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/'empty.zip'
            self.make_zip(path, {'abc': b''}, align=False)
            with self.assertRaisesRegex(ValueError, '4-byte aligned'):
                build.zip_structure(path.read_bytes(), 'fixture')

    def test_local_alignment_extra_may_differ_from_central(self):
        # zipalign may append 1-3 raw zero bytes, not a complete TLV field.
        for padding in (1, 2, 3):
            with self.subTest(padding=padding), tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp)/'padding.zip'
                name = 'x' * (6 - padding)
                self.make_zip(path, {name: b'data'})
                local, central, data, _ = self.locations(path, name)
                raw = bytearray(path.read_bytes())
                self.assertEqual(padding, struct.unpack_from('<H', raw, local + 28)[0])
                # Drop the central extra only; keep the local padding/alignment.
                del raw[central + 46 + len(name):central + 46 + len(name) + padding]
                struct.pack_into('<H', raw, central + 30, 0)
                end = raw.rfind(b'PK\x05\x06')
                struct.pack_into('<I', raw, end + 12, struct.unpack_from('<I', raw, end + 12)[0] - padding)
                path.write_bytes(raw)
                self.assert_zip_readable(path)
                build.zip_structure(path.read_bytes(), 'fixture')

    def test_zip64_and_unsupported_layouts_are_refused(self):
        for offset, fmt, value, central, message in [(18, '<I', 0xffffffff, False, 'ZIP64'),
                                                     (20, '<I', 0xffffffff, True, 'ZIP64'),
                                                     (42, '<I', 0xffffffff, True, 'ZIP64'),
                                                     (34, '<H', 1, True, 'unsupported'),
                                                     (8, '<H', 12, False, 'method'),
                                                     (10, '<H', 12, True, 'compression'),
                                                     (8, '<H', 1, True, 'flags')]:
            with self.subTest(offset=offset, central=central), tempfile.TemporaryDirectory() as tmp:
                a, b = self.zip_pair(tmp)
                self.mutate(b, offset, fmt, value, central)
                with self.assertRaisesRegex(ValueError, message):
                    build.verify_zip(a, b)

    def test_zip64_local_extra_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/'zip64.zip'
            with zipfile.ZipFile(path, 'w') as archive:
                with archive.open('data', 'w', force_zip64=True) as entry:
                    entry.write(b'data')
            with self.assertRaisesRegex(ValueError, 'ZIP64'):
                build.zip_structure(path.read_bytes(), 'fixture')

    def test_decoded_size_must_match_consistent_but_false_headers(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp, {'resources.arsc': zipfile.ZIP_DEFLATED})
            self.mutate(b, 22, '<I', 999)
            self.mutate(b, 24, '<I', 999, central=True)
            self.assert_zip_readable(b)
            with self.assertRaisesRegex(ValueError, 'uncompressed size differs'):
                build.verify_zip(a, b)

    def test_local_header_offsets_and_lengths_are_bounded(self):
        for offset, fmt, value, central in [(42, '<I', 0, True), (42, '<I', 999999, True),
                                           (26, '<H', 65535, False), (28, '<H', 65535, False),
                                           (18, '<I', 999999, False), (20, '<I', 999999, True)]:
            with self.subTest(offset=offset, central=central), tempfile.TemporaryDirectory() as tmp:
                a, b = self.zip_pair(tmp)
                self.mutate(b, offset, fmt, value, central)
                with self.assertRaises(ValueError):
                    build.verify_zip(a, b)

    def test_consistent_sizes_cannot_overlap_next_local_record(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp)
            self.mutate(b, 18, '<I', 999999)
            self.mutate(b, 20, '<I', 999999, central=True)
            self.mutate(b, 22, '<I', 999999)
            self.mutate(b, 24, '<I', 999999, central=True)
            with self.assertRaisesRegex(ValueError, 'payload exceeds'):
                build.verify_zip(a, b)

    def test_duplicate_entries_and_raw_nul_names_are_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp)
            self.mutate(b, 30, '<B', 0)
            self.mutate(b, 46, '<B', 0, central=True)
            with self.assertRaisesRegex(ValueError, 'NUL-containing'):
                build.verify_zip(a, b)
            self.make_zip(b, {'resources.arsc': b'resources'})
            with self.assertWarns(UserWarning), zipfile.ZipFile(b, 'a') as archive:
                archive.writestr('resources.arsc', b'duplicate')
            with self.assertRaisesRegex(ValueError, 'Duplicate ZIP entries'):
                build.verify_zip(a, b)

    def test_zip64_extra_without_sentinels_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/'extra.zip'
            with zipfile.ZipFile(path, 'w') as archive:
                info = zipfile.ZipInfo('data')
                info.extra = struct.pack('<HH', 1, 0)
                archive.writestr(info, b'data')
            with self.assertRaisesRegex(ValueError, 'ZIP64 is unsupported'):
                build.zip_structure(path.read_bytes(), 'fixture')

    def test_eocd_counts_extents_comments_and_trailing_bytes_are_bounded(self):
        for field, fmt, value in [(8, '<H', 1), (10, '<H', 2), (12, '<I', 999999),
                                  (16, '<I', 999999), (20, '<H', 1), (4, '<H', 1),
                                  (10, '<H', 0xffff)]:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as tmp:
                a, b = self.zip_pair(tmp)
                raw = bytearray(b.read_bytes())
                struct.pack_into(fmt, raw, raw.rfind(b'PK\x05\x06') + field, value)
                b.write_bytes(raw)
                with self.assertRaises(ValueError):
                    build.verify_zip(a, b)
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp)
            b.write_bytes(b.read_bytes() + b'trailing')
            with self.assertRaisesRegex(ValueError, 'EOCD'):
                build.verify_zip(a, b)

    def test_archive_comment_with_fake_eocd_is_explicitly_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp)
            with zipfile.ZipFile(b, 'a') as archive:
                archive.comment = b'comment PK\x05\x06' + b'\0' * 18
            with self.assertRaisesRegex(ValueError, 'EOCD signature inside'):
                build.verify_zip(a, b)

    def test_ordinary_archive_comment_is_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp)
            with zipfile.ZipFile(b, 'a') as archive:
                archive.comment = b'offline validation'
            build.verify_zip(a, b)

    def test_signed_source_block_and_padding_are_accepted_only_in_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = self.zip_pair(tmp)
            with zipfile.ZipFile(a) as archive:
                central = archive.start_dir
            pair = struct.pack('<QI', 8, 0x7109871a) + b'test'
            size = len(pair) + 24
            block = struct.pack('<Q', size) + pair + struct.pack('<Q', size) + b'APK Sig Block 42'
            self.insert_bytes(a, central, b'\0' * 3 + block)
            build.verify_zip(a, b)
            with self.assertRaisesRegex(ValueError, 'APK Signing Block'):
                build.zip_structure(a.read_bytes(), 'output')
            raw = bytearray(a.read_bytes())
            struct.pack_into('<Q', raw, central + 3, size + 1)
            a.write_bytes(raw)
            with self.assertRaisesRegex(ValueError, 'Signing Block extent'):
                build.verify_zip(a, b)

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
