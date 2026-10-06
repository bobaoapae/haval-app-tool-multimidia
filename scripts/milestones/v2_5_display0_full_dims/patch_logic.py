"""
v2.1 Patch — AndroidAutoApp: Focus Bypass + Live Resize on Config Change
========================================================================

Versioning scheme (x.y)
    x = major aim of the patching effort
    y = iteration within that aim

    v1.x  Focus-lost-patch effort. Goal: AA video stays alive when the
          user interacts with other apps or the system steals focus.
          v1.19  First fully-functional milestone — see
                 scripts/milestones/v19_focus_bypass/ (historical folder
                 name kept; conceptually this is v1.19).

    v2.x  Dynamic-resize effort. Goal: AA video reflows to the active
          display's resolution at every layout pass AND when the activity
          is moved between displays mid-session.
          v2.0  Earlier version of this script — patched setDisplayParams
                to read DisplayMetrics at the start of every call. Worked
                for the initial layout but did NOT react to display moves
                (no configChanges declared, no onConfigurationChanged
                override; the activity just kept its first-launch size).
          v2.1  This script (what you're reading).

This script applies *all* v1 patches plus *all* v2 patches; v2 strictly
supersedes v1.

What v2.1 adds on top of v2.0:
    A. AndroidManifest.xml configChanges expanded to include screenSize,
       smallestScreenSize, screenLayout, orientation, density, navigation,
       keyboard, keyboardHidden, locale, fontScale. This prevents Android
       from destroying/recreating the activity on a stack resize or display
       move; instead onConfigurationChanged() fires and we keep the live
       projection session intact.

    B. A new onConfigurationChanged(Configuration) method is injected into
       AapActivity.smali that re-invokes the (v2-patched) setDisplayParams()
       so the layout is rebuilt against the new display metrics.

    C. The v2 setDisplayParams resize injection now uses getResources()
       .getDisplayMetrics() (simpler, less deprecated than the WindowManager
       chain) and its regex matches the stock APK's `.locals 5` directive
       instead of the historical `.registers 5`. The injection is idempotent
       and safe to re-run.

Patch layers applied by this script (smali targets are AapActivity.smali
unless noted):
    L1  v1  onPause                  force mIsOnPause = false
    L2  v1  onWindowFocusChanged     force hasFocus = true
    L3  v1  hideSurface              NOP
    L4  v1  releaseSurface           NOP
    L5  v1  onPauseSetVisibility     NOP
    L6  v1  finish()                 comment-block all calls
    L7  v2  setDisplayParams         prepend getResources/getDisplayMetrics
    L8  v2  onConfigurationChanged   inject override -> setDisplayParams
    L9  v2  AndroidManifest.xml      expand configChanges flag list

Idempotency:
    Each patch leaves a sentinel comment in the patched file. All branches
    check for their sentinel before applying, so a second invocation
    against an already-patched tree is a no-op. The script is safe to
    chain into a build pipeline. Sentinels are intentionally kept under
    legacy "V2/V3/V4" names (V2_ONPAUSE_SENTINEL, V2_FINISH_SENTINEL,
    V3_SENTINEL, V4_SENTINEL) so APKs patched by earlier versions of this
    script remain recognized — renaming them would silently re-apply
    already-applied patches.

Smali register safety (setDisplayParams):
    Stock declares `.locals 5` (= 6 registers including p0). The v2
    injection uses v0/v1/v2 and exits before the original body reads
    them, so no register conflict. If a future stock declares fewer
    locals, `apply_v2_setdisplayparams_patch` bumps the count to 5
    automatically. The injection also handles legacy `.registers N`
    declarations.

Usage:
    1. Disassemble the stock APK:
       java -jar tools/apktool_3.0.2.jar d -f -o build_v19/app \\
           scripts/aa-patches/stock/AndroidAutoApp_stock.apk
    2. Run this script:
       python scripts/aa-patches/patch_logic.py
    3. Reassemble + align + sign as described in scripts/aa-patches/README.md.
    4. Deploy via either:
       - scripts/milestones/v19_focus_bypass/deploy_to_car.py (direct, fast
         iteration; the deploy script itself is reusable across milestones)
       - or copy the signed APK into app/src/main/assets/aa_patches/ and
         rebuild + deploy the Haval app (production flow; manager handles
         the mount on the user's car — see AndroidAutoPatchManager.kt).

Build dir is build_v19/app (folder name kept from v1.19 milestone tooling
for continuity with the existing scripts).
"""

import os
import re
import sys

BUILD_DIR = "build_v19/app"
SMALI_BASE = f"{BUILD_DIR}/smali/com/ts/androidauto/app/display"
AAP_ACTIVITY = f"{SMALI_BASE}/AapActivity.smali"
MANIFEST = f"{BUILD_DIR}/AndroidManifest.xml"

