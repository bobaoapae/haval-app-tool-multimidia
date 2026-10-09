# Offline unsigned developer package

`prepare_package.py` prepares a reviewable ZIP containing a freshly assembled
unsigned AndroidAutoService and its evidence. It is **not an installable or
autopatched vehicle candidate**, and is not a release artifact. All output stays
under `scripts/.build/`; nothing is written to application assets or milestones.

## Run from current source

Use already supplied, fingerprint-matching tools and the exact supported Service
APK. Prerequisites and public tool hashes are in [README.md](README.md). Full
assembly requires the reviewed Java 21 profile. No tool downloads, credentials,
APK signing, privileged shell, installation, bind mounts or car connection are
part of this command.

```sh
python3 scripts/aa-patches/integration/prepare_package.py \
  --android-jar tools/android-28/android.jar \
  --source-apk /absolute/path/to/exact-Service.apk \
  --apktool tools/apktool_3.0.2.jar \
  --r8 tools/r8-9.1.31.jar \
  --zipalign /absolute/path/to/SDK/build-tools/36.0.0/zipalign \
  --output scripts/.build/cluster-developer.zip
```

The output must be a **new ZIP directly inside `scripts/.build/`**. Input paths
must be regular files without symlink components or `..` traversal. Use absolute
paths for tools outside the checkout. Existing output files, directories and
dangling symlinks are refused. The local filesystem must support same-filesystem
hard links; unsupported atomic publication fails without an overwrite fallback.

Default handoff and CLUSTER registration remain disabled, with no client pins.
The existing explicit `--enable-handoff` plus repeated
`--client-cert-sha256 <lowercase-public-SHA256>` options are accepted only together.
An enabled package records and verifies exactly those normalized public pins; it
still leaves every vehicle gate blocked. The command does not identify the
installed client's signer or grant approval to enable trust or deploy. Never
supply private signing keys or passwords.

The host's only accepted Service signer remains
`7be3a99482e3f2f7f4f411f0a5a571ac97a505e500f9e05863fa8574e00baeb0`.
Preparation rejects a changed/multiple-pin declaration or altered single-signer
predicate. It cannot make this unsigned Service satisfy that authentication gate.

## Contents and evidence

The ZIP has exactly four root entries:

- `AndroidAutoService-UNSIGNED.apk`: freshly built Service, never signed
- `GeneratedTrust.java`: exact UTF-8/LF generated public trust configuration
- `report.json`: successful full assembly evidence and source/profile/tool hashes
- `manifest.json`: hashes of the three payload files, actual Service byte size
  and SHA-256, preparer hash, source provenance, trust configuration and blocked gates

The source inventory hashes all Service helper/API-stub Java inputs, shared
protocol/frame-pump/ownership code, compiled host Java inputs and the builder,
hook patcher and final-reference verifier. Its inventory digest is SHA-256 of the
UTF-8, sorted-key, compact JSON path-to-SHA256 map. Raw source bytes, including
line endings, are hashed. This identifies the relevant build inputs, **not the
entire Android project or an independently verified Git commit**. The generated
trust hash is separate because that source is derived from the explicit options.
The profile receipt also records `protocol_version=2` and
`protocol_profile=stock48ff-cluster-v2`, checked against the current protocol
source constants; a changed pair contract is refused.

`manifest.json` is an **integrity receipt, not a signed attestation**, independent
authentication of executable provenance, or permission to load the artifact.
Anyone able to change all package bytes could also recompute its hashes. Review
and trust the local scripts/source/tool pins independently. Do not interpret a
self-consistent package received from another party as proof of a trusted build.

## Fail-closed workflow

1. Validate destination, exact APK/tool fingerprints, trust configuration and
   retained host single-pin verification before beginning assembly
2. Snapshot the actual source inventory and run the existing builder in fresh
   private staging under `scripts/.build/`. There is no import of existing build
   directories/reports and no compile-only, signing or deployment mode
3. Require successful compilation, full unsigned assembly, final DEX reference
   and hook evidence, exact reviewed profile, unchanged OEM inventory, matching
   generated trust and pinned toolchain. Independently re-run the strict raw ZIP,
   alignment, unsignedness and retained manifest/resource comparisons against
   the original supplied APK; report booleans and hashes alone are insufficient
