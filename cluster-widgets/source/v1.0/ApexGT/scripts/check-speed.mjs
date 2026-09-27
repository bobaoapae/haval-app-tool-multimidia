import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { renderSharedRuntimeScript } from './shared-runtime.mjs';

// Velocidade mostrada = a do HUD: fórmula compartilhada (carDerivations.getAdjustedSpeed) sobre a
// chave canônica, com as duas preferências globais do app por cima.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const shared = renderSharedRuntimeScript(root);
const source = fs.readFileSync(path.join(root, 'src/apex-speed.js'), 'utf8');

function create({ bridge, withShared = true } = {}) {
    const window = { Android: bridge };
    const context = vm.createContext({ window });
    if (withShared) vm.runInContext(shared, context, { filename: 'shared-runtime.js' });
    vm.runInContext(source, context, { filename: 'apex-speed.js' });
    return window.ApexSpeed.create();
}

let cases = 0;
function test(name, callback) { callback(); cases++; console.log(`PASS ${name}`); }

test('calibration matches the instrument cluster formula and floors like the app', () => {
    const s = create();
    assert.equal(s.display(72), 77);          // 72*1.07 - 72/180*0.02 = 77.03
    assert.equal(s.display('100'), 106);      // 107 - 0.011 = 106.99 -> floor 106, igual ao Kotlin toInt()
    assert.equal(s.display(0), 0);
    assert.equal(s.display(180), 192);
    assert.deepEqual([...s.keys], ['app.preferences.enableSpeedAdjustment', 'app.preferences.speedAdjustmentOffset']);
    assert.deepEqual([...s.preferenceKeys], [...s.keys]);
    assert.equal(s.isAdjustmentEnabled(), false);
});

test('missing or invalid raw speed stays missing, never a calibrated zero', () => {
    const s = create();
    for (const raw of [null, undefined, NaN, 'abc', '', {}]) assert.equal(s.display(raw), null, String(raw));
});

test('global offset only applies when the adjustment is enabled; both come through app.preferences', () => {
    const s = create();
    assert.equal(s.update('app.preferences.speedAdjustmentOffset', '10', false), true);
    assert.equal(s.display(100), 106, 'offset ignored while disabled');
    assert.equal(s.update('app.preferences.enableSpeedAdjustment', 'true', false), true);
    assert.equal(s.display(100), 117);        // 106.99 * 1.10 = 117.69 -> 117
    assert.equal(s.update('app.preferences.enableSpeedAdjustment', 'true', false), false, 'unchanged -> no re-render');
    assert.equal(s.update('app.preferences.speedAdjustmentOffset', 'abc', false), false, 'garbage keeps the value');
    assert.equal(s.update('app.preferences.enableSpeedAdjustment', 'maybe', false), false);
    assert.equal(s.offsetPercent(), 10);
    assert.equal(s.update('app.preferences.speedAdjustmentOffset', '-5', false), true);
    assert.equal(s.display(100), 101);        // 106.99 * 0.95 = 101.64 -> 101
});

test('legacy control aliases need allowAliases; unrelated keys are ignored', () => {
    const s = create();
    assert.equal(s.update('enableSpeedAdjustment', 'true', false), false);
    assert.equal(s.isAdjustmentEnabled(), false);
    assert.equal(s.update('enableSpeedAdjustment', 'true', true), true);
    assert.equal(s.update('speedAdjustmentOffset', '5', true), true);
    assert.equal(s.display(100), 112);        // 106.99 * 1.05 = 112.34 -> 112
    assert.equal(s.update('car.basic.vehicle_speed', 50, false), false);
});

test('initial preferences come from the theme-scoped getPreference with global fallback; bridge failures are harmless', () => {
    const on = create({ bridge: { getPreference: (key, fallback) => ({ enableSpeedAdjustment: 'true', speedAdjustmentOffset: '2.5' })[key] ?? fallback } });
    assert.equal(on.isAdjustmentEnabled(), true); assert.equal(on.offsetPercent(), 2.5);
    assert.equal(on.display(100), 109);       // 106.99 * 1.025 = 109.66 -> 109
    const broken = create({ bridge: { getPreference: () => { throw new Error('down'); } } });
    assert.equal(broken.display(72), 77);
    const none = create({ bridge: {} });
    assert.equal(none.display(72), 77);
});

test('without the shared runtime the raw speed passes through unchanged', () => {
    const s = create({ withShared: false });
    assert.equal(s.display(72), 72);
    assert.equal(s.display(null), null);
});

test('cleanup freezes preferences', () => {
    const s = create();
    s.cleanup();
    assert.equal(s.update('app.preferences.enableSpeedAdjustment', 'true', false), false);
    assert.equal(s.isAdjustmentEnabled(), false);
});

console.log(`Apex GT speed calibration: ${cases} behavioral cases passed.`);
