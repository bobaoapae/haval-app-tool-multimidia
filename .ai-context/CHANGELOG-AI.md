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
