# Independent CLUSTER source integration (HAV-24)

This is an **unsigned, exact-profile lab implementation**. It is not an
installable vehicle candidate, a release, or proof that a phone sends CLUSTER
video. Default builds have caller handoff and CLUSTER registration disabled;
they still compile the real branches, rather than substituting success stubs.
The original read-only preflight is unchanged and continues refusing patch mode.

## Implemented source path

- Exact pre-start hook registers independent video 21/input 22 atomically under
  the existing receiver monitor, after checking both slots and fresh providers.
  Failed creation/configuration rolls back only owned providers. No native offset
  patches and no replacement of the MAIN sink, renderer, channel or listener
- CLUSTER advertises displayId 1, H.264 baseline, 1280×720/30fps, density160 and
  the existing vehicle viewing distance. These are explicit lab-profile values,
  not new device measurements. The host's existing D3 bounds remain unchanged
- `autoStartProjection=false` suppresses automatic projection. Pending demand
  waits for setup. NATIVE/PROJECTED focus is sent only through the CLUSTER sink.
  A replacement Surface first withdraws focus, closes the old decoder, installs
  the new bounded pump, then requests projection. No shared-GAL stop/reconnect
- Each buffer retains its original session/sink and immutable codec-config
  generation. The worker copies it into its own asynchronous Android MediaCodec,
  then returns pool ownership and ACK credit. Data admission is bounded to16
  frames/8MiB including in-flight backing arrays; overflow terminates that output
- The first IDR is queued while codec creation occurs, not discarded on the GAL
  callback. Annex-B SPS/PPS and IDR presence are checked; MediaCodec validates
  codec contents. Config is separate from data and reproduces the observed OEM
  synthetic session0 ACK. Phone framing/credit/restart semantics still need proof
- Codec input waits750ms, missing config2500ms, first rendered generation6s.
  Non-IDR recovery discards are bounded to120 frames. Oversized access units fail
  rather than inventing fragmentation. One process-wide CLUSTER decoder slot
  prevents overlap across receiver lifetimes; uncertain release quarantines it and its retained Surface until process restart
- Receiver teardown synchronously prevents later native focus/ACK before the
  OEM destroys endpoints. Decoder/Surface cleanup stays on the worker. Hiding
  CLUSTER does not destroy the shared receiver or touch MAIN/audio/TBT
- New transaction55 exists only in the concrete Service Binder. Every original
  transaction delegates unchanged to OEM super. Both client and server validate
  version, package/current UID, public signer identity and bounded parcel shape.
  Missing explicit Impulse signer pins fail closed before Surface unmarshalling
- The app owns the actual callback-provided SurfaceTexture through a TextureView,
  with separate view, desired-output, remote and in-flight transport leases.
  Terminal release identity survives close, disconnect and newer requests. The existing AA_CLUSTER_SURFACE event becomes true only
  after an authenticated current MediaCodec frame-rendered callback. No JS keys,
  methods, theme layout or CarPlay path change

See [API-CONTRACT.md](API-CONTRACT.md) for exact hook/ownership contracts and
[the evidence report](../DUMPS_V2_6_CLUSTER.md) for supplied artifact hashes.

## Reproducible local validation

Required tools are JDK17+ (Java8 source syntax), Python3, and the exact public
Android28 API jar. Full unsigned assembly additionally needs the user-supplied
Service APK, apktool3.0.2 and R8/D89.1.31. `build_unsigned.py` pins SHA-256 for
all tools and the APK. It neither downloads nor executes the supplied APK/native
libraries. API stubs are compile-only declarations and are excluded from DEX.

Official tool sources:

- https://dl.google.com/android/repository/platform-28_r04.zip (android.jar)
- https://github.com/iBotPeaches/Apktool/releases/download/v3.0.2/apktool_3.0.2.jar
- https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.1.31/r8-9.1.31.jar

Run from the repository root with already obtained tools:

    python3 -m unittest discover -s scripts/aa-patches/tests -v
    python3 scripts/aa-patches/integration/build_unsigned.py \
      --android-jar tools/android-28/android.jar \
      --compile-only --output scripts/.build/cluster-source-validation

Full local unsigned assembly into a NEW directory:

    python3 scripts/aa-patches/integration/build_unsigned.py \
      --android-jar tools/android-28/android.jar \
      --apktool tools/apktool_3.0.2.jar --r8 tools/r8-9.1.31.jar \
      --source-apk <supplied-exact-Service.apk> \
      --output scripts/.build/cluster-unsigned-validation

