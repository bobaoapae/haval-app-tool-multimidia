'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createCanvas, GlobalFonts } = require('@napi-rs/canvas');
const G = require('./galaxy-stars');
const Scale = require('./scales');
const out = path.join(__dirname, 'qa', 'galaxy');
fs.mkdirSync(out, { recursive: true });
let assertions = 0;
function ok(value, message) { assert(value, message); assertions++; }
function equal(a, b, message) { assert.equal(a, b, message); assertions++; }
function near(a, b, epsilon = 1e-9) { ok(Math.abs(a - b) <= epsilon, `${a} != ${b}`); }
function deep(a, b) { assert.deepEqual(a, b); assertions++; }
for (const input of [0, -3, NaN, undefined, Infinity, -Infinity]) equal(G.speedRate(input), 0);
let last = 0;
for (let speed = 1; speed <= 360; speed++) { ok(G.speedRate(speed) > last); last = G.speedRate(speed); }
equal(G.speedRate(900), G.speedRate(360));
// Owner v9 contract: stationary at zero, then progressively faster outward
// motion with speed. Check the actual projected displacement of the SAME
// stars from the SAME depth phase, not just different-looking snapshots.
const requestedSpeeds = [0, 0.1, 0.5, 1, 5, 10, 20, 30, 40, 50, 60, 80, 100, 120, 140, 160, 200];
const motionProbes = Array.from({ length: G.COUNT }, (_, i) => G.sampleStar(i, { speed: 0, travel: 0.5 }))
  .filter(p => p.visible && p.alpha > 0.2 && p.z > 1 && Math.hypot(p.x - G.VANISH_X, p.y - G.VANISH_Y) > 20)
  .slice(0, 96);
