# HAV-24: v9 integration consolidated into PR152

## Exact inputs and preserved history

The PR152 head is `5c0913a50265291a55cf103efa84ca918ef061c6`; the v9 integration
source is PR7 head `6eb3ee0c2e6db830dae36429801c423201b7775c`.
The live destination `preview` is `681bf61f052f746ccd792d5186d88a9e20a28cfc`,
already an ancestor of PR7. The consolidation commit has both PR heads as
parents and advances only PR152's head branch without force. Neither PR is
merged into its destination by this source consolidation.

The three bounds/mask/navigation corrections and package/file-status work are
retained. PR152's three older integration reports are preserved as historical
records. Their single-pin and loading-scope statements describe those older
commits and are superseded for this candidate by the boundary below.

## Explicit source-code adoption

This candidate adopts two reviewed v9 changes for source review and tests:

- The host accepts `com.ts.androidauto.projectionservice` with exactly one
  current PackageManager signer matching either
  `7be3a99482e3f2f7f4f411f0a5a571ac97a505e500f9e05863fa8574e00baeb0` or
  `3c7d703011f11ea2a4baa35ba2c522d6b03e3af011d70dcb95c1331f11ad0f65`.
  The latter was identified as Android Debug in earlier offline artifact
  verification. An allowlist entry is not proof of an installed identity or
  OEM provenance. Package/interface/profile checks are retained.
- The existing v9 bundled Service asset (Git blob
  `11d0fa60d7a8b459c1e0747ecd3bf33fe0e2b1fe`) and default-off, persistent
  Service auto-mount code are carried into PR152. On a later device execution
  with its opt-in enabled, this code stages and labels the Service file,
  bind-mounts APK/OAT paths, clears relevant Dalvik-cache files and sends SIGTERM
  to `com.ts.androidauto` after a settled phone session. It may repeat after
  reboot. No such device operation is performed by this source task.

These are material changes relative to the former PR152 head, even though they
were already present in the v9 base. Schema-2 package evidence records the same
exact two-entry allowlist; it does not grant deployment approval.

## Home and independent theme exclusions

The destination already contains the Home installer, utility classes and
Save-and-open setup behavior. Those bytes are preserved. Four PR7-only regions
(14 lines) are excluded from `InstallAppsScreen.kt`: the ViewerFirstRun import,
version tracking, version-change grants and immediate post-install grants.
Thus this PR does not add those unrelated Home access triggers.

The independent theme-preview task is not included. Existing destination
theme/virtual-cluster work remains unchanged. No theme contract keys are added.

## Local verification of the combined candidate

- `python3 -m unittest discover -s scripts/aa-patches/tests -v`: **227 passed**,
  zero skips, including retained/adapted PR152 trust guards and Home exclusions
- Actual Kotlin with synthetic adapters: **58 geometry/refresh** checks and
  **37 complete-controller** checks passed
- Virtual-cluster production Java lifecycle harness: **15 passed**, zero failed
- Real pinned Android28 helper/client compilation passed
- Fresh full disabled unsigned package preparation and reopened ZIP verification
  passed: four hooks, 121 other methods and 4,897 OEM classes preserved;
  resources/manifest unchanged, only classes.dex changed, original signatures
  absent and all eight STORED entries aligned
- The local unsigned package keeps handoff disabled, generated client trust
  empty, and signed/deployment_ready/vehicle_validated=false. This generated
  artifact is separate from the adopted v9 asset and is not committed

Commands for the supplemental source harnesses:

```sh
python3 scripts/aa-patches/tests/run_cluster_geometry_kotlin.py --compiler-dir /path/to/verified/kotlin-jars
python3 scripts/aa-patches/tests/run_navigation_controller_kotlin.py --dependencies /path/to/verified/kotlin-jars
python3 scripts/virtual-cluster-tests/run.py
```

Full Android/Compose build, JVM app tests, Linux/Windows suites and merge-tree
checks must be verified in the exact-head remote CI after publication. Local
synthetic checks do not substitute for those gates. CI outcome is recorded in
HAV-24, rather than predicted here.

## Remaining limits

The adopted loading implementation still has the previously recorded one-shot
arm latch, single delayed certificate-error scan and unverified rollback/
restart recovery. An empty log scan is not proof of loaded code or recovered
MAIN/AOA. The existing sibling gradient/attribution and composition concerns
also remain; this consolidation does not fix or validate them.

Installed PackageManager identities, rendered/large-font UI, real
Binder/MediaCodec/compositor behavior, navigation/reconnection, MAIN/audio/TBT,
warnings and performance still require separately authorized validation.
No signing, installation, activation, vehicle operation, release, deployment or
PR merge is performed. HAV-24 remains In Progress.
