# HAV-24 host runtime follow-up, 2026-10-09

## Scope

This follow-up starts from shared draft head
`f54ea1dd534b9fec6ab7a21d00cac001ce4676d3` on PR152 and netseek PR6.
It addresses the three retained source defects recorded in
[INTEGRATION-20261009.md](INTEGRATION-20261009.md), without changing the
Service patch, protocol, signer trust, loading or persistence.

The two draft heads are intended to carry the same follow-up commit. The
author's `fix/pr152-cluster-streams` base is not a push target. No merge into
preview/master or release is part of this change.

## Implemented source behavior

1. Resolve custom/theme/default map geometry, then intersect it with the D3
   panel and native-card protected region. A custom override must not bypass
   protection. Empty intersections are not expanded into a visible map or mask
   hole. Null/blank custom preferences retain the documented theme/default
   behavior; malformed nonblank geometry fails closed. The pure production
   resolver is `AaClusterGeometry.java`.
2. Refresh the AA video clip and native-mask hole together on the UI thread
   when geometry changes, including sessions with no managed external app.
   Preserve ordinary external-app handling and its resize/notification loop
   prevention. The AA callback updates only an AA-owned hole, leaving a normal
   external app's hole alone. Card handling retains its mandatory repaint but
   avoids a duplicate repaint from the geometry callback. Theme bridge methods
   and telemetry contracts stay compatible.
3. Arm the navigation-hide delay once on the first inactive update. Repeated
   inactive messages must not extend its deadline. Reactivation and session
   reset cancel the pending action; stale timer callbacks must not change a
   newer state. `AndroidAutoClusterNavigationDebouncer.java` owns the one-shot
   timer, fences callbacks by their exact pending identity, and is used by the
   existing controller without changing navigation-monitor telemetry.

## Validation and limits

Final local source validation passed on 2026-10-09:

- All **217** aggregate Python/JVM tests passed, with no skips. The seven new
  checks include executable production geometry and debounce policies plus
  supplementary Kotlin wiring guards
- The production Java geometry harness passed 21,689 assertions; the production
  navigation policy passed 43,764 assertions across nine controlled-scheduler
  scenarios. These include repeated inactive updates, reactivation, resets and
  stale/duplicate/dequeued timer callbacks
- An additional Kotlin 2.0.21 harness compiled/executed the complete production
  controller with synthetic dependencies: **35** checks passed. The exact
  pre-change controller failed the same original five-second deadline check
- Another Kotlin harness executed verbatim production geometry/refresh/bridge
  methods and the actual hole-selection block with synthetic Android/render
  adapters: **53** checks passed. Coverage includes blank defaults, card/warning
  changes, empty clips, AA-only theme resizing, external-hole ownership, queued
  UI work, repeated theme echoes and mandatory single card repaint
- Deliberately reintroduced defects were rejected by the regression harnesses
- Pinned Android28 helper/host-Java compilation passed with handoff disabled
- Independent source review found no remaining defect in this bounded follow-up

The always-required portable suite is:

```sh
python3 -m unittest discover -s scripts/aa-patches/tests -v
```

Additional local wiring checks use an already available official Kotlin compiler
and never download dependencies:

```sh
python3 scripts/aa-patches/tests/run_cluster_geometry_kotlin.py \
  --compiler-dir /path/to/kotlin-jars
python3 scripts/aa-patches/tests/run_navigation_controller_kotlin.py \
  --dependencies /path/to/kotlin-jars
```

The controller runner also requires the compiler directory's sibling
`dependency-manifest.json` with official Maven Central URLs and SHA-256 hashes.
Local dependencies were checked against that existing manifest; the Kotlin
2.0.21 embeddable compiler SHA-256 was
`9fa8cdd1de0dccffe154c997d423ec6b5f53cd6d9177e3a77a9b0de03fb1bc81`.
Generated evidence stays under `scripts/.build/`; no jars or binaries are
committed. The optional harnesses are separate evidence, not skipped CI tests.

The partial local snapshot has no Gradle wrapper. Full Android host build/JVM
tests and Windows execution therefore require the final remote checks.
Exact-head CI is recorded separately in HAV-24. These synthetic adapters do not
execute Android Binder, a real compositor, OEM code or a vehicle.

The original single Service signer, disabled-by-default handoff, packaged APK
assets, mount/permission behavior and App-only automatic mounting are unchanged.
The excluded logo-hiding shift and gradient Views remain excluded. This is not
a blanket attribution-compliance finding; the actual map composition still
needs inspection.

This change does not deliver complete persistent Service autopatching. The
approved artifact/installed-identity/loading/recovery chain remains a separate
gate. Actual decoder dimensions/crop, native-card/warning composition,
MAIN/audio/TBT coexistence, settled performance, reconnection and boot/rollback
need authorized runtime evidence under [TEST-PLAN.md](TEST-PLAN.md).
Keep HAV-24 In Progress and the PRs in draft.
