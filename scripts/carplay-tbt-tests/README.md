# Pure Kotlin CarPlay preparation tests

These tests compile the **actual** new Kotlin sources and run JUnit4 on a normal JVM.
All guidance/session/clock inputs are synthetic. No Android fakes, Binder calls, OEM code,
car access, side effects or downloaded dependencies are part of this runner.

## Full project checks (normal Android development environment)

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

The dedicated `CarPlay TBT preparation` workflow runs these checks on the exact PR/push
head. It is read-only and does not sign releases, upload APKs or deploy. Fork pull-request
runs may require maintainer approval; no approval is bypassed.

## Offline focused checks

```sh
python3 scripts/carplay-tbt-tests/run.py --deps-dir /path/to/existing/dependencies
```

Alternatively set `KOTLIN_TEST_DEPS_DIR`. Requires Python 3 and JDK 11+.
The directory must already contain these dependencies from their official Maven artifacts:

- org.jetbrains.kotlin: kotlin-compiler-embeddable, kotlin-stdlib,
  kotlin-script-runtime and kotlin-daemon-embeddable, all 2.0.21
- org.jetbrains.kotlin:kotlin-reflect:1.6.10
- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
- org.jetbrains.intellij.deps:trove4j:1.0.20200330
- org.jetbrains:annotations:13.0
- junit:junit:4.13.2
- org.hamcrest:hamcrest-core:1.3

The runner does not install or fetch anything and does not include binaries in the repo.
It compiles to a temporary directory, with JVM target 11 and warnings treated as errors.
A focused JVM pass does not establish an Android build or runtime/device correctness.

Coverage: 11 raw fields, null/unknown/extreme values, every known route state/unit,
localized distance ambiguity, all 54 unmapped maneuver IDs, angle/time withholding,
AA priority, linked-vs-active distinction, source transfers, stale session identities,
non-owner cleanup, fixed-deadline coalescing, reset, snapshot immutability and 6,000 seeded
session events. No Parcel parser exists here, so malformed/truncated Parcel tests remain
a separate required gate before a Binder adapter is enabled.
