import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source = fs.readFileSync(new URL('../../shared/utils/clusterAcFocus.js', import.meta.url), 'utf8');
const { createAcFocusPreference } = await import(`data:text/javascript,${encodeURIComponent(source)}`);
const quiet = () => {};

test('restore accepts only fan/temp and never writes', () => {
    let writes = 0;
    for (const value of ['fan', 'temp', '', null, 'TEMPERATURE', 1]) {
        const prefs = createAcFocusPreference(() => ({ getClusterAcFocus: () => value,
            saveClusterAcFocus: () => { writes++; } }), quiet);
        assert.equal(prefs.restore(), value === 'temp' ? 'temp' : 'fan');
    }
    assert.equal(writes, 0);
});
test('missing, partial, throwing bridge fall back without writing', () => {
    for (const bridge of [null, {}, { getClusterAcFocus: () => 'temp' },
        { getClusterAcFocus: () => { throw Error(); }, saveClusterAcFocus() {} }]) {
        assert.equal(createAcFocusPreference(() => bridge, quiet).restore(), 'fan');
    }
    assert.equal(createAcFocusPreference(() => { throw Error(); }, quiet).restore(), 'fan');
});
test('save uses strict true, rejects invalid focus, and survives failures', () => {
    let writes = 0;
    for (const result of [true, false, 1, 'true', null, undefined]) {
        const prefs = createAcFocusPreference(() => ({ getClusterAcFocus: () => 'fan',
            saveClusterAcFocus: () => { writes++; return result; } }), quiet);
        assert.equal(prefs.save('temp'), result === true);
        assert.equal(prefs.save('invalid'), false);
    }
    assert.equal(writes, 6);
    assert.equal(createAcFocusPreference(() => ({ getClusterAcFocus() {},
        saveClusterAcFocus() { throw Error(); } }), quiet).save('fan'), false);
});
test('actual default ENTER path toggles and persists without HVAC writes', () => {
    const main = fs.readFileSync(new URL('../src/core/main.js', import.meta.url), 'utf8');
    const block = main.match(/if \(keyName === 'ENTER'\) \{\s*const nextFocus[\s\S]*?\n        \} else if/)[0]
        .replace(/ else if$/, '');
    const writes = [];
    const state = { focus: 'fan' };
    for (const expected of ['temp', 'fan']) {
        vm.runInNewContext(block, { keyName: 'ENTER', focusArea: state.focus,
            setState: (key, value) => { assert.equal(key, 'focusArea'); state.focus = value; },
            acFocusPreference: { save: value => { writes.push(value); return false; } } });
        assert.equal(state.focus, expected); // failed save retains selection
    }
    assert.deepEqual(writes, ['temp', 'fan']);
    const focusHandler = main.slice(main.indexOf('window.focus ='), main.indexOf('window.control ='));
    assert(!focusHandler.includes('acFocusPreference.save'));
    assert(!focusHandler.includes('updateCarData'));
    assert(main.includes("setState('focusArea', acFocusPreference.restore());"));
    assert(main.indexOf("setState('focusArea', acFocusPreference.restore());") < main.indexOf('initializeConstants();'));
    assert.equal(main.match(/acFocusPreference\.restore\(\)/g).length, 1);
});
test('browser mock persists both values and retains memory on storage exceptions', async () => {
    const mockSource = fs.readFileSync(new URL('../../shared/runtime/clusterAcFocusMock.js', import.meta.url), 'utf8');
    const { createClusterAcFocusMock } = await import(`data:text/javascript,${encodeURIComponent(mockSource)}`);
    const values = new Map(); let denied = false;
    const storage = { getItem: key => values.get(key) ?? null,
        setItem: (key, value) => values.set(key, value) };
    const getStorage = () => { if (denied) throw Error(); return storage; };
    const bridge = createClusterAcFocusMock(getStorage);
    assert.equal(bridge.getClusterAcFocus(), 'fan');
    assert.equal(bridge.saveClusterAcFocus('temp'), true);
    assert.equal(values.get('haval_mock_cluster_ac_focus'), 'temp');
    assert.equal(createClusterAcFocusMock(getStorage).getClusterAcFocus(), 'temp');
    values.set('haval_mock_cluster_ac_focus', 'fan');
    assert.equal(bridge.getClusterAcFocus(), 'fan'); // re-read disk every time
    denied = true;
    assert.equal(bridge.saveClusterAcFocus('temp'), true);
    assert.equal(bridge.getClusterAcFocus(), 'temp');
    assert.equal(bridge.saveClusterAcFocus(null), false);
    assert.equal(bridge.getClusterAcFocus(), 'temp');
});

test('legacy focus and leave/re-entry retain memory without preference or HVAC writes', () => {
    const main = fs.readFileSync(new URL('../src/core/main.js', import.meta.url), 'utf8');
    const handler = main.slice(main.indexOf('window.focus ='), main.indexOf('window.control ='));
    const state = { screen: 'aircon', focusArea: 'temp' };
    const window = {};
    vm.runInNewContext(handler, { window, get: key => state[key],
        setState: (key, value) => { state[key] = value; },
        logger: { enter() {}, leave() {} }, console });
    state.screen = 'main_menu'; window.focus('option_7');
    assert.equal(state.focusArea, 'temp');
    state.screen = 'aircon';
    assert.equal(state.focusArea, 'temp');
    window.focus('fan'); assert.equal(state.focusArea, 'fan');
    // No bridge is provided: restore/navigation/focus handlers cannot persist or issue HVAC calls.
});
