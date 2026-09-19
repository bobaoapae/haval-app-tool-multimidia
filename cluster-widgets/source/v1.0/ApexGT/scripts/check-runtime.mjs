import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/apex-gt.js', import.meta.url), 'utf8');
const K = {
    speed: 'car.basic.vehicle_speed', rpm: 'car.basic.engine_speed', odo: 'car.basic.total_odometer',
    gear: 'car.basic.gear_status', drive: 'car.drive_setting.drive_mode', prop: 'car.ev_setting.power_model_config',
    inside: 'car.basic.inside_temp', outside: 'car.basic.outside_temp', unit: 'car.configure.default_temp_unit',
    fuel: 'car.basic.remain_fuel_percentage', battery: 'car.ev_info.cur_battery_power_percentage',
    fuelRange: 'car.ev_info.fuel_mode_remain_odometer', batteryRange: 'car.ev_info.electric_mode_remain_odometer',
    trip: 'car.basic.cur_journey_odometer', consumption: 'car.basic.cur_journey_avg_fuel_consume',
    voltage: 'car.ev_info.power_battery_voltage', current: 'car.ev_info.cur_charge_current',
    projection: 'carPlayInDash', preparing: 'projectionPreparingD3'
};

function environment({ snapshot = {}, available = Object.values(K), initialPush, bridge = true, badAvailable = false, menus = null, display = null } = {}) {
    let nextId = 1;
    let writes = 0;
    const nodes = new Map();
    const frames = new Map();
    const timeouts = new Map();
    const intervals = new Map();
    const listeners = new Map();
    const events = [];
    const subscriptions = [];
    const unsubscriptions = [];
    const snapshotReads = [];
    let heartbeats = 0;

    function element(id) {
        if (!nodes.has(id)) {
            let content = '';
            const classes = new Set();
            nodes.set(id, {
                get textContent() { return content; },
                set textContent(value) { writes++; content = value; },
                style: {}, hidden: false,
                classList: {
                    add: (name) => classes.add(name), remove: (name) => classes.delete(name),
                    contains: (name) => classes.has(name),
                    toggle: (name, enabled) => { if (enabled) classes.add(name); else classes.delete(name); }
                }
            });
        }
        return nodes.get(id);
    }
    const window = {
        ApexMenus: menus ? { create: () => menus } : undefined,
        ApexDisplay: display ? { create: () => display } : undefined,
        requestAnimationFrame(callback) { const id = nextId++; frames.set(id, callback); return id; },
        cancelAnimationFrame(id) { frames.delete(id); },
        setTimeout(callback, delay) { const id = nextId++; timeouts.set(id, { callback, delay }); return id; },
        clearTimeout(id) { timeouts.delete(id); },
        setInterval(callback, delay) { const id = nextId++; intervals.set(id, { callback, delay }); return id; },
        clearInterval(id) { intervals.delete(id); },
        addEventListener(name, callback) { if (!listeners.has(name)) listeners.set(name, new Set()); listeners.get(name).add(callback); },
        removeEventListener(name, callback) { listeners.get(name)?.delete(callback); },
        dispatchEvent(event) { events.push(event); listeners.get(event.type)?.forEach((callback) => callback(event)); }
    };
    if (bridge) window.Android = {
        getAvailableKeys() { return badAvailable ? '{invalid' : JSON.stringify(available); },
        subscribe(json) { subscriptions.push(JSON.parse(json)); if (initialPush) initialPush(window); },
        unsubscribe(json) { unsubscriptions.push(JSON.parse(json)); },
        getCarData(key) { snapshotReads.push(key); return Object.hasOwn(snapshot, key) ? snapshot[key] : ''; },
        heartbeat() { heartbeats++; }
    };
    class FixedDate extends Date {
        constructor() { super(2026, 8, 19, 14, 38, 17, 250); }
    }
    vm.runInNewContext(source, {
        window, document: { getElementById: element }, Date: FixedDate,
        CustomEvent: class { constructor(type, options = {}) { this.type = type; this.detail = options.detail; } }
    }, { filename: 'apex-gt.js' });
    return {
        window, nodes, frames, timeouts, intervals, listeners, events, subscriptions, unsubscriptions, snapshotReads,
        root: element('apex-gt'), get writes() { return writes; }, get heartbeats() { return heartbeats; },
        text: (id) => element(id).textContent,
        feed(values) { Object.entries(values).forEach(([key, value]) => window.onDataChanged(key, value)); },
        flush() { const pending = [...frames.values()]; frames.clear(); pending.forEach((callback) => callback(16)); },
        gaugeEvents: () => events.filter((event) => event.type === 'apex-telemetry')
    };
}

