# Anonymous fleet telemetry (PostHog EU)

Updated: 2026-09-16

## Purpose

Opt-outable, anonymous power-on pings for fleet mix and feature adoption. No raw VIN, GPS, IP, or installed-app inventory.

## Backend

- PostHog Cloud EU (`BuildConfig.POSTHOG_HOST`, default `https://eu.i.posthog.com`)
- Disabled when `POSTHOG_API_KEY` is blank
- Event: `impulse_fleet_ping`
- GeoIP (country/city) is server-side from request IP; IP is not stored by the app

## Gradle / secrets

Set in `gradle.properties` or CI `-P` flags (not committed secrets):

```
posthogApiKey=phc_...
posthogHost=https://eu.i.posthog.com
telemetryVinSalt=impulse-fleet-v1
```

## Payload (high level)

- `distinct_id` / `vehicle_uuid`: SHA-256 hex of `VIN|salt`
- `vin_prefix`: first 8 VIN chars
- models + getprops (`configure.code`, trim, mode1, engine, project.name)
- `odometer_km_bucket`: floor to 1000 km
- `theme`, `theme_changed`, `power_on_count`
- curated boolean settings allowlist
- `$process_person_profile=false`

## Cadence

One ping **3 minutes after** `ServiceManager` init in `ForegroundService` (`postDelayed` 180s), with an additional 5-minute debounce between successful sends.

## UI

Informações → “Dados anônimos de uso” (Participar switch + info dialog). Default ON; opt-out via `ANONYMOUS_TELEMETRY_OPTED_OUT`.

## Code

- `diagnostics/AnonymousTelemetryPayload.kt`
- `diagnostics/AnonymousTelemetryCollector.kt`
- Hook: `ForegroundService` post-init `backgroundHandler.postDelayed` (**3 min**)

## Settings capture

Curated boolean prefs are read from `haval_prefs` **at send time** (after the 3 min delay), not snapshotted at boot start. Theme, odometer, getprops, and VIN hash use the same moment.