No pins are guessed from a package name or an arbitrary debug key. Enabled
handoff requires both `--enable-handoff` and one or more explicit
`--client-cert-sha256 <lowercase-public-certificate-SHA256>` arguments. This does
not sign the Service, authorize installation, or make `deployment_ready` true.
Do not send private signing keys/passwords to this tool or commit them.

The builder uses fresh staging, compiles real helper/client Java against the
pinned SDK, DEXes only program classes, validates generated helper/OEM member
references, applies hooks to the exact original tree, assembles without signing,
then re-decodes and verifies references/class inventory again. Existing OEM
classes outside the three hooks must remain text-identical after normalizing
only apktool's omission of redundant static boolean false defaults. Manifest
and every other non-signature ZIP entry must be byte-identical; only classes.dex
changes. Original signature entries must be absent. It publishes report.json
and the new output directory only after checks pass; never overwrites an input.

CI runs pure-core/hook/build-gate tests, Android28 Java compilation, host Kotlin/
Java compilation and existing JVM unit tests. It does not receive the private
OEM artifact, assemble an OEM APK, sign, access credentials, or publish a release.

## Consumer ownership and terminal release (private protocol v2)

This supersedes the earlier SurfaceHolder destruction blocker. `AaClusterVideoHost`
now uses a TextureView while preserving its existing bounds and position under
masks/WebView. Its destruction callback returns false for an adopted consumer,
so the framework detaches its hardware layer but does not release that
SurfaceTexture. `ClusterSurfaceOutput` retains the consumer itself, not merely a
Surface producer handle. Fresh view generations use fresh consumers; no
`setSurfaceTexture`, manual GL attach/detach, or `updateTexImage` call is added.

The host retires its view owner immediately without waiting. Actual consumer
release occurs on main only after that owner and every borrow have closed:

1. The client records possible remote ownership before sending an enable. A
   separate transport fork covers serialization and the transaction, including
   a terminal event that arrives before the transaction returns
2. Only one remote output is admitted. Changed demand first sends disable and
   awaits its predecessor's terminal event. Pending toggles coalesce; they cannot
   accumulate remote decoders or replace an unresolved record
3. The Service seals each retired request against new jobs. Its lease barrier
   sends `CALLBACK_RELEASED(requestId)` only after all decoder borrows close
   successfully and the Service Surface is released. Codec cleanup uncertainty
   retains the borrow and Surface, so no terminal event is fabricated
4. The client authenticates that event against the exact retained Binding object,
   caller UID and request ID. It is independent of current output/status revision
   and remains usable after client.close. Stale/duplicate events cannot free a
   newer request. A local disposal failure remains quarantined without retries
5. Death, disconnect, unbind, RemoteException, acceptance, DISABLED and FAILED are
   not terminal proof. Binder death does not prove downstream codec-service
   cleanup; that lease stays quarantined. A five-second retirement watchdog
   changes status to failed but never releases ownership or admits a replacement
6. A process-rooted pool caps adopted consumers at two: one remotely exposed plus
   one never-submitted view. If cleanup cannot be proved, later adoption/submission
   stays blocked instead of freeing an uncertain consumer. A stale view callback
   retires only its matching retained consumer

Protocol version, descriptor and profile are bumped to v2 so a v1 client/helper
pair cannot silently interpret status as terminal ownership. Legacy OEM Binder
transactions and the public theme/JS contract are unchanged. Default generated
trust is still empty, and no enabled or installable artifact is being published.

This resolves the identified source ownership gap. It does not bound a vendor
native MediaCodec.stop/release call: a native hang is isolated to its worker and
retains the bounded quarantine; the UI does not wait. Android/vehicle execution
must still verify teardown when an offscreen consumer stops draining and the
composition/performance implications of TextureView.

