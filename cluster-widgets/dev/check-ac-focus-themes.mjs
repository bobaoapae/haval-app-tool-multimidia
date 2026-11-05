import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const read = path => fs.readFileSync(new URL('../' + path, import.meta.url), 'utf8');
const helper = await import('../source/v1.0/shared/utils/clusterAcFocus.js');
const minimalist = read('source/v1.0/minimalist/src/core/main.js');
const component = read('source/v1.0/minimalist/src/core/components/aircon/mainAcControl.js');
const handler = minimalist.slice(minimalist.indexOf('function handleAirconKey('), minimalist.indexOf('function handleSteeringWheelKey('));
let checks = 0;
for (const power of [0, 1, undefined, 'unknown']) {
    const state = { focusArea: 'temp', power, temp: 22, fan: 3 };
    const hvac = []; const saved = [];
    const context = { get: key => state[key], setState: (key, value) => state[key] = value,
        androidUpdateCarData: (...args) => hvac.push(args), acFocusPreference: { save: value => saved.push(value) },
        acFocusTouched: false, window: {}, console };
    vm.createContext(context); vm.runInContext(handler, context);
    vm.runInContext("handleAirconKey('ENTER')", context);
    assert.equal(state.focusArea, 'fan'); assert.deepEqual(saved, ['fan']); assert.equal(hvac.length, 0);
    vm.runInContext("handleAirconKey('ENTER')", context);
    assert.equal(state.focusArea, 'temp'); assert.equal(hvac.length, 0);
    vm.runInContext("handleAirconKey('UP')", context);
    assert.deepEqual(hvac, [['car.hvac.driver_temperature', '22.5']]);
    checks++;
}
// Actual component power and mount paths must preserve both saved selections.
const powerBlock = component.slice(component.indexOf('    const applyPowerUi ='), component.indexOf("    unsubs.push(subscribe('power'"));
const mountBlock = component.slice(component.indexOf('    const onMount ='), component.indexOf('    const cleanup ='));
for (const focus of ['fan', 'temp']) {
    const state = { focusArea: focus };
    const element = { textContent: '', classList: { toggle() {} } };
    vm.runInNewContext(powerBlock + mountBlock + '\napplyPowerUi(0); applyPowerUi(undefined); onMount();', {
        powerPill: element, panel: element, stateManager: { get: key => state[key] },
        setState: (key, value) => state[key] = value
    });
    assert.equal(state.focusArea, focus); checks++;
}
// Startup/late-development-mock ordering, intervening selection and cleanup.
const init = minimalist.slice(minimalist.indexOf('const acFocusPreference ='), minimalist.indexOf('initializeConstants();'));
for (const touched of [false, true]) {
    const listeners = new Map(); const state = {}; let saves = 0;
    const window = { addEventListener: (event, fn) => listeners.set(event, fn),
        removeEventListener: event => listeners.delete(event) };
    const context = { window, setState: (key, value) => state[key] = value,
        createAcFocusPreference: getter => helper.createAcFocusPreference(getter, () => {}) };
    vm.createContext(context); vm.runInContext(init, context);
    assert.equal(state.focusArea, 'fan');
    if (touched) { vm.runInContext('acFocusTouched = true', context); state.focusArea = 'fan'; }
    window.Android = { __themeLabMock: 'shared', getClusterAcFocus: () => 'temp',
        saveClusterAcFocus: () => { saves++; return true; } };
    listeners.get('cluster-ac-focus-bridge-ready')();
    assert.equal(state.focusArea, touched ? 'fan' : 'temp');
    assert.equal(listeners.size, 0); assert.equal(saves, 0); checks++;
}
// Regression: exercise the REAL arrow handler before the mock arrives, not a manual flag.
for (const key of ['UP', 'DOWN']) {
    const listeners = new Map(); const state = { fan: 3, temp: 22, power: 1 }; const hvac = [];
    const window = { addEventListener: (event, fn) => listeners.set(event, fn),
        removeEventListener: event => listeners.delete(event) };
    const context = { window, console, get: name => state[name],
        setState: (name, value) => state[name] = value,
        androidUpdateCarData: (...args) => hvac.push(args),
        createAcFocusPreference: getter => helper.createAcFocusPreference(getter, () => {}) };
    vm.createContext(context); vm.runInContext(init + '\n' + handler, context);
    vm.runInContext(`handleAirconKey('${key}')`, context);
    assert.equal(hvac[0][0], 'car.hvac.fan_speed');
    window.Android = { __themeLabMock: 'shared', getClusterAcFocus: () => 'temp',
        saveClusterAcFocus: () => { throw Error('rehydration must not save'); } };
    listeners.get('cluster-ac-focus-bridge-ready')();
    assert.equal(state.focusArea, 'fan');
    vm.runInContext(`handleAirconKey('${key}')`, context);
    assert.equal(hvac[1][0], 'car.hvac.fan_speed');
    assert.equal(listeners.size, 0); checks++;
}
// Regression: run restoration through the REAL seed/snapshot and live telemetry
// paths before the first arrow. Component-only power tests miss this transition.
const telemetry = minimalist.slice(minimalist.indexOf('function handleSettingsTelemetry('), minimalist.indexOf('async function initMinimalistBridge('));
const settings = minimalist.slice(minimalist.indexOf('const SETTINGS_KEYS_TO_SUBSCRIBE ='), minimalist.indexOf('const GRAPH_KEYS_TO_SUBSCRIBE ='));
const seedStart = minimalist.indexOf('    SETTINGS_KEYS_TO_SUBSCRIBE.forEach((key) => {');
assert(seedStart >= 0, 'initial snapshot seed must remain covered');
const seed = minimalist.slice(seedStart, minimalist.indexOf('    });', seedStart) + '    });'.length);
const subscriptionStart = minimalist.indexOf('    bridge.subscribe(\n        [...SETTINGS_KEYS_TO_SUBSCRIBE');
assert(subscriptionStart >= 0, 'live telemetry subscription must remain covered');
const subscription = minimalist.slice(subscriptionStart, minimalist.indexOf('    );', subscriptionStart) + '    );'.length);
function createSnapshotHarness(savedFocus, power) {
    const state = { fan: 3, temp: 22 };
    const hvac = []; const saved = []; let stored = savedFocus; let listener;
    const KEYS = new Proxy({}, { get: (_, key) => String(key) });
    const snapshot = { HVAC_POWER: power, HVAC_FAN_SPEED: 3, HVAC_DRIVER_TEMP: 22 };
    const context = { console, KEYS, GRAPH_KEYS_TO_SUBSCRIBE: [], GAUGE_KEYS_TO_SUBSCRIBE: [],
        window: { Android: { getClusterAcFocus: () => stored, saveClusterAcFocus: value => {
            stored = value; saved.push(value); return true;
        } }, addEventListener() {}, removeEventListener() {} },
        get: key => state[key], setState: (key, value) => state[key] = value,
        readCarDataRaw: key => snapshot[key] == null ? '' : String(snapshot[key]),
        handleGraphTelemetry: () => false,
        bridge: { subscribe: (_, callback) => listener = callback },
        androidUpdateCarData: (...args) => hvac.push(args),
        createAcFocusPreference: getter => helper.createAcFocusPreference(getter, () => {}) };
    vm.createContext(context);
    vm.runInContext(init + '\n' + settings + '\n' + telemetry + '\n' + handler + '\n' + subscription, context);
    return { context, state, hvac, saved, stored: () => stored,
        push: value => listener(KEYS.HVAC_POWER, value) };
}
for (const route of ['seed', 'push']) {
    for (const focus of ['temp', 'fan']) {
        for (const power of [0, '0', 1, '1']) {
            for (const key of ['UP', 'DOWN']) {
                const h = createSnapshotHarness(focus, power);
                assert.equal(h.state.focusArea, focus, 'restore before telemetry');
                if (route === 'seed') vm.runInContext(seed, h.context);
                else h.push(power);
                assert.equal(h.state.focusArea, focus, `${route} power=${power} must preserve ${focus}`);
                assert.equal(h.stored(), focus); assert.deepEqual(h.saved, []);
                assert.deepEqual(h.hvac, [], 'snapshot must not issue HVAC commands');
                vm.runInContext(`handleAirconKey('${key}')`, h.context);
                assert.deepEqual(h.hvac, [[focus === 'temp' ? 'car.hvac.driver_temperature' : 'car.hvac.fan_speed',
                    focus === 'temp' ? (key === 'UP' ? '22.5' : '21.5') : (key === 'UP' ? '4' : '2')]]);
                assert.equal(h.stored(), focus); assert.deepEqual(h.saved, []); checks++;
            }
        }
    }
}
// An explicit change may be followed by repeated off snapshots/pushes; neither
// must undo the choice or cause another persistence write.
for (const focus of ['temp', 'fan']) {
    for (const key of ['UP', 'DOWN']) {
        const h = createSnapshotHarness(focus, 0);
        vm.runInContext(seed, h.context);
        vm.runInContext("handleAirconKey('ENTER')", h.context);
        const selected = focus === 'temp' ? 'fan' : 'temp';
        h.push(0); h.push('0'); h.push(undefined); h.push('unknown');
        vm.runInContext(seed, h.context);
        assert.equal(h.state.focusArea, selected); assert.equal(h.stored(), selected);
        assert.deepEqual(h.saved, [selected]); assert.deepEqual(h.hvac, []);
        vm.runInContext(`handleAirconKey('${key}')`, h.context);
        assert.equal(h.hvac[0][0], selected === 'temp' ? 'car.hvac.driver_temperature' : 'car.hvac.fan_speed');
        assert.deepEqual(h.saved, [selected]); checks++;
    }
}
assert(minimalist.includes("window.removeEventListener('cluster-ac-focus-bridge-ready', rehydrateMockAcFocus);"));
for (const [name, version] of [['default', '1.4.49'], ['minimalist', '1.0.9'], ['ApexGT', '1.0.18']]) {
    const manifest = read(`source/v1.0/${name}/theme.xml`);
    assert(manifest.includes(`<version>${version}</version>`));
    assert(manifest.includes('<minBridgeVersion>1.0.2</minBridgeVersion>'));
}
console.log(`Global AC focus theme integration: ${checks} scenarios passed; three manifests verified.`);
