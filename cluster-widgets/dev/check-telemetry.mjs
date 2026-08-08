import assert from 'node:assert/strict';
import { createLegacyTelemetryStateManager } from './legacy-telemetry.js';

const calls = [];
const frameWindow = {
  control(key, value) {
    calls.push([key, value]);
  },
};

const stateManager = createLegacyTelemetryStateManager(frameWindow);
stateManager.pushInitialTelemetry();

assert.ok(calls.some(([key, value]) => key === 'carSpeed' && value === 0));
assert.ok(calls.some(([key, value]) => key === 'fuelPercent' && value === 100));
assert.ok(calls.some(([key, value]) => key === 'batteryPercent' && value === 100));

let observedSpeed = null;
const unsubscribe = stateManager.subscribe('carSpeed', (value) => {
  observedSpeed = value;
});

const callCountBeforeChange = calls.length;
stateManager.set('carSpeed', 88);
assert.equal(stateManager.get('carSpeed'), 88);
assert.equal(stateManager.getState().carSpeed, 88);
assert.equal(observedSpeed, 88);
assert.deepEqual(calls.at(-1), ['carSpeed', 88]);

stateManager.set('carSpeed', 88);
assert.equal(calls.length, callCountBeforeChange + 1, 'valores repetidos devem ser deduplicados');

unsubscribe();
stateManager.dispose();

console.log('Adaptador de telemetria legado válido.');
