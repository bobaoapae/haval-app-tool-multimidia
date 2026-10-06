# Android Auto DHU Simulation Runbook

Updated: 2026-06-24

## Goal

Use the official Android Auto Desktop Head Unit (DHU) to test the phone and the
standard Android Auto protocol while the Haval head unit is unavailable.

This is a triage harness, not a replacement for the car:

- if `play/pause` fails in DHU, we have a reproduction outside the Haval stack
  for phone, Spotify, Android Auto, and standard DHU keycode logs;
- if `play/pause` works in DHU, the Haval/vendor path remains suspect:
  `com.ts.androidauto.projectionservice`, GAL/AAP, MediaCenter `402`, D3, or
  HardKeyPolicy.

Do not replace this with an Android Automotive OS AVD for this bug. The AVD
runs apps inside Android Automotive OS; DHU emulates an Android Auto head unit
connected to the phone.

Official references:

- https://developer.android.com/training/cars/testing/dhu
- https://developer.android.com/training/cars/testing/emulator

## Local State

DHU is installed at:

```bash
$HOME/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

Verified version:

```text
Android Auto - Desktop Head Unit
Build: 2022-03-30-438482292
Version: 2.0-mac
```

Files in this directory:

- `install_dhu.sh`
- `run_dhu.sh`
- `check_sim_environment.sh`
- `capture_phone_spotify_pause_baseline.sh`
- `capture_phone_dhu_play_pause_probe.sh`
- `wait_for_ready_phone_dhu_probe.sh`
- `validate_pause_probe_analyzers.sh`
- `haval_dhu_basic.ini`
- `haval_dhu_probe.ini`
- `play_pause_probe.dhu`

## Preflight

Before opening DHU or comparing against the Haval native path, run:

```bash
scripts/android-auto-sim/check_sim_environment.sh
```

This read-only preflight records:

- Mac IP on `en0`;
- DHU binary/version and default config presence;
- connected ADB phone and Android Auto package version;
- current phone-side Spotify media-session state;
- `dhu_phone_probe_ready=1` only when DHU/config/phone are ready and Spotify is
  already `PLAYING(3)`;
- central reachability on ping, ADB TCP `5555`, and telnet `23`;
- local analyzer validation result;
- recommended next step.

It does not open DHU, dispatch media commands, adb-connect to the central,
deploy APKs, mount files, rollback, restart, or force-stop anything.

## Phone-Side Spotify Baseline

If Spotify does not pause from the phone UI itself, do not start DHU yet. First
capture the phone-only baseline:

```bash
scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh
```

To collect evidence while manually pressing pause in Spotify on the phone:

```bash
scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh --manual-window 20
```

For a controlled phone-side media pause command without DHU:

```bash
scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh --send-pause
```

To test whether Android can wake Spotify through the current media routing:

```bash
scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh --send-media-key play
```

Supported `--send-media-key` values are `play`, `pause`, `play-pause`, `stop`,
`next`, and `previous`.

If the baseline shows `blocked_no_spotify_session_gearhead_focus`, stop the
stuck Android Auto/Gearhead state on the phone and recapture:

```bash
scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh --stop-gearhead
```

The baseline writes full phone `dumpsys media_session`, `dumpsys audio`, filtered
activity, notification, and logcat artifacts. It classifies common blockers:

- `phone_spotify_playing_detected`: Spotify is visible as an active phone-side
  playback target.
- `phone_spotify_paused_detected`: Spotify is visible as a phone-side playback
  target and is paused.
- `blocked_no_spotify_session_gearhead_focus`: Android Auto/Gearhead has audio
  focus, but Spotify has no active media session on the phone.
- `blocked_no_media_session`: the phone has no active media session to pause.
- `inconclusive_phone_spotify_state`: more manual inspection is required.

After `--stop-gearhead`, reopen Spotify on the phone, start playback, and run
the manual-window baseline again before opening DHU.

Only proceed to the DHU probe after the phone baseline exposes a valid Spotify
playing state or confirms the manual pause behavior you are trying to capture.

## Prepare The Phone

1. Connect the Android phone to the Mac over USB.
2. Confirm Android Auto is updated on the phone.
3. Open Android Auto on the phone.
4. Enable Android Auto developer mode.
5. Start the head unit server from Android Auto developer settings.
6. Confirm the phone appears in ADB:

```bash
$HOME/Library/Android/sdk/platform-tools/adb devices
```

## Run With USB Accessory

This is the stable path observed for `moto_g56_5G` + Android Auto
`17.0.662234-release` + DHU `2.0-mac`.

Before each USB run, leave the phone unlocked and put USB back in normal
Motorola mode if the previous DHU session left it as an Android accessory:

```bash
$HOME/Library/Android/sdk/platform-tools/adb shell svc usb resetUsbGadget
$HOME/Library/Android/sdk/platform-tools/adb kill-server
$HOME/Library/Android/sdk/platform-tools/adb start-server
$HOME/Library/Android/sdk/platform-tools/adb devices -l
```

Then start DHU with the official SDK config:

```bash
scripts/android-auto-sim/run_dhu.sh --usb \
  --config "$HOME/Library/Android/sdk/extras/google/auto/config/default_720p.ini"
