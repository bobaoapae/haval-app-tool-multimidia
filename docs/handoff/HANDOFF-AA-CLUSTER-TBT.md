# Handoff — Android Auto cluster map + TBT events (Impulse)

**Status:** host + Minimalist **1.0.6** implemented 2026-09-11. Theme Lab `n` shows the TBT strip. CLUSTER video **unproven** on the car (`registerInner` still fail-closed). Do not implement the 3D viewer card here.

**Requester intent:** keep full Android Auto on display 0, put a second official Maps stream on display 3, notify themes and the 3D viewer when the AA session starts/stops, publish turn-by-turn as telemetry, and let the Minimalist theme request that D3 map and show TBT under the menu.

This is **not** a pixel mirror of D0 and **not** a TBT-reconstructed map. D3 shows the phone’s CLUSTER video of the same trip. TBT is a separate structured overlay.

---

## Agent prompt (paste into a new Impulse session)

You are working in `haval-app-tool-multimidia` (Impulse). Implement the plan in this file, in order. Measure on the car before treating CLUSTER/TBT as done. Do not use Frida. Do not force-stop `com.ts.androidauto.projectionservice` without an AOA re-handshake path (DCM transaction 30 or reboot) — that drops USB accessory mode.

Read this whole file before editing. After each milestone, say what was verified and what is still unproven.

---

## Prior art (do not copy Frida)

Public AutoPanel APK `leonardovin/shizuku-bottom-bar-releases` v1.10.3 already did the CLUSTER stream on this head unit. Mechanism:

- Frida agent in `com.ts.androidauto` wraps `GalIntegration.registerCarService()`, then registers:
  - VideoSink service id **21**, `setDisplayIdAndType(1, CLUSTER)`, 1280×720 @ 160 dpi
  - InputSource service id **22** with D-pad keys 19–23 (mandatory; phone drops the link without it)
- Phone opens a **second** H.264 stream. MAIN `AapActivity` on D0 is untouched.
- Frames copied over unix socket `@aa-cluster-video` into the app. Measured **~11.5 fps** because Frida copies on the GAL reader thread. Do not repeat that.
- Late `nativeRegister` on a live session is accepted by the HU and **ignored by the phone**. Cluster must be advertised **before** the AAP session starts (or the session must be re-handshaked).
- Bind-mounts a patched `AndroidAutoService.apk` over `/vendor/app/AndroidAutoService/` (same mount pattern as `AndroidAutoPatchManager`).
- `UiConfig.margins` selects Google’s cluster layout (wide top card + bottom ETA pill). Content insets only slide the compact card. Impulse’s overlay will own TBT, so crop/UiConfig the video toward map-forward and do **not** rely on Google’s ETA chip.
- `am force-stop` on projectionservice drops AOA; AutoPanel’s mount script therefore does **not** restart AA.

Impulse already has:

- App-APK patches v1–v2.5 (`scripts/aa-patches/`, `AndroidAutoPatchManager`). Service CLUSTER advertise is **v2.6**: dumps compared in `DUMPS_V2_6_CLUSTER.md`, helper script `patch_android_auto_service_cluster.py`. `registerInner` constructors are still a fail-closed stub until apktool decode of stock `48ff`. Do not mix with the old “lie MAIN is CLUSTER” notes in `TODO_v2_5_display_type_cluster.md`. Do not bundle a rebuilt Service APK until on-car fps is measured.
- `LinkCommand` bind + link status 3/7/8 (`ACTIVATED` / `SHOW_VIDEO` / `AAP_FRX`) in `DisplayAppLauncher` / `AndroidAutoNowPlayingMonitor`.
- Theme telemetry via `ThemeBridgeImpl` + `control(key, value)` / `subscribe`.
- External apps via `ServiceManager.dispatchTelemetryOnly` → `com.haval.vehicle.EVENT_CHANGED` `{key,value}` and `REQUEST_SNAPSHOT`.
- Stub key **`app.navigation.directions`** already in `getAvailableKeys()` / `VirtualTelemetryManager` (returns `"{}"`). Theme-lab mock: `{"street","distance","turn"}`.
- Minimalist already treats `projectionMirrorInDash || carPlayInDash` as map-on-cluster and switches **mask** via `navigationDisplayMode` (default Clean). It must **not** rewrite persisted `display` (`docs/architecture/themes-contract-v1.md`).
- D3 mask punch when a projection rect is live (`InstrumentProjector2` / `DisplayAppLauncher`).
- External commands: exported receivers + `ImpulseApiCallers` (allowlist includes `com.havalh6.viewer`). Do not dump a new command into `VehicleCommandReceiver`’s window/sunroof list; add a dedicated receiver.

---

## Non-goals

- Do not move `AapActivity` to D3 for this feature. Existing “send AA to cluster” stays a separate path. Rich cluster keeps MAIN on D0.
- Do not rewrite theme `display` (Normal / Esportivo / …). Navigation look = `projectionMirrorInDash` / new cluster flag + `navigationDisplayMode`.
- Do not put trip ETA on the Minimalist overlay. Overlay = next manoeuvre + street + distance-to-turn. Remaining/ETA may still ride in the JSON for the 3D viewer.
- Do not ship Frida, unix-socket frame copies, or `screencap` mirroring.
- Do not treat a single unmeasured run as proof. Settle, then measure; CLUSTER fps and TBT freshness need on-car evidence.

