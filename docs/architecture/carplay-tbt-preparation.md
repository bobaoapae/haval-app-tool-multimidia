# CarPlay TBT: isolated preparation, not a runtime feature

Date: 2026-10-21. Base: `netseek/haval-app-tool-multimidia`,
`feature/new-screen-enhancements-v9` at `34347c29daf81ac9fc76cc7e509c68936ec54737`.
Related work: [HAV-24](https://linear.app/haval-impulse/issue/HAV-24/navigation).

## What this change does

Two pure Kotlin files under `navigation/` prepare a lossless raw model, conservative
normalization and a source arbiter. Nothing instantiates these classes in production.
This is deliberately **not** milestone 1 completion or proof of working CarPlay TBT.

- `CarPlayGuidance.kt`: 11 fields in the observed wire order, nullable strings, decoded
  state/unit enums while retaining the raw values, and a no-assumptions normalizer.
- `PreparedNavigationArbiter.kt`: session identity/generation, AA-over-CP selection,
  owner-only cleanup, immediate identity changes, fixed-deadline trailing throttle,
  reset and a last-published snapshot.
- `PreparedDirections` is an internal preparation value, **not a new JSON schema**.
  Its lane placeholder is synthetic, not Google's existing lane-direction contract.
  No adapter to the existing AA model is included or claimed lossless.

The existing `AndroidAutoClusterController` remains the only directions publisher;
`VirtualTelemetryManager` still reads its snapshot and HUD calls are unchanged.
No `bindCarPlay`/guidance monitor, Binder transaction, Parcel parser, registration,
telemetry write, theme/bridge change, display operation, permission, OEM patch,
mount, process restart, network connection or deployment is introduced.

## Static evidence and provenance

The supplied original `TsCarPlayService.apk` has SHA-256
`03cc69d166ced0cf0b38e47ef08d0a6cd7b27ab57d6954bd87a23da436c911c7`.
The supplied original `TsCarPlayApp.apk` has SHA-256
`ed0bf9568bf594cba5d708b3ea8782af0c5bbd7e5a11f00b36d08a0677a60855`.
They were read statically; this is **not** a fresh pull or identification of the build
currently installed in a vehicle. No supplied APK/library is added by this change.

Observed TS service component: `com.ts.carplay/com.ts.carplay.CarPlayService`.
Action: `com.ts.carplay.action.CarPlayService`.
Service descriptor: `com.ts.carplay.common.aidl.ICarPlayService`.
Callback descriptor: `com.ts.carplay.common.aidl.IRouteGuidanceUpdateCallback`.

| Operation | Transaction | Flags / reply |
| --- | ---: | --- |
| startRouteGuidanceUpdates | 7 | oneway (1), null reply |
| stopRouteGuidanceUpdates | 8 | oneway (1), null reply |
| onRouteGuidanceUpdate | 1 | oneway (1), null reply |

Subscription data is an interface token followed by the same strong callback Binder.
Callback data is its interface token, a Parcelable presence integer, then these fields
of `com.ts.carplay.common.data.iap.RouteGuidanceInfo` in order:

| Position | Field | Type |
| --- | --- | --- |
| 1 | mManeuverType | int |
| 2 | mDistanceBetweenManeuverDisplayStr | nullable String |
| 3 | mDistanceBetweenManeuverDisplayUnits | int |
| 4 | mAfterManeuverRoadName | nullable String |
| 5 | mDistanceRemainingDisplayStr | nullable String |
| 6 | mDistanceRemainingDisplayUnits | int |
| 7 | mTimeRemainingToDestination | long |
| 8 | mRouteGuidanceState | int |
| 9 | mJunctionType | int |
| 10 | mDrivingSide | int |
| 11 | mJunctionElementExitAngle | int |

The static survey cross-checked Stub transaction constants against Proxy transact calls
and Parcelable read order against write order. It found a callback registration and
cached-info delivery path, but did not establish live external bind/delivery.
Do not generate a partial AIDL and rely on its sequential transaction IDs: the OEM
service interface has 34 methods. No Parcel decoding or truncation handling is tested
by this PR; a null raw object fixture is not a real null Parcelable transaction test.

Alt-screen functions are exported by both `libAirPlay_ts.so` architectures, but the
examined `_getScreenDisplays` path in `libcarplaystack.so` called MainScreen functions.
This does **not** establish a second advertised panel, decoder/Surface or stream.
The separate `com.autolink.carplay` family is not interchangeable with this TS Binder.

## Conservative policy (not inferred maneuver semantics)

- State 0 NO_ROUTE and 2 ARRIVED are ineligible.
- State 1 ROUTE_SET, 5 REROUTING and 6 PROCEED_TO_ROUTE are eligible when linked.
- State 3 LOADING, 4 LOCATING, unknown states and null raw input fail closed. This
  explicit provisional policy needs captures before runtime integration.
- State 6 is not maneuver 6. It retains `turn = UNKNOWN`; no DEPART mapping is invented.
- Every maneuver value, including -1, unknown integers and 0..53, is preserved in
  `turnId` with `turn = UNKNOWN`. IDs are source-specific, not AA arrow identifiers.
- Unit codes are KM=0, MILES=1, M=2, YARDS=3 and FT=4. Strings are preserved exactly,
  including whitespace, null (raw only) and ambiguous localized values such as `1,234`.
  All numeric distance conversions remain null, even when a sample looks parseable.
- `nextStreet` uses the after-maneuver road; `street` remains empty because current road
  is absent. `remainingSeconds` is null because the long's unit is unverified; ETA is empty.
- Exit number, turn angle and clockwise direction remain null, and lanes remain empty.
  Angle 181 is the default sentinel; even an apparently valid raw angle is not published.
  No logging of private route data or new capture mechanism is introduced.

## Arbiter contract

`connect(source, nowMs)` is a future adapter's report of a confirmed linked session,
not an instruction to bind. It returns a new identity-bearing session token and any
necessary publication. Updates/disconnects from old sessions or other arbiter instances
are ignored. Rebinding invalidates previous data for that source; reset invalidates all
sessions without reusing generations.

`onUpdate` copies the provided lanes defensively. AA wins only with a current linked
session and active guidance; otherwise eligible CP wins. The latest suppressed CP update
is retained for immediate fallback. A non-owner's inactive/disconnect cannot clear the
owner. Ending/disconnecting the owner either selects the other eligible source directly
(no inactive flash) or produces a canonical empty inactive snapshot once.

Source, generation, active, navigation state, turn, street and other maneuver identity
changes bypass throttling. Distance/remaining/ETA updates coalesce with a trailing commit
at the previous publication + 1,000 ms. The deadline does not move with each new callback.
Returning to the already published value cancels the pending update. Ownership changes,
reset, route end and disconnection cancel obsolete trailing data. `snapshot()` exposes
only the last committed publication, not a throttled candidate.

The future adapter must serialize mutations **and consumption of returned publications**
on one executor, use a nonnegative monotonic elapsed clock, cancel/re-arm a single timer
from `nextFlushAtMs()`, and invoke `flush(nowMs)`. The core owns no timer, coroutine or
Android handler. Time is never taken from a route timestamp or wall clock. Tokens do not
solve out-of-order messages within the same live Binder session; capture/order policy is
still a runtime integration gate. No freshness timeout is inferred from synthetic data.

## Validation and remaining gates

See [handoff](../handoff/HANDOFF-CARPLAY-TBT-PREPARATION-2026-10-21.md) and
[host runner](../../scripts/carplay-tbt-tests/README.md) for commands and results.
The JVM fixtures are explicitly synthetic, including 6,000 seeded session events.
There are no real callback, device, Android Parcel, performance or rendering captures.

Before integration: implement a bounds-checked Android Parcel adapter, test null presence
and truncated input against Android, confirm installed hashes/live bind and session
lifecycle with consent, capture Apple Maps active/end/rerouting/proceed-to-route and
loading/locating, determine distance locale/time units/maneuver mappings/angle semantics,
and verify the sole publisher/HUD handoff while retaining all existing AA fields.
Preserve [the CarPlay regression contract](../carplay-cluster-regression-contract.md).
Neither these results nor a successful APK build authorizes vehicle operations or makes
the video milestone complete. No merge, release or deployment is part of this PR.
