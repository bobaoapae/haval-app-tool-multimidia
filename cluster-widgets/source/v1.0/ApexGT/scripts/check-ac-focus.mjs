import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const menuSource = fs.readFileSync(new URL('../src/apex-menus.js', import.meta.url), 'utf8');
const helper = fs.readFileSync(new URL('../../shared/utils/clusterAcFocus.js', import.meta.url), 'utf8')
    .replace(/^export /gm, '');

function environment(initial = 'temp', { late = false, rejectSave = false } = {}) {
    let now = 1000;
    const calls = [], saves = [], order = [], listeners = new Map();
    const classes = () => ({ add() {}, remove() {}, toggle() {} });
    const node = () => ({ classList: classes(), style: {}, children: [], setAttribute() {},
        removeEventListener() {}, addEventListener() {} });
    const root = node();
    const mount = node();
    mount.querySelectorAll = query => query === '[id]'
        ? ['graph-bars', 'ac-fan-level'].map(id => ({ ...node(), id: 'apex-menu-' + id })) : [];
    Object.defineProperty(mount, 'innerHTML', { set() { order.push('mount'); } });
    const bridge = { getClusterAcFocus() { order.push('restore'); return initial; },
        saveClusterAcFocus(value) { saves.push(value); initial = value; return !rejectSave; },
        updateCarData(key, value) { calls.push([key, value]); },
        triggerSystemAction(action) { calls.push(['action', action]); } };
    const window = { Android: late ? undefined : bridge,
        requestAnimationFrame() { return 1; }, cancelAnimationFrame() {}, setTimeout() { return 2; }, clearTimeout() {},
        addEventListener(key, fn) { if (!listeners.has(key)) listeners.set(key, new Set()); listeners.get(key).add(fn); },
        removeEventListener(key, fn) { listeners.get(key)?.delete(fn); } };
    const context = vm.createContext({ window, console: { warn() {} }, Date: { now: () => now } });
    vm.runInContext(helper + '\nwindow.ApexShared = { createAcFocusPreference };', context);
    vm.runInContext(menuSource, context);
    const menus = window.ApexMenus.create({ root, mount });
    menus.onCardChanged(3);
    const key = key => { now += 100; menus.onKeyEvent(key); };
    const populate = () => {
        menus.update('car.hvac.driver_temperature', 22);
        menus.update('car.hvac.fan_speed', 3);
        menus.update('car.hvac.power_mode', 0);
    };
    const ready = () => { window.Android = { ...bridge, __themeLabMock: 'ApexGT' };
        listeners.get('cluster-ac-focus-bridge-ready')?.forEach(fn => fn()); };
    return { menus, calls, saves, order, populate, key, ready, listeners };
}

for (const focus of ['fan', 'temp']) {
    const e = environment(focus);
    assert.deepEqual(e.order.slice(0, 2), ['restore', 'mount'], 'restore precedes component mount');
    e.key('UP');
    assert.equal(e.calls.length, 0, 'unknown data never writes HVAC');
    e.populate();
    e.menus.onCardChanged(0); e.menus.onCardChanged(3);
    assert.equal(e.calls.length, 0, 'data/card change/restoration have no HVAC writes');
    e.key('UP');
    assert(e.calls.some(([key]) => key === (focus === 'temp' ? 'car.hvac.driver_temperature' : 'car.hvac.fan_speed')));
    assert(!e.calls.some(([key]) => key === (focus === 'temp' ? 'car.hvac.fan_speed' : 'car.hvac.driver_temperature')));
    e.calls.length = 0;
    e.key('ENTER');
    assert.deepEqual(e.saves, [focus === 'temp' ? 'fan' : 'temp']);
    assert.equal(e.calls.length, 0, 'selection only persists focus');
    e.menus.cleanup();
    assert.equal(e.calls.length, 0, 'cleanup sends no HVAC');
    assert.equal(e.listeners.get('cluster-ac-focus-bridge-ready').size, 0);
}
{
    const e = environment('temp', { late: true }); e.populate(); e.ready(); e.key('UP');
    assert.equal(e.calls[0][0], 'car.hvac.driver_temperature', 'late mock rehydrates focus before interaction');
    assert.equal(e.saves.length, 0);
    assert.equal(e.listeners.get('cluster-ac-focus-bridge-ready').size, 0);
}
{
    const e = environment('fan', { late: true }); e.populate(); e.key('ENTER'); e.ready(); e.key('UP');
    assert.equal(e.calls[0][0], 'car.hvac.driver_temperature', 'late mock never overrides intervening user selection');
}
{
    const e = environment('temp', { rejectSave: true }); e.populate(); e.key('ENTER'); e.key('UP');
    assert(e.calls.some(([key]) => key === 'car.hvac.fan_speed'), 'failed save keeps in-memory selection');
}
console.log('PASS Apex AC focus: native pre-mount restore, both first-arrow targets, unknown/off data, navigation, ENTER, rejected save, guarded late mock, cleanup.');