```

Expected USB trace:

- Motorola device appears as `vid=22b8,pid=2e81`;
- DHU starts AOAP/accessory mode and the phone re-enumerates as
  `vid=18d1,pid=2d01`;
- TLS negotiates and Android Auto starts in the DHU window.

After DHU exits, restart ADB and confirm the phone is visible again:

```bash
$HOME/Library/Android/sdk/platform-tools/adb kill-server
$HOME/Library/Android/sdk/platform-tools/adb start-server
$HOME/Library/Android/sdk/platform-tools/adb devices -l
```

## Run With ADB

First stabilize the projection with the SDK default config. Do this before using
the Haval probe config:

```bash
$HOME/Library/Android/sdk/extras/google/auto/desktop-head-unit \
  --adb=127.0.0.1:5277 \
  -c "$HOME/Library/Android/sdk/extras/google/auto/config/default_720p.ini"
```

Wrapper default mode:

```bash
scripts/android-auto-sim/run_dhu.sh --adb
```

Probe mode with playback status and cluster windows. Use only after the basic
DHU window reaches a valid Android Auto screen:

```bash
scripts/android-auto-sim/run_dhu.sh --adb --config scripts/android-auto-sim/haval_dhu_probe.ini
```

The wrapper runs:

```bash
adb forward tcp:5277 tcp:5277
desktop-head-unit --adb=127.0.0.1:5277 -c scripts/android-auto-sim/haval_dhu_basic.ini
```

Current device note: ADB tunneling connected to `127.0.0.1:5277`, but did not
produce a valid video surface on the tested phone. Treat ADB mode as secondary
until it reaches the Android Auto launcher.

## Play/Pause Probe

With the phone unlocked and Spotify already playing in Android Auto inside DHU:

```bash
scripts/android-auto-sim/run_dhu.sh --usb \
  --config "$HOME/Library/Android/sdk/extras/google/auto/config/default_720p.ini" \
  < scripts/android-auto-sim/play_pause_probe.dhu
```

Preferred wrapper when collecting evidence:

```bash
scripts/android-auto-sim/capture_phone_dhu_play_pause_probe.sh \
  --run-dhu \
  --reset-usb \
  --require-spotify-playing
