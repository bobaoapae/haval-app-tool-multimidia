/* Haval Impulse visual preview: deterministic speed-driven depth field.
 * Browser: GalaxyStars; Node: require('./galaxy-stars'). No vehicle APIs.
 * Coordinates and streak limits use a 1920 x 720 design surface.
 * Pass continuous integrated travel, never speed * time for changing speed.
 * time is accepted for the shared render contract but does not animate stars.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.GalaxyStars = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const Scale = typeof module === 'object' ? require('./scales') : globalThis.ThemeScales;
  if (!Scale || !Scale.leftTicks || !Scale.rightTicks) throw new Error('Load ThemeScales before GalaxyStars');
  const WIDTH = 1920, HEIGHT = 720, COUNT = 2800;
  const VANISH_X = 960, VANISH_Y = 260, FOCAL = 640;
  const NEAR = 0.24, FAR = 2.60, DEPTH_SPAN = FAR - NEAR;
  const MAX_STREAK = 14, INTEGRATION_HZ = 120;
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const finite = (v, fallback = 0) => Number.isFinite(v) ? v : fallback;
  const modulo = (v, size) => ((v % size) + size) % size;
  function smoothstep(a, b, v) {
    const q = clamp((v - a) / (b - a), 0, 1);
    return q * q * (3 - 2 * q);
  }
  function hash(index, salt) {
    let n = (Math.imul(index + 1, 374761393) + salt) >>> 0;
    n = Math.imul(n ^ (n >>> 13), 1274126177) >>> 0;
    return ((n ^ (n >>> 16)) >>> 0) / 4294967296;
  }
  const stars = Array.from({ length: COUNT }, (_, i) => Object.freeze({
    worldX: (hash(i, 99181) * 2 - 1) * 2.60,
    worldY: (hash(i, 82091) * 2 - 1) * 0.96,
    offset: hash(i, 19139) * DEPTH_SPAN,
    // Mostly fine points, with a sparse brighter/larger population like the
    // original sky. These properties are fixed per star, never time-twinkled.
    size: 0.29 + Math.pow(hash(i, 71713), 3.4) * 1.55,
    luminance: 0.31 + Math.pow(hash(i, 29873), 1.2) * 0.69,
    blue: hash(i, 63131)
  }));
  // Scene-depth units per second, supplied speed in km/h. The safety ceiling
  // only bounds unreasonable inputs; every ordinary speed is strictly linear.
  function speedRate(speed) { return clamp(finite(speed), 0, 360) * 0.0032; }
  // Protection follows the actual text runs, not whole horizontal sky bands.
  // Floating readouts retain their v8 positions. Scale guards consume the
  // same rotated geometry as the renderer, without a renderer dependency/cycle.
  // Half-extents include a small glyph clearance; the exterior soft shoulder
  // is not a dark backing card and leaves the space between readouts open.
  const READABILITY_REGIONS = [
    ['inside-temperature', 100, 73, 49, 26, 17],
    ['outside-temperature', 235, 73, 50, 26, 17],
    ['clock', 428, 68, 46, 18, 18],
    ['gear', 960, 120, 23, 26, 18],
    ['drive-mode', 896, 156, 44, 13, 16],
    ['propulsion-mode', 1025, 156, 26, 14, 16],
    ['odometer', 1780, 126, 86, 27, 18],
    ['trip-a', 167, 177, 104, 21, 20],
    ['average-consumption', 1732, 187, 123, 22, 20],
    ['speed-label', Scale.speedLabel.x, Scale.speedLabel.y,
      Scale.speedLabel.text.length * Scale.speedLabel.size * 0.35 + Scale.speedLabel.text.length + 5,
      Scale.speedLabel.size * 0.55 + 6, 16],
    ['battery-label', 1480, 256, 119, 13, 16],
    ['battery-state', 1480, 283, 151, 17, 16],
    ['engine-rpm', 960, 470, 88, 18, 14],
    ['fuel-summary', 355, 646, 145, 21, 12],
    ['fuel-range', 360, 687, 145, 16, 12],
    ['battery-summary', 1560, 646, 130, 21, 12],
    ['battery-range', 1570, 687, 130, 16, 12],
    ['total-range', 960, 677, 165, 20, 12]
  ];
  for (const [name, ticks] of [['speed', Scale.leftTicks], ['battery', Scale.rightTicks]]) {
    for (const tick of ticks) {
      const localHalfWidth = String(tick.value).length * tick.size * 0.31 + 4;
      const localHalfHeight = tick.size * 0.48 + 4;
      const angle = finite(tick.angle), c = Math.abs(Math.cos(angle)), s = Math.abs(Math.sin(angle));
      // The rendered glyph is rotated around its tick anchor. Bound every
      // rotated corner before applying the star's halo and swept-tail guard.
      const halfWidth = c * localHalfWidth + s * localHalfHeight;
      const halfHeight = s * localHalfWidth + c * localHalfHeight;
      READABILITY_REGIONS.push([`${name}-${tick.value}`, tick.x, tick.y,
        halfWidth, halfHeight, 12, angle, localHalfWidth, localHalfHeight]);
    }
  }
  for (let i = 0; i < READABILITY_REGIONS.length; i++) {
    const [id, x, y, halfWidth, halfHeight, feather, angle = 0,
      localHalfWidth = halfWidth, localHalfHeight = halfHeight] = READABILITY_REGIONS[i];
    READABILITY_REGIONS[i] = Object.freeze({ id, x, y, halfWidth, halfHeight, feather, angle, localHalfWidth, localHalfHeight });
  }
  Object.freeze(READABILITY_REGIONS);
  function visibilityMask(x, y, padding = 0, tailX = x, tailY = y) {
    let a = smoothstep(18, 42, y) * (1 - smoothstep(HEIGHT, HEIGHT + 8, y));
    a *= smoothstep(-8, 24, x) * (1 - smoothstep(WIDTH - 24, WIDTH + 8, x));
    if (!a) return 0;
    const minX = Math.min(x, tailX) - padding, maxX = Math.max(x, tailX) + padding;
    const minY = Math.min(y, tailY) - padding, maxY = Math.max(y, tailY) + padding;
    for (const r of READABILITY_REGIONS) {
      const dx = Math.max(r.x - r.halfWidth - maxX, minX - r.x - r.halfWidth, 0);
      const dy = Math.max(r.y - r.halfHeight - maxY, minY - r.y - r.halfHeight, 0);
      if (dx >= r.feather || dy >= r.feather) continue;
      a *= smoothstep(0, r.feather, Math.hypot(dx, dy));
      if (!a) return 0;
    }
    // No star, core, halo or bounded radial tail may enter the speed lens.
    const lensDistance = Math.hypot(x - 960, y - 310) - padding - Math.hypot(x - tailX, y - tailY);
    a *= smoothstep(160, 215, lensDistance);
    return a;
  }
  const renderStats = { nominal: COUNT, culled: 0, sampled: 0, visible: 0 };
  function sampleStar(index, options = {}, target) {
    return sampleInternal(index, options, target, false);
  }
  function sampleInternal(index, options, target, renderOnly, renderRate) {
    index = modulo(Math.floor(finite(index)), COUNT);
    const p = stars[index], travel = finite(options.travel);
    const phase = modulo(p.offset + travel, DEPTH_SPAN);
    const z = FAR - phase;
    const x = VANISH_X + p.worldX * FOCAL / z;
    const y = VANISH_Y + p.worldY * FOCAL / z;
    // Only the paint path short-circuits. Public sampleStar still returns every
    // field, including invisible stars, with the original calculations intact.
    if (renderOnly && !(x > -8 && x < WIDTH + 8 && y > 18 && y < HEIGHT + 8)) {
      renderStats.culled++; target.visible = false; return target;
    }
    if (renderOnly) renderStats.sampled++;
    const distance = Math.hypot(x - VANISH_X, y - VANISH_Y);
    const rate = renderOnly ? renderRate : speedRate(options.speed);
    const radialSpeed = distance * rate / z;
    // An optical shutter, not a second time source: motion is always radial.
    const streakLength = Math.min(MAX_STREAK, radialSpeed / 32);
    const depthFade = smoothstep(NEAR, NEAR + 0.23, z) * (1 - smoothstep(FAR - 0.43, FAR, z));
    const radius = clamp(p.size * (0.75 + 0.50 / z), 0.22, 2.4);
    const directionX = distance ? (x - VANISH_X) / distance : 0;
    const directionY = distance ? (y - VANISH_Y) / distance : 0;
    const tailX = x - directionX * streakLength, tailY = y - directionY * streakLength;
    // Protect the entire footprint, including anti-aliasing and the local
    // optical halo. A point outside a label cannot trail across its glyphs.
    const alpha = clamp(p.luminance * (0.85 + 0.40 / z) * depthFade *
      visibilityMask(x, y, Math.max(1, radius * 2.15) + 1, tailX, tailY), 0, 0.9);
    const result = target || {};
    result.index = index; result.x = x; result.y = y; result.z = z;
    result.phase = phase; result.alpha = alpha; result.radius = radius;
    result.depthFade = depthFade; result.radialSpeed = radialSpeed;
    result.streakLength = streakLength; result.tailX = tailX; result.tailY = tailY;
    result.blue = p.blue;
    result.visible = alpha > 0.002 && x > -8 && x < WIDTH + 8 && y > 18 && y < HEIGHT + 8;
    return result;
  }
  const renderSample = {};
  function render(ctx, options = {}) {
    const width = finite(options.width, WIDTH), height = finite(options.height, HEIGHT);
    const rate = speedRate(options.speed);
    renderStats.culled = renderStats.sampled = renderStats.visible = 0;
    ctx.save();
    ctx.scale(width / WIDTH, height / HEIGHT);
    ctx.globalCompositeOperation = 'source-over';
    ctx.globalAlpha = 1;
    ctx.shadowBlur = 0;
    ctx.lineCap = 'round';
    for (let i = 0; i < COUNT; i++) {
      const s = sampleInternal(i, options, renderSample, true, rate);
      if (!s.visible) continue;
      renderStats.visible++;
      const rgb = s.blue > 0.82 ? '244,244,242' : s.blue > 0.35 ? '226,237,253' : '194,216,248';
      const streakGain = smoothstep(0.35, 0.85, s.streakLength) * smoothstep(0.025, 0.085, s.alpha);
      if (streakGain > 0) {
        const streak = ctx.createLinearGradient(s.tailX, s.tailY, s.x, s.y);
        streak.addColorStop(0, `rgba(${rgb},0)`);
        streak.addColorStop(0.65, `rgba(${rgb},${s.alpha * 0.16 * streakGain})`);
        streak.addColorStop(1, `rgba(${rgb},${s.alpha * 0.45 * streakGain})`);
        ctx.strokeStyle = streak;
        ctx.lineWidth = clamp(s.radius * 0.75, 0.42, 0.95);
        ctx.beginPath(); ctx.moveTo(s.tailX, s.tailY); ctx.lineTo(s.x, s.y); ctx.stroke();
      }
      // Tiny local optical halo only for the largest points. No full-field bloom.
      const haloGain = smoothstep(0.85, 1.25, s.radius) * smoothstep(0.10, 0.22, s.alpha);
      if (haloGain > 0) {
        const glow = ctx.createRadialGradient(s.x, s.y, 0, s.x, s.y, s.radius * 2.15);
        glow.addColorStop(0, `rgba(${rgb},${s.alpha * 0.22 * haloGain})`);
        glow.addColorStop(1, `rgba(${rgb},0)`);
        ctx.fillStyle = glow;
        ctx.fillRect(s.x - s.radius * 2.15, s.y - s.radius * 2.15, s.radius * 4.3, s.radius * 4.3);
      }
      ctx.fillStyle = `rgba(${rgb},${s.alpha})`;
      ctx.beginPath(); ctx.arc(s.x, s.y, s.radius, 0, Math.PI * 2); ctx.fill();
      // A compact subpixel core survives a dense cluster's dark glass and
      // video compression. Do not round position: fractional coverage stays
      // continuous during slow travel, avoiding pixel-snapping sparkle.
      const coreGain = smoothstep(0.55, 0.90, s.radius) * smoothstep(0.12, 0.35, s.alpha);
      if (coreGain > 0) {
        const coreSize = clamp(s.radius * 0.9, 0.60, 1.6);
        ctx.fillStyle = `rgba(247,250,255,${Math.min(0.96, s.alpha * 1.35) * coreGain})`;
        ctx.fillRect(s.x - coreSize / 2, s.y - coreSize / 2, coreSize, coreSize);
      }
    }
    ctx.restore();
  }
  // Precompute a fixed-origin integral once per telemetry track. Seeking and
  // playback share this same function, with no mutable frame history or RNG.
  // Integration uses piecewise-linear speed rates at 120 Hz. Its value is exact
  // for constant speed and for linear speed ramps whose knots lie on that grid.
  function createTravelSampler(sampleSpeed, duration) {
    if (typeof sampleSpeed !== 'function') throw new TypeError('sampleSpeed must be a function');
    if (!Number.isFinite(duration) || duration < 0) throw new RangeError('duration must be finite and nonnegative');
    const steps = Math.ceil(duration * INTEGRATION_HZ);
    const rates = new Float64Array(steps + 1), distances = new Float64Array(steps + 1);
    for (let i = 0; i <= steps; i++) {
      const t = Math.min(i / INTEGRATION_HZ, duration);
      rates[i] = speedRate(sampleSpeed(t));
      if (i) {
        const dt = t - (i - 1) / INTEGRATION_HZ;
        distances[i] = distances[i - 1] + (rates[i - 1] + rates[i]) * dt / 2;
      }
    }
    return function travelAt(time) {
      const t = clamp(finite(time), 0, duration);
      if (t === duration) return distances[steps];
      const i = Math.min(steps - 1, Math.floor(t * INTEGRATION_HZ));
      if (i < 0) return 0;
      const dt = t - i / INTEGRATION_HZ;
      const interval = Math.min((i + 1) / INTEGRATION_HZ, duration) - i / INTEGRATION_HZ;
      return distances[i] + rates[i] * dt + (rates[i + 1] - rates[i]) * dt * dt / (2 * interval);
    };
  }
  function integrateTravel(sampleSpeed, time, duration = Math.max(0, finite(time))) {
    return createTravelSampler(sampleSpeed, duration)(time);
  }
  return Object.freeze({
    WIDTH, HEIGHT, COUNT, VANISH_X, VANISH_Y, NEAR, FAR, DEPTH_SPAN, MAX_STREAK,
    INTEGRATION_HZ, speedRate, sampleStar, render, createTravelSampler, integrateTravel,
    visibilityMask, READABILITY_REGIONS,
    stats: () => Object.assign({}, renderStats)
  });
});
