/* Node Canvas verification of the production painter; no vehicle or bridge. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { performance } = require('node:perf_hooks');
const { createHash } = require('node:crypto');
const root = path.resolve(__dirname, '..');
const study = path.resolve(root, '../../../dev/studies/Starlight');
let canvasApi;
try { canvasApi = require('@napi-rs/canvas'); }
catch (_) { canvasApi = require(path.join(study, 'node_modules/@napi-rs/canvas')); }
const { createCanvas, GlobalFonts } = canvasApi;
const R = require('../src/renderer');
const A = require('../src/apex-data');
const G = require('../src/galaxy-stars');
const Smoke = require('../src/glass-smoke');
const evidenceRoot = path.resolve(root, '../../../../tools/headunit-dev/output/starlight-161-port-20261011');
const frozenRoot = path.join(evidenceRoot, 'candidate/source-1.0.2-before-culling/src');
const Frozen = fs.existsSync(path.join(frozenRoot, 'renderer.js')) ? require(path.join(frozenRoot, 'renderer')) : null;
const FrozenStars = Frozen ? require(path.join(frozenRoot, 'galaxy-stars')) : null;
const visualRoot = path.resolve(root, '../../../../tools/headunit-dev/output/starlight-ui-20261012/source-1.0.3-before-visual/src');
const skyRoot = path.resolve(root, '../../../../tools/headunit-dev/output/starlight-full-sky-20261013/source-1.0.6/src');
const SkyBefore = fs.existsSync(path.join(skyRoot, 'renderer.js')) ? require(path.join(skyRoot, 'renderer')) : null;
const VisualBefore = SkyBefore || (fs.existsSync(path.join(visualRoot, 'renderer.js')) ? require(path.join(visualRoot, 'renderer')) : Frozen);
if (VisualBefore) VisualBefore.setCanvasFactory(createCanvas);
let frozenBrushDraws = 0;
if (Frozen) Frozen.setCanvasFactory((w, h) => {
  const canvas = createCanvas(w, h), getContext = canvas.getContext.bind(canvas);
  canvas.getContext = type => new Proxy(getContext(type), {
    get(o, k) {
      if (k === 'drawImage') return (...args) => {
        if (args[0].width === 48 && args[0].height === 48) frozenBrushDraws++;
        return o.drawImage(...args);
      };
      return typeof o[k] === 'function' ? o[k].bind(o) : o[k];
    }, set(o, k, v) { o[k] = v; return true; }
  });
  return canvas;
});
if (VisualBefore === Frozen && Frozen) VisualBefore.setCanvasFactory(createCanvas);
for (const [file, family] of [['ScaleDigits.ttf', 'Scale Digits'], ['LabelSans.ttf', 'Label Sans'],
  ['PanelSans.ttf', 'Panel Sans'], ['HeaderSans.ttf', 'Header Sans'], ['SpeedDigits.ttf', 'Speed Digits']]) {
  assert(GlobalFonts.registerFromPath(path.join(root, 'assets', file), family));
}
const owned = [];
R.setCanvasFactory((w, h) => { const c = createCanvas(w, h); owned.push(c); return c; });
function state(speed, battery, time = 0) {
  const s = R.functionalState({ speedRaw: speed, batteryPowerKw: battery, powerKw: battery, time });
  s.readouts = A.decode(); return s;
}
function render(c, s, t, extra = {}) {
  return R.render(c.getContext('2d'), t, 'live', {
    state: s, starMotion: { speed: s.speedRaw || 0, travel: 0.33 }, ...extra
  });
}
const layers = [createCanvas(R.W, R.H), createCanvas(R.W, R.H), createCanvas(R.W, R.H)];
const composite = createCanvas(R.W, R.H);
function renderLayers(s, t, extra = {}) {
  const options = { state: s, starMotion: { speed: s.speedRaw || 0, travel: 0.33 }, ...extra };
  R.renderBackground(layers[0].getContext('2d'), t, 'live', options);
  R.renderBeams(layers[1].getContext('2d'), t, 'live', options);
  R.renderForeground(layers[2].getContext('2d'), t, 'live', options);
  const ctx = composite.getContext('2d');
  ctx.clearRect(0, 0, R.W, R.H);
  for (const layer of layers) ctx.drawImage(layer, 0, 0);
  return composite;
}
function difference(a, b) {
  const aa = a.getContext('2d').getImageData(0, 0, R.W, R.H).data;
  const bb = b.getContext('2d').getImageData(0, 0, R.W, R.H).data;
  let maximum = 0, total = 0, changed = 0, above8 = 0;
  let minX = R.W, minY = R.H, maxX = -1, maxY = -1;
  const outliers = [];
  for (let i = 0; i < aa.length; i += 4) {
    let pixel = 0;
    for (let j = 0; j < 3; j++) {
      const delta = Math.abs(aa[i + j] - bb[i + j]);
      total += delta; pixel = Math.max(pixel, delta); maximum = Math.max(maximum, delta);
    }
    if (pixel) changed++;
    if (pixel > 8) {
      above8++; const x = i / 4 % R.W, y = Math.floor(i / 4 / R.W);
      minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
      if (outliers.length < 4) outliers.push({ x, y, delta: pixel, direct: Array.from(aa.slice(i, i + 3)), layered: Array.from(bb.slice(i, i + 3)) });
    }
    maximum = Math.max(maximum, Math.abs(aa[i + 3] - bb[i + 3]));
  }
  return { maximum, mean: total / (R.W * R.H * 3), changed, above8,
    boundsAbove8: above8 ? { minX, minY, maxX, maxY } : null, outliers };
}
function geometryTrace(s, time, motion, layered) {
  const hash = createHash('sha256');
  const methods = new Set(['moveTo', 'lineTo', 'quadraticCurveTo', 'bezierCurveTo', 'arc', 'ellipse',
    'fillRect', 'strokeRect', 'fillText', 'strokeText', 'translate', 'rotate', 'scale', 'transform', 'setTransform',
    'createLinearGradient', 'createRadialGradient', 'drawImage']);
  const traced = c => new Proxy(c.getContext('2d'), {
    get(o, k) {
      if (methods.has(k)) return (...args) => {
        hash.update(JSON.stringify([k, k === 'drawImage' ? args.slice(1) : args]));
        return o[k](...args);
      };
      return typeof o[k] === 'function' ? o[k].bind(o) : o[k];
    }, set(o, k, v) { o[k] = v; return true; }
  });
  const options = { state: s, starMotion: motion };
  if (layered) {
    R.renderBackground(traced(layers[0]), time, 'live', options);
    R.renderBeams(traced(layers[1]), time, 'live', options);
    R.renderForeground(traced(layers[2]), time, 'live', options);
  } else R.render(traced(createCanvas(R.W, R.H)), time, 'live', options);
  return hash.digest('hex');
}
assert.equal(R.render, R.renderPanel);
for (const name of ['renderBackground', 'renderBeams', 'renderForeground']) assert.equal(typeof R[name], 'function');
assert.throws(() => R.render(createCanvas(R.W, R.H).getContext('2d'), 0), /explicit state/);
assert.throws(() => R.render(createCanvas(R.W, R.H).getContext('2d'), 0, 'live', { state: state(0, 0) }), /starMotion/);
const absent = state(null, null), beyond = state(240, 150);
assert.equal(absent.speed, null); assert.equal(absent.powerKw, null);
assert.equal(absent.left, 0); assert.equal(absent.right, 0); assert.equal(absent.regen, false);
assert.equal(absent.batteryState, 'SEM DADOS');
assert.equal(beyond.speed, 240); assert.equal(beyond.speedRaw, 240);
assert.equal(beyond.powerKw, 150); assert.equal(beyond.batteryPowerKw, 150);
assert.equal(beyond.left, 8); assert.equal(beyond.right, 8);
assert.equal(state(160, 0).left > 0, true); assert.equal(state(160, 0).right, 0);
assert.equal(state(0, -40).regen, true);
assert.equal(G.sampleStar(0, { speed: 0, travel: 1 }).streakLength, 0);
const target = {}; assert.equal(G.sampleStar(0, { speed: 50, travel: 1 }, target), target);

// Compare material and all unmodified foreground pixels independently. The
// only excluded foreground areas are measured glyph boxes for removed ticks,
// the changed caption/shadows, and the two new tip labels, never broad bands.
function traceForeground(painter, canvas, value, time, extra = {}) {
  const records = [], ctx = canvas.getContext('2d');
  const traced = new Proxy(ctx, {
    get(o, key) {
      if (key === 'fillText' || key === 'strokeText') return (text, x, y, ...rest) => {
        const m = o.measureText(String(text)), matrix = o.getTransform();
        const left = Number.isFinite(m.actualBoundingBoxLeft) ? m.actualBoundingBoxLeft : 0;
        const right = Number.isFinite(m.actualBoundingBoxRight) ? m.actualBoundingBoxRight : m.width;
        const ascent = Number.isFinite(m.actualBoundingBoxAscent) ? m.actualBoundingBoxAscent : parseFloat(o.font) * .7;
        const descent = Number.isFinite(m.actualBoundingBoxDescent) ? m.actualBoundingBoxDescent : parseFloat(o.font) * .15;
        const corners = [[x - left, y - ascent], [x + right, y - ascent], [x - left, y + descent], [x + right, y + descent]]
          .map(([xx, yy]) => [matrix.a * xx + matrix.c * yy + matrix.e, matrix.b * xx + matrix.d * yy + matrix.f]);
        const padding = o.shadowBlur ? Math.ceil(o.shadowBlur * 2) + 3 : key === 'strokeText' ? o.lineWidth / 2 + 2 : 2;
        records.push({ type: key, text: String(text), x, y, font: o.font, align: o.textAlign, baseline: o.textBaseline,
          fill: o.fillStyle, blur: o.shadowBlur, matrix: [matrix.a, matrix.b, matrix.c, matrix.d, matrix.e, matrix.f],
          bounds: { left: Math.min(...corners.map(c => c[0])) - padding, right: Math.max(...corners.map(c => c[0])) + padding,
            top: Math.min(...corners.map(c => c[1])) - padding, bottom: Math.max(...corners.map(c => c[1])) + padding } });
        return o[key](text, x, y, ...rest);
      };
      return typeof o[key] === 'function' ? o[key].bind(o) : o[key];
    }, set(o, key, value) { o[key] = value; return true; }
  });
  painter.renderForeground(traced, time, 'live', { state: value, ...extra });
  return records;
}
function glyphKey(r) { return JSON.stringify([r.text, r.x, r.y, r.font, r.align, r.baseline, r.matrix]); }
function compareRegions(value, time, motion) {
  if (!VisualBefore) return { available: false };
  const before = [createCanvas(R.W, R.H), createCanvas(R.W, R.H), createCanvas(R.W, R.H)];
  const after = [createCanvas(R.W, R.H), createCanvas(R.W, R.H), createCanvas(R.W, R.H)];
  const options = { state: value, starMotion: motion };
  R.renderBackground(after[0].getContext('2d'), time, 'live', options);
  VisualBefore.renderBackground(before[0].getContext('2d'), time, 'live', options);
  const bgA = after[0].getContext('2d').getImageData(0, 0, R.W, R.H).data;
  const bgB = before[0].getContext('2d').getImageData(0, 0, R.W, R.H).data;
  for (let y = 0; y < R.H; y++) for (let x = 0; x < R.W; x++) {
    const i = (y * R.W + x) * 4;
    assert.equal(bgA[i + 3], 255, 'Normal sky must cover the OEM background through the last row');
    // Lower stars include their bounded tails/halos; only the top stripe and
    // this lower footprint are authorized background changes.
    if ((y >= 16 && y < 25) || y >= 450) continue;
    for (let c = 0; c < 4; c++) assert.equal(bgA[i + c], bgB[i + c], 'Sky outside the requested bands must remain pixel-identical');
  }
  R.renderBeams(after[1].getContext('2d'), time, 'live', options);
  VisualBefore.renderBeams(before[1].getContext('2d'), time, 'live', options);
  assert.equal(difference(after[1], before[1]).maximum, 0, 'Complete beams must remain RGBA-identical');
  const oldGlyphs = traceForeground(VisualBefore, before[2], value, time);
  const newGlyphs = traceForeground(R, after[2], value, time);
  if (SkyBefore) assert.equal(difference(after[2], before[2]).maximum, 0, 'All foreground and menus must remain RGBA-identical to1.0.6');
  const floatKeys = new Set(oldGlyphs.filter(r => r.blur === 5 || r.blur === 6).map(glyphKey));
  for (const old of oldGlyphs.filter(r => r.blur === 5)) {
    const current = newGlyphs.find(r => r.type === 'fillText' && glyphKey(r) === glyphKey(old));
    assert(current && current.fill === old.fill, 'Floating data/font/coordinates/color must remain unchanged');
  }
  const changed = oldGlyphs.filter(r => floatKeys.has(glyphKey(r)) || Math.abs(r.matrix[1]) > .1 || (r.y === 283 && r.font.includes('Label Sans')))
    .concat(newGlyphs.filter(r => floatKeys.has(glyphKey(r)) || (r.y === 283 && r.font.includes('Label Sans'))));
  for (const side of [-1, 1]) changed.push({ bounds: R.tipReadout(after[2].getContext('2d'), side, value).bounds });
  const mask = new Uint8Array(R.W * R.H);
  for (const r of changed) {
    const left = Math.max(0, Math.floor(r.bounds.left) - 1), right = Math.min(R.W, Math.ceil(r.bounds.right) + 1);
    const top = Math.max(0, Math.floor(r.bounds.top) - 1), bottom = Math.min(R.H, Math.ceil(r.bounds.bottom) + 1);
    for (let y = top; y < bottom; y++) mask.fill(1, y * R.W + left, y * R.W + right);
  }
  const masked = mask.reduce((a, b) => a + b, 0);
  assert(masked < R.W * R.H * .2, 'Changed glyph mask must remain narrow');
  const fgA = after[2].getContext('2d').getImageData(0, 0, R.W, R.H).data;
  const fgB = before[2].getContext('2d').getImageData(0, 0, R.W, R.H).data;
  for (let pixel = 0; pixel < mask.length; pixel++) if (!mask[pixel]) {
    for (let j = 0; j < 4; j++) assert.equal(fgA[pixel * 4 + j], fgB[pixel * 4 + j], `Unchanged foreground pixel moved at ${pixel % R.W},${Math.floor(pixel / R.W)}`);
  }
  assert(!newGlyphs.some(r => Math.abs(r.matrix[1]) > .1), 'Fixed scale numerals must be removed');
  return { available: true, beamsMaximum: 0, backgroundOutsideRequestedBandsMaximum: 0, normalBackgroundAlpha: 255, foregroundOutsideGlyphsMaximum: 0, changedGlyphPixels: masked };
}
const tipChecks = [];
const tipCanvas = createCanvas(R.W, R.H), tipCtx = tipCanvas.getContext('2d');
const normalSky = createCanvas(R.W, R.H), skyCtx = normalSky.getContext('2d');
R.renderBackground(skyCtx, 0, 'live', { starMotion: { speed: 0, travel: 0 } });
const normalPixels = skyCtx.getImageData(0, 0, R.W, R.H).data;
for (let i = 3; i < normalPixels.length; i += 4) assert.equal(normalPixels[i], 255, 'Normal sky must cover every OEM background pixel');
let brightTopPixels = 0;
for (let x = 0; x < R.W; x++) if (normalPixels[(17 * R.W + x) * 4] > 120) brightTopPixels++;
assert(brightTopPixels < R.W / 3, 'The top must not contain a full-width bright stripe');
assert.equal(G.COUNT, 2800, 'Full-height sky must reuse the existing particle budget');
for (const [speed, power] of [[null, null], [0, 0], [8, .05], [38, 18.4], [64, 32], [48, -18], [55, .1], [80, 10], [109, -15], [120, -10], [240, 150.1], [999, -175.3]]) {
  const s = state(speed, power), left = R.tipReadout(tipCtx, -1, s), right = R.tipReadout(tipCtx, 1, s);
  for (const tip of [left, right]) {
    assert.equal(tip.value, tip.side === -1 ? s.speed : s.batteryPowerKw);
    assert.deepEqual(tip.blade, R.frontierGeometry(tip.side === -1 ? s.leftLength : s.rightLength, tip.side));
    assert(tip.bounds.left >= 0 && tip.bounds.right <= R.W && tip.bounds.top >= 0 && tip.bounds.bottom <= R.H);
    const dx = Math.max(tip.bounds.left - tip.anchor.x, 0, tip.anchor.x - tip.bounds.right);
    const dy = Math.max(tip.bounds.top - tip.anchor.y, 0, tip.anchor.y - tip.bounds.bottom);
    assert(Math.hypot(dx, dy) <= 22, 'Readout must remain attached to the visible upper tip, including foreground clearance');
  }
  for (const r of [{left:258,top:335,right:322,bottom:355}, {left:247,top:497,right:293,bottom:543}, {left:350.4,top:466,right:409.6,bottom:514}, {left:342,top:269,right:537,bottom:287}]) {
    assert(!(left.bounds.left < r.right + 4 && left.bounds.right > r.left - 4 && left.bounds.top < r.bottom + 4 && left.bounds.bottom > r.top - 4), 'Native TSR/ESP must remain clear of the speed label');
  }
  assert(!(right.bounds.left < 1586 && right.bounds.right > 1374 && right.bounds.top < 295 && right.bounds.bottom > 240), 'Power captions must remain clear of the near-tip number');
  assert.equal(right.label, R.tipBatteryLabel(power));
  assert.equal(right.color, power < 0 ? '#9ceac0' : power > 0 ? '#b4d4ff' : '#8c9bac');
  if (speed === 240) assert.equal(left.label, '240');
  if (speed === 109) {
    assert(Math.abs(left.anchor.x - 255.135) < .001 && Math.abs(left.anchor.y - 342.60343097826086) < .001, 'Upper tip must match the user-marked 109km/h reference');
    assert(Math.abs(left.bounds.right - 251.135) < .001, '109 readout must sit directly beside the marked tip');
    assert(Math.abs((left.bounds.top + left.bounds.bottom) / 2 - left.anchor.y) < 1e-8);
  }
  tipChecks.push({ speed, power, left, right });
}
assert.equal(R.tipBatteryLabel(null), '-- kW'); assert.equal(R.tipBatteryLabel(0), '0,0 kW');
assert.equal(R.tipBatteryLabel(32), '+32,0 kW'); assert.equal(R.tipBatteryLabel(-18), '−18,0 kW');
assert.equal(R.tipBatteryLabel(150.1), '+150,1 kW');
const oldTip = Object.freeze({ ...state(100, 40) }), oldTipSnapshot = JSON.stringify(oldTip);
for (const latestValue of [null, 0]) {
  const latest = Object.freeze({ ...state(latestValue, latestValue) });
  const resolved = R.resolveTipState(latest, oldTip);
  assert.equal(resolved.speed, latestValue); assert.equal(resolved.batteryPowerKw, latestValue);
  assert.equal(resolved.regen, false); assert.equal(resolved.batteryState, latestValue === null ? 'SEM DADOS' : 'NEUTRA');
  assert.equal(resolved.leftLength, oldTip.leftLength); assert.equal(resolved.rightLength, oldTip.rightLength);
  for (const side of [-1, 1]) assert.deepEqual(R.tipReadout(tipCtx, side, resolved).blade, R.tipReadout(tipCtx, side, oldTip).blade);
  const paintState = state(latestValue, latestValue);
  const glyphs = traceForeground(R, tipCanvas, paintState, 1, { tipState: oldTip });
  assert.equal(glyphs.filter(r => r.type === 'fillText' && r.y === 283 && r.font.includes('Label Sans')).map(r => r.text).join(''), resolved.batteryState);
}
assert.equal(JSON.stringify(oldTip), oldTipSnapshot, 'Presented tip input must never be mutated');
const speedOnlyGuard = R.resolveTipState(state(null, -18), oldTip);
assert.equal(speedOnlyGuard.speed, null); assert.equal(speedOnlyGuard.batteryPowerKw, 40);
assert.equal(speedOnlyGuard.batteryState, oldTip.batteryState);
const powerOnlyGuard = R.resolveTipState(state(64, null), oldTip);
assert.equal(powerOnlyGuard.speed, 100); assert.equal(powerOnlyGuard.batteryPowerKw, null);
assert.equal(powerOnlyGuard.regen, false); assert.equal(powerOnlyGuard.batteryState, 'SEM DADOS');
const latest = state(64, -18), ack = state(32, 32), displayed = R.resolveTipState(latest, ack);
assert.equal(displayed, ack);
const ackGlyphs = traceForeground(R, tipCanvas, latest, 1, { tipState: ack });
for (const side of [-1, 1]) {
  const expected = R.tipReadout(tipCtx, side, ack);
  assert(ackGlyphs.some(r => r.type === 'fillText' && r.text === expected.label && r.x === expected.x && r.y === expected.y));
}
assert(ackGlyphs.some(r => r.type === 'fillText' && r.text === '64' && parseFloat(r.font) > 80), 'Center speed stays latest while tip follows beam ACK');
assert.equal(ackGlyphs.filter(r => r.type === 'fillText' && r.y === 283 && r.font.includes('Label Sans')).map(r => r.text).join(''), ack.batteryState);
const parity = [];
const original = fs.existsSync(path.join(study, 'renderer.js')) ? require(path.join(study, 'renderer')) : null;
if (original) original.setCanvasFactory(createCanvas);
for (const [speed, battery, time] of [[0, 0, 0], [20, 10, 1], [100, 50, 2],
  [200, -100, 3], [240, 150, 4], [null, null, 5],
  [8, 0, 0.033], [8, -5, 0.47], [8, 100, 3.83],
  [10, -1, 0.071], [10, 10, 2.125], [10, -50, 12.07],
  [38, 5, 0.237], [38, -20, 7.313], [38, 100, 13.77], [8, -100, 1234.567]]) {
  const s = state(speed, battery, time), production = createCanvas(R.W, R.H), reference = createCanvas(R.W, R.H);
  R.reset(); render(production, s, time);
  const preserved = compareRegions(s, time, { speed: speed || 0, travel: 0.33 });
  const d = { maximum: preserved.available ? 0 : null, mean: preserved.available ? 0 : null, changed: 0 };
  const layered = difference(production, renderLayers(s, time));
  assert(layered.maximum <= 10 && layered.mean < 0.01, `Layer alpha quantization changed material: ${JSON.stringify(layered)}`);
  const stats = R.cacheStats();
  assert(stats.dustComputed <= stats.dustNominal);
  if (stats.dustNominal) assert(stats.dustComputed < stats.dustNominal, 'Bound culling/shared motion must avoid redundant computations');
  if (speed === 8 && battery === 0) assert(stats.dustComputed < stats.dustNominal * 0.3, 'Short beam must cull only mathematically invisible particles before trigonometry');
  parity.push({ speed, battery, time, ...d, preserved, layered, dustNominal: stats.dustNominal, dustComputed: stats.dustComputed });
}

// Local vehicle evidence is optional in clean checkouts. When available, replay
// each captured beginning/end state without contacting the central. Fallback
// low-power states still exercise both signs and very short smoke frontiers.
const driveCases = [];
for (let sample = 1; sample <= 6; sample++) {
  const file = path.join(evidenceRoot, `deploy-102-drive-${sample}.json`);
  if (!fs.existsSync(file)) continue;
  const capture = JSON.parse(fs.readFileSync(file, 'utf8'));
  for (const endpoint of ['begin', 'end']) {
    const value = capture.samples[endpoint], d = value.state.decoded;
    const s = state(d.speed, d.powerKw, value.at / 1000); s.readouts = d;
    driveCases.push({ name: `drive-${sample}-${endpoint}`, s, time: value.at / 1000, travel: value.status.travel });
  }
}
for (const [speed, power, time] of [[3, 2.04, 0.031], [8, -0.1, 0.511], [19, 0.1, 3.017]]) {
  driveCases.push({ name: 'low-power', s: state(speed, power, time), time, travel: 0.33 });
}
const driveParity = [], driveTargets = [createCanvas(R.W, R.H), createCanvas(R.W, R.H)];
for (const value of driveCases) {
  R.reset(); frozenBrushDraws = 0;
  const motion = { speed: value.s.speedRaw || 0, travel: value.travel };
  render(driveTargets[0], value.s, value.time, { starMotion: motion });
  const smoke = Smoke.stats(), stars = G.stats();
  const preserved = compareRegions(value.s, value.time, motion);
  const d = { maximum: preserved.available ? 0 : null, mean: preserved.available ? 0 : null, changed: 0 };
  const brushAfter = smoke.left.drawn + smoke.right.drawn;
  const brushBefore = smoke.left.nominal + smoke.right.nominal;
  if (Frozen && VisualBefore !== Frozen) {
    frozenBrushDraws = 0; Frozen.renderBeams(driveTargets[1].getContext('2d'), value.time, 'live', { state: value.s });
    assert.equal(frozenBrushDraws, brushBefore, 'Frozen brush baseline must agree with the measured command stream');
  }
  assert.equal(stars.culled + stars.sampled, G.COUNT);
  assert(stars.culled > G.COUNT * 0.4, 'Outside-box stars must bypass the heavy visibility path');
  assert(brushAfter < brushBefore, 'Only invisible wisp draws should be omitted');
  driveParity.push({ name: value.name, speed: value.s.speed, power: value.s.powerKw, ...d, preserved,
    brushBefore, brushAfter, wispComputed: smoke.left.computed + smoke.right.computed,
    starsBefore: G.COUNT, starsSampled: stars.sampled, starsVisible: stars.visible });
}
{
  for (const options of [{ speed: 0, travel: 0 }, { speed: 19, travel: 3.36 }, { speed: 240, travel: 1234.567 }]) {
    let footer = 0, lastRows = 0;
    for (let i = 0; i < G.COUNT; i++) {
      const current = G.sampleStar(i, options);
      if (FrozenStars) {
        const previous = FrozenStars.sampleStar(i, options);
        if (current.y < 475) assert.deepEqual(current, previous, 'Upper public star samples must remain identical');
        else for (const key of Object.keys(previous)) if (key !== 'alpha' && key !== 'visible') {
          assert.equal(current[key], previous[key], 'Lower sky must retain seed, geometry and motion');
        }
      }
      if (current.visible && current.y >= 620 && current.y < 720) footer++;
      if (current.visible && current.y >= 700 && current.y < 720) lastRows++;
    }
    assert(footer > 10 && lastRows > 0, 'The existing star field must continue through the footer and last rows');
  }
}

R.reset(); assert.equal(R.setMaterialHz(4), 30);
const c = createCanvas(R.W, R.H), s = state(100, 50);
render(c, s, 0);
const allocated = owned.length, starFrames = new Set(), beamFrames = new Set();
const hash = (x, y, w, h) => createHash('sha256').update(c.getContext('2d').getImageData(x, y, w, h).data).digest('hex');
for (let i = 1; i <= 12; i++) {
  const time = i / 30;
  render(c, s, time, { starMotion: { speed: 100, travel: 0.33 + time * G.speedRate(100) } });
  starFrames.add(hash(0, 25, R.W, 205));
  beamFrames.add(hash(0, 250, 780, 470));
}
assert.equal(starFrames.size, 12, 'Star positions must change at every moving frame');
assert.equal(beamFrames.size, 12, 'Smoke/dust phase must change at every moving frame');
assert.deepEqual([R.cacheStats().stars, R.cacheStats().left, R.cacheStats().right], [13, 13, 13]);
assert.equal(owned.length, allocated, 'Moving composition must reuse every immutable material asset');
const next = state(160, -80);
render(c, next, 0.5);
assert.deepEqual([R.cacheStats().left, R.cacheStats().right], [14, 14], 'Latest geometry must paint immediately');
render(c, absent, 0.51);
assert.equal(R.cacheStats().left, 14); assert.equal(R.cacheStats().right, 14);

const layeredMotion = [], layeredStars = new Set(), layeredBeams = new Set();
for (let i = 1; i <= 12; i++) {
  const time = 0.6 + i / 30, motion = { speed: 100, travel: 0.33 + time * G.speedRate(100) };
  render(c, s, time, { starMotion: motion });
  const d = difference(c, renderLayers(s, time, { starMotion: motion }));
  assert(d.maximum <= 10 && d.mean < 0.01, `Moving layer composition diverged: ${JSON.stringify(d)}`);
  if (d.above8) assert.equal(geometryTrace(s, time, motion, false), geometryTrace(s, time, motion, true), 'Quantization outlier must retain identical painting geometry');
  layeredStars.add(createHash('sha256').update(layers[0].getContext('2d').getImageData(0, 25, R.W, 205).data).digest('hex'));
  layeredBeams.add(createHash('sha256').update(layers[1].getContext('2d').getImageData(0, 250, 780, 470).data).digest('hex'));
  layeredMotion.push(d);
}
assert.equal(layeredStars.size, 12); assert.equal(layeredBeams.size, 12);
assert.equal(owned.length, allocated, 'Layer seams must not allocate new internal surfaces');

// A seam clears its own target by default, and clear:false preserves it for
// single-surface composition. The caller owns these three layer surfaces.
const beamsCtx = layers[1].getContext('2d');
beamsCtx.fillStyle = '#f0f'; beamsCtx.fillRect(0, 0, R.W, R.H);
R.renderBeams(beamsCtx, 1, 'live', { state: absent, clear: false });
assert.equal(beamsCtx.getImageData(0, 0, 1, 1).data[3], 255);
R.renderBeams(beamsCtx, 1, 'live', { state: absent });
assert(beamsCtx.getImageData(0, 0, R.W, R.H).data.every(v => v === 0));
const foregroundCtx = layers[2].getContext('2d');
foregroundCtx.fillStyle = '#f0f'; foregroundCtx.fillRect(0, 0, R.W, R.H);
R.renderForeground(foregroundCtx, 1, 'live', { state: absent, clear: false });
assert.equal(foregroundCtx.getImageData(0, 0, 1, 1).data[3], 255);
R.renderForeground(foregroundCtx, 1, 'live', { state: absent, menuState: { card: 1 } });
assert.equal(foregroundCtx.getImageData(0, 0, 1, 1).data[3], 0); assert.equal(absent.menuOpen, true);
R.renderForeground(foregroundCtx, 1, 'live', { state: absent, menuState: { card: 3 } }); assert.equal(absent.menuOpen, true);
R.renderForeground(foregroundCtx, 1, 'live', { state: absent, menuState: { card: 0 } }); assert.equal(absent.menuOpen, false);

// TextMetrics fallback corresponds to older Android WebViews that provide width.
const ctx = c.getContext('2d'), labels = [];
const legacyMetrics = new Proxy(ctx, {
  get(o, k) {
    if (k === 'measureText') return text => ({ width: o.measureText(text).width });
    if (k === 'fillText') return (...args) => { assert(Number.isFinite(args[1]) && Number.isFinite(args[2])); labels.push(args[0]); return o.fillText(...args); };
    return typeof o[k] === 'function' ? o[k].bind(o) : o[k];
  }, set(o, k, v) { o[k] = v; return true; }
});
for (const speed of [null, 0, 160, 240, 999]) {
  const metrics = R.drawSpeed(legacyMetrics, speed);
  assert(Number.isFinite(metrics.width) && metrics.width <= 203);
}
assert(labels.includes('--')); assert(labels.includes('240'));

// Flush native deferred Canvas draws. Software Canvas timings are recorded,
// not used to freeze/gate movement or claimed as the Android GPU budget.
R.reset(); render(c, s, 0); ctx.getImageData(0, 0, 1, 1);
const timings = [];
for (let i = 1; i <= 4; i++) {
  const moved = state(95 + i, 46 + i), start = performance.now();
  render(c, moved, i / 30, { starMotion: { speed: moved.speedRaw, travel: 0.33 + i / 30 * G.speedRate(moved.speedRaw) } });
  ctx.getImageData(0, 0, 1, 1); timings.push(performance.now() - start);
}
const benchmark = { meanMs: timings.reduce((a, b) => a + b, 0) / timings.length, maxMs: Math.max(...timings) };
const budget = R.cacheStats();
assert.equal(budget.materialHz, 30); assert.equal(budget.materialMotionReduced, false);
const beforeAfter = [];
if (Frozen) {
  // Command-count proxies are useful for parity evidence, but cannot be kept
  // in a fair timing comparison against the unwrapped production contexts.
  Frozen.setCanvasFactory(createCanvas);
  for (const value of driveCases.filter(x => ['drive-1-begin', 'drive-2-begin', 'drive-3-end', 'low-power'].includes(x.name))) {
    const motion = { speed: value.s.speedRaw || 0, travel: value.travel }, options = { state: value.s, starMotion: motion };
    R.render(driveTargets[0].getContext('2d'), value.time, 'live', options);
    Frozen.render(driveTargets[1].getContext('2d'), value.time, 'live', options);
    driveTargets[0].getContext('2d').getImageData(0, 0, 1, 1); driveTargets[1].getContext('2d').getImageData(0, 0, 1, 1);
    const times = { before: [], after: [] };
    for (let i = 0; i < 3; i++) for (const key of ['before', 'after']) {
      const painter = key === 'before' ? Frozen : R, target = driveTargets[key === 'before' ? 1 : 0], start = performance.now();
      painter.render(target.getContext('2d'), value.time + i / 30, 'live', options);
      target.getContext('2d').getImageData(0, 0, 1, 1); times[key].push(performance.now() - start);
    }
    beforeAfter.push({ name: value.name, speed: value.s.speed, power: value.s.powerKw,
      beforeMs: times.before.reduce((a, b) => a + b, 0) / times.before.length,
      afterMs: times.after.reduce((a, b) => a + b, 0) / times.after.length });
  }
}
R.cleanup(); assert.equal(R.cacheStats().allocated, 0);
if (Frozen) Frozen.cleanup();
assert(owned.every(canvas => canvas.width === 1 && canvas.height === 1), 'Cleanup must release every owned surface');
for (const file of ['renderer.js', 'scales.js', 'galaxy-stars.js', 'glass-smoke.js']) {
  const source = fs.readFileSync(path.join(root, 'src', file), 'utf8');
  assert(!/Object\.hasOwn\(|\.at\(-1\)/.test(source), `${file} contains unsupported built-ins`);
}
console.log(JSON.stringify({ renderer: 'PASS', dimensions: [R.W, R.H], tipChecks, visualReferenceAvailable: !!VisualBefore, parity, frozen102Available: !!Frozen, driveParity, beforeAfter, continuousFrames: { stars: starFrames.size, beams: beamFrames.size, layeredStars: layeredStars.size, layeredBeams: layeredBeams.size }, layeredMotion, benchmark, budget,
  limits: 'Node Canvas only; Android API28/OEM composition and physical performance remain unverified.' }, null, 2));
