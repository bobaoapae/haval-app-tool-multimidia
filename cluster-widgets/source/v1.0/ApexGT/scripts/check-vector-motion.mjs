import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const sources = ['gauge-geometry.js', 'vector-geometry.js', 'gauge-motion.js', 'vector-gauges.js']
  .map(name => fs.readFileSync(new URL(`../src/${name}`, import.meta.url), 'utf8'));

function target() {
  const listeners = new Map();
  return {
    addEventListener(type, fn) {
      if (!listeners.has(type)) listeners.set(type, new Set());
      listeners.get(type).add(fn);
    },
    removeEventListener(type, fn) { listeners.get(type)?.delete(fn); },
    emit(type, detail) {
      for (const fn of [...(listeners.get(type) || [])]) fn({ detail });
    },
    count: () => [...listeners.values()].reduce((sum, set) => sum + set.size, 0)
  };
}

function harness({ initial = 'Contour', vectorLoaded = true } = {}) {
  let now = 0;
  let nextFrame = 0;
  const frames = new Map();
  const surfaces = [];
  const root = { dataset: { display: initial } };
  const svg = { innerHTML: '' };
  const motion = { ...target(), matches: false };
  const reads = { contour: 0, vector: 0 };
  const materials = {
    contour: { ...target(), name: 'contour', complete: true, naturalWidth: 1920, naturalHeight: 720 },
    vector: { ...target(), name: 'vector', complete: vectorLoaded, naturalWidth: vectorLoaded ? 1920 : 0, naturalHeight: 720 }
  };
  const imageData = (width, height) => ({ width, height, data: new Uint8ClampedArray(width * height * 4) });

  function canvas() {
    const node = { dataset: {}, paintTimes: [], lastStrokes: [], images: [] };
    let path = [];
    let clears = 0;
    const context = new Proxy({
      createLinearGradient: () => ({ addColorStop() {} }),
      createRadialGradient: () => ({ addColorStop() {} }),
      clearRect() {
        if (++clears % 2 === 1) { node.paintTimes.push(now); node.lastStrokes = []; }
      },
      beginPath() { path = []; },
      moveTo(x, y) { path.push({ x, y }); },
      lineTo(x, y) { path.push({ x, y }); },
      stroke() { node.lastStrokes.push({ points: [...path], width: context.lineWidth, color: context.strokeStyle }); },
      drawImage(...args) { node.images.push(args); }
    }, { get: (object, property) => object[property] ?? (() => {}) });
    node.getContext = () => context;
    return node;
  }
  const contour = canvas();
  const vector = canvas();
  const elements = {
    'apex-gt': root, 'gauge-motion': contour, 'gauge-material': materials.contour,
    'vector-gauge-motion': vector, 'vector-material': materials.vector, 'vector-gauge-scales': svg
  };
  const document = {
    ...target(), hidden: false,
    getElementById: id => elements[id] || null,
    createElement(tag) {
      assert.equal(tag, 'canvas');
      const output = { width: 0, height: 0 };
      let image = null;
      const context = {
        drawImage(input) { image = input; output.materialName = input.name; },
        createImageData: imageData,
        getImageData(x, y, width, height) {
          reads[image.name]++;
          const pixels = imageData(width, height);
          for (let row = 0; row < height; row++) {
            for (let col = 0; col < width; col++) {
              const level = row % 20 < 2 ? 0 : 180;
              const index = (row * width + col) * 4;
              pixels.data.set(row === 3 ? [220, 20, 20, 255] : [level, level, level, 255], index);
            }
          }
          return pixels;
        },
        putImageData(pixels) { output.pixels = pixels; }
      };
      output.getContext = () => context;
      surfaces.push(output);
      return output;
    }
  };
  const window = {
    ...target(), matchMedia: () => motion,
    requestAnimationFrame(fn) { frames.set(++nextFrame, fn); return nextFrame; },
    cancelAnimationFrame(id) { frames.delete(id); }
  };
  class Path2D { moveTo() {} lineTo() {} closePath() {} }
  const sandbox = vm.createContext({ document, window, Path2D, performance: { now: () => now } });
  for (const source of sources) vm.runInContext(source, sandbox);
  return {
    window, document, contour, vector, root, svg, frames, motion, materials, reads, surfaces,
    telemetry: detail => window.emit('apex-telemetry', detail),
    mode(mode) { root.dataset.display = mode; window.emit('apex-display-change', { mode }); },
    tick(time) {
      now = time;
      const pending = [...frames.values()];
      frames.clear();
      pending.forEach(fn => fn(now));
    }
  };
}

