'use strict';

// Pure fixtures only: no browser, Android, vehicle, Canvas, network or commands.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const A = require('./apex-data');
const Adapter = require('./contract-adapter');
const K = A.KEYS;
const ALL = Object.values(K);
const completed = [];
function test(name, run) { run(); completed.push(name); }

function target() {
  const listeners = new Map();
  return {
    addEventListener(name, fn) { if (!listeners.has(name)) listeners.set(name, new Set()); listeners.get(name).add(fn); },
    removeEventListener(name, fn) { listeners.get(name)?.delete(fn); },
    emit(name) { for (const fn of [...(listeners.get(name) || [])]) fn(); },
    listenerCount() { return [...listeners.values()].reduce((sum, set) => sum + set.size, 0); }
  };
}
function scheduler() {
  const doc = Object.assign(target(), {hidden: false});
  const tasks = new Map(), saved = new Map(), cancelled = [];
  let id = 0;
  const win = Object.assign(target(), {
    document: doc,
    requestAnimationFrame(fn) { const key = id++; tasks.set(key, fn); saved.set(key, fn); return key; },
    cancelAnimationFrame(key) { cancelled.push(key); tasks.delete(key); }
  });
  return {
    win, doc, tasks, saved, cancelled,
    flush() { const queued = [...tasks]; tasks.clear(); for (const [, fn] of queued) fn(); }
  };
}
function mock(options = {}) {
  const calls = [];
  const values = {...options.values};
  const bridge = {
    __themeLabMock: Adapter.MOCK_SENTINEL,
    getAvailableKeys() { calls.push(['available']); return options.availableResult ?? JSON.stringify(options.available ?? ALL); },
    subscribe(json) { const keys = JSON.parse(json); calls.push(['subscribe', keys]); options.onSubscribe?.(keys); },
    getCarData(key) { calls.push(['read', key]); return options.onRead ? options.onRead(key, values) : values[key] ?? null; },
    unsubscribe(json) { const keys = JSON.parse(json); calls.push(['unsubscribe', keys]); options.onUnsubscribe?.(keys); }
  };
  for (const method of ['getPreference', 'setPreference', 'updateCarData', 'triggerSystemAction', 'heartbeat', 'setNativeMaskState', 'setClusterBackground', 'setAppDefaultDimensions', 'setAaClusterMapEnabled']) {
    bridge[method] = () => { throw new Error('Forbidden method called: ' + method); };
  }
  return {bridge, calls, values};
}
function harness(options = {}) {
  const s = scheduler(), m = mock(options), snapshots = [], keyEvents = [];
  const adapter = Adapter.create({
    labMode: true, window: s.win, bridge: m.bridge,
    onSnapshot: item => snapshots.push(item),
    onKeyEvent: (key, detail) => keyEvents.push([key, detail]),
    decodeOptions: options.decodeOptions
  });
  return {...s, ...m, adapter, hooks: adapter.hooks, snapshots, keyEvents};
}

