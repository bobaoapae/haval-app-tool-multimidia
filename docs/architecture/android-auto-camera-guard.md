# Android Auto: delayed recovery across camera transitions

## Scope

The reported symptom is a brief display-3 flicker after the native camera closes,
followed by normal projection. Vehicle root cause remains **unconfirmed**. This change
addresses a separately identifiable asynchronous recovery race on `preview`.

`preserveAndroidAutoClusterContractAfterWindowChange` previously queued focus/recovery
at approximately 0.5, 1.7, 5.2 and 9.2 seconds after an ordinary window event. A later
camera event selected `VERIFY_ONLY` for its own job but did not invalidate previous
jobs. Those jobs could still broadcast focus or restart the AA visual package after
a transient stale Surface probe. Even `VERIFY_ONLY` could recreate a missing task.

## Change

- A synchronized, AA-only camera policy gives each automatic contract-guard sequence
  a generation token. Camera open/close transitions invalidate previous tokens.
- AVM telemetry is recorded before telemetry broadcasts/listeners, including startup
  snapshots, without sending any camera or vehicle command.
- A camera window also blocks recovery before telemetry arrives. Projection and
  passive SystemUI/MediaCenter/VehicleCenter events do not establish camera exit.
  Other window events can release window-only ownership, but never active AVM telemetry.
- The same token crosses all delayed retries, Surface probes, missing-task restores,
  and the restore's delayed post-start focus jobs. Invalidated work is a no-op.
- Checks are repeated after blocking reads and before recovery/focus. A visual recovery
  that has already issued force-stop finishes recreation, rather than leaving AA absent.
- Fresh post-camera work is not blocked by cooldown timestamps from an older generation.
- Explicit user handoffs, CarPlay, camera behavior, display geometry, bridge contracts,
  themes and native APK patches are unchanged.

No new polling or delay is introduced. Invalid jobs remain scheduled until their next
existing wake, then return without shell work. This is cooperative invalidation:
an already executing shell command cannot be undone atomically by a later camera event.

## Verification and physical follow-up

JVM tests exercise generation invalidation, camera close, repeated and interrupted
cycles, telemetry/window ordering, missing open telemetry, unknown signals and a full
camera open/close cycle during a blocking probe. Existing guard tests cover package
classification and unaffected focus/cooldown behavior. They do not execute OEM Binder,
SurfaceFlinger, Android Activity lifecycle, or graphics on a vehicle.

Physical validation remains required, with the vehicle safely stationary:

1. Record Impulse version, head-unit firmware, AA wired/wireless, phone/AA version,
   selected theme/display and installed AA patch status.
2. Start AA on display 3 and record the existing camera-close symptom.
3. Repeat manual camera open/close, including rapid repeated cycles and camera activation
   shortly after opening a normal display-0 app. Include the normal reverse-camera
   scenario only under the vehicle operator's own safe procedure.
4. Capture synchronized video of D0/D3 and diagnostic logs around the transition.
   Look for `AA_CAMERA_GUARD`, `WINDOW_CHANGE_*_AA_CONTRACT_*`,
   `STALE_SURFACE_GUARD`, `VISUAL_RESTART`, and OEM Surface/decoder lifecycle events.
5. Confirm pre-camera retries do not send focus/restart after camera close; confirm
   new ordinary window guards and explicit D0/D3 handoffs still work.
6. Verify CarPlay separately remains unaffected. Do not infer native rendering success
   from a build, JVM test, Binder response or valid Surface dimensions alone.

If flicker remains without app-side recovery at the matching timestamp, investigate
native camera/video routing and compositor events before widening the patch. Do not
add decoder restarts or change native patches without that evidence.

## Cloud verification — 2026-10-01

- `:app:testDebugUnitTest`: 412 tests / 46 suites, no failures, errors or skips
- `:app:assembleDebug`: passed
- `:app:lintDebug`: passed, zero errors; 251 warnings and 22 hints across the project
  (no separate baseline lint comparison was run)
- APK v2 signature verification passed; this is a debug signing certificate, not the
  production release key. Same application ID means a differently signed installed
  release cannot be updated in place by this APK. Do not uninstall the production app
  merely to bypass that restriction; installation must follow an agreed test plan.
- No emulator/head-unit runtime validation, vehicle operation or deployment

This candidate remains pending physical validation. Reviewers can compile the branch
with their established signing setup for an in-place device test.
