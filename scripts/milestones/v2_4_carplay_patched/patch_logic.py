"""
CarPlay App Patching Pipeline (v2.4 equivalent)
========================================================================

This script automates:
1. Modifying AndroidManifest.xml to expand android:configChanges so the activity handles display/size moves without recreating.
2. Modifying CarPlayDisplayActivity.smali to keep CarPlay projection active during focus pauses, prevent self-termination, and hook onConfigurationChanged.
3. Modifying CarPlayDisplayFragment.smali to inject a dynamic display size updater and overscan crop for the cluster display.

Idempotency:
    Each patch leaves a sentinel comment in the patched file. All blocks
    check for their sentinel before applying, so a second invocation is a no-op.
"""

import os
import re
import sys

BUILD_DIR = "build_carplay/app"
SMALI_BASE = f"{BUILD_DIR}/smali/com/ts/carplay/app/ui/display/view"
CARPLAY_ACTIVITY = f"{SMALI_BASE}/CarPlayDisplayActivity.smali"
CARPLAY_FRAGMENT = f"{SMALI_BASE}/CarPlayDisplayFragment.smali"
CARPLAY_FRAGMENT_CALLBACK = f"{SMALI_BASE}/CarPlayDisplayFragment$2.smali"
MANIFEST = f"{BUILD_DIR}/AndroidManifest.xml"

# Extra configChanges flags to OR into the existing list.
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
    new, count = re.subn(regex, replacement, content, flags=flags)
    if count > 0:
        print(f"  [OK]   {description} (applied {count} times)")
        return new, True
    print(f"  [SKIP] {description} (already applied or no match)")
    return content, False

def patch_direct(content, target, replacement, description):
    if target in content:
        print(f"  [OK]   {description}")
        return content.replace(target, replacement), True
    print(f"  [SKIP] {description} (already applied or no match)")
    return content, False

# ===========================================================================
# 1. AndroidManifest.xml patch
# ===========================================================================
def apply_manifest_patch(manifest_path):
    print("[Manifest] Expanding configChanges for CarPlayDisplayActivity:")
    if not os.path.exists(manifest_path):
        print(f"  [WARN] manifest not found at {manifest_path}")
        return 0

    content = read_file(manifest_path)
    
    # Match the CarPlayDisplayActivity activity tag
    match = re.search(
        r'(<activity[^>]*android:name="\.ui\.display\.view\.CarPlayDisplayActivity"[^>]*)',
        content,
    )
    if not match:
        print("  [WARN] CarPlayDisplayActivity entry not found in manifest")
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

    # Stable ordering
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
        new_activity = activity_tag.replace(
            "<activity ", f'<activity android:configChanges="{merged}" ', 1
        )

    new_content = content.replace(activity_tag, new_activity, 1)
    write_file(manifest_path, new_content)
    print(f"  [OK]   configChanges now includes: {merged}")
    return 1

# ===========================================================================
# 2. CarPlayDisplayActivity patches
# ===========================================================================
CARPLAY_ONPAUSE_SENTINEL = "# CARPLAY_ONPAUSE_FOREGROUND_PATCH"
CARPLAY_FINISH_SENTINEL = "# CARPLAY_FINISH_BLOCKED_PATCH"
CARPLAY_ONCONFIG_SENTINEL = "# CARPLAY_ONCONFIGCHANGED_INJECTED"

CARPLAY_ONCONFIG_METHOD = f"""

{CARPLAY_ONCONFIG_SENTINEL}
.method public onConfigurationChanged(Landroid/content/res/Configuration;)V
    .locals 2
    .param p1, "newConfig"    # Landroid/content/res/Configuration;

    invoke-super {{p0, p1}}, Landroid/app/Activity;->onConfigurationChanged(Landroid/content/res/Configuration;)V

    const-string v0, "CARPLAY_PATCH"

    const-string v1, "onConfigurationChanged fired -> calling updateDisplayParams"

    invoke-static {{v0, v1}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->mFragment:Lcom/ts/carplay/app/ui/display/view/BaseFragment;

    if-eqz v0, :cond_skip

    check-cast v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;

    invoke-virtual {{v0}}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->updateDisplayParams()V

    :cond_skip
    return-void
.end method
"""

