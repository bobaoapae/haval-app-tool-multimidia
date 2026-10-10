#!/usr/bin/env python3
"""Prepare a fresh, offline unsigned developer ZIP. Never a vehicle candidate."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import zipfile

import build_unsigned as build

APK = "AndroidAutoService-UNSIGNED.apk"
TRUST = "GeneratedTrust.java"
REPORT = "report.json"
MANIFEST = "manifest.json"
PACKAGE_SCHEMA = 2
PAYLOAD = (APK, TRUST, REPORT)
GATES = ["signing_and_signature_compatible_loading_not_authorized",
         "installed_client_identity_and_enabled_trust_not_validated",
         "android_native_lifecycle_and_host_runtime_not_validated",
         "parked_vehicle_validation_and_recovery_not_completed"]


def safe_path(path: Path, *, file: bool = False) -> Path:
    # Check lexical components before resolving: resolve alone hides symlinks.
    if ".." in path.parts:
        raise ValueError("Parent traversal is not accepted")
    path = path.absolute()
    if any(part.is_symlink() for part in (path, *path.parents)):
        raise ValueError("Symlink paths are not accepted")
    if file and not path.is_file():
        raise ValueError(f"Missing regular input file: {path.name}")
    return path


def output_path(path: Path) -> Path:
    path = safe_path(path)
    root = safe_path(build.ROOT / "scripts/.build")
    if path.parent != root or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*\.zip", path.name):
        raise ValueError("Output must be a new named ZIP directly in scripts/.build")
    if path.exists():
        raise ValueError("Output already exists; nothing is overwritten")
    return path


def read_json(path: Path) -> dict:
    safe_path(path, file=True)
    if path.stat().st_size > 1024 * 1024:
        raise ValueError("JSON report exceeds the package limit")
    def pairs(items):
        result = {}
        for name, value in items:
            if name in result:
                raise ValueError("Duplicate JSON field")
            result[name] = value
        return result
    def constant(value):
        raise ValueError("Non-finite JSON value")
    value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=pairs,
                       parse_constant=constant)
    if not isinstance(value, dict):
        raise ValueError("Expected a JSON object")
    return value


def write_json(path: Path, value: dict) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, sort_keys=True, indent=2, allow_nan=False)
        stream.write("\n")


def check_host_trust() -> None:
    """Verify the unchanged v9 allowlist/predicate; never infer another signer."""
    java = build.ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku"
    build.host_service_signers()
    client = (java / "managers/AndroidAutoClusterClient.java").read_text(encoding="utf-8")
    start, end = client.find("private int verifyService("), client.find("private void query(")
    check = re.sub(r"\s+", "", client[start:end]) if 0 <= start < end else ""
    expected = ('if(!java.util.Arrays.asList(AaClusterProtocol.OEM_SIGNER_SHA256).contains(hex.toString()))'
                'thrownewSecurityException("AAServicesignermismatch:"+hex);')
    if ("signers==null||signers.length!=1||info.applicationInfo==null" not in check
            or expected not in check or check.count("OEM_SIGNER_SHA256") != 1
            or "returninfo.applicationInfo.uid;" not in check
            or check.index("OEM_SIGNER_SHA256") > check.index("returninfo.applicationInfo.uid;")):
        raise ValueError("Host Service verification must keep the existing v9 allowlist and single APK signer")


def refuse_readiness(value) -> None:
    if isinstance(value, dict):
        for key, item in value.items():
            if key in ("deployment_ready", "signed", "vehicle_validated") and item is not False:
                raise ValueError("Unsigned developer evidence cannot claim deployment readiness")
            refuse_readiness(item)
    elif isinstance(value, list):
        for item in value:
            refuse_readiness(item)


def validate_build(work: Path, source: Path, sources: dict, trust: str,
                   enabled: bool, certificates: list[str]) -> dict:
    # Only called for the fresh build made below. There is deliberately no CLI
    # input for a prior directory, manifest or a user-asserted successful report.
    report = read_json(work / REPORT)
    refuse_readiness(report)
    for flag in ("java_compile", "host_java_compile", "unsigned_assembly"):
        if report.get(flag) is not True:
            raise ValueError("Successful full unsigned assembly is required")
    for flag in ("signed", "vehicle_validated", "deployment_ready", "host_kotlin_compile"):
        if report.get(flag) is not False:
            raise ValueError("Invalid unsigned build safety flags")
    if (type(report.get("report_schema")) is not int or report["report_schema"] != build.REPORT_SCHEMA
            or type(report.get("android_api")) is not int or report["android_api"] != 28
            or type(report.get("assembly_java_major")) is not int
            or report["assembly_java_major"] != build.ASSEMBLY_JAVA_MAJOR):
        raise ValueError("Unsupported build report schema/toolchain")
    if (report.get("handoff_enabled") is not enabled
            or report.get("client_public_certificate_sha256") != certificates):
        raise ValueError("Client pins/handoff differ from requested trust")
    expected_provenance = {"sources": sources, "profile": build.profile_manifest(),
                           "generated_trust_sha256": hashlib.sha256(trust.encode()).hexdigest()}
    if (report.get("provenance") != expected_provenance
            or report.get("source_apk_sha256") != build.hooks.APK_SHA256
            or build.digest(source) != build.hooks.APK_SHA256):
        raise ValueError("Build source/profile provenance mismatch")
    if report.get("tool_sha256") != dict(build.TOOL_HASHES, zipalign=build.ZIPALIGN_HASHES[sys.platform]):
        raise ValueError("Build tool fingerprints differ from reviewed pins")
    generated = safe_path(work / "generated/com/ts/androidauto/impulse/cluster" / TRUST, file=True)
    if generated.read_bytes() != trust.encode():
        raise ValueError("Generated client trust differs from the report/request")
    assembly = report.get("assembly")
    if not isinstance(assembly, dict) or assembly.get("ok") is not True:
        raise ValueError("Missing successful assembly evidence")
    for section in ("final_references", "final_hooks"):
        if not isinstance(assembly.get(section), dict) or assembly[section].get("ok") is not True:
            raise ValueError("Missing final DEX verification evidence")
    hooks = assembly["final_hooks"]
    counts = {"hook_classes": 3, "hook_methods": 4, "incoming_helper_calls": 4,
              "preserved_original_methods": 121}
    if (any(type(hooks.get(key)) is not int or hooks[key] != value for key, value in counts.items())
            or hooks.get("class_and_field_metadata_preserved") is not True
            or assembly.get("changed_oem_classes") != sorted(build.CHANGED_STOCK)
            or type(assembly.get("preserved_oem_classes")) is not int
            or assembly["preserved_oem_classes"] != 4897):
        raise ValueError("Final DEX profile evidence mismatch")
    apk = safe_path(work / APK, file=True)
    # Recheck actual raw ZIP records, unsignedness, exact retained content and
    # alignment. Hashes or success booleans in a JSON file do not replace this.
    if assembly.get("zip") != build.verify_zip(source, apk):
        raise ValueError("APK content/hash does not match build evidence")
    alignment = assembly.get("zip_alignment", {})
    if (not isinstance(alignment, dict) or alignment.get("alignment_bytes") != 4
            or alignment.get("sdk_build_tools_revision") != build.ZIPALIGN_REVISION
            or alignment.get("tool_sha256") != build.ZIPALIGN_HASHES[sys.platform]):
        raise ValueError("Missing reviewed alignment evidence")
    return report


def copy_verified(source: Path, target: Path) -> None:
    source = safe_path(source, file=True)
    before = build.digest(source)
    # Exclusive creation, fixed filenames, no copytree/hardlink to mutable input.
    with source.open("rb") as src, target.open("xb") as dst:
        shutil.copyfileobj(src, dst)
    if build.digest(source) != before or build.digest(target) != before:
        raise ValueError("Package copy failed integrity verification")


def verify_archive(path: Path, manifest: dict) -> None:
    refuse_readiness(manifest)
    files = manifest.get("files_sha256")
    if (type(manifest.get("schema")) is not int or manifest["schema"] != PACKAGE_SCHEMA
            or manifest.get("artifact_kind") != "unsigned-developer-package"
            or not isinstance(files, dict) or set(files) != set(PAYLOAD)
            or any(not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{64}", value)
                   for value in files.values())
            or manifest.get("blocked_vehicle_gates") != GATES
            or any(manifest.get(flag) is not False for flag in ("deployment_ready", "signed", "vehicle_validated"))):
        raise ValueError("Invalid package manifest")
    with zipfile.ZipFile(path) as archive:
        expected = set(PAYLOAD) | {MANIFEST}
        if set(archive.namelist()) != expected or len(archive.infolist()) != len(expected):
            raise ValueError("Package ZIP inventory differs from fixed manifest")
        if archive.read(MANIFEST) != (json.dumps(manifest, sort_keys=True, indent=2) + "\n").encode():
            raise ValueError("Package manifest bytes changed")
        for name, expected_hash in files.items():
            if hashlib.sha256(archive.read(name)).hexdigest() != expected_hash:
                raise ValueError("Package ZIP content differs from manifest hash")
        artifact = manifest.get("unsigned_service")
        if (not isinstance(artifact, dict) or set(artifact) != {"filename", "sha256", "size_bytes"}
                or artifact.get("filename") != APK or artifact.get("sha256") != files[APK]
                or type(artifact.get("size_bytes")) is not int
                or artifact["size_bytes"] != archive.getinfo(APK).file_size):
            raise ValueError("Unsigned Service size/hash receipt mismatch")
        if archive.testzip() is not None:
            raise ValueError("Package ZIP CRC mismatch")


def prepare(args) -> dict:
    output = output_path(args.output)
    trust = build.trust_source(args.client_cert_sha256, args.enable_handoff)
    certificates = sorted(set(args.client_cert_sha256))
    if sys.platform not in build.ZIPALIGN_HASHES:
        raise ValueError("No reviewed zipalign for this platform")
    expected = dict(build.TOOL_HASHES, source_apk=build.hooks.APK_SHA256,
                    zipalign=build.ZIPALIGN_HASHES[sys.platform])
    inputs = {name: safe_path(getattr(args, name if name != "android" else "android_jar"), file=True)
              for name in expected}
    for name, path in inputs.items():
        build.pinned(path, expected[name])
    check_host_trust()
    build.profile_manifest()
    sources = build.source_manifest()
    preparer_sha256 = build.digest(Path(__file__))
    output.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".prepare-aa-", dir=output.parent) as temporary:
        stage = Path(temporary)
        work = stage / "assembly"
        command = ["--output", str(work)]
        for name, path in inputs.items():
            command += ["--android-jar" if name == "android" else "--" + name.replace("_", "-"), str(path)]
        if args.enable_handoff:
            command += ["--enable-handoff"]
        for certificate in certificates:
            command += ["--client-cert-sha256", certificate]
        if build.main(command) != 0:
            raise ValueError("Fresh unsigned assembly failed; no package was prepared")
        report = validate_build(work, inputs["source_apk"], sources, trust, args.enable_handoff, certificates)
        package = stage / "package"
        package.mkdir()
        for name, source in ((APK, work / APK), (REPORT, work / REPORT),
                             (TRUST, work / "generated/com/ts/androidauto/impulse/cluster" / TRUST)):
            copy_verified(source, package / name)
        # Bind copies to the evidence already validated, not merely to whatever
        # bytes happen to occupy staging when the subsequent copy starts.
        if (build.digest(package / APK) != report["assembly"]["zip"]["unsigned_apk_sha256"]
                or (package / TRUST).read_bytes() != trust.encode()
                or json.dumps(read_json(package / REPORT), sort_keys=True, allow_nan=False)
                != json.dumps(report, sort_keys=True, allow_nan=False)):
            raise ValueError("Copied payload differs from validated build evidence")
        manifest = {"schema": PACKAGE_SCHEMA, "artifact_kind": "unsigned-developer-package",
                    "deployment_ready": False, "signed": False, "vehicle_validated": False,
                    "handoff_enabled": args.enable_handoff,
                    "client_public_certificate_sha256": certificates,
                    "provenance": report["provenance"], "preparer_sha256": preparer_sha256,
                    "unsigned_service": {"filename": APK, "sha256": build.digest(package / APK),
                                         "size_bytes": (package / APK).stat().st_size},
                    "blocked_vehicle_gates": list(GATES),
                    "files_sha256": {name: build.digest(package / name) for name in PAYLOAD}}
        write_json(package / MANIFEST, manifest)
        if read_json(package / MANIFEST) != manifest:
            raise ValueError("Written package manifest changed")
        archive = stage / "prepared.zip"
        with zipfile.ZipFile(archive, "x", zipfile.ZIP_STORED) as container:
            for name in (*PAYLOAD, MANIFEST):
                container.write(package / name, name)
        verify_archive(archive, manifest)
        # Recheck every immutable input after assembly AND copy/ZIP verification.
        if build.source_manifest() != sources or build.digest(Path(__file__)) != preparer_sha256:
            raise ValueError("Source inputs changed during package preparation")
        check_host_trust()
        for name, path in inputs.items():
            safe_path(path, file=True)
            build.pinned(path, expected[name])
        output_path(output)
        # Same-filesystem hardlink is an atomic no-replace publication for a FILE.
        # It fails if a destination races us, even if it is an empty file/symlink.
        # No overwrite/rename fallback; unsupported filesystems fail closed.
        os.link(archive, output)
    return manifest


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("android-jar", "source-apk", "apktool", "r8", "zipalign", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--enable-handoff", action="store_true")
    parser.add_argument("--client-cert-sha256", action="append", default=[])
    args = parser.parse_args(argv)
    try:
        manifest = prepare(args)
        print(json.dumps(manifest, indent=2))
        return 0
    except (ValueError, OSError, UnicodeError, zipfile.BadZipFile) as error:
        print(f"Offline package refused/failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
