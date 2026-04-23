// Mock do ServiceManager.java — estado reativo do veículo + lógica de automações

const state = {
    CAR_BASIC_VEHICLE_SPEED: 0,
    CAR_BASIC_INSIDE_TEMP: 24,
    CAR_BASIC_OUTSIDE_TEMP: 28,
    CAR_BASIC_DRIVING_READY_STATE: 1,
    CAR_BASIC_GEAR_STATUS: 3,                               // 3=Park 4=Reverse 1=Drive
    CAR_BASIC_WINDOW_STATUS: 0,                             // 0=fechado 1=aberto
    CAR_BASIC_SUNROOF_STATUS: 0,
    CAR_BASIC_SUNSHADE_STATUS: 0,
    CAR_DMS_WORK_STATE: 1,                                  // 0=desligado
    CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE: 1,    // 1=aberto 0=dobrado
    CAR_HVAC_POWER_MODE: 0,
    CAR_HVAC_FAN_SPEED: 1,
    CAR_HVAC_DRIVER_TEMPERATURE: 22,
    CAR_HVAC_AUTO_ENABLE: 0,
    CAR_HVAC_CYCLE_MODE: 0,
    CAR_HVAC_ANION_ENABLE: 0,
    CAR_HVAC_AC_ENABLE: 0,
    CAR_EV_SETTING_POWER_MODEL_CONFIG: 1,                   // 0=PHEV 1=HEV 3=EV
    CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL: 2,                // 0=Normal 1=Alto 2=Baixo
    CAR_CONFIGURE_PEDAL_CONTROL_ENABLE: 0,
    CAR_DRIVE_SETTING_ESP_ENABLE: 1,
    CAR_DRIVE_SETTING_DRIVE_MODE: 0,                        // 0=Normal 1=Sport 2=Eco 3=Neve 4=Areia 5=Lama 11=AWD
    CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE: 2,        // 0=Normal 1=Esportiva 2=Conforto
    CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE: 75,
    CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL: 0,
    // Valores derivados / simulados
    SYS_EV_CONSUMPTION: 0,
    SYS_GAS_CONSUMPTION: 0,
    SYS_GAS_CONSUMPTION_IDLE: 0,
    SYS_GAS_CONSUMPTION_MODE: 'Running',
    SYS_LAST_REGEN_VALUE: 0,
};

const settings = {
    CLOSE_WINDOW_ON_POWER_OFF: false,
    CLOSE_SUNROOF_ON_POWER_OFF: false,
    CLOSE_SUNSHADE_ON_SUNROOF_CLOSE: false,
    CLOSE_WINDOW_ON_FOLD_MIRROR: false,
    CLOSE_SUNROOF_ON_FOLD_MIRROR: false,
    CLOSE_WINDOWS_ON_SPEED: false,
    WINDOWS_SPEED_THRESHOLD: 15,
    CLOSE_SUNROOF_ON_SPEED: false,
    SUNROOF_SPEED_THRESHOLD: 15,
    DISABLE_BLUETOOTH_ON_POWER_OFF: false,
    DISABLE_HOTSPOT_ON_POWER_OFF: false,
    ENABLE_MAX_AC_ON_UNLOCK: false,
    MAX_AC_ON_UNLOCK_THRESHOLD: 34,
    MAX_AC_TARGET_TEMP: 28,
    MAX_AC_TIMEOUT: 0,
    ENABLE_SEAT_VENTILATION_ON_AC_ON: false,
};

// ── Cluster bridge: CarConstants → window.control() ──────────────────────────
const EV_MODE_MAP  = { 0: 'PHEV', 1: 'HEV', 3: 'EV' };
const REGEN_MAP    = { 0: 'Normal', 1: 'Alto', 2: 'Baixo' };
const DRIVE_MAP    = { 0: 'Normal', 1: 'Sport', 2: 'Eco', 3: 'Neve', 4: 'Areia', 5: 'Lama', 11: 'AWD' };
const STEER_MAP    = { 0: 'Normal', 1: 'Esportiva', 2: 'Conforto' };

