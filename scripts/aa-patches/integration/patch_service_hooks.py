#!/usr/bin/env python3
"""Exact-profile, source-only CLUSTER hooks. Never signs, builds, or deploys an APK."""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import shutil
import tempfile

APK_SHA256 = "a9cfb4c0e559f9638466a80d77a861ffd4b5737d5ac9f71e0c20370f048ae1d8"
GAL = "Lcom/google/android/projection/protocol/GalReceiver;"
PROVIDER = "Lcom/google/android/projection/protocol/CarServiceProvider;"
INTEGRATION = "Lcom/ts/androidauto/aap/sink/GalIntegration;"
BINDER = "Lcom/ts/androidauto/projectionservice/AndroidAutoService$LinkCommandBinder;"
STUB = "Lcom/ts/androidauto/sdk/aidl/LinkCommand$Stub;"
SERVICE = "Lcom/ts/androidauto/projectionservice/AndroidAutoService;"
VIDEO = "Lcom/google/android/projection/protocol/VideoSink;"
INPUT = "Lcom/google/android/projection/protocol/InputSource;"
HELPER = "Lcom/ts/androidauto/impulse/cluster/ClusterIntegration;"
BINDER_HELPER = "Lcom/ts/androidauto/impulse/cluster/ClusterBinder;"
ON_TRANSACT = "onTransact(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z"
PAIR_METHOD = f"registerImpulseClusterPair({PROVIDER}{PROVIDER})Z"
PAIR_HELPER = f"registerPair(Landroid/util/SparseArray;JZ{PROVIDER}{PROVIDER})Z"
TRANSACTION = 55
SERVICE_IDS = (21, 22)
LEGACY_IDS = (2, 3, 1, 8, 17, 18, 19, 20, 7, 9, 10, 12, 16)
MARKER = "IMPULSE_CLUSTER_HOOK_V1"


class Refusal(ValueError):
    """The supplied input does not match the reviewed profile."""


@dataclass(frozen=True)
class Profile:
    tree_sha256: str
    class_hashes: dict[str, str]


# Byte-identical apktool 3.0.2 decode: d -r, with debug information preserved.
# These are fingerprints, not embedded/decompiled OEM implementation.
VERIFIED_PROFILE = Profile(
    "3e86b201832586928e96a5296af5dd477c2978b18c8ced7e342b767225847149",
    {
        INTEGRATION: "3d569937e82514c6146e37a6ac802a6ac4f5f02debbe42c99e21136e0f0d4ff3",
        GAL: "793651e4649cd74f7e313db60a2b50a269fc8f2f0b68df91d8d6e011879bf71b",
        BINDER: "3539b65862b0198f09be4380e3818f8ada18349c75b22c64b188d2ca08059399",
        STUB: "b620c6c9252bde440025847331ed3ea6287ed9fc783c86c865d134d119179a59",
        VIDEO: "ad8f858a57efe053e7853ad9b135be59f7e69dcdf6234e8167be4f9832f59951",
        INPUT: "40515dc3921ea29c9ebea1c655d18c9fecd605758022d0708b8a60731342bc1c",
    },
)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def relative_path_key(path: Path, root: Path) -> tuple[str, ...]:
    # Preserve POSIX component ordering, including directory/file prefixes.
    # Native Windows Path ordering case-folds names and changes the fingerprint.
    return path.relative_to(root).parts


