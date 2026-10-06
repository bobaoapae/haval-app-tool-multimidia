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
  'car.basic.cur_journey_odometer': 18.6,
  'car.basic.cur_journey_drivetime': 42,
  'car.basic.cur_journey_avg_fuel_consume': 7.1,
  'car.basic.avg_vehicle_speed_since_startup': 38.4,
  'car.basic.accumulated_odometer': 286.4,
  'car.basic.accumulated_drivetime': 425,
  'car.basic.avg_fuel_consumption': 7.4,
  'car.basic.vehicle_speed_since_reset': 41.2,
  'car.basic.tire_pressure_front_left': 250,
  'car.basic.tire_pressure_front_right': 248,
  'car.basic.tire_pressure_rear_left': 220,
  'car.basic.tire_pressure_rear_right': 249,
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
  carPlayInDash: false,
  projectionPreparingD3: false,
};

const PANELS_NOT_SUPPORTED_BY_INJECTED_THEMES = [
  'testing-settings-harness',
  'testing-console-harness',
];

function sendControl(frameWindow, key, value) {
  if (key === 'cardId' && typeof frameWindow.onCardChanged === 'function') {
    frameWindow.onCardChanged(value);
    return;
  }
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

export function activateInjectedTelemetrySimulation(frameWindow, theme, stateManager) {
  if (theme?.keyboard !== 'none') return false;
  frameWindow.__TEST_HARNESS?.startSimulation?.();
  stateManager.pushInitialTelemetry();
  return true;
}

export function installLegacyTelemetryHarness(frameWindow, theme, callbacks = {}) {
  const usesInjectedTelemetry = theme?.telemetry === 'injected'
    || theme?.keyboard?.startsWith('legacy-');
  if (!frameWindow || !usesInjectedTelemetry) {
    return { cleanup() {} };
  }

  const childDocument = frameWindow.document;
  const isApexGt = theme.keyboard === 'none' && theme.folder === 'source/v1.0/ApexGT'
    && Boolean(childDocument.getElementById('apex-gt'));
  const stateManager = createLegacyTelemetryStateManager(frameWindow);
  const menuIds = theme.keyboard === 'legacy-sport' ? SPORT_MENU_ITEMS : BASIC_MENU_ITEMS;
  const menuItems = menuIds.map((id) => ({ id }));

  frameWindow.__THEME_LAB_STATE_MANAGER__ = stateManager;
  frameWindow.__THEME_LAB_MENU_ITEMS__ = menuItems;
  if (isApexGt) {
    let displayMode = 'Contour';
    try {
      if (frameWindow.Android?.getPreference?.('apexDisplayMode', 'Contour') === 'Vector') {
        displayMode = 'Vector';
      }
    } catch {
      // Keep the theme default if the optional preference store is unavailable.
    }
    // Seed before the shared harness creates its combo pills; otherwise its
    // initial default would overwrite a previously saved Vector selection.
    stateManager.set('apexDisplayMode', displayMode);
    stateManager.set('apex_display_mode', displayMode);
  }
  // The Theme Lab already owns legacy keyboard routing. This prevents the reused
  // v1.0 simulator from binding a second keydown listener while retaining its
  // telemetry engine and state controls.
  frameWindow.__SIMULATION_HARNESS_BOUND__ = true;
  stateManager.pushInitialTelemetry();

  let cleanedUp = false;
  const onReady = () => {
    if (cleanedUp) return;
    activateInjectedTelemetrySimulation(frameWindow, theme, stateManager);
    const unsupportedPanels = theme.keyboard === 'none'
      ? PANELS_NOT_SUPPORTED_BY_INJECTED_THEMES.filter((id) => !isApexGt || id !== 'testing-settings-harness')
      : [...PANELS_NOT_SUPPORTED_BY_INJECTED_THEMES, 'testing-shortcuts-harness'];
    for (const panelId of unsupportedPanels) {
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
