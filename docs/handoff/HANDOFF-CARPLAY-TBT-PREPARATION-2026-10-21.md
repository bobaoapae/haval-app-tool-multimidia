# Handoff: CarPlay TBT preparation

## Scope and base

Preparation only for HAV-24. No runtime activation, OEM modifications, installation,
car access, contract change, merge, release or deployment.
Base v9: `34347c29daf81ac9fc76cc7e509c68936ec54737`.
This base already contains radar PR #13, merged by `netseek` at 2026-10-21 22:36:39 UTC;
no radar changes are duplicated or made here. Other open AA PRs are not modified.

## Files

- `app/src/main/java/br/com/redesurftank/havalshisuku/navigation/CarPlayGuidance.kt`
- `app/src/main/java/br/com/redesurftank/havalshisuku/navigation/PreparedNavigationArbiter.kt`
- Matching two test files in `app/src/test/java/.../navigation/`
- `scripts/carplay-tbt-tests/run.py` and README
- `.github/workflows/carplay-tbt-preparation.yml`
- `docs/architecture/carplay-tbt-preparation.md` and this handoff

## Local validation

- PASS: cached Kotlin 2.0.21 compiles both real production sources and test suites for
  JVM 11 with `-Werror`; **41 JUnit tests** pass (18 normalizer/model, 23 arbiter).
- Included in those tests: 30 seeded sequences of 200 session events, not 6,000 separate
  JUnit tests; all are synthetic, not device/Binder captures.
- PASS: `python3 -m unittest discover -s scripts/virtual-cluster-tests -v`:
  six tests, including actual production Java against collaborators and 15 lifecycle cases.
- PASS: `git diff --check` and no changes to existing production/runtime/assets.
- BLOCKED before compilation: `./gradlew :app:testDebugUnitTest --no-daemon` and
  `./gradlew :app:assembleDebug --no-daemon`. The initial default Gradle cache location
  was unwritable. A writable workspace `GRADLE_USER_HOME` fixed that setup issue, but
  downloading Gradle 9.3.1 failed with `java.net.SocketException: Network is unreachable`.
  No SDK, APK or complete Android pass is claimed from the local run.
- FAIL, pre-existing static lock drift: `python3 scripts/carplay-patches/verify_regression_lock.py`.
  Existing asset hashes and patch sentinels passed. The unchanged v9 tree lacks three
  expected tokens in `InstrumentProjector2.kt`, the expected `ProjectionCardOverlayPolicy.kt`,
  and the old `cluster-widgets/default/src/core/main.js` path. None is introduced/modified
  here. The old checker was not weakened or rewritten to hide this failure.

The CI workflow checks the exact head independently. Consult the PR checks for its current
result; this document does not substitute a local focused pass for pending CI.

## Risks and next step

The raw fields and Binder codes have static evidence, not installed-build/live-delivery proof.
Numeric distance, remaining seconds, semantic maneuver and roundabout conversion are unknown.
Loading/locating fail closed by explicit preparatory policy. `PreparedDirections` is not a
serialized theme contract and the AA adapter is intentionally absent. No timer/consumer is wired.

Before any runtime integration, review the
[architecture note](../architecture/carplay-tbt-preparation.md), implement/test the Android
Parcel boundary, obtain consent and real session/capture evidence, and design one publisher
without losing existing AA next-turn/lane fields. Keep the protected runtime, display/focus,
HVAC/camera and OEM binaries untouched in this step. Hardware tests remain unperformed.
