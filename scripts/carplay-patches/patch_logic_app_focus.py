"""
CarPlay app focus patch for HVAC validation.

Target: build_carplay/ts-app-hvac-focus-v13/ (com.ts.carplay.app)

This is intentionally smaller than the archived app patch. It changes:
- VideoModel.lambda$priorityChanged$3 so the visual app does not forward HVAC
  uiNotification focus to the vendor CarPlay service.
- The same method also ignores normal display-0 focus return events
  (priority 0, action 1, empty borrowId) while CarPlay is still present in the
  focus stack or while our launcher has explicitly targeted CarPlay to D3.
  Without this, opening an app on D0 after a reboot can replace the CarPlay
  video route even though the D3 Activity and Surface are still alive.
- CarPlayDisplayActivity.onPause so native AC/HVAC focus changes cannot
  broadcast "background" and drop the decoder route. The previous
  secondary-display guard was not reliable after a head-unit reboot because
  getDisplay() could still drive the stock background path during HVAC close.
- CarPlayDisplayActivity.FinishActivityReceiver so the stock FINISH_ACTIVITY
  broadcast from AppList/display-0 focus changes does not finish the CarPlay
  Activity when that Activity is running on a secondary display.
- CarPlayDisplayActivity.requestVideoFocus so direct focus callbacks can still
  close the display-0 Activity, but cannot finish an Activity that is already
  running on a secondary display.
- fragment_display_surface.xml so the native SurfaceView fills the activity
  instead of using the stock 1896x700 centered viewport that leaves gray margins
  on a 1920x720 cluster.
- CarPlayDisplayFragment$2.surfaceChanged so the native renderer receives
  1904x704 on secondary displays. That matches the aligned native CarPlay
  buffer observed by SurfaceFlinger while the Activity/window stay 1920x720.
- CarPlayDisplayFragment.initView calls SurfaceHolder.setFixedSize(1904,704)
  before registering the Surface callback on secondary displays. Applying the
  fixed size before surfaceChanged avoids a mid-callback Surface recreation
  that was observed to leave a stale 1x1 SurfaceView beside the real 1904x704
  Surface on the first cold D0 -> D3 handoff.

It does not change Activity launchMode, dynamic resize loops, overscan crop, or
Android Auto.
"""

import os
import re
import sys

BUILD_DIR = "build_carplay/ts-app-hvac-focus-v13"
VIDEO_MODEL = f"{BUILD_DIR}/smali/com/ts/carplay/app/service/model/video/VideoModel.smali"
DISPLAY_ACTIVITY = f"{BUILD_DIR}/smali/com/ts/carplay/app/ui/display/view/CarPlayDisplayActivity.smali"
FINISH_RECEIVER = f"{BUILD_DIR}/smali/com/ts/carplay/app/ui/display/view/CarPlayDisplayActivity$FinishActivityReceiver.smali"
DISPLAY_FRAGMENT = f"{BUILD_DIR}/smali/com/ts/carplay/app/ui/display/view/CarPlayDisplayFragment.smali"
FRAGMENT_CALLBACK = f"{BUILD_DIR}/smali/com/ts/carplay/app/ui/display/view/CarPlayDisplayFragment$2.smali"
SURFACE_LAYOUT = f"{BUILD_DIR}/res/layout/fragment_display_surface.xml"
VIDEO_SENTINEL = "# CP_KEEP_VIDEO_FOCUS_FOR_HVAC_D0_APPS_AND_NORMAL_RETURN"
LEGACY_LIFECYCLE_SENTINEL = "# CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE"
LIFECYCLE_SENTINEL = "# CP_KEEP_CLUSTER_VIDEO_FOREGROUND_ON_ANY_PAUSE"
FINISH_RECEIVER_SENTINEL = "# CP_IGNORE_FINISH_BROADCAST_ON_SECONDARY_DISPLAY"
REQUEST_VIDEO_FOCUS_SENTINEL = "# CP_IGNORE_REQUEST_VIDEO_FOCUS_FINISH_ON_SECONDARY_DISPLAY"
SURFACE_LAYOUT_SENTINEL = "CP_SURFACE_MATCH_PARENT_FULLSCREEN"
SURFACE_PRE_SIZE_SENTINEL = "# CP_SURFACE_FIXED_SIZE_BEFORE_CALLBACK_ON_SECONDARY"
SURFACE_SHOW_SENTINEL = "# CP_SURFACE_SHOW_NATIVE_1904_704_ON_SECONDARY"