def apply_activity_patches(path):
    print("[Activity] Patching CarPlayDisplayActivity.smali:")
    if not os.path.exists(path):
        print(f"  [ERROR] File not found: {path}")
        return 0

    content = read_file(path)
    count = 0

    # A. onPause: view_state to "foreground"
    if CARPLAY_ONPAUSE_SENTINEL in content:
        print("  [SKIP] onPause view_state already patched to foreground")
    else:
        # Match only inside the onPause method to be extremely safe
        pattern = r"(\.method protected onPause\(\)V.*?const-string v1, \"state\"\s*\n\s*\n\s*const-string v2, \")background(\".*?sendBroadcast\(Landroid/content/Intent;\)V)"
        replacement = r"\1foreground\2\n    " + CARPLAY_ONPAUSE_SENTINEL + "\n"
        content, ok = patch_regex(content, pattern, replacement, "onPause: force view_state to foreground")
        if ok:
            count += 1

    # B. Block self-termination inside requestVideoFocus
    if CARPLAY_FINISH_SENTINEL in content:
        print("  [SKIP] requestVideoFocus finish() already blocked")
    else:
        # Let's isolate the requestVideoFocus method and comment out the finish() call
        pattern = r"(\.method public requestVideoFocus\(I\)V.*?\.end method)"
        # Let's find and replace specifically the finish call inside requestVideoFocus
        def block_finish(match):
            method_body = match.group(1)
            finish_call = 'invoke-virtual {p0}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->finish()V'
            if finish_call in method_body:
                patched_body = method_body.replace(
                    finish_call, 
                    f"{CARPLAY_FINISH_SENTINEL}\n    # invoke-virtual {{p0}}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->finish()V"
                )
                return patched_body
            return method_body

        new_content, n = re.subn(pattern, block_finish, content, flags=re.DOTALL)
        if n > 0 and CARPLAY_FINISH_SENTINEL in new_content:
            print("  [OK]   requestVideoFocus: blocked self-termination finish() call")
            content = new_content
            count += 1
        else:
            print("  [WARN] requestVideoFocus: finish() call not found or not matched")

    # C. Inject onConfigurationChanged
    if CARPLAY_ONCONFIG_SENTINEL in content:
        print("  [SKIP] onConfigurationChanged already injected")
    else:
        if not content.endswith("\n"):
            content += "\n"
        content += CARPLAY_ONCONFIG_METHOD
        print("  [OK]   onConfigurationChanged override injected")
        count += 1

    write_file(path, content)
    return count


# ===========================================================================
# 3. CarPlayDisplayFragment patches
# ===========================================================================
CARPLAY_HOOK_SENTINEL = "# CARPLAY_INITVIEW_HOOK_PATCH"
CARPLAY_UPDATEDP_SENTINEL = "# CARPLAY_UPDATEDISPLAYPARAMS_INJECTED"

