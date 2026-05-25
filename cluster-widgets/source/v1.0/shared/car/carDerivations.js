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