PATCHED_METHOD = r""".method public static synthetic lambda$priorityChanged$3(Lcom/ts/carplay/app/service/model/video/VideoModel;Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;I)V
    .locals 4
    .param p1, "inF"    # Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;
    .param p2, "action"    # I

    # CP_KEEP_VIDEO_FOCUS_FOR_HVAC_D0_APPS_AND_NORMAL_RETURN
    if-eqz p1, :cond_return

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getBid()Ljava/lang/String;

    move-result-object v0

    const-string v1, "uiNotification"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_normal_return_guard

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getFocus()Ljava/lang/String;

    move-result-object v0

    const-string v1, "com.beantechs.hvac"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_keep_focus

    const-string v1, "uiNotification"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_keep_focus

    goto :cond_normal_return_guard

    :cond_keep_focus
    const-string v0, "VideoModel"

    const-string v1, "priorityChanged patched: keep CarPlay video focus for HVAC uiNotification"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    :cond_normal_return_guard
    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getPty()I

    move-result v1

    if-nez v1, :cond_stock

    if-eqz p2, :cond_stock

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getBid()Ljava/lang/String;

    move-result-object v1

    const-string v2, ""

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_stock

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getFocus()Ljava/lang/String;

    move-result-object v1

    if-eqz v1, :cond_stock

    const-string v2, "com.ts.carplay.app"

    invoke-virtual {v1, v2}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v2

    if-nez v2, :cond_stock

    invoke-direct {p0}, Lcom/ts/carplay/app/service/model/video/VideoModel;->isCarPlayInStack()Z

    move-result v2

    if-nez v2, :cond_keep_normal_d0_focus

    iget v2, p0, Lcom/ts/carplay/app/service/model/video/VideoModel;->mConnStatus:I

    const/4 v3, 0x2

    if-ne v2, v3, :cond_stock

    const-string v2, "persist.haval.carplay.desired_display"

    const/4 v3, -0x1

    invoke-static {v2, v3}, Landroid/os/SystemProperties;->getInt(Ljava/lang/String;I)I

    move-result v2

    const/4 v3, 0x3

    if-ne v2, v3, :cond_stock

    :cond_keep_normal_d0_focus
    const-string v0, "VideoModel"

    const-string v1, "priorityChanged patched: keep CarPlay video focus for D3 display0 normal app"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    :cond_stock
    iget-object v0, p0, Lcom/ts/carplay/app/service/model/video/VideoModel;->mCarPlayRemoteManager:Lcom/ts/carplay/app/service/manager/CarPlayRemoteManager;

    if-eqz v0, :cond_return

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getPty()I

    move-result v0

    if-nez v0, :cond_change_focus

    if-nez p2, :cond_change_focus

    const-string v0, "VideoModel"

    const-string v1, "normol screens have no exit action"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    goto :cond_return

    :cond_change_focus
    const-string v0, "VideoModel"

    const-string v1, "changeVideoFocus"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    iget-object v0, p0, Lcom/ts/carplay/app/service/model/video/VideoModel;->mCarPlayRemoteManager:Lcom/ts/carplay/app/service/manager/CarPlayRemoteManager;

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getPty()I

    move-result v1

    const/4 v2, 0x1

    invoke-virtual {p1}, Lcom/ts/carplay/app/service/model/video/VideoModel$FocusModeInfo;->getBid()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v0, v1, v2, v3, p2}, Lcom/ts/carplay/app/service/manager/CarPlayRemoteManager;->changeVideoFocus(IZLjava/lang/String;I)V

    :cond_return
    return-void
.end method"""

PATCHED_REQUEST_VIDEO_FOCUS = r""".method public requestVideoFocus(I)V
    .locals 4
    .param p1, "requestType"    # I

    .line 209
    sget-object v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->TAG:Ljava/lang/String;

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "requestVideoFocus type: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    # CP_IGNORE_REQUEST_VIDEO_FOCUS_FINISH_ON_SECONDARY_DISPLAY
    .line 211
    const/4 v0, 0x1

    if-eq p1, v0, :cond_maybe_finish

    const/4 v0, 0x2

    if-ne p1, v0, :cond_return

    :cond_maybe_finish
    invoke-virtual {p0}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->getDisplay()Landroid/view/Display;

    move-result-object v0

    if-eqz v0, :cond_finish

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    if-eqz v0, :cond_finish

    sget-object v1, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->TAG:Ljava/lang/String;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "requestVideoFocus patched: ignore finish on secondary display "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    :cond_finish
    .line 213
    sget-object v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->TAG:Ljava/lang/String;

    const-string v1, "requestVideoFocus finish the activity"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    .line 214
    invoke-virtual {p0}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->finish()V

    .line 216
    :cond_return
    return-void
.end method"""

