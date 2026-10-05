# Virtual Cluster host regression tests (HAV-26)

Run from the repository root:

```sh
python3 -m unittest discover -s scripts/virtual-cluster-tests -v
```

Requirements: Python 3 and a JDK 17 or newer. No Gradle, Android SDK, dependency
installation, network, vehicle, or emulator is used. If the image includes the
`jdk.compiler` module but omits the `javac` launcher, the runner uses the module
directly. Compilation uses source/target 17 so a stripped JDK without historical
`ct.sym` release signatures can run the suite. All compiled files go into an
automatically removed temporary directory.

For only the Java behavior tests:

```sh
python3 scripts/virtual-cluster-tests/run.py
```

## What executes

The harness compiles these **actual production files**, without copying their
algorithms into a test implementation:

- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/ProjectorManager.java`
- `app/src/main/java/br/com/redesurftank/havalshisuku/utils/VirtualClusterPreferences.java`

The classes under `src/android` and the remaining `src/br/...` classes are
small, explicitly fake collaborators. They record presentation lifetime,
listener counts, and thread-affinity violations. The deterministic handler
queue is manually drained on the test main thread. Background tests use a real
worker thread. The preference fake supports relevant reads and notifications,
including an intentionally captured callback dispatched after unregistering.
The fake preference-key enum is checked against the production Kotlin enum.

## Behavioral coverage

- Absent key defaults to enabled without writing/seeding a value
- Explicit false prevents D3 construction at startup and after explicit refresh
- Repeated on/off/on removes/recreates only D3; live D1 identity stays unchanged
- Repeated initialize and duplicate same-value callbacks do not duplicate windows
- Rapid writes coalesce into one queued reconciliation using the latest value
- Preference callbacks and initialize/refresh/stop dispatch window work to main
- Stop unregisters preference/display listeners and cancels queued reconciliation
- A callback captured before stop cannot restart the host after stop
- Null-key/clear notifications restore the default-enabled state
- Unrelated preferences cause no handler or display-scan work
- A disabled D3 cannot return on display add/change/re-add
- Enabled missing/cancelled displays still recover
- One car-data listener targets only the current projector instances
- Existing normal-D1 and stealth-mode behavior stays intact

`test_virtual_cluster.py` also contains **source-wiring assertions**, explicitly
separate from behavior tests. These check the shared effective-value helper in
Kotlin consumers, native-mask/view off gates, cache invalidation before an off
control, and preservation of theme/color selection in the UI off paths.

## Validation limits

This is JVM host logic validation against lightweight fakes. It does **not**
compile Kotlin, run Android/Compose, instantiate a real Presentation or WebView,
render themes, validate native mask geometry, or prove lifecycle/visual behavior
on a device. Source assertions do not replace those checks. Android builds,
Android tests, frontend tests, and a parked vehicle/emulator smoke test remain
separate validation steps. No CarPlay/Android Auto implementation runs here.
