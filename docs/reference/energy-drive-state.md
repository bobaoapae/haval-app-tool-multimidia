# `car.ev_info.energy_drive_state` — powertrain flow states

Decoded 2026-08-24 from the OEM app on the car:
`/system/priv-app/BeanEnergyAssistant-app-Release/BeanEnergyAssistant-app-Release.apk`
(`com.beantechs.energyassistant`), resource keys `flow_name_value_<N>` in
`res/values/strings.xml`. This is the OEM's own table, not an observation.

The signal is the single source that decomposes into **which motor is running** and **which way
energy is flowing**, per axle. That is what makes a compact multi-state icon possible.

## Axle naming

The OEM uses powertrain position codes:

- **P2** — the motor mounted on the engine/transmission, i.e. the **front** axle.
- **P4** — the **rear**-axle motor (AWD variants only).

`flow_name_value_38` / `_39` say it in plain words: "Front wheel energy recovery" / "Rear wheel
energy recovery".

## Value table

| N | OEM label (EN) | ICE | front / P2 | rear / P4 |
|---|---|---|---|---|
| 0, 10 | Vehicle stationary | off | – | – |
| 11 | Vehicle stationary (engine is running) | **on** | – | – |
| 1 | Driving charging | on | generating | – |
| 3 | Only driven by engine | on | – | – |
| 6 | Energy recovery | – | generating | – |
| 12 | Idling charging | on | generating | – |
| 13 | Energy recovery + driving charging | on | generating | – |
| 14 | Pure electric driving | off | driving | – |
| 15 | Series driven (battery discharge) | on | driving | – |
| 16 | Series driven | on | driving | – |
| 17 | Series driven (battery charging) | on | driving | – |
| 18 | External charging and heating of traction battery | – | – | – |
| 20 | Engine and motor drive at the same time | on | driving | – |
| 21 | Idling speed | on | – | – |
| 22 | Only driven by motor | off | driving | – |
| 23 | External charging | – | – | – |
| 24 | External heating | – | – | – |
| 30 | Driving charging of P2 & P4 | on | generating | generating |
| 31 | Engine & P4 drive, P2 generates | on | generating | driving |
| 32 | Driving charging of P2 | on | generating | – |
| 33 | Driving charging of P4 | on | – | generating |
| 34 | Driven by engine & P4 | on | – | driving |
| 35 | Driven by engine & P2 & P4 | on | driving | driving |
| 36 | Driven by engine & P2 | on | driving | – |
| 37 | Driven by P4, power generation by engine & P2 | on | generating | driving |
| 38 | Front wheel energy recovery | – | generating | – |
| 39 | Rear wheel energy recovery | – | – | generating |
| 40 | Driven by P2 & P4 | off | driving | driving |
| 41 | Driven by P4 | off | – | driving |
| 42 | Driven by P2 | off | driving | – |
| 43 | Engine direct drive (battery charging) | on | generating | – |
| 44 | Engine direct drive (battery discharging) | on | – | – |
| 45 | External charging (slow) | – | – | – |
| 46 | Fast charging | – | – | – |
| 73 | Discharging | – | – | – |
| — | Unknown state (`flow_name_value_none`) | – | – | – |

States **30–44** are the AWD set. On a front-drive HEV they should never appear; confirm per vehicle
with a drive capture rather than assuming.

### `_re300` is a variant override, not a value

`flow_name_value_3_re300`, `_18_re300`, `_24_re300` are model-specific label overrides (RE300 is one
of several platforms, each with its own `*FlowView`). Raw state **3 is "only driven by engine"** — do
not treat `3_re300` as a distinct raw value.

## Invalid-value sentinels

The car signals "no data" with out-of-range values, and they arrive interleaved with real ones.
Verified live on a parked car, 2026-08-24: `car.ev_info.motor_speed` alternates between `0` and
`-99999` seconds apart.

| Key | Sentinel |
|---|---|
| `car.ev_info.motor_speed`, `car.ev_info.rear_motor_speed` | `-99999` |
| `car.ev_info.motor_power` | `-1001` |
| `soc_of_battery`, `fuel_consume_info`, `power_battery_current`, `remain_odometer`, … | `-1` / `-1.0` |

**Any consumer must drop sentinels and hold the last valid value**, or the UI will flicker.

Because `-99999` also shows up on the *front* motor, a `-99999` on `rear_motor_speed` is **not**
evidence that the vehicle has no rear motor. Attribute axles from `energy_drive_state`, never from
the motor speeds.

## Related keys

| Key | Notes |
|---|---|
| `car.configure.ev_drive_architecture` | Topology selector (`6` on this car). Picks which OEM `*FlowView` renders — `EVFlowView`, `P2FlowView`, `P2P4FlowView`, `PSP4PHEVFlowView`, `Re300HEVFlowView`, `Re300PHEVFlowView`, `P0OilFlowView`, `P2FuelFlowView`. Static config, **not** a live 4x2/4x4 state. |
| `car.basic.engine_state` | `11` observed with `engine_speed = 0` and `driving_ready_state = 1`. Full map not yet decoded. |
| `car.ev_info.energy_output_percentage` | Signed global battery flow; negative = regen. Already reaches themes as `evPowerFactor`. |
| `car.ev_info.charging_state` | `0` when not plugged in. |
| `car.ipk_light.brake_energe_recycle` | Regen telltale lamp. |

## OEM visual vocabulary

The OEM drawables confirm the natural way to draw this — send/return × axle × colour:

```
flow_send_up_to_front_axis      flow_back_down_front_axis
flow_send_down_to_back_axis     flow_back_up_back_axis
channel_tod_to_front_wheel{,_blue,_green}
channel_four_wheel_drive_fuel_flow{,_blue,_green}
channel_motor_to_battery_fuel_flow{,_blue,_green}
```

## Reading these values live

`com.havalh6.viewer` logs every push it receives:

```
W/H6Viewer: CarSignal com.haval.vehicle.EVENT_CHANGED key=<key> value=<value>
```

That is the fastest probe available without rebuilding the app. Force a full snapshot first, since
keys are only logged when they change:

```bash
adb shell am broadcast -a br.com.redesurftank.havalshisuku.ACTION_DISPATCH_ALL_DATAS
```

then `adb logcat -d -s H6Viewer`. `scripts/Capture-Energy-Flow.ps1` wraps both — `-Snapshot` for a
one-shot dump, no flag to record a drive to CSV.

## Reaching these keys from a cluster theme

Registering a key in `ServiceManager.DEFAULT_KEYS` is **not** enough for a theme. Themes read through
`ThemeBridgeImpl`, which rejects any key absent from `getAvailableKeys()` ("Blocked subscription to
undeclared theme key"). Exposing a new signal to a theme needs both:

1. the key monitored by `ServiceManager` (`DEFAULT_KEYS`, or dynamically via `ensureKeysMonitored`);
2. the key listed in `ThemeBridgeImpl.getAvailableKeys()`.

The viewer path is different and unfiltered — `TelemetryPublisher` broadcasts everything
`ServiceManager` sees, which is why the viewer already receives `energy_drive_state` while themes
cannot.