# Extra configChanges flags to OR into the existing list. Anything that can
# change as the app is moved between physical displays / resized in-place.
EXTRA_CONFIG_CHANGES = [
    "screenSize",
    "smallestScreenSize",
    "screenLayout",
    "orientation",
    "density",
    "navigation",
    "keyboard",
    "keyboardHidden",
    "locale",
    "fontScale",
]


def read_file(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write_file(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def patch_regex(content, regex, replacement, description, flags=re.DOTALL):
    new = re.sub(regex, replacement, content, flags=flags)
    if new != content:
        print(f"  [OK]   {description}")
        return new, True
    print(f"  [SKIP] {description} (already applied or no match)")
    return content, False


def patch_direct(content, target, replacement, description):
    if target in content:
        print(f"  [OK]   {description}")
        return content.replace(target, replacement), True
    print(f"  [SKIP] {description} (already applied or no match)")
    return content, False


# ---------------------------------------------------------------------------
# v1.x layers — focus / lifecycle stability
# ---------------------------------------------------------------------------

# Sentinel strings kept under their legacy "V2_*" names on purpose: they
# get embedded in patched smali files in the wild, and renaming them here
# would silently re-apply patches that are already in place.
V2_ONPAUSE_SENTINEL = "# V2_ONPAUSE_INJECTION"
V2_FINISH_SENTINEL = "# V2_FINISH_BLOCKED"


def apply_v1_focus_patches(content):
    print("[v1.x] Focus / lifecycle stability:")
    count = 0

    # 1. onPause: force mIsOnPause = false right after super.onPause()
    if V2_ONPAUSE_SENTINEL in content:
        print("  [SKIP] onPause: force mIsOnPause = false (already applied)")
    else:
        content, ok = patch_regex(
            content,
            r"(\.method protected onPause\(\)V.*?invoke-super \{p0\}, Landroid/app/Activity;->onPause\(\)V)",
            r"\1\n\n    " + V2_ONPAUSE_SENTINEL + "\n    const/4 v0, 0x0\n\n"
            r"    iput-boolean v0, p0, "
            r"Lcom/ts/androidauto/app/display/AapActivity;->mIsOnPause:Z\n",
            "onPause: force mIsOnPause = false",
        )
        count += int(ok)

    # 2. onWindowFocusChanged: force hasFocus = true
    content, ok = patch_regex(
        content,
        r'(\.method public onWindowFocusChanged\(Z\)V\s*\.locals \d+\s*'
        r'\.param p1, "hasFocus"\s*# Z)\s*\n(\s*\.line)',
        r"\1\n\n    const/4 p1, 0x1\n\n    \2",
        "onWindowFocusChanged: force hasFocus = true",
    )
    count += int(ok)

    # 3. hideSurface: NOP
    content, ok = patch_regex(
        content,
        r"(\.method private hideSurface\(\)V\s*\.locals \d+)\s*\n(.*?)\.end method",
        r"\1\n\n    return-void\n.end method",
        "hideSurface: NOP",
    )
    count += int(ok)

    # 4. releaseSurface: NOP
    content, ok = patch_regex(
        content,
        r"(\.method private releaseSurface\(\)V\s*\.locals \d+)\s*\n(.*?)\.end method",
        r"\1\n\n    return-void\n.end method",
        "releaseSurface: NOP",
    )
    count += int(ok)

    # 5. onPauseSetVisibility: NOP
    content, ok = patch_regex(
        content,
        r"(\.method private onPauseSetVisibility\(\)V\s*\.locals \d+)\s*\n(.*?)\.end method",
        r"\1\n\n    return-void\n.end method",
        "onPauseSetVisibility: NOP",
    )
    count += int(ok)

    # 6. Block finish() self-termination
    if V2_FINISH_SENTINEL in content:
        print("  [SKIP] finish() calls: blocked (already applied)")
    else:
        content, ok = patch_direct(
            content,
            "invoke-virtual {p0}, Lcom/ts/androidauto/app/display/AapActivity;->finish()V",
            f"{V2_FINISH_SENTINEL} invoke-virtual {{p0}}, "
            "Lcom/ts/androidauto/app/display/AapActivity;->finish()V",
            "finish() calls: blocked",
        )
        count += int(ok)

    return content, count


# ---------------------------------------------------------------------------
# v2.x layers — dynamic resize
# ---------------------------------------------------------------------------
# Two pieces:
#   (a) setDisplayParams prologue that reads current display metrics
#       — introduced in v2.0, still required in v2.1.
#   (b) onConfigurationChanged override + manifest configChanges expansion
#       — new in v2.1, this is what makes (a) re-fire on display moves.
# ---------------------------------------------------------------------------

# Sentinel comment so we can detect a prior injection and skip cleanly.
# Suffixed with _V2 because the injection body changed (window bounds instead
# of display metrics). An APK patched by the older script that wrote the
# original V3_RESIZE_INJECTION sentinel will fail this check and have the
# new code applied. Since the patching pipeline always starts from a fresh
# disassemble of the stock APK, the old code is never present in practice;
# the suffix only matters for ad-hoc re-runs on a stale build_v19/ tree.
V3_SENTINEL = "# V3_RESIZE_INJECTION_V2"

# What this reads:
#   Configuration.windowConfiguration.getBounds() returns the activity's
#   actual WINDOW bounds (e.g. 1920x596 after `am stack resize 7 0 62 1920
#   658`), unlike DisplayMetrics.widthPixels/heightPixels which always
#   returns the *physical display* size (1920x720 on Display 3). The
#   stock code copies mVehicleInfo's hardcoded 1920x720 into the layout
#   params; if we don't override with the true window size, the SurfaceView
#   spills out of the stack-resized window and looks "not resized".
#
# Hidden-API access:
#   Configuration.windowConfiguration is `public` on API 28+. The owning
#   class WindowConfiguration is @hide, but AA is a system/vendor app
#   (`flags=[ SYSTEM ... ]` in PackageManager) and on Android 9 hidden-API
#   restrictions don't apply to SYSTEM-flagged apps, so iget-object on
#   the public field and invoke-virtual on getBounds() both succeed.
#
# Diagnostics:
#   Logs entry and the bounds it reads with Log.e under tag "V21_PATCH",
#   which is always visible in logcat (LogUtil.debug used elsewhere in
#   this class is routed somewhere we can't see — likely a file sink).
V3_RESIZE_CODE = f"""
    {V3_SENTINEL}
    const-string v0, "V21_PATCH"

    const-string v1, "setDisplayParams: entering window-bounds prologue"

    invoke-static {{v0, v1}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    invoke-virtual {{p0}}, Lcom/ts/androidauto/app/display/AapActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    invoke-virtual {{v0}}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    iget-object v0, v0, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {{v0}}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object v0

    invoke-virtual {{v0}}, Landroid/graphics/Rect;->width()I

    move-result v1

    if-lez v1, :v3_skip_resize

    invoke-virtual {{v0}}, Landroid/graphics/Rect;->height()I

    move-result v2

    if-lez v2, :v3_skip_resize

    invoke-virtual {{p0}}, Lcom/ts/androidauto/app/display/AapActivity;->getDisplay()Landroid/view/Display;

    move-result-object v0

    if-eqz v0, :v3_skip_display0_override

    invoke-virtual {{v0}}, Landroid/view/Display;->getDisplayId()I

    move-result v3

    if-nez v3, :v3_skip_display0_override

    new-instance v3, Landroid/util/DisplayMetrics;

    invoke-direct {{v3}}, Landroid/util/DisplayMetrics;-><init>()V

    invoke-virtual {{v0, v3}}, Landroid/view/Display;->getRealMetrics(Landroid/util/DisplayMetrics;)V

    iget v1, v3, Landroid/util/DisplayMetrics;->widthPixels:I

    iget v2, v3, Landroid/util/DisplayMetrics;->heightPixels:I

    :v3_skip_display0_override
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {{v3}}, Ljava/lang/StringBuilder;-><init>()V

    const-string v0, "setDisplayParams: effective bounds "

    invoke-virtual {{v3, v0}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{v3, v1}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v0, "x"

    invoke-virtual {{v3, v0}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{v3, v2}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {{v3}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    const-string v0, "V21_PATCH"

    invoke-static {{v0, v3}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    iput v1, p0, Lcom/ts/androidauto/app/display/AapActivity;->mAapDisplayAreaWidth:I

    iput v2, p0, Lcom/ts/androidauto/app/display/AapActivity;->mAapDisplayAreaHeight:I

    const/4 v1, 0x0

    iput v1, p0, Lcom/ts/androidauto/app/display/AapActivity;->mAapDisplayAreaMarginLeft:I

    iput v1, p0, Lcom/ts/androidauto/app/display/AapActivity;->mAapDisplayAreaMarginTop:I

    :v3_skip_resize
"""


def apply_v2_setdisplayparams_patch(content):
    print("[v2.0] setDisplayParams - dynamic display metrics:")
    if V3_SENTINEL in content:
        print("  [SKIP] setDisplayParams: dynamic metrics already injected")
        return content, 0

    # Match `.locals N` (current stock) or `.registers N` (legacy stock).
    # The widthPixels register read needs an extra local for the height
    # guard so we bump locals to at least 5 if necessary.
    pattern = re.compile(
        r"(\.method private setDisplayParams\(\)V\s*\n\s*\.locals )(\d+)",
        re.MULTILINE,
    )
    legacy_pattern = re.compile(
        r"(\.method private setDisplayParams\(\)V\s*\n\s*\.registers )(\d+)",
        re.MULTILINE,
    )

    def bump_and_inject(match, kind):
        prefix, count = match.group(1), int(match.group(2))
        needed = 5 if kind == "locals" else 6  # +1 for v4 used by body
        new_count = max(count, needed)
        return f"{prefix}{new_count}{V3_RESIZE_CODE}"

    new_content, n = pattern.subn(lambda m: bump_and_inject(m, "locals"), content)
    if n == 0:
        new_content, n = legacy_pattern.subn(
            lambda m: bump_and_inject(m, "registers"), content
        )

    if n == 0:
        print("  [WARN] setDisplayParams: could not find method signature")
        return content, 0

    print("  [OK]   setDisplayParams: inject getResources().getDisplayMetrics()")
    return new_content, 1


# ---------------------------------------------------------------------------
# v2.1 updateDisplayParams — passthrough to setDisplayParams
# ---------------------------------------------------------------------------
# Why this exists:
#   AapActivity has TWO separate "apply layout" methods:
#     - setDisplayParams()                  reads mAapDisplayAreaWidth/Height
#                                           (which our v2.0 prologue fills
#                                           with the live window bounds)
#     - updateDisplayParams(AapVideoConfig) reads p1.mAapSurfaceViewWidth/Height
#                                           (a hard-coded per-mode config
#                                           from mVehicleInfo — e.g. 1920x1080
#                                           for the 1080p AAP mode)
#
#   updateDisplayParams is dispatched ASYNC from a handler message posted in
#   onResume (one of three modes: 480p/720p/1080p). It races with our
#   setDisplayParams call in init() and *wins* because it fires later,
#   resetting the SurfaceView to 1920x1080 with negative-Y position so the
#   visible region is just the middle stripe — looks like "no resize" on a
#   smaller cluster window.
#
#   We collapse updateDisplayParams to a single invoke-direct of
#   setDisplayParams, which re-reads windowConfiguration.getBounds() and
#   sizes the SurfaceView to the actual window. The phone keeps encoding
#   at 1920x1080; the surface buffer scales down to fit (slight vertical
#   compression on the cluster but the *whole* video is visible). A proper
#   AAP-protocol renegotiation would require patching the Service APK,
#   which is out of v2.1's scope.

V2_1_UPDATEDP_SENTINEL = "# V21_UPDATEDP_PASSTHROUGH"

# New body for updateDisplayParams. The original takes an AapVideoConfig
# parameter and writes the per-mode SurfaceView dimensions; we ignore the
# param (still need to declare it via .param so the signature matches) and
# just delegate to setDisplayParams, which now reads live window bounds.
V2_1_UPDATEDP_NEW_BODY = f"""\\1    .locals 2
\\2
    {V2_1_UPDATEDP_SENTINEL}
    const-string v0, "V21_PATCH"

    const-string v1, "updateDisplayParams: redirecting to setDisplayParams"

    invoke-static {{v0, v1}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    invoke-direct {{p0}}, Lcom/ts/androidauto/app/display/AapActivity;->setDisplayParams()V

    return-void
.end method"""


def apply_v2_1_updatedp_patch(content):
    print("[v2.1] updateDisplayParams: passthrough to setDisplayParams:")
    if V2_1_UPDATEDP_SENTINEL in content:
        print("  [SKIP] updateDisplayParams: already passthrough")
        return content, 0

    pattern = re.compile(
        r"(\.method private updateDisplayParams\(Lcom/ts/androidauto/sdk/aidl/data/IfVehicleInfo\$AapVideoConfig;\)V\s*\n)"
        r"\s*\.locals \d+\s*\n"
        r"(\s*\.param p1[^\n]*\n)"
        r"(.*?)\.end method",
        re.DOTALL,
    )

    new_content, n = pattern.subn(V2_1_UPDATEDP_NEW_BODY, content)
    if n == 0:
        print("  [WARN] updateDisplayParams: signature not found")
        return content, 0

    print("  [OK]   updateDisplayParams: replaced body with setDisplayParams() call")
    return new_content, 1


# ---------------------------------------------------------------------------
# v2.2 setDisplayParams — crop 1080p buffer's padding bars
# ---------------------------------------------------------------------------
# Why this exists:
#   VehicleInfoLoader.initAapVideoConfig1080p declares the AAP "1080p" mode
#   as a 1920x1080 buffer where the *logical* content is only the inner
#   1920x720 (rows 180..900) — top and bottom 180 rows are encoder padding.
#   Stock AA crops those bars by using a 1080-tall SurfaceView inside a
#   720-tall LinearLayout with the parent ScrollView scrolled by 180.
#
#   Our v2.0 patch sized the SurfaceView to match the window (e.g.
#   1920x596 on cluster), which makes SurfaceFlinger scale the *whole*
#   1080-row buffer including the padding into the visible area. Visible
#   result: padding bars become smaller bars but still visible. User sees
#   ~99px black on top + ~99px black on bottom of the cluster zone.
#
#   We fix it by repeating the stock crop trick at the new window size:
#   make SurfaceView 1.5x the window height, then negative-top-margin
#   it by -windowHeight/4 so the FrameLayout parent clips out the padding
#   bars. Math generalizes to any window size:
#       SurfaceView height = windowHeight * 3 / 2
#       SurfaceView topMargin = -windowHeight / 4
#   For cluster (596): 894 / -149  -> visible 596 = inner content area.
#   For Display 0 (720): 1080 / -180 -> exactly the stock 1080p layout.

V2_2_CROP_SENTINEL = "# V22_CROP_PADDING_BARS"

V2_2_CROP_CODE = f"""
    {V2_2_CROP_SENTINEL}
    const-string v0, "V21_PATCH"

    const-string v2, "v2.2 crop: oversize SurfaceView + negative topMargin to clip 1080p padding bars"

    invoke-static {{v0, v2}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    iget-object v0, p0, Lcom/ts/androidauto/app/display/AapActivity;->mSurfaceProjection:Landroid/view/SurfaceView;

    invoke-virtual {{v0}}, Landroid/view/SurfaceView;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v0

    check-cast v0, Landroid/widget/FrameLayout$LayoutParams;

    iget v1, p0, Lcom/ts/androidauto/app/display/AapActivity;->mAapDisplayAreaHeight:I

    mul-int/lit8 v2, v1, 0x3

    div-int/lit8 v2, v2, 0x2

    iput v2, v0, Landroid/widget/FrameLayout$LayoutParams;->height:I

    div-int/lit8 v1, v1, 0x4

    neg-int v1, v1

    iput v1, v0, Landroid/view/ViewGroup$MarginLayoutParams;->topMargin:I

    iget-object v1, p0, Lcom/ts/androidauto/app/display/AapActivity;->mSurfaceProjection:Landroid/view/SurfaceView;

    invoke-virtual {{v1, v0}}, Landroid/view/SurfaceView;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    return-void
"""


def apply_v2_2_crop_patch(content):
    print("[v2.2] setDisplayParams: crop 1080p padding bars:")
    if V2_2_CROP_SENTINEL in content:
        print("  [SKIP] crop epilogue: already injected")
        return content, 0

    # Replace the success-path return-void at the end of setDisplayParams.
    # Uniquely identified by the preceding mCoverView.setLayoutParams() call.
    pattern = re.compile(
        r"(iget-object v2, p0, Lcom/ts/androidauto/app/display/AapActivity;->mCoverView:Landroid/widget/ImageView;\s*\n"
        r"\s*\n"
        r"\s*invoke-virtual \{v2, v1\}, Landroid/widget/ImageView;->setLayoutParams\(Landroid/view/ViewGroup\$LayoutParams;\)V\s*\n"
        r"\s*\n"
        r"\s*\.line \d+\s*\n)"
        r"\s*return-void\s*\n"
        r"(\.end method)",
        re.MULTILINE,
    )

    replacement = r"\1" + V2_2_CROP_CODE + r"\2"
    new_content, n = pattern.subn(replacement, content)
    if n == 0:
        print("  [WARN] setDisplayParams success-path return-void not found")
        return content, 0

    print("  [OK]   setDisplayParams: appended crop epilogue before return-void")
    return new_content, 1


# ---------------------------------------------------------------------------
# v2.4 setDisplayParams — X-crop AAP sidebar when on a non-main display
# ---------------------------------------------------------------------------
# Why this exists:
#   On the main display (id 0), Google's Android Auto app on the phone
#   renders a left-edge "rail" with app icons (Maps / Music / Messages /
#   etc.). On the cluster display (id 1 or 3) that sidebar wastes precious
#   horizontal space — the user wants a clean video area there.
#
#   The sidebar is part of what the phone renders into the 1920x1080 buffer
#   (it's not a separate Android view we can hide). The correct long-term
#   fix is to advertise the display as DISPLAY_TYPE_CLUSTER via the AAP
#   protocol so the phone renders cluster-style UI from the start — that
#   lives in the Service APK and is planned for v2.5.
#
#   Quick fix (this patch): on a non-main display, extend the trick from
#   v2.2 to the X-axis as well — oversize SurfaceView width and apply a
#   negative leftMargin so the FrameLayout parent clips the leftmost band
#   of the buffer (where the sidebar lives).
#
#   Sidebar geometry assumption: Google AA "app rail" is 96dp wide. The
#   head unit reports 213dpi to the phone (mAapResolutionReportDpi in
#   VehicleInfoLoader.initAapVideoConfig1080p), so 96dp ≈ 128px in the
#   1920-wide source buffer. That's ~1/15 of the width. We use 1/15 to
#   keep smali integer-math clean (×16 / 15 and / 15).
#
#   Math:
#       SurfaceView width      = windowWidth × 16 / 15
#       SurfaceView leftMargin = −windowWidth / 15
#   For cluster (1920 wide): width = 2048, leftMargin = −128.
#   For Display 0: skipped (sidebar stays visible — user wants it there).

V2_4_XCROP_SENTINEL = "# V24_XCROP_SIDEBAR"

V2_4_XCROP_CODE = f"""

    {V2_4_XCROP_SENTINEL}
    const-string v0, "V21_PATCH"

    const-string v2, "v2.4 xcrop: checking displayId for sidebar X-crop"

    invoke-static {{v0, v2}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    const/4 v2, 0x0

    invoke-virtual {{p0}}, Lcom/ts/androidauto/app/display/AapActivity;->getDisplay()Landroid/view/Display;

    move-result-object v0

    if-eqz v0, :v24_skip_id_read

    invoke-virtual {{v0}}, Landroid/view/Display;->getDisplayId()I

    move-result v2

    :v24_skip_id_read
    iget-object v0, p0, Lcom/ts/androidauto/app/display/AapActivity;->mSurfaceProjection:Landroid/view/SurfaceView;

    invoke-virtual {{v0}}, Landroid/view/SurfaceView;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v0

    check-cast v0, Landroid/widget/FrameLayout$LayoutParams;

    if-eqz v2, :v24_reset_to_default

    iget v1, p0, Lcom/ts/androidauto/app/display/AapActivity;->mAapDisplayAreaWidth:I

    mul-int/lit8 v2, v1, 0x10

    div-int/lit8 v2, v2, 0xf

    iput v2, v0, Landroid/widget/FrameLayout$LayoutParams;->width:I

    div-int/lit8 v1, v1, 0xf

    neg-int v1, v1

    iput v1, v0, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I

    const/16 v1, 0x33

    iput v1, v0, Landroid/widget/FrameLayout$LayoutParams;->gravity:I

    goto :v24_apply

    :v24_reset_to_default
    iget v1, p0, Lcom/ts/androidauto/app/display/AapActivity;->mAapDisplayAreaWidth:I

    iput v1, v0, Landroid/widget/FrameLayout$LayoutParams;->width:I

    const/4 v1, 0x0

    iput v1, v0, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I

    const/4 v1, 0x1

    iput v1, v0, Landroid/widget/FrameLayout$LayoutParams;->gravity:I

    :v24_apply
    iget-object v1, p0, Lcom/ts/androidauto/app/display/AapActivity;->mSurfaceProjection:Landroid/view/SurfaceView;

    invoke-virtual {{v1, v0}}, Landroid/view/SurfaceView;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V
"""


def apply_v2_4_xcrop_patch(content):
    print("[v2.4] setDisplayParams: AAP sidebar X-crop on non-main displays:")
    if V2_4_XCROP_SENTINEL in content:
        print("  [SKIP] xcrop epilogue: already injected")
        return content, 0

    # Insert v2.4's body between v2.2's final setLayoutParams and the closing
    # return-void. Pattern is the unique trailer v2.2 leaves behind.
    pattern = re.compile(
        r"(invoke-virtual \{v1, v0\}, Landroid/view/SurfaceView;->setLayoutParams\(Landroid/view/ViewGroup\$LayoutParams;\)V\s*\n)"
        r"(\s*return-void\s*\n\.end method)",
        re.MULTILINE,
    )

    replacement = r"\1" + V2_4_XCROP_CODE + r"\2"
    new_content, n = pattern.subn(replacement, content)
    if n == 0:
        print("  [WARN] could not find v2.2 trailer to inject after (apply v2.2 first?)")
        return content, 0

    print("  [OK]   setDisplayParams: appended X-crop epilogue (gated on displayId != 0)")
    return new_content, 1


# ---------------------------------------------------------------------------
# v2.5-diag — emit window/display geometry + windowing mode each call
# ---------------------------------------------------------------------------
# Hunting the "AA launched from car shortcut on Display 0 has small gaps on
# all sides" bug. We need to know what bounds the system actually gives AA
# in that scenario vs in the post-Impulse-resize state. Each setDisplayParams
# call logs:
#   window=WxH  (Configuration.windowConfiguration.getBounds())
#   mode=N      (WindowConfiguration.getWindowingMode(): 1=fullscreen,
#                2=pinned, 3=split-primary, 4=split-secondary, 5=freeform)
#   displayId=I (Activity.getDisplay().getDisplayId())
# Tag "V25_DIAG" so it's easy to grep.

V25_DIAG_SENTINEL = "# V25_DIAG_BOUNDS"

V25_DIAG_CODE = f"""
    {V25_DIAG_SENTINEL}
    const-string v0, "V25_DIAG"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {{v1}}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "setDisplayParams w="

    invoke-virtual {{v1, v2}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{p0}}, Lcom/ts/androidauto/app/display/AapActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v2

    invoke-virtual {{v2}}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object v2

    iget-object v2, v2, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {{v2}}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object v3

    invoke-virtual {{v3}}, Landroid/graphics/Rect;->width()I

    move-result v4

    invoke-virtual {{v1, v4}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v4, " h="

    invoke-virtual {{v1, v4}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{v3}}, Landroid/graphics/Rect;->height()I

    move-result v3

    invoke-virtual {{v1, v3}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v3, " mode="

    invoke-virtual {{v1, v3}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{v2}}, Landroid/app/WindowConfiguration;->getWindowingMode()I

    move-result v2

    invoke-virtual {{v1, v2}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, " displayId="

    invoke-virtual {{v1, v2}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{p0}}, Lcom/ts/androidauto/app/display/AapActivity;->getDisplay()Landroid/view/Display;

    move-result-object v2

    if-eqz v2, :v25_no_display

    invoke-virtual {{v2}}, Landroid/view/Display;->getDisplayId()I

    move-result v2

    invoke-virtual {{v1, v2}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    goto :v25_log

    :v25_no_display
    const-string v2, "null"

    invoke-virtual {{v1, v2}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    :v25_log
    invoke-virtual {{v1}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {{v0, v1}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
"""


def apply_v25_diag_patch(content):
    print("[v2.5-diag] setDisplayParams: log bounds + mode + displayId:")
    if V25_DIAG_SENTINEL in content:
        print("  [SKIP] diag log already injected")
        return content, 0

    # Inject right after v2.0's V3_RESIZE_INJECTION_V2 sentinel — which is
    # the very first thing in the method body. We tuck our diagnostic between
    # `.locals N` and v2.0's prologue so it runs on every call.
    pattern = re.compile(
        r"(\.method private setDisplayParams\(\)V\s*\n\s*\.locals )(\d+)(\s*\n)",
        re.MULTILINE,
    )

    def bump_and_inject(match):
        prefix, count, suffix = match.group(1), int(match.group(2)), match.group(3)
        # Diag uses v0..v4, body uses v0..v4 — .locals 5 is enough.
        new_count = max(count, 5)
        return f"{prefix}{new_count}{suffix}{V25_DIAG_CODE}"

    new_content, n = pattern.subn(bump_and_inject, content)
    if n == 0:
        print("  [WARN] could not find setDisplayParams .locals to inject after")
        return content, 0

    print("  [OK]   diag log injected at setDisplayParams entry")
    return new_content, 1


# ---------------------------------------------------------------------------
# v2.1 onConfigurationChanged — re-trigger setDisplayParams on size change
# ---------------------------------------------------------------------------

# Suffixed with _V2 because the method body changed (added Log.e). Same
# reasoning as V3_SENTINEL_V2 above — keep the suffix in lockstep with
# meaningful body changes so stale build_v19/ trees still get re-patched.
V4_SENTINEL = "# V4_ONCONFIGCHANGED_INJECTION_V2"

V4_ONCONFIG_METHOD = f"""

{V4_SENTINEL}
.method public onConfigurationChanged(Landroid/content/res/Configuration;)V
    .locals 2
    .param p1, "newConfig"    # Landroid/content/res/Configuration;

    invoke-super {{p0, p1}}, Landroid/app/Activity;->onConfigurationChanged(Landroid/content/res/Configuration;)V

    const-string v0, "V21_PATCH"

    const-string v1, "onConfigurationChanged fired -> calling setDisplayParams"

    invoke-static {{v0, v1}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    invoke-direct {{p0}}, Lcom/ts/androidauto/app/display/AapActivity;->setDisplayParams()V

    return-void
.end method
"""


def apply_v2_1_onconfig_patch(content):
    print("[v2.1] onConfigurationChanged injection:")
    if V4_SENTINEL in content:
        print("  [SKIP] onConfigurationChanged: already injected")
        return content, 0

    # Refuse to inject if the class already declares its own override —
    # we'd produce a duplicate-method error from apktool/dexer otherwise.
    if re.search(
        r"\.method\s+public\s+onConfigurationChanged\(Landroid/content/res/Configuration;\)V",
        content,
    ):
        print("  [SKIP] onConfigurationChanged: class already overrides it")
        return content, 0

    # Append the new method at the end of the file. Smali class files have
    # no terminating `.end class` directive so appending is safe.
    if not content.endswith("\n"):
        content += "\n"
    print("  [OK]   onConfigurationChanged: appended override calling setDisplayParams()")
    return content + V4_ONCONFIG_METHOD, 1


# ---------------------------------------------------------------------------
# v2.1 AndroidManifest.xml — expand configChanges
# ---------------------------------------------------------------------------

def apply_v2_1_manifest_patch(manifest_path):
    print("[v2.1] AndroidManifest.xml configChanges:")
    if not os.path.exists(manifest_path):
        print(f"  [WARN] manifest not found at {manifest_path}")
        return 0

    content = read_file(manifest_path)
    # Match the AapActivity entry's android:configChanges value.
    match = re.search(
        r'(<activity[^>]*android:name="\.display\.AapActivity"[^>]*)',
        content,
    )
    if not match:
        print("  [WARN] AapActivity entry not found in manifest")
        return 0

    activity_tag = match.group(1)
    cc_match = re.search(r'android:configChanges="([^"]*)"', activity_tag)
    if cc_match:
        current = set(filter(None, cc_match.group(1).split("|")))
    else:
        current = set()

    desired = current.union(EXTRA_CONFIG_CHANGES)
    if desired == current:
        print("  [SKIP] configChanges already covers all required flags")
        return 0

    # Preserve a stable ordering (existing first, then new alphabetical).
    ordered_existing = [f for f in cc_match.group(1).split("|") if f] if cc_match else []
    new_flags = sorted(desired - set(ordered_existing))
    merged = "|".join(ordered_existing + new_flags)

    if cc_match:
        new_activity = re.sub(
            r'android:configChanges="[^"]*"',
            f'android:configChanges="{merged}"',
            activity_tag,
        )
    else:
        # Inject the attribute right after the opening `<activity ` token.
        new_activity = activity_tag.replace(
            "<activity ", f'<activity android:configChanges="{merged}" ', 1
        )

    new_content = content.replace(activity_tag, new_activity, 1)
    write_file(manifest_path, new_content)
    added = sorted(desired - current)
    print(f"  [OK]   configChanges now includes: {merged}")
    print(f"         (added: {', '.join(added)})")
    return 1


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def main():
    print("=" * 62)
    print("  v2.1 Patch: AndroidAutoApp focus bypass + live resize")
    print("=" * 62)

    if not os.path.exists(AAP_ACTIVITY):
        print(f"\nERROR: AapActivity.smali not found at {AAP_ACTIVITY}")
        print("\nDid you disassemble the stock APK first?")
        print(
            f"  java -jar tools/apktool_3.0.2.jar d -f -o {BUILD_DIR} "
            "scripts/aa-patches/stock/AndroidAutoApp_stock.apk"
        )
        sys.exit(1)

    content = read_file(AAP_ACTIVITY)
    total = 0

    content, n = apply_v1_focus_patches(content)
    total += n

    content, n = apply_v2_setdisplayparams_patch(content)
    total += n

    content, n = apply_v2_1_updatedp_patch(content)
    total += n

    content, n = apply_v2_1_onconfig_patch(content)
    total += n

    content, n = apply_v2_2_crop_patch(content)
    total += n

    content, n = apply_v2_4_xcrop_patch(content)
    total += n

    content, n = apply_v25_diag_patch(content)
    total += n

    write_file(AAP_ACTIVITY, content)

    total += apply_v2_1_manifest_patch(MANIFEST)

    print()
    print(f"Done. {total} change(s) applied.")
    if total == 0:
        print("INFO: All patches were already present.")
    print()
    print("Next steps:")
    print(
        f"  1. Reassemble:  java -jar tools/apktool_3.0.2.jar b -o "
        f"build_v19/AndroidAutoApp_v19_unsigned.apk {BUILD_DIR}"
    )
    print(
        '  2. Align:       & "C:\\Users\\vanes\\AppData\\Local\\Android\\Sdk\\'
        'build-tools\\36.1.0\\zipalign.exe" -f 4 '
        "build_v19\\AndroidAutoApp_v19_unsigned.apk "
        "build_v19\\AndroidAutoApp_v19_aligned.apk"
    )
    print(
        '  3. Sign:        & "C:\\Users\\vanes\\AppData\\Local\\Android\\Sdk\\'
        'build-tools\\36.1.0\\apksigner.bat" sign --ks '
        "C:\\Users\\vanes\\.android\\debug.keystore --ks-pass pass:android "
        "--out build_v19\\AndroidAutoApp_v19_signed.apk "
        "build_v19\\AndroidAutoApp_v19_aligned.apk"
    )


if __name__ == "__main__":
    main()
