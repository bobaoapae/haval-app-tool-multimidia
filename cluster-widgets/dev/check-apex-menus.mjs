import assert from 'node:assert/strict';
import { installApexGtSimulator } from './apex-gt-simulator.js';
import { createLegacyTelemetryStateManager, installLegacyTelemetryHarness } from './legacy-telemetry.js';

function createFrame(androidDescriptor) {
  const calls = [];
  const listeners = new Map();
  const frame = {
    document: { getElementById: (id) => id === 'apex-gt' ? {} : null },
    control(key, value) { calls.push([key, value]); },
    addEventListener(name, handler) {
      if (!listeners.has(name)) listeners.set(name, new Set());
      listeners.get(name).add(handler);
    },
    removeEventListener(name, handler) { listeners.get(name)?.delete(handler); },
  };
  if (androidDescriptor) Object.defineProperty(frame, 'Android', androidDescriptor);
  return {
    frame,
    calls,
    emit: (name) => { for (const handler of listeners.get(name) || []) handler(); },
    listenerCount: (name) => listeners.get(name)?.size || 0,
    latest: (key) => calls.findLast(([received]) => received === key)?.[1],
  };
}

const fixture = createFrame();
const { frame, calls, latest } = fixture;
const originalControl = frame.control;
const adapter = installApexGtSimulator(frame);
const bridge = frame.Android;
assert.equal(bridge.__themeLabMock, 'ApexGT');
assert.equal(bridge.subscribe, undefined, 'o mock não altera assinaturas nem inicialização do tema');
assert.equal(fixture.listenerCount('apex-cleanup'), 1);
const beforeReady = calls.length;
assert.throws(() => bridge.updateCarData('car.hvac.fan_speed', '2'), /not ready/);
assert.equal(calls.length, beforeReady, 'sem harness pronto, nenhum comando deve confirmar sucesso');

// The real Lab also installs its manager after the Apex adapter.
const manager = createLegacyTelemetryStateManager(frame);
frame.__THEME_LAB_STATE_MANAGER__ = manager;
frame.__TEST_HARNESS = { stateManager: manager };
manager.pushInitialTelemetry();

const writes = [
  ['car.hvac.power_mode', '0', 'power', 0],
  ['car.hvac.fan_speed', '4', 'fan', 4],
  ['car.hvac.driver_temperature', '24.5', 'temp', 24.5],
  ['car.hvac.cycle_mode', '1', 'recycle', 1],
  ['car.hvac.auto_enable', '1', 'auto', 1],
  ['car.drive_setting.drive_mode', '2', 'drivingMode', 'Eco'],
  ['car.ev_setting.power_model_config', '3', 'evMode', 'EV'],
  ['car.drive_setting.steering_wheel_assist_mode', '0', 'steerMode', 'Normal'],
  ['car.ev_setting.energy_recovery_level', '1', 'regenMode', 'Alto'],
  ['car.drive_setting.esp_enable', '0', 'espStatus', 'OFF'],
];
for (const [key, value, alias, friendly] of writes) {
  let observed;
  const unsubscribe = manager.subscribe(alias, (next) => { observed = next; });
  bridge.updateCarData(key, value);
  assert.equal(latest(key), Number(value), `comando ${key} deve confirmar canônico`);
  assert.equal(manager.get(key), Number(value));
  assert.equal(manager.get(alias), friendly, `controle ${alias} deve refletir o menu`);
  assert.equal(observed, friendly, `assinantes do painel ${alias} devem ser notificados`);
  unsubscribe();
}

const rejectedWrites = [
  ['car.basic.vehicle_speed', '90'],
  ['car.configure.default_temp_unit', '1'],
  ['car.hvac.fan_speed', '8'],
  ['car.hvac.fan_speed', '-1'],
  ['car.hvac.fan_speed', '2.5'],
  ['car.hvac.driver_temperature', '15.5'],
  ['car.hvac.driver_temperature', '33'],
  ['car.hvac.driver_temperature', '24.1'],
  ['car.hvac.auto_enable', '2'],
  ['car.hvac.auto_enable', null],
  ['car.hvac.auto_enable', ''],
  ['car.hvac.auto_enable', true],
  ['car.drive_setting.drive_mode', '99'],
  ['car.ev_setting.power_model_config', '2'],
  ['__proto__', '1'],
];
const beforeRejections = manager.getState();
const callsBeforeRejections = calls.length;
for (const [key, value] of rejectedWrites) assert.throws(() => bridge.updateCarData(key, value), /rejects/);
assert.throws(() => bridge.triggerSystemAction('LAUNCH_APP'), /unsupported action/);
assert.deepEqual(manager.getState(), beforeRejections);
assert.equal(calls.length, callsBeforeRejections, 'leituras/valores inválidos não podem produzir eco');

