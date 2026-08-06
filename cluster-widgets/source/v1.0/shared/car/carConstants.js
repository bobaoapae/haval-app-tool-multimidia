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
    // HEV energy-reserve submode (only meaningful when power_model_config == 0 / HEV).
    // 1 = Inteligente, 2 = Prioritário. SOC target (20–80) applies in Prioritário.
    POWER_RESERVE_CONFIG: "car.ev_setting.power_reserve_config",
    CHARGE_SOC_TARGET_CONFIG: "car.ev_setting.charge_soc_target_config",
    ENERGY_OUTPUT_PERCENTAGE: "car.ev_info.energy_output_percentage",
    CHARGE_CURRENT: "car.ev_info.cur_charge_current",
    BATTERY_VOLTAGE: "car.ev_info.power_battery_voltage",
    INSTANT_ENERGY_CONSUMPTION: "car.ev_info.Instant_energy_consumption",
    REMAIN_FUEL_PERCENTAGE: "car.basic.remain_fuel_percentage",
    BATTERY_POWER_PERCENTAGE: "car.ev_info.cur_battery_power_percentage",
    FUEL_MODE_REMAIN_ODOMETER: "car.ev_info.fuel_mode_remain_odometer",
    ELECTRIC_MODE_REMAIN_ODOMETER: "car.ev_info.electric_mode_remain_odometer",

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
    // Matches the native getGearLabel()/formatGear() mapping used by the
    // Kotlin side (InstrumentProjector2.kt / BottomBarUI.kt): only 2/3/4 are
    // meaningful, everything else (0, 1, unknown) is neutral/"N". This table
    // previously used a different P/R/N/D order than native, which caused the
    // WebView cluster gear indicator to show the wrong letter (e.g. D as R)
    // on every live telemetry update after the correct initial page-load value.
    [KEYS.GEAR_STATUS]: {
        "0": "N",
        "1": "N",
        "2": "D",
        "3": "P",
        "4": "R"
    },
    [KEYS.REGEN_LEVEL]: {
        "0": "Normal",
        "1": "Alto",
        "2": "Baixo"
    },
    [KEYS.PEDAL_CONTROL_ENABLE]: {
        "0": "OFF",
        "1": "ON"
    },
    [KEYS.POWER_RESERVE_CONFIG]: {
        "1": "Inteligente",
        "2": "Prioritário"
    }
};

// 3. Permitted Option Cycle Sequences (matching Java menu click orders)
export const CYCLE_VALUES = {
    [KEYS.ESP_ENABLE]: ["1", "0"], // ON -> OFF
    [KEYS.POWER_MODEL_CONFIG]: ["3", "1", "0"], // EV -> EVP -> HEV
    [KEYS.DRIVE_MODE]: ["0", "2", "1"], // Normal -> Eco -> Sport
    [KEYS.STEER_ASSIST_MODE]: ["2", "0", "1"], // Conforto -> Normal -> Esportiva
    [KEYS.REGEN_LEVEL]: ["2", "0", "1"], // Baixo -> Normal -> Alto
    [KEYS.PEDAL_CONTROL_ENABLE]: ["0", "1"],
    [KEYS.POWER_RESERVE_CONFIG]: ["1", "2"] // Inteligente <-> Prioritário
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
    gearState: KEYS.GEAR_STATUS,
    // Raw CAN — themes compose "HEV Inteligente" / "HEV Prioridade XX%" themselves.
    // Deliberately excludes hevSocTarget (numeric) and onepedal (boolean).
    hevReserve: KEYS.POWER_RESERVE_CONFIG
};

/**
 * HEV menu/dashboard sublabel when power mode is HEV.
 * @param {string} hevReserve raw '1' | '2' (or labeled Inteligente/Prioritário)
 * @param {number|string} hevSocTarget SOC % for Prioritário (20–80)
 * @returns {string} '' | 'Inteligente' | 'Prioridade XX%'
 */
export function formatHevReserveSublabel(hevReserve, hevSocTarget) {
    const reserve = String(hevReserve == null ? '1' : hevReserve).trim();
    const isPrioritario =
        reserve === '2' || reserve.toLowerCase() === 'prioritário' || reserve.toLowerCase() === 'prioritario';
    if (isPrioritario) {
        const pct = Math.min(80, Math.max(20, parseInt(hevSocTarget, 10) || 50));
        return `Prioridade ${pct}%`;
    }
    return 'Inteligente';
}

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
