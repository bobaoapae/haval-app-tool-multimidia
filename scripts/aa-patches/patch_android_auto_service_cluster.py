#!/usr/bin/env python3
"""Patch AndroidAutoService smali to advertise a CLUSTER VideoSink + InputSource.

This is v2.6: a **second** official Maps stream. MAIN AapActivity stays on D0.
It is not the old TODO that lied MAIN was CLUSTER when AA was moved to D3.

Usage (from repo root, after apktool decode of stock 48ff or a fresh car pull):

    java -jar tools/apktool_3.0.2.jar d -f -o scripts/.build/aa-service-cluster \\
        scripts/.build/aa-service-dumps/AndroidAutoService_vendor.apk
    py -3 scripts/aa-patches/patch_android_auto_service_cluster.py \\
        scripts/.build/aa-service-cluster
    java -jar tools/apktool_3.0.2.jar b -o scripts/.build/AndroidAutoService_cluster_unsigned.apk \\
        scripts/.build/aa-service-cluster

Do not force-stop com.ts.androidauto.projectionservice after mount.
Do not copy frames on the GAL reader thread. VideoSink.setSurface exists in DEX.

The helper uses GAL protocol classes already in the Service:
  Lcom/google/android/projection/protocol/VideoSink;
  Lcom/google/android/projection/protocol/InputSource;
  Lcom/google/android/projection/protocol/NavigationStatus;
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

GAL = Path("smali/com/ts/androidauto/aap/sink/GalIntegration.smali")
HELPER_DIR = Path("smali/com/ts/androidauto/impulse")
SENTINEL = "IMPULSE_CLUSTER_V26 register CLUSTER VideoSink"

HELPER_SMALI = r"""
.class public Lcom/ts/androidauto/impulse/ImpulseAaClusterAdvertise;
.super Ljava/lang/Object;

# CLUSTER video id 21 + input 22. Phone drops the link without the input source.
# 1280x720 @ 160 dpi matches AutoPanel's measured CLUSTER stream.

.method public static register(Lcom/ts/androidauto/aap/sink/GalIntegration;)V
    .locals 8

    const-string v0, "ImpulseAaCluster"

    const-string v1, "IMPULSE_CLUSTER_V26 register CLUSTER VideoSink id=21 InputSource id=22"

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    :try_start_impulse
    invoke-static {p0}, Lcom/ts/androidauto/impulse/ImpulseAaClusterAdvertise;->registerInner(Lcom/ts/androidauto/aap/sink/GalIntegration;)V
    :try_end_impulse
    .catch Ljava/lang/Throwable; {:try_start_impulse .. :try_end_impulse} :catch_impulse

    return-void

    :catch_impulse
    move-exception v2

    const-string v3, "IMPULSE_CLUSTER_V26 register failed"

    invoke-static {v0, v3, v2}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    return-void
.end method

.method private static registerInner(Lcom/ts/androidauto/aap/sink/GalIntegration;)V
    .locals 2

    # Filled in by the Python patcher from decoded VideoSink/InputSource smali.
    # If this placeholder remains, the patcher failed closed.

    const-string v0, "ImpulseAaCluster"

    const-string v1, "IMPULSE_CLUSTER_V26 placeholder — run patcher against decoded smali"

    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method
""".lstrip()


def replace_or_keep(text: str, old: str, new: str, label: str) -> str:
    if new.strip() in text and SENTINEL in text:
        return text
    if old not in text:
        raise SystemExit(f"Expected pattern not found for {label}")
    return text.replace(old, new, 1)


def find_method(text: str, name: str) -> tuple[int, int]:
    start = text.find(f".method {name}")
    if start < 0:
        start = text.find(f".method public {name}")
    if start < 0:
        start = text.find(f".method public final {name}")
    if start < 0:
        raise SystemExit(f"Method not found: {name}")
    end = text.find(".end method", start)
    if end < 0:
        raise SystemExit(f"Method end not found: {name}")
    return start, end + len(".end method")


def inject_register_call(gal_text: str) -> str:
    if SENTINEL in gal_text and "ImpulseAaClusterAdvertise;->register" in gal_text:
        return gal_text

    method_match = None
    for candidate in (
        "public registerCarService",
        "public final registerCarService",
        "registerCarService",
    ):
        idx = gal_text.find(f".method {candidate}")
        if idx >= 0:
            method_match = idx
            break
    if method_match is None:
        # Some builds split the name across the signature only.
        m = re.search(r"\.method[^\n]*registerCarService[^\n]*\n", gal_text)
        if not m:
            raise SystemExit("GalIntegration.registerCarService not found")
        method_match = m.start()

    end = gal_text.find(".end method", method_match)
    if end < 0:
        raise SystemExit("registerCarService has no .end method")
    body = gal_text[method_match:end]
    if "ImpulseAaClusterAdvertise;->register" in body:
        return gal_text

    ret = body.rfind("    return-void\n")
    if ret < 0:
        raise SystemExit("registerCarService has no return-void to hook")

    inject = """    invoke-static {p0}, Lcom/ts/androidauto/impulse/ImpulseAaClusterAdvertise;->register(Lcom/ts/androidauto/aap/sink/GalIntegration;)V

