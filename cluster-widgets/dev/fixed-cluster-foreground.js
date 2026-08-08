const FIXED_LAYER_ID = 'theme-lab-fixed-cluster-foreground';
const FIXED_STYLE_ID = 'theme-lab-fixed-cluster-foreground-style';

export const CLUSTER_CANVAS = Object.freeze({ width: 1920, height: 720 });
export const SPORT_THEME_FIXED_MOCK_SELECTOR = '.g20-v2-native-status-mock';

// Coordinates measured from the existing Default native mock on the 1920x720 canvas.
// `safeZone` is deliberately larger than `visibleBounds`: themes must keep decorative
// strokes and important content outside it because the real cluster composites these
// OEM indicators above the WebView.
export const FIXED_CLUSTER_FOREGROUND = Object.freeze({
  ready: Object.freeze({
    label: 'READY',
    visibleBounds: Object.freeze({ x: 258, y: 335, width: 64, height: 20 }),
    safeZone: Object.freeze({ x: 238, y: 315, width: 104, height: 60 }),
  }),
  trafficSign: Object.freeze({
    label: 'Placa de velocidade',
    visibleBounds: Object.freeze({ x: 247, y: 497, width: 46, height: 46 }),
    safeZone: Object.freeze({ x: 225, y: 475, width: 90, height: 90 }),
  }),
  esp: Object.freeze({
    label: 'ESP',
    visibleBounds: Object.freeze({ x: 350.4, y: 466, width: 59.2, height: 48 }),
    safeZone: Object.freeze({ x: 330, y: 445, width: 100, height: 100 }),
  }),
});

const FIXED_LAYER_CSS = `
  :root {
    --haval-ready-safe-x: 238px;
    --haval-ready-safe-y: 315px;
    --haval-ready-safe-width: 104px;
    --haval-ready-safe-height: 60px;
    --haval-traffic-sign-safe-x: 225px;
    --haval-traffic-sign-safe-y: 475px;
    --haval-traffic-sign-safe-width: 90px;
    --haval-traffic-sign-safe-height: 90px;
    --haval-esp-safe-x: 330px;
    --haval-esp-safe-y: 445px;
    --haval-esp-safe-width: 100px;
    --haval-esp-safe-height: 100px;
  }

  /* Theme-owned mocks are replaced by one contract mock so every catalog entry
     uses exactly the same physical coordinates and foreground precedence. */
  html.theme-lab-fixed-foreground body #app .dashboard-fixed-overlay,
  html.theme-lab-fixed-foreground body #app .dashboard-sport-fixed-overlay,
  html.theme-lab-fixed-foreground body .dashboard-fixed-overlay,
  html.theme-lab-fixed-foreground body .dashboard-sport-fixed-overlay,
  html.theme-lab-fixed-foreground body ${SPORT_THEME_FIXED_MOCK_SELECTOR} {
    display: none !important;
  }

  #${FIXED_LAYER_ID} {
    position: fixed !important;
    inset: 0 auto auto 0 !important;
    width: 1920px !important;
    height: 720px !important;
    margin: 0 !important;
    padding: 0 !important;
    overflow: hidden !important;
    pointer-events: none !important;
    visibility: visible !important;
    opacity: 1 !important;
    transform: none !important;
    isolation: isolate !important;
    contain: layout style paint !important;
    z-index: 2147483647 !important;
  }

  #${FIXED_LAYER_ID},
  #${FIXED_LAYER_ID} * {
    box-sizing: border-box !important;
  }

  #${FIXED_LAYER_ID} .haval-fixed-ready {
    position: absolute !important;
    left: 258px !important;
    top: 335px !important;
    width: 64px !important;
    height: 20px !important;
    color: #2afc2a !important;
    font-family: Eurostile, "Arial Narrow", Arial, sans-serif !important;
    font-size: 20px !important;
    font-weight: 700 !important;
    letter-spacing: 1px !important;
    line-height: 20px !important;
    text-align: center !important;
    text-shadow: 0 0 12px rgba(42, 252, 42, 0.45) !important;
    white-space: nowrap !important;
  }

  #${FIXED_LAYER_ID} .haval-fixed-traffic-sign {
    position: absolute !important;
    left: 247px !important;
    top: 497px !important;
    width: 46px !important;
    height: 46px !important;
    border: 4px solid #ff3b30 !important;
    border-radius: 50% !important;
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
    color: #1b1b1b !important;
    background: #ffffff !important;
    font-family: Eurostile, "Arial Narrow", Arial, sans-serif !important;
    font-size: 25px !important;
    font-weight: 700 !important;
    line-height: 1 !important;
  }

  #${FIXED_LAYER_ID} .haval-fixed-esp {
    position: absolute !important;
    left: 350.4px !important;
    top: 466px !important;
    width: 59.2px !important;
    height: 48px !important;
  }

  #${FIXED_LAYER_ID} .haval-fixed-esp-graphic {
    position: absolute !important;
    inset: 0 auto auto 0 !important;
    width: 74px !important;
    height: 60px !important;
    transform: scale(0.8) !important;
    transform-origin: top left !important;
  }

  #${FIXED_LAYER_ID} .haval-fixed-esp-lane {
    position: absolute !important;
    top: 6px !important;
    width: 4px !important;
    height: 46px !important;
    border-radius: 4px !important;
    background: repeating-linear-gradient(
      to bottom,
      #20f640 0,
      #20f640 5px,
      transparent 5px,
      transparent 11px
    ) !important;
    filter: drop-shadow(0 0 4px rgba(32, 246, 64, 0.55)) !important;
  }

  #${FIXED_LAYER_ID} .haval-fixed-esp-lane.is-left {
    left: 10px !important;
  }

  #${FIXED_LAYER_ID} .haval-fixed-esp-lane.is-right {
    right: 10px !important;
  }

  #${FIXED_LAYER_ID} .haval-fixed-esp-car {
    position: absolute !important;
    left: 50% !important;
    top: 50% !important;
    width: 28px !important;
    height: 44px !important;
    border: 4px solid #20f640 !important;
    border-radius: 8px !important;
    box-shadow: 0 0 10px rgba(32, 246, 64, 0.45) !important;
    transform: translate(-50%, -50%) rotate(30deg) !important;
  }

  #${FIXED_LAYER_ID} .haval-fixed-esp-car::before {
    content: "" !important;
    position: absolute !important;
    left: 50% !important;
    top: 8px !important;
    width: 3px !important;
    height: 22px !important;
    border-radius: 2px !important;
    background: #20f640 !important;
    transform: translateX(-50%) !important;
  }
`;