const BRIDGE = {
    CAR_HVAC_FAN_SPEED:                          v => ctrl('fan', v),
    CAR_HVAC_DRIVER_TEMPERATURE:                 v => ctrl('temp', v),
    CAR_HVAC_POWER_MODE:                         v => ctrl('power', v),
    CAR_HVAC_AUTO_ENABLE:                        v => ctrl('auto', v),
    CAR_HVAC_CYCLE_MODE:                         v => ctrl('recycle', v),
    CAR_HVAC_ANION_ENABLE:                       v => ctrl('aion', v),
    CAR_BASIC_VEHICLE_SPEED:                     v => ctrl('carSpeed', v),
    CAR_BASIC_INSIDE_TEMP:                       v => ctrl('inside_temp', v),
    CAR_BASIC_OUTSIDE_TEMP:                      v => ctrl('outside_temp', v),
    CAR_EV_SETTING_POWER_MODEL_CONFIG:           v => ctrl('evMode', EV_MODE_MAP[v] ?? 'HEV'),
    CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL:        v => ctrl('regenMode', REGEN_MAP[v] ?? 'Normal'),
    CAR_CONFIGURE_PEDAL_CONTROL_ENABLE:          v => ctrl('onepedal', Boolean(v)),
    CAR_DRIVE_SETTING_ESP_ENABLE:                v => ctrl('espStatus', v ? 'ON' : 'OFF'),
    CAR_DRIVE_SETTING_DRIVE_MODE:                v => ctrl('drivingMode', DRIVE_MAP[v] ?? 'Normal'),
    CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE:v => ctrl('steerMode', STEER_MAP[v] ?? 'Normal'),
    SYS_EV_CONSUMPTION:                          v => ctrl('evConsumption', v),
    SYS_GAS_CONSUMPTION:                         v => ctrl('gasConsumption', v),
    SYS_GAS_CONSUMPTION_IDLE:                    v => ctrl('gasConsumptionIdle', v),
    SYS_GAS_CONSUMPTION_MODE:                    v => ctrl('gasConsumptionMode', v),
    SYS_LAST_REGEN_VALUE:                        v => ctrl('lastRegenValue', v),
};

function ctrl(key, value) {
    if (window.control) window.control(key, value);
}

// ── Subscribers ───────────────────────────────────────────────────────────────
const subs = [];

function notify(key, value, source) {
    subs.forEach(fn => fn(key, value, source));
}

// ── Internal set (usado por automações e simulação — sem re-trigger) ──────────
function _set(key, value, source = 'system') {
    if (state[key] === value) return;
    state[key] = value;
    if (BRIDGE[key]) BRIDGE[key](value);
    notify(key, value, source);
}

// ── Automation state ──────────────────────────────────────────────────────────
let windowsClosedBySpeed   = false;
let sunroofClosedBySpeed   = false;
let maxAcActive            = false;
let maxAcPrevState         = null;

