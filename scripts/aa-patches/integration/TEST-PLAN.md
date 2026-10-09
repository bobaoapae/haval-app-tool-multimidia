# Authorized CLUSTER v2 validation plan

This plan begins with the reviewed source and unsigned build. It does not make
the default artifact installable: caller trust is empty/disabled, and signing,
loading and device execution require separate owner approval. Keep HAV-24 open
until the required runtime evidence exists.

## Approval and artifact record

Before any device action, record:

- Exact PR commit and green checks for that head
- Intended test Impulse package and approved public signer SHA-256. The observed
  v1.0.0.88-preview certificate is
  `086d315e4a9c4f7b146dee9386841f3cb294f0f1e10a799d0d3ed97c3c02927d`;
  this is a candidate identity, not approval or proof of the installed tester app
- Who controls the legitimate signing/loading process, the named parked vehicle
  or Android lab target, and the approved recovery process. The OEM Service uses
  `android.uid.system` and platform signer SHA-256
  `7be3a99482e3f2f7f4f411f0a5a571ac97a505e500f9e05863fa8574e00baeb0`
- Hashes, package/version, public signer verification and provenance for both
  final artifacts. The v2 host and Service must be paired; a v1 peer is rejected
- Head-unit firmware/Android version, actual running Service/native ABI mapping,
  phone/Android Auto version and tested navigation app version

Do not provide private keys/passwords in the issue or this toolchain. If a
signature-compatible, authorized loading route is unavailable, stop the device
portion. This plan contains no permission grants, remount/bind-mount, signature
bypass or OEM service restart commands.

## Before loading

1. Run the suite and pinned local builder described in [README.md](README.md).
   Keep the source APK unchanged. Require successful helper linkage, final hook
   checks, manifest/resource preservation and the [M0 ZIP structure gate](README.md#m0-zip-structure-gate)
   (source method preservation, local/central agreement, bounded valid bit-3
   descriptors and four-byte STORED alignment); inspect report.json rather than
   treating a successful compiler exit alone as a candidate
2. Once the intended caller fingerprint is explicitly approved, the responsible
   engineer may prepare an enabled unsigned lab build using the documented
   explicit trust arguments. That changes the artifact hash; record it. It still
   has deployment_ready=false and requires the separately approved signing and
   loading process. Do not replace a missing signer with an arbitrary debug key
3. Verify the final signed artifacts with the owner's existing official Android
   SDK verification tools before loading. A failed signature check on the
   deliberately unsigned local report artifact is expected, not permission to
   bypass PackageManager. Preserve the original supplied APK and approved
   recovery artifacts separately
4. The authorized operator performs the approved loading steps. This plan does
   not authorize the assistant to install, sign, modify security settings or
   operate the vehicle. Keep the vehicle parked for all user-facing checks

## Baseline and ordinary operation

For every row, record PASS, FAIL, NOT RUN or BLOCKED, the exact artifact pair,
timestamps and observation method. A missing measurement is not a pass.

| Check | Procedure | Required observation |
|---|---|---|
| MAIN baseline | Establish AA with CLUSTER demand off; use the existing approved diagnostics | MAIN image, audio and TBT work; record their baseline behavior/FPS |
| First enable | Request the cluster map in a session where the v2 endpoint was advertised before discovery | A current authenticated frame appears on D3; MAIN/phone connection persists; mount hash or a shown view is insufficient |
| Disable/re-enable | Toggle through the existing Impulse/theme controls, then repeat at least ten times | Cluster decoder closes and terminal RELEASED settles the old output before replacement; no phone reconnect, MAIN stall or stale map readiness |
| Rapid toggles | Alternate on/off quickly, finishing both once on and once off | Final demand wins; stale callbacks do not reopen the map; outputs do not accumulate |
| View recreation | Use the normal supported app/Presentation lifecycle to remove and recreate its view | Old consumer survives decoder retirement; a later generation renders; original bounds, masks and TBT remain correct |
| Phone reconnect | Disconnect/reconnect using the already approved normal AA procedure | Old native endpoint is retired; no ACK reaches a freed endpoint; new session is advertised before discovery and demand is handled correctly |
| Maps and Waze | Repeat separately for apps already available to the tester | Record actual second-stream support independently; success in one app does not prove the other |
| Settled performance | Capture two settled MAIN/CLUSTER runs, with timestamps and the measurement method | Compare to baseline and record decoder/presentation FPS, stalls and resource use; display refresh Hz alone is not stream FPS |

Do not demand late-session advertisement from an already running stock session.
Registration occurs before discovery. Focus-off notifications do not themselves
prove that the phone stopped encoding; record that behavior separately if the
approved diagnostics can observe it.

If trustworthy per-sink FPS/ownership measurements are unavailable, mark those
rows NOT RUN and identify the missing diagnostic. Do not infer them from a smooth
animation, generic UI refresh rate or the first-frame callback alone.

## Fault tests reserved for an approved Android lab harness

These are not instructions to disrupt the OEM service or physical instrument
cluster in the car. Use a controlled test harness and record which actual
Android paths executed; the existing JVM tests only simulate ownership events.

- Remove the Presentation or retire its controlled virtual display while output
  is live or an enable transaction is in flight. Confirm the adopted consumer is
  not released until its view owner and all borrows settle
- Delay/drop a reply or terminal event. Confirm there is at most one remote
  output, at most two adopted consumers, no early release and no UI wait. The
  retirement watchdog may report failure; it must not make room by freeing an
  uncertain consumer
- Deliver stale/duplicate terminal events and change the current output. Only
  the exact authenticated Binding/request may settle its own record
- Exercise client.close and Service death. Old release identity must survive
  close; death without terminal proof remains quarantined, including downstream
  codec-service uncertainty
- Stop the decoder after its TextureView stops consuming frames. Verify clean
  codec release and callback-thread termination before RELEASED. A native hang
  must remain isolated on its worker with bounded quarantine and responsive UI

## Decision and evidence

A test fails if MAIN/audio/TBT regresses, the phone reconnects during an ordinary
cluster toggle, stale data becomes live, any consumer is freed early, retention
exceeds the bound, or the UI waits on native stop. Stop further stress in the car
and use the operator's already approved recovery process; do not improvise a
service force-stop, new grant or loading workaround.

Attach only necessary sanitized diagnostics to HAV-24: commit/artifact hashes,
versions, case result, timestamps, per-sink measurements and relevant lifecycle
state/error lines. Avoid credentials, AAP authentication material, personal route
coordinates, VIN and unrelated logs. Record skipped/blocked cases explicitly.

Source/CI success plus an unsigned APK is not the final acceptance condition.
The authorized runtime results, compatible loading record and agreed performance
comparison are required before considering a vehicle candidate or promotion.
