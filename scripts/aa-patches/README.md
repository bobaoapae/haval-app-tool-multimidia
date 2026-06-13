# Android Auto APK Patching Pipeline

This directory contains everything needed to build, patch, and deploy a modified `AndroidAutoApp.apk` that:
- Prevents focus loss on Haval head units (v1.x — focus-bypass).
- Detects the active display resolution at every layout pass (v2.0).
- Reflows the UI live when the app is moved between displays (v2.1 — `onConfigurationChanged`).
- Crops the 1080p AAP buffer's encoder padding so the video fills the cluster zone without black bars (v2.2).
- Hides Google's left app-rail when AA is on a cluster display, keeps it on the main display (v2.4).
- Overrides Display 0 window bounds with `Display.getRealMetrics()` so car-shortcut launches don't get cropped to a 1792-wide stack (v2.5).

See [`patch_guide.md`](patch_guide.md) for the full v1/v2 patch breakdown, the versioning scheme, and on-vehicle verification steps.

> 📋 **Before you start a patch run**: read the **Working conventions** section
> in [`../README.md`](../README.md) for the repo-wide convention on where
> build/decompile output goes. This pipeline historically uses `build_v19/` at
> the repo root (gitignored, working) — keep using it for established
> workflows, but route any ad-hoc / new work through `scripts/.build/` as
> described there.

## Versioning at a glance

Patches use an `x.y` scheme — `x` is the major aim, `y` is the iteration inside it:

| Version | Aim | State |
|---------|-----|-------|
| **v1.x** | Focus-lost-patch effort | v1.19 = first validated milestone, in `../milestones/v19_focus_bypass/` (folder kept under its original `v19_*` name; conceptually v1.19) |
| **v2.x** | Dynamic resize on display moves + AAP video adaptation | v2.0 = setDisplayParams reads live window bounds; v2.1 = onConfigurationChanged + updateDisplayParams passthrough; v2.2 = SurfaceView Y-crop to hide encoder padding bars; v2.4 = X-crop hides Google AA's left app rail on cluster; **v2.5 = current**, [`../milestones/v2_5_display0_full_dims/`](../milestones/v2_5_display0_full_dims/) — adds Display 0 override using `Display.getRealMetrics()` so car-shortcut launches fill the full 1920×720. Includes all v1 patches. |
| **v2.3** | Overscan-aware Display 0 bounds | Code shipped in Impulse but visual change unconfirmed and now superseded for AA by v2.5 (which bypasses overscan on Display 0). Notes parked in [TODO_v2_3_overscan_investigation.md](TODO_v2_3_overscan_investigation.md). |
| **v2.6+** | AAP `DISPLAY_TYPE_CLUSTER` (planned) | Service-APK patch to advertise cluster displays as `DISPLAY_TYPE_CLUSTER` so the phone natively renders cluster UI (no sidebar, no rendering wasted on hidden bands). Supersedes the v2.4 crop trick. See [TODO_v2_5_display_type_cluster.md](TODO_v2_5_display_type_cluster.md) (the file name predates the v2.5 reassignment). |

## Quick Reference

| What | Where |
|------|-------|
| Stock APK | `stock/AndroidAutoApp_stock.apk` |
| **Current patch script (v2.5)** | [`patch_logic.py`](patch_logic.py) — applies v1.x + v2.0 + v2.1 + v2.2 + v2.4 + v2.5 + v2.5-diag |
| **Latest validated milestone** | [`../milestones/v2_5_display0_full_dims/`](../milestones/v2_5_display0_full_dims/) — v2.5 signed APK + patch snapshot + deploy script |
| Earlier milestones | `../milestones/v2_4_sidebar_xcrop/` (v2.4, sidebar X-crop), `../milestones/v2_2_dynamic_resize/` (v2.2, dynamic resize + Y-crop), `../milestones/v19_focus_bypass/` (v1.19, focus-only) |
| Deploy script (standalone, reusable) | Each milestone ships its own `deploy_to_car.py`; takes `--apk` so the same code works for any milestone |
| Build output | `../../build_v19/` (project root, gitignored — folder name kept from v1.19 era) |
| apktool | `../../tools/apktool_3.0.2.jar` (gitignored — download once from `iBotPeaches/Apktool` releases) |
| Assets for in-app deploy | `../../app/src/main/assets/aa_patches/AndroidAutoApp.apk` |

## Full Build & Deploy Pipeline

### Prerequisites
- Java (JRE 8+) in PATH
- Python 3.x in PATH
- `tools/apktool_3.0.2.jar` (already downloaded)
- Android SDK build-tools at `C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\`
- Debug keystore at `C:\Users\vanes\.android\debug.keystore` (password: `android`)
- Car connected via Wi-Fi (ADB port 5555, Telnet port 23)

### Step-by-Step

All commands run from the **project root** (`haval-app-tool-multimidia/`):

```powershell
# 1. Disassemble stock APK into Smali
java -jar tools/apktool_3.0.2.jar d -f -o build_v19/app scripts/aa-patches/stock/AndroidAutoApp_stock.apk