def inventory(root: Path) -> tuple[dict[str, tuple[Path, str]], str]:
    if not root.is_dir() or root.is_symlink():
        raise Refusal("Input must be an existing, non-symlink decode directory")
    files = sorted(root.rglob("*"), key=lambda p: relative_path_key(p, root))
    if any(p.is_symlink() for p in files):
        raise Refusal("Symlink in decode tree")
    classes: dict[str, tuple[Path, str]] = {}
    tree = hashlib.sha256()
    for path in files:
        rel = path.relative_to(root)
        if not path.is_file() or path.suffix != ".smali":
            continue
        if not re.fullmatch(r"smali(?:_classes[2-9][0-9]*)?", rel.parts[0]):
            raise Refusal(f"Unexpected smali root: {rel}")
        raw = path.read_bytes()
        try:
            text = raw.decode("utf-8")
        except UnicodeError as exc:
            raise Refusal(f"Unreadable smali: {rel}") from exc
        declarations = re.findall(r"^\.class[ \t]+([^\r\n]+)\r?$", text, re.M)
        if len(declarations) != 1:
            raise Refusal(f"Expected one class declaration: {rel}")
        name = declarations[0].split()[-1]
        if not re.fullmatch(r"L[^\s;]+;", name) or name in classes:
            raise Refusal(f"Invalid or duplicate class: {name}")
        if MARKER in text or "Lcom/ts/androidauto/impulse/cluster/" in text:
            raise Refusal("Input is already patched or contains integration helpers")
        classes[name] = (path, text)
        tree.update(rel.as_posix().encode() + b"\0" + hashlib.sha256(raw).digest())
    return classes, tree.hexdigest()


def methods(text: str) -> dict[str, str]:
    result = {}
    for match in re.finditer(r"^\.method\s+([^\r\n]+)\r?\n(.*?)^\.end method\s*$", text, re.M | re.S):
        sig = match.group(1).split()[-1]
        if sig in result:
            raise Refusal(f"Duplicate method declaration: {sig}")
        result[sig] = match.group(0)
    if len(re.findall(r"^\.method\s", text, re.M)) != len(result):
        raise Refusal("Malformed method blocks")
    return result


def require_method(ms: dict[str, str], sig: str, flags: tuple[str, ...] = ()) -> str:
    body = ms.get(sig)
    if body is None or any(f not in body.splitlines()[0].split() for f in flags):
        raise Refusal(f"Missing method/access contract: {sig}")
    return body


def declared_field(text: str, signature: str, flags: tuple[str, ...]) -> None:
    hits = re.findall(r"^\.field\s+([^\r\n]+)$", text, re.M)
    hits = [h.split(" = ")[0].split() for h in hits if h.split(" = ")[0].split()[-1] == signature]
    if len(hits) != 1 or any(f not in hits[0] for f in flags):
        raise Refusal(f"Missing/duplicate field contract: {signature}")


def _instructions(body: str) -> list[str]:
    return [line.strip() for line in body.splitlines() if line.strip() and not line.lstrip().startswith((".", "#", ":"))]


def _legacy_ids(body: str) -> tuple[int, ...]:
    constants = {}
    found = []
    for line in _instructions(body):
        match = re.fullmatch(r"const(?:/4|/16)?\s+([vp]\d+),\s*(0x[0-9a-f]+|\d+)", line)
        if match:
            constants[match[1]] = int(match[2], 0)
        call = re.fullmatch(r"invoke-virtual\s+\{([^}]+)\},\s*" + re.escape(GAL) + r"->registerCarService\(I" + re.escape(PROVIDER) + r"\)Z", line)
        if call:
            reg = [x.strip() for x in call[1].split(",")][1]
            if reg not in constants:
                raise Refusal("Unresolved registration ID")
            found.append(constants[reg])
    return tuple(found)


def _verify_legacy_transactions(body: str) -> None:
    switches = re.findall(r"^\s*\.packed-switch\s+(0x[0-9a-f]+|\d+)\s*\n(.*?)^\s*\.end packed-switch\s*$", body, re.M | re.S)
    if len(switches) != 1 or int(switches[0][0], 0) != 1:
        raise Refusal("Unrecognized Binder transaction dispatch")
    cases = [x.strip() for x in switches[0][1].splitlines() if x.strip()]
    if len(cases) != 54 or any(not re.fullmatch(r":\w+", x) for x in cases):
        raise Refusal("Transaction 55 may collide or legacy transaction set changed")
    if "packed-switch p1," not in body or "0x5f4e5446" not in body:
        raise Refusal("Unrecognized Binder dispatch control flow")