// Environment and ownership boundaries.
test('requires literal opt-in and explicit own mock sentinel before any bridge calls', () => {
  const s = scheduler(), m = mock();
  const base = {window: s.win, bridge: m.bridge, onSnapshot() {}};
  for (const labMode of [undefined, false, 1, 'true']) assert.throws(() => Adapter.create({...base, labMode}), /labMode/);
  for (const bridge of [undefined, {}, {__themeLabMock: true}, {__themeLabMock: 'ApexGT'}, Object.create(m.bridge)]) {
    assert.throws(() => Adapter.create({...base, labMode: true, bridge}), /sentinel/);
  }
  assert.deepEqual(m.calls, []);
});
test('rejects any Android property without invoking a getter, even with a valid mock', () => {
  for (const native of [undefined, null, {}, {subscribe() { throw new Error('native called'); }}]) {
    const s = scheduler(), m = mock();
    s.win.Android = native;
    assert.throws(() => Adapter.create({labMode: true, window: s.win, bridge: m.bridge, onSnapshot() {}}), /Android host/);
    assert.deepEqual(m.calls, []);
  }
  const s = scheduler();
  Object.setPrototypeOf(s.win, {Android: undefined});
  assert.throws(() => Adapter.create({labMode: true, window: s.win, bridge: mock().bridge, onSnapshot() {}}), /Android host/);
  let reads = 0;
  Object.defineProperty(globalThis, 'Android', {configurable: true, get() { reads++; throw new Error('native property read'); }});
  try { assert.throws(() => harness(), /Android host/); } finally { delete globalThis.Android; }
  assert.equal(reads, 0);
});
test('browser module loads inertly and does not overwrite host entry points', () => {
  const previous = () => 'preserved';
  const context = {ApexData: A, onDataChanged: previous, control: previous, onCardChanged: previous, onKeyEvent: previous, cleanup: previous};
  Object.defineProperty(context, 'Android', {get() { throw new Error('native access on script load'); }});
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'contract-adapter.js'), 'utf8'), context);
  assert.equal(typeof context.StarlightContractAdapter.create, 'function');
  for (const key of ['onDataChanged', 'control', 'onCardChanged', 'onKeyEvent', 'cleanup']) assert.equal(context[key], previous);
  assert.throws(() => context.StarlightContractAdapter.create({labMode: true, window: scheduler().win, bridge: mock().bridge, onSnapshot() {}}), /Android host/);
});
test('requires read-only method surface, scheduler, decoder and callback', () => {
  for (const method of ['getAvailableKeys', 'subscribe', 'getCarData', 'unsubscribe']) {
    const m = mock(); delete m.bridge[method];
    assert.throws(() => Adapter.create({labMode: true, window: scheduler().win, bridge: m.bridge, onSnapshot() {}}), /mock method/);
    assert.deepEqual(m.calls, []);
  }
  assert.throws(() => Adapter.create({labMode: true, window: {}, bridge: mock().bridge, onSnapshot() {}}), /scheduler/);
  assert.throws(() => Adapter.create({labMode: true, window: scheduler().win, bridge: mock().bridge}), /onSnapshot/);
  const context = {};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'contract-adapter.js'), 'utf8'), context);
  assert.throws(() => context.StarlightContractAdapter.create({labMode: true, window: scheduler().win, bridge: mock().bridge, onSnapshot() {}}), /apex-data/);
});
test('creation does not start, register global callbacks or schedule work', () => {
  const h = harness();
  assert.deepEqual(h.calls, []);
  assert.equal(h.tasks.size, 0);
  assert.equal(h.win.listenerCount() + h.doc.listenerCount(), 0);
  for (const key of ['Android', 'control', 'onDataChanged', 'onCardChanged', 'onKeyEvent', 'cleanup', 'focus', 'showScreen']) assert.equal(key in h.win, false);
  h.adapter.cleanup();
  assert.equal(h.adapter.start(), false);
  assert.deepEqual(h.calls, []);
});
test('late Android or revoked mock sentinel disables the adapter and its pending callback', () => {
  for (const mode of ['before-start', 'after-start', 'sentinel']) {
    const h = harness();
    if (mode !== 'before-start') h.adapter.start();
    const calls = h.calls.length;
    if (mode === 'sentinel') h.bridge.__themeLabMock = 'revoked';
    else h.win.Android = undefined;
    if (mode === 'before-start') assert.equal(h.adapter.start(), false);
    else h.hooks.onDataChanged(K.speed, 100);
    h.flush();
    assert.equal(h.calls.length, calls);
    assert.equal(h.adapter.getStatus().disposed, true);
    assert.equal(h.snapshots.length, 0);
    assert.equal(h.tasks.size, 0);
    assert.equal(h.win.listenerCount() + h.doc.listenerCount(), 0);
  }
});

