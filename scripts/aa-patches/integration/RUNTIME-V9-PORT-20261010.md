# HAV-24: three runtime fixes ported onto v9

## Exact inputs and scope

The base is `netseek/haval-app-tool-multimidia` commit
`2dad9adb36ad7793c101fa7df76147ae863b4469`. The reviewed fix source is PR152
head `5c0913a50265291a55cf103efa84ca918ef061c6`. Selected source and baseline
files were checked against their Git blob identities before editing.

This port applies only the three runtime defects already addressed on PR152:

1. Intersect custom, theme and default AA map bounds with the 1920×720 panel and
   the native-card protected region. Invalid explicit bounds and empty
   intersections stay closed; blank custom preferences keep theme/default
   behavior. Production policy: `AaClusterGeometry.java`.
2. Apply the video clip and its AA-owned native-mask hole together on the UI
   thread, including a theme resize when no managed secondary app exists.
   Ordinary external/projection holes retain ownership. Identical geometry
   remains a no-op and card changes retain their existing single mandatory
   mask repaint. No resize/event/JavaScript feedback is added.
3. Arm one five-second deadline on the first inactive navigation update.
   Repeated inactive updates do not postpone it; reactivation and session reset
   invalidate canceled, dequeued or duplicate callbacks. Production policy:
   `AndroidAutoClusterNavigationDebouncer.java`.

The existing bridge method and telemetry contracts are unchanged. The optional
Kotlin callback is host-internal; the existing theme entry point still calls
`refreshDisplayBounds()`. The redundant clip-only bridge refresh is removed
because that context callback now refreshes both layers.

## v9 behavior deliberately preserved

The port retains v9's functional CLUSTER path, 1920×1080 coded stream with its
360-pixel height margin, vehicle-center translation, left/right gradient Views,
`statusForPoll` session-disconnect handling, dock `publishAndroidAutoLinked`
publication and virtual-cluster enable/visibility/mask gates. Twenty-one
unaffected production methods were also compared byte-for-byte with v9,
including session polling/publication, fade methods/lifecycle, native-mask gates
and ordinary secondary-app synchronization.

The three fixes do **not** correct the pre-existing gradient/attribution issue:
the author's gradient Views remain siblings of the TextureView and are not
bounded by that view's map clip. Therefore a clipped or empty video rectangle
is not evidence that every sibling overlay lies within the map window. No
attribution-compliance or physical composition claim is made by this port.

There are no changes in this runtime port to signer trust, certificates,
permission behavior, Service APK/assets, loading, mounting, reload, rollback,
automatic mounting, CarPlay or vehicle actions. No signing, installation,
publication, merge or release is performed by these tests.

## Local evidence

Executed against the combined v9 candidate on 2026-10-10:

- Nine focused Python/JVM tests passed with no skips
- Production Java geometry: 21,689 checks passed
- Production Java navigation policy: 43,764 checks in nine controlled-scheduler
  scenarios passed
- Verbatim Kotlin geometry/refresh/bridge methods plus the actual AA-hole
  selection block: 58 checks passed with synthetic Android/render adapters
- The complete production Kotlin controller: 37 checks passed with synthetic
  scheduler/dependency adapters, including the preserved v9 dock publication
- A custom-clamp bypass mutant failed at `custom native card clip`
- A clip-only refresh mutant failed at `AA-only theme resize hole`
- The exact unmodified v9 controller failed at
  `repeated inactive callbacks must hide at first five-second deadline`

The v9 adapters retain the same production calls and observe shift/fade and
linked-state publication. Stable synthetic link evidence isolates navigation
expiry from the independent session policy; the controller harness does not
claim to validate OEM link polling or the real session debouncer.

Commands:

```sh
python3 -m unittest discover -s scripts/aa-patches/tests -p 'test_cluster_geometry.py' -v
python3 -m unittest discover -s scripts/aa-patches/tests -p 'test_cluster_navigation_debounce.py' -v
python3 scripts/aa-patches/tests/run_cluster_geometry_kotlin.py --compiler-dir /path/to/kotlin-jars
python3 scripts/aa-patches/tests/run_navigation_controller_kotlin.py --dependencies /path/to/kotlin-jars
```

Negative controls:

```sh
python3 scripts/aa-patches/tests/run_cluster_geometry_kotlin.py --compiler-dir /path/to/kotlin-jars --regression legacy-custom-bypass
python3 scripts/aa-patches/tests/run_cluster_geometry_kotlin.py --compiler-dir /path/to/kotlin-jars --regression legacy-theme-refresh
python3 scripts/aa-patches/tests/run_navigation_controller_kotlin.py --dependencies /path/to/kotlin-jars --controller /path/to/exact-v9/AndroidAutoClusterController.kt
```

The Java compiler is available through the JDK `jdk.compiler` module. Existing
Kotlin 2.0.21 compiler/runtime jars were SHA-256 checked against their retained
official Maven dependency manifest; the tests download nothing. Generated
sources, classes, source hashes and logs remain under `scripts/.build/` and
are not part of the source deliverable. The optional Kotlin checks supplement
the always-required portable suite; no missing test is counted as a skip/pass.

## Remaining verification

The local workspace is a selected source snapshot, without a Gradle wrapper.
No local `:app:assembleDebug` or `:app:testDebugUnitTest` result is claimed.
Exact-head full Android build/JVM CI must be recorded for the combined commit.

Synthetic checks do not execute Android Binder, a real compositor, the OEM
Service, vehicle graphics, reconnect/boot/rollback or MAIN/audio/TBT coexistence.
Those remain separate authorized runtime verification. Existing Service
identity/loading/recovery decisions are not satisfied by this source port.