function runAutomations(key, value) {
    const v = parseFloat(value);

    if (key === 'CAR_BASIC_VEHICLE_SPEED') {
        if (!windowsClosedBySpeed && settings.CLOSE_WINDOWS_ON_SPEED && v > settings.WINDOWS_SPEED_THRESHOLD) {
            windowsClosedBySpeed = true;
            _set('CAR_BASIC_WINDOW_STATUS', 0);
            log('⚡ Velocidade ' + v + ' km/h > ' + settings.WINDOWS_SPEED_THRESHOLD + ' km/h → fechando janelas');
        }
        if (!sunroofClosedBySpeed && settings.CLOSE_SUNROOF_ON_SPEED && v > settings.SUNROOF_SPEED_THRESHOLD) {
            sunroofClosedBySpeed = true;
            _set('CAR_BASIC_SUNROOF_STATUS', 0);
            log('⚡ Velocidade ' + v + ' km/h > ' + settings.SUNROOF_SPEED_THRESHOLD + ' km/h → fechando teto');
        }
        if (v < 10) { windowsClosedBySpeed = false; sunroofClosedBySpeed = false; }
    }

    if (key === 'CAR_DMS_WORK_STATE' && value == 0) {
        if (settings.CLOSE_WINDOW_ON_POWER_OFF)  { _set('CAR_BASIC_WINDOW_STATUS', 0);  log('🔌 Desligamento → fechando janelas'); }
        if (settings.CLOSE_SUNROOF_ON_POWER_OFF) { _set('CAR_BASIC_SUNROOF_STATUS', 0); log('🔌 Desligamento → fechando teto'); }
        if (settings.DISABLE_BLUETOOTH_ON_POWER_OFF) log('🔌 Desligamento → Bluetooth desligado (simulado)');
        if (settings.DISABLE_HOTSPOT_ON_POWER_OFF)   log('🔌 Desligamento → Hotspot desligado (simulado)');
    }

    if (key === 'CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE' && value == 0) {
        if (state.CAR_BASIC_VEHICLE_SPEED == 0 && state.CAR_BASIC_GEAR_STATUS == 3) {
            if (settings.CLOSE_WINDOW_ON_FOLD_MIRROR)  { _set('CAR_BASIC_WINDOW_STATUS', 0);  log('🪟 Espelhos dobrados (parado/Park) → fechando janelas'); }
            if (settings.CLOSE_SUNROOF_ON_FOLD_MIRROR) { _set('CAR_BASIC_SUNROOF_STATUS', 0); log('🪟 Espelhos dobrados (parado/Park) → fechando teto'); }
        }
    }

    if (key === 'CAR_BASIC_DRIVING_READY_STATE' && value != 0 && value != -1) {
        if (settings.ENABLE_MAX_AC_ON_UNLOCK && !maxAcActive
            && state.CAR_BASIC_INSIDE_TEMP >= settings.MAX_AC_ON_UNLOCK_THRESHOLD) {
            activateMaxAc();
        }
    }

    if (key === 'CAR_BASIC_INSIDE_TEMP' && maxAcActive) {
        if (v <= settings.MAX_AC_TARGET_TEMP) cancelMaxAc();
    }

    if (key === 'CAR_HVAC_POWER_MODE' && settings.ENABLE_SEAT_VENTILATION_ON_AC_ON) {
        const level = value == 1 ? 3 : 0;
        _set('CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL', level);
        log('❄️ A/C ' + (value == 1 ? 'ligado' : 'desligado') + ' → ventilação bancos: ' + level);
    }
}

function activateMaxAc() {
    maxAcActive = true;
    maxAcPrevState = {
        fan:  state.CAR_HVAC_FAN_SPEED,
        temp: state.CAR_HVAC_DRIVER_TEMPERATURE,
        auto: state.CAR_HVAC_AUTO_ENABLE,
    };
    _set('CAR_HVAC_POWER_MODE', 1);
    _set('CAR_HVAC_AUTO_ENABLE', 0);
    _set('CAR_HVAC_FAN_SPEED', 7);
    _set('CAR_HVAC_DRIVER_TEMPERATURE', 16);
    log('🥵 Max A/C ATIVADO (interna: ' + state.CAR_BASIC_INSIDE_TEMP + '°C ≥ ' + settings.MAX_AC_ON_UNLOCK_THRESHOLD + '°C)');
}

function cancelMaxAc() {
    if (!maxAcActive) return;
    maxAcActive = false;
    if (maxAcPrevState) {
        _set('CAR_HVAC_FAN_SPEED', maxAcPrevState.fan);
        _set('CAR_HVAC_DRIVER_TEMPERATURE', maxAcPrevState.temp);
        _set('CAR_HVAC_AUTO_ENABLE', maxAcPrevState.auto);
        maxAcPrevState = null;
    }
    log('✅ Max A/C cancelado (temp alvo atingida)');
}

