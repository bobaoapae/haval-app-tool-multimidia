/**
 * powerFlow.js
 * Decodes `car.ev_info.energy_drive_state` into which power sources are running
 * and which way energy moves, per axle.
 *
 * Ported from the POWER ("Fluxo") card in the H6 3D viewer (com.havalh6.viewer,
 * assets/www/index.html -> CAR_POWER_FLOW), which in turn mirrors the OEM
 * BeanEnergyAssistant flow views. Table cross-checked against the OEM's own
 * `flow_name_value_<N>` strings; see docs/reference/energy-drive-state.md.
 *
 * P2 is the front motor (on the engine/transmission), P4 the rear-axle motor.
 *
 * Per-node direction fields use the same encoding throughout:
 *   +1 = energy leaving the source (driving)
 *   -1 = energy returning to the battery (regenerating / charging)
 *    0 = inactive
 */

/** @typedef {-1|0|1} FlowDir */

/**
 * @typedef {object} PowerFlow
 * @property {'idle'|'ev'|'ice'|'hybrid'|'regen'|'charge'} tone   Overall character of the state.
 * @property {string}  label     Short PT-BR label.
 * @property {boolean} ice       Combustion engine running.
 * @property {boolean} batt      Traction battery participating.
 * @property {boolean} wheel     Wheels exchanging energy.
 * @property {FlowDir} iceBatt   Engine -> battery channel (1 = engine charging the pack).
 * @property {FlowDir} battWheel Battery <-> wheels channel.
 * @property {FlowDir} iceWheel  Engine -> wheels channel (mechanical/direct drive).
 * @property {FlowDir} front     Front axle / P2.
 * @property {FlowDir} rear      Rear axle / P4.
 */

/** @type {PowerFlow} */
export const POWER_FLOW_IDLE = Object.freeze({
    tone: 'idle', label: 'Parado',
    ice: false, batt: false, wheel: false,
    iceBatt: 0, battWheel: 0, iceWheel: 0, front: 0, rear: 0
});

const f = (tone, label, ice, batt, wheel, iceBatt, battWheel, iceWheel, front, rear) =>
    Object.freeze({ tone, label, ice, batt, wheel, iceBatt, battWheel, iceWheel, front, rear });

/**
 * `energy_drive_state` -> flow decomposition.
 *
 * States 30-44 are the AWD (P2+P4) set and only appear on dual-motor vehicles.
 * They are kept so the same table serves every variant.
 *
 * @type {Readonly<Record<number, PowerFlow>>}
 */
export const POWER_FLOW_BY_STATE = Object.freeze({
    0:  POWER_FLOW_IDLE,
    10: POWER_FLOW_IDLE,
    11: f('idle',   'Parado',            true,  false, false,  0,  0, 0,  0,  0),
    21: f('idle',   'Marcha lenta',      true,  false, false,  0,  0, 0,  0,  0),

    14: f('ev',     'Elétrico',          false, true,  true,   0,  1, 0,  1,  0),
    22: f('ev',     'Elétrico',          false, true,  true,   0,  1, 0,  1,  0),

    6:  f('regen',  'Regen',             false, true,  true,   0, -1, 0, -1,  0),
    13: f('regen',  'Regen',             true,  true,  true,   1, -1, 0, -1,  0),

    15: f('hybrid', 'Híbrido',           true,  true,  true,   1,  1, 0,  1,  0),
    16: f('hybrid', 'Híbrido',           true,  true,  true,   1,  1, 0,  1,  0),
    17: f('hybrid', 'Híbrido',           true,  true,  true,   1,  1, 0,  1,  0),

    // RE300 (this vehicle's platform) labels state 3 "Condução paralela"; the
    // generic OEM string is "Only driven by engine". Same mechanics either way.
    3:  f('ice',    'Paralelo',          true,  false, true,   0,  0, 1,  0,  0),
    20: f('hybrid', 'Combinado',         true,  true,  true,   0,  1, 1,  1,  0),

    1:  f('charge', 'Carga',             true,  true,  true,   1,  0, 1,  1,  0),
    12: f('charge', 'Carga lenta',       true,  true,  false,  1,  0, 0,  0,  0),
    18: f('charge', 'Aquecendo',         false, true,  false,  0,  0, 0,  0,  0),
    23: f('charge', 'Externa',           false, true,  false,  0,  0, 0,  0,  0),
    45: f('charge', 'Externa',           false, true,  false,  0,  0, 0,  0,  0),
    46: f('charge', 'Externa',           false, true,  false,  0,  0, 0,  0,  0),

    // Present in the OEM string table but absent from the viewer's map.
    43: f('charge', 'Motor (carga)',     true,  true,  true,   1,  0, 1,  0,  0),
    44: f('ice',    'Motor (descarga)',  true,  true,  true,   0,  1, 1,  0,  0),
    73: f('charge', 'Descarga',          false, true,  false,  0,  0, 0,  0,  0),

    // P2 (front) / P4 (rear) — AWD set.
    30: f('charge', 'Carga P2+P4',       true,  true,  true,   1,  1, 0,  1,  1),
    // 31/37 differ from the viewer's table, which marks both axles as driving.
    // The OEM strings are explicit that P2 is *generating* while P4 drives
    // ("Engine & P4 drive P2 to generate electricity" / "Driven by P4, power
    // generation by engine & P2"), so front is -1 here. These are the two
    // states where the axles genuinely run in opposite directions.
    31: f('hybrid', 'P4 + carga P2',     true,  true,  true,   1,  1, 1, -1,  1),
    32: f('charge', 'Carga P2',          true,  true,  true,   1,  0, 1,  1,  0),
    33: f('charge', 'Carga P4',          true,  true,  true,   1,  0, 1,  0,  1),
    34: f('hybrid', 'Motor + P4',        true,  false, true,   0,  0, 1,  0,  1),
    35: f('hybrid', 'Motor + P2+P4',     true,  true,  true,   0,  1, 1,  1,  1),
    36: f('hybrid', 'Motor + P2',        true,  true,  true,   0,  1, 1,  1,  0),
    37: f('hybrid', 'P4, carga P2',      true,  true,  true,   1,  1, 0, -1,  1),
    38: f('regen',  'Regen dianteiro',   false, true,  true,   0, -1, 0, -1,  0),
    39: f('regen',  'Regen traseiro',    false, true,  true,   0, -1, 0,  0, -1),
    40: f('ev',     'P2 + P4',           false, true,  true,   0,  1, 0,  1,  1),
    41: f('ev',     'P4',                false, true,  true,   0,  1, 0,  0,  1),
    42: f('ev',     'P2',                false, true,  true,   0,  1, 0,  1,  0)
});

