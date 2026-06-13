"""
V19 Focus-Bypass Patch for AndroidAutoApp
==========================================
Applies Smali-level patches to the disassembled AndroidAutoApp to prevent
the system from stealing focus from Android Auto.

IMPORTANT: The stock APK in stock/AndroidAutoApp_stock.apk is partially
pre-patched from previous work (hideSurface, releaseSurface, onWindowFocusChanged
and finish() are already modified). This script handles BOTH truly-stock and
partially-patched APKs by using regex patterns that match either state.

Usage:
    1. Disassemble the stock APK first:
       java -jar tools/apktool_3.0.2.jar d -f -o build_v19/app scripts/aa-patches/stock/AndroidAutoApp_stock.apk
    2. Run this script:
       python scripts/aa-patches/patch_v19_focus.py
    3. Reassemble:
       java -jar tools/apktool_3.0.2.jar b -o build_v19/AndroidAutoApp_v19_unsigned.apk build_v19/app

Patches applied (all in AapActivity.smali):
    1. onPause: force mIsOnPause = false after super.onPause()
    2. onWindowFocusChanged: force hasFocus = true (const/4 p1, 0x1)
    3. hideSurface: NOP (return-void)
    4. releaseSurface: NOP (return-void)
    5. onPauseSetVisibility: NOP (return-void)
    6. finish() calls: commented out (blocked)

Build date: 2026-05-16
MD5 of signed APK: 1EE968990299EB9AD9B963B31D119154
"""

import os
import re
import sys

# --- Configuration ---
BUILD_DIR = "build_v19/app"
SMALI_BASE = f"{BUILD_DIR}/smali/com/ts/androidauto/app/display"
AAP_ACTIVITY = f"{SMALI_BASE}/AapActivity.smali"


def read_file(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write_file(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def patch_regex(content, regex, replacement, description):
    """Apply a regex-based patch. Returns (new_content, was_applied)."""
    new_content = re.sub(regex, replacement, content, flags=re.DOTALL)
    if new_content != content:
        print(f"  [OK]   {description}")
        return new_content, True
    else:
        print(f"  [SKIP] {description} (already applied or no match)")
        return content, False


def patch_direct(content, target, replacement, description):
    """Apply a direct string replacement. Returns (new_content, was_applied)."""
    if target in content:
        new_content = content.replace(target, replacement)
        print(f"  [OK]   {description}")
        return new_content, True
    else:
        print(f"  [SKIP] {description} (already applied or no match)")
        return content, False


def main():
    print("=" * 60)
    print("  V19 Focus-Bypass Patch for AndroidAutoApp")
    print("=" * 60)
    print()

    if not os.path.exists(AAP_ACTIVITY):
        print(f"ERROR: AapActivity.smali not found at {AAP_ACTIVITY}")
        print(f"Did you disassemble the stock APK first?")
        print(f"  java -jar tools/apktool_3.0.2.jar d -f -o {BUILD_DIR} scripts/aa-patches/stock/AndroidAutoApp_stock.apk")
        sys.exit(1)

    content = read_file(AAP_ACTIVITY)
    patches_applied = 0

    # --- Patch 1: onPause - force mIsOnPause = false ---
    # The stock onPause sets mIsOnPause=true at the end. We inject mIsOnPause=false
    # right after super.onPause() so the flag never sticks.
    # Pattern works for both .registers and .locals declarations.
    print("[1/6] Patching onPause...")
    content, ok = patch_regex(
        content,
        r'(\.method protected onPause\(\)V.*?invoke-super \{p0\}, Landroid/app/Activity;->onPause\(\)V)',
        r'\1\n\n    const/4 v0, 0x0\n\n    iput-boolean v0, p0, Lcom/ts/androidauto/app/display/AapActivity;->mIsOnPause:Z\n',
        "onPause: force mIsOnPause = false"
    )
    if ok:
        patches_applied += 1

    # --- Patch 2: onWindowFocusChanged - force hasFocus = true ---
    # The stock method uses .locals 3 (not .registers 5 as in some older versions).
    # We inject const/4 p1, 0x1 right after the .param line.
    print("[2/6] Patching onWindowFocusChanged...")
    content, ok = patch_regex(
        content,
        r'(\.method public onWindowFocusChanged\(Z\)V\s*\.locals \d+\s*\.param p1, "hasFocus"\s*# Z)\s*\n(\s*\.line)',
        r'\1\n\n    const/4 p1, 0x1\n\n    \2',
        "onWindowFocusChanged: force hasFocus = true"
    )
    if ok:
        patches_applied += 1

    # --- Patch 3: hideSurface - NOP ---
    # Replace the full method body with return-void.
    # Handles both stock (full body) and already-patched (just return-void).
    print("[3/6] Neutralizing hideSurface()...")
    content, ok = patch_regex(
        content,
        r'(\.method private hideSurface\(\)V\s*\.locals \d+)\s*\n(.*?)\.end method',
        r'\1\n\n    return-void\n.end method',
        "hideSurface: converted to NOP"
    )
    if ok:
        patches_applied += 1

    # --- Patch 4: releaseSurface - NOP ---
    print("[4/6] Neutralizing releaseSurface()...")
    content, ok = patch_regex(
        content,
        r'(\.method private releaseSurface\(\)V\s*\.locals \d+)\s*\n(.*?)\.end method',
        r'\1\n\n    return-void\n.end method',
        "releaseSurface: converted to NOP"
    )
    if ok:
        patches_applied += 1

    # --- Patch 5: onPauseSetVisibility - NOP ---
    print("[5/6] Neutralizing onPauseSetVisibility()...")
    content, ok = patch_regex(
        content,
        r'(\.method private onPauseSetVisibility\(\)V\s*\.locals \d+)\s*\n(.*?)\.end method',
        r'\1\n\n    return-void\n.end method',
        "onPauseSetVisibility: converted to NOP"
    )
    if ok:
        patches_applied += 1

    # --- Patch 6: Block finish() calls ---
    print("[6/6] Blocking finish() self-termination...")
    content, ok = patch_direct(
        content,
        'invoke-virtual {p0}, Lcom/ts/androidauto/app/display/AapActivity;->finish()V',
        '# invoke-virtual {p0}, Lcom/ts/androidauto/app/display/AapActivity;->finish()V # BLOCKED',
        "finish() calls: blocked"
    )
    if ok:
        patches_applied += 1

    # Write back
    write_file(AAP_ACTIVITY, content)

    print()
    print(f"Done. {patches_applied} new patch(es) applied to AapActivity.smali")
    print()

    if patches_applied == 0:
        print("INFO: All patches were already present. The APK is ready to reassemble.")
    
    print("Next steps:")
    print(f"  1. Reassemble: java -jar tools/apktool_3.0.2.jar b -o build_v19/AndroidAutoApp_v19_unsigned.apk {BUILD_DIR}")
    print(f'  2. Align:      & "C:\\Users\\vanes\\AppData\\Local\\Android\\Sdk\\build-tools\\36.1.0\\zipalign.exe" -f 4 build_v19\\AndroidAutoApp_v19_unsigned.apk build_v19\\AndroidAutoApp_v19_aligned.apk')
    print(f'  3. Sign:       & "C:\\Users\\vanes\\AppData\\Local\\Android\\Sdk\\build-tools\\36.1.0\\apksigner.bat" sign --ks C:\\Users\\vanes\\.android\\debug.keystore --ks-pass pass:android --out build_v19\\AndroidAutoApp_v19_signed.apk build_v19\\AndroidAutoApp_v19_aligned.apk')


if __name__ == "__main__":
    main()
