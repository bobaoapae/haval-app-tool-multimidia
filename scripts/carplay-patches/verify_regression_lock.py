#!/usr/bin/env python3
"""
Verify the current CarPlay D3 blackout regression lock.

This is intentionally a static check. It guards the validated state from
2026-05-29 where HVAC, normal display-0 apps, and camera/AVM must not black out
or pull CarPlay from display 3 back to display 0. The protected runtime keeps
the visual app patch and uses a conditional camera service patch: camera stays
stock on display 0, but is ignored when the desired CarPlay target is display 3.
It does not replace the physical camera/AVM test.
"""

from pathlib import Path
import hashlib
import os
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]

EXPECTED_ASSET_MD5 = {
    "app/src/main/assets/carplay_patches/TsCarPlayApp.apk": "9d48c33f49dbeeb020c2fdc7e16bbc53",
    "app/src/main/assets/carplay_patches/TsCarPlayService.apk": "f0269fc640778825843762dcf55a8b83",
}

REQUIRED_TOKENS = {
    "scripts/carplay-patches/patch_logic_app_focus.py": [
        "CP_KEEP_VIDEO_FOCUS_FOR_HVAC_D0_APPS_AND_NORMAL_RETURN",
        "CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE",
        "CP_KEEP_CLUSTER_VIDEO_FOREGROUND_ON_ANY_PAUSE",
        "CP_IGNORE_FINISH_BROADCAST_ON_SECONDARY_DISPLAY",
        "CP_IGNORE_REQUEST_VIDEO_FOCUS_FINISH_ON_SECONDARY_DISPLAY",
        "CP_SURFACE_MATCH_PARENT_FULLSCREEN",
        "CP_SURFACE_FIXED_SIZE_BEFORE_CALLBACK_ON_SECONDARY",
        "CP_SURFACE_SHOW_NATIVE_1904_704_ON_SECONDARY",
        "priorityChanged patched: keep CarPlay video focus for HVAC uiNotification",
        "priorityChanged patched: keep CarPlay video focus for D3 display0 normal app",
        "onPause patched: keep CarPlay video foreground and suppress background",
        "finish receiver patched: ignore finish on secondary display",
        "requestVideoFocus patched: ignore finish on secondary display",
        "clean 1904x704 buffer",
        "persist.haval.carplay.desired_display",
    ],
    "scripts/carplay-patches/patch_logic_service.py": [
        "CARPLAY_HVAC_KEEP_FOREGROUND_PATCH",
        "CARPLAY_HVAC_RELEASE_KEEP_FOREGROUND_PATCH",
        "CARPLAY_CAMERA_KEEP_FOREGROUND_PATCH",
        "CARPLAY_CAMERA_CONDITIONAL_KEEP_FOREGROUND_PATCH",
        "HVAC release patched: ignore unborrow for uiNotification",
        "0x7 (camera/AVM backupCamera) re-routed from :sswitch_0",
        "backCameraStatusChangedTo ON/OFF rerouted to sendMessage(6)",
        "--include-camera",
        "--conditional-camera",
        "persist.haval.carplay.desired_display",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/managers/CarPlayPatchManager.kt": [
        "private const val PATCH_RUNTIME_ENABLED = true",
        "private const val SERVICE_APK = \"TsCarPlayService.apk\"",
        "const val SYSTEM_SERVICE_PATH = \"/vendor/app/TsCarPlayService/TsCarPlayService.apk\"",
        "reloadCarPlayProcessesAfterPatchMount(\"AUTO_MOUNT_AFTER_BOOT\")",
        "CarPlay visual task is active; reloading visual and host so mounted HVAC focus patches are loaded",
        "am stack start 0 -f 0x14000000",
        "Bundled CarPlay HVAC focus patches refreshed; re-applying mounts",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/services/ForegroundService.java": [
        "app_visual_d0_focus_service_conditional_camera_native1904x704_v13",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/managers/DisplayAppLauncher.kt": [
        "restoreCarPlayFromMainDisplayToCluster",
        "recreateMissingCarPlayVisualTaskOnCluster",
        "CARPLAY_CLUSTER_WATCHDOG_NO_TASK",
        "BOOT_USB_CARPLAY_D0_AUTOSTART",
        "A plain explicit start is the stable display-0 clean-start path",
        "Skipping missing CarPlay visual restore because no recent cluster visual was observed",
        "prevents post-reboot Impulse launch loop",
        "Clearing stale desired CarPlay cluster target because no recent cluster visual/session was observed",
        "CARPLAY_CLUSTER_WATCHDOG_START_PENDING_CLUSTER_TARGET",
        "rememberTarget = !preserveClusterTarget",
        "D0 may be used only as a staging display",
        "packageName == App.getContext().packageName",
        "am start -f 0x14000000 -n $CARPLAY_PACKAGE/$escapedActivity",
        "Restoring CarPlay from display 0 stack",
        "ActivityManager reused display-0 CarPlay; defocusing once more and retrying cluster start",
        "CLEAN_DISPLAY0_DUPLICATE",
        "persist.haval.carplay.desired_display",
        "isProjectionUsbStateReady",
        "state == \"CONFIGURED\" || state == \"CONNECTED\"",
        "Skipping CarPlay visual recreate because USB is not configured",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/projectors/InstrumentProjector2.kt": [
        "Camera/AVM/HVAC no longer hide the cluster Presentation",
        "removes the protected Mapa overlay",
        "projectionCardOverlayAllowed",
        "ProjectionCardOverlayPolicy.shouldAllowAfterCardChange",
        "physical_input_or_overlay_session",
        "CLUSTER_INPUT_KEY",
        "no_recent_input",
        "return false",
    ],
    "app/src/main/java/br/com/redesurftank/havalshisuku/projectors/ProjectionCardOverlayPolicy.kt": [
        "ProjectionCardOverlayPolicy",
        "shouldArmFromClusterInput",
        "shouldAllowAfterCardChange",
        "overlayAlreadyAllowed",
    ],
    "cluster-widgets/default/src/core/main.js": [
        "projectionCardOverlayAllowed",
        "function isMainMenuSessionScreen(screen)",
        "return isMainMenuSessionScreen(screen) || screen === 'aircon';",
        "function isRightMenuVisible(cardId, screen)",
        "return projectionCardOverlayActive;",
        "projectionCardOverlayActive: isProjectionCardOverlayActive()",
    ],
    "docs/carplay-cluster-regression-contract.md": [
        "Contrato Unificado de Estado D3",
        "projection_type",
        "Regra 29 - CarPlay nao deve enviar background em onPause",
        "`onPause()` envia `ts.car.carplay.view_state=foreground`, nunca `background`",
        "Regra 31 - D3 ignora FINISH_ACTIVITY de AppList/display 0",
        "CP_IGNORE_FINISH_BROADCAST_ON_SECONDARY_DISPLAY",
        "Regra 32 - D3 ignora requestVideoFocus finish e app normal do D0",
        "CP_IGNORE_REQUEST_VIDEO_FOCUS_FINISH_ON_SECONDARY_DISPLAY",
        "`SurfaceView` nativo deve usar `match_parent`",
        "CP_SURFACE_MATCH_PARENT_FULLSCREEN",
        "CP_SURFACE_FIXED_SIZE_BEFORE_CALLBACK_ON_SECONDARY",
        "CP_SURFACE_SHOW_NATIVE_1904_704_ON_SECONDARY",
        "Regra 33 - Service embarcado usa camera condicional por desired_display",
        "Regra 34 - Mapa permanece visivel durante camera/AVM/HVAC",
        "nao podem esconder a `Presentation` inteira por `windowAlpha=0`",
        "projectionCardOverlayAllowed=true",
        "sem tecla fisica recente",
        "somente apos input real recente do",
        "watchdog pode restaurar o visual no D3",
        "sem `force-stop`, defocando antes o D0",
        "Antes de qualquer adequacao funcional, capturar baseline/logs/screenshot",
    ],
}

