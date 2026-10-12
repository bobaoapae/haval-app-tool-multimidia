'use strict';

// Independent contract tests for the owner-authored preview scales. The endpoints
// are visual limits, not researched or measured ratings of a vehicle.
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { createCanvas, GlobalFonts } = require('@napi-rs/canvas');
const Scale = require('./scales');
const R = require('./renderer');

const SPEED_VALUES = [0, 20, 40, 50, 60, 80, 100, 120, 140, 160, 200];
const SPEED_X = [710, 600, 500, 450, 400, 342, 285, 228.5, 172.5, 117, 50];
const BATTERY_VALUES = [0, 10, 20, 30, 40, 50, 75, 100];
const APEX_DISTANCES = [0, 39, 74, 106, 134, 159, 186, 207.6];
const BATTERY_X = APEX_DISTANCES.map(d => 710 - 660 * d / 207.6);
const failures = [];
let assertions = 0;
const report = { result: 'PASS', groups: [], fontBounds: {}, samples: [] };
function ok(condition, message) { assertions++; assert.ok(condition, message); }
function equal(actual, expected, message) { assertions++; assert.deepEqual(actual, expected, message); }
function near(actual, expected, message, epsilon = 1e-7) {
  ok(Number.isFinite(actual) && Math.abs(actual - expected) <= epsilon,
    `${message}: expected ${expected}, received ${actual}`);
}
function group(name, run) {
  try { run(); report.groups.push({ name, result: 'PASS' }); }
  catch (error) { failures.push({ name, message: error.message }); report.groups.push({ name, result: 'FAIL', message: error.message }); }
}
function expectedX(values, positions, input) {
  const v = Math.max(values[0], Math.min(values.at(-1), input));
  for (let i = 1; i < values.length; i++) if (v <= values[i]) {
    const q = (v - values[i - 1]) / (values[i] - values[i - 1]);
    return positions[i - 1] + (positions[i] - positions[i - 1]) * q;
  }
  return positions.at(-1);
}

group('Exact perspective speed knots and shared renderer export', () => {
  equal(R.Scale, Scale, 'Renderer must consume the same geometry object');
  equal(Scale.leftTicks.map(t => t.value), SPEED_VALUES, 'Exact speed tick set');
  equal(Scale.leftTicks.map(t => t.x), SPEED_X, 'Expected perspective speed knot positions');
  for (let i = 0; i < SPEED_VALUES.length; i++) {
    near(Scale.speedX(SPEED_VALUES[i]), SPEED_X[i], `Speed knot ${SPEED_VALUES[i]}`);
    near(Scale.speedRatio(SPEED_VALUES[i]), (710 - SPEED_X[i]) / 660, `Speed spatial ratio ${SPEED_VALUES[i]}`);
  }
  const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
  ok(html.indexOf('src="scales.js"') >= 0, 'Browser preview includes shared scales');
  ok(html.indexOf('src="scales.js"') < html.indexOf('src="renderer.js"'), 'Scale module loads before renderer');
});

group('Speed monotonicity, interpolation, and low-range allocation', () => {
  let previous = Infinity;
  for (let speed = 0; speed <= 200; speed += .25) {
    const x = Scale.speedX(speed);
    ok(x <= previous, `Speed frontier must move outward monotonically at ${speed}`);
    near(x, expectedX(SPEED_VALUES, SPEED_X, speed), `Interpolated speed ${speed}`);
    near(Scale.speedRatio(speed), (710 - x) / 660, `Interpolated speed ratio ${speed}`);
    previous = x;
  }
  near(Scale.speedX(-1), 710, 'Negative speed clamps to zero');
  near(Scale.speedX(201), 50, 'Above-range speed clamps to endpoint');
  const low = Scale.speedX(0) - Scale.speedX(60);
  const high = Scale.speedX(60) - Scale.speedX(200);
  // Urban expansion is pixels per km/h, not a majority of total scale width.
  ok(low / 60 > 2 * high / 140, 'Low range must have substantially more pixels per km/h');
  report.speedAllocation = { lowRangePx: low, highRangePx: high };
});