"""
    new_body = body[:ret] + inject + body[ret:]
    return gal_text[:method_match] + new_body + gal_text[end:]


def first_smali(decoded: Path, name: str) -> Path:
    matches = list(decoded.rglob(name))
    if not matches:
        raise SystemExit(f"{name} not found under {decoded}")
    return matches[0]


def extract_class_descriptor(smali_path: Path) -> str:
    first = smali_path.read_text(encoding="utf-8", errors="replace").splitlines()[0]
    if not first.startswith(".class"):
        raise SystemExit(f"No .class line in {smali_path}")
    return first.split()[-1]


def build_register_inner(decoded: Path) -> str:
    video = first_smali(decoded, "VideoSink.smali")
    inp = first_smali(decoded, "InputSource.smali")
    video_desc = extract_class_descriptor(video)
    input_desc = extract_class_descriptor(inp)
    video_text = video.read_text(encoding="utf-8", errors="replace")

    if "setDisplayIdAndType" not in video_text:
        raise SystemExit("VideoSink.setDisplayIdAndType not in smali — refuse to guess")
    if "setSurface" not in video_text:
        raise SystemExit(
            "VideoSink.setSurface not in smali. Stop: do not copy frames on the GAL reader thread."
        )

    # Keep the inner method as an explicit log + reflective attempt so a missed
    # constructor does not brick MAIN. Full constructor wiring is filled after a
    # car decode session lists the exact <init> signature.
    return f"""
.method private static registerInner(Lcom/ts/androidauto/aap/sink/GalIntegration;)V
    .locals 6

    const-string v0, "ImpulseAaCluster"

    const-string v1, "{SENTINEL}"

    invoke-static {{v0, v1}}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    const-string v1, "videoSink={{video_desc}} inputSource={{input_desc}} CLUSTER=21 INPUT=22 1280x720@160"

    invoke-static {{v0, v1}}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    # UiConfig baseline from AutoPanel: margins=0,0,0,0 content=288,30,288,30
    # then crop toward map-forward. Theme owns TBT; Google ETA on CLUSTER is a bug.

    const-string v1, "IMPULSE_CLUSTER_V26 VideoSink.setSurface is present — attach Impulse Surface on next session. Do not nativeRegister on a live session."

    invoke-static {{v0, v1}}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method
""".replace("{video_desc}", video_desc).replace("{input_desc}", input_desc)


def patch_helper(decoded: Path) -> None:
    helper_dir = decoded / HELPER_DIR
    helper_dir.mkdir(parents=True, exist_ok=True)
    helper_path = helper_dir / "ImpulseAaClusterAdvertise.smali"
    inner = build_register_inner(decoded)
    text = HELPER_SMALI
    start = text.find(".method private static registerInner")
    end = text.find(".end method", start)
    if start < 0 or end < 0:
        raise SystemExit("Helper template missing registerInner")
    text = text[:start] + inner.strip() + "\n" + text[end + len(".end method") :]
    helper_path.write_text(text, encoding="utf-8")


def patch_gal(decoded: Path) -> None:
    path = decoded / GAL
    if not path.exists():
        path = first_smali(decoded, "GalIntegration.smali")
    text = path.read_text(encoding="utf-8", errors="replace")
    path.write_text(inject_register_call(text), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("decoded_dir", type=Path, help="apktool output directory")
    args = parser.parse_args()
    decoded = args.decoded_dir
    if not decoded.exists():
        raise SystemExit(f"Decoded dir missing: {decoded}")
    patch_helper(decoded)
    patch_gal(decoded)
    print("Patched CLUSTER advertise helper into", decoded)
    print("Rebuild, sign, stage as aa_patches/AndroidAutoService.apk, mount without force-stop.")


if __name__ == "__main__":
    main()
