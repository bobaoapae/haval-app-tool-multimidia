/* Material illumination is cached once; telemetry moves only local light/needle.
 * Numeric readouts and the native bridge remain independent of this renderer. */
(() => {
  const canvas = document.getElementById('gauge-motion');
  const ctx = canvas && canvas.getContext('2d');
  const geometry = window.ApexGaugeGeometry;
  if (!ctx || !geometry) return;

  const DURATION = 180;
  const POWER_TIME_CONSTANT = 100;
  const POWER_MAX_DELTA = 80;
  const POWER_EPSILON = 0.05;
  const FRAME_MS = 1000 / 30;
  const LEFT = { x: 65, y: 110, width: 160, height: geometry.mapY(570) - 110 };
  const rightTop = Math.floor(geometry.mapY(130));
  const RIGHT = { x: 1675, y: rightTop, width: 110, height: Math.ceil(geometry.mapY(540)) - rightTop };
  const paintBottom = Math.ceil(geometry.mapY(570)) + 25;
  const sourceZeroY = geometry.inverseY(geometry.speedY(0));
  const blueEndY = geometry.mapY(sourceZeroY + 28);
  const pearlEndY = geometry.mapY(sourceZeroY + 13);
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const material = document.getElementById('gauge-material');
  const root = document.getElementById('apex-gt');
  let active = !root || root.dataset.display !== 'Vector';
  const channels = {
    speed: { value: null, from: null, target: null, started: 0 },
    power: { value: null, from: null, target: null, started: 0, sampledAt: 0 }
  };
  let frame = 0;
  let lastPaint = -Infinity;
  let paintCount = 0;
  let disposed = false;
  let leftBlue = null;
  let leftPearl = null;
  let materialPrepared = false;
  const clamp = (value, low = 0, high = 1) => Math.max(low, Math.min(high, value));

  canvas.dataset.speed = '';
  canvas.dataset.power = '';
  canvas.dataset.running = 'false';
  canvas.dataset.paints = '0';
  canvas.dataset.material = 'pending';
  canvas.dataset.active = String(active);

  function surface(width, height) {
    const output = document.createElement('canvas');
    output.width = width;
    output.height = height;
    return output;
  }

  function leftFace(y) {
    const bounds = geometry.leftBounds(y);
    return { outer: bounds.outer + 11, inner: bounds.inner - 3 };
  }

  const leftClip = new Path2D();
  for (let y = LEFT.y; y <= LEFT.y + LEFT.height; y += 5) {
    const face = leftFace(y);
    if (y === LEFT.y) leftClip.moveTo(face.outer, y);
    else leftClip.lineTo(face.outer, y);
  }
  leftClip.lineTo(leftFace(LEFT.y + LEFT.height).outer, LEFT.y + LEFT.height);
  for (let y = LEFT.y + LEFT.height; y >= LEFT.y; y -= 5) {
    leftClip.lineTo(leftFace(y).inner, y);
  }
  leftClip.lineTo(leftFace(LEFT.y).inner, LEFT.y);
  leftClip.closePath();

  function prepareLeftMaterial() {
    if (disposed || materialPrepared || !material || !material.naturalWidth) return;
    materialPrepared = true;
    material.removeEventListener('load', prepareLeftMaterial);
    material.removeEventListener('error', materialFailed);
    try {
      const source = surface(LEFT.width, LEFT.height);
      const sourceContext = source.getContext('2d', { willReadFrequently: true });
      const scaleX = material.naturalWidth / 1920;
      const scaleY = material.naturalHeight / 720;
      sourceContext.drawImage(material, LEFT.x * scaleX, LEFT.y * scaleY,
        LEFT.width * scaleX, LEFT.height * scaleY, 0, 0, LEFT.width, LEFT.height);
      const pixels = sourceContext.getImageData(0, 0, LEFT.width, LEFT.height);
      leftBlue = surface(LEFT.width, LEFT.height);
      leftPearl = surface(LEFT.width, LEFT.height);
      const blueContext = leftBlue.getContext('2d');
      const pearlContext = leftPearl.getContext('2d');
      const blue = blueContext.createImageData(LEFT.width, LEFT.height);
      const pearl = pearlContext.createImageData(LEFT.width, LEFT.height);
      for (let row = 0; row < LEFT.height; row++) {
        const y = LEFT.y + row;
        const face = leftFace(y);
        for (let col = 0; col < LEFT.width; col++) {
          const x = LEFT.x + col;
          if (x <= face.outer || x >= face.inner) continue;
          const index = (row * LEFT.width + col) * 4;
          const sourceAlpha = pixels.data[index + 3] / 255;
          if (!sourceAlpha) continue;
          const luminance = pixels.data[index] * 0.2126
            + pixels.data[index + 1] * 0.7152 + pixels.data[index + 2] * 0.0722;
          // Actual black grooves stay black. The raster supplies its own bevel,
          // grain, division spacing and shadow; no synthetic segment mask exists.
          const edge = clamp(Math.min(x - face.outer, face.inner - x) / 3);
          const texture = clamp((luminance - 5) / 37);
          const alpha = Math.round(255 * sourceAlpha * edge * texture);
          const across = clamp((x - face.outer) / (face.inner - face.outer));
          const brightness = 0.46 + 0.54 * clamp(luminance / 100);
          const blueRise = Math.pow(across, 1.3);
          const pearlRise = Math.pow(across, 0.65);
          blue.data[index] = (5 + 70 * blueRise) * brightness;
          blue.data[index + 1] = (100 + 138 * blueRise) * brightness;
          blue.data[index + 2] = (183 + 72 * blueRise) * brightness;
          pearl.data[index] = (43 + 184 * pearlRise) * brightness;
          pearl.data[index + 1] = (139 + 110 * pearlRise) * brightness;
          pearl.data[index + 2] = (185 + 70 * pearlRise) * brightness;
          blue.data[index + 3] = alpha;
          pearl.data[index + 3] = alpha;
        }
      }
      blueContext.putImageData(blue, 0, 0);
      pearlContext.putImageData(pearl, 0, 0);
      canvas.dataset.material = 'ready';
    } catch (_) {
      // A missing/tainted image must not turn into another fabricated overlay.
      leftBlue = null;
      leftPearl = null;
      canvas.dataset.material = 'unavailable';
    }
    if (channels.speed.value !== null) paint(performance.now());
  }

  function materialFailed() {
    if (disposed) return;
    canvas.dataset.material = 'unavailable';
    if (material) {
      material.removeEventListener('load', prepareLeftMaterial);
      material.removeEventListener('error', materialFailed);
    }
  }

  function rightMaterial(regeneration) {
    const output = surface(RIGHT.width, RIGHT.height);
    const context = output.getContext('2d');
    const pixels = context.createImageData(RIGHT.width, RIGHT.height);
    for (let row = 0; row < RIGHT.height; row++) {
      const y = RIGHT.y + row;
      const edge = geometry.rightEdge(y) - 1;
      for (let col = 0; col < RIGHT.width; col++) {
        const distance = edge - (RIGHT.x + col);
        if (distance < 0 || distance > 19) continue;
        const index = (row * RIGHT.width + col) * 4;
        const across = 1 - distance / 19;
        // Fine, low-contrast etching within one continuous light channel.
        const hatch = (y + distance * 0.12) % 3 < 0.8 ? 0.55 : 1;
        const intensity = Math.pow(across, 0.65);
        pixels.data[index] = regeneration ? 50 + 150 * intensity : 25 + 140 * intensity;
        pixels.data[index + 1] = regeneration ? 202 + 53 * intensity : 128 + 119 * intensity;
        pixels.data[index + 2] = regeneration ? 117 + 101 * intensity : 239 + 16 * intensity;
        pixels.data[index + 3] = 255 * intensity * hatch * clamp(distance + 0.5);
      }
    }
    context.putImageData(pixels, 0, 0);
    return output;
  }

  const powerLight = rightMaterial(false);
  const regenLight = rightMaterial(true);

  function drawSlice(sprite, box, top, bottom, opacity = 1) {
    const y = Math.max(box.y, top);
    const end = Math.min(box.y + box.height, bottom);
    if (!sprite || end <= y) return;
    ctx.globalAlpha = opacity;
    ctx.drawImage(sprite, 0, y - box.y, box.width, end - y,
      box.x, y, box.width, end - y);
    ctx.globalAlpha = 1;
  }

  function speedLight(value) {
    if (!leftBlue || value <= 0) return;
    const y = geometry.speedY(value);
    const face = leftFace(y);
    // The subdued cyan upper ribbon and pearl lower material share the original
    // grooves. Only the illuminated range and concentrated hotspot move.
    drawSlice(leftBlue, LEFT, geometry.speedY(180), blueEndY, 0.82 * Math.min(1, value / 30));
    drawSlice(leftPearl, LEFT, y, pearlEndY, 0.91);
    const x = face.outer + (face.inner - face.outer) * 0.72;
    const glow = ctx.createRadialGradient(x, y, 0, x, y, 23);
    glow.addColorStop(0, 'rgba(238,252,255,0.8)');
    glow.addColorStop(0.22, 'rgba(172,232,255,0.5)');
    glow.addColorStop(1, 'rgba(80,180,255,0)');
    ctx.save();
    ctx.clip(leftClip);
    ctx.fillStyle = glow;
    ctx.fillRect(x - 23, y - 23, 46, 46);
    const reflection = ctx.createLinearGradient(face.outer, y, face.inner, y);
    reflection.addColorStop(0, 'rgba(184,230,255,0)');
    reflection.addColorStop(0.6, 'rgba(212,245,255,0.45)');
    reflection.addColorStop(1, 'rgba(249,254,255,0.95)');
    ctx.strokeStyle = reflection;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(face.outer, y);
    ctx.lineTo(face.inner, y);
    ctx.stroke();
    ctx.restore();
  }

  function powerNeedle(value) {
    const y = geometry.powerY(value);
    const point = { x: geometry.rightEdge(y) - 1, y };
    const pivot = geometry.powerPivot();
    const length = Math.hypot(point.x - pivot.x, point.y - pivot.y);
    const nx = -(point.y - pivot.y) / length;
    const ny = (point.x - pivot.x) / length;
    const finish = ctx.createLinearGradient(pivot.x, pivot.y, point.x, point.y);
    finish.addColorStop(0, 'rgba(198,232,249,0.32)');
    finish.addColorStop(0.28, '#e6f3fc');
    finish.addColorStop(0.78, '#fbfeff');
    finish.addColorStop(1, '#ffffff');
    ctx.beginPath();
    ctx.moveTo(pivot.x, pivot.y);
    ctx.lineTo(point.x + nx * 7, point.y + ny * 7);
    ctx.lineTo(point.x - nx * 7, point.y - ny * 7);
    ctx.closePath();
    ctx.fillStyle = value < 0 ? 'rgba(143,255,193,0.13)' : 'rgba(112,196,237,0.13)';
    ctx.fill();
    ctx.beginPath();
    ctx.moveTo(pivot.x, pivot.y);
    ctx.lineTo(point.x + nx * 5, point.y + ny * 5);
    ctx.lineTo(point.x - nx * 5, point.y - ny * 5);
    ctx.closePath();
    ctx.fillStyle = finish;
    ctx.fill();
    // A single thin bevel adds depth without a blur or broad shadow.
    ctx.beginPath();
    ctx.moveTo(pivot.x + 3, pivot.y);
    ctx.lineTo(point.x - nx * 5, point.y - ny * 5);
    ctx.strokeStyle = 'rgba(111,158,184,0.75)';
    ctx.lineWidth = 1;
    ctx.stroke();
    canvas.dataset.needleX = String(point.x);
    canvas.dataset.needleY = String(point.y);
  }

  function paint(now) {
    if (disposed || !active) return;
    ctx.clearRect(65, 85, 205, paintBottom - 85);
    ctx.clearRect(1595, 90, 245, paintBottom - 90);
    const speed = channels.speed.value;
    const power = channels.power.value;
    if (speed !== null) speedLight(speed);
    if (power !== null) {
      const y = geometry.powerY(power);
      if (power > 0) drawSlice(powerLight, RIGHT, y, geometry.powerY(0));
      else if (power < 0) drawSlice(regenLight, RIGHT, geometry.powerY(0), y);
      powerNeedle(power);
    } else {
      canvas.dataset.needleX = '';
      canvas.dataset.needleY = '';
    }
    canvas.dataset.speed = speed === null ? '' : String(speed);
    canvas.dataset.speedY = speed === null ? '' : String(geometry.speedY(speed));
    canvas.dataset.power = power === null ? '' : String(power);
    canvas.dataset.paints = String(++paintCount);
    lastPaint = now;
  }

  function sample(now) {
    let running = false;
    for (const [name, channel] of Object.entries(channels)) {
      if (channel.value === channel.target) continue;
      if (name === 'power') {
        const delta = Math.min(POWER_MAX_DELTA, Math.max(0, now - channel.sampledAt));
        channel.sampledAt = now;
        const remaining = channel.target - channel.value;
        channel.value += remaining * (1 - Math.exp(-delta / POWER_TIME_CONSTANT));
        if (Math.abs(channel.target - channel.value) <= POWER_EPSILON) channel.value = channel.target;
        else running = true;
        continue;
      }
      const progress = Math.min(1, Math.max(0, (now - channel.started) / DURATION));
      const eased = 1 - (1 - progress) * (1 - progress);
      channel.value = progress === 1 ? channel.target
        : channel.from + (channel.target - channel.from) * eased;
      if (progress < 1) running = true;
    }
    return running;
  }

  function animate(now) {
    frame = 0;
    if (disposed || !active) return;
    if (now - lastPaint < FRAME_MS) {
      frame = window.requestAnimationFrame(animate);
      return;
    }
    const running = sample(now);
    paint(now);
    canvas.dataset.running = String(running);
    if (running) frame = window.requestAnimationFrame(animate);
  }

  function settle(now) {
    if (frame) window.cancelAnimationFrame(frame);
    frame = 0;
    for (const channel of Object.values(channels)) {
      channel.value = channel.target;
      if (Object.prototype.hasOwnProperty.call(channel, 'sampledAt')) channel.sampledAt = now;
    }
    canvas.dataset.running = 'false';
    paint(now);
  }

  function receive(event) {
    if (disposed) return;
    const detail = event.detail || {};
    const now = performance.now();
    sample(now);
    let changed = false;
    let mustPaint = false;
    for (const name of ['speed', 'power']) {
      if (!Object.prototype.hasOwnProperty.call(detail, name)) continue;
      const raw = detail[name];
      const value = typeof raw === 'number' && Number.isFinite(raw)
        ? Math.max(name === 'speed' ? 0 : -100, Math.min(name === 'speed' ? 180 : 100, raw))
        : null;
      const channel = channels[name];
      if (value === channel.target) continue;
      changed = true;
      channel.from = channel.value;
      channel.target = value;
      channel.started = now;
      if (name === 'power') channel.sampledAt = now;
      // Missing values disappear immediately; first samples do not sweep from fake zero.
      if (value === null || channel.value === null) {
        channel.value = value;
        channel.from = value;
        mustPaint = true;
      }
    }
    if (!changed) return;
    if (!active || motion.matches || document.hidden) {
      settle(now);
      return;
    }
    if (mustPaint) paint(now);
    const running = Object.values(channels).some(channel => channel.value !== channel.target);
    canvas.dataset.running = String(running);
    if (!running) {
      // A new target can coincide with the interpolated value between two paints.
      if (!mustPaint) paint(now);
      if (frame) window.cancelAnimationFrame(frame);
      frame = 0;
    } else if (running && !frame) {
      frame = window.requestAnimationFrame(animate);
    }
  }

  function motionChanged() {
    if (motion.matches) settle(performance.now());
  }

  function visibilityChanged() {
    if (document.hidden && frame) settle(performance.now());
  }

  function displayChanged(event) {
    const mode = event.detail && event.detail.mode;
    if (disposed || (mode !== 'Contour' && mode !== 'Vector')) return;
    const next = mode === 'Contour';
    if (active === next) return;
    active = next;
    canvas.dataset.active = String(active);
    // Inactive telemetry is cached in target; returning snaps once to the latest
    // values, without replaying an animation hidden behind the other display.
    settle(performance.now());
  }

  function cleanup() {
    if (disposed) return;
    disposed = true;
    if (frame) window.cancelAnimationFrame(frame);
    frame = 0;
    canvas.dataset.running = 'false';
    window.removeEventListener('apex-telemetry', receive);
    window.removeEventListener('apex-cleanup', cleanup);
    window.removeEventListener('apex-display-change', displayChanged);
    document.removeEventListener('visibilitychange', visibilityChanged);
    if (material) {
      material.removeEventListener('load', prepareLeftMaterial);
      material.removeEventListener('error', materialFailed);
    }
    leftBlue = null;
    leftPearl = null;
    if (motion.removeEventListener) motion.removeEventListener('change', motionChanged);
    else motion.removeListener(motionChanged);
  }

  window.addEventListener('apex-telemetry', receive);
  window.addEventListener('apex-cleanup', cleanup);
  window.addEventListener('apex-display-change', displayChanged);
  document.addEventListener('visibilitychange', visibilityChanged);
  if (motion.addEventListener) motion.addEventListener('change', motionChanged);
  else motion.addListener(motionChanged);
  if (material) {
    material.addEventListener('load', prepareLeftMaterial);
    material.addEventListener('error', materialFailed);
    if (material.complete && material.naturalWidth) prepareLeftMaterial();
  } else materialFailed();
})();
