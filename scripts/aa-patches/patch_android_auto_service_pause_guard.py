#!/usr/bin/env python3
"""Patch AndroidAutoService smali for explicit Android Auto playback control.

Usage:
    python3 scripts/aa-patches/patch_android_auto_service_pause_guard.py \
        scripts/.build/aa-service-current-asset

The decoded directory must come from apktool. Rebuild/sign the APK after this.
"""

from __future__ import annotations

import argparse
from pathlib import Path


HANDLE_INPUT_EVENT = Path("smali/com/ts/androidauto/aap/input/HandleInputEvent.smali")
LINK_COMMAND_BINDER = Path(
    "smali/com/ts/androidauto/projectionservice/AndroidAutoService$LinkCommandBinder.smali"
)
AAP_CONTROLLER = Path("smali/com/ts/androidauto/aap/AapController.smali")

GUARD_MS_HEX = "0x2ee0"  # 12000ms
MEDIA_ACTION_PLAY_HEX = "0x7e"  # Android KEYCODE_MEDIA_PLAY
MEDIA_ACTION_PAUSE_HEX = "0x7f"  # Android KEYCODE_MEDIA_PAUSE


def replace_required(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise SystemExit(f"Expected pattern not found for {label}")
    return text.replace(old, new, 1)


def insert_after_annotation(text: str, method_name: str, block: str, sentinel: str) -> str:
    if sentinel in text:
        return text

    method_start = text.find(f".method public {method_name}")
    if method_start < 0:
        raise SystemExit(f"Method not found: {method_name}")

    annotation_end = text.find("    .end annotation\n", method_start)
    if annotation_end < 0:
        raise SystemExit(f"Annotation end not found for: {method_name}")

    insert_at = annotation_end + len("    .end annotation\n")
    return text[:insert_at] + "\n" + block + text[insert_at:]


def insert_before_method(text: str, marker: str, block: str, sentinel: str) -> str:
    if sentinel in text:
        return text
    insert_at = text.find(marker)
    if insert_at < 0:
        raise SystemExit(f"Method marker not found: {marker}")
    return text[:insert_at] + block + "\n" + text[insert_at:]


def patch_handle_input_event(decoded_dir: Path) -> None:
    path = decoded_dir / HANDLE_INPUT_EVENT
    text = path.read_text()

    text = replace_required(
        text,
        ".field private static sImpulseLastPauseKeyAtMs:J",
        ".field public static sImpulseLastPauseKeyAtMs:J",
        "pause marker visibility",
    )
    text = replace_required(
        text,
        "    const-wide/16 v1, 0x1388",
        f"    const-wide/16 v1, {GUARD_MS_HEX}",
        "pause guard window",
    )

    path.write_text(text)


def patch_link_command_binder(decoded_dir: Path) -> None:
    path = decoded_dir / LINK_COMMAND_BINDER
    text = path.read_text()

    pause_arm = """    const-string v0, "AAPAndroidAutoService"

    const-string v1, "IMPULSE_MEDIA_PATCH binder pause armed play suppression"

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J

    move-result-wide v0

    sput-wide v0, Lcom/ts/androidauto/aap/input/HandleInputEvent;->sImpulseLastPauseKeyAtMs:J
"""
    play_clear = """    const-wide/16 v0, 0x0

    sput-wide v0, Lcom/ts/androidauto/aap/input/HandleInputEvent;->sImpulseLastPauseKeyAtMs:J

    const-string v0, "AAPAndroidAutoService"

    const-string v1, "IMPULSE_MEDIA_PATCH binder play cleared play suppression"

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
"""
    sendkey_guard = """    const/4 v0, 0x7

    if-ne p1, v0, :cond_impulse_not_explicit_play

    const-wide/16 v0, 0x0

    sput-wide v0, Lcom/ts/androidauto/aap/input/HandleInputEvent;->sImpulseLastPauseKeyAtMs:J

    const-string v0, "AAPAndroidAutoService"

    const-string v1, "IMPULSE_MEDIA_PATCH sendKeyEvent PLAY cleared play suppression"

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    goto :cond_impulse_media_guard_done

    :cond_impulse_not_explicit_play
    const/16 v0, 0x8

    if-ne p1, v0, :cond_impulse_media_guard_done

    const-string v0, "AAPAndroidAutoService"

    const-string v1, "IMPULSE_MEDIA_PATCH sendKeyEvent PAUSE armed play suppression"

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J

    move-result-wide v0

    sput-wide v0, Lcom/ts/androidauto/aap/input/HandleInputEvent;->sImpulseLastPauseKeyAtMs:J

    :cond_impulse_media_guard_done
"""

    text = insert_after_annotation(
        text,
        "pause()V",
        pause_arm,
        "IMPULSE_MEDIA_PATCH binder pause armed play suppression",
    )
    text = insert_after_annotation(
        text,
        "play()V",
        play_clear,
        "IMPULSE_MEDIA_PATCH binder play cleared play suppression",
    )
    text = insert_after_annotation(
        text,
        "sendKeyEvent(II)V",
        sendkey_guard,
        "IMPULSE_MEDIA_PATCH sendKeyEvent PAUSE armed play suppression",
    )

    path.write_text(text)


def patch_aap_controller(decoded_dir: Path) -> None:
    path = decoded_dir / AAP_CONTROLLER
    text = path.read_text()

    helper_sentinel = "IMPULSE_MEDIA_PATCH reportAction action="
    helper = f""".method private reportImpulseMediaPlaybackAction(I)V
    .locals 4
    .param p1, "action"    # I

    iget-object v0, p0, Lcom/ts/androidauto/aap/AapController;->mGal:Lcom/ts/androidauto/aap/sink/GalIntegration;

    if-eqz v0, :cond_impulse_report_action_done

    iget-object v0, v0, Lcom/ts/androidauto/aap/sink/GalIntegration;->mediaPlaybackStatus:Lcom/google/android/projection/protocol/MediaPlaybackStatus;

    if-eqz v0, :cond_impulse_report_action_done

    :try_start_impulse_report_action
    invoke-virtual {{v0, p1}}, Lcom/google/android/projection/protocol/MediaPlaybackStatus;->reportAction(I)I

    move-result v0

    sget-object v1, Lcom/ts/androidauto/aap/AapController;->TAG:Ljava/lang/String;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {{v2}}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "{helper_sentinel}"

    invoke-virtual {{v2, v3}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{v2, p1}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v3, " result="

    invoke-virtual {{v2, v3}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{v2, v0}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {{v2}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {{v1, v0}}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_impulse_report_action
    .catch Ljava/lang/Throwable; {{:try_start_impulse_report_action .. :try_end_impulse_report_action}} :catch_impulse_report_action

    goto :cond_impulse_report_action_done

    :catch_impulse_report_action
    move-exception v0

    sget-object v1, Lcom/ts/androidauto/aap/AapController;->TAG:Ljava/lang/String;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {{v2}}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "IMPULSE_MEDIA_PATCH reportAction failed action="

    invoke-virtual {{v2, v3}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{v2, p1}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {{v2}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {{v1, v2, v0}}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    :cond_impulse_report_action_done
    return-void
.end method

"""
    text = insert_before_method(
        text,
        ".method public pause()V",
        helper,
        helper_sentinel,
    )

    pause_report = f"""    const/16 v0, {MEDIA_ACTION_PAUSE_HEX}

    invoke-direct {{p0, v0}}, Lcom/ts/androidauto/aap/AapController;->reportImpulseMediaPlaybackAction(I)V

"""
    text = replace_required(
        text,
        "    .line 2601\n    iget-object v0, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "    .line 2601\n" + pause_report +
        "    iget-object v0, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "pause reportAction",
    )

    play_report = f"""    const/16 v1, {MEDIA_ACTION_PLAY_HEX}

    invoke-direct {{p0, v1}}, Lcom/ts/androidauto/aap/AapController;->reportImpulseMediaPlaybackAction(I)V

"""
    text = replace_required(
        text,
        "    .line 2589\n    :cond_0\n    iget-object v1, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "    .line 2589\n    :cond_0\n" + play_report +
        "    iget-object v1, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "play reportAction",
    )

    sendkey_report = f"""    const/16 v1, {MEDIA_ACTION_PLAY_HEX}

    if-ne p1, v1, :cond_impulse_sendkey_report_pause

    if-nez p2, :cond_impulse_sendkey_report_done

    invoke-direct {{p0, p1}}, Lcom/ts/androidauto/aap/AapController;->reportImpulseMediaPlaybackAction(I)V

    goto :cond_impulse_sendkey_report_done

    :cond_impulse_sendkey_report_pause
    const/16 v1, {MEDIA_ACTION_PAUSE_HEX}

    if-ne p1, v1, :cond_impulse_sendkey_report_done

    if-nez p2, :cond_impulse_sendkey_report_done

    invoke-direct {{p0, p1}}, Lcom/ts/androidauto/aap/AapController;->reportImpulseMediaPlaybackAction(I)V

    :cond_impulse_sendkey_report_done

"""
    text = replace_required(
        text,
        "    .line 2497\n    iget-object v1, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "    .line 2497\n" + sendkey_report +
        "    iget-object v1, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "sendKeyEvent reportAction",
    )

    send_media_pause_report = f"""    if-eqz p1, :cond_impulse_send_media_pause_report_done

    const/16 v0, {MEDIA_ACTION_PAUSE_HEX}

    invoke-direct {{p0, v0}}, Lcom/ts/androidauto/aap/AapController;->reportImpulseMediaPlaybackAction(I)V

    :cond_impulse_send_media_pause_report_done

"""
    text = replace_required(
        text,
        "    .line 2467\n    iget-object v0, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "    .line 2467\n" + send_media_pause_report +
        "    iget-object v0, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "sendMediaPauseKey reportAction",
    )

    send_media_play_report = f"""    if-eqz p1, :cond_impulse_send_media_play_report_done

    const/16 v0, {MEDIA_ACTION_PLAY_HEX}

    invoke-direct {{p0, v0}}, Lcom/ts/androidauto/aap/AapController;->reportImpulseMediaPlaybackAction(I)V

    :cond_impulse_send_media_play_report_done

"""
    text = replace_required(
        text,
        "    .line 2453\n    iget-object v0, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "    .line 2453\n" + send_media_play_report +
        "    iget-object v0, p0, Lcom/ts/androidauto/aap/AapController;->mHandleInputEvent:Lcom/ts/androidauto/aap/input/HandleInputEvent;\n",
        "sendMediaPlayKey reportAction",
    )

    path.write_text(text)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("decoded_dir", type=Path)
    args = parser.parse_args()

    decoded_dir = args.decoded_dir
    patch_handle_input_event(decoded_dir)
    patch_link_command_binder(decoded_dir)
    print(f"Patched AndroidAutoService pause guard in {decoded_dir}")


if __name__ == "__main__":
    main()