ANDROID_AUTO_DIFF_PATHS = [
    "app/src/main/java/br/com/redesurftank/havalshisuku/managers/AndroidAutoPatchManager.kt",
    "app/src/main/assets/aa_patches/",
]

ANDROID_AUTO_JUSTIFICATION_TOKEN = "ANDROID_AUTO_CHANGE_JUSTIFICATION:"

RISKY_DIFF_PATH_PREFIXES = (
    "app/src/main/java/",
    "cluster-widgets/default/src/",
)

RISKY_ADDED_LINE_PATTERNS = [
    (
        "windowAlpha=0 / transparent Presentation bypass",
        re.compile(r"\b(?:windowAlpha|attrs\.alpha|alpha)\s*=\s*0(?:\.0)?f?\b"),
    ),
    (
        "CarPlay VIDEO_FOCUS_CHANGE in new functional code",
        re.compile(r"VIDEO_FOCUS_CHANGE"),
    ),
    (
        "CarPlay REFRESH_RENDER in new functional code",
        re.compile(r"REFRESH_RENDER"),
    ),
    (
        "force-stop com.ts.carplay.app in new functional code",
        re.compile(r"force-stop\s+com\.ts\.carplay\.app"),
    ),
]


def file_md5(path: Path) -> str:
    digest = hashlib.md5()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def git_output(args: list[str]) -> str:
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=ROOT,
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    except OSError:
        return ""
    if result.returncode != 0:
        return ""
    return result.stdout


