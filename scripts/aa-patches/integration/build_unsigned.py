#!/usr/bin/env python3
"""Pinned, local unsigned assembly. Never downloads, signs, installs or deploys."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile
import patch_service_hooks as hooks

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
TOOL_HASHES = {
    "android": "96c0b5750ea7715f1db5005085d0b0a46c98680cfd28c88c370540327ebbc862",
    "apktool": "eee4669a704a14e0623407e6701b0b91887e61e1e4049cb7a82833e14ae8b5fd",
    "r8": "3b4de3053885da105e39c15212261d22653d6d1b5eb92323dd04ae913cc8286f",
}
SIGNATURE_FILES = {"META-INF/CERT.RSA", "META-INF/CERT.SF", "META-INF/MANIFEST.MF"}
CHANGED_STOCK = {
    "com/ts/androidauto/aap/sink/GalIntegration.smali",
    "com/google/android/projection/protocol/GalReceiver.smali",
    "com/ts/androidauto/projectionservice/AndroidAutoService$LinkCommandBinder.smali",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def pinned(path: Path, expected: str) -> Path:
    path = path.resolve(strict=True)
    if not path.is_file() or digest(path) != expected:
        raise ValueError(f"Unexpected artifact/tool fingerprint: {path.name}")
    return path


def trust_source(certificates: list[str], enabled: bool) -> str:
    certificates = sorted(set(certificates))
    if any(not re.fullmatch(r"[0-9a-f]{64}", x) or x == "0" * 64 for x in certificates):
        raise ValueError("Use explicit lowercase public SHA-256 certificate fingerprints, never keys")
    if enabled != bool(certificates):
        raise ValueError("Enabled handoff requires explicit client pins; validation-only must have none")
    pins = ",".join(json.dumps(x) for x in certificates)
    # Not a javac constant: compile the real registration/authentication branches
    # even when this lab artifact is disabled. No fabricated successful caller.
    return ("package com.ts.androidauto.impulse.cluster;\n"
            "/** Generated public trust configuration; no credential material. */\n"
            "public final class GeneratedTrust {\n"
            f" public static final boolean HANDOFF_ENABLED = Boolean.parseBoolean(\"{str(enabled).lower()}\");\n"
            f" public static final String[] CLIENT_CERTIFICATES = new String[]{{{pins}}};\n"
            "}\n")


def run(command: list[str]) -> None:
    subprocess.run(command, cwd=ROOT, check=True, timeout=180)


def jar_classes(source: Path, output: Path) -> None:
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(source.rglob("*.class")):
            jar.write(path, path.relative_to(source).as_posix())


def compile_sources(work: Path, android: Path, trust: str) -> None:
    java = shutil.which("java")
    if not java:
        raise ValueError("A JDK is required; missing compiler is not a skipped pass")
    javac = shutil.which("javac")
    compiler = [javac] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
    common = compiler + ["-source", "8", "-target", "8", "-Xlint:-options"]
    stubs = work / "api-stubs"
    classes = work / "classes"
    host = work / "host-client"
    for directory in (stubs, classes, host):
        directory.mkdir()
    generated = work / "generated/com/ts/androidauto/impulse/cluster/GeneratedTrust.java"
    generated.parent.mkdir(parents=True)
    generated.write_text(trust)
    protocol = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku/api/AaClusterProtocol.java"
    pump = HERE.parent / "prototype/ClusterFramePump.java"
    barrier = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku/api/ClusterLeaseBarrier.java"
    run(common + ["-cp", str(android), "-d", str(stubs)] + [str(p) for p in sorted((HERE / "api-stubs").rglob("*.java"))])
    sources = sorted((HERE / "src").rglob("*.java")) + [generated, protocol, barrier, pump]
    run(common + ["-cp", os.pathsep.join((str(android), str(stubs))), "-d", str(classes)] + [str(p) for p in sources])
    client = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku/managers/AndroidAutoClusterClient.java"
    ledger = protocol.parent / "ClusterReleaseLedger.java"
    output = client.parent / "ClusterSurfaceOutput.java"
    run(common + ["-cp", str(android), "-d", str(host), str(protocol), str(barrier), str(ledger), str(output), str(client)])
    jar_classes(stubs, work / "api-stubs.jar")
    jar_classes(classes, work / "helpers.jar")


def verify_zip(source: Path, output: Path) -> dict:
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(output) as rebuilt:
        before, after = set(original.namelist()), set(rebuilt.namelist())
        if len(before) != len(original.infolist()) or len(after) != len(rebuilt.infolist()):
            raise ValueError("Duplicate ZIP entries")
        changed = sorted(n for n in before & after if original.read(n) != rebuilt.read(n))
        if changed != ["classes.dex"] or before - after != SIGNATURE_FILES or after - before:
            raise ValueError("Unexpected manifest/resource/ZIP mutation")
        raw = output.read_bytes()
        if any(n.startswith("META-INF/") for n in after) or b"APK Sig Block 42" in raw:
            raise ValueError("Unsigned output unexpectedly contains signature metadata")
        return {"changed_entries": changed, "removed_signature_entries": sorted(before-after),
                "manifest_and_resources_byte_identical": True, "unsigned_apk_sha256": digest(output)}


def canonical_stock(text: str) -> str:
    # apktool drops redundant static boolean default-false encoded initializers.
    # Do not normalize instructions, method bodies or any other initialization.
    return re.sub(r"(?m)^(\.field[^\n]*\bstatic\b[^\n]*:Z) = false$", r"\1", text)


def assemble(work: Path, source: Path, android: Path, apktool: Path, r8: Path) -> dict:
    from verify_helper_references import verify, verify_final_hooks
    java = shutil.which("java")
    dex = work / "dex"
    dex.mkdir()
    run([java, "-cp", str(r8), "com.android.tools.r8.D8", "--min-api", "28", "--lib", str(android),
         "--classpath", str(work / "api-stubs.jar"), "--output", str(dex), str(work / "helpers.jar")])
    framework = work / "framework"
    def decode(apk: Path, directory: Path) -> None:
        run([java, "-jar", str(apktool), "d", "-r", "-j", "2", "-p", str(framework), "-o", str(directory), str(apk)])
    helper_apk = work / "helper-only.apk"
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(helper_apk, "w") as container:
        container.writestr("AndroidManifest.xml", original.read("AndroidManifest.xml"))
        container.write(dex / "classes.dex", "classes.dex")
    stock, helper, hooked = work / "stock", work / "helper", work / "hooked"
    decode(source, stock)
    decode(helper_apk, helper)
    # The patcher enforces the exact APK and whole original smali inventory.
    run([shutil.which("python3"), str(HERE / "patch_service_hooks.py"), str(stock),
         "--source-apk", str(source), "--output", str(hooked)])
    report = verify(helper / "smali", hooked / "smali")
    for path in sorted((helper / "smali").rglob("*.smali")):
        target = hooked / "smali" / path.relative_to(helper / "smali")
        if target.exists():
            raise ValueError("Helper collision with OEM class")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
    output = work / "AndroidAutoService-UNSIGNED.apk"
    run([java, "-jar", str(apktool), "b", "-j", "2", "-p", str(framework), "-o", str(output), str(hooked)])
    report["zip"] = verify_zip(source, output)
    final = work / "final-decode"
    decode(output, final)
    stock_files = {p.relative_to(stock / "smali").as_posix(): p for p in (stock / "smali").rglob("*.smali")}
    helper_files = {p.relative_to(helper / "smali").as_posix() for p in (helper / "smali").rglob("*.smali")}
    final_files = {p.relative_to(final / "smali").as_posix(): p for p in (final / "smali").rglob("*.smali")}
    if set(final_files) != set(stock_files) | helper_files:
        raise ValueError("Final DEX class inventory mismatch")
    for name, path in stock_files.items():
        if name not in CHANGED_STOCK and canonical_stock(path.read_text()) != canonical_stock(final_files[name].read_text()):
            raise ValueError(f"Unexpected change to existing OEM class: {name}")
    final_helpers = work / "final-helpers"
    for name in helper_files:
        target = final_helpers / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(final_files[name], target)
    report["final_references"] = verify(final_helpers, final / "smali")
    report["final_hooks"] = verify_final_hooks(stock / "smali", hooked / "smali", final / "smali")
    report["preserved_oem_classes"] = len(stock_files)-len(CHANGED_STOCK)
    report["changed_oem_classes"] = sorted(CHANGED_STOCK)
    return report


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-jar", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--compile-only", action="store_true")
    parser.add_argument("--source-apk", type=Path)
    parser.add_argument("--apktool", type=Path)
    parser.add_argument("--r8", type=Path)
    parser.add_argument("--enable-handoff", action="store_true")
    parser.add_argument("--client-cert-sha256", action="append", default=[])
    args = parser.parse_args(argv)
    try:
        trust = trust_source(args.client_cert_sha256, args.enable_handoff)
        android = pinned(args.android_jar, TOOL_HASHES["android"])
        source = apktool = r8 = None
        if not args.compile_only:
            if not all((args.source_apk, args.apktool, args.r8)):
                raise ValueError("Assembly requires supplied APK and pinned apktool/R8 files")
            source = pinned(args.source_apk, hooks.APK_SHA256)
            apktool = pinned(args.apktool, TOOL_HASHES["apktool"])
            r8 = pinned(args.r8, TOOL_HASHES["r8"])
        output = args.output.resolve()
        if output.exists():
            raise ValueError("Output must be a new directory; no existing output is overwritten")
        output.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="cluster-unsigned-", dir=output.parent) as temporary:
            work = Path(temporary) / "result"
            work.mkdir()
            compile_sources(work, android, trust)
            report = {"android_api": 28, "java_compile": True, "host_java_compile": True,
                      "host_kotlin_compile": False, "handoff_enabled": args.enable_handoff,
                      "client_public_certificate_sha256": sorted(set(args.client_cert_sha256)),
                      "deployment_ready": False, "signed": False, "vehicle_validated": False,
                      "tool_sha256": TOOL_HASHES, "unsigned_assembly": False}
            if not args.compile_only:
                report["assembly"] = assemble(work, source, android, apktool, r8)
                report["unsigned_assembly"] = True
                report["source_apk_sha256"] = hooks.APK_SHA256
            (work / "report.json").write_text(json.dumps(report, indent=2)+"\n")
            work.rename(output)
        print(json.dumps(report, indent=2))
        return 0
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"Unsigned validation refused/failed: {error}", file=__import__("sys").stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
