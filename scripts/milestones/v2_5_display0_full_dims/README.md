# v2.5 — Display 0 Full-Dimensions Override (Validated Milestone)

**Date**: 2026-05-20
**Status**: Deployed and visually validated on car (Display 0 from car-shortcut now fills full 1920×720)
**Signed APK MD5**: `D8353F6F258CD1C11A43D557980FCCD8`
**APK Size**: 348,526 bytes
**Supersedes**: [v2_4_sidebar_xcrop](../v2_4_sidebar_xcrop/) — keeps all of v2.4's layers (focus / dynamic resize / Y-crop / X-crop) and adds the Display 0 override.

## What this milestone fixes

When AA is launched from the **car's home shortcut** (not via Impulse's
`am stack resize`), Android assigns it a stack with bounds
`Rect(0, 0, 1792, 720)` — 128px narrower than the full 1920×720 display
in `fullscreen` windowing mode. Our v2.0–v2.4 patches faithfully read the
window bounds and laid out the SurfaceView for 1792 wide, leaving visible
gaps on the display edges. Moving AA to Display 3 and back via Impulse
fixed it because Impulse explicitly calls `am stack resize <id> 0 0 1920
720`, which overrides the system's inset.

v2.5 fixes the first-launch case **at the smali layer**: when the
activity is on `displayId == 0`, the v2.0 prologue now ignores the
narrower window bounds and uses `Display.getRealMetrics()` (= true
display dimensions, 1920×720). Non-zero displays (cluster) still use
window bounds so the cluster zone reflow keeps working.

## Confirmed on-vehicle behavior

```
=== Before (v2.4) ===
V25_DIAG: setDisplayParams w=1792 h=720 mode=1 displayId=0
SurfaceView pos=(64,-180) size=(1792,1080)   ← stretched into 1792-wide window

=== After (v2.5) ===
V21_PATCH: setDisplayParams: effective bounds 1920x720
SurfaceView pos=(0,-180) size=(1920,1080)    ← uses full display
```

## What's also in this milestone

All v1.x focus-bypass + v2.0–v2.4 layers, unchanged:

| Layer | Purpose |
|---|---|
| v1.x | Focus / lifecycle stability (onPause / onWindowFocusChanged / hideSurface / releaseSurface / onPauseSetVisibility / finish() block) |
| v2.0 | setDisplayParams prologue reads `Configuration.windowConfiguration.getBounds()` — **with v2.5 Display-0 override** |
| v2.1 | onConfigurationChanged hook + updateDisplayParams passthrough + manifest configChanges expansion |
| v2.2 | SurfaceView Y-crop epilogue (oversize to `windowHeight × 1.5`, `topMargin = −windowHeight / 4`) — clips the 180px AAP encoder padding bars |
| v2.4 | SurfaceView X-crop epilogue gated on `displayId != 0` — hides Google's left app rail on cluster |
| v2.5 | (**new**) Display-0 override using `Display.getRealMetrics()` |
| v2.5-diag | (**new**) `Log.e("V25_DIAG", "setDisplayParams w=W h=H mode=N displayId=I")` once per call — useful for debugging future bound issues, cheap to leave in |

## Tradeoff

v2.3 overscan (Impulse subtracts a few px from Display 0 height to leave
room for the persistent bottom bar) is now bypassed by v2.5 for AA
specifically — AA always uses the full display dims on Display 0. Since
v2.3 wasn't visually working anyway on AA, this is acceptable for now.
If a future v2.x wants to re-enable overscan-aware sizing for AA, it can
read the overscan pref inside the smali override and subtract it before
writing.

## Reproducer scaffold

```powershell
java -jar tools/apktool_3.0.2.jar d -f -o build_v19/app `
    scripts/aa-patches/stock/AndroidAutoApp_stock.apk
python scripts/milestones/v2_5_display0_full_dims/patch_logic.py
java -jar tools/apktool_3.0.2.jar b `
    -o build_v19/AndroidAutoApp_v25_unsigned.apk build_v19/app
# align + sign with debug keystore as per scripts/aa-patches/README.md
```

## Deployment

Same workflow as previous milestones:

```powershell
# Direct push for development
python scripts/milestones/v2_5_display0_full_dims/deploy_to_car.py `
    --ip <CAR_IP> `
    --apk scripts/milestones/v2_5_display0_full_dims/AndroidAutoApp_v25_signed.apk

# Production: bundle in Impulse assets and rebuild
Copy-Item scripts/milestones/v2_5_display0_full_dims/AndroidAutoApp_v25_signed.apk `
          app/src/main/assets/aa_patches/AndroidAutoApp.apk -Force
.\scripts\Deploy-To-Car.ps1
```

## Known limitations

- **Overscan on Display 0 not respected by AA** (see Tradeoff above).
  Workaround: bring AA from Display 3 → Display 0 via Impulse. The first
  shortcut-launch ignores overscan; subsequent Impulse moves respect it
  if you've previously visited a non-zero display. Tracked for a future
  release.
- **Sidebar X-crop ratio (1/15)** is calibrated for Google AA's default
  96dp app rail at 213dpi. If Google updates the rail width, the crop
  offset will need tuning. See parent v2.4 milestone README for the
  tuning table.

## Versioning note

The old `TODO_v2_5_display_type_cluster.md` in `scripts/aa-patches/` was
written when v2.5 was planned to be the AAP Service-APK
`DISPLAY_TYPE_CLUSTER` work. That work has been deferred and the v2.5
number was reassigned to this Display 0 override. The Service-APK plan
should be renumbered (probably v2.6 or v3.0) the next time it's picked up.