group('Gradual high-range compression', () => {
  const ticks = Scale.leftTicks.filter(t => t.value >= 60);
  const densities = ticks.slice(1).map((t, i) => (ticks[i].x - t.x) / (t.value - ticks[i].value));
  report.highRangePixelsPerKmh = densities;
  for (let i = 1; i < densities.length; i++) {
    ok(densities[i] < densities[i - 1], 'Pixels/km/h must decrease throughout 60–200');
    // A visual QA tolerance: neighboring segment densities retain at least 60%
    // of their predecessor. This guards an abrupt last-interval squeeze while
    // allowing piecewise-linear authored geometry and the missing 180 label.
    ok(densities[i] / densities[i - 1] >= .60,
      `Abrupt compression at ${ticks[i].value}–${ticks[i + 1].value}: density changes ${densities[i - 1]} → ${densities[i]}`);
  }
});

group('Exact normalized Apex battery knots, both signs, fixed scale', () => {
  equal(Scale.rightTicks.map(t => t.value), BATTERY_VALUES, 'Fixed magnitude-only battery tick set');
  for (let i = 0; i < BATTERY_VALUES.length; i++) {
    near(Scale.rightTicks[i].x, 1920 - BATTERY_X[i], `Battery tick ${BATTERY_VALUES[i]}`);
    for (const sign of [-1, 1]) {
      near(Scale.batteryX(sign * BATTERY_VALUES[i]), BATTERY_X[i], `Signed battery knot ${sign * BATTERY_VALUES[i]}`);
      near(Scale.batteryRatio(sign * BATTERY_VALUES[i]), APEX_DISTANCES[i] / 207.6, `Signed battery ratio ${sign * BATTERY_VALUES[i]}`);
    }
  }
  let previous = Infinity;
  for (let magnitude = 0; magnitude <= 100; magnitude += .25) {
    const expected = expectedX(BATTERY_VALUES, BATTERY_X, magnitude);
    ok(Scale.batteryX(magnitude) <= previous, `Battery extent monotonic at ${magnitude}`);
    near(Scale.batteryX(magnitude), expected, `Battery interpolation +${magnitude}`);
    near(Scale.batteryX(-magnitude), expected, `Battery interpolation −${magnitude}`);
    near(Scale.batteryRatio(magnitude), Scale.batteryRatio(-magnitude), 'Sign-independent extent');
    previous = Scale.batteryX(magnitude);
  }
  near(Scale.batteryX(101), 50, 'Positive visual endpoint clamp');
  near(Scale.batteryX(-101), 50, 'Negative visual endpoint clamp');
});

group('Functional frontier/label alignment and channel independence', () => {
  for (const speed of SPEED_VALUES) for (const tractionKw of [0, 80, 160]) {
    for (const batteryPowerKw of [-100, -75, -50, -10, 0, 10, 50, 75, 100]) {
      const state = R.functionalState({ speedRaw: speed, tractionKw, batteryPowerKw });
      near(state.leftLength, 780 - Scale.speedX(speed), `Left extent ${speed} km/h, traction ${tractionKw}`);
      near(state.left, Scale.speedRatio(speed) * 8, 'Left spatial level');
      near(state.rightLength, 780 - Scale.batteryX(batteryPowerKw), 'Right extent independent of speed/traction');
      near(state.right, Scale.batteryRatio(batteryPowerKw) * 8, 'Right spatial level');
      equal(state.regen, batteryPowerKw < 0, 'Battery sign controls regeneration');
      equal(state.batteryState, batteryPowerKw < 0 ? 'REGENERANDO' : batteryPowerKw > 0 ? 'CONSUMINDO' : 'NEUTRA', 'Signed battery status');
      near(R.frontierGeometry(state.leftLength, -1).bx, Scale.speedX(speed), 'Left frontier exact tick x');
      near(R.frontierGeometry(state.rightLength, 1).bx, 1920 - Scale.batteryX(batteryPowerKw), 'Right frontier exact tick x');
    }
  }
  for (const tick of Scale.leftTicks) {
    const rim = R.frontierGeometry(780 - Scale.speedX(tick.value), -1);
    const bladeBaseline = Scale.scaleY(tick.x);
    near(rim.scaleY, bladeBaseline, `Left frontier scale height ${tick.value}`);
    near(rim.topY, bladeBaseline - 116, 'Preserved tall glass blade height');
  }
  for (const tick of Scale.rightTicks) {
    const rim = R.frontierGeometry(780 - Scale.batteryX(tick.value), 1);
    const bladeBaseline = Scale.scaleY(1920 - tick.x);
    near(rim.scaleY, bladeBaseline, `Right frontier scale height ${tick.value}`);
    near(rim.topY, bladeBaseline - 116, 'Preserved right glass blade height');
  }
  const zero = R.functionalState({ speedRaw: 60, batteryPowerKw: 0 });
  near(zero.right, 0, 'Zero has no active battery band');
  near(zero.batteryIntensity, 0, 'Zero battery is neutral');
  near(zero.greenIntensity, 0, 'Zero has no regeneration intensity');
});

