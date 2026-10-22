# Radar refresh/cache correction for v9

Base: netseek/haval-app-tool-multimidia, feature/new-screen-enhancements-v9, 97f8ef1cbfbc45545a7581a950c9eb77a8448c0a.

## Scope

Only radar source refresh, persistence, status text, tests and documentation. No theme assets, bridge contracts, alert thresholds, CarPlay, new datasets, release or deployment changes. No issue number was supplied; do not associate an unrelated ticket.

## Changes

- `RadarRefresh`: pure, independently testable source orchestration. OSM success, ANTT metadata checks/CSV downloads and roads success are persisted separately.
- ANTT invalidates on ID/date/URL and revalidates unchanged data after seven days. Manual refresh forces camera sources once; failed sources alone retry.
- Roads remain monthly for stable camera geometry. Changed positions or unknown legacy geometry allow a weekly refresh, anchored to the existing valid road copy; no extra geolocation or incremental network queries.
- `RadarCacheFiles`: failed replacement never deletes the last valid destination. Repository validates disk caches and falls back to the bundled copy on read failure.
- Settings text and diagnostics reflect partial failures and the new source-specific policy.

## Validation

- JVM regression suite: 61 tests passed, covering existing parsers/merge/files, alert engine and road matching plus refresh/cache tests.
- Focused compilation passed for the actual updater, repository, settings and preference enum against Android API classes, with unrelated app collaborators stubbed. This is not a complete Android build.
- `git diff --check` passed.
- Full requested commands attempted: `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`. The default Gradle home is not writable here; with a writable `GRADLE_USER_HOME`, the Gradle 9.3.1 download fails with `java.net.SocketException: Network is unreachable`. No full Android SDK installation is available. Therefore no full Gradle test/build, APK, emulator or vehicle validation is claimed.
- JVM harness used Kotlin 2.0.21, JRE 21, JUnit 4.13.2, Gson 2.13.1 and org.json 20231013. The project remains configured for Kotlin 2.2.10/JVM 11. Production code and checked-in tests were compiled targeting JVM 11.
- All test data is synthetic/local. No live ANTT/Overpass downloads were requested.

## Vehicle / CI follow-up

1. Run the normal Gradle unit suite and debug build in a complete Android environment.
2. On a test head unit, check automatic and manual refresh; simulate an ANTT/network failure and confirm error reporting plus successful-source reuse on retry.
3. Confirm existing downloaded and bundled caches remain available after restart and a failed write. No production data should be deleted to force this test.
4. Observe real seven-/thirty-day eligibility through injected/local test state rather than repeated live national Overpass queries.

No merge, deploy or release is authorized by this handoff.