// Subscription and snapshot contract.
test('subscribes only supported canonical keys, before snapshots, once', () => {
  const h = harness({available: [K.speed, K.speed, K.current, 'carSpeed', 'cardId', 'clusterEnabled', 'app.preferences.enableSpeedAdjustment', 'unknown'], values: {[K.speed]: 60, [K.current]: -20}});
  assert.equal(h.adapter.start(), true);
  assert.equal(h.adapter.start(), true);
  assert.deepEqual(h.calls, [['available'], ['subscribe', [K.speed, K.current]], ['read', K.speed], ['read', K.current]]);
  assert.equal(h.tasks.size, 1);
  h.flush();
  assert.equal(h.snapshots[0].decoded.speed, 60);
  assert.equal(h.snapshots[0].decoded.powerKw, null);
  assert.equal(h.snapshots[0].cardId, null);
  h.adapter.cleanup();
  assert.deepEqual(h.calls.at(-1), ['unsubscribe', [K.speed, K.current]]);
});
test('synchronous subscribe push and canonical missing values beat snapshots', () => {
  let h;
  h = harness({available: [K.speed, K.current, K.voltage], values: {[K.speed]: 10, [K.current]: 20, [K.voltage]: 400}, onSubscribe() {
    h.hooks.onDataChanged(K.speed, 82);
    h.hooks.onDataChanged(K.current, null);
  }});
  h.adapter.start(); h.flush();
  assert.equal(h.snapshots[0].decoded.speed, 82);
  assert.equal(h.snapshots[0].decoded.powerKw, null);
  assert.deepEqual(h.calls.filter(call => call[0] === 'read'), [['read', K.voltage]]);
  h.adapter.cleanup();
});
test('push during getCarData and push before start retain precedence', () => {
  let h;
  h = harness({available: [K.speed, K.fuelPct], onRead(key) { h.hooks.onDataChanged(key, 75); return 10; }});
  h.hooks.onDataChanged(K.fuelPct, 0);
  h.adapter.start(); h.flush();
  assert.equal(h.snapshots[0].decoded.speed, 75);
  assert.equal(h.snapshots[0].decoded.fuelPct, 0);
  assert.deepEqual(h.calls.filter(call => call[0] === 'read'), [['read', K.speed]]);
  h.adapter.cleanup();
});
test('available-key errors do not invent values or start subscriptions', () => {
  for (const availableResult of ['bad JSON', '{}', 'null', '42']) {
    const h = harness({availableResult});
    assert.equal(h.adapter.start(), false);
    assert.equal(h.adapter.start(), false);
    assert.equal(h.adapter.getStatus().ready, false); h.flush();
    assert.equal(h.snapshots[0].decoded.speed, null);
    assert.deepEqual(h.calls, [['available']]);
    assert.equal(h.adapter.getStatus().errors.length, 1);
    h.adapter.cleanup();
  }
});
test('empty advertised keys are a valid missing-data state', () => {
  const h = harness({available: []});
  assert.equal(h.adapter.start(), true); h.flush();
  assert.deepEqual(h.calls, [['available']]);
  for (const value of Object.values(h.snapshots[0].snapshot)) assert.equal(value, null);
  assert.equal(h.snapshots[0].decoded.speed, null);
  assert.equal(h.snapshots[0].decoded.powerKw, null);
  assert.equal(h.snapshots[0].decoded.gear, '--');
  assert.equal(h.snapshots[0].decoded.fuelLiters, null);
  assert.equal(A.fmt(h.snapshots[0].decoded.speed), '--');
  h.adapter.cleanup();
});
test('failed subscription rolls back possible partial registration; reads fail independently', () => {
  const broken = harness({available: [K.speed], onSubscribe() { throw new Error('mock registration failed'); }});
  assert.equal(broken.adapter.start(), false);
  assert.deepEqual(broken.calls, [['available'], ['subscribe', [K.speed]], ['unsubscribe', [K.speed]]]);
  broken.adapter.cleanup();
  assert.equal(broken.calls.filter(call => call[0] === 'unsubscribe').length, 1);
  const h = harness({available: [K.speed, K.fuelPct], onRead(key) { if (key === K.speed) throw new Error('no cache'); return 0; }});
  h.adapter.start(); h.flush();
  assert.equal(h.snapshots[0].decoded.speed, null);
  assert.equal(h.snapshots[0].decoded.fuelPct, 0);
  assert.deepEqual(h.adapter.getStatus().errors, ['snapshot-failed:' + K.speed]);
  h.adapter.cleanup();
});

