# Android Auto Service evidence and CLUSTER preflight (HAV-24)

Updated 2026-10-04. **The second CLUSTER stream is not implemented or validated
on the car.** The current tool is read-only; default patch invocation exits 2
without changing any file. Passing the API preflight does not change that gate.

## Artifact identity

| Artifact | Size | MD5 | Evidence |
|---|---:|---|---|
| Vendor `AndroidAutoService_vendor.apk`, shared in `/Haval/androidauto-apks/` on 2026-09-07 | 2,423,318 bytes | `48ffded64e9b485521e3174dcd70db27` | Independently inspected historical APK on 2026-10-04 |
| Impulse bundled `app/src/main/assets/aa_patches/AndroidAutoService.apk` | 6,490,581 bytes | `54df14713bf26466af55a76382a67ce6` | Earlier 2026-09-11 comparison; not revalidated by this preflight |

Historical vendor SHA-256:
`a9cfb4c0e559f9638466a80d77a861ffd4b5737d5ac9f71e0c20370f048ae1d8`.
Package: `com.ts.androidauto.projectionservice`, versionName `9`, versionCode `28`,
target SDK 28. The compressed APK contains a roughly 6.3 MB DEX. This copy does
**not** establish which build is currently installed on a vehicle. Preserve the
original bytes; do not commit the vendor APK or decompiled implementation.

The 2026-06-24 `stock48ff` deploy candidate (`27c625…`) is a different, known-bad
SSL-client-certificate build. A filename containing `stock48ff` is not identity.

## Corrected contracts in the historical 48ff DEX

The old `setSurface` claim was incorrect: `VideoSink` has no such method. A
string search across the DEX had confused separate classes. Checked definitions:

- `VideoSink.<init>(VideoSink.ProjectionListener, boolean, int)`
- `VideoSink.setDisplayIdAndType(int, Protos.DisplayType)`; the public setter takes
  the enum, not the two integers accepted by its private native implementation
- `VideoSink.addSupportedConfiguration(Protos.VideoConfiguration)`,
  `setVideoFocus(int, int, boolean)` and `updateUiConfig(Protos.UiConfig)`
- `InputSource.<init>(InputSource.InputInjector)`, `registerKeyCodes(int[])` and
  `setDisplayId(int)`
- `GalReceiver.registerCarService(int, CarServiceProvider): boolean`
- `GalIntegration.registerCarService()`
- `AapVideoManager.showVideo(Surface, int, int)` and
  `VideoPlayer.updateSurface(Surface, int, int)`
- `LinkCallback.onNotifyNextTurn(IfNavigationData)`,
  `onNotifyNextTurnDistance(int, int, int, int)` and
  `onNavigationState(IfNavigationStateData)`

`VideoSink.ProjectionListener` receives codec configuration/setup and
`VideoFrame` callbacks. That is not a direct Surface API. The existing
`AapVideoManager`/`VideoPlayer` route is used by MAIN; calling `showVideo` on the
existing manager is **not** a verified independent CLUSTER rendering solution.
Frame ownership, acknowledgements, scheduling and an independent decoder still
need implementation and review. Do not copy frames on the GAL reader thread.

In this historical build, NAV registration uses service id 9 and is gated by
`VehicleInfo.mIsSupportDisplayNaviDataUpdate`. This is a static observation, not a
request to alter the OEM flag. The existing Impulse `AndroidAutoNavigationMonitor`
already binds `LinkCallback`; preserve that path instead of adding a duplicate
NAV patch. CarPlay navigation is not established by any of these AA signatures.

## Reproducible, read-only preflight

For a separately obtained and decoded Service APK (apktool output), run:

```bash
python3 scripts/aa-patches/patch_android_auto_service_cluster.py \
  scripts/.build/aa-service-cluster --check-contract --json
python3 -m unittest discover -s scripts/aa-patches/tests -v
```

No decoder, APK, helper class or registration hook is written by this tool.
Decode/build intermediates belong under `scripts/.build/` as required by
[`../README.md`](../README.md). The old bare invocation intentionally refuses:

```bash
python3 scripts/aa-patches/patch_android_auto_service_cluster.py \
  scripts/.build/aa-service-cluster
# exit 2: patching disabled; no mutation
```

Exit codes: `--check-contract` returns 0 for a match of the **known API subset**,
1 for a mismatch/unreadable input; patch mode always returns 2. The JSON always
sets `cluster_implemented` and `deployment_ready` to false. It includes exact
class descriptors, expected signatures, file hashes, errors and remaining gates.
The scanner accepts `smali` and `smali_classesN`, rejects duplicate target
classes/declarations, and checks declared public instance methods and the public
static CLUSTER enum field. It never accepts a method name found only in another
class, a comment, string or call. This is not an assembler or APK verifier; it
does not inspect native libraries or prove service ids are free on the target.

## Verification performed for this change

- 18 synthetic declaration/CLI regression tests passed, including unchanged
  input trees on success, refusal and prior-helper paths
- 18 required method definitions plus the CLUSTER enum field independently
  compared against the historical APK's DEX definitions; all matched, with no
  `VideoSink.setSurface` declaration
- The preflight passed on a declaration-only tree derived from those DEX
  definitions; this is **not** an apktool decode or smali assembly test
- Android build/unit tests attempted with `./gradlew :app:assembleDebug
  :app:testDebugUnitTest`; default Gradle cache was not writable. Retrying with
  `GRADLE_USER_HOME` in `scripts/.build/` reached the wrapper download but failed
  because `services.gradle.org` was unreachable. Android checks did not run
- No OEM APK rebuilt, signed, staged, mounted or installed; no vehicle testing

## Gates before a deployable implementation

1. Obtain the currently installed Service APK with explicit vehicle access;
   record SHA-256, package/version and head-unit firmware alongside the decode
2. Recheck the current registration path and free IDs. The historical proposal
   is video 21/input 22, displayId 1/CLUSTER, 1280×720 at 160 dpi, D-pad 19–23.
   These values are prior-art targets, not new vehicle measurements
3. Implement independent registration and rendering with cleanup, backpressure,
   frame ownership/ACKs and an authenticated Surface handoff. Keep MAIN on D0;
   do not silently reuse its decoder/manager or move `AapActivity`
4. Advertise before the session; mid-session requests must wait for the next
   session without showing a black D3. A mount/sentinel or visible Surface alone
   does not prove the phone accepted the CLUSTER handshake or produced frames
5. With an authorized parked-vehicle validation setup, check enable/disable,
   reconnect, Surface recreation, Maps and Waze separately, plus two settled
   CLUSTER/MAIN fps runs and TBT freshness. Preserve existing theme contract and
   map-only layout; no duplicate trip ETA overlay
6. Only after implementation and physical evidence may a reviewed candidate be
   built/promoted. No release or vehicle operation is authorized by a preflight

Never use Frida, socket frame copies, screencap mirroring or an unplanned
`force-stop` of `com.ts.androidauto.projectionservice` to close these gaps.