// ── Log ───────────────────────────────────────────────────────────────────────
const logEntries = [];
function log(msg) {
    const ts = new Date().toLocaleTimeString('pt-BR', { hour12: false })
        + '.' + String(new Date().getMilliseconds()).padStart(3, '0');
    const entry = { ts, msg };
    logEntries.unshift(entry);
    if (logEntries.length > 50) logEntries.length = 50;
    notify('__log__', entry, 'log');
}

// ── Scenarios ─────────────────────────────────────────────────────────────────
const SCENARIOS = {
    highway: {
        label: 'Autoestrada',
        state: { CAR_BASIC_VEHICLE_SPEED: 120, CAR_DRIVE_SETTING_DRIVE_MODE: 1, CAR_EV_SETTING_POWER_MODEL_CONFIG: 1,
                 CAR_HVAC_FAN_SPEED: 3, CAR_HVAC_DRIVER_TEMPERATURE: 22, CAR_HVAC_POWER_MODE: 1,
                 CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL: 1, CAR_BASIC_GEAR_STATUS: 1,
                 CAR_BASIC_DRIVING_READY_STATE: 1, CAR_DMS_WORK_STATE: 1 }
    },
    city: {
        label: 'Cidade',
        state: { CAR_BASIC_VEHICLE_SPEED: 40, CAR_DRIVE_SETTING_DRIVE_MODE: 2, CAR_EV_SETTING_POWER_MODEL_CONFIG: 1,
                 CAR_HVAC_FAN_SPEED: 2, CAR_HVAC_DRIVER_TEMPERATURE: 22, CAR_HVAC_POWER_MODE: 1,
                 CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL: 2, CAR_BASIC_GEAR_STATUS: 1,
                 CAR_BASIC_DRIVING_READY_STATE: 1, CAR_DMS_WORK_STATE: 1 }
    },
    charging: {
        label: 'Carregando',
        state: { CAR_BASIC_VEHICLE_SPEED: 0, CAR_EV_SETTING_POWER_MODEL_CONFIG: 3, CAR_HVAC_POWER_MODE: 0,
                 CAR_BASIC_GEAR_STATUS: 3, CAR_BASIC_DRIVING_READY_STATE: 0, CAR_DMS_WORK_STATE: 0 }
    },
    cold_start: {
        label: 'Partida fria',
        state: { CAR_BASIC_DRIVING_READY_STATE: 1, CAR_HVAC_DRIVER_TEMPERATURE: 32, CAR_HVAC_FAN_SPEED: 7,
                 CAR_HVAC_POWER_MODE: 1, CAR_HVAC_AUTO_ENABLE: 1, CAR_BASIC_INSIDE_TEMP: 36,
                 CAR_BASIC_OUTSIDE_TEMP: -5, CAR_DRIVE_SETTING_DRIVE_MODE: 0, CAR_DMS_WORK_STATE: 1 }
    },
};

// ── Simulation loop ───────────────────────────────────────────────────────────
let simInterval   = null;
let simPhase      = 'idle';
let simSpeed      = 0;
let simCounter    = 0;
let simModeTimer  = 80;
let simLastVal    = 0;
const SMOOTH      = 0.1;