---

## Milestone 1.1 — Patch AA Service: CLUSTER video + NAV channel

**What:** Service-APK (v2.6) so stock Autolink advertises a CLUSTER display and exposes NavigationStatus.

**Files (expected):**

- `scripts/aa-patches/` — new Service patch script (do not overload App-only `patch_logic.py` without a clear split)
- `scripts/aa-patches/TODO_v2_5_display_type_cluster.md` — update status
- `app/src/main/assets/aa_patches/AndroidAutoService.apk` — bundled patched service
- `AndroidAutoPatchManager.kt` — mount Service APK (it already has the path; today Service is optional)
- New Impulse-side `AaClusterVideoHost` (name as you like): own a D3 `Surface` / `SurfaceView` under the theme WebView, pass it into the patched service (AIDL or existing LinkCommand if a free transaction exists). Decode **inside AA**, not in Impulse, if the OEM `VideoSink` will take a Surface. If it will not, MediaCodec in Impulse from a BufferQueue — still no Frida.

**Why this shape:** AutoPanel proved the GAL APIs. Impulse should compile the advertise into the Service dex and render to a Surface Impulse owns, so MAIN stays at full rate and CLUSTER is not copied through JS.

**Implementation notes:**

1. Pull current `/vendor/app/AndroidAutoService/AndroidAutoService.apk` from the car. Do not assume the stock dump in-tree is that build.
2. Locate `GalIntegration.registerCarService`, `VideoSink`, `InputSource`, `Protos$DisplayType`, `Protos$UiConfig`, and NavigationStatus / NextTurn protocol classes.
3. After OEM services (ids 1,2,3,7,8,9,10,12,16,17–20) register CLUSTER video on a **free** id (21) and input on 22. Grant cluster `VIDEO_FOCUS_PROJECTED` unsolicited — the phone will not ask.
4. Advertise only when cluster-output is enabled (preference or last 1.4 command). If AA is already in session, do **not** silently `nativeRegister`; schedule a documented re-handshake (see AutoPanel). Prefer: apply on next session; optional explicit reconnect later.
5. Hook AAP navigation messages in the same process. Map turn enums to a stable JSON (keep the theme-lab `turn` strings like `TURN_RIGHT` plus numeric `turn_id` if known).
6. Never `force-stop` projectionservice from the mount path. Mount + empty oat dir + dalvik wipe; load on next AA start. If a live remount is required, recover AOA (DCM 30) and say so in the log.
7. UiConfig: start from AutoPanel’s measured `margins=0,0,0,0;content=288,30,288,30` only as a baseline, then crop toward map-only. Theme will draw TBT; double ETA is a bug.

**Done when:**

- D0 still shows full AA (rail, ETA, the lot).
- With cluster enabled, a second H.264/map is visible on a Surface Impulse controls (even a debug `SurfaceView` on D3 is enough).
- `adb logcat` shows CLUSTER codec setup **without** Frida.
- Two settled runs: CLUSTER fps and that MAIN fps did not collapse.
- Unmount restores stock Service on next boot.

---

## Milestone 1.2 — Session notification (theme + external)

**What:** Publish AA session running/stopped on the existing telemetry bus.

**Key:** `app.androidauto.session`  
Values: `stopped` | `active`  
Optional later: `connecting`. Do not invent more states until measured.

**Source of truth:** `LinkCommand` GET_LINK_STATUS (already: 3, 7, 8 = linked). Drive this from a single monitor; do not poll from the theme.

**Dispatch:** `ServiceManager.dispatchTelemetryOnly` so:

- themes that `subscribe(['app.androidauto.session'])` get `control(...)`
- 3D viewer gets `com.haval.vehicle.EVENT_CHANGED`
- `REQUEST_SNAPSHOT` includes the key

**Also:** add the key to `ThemeBridgeImpl.getAvailableKeys()`. Additive v1.0.

**Throttle:** session is a rare edge. No timer. On link-status change only, with a small debounce if the OEM flaps 7↔8 during video start.

**Done when:** plug/unplug (or wireless connect/disconnect) flips the key; snapshot after boot reports the live value; Minimalist can subscribe without a host crash.

---

## Milestone 1.3 — TBT on `app.navigation.directions`

**What:** Fill the existing stub from the NAV channel in 1.1.

**JSON (additive; keep theme-lab fields):**

```json
{
  "active": true,
  "street": "Av. Paulista",
  "distance": "200 m",
  "distance_m": 200,
  "turn": "TURN_RIGHT",
  "turn_id": 103,
  "next_street": "",
  "next_distance_m": null,
  "next_turn": null,
  "remaining_m": 12300,
  "remaining_s": 840
}
```

When guidance is off: `{"active":false}` (or `{}` as today). Do not leave a stale street.

**Hot path:** `distance_m` will change often while driving. Same rule as speed in the viewer: do **not** rebuild the theme at bus rate.

