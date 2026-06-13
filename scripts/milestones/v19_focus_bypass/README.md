# V19 Focus-Bypass — Validated Milestone

**Date**: 2026-05-16
**Status**: Deployed to car, pending user validation
**APK MD5**: `1EE968990299EB9AD9B963B31D119154`
**APK Size**: 348,526 bytes

## Contents

| File | Purpose |
|------|---------|
| `AndroidAutoApp_v19_signed.apk` | Ready-to-deploy signed APK |
| `patch_v19_focus.py` | Patch script (can recreate from stock APK) |
| `deploy_to_car.py` | Deployment script (ADB push + Telnet mount) |

## What This Milestone Does

Prevents Android Auto from losing focus by neutralizing 6 lifecycle/focus methods in `AapActivity.smali`:

| # | Method | Patch | Effect |
|---|--------|-------|--------|
| 1 | `onPause()` | Force `mIsOnPause = false` after `super.onPause()` | App never enters paused state |
| 2 | `onWindowFocusChanged(Z)` | Force `hasFocus = true` (`const/4 p1, 0x1`) | App always believes it has focus |
| 3 | `hideSurface()` | `return-void` (NOP) | Video surface never hidden |
| 4 | `releaseSurface()` | `return-void` (NOP) | Decoder never destroyed |
| 5 | `onPauseSetVisibility()` | `return-void` (NOP) | Visibility never toggled on pause |
| 6 | `finish()` calls | Commented out | App cannot close itself |

## How to Reproduce from Scratch

Run all commands from the project root (`haval-app-tool-multimidia/`):

```powershell
# 1. Disassemble stock APK
java -jar tools/apktool_3.0.2.jar d -f -o build_v19/app scripts/aa-patches/stock/AndroidAutoApp_stock.apk

# 2. Apply focus patches
python scripts/aa-patches/patch_v19_focus.py

# 3. Reassemble
java -jar tools/apktool_3.0.2.jar b -o build_v19/AndroidAutoApp_v19_unsigned.apk build_v19/app

# 4. Align
& "C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\zipalign.exe" -f 4 build_v19\AndroidAutoApp_v19_unsigned.apk build_v19\AndroidAutoApp_v19_aligned.apk

# 5. Sign
& "C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat" sign --ks C:\Users\vanes\.android\debug.keystore --ks-pass pass:android --out build_v19\AndroidAutoApp_v19_signed.apk build_v19\AndroidAutoApp_v19_aligned.apk

# 6. Verify
& "C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat" verify --verbose build_v19\AndroidAutoApp_v19_signed.apk
```

## How to Deploy

```powershell
# Option A: Use the deploy script (auto-detects car IP)
python scripts/aa-patches/deploy_to_car.py --ip <CAR_IP>

# Option B: Manual deployment
adb -s <CAR_IP>:5555 push build_v19\AndroidAutoApp_v19_signed.apk /data/local/tmp/v19_app.apk
# Then via Telnet (port 23, root shell):
umount -l /vendor/app/AndroidAutoApp/AndroidAutoApp.apk
chcon u:object_r:vendor_app_file:s0 /data/local/tmp/v19_app.apk
chmod 644 /data/local/tmp/v19_app.apk
mount --bind /data/local/tmp/v19_app.apk /vendor/app/AndroidAutoApp/AndroidAutoApp.apk
am force-stop com.ts.androidauto.app
am start -n com.ts.androidauto.app/com.ts.androidauto.app.display.AapActivity
```

## Important Notes

### Stock APK Is Partially Pre-Patched
The file `scripts/aa-patches/stock/AndroidAutoApp_stock.apk` already contains some patches from earlier work:
- `hideSurface()` → already NOP'd
- `releaseSurface()` → already NOP'd
- `onWindowFocusChanged()` → already has `const/4 p1, 0x1`
- `finish()` calls → already commented out

The patch script handles this correctly — it skips patches that are already applied and applies any that are missing. If starting from a truly virgin stock APK, all 6 patches will be applied.

### No Resize in This Milestone
This milestone is focus-stability only. Display resize (AapResizer) is tracked separately in `scripts/experimental/resize/`.

### OAT Directory
On some car firmware versions, `/vendor/app/AndroidAutoApp/oat` doesn't exist. The deploy script attempts to mount an empty directory over it but won't fail if the path is missing.

### Dalvik Cache
Always delete dalvik cache entries before mounting, otherwise the system may use the old optimized DEX:
```
rm /data/dalvik-cache/arm64/vendor@app@AndroidAutoApp@AndroidAutoApp.apk@classes.dex
rm /data/dalvik-cache/arm64/vendor@app@AndroidAutoApp@AndroidAutoApp.apk@classes.vdex
```

## Tools Required

| Tool | Version | Path |
|------|---------|------|
| apktool | 3.0.2 | `tools/apktool_3.0.2.jar` |
| apksigner | 0.9 | `C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat` |
| zipalign | — | `C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\zipalign.exe` |
| Java | 8+ | System PATH |
| Python | 3.x | System PATH |
| ADB | — | `C:\Users\vanes\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| Debug keystore | — | `C:\Users\vanes\.android\debug.keystore` (password: `android`) |

## Verification

After deployment, verify on the car:
```
# Via Telnet — MD5 should match: 1EE968990299EB9AD9B963B31D119154
md5sum /vendor/app/AndroidAutoApp/AndroidAutoApp.apk
md5sum /data/local/tmp/v19_app.apk

# Mount should show bind mount
mount | grep AndroidAutoApp
```

Or use the diagnostics dialog in the Impulse app (Bug icon in the Install Apps tab).
