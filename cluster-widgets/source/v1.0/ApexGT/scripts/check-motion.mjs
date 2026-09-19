import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const geometrySource = fs.readFileSync(new URL('../src/gauge-geometry.js', import.meta.url), 'utf8');
const source = fs.readFileSync(new URL('../src/gauge-motion.js', import.meta.url), 'utf8');
await import('../src/gauge-geometry.js');
const nodeGeometry = globalThis.ApexGaugeGeometry;
assert.equal(nodeGeometry.mapY(110), 110, 'build import preserves the header boundary');
assert.equal(nodeGeometry.mapY(286), 286, 'upper speed accents stay aligned with their fixed readout');
assert.equal(nodeGeometry.mapY(300), 300, 'lower panel extension starts below the upper instruments');
assert.equal(nodeGeometry.mapY(435), 462, 'lower panel stretches by the shared 1.2 ratio');
assert.equal(nodeGeometry.mapY(570), 624, 'panel base expands to the agreed physical row');
assert.equal(nodeGeometry.mapY(720), 720, 'footer keeps the fixed canvas bottom');
assert.equal(nodeGeometry.inverseY(624), 570, 'asset builder can sample the original panel base');
for (const y of [0, 109, 110, 132, 286, 300, 309, 330, 503, 512, 540, 570, 620, 719, 720]) {
  assert.ok(Math.abs(nodeGeometry.inverseY(nodeGeometry.mapY(y)) - y) < 1e-9,
    'asset sampling and displayed coordinates round-trip without drift');
}

function eventTarget() {
  const listeners = new Map();
  return {
    addEventListener(type, listener) {
      if (!listeners.has(type)) listeners.set(type, new Set());
      listeners.get(type).add(listener);
    },
    removeEventListener(type, listener) { listeners.get(type)?.delete(listener); },
    emit(type, detail) {
      for (const listener of [...(listeners.get(type) || [])]) listener({ detail });
    },
    listenerCount() { return [...listeners.values()].reduce((sum, set) => sum + set.size, 0); }
  };
}

function harness({ loaded = true } = {}) {
  let now = 0;
  let nextFrame = 0;
  let materialReads = 0;
  const frames = new Map();
  const drawTimes = [];
  const filledPaths = [];
  const drawImages = [];
  const glowRects = [];
  const clearRects = [];
  const surfaces = [];
  let currentPath = [];
  let clears = 0;
  const imageData = (width, height) => ({ width, height, data: new Uint8ClampedArray(width * height * 4) });
  const context = new Proxy({
    createLinearGradient: () => ({ addColorStop() {} }),
    createRadialGradient: () => ({ addColorStop() {} }),
    clearRect(...args) { clearRects.push(args); if (++clears % 2 === 1) drawTimes.push(now); },
    beginPath() { currentPath = []; },
    moveTo(x, y) { currentPath.push({ x, y }); },
    lineTo(x, y) { currentPath.push({ x, y }); },
    fill() { filledPaths.push(currentPath); },
    drawImage(...args) { drawImages.push(args); },
    fillRect(...args) { glowRects.push(args); }
  }, { get: (target, property) => target[property] || (() => {}) });
  const canvas = { dataset: {}, getContext: () => context };
  const material = { ...eventTarget(), complete: loaded, naturalWidth: loaded ? 1920 : 0, naturalHeight: 720 };
  const motion = { ...eventTarget(), matches: false };
  const document = {
    ...eventTarget(), hidden: false,
    getElementById: id => id === 'gauge-motion' ? canvas : id === 'gauge-material' ? material : null,
    createElement(tag) {
      assert.equal(tag, 'canvas');
      const surface = { width: 0, height: 0 };
      const offscreen = {
        drawImage() {}, createImageData: imageData,
        getImageData(x, y, width, height) {
          materialReads++;
          const pixels = imageData(width, height);
          // Known dark grooves and lit faces ensure the mask comes from pixels.
          for (let row = 0; row < height; row++) {
            for (let col = 0; col < width; col++) {
              const index = (row * width + col) * 4;
              const level = row % 20 < 2 ? 0 : 76;
              pixels.data.set([level, level, level, 255], index);
            }
          }
          return pixels;
        },
        putImageData(pixels) { surface.pixels = pixels; }
      };
      surface.getContext = () => offscreen;
      surfaces.push(surface);
      return surface;
    }
  };
  const window = {
    ...eventTarget(), matchMedia: () => motion,
    requestAnimationFrame(callback) { frames.set(++nextFrame, callback); return nextFrame; },
    cancelAnimationFrame(id) { frames.delete(id); }
  };
  class Path2D { moveTo() {} lineTo() {} closePath() {} }
  const sandbox = vm.createContext({ document, window, Path2D, performance: { now: () => now } });
  vm.runInContext(geometrySource, sandbox);
  vm.runInContext(source, sandbox);
  return {
    canvas, material, window, document, motion, frames, drawTimes, filledPaths, drawImages, glowRects, clearRects, surfaces,
    get materialReads() { return materialReads; },
    telemetry: detail => window.emit('apex-telemetry', detail),
    tick(time) {
      now = time;
      const pending = [...frames.values()];
      frames.clear();
      for (const callback of pending) callback(now);
    }
  };
}