Sources: [TextureView listener contract](https://developer.android.com/reference/android/view/TextureView.SurfaceTextureListener),
[Surface consumer ownership](https://developer.android.com/reference/android/view/Surface),
[Android 9 TextureView implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/core/java/android/view/TextureView.java),
and [SurfaceView/TextureView composition](https://source.android.com/docs/core/graphics/arch-tv).

## Remaining gates

1. Public SHA-256 fingerprints for the intended installed Impulse signer(s)
2. An owner-approved signature-compatible loading path: the stock Service uses
   `android.uid.system` and an OEM platform certificate. Rebuilding invalidates
   its signatures. This work supplies no signing authority or security workaround
3. Validate the v2 consumer/terminal-event lifecycle on Android, including forced
   display loss, offscreen codec close, lost replies and death. Check exact-head
   host compilation/CI; JVM ownership tests do not execute Android/native graphics
4. Authorized parked-vehicle tests: actual firmware/native mapping, phone
   negotiation, Maps/Waze separately, Annex-B config and IDR on resume, focus-off
   encoding/power behavior, callback timing, hardware decoder capacity, repeated
   enable/disable/Surface replacement/reconnect, MAIN/audio/TBT preservation and
   two settled MAIN+CLUSTER FPS captures

Source tests and unsigned assembly do not establish physical behavior or a
successful independent stream. Dynamic late-session advertisement is not
implemented: the second endpoint must be registered before discovery. Local
focus callbacks are never treated as phone acknowledgement or live video.

## Initial v1 local result recorded 2026-10-06

The reproducible full build passed with default handoff disabled. Its
[report](validation-20261006.json) records51 helper/compiler-metadata classes,
1266 symbolic references (41 OEM members and10 OEM override checks),4897 preserved
OEM classes and only the three planned hook classes changed. Android/Java
platform references were compiled against the actual SDK; the smali verifier
reports them separately rather than claiming OEM resolution.

Unsigned validation APK SHA-256:
`c2049eeffbbd2f53ecbdd4fad766c618d4636d80e4f7fec7360b05666380348e`.
This hash identifies the disabled local validation build, not a distributed or
installable test candidate. APK ZIP timestamps can differ in a later rebuild;
input/tool hashes and semantic/resource invariants are the reproduction gates.

Public Android API contracts used:
[SurfaceHolder callbacks](https://developer.android.com/reference/android/view/SurfaceHolder.Callback)
and [MediaCodec frame-rendered callbacks](https://developer.android.com/reference/android/media/MediaCodec.OnFrameRenderedListener).
The rendered callback establishes delivery to the output Surface, not optical
proof on a car or success for every frame; the parked-car checklist still applies.

## Observed published Impulse certificate (not an active allowlist)

The repository's [v1.0.0.88-preview release](https://github.com/bobaoapae/haval-app-tool-multimidia/releases/tag/v1.0.0.88-preview)
contains package `br.com.redesurftank.havalshisuku`, versionCode89. The downloaded
60,856,077-byte APK matched GitHub asset614084222's published SHA-256:
`4ab4cb2807a3d001b6d7d6bfe7ce3c1c67fa992ebf6eec5425ec57313f55ec40`.
Its APKv2 signing-block public certificate has SHA-256:
`086d315e4a9c4f7b146dee9386841f3cb294f0f1e10a799d0d3ed97c3c02927d`.

This is static public metadata extraction, not full APK-signature verification,
not confirmation of a tester's installed app, and not approval to enable trust.
No private key was read. Default generated trust remains empty/disabled. The
Surface lifecycle and approved loading gates remain regardless of this finding.

The final roundtrip also verifies all four OEM-to-helper hook calls and preserves
all121 other original methods in the three hook classes. The full116-test Python
suite includes25 actual Java frame-pump checks and33 actual Java registration/
native-guard/NAL checks; both JVM harnesses passed20 additional consecutive runs.
Synthetic hook/member/build-gate tests are not Android execution. Independent
review found no additional source-draft blocker beyond the stated lifecycle and
physical-validation gates.

## Quiescence redesign validation

The new [v2 local report](validation-quiescence-20261006.json) records successful
Android 28 helper/client Java compilation and full disabled unsigned assembly:
55 helper/compiler classes, 1,342 symbolic references, 41 OEM members, 10 override
checks, four final hook calls, 4,897 preserved OEM classes and 121 preserved other
methods in the three hook classes. Manifest/resources remain byte-identical;
only classes.dex changes, and old signatures are removed. Unsigned local SHA-256:
`d8dba90751449da88184abd8a7aa90afa286d2c36977a47d3761e5d0d688a779`.

All 118 Python tests pass. Their four actual-source JVM harnesses exercise 97
checks: 25 frame-pump, 33 registration/guard/NAL, 18 lease-barrier and 21 release-ledger.
The new harnesses each passed 20 additional runs. Cases include forced view-owner
retirement while a remote borrow is live, ACK before transport returns, wrong
connection/request, stale/duplicate ACK, concurrent claims and settlement,
uncertain/dead connections, reentrant callbacks and failed disposal retention.
These simulate ownership events with real project code; they do not claim to
execute a real forced display teardown or Android Binder/MediaCodec.

Independent review found and corrected failed-disposal record loss, the terminal
retirement-state race and stale texture callback ownership. Full host Kotlin/Java
and existing JVM unit tasks are checked by the PR's exact-head CI after publication.
