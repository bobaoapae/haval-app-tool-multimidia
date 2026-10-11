# AI change log

## 2026-10-10 — HAV-24 bounded v9 port

Integrated the three geometry/mask/navigation fixes and offline package/file
status functionality into the exact v9 base `2dad9adb`, preserving its existing
trust, loading and permission behavior. Added target-specific regressions and
schema-2 provenance checks. Combined local suite passed 222 tests without skips;
additional Kotlin harnesses passed 58 geometry/refresh and 37 controller checks.
Full disabled unsigned assembly/package re-verification and Android28 helper/
client compilation passed; generated binaries remain local and uncommitted.
See [HANDOFF](HANDOFF.md) and the two v9 integration documents for scope, commands
and validation limits. Exact-head remote CI must be checked after publication.

## 2026-10-11 — PR152 source consolidation

Consolidated the exact PR7/v9 tree with PR152 history after specific source-code
review approval for the additional Service signer and optional auto-loading
path. Excluded the four new Home grant regions while preserving live preview
Home behavior. Restored historical integration documents and adapted retained
trust regressions. Local aggregate: 227 tests, zero skips; Kotlin 58/37 checks;
virtual-host lifecycle 15 checks; real Android28 compilation and full disabled
unsigned preparation/reopen verification passed. Remote CI remains to be
verified after publication. See the current HANDOFF and consolidation report.
