# AA App v3.2 — media-button bridge (bundled md5 `a8865d2b`)

**Problem.** Android Auto registers no Android `MediaSession`, so a paused local app (YouTube)
stays the OS media-button session. The car's native route delivers steering-wheel
PLAY/PAUSE, NEXT and PREVIOUS to that session, so each press paused one app and started the
other. June's fix (v2.6, `fc4d5ea`/`82e6d5f`) only ever lived on the v6 branch.

**Fix.** `AppMediaButtonBridge` (smali in this folder) owns a `MediaSession` ("HavalAaApp",
HANDLES_MEDIA_BUTTONS|TRANSPORT, state PLAYING), installed from `AndroidAutoRemoteUiService.onCreate`
and `AapActivity.onCreate`, re-claimed in `AapActivity.onResume`, never released while the App
process lives. Android then routes the wheel to it while AA plays. `onMediaButtonEvent` maps
85→0x3ec (toggle), 87→0x3eb (next), 88→0x3ea (previous) and sends the car's own hardkey via
`ImpulseHardKeyDispatcher` (on a `HandlerThread`), so the phone applies a real toggle. Discrete
PLAY/PAUSE (126/127) fall through to the default callbacks.

Impulse companion change: no delayed LinkCommand skip fallback for wheel NEXT/PREVIOUS
(`DisplayAppLauncher`); it double-skipped AA and reached YouTube.

## Build — dex only, never re-pack resources
An apktool round trip of v2.8 re-packs `resources.arsc`, and every such build crashed the App
once on start (SIGSEGV in `LoadedPackage::GetEntryOffset` from `View.toString` on detach,
2026-10-13). Ship v2.8's original resources and manifest with only `classes.dex` replaced:

```
apktool d -f -o tree <v2.8 AndroidAutoApp.apk, md5 cdaae63d>
python scripts/aa-patches/patch_logic.py  # or apply_v29_fullscreen_patch only, on top of v2.8
python inject.py tree AppMediaButtonBridge.smali
apktool b -o rebuilt.apk tree             # only for its classes.dex
# copy every entry of the v2.8 APK except META-INF/*, with classes.dex from rebuilt.apk,
# keeping each entry's compression; then:
zipalign -f 4 unsigned.apk aligned.apk
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --out signed.apk aligned.apk
```

## Swapping the App on a running car
Never copy over the bind-mounted file in place (a restarting App reads it half-written). Stage
`.new` with the stock mtime + `vendor_app_file` label, `umount -l` the vendor path, `mv` it in,
`mount --bind` again, clear the App's dalvik cache, then `am force-stop com.ts.androidauto.app`.

## On the car (2026-10-13)
- v3.2 dex-only: no crash; PLAY/PAUSE drives only AA (YouTube stays paused).
- With the old Impulse fallback still active, PREVIOUS skipped two songs and NEXT also moved
  YouTube. The fallback removal ships with this change — **not yet verified on the car**.
