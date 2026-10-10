"""Synthetic package gates; fixture assembly is mocked, never vehicle evidence."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

HERE = Path(__file__).resolve().parents[1] / "integration"
sys.path.insert(0, str(HERE))
import build_unsigned as build
import prepare_package as package

REAL_ROOT, REAL_HERE = build.ROOT, build.HERE
SOURCE_PATHS = tuple(build.source_manifest()["files_sha256"])


def make_apk(path, entries):
    with zipfile.ZipFile(path, "w") as archive:
        for name, content in entries.items():
            entry = zipfile.ZipInfo(name)
            entry.extra = b"\0" * (-(archive.fp.tell() + 30 + len(name.encode())) % 4)
            archive.writestr(entry, content)


class PackagePreparationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in SOURCE_PATHS:
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REAL_ROOT / name, target)
        self.source = self.root / "Service.apk"
        self.original = {"AndroidManifest.xml": b"manifest", "resources.arsc": b"resources", "classes.dex": b"old-dex"}
        make_apk(self.source, dict(self.original, **{name: b"signature" for name in build.SIGNATURE_FILES}))
        self.inputs = {"source_apk": self.source}
        for name in ("android", "apktool", "r8", "zipalign"):
            path = self.root / name
            path.write_bytes(("synthetic-" + name).encode())
            self.inputs[name] = path
        self.hashes = {name: build.digest(path) for name, path in self.inputs.items()}
        patches = [mock.patch.object(build, "ROOT", self.root),
                   mock.patch.object(build, "HERE", self.root / REAL_HERE.relative_to(REAL_ROOT)),
                   mock.patch.object(build, "TOOL_HASHES", {name: self.hashes[name] for name in ("android", "apktool", "r8")}),
                   mock.patch.object(build.hooks, "APK_SHA256", self.hashes["source_apk"]),
                   mock.patch.dict(build.ZIPALIGN_HASHES, {sys.platform: self.hashes["zipalign"]})]
        for patch in patches:
            patch.start()
            self.addCleanup(patch.stop)
        self.output = self.root / "scripts/.build/developer.zip"
        self.args = argparse.Namespace(output=self.output, android_jar=self.inputs["android"],
            source_apk=self.source, apktool=self.inputs["apktool"], r8=self.inputs["r8"],
            zipalign=self.inputs["zipalign"], enable_handoff=False, client_cert_sha256=[])
        self.mutate = lambda report, work: None
        self.build_calls = []
        patch = mock.patch.object(build, "main", side_effect=self.assemble_fixture)
        self.builder = patch.start()
        self.addCleanup(patch.stop)

    def assemble_fixture(self, argv):
        self.build_calls.append(argv)
        work = Path(argv[argv.index("--output") + 1])
        work.mkdir()
        self.work = work
        self.assertIn(".prepare-aa-", str(work))
        self.assertNotIn("--compile-only", argv)
        enabled = "--enable-handoff" in argv
        pins = sorted(set(argv[i + 1] for i, item in enumerate(argv) if item == "--client-cert-sha256"))
        trust = build.trust_source(pins, enabled)
        generated = work / "generated/com/ts/androidauto/impulse/cluster" / package.TRUST
        generated.parent.mkdir(parents=True)
        generated.write_bytes(trust.encode())
        make_apk(work / package.APK, dict(self.original, **{"classes.dex": b"new-dex"}))
        report = {"report_schema": build.REPORT_SCHEMA, "android_api": 28,
            "assembly_java_major": build.ASSEMBLY_JAVA_MAJOR,
            "java_compile": True, "host_java_compile": True, "host_kotlin_compile": False,
            "unsigned_assembly": True, "handoff_enabled": enabled,
            "client_public_certificate_sha256": pins, "signed": False,
            "vehicle_validated": False, "deployment_ready": False,
            "source_apk_sha256": self.hashes["source_apk"],
            "tool_sha256": dict(build.TOOL_HASHES, zipalign=self.hashes["zipalign"]),
            "provenance": {"sources": build.source_manifest(), "profile": build.profile_manifest(),
                           "generated_trust_sha256": hashlib.sha256(trust.encode()).hexdigest()},
            "assembly": {"ok": True, "final_references": {"ok": True, "deployment_ready": False},
                "final_hooks": {"ok": True, "hook_classes": 3, "hook_methods": 4,
                    "incoming_helper_calls": 4, "preserved_original_methods": 121,
                    "class_and_field_metadata_preserved": True, "deployment_ready": False},
                "preserved_oem_classes": 4897, "changed_oem_classes": sorted(build.CHANGED_STOCK),
                "zip": build.verify_zip(self.source, work / package.APK),
                "zip_alignment": {"alignment_bytes": 4, "sdk_build_tools_revision": build.ZIPALIGN_REVISION,
                                  "tool_sha256": self.hashes["zipalign"]}}}
        self.mutate(report, work)
        if not (work / package.REPORT).exists():
            package.write_json(work / package.REPORT, report)
        return 0

    def assert_clean_refusal(self, exception=ValueError):
        with self.assertRaises(exception):
            package.prepare(self.args)
        self.assertFalse(self.output.exists())
        if self.output.parent.is_dir():
            self.assertEqual([], list(self.output.parent.iterdir()))

    def test_disabled_fresh_package_has_service_hashes_and_blocked_gates(self):
        before = {path: path.read_bytes() for path in self.inputs.values()}
        sources = build.source_manifest()
        manifest = package.prepare(self.args)
        self.assertEqual(1, len(self.build_calls))
        self.assertFalse(self.work.exists())
        self.assertEqual([self.output], list(self.output.parent.iterdir()))
        self.assertEqual(sources, build.source_manifest())
        self.assertEqual(before, {path: path.read_bytes() for path in before})
        self.assertEqual([], manifest["client_public_certificate_sha256"])
        self.assertFalse(manifest["handoff_enabled"])
        self.assertEqual(package.GATES, manifest["blocked_vehicle_gates"])
        self.assertEqual(list(build.HOST_SERVICE_SIGNERS_SHA256),
                         manifest["provenance"]["profile"]["host_service_signers_sha256"])
        self.assertEqual(2, manifest["schema"])
        self.assertNotIn("host_service_signer_sha256", manifest["provenance"]["profile"])
        self.assertEqual(2, manifest["provenance"]["profile"]["protocol_version"])
        self.assertEqual("stock48ff-cluster-v2", manifest["provenance"]["profile"]["protocol_profile"])
        for key in ("deployment_ready", "signed", "vehicle_validated"):
            self.assertIs(False, manifest[key])
        package.verify_archive(self.output, manifest)
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(set(package.PAYLOAD) | {package.MANIFEST}, set(archive.namelist()))
            self.assertEqual(build.trust_source([], False).encode(), archive.read(package.TRUST))
            self.assertEqual(len(archive.read(package.APK)), manifest["unsigned_service"]["size_bytes"])
            self.assertEqual(manifest["files_sha256"][package.APK], manifest["unsigned_service"]["sha256"])

    def test_enabled_exact_pins_are_bound_without_readiness(self):
        self.args.enable_handoff = True
        self.args.client_cert_sha256 = ["b" * 64, "a" * 64, "b" * 64]
        manifest = package.prepare(self.args)
        self.assertTrue(manifest["handoff_enabled"])
        self.assertEqual(["a" * 64, "b" * 64], manifest["client_public_certificate_sha256"])
        self.assertFalse(manifest["deployment_ready"])
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(build.trust_source(self.args.client_cert_sha256, True).encode(), archive.read(package.TRUST))

    def test_invalid_pin_combinations_refused_before_build(self):
        for enabled, pins in ((True, []), (False, ["a" * 64]), (True, ["A" * 64]),
                              (True, ["0" * 64]), (True, ["../fake"])):
            with self.subTest(enabled=enabled, pins=pins):
                self.args.enable_handoff, self.args.client_cert_sha256 = enabled, pins
                self.assert_clean_refusal()
        self.builder.assert_not_called()

    def test_report_flags_compile_only_partial_and_false_readiness_refused(self):
        mutations = {"unsigned_assembly": False, "java_compile": False, "host_java_compile": False,
                     "signed": True, "vehicle_validated": True, "deployment_ready": True,
                     "host_kotlin_compile": True, "report_schema": True, "android_api": "28",
                     "assembly_java_major": 17, "handoff_enabled": True,
                     "client_public_certificate_sha256": ["a" * 64], "assembly": None}
        for key, value in mutations.items():
            with self.subTest(key=key):
                self.mutate = lambda report, work, k=key, v=value: report.update({k: v})
                self.assert_clean_refusal()

    def test_legacy_schema_and_single_pin_receipts_are_refused(self):
        def singleton_key(report, work):
            profile = report["provenance"]["profile"]
            profile.pop("host_service_signers_sha256")
            profile["host_service_signer_sha256"] = build.HOST_SERVICE_SIGNERS_SHA256[0]

        for mutation in (lambda r, w: r.update(report_schema=1), singleton_key,
                         lambda r, w: r["provenance"]["profile"].update(
                             host_service_signers_sha256=[build.HOST_SERVICE_SIGNERS_SHA256[0]])):
            with self.subTest(mutation=mutation):
                self.mutate = mutation
                self.assert_clean_refusal()

    def test_missing_report_and_missing_apk_do_not_publish(self):
        for name in (package.APK, package.REPORT):
            with self.subTest(name=name):
                if name == package.APK:
                    self.mutate = lambda report, work: (work / package.APK).unlink()
                else:
                    self.mutate = lambda report, work: (work / package.REPORT).mkdir()
                self.assert_clean_refusal()

    def test_hash_profile_tool_and_generated_trust_mismatches_refused(self):
        mutations = [lambda r, w: r["provenance"]["sources"].update(inventory_sha256="f" * 64),
            lambda r, w: r["provenance"]["profile"].update(smali_tree_sha256="f" * 64),
            lambda r, w: r["provenance"]["profile"].update(host_service_signers_sha256=[build.HOST_SERVICE_SIGNERS_SHA256[0]]),
            lambda r, w: r["provenance"].update(generated_trust_sha256="f" * 64),
            lambda r, w: r.update(source_apk_sha256="f" * 64),
            lambda r, w: r["tool_sha256"].update(android="f" * 64),
            lambda r, w: (w / "generated/com/ts/androidauto/impulse/cluster" / package.TRUST).write_bytes(b"altered"),
            lambda r, w: r["assembly"]["zip"].update(unsigned_apk_sha256="f" * 64),
            lambda r, w: r["assembly"].update(zip_alignment="not an object"),
            lambda r, w: r["assembly"].update(final_references={}),
            lambda r, w: r["assembly"]["final_hooks"].update(incoming_helper_calls=3),
            lambda r, w: r["assembly"]["final_hooks"].update(deployment_ready=True)]
        for number, mutation in enumerate(mutations):
            with self.subTest(case=number):
                self.mutate = mutation
                self.assert_clean_refusal()

    def test_report_json_malformed_duplicate_and_nonfinite_refused(self):
        for raw in ('{', '[]', '{"ok":true,"ok":true}', '{"ok":NaN}', '\udcff'):
            with self.subTest(raw=repr(raw)):
                self.mutate = lambda r, w, value=raw: (w / package.REPORT).write_bytes(value.encode("utf-8", "surrogatepass"))
                self.assert_clean_refusal((ValueError, UnicodeError))

    def test_apk_tamper_even_with_matching_claimed_hash_is_refused(self):
        def mutate(report, work):
            make_apk(work / package.APK, dict(self.original, **{"classes.dex": b"new", "resources.arsc": b"altered"}))
            report["assembly"]["zip"]["unsigned_apk_sha256"] = build.digest(work / package.APK)
        self.mutate = mutate
        self.assert_clean_refusal()

    def test_signed_or_malformed_apk_refused(self):
        for content in (b"not a zip", self.source.read_bytes()):
            with self.subTest(content=content[:10]):
                self.mutate = lambda r, w, value=content: (w / package.APK).write_bytes(value)
                self.assert_clean_refusal()

    def test_assembly_failure_and_interruption_clean_staging(self):
        for outcome in (2, KeyboardInterrupt()):
            with self.subTest(outcome=repr(outcome)):
                def fail(argv):
                    work = Path(argv[argv.index("--output") + 1])
                    work.mkdir()
                    (work / package.APK).write_bytes(b"partial")
                    if isinstance(outcome, BaseException):
                        raise outcome
                    return outcome
                self.builder.side_effect = fail
                self.assert_clean_refusal(KeyboardInterrupt if isinstance(outcome, BaseException) else ValueError)

    def test_copy_failure_corruption_and_interruption_clean_staging(self):
        for error in (OSError("disk full"), KeyboardInterrupt()):
            with self.subTest(error=repr(error)), mock.patch.object(package.shutil, "copyfileobj", side_effect=error):
                self.assert_clean_refusal(type(error))
        def corrupt(src, dst):
            dst.write(b"not the payload")
        with mock.patch.object(package.shutil, "copyfileobj", side_effect=corrupt):
            self.assert_clean_refusal()

    def test_staging_mutation_after_validation_before_copy_is_refused(self):
        copy_verified = package.copy_verified
        for changed in package.PAYLOAD:
            def mutate_then_copy(source, destination):
                if destination.name == changed:
                    if changed == package.REPORT:
                        report = package.read_json(source)
                        report["extra_unvalidated_claim"] = "changed after validation"
                        source.write_text(json.dumps(report), encoding="utf-8")
                    else:
                        source.write_bytes(b"changed after validation")
                copy_verified(source, destination)
            with self.subTest(payload=changed), mock.patch.object(package, "copy_verified", side_effect=mutate_then_copy):
                self.assert_clean_refusal()

    def test_interruption_after_publication_leaves_only_complete_verified_zip(self):
        link = package.os.link
        def publish_then_interrupt(source, destination):
            link(source, destination)
            raise KeyboardInterrupt()
        with mock.patch.object(package.os, "link", side_effect=publish_then_interrupt), self.assertRaises(KeyboardInterrupt):
            package.prepare(self.args)
        self.assertEqual([self.output], list(self.output.parent.iterdir()))
        with zipfile.ZipFile(self.output) as archive:
            manifest = json.loads(archive.read(package.MANIFEST))
        package.verify_archive(self.output, manifest)

    def test_copied_report_cannot_substitute_numbers_for_booleans(self):
        copy_verified = package.copy_verified
        for key, value in (("signed", 0), ("deployment_ready", 0), ("vehicle_validated", 0),
                           ("handoff_enabled", 0), ("java_compile", 1), ("report_schema", 1.0)):
            def mutate_then_copy(source, destination):
                if destination.name == package.REPORT:
                    report = package.read_json(source)
                    report[key] = value
                    source.write_text(json.dumps(report), encoding="utf-8")
                copy_verified(source, destination)
            with self.subTest(key=key, value=value), mock.patch.object(package, "copy_verified", side_effect=mutate_then_copy):
                self.assert_clean_refusal()

    def test_input_tool_mutation_during_build_refused(self):
        self.mutate = lambda report, work: self.inputs["android"].write_bytes(b"changed")
        self.assert_clean_refusal()

    def test_source_mutation_during_build_refused(self):
        path = self.root / SOURCE_PATHS[-1]
        self.mutate = lambda report, work: path.write_bytes(path.read_bytes() + b"\n")
        self.assert_clean_refusal()

    def test_input_hash_failure_precedes_output_creation(self):
        self.inputs["r8"].write_bytes(b"different")
        self.assert_clean_refusal()
        self.builder.assert_not_called()
        self.assertFalse(self.output.parent.exists())

    def test_existing_outputs_never_overwritten(self):
        self.output.parent.mkdir()
        for directory in (False, True):
            with self.subTest(directory=directory):
                if directory:
                    self.output.mkdir()
                else:
                    self.output.write_bytes(b"existing")
                with self.assertRaises(ValueError):
                    package.prepare(self.args)
                if directory:
                    self.assertEqual([], list(self.output.iterdir()))
                    self.output.rmdir()
                else:
                    self.assertEqual(b"existing", self.output.read_bytes())
                    self.output.unlink()
        self.builder.assert_not_called()

    def test_outside_build_traversal_and_invalid_names_refused(self):
        for target in (self.root / "outside.zip", self.root / "scripts/.build/../outside.zip",
                       self.root / "scripts/.build/nested/file.zip", self.root / "scripts/.build/no-apk.apk",
                       self.root / "scripts/.build/.hidden.zip"):
            with self.subTest(target=str(target)):
                self.args.output = target
                with self.assertRaises(ValueError):
                    package.prepare(self.args)
                self.assertFalse(target.exists())
        self.builder.assert_not_called()

    def test_simulated_symlink_input_output_and_ancestor_refused(self):
        original = Path.is_symlink
        for target in (self.inputs["android"], self.output, self.root / "scripts"):
            with self.subTest(target=target), mock.patch.object(Path, "is_symlink", lambda p: p == target or original(p)):
                with self.assertRaises(ValueError):
                    package.prepare(self.args)
        self.builder.assert_not_called()

    def test_real_symlink_refused_when_supported(self):
        link = self.root / "linked-android"
        try:
            link.symlink_to(self.inputs["android"])
        except OSError as error:
            if getattr(error, "winerror", None) == 1314:
                self.skipTest("Windows account lacks symlink privilege; simulated branches are covered")
            raise
        self.args.android_jar = link
        self.assert_clean_refusal()
        self.builder.assert_not_called()

    def test_generated_output_symlink_refused(self):
        original = Path.is_symlink
        with mock.patch.object(Path, "is_symlink", lambda p: p.name == package.APK or original(p)):
            self.assert_clean_refusal()

    def test_new_destination_race_is_atomic_no_replace(self):
        link = package.os.link
        def racing(source, destination):
            destination.write_bytes(b"someone else won")
            return link(source, destination)
        with mock.patch.object(package.os, "link", side_effect=racing), self.assertRaises(FileExistsError):
            package.prepare(self.args)
        self.assertEqual(b"someone else won", self.output.read_bytes())
        self.assertEqual([self.output], list(self.output.parent.iterdir()))

    def test_unsupported_atomic_promotion_has_no_rename_fallback(self):
        with mock.patch.object(package.os, "link", side_effect=OSError("unsupported")):
            self.assert_clean_refusal(OSError)

    def test_changed_v9_service_allowlist_refused_before_build(self):
        java = self.root / "app/src/main/java/br/com/redesurftank/havalshisuku"
        path = java / "api/AaClusterProtocol.java"
        original = path.read_bytes()
        first, second = [pin.encode() for pin in build.HOST_SERVICE_SIGNERS_SHA256]
        altered = [original.replace(first, b"f" * 64),
                   original.replace(second, b"f" * 64),
                   original.replace(b'"' + second + b'",', b''),
                   original.replace(b'"' + second + b'",', b'"' + second + b'", "' + b'f' * 64 + b'",'),
                   original.replace(b'String[] OEM_SIGNER_SHA256', b'String OEM_SIGNER_SHA256')]
        for content in altered:
            with self.subTest(content=content):
                path.write_bytes(content)
                self.assert_clean_refusal()
        path.write_bytes(original)
        self.builder.assert_not_called()

    def test_changed_service_predicate_refused_before_build(self):
        client = self.root / "app/src/main/java/br/com/redesurftank/havalshisuku/managers/AndroidAutoClusterClient.java"
        original = client.read_bytes()
        mutations = [(b').contains(hex.toString())', b').contains("anything")'),
                     (b'if(!java.util.Arrays', b'if(java.util.Arrays'),
                     (b'signers.length!=1', b'signers.length<1')]
        for before, after in mutations:
            with self.subTest(after=after):
                self.assertIn(before, original)
                client.write_bytes(original.replace(before, after))
                self.assert_clean_refusal()
        self.builder.assert_not_called()

    def test_changed_protocol_version_or_profile_refused_before_build(self):
        path = self.root / "app/src/main/java/br/com/redesurftank/havalshisuku/api/AaClusterProtocol.java"
        original = path.read_bytes()
        for before, after in ((b"VERSION = 2", b"VERSION = 3"),
                              (b"stock48ff-cluster-v2", b"different-profile")):
            with self.subTest(after=after):
                path.write_bytes(original.replace(before, after))
                self.assert_clean_refusal()
        self.builder.assert_not_called()

    def test_source_inventory_hashes_bytes_including_added_java(self):
        before = build.source_manifest()
        added = build.HERE / "src/Added.java"
        added.write_bytes(b"class Added {}\r\n")
        after = build.source_manifest()
        self.assertNotEqual(before["inventory_sha256"], after["inventory_sha256"])
        self.assertEqual(hashlib.sha256(added.read_bytes()).hexdigest(), after["files_sha256"][added.relative_to(self.root).as_posix()])
        added.write_bytes(b"class Added {}\n")
        self.assertNotEqual(after, build.source_manifest())

    def test_missing_required_source_refused(self):
        path = self.root / SOURCE_PATHS[-1]
        path.unlink()
        with self.assertRaises(ValueError):
            build.source_manifest()

    def test_manifest_path_abuse_hash_failure_and_false_readiness_refused(self):
        manifest = package.prepare(self.args)
        for mutation in (lambda m: m["files_sha256"].update({"../escape": "a" * 64}),
                         lambda m: m["files_sha256"].update({package.APK: "invalid"}),
                         lambda m: m["files_sha256"].update({package.APK: "f" * 64}),
                         lambda m: m.update(deployment_ready=True),
                         lambda m: m.update(blocked_vehicle_gates=[]),
                         lambda m: m["unsigned_service"].update(size_bytes=True),
                         lambda m: m.update(schema=True),
                         lambda m: m.update(schema=1)):
            changed = copy.deepcopy(manifest)
            mutation(changed)
            with self.assertRaises(ValueError):
                package.verify_archive(self.output, changed)

    def test_no_report_import_compile_only_or_deployment_options(self):
        arguments = ["--android-jar", str(self.inputs["android"]), "--source-apk", str(self.source),
                     "--apktool", str(self.inputs["apktool"]), "--r8", str(self.inputs["r8"]),
                     "--zipalign", str(self.inputs["zipalign"]), "--output", str(self.output)]
        for extra in (["--build-report", "supplied.json"], ["--compile-only"], ["--install"], ["--sign"]):
            with self.subTest(extra=extra), self.assertRaises(SystemExit):
                package.main(arguments + extra)
        self.builder.assert_not_called()


class BuildProvenanceTest(unittest.TestCase):
    def test_generated_trust_bytes_are_utf8_lf(self):
        trust = build.trust_source([], False)
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(build, "run"):
            work = Path(tmp)
            build.compile_sources(work, Path("android.jar"), trust)
            generated = work / "generated/com/ts/androidauto/impulse/cluster/GeneratedTrust.java"
            self.assertEqual(trust.encode("utf-8"), generated.read_bytes())
            self.assertNotIn(b"\r", generated.read_bytes())

    def test_compile_only_receipt_is_source_bound_but_not_assembly(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "compile"
            with mock.patch.object(build, "pinned", side_effect=lambda path, expected: path), \
                 mock.patch.object(build, "compile_sources"):
                self.assertEqual(0, build.main(["--android-jar", "android.jar", "--compile-only", "--output", str(output)]))
            report = package.read_json(output / package.REPORT)
            self.assertFalse(report["unsigned_assembly"])
            self.assertFalse(report["deployment_ready"])
            self.assertNotIn("assembly", report)
            self.assertEqual(build.source_manifest(), report["provenance"]["sources"])
            self.assertEqual(build.profile_manifest(), report["provenance"]["profile"])
            self.assertEqual(hashlib.sha256(build.trust_source([], False).encode()).hexdigest(),
                             report["provenance"]["generated_trust_sha256"])

    def test_builder_refuses_source_changes_without_publishing(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "compile"
            with mock.patch.object(build, "pinned", side_effect=lambda path, expected: path), \
                 mock.patch.object(build, "compile_sources"), \
                 mock.patch.object(build, "source_manifest", side_effect=[{"hash": "before"}, {"hash": "after"}]):
                self.assertEqual(2, build.main(["--android-jar", "android.jar", "--compile-only", "--output", str(output)]))
            self.assertEqual([], list(Path(tmp).iterdir()))

    def test_builder_refuses_changed_tool_after_compilation(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "compile"
            with mock.patch.object(build, "pinned", side_effect=[Path("android.jar"), ValueError("tool changed")]), \
                 mock.patch.object(build, "compile_sources"):
                self.assertEqual(2, build.main(["--android-jar", "android.jar", "--compile-only", "--output", str(output)]))
            self.assertEqual([], list(Path(tmp).iterdir()))


if __name__ == "__main__":
    unittest.main()