const h = harness();
assert.equal(h.frames.size, 0, 'no animation starts without telemetry');
h.telemetry({ speed: 72, power: 96 });
assert.equal(h.canvas.dataset.speed, '72', 'first sample must not sweep from fictional zero');
assert.equal(h.canvas.dataset.power, '96');
assert.equal(h.frames.size, 0, 'first values settle immediately');
const initialPaints = h.canvas.dataset.paints;
h.telemetry({ speed: 72, power: 96 });
assert.equal(h.canvas.dataset.paints, initialPaints, 'identical telemetry does not repaint');
h.telemetry({ speed: 120, power: -34 });
assert.equal(h.canvas.dataset.running, 'true');
assert.equal(h.frames.size, 1, 'channels share one animation frame');
for (let time = 10; time <= 900; time += 10) h.tick(time);
assert.equal(h.canvas.dataset.speed, '120');
assert.equal(h.canvas.dataset.power, '-34', 'power crosses zero and ends at signed regeneration');
assert.equal(h.frames.size, 0, 'stable gauges stop scheduling frames');
assert.equal(h.canvas.dataset.running, 'false');
for (let index = 1; index < h.drawTimes.length; index++) {
  assert.ok(h.drawTimes[index] - h.drawTimes[index - 1] >= 1000 / 30,
    'animation paints must not exceed 30 fps');
}

const smooth = harness();
smooth.telemetry({ speed: 0, power: 0 });
smooth.telemetry({ power: 100 });
const expectedPower = new Map([
  [40, 32.967995396436066], [80, 55.067103588277845],
  [120, 10.538187095194871], [160, -19.310438258365647],
  [200, 6.83662337554561], [240, 24.3635229336857]
]);
for (const time of [40, 80]) {
  smooth.tick(time);
  assert.ok(Math.abs(Number(smooth.canvas.dataset.power) - expectedPower.get(time)) < 1e-9,
    `power follows the Analog V2 exponential response at ${time}ms`);
}
const beforeFirstRetarget = Number(smooth.canvas.dataset.power);
smooth.telemetry({ power: -80 });
assert.equal(Number(smooth.canvas.dataset.power), beforeFirstRetarget,
  'retargeting power never teleports the rendered needle');
for (const time of [120, 160]) {
  smooth.tick(time);
  assert.ok(Math.abs(Number(smooth.canvas.dataset.power) - expectedPower.get(time)) < 1e-9,
    `power remains continuous through the first retarget at ${time}ms`);
}
const beforeSecondRetarget = Number(smooth.canvas.dataset.power);
smooth.telemetry({ power: 60 });
assert.equal(Number(smooth.canvas.dataset.power), beforeSecondRetarget,
  'a second retarget also preserves the current rendered position');
for (const time of [200, 240]) {
  smooth.tick(time);
  assert.ok(Math.abs(Number(smooth.canvas.dataset.power) - expectedPower.get(time)) < 1e-9,
    `power remains continuous through the second retarget at ${time}ms`);
}
assert.ok(Number(smooth.canvas.dataset.power) > -80 && Number(smooth.canvas.dataset.power) < 60,
  'the damped follower does not overshoot its live target');
for (let time = 280; time <= 1200; time += 40) smooth.tick(time);
assert.equal(smooth.canvas.dataset.power, '60');
assert.equal(smooth.frames.size, 0, 'damped power stops scheduling frames after convergence');