# 2. Apply all v1.x + v2.x patches (focus bypass + dynamic resize on config change)
python scripts/aa-patches/patch_logic.py

# 3. Reassemble patched Smali into APK
java -jar tools/apktool_3.0.2.jar b -o build_v19/AndroidAutoApp_v19_unsigned.apk build_v19/app

# 4. Align
& "C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\zipalign.exe" -f 4 build_v19\AndroidAutoApp_v19_unsigned.apk build_v19\AndroidAutoApp_v19_aligned.apk

# 5. Sign
& "C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat" sign --ks C:\Users\vanes\.android\debug.keystore --ks-pass pass:android --out build_v19\AndroidAutoApp_v19_signed.apk build_v19\AndroidAutoApp_v19_aligned.apk

# 6. Verify signature
& "C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat" verify --verbose build_v19\AndroidAutoApp_v19_signed.apk

# 7a. Deploy directly to car for quick iteration (push + Telnet bind-mount)
python scripts/milestones/v19_focus_bypass/deploy_to_car.py --ip <CAR_IP>

# 7b. -- OR -- ship inside the Haval app: copy the signed APK into assets
#     and rebuild + deploy the Haval app, which manages the mount via
#     AndroidAutoPatchManager.kt on the user's car.
Copy-Item build_v19\AndroidAutoApp_v19_signed.apk `
          app\src\main\assets\aa_patches\AndroidAutoApp.apk -Force
.\scripts\Deploy-To-Car.ps1
```

> Path 7a writes the bind mount from your PC directly and is the right
> iteration loop while developing patches. Path 7b is the production flow —
> end users install the Haval app and the manager copies the APK out of
> assets and mounts it on boot (see `AndroidAutoPatchManager.ensureMounted`).

### Finding the Car IP

The car's IP changes on each Wi-Fi session. Find it via ARP:
```powershell
arp -a
# Look for entries on the 192.168.33.x subnet (excluding your own IP)
# Then: adb connect <IP>:5555
```

### What the Patches Do

| # | Layer | Target | Patch | Purpose |
|---|-------|--------|-------|---------|
| 1 | v1.x | `onPause()` | Force `mIsOnPause = false` | Prevents pause state from sticking |
| 2 | v1.x | `onWindowFocusChanged()` | Force `hasFocus = true` | Locks focus — app always thinks it has focus |
| 3 | v1.x | `hideSurface()` | `return-void` (NOP) | Prevents video surface from being hidden |
| 4 | v1.x | `releaseSurface()` | `return-void` (NOP) | Prevents decoder destruction |
| 5 | v1.x | `onPauseSetVisibility()` | `return-void` (NOP) | Prevents visibility toggle on pause |
| 6 | v1.x | `finish()` calls | Commented out | Prevents app from closing itself |
| 7 | v2.0 | `setDisplayParams()` (prologue) | Prepend `Configuration.windowConfiguration.getBounds()` read of width/height into `mAapDisplayAreaWidth/Height` | Lays out the projection to the *current window* every call (not the physical display — important when Impulse stack-resizes AA into a sub-area of the display) |
| 8 | v2.1 | `onConfigurationChanged()` | New override → `super` + `setDisplayParams()` | Re-applies layout when the activity is resized in-place (after the v2.1 manifest change) |
| 9 | v2.1 | `updateDisplayParams(AapVideoConfig)` | Replace body with passthrough to `setDisplayParams()` | Stops the async AAP mode handler from clobbering the SurfaceView back to a hardcoded 1080p config after our resize |
| 10 | v2.1 | `AndroidManifest.xml` | Extend `android:configChanges` to include `screenSize\|smallestScreenSize\|screenLayout\|orientation\|density\|navigation\|keyboard\|keyboardHidden\|locale\|fontScale` (in addition to `uiMode`) | Stops Android from recreating the activity on display moves/resizes so the v2.1 hook actually fires |
| 11 | v2.2 | `setDisplayParams()` (epilogue) | After layout, oversize SurfaceView to `windowHeight × 1.5` with `topMargin = −windowHeight / 4` | Causes the parent `FrameLayout` to clip out the 180px black bars the phone encodes around the 1080p buffer's 720-row content area |
| 12 | v2.4 | `setDisplayParams()` (second epilogue) | When `getDisplay().getDisplayId() != 0`: oversize SurfaceView width to `windowWidth × 16/15`, set `leftMargin = −windowWidth / 15`, override `LayoutParams.gravity = LEFT \| TOP` | Hides Google AA's ~128px left app rail on cluster displays; skips on Display 0 so the rail remains usable there |
| 13 | v2.5 | `setDisplayParams()` (prologue, inside v2.0) | If `getDisplay().getDisplayId() == 0`, overwrite the window-bound width/height with `Display.getRealMetrics()` | Fixes car-shortcut launches where the system stack is 1792×720 instead of the full 1920×720. AA always uses the full display on the main screen. |
| 14 | v2.5-diag | `setDisplayParams()` (entry) | `Log.e("V25_DIAG", "setDisplayParams w=W h=H mode=N displayId=I")` | Cheap diagnostic that records what bounds + windowing mode + display each call sees. Useful for future bound-related investigations. |

