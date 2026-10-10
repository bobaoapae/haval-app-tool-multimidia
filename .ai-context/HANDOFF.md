# HAV-24 bounded v9 integration — 2026-10-10

## Base and purpose

Target `netseek/haval-app-tool-multimidia:feature/new-screen-enhancements-v9`
at `2dad9adb36ad7793c101fa7df76147ae863b4469`. This is a separate v9-based
draft change; PR152 and PR6 retain their original heads and targets.

Port the three host fixes from `5c0913a50265291a55cf103efa84ca918ef061c6`
and offline packaging/status from `f54ea1dd534b9fec6ab7a21d00cac001ce4676d3`
onto the functional v9 implementation, without importing the v9 branch into
PR152 or changing the target's existing trust/loading/permission behavior.

## Implemented

- All custom/theme/default map bounds intersect the panel and native-card region
- AA video clip and AA-owned mask opening refresh together on the UI thread,
  including AA-only sessions, without a managed-app resize feedback loop
- The first inactive navigation update starts a fixed five-second deadline;
  repeated inactive updates do not extend it, and stale callbacks are fenced
- Offline package preparation validates current source/tool/profile evidence,
  uses a new staging/output directory and leaves the result unsigned and disabled
- App and Service file evidence are shown separately from unverified runtime
  readiness; uncertain reads and stale polling results do not become success
- Report/package schema 2 records the target's two existing allowed Service
  certificates. Old schema/singleton reports do not attest this v9 source tree
- CI watches the affected source/test paths and builds the debug host

Details and reproduction:
[runtime port](../scripts/aa-patches/integration/RUNTIME-V9-PORT-20261010.md),
[package/status port](../scripts/aa-patches/integration/PACKAGE-V9-PORT-20261010.md).

## Verification

- Combined portable suite: `python3 -m unittest discover -s scripts/aa-patches/tests -v`
  passed **222 tests**, no skips
- Runtime policies: 21,689 production-Java geometry assertions and 43,764
  production-Java navigation assertions passed
- Kotlin synthetic-adapter harnesses: 58 geometry/refresh checks and 37
  complete-controller checks passed, including preserved v9 dock/session calls
- Exact old v9 controller and geometry defect mutations fail the relevant
  regression checks; final fixed-source checks pass
- Independent focused review/tests found no blocker within this bounded port
- Full disabled unsigned package preparation and ZIP re-verification passed:
  Android28 helper/client compilation, assembly, four hooks, 121 original
  methods and 4,897 unchanged OEM classes. The unsigned Service SHA-256 is
  `05b51e577bc67e8bea78e311c19470ed8ae894e5d6871bd7d79d6ec438b740f1`;
  generated handoff is disabled and signed/deployment_ready/vehicle_validated
  are false. Generated OEM artifacts are local and are not committed
- Full Android/Compose build and Windows verification depend on the final
  exact-head CI; their result must be checked after publication
- Rendered Compose layout, real compositor/Binder/OEM behavior and vehicle
  operation have not been validated by these offline checks

## Preserved boundaries

Protocol/client certificate checks, Service APK, ServiceManager, existing
installation/mount/auto-mount/reload/rollback methods and the two automatic-mount
preference callbacks are unchanged from the target. The package evidence schema
describes the pre-existing target trust; it does not expand or approve trust.
Home installer/grant and CarPlay behavior are unchanged. v9's dock publication,
session polling, virtual-panel gates, stream dimensions and author shift/fades
are preserved. No theme/telemetry contract keys were added or changed.

## Still open

Keep HAV-24 In Progress and the PR in draft. The unchanged loading flow's
post-reload verification, rollback verification and one-shot rearm limitations
remain. So do installed identity proof and authorized physical validation of
MAIN/audio/TBT, navigation, reconnection, performance and warning composition.
The existing sibling-gradient/attribution concern remains outside this port.
No merge, release, deployment, signing, installation or vehicle action is part
of this work.