PATCHED_ON_PAUSE = r""".method protected onPause()V
    .locals 3

    .line 141
    invoke-super {p0}, Landroid/app/Activity;->onPause()V

    .line 142
    sget-object v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->TAG:Ljava/lang/String;

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "Lifecycle --- onPause: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    # CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE
    # CP_KEEP_CLUSTER_VIDEO_FOREGROUND_ON_ANY_PAUSE
    sget-object v0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->TAG:Ljava/lang/String;

    const-string v1, "onPause patched: keep CarPlay video foreground and suppress background"

    invoke-static {v0, v1}, Lcom/ts/carplay/common/util/LogUtil;->debug(Ljava/lang/String;Ljava/lang/String;)V

    .line 143
    new-instance v0, Landroid/content/Intent;

    invoke-direct {v0}, Landroid/content/Intent;-><init>()V

    .line 144
    .local v0, "intent":Landroid/content/Intent;
    const-string v1, "ts.car.carplay.view_state"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    .line 145
    const-string v1, "state"

    const-string v2, "foreground"

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 146
    invoke-virtual {p0, v0}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->sendBroadcast(Landroid/content/Intent;)V

    .line 147
    return-void
.end method"""

PATCHED_FINISH_RECEIVER_ON_RECEIVE = r""".method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 5
    .param p1, "context"    # Landroid/content/Context;
    .param p2, "intent"    # Landroid/content/Intent;

    .line 286
    invoke-virtual {p2}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v0

    .line 287
    .local v0, "action":Ljava/lang/String;
    invoke-static {}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->access$200()Ljava/lang/String;

    move-result-object v1

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "onReceive broadcast action: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Lcom/ts/carplay/common/util/LogUtil;->info(Ljava/lang/String;Ljava/lang/String;)V

    # CP_IGNORE_FINISH_BROADCAST_ON_SECONDARY_DISPLAY
    iget-object v1, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity$FinishActivityReceiver;->this$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;

    invoke-virtual {v1}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->getDisplay()Landroid/view/Display;

    move-result-object v1

    if-eqz v1, :cond_finish

    invoke-virtual {v1}, Landroid/view/Display;->getDisplayId()I

    move-result v1

    if-eqz v1, :cond_finish

    invoke-static {}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->access$200()Ljava/lang/String;

    move-result-object v2

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "finish receiver patched: ignore finish on secondary display "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Lcom/ts/carplay/common/util/LogUtil;->info(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    .line 288
    :cond_finish
    iget-object v1, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity$FinishActivityReceiver;->this$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;

    invoke-virtual {v1}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayActivity;->finish()V

    .line 289
    return-void
.end method"""


def patch_surface_layout() -> None:
    if not os.path.exists(SURFACE_LAYOUT):
        print(f"ERROR: layout file not found: {SURFACE_LAYOUT}")
        sys.exit(1)

    with open(SURFACE_LAYOUT, "r", encoding="utf-8") as f:
        layout_content = f.read()

    if SURFACE_LAYOUT_SENTINEL in layout_content:
        print("[SKIP] SurfaceView layout already uses match_parent fullscreen")
        return

    width_attr = 'android:layout_width="@dimen/surface_width"'
    height_attr = 'android:layout_height="@dimen/surface_height"'
    if width_attr not in layout_content or height_attr not in layout_content:
        print("ERROR: stock SurfaceView 1896x700 layout attributes not found")
        sys.exit(1)

    layout_content = layout_content.replace(
        "<SurfaceView ",
        f"<!-- {SURFACE_LAYOUT_SENTINEL}: fill 1920x720 parent while native CarPlay uses a clean 1904x704 buffer -->\n"
        "        <SurfaceView ",
        1,
    )
    layout_content = layout_content.replace(width_attr, 'android:layout_width="match_parent"', 1)
    layout_content = layout_content.replace(height_attr, 'android:layout_height="match_parent"', 1)

    with open(SURFACE_LAYOUT, "w", encoding="utf-8") as f:
        f.write(layout_content)

    print("[OK] SurfaceView layout now fills the CarPlay activity parent")