h.telemetry({ speed: 80, power: 100 });
h.tick(260);
assert.equal(h.frames.size, 1);
h.telemetry({ power: null });
assert.equal(h.canvas.dataset.power, '', 'missing power clears synchronously during motion');
assert.equal(h.canvas.dataset.running, 'true', 'other channel continues its transition');
h.telemetry({ speed: Number.NaN });
assert.equal(h.canvas.dataset.speed, '');
assert.equal(h.frames.size, 0, 'all missing values cancel pending motion');
h.telemetry({ speed: 300, power: -150 });
assert.equal(h.canvas.dataset.speed, '180');
assert.equal(h.canvas.dataset.power, '-100');

h.motion.matches = true;
h.motion.emit('change');
h.telemetry({ speed: 32, power: 66 });
assert.equal(h.canvas.dataset.speed, '32');
assert.equal(h.canvas.dataset.power, '66');
assert.equal(h.frames.size, 0, 'reduced motion uses immediate updates');
h.motion.matches = false;
h.motion.emit('change');
h.telemetry({ speed: 78 });
assert.equal(h.frames.size, 1);
h.document.hidden = true;
h.document.emit('visibilitychange');
assert.equal(h.canvas.dataset.speed, '78');
assert.equal(h.frames.size, 0, 'hidden document cancels rendering');
h.document.hidden = false;
h.telemetry({ speed: 22 });
assert.equal(h.frames.size, 1);
h.window.emit('apex-cleanup');
assert.equal(h.frames.size, 0, 'cleanup cancels animation');
assert.equal(h.window.listenerCount(), 0);
assert.equal(h.document.listenerCount(), 0);
assert.equal(h.motion.listenerCount(), 0);
const paintsAfterCleanup = h.canvas.dataset.paints;
h.telemetry({ speed: 99, power: 99 });
h.tick(500);
assert.equal(h.canvas.dataset.paints, paintsAfterCleanup, 'disposed theme never resumes');

const visual = harness();
const geometry = visual.window.ApexGaugeGeometry;
assert.equal(geometry.mapY(570), nodeGeometry.mapY(570), 'browser and asset builder share the same layout map');
assert.equal(geometry.leftBounds(geometry.mapY(350)).outer, 68);
assert.equal(geometry.leftBounds(geometry.mapY(350)).inner, 127);
assert.equal(geometry.rightEdge(geometry.mapY(503)), 1724, 'horizontal metal contours remain unchanged');
assert.equal(geometry.powerPivot().x, 1631);
assert.equal(geometry.powerPivot().y, geometry.mapY(309), 'needle origin follows the same vertical remap');
for (const [value, y] of [[0, 512], [20, 432.5], [40, 365.8333333333], [60, 312.5], [80, 263], [100, 221], [120, 189], [140, 165], [160, 147], [180, 132]]) {
  assert.equal(geometry.speedY(value), geometry.mapY(y), 'speed marker and scale use identical remapped coordinates');
}
const urbanSpeeds = [0, 20, 40, 60, 80, 100, 120, 140, 160, 180];
const urbanDistances = urbanSpeeds.slice(1).map((speed, index) => geometry.speedY(urbanSpeeds[index]) - geometry.speedY(speed));
for (let index = 1; index < urbanDistances.length; index++) {
  assert.ok(urbanDistances[index - 1] > urbanDistances[index], 'each successive speed interval must occupy less arc');
}
assert.ok(geometry.speedY(0) - geometry.speedY(80) > (geometry.speedY(80) - geometry.speedY(180)) * 2,
  '0–80 km/h receives more than twice the travel of 80–180 km/h');
for (const [value, y] of [[-100, 503], [-75, 485], [-50, 462.5], [-40, 441.6666666667], [-30, 418.3333333333], [-20, 391.6666666667], [-10, 362.5], [0, 330], [10, 300], [20, 268], [30, 240], [40, 216], [50, 195], [75, 172], [100, 153]]) {
  const displayY = geometry.mapY(y);
  assert.equal(geometry.powerY(value), displayY);
  visual.motion.matches = true;
  visual.telemetry({ speed: null, power: value });
  assert.equal(Number(visual.canvas.dataset.needleY), displayY, 'needle points at the live scale value');
  assert.equal(Number(visual.canvas.dataset.needleX), geometry.rightEdge(displayY) - 1);
  assert.equal(visual.filledPaths.at(-1)[0].y, geometry.powerPivot().y, 'needle is drawn from the remapped pivot');
}
for (const direction of [-1, 1]) {
  const values = [0, 10, 20, 30, 40, 50].map(value => value * direction);
  const distances = values.slice(1).map((value, index) => Math.abs(geometry.powerY(value) - geometry.powerY(values[index])));
  for (let index = 1; index < distances.length; index++) {
    assert.ok(distances[index - 1] > distances[index], 'each successive 10 kW interval must occupy less arc');
  }
  assert.ok(Math.abs(geometry.powerY(50 * direction) - geometry.powerY(0))
    > Math.abs(geometry.powerY(100 * direction) - geometry.powerY(50 * direction)) * 2,
  '0–50 kW receives more than twice the travel of the compact 50–100 reserve');
}
assert.ok(visual.clearRects.every(([, y, , height]) => y + height >= geometry.mapY(570) + 23),
  'local clear regions cover expanded material plus the bounded glow');