// Data correctness and independence.
test('legacy aliases work only through control until the first canonical reading', () => {
  const h = harness({available: []}); h.adapter.start();
  assert.equal(h.hooks.onDataChanged('carSpeed', 90), false);
  h.hooks.control('carSpeed', 30);
  assert.equal(h.adapter.getState().decoded.speed, 30);
  h.hooks.onDataChanged(K.speed, 0);
  assert.equal(h.hooks.control('carSpeed', 200), false);
  assert.equal(h.adapter.getState().decoded.speed, 0);
  h.hooks.control(K.speed, null);
  h.hooks.control('carSpeed', 99);
  assert.equal(h.adapter.getState().decoded.speed, null);
  h.hooks.control('fuelPercent', 50);
  h.hooks.onDataChanged(K.fuelPct, 'invalid');
  h.hooks.control('fuelPercent', 90);
  assert.equal(h.adapter.getState().decoded.fuelPct, null);
  h.adapter.cleanup();
});
test('legacy labels normalize to existing enum keys without replacing canonical data', () => {
  const h = harness({available: []});
  for (const [alias, value, field, expected] of [
    ['gearState', 'D', 'gear', 'D'], ['gearState', 'P', 'gear', 'P'],
    ['drivingMode', 'sport', 'driveMode', 'SPORT'], ['evMode', 'HEV Inteligente', 'propulsionMode', 'HEV'],
    ['evMode', 'HEV PRIORIDADE 70%', 'propulsionMode', 'HEV'], ['evMode', 'EV', 'propulsionMode', 'EV'],
    ['tempUnit', '°F', 'tempUnit', '°F'], ['tempUnit', '°C', 'tempUnit', '°C']
  ]) { h.hooks.control(alias, value); assert.equal(h.adapter.getState().decoded[field], expected); }
  h.hooks.onDataChanged(K.gear, 0);
  h.hooks.control('gearState', 'R');
  assert.equal(h.adapter.getState().decoded.gear, 'N');
  h.hooks.control('gearState', {});
  assert.equal(h.adapter.getState().decoded.gear, 'N');
  h.adapter.cleanup();
});
test('missing, malformed and sentinel readings stay missing; valid zero survives', () => {
  const h = harness({available: []});
  for (const value of [undefined, null, '', ' ', false, true, {}, [], NaN, Infinity, 'Infinity', '0x20', '1 kW', -1]) {
    h.hooks.onDataChanged(K.speed, value);
    assert.equal(h.adapter.getState().decoded.speed, null);
  }
  for (const value of [0, '0', ' 0 ', '+0', '0e2']) {
    h.hooks.onDataChanged(K.speed, value);
    assert.equal(h.adapter.getState().decoded.speed, 0);
  }
  for (const key of [K.fuelPct, K.batteryPct]) for (const value of [-1, 101]) {
    h.hooks.onDataChanged(key, value);
    assert.equal(h.adapter.getState().decoded[key === K.fuelPct ? 'fuelPct' : 'batteryPct'], null);
  }
  for (const value of [-1, 255]) {
    h.hooks.onDataChanged(K.insideTemp, value);
    assert.equal(h.adapter.getState().decoded.insideTemp, null);
  }
  h.hooks.onDataChanged(K.insideTemp, -5);
  assert.equal(h.adapter.getState().decoded.insideTemp, -5);
  h.hooks.onDataChanged(K.consumption, 0);
  assert.deepEqual(h.adapter.getState().decoded.consumption, {value: 0, unit: 'L/100 km'});
  h.adapter.cleanup();
});
test('electrical power requires two independent canonical operands and keeps signed zero', () => {
  const h = harness({available: []});
  const power = () => h.adapter.getState().decoded.powerKw;
  h.hooks.onDataChanged(K.speed, 200);
  for (const key of ['batteryPowerKw', 'powerKw', 'electricPower', 'voltage', 'current']) assert.equal(h.hooks.control(key, 160), false);
  assert.equal(power(), null);
  h.hooks.onDataChanged(K.voltage, 400); assert.equal(power(), null);
  h.hooks.onDataChanged(K.current, -125); assert.equal(power(), -50);
  h.hooks.onDataChanged(K.speed, 0); assert.equal(power(), -50);
  h.hooks.onDataChanged(K.current, 0); assert.equal(power(), 0);
  h.hooks.onDataChanged(K.current, 250); assert.equal(power(), 100);
  h.hooks.onDataChanged(K.voltage, null); assert.equal(power(), null);
  h.hooks.onDataChanged(K.voltage, 0); assert.equal(power(), 0);
  h.hooks.onDataChanged(K.current, null); assert.equal(power(), null);
  h.hooks.onDataChanged(K.voltage, 1e308);
  h.hooks.onDataChanged(K.current, 1e308); assert.equal(power(), null);
  h.adapter.cleanup();
});
test('derived ranges require both values and tank liters require explicit capacity', () => {
  const h = harness({available: [], decodeOptions: {tankCapacityL: 55, clockText: '12:34'}});
  h.hooks.onDataChanged(K.fuelRange, 0);
  assert.equal(h.adapter.getState().decoded.totalRange, null);
  h.hooks.onDataChanged(K.electricRange, 0);
  h.hooks.onDataChanged(K.fuelPct, 0);
  assert.equal(h.adapter.getState().decoded.totalRange, 0);
  assert.equal(h.adapter.getState().decoded.fuelLiters, 0);
  assert.equal(h.adapter.getState().decoded.clock, '12:34');
  h.adapter.cleanup();
});
test('snapshot payloads are detached, deeply frozen and reject prototype/unknown keys', () => {
  const h = harness({available: []}); h.adapter.start(); h.flush();
  const first = h.snapshots[0];
  for (const key of ['__proto__', 'constructor', 'toString', 'cardId', 'batteryPowerKw', 'app.preferences.enableSpeedAdjustment']) {
    assert.equal(h.hooks.control(key, 123), false);
    assert.equal(h.hooks.onDataChanged(key, 123), false);
  }
  for (const item of [first, first.snapshot, first.decoded, first.decoded.consumption, first.decoded.tirePsi, h.adapter.getStatus().errors]) assert.equal(Object.isFrozen(item), true);
  assert.throws(() => { first.snapshot[K.speed] = 99; }, TypeError);
  h.hooks.onDataChanged(K.speed, 0); h.flush();
  assert.equal(first.snapshot[K.speed], null);
  assert.equal(h.snapshots[1].snapshot[K.speed], 0);
  h.adapter.cleanup();
});

