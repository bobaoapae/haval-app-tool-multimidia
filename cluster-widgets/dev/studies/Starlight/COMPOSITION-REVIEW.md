# Native composition review

Reference: `bobaoapae/haval-app-tool-multimidia`, preview commit `681bf61f052f746ccd792d5186d88a9e20a28cfc`.

The owner accepts the visual choice of covering ADAS. This review distinguishes that choice from evidence about native warnings. No vehicle operation was performed.

## Confirmed from code

- `TelasScreen.kt` describes D1 behind ADAS/other information, D3 above them; it does not classify every OEM layer
- `InstrumentProjector2.kt` adds the transparent WebView above native mask views. Its Presentation is above ordinary Android activities; opaque Canvas pixels therefore cover content underneath
- Holes in native masks do not make corresponding WebView pixels transparent
- `AaClusterVideoHost` adds its video surface below masks/WebView. An opaque center can also cover that surface
- `ClusterCardFlowPolicy.shouldUseNativeCardPassThrough()` always returns false, including when a warning is active; its unit test locks in that behavior. There is no automatic theme-hiding guarantee during a warning
- Warning handling tracks seatbelt, doors, tyre pressure, coolant/oil and other cards. `warningActive` reduces managed D3 applications to 70% width, reserving a right-side area; this is not removal of opaque theme pixels
- Default clears its own right-side mask/menu for warnings. This study does not yet implement equivalent warning-aware composition
- BSD/LCA, DOW, FCTA, FCW and `warning_tts_notify` do not all participate in `warningActive`. BSD uses separate left/right callbacks

## Coordinates and evidence limits

Apex's transparent center `(520,120,880,530)` is an authoring aperture, not a measured classification of all content there. The Starlight center at `(960,308)` and opaque background overlap it intentionally.

Theme Lab documents READY `(238,315,104,60)`, TSR `(225,475,90,90)` and ESP `(330,445,100,100)` safe zones. Starlight's 120 numeral is anchored inside TSR; 80 is inside ESP. Stars have no separate OEM READY exclusion. Tests detect these intersections and keep `hardwareReady=false`; they do not erase or move the approved visual.

Contract docs say READY/TSR/ESP remain above the WebView, but the host does not enforce per-indicator ordering. `docs/operations/fixed-cluster-icons-diagnostic-session.md` still leaves the physical visibility/source-layer matrix **A confirmar**, with Android/native/QNX/MCU alternatives. A topmost CSS simulator layer does not resolve that question.

Historical physical reports establish that warning cards exist, but do not prove their visibility under this full-screen opaque study. Precise physical bounds and priority of each alert remain unverified.

## Deliberate boundary

This PR changes no compositor, native masks, warning policy, host trust or vehicle settings. It adds a development-only study with an explicit installability block. A later real-host implementation needs evidence for the warning/assistance interactions above, not a blanket assertion that all ADAS overlap is forbidden or safe.
