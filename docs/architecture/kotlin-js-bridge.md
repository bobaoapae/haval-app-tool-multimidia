# Kotlin JS Bridge

Updated: 2026-08-06

## Current contract themes (`v1.0` / bridge `1.0.0`)

Contract themes talk to the host through `window.Android` (see `ThemeBridgeImpl.kt` and the full table in [`THEME_GUIDE.md`](../../cluster-widgets/Themes/THEME_GUIDE.md)).

Typical theme → host flow:

- `subscribe(keysJson)` / `unsubscribe(keysJson)` — client-driven telemetry
- `getCarData` / `updateCarData` — read cache / write vehicle settings
- `heartbeat()` — WebView liveness
- Layout / wallpaper / preferences / `launchApp` — as documented in THEME_GUIDE

Host → theme:

- Telemetry updates for subscribed keys
- Raw steering events via `window.onKeyEvent(key)` (`UP`, `DOWN`, `ENTER`, `BACK`, …)

System layout and versioning: [`themes-contract-v1.md`](themes-contract-v1.md).

`CompatTranslationLayer.kt` supplies JS polyfills when a theme’s `minBridgeVersion` is older than the host bridge.

## Legacy host → JS helpers (still present)

`InstrumentProjector2` can still push into the page with `evaluateJavascript`, historically via:

- `evaluateJsIfReady` / `batchEvaluateJs` / `updateValuesWebView`
- `ServiceManager` listeners

Older / transitional globals include:

```text
control('key', value)
showScreen(...)
focus(...)
updateWarning(...)
clearWarnings()
```

New `v1.0` themes should prefer **subscribe + `onKeyEvent`**, not assume a host-driven `showScreen` FSM. Prefer extending `window.Android` (and THEME_GUIDE) over adding new ad-hoc globals.

## Warning policy

`InstrumentProjector2` separates visual vs critical warnings:

- Visual keys (`car.ipk_info.warning_tts_notify`,
  `car.ipk_info.bsd_lca_warning_reqleft`, `car.ipk_info.bsd_lca_warning_reqright`) still go to the
  frontend via `updateWarning(...)`;
- those keys do not drive `syncInitialWarnings()`, critical dismiss, or heavy card/visibility recompute;
- critical warnings may still use `window.Android.setWarningActive(...)`.

Goal: keep visual-only pulses off the expensive warning/card path.

## Readiness and WebView reload

Theme load, watchdog reload, and theme swap set a `loading` state:
`webViewsLoaded=false`, pending JS queue cleared, heartbeat renewed. That avoids running
`control` / `updateWarning` / `focus` / `showScreen` before the theme reinstalls its globals.

On `onPageFinished`, heartbeat is renewed before full sync so the watchdog does not reload
while the first `window.Android.heartbeat()` has not fired yet.

## Related files

- `InstrumentProjector2.kt`
- `ThemeBridgeImpl.kt`
- `CompatTranslationLayer.kt`
- `cluster-widgets/source/v1.0/` (active themes)
- `cluster-widgets/Themes/THEME_GUIDE.md`

## Risks

- Unescaped strings break JS evaluation.
- Calls before load must queue or drop safely.
- Warning loops can burn CPU without guards.
- Changing bridge method signatures without a bridge/contract bump breaks OTA themes.

## Open

- Automated tests covering the full `window.Android` surface.
- How long legacy `control()` push remains required for `noncontract/` themes.