def patch_surface_fixed_size_before_callback() -> None:
    if not os.path.exists(DISPLAY_FRAGMENT):
        print(f"ERROR: smali file not found: {DISPLAY_FRAGMENT}")
        sys.exit(1)

    with open(DISPLAY_FRAGMENT, "r", encoding="utf-8") as f:
        fragment_content = f.read()

    if SURFACE_PRE_SIZE_SENTINEL in fragment_content:
        print("[SKIP] SurfaceHolder fixed size is already applied before callback registration")
        return

    target = """    .line 59
    iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v0}, Landroid/view/SurfaceView;->getHolder()Landroid/view/SurfaceHolder;

    move-result-object v0

    iget-object v1, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mSurfaceHolderCallback:Landroid/view/SurfaceHolder$Callback;

    invoke-interface {v0, v1}, Landroid/view/SurfaceHolder;->addCallback(Landroid/view/SurfaceHolder$Callback;)V"""

    if target not in fragment_content:
        print("ERROR: SurfaceHolder.addCallback block not found")
        sys.exit(1)

    replacement = f"""{SURFACE_PRE_SIZE_SENTINEL}
    invoke-virtual {{p0}}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->getActivity()Landroid/app/Activity;

    move-result-object v0

    if-eqz v0, :cond_cp_pre_size_done

    invoke-virtual {{v0}}, Landroid/app/Activity;->getDisplay()Landroid/view/Display;

    move-result-object v0

    if-eqz v0, :cond_cp_pre_size_done

    invoke-virtual {{v0}}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    if-eqz v0, :cond_cp_pre_size_done

    iget-object v0, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {{v0}}, Landroid/view/SurfaceView;->getHolder()Landroid/view/SurfaceHolder;

    move-result-object v0

    const/16 v1, 0x770

    const/16 p1, 0x2c0

    invoke-interface {{v0, v1, p1}}, Landroid/view/SurfaceHolder;->setFixedSize(II)V

    :cond_cp_pre_size_done
{target}"""

    fragment_content = fragment_content.replace(target, replacement, 1)

    with open(DISPLAY_FRAGMENT, "w", encoding="utf-8") as f:
        f.write(fragment_content)

    print("[OK] SurfaceHolder fixed size now runs before callback registration on secondary displays")


def patch_surface_callback() -> None:
    if not os.path.exists(FRAGMENT_CALLBACK):
        print(f"ERROR: smali file not found: {FRAGMENT_CALLBACK}")
        sys.exit(1)

    with open(FRAGMENT_CALLBACK, "r", encoding="utf-8") as f:
        callback_content = f.read()

    if SURFACE_SHOW_SENTINEL in callback_content:
        print("[SKIP] SurfaceView renderer size already forced on secondary displays")
        return

    callback_content, locals_count = re.subn(
        r"(\.method public surfaceChanged\(Landroid/view/SurfaceHolder;III\)V\s*\n\s*)\.locals 3",
        r"\1.locals 4",
        callback_content,
        count=1,
        flags=re.DOTALL,
    )
    if locals_count != 1:
        print("ERROR: surfaceChanged .locals 3 declaration not found")
        sys.exit(1)

    target = (
        "    invoke-interface {v1, v2, p3, p4}, "
        "Lcom/ts/carplay/app/ui/display/view/DisplayContract$Presenter;->show(Landroid/view/Surface;II)Z"
    )
    if target not in callback_content:
        print("ERROR: DisplayContract.Presenter.show(surface,p3,p4) call not found")
        sys.exit(1)

    replacement = f"""{SURFACE_SHOW_SENTINEL}
    iget-object v3, p0, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment$2;->this$0:Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;

    invoke-virtual {{v3}}, Lcom/ts/carplay/app/ui/display/view/CarPlayDisplayFragment;->getActivity()Landroid/app/Activity;

    move-result-object v3

    if-eqz v3, :cond_cp_surface_keep_callback_size

    invoke-virtual {{v3}}, Landroid/app/Activity;->getDisplay()Landroid/view/Display;

    move-result-object v3

    if-eqz v3, :cond_cp_surface_keep_callback_size

    invoke-virtual {{v3}}, Landroid/view/Display;->getDisplayId()I

    move-result v3

    if-eqz v3, :cond_cp_surface_keep_callback_size

    const/16 p3, 0x770

    const/16 p4, 0x2c0

    :cond_cp_surface_keep_callback_size
{target}"""

    callback_content = callback_content.replace(target, replacement, 1)

    with open(FRAGMENT_CALLBACK, "w", encoding="utf-8") as f:
        f.write(callback_content)

    print("[OK] SurfaceView renderer now receives native 1904x704 on secondary displays")


