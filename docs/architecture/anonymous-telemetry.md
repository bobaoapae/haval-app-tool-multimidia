# Anonymous fleet telemetry (PostHog EU)

Updated: 2026-09-24

## Purpose

Opt-outable, anonymous power-on pings for fleet mix, feature adoption and geographic distribution. No raw VIN, no GPS, no installed-app inventory.

## Backend

- PostHog Cloud EU (`BuildConfig.POSTHOG_HOST`, default `https://eu.i.posthog.com`)
- Disabled when `POSTHOG_API_KEY` is blank
- Event: `impulse_fleet_ping`
- GeoIP (country / city / region / approximate lat-lon) is added **server-side** from the request IP. The app sends no location itself.

## Identity: why not the VIN

`vehicle_uuid` is `SHA-256(ANDROID_ID|salt)`. It was previously `SHA-256(VIN|salt)`, which was **reversible**:

- the salt ships inside the APK by construction (the hash is computed on the car), and
- a VIN is highly structured — positions 1–8 were published as `vin_prefix`, position 9 is a derived check digit, leaving model year, plant and a 6-digit serial ≈ 5×10^7 candidates, brute-forceable in under a second on a GPU.

Changing the salt value only rotates the ids; it does not make them irreversible. Hashing only conceals an input that was unguessable to begin with, and a VIN is not.

`ANDROID_ID` instead is random, scoped to the app signing key, and survives uninstall / reinstall / data clear — resetting only on factory reset. That gives a stable per-head-unit identity with nothing to enumerate, so geography and vehicle data can safely ride on the same event.

Consequences to know:
- Re-signing the APK yields a new id (a debug build and a release build count separately). Acceptable: the two never appear active at the same time, so recent-activity views stay correct.
- A factory reset or head-unit reflash starts a new identity.
- The salt still applies, guarding against ROMs that hand every app the same `ANDROID_ID`.
- `BROKEN_ANDROID_ID` (`9774d56d682e549c`) and a missing value fall back to a locally generated UUID in prefs. That fallback loses reinstall stability — it is a last resort, and it logs a warning.

`vin_prefix` was **removed**: it identified model, not car, and that information is already carried by `configure_code`, `project_name`, `vehicle_model1`, `trim_level` and `engine_type`. The collector no longer reads the VIN at all, which also removes the old `skipped: VIN unavailable` guard.

## Gradle / secrets

Set in `gradle.properties` or CI `-P` flags (not committed secrets):

```
posthogApiKey=phc_...
posthogHost=https://eu.i.posthog.com
telemetryVinSalt=impulse-fleet-v1
```

`telemetryVinSalt` keeps its name for CI compatibility; it now salts the device id, not a VIN.

## Payload

- `distinct_id` / `vehicle_uuid`: SHA-256 hex of `ANDROID_ID|salt`
- models + getprops (`configure.code`, trim, mode1, engine, project.name)
- `odometer_km_bucket`: floor to 1000 km
- `theme`, `theme_changed`, `power_on_count`
- curated boolean settings allowlist
- `$process_person_profile=false`

Counting distinct cars per city:

```sql
SELECT properties.$geoip_country_name, properties.$geoip_city_name, uniq(distinct_id)
FROM events WHERE event = 'impulse_fleet_ping' GROUP BY 1, 2
```

## Residual privacy note

The event remains a high-dimensional fingerprint — city plus trim, engine, odometer bucket, theme and 13 booleans (8192 combinations on their own). In a city with few installs that combination is likely unique, and the stable id accumulates it over time. It is not linkable to a person without an external dataset, and with the VIN hash gone there is no longer one to join against — but it is still personal data under GDPR/LGPD. Dropping the settings allowlist would remove most of the entropy, at the cost of feature-adoption data.

## Cadence

One ping **3 minutes after** `ServiceManager` init in `ForegroundService` (`postDelayed` 180s), with an additional 5-minute debounce between successful sends.

`power_on_count` increments **only on a successful send**, so it counts deliveries, not ignitions — any service restart bumps it, and a failed send does not.

## UI

Informações → single row “Permitir coletar dados anônimos” + (i) + toggle. Default ON when backend is configured; opt-out via `ANONYMOUS_TELEMETRY_OPTED_OUT`.

If `POSTHOG_API_KEY` is blank (`AnonymousTelemetryCollector.isConfigured() == false`): switch is **disabled/grayed** and the (i) dialog states collection is **fully disabled** (nothing is sent).

Note: opt-out stops future pings only. Remote erasure in PostHog is not implemented (project token cannot delete events).

## Logging

The car runs `persist.log.tag=WARN`, which drops DEBUG **and INFO**. `ping ok` is `Log.i` and every skip reason is `Log.d`, so in normal operation only failures are visible. To diagnose: `adb shell setprop log.tag.AnonTelemetry VERBOSE` (non-persistent; clears on reboot).

## Code

- `diagnostics/AnonymousTelemetryPayload.kt`
- `diagnostics/AnonymousTelemetryCollector.kt`
- Hook: `ForegroundService` post-init `backgroundHandler.postDelayed` (**3 min**)

## Settings capture

Curated boolean prefs are read from `haval_prefs` **at send time** (after the 3 min delay), not snapshotted at boot start. Theme, odometer, getprops and the device id use the same moment.