def pair_wrapper() -> str:
    return f"""
# {MARKER}: receiver-monitor-protected paired registration; no reflection.
.method public final declared-synchronized {PAIR_METHOD}
    .locals 6
    monitor-enter p0
    :impulse_pair_try_start
    iget-object v0, p0, {GAL}->mRegisteredServices:Landroid/util/SparseArray;
    iget-wide v1, p0, {GAL}->mNativeGalReceiver:J
    iget-boolean v3, p0, {GAL}->mStopping:Z
    move-object v4, p1
    move-object v5, p2
    invoke-static/range {{v0 .. v5}}, {HELPER}->{PAIR_HELPER}
    move-result v0
    monitor-exit p0
    :impulse_pair_try_end
    return v0
    :impulse_pair_catchall
    move-exception v0
    monitor-exit p0
    throw v0
    .catchall {{:impulse_pair_try_start .. :impulse_pair_try_end}} :impulse_pair_catchall
.end method
"""


def binder_override() -> str:
    return f"""
# {MARKER}: only the verified-unused extension code; preserve every legacy call.
.method public {ON_TRANSACT}
    .locals 2
    const/16 v0, 0x37
    if-ne p1, v0, :impulse_legacy_transaction
    iget-object v0, p0, {BINDER}->this$0:{SERVICE}
    invoke-static {{v0, p2, p3, p4}}, {BINDER_HELPER}->dispatch(Landroid/content/Context;Landroid/os/Parcel;Landroid/os/Parcel;I)Z
    move-result v0
    return v0
    :impulse_legacy_transaction
    invoke-super {{p0, p1, p2, p3, p4}}, {STUB}->{ON_TRANSACT}
    move-result v0
    return v0
.end method
"""


def plan_patch(root: Path, *, _profile: Profile = VERIFIED_PROFILE) -> dict[Path, str]:
    """Validate all preconditions before returning any prospective changes.

    _profile is a Python-only seam for synthetic tests, never a CLI override.
    """
    classes, tree_hash = inventory(root)
    required = (INTEGRATION, GAL, BINDER, STUB, VIDEO, INPUT)
    if any(name not in classes for name in required):
        raise Refusal("Missing required declared class")
    # Fingerprints remain over the exact original UTF-8 bytes, never normalized
    # text. Only the parser/patch view accepts CRLF; it cannot approve a new tree.
    class_hashes = {name: digest(classes[name][1].encode("utf-8")) for name in required}
    classes = {name: (path, text.replace("\r\n", "\n"))
               for name, (path, text) in classes.items()}
    ms = {name: methods(classes[name][1]) for name in required}
    if PAIR_METHOD in ms[GAL] or ON_TRANSACT in ms[BINDER]:
        raise Refusal("Already patched or concrete Binder dispatch changed")
    if not re.search(r"^\.super " + re.escape(STUB) + r"$", classes[BINDER][1], re.M):
        raise Refusal("Binder superclass changed")
    declared_field(classes[BINDER][1], "this$0:" + SERVICE, ("final", "synthetic"))
    for sig, flags in [("mRegisteredServices:Landroid/util/SparseArray;", ("private", "final")), ("mNativeGalReceiver:J", ("private",)), ("mStopping:Z", ("private", "volatile"))]:
        declared_field(classes[GAL][1], sig, flags)
    declared_field(classes[INTEGRATION][1], "mViewingDistance:I", ("private",))
    register = require_method(ms[INTEGRATION], "registerCarService()V", ("public",))
    destroy = require_method(ms[INTEGRATION], "destroy()V", ("public",))
    if _legacy_ids(register) != LEGACY_IDS:
        raise Refusal("Reserved IDs or legacy registration profile changed")
    if _instructions(register).count("return-void") != 1 or "    .locals 7\n" not in register:
        raise Refusal("Registration return/register layout changed")
    shutdown = f"    invoke-virtual {{v0}}, {GAL}->destroy()V"
    if destroy.count(shutdown) != 1 or "    .locals 2\n" not in destroy:
        raise Refusal("Teardown delegate profile changed")
    _verify_legacy_transactions(require_method(ms[STUB], ON_TRANSACT, ("public",)))
    for name in (VIDEO, INPUT):
        if "final" in classes[name][1].splitlines()[0].split():
            raise Refusal("Provider cannot be subclassed")
        for sig in ("create(IJ)Z", "destroy()V", "getNativeInstance()J"):
            body = require_method(ms[name], sig, ("public",))
            if any(x in body.splitlines()[0].split() for x in ("final", "static", "abstract")):
                raise Refusal("Provider lifecycle contract changed")
    if tree_hash != _profile.tree_sha256:
        raise Refusal("Decoded smali tree differs from the reviewed exact profile")
    for name in required:
        if class_hashes[name] != _profile.class_hashes.get(name):
            raise Refusal(f"Class bytes differ from reviewed profile: {name}")
    register_new = register.replace("    return-void", f"    # {MARKER}\n    iget-object v0, p0, {INTEGRATION}->galReceiver:{GAL}\n    if-eqz v0, :impulse_cluster_registration_done\n    iget v1, p0, {INTEGRATION}->mViewingDistance:I\n    invoke-static {{v0, v1}}, {HELPER}->register({GAL}I)V\n    :impulse_cluster_registration_done\n    return-void")
    destroy_new = destroy.replace(shutdown, f"    # {MARKER}\n    invoke-static {{v0}}, {HELPER}->retire({GAL})V\n\n" + shutdown)
    integration = classes[INTEGRATION][1].replace(register, register_new).replace(destroy, destroy_new)
    return {
        classes[INTEGRATION][0]: integration,
        classes[GAL][0]: classes[GAL][1] + pair_wrapper(),
        classes[BINDER][0]: classes[BINDER][1] + binder_override(),
    }


