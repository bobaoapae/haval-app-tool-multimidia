# Theme study handoff — 2026-10-12

## Scope

Draft PR to `preview`, based on `681bf61f052f746ccd792d5186d88a9e20a28cfc`. Add Starlight revision 10 as a development-only study and explicit contract-fixture laboratory. Obsidian is being handled in a separate PR by the owner's existing workflow and is excluded from this Starlight change.

## Files and boundaries

- `cluster-widgets/dev/studies/Starlight/`: frozen visual modules/fonts/licenses, synthetic preview, read-only fixture adapter/renderer, tests and source provenance
- `cluster-widgets/index.html`: development-study links, outside the theme picker
- `.github/workflows/starlight-study.yml`: PR-only tests, read-only repository permission
- No `theme.xml`, OTA package, APK, host bridge, certificate, permission, projection policy or warning-policy changes
- PR152 and the AA integration remain independent

## Validation and remaining work

Local Node/Canvas suite and exact-preview Theme Lab checks are run against the combined tree; PR CI is the final browser/Android verification source. The local sandbox cannot launch Chromium or share its loopback server with the managed browser. A repeatable Playwright suite is included for CI. Do not describe browser checks as passed until the exact PR head's job succeeds.

The opaque ADAS overlap is an accepted visual choice. Native warning visibility remains unresolved: the exact-preview host does not enable native-card pass-through on `warningActive`, its managed-app width reduction does not clear opaque WebView pixels, and Default contains warning-aware right-side handling absent from this study. BSD and other assistance notifications do not all map to `warningActive`. READY/TSR/ESP foreground priority is documented but the physical diagnostic matrix remains unfilled. See the study README and composition report; do not infer vehicle safety from synthetic tests.

Next steps: establish native-alert composition; decide the bounded real-host port, including lifecycle/calibration/projection and explicit controls, before creating any installable artifact.