assert.equal(visual.materialReads, 1, 'material is read only once during initialization');
visual.material.emit('load');
assert.equal(visual.materialReads, 1, 'duplicate image load does not rebuild sprites');
const leftSprites = visual.surfaces.filter(surface => surface.width === 160 && surface.pixels);
assert.equal(leftSprites.length, 2, 'left light uses two cached material finishes');
for (const sprite of leftSprites) {
  assert.equal(sprite.height, 514, 'left material is sampled through the expanded base at y624');
  let visible = 0;
  for (let row = 0; row < sprite.height; row++) {
    const bounds = geometry.leftBounds(110 + row);
    for (let col = 0; col < 160; col++) {
      const alpha = sprite.pixels.data[(row * 160 + col) * 4 + 3];
      if (row % 20 < 2) assert.equal(alpha, 0, 'actual black material grooves remain unlit');
      if (!alpha) continue;
      visible++;
      assert.ok(65 + col > bounds.outer + 11 && 65 + col < bounds.inner - 3,
        'left light stays inside the existing rib face and off silver lips');
    }
  }
  assert.ok(visible > 10000, 'broad rib material is illuminated rather than a thin synthetic stripe');
}
const rightSprites = visual.surfaces.filter(surface => surface.width === 110 && surface.pixels);
assert.equal(rightSprites.length, 2);
const rightTop = Math.floor(geometry.mapY(130));
for (const [finish, sprite] of rightSprites.entries()) {
  assert.equal(sprite.height, Math.ceil(geometry.mapY(540)) - rightTop);
  for (let row = 0; row < sprite.height; row++) {
    const edge = geometry.rightEdge(rightTop + row) - 1;
    for (let col = 0; col < 110; col++) {
      const index = (row * 110 + col) * 4;
      if (!sprite.pixels.data[index + 3]) continue;
      const x = 1675 + col;
      assert.ok(x <= edge && x >= edge - 19, 'fine right hatch stays inside the instrument-side channel');
      const green = sprite.pixels.data[index + 1];
      const blue = sprite.pixels.data[index + 2];
      if (finish === 1) assert.ok(green >= blue + 35, 'regeneration is visibly green rather than cyan');
      else assert.ok(blue > green, 'traction retains its blue finish');
    }
  }
}
visual.telemetry({ speed: null, power: -50 });
assert.equal(visual.drawImages.at(-1)[0], rightSprites[1], 'negative power selects the cached green material');
visual.telemetry({ power: 50 });
assert.equal(visual.drawImages.at(-1)[0], rightSprites[0], 'positive power returns to the original blue material');
visual.telemetry({ speed: 0, power: 0 });
assert.equal(visual.glowRects.length, 0, 'zero speed does not draw a hotspot');
visual.telemetry({ speed: 72, power: 96 });
assert.ok(visual.drawImages.length > 0, 'frames reuse prepared sprites');
assert.equal(visual.materialReads, 1, 'animation never resamples raster pixels');

const waiting = harness({ loaded: false });
waiting.telemetry({ speed: 72 });
assert.equal(waiting.canvas.dataset.material, 'pending');
waiting.material.naturalWidth = 1920;
waiting.material.complete = true;
waiting.material.emit('load');
assert.equal(waiting.canvas.dataset.material, 'ready');
assert.equal(waiting.materialReads, 1);
assert.equal(waiting.frames.size, 0, 'material arrival renders once and does not start a loop');
const abandoned = harness({ loaded: false });
abandoned.window.emit('apex-cleanup');
abandoned.material.naturalWidth = 1920;
abandoned.material.emit('load');
assert.equal(abandoned.material.listenerCount(), 0);
assert.equal(abandoned.materialReads, 0, 'cleanup cancels pending image preparation');

console.log('Apex GT motion: timing/lifecycle, raster groove preservation, light containment and exact scale/needle correspondence OK.');