CARPLAY_UPDATEDP_METHOD = f"""

{CARPLAY_UPDATEDP_SENTINEL}
.method public updateDisplayParams()V
    .locals 5

    const-string v0, "CARPLAY_PATCH"

    const-string v1, "updateDisplayParams: entering window-bounds calculation"

    invoke-static {{v0, v1}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mSurfaceView:Landroid/view/SurfaceView;

    if-nez v0, :cond_exit

    invoke-virtual {{p0}}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    invoke-virtual {{v0}}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    iget-object v0, v0, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {{v0}}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object v0

    invoke-virtual {{v0}}, Landroid/graphics/Rect;->width()I

    move-result v1

    invoke-virtual {{v0}}, Landroid/graphics/Rect;->height()I

    move-result v2

    if-gtz v1, :cond_check_height

    return-void

    :cond_check_height
    if-gtz v2, :cond_apply_resize

    return-void

    :cond_apply_resize
    iget-object v3, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {{v3}}, Landroid/view/SurfaceView;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v3

    check-cast v3, Landroid/widget/RelativeLayout$LayoutParams;

    # Detect displayId
    const/4 v4, 0x0

    invoke-virtual {{p0}}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->getActivity()Landroid/app/Activity;

    move-result-object v0

    if-eqz v0, :cond_skip_display_check

    invoke-virtual {{v0}}, Landroid/app/Activity;->getDisplay()Landroid/view/Display;

    move-result-object v0

    if-eqz v0, :cond_skip_display_check

    invoke-virtual {{v0}}, Landroid/view/Display;->getDisplayId()I

    move-result v4

    :cond_skip_display_check
    if-eqz v4, :cond_reset_to_default

    # Secondary Display (Oversize width & negative leftMargin to crop CarPlay left dock)
    mul-int/lit8 v0, v1, 0x10

    div-int/lit8 v0, v0, 0xf

    iput v0, v3, Landroid/view/ViewGroup$LayoutParams;->width:I

    iput v2, v3, Landroid/view/ViewGroup$LayoutParams;->height:I

    div-int/lit8 v1, v1, 0xf

    neg-int v1, v1

    iput v1, v3, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I

    goto :apply_layout

    :cond_reset_to_default
    # Main Display (No crop, full width/height)
    iput v1, v3, Landroid/view/ViewGroup$LayoutParams;->width:I

    iput v2, v3, Landroid/view/ViewGroup$LayoutParams;->height:I

    const/4 v0, 0x0

    iput v0, v3, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I

    :apply_layout
    iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {{v0, v3}}, Landroid/view/SurfaceView;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    :cond_exit
    return-void
.end method
"""

def apply_fragment_patches(path):
    print("[Fragment] Patching CarPlayDisplayFragment.smali:")
    if not os.path.exists(path):
        print(f"  [ERROR] File not found: {path}")
        return 0

    content = read_file(path)
    count = 0

    # A. Hook initView to call updateDisplayParams() right before return-void
    if CARPLAY_HOOK_SENTINEL in content:
        print("  [SKIP] initView hook already patched")
    else:
        # Match only the return-void inside initView
        pattern = r"(\.method private initView\(Landroid/view/View;\)V.*?:cond_0\s*\n)(.*?)(\s*return-void\s*\n\.end method)"
        replacement = r"\1\2    " + CARPLAY_HOOK_SENTINEL + r"\n    invoke-virtual {p0}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->updateDisplayParams()V\n\3"
        content, ok = patch_regex(content, pattern, replacement, "initView: hook updateDisplayParams before return-void")
        if ok:
            count += 1

    # B. Inject updateDisplayParams method
    if CARPLAY_UPDATEDP_SENTINEL in content:
        print("  [SKIP] updateDisplayParams method already injected")
    else:
        if not content.endswith("\n"):
            content += "\n"
        content += CARPLAY_UPDATEDP_METHOD
        print("  [OK]   updateDisplayParams method injected")
        count += 1

    # C. Inject mLastWidth and mLastHeight fields
    if "mLastWidth" in content:
        print("  [SKIP] mLastWidth / mLastHeight fields already injected")
    else:
        target = ".field private mSurfaceView:Landroid/view/SurfaceView;"
        replacement = ".field private mSurfaceView:Landroid/view/SurfaceView;\n\n.field public mLastHeight:I\n\n.field public mLastWidth:I"
        content, ok = patch_direct(content, target, replacement, "CarPlayDisplayFragment: inject mLastWidth and mLastHeight fields")
        if ok:
            count += 1

    write_file(path, content)
    return count


