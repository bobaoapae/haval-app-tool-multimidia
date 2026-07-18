/**
 * carConstants.js
 * Authoritative constants, value labels, and cycle configurations for Haval Shisuku / Impulse.
 * Derived directly from MainMenu.java and CarConstants.kt.
 */

// 1. CAN-bus Telemetry Keys Constants
export const KEYS = {
    // Physical Car CAN values
    VEHICLE_SPEED: "car.basic.vehicle_speed",
    ENGINE_SPEED: "car.basic.engine_speed",
    INSTANT_FUEL_CONSUMPTION: "car.basic.instant_fuel_consumption",
    TOTAL_ODOMETER: "car.basic.total_odometer",
    GEAR_STATUS: "car.basic.gear_status",
    ESP_ENABLE: "car.drive_setting.esp_enable",
    POWER_MODEL_CONFIG: "car.ev_setting.power_model_config", // EV Mode
    DRIVE_MODE: "car.drive_setting.drive_mode",
    STEER_ASSIST_MODE: "car.drive_setting.steering_wheel_assist_mode",
    REGEN_LEVEL: "car.ev_setting.energy_recovery_level",
    PEDAL_CONTROL_ENABLE: "car.ev.setting.pedal_control_enable",
    ENERGY_OUTPUT_PERCENTAGE: "car.ev_info.energy_output_percentage",
    CHARGE_CURRENT: "car.ev_info.cur_charge_current",
    BATTERY_VOLTAGE: "car.ev_info.power_battery_voltage",
    INSTANT_ENERGY_CONSUMPTION: "car.ev_info.Instant_energy_consumption",
    
    // HVAC Controls
    HVAC_POWER: "car.hvac.power_mode",
    HVAC_FAN_SPEED: "car.hvac.fan_speed",
    HVAC_DRIVER_TEMP: "car.hvac.driver_temperature",
    HVAC_CYCLE_MODE: "car.hvac.cycle_mode",
    HVAC_AUTO: "car.hvac.auto_enable",
    HVAC_ANION: "car.hvac.anion_enable",

    // Virtual Telemetry / Multi-Display / App Launcher Keys
    APP_DISPLAY_1_ACTIVE_APP: "app.display.1.active_app",
    APP_DISPLAY_1_ACTIVE_APP_LABEL: "app.display.1.active_app_label",
    APP_DISPLAY_1_ACTIVE_APP_ICON: "app.display.1.active_app_icon",

    APP_DISPLAY_3_ACTIVE_APP: "app.display.3.active_app",
    APP_DISPLAY_3_ACTIVE_APP_LABEL: "app.display.3.active_app_label",
    APP_DISPLAY_3_ACTIVE_APP_ICON: "app.display.3.active_app_icon",

    APP_LAUNCHER_APPS: "app.launcher.apps",
    APP_NAVIGATION_DIRECTIONS: "app.navigation.directions",
    
    // Media & Telephony
    APP_MEDIA_STATE: "app.media.state",
    APP_MEDIA_TITLE: "app.media.title",
    APP_MEDIA_ARTIST: "app.media.artist",
    APP_MEDIA_ALBUM: "app.media.album",
    APP_MEDIA_DURATION: "app.media.duration",
    APP_MEDIA_POSITION: "app.media.position",
    APP_MEDIA_ALBUM_ART: "app.media.album_art",

    APP_PHONE_STATE: "app.phone.state",
    APP_PHONE_CALLER_NAME: "app.phone.caller_name",
    APP_PHONE_CALLER_NUMBER: "app.phone.caller_number",
    APP_PHONE_CALL_DURATION: "app.phone.call_duration"
};

// 2. Authoritative Map of Value Labels
export const VALUE_LABELS = {
    [KEYS.ESP_ENABLE]: {
        "0": "OFF",
        "1": "ON"
    },
    [KEYS.POWER_MODEL_CONFIG]: {
        "0": "HEV",
        "1": "EVP",
        "3": "EV"
    },
    [KEYS.DRIVE_MODE]: {
        "0": "Normal",
        "1": "Sport",
        "2": "Eco",
        "3": "Neve",
        "4": "Areia",
        "5": "Lama",
        "11": "AWD"
    },
    [KEYS.STEER_ASSIST_MODE]: {
        "0": "Normal",
        "1": "Esportiva",
        "2": "Conforto"
    },
    [KEYS.GEAR_STATUS]: {
        "1": "P",
        "2": "R",
        "3": "N",
        "4": "D",
        "0": "--"
    },
    [KEYS.REGEN_LEVEL]: {
        "0": "Normal",
        "1": "Alto",
        "2": "Baixo"
    },
    [KEYS.PEDAL_CONTROL_ENABLE]: {
        "0": "OFF",
        "1": "ON"
    }
};

// 3. Permitted Option Cycle Sequences (matching Java menu click orders)
export const CYCLE_VALUES = {
    [KEYS.ESP_ENABLE]: ["1", "0"], // ON -> OFF
    [KEYS.POWER_MODEL_CONFIG]: ["3", "1", "0"], // EV -> EVP -> HEV
    [KEYS.DRIVE_MODE]: ["0", "2", "1"], // Normal -> Eco -> Sport
    [KEYS.STEER_ASSIST_MODE]: ["2", "0", "1"], // Conforto -> Normal -> Esportiva
    [KEYS.REGEN_LEVEL]: ["2", "0", "1"], // Baixo -> Normal -> Alto
    [KEYS.PEDAL_CONTROL_ENABLE]: ["0", "1"]
};

/**
 * Resolves a human-readable display label for any key/value pair.
 * @param {string} key
 * @param {string|number} value
 * @returns {string}
 */
export function getLabel(key, value) {
    const stringVal = String(value);
    if (VALUE_LABELS[key] && VALUE_LABELS[key][stringVal] !== undefined) {
        return VALUE_LABELS[key][stringVal];
    }
    return stringVal || "--";
}

// 4. Friendly theme-state key aliases for the canonical CAN keys above.
// Native pushes some raw CAN snapshots to the WebView using these short,
// UI-facing names (e.g. control('espStatus', '1')) instead of the dotted
// canonical key, so getLabel() can't be reached directly from that channel.
// Deliberately excludes boolean-consumed keys like 'onepedal': ON/OFF are
// both non-empty (truthy) strings, so labelling them would break truthy checks.
export const FRIENDLY_KEY_TO_CAN_KEY = {
    espStatus: KEYS.ESP_ENABLE,
    evMode: KEYS.POWER_MODEL_CONFIG,
    drivingMode: KEYS.DRIVE_MODE,
    steerMode: KEYS.STEER_ASSIST_MODE,
    regenMode: KEYS.REGEN_LEVEL,
    gearState: KEYS.GEAR_STATUS
};

/**
 * Translates a raw CAN value arriving under a friendly theme-state key name
 * (e.g. 'espStatus') into its display label, via the same VALUE_LABELS table
 * getLabel() uses. Safe to call unconditionally: keys with no known CAN
 * mapping, and values that are already labels (not present as a raw-value
 * entry in VALUE_LABELS), pass through unchanged.
 * @param {string} friendlyKey
 * @param {string|number} value
 * @returns {string|number}
 */
export function translateFriendlyValue(friendlyKey, value) {
    const canonicalKey = FRIENDLY_KEY_TO_CAN_KEY[friendlyKey];
    if (!canonicalKey) return value;
    return getLabel(canonicalKey, value);
}
