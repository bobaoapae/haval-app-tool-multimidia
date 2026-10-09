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
import struct
import subprocess
import sys
import tempfile
import zipfile
import zlib
import patch_service_hooks as hooks

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
TOOL_HASHES = {
    "android": "96c0b5750ea7715f1db5005085d0b0a46c98680cfd28c88c370540327ebbc862",
    "apktool": "eee4669a704a14e0623407e6701b0b91887e61e1e4049cb7a82833e14ae8b5fd",
    "r8": "3b4de3053885da105e39c15212261d22653d6d1b5eb92323dd04ae913cc8286f",
}
ASSEMBLY_JAVA_MAJOR = 21
ZIPALIGN_REVISION = "36.0.0"
# Official SDK Build Tools 36.0.0 executables, verified against Google's
# repository manifest/archive checksums; never accept a user-supplied hash.
ZIPALIGN_HASHES = {
    "darwin": "0427144f4a3fd242c5a159e7088637082539ae556bc1d2bbc2032bb775d47cea",
    "linux": "c5f559e946de5a9e7d58792181db20383b228877812136bc469d97ae00a43b0a",
    "win32": "c503c7da88bd4f6cddbcc8d3febd41e1e5022a525147f05f8caf4602353409c0",
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


def require_assembly_java() -> None:
    # apktool's float comments differ between Java 17 and 21. The reviewed
    # raw-byte smali profile was generated with Java 21; never normalize hashes.
    java = shutil.which("java")
    if not java:
        raise ValueError("Unsigned assembly requires the reviewed Java 21 runtime")
    result = subprocess.run([java, "-XshowSettings:properties", "-version"],
                            capture_output=True, text=True, check=True, timeout=15)
    versions = re.findall(r"^\s*java\.specification\.version\s*=\s*(\d+)\s*$",
                          result.stdout + "\n" + result.stderr, re.M)
    if versions != [str(ASSEMBLY_JAVA_MAJOR)]:
        raise ValueError("Unsigned assembly requires Java 21 for the reviewed raw-byte "
                         "smali profile; compile-only may use JDK 17+. Do not replace "
                         "the tree fingerprint to accept another runtime's decode")


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


def zip_structure(raw: bytes, label: str, allow_signing_block: bool = False,
                  require_alignment: bool = True) -> dict:
    """Check the bounded, single-disk ZIP32 layout used by the reviewed APK.

    zipfile reads central metadata and does not validate local sizes or data
    descriptors. Inspect those records before trusting decompressed content.
    """
    def refuse(reason: str) -> None:
        raise ValueError(f"Invalid {label} ZIP structure: {reason}")

    def extra_fields(extra: bytes) -> None:
        cursor = 0
        while cursor < len(extra):
            # Older zipalign versions append zero padding to the local extra.
            if not any(extra[cursor:]):
                return
            if cursor + 4 > len(extra):
                refuse("truncated extra field")
            kind, size = struct.unpack_from("<HH", extra, cursor)
            if kind == 1:
                refuse("ZIP64 is unsupported")
            if kind == 0x7075:
                refuse("Unicode filename aliases are unsupported")
            cursor += 4 + size
            if cursor > len(extra):
                refuse("extra field exceeds its header")

    # An EOCD signature can occur in the comment. Require both the declared
    # comment length and the central-directory extent, not just the last magic.
    end = len(raw)
    eocd = None
    while True:
        end = raw.rfind(b"PK\x05\x06", max(0, len(raw) - 65557), end)
        if end < 0:
            break
        if end + 22 > len(raw):
            continue
        fields = struct.unpack_from("<4s4H2IH", raw, end)
        if end + 22 + fields[7] != len(raw):
            continue
        if 0xffff in fields[1:5] or 0xffffffff in fields[5:7]:
            refuse("ZIP64 is unsupported")
        if fields[6] + fields[5] == end:
            eocd = fields
            break
    if eocd is None:
        refuse("missing EOCD or invalid central-directory extent/comment length")
    if raw.rfind(b"PK\x05\x06") != end:
        refuse("EOCD signature inside the archive comment is unsupported by zipfile")
    if raw[max(0, end - 20):end - 16] == b"PK\x06\x07":
        refuse("ZIP64 is unsupported")
    _, disk, central_disk, disk_count, count, central_size, central, _ = eocd
    if disk or central_disk or disk_count != count:
        refuse("multi-disk ZIP is unsupported")
    if central > end or central_size < count * 46:
        refuse("central directory exceeds its bounds")

    local_limit = central
    if raw[max(0, central - 16):central] == b"APK Sig Block 42":
        if not allow_signing_block:
            refuse("unsigned output contains an APK Signing Block")
        if central < 32:
            refuse("truncated APK Signing Block")
        size = struct.unpack_from("<Q", raw, central - 24)[0]
        local_limit = central - size - 8
        if size < 24 or local_limit < 0 or struct.unpack_from("<Q", raw, local_limit)[0] != size:
            refuse("invalid APK Signing Block extent")
        cursor = local_limit + 8
        while cursor < central - 24:
            if cursor + 8 > central - 24:
                refuse("truncated APK Signing Block pair")
            pair_size = struct.unpack_from("<Q", raw, cursor)[0]
            cursor += 8 + pair_size
            if pair_size < 4 or cursor > central - 24:
                refuse("invalid APK Signing Block pair extent")

    records = []
    methods = {}
    cursor = central
    for _ in range(count):
        if cursor + 46 > end or raw[cursor:cursor + 4] != b"PK\x01\x02":
            refuse("truncated or invalid central header")
        header = struct.unpack_from("<4s6H3I5H2I", raw, cursor)
        version, flags, method = header[2:5]
        values = header[7:10]
        name_size, extra_size, comment_size, entry_disk = header[10:14]
        offset = header[16]
        record_end = cursor + 46 + name_size + extra_size + comment_size
        if record_end > end:
            refuse("central entry exceeds its directory")
        if version >= 45 or 0xffffffff in (*values[1:], offset) or entry_disk == 0xffff:
            refuse("ZIP64 or newer extraction layout is unsupported")
        if version > 20 or entry_disk or method not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
            refuse("unsupported extraction version, disk or compression method")
        if flags & ~0x080e or (method == zipfile.ZIP_STORED and flags & 6):
            refuse("unsupported ZIP flags (encrypted/reserved layout)")
        name = raw[cursor + 46:cursor + 46 + name_size]
        if not name or b"\0" in name or b"\\" in name:
            refuse("empty, NUL-containing or backslash filename is unsupported")
        try:
            decoded = name.decode("utf-8" if flags & 0x800 else "cp437")
        except UnicodeDecodeError:
            refuse("invalid UTF-8 filename")
        if decoded in methods:
            refuse("Duplicate ZIP entries")
        extra_fields(raw[cursor + 46 + name_size:cursor + 46 + name_size + extra_size])
        methods[decoded] = method
        records.append((offset, name, flags, method, values))
        cursor = record_end
    if cursor != end:
        refuse("central directory count/size mismatch")

    descriptors = aligned = stored = 0
    previous_end = 0
    records.sort()
    for index, (offset, name, flags, method, values) in enumerate(records):
        bound = min(records[index + 1][0], local_limit) if index + 1 < len(records) else local_limit
        if offset != previous_end or not 0 <= offset <= local_limit or offset + 30 > bound:
            refuse("overlapping, out-of-bounds or noncontiguous local records")
        local = struct.unpack_from("<4s5H3I2H", raw, offset)
        if local[0] != b"PK\x03\x04":
            refuse("invalid local header signature")
        if local[1] >= 45 or 0xffffffff in local[7:9]:
            refuse("ZIP64 local header is unsupported")
        if local[1] > 20:
            refuse("unsupported local extraction version")
        data = offset + 30 + local[9] + local[10]
        if data > bound:
            refuse("local name/extra exceeds its record")
        if raw[offset + 30:offset + 30 + local[9]] != name:
            refuse("local/central filename mismatch")
        if local[2:4] != (flags, method):
            refuse("local/central flags or compression method mismatch")
        extra_fields(raw[offset + 30 + local[9]:data])
        if flags & 8:
            if any(value not in (0, expected) for value, expected in zip(local[6:9], values)):
                refuse("local/central CRC or size mismatch with bit 3")
        elif local[6:9] != values:
            refuse("local/central CRC or size mismatch")
        record_end = data + values[1]
        if record_end > bound:
            refuse("compressed payload exceeds its local record")
        if method == zipfile.ZIP_STORED:
            if values[1] != values[2]:
                refuse("STORED compressed/uncompressed size mismatch")
            directory = name.endswith(b"/") and values[1] == values[2] == 0
            stored += not directory
            if not directory and data % 4 and require_alignment:
                raise ValueError(f"Invalid {label} Android payload alignment: STORED payload is not "
                                 f"4-byte aligned: {name!r} (offset {data})")
            aligned += not directory and data % 4 == 0
            if zlib.crc32(raw[data:record_end]) != values[0]:
                refuse("STORED CRC differs from raw payload")
        else:
            # ZipFile.read may truncate inflated bytes to the declared size.
            # Independently consume the entire raw DEFLATE span, with bounded
            # output chunks, and require an exact end-of-stream and CRC/length.
            decoder = zlib.decompressobj(-15)
            inflated = crc = 0
            position = data
            try:
                while position < record_end:
                    piece = raw[position:min(position + 65536, record_end)]
                    position += len(piece)
                    while True:
                        limit = min(65536, values[2] - inflated + 1)
                        chunk = decoder.decompress(piece, limit)
                        inflated += len(chunk)
                        crc = zlib.crc32(chunk, crc)
                        if inflated > values[2]:
                            refuse("DEFLATE uncompressed size differs from full raw stream")
                        if decoder.unused_data or (decoder.eof and position != record_end):
                            refuse("unused bytes inside declared DEFLATE span")
                        piece = decoder.unconsumed_tail
                        if not piece and len(chunk) < limit:
                            break
            except zlib.error:
                refuse("invalid raw DEFLATE stream")
            if not decoder.eof:
                refuse("unfinished raw DEFLATE stream")
            if inflated != values[2] or crc != values[0]:
                refuse("DEFLATE CRC/uncompressed size differs from full raw stream")
        if flags & 8:
            # CRC32 may itself equal 0x08074b50. Try both bounded layouts;
            # never scan for magic or assume the first word is a signature.
            lengths = []
            for marker in (0, 4):
                start = record_end + marker
                if start + 12 <= bound and (not marker or raw[record_end:start] == b"PK\x07\x08"):
                    if struct.unpack_from("<3I", raw, start) == values:
                        lengths.append(marker + 12)
            if len(lengths) != 1:
                refuse("missing, invalid or ambiguous data descriptor")
            record_end += lengths[0]
            descriptors += 1
        previous_end = record_end
    # apksigner can pad the signed source to the signing-block boundary.
    if previous_end != local_limit and not (allow_signing_block and local_limit != central
                                           and previous_end < local_limit
                                           and not any(raw[previous_end:local_limit])):
        refuse("unexpected bytes after the final local record")
    return {"compression_methods": methods, "entries": count,
            "data_descriptors": descriptors, "stored_payload_entries": stored,
            "aligned_stored_entries": aligned}


def verify_zip(source: Path, output: Path, require_output_alignment: bool = True) -> dict:
    source_structure = zip_structure(source.read_bytes(), "source", allow_signing_block=True)
    raw = output.read_bytes()
    output_structure = zip_structure(raw, "output", require_alignment=require_output_alignment)
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(output) as rebuilt:
        before, after = set(original.namelist()), set(rebuilt.namelist())
        if len(before) != len(original.infolist()) or len(after) != len(rebuilt.infolist()):
            raise ValueError("Duplicate ZIP entries")
        if before != set(source_structure["compression_methods"]) or after != set(output_structure["compression_methods"]):
            raise ValueError("ZIP filename interpretation differs from raw headers")
        if any(source_structure["compression_methods"][n] != output_structure["compression_methods"][n]
               for n in before & after):
            raise ValueError("Source/output ZIP compression method changed")
        changed = []
        for name in sorted(before & after):
            old, new = original.read(name), rebuilt.read(name)
            if len(old) != original.getinfo(name).file_size or len(new) != rebuilt.getinfo(name).file_size:
                raise ValueError("ZIP uncompressed size differs from decoded content")
            if old != new:
                changed.append(name)
        if changed != ["classes.dex"] or before - after != SIGNATURE_FILES or after - before:
            raise ValueError("Unexpected manifest/resource/ZIP mutation")
        if any(n.startswith("META-INF/") for n in after) or b"APK Sig Block 42" in raw:
            raise ValueError("Unsigned output unexpectedly contains signature metadata")
        return {"changed_entries": changed, "removed_signature_entries": sorted(before-after),
                "manifest_and_resources_byte_identical": True, "unsigned_apk_sha256": digest(output),
                "android_stored_alignment_verified": require_output_alignment,
                "structure": {label: {k: v for k, v in report.items() if k != "compression_methods"}
                              for label, report in (("source", source_structure), ("output", output_structure))}}


def align_unsigned(source: Path, unaligned: Path, output: Path, zipalign: Path) -> dict:
    if output.exists():
        raise ValueError("Aligned output must be a new file; no existing file is overwritten")
    # Refuse malformed descriptors, hidden DEFLATE bytes, name aliases, content
    # mutations and signing metadata BEFORE zipalign can rewrite any headers.
    before = verify_zip(source, unaligned, require_output_alignment=False)
    run([str(zipalign), "-v", "4", str(unaligned), str(output)])
    run([str(zipalign), "-c", "-v", "4", str(output)])
    return {"zip": verify_zip(source, output),
            "zip_alignment": {"alignment_bytes": 4, "sdk_build_tools_revision": ZIPALIGN_REVISION,
                              "tool_sha256": digest(zipalign),
                              "pre_alignment_output": before["structure"]["output"]}}


def canonical_stock(text: str) -> str:
    # apktool drops redundant static boolean default-false encoded initializers.
    # Do not normalize instructions, method bodies or any other initialization.
    return re.sub(r"(?m)^(\.field[^\n]*\bstatic\b[^\n]*:Z) = false$", r"\1", text)


def assemble(work: Path, source: Path, android: Path, apktool: Path, r8: Path, zipalign: Path) -> dict:
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
    unaligned = work / "AndroidAutoService-UNSIGNED-unaligned.apk"
    run([java, "-jar", str(apktool), "b", "-j", "2", "-p", str(framework), "-o", str(unaligned), str(hooked)])
    report.update(align_unsigned(source, unaligned, output, zipalign))
    unaligned.unlink()  # This private staging file was created above, never supplied input.
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
    parser.add_argument("--zipalign", type=Path)
    parser.add_argument("--enable-handoff", action="store_true")
    parser.add_argument("--client-cert-sha256", action="append", default=[])
    args = parser.parse_args(argv)
    try:
        trust = trust_source(args.client_cert_sha256, args.enable_handoff)
        android = pinned(args.android_jar, TOOL_HASHES["android"])
        source = apktool = r8 = zipalign = None
        if not args.compile_only:
            if not all((args.source_apk, args.apktool, args.r8, args.zipalign)):
                raise ValueError("Assembly requires supplied APK and pinned apktool/R8/zipalign files")
            source = pinned(args.source_apk, hooks.APK_SHA256)
            apktool = pinned(args.apktool, TOOL_HASHES["apktool"])
            r8 = pinned(args.r8, TOOL_HASHES["r8"])
            if sys.platform not in ZIPALIGN_HASHES:
                raise ValueError("Pinned SDK zipalign supports macOS, Linux and Windows only")
            zipalign = pinned(args.zipalign, ZIPALIGN_HASHES[sys.platform])
            require_assembly_java()
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
                      "tool_sha256": dict(TOOL_HASHES), "unsigned_assembly": False}
            if not args.compile_only:
                report["tool_sha256"]["zipalign"] = ZIPALIGN_HASHES[sys.platform]
                report["assembly"] = assemble(work, source, android, apktool, r8, zipalign)
                report["unsigned_assembly"] = True
                report["assembly_java_major"] = ASSEMBLY_JAVA_MAJOR
                report["source_apk_sha256"] = hooks.APK_SHA256
            (work / "report.json").write_text(json.dumps(report, indent=2)+"\n")
            work.rename(output)
        print(json.dumps(report, indent=2))
        return 0
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        print(f"Unsigned validation refused/failed: {error}", file=__import__("sys").stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
