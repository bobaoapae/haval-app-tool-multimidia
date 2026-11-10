# Engineering changes — 2026-10-12

- Added Starlight development study from aligned-numeral revision 10
- Kept approved effects/menu typography; added explicit state renderer seam without changing the demo path
- Added mock-only canonical telemetry adapter, host guards and teardown
- Kept development pages out of contract/OTA catalogs; no native or installed-theme edits
- Added geometry/contract/boundary/browser checks and PR-only CI
- Obsidian is handled separately and excluded from this change

Baseline has no tracked `.ai-context` directory or `.agents/skills` packages. Existing AGENTS, theme/bridge/WebView architecture and exact repository code were consulted. These handoff files establish only this task's technical record.

## 2026-10-25 — Native steering preservation

- Added native-selection policy and durable backup/readback adapter for both buttons.
- Limit normal capture to custom short taps; preserve stealth and existing dispatch.
- Added warning UI documenting nonexclusive native/custom double and long actions.
- Added 33 focused tests and `docs/steering-native-preservation.md`; focused tests pass.
- Android integration and on-device UI/behavior validation remain separate checks.

- Isolated Android validation: 596 tests passed; unsigned debug assembly passed. No signing or deployment.

## 2026-10-25 — PR #165: confirmação durável do backup

- Removido o atalho por igualdade no cache do adaptador em ServiceManager.
- A captura exige commit síncrono bem-sucedido mesmo depois de uma falha que alterou a memória.
- FakeStore agora separa cache e disco; regressões cobrem repetição, recuperação e reinício com backup antigo/ausente nos dois botões.
- Atualizado o contrato de fonte para rejeitar o atalho defeituoso.
- Red/green local: 37 testes JUnit, 5 falhas antes e zero após a correção. Java 21 com source/target 11; não é compilação contra a API Java 11.
- Build/testes Android agregados não executados: wrapper falhou ao baixar Gradle 9.3.1 com Network is unreachable. Nenhuma alteração de APK instalado, central ou workflow CI.
## 2026-10-25 — Global cluster AC focus (local review branch)

- Added bridge1.0.2 focus read/save methods over the existing global preference;
  invalid reads fall back without writes and existing scoped preferences stay unchanged.
- Default1.4.44, Minimalist1.0.4 and ApexGT1.0.16 restore before first rendering,
  persist explicit selection, and preserve focus across off/unknown telemetry.
- Late development mock rehydration is one-shot, cleaned up, and cannot replace the
  selection after any valid AC input (including UP/DOWN).
- Rebuilt all three themes through their existing build scripts; Default APK output
  and separate Minimalist/ApexGT OTA packages match their generated source outputs.
- PASS: all three npm builds, cluster-widgets npm check, ApexGT npm check,
  generated package parity, git diff --check. Android validation coordinated separately.
- No bootstrap, firewall, steering integration, UID, signing, migration or deployment changes.

- Isolated Android validation: 570 tests passed; unsigned debug assembly passed. No signing or deployment.

## 2026-10-25 — PR166 follow-up: preserve focus through HVAC snapshots

- Removed the remaining Minimalist telemetry reset of focusArea on HVAC_POWER=0.
  Power telemetry changes power state only, not the restored or explicit selection.
- Added 36 regression scenarios to the existing 10: both saved values, initial
  cache seeding and live subscription callbacks, numeric/string off/on power,
  first UP/DOWN routing, and repeated off/unknown updates after explicit selection.
- Red-before-green: the new test failed on the original code with
  `seed power=0 must preserve temp`; all 46 scenarios pass after the fix.
- Bumped Minimalist to 1.0.5 and regenerated its HTML/manifest via the normal build.
- PASS: npm ci (locked dependencies), Minimalist build, aggregate npm check,
  package byte parity, host lifecycle regression (6 Python tests; 15 inner JVM cases),
  and git diff --check. No other theme, Android production code, bridge or CI change.
- Android assemble/unit tasks were attempted but the Gradle wrapper download was
  blocked by unavailable network (SocketException: Network is unreachable). No
  Android build/unit result or new vehicle/browser validation is claimed.

## 2026-11-10 - Navigation chevron distance (draft PR)

On preview 16a5bef612171ed144f73e0dba7bf786e91c32ce, frameFor replaced
Navigation's guideMeters with the AR-only arMeters (null without an override).
Consequently the always-visible chevron used Float.MAX_VALUE and stayed upright
near turns, while near-only chevrons disappeared. The existing both-layout test
provided an AR override and did not exercise the controller's Navigation path.

- Preserve reported guide distance for HERO/COMPACT/FAR when valid; retain explicit
  overrides, AR interpolation and roundabout exit handling.
- Add three regression tests without an AR override: both turn sides/always modes,
  distance thresholds, compact/far styles, hidden/stopped/disabled/stale cases.
- Static inspection and git diff --check only. Tests, Gradle tasks and APK builds
  were not executed at the requester's direction. No workflow, renderer, layout,
  bridge, theme or vehicle changes. The commit uses [skip ci] intentionally.
- Historical source trace: introduced in 4cc5798898e0862c03e5559bce64ebf7c3a664ef
  (2026-10-20), first merged to preview by PR162 / 2e45dfaa (2026-11-04).
