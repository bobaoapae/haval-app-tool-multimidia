# Stock Android Auto APK Archive

This directory holds the **original, unmodified** `AndroidAutoApp.apk` pulled directly from the car, plus any historical versions for reference.

## Files

| File | MD5 | Size | Description |
|------|-----|------|-------------|
| `AndroidAutoApp_stock.apk` | `D12A2AB178AFE8328228564E548CDC5A` | 361,517 bytes | **True stock** — pulled from car's `/vendor/app/AndroidAutoApp/` on 2026-05-16 |
| `AndroidAutoApp_prepatched_v18.apk` | `CDABF96B0582F492F915D5E107699B96` | 361,517 bytes | Previously used as "stock" — actually contains partial patches from earlier sessions (hideSurface, releaseSurface, onWindowFocusChanged, finish already modified) |

## How This Was Obtained

```powershell
# 1. Unmount any bind-mounts to expose the true vendor APK
adb -s <CAR_IP>:5555 shell "umount -l /vendor/app/AndroidAutoApp/AndroidAutoApp.apk"

# 2. Pull the real stock APK
adb -s <CAR_IP>:5555 pull /vendor/app/AndroidAutoApp/AndroidAutoApp.apk scripts/aa-patches/stock/AndroidAutoApp_stock.apk

# 3. Verify hash matches car
adb -s <CAR_IP>:5555 shell "md5sum /vendor/app/AndroidAutoApp/AndroidAutoApp.apk"
# Expected: d12a2ab178afe8328228564e548cdc5a
```

## Important

- The `AndroidAutoApp_prepatched_v18.apk` was historically used as the baseline in previous patching sessions (V9–V18). It already has some methods NOP'd out, which is why the V19 patch script often reported patches as "already applied".
- The `AndroidAutoApp_stock.apk` is the true baseline and should be used for all future patching from scratch.
- When building from scratch, always start with the true stock APK.