4. Copy only fixed payload filenames into private packaging staging and verify
   source/copy hashes. Also bind the copies to the already validated APK hash,
   requested trust bytes and type-preserving canonical JSON build report. Write
   the manifest, build the ZIP, then check its exact
   inventory and every payload hash/Service size
5. Recheck all source/tool/APK inputs and host trust after packaging. Publish the
   fully checked ZIP using an atomic, no-replace hard link, then remove private
   staging. A concurrently created destination wins and is never overwritten

Malformed/duplicate/non-finite JSON, missing or compile-only evidence, mismatched
source/profile/pins, changed resources, signatures, damaged copies, unexpected
paths and false readiness all fail. Ordinary exceptions and KeyboardInterrupt
before publication clean private staging and leave no destination. After the
atomic publication step, an interruption or staging-cleanup error can leave the
complete verified final ZIP; it is never silently overwritten on retry.
A hard process kill or power
loss can leave private staging for manual inspection; it cannot expose a partial
ZIP via the final publication step. Atomic visibility is not a power-loss
filesystem durability guarantee. As with the existing builder, this assumes a
trusted local workspace rather than defending against a hostile process changing
files and reverting them between verification reads.

The package always records `deployment_ready=false`, `signed=false` and
`vehicle_validated=false`. Blocked gates cover approved signing/signature-compatible
loading, installed-client identity and enabled trust, Android/native lifecycle
and host runtime, and authorized parked-vehicle validation/recovery. See
[TEST-PLAN.md](TEST-PLAN.md) and the outstanding review findings in
[INTEGRATION-20261009.md](INTEGRATION-20261009.md). Packaging does not resolve those
findings, change firmware support, modify layouts/protobuf, or establish live
independent CLUSTER video.

## Validation and handoff

Run the regression suite from the repository root:

```sh
python3 -m unittest discover -s scripts/aa-patches/tests -v
```

`test_cluster_package_preparation.py` uses synthetic APKs and a mocked assembly
boundary. Its 34 tests cover disabled/enabled pin consistency, raw ZIP rechecks,
malformed reports, bad hashes/profiles/manifests, source/tool immutability, fixed
paths/symlinks/traversal, failed/interrupted assembly and copying, existing and
racing outputs, mutation between validation and copying (including numeric
substitution for JSON booleans), interruption after
publication, unsupported atomic publication, explicit UTF-8/LF trust output,
source-bound compile-only receipts, retained Service pin, exact protocol pair
and no false readiness.
These tests are not actual Android or OEM execution. Native Windows can skip the
real-symlink fixture only for missing Windows symlink privilege; simulated
symlink branches still run.

Local implementation validation on 2026-10-09: the focused tests passed and
actual Java helper/host compilation against the pinned Android 28 API passed
with Java 21 and default handoff disabled. All 210 aggregate tests passed locally
with no skips. Exact-head CI and full preparation evidence must be recorded
separately from these synthetic source tests.
No APK was executed, signed, installed or applied to a vehicle by these tests.

Final-source full preparation also passed on Linux/Java21 with the pinned tools
and stock Service on 2026-10-09. The disabled unsigned Service was 2,461,183 bytes,
SHA-256 `3981a7cf9f128b64ed2750eac3cb58c7e554ec80dcc2d0ca44cff9d01bd06b90`.
The local ZIP was 2,477,724 bytes, SHA-256
`15ce6b2e814f0ec188faeaf982c5c9c3c454bfb5959c6c435da144df1f444926`.
These identify that local run, not a promise of timestamp-independent binary
reproducibility. No OEM binary is committed or published with this source change.

Final re-decode verified four hooks, 121 preserved original methods and 4,897
preserved OEM classes. Only `classes.dex` changed; manifest/resources stayed
byte-identical, original signatures were removed, and all eight STORED output
entries were aligned. The package was re-opened and every payload hash/length
checked against the manifest. `unsigned_assembly=true`; handoff, signing,
deployment readiness and vehicle validation remain false.

The separate App/Service status model passed 499 executable JVM assertions.
All nine existing manager mutation/automount bodies and the CarPlay card remain
unchanged. Normal fixed-height card layout was inspected in source; rendered
Compose/large-font/head-unit layout and live shell/device behavior were not
tested locally. Exact-head Android CI remains a separate required check.