def changed_files() -> set[str]:
    files: set[str] = set()
    for args in (["diff", "--name-only", "--"], ["diff", "--cached", "--name-only", "--"]):
        for line in git_output(args).splitlines():
            path = line.strip()
            if path:
                files.add(path)
    return files


def justification_text() -> str:
    files = [
        ROOT / ".ai-context/DECISIONS.md",
        ROOT / ".ai-context/HANDOFF.md",
        ROOT / "docs/carplay-cluster-regression-contract.md",
    ]
    return "\n".join(read_text(path) for path in files if path.exists())


def risky_added_lines() -> list[str]:
    findings: list[str] = []
    diff_text = "\n".join(
        [
            git_output(["diff", "--unified=0", "--"]),
            git_output(["diff", "--cached", "--unified=0", "--"]),
        ]
    )
    current_file = ""

    for line in diff_text.splitlines():
        if line.startswith("+++ b/"):
            current_file = line.removeprefix("+++ b/")
            continue
        if not line.startswith("+") or line.startswith("+++"):
            continue
        if not current_file.startswith(RISKY_DIFF_PATH_PREFIXES):
            continue
        content = line[1:]
        if not content.strip() or content.lstrip().startswith("//"):
            continue
        for label, pattern in RISKY_ADDED_LINE_PATTERNS:
            if pattern.search(content):
                findings.append(f"{current_file}: added risky pattern ({label}): {content.strip()}")
    return findings


def main() -> int:
    failures: list[str] = []

    print("Checking CarPlay regression lock...")

    for relative_path, expected in EXPECTED_ASSET_MD5.items():
        path = ROOT / relative_path
        if not path.exists():
            failures.append(f"missing asset: {relative_path}")
            continue
        actual = file_md5(path)
        if actual != expected:
            failures.append(f"{relative_path} md5 {actual} != expected {expected}")
        else:
            print(f"[OK] {relative_path} md5 {actual}")

    for relative_path, tokens in REQUIRED_TOKENS.items():
        path = ROOT / relative_path
        if not path.exists():
            failures.append(f"missing file: {relative_path}")
            continue
        content = read_text(path)
        for token in tokens:
            if token not in content:
                failures.append(f"{relative_path} missing token: {token}")
        if all(token in content for token in tokens):
            print(f"[OK] {relative_path} sentinels present")

    changed = changed_files()
    android_auto_changed = [
        path
        for path in changed
        if any(path == marker or path.startswith(marker) for marker in ANDROID_AUTO_DIFF_PATHS)
    ]
    if android_auto_changed and os.environ.get("CARPLAY_ALLOW_ANDROID_AUTO_DIFF") != "1":
        if ANDROID_AUTO_JUSTIFICATION_TOKEN not in justification_text():
            failures.append(
                "Android Auto files changed without explicit justification token "
                f"{ANDROID_AUTO_JUSTIFICATION_TOKEN} ({', '.join(sorted(android_auto_changed))})"
            )
    elif android_auto_changed:
        print("[OK] Android Auto diff override set by CARPLAY_ALLOW_ANDROID_AUTO_DIFF=1")
    else:
        print("[OK] Android Auto patch files unchanged in current diff")

    risky_lines = risky_added_lines()
    for finding in risky_lines:
        failures.append(finding)
    if not risky_lines:
        print("[OK] No newly added forbidden CarPlay focus/restore/bypass patterns in functional diff")

    display_launcher_path = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku/managers/DisplayAppLauncher.kt"
    if display_launcher_path.exists():
        display_launcher = read_text(display_launcher_path)
        if 'state.contains("CONNECTED")' in display_launcher:
            failures.append(
                "DisplayAppLauncher must not use substring matching for USB CONNECTED; "
                "DISCONNECTED contains CONNECTED and triggers aggressive CarPlay restore"
            )

    if failures:
        print("\nRegression lock FAILED:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("\nCarPlay regression lock OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
