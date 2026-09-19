/* Vector lights the original brushed metal and places a short angular marker
 * across it. Cached sprites are shared by all values; no work runs at rest. */
(() => {
  const canvas = document.getElementById('vector-gauge-motion');
  const ctx = canvas && canvas.getContext('2d');
  const geometry = window.ApexVectorGeometry;
  if (!ctx || !geometry) return;

  const root = document.getElementById('apex-gt');
  const material = document.getElementById('vector-material');
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const DURATION = 180;
  const FRAME_MS = 1000 / 30;
  const boxes = {
    left: { x: 125, y: 130, width: 205, height: 360 },
    right: { x: 1580, y: 130, width: 225, height: 360 }
  };
  const channels = {
    speed: { value: null, target: null, from: null, started: 0 },
    power: { value: null, target: null, from: null, started: 0 }
  };
  let active = Boolean(root && root.dataset.display === 'Vector');
  let disposed = false;
  let prepared = false;
  let leftLight = null;
  let rightLight = null;
  let rightRegenLight = null;
  let frame = 0;
  let lastPaint = -Infinity;
  let paints = 0;
  const clamp = (value, low = 0, high = 1) => Math.max(low, Math.min(high, value));

  Object.assign(canvas.dataset, {
    active: String(active), material: 'pending', speed: '', power: '',
    speedY: '', powerY: '', running: 'false', paints: '0'
  });

  function createScales() {
    const svg = document.getElementById('vector-gauge-scales');
    if (!svg) return;
    const ticks = [];
    const labels = [];
    const speedMajor = [0, 40, 80, 120, 160, 200];
    const centers = { 0: 297, 40: 266.5, 120: 233.5, 160: 284, 200: 335.5 };
    for (let value = 0; value <= 200; value += 20) {
      const y = geometry.speedY(value);
      const edge = geometry.leftBounds(y).inner;
      const major = speedMajor.includes(value);
      // The 80 glyph fits between the metal and READY; its shorter tick avoids
      // crossing the glyph. Other marks retain the reference's generous spacing.
      ticks.push(`M${edge + 2} ${y}h${value === 80 ? 3 : major ? 10 : 5}`);
      if (!major) continue;
      if (value === 80) {
        labels.push(`<text x="234" y="${y + 10}" text-anchor="end" font-size="26">80</text>`);
      } else {
        labels.push(`<text x="${centers[value]}" y="${y + 10}" text-anchor="middle">${value}</text>`);
      }
    }
    const powerCenters = { '-100': 1647, '-50': 1677, 0: 1708.5, 50: 1718.5, 150: 1692, 200: 1646.5 };
    for (const value of [-100, -75, -50, -25, 0, 25, 50, 100, 150, 175, 200]) {
      const y = geometry.powerY(value);
      const edge = geometry.rightBounds(y).inner;
      const major = Object.prototype.hasOwnProperty.call(powerCenters, value);
      ticks.push(`M${edge - 2} ${y}h-${major ? 5 : 3}`);
      if (major) {
        labels.push(`<text x="${powerCenters[value]}" y="${y + 10}" text-anchor="middle" font-size="30">${value < 0 ? '−' + -value : value}</text>`);
      }
    }
    svg.innerHTML = `<g class="vector-scale-ticks" fill="none" stroke="#d5e4ec" stroke-width="1.4" opacity=".75"><path d="${ticks.join(' ')}"/></g><g class="vector-scale-labels" font-family="Khand, Arial Narrow, sans-serif" font-size="32" font-weight="400" fill="#e6edf2" stroke="#061018" stroke-width="2.5" stroke-linejoin="round" paint-order="stroke fill">${labels.join('')}</g>`;
  }

  function surface(box) {
    const output = document.createElement('canvas');
    output.width = box.width;
    output.height = box.height;
    return output;
  }

  function illuminate(side) {
    const box = boxes[side];
    const source = surface(box);
    const sourceContext = source.getContext('2d', { willReadFrequently: true });
    const scaleX = material.naturalWidth / 1920;
    const scaleY = material.naturalHeight / 720;
    sourceContext.drawImage(material, box.x * scaleX, box.y * scaleY,
      box.width * scaleX, box.height * scaleY, 0, 0, box.width, box.height);
    const input = sourceContext.getImageData(0, 0, box.width, box.height);
    const output = surface(box);
    const outputContext = output.getContext('2d');
    const pixels = outputContext.createImageData(box.width, box.height);
    // Both right-side finishes reuse this one raster read and the same mask.
    const regenOutput = side === 'right' ? surface(box) : null;
    const regenContext = regenOutput && regenOutput.getContext('2d');
    const regenPixels = regenContext && regenContext.createImageData(box.width, box.height);
    for (let row = 0; row < box.height; row++) {
      const bounds = side === 'left' ? geometry.leftBounds(box.y + row) : geometry.rightBounds(box.y + row);
      const low = Math.min(bounds.outer, bounds.inner) + 1;
      const high = Math.max(bounds.outer, bounds.inner) - 1;
      for (let col = 0; col < box.width; col++) {
        const x = box.x + col;
        if (x <= low || x >= high) continue;
        const index = (row * box.width + col) * 4;
        const alpha = input.data[index + 3] / 255;
        if (!alpha) continue;
        const r = input.data[index];
        const g = input.data[index + 1];
        const b = input.data[index + 2];
        // Leave the static red cap intact, even near the upper range.
        if (r > g * 1.8 && r > b * 1.8) continue;
        const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        const texture = clamp((luminance - 18) / 150);
        const edge = clamp(Math.min(x - low, high - x) / 2);
        const across = side === 'left' ? (x - low) / (high - low) : (high - x) / (high - low);
        const brightness = 0.22 + 0.78 * clamp(luminance / 235);
        pixels.data[index] = (10 + 144 * across) * brightness;
        pixels.data[index + 1] = (132 + 108 * across) * brightness;
        pixels.data[index + 2] = (214 + 41 * across) * brightness;
        pixels.data[index + 3] = 255 * alpha * texture * edge;
        if (regenPixels) {
          regenPixels.data[index] = (50 + 150 * across) * brightness;
          regenPixels.data[index + 1] = (202 + 53 * across) * brightness;
          regenPixels.data[index + 2] = (117 + 101 * across) * brightness;
          regenPixels.data[index + 3] = pixels.data[index + 3];
        }
      }
    }
    outputContext.putImageData(pixels, 0, 0);
    if (regenPixels) {
      regenContext.putImageData(regenPixels, 0, 0);
      rightRegenLight = regenOutput;
    }
    return output;
  }

  function stopMaterialListeners() {
    if (!material) return;
    material.removeEventListener('load', prepareMaterial);
    material.removeEventListener('error', materialFailed);
  }

  function prepareMaterial() {
    if (disposed || prepared || !material || !material.naturalWidth) return;
    prepared = true;
    stopMaterialListeners();
    try {
      leftLight = illuminate('left');
      rightLight = illuminate('right');
      canvas.dataset.material = 'ready';
    } catch (_) {
      leftLight = null;
      rightLight = null;
      rightRegenLight = null;
      canvas.dataset.material = 'unavailable';
    }
    if (active && (channels.speed.value !== null || channels.power.value !== null)) paint(performance.now());
  }

  function materialFailed() {
    if (disposed) return;
    canvas.dataset.material = 'unavailable';
    stopMaterialListeners();
  }

  function drawRange(sprite, box, a, b) {
    const top = Math.max(box.y, Math.min(a, b));
    const bottom = Math.min(box.y + box.height, Math.max(a, b));
    if (!sprite || bottom <= top) return;
    ctx.globalAlpha = 0.84;
    ctx.drawImage(sprite, 0, top - box.y, box.width, bottom - top,
      box.x, top, box.width, bottom - top);
    ctx.globalAlpha = 1;
  }

  function boundsAt(side, y) {
    return side === 'left' ? geometry.leftBounds(y) : geometry.rightBounds(y);
  }

  function outerGuide(side, y) {
    return boundsAt(side, y).outer + (side === 'left' ? -10 : 10);
  }

  function railPath(side, from, to) {
    const steps = Math.max(1, Math.ceil(Math.abs(to - from) / 7));
    ctx.beginPath();
    for (let step = 0; step <= steps; step++) {
      const y = from + (to - from) * step / steps;
      const x = outerGuide(side, y);
      if (!step) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
  }

  function marker(side, y, restY, regeneration = false) {
    const bounds = boundsAt(side, y);
    const outer = outerGuide(side, y);
    const inner = bounds.inner + (side === 'left' ? -1 : 1);
    // The bent rail is a local light on the existing outside chamfer, ending at
    // a short white crossbar through the metal. There is no floating long needle.
    if (Math.abs(restY - y) > 1) {
      const railColors = regeneration
        ? [[15, 'rgba(50,202,117,.10)'], [8, 'rgba(75,220,132,.32)'], [3, '#8fffc1']]
        : [[15, 'rgba(16,153,241,.10)'], [8, 'rgba(36,178,250,.32)'], [3, '#8cddff']];
      for (const [width, color] of railColors) {
        railPath(side, restY, y);
        ctx.lineWidth = width;
        ctx.lineJoin = 'round';
        ctx.lineCap = 'butt';
        ctx.strokeStyle = color;
        ctx.stroke();
      }
    }
    const rise = side === 'left' ? -6 : 6;
    const light = ctx.createLinearGradient(outer, y + rise, inner, y);
    light.addColorStop(0, '#f5fcff');
    light.addColorStop(0.55, regeneration ? '#effff4' : '#e3f7ff');
    light.addColorStop(1, regeneration ? '#c8ffda' : '#9adaff');
    ctx.beginPath();
    ctx.moveTo(outer, y + rise);
    ctx.lineTo(inner, y);
    ctx.lineWidth = 11;
    ctx.strokeStyle = regeneration ? 'rgba(143,255,193,.22)' : 'rgba(71,181,243,.22)';
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(outer, y + rise);
    ctx.lineTo(inner, y);
    ctx.lineWidth = 5;
    ctx.strokeStyle = light;
    ctx.stroke();
  }

  function paint(now) {
    if (disposed || !active) return;
    ctx.clearRect(100, 115, 255, 390);
    ctx.clearRect(1560, 115, 275, 390);
    const speed = channels.speed.value;
    const power = channels.power.value;
    if (leftLight && speed !== null) {
      const y = geometry.speedY(speed);
      drawRange(leftLight, boxes.left, y, geometry.speedY(0));
      marker('left', y, geometry.speedY(0));
    }
    if (rightLight && power !== null) {
      const y = geometry.powerY(power);
      const regeneration = power < 0;
      drawRange(regeneration ? rightRegenLight : rightLight, boxes.right, y, geometry.powerY(0));
      marker('right', y, geometry.powerY(0), regeneration);
    }
    canvas.dataset.speed = speed === null ? '' : String(speed);
    canvas.dataset.power = power === null ? '' : String(power);
    canvas.dataset.speedY = speed === null ? '' : String(geometry.speedY(speed));
    canvas.dataset.powerY = power === null ? '' : String(geometry.powerY(power));
    canvas.dataset.paints = String(++paints);
    lastPaint = now;
  }

  function sample(now) {
    let running = false;
    for (const channel of Object.values(channels)) {
      if (channel.value === channel.target) continue;
      const progress = clamp((now - channel.started) / DURATION);
      const eased = 1 - (1 - progress) * (1 - progress);
      channel.value = progress === 1 ? channel.target : channel.from + (channel.target - channel.from) * eased;
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
    for (const channel of Object.values(channels)) channel.value = channel.target;
    canvas.dataset.running = 'false';
    paint(now);
  }

  function receive(event) {
    if (disposed) return;
    const detail = event.detail || {};
    const now = performance.now();
    sample(now);
    let changed = false;
    let immediate = false;
    for (const name of ['speed', 'power']) {
      if (!Object.prototype.hasOwnProperty.call(detail, name)) continue;
      const raw = detail[name];
      const value = typeof raw === 'number' && Number.isFinite(raw)
        ? clamp(raw, name === 'speed' ? 0 : -100, 200) : null;
      const channel = channels[name];
      if (value === channel.target) continue;
      channel.from = channel.value;
      channel.target = value;
      channel.started = now;
      changed = true;
      if (value === null || channel.value === null) {
        channel.value = value;
        channel.from = value;
        immediate = true;
      }
    }
    if (!changed) return;
    canvas.dataset.targetSpeed = channels.speed.target === null ? '' : String(channels.speed.target);
    canvas.dataset.targetPower = channels.power.target === null ? '' : String(channels.power.target);
    if (!active || motion.matches || document.hidden) {
      settle(now);
      return;
    }
    if (immediate) paint(now);
    const running = Object.values(channels).some(channel => channel.value !== channel.target);
    canvas.dataset.running = String(running);
    if (!running) {
      if (!immediate) paint(now);
      if (frame) window.cancelAnimationFrame(frame);
      frame = 0;
    } else if (!frame) frame = window.requestAnimationFrame(animate);
  }

  function displayChanged(event) {
    const mode = event.detail && event.detail.mode;
    if (disposed || (mode !== 'Contour' && mode !== 'Vector')) return;
    const next = mode === 'Vector';
    if (next === active) return;
    active = next;
    canvas.dataset.active = String(active);
    settle(performance.now());
  }

  function motionChanged() {
    if (motion.matches) settle(performance.now());
  }

  function visibilityChanged() {
    if (document.hidden && frame) settle(performance.now());
  }

  function cleanup() {
    if (disposed) return;
    disposed = true;
    if (frame) window.cancelAnimationFrame(frame);
    frame = 0;
    canvas.dataset.running = 'false';
    window.removeEventListener('apex-telemetry', receive);
    window.removeEventListener('apex-display-change', displayChanged);
    window.removeEventListener('apex-cleanup', cleanup);
    document.removeEventListener('visibilitychange', visibilityChanged);
    if (motion.removeEventListener) motion.removeEventListener('change', motionChanged);
    else motion.removeListener(motionChanged);
    stopMaterialListeners();
    leftLight = null;
    rightLight = null;
    rightRegenLight = null;
  }

  createScales();
  window.addEventListener('apex-telemetry', receive);
  window.addEventListener('apex-display-change', displayChanged);
  window.addEventListener('apex-cleanup', cleanup);
  document.addEventListener('visibilitychange', visibilityChanged);
  if (motion.addEventListener) motion.addEventListener('change', motionChanged);
  else motion.addListener(motionChanged);
  if (material) {
    material.addEventListener('load', prepareMaterial);
    material.addEventListener('error', materialFailed);
    if (material.complete && material.naturalWidth) prepareMaterial();
    else if (material.complete) materialFailed();
  } else materialFailed();
})();
