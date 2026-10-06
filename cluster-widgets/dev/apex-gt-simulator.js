// Theme Lab only. These mock values never enter the standalone theme package.
const MOCK_BATTERY_VOLTAGE = 400;
const DISPLAY_STORAGE_KEY = 'haval.ApexGT.apexDisplayMode';
const DISPLAY_PREFERENCE_KEYS = new Set([
  'apexDisplayMode', 'app.preferences.apexDisplayMode',
  'apex_display_mode', 'app.preferences.apex_display_mode',
]);
const installedAdapters = new WeakMap();
const canonicalKeys = {
  carSpeed: 'car.basic.vehicle_speed',
  engineRPM: 'car.basic.engine_speed',
  odometer: 'car.basic.total_odometer',
  gearState: 'car.basic.gear_status',
  drivingMode: 'car.drive_setting.drive_mode',
  evMode: 'car.ev_setting.power_model_config',
  inside_temp: 'car.basic.inside_temp',
  outside_temp: 'car.basic.outside_temp',
  tempUnit: 'car.configure.default_temp_unit',
  fuelPercent: 'car.basic.remain_fuel_percentage',
  batteryPercent: 'car.ev_info.cur_battery_power_percentage',
  fuelRange: 'car.ev_info.fuel_mode_remain_odometer',
  batteryRange: 'car.ev_info.electric_mode_remain_odometer',
  steerMode: 'car.drive_setting.steering_wheel_assist_mode',
  regenMode: 'car.ev_setting.energy_recovery_level',
  espStatus: 'car.drive_setting.esp_enable',
  power: 'car.hvac.power_mode',
  fan: 'car.hvac.fan_speed',
  temp: 'car.hvac.driver_temperature',
  recycle: 'car.hvac.cycle_mode',
  auto: 'car.hvac.auto_enable',
  tripAOdometer: 'car.basic.cur_journey_odometer',
  tripADriveTime: 'car.basic.cur_journey_drivetime',
  tripAAvgConsumption: 'car.basic.cur_journey_avg_fuel_consume',
  tripAAvgSpeed: 'car.basic.avg_vehicle_speed_since_startup',
  tripBOdometer: 'car.basic.accumulated_odometer',
  tripBDriveTime: 'car.basic.accumulated_drivetime',
  tripBAvgConsumption: 'car.basic.avg_fuel_consumption',
  tripBAvgSpeed: 'car.basic.vehicle_speed_since_reset',
  tripOdometer: 'car.basic.accumulated_odometer',
  tripDriveTime: 'car.basic.accumulated_drivetime',
  tripAvgConsumption: 'car.basic.avg_fuel_consumption',
  tripAvgSpeed: 'car.basic.vehicle_speed_since_reset',
  tirePressureFrontLeft: 'car.basic.tire_pressure_front_left',
  tirePressureFrontRight: 'car.basic.tire_pressure_front_right',
  tirePressureRearLeft: 'car.basic.tire_pressure_rear_left',
  tirePressureRearRight: 'car.basic.tire_pressure_rear_right',
};

const enumLabels = {
  drivingMode: { 0: 'Normal', 1: 'Sport', 2: 'Eco', 3: 'Neve', 4: 'Areia', 5: 'Lama', 11: 'AWD' },
  evMode: { 0: 'HEV', 1: 'EVP', 3: 'EV' },
  steerMode: { 0: 'Normal', 1: 'Esportiva', 2: 'Conforto' },
  regenMode: { 0: 'Normal', 1: 'Alto', 2: 'Baixo' },
  espStatus: { 0: 'OFF', 1: 'ON' },
  tempUnit: { 0: '°C', 1: '°F' },
};
const enumAliases = {
  evMode: { 'HEV INTELIGENTE': 0 },
  espStatus: { ATIVO: 1, INATIVO: 0, TRUE: 1, FALSE: 0 },
  tempUnit: { C: 0, F: 1 },
};
const writableFields = {
  'car.drive_setting.drive_mode': { alias: 'drivingMode', values: [0, 1, 2, 3, 4, 5, 11] },
  'car.ev_setting.power_model_config': { alias: 'evMode', values: [0, 1, 3] },
  'car.drive_setting.steering_wheel_assist_mode': { alias: 'steerMode', values: [0, 1, 2] },
  'car.ev_setting.energy_recovery_level': { alias: 'regenMode', values: [0, 1, 2] },
  'car.drive_setting.esp_enable': { alias: 'espStatus', values: [0, 1] },
  'car.hvac.power_mode': { alias: 'power', values: [0, 1] },
  'car.hvac.fan_speed': { alias: 'fan', values: [0, 1, 2, 3, 4, 5, 6, 7] },
  'car.hvac.driver_temperature': { alias: 'temp', min: 16, max: 32, step: 0.5 },
  'car.hvac.cycle_mode': { alias: 'recycle', values: [0, 1] },
  'car.hvac.auto_enable': { alias: 'auto', values: [0, 1] },
};
const tripBKeys = [
  'car.basic.accumulated_odometer',
  'car.basic.accumulated_drivetime',
  'car.basic.avg_fuel_consumption',
  'car.basic.vehicle_speed_since_reset',
];