let cases = 0;
function test(name, callback) {
    callback();
    cases++;
    console.log(`PASS ${name}`);
}

test('missing snapshot never invents zero; clock uses local HH:mm', () => {
    const e = environment(); e.flush();
    ['speed-value', 'power-value', 'regen-value', 'gear-value', 'drive-mode', 'propulsion-mode',
        'outside-temp', 'inside-temp', 'odometer', 'engine-rpm', 'fuel-percent', 'fuel-liters',
        'fuel-range', 'battery-percent', 'battery-range', 'trip-distance', 'average-consumption', 'total-range']
        .forEach((id) => assert.equal(e.text(id), '--', id));
    assert.equal(e.text('clock'), '14:38');
    assert.equal(e.nodes.get('rpm-readout').hidden, true);
    assert.equal(e.timeouts.size, 1);
    assert.equal([...e.timeouts.values()][0].delay, 42750);
    assert.deepEqual(JSON.parse(JSON.stringify(e.gaugeEvents()[0].detail)), { speed: null, power: null });
});

test('initial subscription snapshot populates supported keys and derived values in one frame', () => {
    const e = environment({ snapshot: {
        [K.speed]: '72', [K.voltage]: '400', [K.current]: '240', [K.fuel]: '80', [K.battery]: '64',
        [K.fuelRange]: '455', [K.batteryRange]: '84', [K.trip]: '1656.7', [K.consumption]: '5.9'
    } });
    assert.equal(e.frames.size, 1); e.flush();
    assert.equal(e.text('speed-value'), '72');
    assert.equal(e.text('power-value'), '96'); assert.equal(e.text('regen-value'), '0');
    assert.equal(e.text('fuel-percent'), '80%'); assert.equal(e.text('fuel-liters'), '44.0');
    assert.equal(e.text('battery-percent'), '64%'); assert.equal(e.text('total-range'), '539');
    assert.equal(e.text('trip-distance'), '1656.7'); assert.equal(e.text('average-consumption'), '16.9');
    assert.equal(e.text('consumption-unit'), 'km/L');
    assert.equal(e.nodes.get('fuel-fill').style.transform, 'scaleX(0.8)');
    assert.equal(e.subscriptions.length, 1); assert.equal(e.subscriptions[0].length, 19);
    assert.equal(e.gaugeEvents().length, 1);
});

test('all genuine zero readings remain distinct from missing', () => {
    const e = environment();
    e.feed(Object.fromEntries(Object.values(K).filter((key) => !['carPlayInDash', 'projectionPreparingD3'].includes(key)).map((key) => [key, 0])));
    e.flush();
    ['speed-value', 'power-value', 'regen-value', 'engine-rpm', 'odometer', 'fuel-range', 'battery-range', 'total-range']
        .forEach((id) => assert.equal(e.text(id), '0', id));
    assert.equal(e.text('fuel-liters'), '0.0'); assert.equal(e.text('fuel-percent'), '0%');
    assert.equal(e.text('average-consumption'), '0.0'); assert.equal(e.text('consumption-unit'), 'L/100 km');
    assert.equal(e.text('gear-value'), 'N'); assert.equal(e.text('inside-temp'), '0°C');
    assert.equal(e.nodes.get('rpm-readout').hidden, true);
});

