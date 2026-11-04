#!/usr/bin/env python3
"""Read-only preflight for the experimental second Android Auto CLUSTER stream.

The previous implementation wrote a log-only registerInner stub and required a
VideoSink.setSurface method that does not exist in the historical 48ff APK.
Patching is deliberately disabled until registration AND independent rendering
are implemented and verified. No successful preflight is deployment approval.

    python3 scripts/aa-patches/patch_android_auto_service_cluster.py \
        scripts/.build/aa-service-cluster --check-contract --json

Only decoded smali declarations are inspected. No APK or decoded file is changed.
See DUMPS_V2_6_CLUSTER.md for provenance, limitations and the validation gates.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

PROTOCOL = "com/google/android/projection/protocol/"
PROTO = "com/google/android/projection/proto/Protos$"
AA = "com/ts/androidauto/"
VIDEO = f"L{PROTOCOL}VideoSink;"

# A deliberately bounded API subset observed in the historical stock 48ff DEX.
# These are declarations, NOT proof of handshake behavior or a usable decoder.
REQUIRED_METHODS = {
    VIDEO: (
        f"<init>(L{PROTOCOL}VideoSink$ProjectionListener;ZI)V",
        f"setDisplayIdAndType(IL{PROTO}DisplayType;)V",
        f"addSupportedConfiguration(L{PROTO}VideoConfiguration;)V",
        "setVideoFocus(IIZ)V",
        f"updateUiConfig(L{PROTO}UiConfig;)V",
    ),
    f"L{PROTOCOL}VideoSink$ProjectionListener;": (
        "onCodecConfig([B)V",
        "onCodecSetup(I)V",
        f"onProjectionUpdate(L{PROTOCOL}VideoFrame;)V",
    ),
    f"L{PROTOCOL}InputSource;": (
        f"<init>(L{PROTOCOL}InputSource$InputInjector;)V",
        "registerKeyCodes([I)V",
        "setDisplayId(I)V",
    ),
    f"L{PROTOCOL}GalReceiver;": (
        f"registerCarService(IL{PROTOCOL}CarServiceProvider;)Z",
    ),
    f"L{AA}aap/sink/GalIntegration;": ("registerCarService()V",),
    f"L{AA}aap/video/AapVideoManager;": ("showVideo(Landroid/view/Surface;II)V",),
    f"L{AA}aap/video/VideoPlayer;": ("updateSurface(Landroid/view/Surface;II)V",),
    f"L{AA}sdk/aidl/LinkCallback;": (
        f"onNotifyNextTurn(L{AA}sdk/aidl/data/IfNavigationData;)V",
        "onNotifyNextTurnDistance(IIII)V",
        f"onNavigationState(L{AA}sdk/aidl/data/IfNavigationStateData;)V",
    ),
}
REQUIRED_FIELDS = {
    f"L{PROTO}DisplayType;": (f"DISPLAY_TYPE_CLUSTER:L{PROTO}DisplayType;",),
}
BLOCKERS = (
    "Verify the APK currently installed on the target head unit; 48ff is historical evidence only.",
    "Implement and review pre-session CLUSTER registration with free service IDs and paired input.",
    "Implement an independent decoder/Surface route, frame ownership and cleanup; do not reuse MAIN's renderer.",
    "Verify next-session enable, disconnect/reconnect and Surface recreation without a black D3.",
    "Measure two settled on-car runs: CLUSTER fps, unchanged MAIN fps/D0 behavior and fresh TBT.",
)
PATCH_DISABLED = (
    "CLUSTER patching is disabled: registration/rendering is not implemented. "
    "Use --check-contract for a read-only API preflight. "
    "Do not rebuild, bundle or install a CLUSTER Service APK from this tool."
)


def declarations(text: str, directive: str) -> list[list[str]]:
    """Read declaration lines only, never method calls, strings or comments."""
    result = []
    for line in text.splitlines():
        tokens = line.split("#", 1)[0].split()
        if tokens and tokens[0] == directive:
            result.append(tokens[1:])
    return result


def inspect_contract(decoded: Path) -> dict:
    """Inspect an apktool-style tree without writing to it or following guesses."""
    if not decoded.is_dir():
        raise ValueError(f"Decoded directory missing: {decoded}")
    roots = sorted(
        p for p in decoded.iterdir()
        if p.is_dir() and re.fullmatch(r"smali(?:_classes[2-9][0-9]*|_classes1[0-9]+)?", p.name)
    )
    if not roots:
        raise ValueError("No smali or smali_classesN directory found")

    checks = []
    errors = []
    video_surface_methods = []
    for descriptor in sorted(REQUIRED_METHODS.keys() | REQUIRED_FIELDS.keys()):
        relative = descriptor[1:-1] + ".smali"
        matches = [root / relative for root in roots if (root / relative).is_file()]
        check = {"class": descriptor, "files": [p.relative_to(decoded).as_posix() for p in matches]}
        checks.append(check)
        if len(matches) != 1:
            errors.append(f"{descriptor}: expected one class file, found {len(matches)}")
            continue
        path = matches[0]
        raw = path.read_bytes()
        check["sha256"] = hashlib.sha256(raw).hexdigest()
        text = raw.decode("utf-8")
        classes = declarations(text, ".class")
        if len(classes) != 1 or not classes[0] or classes[0][-1] != descriptor:
            errors.append(f"{descriptor}: class descriptor mismatch")
            continue
        if "public" not in classes[0][:-1]:
            errors.append(f"{descriptor}: expected public class")
        methods = declarations(text, ".method")
        fields = declarations(text, ".field")
        if descriptor == VIDEO:
            video_surface_methods = [
                m[-1] for m in methods if m and m[-1].startswith("setSurface(")
            ]
        check["required_methods"] = list(REQUIRED_METHODS.get(descriptor, ()))
        for signature in REQUIRED_METHODS.get(descriptor, ()):
            found = [m for m in methods if m and m[-1] == signature]
            if len(found) != 1:
                errors.append(f"{descriptor}->{signature}: expected one declaration, found {len(found)}")
            elif "public" not in found[0][:-1] or "static" in found[0][:-1]:
                errors.append(f"{descriptor}->{signature}: expected public instance method")
        check["required_fields"] = list(REQUIRED_FIELDS.get(descriptor, ()))
        for signature in REQUIRED_FIELDS.get(descriptor, ()):
            # An enum declaration can optionally end in '= value'.
            declared_fields = [f[:f.index("=")] if "=" in f else f for f in fields]
            found = [f for f in declared_fields if f and f[-1] == signature]
            if len(found) != 1:
                errors.append(f"{descriptor}->{signature}: expected one field, found {len(found)}")
            elif not {"public", "static"}.issubset(found[0][:-1]):
                errors.append(f"{descriptor}->{signature}: expected public static field")

    return {
        "schema_version": 1,
        "mode": "read_only_api_preflight",
        "profile": "historical_stock48ff_api_subset",
        "known_api_subset_matches": not errors,
        "cluster_implemented": False,
        "deployment_ready": False,
        "checks": checks,
        "errors": errors,
        "video_sink_surface_declarations": video_surface_methods,
        "limitations": [
            "Declaration-only inspection does not validate smali assembly, native libraries, APK identity or runtime behavior.",
            "Surface endpoints belong to the existing OEM renderer; their presence does not establish a second-stream route.",
            "No VideoSink.setSurface is required. A method with that name must never be inferred from another class or a string.",
            "The existing LinkCallback TBT path is separate; these checks do not establish CarPlay navigation support.",
        ],
        "remaining_blockers": list(BLOCKERS),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("decoded_dir", type=Path, help="apktool output directory")
    parser.add_argument("--check-contract", action="store_true", help="read-only declaration checks; does not enable patching")
    parser.add_argument("--json", action="store_true", help="emit machine-readable preflight report")
    args = parser.parse_args(argv)
    # Refuse before creating helpers, directories or injecting a hook. A previous
    # run that left a helper is not treated as success and is not altered either.
    if not args.check_contract:
        print(PATCH_DISABLED, file=sys.stderr)
        return 2
    try:
        report = inspect_contract(args.decoded_dir)
    except (OSError, ValueError) as exc:
        print(f"Preflight failed: {exc}", file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        outcome = "MATCH" if report["known_api_subset_matches"] else "MISMATCH"
        print(f"Historical API subset: {outcome}. CLUSTER not implemented; deployment not ready.")
        for error in report["errors"]:
            print(f"  - {error}")
        for blocker in report["remaining_blockers"]:
            print(f"  - {blocker}")
    return 0 if report["known_api_subset_matches"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
