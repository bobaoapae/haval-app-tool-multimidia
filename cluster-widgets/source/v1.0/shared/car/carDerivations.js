/**
 * carDerivations.js
 * Centralized telemetry calculations and formatters for the Haval Shisuku / Impulse themes.
 * Resolves C3 (Mathematical Derivations Consolidation).
 */

/**
 * Calibrates and adjusts the raw vehicle speed using preference settings.
 * Matches the formula inside InstrumentProjector2.kt exactly.
 * @param {number|string} rawSpeed - Raw vehicle speed in km/h from CAN bus.
 * @param {boolean} enableAdjustment - Whether offset adjustments are enabled.
 * @param {number} offsetPercent - Offset adjustment factor as percentage (e.g. -5 to +5).
 * @returns {string} - Rounded final speed value as a string.
 */
export function getAdjustedSpeed(rawSpeed, enableAdjustment = false, offsetPercent = 0.0) {
    const speed = parseFloat(rawSpeed) || 0.0;
    
    // Formula to match the physical instrument cluster
    const adjustedSpeed = speed * 1.07 - (speed / 180.0) * 0.02;
    
    const finalSpeed = enableAdjustment
        ? adjustedSpeed * (1.0 + (parseFloat(offsetPercent) / 100.0))
        : adjustedSpeed;
        
    return String(Math.floor(finalSpeed));
}

/**
 * Calculates battery power output in kW.
 * @param {number|string} voltage - Battery voltage (V).
 * @param {number|string} current - Battery current (A).
 * @returns {string} - Calculated battery kilowatts formatted as a string.
 */
export function getBatteryKW(voltage, current) {
    const v = parseFloat(voltage) || 0.0;
    const c = parseFloat(current) || 0.0;
    return ((v * c) / 1000.0).toFixed(1);
}

/**
 * Derives dynamic fuel consumption mode and value from CAN payload.
 * Matches the dual metric/idle parser inside InstrumentProjector2.kt.
 * @param {string} rawValue - Raw CAN payload string (e.g. "1.0" or "{1.0, 7.5}" or "{4.0, 1.2}").
 * @returns {{mode: "Idle"|"Running", value: number}}
 */
export function deriveGasConsumption(rawValue) {
    let metricValue = 1.0;
    let consumptionValue = 0.0;
    const str = String(rawValue || "").trim();

    if (str.startsWith("{") && str.endsWith("}") && str.includes(",")) {
        try {
            const cleaned = str.substring(1, str.length - 1);
            const parts = cleaned.split(",");
            if (parts.length >= 2) {
                metricValue = parseFloat(parts[0].trim()) || 0.0;
                consumptionValue = parseFloat(parts[1].trim()) || 0.0;
            }
        } catch (e) {
            metricValue = 1.0;
            consumptionValue = 0.0;
        }
    } else {
        consumptionValue = parseFloat(str) || 0.0;
        metricValue = 1.0;
    }

    if (metricValue === 4.0) {
        // Idle Mode (e.g., L/h)
        const adjustedIdle = Math.floor(consumptionValue * 10.0) / 10.0;
        return { mode: "Idle", value: Math.max(0.0, adjustedIdle) };
    } else {
        // Running Mode (e.g., L/100km or km/L)
        let adjustedRunning = 0.0;
        if (consumptionValue > 0.0) {
            adjustedRunning = Math.floor(10.0 * 100.0 / consumptionValue) / 10.0;
        }
        return { mode: "Running", value: adjustedRunning };
    }
}

/**
 * Calculates recovery/regeneration power factor from dynamic output percentage.
 * @param {number|string} outputPercentage - Raw battery output percentage.
 * @returns {number} - Absolute energy recovery index (>= 0).
 */
export function getRegenPower(outputPercentage) {
    const floatVal = parseFloat(outputPercentage) || 0.0;
    return Math.max(0.0, -1.0 * floatVal);
}

/**
 * Creates the common reducer used by every v1.0 theme for live graph telemetry.
 * Raw voltage/current and fuel payloads need combining/translation before they
 * match the state keys consumed by graphs.js.
 *
 * @param {(key: string, value: *) => void} setState
 * @param {{adjustSpeed?: (rawValue: *) => number|string}} options
 * @returns {(key: string, value: *) => boolean} true when the key was handled
 */
export function createGraphTelemetryHandler(setState, options = {}) {
    let batteryVoltage = 0.0;
    let batteryCurrent = 0.0;

    const asNumber = (value) => {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : 0;
    };

    const updateBatteryPower = () => {
        setState('evPowerKw', Number(getBatteryKW(batteryVoltage, batteryCurrent)));
    };

    return (key, value) => {
        switch (key) {
            case 'car.basic.vehicle_speed':
                setState(
                    'carSpeed',
                    Number(options.adjustSpeed ? options.adjustSpeed(value) : value) || 0
                );
                return true;
            case 'car.basic.engine_speed':
                setState('engineRPM', asNumber(value));
                return true;
            case 'car.ev_info.energy_output_percentage': {
                const output = asNumber(value);
                setState('evPowerFactor', output);
                setState('evPowerRegen', getRegenPower(output));
                return true;
            }
            case 'car.ev_info.power_battery_voltage':
                batteryVoltage = asNumber(value);
                updateBatteryPower();
                return true;
            case 'car.ev_info.cur_charge_current':
                batteryCurrent = asNumber(value);
                updateBatteryPower();
                return true;
            case 'car.basic.instant_fuel_consumption': {
                const gas = deriveGasConsumption(value);
                setState('gasConsumptionMode', gas.mode);
                setState('gasConsumption', gas.mode === 'Running' ? gas.value : 0);
                setState('gasConsumptionIdle', gas.mode === 'Idle' ? gas.value : 0);
                return true;
            }
            case 'car.ev_info.Instant_energy_consumption':
                setState('instantEVConsumption', asNumber(value));
                return true;
            default:
                return false;
        }
    };
}
