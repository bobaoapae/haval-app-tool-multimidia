# Virtual cluster enable/disable (HAV-26)

## Ownership and compatibility

`enableVirtualCluster` controls whether the display-3 `InstrumentProjector2` Presentation
exists. `ProjectorManager` observes this preference after initialization and reconciles on the
main Looper. The callback reads the latest value and is coalesced, so quick off/on writes do
not leave a stale disable request behind. A disabled projector is dismissed, not merely made
transparent; boot and display reconnect still use the same creation gate. Stop unregisters
the listener and cancels pending reconciliation. Reconciliation does not recreate the separate
D1 wallpaper Presentation or add another car-data/display listener.

`VirtualClusterPreferences.isEnabled()` retains the historical host default **true** for an
absent key. It is shared by Telas, projector lifetime/rendering, app-bounds selection and the
existing telemetry field. No preference is seeded or migrated; a stored false stays false.
The parent cluster-functions switch includes this effective state when initially displaying
whether any function is active. Turning the parent switch off saves its flags in one edit.

Disabling either switch preserves the selected theme, its colors/settings and reload nonce.
Enabling Painel Virtual still requires the existing warning-dialog confirmation, then creates
D3 with the saved theme. Canceling that dialog leaves the flag off. The parent switch does not
silently opt the virtual panel back in.

## Defense before dismissal

The native projector independently gates its root/WebView and native mask painting on the
same preference. While off it clears the displayed mask bitmap and skips normal visibility
work. A rapid off/on before teardown invalidates the host's cached JS visibility payload and
refreshes native masks so the restored page receives `clusterEnabled=true`.

Default, Minimalist and ApexGT implement the existing `control('clusterEnabled', boolean)`
signal as a page-wide hide/restore. Default and Minimalist keep their existing state value;
ApexGT adds handling for that existing host signal. There is no new bridge/telemetry key,
contract version, display geometry or projection route. Older downloaded/custom themes are
still protected by the native Presentation gate even if they ignore the signal.

D1 has its own background preference and is intentionally unchanged. This work does not move,
resize, focus or restart native Android Auto/CarPlay tasks.

## Verification

Automated checks are documented in the PR and the test READMEs. The Java lifecycle harness
compiles the production manager against deterministic Android/collaborator fakes; it verifies
state transitions and scheduling, not WindowManager, WebView destruction or vehicle rendering.
Theme checks execute real control/state modules and validate the source/generated packages;
they do not prove native transparency on a head unit.

Required physical acceptance, with the car parked, for **Default, Minimalist and ApexGT**:

1. Select the theme, customize a color, and note its settings. Ensure the panel is visible.
2. Switch Painel Virtual off. The native display-3 cluster must return immediately, with no
   theme chrome or `d3_mask` insets. Verify no D3 Impulse Presentation remains in WindowManager.
3. Re-enable and accept the warning. The same selected theme/settings must return once.
4. Repeat off/on quickly, then leave off; no later callback may bring the virtual panel back.
5. Restart the app/process while off. Verify D3 stays native. Re-enable and verify it loads once.
6. Turn the parent cluster-functions switch off. Repeat the off/restart check, then enable
   the parent only: the virtual panel must remain off until its own warning is accepted.
7. Test canceling the enable warning. Reopen Telas and confirm the saved state remains off.
8. Test an install with the key missing: UI and host both resolve enabled, preserving legacy
   behavior. Saving false must survive process restart.
9. Where available, repeat around a native card, warning, display reconnect and active
   projection. Confirm D0 controls and D1 background behavior are unchanged; do not count
   DOM/unit checks as physical evidence.

No merge, release, signed APK, theme publication to preview, or device deployment is part of
this draft PR. Android CI is debug-only; the existing release workflow is not invoked.