test('power is signed V*A/1000 and drops stale values if either operand is missing', () => {
    const e = environment(); e.feed({ [K.voltage]: 400, [K.current]: -85 }); e.flush();
    assert.equal(e.text('power-value'), '0'); assert.equal(e.text('regen-value'), '-34');
    assert.equal(e.root.classList.contains('is-regenerating'), true);
    e.feed({ [K.current]: null }); e.flush();
    assert.equal(e.text('power-value'), '--'); assert.equal(e.text('regen-value'), '--');
    assert.equal(e.root.classList.contains('is-regenerating'), false);
    e.feed({ [K.current]: 0 }); e.flush(); assert.equal(e.text('power-value'), '0');
    e.feed({ [K.voltage]: undefined }); e.flush(); assert.equal(e.text('power-value'), '--');
    e.window.control('evPowerKw', 99); e.flush(); assert.equal(e.text('power-value'), '--');
});

test('range sum requires both ranges; invalid percentages and negative distance are absent', () => {
    const e = environment(); e.feed({ [K.fuelRange]: 455 }); e.flush();
    assert.equal(e.text('total-range'), '--');
    e.feed({ [K.batteryRange]: 0, [K.fuel]: 101, [K.battery]: -1, [K.trip]: -8 }); e.flush();
    assert.equal(e.text('total-range'), '455'); assert.equal(e.text('fuel-liters'), '--');
    assert.equal(e.text('battery-percent'), '--'); assert.equal(e.text('trip-distance'), '--');
    e.feed({ [K.fuelRange]: '' }); e.flush(); assert.equal(e.text('total-range'), '--');
});

test('malformed and nonfinite values cannot turn into zero or survive stale', () => {
    const e = environment();
    for (const invalid of [null, undefined, '', ' ', '--', 'null', NaN, Infinity, '1km', true, [], {}, '0x10']) {
        e.feed({ [K.speed]: 34 }); e.flush();
        e.feed({ [K.speed]: invalid }); e.flush();
        assert.equal(e.text('speed-value'), '--', String(invalid));
    }
    e.feed({ [K.consumption]: 1e-310, [K.voltage]: 1e308, [K.current]: 1e308 }); e.flush();
    assert.equal(e.text('average-consumption'), '--'); assert.equal(e.text('power-value'), '--');
});

test('canonical gear, drive and propulsion enums stay independent; unknown is absent', () => {
    const e = environment();
    for (const [input, expected] of [[0, 'N'], [1, 'N'], [2, 'D'], [3, 'P'], [4, 'R'], [99, '--']]) {
        e.feed({ [K.gear]: input }); e.flush(); assert.equal(e.text('gear-value'), expected);
    }
    for (const [input, expected] of [[0, 'NORMAL'], [1, 'SPORT'], [2, 'ECO'], [3, 'NEVE'], [4, 'AREIA'], [5, 'LAMA'], [11, 'AWD'], [99, '--']]) {
        e.feed({ [K.drive]: input }); e.flush(); assert.equal(e.text('drive-mode'), expected);
    }
    for (const [input, expected] of [[0, 'HEV'], [1, 'EVP'], [3, 'EV'], [2, '--']]) {
        e.feed({ [K.prop]: input }); e.flush(); assert.equal(e.text('propulsion-mode'), expected);
    }
});

test('temperature honors configured label without speculative conversion; RPM clears at zero', () => {
    const e = environment(); e.feed({ [K.inside]: 22, [K.outside]: -3, [K.unit]: 1, [K.rpm]: 1680 }); e.flush();
    assert.equal(e.text('inside-temp'), '22°F'); assert.equal(e.text('outside-temp'), '-3°F');
    assert.equal(e.nodes.get('rpm-readout').hidden, false);
    e.feed({ [K.unit]: 0, [K.rpm]: 0 }); e.flush();
    assert.equal(e.text('inside-temp'), '22°C'); assert.equal(e.text('engine-rpm'), '0');
    assert.equal(e.nodes.get('rpm-readout').hidden, true);
});

