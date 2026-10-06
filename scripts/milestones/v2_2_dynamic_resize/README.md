# v2.2 — Dynamic Resize with AAP Padding Crop (Validated Milestone)

**Date**: 2026-05-18
**Status**: Deployed and visually validated on car (cluster + Display 0, cross-display moves)
**Signed APK MD5**: `231CBF1CC0F46076BE9FABB0156FF156`
**APK Size**: 348,526 bytes
**Supersedes**: [v19_focus_bypass](../v19_focus_bypass/) (v1.19) which provided only the focus-bypass layer (V2) without dynamic resize.

## What this milestone is

The first AndroidAutoApp build where the AA video correctly reflows to fit
the activity's window when the user moves it between displays — *with* the
1080p AAP buffer's encoder padding cropped away. Earlier cuts under the
v2.x effort either didn't react to display moves at all (v2.0), reflowed
but kept a stale 1080p SurfaceView from a separate update path (v2.1
preview), or reflowed correctly but inherited 1080p padding bars (v2.1.2).
v2.2 puts all three layers together.

## Layers included (all in `AapActivity.smali` unless noted)

| Layer | Target | Patch |
|---|---|---|
| **v1.x** focus | `onPause` | Force `mIsOnPause = false` after `super.onPause()` |
| **v1.x** focus | `onWindowFocusChanged` | Force `hasFocus = true` |
| **v1.x** focus | `hideSurface` / `releaseSurface` / `onPauseSetVisibility` | NOP |
| **v1.x** focus | `finish()` calls | Commented out |
| **v2.0** resize | `setDisplayParams` | Prologue reads `Configuration.windowConfiguration.getBounds()` and writes window dimensions into `mAapDisplayAreaWidth/Height` |
| **v2.1** resize | `onConfigurationChanged` | New override calling `setDisplayParams()`; manifest `configChanges` expanded to absorb screen-size changes without recreating the activity |
| **v2.1** resize | `updateDisplayParams(AapVideoConfig)` | Replaced body with passthrough to `setDisplayParams()` so the async mode-change handler no longer resets the SurfaceView to 1920×1080 |
| **v2.2** crop | `setDisplayParams` (epilogue) | After applying layout, oversize the SurfaceView to `windowHeight × 1.5` and apply `topMargin = −windowHeight / 4` so the parent `FrameLayout` clips out the 180px black bars the phone encodes around its 720-row content area |

All patches are idempotent (sentinel-comment guarded). The full patch
script is bundled as `patch_logic.py` in this folder.

## Why the v2.2 crop math works

`VehicleInfoLoader.initAapVideoConfig1080p` declares the "1080p" mode as
a 1920×1080 buffer where the *logical* video is only the inner 1920×720
(rows 180..900); top/bottom 180 rows are encoder padding. Stock AA crops
those bars by scrolling a 1080-tall `SurfaceView` inside a 720-tall
`LinearLayout` by 180 px. We generalize that trick to any window size:

```
SurfaceView.height   = windowHeight × 3 / 2
SurfaceView.topMargin = −windowHeight / 4
```

| Window | SurfaceView dims | topMargin | Result |
|---|---|---|---|
| Display 0 (1920×720) | 1920×1080 | −180 | Matches the stock 1080p layout exactly |
| Cluster (1920×596) | 1920×894 | −149 | Inner content scales to fill 596 rows, padding bars clipped out of view |

Verified on-vehicle via `dumpsys SurfaceFlinger`:
- Display 0: `SurfaceView pos=(_,−180), size=(1792,1080)` ✓
- Cluster: `SurfaceView pos=(0,−87), size=(1920,894), VisibleRegion=[0,62,1920,658]` ✓

## Known tradeoffs (intentional, not bugs)

- **Slight vertical squeeze on the cluster.** The inner 720-row content is
  scaled non-uniformly to 596 rows (~17% vertical compression). Going
  further requires renegotiating AAP resolution with the phone, which
  lives in the Service APK — out of scope for this milestone.
- **The bottom-bar overscan on Display 0 still overlays AA's video** —
  it doesn't shrink AA's window. That's an Impulse-side feature gap
  (`DisplayAppLauncher.getEffectiveBounds` doesn't subtract overscan on
  Display 0), targeted in v2.3.
- **Mid-session display moves keep the AAP session alive.** Resolution
  was negotiated once at handshake time, so the buffer aspect ratio is
  fixed. Our crop adapts the layout but cannot change what the phone
  encodes.

## How to deploy

### Path A — Direct push for development iteration

```powershell
python scripts/milestones/v2_2_dynamic_resize/deploy_to_car.py `
    --ip <CAR_IP> `
    --apk scripts/milestones/v2_2_dynamic_resize/AndroidAutoApp_v22_signed.apk
```

This bind-mounts the signed APK over `/vendor/app/AndroidAutoApp/AndroidAutoApp.apk`
via Telnet, clears the dalvik cache, and restarts AA. The Service APK
mount Impulse already applied stays intact so `AndroidAutoPatchManager.isMounted()`
keeps returning true and won't clobber us on the next foreground-service
boot. Reboot reverts to whatever Impulse has in its assets.

### Path B — Bundle in Impulse assets (production path)

```powershell
Copy-Item scripts/milestones/v2_2_dynamic_resize/AndroidAutoApp_v22_signed.apk `
          app/src/main/assets/aa_patches/AndroidAutoApp.apk -Force
.\scripts\Deploy-To-Car.ps1
```

Then trigger "Install patches" + "Apply mounts" from Impulse. The Haval
companion app will copy v2.2 to `/data/local/tmp/aa_patches/` and bind-
mount it. This is the production path; new Impulse installs/updates ship
v2.2 by default.

## How to reproduce from stock

```powershell
# 1. Disassemble stock APK
java -jar tools/apktool_3.0.2.jar d -f -o build_v22/app `
    scripts/aa-patches/stock/AndroidAutoApp_stock.apk

# 2. Apply patches (this milestone's patch_logic.py is a snapshot;
#    the active one at scripts/aa-patches/patch_logic.py may have evolved)
python scripts/milestones/v2_2_dynamic_resize/patch_logic.py
# (will need BUILD_DIR adjusted if you keep using build_v22 instead of build_v19)

# 3. Reassemble + align + sign — see scripts/aa-patches/README.md for the
#    full apktool/zipalign/apksigner command sequence.
```

## Validation evidence

- `launchCount=1` preserved across `am display move-stack` between
  Display 0 and Display 3, confirming the activity does not recreate.
- `dumpsys SurfaceFlinger` shows `SurfaceView` geometry that exactly
  matches the crop formula on both displays.
- `V21_PATCH` log lines confirm `setDisplayParams` and
  `onConfigurationChanged` hooks fire on every resize and read the live
  `Configuration.windowConfiguration.getBounds()`.
- User visual confirmation on 2026-05-18: video fills cluster zone, no
  black bars, smooth reflow on display moves.

## Build tools

| Tool | Version | Notes |
|---|---|---|
| apktool | 3.0.2 | `tools/apktool_3.0.2.jar` (gitignored) |
| zipalign | build-tools 36.1.0 | `…\AppData\Local\Android\Sdk\build-tools\36.1.0\zipalign.exe` |
| apksigner | build-tools 36.1.0 | Signed with the user's debug keystore (`%USERPROFILE%\.android\debug.keystore`, password `android`) |
| Signing scheme | v3 only | Same as v1.19 milestone — Android 9 head unit accepts it |
