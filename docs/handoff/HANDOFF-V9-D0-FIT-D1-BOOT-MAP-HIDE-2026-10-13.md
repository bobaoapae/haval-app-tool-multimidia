# Handoff — v9: AA D0 fit, D1 boot failure, cluster map vs D1/D3 apps (2026-10-13)

Branch `fix/v9-d0-fit-d1-boot-map-hide`, based on `origin/feature/new-screen-enhancements-v9` (`2dad9ad`).

## What the car showed (2026-10-13, build installed 2026-10-09 20:34 = origin v9)

1. **AA on D0 runs under the bottom bar.** The fit-above-bar work (`95c25f3`, `5331600`, `24b3730`) lived only on the unpushed `feature/aa-cluster-v2-host` and never reached v9.
2. **D1 wallpaper missing.** `cluster-diagnostics` logged `projector_create_failed displayId=1 detail=android.util.AndroidRuntimeException reason=INITIALIZE` on 6 of 8 boots (2026-10-11 18:51, 10-12 05:30, 12:11, 17:33, 18:42, 10-13 06:32), always 18–23 s after boot. On the two good boots D1 was built at 28–30 s. D3 was built a few seconds later in the same pass every time. `dumpsys window` on 10-13 confirmed no Impulse window on D1. Nothing retried.
   - Most likely cause (to confirm with the new log detail): D1's `InstrumentProjector.onCreate` built a WebView that only the `WEB_URL` wallpaper uses. As the first projector of the process it reached the WebView provider before it was ready; `WebViewFactory` wraps that failure in `AndroidRuntimeException`.
   - "Wallpaper does not come back at navigation end" is the same bug: with D1 absent, `ClusterBackgroundSync` does not hold D3 (`d1Attached=false`), so the cluster centre shows black through the transparent theme once the map hides. The AA map code never touches D1.
3. **AA map covers apps sent to D1/D3.** The map's TextureView lives inside D3's Presentation, above D1/D3 tasks; it hid only on navigation end or session stop.

## Changes

| Commit | Change |
|---|---|
| `89b78d4`, `fbf5b73`, `5c65637` | Cherry-picks of the D0 fit (App patch v2.7 → v2.8, `cdaae63d`), foreground refit, and the install card that mounts the bundled App build. |
| `aad13f4` | `BottomBarService.observeVisibility`: refit AA (debounced 300 ms) whenever the bar hides or returns while AA is in front. |
| `b81abef` | `InstrumentProjector`: WebView built lazily for `WEB_URL` only (`d1_webview_unavailable` event if it still fails). `ProjectorManager`: retry failed creators at 2/5/10/20/30 s; failure event records message + cause. |
| `487bbb6` | `AndroidAutoClusterController.setSecondaryDisplayAppActive`; `wantsOutput()` now also requires no app on D1/D3. Fed by `InstrumentProjector2` on create and on `DISPLAY_1/3_APP_STATE_CHANGED`. |

## Validation

- `./gradlew :app:assembleDebug :app:testDebugUnitTest --offline`: build OK, 570 tests, 0 failures.
- **Not yet verified on the car.**

## Car checks still to run

- [ ] Cold boot ×3: `projector_created displayId=1` each time (or `RETRY_n` after a logged failure with its message); wallpaper visible.
- [ ] AA on D0 with the bar visible → window ends at the bar (1920×660); bar auto-hide → AA back to 1920×720 without a gap; bar shown → refit. Touches land where drawn at both heights.
- [ ] Navigation on with the cluster map → send an app to D3 → map hides, app visible; app leaves → map returns.
- [ ] Navigation end → wallpaper visible behind the theme.

## Risks

- `isAnyAppOnDisplay1/3` in the D3 projector is event-driven; a missed "app left" event keeps the map hidden (fail-safe, same flag the mask hole already uses).
- Each bar hide/show resizes the AA stack; watch for a visible relayout flicker.
- A `WEB_URL` D1 wallpaper at boot still depends on the WebView provider; it now retries on the next apply instead of killing the projector.

## Next (proposed)

Host the AA map on D1 (the wallpaper layer) instead of D3, behind a setting: D3's mask already cuts a hole for D1 apps, and apps on D3 would cover the map by layering. Depends on this D1 fix holding on the car.
