# Android Auto Service evidence and CLUSTER preflight (HAV-24)

Updated 2026-10-06. **Source integration and unsigned lab assembly now exist;
no independent CLUSTER stream or installable vehicle candidate is validated.**
The original preflight remains read-only; its default invocation exits2 without
changing files. The new exact-profile integration is separate and fail-closed.
See the latest integration section below; earlier phase notes are historical.

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

1. Use the supplied current Service identity below; record the actual firmware,
   running-process ABI/native mapping and APK hash during physical validation.
   Do not request the already supplied identical APK again
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


## 2026-10-06 current-source and native lifecycle evidence

Rafael reports that the installed Service is the Dropbox vendor copy. Marcel's
new direct `AndroidAutoService.apk` download was compared byte-for-byte with the
historical artifact above: identical, including SHA-256 `a9cfb4…ae1d8`. This resolves
which supplied APK to analyze; it is not an independent live-device inspection.

Marcel then added `androidauto-apks/lib` and `lib64`. Both native files were
inspected as ELF data, never loaded or executed:

| Artifact | Bytes | SHA-256 | ELF |
|---|---:|---|---|
| `lib64/libautoreceiver_jni.so` | 3,503,536 | `f2d5aeb527919f351b3de3aa60b4f89ca9cc740735ac1c7ed11e06c7d73a0065` | AArch64, Android API-28 note |
| `lib/libautoreceiver_jni.so` | 2,428,048 | `1a9f84758ad121d91fdebd176cd06542a14e9e9fa8500f7b5563c7af08eab14f` | ARM EABI5, Android API-28 note |

Both list `liblog.so`, `libc++.so`, `libc.so`, `libm.so`, `libdl.so` as dependencies.
Their JNI export sets match (127 unmangled `Java_*` names plus one mangled
nativeBugReport symbol). All 11 VideoSink natives invoked by the inspected DEX
have exports. The unused `nativeMaxUnackedFrames(I)V` declaration has no export;
the called `nativeSetMaxUnackedFrames(I)V` does. Actual process mapping/ABI still
requires device evidence, but the native artifact is no longer missing.

Static AArch64 virtual addresses below are evidence coordinates only. No prototype
or patch uses hardcoded native addresses:

- `VideoSinkCallbacks::dataAvailableCallback` at `0x1528b0` obtains
  `BufferPool.getBuffer(length)`, accesses `array`/`arrayOffset`, and copies native
  payload using JNI `SetByteArrayRegion` at `0x152a7c` before invoking Java at
  `0x152a98`. The Java buffer is not a borrowed native payload pointer. Retaining
  it for worker-thread consumption is supported; return it only after use/disposal
- `nativeAckFrames` at `0x1522ec` obtains this Java sink's native object, then
  `MediaSinkBase::ackFrames` at `0x16e50c` emits message `0x8004` with session/count
  on that endpoint. This does not recycle the Java buffer
- `VideoSink::setVideoFocus` at `0x175b54` sends per-sink `VideoFocusNotification`
  `0x8008` (mode + unsolicited). It does not locally stop transport. Its channel
  gate at `0x175bbc` drops pre-open requests; preserve pending demand until setup
- `handleSetup` at `0x175ae4` sends automatic unsolicited PROJECTED only when
  `autoStartProjection` is true. A pre-advertised sink with that flag false is a
  concrete on-demand design, not proof that the phone pauses/resumes encoding
- Java's `onVideoFocusModeChange` fires before native send. It is not phone ACK
- Java `registerCarService` stores/creates the provider; `startCarServices` later
  calls `nativeRegister`. Native `MessageRouter::registerService` at `0x15f9ec`
  rejects duplicate IDs/255 and inserts the endpoint; it does not re-advertise a
  running session

The OEM `VideoPlayer` cannot simply be reused as the demand-driven implementation:
it ACKs before queueing, has an unbounded queue, ignores null Surface updates,
does not reconfigure a live codec when replacing the Surface reference, and
clears queued buffers on release without individual pool returns. Its associated
`AapVideoManager` listener ACKs MAIN. Keep those paths untouched.