The v1 patches preserve the live projection session across display moves.
Without v2.0 + v2.1 the projection would survive but stay at its initial size.
Without v1 the v2.1 trigger would still recreate the activity from scratch.
Without v2.2, v2.1 reflows the layout correctly but leaves visible black
bars top/bottom because of how the phone encodes 1080p.
Without v2.4, the cluster shows Google's app rail wasting horizontal space.
Without v2.5, the first launch from the car shortcut leaves gaps on
Display 0 because the system stack is narrower than the display.
All six layers are required for the seamless full-screen + cluster
experience.

### Rolled Back v2.6 No-Rail Experiment

In 2026-06-10 field testing, a v2.6 experiment removed only the v2.4 X-crop
block so Google's Android Auto app rail would remain visible on Display 3.
That APK caused a black Android Auto screen on Display 0 and Display 3, so it
was rolled back immediately to the validated v2.5 APK. Do not re-enable
`PRESERVE_AA_APP_RAIL_ON_CLUSTER` without a new APK-level investigation and
physical test.

### Important: Stock APK State

The file `stock/AndroidAutoApp_stock.apk` is **partially pre-patched** from earlier work sessions. Specifically:
- `hideSurface()`, `releaseSurface()` → already NOP'd
- `onWindowFocusChanged()` → already has `const/4 p1, 0x1`
- `finish()` → already commented out

The v2.1 patch script (`patch_logic.py`) is fully idempotent — it uses sentinel comments to detect prior injections and skip cleanly. The legacy `patch_v19_focus.py` in the milestone is *almost* idempotent (the `onPause` and `finish()` blocks would re-stack on multiple runs); always disassemble from the stock APK fresh before running it. The current `patch_logic.py` does not have this problem.

Smali method-signature note: this stock APK declares `setDisplayParams()` with `.locals 5` (current apktool output), not `.registers 5` (older apktool output). The v2.1 patch matches both forms; the legacy `patch_resize.py` under `scripts/experimental/resize/` only matches `.registers 5` and is therefore obsolete for the current stock.

Sentinel-string note: the comments embedded in patched smali (`# V2_ONPAUSE_INJECTION`, `# V2_FINISH_BLOCKED`, `# V3_RESIZE_INJECTION`, `# V4_ONCONFIGCHANGED_INJECTION`) keep their legacy `V2/V3/V4` prefixes on purpose. They are an on-disk format that APKs in the wild already carry; renaming them to `v1`/`v2` would silently re-apply already-applied patches. The Python *section labels* follow the new x.y scheme; the *sentinel strings* are frozen.

### Deployment Architecture

```
PC (this machine)
  │
  ├── ADB push ──────► /data/local/tmp/v19_app.apk
  │                         │
  └── Telnet (root) ──► umount -l  (clear stale mounts)
                        rm dalvik-cache  (force fresh DEX load)
                        chcon + chmod  (SELinux context)
                        mount --bind ──► /vendor/app/AndroidAutoApp/AndroidAutoApp.apk
                        am force-stop + am start (restart AA)
```

### Milestone Promotion

After validating on the car, the APK and scripts are saved to `../milestones/v19_focus_bypass/` with full reproduction instructions.

### Troubleshooting

- **APK not loading**: Delete dalvik cache and remount
- **"Device or resource busy" on umount**: Use `umount -l` (lazy unmount), repeat until clear
- **Stale double mounts**: Run `umount -l` 3x to clear all layers, then remount once
- **Focus still lost**: Check if Service APK also needs patching — see `../experimental/patches/`
- **Car IP changed**: Run `arp -a` to find the new IP on the 192.168.33.x subnet
- **AA video area doesn't resize after a display move**: Verify the v2.1 manifest patch landed in the *deployed* APK with `aapt2 dump xmltree /vendor/app/AndroidAutoApp/AndroidAutoApp.apk AndroidManifest.xml | grep configChanges` — should print the full flag list, not just `uiMode`. If only `uiMode` shows, the bind mount didn't pick up the new APK (clear dalvik cache and remount). The patch has been verified to round-trip cleanly through `apktool b` — the binary AXML manifest preserves all flags.
- **`setDisplayParams` crashes on entry**: the v2.0 prologue guards against zero-sized metrics (`if-lez widthPixels`), but if you see a crash inside our prologue, it most likely means the stock APK has a different register-count declaration on `setDisplayParams` that the regex didn't match. Confirm with `head -3 build_v19/app/smali/com/ts/androidauto/app/display/AapActivity.smali | grep -A1 setDisplayParams`.