test('canonical readings retain authority over native formatted or adjusted legacy aliases', () => {
    const e = environment({ bridge: false });
    e.feed({ [K.prop]: 0, [K.speed]: 72 });
    e.window.control('evMode', 'HEV Inteligente');
    e.window.control('carSpeed', 75); e.flush();
    assert.equal(e.text('propulsion-mode'), 'HEV');
    assert.equal(e.text('speed-value'), '72');
    e.window.control('evMode', 'HEV Prioridade 50%'); e.flush();
    assert.equal(e.text('propulsion-mode'), 'HEV');
    e.feed({ [K.prop]: null, [K.speed]: null });
    e.window.control('evMode', 'HEV Inteligente');
    e.window.control('carSpeed', 75); e.flush();
    assert.equal(e.text('propulsion-mode'), '--');
    assert.equal(e.text('speed-value'), '--');
    e.feed({ [K.prop]: 3, [K.speed]: 18 }); e.flush();
    assert.equal(e.text('propulsion-mode'), 'EV');
    assert.equal(e.text('speed-value'), '18');
});

test('OEM temperature sentinels are missing while genuine negative temperatures remain visible', () => {
    const e = environment({ snapshot: { [K.inside]: 255, [K.outside]: -1 } }); e.flush();
    assert.equal(e.text('inside-temp'), '--'); assert.equal(e.text('outside-temp'), '--');
    e.feed({ [K.inside]: '-1', [K.outside]: '255' }); e.flush();
    assert.equal(e.text('inside-temp'), '--'); assert.equal(e.text('outside-temp'), '--');
    e.feed({ [K.inside]: -5, [K.outside]: -3 }); e.flush();
    assert.equal(e.text('inside-temp'), '-5°C'); assert.equal(e.text('outside-temp'), '-3°C');
    e.feed({ [K.inside]: 255, [K.outside]: -1 });
    e.window.control('inside_temp', '23'); e.window.control('outside_temp', '12'); e.flush();
    assert.equal(e.text('inside-temp'), '--'); assert.equal(e.text('outside-temp'), '--');
});

test('legacy HEV submode labels remain a usable fallback before canonical data arrives', () => {
    const e = environment({ bridge: false });
    e.window.control('evMode', 'HEV Inteligente'); e.flush();
    assert.equal(e.text('propulsion-mode'), 'HEV');
    e.window.control('evMode', 'HEV Prioridade 50%'); e.flush();
    assert.equal(e.text('propulsion-mode'), 'HEV');
    e.window.control('evMode', 'unknown'); e.flush();
    assert.equal(e.text('propulsion-mode'), '--');
    e.window.control('carSpeed', 75); e.flush();
    assert.equal(e.text('speed-value'), '75');
});

test('repeated telemetry schedules no frame and causes no DOM text writes or gauge event', () => {
    const e = environment(); const values = { [K.speed]: 72, [K.voltage]: 400, [K.current]: 240 };
    e.feed(values); e.flush(); const before = e.writes; const events = e.gaugeEvents().length;
    for (let index = 0; index < 100; index++) e.feed(values);
    assert.equal(e.frames.size, 0); assert.equal(e.writes, before); assert.equal(e.gaugeEvents().length, events);
    e.feed({ [K.fuel]: 50 }); e.flush(); assert.equal(e.gaugeEvents().length, events);
});