function createEspIcon(document) {
  const icon = document.createElement('div');
  icon.className = 'haval-fixed-esp';
  icon.dataset.fixedClusterIcon = 'esp';

  const graphic = document.createElement('div');
  graphic.className = 'haval-fixed-esp-graphic';

  const leftLane = document.createElement('span');
  leftLane.className = 'haval-fixed-esp-lane is-left';
  const car = document.createElement('span');
  car.className = 'haval-fixed-esp-car';
  const rightLane = document.createElement('span');
  rightLane.className = 'haval-fixed-esp-lane is-right';

  graphic.append(leftLane, car, rightLane);
  icon.appendChild(graphic);
  return icon;
}

export function installFixedClusterForeground(frameWindow) {
  const { document } = frameWindow;
  if (!document?.head || !document?.body) {
    throw new Error('documento do tema ainda não está pronto');
  }

  document.getElementById(FIXED_LAYER_ID)?.remove();
  document.getElementById(FIXED_STYLE_ID)?.remove();

  const style = document.createElement('style');
  style.id = FIXED_STYLE_ID;
  style.textContent = FIXED_LAYER_CSS;
  document.head.appendChild(style);

  const layer = document.createElement('div');
  layer.id = FIXED_LAYER_ID;
  layer.setAttribute('aria-hidden', 'true');
  layer.setAttribute('role', 'presentation');
  layer.dataset.contractLayer = 'fixed-cluster-foreground-v1';

  const ready = document.createElement('div');
  ready.className = 'haval-fixed-ready';
  ready.dataset.fixedClusterIcon = 'ready';
  ready.textContent = 'READY';

  const trafficSign = document.createElement('div');
  trafficSign.className = 'haval-fixed-traffic-sign';
  trafficSign.dataset.fixedClusterIcon = 'traffic-sign';
  trafficSign.textContent = '30';

  layer.append(ready, trafficSign, createEspIcon(document));
  document.documentElement.classList.add('theme-lab-fixed-foreground');
  document.body.appendChild(layer);

  return {
    cleanup() {
      layer.remove();
      style.remove();
      document.documentElement.classList.remove('theme-lab-fixed-foreground');
    },
  };
}