const canonicalCases = [
  ['drivingMode', 'Normal', 'car.drive_setting.drive_mode', 0],
  ['drivingMode', 'Sport', 'car.drive_setting.drive_mode', 1],
  ['evMode', 'HEV Inteligente', 'car.ev_setting.power_model_config', 0],
  ['evMode', 'HEV Prioridade 50%', 'car.ev_setting.power_model_config', 0],
  ['steerMode', 'Conforto', 'car.drive_setting.steering_wheel_assist_mode', 2],
  ['steerMode', 'Normal', 'car.drive_setting.steering_wheel_assist_mode', 0],
  ['steerMode', 'Esportiva', 'car.drive_setting.steering_wheel_assist_mode', 1],
  ['regenMode', 'Baixo', 'car.ev_setting.energy_recovery_level', 2],
  ['regenMode', 'Normal', 'car.ev_setting.energy_recovery_level', 0],
  ['regenMode', 'Alto', 'car.ev_setting.energy_recovery_level', 1],
  ['espStatus', 'ON', 'car.drive_setting.esp_enable', 1],
  ['espStatus', 'OFF', 'car.drive_setting.esp_enable', 0],
  ['tempUnit', '°F', 'car.configure.default_temp_unit', 1],
  ['tempUnit', '°C', 'car.configure.default_temp_unit', 0],
  ['fan', 2, 'car.hvac.fan_speed', 2],
  ['temp', 18.5, 'car.hvac.driver_temperature', 18.5],
  ['tripAOdometer', 71.2, 'car.basic.cur_journey_odometer', 71.2],
  ['tripADriveTime', 120, 'car.basic.cur_journey_drivetime', 120],
  ['tripAAvgConsumption', 6.5, 'car.basic.cur_journey_avg_fuel_consume', 6.5],
  ['tripAAvgSpeed', 35.6, 'car.basic.avg_vehicle_speed_since_startup', 35.6],
  ['tripBOdometer', 850, 'car.basic.accumulated_odometer', 850],
  ['tripBDriveTime', 900, 'car.basic.accumulated_drivetime', 900],
  ['tripBAvgConsumption', 7.3, 'car.basic.avg_fuel_consumption', 7.3],
  ['tripBAvgSpeed', 47.1, 'car.basic.vehicle_speed_since_reset', 47.1],
  ['tirePressureFrontLeft', 250, 'car.basic.tire_pressure_front_left', 250],
  ['tirePressureFrontRight', 251, 'car.basic.tire_pressure_front_right', 251],
  ['tirePressureRearLeft', 248, 'car.basic.tire_pressure_rear_left', 248],
  ['tirePressureRearRight', 249, 'car.basic.tire_pressure_rear_right', 249],
];
for (const [alias, friendly, key, expected] of canonicalCases) {
  manager.set(alias, friendly);
  assert.equal(latest(key), expected, `${alias} deve atualizar o contrato canônico`);
  assert.equal(manager.get(key), expected);
  manager.pushInitialTelemetry();
  assert.equal(latest(key), expected, `snapshot posterior não pode restaurar ${key} antigo`);
}
manager.set('temp', null);
assert.equal(latest('car.hvac.driver_temperature'), null, 'ausência deve continuar ausente');

manager.set('maxauto', 1);
const temperatureBeforeCancel = manager.get('temp');
const fanBeforeCancel = manager.get('fan');
bridge.triggerSystemAction('CANCEL_MAX_AC');
assert.equal(manager.get('maxauto'), 0);
assert.equal(manager.get('temp'), temperatureBeforeCancel);
assert.equal(manager.get('fan'), fanBeforeCancel);

const tripAKeys = canonicalCases.filter(([alias]) => alias.startsWith('tripA')).map(([, , key]) => key);
const tripBKeys = canonicalCases.filter(([alias]) => alias.startsWith('tripB')).map(([, , key]) => key);
const tripABeforeReset = tripAKeys.map((key) => manager.get(key));
bridge.triggerSystemAction('RESET_DRIVE_INFO');
for (const key of tripBKeys) {
  assert.equal(manager.get(key), 0);
  assert.equal(latest(key), 0);
}
for (const alias of ['tripBOdometer', 'tripBDriveTime', 'tripBAvgConsumption', 'tripBAvgSpeed',
  'tripOdometer', 'tripDriveTime', 'tripAvgConsumption', 'tripAvgSpeed']) assert.equal(manager.get(alias), 0);
assert.deepEqual(tripAKeys.map((key) => manager.get(key)), tripABeforeReset, 'reset B deve preservar Trip A');
manager.pushInitialTelemetry();
for (const key of tripBKeys) assert.equal(latest(key), 0, 'snapshot deve preservar reset confirmado');