- Publish immediately on `turn` / `street` / `active` change.
- Throttle `distance_m` / `remaining_*` to ~1 Hz, with a trailing commit so a stop still lands.

**Done when:** guiding in Maps on AA updates JSON on the bus; stopping guidance clears `active`; parked-car bench is not treated as proof (bus is quiet parked).

---

## Milestone 1.4 — Command: show CLUSTER map on D3

**What:** Theme or `com.havalh6.viewer` can enable/disable the second display on D3.

**Theme:** `window.Android` method, e.g. `setAaClusterMapEnabled(boolean)`. Do **not** persist across disconnect: `session=stopped` tears the Surface down and the next connect starts over; Minimalist asks again when it sees `active`.

**External:** new exported receiver, `ImpulseApiCallers.verify`, **not** `ACTION_VEHICLE_COMMAND`.

Suggested:

- action `br.com.redesurftank.havalshisuku.ACTION_AA_CLUSTER`
- extras: `enabled` (`true`/`false`), plus `caller` PendingIntent

**Host behaviour when enabled:**

1. Preference on.
2. If no AA session yet: advertise CLUSTER on next session (1.1).
3. If session already running and CLUSTER was not advertised: do **not** pretend; either wait for next session or run the documented re-handshake. Log which path.
4. Attach CLUSTER Surface on D3 **under** the theme WebView. Punch the native-mask hole. Set `projectionMirrorInDash` (or a dedicated `aaClusterInDash` that Minimalist treats the same) so existing map CSS/masks apply.
5. Do **not** `am display move-stack` of `AapActivity` to D3.

When disabled: tear down the D3 Surface, clear the projection flag, stop punching the hole. Leave MAIN on D0. Optionally keep advertising CLUSTER (phone still encodes) vs drop on next session — prefer drop on next session to save the extra encoder; document it.

**Done when:** theme or viewer broadcast shows/hides the D3 map while D0 AA stays; disable restores the previous cluster theme without a stuck hole; command from a non-allowlisted package is rejected.

---

## Milestone 1.5 — Minimalist: on session, request map + TBT under menu

**What:** `cluster-widgets/source/v1.0/minimalist/` only (then `npm run build` + commit `Themes/v1.0/minimalist/` + bump `theme.xml` `<version>`).

**Behaviour:**

1. Subscribe `app.androidauto.session` and `app.navigation.directions`.
2. On `active`: call `setAaClusterMapEnabled(true)`. On `stopped`: disable. Not sticky — a parked phone must not keep a black hole.
3. Do **not** write `display`. Rely on 1.4’s projection flag so `getEffectiveMaskMode()` uses `navigationDisplayMode` (already defaults to Clean).
4. When `directions.active` and projection is on, render TBT in the top-centre column **below the menu**. Strip: turn glyph + next-turn distance under the icon; street; then remaining distance | remaining time | arrival clock on one row (only arrival in menu lime). Hide the strip when `active` is false.
5. Keep clock/gear readable; TBT must not cover the OEM READY / speed-limit / ESP exclusion zones (`THEME_GUIDE.md`).

**Done when:** OTA package built; Theme Lab mock of `app.navigation.directions` shows the strip; on-car, AA navigate → D3 map + strip, disconnect → strip gone and map released.

---

## Suggested order and parallelism

| Step | Depends on | Parallel? |
|---|---|---|
| 1.1 Service patch + Surface host | — | Start first |
| 1.2 Session key | LinkCommand only (can ship before CLUSTER video works) | Parallel with 1.1 |
| 1.3 TBT JSON | 1.1 NAV hook | After 1.1 NAV exists; mock JSON can land earlier for 1.5 UI |
| 1.4 D3 command | 1.1 Surface | After a debug Surface exists |
| 1.5 Minimalist | 1.2 + 1.3 mock + 1.4 API | UI against mocks in parallel; wire live keys last |

Most efficient: **1.2 + Minimalist TBT mock UI in parallel with 1.1**, then 1.3/1.4, then point Minimalist at live keys.

---

## Verification (car)

Ask before any test that needs a human at the car.

1. AA on D0 only, cluster command off — unchanged MAIN.
2. Enable cluster with no session — next AA connect shows D3 map.
3. Enable cluster mid-session — either wait/re-handshake as documented, or fail closed (no black D3).
4. Maps guiding — TBT JSON + Minimalist strip; distance throttled; turn change immediate.
5. Guidance off / AA disconnect — `session=stopped`, directions cleared, D3 released.
6. `am broadcast -a com.haval.vehicle.REQUEST_SNAPSHOT` includes the new keys.
7. Viewer-shaped command with `ImpulseApiCallers` token enables D3; a random package does not.
8. Do not `force-stop` projectionservice during the drive.

---

## Out of scope here (3D viewer, later)

Once this ships, a **separate** session in `haval-h6-3d` will:

- Listen to `EVENT_CHANGED` for `app.androidauto.session` / `app.navigation.directions`
- Render TBT on the existing native `navigation` card (`QuickCardGraphicView` needs a real `case`, not the generic ring)

Do not implement that in Impulse.
