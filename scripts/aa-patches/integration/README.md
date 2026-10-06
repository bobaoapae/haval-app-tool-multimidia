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
- The app duplicates Surface handles without hidden APIs; each in-flight Binder
  request owns a lease. Holder lifecycle generations suppress duplicate enables
  and stale callbacks. The existing AA_CLUSTER_SURFACE event becomes true only
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

## Blocking Surface lifecycle gap

The current host revokes output asynchronously on `surfaceDestroyed`. A
parcel-duplicated Surface owns its handle, but this does **not** prove that the
remote decoder is quiescent before SurfaceHolder's destruction callback returns.
Android requires that rendering access stop at that boundary. Controlled hides
can be redesigned to await an explicit decoder-stop acknowledgement before
removing the view; forced display/Surface destruction still needs a sound
lifecycle design and Android validation. An arbitrary timeout is not proof.

Keep the current artifact disabled. This issue blocks an enabled vehicle test
candidate even if public signer pins and a loading path become available.
No source/assembly test here claims to close that gap.

## Remaining gates

1. Public SHA-256 fingerprints for the intended installed Impulse signer(s)
2. An owner-approved signature-compatible loading path: the stock Service uses
   `android.uid.system` and an OEM platform certificate. Rebuilding invalidates
   its signatures. This work supplies no signing authority or security workaround
3. Resolve the Surface destruction barrier above; finish host full-app compilation/
   CI and independent review for the final commit
4. Authorized parked-vehicle tests: actual firmware/native mapping, phone
   negotiation, Maps/Waze separately, Annex-B config and IDR on resume, focus-off
   encoding/power behavior, callback timing, hardware decoder capacity, repeated
   enable/disable/Surface replacement/reconnect, MAIN/audio/TBT preservation and
   two settled MAIN+CLUSTER FPS captures

Source tests and unsigned assembly do not establish physical behavior or a
successful independent stream. Dynamic late-session advertisement is not
implemented: the second endpoint must be registered before discovery. Local
focus callbacks are never treated as phone acknowledgement or live video.

## Local result recorded 2026-10-06

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