group('Left red ramp is speed-driven, linear from 120 to 200', () => {
  for (let speed = 0; speed <= 220; speed += .5) {
    const expected = Math.max(0, Math.min(1, (speed - 120) / 80));
    near(R.speedHeat(speed), expected, `Heat ramp ${speed}`);
    for (const tractionKw of [0, 160]) {
      const state = R.functionalState({ speedRaw: speed, tractionKw, batteryPowerKw: -50 });
      near(state.tipHeat, expected, 'Heat independent of traction and battery sign');
    }
  }
});

for (const [file, family] of [
  ['ScaleDigits.ttf', 'Scale Digits'], ['LabelSans.ttf', 'Label Sans'],
  ['PanelSans.ttf', 'Panel Sans'], ['HeaderSans.ttf', 'Header Sans'], ['SpeedDigits.ttf', 'Speed Digits']
]) ok(GlobalFonts.registerFromPath(path.join(__dirname, 'assets', file), family), `Register actual font ${family}`);
R.setCanvasFactory(createCanvas);

function inkBox(ctx, text, x, y) {
  const m = ctx.measureText(String(text));
  const transform = ctx.getTransform();
  const points = [
    [x - m.actualBoundingBoxLeft, y - m.actualBoundingBoxAscent],
    [x + m.actualBoundingBoxRight, y - m.actualBoundingBoxAscent],
    [x + m.actualBoundingBoxRight, y + m.actualBoundingBoxDescent],
    [x - m.actualBoundingBoxLeft, y + m.actualBoundingBoxDescent]
  ].map(([px, py]) => [transform.a * px + transform.c * py + transform.e, transform.b * px + transform.d * py + transform.f]);
  return { points, inkHeight: m.actualBoundingBoxAscent + m.actualBoundingBoxDescent, left: Math.min(...points.map(p => p[0])), right: Math.max(...points.map(p => p[0])), top: Math.min(...points.map(p => p[1])), bottom: Math.max(...points.map(p => p[1])) };
}
// AABBs can overlap when two oblique labels have ample actual ink clearance.
// Test the transformed font polygons on separating axes, then use exact
// point-to-edge distance for the minimum clearance of disjoint rectangles.
function dot(a, b) { return a[0] * b[0] + a[1] * b[1]; }
function axes(points) {
  return points.map((p, i) => {
    const q = points[(i + 1) % points.length], dx = q[0] - p[0], dy = q[1] - p[1], length = Math.hypot(dx, dy);
    return [-dy / length, dx / length];
  });
}
function pointSegmentDistance(p, a, b) {
  const d = [b[0] - a[0], b[1] - a[1]];
  const u = Math.max(0, Math.min(1, ((p[0] - a[0]) * d[0] + (p[1] - a[1]) * d[1]) / dot(d, d)));
  return Math.hypot(p[0] - a[0] - u * d[0], p[1] - a[1] - u * d[1]);
}
function gap(aBox, bBox) {
  const a = aBox.points, b = bBox.points;
  const separated = [...axes(a), ...axes(b)].some(axis => {
    const aa = a.map(p => dot(p, axis)), bb = b.map(p => dot(p, axis));
    return Math.max(...aa) < Math.min(...bb) || Math.max(...bb) < Math.min(...aa);
  });
  if (!separated) return -1;
  return Math.min(
    ...a.flatMap(p => b.map((q, i) => pointSegmentDistance(p, q, b[(i + 1) % b.length]))),
    ...b.flatMap(p => a.map((q, i) => pointSegmentDistance(p, q, a[(i + 1) % a.length])))
  );
}
const measurement = createCanvas(1920, 720).getContext('2d');
group('Actual local-font oriented ink bounds, native size, and no tick overlaps', () => {
  for (const [side, ticks] of [['left', Scale.leftTicks], ['right', Scale.rightTicks]]) {
    const boxes = ticks.map(tick => {
      measurement.save();
      measurement.translate(tick.x, tick.y);
      measurement.rotate(tick.angle || 0);
      measurement.font = `${tick.size}px "Scale Digits"`;
      measurement.textAlign = 'center';
      measurement.textBaseline = 'middle';
      const box = inkBox(measurement, tick.value, 0, 0);
      measurement.restore();
      ok([box.left, box.right, box.top, box.bottom, box.inkHeight, ...box.points.flat()].every(Number.isFinite), `Real font ink metrics available for ${side} ${tick.value}`);
      ok(box.inkHeight >= 14, `${side} ${tick.value} must have at least 14 px native ink height`);
      ok(box.left >= 10 && box.right <= 1910, `${side} ${tick.value} stays in the panel safe area`);
      return { value: tick.value, size: tick.size, ...box };
    });
    for (let i = 0; i < boxes.length; i++) for (let j = i + 1; j < boxes.length; j++) {
      ok(gap(boxes[i], boxes[j]) >= 2, `${side} labels ${boxes[i].value} and ${boxes[j].value} need 2 px actual-ink clearance`);
    }
    report.fontBounds[side] = boxes;
  }
});