```

If you want the Mac to wait until the phone is in a valid setup and then run
the protected probe automatically:

```bash
scripts/android-auto-sim/wait_for_ready_phone_dhu_probe.sh
```

The wait wrapper polls `check_sim_environment.sh` and only opens DHU when
`dhu_phone_probe_ready=1`. If Spotify is not `PLAYING(3)`, it keeps polling
until timeout and does not create a probe artifact.

If a preflight command fails, the wrapper exits with `rc=2` and records
`preflight_failed=1`, the preflight output path, and the latest preflight
artifact in the top-level `summary.txt`. It still does not open DHU.

The probe sends:

- a `20s` settle window after DHU/AOAP/TLS startup;
- `media_play_pause`;
- an `18s` sustained pause window.

The wrapper captures phone-side `dumpsys media_session`, `dumpsys audio`, Android
Auto package version, and ADB device state before the probe, after the probe,
and after a recheck window. It writes `analysis.txt` with verdicts:

- `pass_pause_sustained`: Spotify was playing before the probe and stayed
  `PAUSED(2)` after the probe and recheck window.
- `setup_not_playing`: Spotify was not playing before the probe, so the test
  setup is invalid.
- `failure_still_or_resumed_playing`: Spotify was playing after the probe or
  resumed by recheck.
- `capture_only`: phone state was captured without running DHU.

With `--require-spotify-playing`, the wrapper captures the pre-probe phone
state and refuses to open DHU unless Spotify is already `PLAYING(3)`. This
prevents invalid probe runs when the phone is connected but media is not active.

If Spotify is already `PLAYING(3)` and a prior USB reset is known to pause or
shift focus to Gearhead, run the wait wrapper with `--no-reset-usb`. In the
2026-06-24 phone/DHU run, `resetUsbGadget` changed the pre-probe state to
`PAUSED(2)`, so the guard correctly refused to open DHU.

This wrapper talks only to the USB phone and DHU. It does not connect to the
Haval central, deploy APKs, mount files, or restart native Haval services.

To reclassify an existing artifact without ADB/DHU:

```bash
scripts/android-auto-sim/capture_phone_dhu_play_pause_probe.sh \
  --analyze-only \
  --run-dhu \
  --artifact-dir test-artifacts/aa-dhu-phone-probe-YYYYMMDD-HHMMSS
```

To validate the phone-side and native artifact classifiers with synthetic
fixtures:

```bash
scripts/android-auto-sim/validate_pause_probe_analyzers.sh
```

Confirm the result with:

```bash
$HOME/Library/Android/sdk/platform-tools/adb shell dumpsys media_session | \
  rg -A35 -B5 'com.spotify.music|state=PlaybackState'
```

## Current Result

Latest local run: 2026-06-24 15:04.

- Phone: `moto_g56_5G`, Android Auto `17.0.662234-release`.
- DHU: `2.0-mac`, build `2022-03-30-438482292`.
- ADB server mode reaches `127.0.0.1:5277`, but the tested phone can close the
  DHU immediately if locked and otherwise stays without valid video focus:
  `Don't have video focus - nothing to screenshot`.
- USB accessory mode with the phone unlocked and SDK `default_720p.ini` reaches
  a valid Android Auto session.
- Phone-only baseline after clearing stale Gearhead focus:
  - `--send-media-key play`: `PLAYING(3)`, Spotify media button session active,
    AudioTrack `state:started`;
  - `--send-media-key pause --recheck-seconds 15`: `PAUSED(2)`, AudioTrack
    `state:paused`.
- First protected DHU run with the old `8s` pre-key delay failed:
  `failure_still_or_resumed_playing`; the audio trace showed Gearhead/Spotify
  focus fade-out/fade-in during the command window.
- The probe file now contains only DHU commands and waits `20s` before
  `media_play_pause`, so DHU does not try to execute comments and the command is
  sent after the session settles.
- Protected DHU run with `--no-reset-usb` passed:
  `before_spotify_state=PLAYING(3)`, `after_spotify_state=PAUSED(2)`,
  `recheck_spotify_state=PAUSED(2)`.

Artifacts:

- `/tmp/aa-phone-spotify-baseline-send-play-before-dhu/analysis.txt`
- `/tmp/aa-phone-spotify-baseline-send-pause-before-dhu/analysis.txt`
- `/tmp/aa-dhu-ready-probe-after-phone-baseline/probe/analysis.txt`
- `/tmp/aa-dhu-ready-probe-after-settle-delay/probe/analysis.txt`
- `/tmp/aa-dhu-ready-probe-no-reset-after-settle-delay/probe/analysis.txt`
- `test-artifacts/aa-dhu-unlocked-20260624-133927/summary.txt`
- `test-artifacts/aa-dhu-usb-unlocked-20260624-134121/summary.txt`
- `test-artifacts/aa-dhu-usb-pause-clean-20260624-135024/summary.txt`
- `test-artifacts/aa-dhu-usb-toggle-pause-clean-20260624-135148/summary.txt`

