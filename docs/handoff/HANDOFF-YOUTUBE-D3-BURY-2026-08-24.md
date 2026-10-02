# Handoff — YouTube D3 bury / lost bottom-bar link (2026-08-24)

## Verdict
YouTube (`app.rvx.android.youtube`) on display 3 was buried under the native `d3_mask` insets, and the 3D viewer right-slot “link” could not bring it back to D0, because Impulse was stuck in an `APP_GEOMETRY_CHANGED` feedback loop that saturated Shizuku (~200–450 geometry events/min).

## Timeline (car `192.168.33.219`)
- `07:45:40` — `display3Active=true`, geometry storm starts
- `08:04:15` — brief `display3Active=false` (false negative under load) → hole closed → buried under insets
- User right-slot raise fails (task on D3; `moveTaskToFront` does not cross displays)
- Reopen from bottom bar recreates/moves task on D0 and re-links

## Root causes
1. Theme `render()` → `setAppDefaultDimensions` always called `refreshDisplayBounds()`, which cleared `lastAppliedConfigs` and re-ran `sync → resizeApp → APP_GEOMETRY_CHANGED → updateVirtualClusterVisibility → …`
2. `APP_GEOMETRY_CHANGED` also called `syncSecondaryDisplayApps`, closing the loop
3. `getTopPackageOnDisplay` matched any `displayId=` (including configuration lines), so a live D3 app could look absent

## Fixes deployed (debug APK installed + process restarted via monkey)
- `ThemeBridgeImpl.setAppDefaultDimensions`: no-op when rect unchanged
- `InstrumentProjector2.refreshDisplayBounds`: do not clear `lastAppliedConfigs` blindly
- `resizeApp(..., notifyGeometry=false)` from sync path
- `APP_GEOMETRY_CHANGED` refreshes mask hole only (no sync re-entry)
- Deduped `appInDash` JS pushes
- Robust stack-header-only displayId parse for top package

## Commands
- Connected via rediscovered ADB `192.168.33.219:5555`
- Emergency: `am display move-stack 14 0` (YouTube later returned to D3)
- `adb install -r -t app-debug.apk` then `am force-stop` + `monkey` relaunch
- Post-fix: geometry events stopped after `08:33`; no continuous `am stack resize` in logcat

## Follow-ups
- Viewer right-slot still cannot pull a task that lives on D3 — needs Impulse `bringAllToMainDisplay` / move-stack path when raise detects foreign display
- Pre-existing unfinished unit tests (`DisplayAppLauncherFreeformOverscanTest`, `ClusterWarningPolicyTest`) still fail to compile; APK assemble succeeded