def main():
    for path in (VIDEO_MODEL, DISPLAY_ACTIVITY, FINISH_RECEIVER, DISPLAY_FRAGMENT, FRAGMENT_CALLBACK, SURFACE_LAYOUT):
        if not os.path.exists(path):
            print(f"ERROR: smali file not found: {path}")
            sys.exit(1)

    with open(VIDEO_MODEL, "r", encoding="utf-8") as f:
        video_content = f.read()

    if VIDEO_SENTINEL in video_content:
        print("[SKIP] HVAC focus patch already applied")
    else:
        pattern = (
            r"\.method public static synthetic lambda\$priorityChanged\$3"
            r"\(Lcom/ts/carplay/app/service/model/video/VideoModel;"
            r"Lcom/ts/carplay/app/service/model/video/VideoModel\$FocusModeInfo;I\)V"
            r".*?\.end method"
        )
        video_content, count = re.subn(pattern, PATCHED_METHOD, video_content, count=1, flags=re.DOTALL)
        if count != 1:
            print("ERROR: priorityChanged lambda method not found or matched more than once")
            sys.exit(1)

        with open(VIDEO_MODEL, "w", encoding="utf-8") as f:
            f.write(video_content)
        print("[OK] VideoModel priorityChanged now keeps CarPlay video focus during HVAC and D0 app focus")

    with open(DISPLAY_ACTIVITY, "r", encoding="utf-8") as f:
        activity_content = f.read()

    if LIFECYCLE_SENTINEL in activity_content:
        print("[SKIP] foreground-on-pause patch already applied")
    else:
        pattern = r"\.method protected onPause\(\)V.*?\.end method"
        activity_content, count = re.subn(pattern, PATCHED_ON_PAUSE, activity_content, count=1, flags=re.DOTALL)
        if count != 1:
            print("ERROR: CarPlayDisplayActivity.onPause method not found or matched more than once")
            sys.exit(1)

        with open(DISPLAY_ACTIVITY, "w", encoding="utf-8") as f:
            f.write(activity_content)

        print("[OK] CarPlayDisplayActivity.onPause now keeps video state foreground during pause")

    with open(DISPLAY_ACTIVITY, "r", encoding="utf-8") as f:
        activity_content = f.read()

    if REQUEST_VIDEO_FOCUS_SENTINEL in activity_content:
        print("[SKIP] secondary-display requestVideoFocus finish patch already applied")
    else:
        pattern = r"\.method public requestVideoFocus\(I\)V.*?\.end method"
        activity_content, count = re.subn(
            pattern,
            PATCHED_REQUEST_VIDEO_FOCUS,
            activity_content,
            count=1,
            flags=re.DOTALL,
        )
        if count != 1:
            print("ERROR: CarPlayDisplayActivity.requestVideoFocus method not found or matched more than once")
            sys.exit(1)

        with open(DISPLAY_ACTIVITY, "w", encoding="utf-8") as f:
            f.write(activity_content)

        print("[OK] CarPlayDisplayActivity.requestVideoFocus now ignores finish on secondary displays")

    with open(FINISH_RECEIVER, "r", encoding="utf-8") as f:
        receiver_content = f.read()

    if FINISH_RECEIVER_SENTINEL in receiver_content:
        print("[SKIP] secondary-display finish receiver patch already applied")
    else:
        pattern = (
            r"\.method public onReceive"
            r"\(Landroid/content/Context;Landroid/content/Intent;\)V"
            r".*?\.end method"
        )
        receiver_content, count = re.subn(
            pattern,
            PATCHED_FINISH_RECEIVER_ON_RECEIVE,
            receiver_content,
            count=1,
            flags=re.DOTALL,
        )
        if count != 1:
            print("ERROR: FinishActivityReceiver.onReceive method not found or matched more than once")
            sys.exit(1)

        with open(FINISH_RECEIVER, "w", encoding="utf-8") as f:
            f.write(receiver_content)

        print("[OK] FinishActivityReceiver now ignores FINISH_ACTIVITY on secondary displays")
    patch_surface_layout()
    patch_surface_fixed_size_before_callback()
    patch_surface_callback()


if __name__ == "__main__":
    main()