This is a simulation result only. It does not authorize another Haval native
deploy by itself.

## Result Criteria

Success in DHU requires:

- Spotify/phone stays paused for at least `15s` after `media_play_pause` from a
  known playing state;
- Android Auto does not visibly drop/reconnect during the window.

Failure includes:

- audio resumes before `15s`;
- DHU shows paused while the phone/Spotify keeps playing;
- Android Auto drops/reconnects when the keycode is sent.

The observed `media_pause` failure is diagnostic, not the success criterion for
this DHU harness. The next native step is to compare the Haval central path
against the standard headunit toggle semantics without reusing known-bad APKs
or treating app-side generic key injection as a fix.

## Impulse In An Emulator

Installing the Impulse APK in an Android emulator does not connect it to this
DHU session in the same way the Haval central does.

Local AVDs found during this investigation:

- `Automotive_Ultrawide`: Android Automotive OS. It runs apps inside AAOS; it
  is not Android Auto projection from the phone through DHU.
- `Pixel_Tablet_API_33`: regular Android tablet. It does not provide the Haval
  native packages and binders used by Impulse.

What this can test:

- app install and UI boot behavior;
- app-side command mapping with fakes/mocks;
- a deliberately fake host bridge, if one is added for lab-only testing.

What this cannot test:

- `com.ts.androidauto.projectionservice` / `com.ts.androidauto.app`;
- Haval `HardKeyPolicyManager`, `mIsMediaFocus`, D3, or MediaCenter `402`;
- whether a real native headunit command pauses the phone/Spotify.

## Recommended Capture

In another terminal, before the probe:

```bash
$HOME/Library/Android/sdk/platform-tools/adb logcat -c
$HOME/Library/Android/sdk/platform-tools/adb logcat -v time | rg 'AndroidAuto|Car|MediaSession|MediaRouter|Spotify|AudioFocus|MediaBrowser|MediaController|FATAL|ANR'
```

Keep the DHU screenshots:

- `aa-dhu-play-start.png`
- `aa-dhu-pause-15s.png`

## Limits

- DHU does not run the Haval `AndroidAutoService.apk`.
- DHU does not validate MediaCenter `402`, `mIsMediaFocus`, Haval
  `HardKeyPolicyManager`, or D3.
- Passing in DHU does not fix the car; it only reduces suspicion on the phone
  and standard Android Auto path.
- Failing in DHU is useful because it gives a reproducible setup without
  deploying or mounting anything on the head unit.

## Native Comparison Harness

When the central is reachable again, capture the Haval-native state before
changing anything:

```bash
scripts/aa-patches/capture_android_auto_native_pause_state.sh
```

With the vehicle stopped, Android Auto connected, and Spotify already playing,
the closest diagnostic comparison to the DHU `media_play_pause` result is the
patched native HardKeyPolicy path:

```bash
scripts/aa-patches/capture_android_auto_native_pause_state.sh \
  --send aa_hardkey_play_pause_d3 \
  --post-wait 25 \
  --vehicle-stopped
```

The capture script writes `analysis.txt` when the analyzer is available:

```bash
scripts/aa-patches/analyze_android_auto_native_pause_artifact.sh \
  test-artifacts/aa-native-pause-YYYYMMDD-HHMMSS
```

Important analyzer verdicts:

- `blocked_offline`: central ADB/TCP preflight failed; no media command was
  dispatched.
- `capture_only`: state was captured without sending a command.
- `candidate_pause_sustained`: Spotify is paused after the wait window and AA
  audio is not reported as started.
- `failure_ack_without_pause`, `failure_still_playing`, or
  `failure_audio_still_started`: command/debug evidence exists but the phone
  did not prove sustained pause.

This is a diagnostic comparison only. Do not convert it into a dashboard/card
fix unless the artifact proves sustained phone/Spotify pause and the route is
checked against duplicate-toggle risk. Keep the known-bad native
`AndroidAutoService.apk` candidates out of this test.