function simTick(mult) {
    const step = 2 * mult;
    switch (simPhase) {
        case 'accelerating':
            simSpeed = Math.min(simSpeed + step, 150);
            if (simSpeed >= 150) { simPhase = 'cruising'; simCounter = 0; }
            break;
        case 'cruising':
            if (++simCounter * 100 > 5000 / mult) simPhase = 'decelerating';
            break;
        case 'decelerating':
            simSpeed = Math.max(simSpeed - step * 2.5, 0);
            if (simSpeed <= 0) { simPhase = 'stopped'; simCounter = 0; }
            break;
        case 'stopped':
            if (++simCounter * 100 > 2000 / mult) { simPhase = 'accelerating'; simCounter = 0; }
            break;
    }

    const sf    = simSpeed / 150;
    const rnd   = Math.random();
    simLastVal  = simLastVal * (1 - SMOOTH) + rnd * 100 * SMOOTH;
    const evC   = Math.round(sf * 100 - 50 + (rnd * 10 - 5));
    const gasC  = simSpeed > 5 ? +(sf * 25 + (rnd * 3 - 1.5)).toFixed(1) : 0;
    const idle  = simSpeed <= 5 ? +(rnd * 2).toFixed(1) : 0;
    const regen = simPhase === 'decelerating' ? Math.round((1 - sf) * 80 + rnd * 10) : 0;

    if (--simModeTimer <= 0) {
        _set('SYS_GAS_CONSUMPTION_MODE', state.SYS_GAS_CONSUMPTION_MODE === 'Running' ? 'Idle' : 'Running', 'simulation');
        simModeTimer = Math.floor(rnd * 80) + 40;
    }

    _set('CAR_BASIC_VEHICLE_SPEED', +simSpeed.toFixed(1), 'simulation');
    _set('SYS_EV_CONSUMPTION',      evC,    'simulation');
    _set('SYS_GAS_CONSUMPTION',     gasC,   'simulation');
    _set('SYS_GAS_CONSUMPTION_IDLE', idle,  'simulation');
    _set('SYS_LAST_REGEN_VALUE',    regen,  'simulation');
    _set('CAR_BASIC_INSIDE_TEMP',
        +(state.CAR_BASIC_INSIDE_TEMP + (rnd < 0.3 ? -0.1 : rnd > 0.7 ? 0.1 : 0)).toFixed(1),
        'simulation');

    // speed automations run each tick (idempotent via flags)
    runAutomations('CAR_BASIC_VEHICLE_SPEED', simSpeed);
}

// ── Public API ────────────────────────────────────────────────────────────────
const engine = {
    state,
    settings,
    logEntries,

    get: (key) => state[key],

    set(key, value) {
        if (state[key] === value) return;
        state[key] = value;
        if (BRIDGE[key]) BRIDGE[key](value);
        notify(key, value, 'user');
        runAutomations(key, value);
    },

    setSetting(key, value) {
        settings[key] = value;
        notify('settings:' + key, value, 'settings');
    },

    subscribe(fn) {
        subs.push(fn);
        return () => { const i = subs.indexOf(fn); if (i >= 0) subs.splice(i, 1); };
    },

    loadScenario(name) {
        const s = SCENARIOS[name];
        if (!s) return;
        Object.entries(s.state).forEach(([k, v]) => this.set(k, v));
        log('📋 Cenário "' + s.label + '" carregado');
    },

    startSimulation(multiplier = 1) {
        if (window.simulationInterval) {
            clearInterval(window.simulationInterval);
            window.simulationInterval = null;
        }
        if (simInterval) clearInterval(simInterval);
        simPhase = 'idle'; simSpeed = 0; simCounter = 0;
        setTimeout(() => { simPhase = 'accelerating'; }, 1500);
        simInterval = setInterval(() => simTick(multiplier), 100);
        log('▶ Simulação iniciada (×' + multiplier + ')');
    },

    stopSimulation() {
        if (simInterval) { clearInterval(simInterval); simInterval = null; }
        log('⏸ Simulação pausada');
    },

    isSimulating: () => simInterval !== null,

    syncToCluster() {
        Object.entries(state).forEach(([k, v]) => { if (BRIDGE[k]) BRIDGE[k](v); });
    },

    SCENARIOS,
};

window.vehicleEngine = engine;

// Auto-sync cluster after main.js loads
window.addEventListener('load', () => {
    setTimeout(() => engine.syncToCluster(), 50);
});