function canonicalValueFor(alias, value) {
  if (!Object.hasOwn(enumLabels, alias) || value === null || value === undefined) return value;
  const label = String(value).trim().toUpperCase();
  if (alias === 'evMode' && /^HEV PRIORIDADE \d+%$/.test(label)) return 0;
  if (Object.hasOwn(enumAliases[alias] || {}, label)) return enumAliases[alias][label];
  for (const [raw, friendly] of Object.entries(enumLabels[alias])) {
    if (label === raw || label === friendly.toUpperCase()) return Number(raw);
  }
  return value;
}

export function installApexGtSimulator(frameWindow) {
  installedAdapters.get(frameWindow)?.cleanup();
  if (!frameWindow?.document?.getElementById('apex-gt') || typeof frameWindow.control !== 'function') {
    return { cleanup() {} };
  }

  const originalControl = frameWindow.control;
  const previousAndroid = Object.getOwnPropertyDescriptor(frameWindow, 'Android');
  let cleanedUp = false;
  let mockBridge = null;
  const getStateManager = () => frameWindow.__THEME_LAB_STATE_MANAGER__
    || frameWindow.__TEST_HARNESS?.stateManager;
  const send = (key, value) => originalControl.call(frameWindow, key, value);
  const sendCanonical = (key, value) => {
    const manager = getStateManager();
    if (typeof manager?.set === 'function' && typeof manager.get === 'function'
      && !Object.is(manager.get(key), value)) {
      // Keep later harness snapshots in sync with changes made through a
      // friendly control. Its setter calls wrappedControl with a canonical key.
      manager.set(key, value);
    } else {
      send(key, value);
    }
  };
  const wrappedControl = function (key, value) {
    const result = originalControl.apply(this, arguments);
    if (key === 'evPowerKw') {
      const numeric = typeof value === 'number' || (typeof value === 'string' && value.trim() !== '');
      const power = numeric ? Number(value) : NaN;
      send('car.ev_info.power_battery_voltage', MOCK_BATTERY_VOLTAGE);
      send('car.ev_info.cur_charge_current', Number.isFinite(power) ? power * 1000 / MOCK_BATTERY_VOLTAGE : null);
    } else if (Object.hasOwn(canonicalKeys, key)) {
      sendCanonical(canonicalKeys[key], canonicalValueFor(key, value));
    }
    return result;
  };

  const adapter = {
    cleanup() {
      if (cleanedUp) return;
      cleanedUp = true;
      frameWindow.removeEventListener?.('apex-cleanup', adapter.cleanup);
      if (frameWindow.control === wrappedControl) frameWindow.control = originalControl;
      if (mockBridge && frameWindow.Android === mockBridge) {
        if (previousAndroid) Object.defineProperty(frameWindow, 'Android', previousAndroid);
        else delete frameWindow.Android;
      }
      if (installedAdapters.get(frameWindow) === adapter) installedAdapters.delete(frameWindow);
    },
  };
  frameWindow.control = wrappedControl;
  // Commands are deliberately available only in the local Lab. Production
  // continues to require the real bridge and its telemetry confirmation.
  if (!frameWindow.Android) {
    let displayPreference = 'Contour';
    try {
      if (frameWindow.localStorage?.getItem(DISPLAY_STORAGE_KEY) === 'Vector') displayPreference = 'Vector';
    } catch {
      // Storage may be unavailable in a sandboxed preview. The current frame
      // still keeps its appearance selection without touching global settings.
    }
    const assertActive = () => {
      if (cleanedUp) throw new Error('Apex GT Theme Lab mock is disposed');
    };
    const requireStateManager = () => {
      assertActive();
      const manager = getStateManager();
      if (!manager || typeof manager.set !== 'function') {
        throw new Error('Apex GT Theme Lab telemetry is not ready');
      }
      return manager;
    };
    const echoValue = (manager, key, value) => {
      manager.set(key, value);
      // A canonical snapshot may already hold the value. Explicitly echo the
      // acknowledged command even when the harness deduplicates its setter.
      send(key, value);
    };
    mockBridge = Object.freeze({
      __themeLabMock: 'ApexGT',
      getPreference(key, defaultValue) {
        assertActive();
        return DISPLAY_PREFERENCE_KEYS.has(key) ? displayPreference : defaultValue;
      },
      savePreference(key, value) {
        assertActive();
        if (!DISPLAY_PREFERENCE_KEYS.has(key)) {
          throw new Error(`Apex GT Theme Lab mock rejects unsupported preference: ${key}`);
        }
        const normalized = value === 'Vector' ? 'Vector' : 'Contour';
        displayPreference = normalized;
        try {
          if (frameWindow.localStorage?.getItem(DISPLAY_STORAGE_KEY) !== normalized) {
            frameWindow.localStorage?.setItem(DISPLAY_STORAGE_KEY, normalized);
          }
        } catch {
          // Match the in-memory selection even if persistent storage is blocked.
        }
        const manager = getStateManager();
        if (typeof manager?.set === 'function') {
          manager.set('apexDisplayMode', normalized);
          manager.set('apex_display_mode', normalized);
        }
        send('app.preferences.apexDisplayMode', normalized);
      },
      updateCarData(key, value) {
        if (!Object.hasOwn(writableFields, key)) {
          throw new Error(`Apex GT Theme Lab mock rejects unsupported write: ${key}`);
        }
        const field = writableFields[key];
        const numeric = (typeof value === 'number' || (typeof value === 'string' && value.trim() !== ''))
          ? Number(value) : NaN;
        const valid = Number.isFinite(numeric) && (field.values
          ? field.values.includes(numeric)
          : numeric >= field.min && numeric <= field.max && Number.isInteger(numeric / field.step));
        if (!valid) throw new Error(`Apex GT Theme Lab mock rejects invalid value for ${key}`);
        const manager = requireStateManager();
        const friendlyValue = enumLabels[field.alias]?.[numeric] ?? numeric;
        manager.set(field.alias, friendlyValue);
        echoValue(manager, key, numeric);
      },
      triggerSystemAction(action) {
        if (action !== 'CANCEL_MAX_AC' && action !== 'RESET_DRIVE_INFO') {
          throw new Error(`Apex GT Theme Lab mock rejects unsupported action: ${action}`);
        }
        const manager = requireStateManager();
        if (action === 'CANCEL_MAX_AC') {
          manager.set('maxauto', 0);
          return;
        }
        for (const key of tripBKeys) {
          for (const [alias, canonical] of Object.entries(canonicalKeys)) {
            if (canonical === key) manager.set(alias, 0);
          }
          echoValue(manager, key, 0);
        }
      },
    });
    frameWindow.Android = mockBridge;
  }
  frameWindow.addEventListener?.('apex-cleanup', adapter.cleanup);
  installedAdapters.set(frameWindow, adapter);
  // Seed fields absent from the legacy harness before its first snapshot. Later
  // user controls (including inside_temp) remain authoritative.
  send('car.basic.inside_temp', 22.5);
  send('car.configure.default_temp_unit', 0);
  send('car.ev_info.power_battery_voltage', MOCK_BATTERY_VOLTAGE);
  send('car.ev_info.cur_charge_current', 0);
  return adapter;
}
