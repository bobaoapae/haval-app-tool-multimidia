# HAV-24 PR152 consolidation — 2026-10-11

## Current inputs and delivery

PR152 head before consolidation: `5c0913a50265291a55cf103efa84ca918ef061c6`.
PR7 source: `6eb3ee0c2e6db830dae36429801c423201b7775c`.
Live preview: `681bf61f052f746ccd792d5186d88a9e20a28cfc`, already an ancestor of PR7.
The publication uses a two-parent consolidation commit and a non-force update
of PR152's existing head branch. It does not merge either pull request.

The source task specifically adopts v9's additional Service signer and its
bundled asset/default-off Service loading path for review and tests. It does
not install, activate, sign, deploy or operate a vehicle. Keep HAV-24 In Progress.

Four PR7-only Home grant regions are omitted; the existing live-preview Home
installer, utilities and setup behavior stay unchanged. Independent theme
preview work is outside this consolidation. Historical PR152 reports and
adapted host-boundary tests are preserved rather than discarded.

## Validation

227 portable tests passed with zero skips. Kotlin synthetic adapters passed
58 geometry/refresh and 37 controller checks. Actual Java virtual-host
lifecycle passed 15 checks. Real Android28 helper/client compile and fresh
full disabled unsigned package preparation/reopen verification passed.
Source/asset boundaries were reviewed independently. Exact-head remote CI must
be checked after publication; local checks are not a full Android app build.

[Scope, commands, adoption and limits](../scripts/aa-patches/integration/CONSOLIDATION-PR152-20261011.md).
The adopted recovery/rearm and gradient limitations remain. Installed identity,
rendered UI and physical MAIN/audio/TBT/reconnection/performance validation are
separate gates. No vehicle acceptance is claimed.