equal(motionProbes.length, 96, 'enough visible, non-wrapping displacement probes');
const outwardMotion = [];
let previousMean = -1;
for (const speed of requestedSpeeds) {
  near(G.speedRate(speed), speed * 0.0032, 1e-12);
  let totalDistance = 0;
  for (const p of motionProbes) {
    const next = G.sampleStar(p.index, { speed, travel: 0.5 + G.integrateTravel(() => speed, 1 / 60) });
    const dx = next.x - p.x, dy = next.y - p.y;
    const distance = Math.hypot(dx, dy);
    if (speed === 0) {
      equal(distance, 0); equal(next.radialSpeed, 0); equal(next.streakLength, 0);
      deep(next, G.sampleStar(p.index, { speed: 0, travel: 0.5, time: 3600 }));
    } else {
      ok(distance > 0, `positive travel even at ${speed} km/h`);
      ok(dx * (p.x - G.VANISH_X) + dy * (p.y - G.VANISH_Y) > 0, 'outward radial direction');
      near(dx * (p.y - G.VANISH_Y) - dy * (p.x - G.VANISH_X), 0, 1e-7);
    }
    totalDistance += distance;
  }
  const meanDisplacementPxPerFrame = totalDistance / motionProbes.length;
  ok(meanDisplacementPxPerFrame > previousMean, 'same-depth motion gets progressively faster');
  previousMean = meanDisplacementPxPerFrame;
  outwardMotion.push({ speedKmh: speed, depthRatePerSecond: G.speedRate(speed), meanDisplacementPxPerFrame });
}
for (const speed of [0, 30, 80, 150]) {
  const integrated = G.createTravelSampler(() => speed, 16);
  for (const t of [0, 0.001, 2.123, 5, 10.99, 16]) near(integrated(t), G.speedRate(speed) * t);
  near(G.integrateTravel(() => speed, 4.751, 16), integrated(4.751));
  equal(integrated(-1), 0); near(integrated(18), integrated(16));
}
for (const t of [0, 0.001, 1.913, 9.999, 10]) {
  const ramp = G.createTravelSampler(t => 15 * t, 10);
  near(ramp(t), 0.0032 * 15 * t * t / 2);
}
equal(G.createTravelSampler(() => 100, 0)(7), 0);
assert.throws(() => G.createTravelSampler(null, 16), TypeError); assertions++;
assert.throws(() => G.createTravelSampler(() => 20, Infinity), RangeError); assertions++;
// Explicit acceleration / coasting / regen / stop telemetry; power is neither
// an input to the module nor a source of travel or streak length.
function speed(t) {
  if (t <= 4) return t * 25;
  if (t <= 6) return 100;
  if (t <= 10) return 100 - (t - 6) * 25;
  return 0;
}
const travelAt = G.createTravelSampler(speed, 12);
near(travelAt(4), 0.64); near(travelAt(6), 1.28); near(travelAt(10), 1.92);
near(travelAt(11), travelAt(10)); near(travelAt(12), travelAt(10));
near(travelAt(5.5) - travelAt(5), 0.16);
ok(travelAt(8.5) - travelAt(8) < travelAt(7.5) - travelAt(7), 'regen slows motion as speed falls');
for (let i = 1; i <= 1440; i++) ok(travelAt(i / 120) >= travelAt((i - 1) / 120));
for (const knot of [4, 6, 10]) {
  ok(Math.abs(travelAt(knot + 1e-6) - travelAt(knot - 1e-6)) < 1e-6, 'continuous travel at telemetry knots');
}
// Full stop/start/coast/regen/stop/restart track. Zero intervals never add
// distance; restart resumes from the accumulated depth instead of resetting.
function stopStartSpeed(t) {
  if (t <= 2) return 0;
  if (t <= 6) return (t - 2) * 25;
  if (t <= 8) return 100;
  if (t <= 12) return 100 - (t - 8) * 25;
  if (t <= 14) return 0;
  return (t - 14) * 20;
}
const stopStartTravel = G.createTravelSampler(stopStartSpeed, 16);
near(stopStartTravel(0), 0); near(stopStartTravel(2), 0);
near(stopStartTravel(6), 0.64); near(stopStartTravel(8), 1.28);
near(stopStartTravel(12), 1.92); near(stopStartTravel(14), 1.92);
near(stopStartTravel(16), 2.048);
for (const t of [0.1, 1, 1.99]) equal(stopStartTravel(t), 0);
for (const t of [12.001, 12.5, 13.9]) near(stopStartTravel(t), stopStartTravel(12));
for (const knot of [2, 6, 8, 12, 14]) {
  ok(Math.abs(stopStartTravel(knot + 1e-6) - stopStartTravel(knot - 1e-6)) < 1e-6, 'stop/start depth is continuous');
}
ok(stopStartTravel(14.01) > stopStartTravel(14), 'restart resumes outward travel');
let radialStars = 0, fadeFar = 0, fadeNear = 0, maximumStreak = 0;
for (let i = 0; i < G.COUNT; i++) {
  const a = G.sampleStar(i, { speed: 30, travel: 0.1 });
  const b = G.sampleStar(i, { speed: 150, travel: 0.1 });
  const stopped = G.sampleStar(i, { speed: 0, travel: 0.1 });
  equal(stopped.streakLength, 0); equal(stopped.radialSpeed, 0);
  near(a.x, b.x); near(a.y, b.y); near(b.radialSpeed, a.radialSpeed * 5);
  ok(a.streakLength <= b.streakLength); ok(b.streakLength <= 14);
  maximumStreak = Math.max(maximumStreak, b.streakLength);
  deep(a, G.sampleStar(i, { time: 999, speed: 30, travel: 0.1, powerKw: -80 }));
  deep(a, G.sampleStar(i, { time: 0, speed: 30, travel: 0.1, powerKw: 160 }));
  const next = G.sampleStar(i, { speed: 30, travel: 0.1 + G.speedRate(30) / 120 });
  if (next.z < a.z && a.visible && next.visible) {
    ok(Math.hypot(next.x - 960, next.y - 260) > Math.hypot(a.x - 960, a.y - 260));
    near((next.x - 960) * (a.y - 260) - (next.y - 260) * (a.x - 960), 0, 1e-7);
    radialStars++;
  }
  const initial = G.sampleStar(i, { travel: 0 });
  const boundaryTravel = G.DEPTH_SPAN - initial.phase;
  const before = G.sampleStar(i, { travel: boundaryTravel - 1e-7 });
  const after = G.sampleStar(i, { travel: boundaryTravel + 1e-7 });
  ok(before.alpha < 1e-9); ok(after.alpha < 1e-9);
  if (before.depthFade < 1e-9) fadeNear++;
  if (after.depthFade < 1e-9) fadeFar++;
}
ok(radialStars > 250); equal(fadeFar, G.COUNT); equal(fadeNear, G.COUNT);
equal(G.visibilityMask(450, 565), 0); equal(G.visibilityMask(960, 710), 0);
// Preserve open sky between actual glyph runs: no full-width header or
// lateral-scale suppression. These are deliberately outside text bounds.
for (const [x, y] of [[600, 100], [1200, 100], [450, 200], [130, 250], [1730, 230], [300, 330]]) {
  ok(G.visibilityMask(x, y) > 0.98, `open sky at ${x},${y}`);
}
for (const r of G.READABILITY_REGIONS) {
  for (const dx of [-r.halfWidth, 0, r.halfWidth]) for (const dy of [-r.halfHeight, 0, r.halfHeight]) {
    ok(G.visibilityMask(r.x + dx, r.y + dy) < 1e-12, `glyph protection ${r.id}`);
  }
}
// Shared geometry prevents stale guards when scale spacing, perspective or
// foreground font size changes. Every rotated corner must remain protected.
for (const [name, ticks] of [['speed', Scale.leftTicks], ['battery', Scale.rightTicks]]) {
  const regions = G.READABILITY_REGIONS.filter(r => r.id.startsWith(name + '-') && /\d$/.test(r.id));
  equal(regions.length, ticks.length);
  for (const tick of ticks) {
    const r = regions.find(r => r.id === `${name}-${tick.value}`);
    ok(r, `guard exists for ${name} ${tick.value}`);
    equal(r.x, tick.x); equal(r.y, tick.y);
    ok(r.halfWidth >= String(tick.value).length * tick.size * 0.3);
    near(r.angle, tick.angle || 0);
    const c = Math.cos(r.angle), s = Math.sin(r.angle);
    for (const localX of [-r.localHalfWidth, r.localHalfWidth]) for (const localY of [-r.localHalfHeight, r.localHalfHeight]) {
      const dx = c * localX - s * localY, dy = s * localX + c * localY;
      ok(Math.abs(dx) <= r.halfWidth + 1e-9 && Math.abs(dy) <= r.halfHeight + 1e-9, 'rotated glyph corner enclosed');
      ok(G.visibilityMask(r.x + dx, r.y + dy) < 1e-12, 'rotated glyph corner is clear of stars');
    }
  }
}
ok(!G.READABILITY_REGIONS.some(r => r.id.startsWith('traction-')), 'left scale protects speed, not traction');
equal(G.visibilityMask(960, 310), 0);
for (let degree = 0; degree < 360; degree += 5) {
  const rad = degree * Math.PI / 180;
  near(G.visibilityMask(960 + 160 * Math.cos(rad), 310 + 160 * Math.sin(rad)), 0);
}
ok(G.visibilityMask(440, 245) > 0.98);
equal(G.visibilityMask(440, 245, 0, 440, 267), 0, 'radial tail cannot cross a glyph');
equal(G.visibilityMask(440, 257, 7), 0, 'halo footprint cannot cross a glyph');
const skyExamples = [];
let maxSubframeAlphaChange = 0;
for (const travel of [0, 0.384, 0.7, 1.2]) {
  const points = Array.from({ length: G.COUNT }, (_, i) => G.sampleStar(i, { speed: 100, travel }));
  const upper = points.filter(s => s.visible && s.y < 240);
  const bright = upper.filter(s => s.alpha > 0.3).length;
  const large = upper.filter(s => s.radius > 1.4 && s.alpha > 0.3).length;
  const small = upper.filter(s => s.radius < 0.65).length;
  ok(bright > 280, 'upper sky has a clearly visible point population');
  ok(large > 24 && small > 250, 'sparse large stars retain a fine-point background');
  for (const p of points) {
    const next = G.sampleStar(p.index, { speed: 100, travel: travel + 0.000001 });
    maxSubframeAlphaChange = Math.max(maxSubframeAlphaChange, Math.abs(next.alpha - p.alpha));
  }
  skyExamples.push({ travel, visibleUpperPoints: upper.length, brightUpperPoints: bright, largerUpperPoints: large, fineUpperPoints: small });
}
ok(maxSubframeAlphaChange < 0.0002, 'local masks and depth fades are continuous at subframe travel');
const browser = { }; vm.createContext(browser);
vm.runInContext(fs.readFileSync(path.join(__dirname, 'scales.js'), 'utf8'), browser);
vm.runInContext(fs.readFileSync(path.join(__dirname, 'galaxy-stars.js'), 'utf8'), browser);
equal(browser.GalaxyStars.COUNT, G.COUNT);
near(browser.GalaxyStars.sampleStar(17, { speed: 50, travel: 1.25 }).x, G.sampleStar(17, { speed: 50, travel: 1.25 }).x);
equal(JSON.stringify(browser.GalaxyStars.READABILITY_REGIONS), JSON.stringify(G.READABILITY_REGIONS), 'browser shares exact tick masks');
const canvas = createCanvas(1920, 720), ctx = canvas.getContext('2d');
function frame(options) {
  ctx.fillStyle = '#020407'; ctx.fillRect(0, 0, 1920, 720);
  G.render(ctx, options);
  return canvas.toBuffer('image/png');
}
const first = frame({ time: 7.35, speed: speed(7.35), travel: travelAt(7.35) });
frame({ time: 9.17, speed: speed(9.17), travel: travelAt(9.17) });
frame({ time: 1.27, speed: speed(1.27), travel: travelAt(1.27) });
ok(first.equals(frame({ time: 7.35, speed: speed(7.35), travel: travelAt(7.35) })), 'byte-identical backward seek');
ok(first.equals(frame({ time: 7.35, speed: speed(7.35), travel: travelAt(7.35), powerKw: 160 })), 'power independent');
const stoppedFrame = frame({ time: 0, speed: 0, travel: travelAt(10) });
ok(stoppedFrame.equals(frame({ time: 900, speed: 0, travel: travelAt(12) })), 'stopped field has no motion or twinkle');
const stoppedAtStart = frame({ time: 0, speed: 0, travel: stopStartTravel(0) });
ok(stoppedAtStart.equals(frame({ time: 1.99, speed: 0, travel: stopStartTravel(1.99) })), 'initial zero-speed field is subtly static');
const stoppedAfterRegen = frame({ time: 12, speed: 0, travel: stopStartTravel(12) });
ok(stoppedAfterRegen.equals(frame({ time: 13.9, speed: 0, travel: stopStartTravel(13.9) })), 'post-regen stop preserves the exact field');
ok(!stoppedAfterRegen.equals(frame({ time: 14.5, speed: stopStartSpeed(14.5), travel: stopStartTravel(14.5) })), 'restart moves the existing field');
ok(!frame({ time: 5, speed: 100, travel: travelAt(5), powerKw: 0 }).equals(frame({ time: 5.25, speed: 100, travel: travelAt(5.25), powerKw: 0 })), 'coasting still moves');
// Independent raster guard: capture actual renderer glyphs (all fonts,
// alignments and transforms), then test the galaxy's points, halos AND trails
// against their alpha footprints with two pixels of readable clearance.
for (const [file, family] of [['SpeedDigits.ttf', 'Speed Digits'], ['PanelSans.ttf', 'Panel Sans'], ['LabelSans.ttf', 'Label Sans'], ['ScaleDigits.ttf', 'Scale Digits'], ['HeaderSans.ttf', 'Header Sans']]) {
  GlobalFonts.registerFromPath(path.join(__dirname, 'assets', file), family);
}
const R = require('./renderer'); R.setCanvasFactory(createCanvas);
equal(R.duration('city'), 50);
const cityTravel = G.createTravelSampler(t => R.sample(t, 'city').speedRaw, R.duration('city'));
for (const time of [0, 1, 2, 4, 7, 12, 18, 27, 31, 32, 35, 40, 42, 46, 48, 49, 50]) {
  const motion = R.starMotion(time, 'city');
  equal(motion.speed, R.sample(time, 'city').speedRaw);
  near(motion.travel, cityTravel(time));
}
const cityInitial = frame(R.starMotion(0, 'city'));
ok(cityInitial.equals(frame(R.starMotion(1.99, 'city'))), 'city initial stop has no drift');
ok(frame(R.starMotion(48, 'city')).equals(frame(R.starMotion(50, 'city'))), 'city final stop has no drift');
equal(R.sample(31.5, 'city').batteryPowerKw, 0);
equal(R.sample(31.5, 'city').tractionKw, 0);
equal(R.sample(31.5, 'city').speedRaw, 200);
ok(R.sample(31.5, 'city').left > 0, 'left speed ribbon persists during zero-traction coast');
ok(!frame(R.starMotion(31.25, 'city')).equals(frame(R.starMotion(31.5, 'city'))), 'city zero-power coasting still moves');
ok(R.sample(41, 'city').batteryPowerKw < 0 && R.sample(42, 'city').batteryPowerKw < 0, 'city regen stage exists');
ok(R.starMotion(41.2, 'city').travel - R.starMotion(41, 'city').travel >
  R.starMotion(42.2, 'city').travel - R.starMotion(42, 'city').travel, 'city regen motion slows with vehicle speed');