// Lifecycle, presentation control and one-way navigation hooks.
test('bursts coalesce to one frame, same values do not redraw, zero frame IDs cancel', () => {
  const h = harness({available: []}); h.adapter.start();
  for (let speed = 0; speed < 100; speed++) h.hooks.onDataChanged(K.speed, speed);
  assert.equal(h.tasks.size, 1); h.flush();
  assert.equal(h.snapshots.length, 1);
  assert.equal(h.snapshots[0].decoded.speed, 99);
  h.hooks.onDataChanged(K.speed, 99);
  assert.equal(h.tasks.size, 0);
  h.adapter.cleanup();
  const pending = harness({available: []}); pending.adapter.start(); pending.adapter.cleanup();
  assert.deepEqual(pending.cancelled, [0]);
  pending.saved.get(0)();
  assert.equal(pending.snapshots.length, 0);
});
test('document hidden cancels render, retains latest data and resumes once without resubscribe', () => {
  const h = harness({available: [K.speed], values: {[K.speed]: 1}}); h.adapter.start();
  h.doc.hidden = true; h.doc.emit('visibilitychange');
  assert.equal(h.tasks.size, 0);
  h.saved.get(0)();
  assert.equal(h.snapshots.length, 0);
  h.hooks.onDataChanged(K.speed, 88);
  assert.equal(h.tasks.size, 0);
  assert.equal(h.adapter.getState().decoded.speed, 88);
  h.doc.hidden = false; h.doc.emit('visibilitychange');
  assert.equal(h.tasks.size, 1);
  h.saved.get(0)(); // A cancelled pre-hide callback cannot clear the newer frame.
  assert.equal(h.adapter.getStatus().pendingFrame, true);
  h.flush();
  assert.equal(h.snapshots.length, 1);
  assert.equal(h.snapshots[0].decoded.speed, 88);
  assert.equal(h.snapshots[0].hidden, false);
  assert.equal(h.calls.filter(call => call[0] === 'subscribe').length, 1);
  h.adapter.cleanup();
});
test('explicit hidden composes with document visibility and does not replay wheel input', () => {
  const h = harness({available: []}); h.adapter.start(); h.hooks.onCardChanged(1); h.flush();
  h.adapter.setHidden(true);
  assert.equal(h.hooks.onKeyEvent('UP'), false);
  h.hooks.onDataChanged(K.speed, 52);
  h.doc.hidden = true;
  h.adapter.setHidden(false);
  assert.equal(h.tasks.size, 0);
  h.doc.hidden = false; h.doc.emit('visibilitychange'); h.flush();
  assert.equal(h.snapshots.at(-1).decoded.speed, 52);
  assert.deepEqual(h.keyEvents, []);
  h.adapter.cleanup();
});
test('initial hidden state stays idle until resume', () => {
  const h = harness({available: []}); h.doc.hidden = true; h.adapter.start();
  assert.equal(h.tasks.size, 0);
  h.doc.hidden = false; h.doc.emit('visibilitychange'); h.flush();
  assert.equal(h.snapshots.length, 1);
  h.adapter.cleanup();
});
test('only onCardChanged sets card; keys never synthesize left/right or change it', () => {
  const h = harness({available: ['cardId']}); h.adapter.start();
  assert.equal(h.hooks.onKeyEvent('UP'), false);
  for (const value of [null, '', false, 2, 4, -1, 'AC', 1.2]) assert.equal(h.hooks.onCardChanged(value), false);
  assert.equal(h.adapter.getState().cardId, null);
  assert.equal(h.hooks.control('cardId', 3), false);
  h.hooks.onCardChanged('0');
  assert.equal(h.adapter.getState().cardId, 0);
  assert.equal(h.hooks.onKeyEvent('ENTER'), false);
  h.hooks.onCardChanged('1');
  for (const key of ['LEFT', 'RIGHT', 'up', '', null, '__proto__']) assert.equal(h.hooks.onKeyEvent(key), false);
  for (const key of ['UP', 'DOWN', 'ENTER', 'BACK', 'HOME', 'ENTER_LONG', 'BACK_LONG']) assert.equal(h.hooks.onKeyEvent(key), true);
  assert.equal(h.keyEvents.length, 7);
  assert.equal(h.adapter.getState().cardId, 1);
  h.hooks.onCardChanged(3);
  assert.equal(h.hooks.onKeyEvent('BACK'), true);
  assert.equal(h.adapter.getState().cardId, 3);
  assert.equal(h.calls.some(call => call[0] === 'read' && call[1] === 'cardId'), false);
  h.adapter.cleanup();
});
test('cluster visibility is control-only presentation state, with no vehicle subscription', () => {
  const h = harness({available: ['clusterEnabled', 'cardId']}); h.adapter.start(); h.hooks.onCardChanged(3);
  assert.equal(h.hooks.control('clusterEnabled', 'false'), true);
  assert.equal(h.hooks.onKeyEvent('UP'), false);
  h.flush();
  assert.equal(h.snapshots[0].clusterEnabled, false);
  assert.equal(h.snapshots[0].cardId, 3);
  assert.equal(h.hooks.control('clusterEnabled', 'bad'), false);
  assert.equal(h.hooks.onDataChanged('clusterEnabled', 1), true);
  assert.equal(h.hooks.onKeyEvent('UP'), true);
  h.flush();
  assert.equal(h.snapshots[1].clusterEnabled, true);
  assert.deepEqual(h.calls, [['available']]);
  h.adapter.cleanup();
});
test('cleanup/pagehide is idempotent, cancels frames and prevents all late work', () => {
  const h = harness({available: [K.speed]}); h.adapter.start();
  assert.equal(h.win.listenerCount(), 1); assert.equal(h.doc.listenerCount(), 1);
  h.win.emit('pagehide'); h.adapter.cleanup(); h.hooks.cleanup();
  assert.equal(h.win.listenerCount() + h.doc.listenerCount(), 0);
  assert.equal(h.calls.filter(call => call[0] === 'unsubscribe').length, 1);
  assert.deepEqual(h.calls.at(-1), ['unsubscribe', [K.speed]]);
  assert.equal(h.tasks.size, 0);
  h.hooks.onDataChanged(K.speed, 100); h.hooks.control('carSpeed', 10);
  h.hooks.onCardChanged(3); h.hooks.onKeyEvent('ENTER');
  h.adapter.setHidden(false); h.doc.emit('visibilitychange');
  assert.equal(h.adapter.start(), false);
  h.saved.get(0)(); h.flush();
  assert.equal(h.snapshots.length, 0);
  assert.equal(h.adapter.getState().decoded.speed, null);
  assert.equal(h.adapter.getState().cardId, null);
  assert.deepEqual(h.keyEvents, []);
});
test('cleanup during subscribe prevents snapshots; failing unsubscribe still disposes', () => {
  let h;
  h = harness({available: [K.speed], onSubscribe() { h.adapter.cleanup(); }});
  assert.equal(h.adapter.start(), false);
  assert.deepEqual(h.calls.map(call => call[0]), ['available', 'subscribe', 'unsubscribe']);
  assert.equal(h.tasks.size, 0);
  const broken = harness({available: [K.speed], onUnsubscribe() { throw new Error('mock stopped'); }});
  broken.adapter.start(); broken.adapter.cleanup(); broken.adapter.cleanup();
  assert.equal(broken.adapter.getStatus().disposed, true);
  assert.deepEqual(broken.adapter.getStatus().errors, ['unsubscribe-failed']);
  assert.equal(broken.tasks.size, 0);
  assert.equal(broken.win.listenerCount() + broken.doc.listenerCount(), 0);
});
test('a delivered callback can dispose without orphan frames or replay', () => {
  const s = scheduler(), m = mock({available: []}); let adapter, delivered = 0;
  adapter = Adapter.create({labMode: true, window: s.win, bridge: m.bridge, onSnapshot() { delivered++; adapter.cleanup(); }});
  adapter.start(); s.flush();
  assert.equal(delivered, 1);
  assert.equal(adapter.getStatus().disposed, true);
  assert.equal(s.tasks.size, 0);
  assert.equal(s.win.listenerCount() + s.doc.listenerCount(), 0);
});

console.log(JSON.stringify({result: 'PASS', testCases: completed.length, scope: 'development-only read-only contract fixtures', hardwareValidated: false, tests: completed}, null, 2));