const replacement = installApexGtSimulator(frame);
assert.notEqual(frame.Android, bridge);
assert.equal(fixture.listenerCount('apex-cleanup'), 1);
adapter.cleanup();
assert.equal(frame.Android.__themeLabMock, 'ApexGT', 'cleanup antigo deve preservar instalação nova');
assert.throws(() => bridge.updateCarData('car.hvac.fan_speed', 2), /disposed/);
calls.length = 0;
frame.control('evPowerKw', -34);
assert.deepEqual(calls, [
  ['evPowerKw', -34],
  ['car.ev_info.power_battery_voltage', 400],
  ['car.ev_info.cur_charge_current', -85],
], 'reinstalação não duplica wrapper e preserva os dois operandos de potência');
fixture.emit('apex-cleanup');
assert.equal(frame.control, originalControl);
assert.equal(Object.hasOwn(frame, 'Android'), false);
assert.equal(fixture.listenerCount('apex-cleanup'), 0);
replacement.cleanup();
manager.dispose();

const realBridge = Object.freeze({ updateCarData() { throw new Error('must not call real bridge'); } });
const native = createFrame({ value: realBridge, writable: false, enumerable: false, configurable: false });
const nativeDescriptor = Object.getOwnPropertyDescriptor(native.frame, 'Android');
installApexGtSimulator(native.frame).cleanup();
assert.equal(native.frame.Android, realBridge);
assert.deepEqual(Object.getOwnPropertyDescriptor(native.frame, 'Android'), nativeDescriptor);

const existingNull = { value: null, writable: true, enumerable: false, configurable: true };
const nullFixture = createFrame(existingNull);
installApexGtSimulator(nullFixture.frame).cleanup();
assert.deepEqual(Object.getOwnPropertyDescriptor(nullFixture.frame, 'Android'), existingNull);

const externallyReplaced = createFrame();
const replacedAdapter = installApexGtSimulator(externallyReplaced.frame);
const externalControl = () => {};
externallyReplaced.frame.Android = realBridge;
externallyReplaced.frame.control = externalControl;
replacedAdapter.cleanup();
assert.equal(externallyReplaced.frame.Android, realBridge);
assert.equal(externallyReplaced.frame.control, externalControl);

const unrelated = createFrame();
unrelated.frame.document.getElementById = () => null;
installApexGtSimulator(unrelated.frame).cleanup();
assert.equal(Object.hasOwn(unrelated.frame, 'Android'), false);
assert.equal(unrelated.calls.length, 0);

const displayStorageKey = 'haval.ApexGT.apexDisplayMode';
const displayAliases = ['apexDisplayMode', 'app.preferences.apexDisplayMode',
  'apex_display_mode', 'app.preferences.apex_display_mode'];
const storageValues = new Map([['haval.OtherTheme.apexDisplayMode', 'Other']]);
let storageWrites = 0;
const storage = {
  getItem: (key) => storageValues.get(key) ?? null,
  setItem(key, value) { storageValues.set(key, String(value)); storageWrites += 1; },
};

function installPreview({ folder = 'ApexGT', localStorage = storage, android, onBootstrap = () => {} } = {}) {
  const result = createFrame(android);
  const { frame: preview } = result;
  const removed = new Set();
  const panels = ['testing-settings-harness', 'testing-console-harness', 'testing-shortcuts-harness'];
  preview.localStorage = localStorage;
  preview.document = {
    getElementById(id) {
      if (id === 'apex-gt') return folder === 'ApexGT' ? {} : null;
      return panels.includes(id) ? { remove: () => removed.add(id) } : null;
    },
    createElement: () => ({ addEventListener() {}, remove() {} }),
    head: { appendChild: () => onBootstrap(preview) },
  };
  const apex = installApexGtSimulator(preview);
  const telemetry = installLegacyTelemetryHarness(preview, { folder: `source/v1.0/${folder}`, folderName: folder, keyboard: 'none', telemetry: 'injected' });
  preview.__TEST_HARNESS = {
    stateManager: preview.__THEME_LAB_STATE_MANAGER__,
    startSimulation() {},
    stopSimulation() {},
  };
  result.emit('theme-lab-telemetry-ready');
  return { ...result, removed, cleanup() { telemetry.cleanup(); apex.cleanup(); } };
}