/**
 * Values the car sends to mean "signal unavailable". They arrive interleaved
 * with real readings — `motor_speed` alternates between 0 and -99999 seconds
 * apart on a parked car — so a consumer that does not drop them will flicker.
 */
const SENTINELS = new Set([-99999, -1001, -2147483648]);

/**
 * True when a raw car value is a real reading rather than an "unavailable" marker.
 * @param {*} raw
 * @returns {boolean}
 */
export function isValidCarNumber(raw) {
    const n = Number(raw);
    return Number.isFinite(n) && !SENTINELS.has(n);
}

/**
 * Looks up a raw `energy_drive_state`. Unknown or unparseable values fall back
 * to idle rather than throwing — the enum is vehicle-specific and we would
 * rather show "Parado" than a broken widget.
 * @param {*} raw
 * @returns {PowerFlow}
 */
export function resolvePowerFlow(raw) {
    const n = parseInt(String(raw == null ? '' : raw).trim(), 10);
    return POWER_FLOW_BY_STATE[n] || POWER_FLOW_IDLE;
}

/**
 * `'drive' | 'regen' | ''` for a direction field, for use as a CSS class.
 * @param {FlowDir} dir
 * @returns {'drive'|'regen'|''}
 */
export function flowClass(dir) {
    if (dir > 0) return 'drive';
    if (dir < 0) return 'regen';
    return '';
}

/**
 * Recovers a usable flow when the car reports an idle/unmapped enum while the
 * vehicle is demonstrably moving under pack power. Dual-motor vehicles do this
 * routinely; without it the widget sits on "Parado" mid-drive.
 *
 * Only ever attributes the rear axle when `awd` is set. The viewer's version
 * always lights both axles, which invents a P4 motor on a front-drive car.
 *
 * @param {PowerFlow} flow    Flow resolved from the raw enum.
 * @param {number} packKw     Signed pack power (negative = into the battery).
 * @param {boolean} engineOn  Engine running, from `car.basic.engine_state`.
 * @param {boolean} [awd]     Vehicle has a rear-axle motor.
 * @returns {PowerFlow}
 */
export function inferFlowWhenIdle(flow, packKw, engineOn, awd = false) {
    if (flow.tone !== 'idle') return flow;
    if (!Number.isFinite(packKw)) return flow;

    // The enum itself can say the engine is running (state 11) even while the
    // tone is idle; trust either source.
    const ice = engineOn || flow.ice;
    const rear = awd ? 1 : 0;

    if (packKw < -0.15) {
        return f('regen', 'Regen', ice, true, true, ice ? 1 : 0, -1, 0, -1, awd ? -1 : 0);
    }
    if (packKw > 0.15) {
        return ice
            ? f('hybrid', 'Híbrido', true, true, true, 0, 1, 1, 1, rear)
            : f('ev', awd ? 'P2 + P4' : 'Elétrico', false, true, true, 0, 1, 0, 1, rear);
    }
    if (ice) return POWER_FLOW_BY_STATE[21];
    return flow;
}

/**
 * Full resolution in one call: enum -> flow, with the charging and
 * standstill corrections the viewer's POWER card applies.
 *
 * @param {object} input
 * @param {*} input.driveState             Raw `car.ev_info.energy_drive_state`.
 * @param {*} [input.chargingState]        Raw `car.ev_info.charging_state` ("1" = plugged in).
 * @param {number} [input.packKw]          Signed pack power, negative into the battery.
 * @param {number} [input.speedKmh]        Vehicle speed.
 * @param {boolean} [input.engineOn]
 * @param {boolean} [input.awd]            Vehicle has a rear-axle motor. Leave
 *   false unless a P4 state (30-42) or a valid `rear_motor_speed` has actually
 *   been observed — otherwise the idle-recovery path invents a rear axle.
 * @returns {PowerFlow}
 */
export function derivePowerFlow({ driveState, chargingState, packKw, speedKmh, engineOn = false, awd = false } = {}) {
    let flow = resolvePowerFlow(driveState);

    // Plugged in but the enum still says idle: the car reports external
    // charging through charging_state, not always through the flow enum.
    const charging = String(chargingState == null ? '' : chargingState).trim() === '1';
    if (charging && flow.tone === 'idle') return POWER_FLOW_BY_STATE[23];

    // At a standstill any residual pack reading is noise, so do not let it
    // promote an idle state into a fake drive/regen.
    const stopped = Number.isFinite(speedKmh) && speedKmh < 0.5;
    if (charging || stopped) return flow;

    return inferFlowWhenIdle(flow, packKw, engineOn, awd);
}