const h = harness();
assert.equal(h.contour.dataset.active, 'true');
assert.equal(h.vector.dataset.active, 'false');
assert.equal(h.vector.dataset.material, 'ready');
assert.equal(h.vector.dataset.paints, '0', 'inactive Vector does not paint during preparation');
h.telemetry({ speed: 72, power: 96 });
assert.equal(h.contour.dataset.speed, '72');
assert.equal(h.vector.dataset.targetSpeed, '72');
assert.equal(h.vector.dataset.paints, '0', 'inactive engine only caches telemetry');
assert.equal(h.frames.size, 0);
const contourPaints = h.contour.dataset.paints;
h.mode('Vector');
assert.equal(h.contour.dataset.active, 'false');
assert.equal(h.vector.dataset.active, 'true');
assert.equal(h.vector.dataset.speed, '72', 'display switch snaps to latest telemetry');
assert.equal(h.vector.dataset.power, '96');
assert.equal(h.vector.dataset.paints, '1');
assert.equal(h.contour.dataset.paints, contourPaints, 'deactivation does not repaint');
assert.equal(h.frames.size, 0);
h.mode('Vector');
assert.equal(h.vector.dataset.paints, '1', 'repeated mode event is deduplicated');

h.vector.paintTimes.length = 0;
h.telemetry({ speed: 160, power: -50 });
assert.equal(h.frames.size, 1, 'only the active engine schedules one shared frame');
for (let time = 10; time <= 220; time += 10) h.tick(time);
assert.equal(h.vector.dataset.speed, '160');
assert.equal(h.vector.dataset.power, '-50');
assert.equal(h.vector.dataset.running, 'false');
assert.equal(h.frames.size, 0);
for (let index = 1; index < h.vector.paintTimes.length; index++) {
  assert.ok(h.vector.paintTimes[index] - h.vector.paintTimes[index - 1] >= 1000 / 30);
}
assert.equal(h.contour.dataset.paints, contourPaints, 'Contour remains unpainted through Vector animation');
const settled = h.vector.dataset.paints;
h.telemetry({ speed: 160, power: -50 });
assert.equal(h.vector.dataset.paints, settled);
h.telemetry({ speed: 20, power: 100 });
h.tick(260);
assert.equal(h.frames.size, 1);
h.telemetry({ power: null });
assert.equal(h.vector.dataset.power, '', 'missing power clears synchronously');
h.telemetry({ speed: Number.NaN });
assert.equal(h.vector.dataset.speed, '');
assert.equal(h.frames.size, 0);
const missingPaints = h.vector.dataset.paints;
h.mode('Contour');
assert.equal(h.contour.dataset.speed, '', 'Contour receives missing telemetry while inactive');
assert.equal(h.vector.dataset.paints, missingPaints);
h.telemetry({ speed: 240, power: -120 });
assert.equal(h.contour.dataset.speed, '180', 'existing Contour visual range remains unchanged');
h.mode('Vector');
assert.equal(h.vector.dataset.speed, '200', 'Vector has its independent 200 km/h scale');
assert.equal(h.vector.dataset.power, '-100');

const geometry = h.window.ApexVectorGeometry;
for (const [value, y] of [[0, 449], [40, 402], [80, 330], [120, 259.5], [160, 198], [200, 145]]) {
  assert.equal(geometry.speedY(value), y);
}
for (const [value, y] of [[-100, 465], [-50, 408], [0, 351.5], [50, 293.5], [150, 242], [200, 190]]) {
  assert.equal(geometry.powerY(value), y);
}
assert.match(h.svg.innerHTML, /x="234" y="340" text-anchor="end" font-size="26">80/);
assert.match(h.svg.innerHTML, /x="297" y="459"[^>]*>0/);
assert.ok(!/>100<\/text>/.test(h.svg.innerHTML), 'reference intentionally has no power 100 label');