### Tested next slice: independent frame-pump prototype

[`prototype/`](prototype/README.md) now contains a standalone Java frame-pump
foundation: bounded retained allocations/frames, worker-only decoding, immutable
connection/Surface generation, separate exactly-once ACK/return attempts and
fail-closed overflow. It delays data ACK until consumption/disposal and released
local capacity, rather than copying the OEM early-ACK strategy. That timing
choice still needs phone validation; tests prove its local accounting only.

The actual Java code passes 24 JVM checks through one Python harness, alongside
all 18 existing preflight tests (19 Python tests total). It is not connected to
GAL/MediaCodec/Binder/the app, provides no installable test candidate and changes
no OEM APK. Patching remains disabled. Next is the real authenticated adapter,
codec/config/keyframe lifecycle and pre-session hook, then physical validation.


## 2026-10-06 source integration and unsigned assembly

The new [`integration/`](integration/README.md) sources now implement pre-session
paired registration, independent MediaCodec/config/IDR handling, bounded frame
ownership, per-sink ACK/focus, synchronous native retirement, authenticated
Binder Surface transport and host first-render readiness. Transaction55 is a
new private extension; the verified original Stub covers1–54 plus the interface
transaction. Only the concrete Binder override handles55; existing dispatch is
preserved. Whole-tree and class fingerprints reject other profiles/collisions.

The service preserves MAIN's providers/renderer and shared GAL lifecycle. Focus
off/on is CLUSTER-only and demand-driven; it is not proof of phone-side encoding
pause or guaranteed fresh IDR on resume. Annex-B config/IDR parsing and OEM-parity
config ACK0 still require actual phone captures. New output/config generations
cannot publish LIVE using a previous decoder's delayed rendered callback.

Caller trust requires the exact Impulse package/current unique UID plus an
explicit allowlist of current public signer SHA-256 fingerprints. The app pins
the observed OEM Service public signer. No caller is authorized by default, no
permissions or legacy transaction authorization change, and no key is generated.
The supplied Service uses system shared UID and the OEM platform signer; an
unsigned/re-signed APK is not a signature-compatible update. An authorized
loading/signing path and physical test remain separate owner decisions.

Local verification uses official pinned Android28 API, apktool3.0.2 and D8/R8
9.1.31. Real helper/client Java compilation and DEX/smali unsigned assembly have
succeeded. A preliminary assembled artifact resolved887 helper/OEM references;
manifest/resources remained byte-identical, only classes.dex changed, and old
signature files were removed. The reproducible builder additionally re-decodes
and checks final references/class inventory. Final exact counts and hashes are
emitted in report.json; no OEM APK, native binary or decompiled implementation is
committed or published.

Default validation builds retain the actual code branches but disable caller
handoff/registration. This checks assembly without pretending a test vehicle
feature is enabled. JVM/core/hook/build-gate tests do not execute MediaCodec,
Binder, native libraries or the supplied APK. PR CI adds real Android API Java
compilation and host Kotlin/Java compilation without signing/releasing. See the
PR's exact-head checks for terminal CI results, not an earlier green commit.

Still required: approved public client signer pins and loading route, parked-car
negotiation/codec/focus/Surface tests, Maps and Waze separately, actual ABI/native
mapping, MAIN/audio/TBT preservation and two settled FPS captures. No installation,
mount, security grant, OEM binary execution, signing, merge or release occurred.


Final review also identified an explicit **enabled-candidate blocker**: the
SurfaceHolder destruction callback currently queues asynchronous remote disable.
A duplicated handle does not prove remote MediaCodec quiescence before that
callback returns. Controlled hides need a stop acknowledgement, and forced
Surface/display loss needs a sound Android lifecycle design and validation.
The default artifact remains disabled; public signer pins alone do not clear
this blocker. See the integration README for the exact boundary.
