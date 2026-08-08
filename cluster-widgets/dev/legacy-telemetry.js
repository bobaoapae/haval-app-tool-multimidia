import { BASIC_MENU_ITEMS, SPORT_MENU_ITEMS } from './legacy-keyboard.js';

const INITIAL_STATE = {
  screen: 'main_menu',
  cardId: 1,
  carSpeed: 0,
  engineRPM: 0,
  evPowerKw: 0,
  evPowerFactor: 0,
  fuelPercent: 100,
  batteryPercent: 100,
  fuelRange: 700,
  batteryRange: 170,
  odometer: 11450,
  gasConsumptionMode: 'Running',
  gasConsumption: 0,
  gasConsumptionIdle: 0,
  inside_temp: 32,
  outside_temp: 28,
  gearState: 'P',
  drivingMode: 'Normal',
  evMode: 'HEV',
  steerMode: 'Conforto',
  espStatus: 'ON',
  regenMode: 'Normal',
  fan: 1,
  temp: 22,
  power: 1,
  auto: 0,
  maxauto: 0,
  recycle: 0,
  focusArea: 'fan',
};

const PANELS_NOT_SUPPORTED_BY_LEGACY_THEMES = [
  'testing-settings-harness',
  'testing-shortcuts-harness',
  'testing-console-harness',
];

function sendControl(frameWindow, key, value) {
  if (typeof frameWindow.control === 'function') {
    frameWindow.control(key, value);
  }
}

export function createLegacyTelemetryStateManager(frameWindow) {
  const values = new Map(Object.entries(INITIAL_STATE));
  const listeners = new Map();
  let disposed = false;

  const notify = (key, value) => {
    for (const listener of listeners.get(key) || []) listener(value);
  };

  const set = (key, value) => {
    if (disposed || Object.is(values.get(key), value)) return;
    values.set(key, value);
    sendControl(frameWindow, key, value);
    notify(key, value);
  };

  return {
    get: (key) => values.get(key),
    getState: () => Object.fromEntries(values),
    set,
    subscribe(key, listener) {
      if (!listeners.has(key)) listeners.set(key, new Set());
      listeners.get(key).add(listener);
      return () => listeners.get(key)?.delete(listener);
    },
    pushInitialTelemetry() {
      for (const [key, value] of values) sendControl(frameWindow, key, value);
    },
    dispose() {
      disposed = true;
      listeners.clear();
    },
  };
}

export function installLegacyTelemetryHarness(frameWindow, theme, callbacks = {}) {
  if (!frameWindow || !theme?.keyboard?.startsWith('legacy-')) {
    return { cleanup() {} };
  }

  const childDocument = frameWindow.document;
  const stateManager = createLegacyTelemetryStateManager(frameWindow);
  const menuIds = theme.keyboard === 'legacy-sport' ? SPORT_MENU_ITEMS : BASIC_MENU_ITEMS;
  const menuItems = menuIds.map((id) => ({ id }));

  frameWindow.__THEME_LAB_STATE_MANAGER__ = stateManager;
  frameWindow.__THEME_LAB_MENU_ITEMS__ = menuItems;
  // The Theme Lab already owns legacy keyboard routing. This prevents the reused
  // v1.0 simulator from binding a second keydown listener while retaining its
  // telemetry engine and state controls.
  frameWindow.__SIMULATION_HARNESS_BOUND__ = true;
  stateManager.pushInitialTelemetry();

  let cleanedUp = false;
  const onReady = () => {
    if (cleanedUp) return;
    for (const panelId of PANELS_NOT_SUPPORTED_BY_LEGACY_THEMES) {
      childDocument.getElementById(panelId)?.remove();
    }
    callbacks.onReady?.();
  };
  const onFailure = (event) => {
    if (cleanedUp) return;
    callbacks.onError?.(event?.detail || 'falha ao carregar o simulador compartilhado');
  };

  frameWindow.addEventListener('theme-lab-telemetry-ready', onReady, { once: true });
  frameWindow.addEventListener('theme-lab-telemetry-error', onFailure, { once: true });

  const bootstrap = childDocument.createElement('script');
  bootstrap.type = 'module';
  bootstrap.id = 'theme-lab-legacy-telemetry-bootstrap';
  bootstrap.textContent = `
    import { initSimulationHarness } from '/source/v1.0/shared/runtime/testing-utils.js';

    try {
      initSimulationHarness(window.__THEME_LAB_STATE_MANAGER__, window.__THEME_LAB_MENU_ITEMS__);
      window.dispatchEvent(new CustomEvent('theme-lab-telemetry-ready'));
    } catch (error) {
      console.error('[Theme Lab] Falha ao iniciar telemetria legada:', error);
      window.dispatchEvent(new CustomEvent('theme-lab-telemetry-error', {
        detail: error instanceof Error ? error.message : String(error),
      }));
    }
  `;
  bootstrap.addEventListener('error', onFailure, { once: true });
  childDocument.head.appendChild(bootstrap);

  return {
    cleanup() {
      cleanedUp = true;
      frameWindow.removeEventListener('theme-lab-telemetry-ready', onReady);
      frameWindow.removeEventListener('theme-lab-telemetry-error', onFailure);
      try {
        frameWindow.__TEST_HARNESS?.stopSimulation?.();
        if (frameWindow.simulationInterval) frameWindow.clearInterval(frameWindow.simulationInterval);
        bootstrap.remove();
        delete frameWindow.__THEME_LAB_STATE_MANAGER__;
        delete frameWindow.__THEME_LAB_MENU_ITEMS__;
      } catch {
        // The iframe may already be navigating to another theme.
      }
      stateManager.dispose();
    },
  };
}
