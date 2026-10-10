# HAV-24: offline package and file-evidence status on v9

This bounded source port targets `feature/new-screen-enhancements-v9` at
`2dad9adb36ad7793c101fa7df76147ae863b4469`. Packaging/status originate in
`f54ea1dd534b9fec6ab7a21d00cac001ce4676d3`, available in the reviewed PR152
head `5c0913a50265291a55cf103efa84ca918ef061c6`. Target input files were
materialized with exact Git blob verification before editing.

## Preserved target behavior

- The existing v9 `AaClusterProtocol.java` and `AndroidAutoClusterClient.java`
  are unchanged, including both accepted Service certificate fingerprints
- Every existing `AndroidAutoPatchManager` method before `getDiagnostics()`
  is unchanged, including legacy checksum predicates, install/mount/unmount,
  Service session polling, reload, certificate-failure rollback and preferences
- New manager methods only read independent App/Service file evidence;
  `getDiagnostics()` now distinguishes file evidence from runtime validation
- Both existing UI auto-mount switches and their handlers remain unchanged;
  this port neither enables nor removes the v9 Service opt-in
- Impulse Home install/grant behavior and the CarPlay UI remain unchanged
- No Service asset, certificate, permission, namespace, loading mechanism,
  release channel or vehicle setting changes are included

The v9 branch already contains functional CLUSTER/auto-mount work. The offline
package does not copy, replace or activate its bundled Service. It rebuilds the
separate exact-profile unsigned developer artifact from the pinned original
OEM input, solely to validate the source/tooling path.

## Schema 2 and existing v9 trust evidence

`report.json` has `report_schema=2`; `manifest.json` has `schema=2`. The profile
field `host_service_signers_sha256` is an array read and checked against the
actual unchanged target protocol declaration:

1. `7be3a99482e3f2f7f4f411f0a5a571ac97a505e500f9e05863fa8574e00baeb0`
2. `3c7d703011f11ea2a4baa35ba2c522d6b03e3af011d70dcb95c1331f11ad0f65`

This records the pre-existing allowlist; it does not broaden runtime trust or
prove an installed identity. The PackageManager predicate still requires
exactly one current APK signer and membership in that existing array. Package
preparation refuses removed, added or changed pins, a changed verification
predicate, schema-1 reports, the old singleton `host_service_signer_sha256`
receipt and a receipt that omits the second pin.

Generated *client* trust is separate. The default build remains empty/disabled.
The existing explicit enabled-build options are not used in the validation
below and do not themselves grant permission to load any artifact.

The source receipt hashes actual builder/helper/API-stub/protocol/host-Java
inputs and records the exact protocol/profile. It is not a hash of the entire
Android app and does not claim host Kotlin compilation. Source/tool/APK hashes
are checked again after assembly and copying. The manifest is an unsigned
integrity receipt, not an independently authenticated build attestation.

## Offline preparation

Use already available files matching the reviewed fingerprints in
`build_unsigned.py` and the pinned source APK profile. Java 21 is required for
full assembly. There is no download, signing, install or deployment step.

```sh
python3 scripts/aa-patches/integration/prepare_package.py \
  --android-jar /absolute/local/tools/android-28/android.jar \
  --source-apk /absolute/local/inputs/AndroidAutoService_vendor.apk \
  --apktool /absolute/local/tools/apktool_3.0.2.jar \
  --r8 /absolute/local/tools/r8-9.1.31.jar \
  --zipalign /absolute/local/sdk/build-tools/36.0.0/zipalign \
  --output scripts/.build/v9-cluster-developer.zip
```

The output must be a new named ZIP directly under `scripts/.build/`. Inputs
must be regular files without symlink components or parent traversal. Existing
outputs are refused. Fresh private staging, raw ZIP/resource verification,
fixed payload inventory, copy/hash checks and atomic no-replace publication
are retained. Assembly failure or an ordinary interruption before publication
does not expose a partial final ZIP. No previous successful report is imported.

The four entries are `AndroidAutoService-UNSIGNED.apk`, `GeneratedTrust.java`,
`report.json` and `manifest.json`. The package always records
`signed=false`, `deployment_ready=false` and `vehicle_validated=false`.

## App/Service status

The UI starts with unverified evidence and reads each component off the main
thread. Independent rows describe absence, staged presence, checksum equality,
checksum difference or an inconclusive/failed read. File absence is conclusive
only when the shell can establish access to the relevant parent/ancestor.
Checksum output must name the exact requested file and contain a valid MD5.

Checksum equality is not proof of a mount, loaded process, CLUSTER candidate,
authenticated handshake or LIVE stream. The runtime caveat is explicit.
Evidence rows and the caveat sit outside the fixed-height feature card, leaving
the existing two switches' layout intact. Poll generations prevent older reads
from replacing state reset by an install, activate, deactivate or uninstall
action. Diagnostics likewise avoid upgrading checksum/OAT output to readiness.

## Validation on 2026-10-10

```sh
python3 -m unittest discover -s scripts/aa-patches/tests -v
```

- Focused package tests: 36 passed, including mocked assembly and adversarial
  path/report/hash/pin/schema/copy/publication cases
- Focused status tests: 5 passed; the production Java model's executable JVM
  harness passed 499 assertions, including stale poll/reset behavior
- Existing-v9 host boundary guards: 3 passed
- Current combined Python suite: 222 passed, zero skips at this checkpoint;
  final combined source verification is recorded separately by the coordinator
- Actual pinned Android 28 helper/host-Java compilation and full disabled
  unsigned preparation passed on Linux/Java 21
- Final re-decode verified four hooks, 121 preserved original methods and 4,897
  unchanged OEM classes; only `classes.dex` changed, original signatures were
  removed, manifest/resources stayed byte-identical and all eight STORED output
  entries were aligned to four bytes
- The produced ZIP was reopened; its fixed inventory, hashes, sizes, disabled
  trust and report provenance were checked against the current source inputs

That local run produced an unsigned Service of 2,460,991 bytes, SHA-256
`05b51e577bc67e8bea78e311c19470ed8ae894e5d6871bd7d79d6ec438b740f1`,
inside a 2,477,722-byte package, SHA-256
`dccb471761e8443f6dcb44e539316e547d05b5c1fda9e7d493983687309d1d25`.
ZIP timestamps may differ on another run; these are observed output identities,
not a promise of timestamp-independent binary reproducibility. The local
artifacts and logs remain under `scripts/.build/` and are not source commits.

Not run here: full Android app/Compose build, rendered/large-font UI checks,
live Shizuku/device shell checks, Binder/MediaCodec execution, vehicle testing,
signing, installation or deployment. The package report explicitly retains
`host_kotlin_compile=false`. A source test or unsigned package is not vehicle
acceptance. See [TEST-PLAN.md](TEST-PLAN.md) for the separate runtime gates.