group('Native renderer uses fixed tick geometry and signed battery colors', () => {
  for (const [time, expectedColor] of [[0, '#a7b3c1'], [12, '#a9cafa'], [21, '#a9cafa'], [31, '#a7b3c1'], [42, '#9ceac0']]) {
    const canvas = createCanvas(1920, 720), ctx = canvas.getContext('2d'), labels = [];
    const wrapped = new Proxy(ctx, {
      get(target, key) {
        if (key === 'fillText') return (text, x, y, ...args) => {
          const matrix = target.getTransform();
          labels.push({ text: String(text),
            x: matrix.a * x + matrix.c * y + matrix.e,
            y: matrix.b * x + matrix.d * y + matrix.f,
            angle: Math.atan2(matrix.b, matrix.a), font: target.font, fill: target.fillStyle,
            box: inkBox(target, text, x, y) });
          return target.fillText(text, x, y, ...args);
        };
        const value = target[key];
        return typeof value === 'function' ? value.bind(target) : value;
      },
      set(target, key, value) { target[key] = value; return true; }
    });
    const state = R.renderPanel(wrapped, time, 'city');
    for (const [side, ticks] of [['left', Scale.leftTicks], ['right', Scale.rightTicks]]) for (const tick of ticks) {
      // The native canvas transform stores float32 translations, so matching
      // transformed anchors permits 0.001 px; model/frontier checks stay exact.
      const matches = labels.filter(label => label.text === String(tick.value) && Math.abs(label.x - tick.x) < 1e-3 && Math.abs(label.y - tick.y) < 1e-3 && label.font.includes('Scale Digits'));
      equal(matches.length, 1, `${side} ${tick.value} drawn exactly once at shared geometry, t=${time}`);
      near(parseFloat(matches[0].font), tick.size, 'Renderer consumes shared font size');
      near(matches[0].angle, tick.angle || 0, 'Renderer consumes shared perspective angle', 1e-6);
      if (side === 'right') equal(matches[0].fill.toLowerCase(), expectedColor, `Signed battery color at t=${time}`);
      for (const other of labels) {
        if (other === matches[0] || other.font.includes('Scale Digits') || other.text.trim() === '') continue;
        ok(gap(matches[0].box, other.box) >= 0, `Scale ${side} ${tick.value} overlaps non-scale label ${other.text}, t=${time}`);
      }
    }
    report.samples.push({ time, speed: state.speed, batteryKw: state.batteryPowerKw, batteryColor: expectedColor });
  }
});

report.assertions = assertions;
report.result = failures.length ? 'FAIL' : 'PASS';
report.failures = failures;
console.log(JSON.stringify(report, null, 2));
if (failures.length) process.exitCode = 1;
