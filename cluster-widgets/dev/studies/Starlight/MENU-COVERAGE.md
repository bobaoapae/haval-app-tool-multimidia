# Apex GT menu coverage — Impulse Motion / model 3

This is an independent, local browser/Node preview. Menu behavior and content were inspected in the actual Apex GT source, not inferred from a picture. No vehicle connection, Android bridge, network request, storage write, repository publication or deployment is implemented by `menu-state.js`.

## Provenance

Inspected source:

- Repository: [bobaoapae/haval-app-tool-multimidia](https://github.com/bobaoapae/haval-app-tool-multimidia)
- Editable file: [cluster-widgets/source/v1.0/ApexGT/src/apex-menus.js](https://github.com/bobaoapae/haval-app-tool-multimidia/blob/preview/cluster-widgets/source/v1.0/ApexGT/src/apex-menus.js)
- Verified menu file Git blob: `4e17e03831c6f99d652b70000df953f6d4ba787f`
- Reference snapshot: `681bf61f052f746ccd792d5186d88a9e20a28cfc`
- Local read-only reference: `haval-theme-manual-preview/cluster-widgets/source/v1.0/ApexGT/src/apex-menus.js`
- Source styling: `src/apex-menus.css`, especially lines 1–24 (400×430 side panel, three sliding rows), 26–55 (tabs, info, graphs, trips), 57–71 (AC controls)
- Source host delegation: `src/apex-gt.js` forwards `onCardChanged` and `onKeyEvent` into ApexMenus; ApexMenus never owns host card changes

The local theme manifest is 1.0.14; the GitHub preview manifest inspected is 1.0.15. The menu file matches both. The later difference is page-wide cluster visibility, not different menu contents.

## Main menu: exact source contents

| Root item | Tabs / options | Data and behavior |
|---|---|---|
| INFORMAÇÕES | GERAL, PNEUS | General: odometer, inside/outside temperatures, fuel % and range, battery % and electric range. Tires: front left/right, rear left/right pressures in PSI |
| GRÁFICOS | POTÊNCIA, VELOCIDADE | Signed electrical power and speed, 18 historical samples, at most a new history slot every 250 ms |
| AJUSTES | CONDUÇÃO, PROPULSÃO, DIREÇÃO, REGENERAÇÃO, ESTABILIDADE, DISPLAY | Five simulated setting changes; original Apex-only display selector retained as an explicitly disabled row |
| TRIP A / TRIP B | TRIP A, TRIP B | Distance, elapsed driving time, average speed, average consumption. A: automatic-reset label. B: hold OK to reset simulated metrics |

References: `apex-menus.js` lines 6–68 (keys/labels/menu definitions), 127–152 (markup), 201–245 (rendering), 436–444 (history).

### Exact setting domains

| Setting | Source key | Selectable cycle | Additional readable values |
|---|---|---|---|
| CONDUÇÃO | `car.drive_setting.drive_mode` | 0 NORMAL → 2 ECO → 1 SPORT | 3 NEVE, 4 AREIA, 5 LAMA, 11 AWD can be displayed, but are not offered by the source cycle |
| PROPULSÃO | `car.ev_setting.power_model_config` | 0 HEV → 1 EVP → 3 EV | Legacy HEV labels normalize to HEV |
| DIREÇÃO | `car.drive_setting.steering_wheel_assist_mode` | 2 CONFORTO → 0 NORMAL → 1 ESPORTIVA | None |
| REGENERAÇÃO | `car.ev_setting.energy_recovery_level` | 2 BAIXO → 0 NORMAL → 1 ALTO | None |
| ESTABILIDADE | `car.drive_setting.esp_enable` | 1 ON → 0 OFF | ATIVO/INATIVO accepted by the reference normalizer |
| DISPLAY | Theme preference, not vehicle telemetry | Contour / Vector in Apex GT | Disabled in this model-3 preview, with an explicit source-only explanation |

## AC menu: exact source contents

- Temperature focus: 16–32, half-degree steps; 16 displays LO, 32 displays HI; otherwise the temperature and °C/°F suffix are displayed
- Fan focus: 0–7 with seven segments; when HVAC is off, effective fan is zero and the readout is OFF
- Status: SISTEMA LIGADO/DESL.; MODO AUTO/MANUAL; ENTRADA RECIRC./EXTERNO
- Short OK toggles focus between fan and temperature
- Up/down changes the focused value, clamped to its domain
- Holding OK toggles Auto; the original source requests `CANCEL_MAX_AC` before writing the requested auto value
- Holding Back toggles recirculation
- Fan moving from off to a positive speed requests power on, then fan speed; moving to zero requests power off, then fan zero

Source keys: `car.hvac.driver_temperature`, `car.hvac.fan_speed`, `car.hvac.power_mode`, `car.hvac.auto_enable`, `car.hvac.cycle_mode`.

No separate compressor/A/C toggle, dual-zone temperature, heated seats, vent-direction selector, defrost or Max AC item was invented. `CANCEL_MAX_AC` is a prerequisite system-action identifier used by Auto, not an extra displayed menu item.

References: `apex-menus.js` lines 35–39, 92–95, 148–151, 249–263, 311–327 and 352–384.

## Host/input contract

| Input | Source behavior |
|---|---|
| `onCardChanged(0)` | Hide menus, native/panel card |
| `onCardChanged(1)` | Main menu; entering from another card returns to the root, retaining selected root row |
| `onCardChanged(3)` | AC menu; retains its selected focus |
| UP / DOWN at main root | Wrap selection across four menu rows |
| ENTER at main root | Open selected detail |
| BACK in a main detail | Return to main root |
| UP / DOWN in info, graphs, trips | Toggle the corresponding two tabs |
| UP / DOWN in settings | Wrap across six source rows |
| ENTER in settings | Cycle selected setting, except disabled source-only DISPLAY in this preview |
| ENTER_LONG in Trip B | Original system action `RESET_DRIVE_INFO`; preview resets only its simulated B metrics |
| BACK at main root, or short BACK in AC | No local card-close action in Apex; the host owns card changes |

Source keys are case-normalized and debounced at 50 ms. Source click handlers open a root item, cycle a setting, select a tab, or focus an AC control. The source's PAINEL/MENU/AC navigation labels are screen-reader-only indicators, not in-theme host-changing controls.

References: `apex-menus.js` lines 386–433 and 470–487. The preview's explicit Menu/AC/Close controls simulate host card selection and are clearly outside the actual vehicle contract.

## Telemetry and units

The module exports all 33 exact canonical source keys in `metadata.keyFields`; local field names can also seed simulation. Missing, invalid or not-yet-received values remain `--`; zero remains a genuine zero where applicable.

- Tire sources are `car.basic.tire_pressure_front_left`, `front_right`, `rear_left`, `rear_right`; PSI is source value / 6.89476; zero pressure is shown as unavailable, matching Apex
- Trip A: `car.basic.cur_journey_odometer`, `cur_journey_drivetime`, `cur_journey_avg_fuel_consume`, `avg_vehicle_speed_since_startup`
- Trip B: `car.basic.accumulated_odometer`, `accumulated_drivetime`, `avg_fuel_consumption`, `vehicle_speed_since_reset`
- Trip time source is minutes, rounded then displayed as hours/minutes
- Consumption source is L/100 km; positive values display 100/value in km/L; a genuine zero displays 0,0 L/100 km
- Power source is `car.ev_info.power_battery_voltage` × `car.ev_info.cur_charge_current` / 1000; needs both operands; negative means regeneration
- Graph speed in the Apex menu uses its canonical raw speed state, unlike the main calibrated speedometer; the preview controller supplies the chosen demonstration speed consistently
- Temperature -1 and 255 sentinel readings are unavailable. The source formats its temperature values with the supplied unit flag; it does not itself convert Celsius to Fahrenheit

No READY, TSR, seatbelt, door, charging-state, tire-temperature or 12 V battery item was added. Apex's OEM READY/traffic-sign/assistance drawings are outside these menu contents.

## Deliberate preview adaptations and limits

1. Revision 9 retains a temporary compact menu below the speedometer (880×180 logical px), with transparent backgrounds, four horizontal root choices, three settings columns following selection, sober gray thin-line graphs and two AC fields plus statuses. Fuel and SOC remain outside it. Total estimated range stays in the central menu footer while open. The independent Canvas renderer and accessible DOM controls retain the Apex navigation contract; the pure state module does not dictate coordinates
2. Speed graph scale is **0–200 km/h**, changed from the original Apex menu's 0–180 to match the owner's explicit 200 km/h panel requirement. Numeric values and samples at 200 and above are retained; a renderer may bound the chart geometry only. Power graph retains the source's −100…100 kW scale; this historical chart is separate from the nonlinear dashboard speed/battery scales
3. DISPLAY shows Contour / Vector as disabled source-only options. It does not silently change the selected model or pretend to switch Apex displays
4. Setting/AC adjustments immediately update a local simulated snapshot and show simulated feedback. Real Apex dispatches to the native bridge and waits for telemetry confirmation. No real command, acknowledgement, cancellation of Max AC, vehicle reset or host action occurs here
5. `lastCommand` is an inert audit description, always marked `simulationOnly: true`. It records the source system-action name and intended write keys/order for tests; it must never be executed
6. Trip B reset requires a deliberate long-key action and all four simulated Trip B metrics to be available. This extra preview-only safeguard prevents missing readings from becoming fabricated zeroes. Source Apex can request the reset without this availability guard
7. No automatic feedback timer is installed. The surrounding preview may dispatch CLEAR_FEEDBACK or clear it when switching cards. This keeps the module pure and timer-free
8. Separate deterministic keyboard/telemetry clocks avoid mixing browser input time with a seekable demonstration timeline. The preview resets history when seeking/restarting
9. Demo data matches the existing `ApexData.demo` profile (Trip A 56.7 km, Trip B 1286.4 km, tire pressures 240/240/235/235 kPa, etc.). Data remains demonstration data, not measured vehicle readings
10. This is not a theme package, an Android integration or evidence of hardware validation. Original Apex source, repository/PR state, deployment and vehicle settings were not modified

## API and rendering contract

UMD browser global: `StarlightMenuState`. Node: `require('./menu-state.js')`.

Exports: `createState(seed)`, `demoData()`, `dispatch(state, action)`, `getView(state)`, `getContent(state)`, `metadata`, `normalize(field,value)`, `power(data)`.

The reducer returns a new state without mutating its input. Supported action types:

```js
{type:'CARD', card:0|1|3}
{type:'OPEN', section:'info'|'graphs'|'settings'|'trips'}
{type:'KEY', key:'UP'|'DOWN'|'ENTER'|'BACK'|'ENTER_LONG'|'BACK_LONG', now:optionalMilliseconds}
{type:'FOCUS', group:'menu'|'setting', index:integer} // selection only, no change/open
{type:'TAB', group:'info'|'graph'|'trip', index:0|1}
{type:'SETTING', index:0..5} // click-equivalent: select and cycle, except DISPLAY
{type:'AC_FOCUS', field:'temp'|'fan'}
{type:'TELEMETRY', values:{fieldOrCanonicalKey:value}, now:optionalMilliseconds}
{type:'RESET_DATA', values:seed} // replace snapshot/history; preserve navigation
{type:'RESET_HISTORY'} // preserve snapshot/navigation, reset timeline history
{type:'CLEAR_FEEDBACK'}
{type:'CLOSE'} // preview host-like card0 action
```

`getContent` returns `{view, visible, title, subtitle, tabs, rows, stats, chart, primary, counter, hint, feedback, footnote, closeAction, backAction}`. Extra relevant controls: `resetAction` for Trip B, `adjustActions` for AC.

Rows/stats/primary share `{id, title, label, value, unit, secondary, subtitle, selected, disabled, action?}`. Tabs have `{title, label, index, group, selected, action}`. AC rows also expose raw value, bounds, temperature fill ratio or fan segments. Charts have `{metric, value, formatted, unit, domain, samples, maxSamples, rangeLabel}`. Arrays returned to renderers are detached from state.

## Verification

Run: `node --test test-menu-state.cjs`.

Coverage includes source metadata, browser UMD and CommonJS, forbidden external capabilities, normalized/missing telemetry, immutable input, every menu/tab/setting cycle, host card reentry and closing, selection-only focus, key throttling, half-degree/LO/HI and fan boundaries, Auto/recirculation, command ordering descriptors, deliberate Trip B reset and missing-data guard, demo-profile parity, 18-sample history, clock separation and replay reset, content contracts, units, 200 km/h numeric/sample retention, and a 3,000-action invariant run.

These are pure state/controller tests; Canvas layout, browser accessibility, final rendering and video validation are separate checks owned by the preview application.