CARPLAY_CALLBACK_SENTINEL = "# CARPLAY_SIZE_CHANGED_LATCH_PATCH"

def apply_fragment_callback_patches(path):
    print("[FragmentCallback] Patching CarPlayDisplayFragment$2.smali:")
    if not os.path.exists(path):
        print(f"  [ERROR] File not found: {path}")
        return 0

    content = read_file(path)
    count = 0

    if CARPLAY_CALLBACK_SENTINEL in content:
        print("  [SKIP] surfaceChanged latch patch already applied")
    else:
        # Match the old conditional invoke of show() inside surfaceChanged
        pattern = r"(iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment\$2;->this\$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;\s*\n\s*invoke-static \{v0\}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->access\$300\(Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;\)Z\s*\n\s*move-result v0\s*\n\s*if-eqz v0, :cond_1.*?:cond_1\s*\n\s*return-void)"
        
        replacement = f"""{CARPLAY_CALLBACK_SENTINEL}
    # Check if shouldSetRender is true (mHasShown is false). If so, we definitely show.
    iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment$2;->this$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;

    invoke-static {{v0}}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->access$300(Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;)Z

    move-result v0

    if-eqz v0, :cond_call_show

    # If shouldSetRender is false, check if size has changed
    iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment$2;->this$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;

    iget-object v1, v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mDisplayPresenter:Lcom/ts/carplay/app/ui/display/view/DisplayContract$Presenter;

    if-nez v1, :cond_skip_show

    iget v1, v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mLastWidth:I

    if-eq v1, p3, :cond_check_height

    goto :cond_call_show

    :cond_check_height
    iget v1, v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mLastHeight:I

    if-eq v1, p4, :cond_skip_show

    :cond_call_show
    # Save the new bounds
    iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment$2;->this$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;

    iput p3, v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mLastWidth:I

    iput p4, v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mLastHeight:I

    # Call show
    iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment$2;->this$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;

    iget-object v1, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment$2;->this$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;

    iget-object v1, v1, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mDisplayPresenter:Lcom/ts/carplay/app/ui/display/view/DisplayContract$Presenter;

    iget-object v2, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment$2;->this$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;

    invoke-static {{v2}}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->access$100(Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;)Landroid/view/Surface;

    move-result-object v2

    invoke-interface {{v1, v2, p3, p4}}, Lcom/ts/carplay/app/ui/display/view/DisplayContract$Presenter;->show(Landroid/view/Surface;II)Z

    move-result v1

    invoke-static {{v0, v1}}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->access$202(Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;Z)Z

    :cond_skip_show
    return-void"""

        content, ok = patch_regex(content, pattern, replacement, "surfaceChanged: inject size-changed latch logic")
        if ok:
            count += 1

    write_file(path, content)
    return count


# ===========================================================================
# Driver
# ===========================================================================
def main():
    print("=" * 70)
    print("      CarPlay App Smali Patching Pipeline (v2.4 Equivalent)")
    print("=" * 70)

    if not os.path.exists(CARPLAY_ACTIVITY):
        print(f"\nERROR: CarPlayDisplayActivity.smali not found at {CARPLAY_ACTIVITY}")
        print("\nDid you disassemble the stock APK first?")
        print(f"  java -jar tools/apktool_3.0.2.jar d -f -o {BUILD_DIR} scripts/carplay-patches/stock/TsCarPlayApp_stock.apk")
        sys.exit(1)

    changes = 0
    changes += apply_manifest_patch(MANIFEST)
    print()
    changes += apply_activity_patches(CARPLAY_ACTIVITY)
    print()
    changes += apply_fragment_patches(CARPLAY_FRAGMENT)
    print()
    changes += apply_fragment_callback_patches(CARPLAY_FRAGMENT_CALLBACK)
    print()

    print("=" * 70)
    print(f"Patches complete! Total modified components/files: {changes}")
    print("=" * 70)

if __name__ == "__main__":
    main()