h.motion.matches = true;
h.motion.emit('change');
const zones = [[238, 315, 104, 60], [225, 475, 90, 90], [330, 445, 100, 100], [520, 120, 880, 530]];
for (const speed of [0, 20, 40, 60, 80, 100, 120, 160, 200]) {
  h.telemetry({ speed, power: null });
  assert.equal(h.vector.dataset.speedY, String(geometry.speedY(speed)));
  assert.equal(h.frames.size, 0);
  for (const stroke of h.vector.lastStrokes) {
    for (const point of stroke.points) {
      const radius = stroke.width / 2;
      for (const [x, y, width, height] of zones) {
        assert.ok(point.x + radius < x || point.x - radius >= x + width
          || point.y + radius < y || point.y - radius >= y + height,
        `Vector speed marker ${speed} overlaps protected rectangle`);
      }
    }
  }
}
for (const power of [-100, -50, 0, 50, 150, 200]) {
  h.telemetry({ speed: null, power });
  assert.equal(h.vector.dataset.powerY, String(geometry.powerY(power)));
}
assert.equal(h.reads.vector, 2, 'one cropped raster read per side, never per frame');
h.materials.vector.emit('load');
assert.equal(h.reads.vector, 2, 'duplicate load cannot rebuild sprites');
for (const [width, side, x] of [[205, 'left', 125], [225, 'right', 1580]]) {
  const sprite = h.surfaces.find(surface => surface.width === width && surface.pixels);
  assert.ok(sprite);
  let lit = 0;
  for (let row = 0; row < 360; row++) {
    const bounds = side === 'left' ? geometry.leftBounds(130 + row) : geometry.rightBounds(130 + row);
    for (let col = 0; col < width; col++) {
      const alpha = sprite.pixels.data[(row * width + col) * 4 + 3];
      if (row % 20 < 2 || row === 3) assert.equal(alpha, 0, 'dark grooves and red caps retain the source material');
      if (!alpha) continue;
      lit++;
      assert.ok(x + col > Math.min(bounds.outer, bounds.inner) + 1);
      assert.ok(x + col < Math.max(bounds.outer, bounds.inner) - 1);
    }
  }
  assert.ok(lit > 10000, 'illumination fills the actual broad metal face');
}
const rightFinishes = h.surfaces.filter(surface => surface.width === 225 && surface.pixels);
assert.equal(rightFinishes.length, 2, 'traction and regeneration each have a cached finish');
for (let index = 0; index < rightFinishes[0].pixels.data.length; index += 4) {
  const blue = rightFinishes[0].pixels.data;
  const green = rightFinishes[1].pixels.data;
  assert.equal(green[index + 3], blue[index + 3], 'green finish preserves every groove and mask alpha');
  if (!green[index + 3]) continue;
  assert.ok(green[index + 1] >= green[index + 2] + 25, 'regeneration has a distinct green-dominant fill');
  assert.ok(blue[index + 2] > blue[index + 1], 'traction remains blue');
}
h.telemetry({ speed: null, power: -50 });
assert.equal(h.vector.images.at(-1)[0], rightFinishes[1], 'negative power renders the prepared green sprite');
assert.ok(h.vector.lastStrokes.some(stroke => stroke.color === '#8fffc1'), 'regeneration rail is mint green');
assert.ok(h.vector.lastStrokes.some(stroke => stroke.color === 'rgba(143,255,193,.22)'), 'regeneration halo is green');
h.telemetry({ power: 50 });
assert.equal(h.vector.images.at(-1)[0], rightFinishes[0], 'positive power renders the original blue sprite');
assert.ok(h.vector.lastStrokes.some(stroke => stroke.color === '#8cddff'), 'traction rail remains blue');
assert.equal(h.reads.vector, 2, 'both cached colors reuse the same one-time raster reads');

h.motion.matches = false;
h.motion.emit('change');
h.telemetry({ speed: 72, power: 18 });
h.telemetry({ speed: 120 });
assert.equal(h.frames.size, 1);
h.document.hidden = true;
h.document.emit('visibilitychange');
assert.equal(h.frames.size, 0);
assert.equal(h.vector.dataset.speed, '120');
h.document.hidden = false;
h.telemetry({ speed: 40 });
assert.equal(h.frames.size, 1);
const beforeSwitch = h.vector.dataset.paints;
h.mode('Contour');
assert.equal(h.frames.size, 0, 'mode switch cancels the previous engine animation');
assert.equal(h.vector.dataset.paints, beforeSwitch);
assert.equal(h.contour.dataset.speed, '40');
h.window.emit('apex-cleanup');
assert.equal(h.window.count(), 0);
assert.equal(h.document.count(), 0);
assert.equal(h.motion.count(), 0);
assert.equal(h.materials.vector.count(), 0);
assert.equal(h.frames.size, 0);
const finalVector = h.vector.dataset.paints;
h.telemetry({ speed: 55 });
h.mode('Vector');
h.tick(500);
assert.equal(h.vector.dataset.paints, finalVector, 'cleanup is final');

const pending = harness({ initial: 'Vector', vectorLoaded: false });
pending.telemetry({ speed: 72, power: 18 });
assert.equal(pending.vector.dataset.material, 'pending');
assert.equal(pending.vector.images.length, 0);
pending.materials.vector.naturalWidth = 1920;
pending.materials.vector.complete = true;
pending.materials.vector.emit('load');
assert.equal(pending.vector.dataset.material, 'ready');
assert.equal(pending.reads.vector, 2);
assert.equal(pending.frames.size, 0, 'material arrival produces a single snap, not a loop');
const removed = harness({ vectorLoaded: false });
removed.window.emit('apex-cleanup');
removed.materials.vector.naturalWidth = 1920;
removed.materials.vector.emit('load');
assert.equal(removed.reads.vector, 0);

console.log('Apex Vector: measured metal/scale geometry, protected zones, active-engine isolation, material caching and lifecycle OK.');
