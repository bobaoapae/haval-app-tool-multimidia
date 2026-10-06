import assert from 'node:assert/strict';
import {
  PROJECTION_MAP_URL,
  PROJECTION_SIMULATOR_CSS,
} from './projection-simulator.js';

assert.match(PROJECTION_MAP_URL, /mapa\.jpeg$/);
assert.match(PROJECTION_SIMULATOR_CSS, /#app\.carplay-in-dash \.cluster-mask-bg/);
assert.match(PROJECTION_SIMULATOR_CSS, /#app\.projection-mirror-in-dash \.cluster-mask-bg/);
assert.match(
  PROJECTION_SIMULATOR_CSS,
  /#app\.native-mock-enabled\.display-analogico-v2 \.cluster-mask-bg/,
);
assert.match(PROJECTION_SIMULATOR_CSS, /width: 1920px !important/);
assert.match(PROJECTION_SIMULATOR_CSS, /height: 720px !important/);
assert.match(PROJECTION_SIMULATOR_CSS, /background-size: cover !important/);
assert.match(PROJECTION_SIMULATOR_CSS, /box-shadow: none !important/);
assert.match(PROJECTION_SIMULATOR_CSS, /border-radius: 0 !important/);
assert.match(PROJECTION_SIMULATOR_CSS, /rgba\(0, 0, 0, 0\.98\) 0%/);
assert.match(PROJECTION_SIMULATOR_CSS, /rgba\(0, 0, 0, 0\) 41%/);
assert.match(PROJECTION_SIMULATOR_CSS, /rgba\(0, 0, 0, 0\) 59%/);
assert.match(PROJECTION_SIMULATOR_CSS, /rgba\(0, 0, 0, 0\.98\) 100%/);
assert.doesNotMatch(PROJECTION_SIMULATOR_CSS, /radial-gradient/);
assert.doesNotMatch(PROJECTION_SIMULATOR_CSS, /filter:/);
assert.doesNotMatch(PROJECTION_SIMULATOR_CSS, /animation:/);
assert.doesNotMatch(PROJECTION_SIMULATOR_CSS, /transition:/);

console.log('Projeção simulada válida: 1920x720, centro limpo e laterais graduais.');
