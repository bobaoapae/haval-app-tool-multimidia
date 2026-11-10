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

## Native steering preservation — 2026-10-25

Isolated change against preview `2d4e32e6`: preserve each live OEM selection when
short tap is native DEFAULT, including custom double/long combinations. Persist the
live choice before capture, guard observed races, and confirm system writes. Both
button settings show nonexclusive-action warnings. Stealth and tap dispatch/timing
remain unchanged; no bootstrap, UID, firewall, theme or migration changes.

Focused production-policy/source-contract JUnit suite: 33 tests passed with Java 11.
Resource XML and whitespace checks passed. On the isolated preview-based tree,
`:app:testDebugUnitTest :app:assembleDebug` passed with 596 tests (73 suites), no
failures/errors/skips; debug assembly used an external unsigned init script, with no
key generation. No isolated-commit hardware or visual UI validation claimed.
Settings has no atomic compare-and-set; double/long OEM overlap remains documented.

## PR #165 — revisão de durabilidade do backup

ServiceManager não considera mais getString igual como confirmação de disco.
Todo saveBackup precisa de commit() true antes de a política gravar 99.
Alterados somente o adaptador, dois testes existentes e documentação da correção.
Os testes mantêm cache e disco distintos, falham após atualizar cache e exercitam
segunda reconciliação, retry bem-sucedido e reinício com backups antigos/ausentes.

Validação atual: javac via módulo jdk.compiler (Java 21, source/target 11) e JUnit
4.13.2 executaram 37 testes das três classes SteeringWheelNativeConfig: cinco
falhas antes da correção, zero depois. git diff --check passou. A execução de
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon parou no download
da distribuição Gradle 9.3.1 (Network is unreachable), antes dos testes/build.
Os 596 testes históricos acima não foram reexecutados para esta revisão.
Não houve teste na central nem instalação; CI Android do novo head continua necessário.
# Global AC focus handoff — 2026-10-25

## Local review scope

Based on preview `2d4e32e6be039d6e741c5f764bf72ab34b92ede4`, branch `review/ac-focus`.
Bridge1.0.2 adds only global UI-focus read/save over the existing preference; v1.0
contract, generic scoped preferences and HVAC handlers stay compatible. Default1.4.44,
Minimalist1.0.4 and ApexGT1.0.16 use the API; older theme requirements remain supported.
Do not add a compatibility polyfill that falsely promises global persistence.

Changed groups: bridge/helper/tests; shared helper/browser mock; three-theme source,
versions and generated assets; Theme Lab checks; API/system docs. No bootstrap/Telnet,
firewall, steering integration, UID, signing or migration files belong to this change.

## Validation

Executed on this worktree using existing approved pinned dependencies:
- `npm run build --prefix cluster-widgets/source/v1.0/default`: PASS.
- `npm run build --prefix cluster-widgets/source/v1.0/minimalist`: PASS.
- `npm run build --prefix cluster-widgets/source/v1.0/ApexGT`: PASS.
- `npm run check --prefix cluster-widgets`: PASS (includes six helper/Default tests
  and ten global-focus scenarios, including actual arrow-before-late-mock interaction).
- `npm run check --prefix cluster-widgets/source/v1.0/ApexGT`: PASS (actual-menu AC
  focus, runtime, display/motion/speed/projection and package checks).
- Generated package byte-parity and `git diff --check`: PASS.

On this isolated preview-based tree, `:app:testDebugUnitTest :app:assembleDebug`
passed with 570 tests (71 suites), no failures/errors/skips, including the added
native Sport compatibility contract. Debug assembly used an external unsigned init
script; no key generation. No visual browser or isolated-commit hardware result is claimed.

## Distribution and next review

Default is embedded via APK raw HTML + manifest; Minimalist and ApexGT remain separate
OTA theme files and are NOT installed by an APK-only update. No ThemeManager or install
mechanism changed. Source plus generated artifacts must be reviewed together.

No push, PR, issue or release-branch mutation performed. Open PR162 overlaps shared
runtime/Default/Minimalist files; check merge interactions before any later publication,
without importing its unrelated changes. Parked head-unit checks still need both focus
values across updated themes/restart, first-arrow targets and unchanged long-key behavior.

### Native Sport compatibility evidence

SportRed/SportRedLite packages and native AcControlScreen are unchanged. The new
bridge persists the same LAST_CLUSTER_AC_CONFIG enum names already read/written by
the native screen; a focused source-contract/enum test checks this compatibility.
`sh cluster-widgets/Themes/validate_sport_themes.sh` passes for both immutable
0.16.44 packages. Starlight remains a development-only study, not an OTA consumer.

## PR166 follow-up — 2026-10-25

The telemetry handler still contained a legacy reset after the initial focus
restoration. This follow-up removes that reset and covers the actual seed and
subscription callback paths before the first arrow, not only the visual component.
Minimalist source/OTA version is now 1.0.5; contract v1.0 and bridge 1.0.2 are unchanged.

Changed: Minimalist main.js, source/OTA theme.xml, generated OTA app.html,
check-ac-focus-themes.mjs and these handoff/changelog notes. Default/ApexGT packages,
Android source and CI workflows are unchanged.

Validation: new regression demonstrably failed before fix, then all 46 focus
scenarios passed; aggregate npm check and normal Minimalist npm build passed;
source/dist/OTA parity verified. Host Python suite passed 6 tests including 15
inner Java lifecycle cases. Android test/build was attempted but Gradle download
failed with Network is unreachable, so those tasks did not execute. No physical
vehicle or rendered-browser test was performed for this follow-up.

Next: publish this scoped update to the existing PR166 after parent QA, then
verify the exact remote SHA and upstream CI. CI execution may require maintainer
approval. No standalone installer/APK or change to the installed vehicle is part
of this follow-up.

## Navigation chevron distance - 2026-11-10 (draft PR)

Base: public preview 16a5bef612171ed144f73e0dba7bf786e91c32ce. Narrow model fix:
keep AA guideMeters for non-AR styles without an override; reject stale distance;
preserve AR distance interpolation, explicit overrides and roundabout handling.
The reported symptom is an upright still arrow near turns in Navigation / large
arrow mode; this source-level defect has not been confirmed on physical hardware.

Changed: HudOverlayModel.kt, HudArArrowTest.kt and these handoff/changelog notes.
Three new tests cover the real no-override Navigation path; existing AR/roundabout
tests are retained. No renderer, geometry, thresholds, bridge, theme or CI changes.

Validation performed: historical Git source comparison, manual call-path and diff
review, git diff --check. Tests and compilation were deliberately NOT run; the head
commit contains [skip ci] to avoid triggering PR compilation. No vehicle access.
Commands used: git log/show/diff, source reads, branch/commit/push and draft PR API.

Next: maintainer review, then authorized CI/unit/build validation on the exact PR
head and separate physical HUD acceptance. Do not infer passing tests or installed
behavior from this static review. The PR must remain a draft until validation.