test('menus share subscriptions/snapshot and navigation hooks with exact cleanup', () => {
    const key = 'car.hvac.fan_speed';
    const deliveries = [], cards = [], pressed = [];
    let cleanups = 0;
    const menus = { keys: [K.speed, key], update: (...args) => deliveries.push(args),
        onCardChanged: value => cards.push(value), onKeyEvent: value => pressed.push(value),
        cleanup: () => { cleanups++; } };
    const e = environment({ menus, available: [K.speed, key], snapshot: { [key]: 2 },
        initialPush: window => window.onDataChanged(key, 4) });
    e.flush();
    assert.deepEqual(e.subscriptions, [[K.speed, key]]);
    assert.ok(!e.snapshotReads.includes(key), 'initial HVAC push wins stale snapshot');
    assert.deepEqual(deliveries.find(([k]) => k === key), [key, 4, false]);
    e.window.onCardChanged(3); e.window.control('cardId', 1); e.window.onDataChanged('cardId', 1);
    assert.deepEqual(cards, [3], 'only canonical hook changes cards');
    e.window.onKeyEvent('ENTER'); assert.deepEqual(pressed, ['ENTER']);
    e.window.cleanup(); e.window.cleanup();
    assert.equal(cleanups, 1); assert.deepEqual(e.unsubscriptions, e.subscriptions);
    e.window.onKeyEvent('UP'); e.window.onCardChanged(0);
    assert.deepEqual(pressed, ['ENTER']); assert.deepEqual(cards, [3]);
});

test('projection preparing/active flags update without vehicle writes or card changes', () => {
    const e = environment(); e.feed({ [K.preparing]: 'true' }); e.flush();
    assert.equal(e.root.classList.contains('projection-preparing'), true);
    e.feed({ [K.preparing]: false, [K.projection]: true }); e.flush();
    assert.equal(e.root.classList.contains('projection-active'), true);
    e.feed({ [K.projection]: 'false' }); e.flush();
    assert.equal(e.root.classList.contains('projection-active'), false);
});

test('theme preference subscription is explicit and never reads the vehicle snapshot channel', () => {
    const preference = 'app.preferences.apexDisplayMode';
    const deliveries = [];
    let cleanups = 0;
    const display = { keys: [preference], update: (...args) => deliveries.push(args), cleanup: () => { cleanups++; } };
    const e = environment({ display, available: [K.speed], snapshot: { [preference]: null },
        initialPush: window => window.onDataChanged(preference, 'Vector') });
    assert.deepEqual(e.subscriptions, [[K.speed, preference]]);
    assert.ok(!e.snapshotReads.includes(preference));
    assert.deepEqual(deliveries.find(([key]) => key === preference), [preference, 'Vector', false]);
    e.window.cleanup(); e.window.cleanup(); assert.equal(cleanups, 1);
    assert.deepEqual(e.unsubscriptions, e.subscriptions);
});

test('subscribe filters available keys and synchronous initial push wins over snapshot', () => {
    const e = environment({ available: [K.speed, 'not.a.key'], snapshot: { [K.speed]: 12 }, initialPush: (window) => window.onDataChanged(K.speed, 42) }); e.flush();
    assert.deepEqual(e.subscriptions, [[K.speed]]); assert.equal(e.text('speed-value'), '42');
    assert.equal(e.snapshotReads.length, 0);
    const invalid = environment({ badAvailable: true }); invalid.flush(); assert.equal(invalid.subscriptions.length, 0);
    const offline = environment({ bridge: false }); offline.flush(); assert.equal(offline.text('speed-value'), '--');
});

test('cleanup cancels pending work, exact subscriptions, timers and pagehide listener once', () => {
    const e = environment(); e.flush();
    assert.equal(e.intervals.size, 1); assert.equal([...e.intervals.values()][0].delay, 2000);
    [...e.intervals.values()][0].callback(); assert.equal(e.heartbeats, 1);
    e.feed({ [K.speed]: 88 }); assert.equal(e.frames.size, 1);
    e.window.cleanup(); e.window.cleanup();
    assert.equal(e.frames.size, 0); assert.equal(e.timeouts.size, 0); assert.equal(e.intervals.size, 0);
    assert.deepEqual(e.unsubscriptions, e.subscriptions);
    assert.equal(e.listeners.get('pagehide').size, 0);
    assert.equal(e.events.filter((event) => event.type === 'apex-cleanup').length, 1);
    const before = e.writes; e.feed({ [K.speed]: 120 }); e.window.onKeyEvent('ENTER'); e.window.onCardChanged(3); e.flush();
    assert.equal(e.writes, before); assert.equal(e.frames.size, 0);
});

console.log(`Apex GT runtime: ${cases} behavioral cases passed.`);