const panel = createCanvas(1920, 720), panelCtx = panel.getContext('2d');
const glyphs = createCanvas(1920, 720), glyphCtx = glyphs.getContext('2d');
const transparent = createCanvas(1920, 720), starCtx = transparent.getContext('2d');
const originalFillText = panelCtx.fillText.bind(panelCtx);
let rotatedGlyphCalls = [];
panelCtx.fillText = function (text, x, y, ...rest) {
  const m = panelCtx.getTransform();
  if (Math.abs(m.b) > 1e-6 || Math.abs(m.c) > 1e-6) {
    rotatedGlyphCalls.push({ text: String(text), anchorX: m.e, anchorY: m.f, angle: Math.atan2(m.b, m.a), font: panelCtx.font });
  }
  glyphCtx.save(); glyphCtx.setTransform(m.a, m.b, m.c, m.d, m.e, m.f);
  for (const key of ['font', 'textAlign', 'textBaseline']) glyphCtx[key] = panelCtx[key];
  glyphCtx.fillStyle = '#fff'; glyphCtx.fillText(text, x, y, ...rest); glyphCtx.restore();
  return originalFillText(text, x, y, ...rest);
};
const glyphChecks = [];
for (const profile of ['demo', 'unavailable']) {
  R.setDataProfile(profile);
  for (const [mode, times] of [['drive', [0, 4, 7.5, 10.5, 13, 16]],
    ['city', [0, 1, 2, 4, 7, 9.5, 12, 15, 18, 21, 24, 27, 31, 32, 35, 38, 40, 42, 44, 46, 48, 49, 50]]]) {
   for (const time of times) {
    rotatedGlyphCalls = [];
    glyphCtx.clearRect(0, 0, 1920, 720); starCtx.clearRect(0, 0, 1920, 720);
    R.renderPanel(panelCtx, time, mode);
    G.render(starCtx, R.starMotion(time, mode));
    equal(rotatedGlyphCalls.length, Scale.leftTicks.length + Scale.rightTicks.length, 'all rotated scale labels captured at native size');
    for (const tick of [...Scale.leftTicks, ...Scale.rightTicks]) {
      // Native Canvas stores transforms in float32; allow <1/1000 px error.
      const glyph = rotatedGlyphCalls.find(g => g.text === String(tick.value) && Math.abs(g.anchorX - tick.x) < 0.001 && Math.abs(g.anchorY - tick.y) < 0.001);
      ok(glyph, `actual rotated glyph captured at ${tick.x},${tick.y}`);
      near(glyph.angle, tick.angle || 0, 1e-6);
      equal(Number.parseFloat(glyph.font), tick.size);
    }
    const textPixels = glyphCtx.getImageData(0, 0, 1920, 720).data;
    const starPixels = starCtx.getImageData(0, 0, 1920, 720).data;
    let overlappingPixels = 0, glyphPixels = 0;
    for (let y = 2; y < 718; y++) for (let x = 2; x < 1918; x++) {
      if (textPixels[(y * 1920 + x) * 4 + 3] < 24) continue;
      glyphPixels++;
      let hit = false;
      for (let dy = -2; dy <= 2 && !hit; dy++) for (let dx = -2; dx <= 2; dx++) {
        if (starPixels[((y + dy) * 1920 + x + dx) * 4 + 3] > 0) { hit = true; break; }
      }
      if (hit) overlappingPixels++;
    }
    equal(overlappingPixels, 0, `no galaxy within actual glyphs + 2px at ${profile}/${mode}/${time}s`);
    ok(glyphPixels > 10000, 'nonempty actual renderer glyph stencil');
    glyphChecks.push({ profile, mode, time, glyphPixels, overlappingPixels, rotatedScaleGlyphs: rotatedGlyphCalls.length });
    if (profile === 'demo' && mode === 'drive' && time === 4) {
      fs.writeFileSync(path.join(out, 'readability-panel.png'), panel.toBuffer('image/png'));
      fs.writeFileSync(path.join(out, 'readability-glyph-stencil.png'), glyphs.toBuffer('image/png'));
      fs.writeFileSync(path.join(out, 'readability-star-stencil.png'), transparent.toBuffer('image/png'));
    }
    if (profile === 'demo' && mode === 'city' && [0, 31, 42, 49].includes(time)) {
      fs.writeFileSync(path.join(out, `readability-city-${time}.png`), panel.toBuffer('image/png'));
    }
   }
  }
}
R.setDataProfile('demo');
const examples = [];
const contact = createCanvas(1280, 1560), cc = contact.getContext('2d');
cc.fillStyle = '#020407'; cc.fillRect(0, 0, contact.width, contact.height);
for (const [row, v] of [0, 30, 150].entries()) {
  const travel = G.integrateTravel(() => v, 4, 4);
  const png = frame({ time: 4, speed: v, travel });
  const filename = `speed-${v}.png`; fs.writeFileSync(path.join(out, filename), png);
  cc.drawImage(canvas, 0, row * 520 + 40, 1280, 480);
  cc.fillStyle = '#adbcd0'; cc.font = '20px sans-serif';
  cc.fillText(`${v} km/h  |  depth rate ${G.speedRate(v).toFixed(3)} / s  |  t = 4 s`, 30, row * 520 + 29);
  const visible = Array.from({ length: G.COUNT }, (_, i) => G.sampleStar(i, { speed: v, travel })).filter(s => s.visible);
  examples.push({ speedKmh: v, travel, visiblePoints: visible.length,
    maxVisibleStreakPx: Math.max(...visible.map(s => s.streakLength)), file: filename });
}
fs.writeFileSync(path.join(out, 'speed-comparison.png'), contact.toBuffer('image/png'));
const report = {
  result: 'PASS', assertions, particleCount: G.COUNT, radialStarsChecked: radialStars,
  zeroSpeed: 'frozen depth, no streaks, no time-driven twinkle',
  outwardMotion, stopStartRestart: 'initial and post-regen stops are byte-identical; restart resumes continuous accumulated travel',
  coasting: 'motion continues at nonzero speed with zero power',
  regeneration: 'travel rate decreases only as speed falls',
  powerIndependent: true, backwardsSeekByteIdentical: true,
  motion: 'seeded XYZ perspective; depth decreases continuously with integrated speed',
  lifecycle: 'smooth near/far fade on all 2800 particles, no visible wrap flash',
  maximumStreakPx: maximumStreak, boundedAt: G.MAX_STREAK,
  textMask: 'v9 floating-readout bounds and rotated tick AABBs from shared scale geometry; zero lens inside radius 160; footer fades to zero at 565; whole halo/streak footprint guarded',
  cityMode: '50-second actual renderer track checked: initial/final stops, zero-power 200 km/h coast, regen deceleration and speed-based left ribbon',
  skyExamples, glyphChecks, maxSubframeAlphaChange,
  visualClaim: 'regression and readability checks only; count and pixel metrics do not prove source fidelity',
  examples, browserUmd: 'API parity checked in vm',
  nativeCanvas: true, browserWebviewVerified: false, vehicleVerified: false
};
fs.writeFileSync(path.join(out, 'validation.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
