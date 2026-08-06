# WebView Flow

Updated: 2026-08-06

## Flow

`InstrumentProjector2` creates a transparent fullscreen WebView on the cluster presentation display.

1. `onCreate` registers callbacks/listeners and calls `setupControlView`.
2. `setupControlView` enables JavaScript and DOM storage.
3. `readAppContent` loads HTML from (priority order):
   - `/data/local/tmp/app.html` when debuggable and valid (hot deploy);
   - selected/custom theme under app `files/themes` (OTA packages);
   - bundled Default: `R.raw.app` (+ assets metadata).
4. `loadDataWithBaseURL` injects the HTML.
5. `onPageFinished` marks the WebView loaded, syncs state, starts heartbeat.
6. `evaluateJsIfReady` runs JS immediately or queues until ready.
7. `WebChromeClient.onConsoleMessage` may record `webview_console` into the debug-only
   persistent day log when capture is enabled.
8. `onStop` removes callbacks/listeners and destroys the WebView.

Theme package layout and OTA vs APK: [`themes-contract-v1.md`](themes-contract-v1.md).
Bridge surface: [`kotlin-js-bridge.md`](kotlin-js-bridge.md).

## Related files

- `InstrumentProjector2.kt`
- `ThemeManager.kt`
- `app/src/main/res/raw/app.html`
- `cluster-widgets/source/v1.0/default/`
- `cluster-widgets/Themes/v1.0/`

## Risks

- Calling JS before load without queuing.
- WebView reload loops.
- Failing to destroy the WebView in `onStop`.
- Invalid `/data/local/tmp/app.html` poisoning debug hot-deploy.
- Noisy theme console loops — capture stays debug/internal and fields are truncated.

## Open

- Whether `WebView.setWebContentsDebuggingEnabled(true)` should remain in release.
- Confirm all active OTA themes stay on contract `v1.0` (see themes-contract doc).
