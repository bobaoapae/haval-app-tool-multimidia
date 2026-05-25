/**
 * carConstants.js
 * Authoritative constants, value labels, and cycle configurations for Haval Shisuku / Impulse.
 * Derived directly from MainMenu.java and CarConstants.kt.
 */

// 1. CAN-bus Telemetry Keys Constants
export const KEYS = {
    // Physical Car CAN values
    VEHICLE_SPEED: "car.basic.vehicle_speed",
    TOTAL_ODOMETER: "car.basic.total_odometer",
    GEAR_STATUS: "car.basic.gear_status",
    ESP_ENABLE: "car.drive_setting.esp_enable",
    POWER_MODEL_CONFIG: "car.ev_setting.power_model_config", // EV Mode
    DRIVE_MODE: "car.drive_setting.drive_mode",
    STEER_ASSIST_MODE: "car.drive_setting.steering_wheel_assist_mode",
    REGEN_LEVEL: "car.ev_setting.energy_recovery_level",
    PEDAL_CONTROL_ENABLE: "car.ev.setting.pedal_control_enable",
    
    // HVAC Controls
    HVAC_POWER: "car.hvac.power_mode",
    HVAC_FAN_SPEED: "car.hvac.fan_speed",
    HVAC_DRIVER_TEMP: "car.hvac.driver_temperature",
    HVAC_CYCLE_MODE: "car.hvac.cycle_mode",

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
        "0": "Baixo",
        "1": "Normal",
        "2": "Alto"
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
    [KEYS.REGEN_LEVEL]: ["0", "1", "2"],
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
