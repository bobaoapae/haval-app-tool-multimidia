# v2.4 — AAP Sidebar X-Crop on Cluster (Validated Milestone)

**Date**: 2026-05-18
**Status**: Deployed and visually validated on car (sidebar hidden on cluster, kept on Display 0)
**Signed APK MD5**: `BEC4B1778930AC1A40EC4CF748F90A69`
**APK Size**: 348,526 bytes
**Supersedes**: [v2_2_dynamic_resize](../v2_2_dynamic_resize/) — adds the X-axis crop on top.

## What this milestone is

The first AndroidAutoApp build that hides Google's AA app rail (the
vertical strip of Maps/Music/Messages/etc. icons on the left edge) when
projecting onto a cluster display — without losing it on the main display
where the user wants it.

The phone always *renders* the rail into the AAP video buffer; we just
push it off-screen on cluster displays by oversizing the SurfaceView's
width and applying a negative `leftMargin`. The parent `FrameLayout`
clips the result so only the sidebar-less right portion of the buffer is
visible inside the cluster zone.

## Layers included

All v2.2 layers plus:

| Layer | Target | Patch |
|---|---|---|
| ... | ... | (v1.x focus + v2.0/2.1/2.2 layers — see v2.2 milestone README) |
| **v2.4** | `setDisplayParams` (epilogue, second pass) | When `getDisplay().getDisplayId() != 0`: oversize SurfaceView width to `windowWidth × 16/15`, set `leftMargin = −windowWidth / 15`, and override `LayoutParams.gravity = LEFT \| TOP (0x33)` to disable the XML `center_horizontal`. |

## Why the v2.4 math works

Google AA's "App rail" is approximately 96dp wide. The head unit reports
213dpi to the phone (`mAapResolutionReportDpi` in `VehicleInfoLoader
.initAapVideoConfig1080p`), so 96dp ≈ **128px** in the 1920-wide source
buffer (~1/15 of the width). Using 1/15 keeps the smali integer-math
clean: multiply by `0x10` (16), divide by `0xf` (15).

```
SurfaceView.width      = windowWidth × 16 / 15
SurfaceView.leftMargin = −windowWidth / 15
SurfaceView.gravity    = LEFT | TOP  (0x33)
```

| Window | Result |
|---|---|
| Cluster Display 3 (1920 wide window) | width = 2048, leftMargin = −128. Source col 128 maps to view col 0 → sidebar clipped, content fills 1920 cluster cols. |
| Display 0 (1792 effective after nav-bar inset, or 1920 raw) | **skipped** — `displayId == 0` short-circuits the patch. Sidebar stays visible (user wants it for app launching there). |

The gravity override is critical: the XML layout (`activity_display.xml`)
declares `android:layout_gravity="center_horizontal"` on the SurfaceView.
Without overriding, our oversized 2048-wide SurfaceView would be centered
inside the 1920-wide FrameLayout, intrinsically shifting it by −64. That
combined with our −128 leftMargin gave a net −192 position in the first
v2.4 cut, leaving a 64px black gap on the right of the cluster window.
Setting gravity to `LEFT | TOP` makes the SurfaceView stick to the left
edge and leftMargin act cleanly.

## On-vehicle evidence

```
=== Display 0 (sidebar kept) ===
SurfaceView pos=(64,-180), size=(1792,1080)   ← unchanged from v2.2

=== Display 3 cluster (sidebar hidden) ===
SurfaceView pos=(-128,-180), size=(2048,1080)
VisibleRegion=[0,62,1920,658]
```

User visual confirmation 2026-05-18: AA's app rail is gone on cluster,
present on Display 0. Cross-display moves still preserve the activity
(`launchCount=1`, no recreate).

## Known tradeoffs

- **Right-side content clipped by ~6.7%** on cluster. Because we scale the
  source buffer 16/15 horizontally to fit the cluster after hiding the
  sidebar, content that was at the right edge of the AAP frame is now
  slightly off-screen on the right. In practice this is negligible — the
  AA UI keeps its main content well inside the safe area.
- **Sidebar still rendered by the phone, just not displayed.** The phone
  is still spending encode bandwidth on the rail, just for nothing on
  cluster. To stop the phone from rendering it at all, we need to
  advertise the display as `DISPLAY_TYPE_CLUSTER` via the AAP protocol —
  that's the v2.5 plan (Service APK patch).
- **Fixed sidebar width assumption.** The 1/15 ratio is calibrated for
  Google AA's default 96dp app rail. If a phone-side update changes the
  rail width, the crop offset will be wrong (too much or too little).
  Tuning constants are documented in the next section.

## Tuning the crop width

If the sidebar shows residue or the crop is too aggressive, change the
smali constants in `patch_logic.py` → `V2_4_XCROP_CODE`:

| Constant pair | Hidden source px (1920 buffer) | When to pick |
|---|---|---|
| `mul × 0x11, div / 0x10` (17/16) | 113 | If 128px crops too much |
| `mul × 0x10, div / 0xf` **← current (16/15)** | **128** | Matches AAP 96dp @ 213dpi default |
| `mul × 0xf, div / 0xe` (15/14) | 137 | If sidebar shows ~10px residue |
| `mul × 0xd, div / 0xc` (13/12) | 160 | If sidebar is noticeably wider in your build |
| `mul × 0xb, div / 0xa` (11/10) | 192 | If sidebar is much wider (icon labels visible) |

leftMargin uses the same denominator: `div-int/lit8 v1, v1, 0xf` for 1/15.

## Deployment

Same workflow as v2.2:

### Path A — Direct push for development

```powershell
python scripts/milestones/v2_4_sidebar_xcrop/deploy_to_car.py `
    --ip <CAR_IP> `
    --apk scripts/milestones/v2_4_sidebar_xcrop/AndroidAutoApp_v24_signed.apk
```

### Path B — Ship inside Impulse (production)

```powershell
Copy-Item scripts/milestones/v2_4_sidebar_xcrop/AndroidAutoApp_v24_signed.apk `
          app/src/main/assets/aa_patches/AndroidAutoApp.apk -Force
.\scripts\Deploy-To-Car.ps1
# On the car, Impulse's "Install patches" + "Apply mounts" picks up v2.4.
```

## How to reproduce from stock

```powershell
java -jar tools/apktool_3.0.2.jar d -f -o build_v19/app `
    scripts/aa-patches/stock/AndroidAutoApp_stock.apk
python scripts/milestones/v2_4_sidebar_xcrop/patch_logic.py
# Reassemble + align + sign — see scripts/aa-patches/README.md for full sequence.
```

## What's next: v2.5

The v2.4 crop is a structural workaround. The right architectural fix is
`DISPLAY_TYPE_CLUSTER` advertised via the AAP protocol from the Service
APK. See [TODO_v2_5_display_type_cluster.md](../../aa-patches/TODO_v2_5_display_type_cluster.md)
for the planned approach.