const firstPreview = installPreview({ onBootstrap(preview) {
  assert.equal(preview.__THEME_LAB_STATE_MANAGER__.get('apexDisplayMode'), 'Contour');
  assert.equal(preview.__THEME_LAB_STATE_MANAGER__.get('apex_display_mode'), 'Contour');
} });
const preferenceBridge = firstPreview.frame.Android;
for (const key of displayAliases) assert.equal(preferenceBridge.getPreference(key, 'Vector'), 'Contour');
assert.equal(preferenceBridge.getPreference('foreignPreference', 'fallback'), 'fallback');
assert.throws(() => preferenceBridge.savePreference('display', 'Vector'), /unsupported preference/);
assert.equal(preferenceBridge.saveSetting, undefined, 'Display local nunca expõe saveSetting global');
assert.ok(!firstPreview.removed.has('testing-settings-harness'), 'Apex mantém painel Aparência');
assert.ok(firstPreview.removed.has('testing-console-harness'));
assert.ok(!firstPreview.removed.has('testing-shortcuts-harness'));

let selectedDisplay;
firstPreview.frame.__TEST_HARNESS.stateManager.subscribe('apexDisplayMode', (value) => { selectedDisplay = value; });
for (const key of displayAliases) preferenceBridge.savePreference(key, 'Vector');
assert.equal(selectedDisplay, 'Vector', 'seleção mantém pills do harness sincronizados');
assert.equal(firstPreview.latest('app.preferences.apexDisplayMode'), 'Vector');
assert.equal(storageValues.get(displayStorageKey), 'Vector');
assert.equal(storageWrites, 1, 'quatro aliases persistem na mesma chave sem quatro escritas');
assert.equal(storageValues.get('haval.OtherTheme.apexDisplayMode'), 'Other');
assert.equal(storageValues.has('display'), false);
assert.equal(firstPreview.frame.__TEST_HARNESS.stateManager.get('display'), undefined);
firstPreview.cleanup();
assert.equal(storageValues.get(displayStorageKey), 'Vector', 'cleanup preserva preferência persistida');
assert.throws(() => preferenceBridge.savePreference('apexDisplayMode', 'Contour'), /disposed/);

const reloadedPreview = installPreview({ onBootstrap(preview) {
  assert.equal(preview.__THEME_LAB_STATE_MANAGER__.get('apexDisplayMode'), 'Vector',
    'Vector deve estar inicializado antes de criar as pills');
  assert.equal(preview.__THEME_LAB_STATE_MANAGER__.get('apex_display_mode'), 'Vector');
} });
assert.equal(reloadedPreview.latest('apexDisplayMode'), 'Vector');
assert.equal(storageWrites, 1, 'inicialização não deve salvar default por cima da escolha');
for (const invalid of ['Unknown', '', null, undefined, 1, 'vector']) {
  reloadedPreview.frame.Android.savePreference('apex_display_mode', invalid);
  assert.equal(reloadedPreview.frame.Android.getPreference('apexDisplayMode', 'Vector'), 'Contour');
  assert.equal(reloadedPreview.latest('app.preferences.apexDisplayMode'), 'Contour');
}
reloadedPreview.cleanup();

storageValues.set(displayStorageKey, 'Unknown');
const invalidStored = installPreview({ onBootstrap(preview) {
  assert.equal(preview.__THEME_LAB_STATE_MANAGER__.get('apexDisplayMode'), 'Contour');
} });
assert.equal(invalidStored.frame.Android.getPreference('apexDisplayMode', 'Vector'), 'Contour');
invalidStored.cleanup();

const blockedStorage = {
  getItem() { throw new Error('blocked'); },
  setItem() { throw new Error('blocked'); },
};
const ephemeralPreview = installPreview({ localStorage: blockedStorage });
ephemeralPreview.frame.Android.savePreference('apexDisplayMode', 'Vector');
assert.equal(ephemeralPreview.frame.Android.getPreference('apex_display_mode', 'Contour'), 'Vector');
assert.equal(ephemeralPreview.latest('app.preferences.apexDisplayMode'), 'Vector');
ephemeralPreview.cleanup();

const foreignPreferences = Object.freeze({ getPreference: () => 'Vector', savePreference() { assert.fail('foreign save'); } });
const foreignPreview = installPreview({ android: { value: foreignPreferences, writable: false, configurable: false } });
assert.equal(foreignPreview.frame.Android, foreignPreferences);
assert.equal(foreignPreview.frame.__THEME_LAB_STATE_MANAGER__.get('apexDisplayMode'), 'Vector');
foreignPreview.cleanup();
assert.equal(foreignPreview.frame.Android, foreignPreferences);

const otherPreview = installPreview({ folder: 'OtherTheme' });
assert.ok(otherPreview.removed.has('testing-settings-harness'), 'outros temas injetados mantêm política original');
assert.equal(otherPreview.frame.__THEME_LAB_STATE_MANAGER__.get('apexDisplayMode'), undefined);
assert.equal(otherPreview.frame.Android, undefined);
otherPreview.cleanup();

console.log('Apex GT Lab: comandos/aliases, reset Trip B, lifecycle e Display persistente Contour/Vector válidos.');