def patch_tree(source: Path, output: Path, *, _profile: Profile = VERIFIED_PROFILE) -> dict:
    """Publish one new tree by atomic sibling rename; never edit input/in-place."""
    source, output = Path(source), Path(output)
    if output.exists() or output.is_symlink():
        raise Refusal("Output must not already exist")
    if source.resolve() == output.resolve() or source.resolve() in output.resolve().parents:
        raise Refusal("Output cannot be inside the source tree")
    changes = plan_patch(source, _profile=_profile)
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix=".cluster-hooks-", dir=output.parent))
    try:
        shutil.copytree(source, stage, dirs_exist_ok=True, symlinks=True)
        # Recheck the staged bytes, catching input changes during copying.
        plan_patch(stage, _profile=_profile)
        for path, text in changes.items():
            (stage / path.relative_to(source)).write_text(text, encoding="utf-8", newline="\n")
        if output.exists():
            raise Refusal("Output appeared while staging; refusing overwrite")
        stage.rename(output)
    except BaseException:
        shutil.rmtree(stage, ignore_errors=True)
        raise
    return {"profile_apk_sha256": APK_SHA256, "changed_files": [p.relative_to(source).as_posix() for p in changes], "extension_transaction": TRANSACTION, "service_ids": list(SERVICE_IDS), "helpers_included": False, "deployment_ready": False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("decoded", type=Path)
    parser.add_argument("--source-apk", type=Path, required=True)
    choice = parser.add_mutually_exclusive_group(required=True)
    choice.add_argument("--check", action="store_true")
    choice.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if digest(args.source_apk.read_bytes()) != APK_SHA256:
            raise Refusal("Source APK SHA-256 does not match the reviewed artifact")
        if args.check:
            changes = plan_patch(args.decoded)
            result = {"profile_match": True, "planned_files": len(changes), "deployment_ready": False}
        else:
            result = patch_tree(args.decoded, args.output)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, ValueError) as exc:
        print(json.dumps({"error": str(exc), "deployment_ready": False}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
