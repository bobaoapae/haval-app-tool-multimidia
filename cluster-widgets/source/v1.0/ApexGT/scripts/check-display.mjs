import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../src/apex-display.js', import.meta.url), 'utf8');

function create(bridge) {
    const classes = new Set();
    const attributes = new Map();
    const events = [];
    const root = {
        classList: { toggle(name, enabled) { if (enabled) classes.add(name); else classes.delete(name); } },
        setAttribute(name, value) { attributes.set(name, value); },
    };
    const window = { Android: bridge, dispatchEvent(event) { events.push(event); } };
    const context = vm.createContext({ window, CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail; } } });
    vm.runInContext(source, context);
    return { display: window.ApexDisplay.create({ root }), classes, attributes, events };
}

const offline = create();
assert.equal(offline.display.getMode(), 'Contour');
assert.equal(offline.attributes.get('data-display'), 'Contour');
assert(offline.classes.has('display-contour'));
assert.equal(offline.events.length, 0);
assert.equal(offline.display.setMode('Vector'), true);
assert.equal(offline.display.getMode(), 'Vector');
assert.equal(offline.events[0].type, 'apex-display-change');
assert.equal(offline.events[0].detail.mode, 'Vector');
assert(offline.classes.has('display-vector'));
assert(!offline.classes.has('display-contour'));
offline.display.setMode('Vector');
assert.equal(offline.events.length, 1, 'duplicate mode must not emit');
assert.equal(offline.display.setMode('Sport'), false);
assert.equal(offline.display.getMode(), 'Vector');
offline.display.update('display', 'Contour', true);
assert.equal(offline.display.getMode(), 'Vector', 'global display is never consumed');
offline.display.update('apex_display_mode', 'Contour', false);
assert.equal(offline.display.getMode(), 'Vector', 'control aliases require permission');
offline.display.update('apexDisplayMode', 'Contour', true);
assert.equal(offline.display.getMode(), 'Contour');
offline.display.update('app.preferences.apex_display_mode', 'Vector', false);
assert.equal(offline.display.getMode(), 'Vector');
offline.display.update('app.preferences.apexDisplayMode', 'Contour', false);
offline.display.update('apexDisplayMode', 'Vector', true);
offline.display.update('app.preferences.apex_display_mode', 'Vector', true);
assert.equal(offline.display.getMode(), 'Contour', 'canonical preference wins over all aliases');
offline.display.update('app.preferences.apexDisplayMode', 'Vector', false);
offline.display.update('app.preferences.apexDisplayMode', null, false);
assert.equal(offline.display.getMode(), 'Vector', 'invalid live preference cannot erase a selected mode');
offline.display.cleanup();
offline.display.update('app.preferences.apexDisplayMode', 'Contour', false);
assert.equal(offline.display.setMode('Contour'), false);
assert.equal(offline.display.getMode(), 'Vector', 'cleanup freezes the disposed controller');

const writes = [];
const saved = create({
    getPreference(key, fallback) { assert.equal(key, 'apexDisplayMode'); assert.equal(fallback, 'Contour'); return 'Vector'; },
    savePreference(key, value) { writes.push([key, value]); },
    updateCarData() { assert.fail('visual preference must not write vehicle data'); },
    saveSetting() { assert.fail('visual preference must not change global display'); },
});
assert.equal(saved.display.getMode(), 'Vector');
assert.equal(writes.length, 0, 'restoring a preference is read-only');
saved.display.setMode('Contour');
assert.deepEqual(writes, [['apexDisplayMode', 'Contour']]);
saved.display.setMode('Contour');
assert.equal(writes.length, 1, 'duplicate mode must not write');

const failing = create({ getPreference() { throw new Error('read unavailable'); }, savePreference() { throw new Error('write unavailable'); } });
assert.equal(failing.display.getMode(), 'Contour');
assert.equal(failing.display.setMode('Vector'), true);
assert.equal(failing.display.getMode(), 'Vector', 'visual changes can remain session-local');
assert.equal(create({ getPreference() { return 'Unknown'; } }).display.getMode(), 'Contour');
console.log('Apex display preference: PASS (restore, aliases, canonical priority, persistence, event dedupe, cleanup)');
