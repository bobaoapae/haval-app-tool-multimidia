import { getState as get, setState, subscribe, stateManager } from './state.js';
import { createDashboardInfo } from './components/dashboardInfo.js';
import { createAcControlScreen } from './components/aircon/mainAcControl.js';
import { createRegenScreen } from './components/regen/regenControl.js';
import { createDisplaySelectionScreen } from './components/display/themeSelection.js';
import { createMainMenu, menuItems } from './components/mainMenu.js';
import { createMask } from './components/display/mask.js';
import { createGraphScreen } from './components/graphs/graphs.js';
import { div } from '../../../shared/utils/createElement.js';
import { logger } from '../../../shared/utils/logger.js';
import { initializeConstants } from '../utils/constants.js';
import { initWarningHandler } from './components/warningHandler.js';
import { bridge } from '../../../shared/bridge/ThemeBridgeAdapter.js';
import { bootstrapThemeFromManifest, themeEngine } from '../../../shared/runtime/clusterRuntime.js';
import { KEYS, getLabel, translateFriendlyValue, FRIENDLY_KEY_TO_CAN_KEY } from '../../../shared/car/carConstants.js';
import { createGraphTelemetryHandler, getAdjustedSpeed } from '../../../shared/car/carDerivations.js';
import { initSimulationHarness } from '../../../shared/runtime/testing-utils.js';

initializeConstants();
initWarningHandler();

if (process.env.NODE_ENV === 'development') {
    document.body.style.backgroundColor = '#111315';
    initSimulationHarness(stateManager, menuItems);
}

const appContainer = document.getElementById('app');
let currentComponent = null;
let maskComponent = null;
let menuWrapper = null;
let dashboardCleanup = null;
const screenCache = {};

function isProjectionMapDisplayActive() {
    return get('projectionMirrorInDash') === true ||
        get('carPlayInDash') === true ||
        get('projectionPreparingD3') === true;
}

function isProjectionCardOverlayActive() {
    if (!isProjectionMapDisplayActive()) {
        return false;
    }
    if (get('projectionCardOverlayAllowed') !== true) {
        return false;
    }
    const screen = get('screen');
    return isMainMenuSessionScreen(screen) || screen === 'aircon';
}

function isMainMenuDetailScreen(screen) {
    return screen === 'graph' || screen === 'graphs' || screen === 'regen' || screen === 'display_selection';
}

function isMainMenuSessionScreen(screen) {
    return screen === 'main_menu' || isMainMenuDetailScreen(screen);
}

function getEffectiveDisplayMode() {
    if (isProjectionMapDisplayActive()) {
        return 'Mapa';
    }
    return get('display') || 'Normal';
}

// Initial state from URL parameters
const urlParams = new URLSearchParams(window.location.search);
const nativeMockEnabled =
    process.env.NODE_ENV === 'development' ||
    urlParams.get('nativeMocks') === '1' ||
    window.__ENABLE_NATIVE_MOCKS === true;

if (nativeMockEnabled) {
    window.__AIR_CONTROL_TEST_MODE = true;
    setState('enableOdometer', true);
    setState('enableRevisionWarning', true);
    setState('odometer', get('odometer') || 11450);
    setState('nextRevisionKm', get('nextRevisionKm') || 12000);
    setState('nextRevisionDate', get('nextRevisionDate') || Date.now() + 15 * 24 * 60 * 60 * 1000);
}

function initializeLayout() {
    logger.enter('initializeLayout');
    if (!appContainer) {
        logger.leave('initializeLayout');
        return;
    }
    appContainer.innerHTML = '';

    if (nativeMockEnabled) {
        try {
            const devBg = div({ className: 'dev-background' });
            appContainer.appendChild(devBg);
        } catch (e) {
            console.error('[Error] Failed to create dev-background:', e);
        }
    }

    // Add mask background first (z-index: 50)
    try {
        const mask = createMask();
        maskComponent = mask;
        appContainer.appendChild(mask.background);
    } catch (e) {
        logger.log('[Error] Failed to create mask: ' + e.message);
    }

    // 1. Dashboard Info Layer
    if (dashboardCleanup) dashboardCleanup();
    try {
        const dashboardInfo = createDashboardInfo;
        const { element: dashElement, menuWrapper: newMenuWrapper, cleanup: dashCleanup } = dashboardInfo();
        appContainer.appendChild(dashElement);

        menuWrapper = newMenuWrapper;
        dashboardCleanup = dashCleanup;

        // Pre-load critical screens
        if (menuWrapper) {
            const cachedScreens = ['main_menu', 'aircon'];
            cachedScreens.forEach(screen => {
                try {
                    let result = null;
                    if (screen === 'main_menu') result = createMainMenu();
                    else if (screen === 'aircon') result = createAcControlScreen();

                    if (result) {
                        const element = result.element || result;
                        element.style.display = 'none';
                        menuWrapper.appendChild(element);
                        if (result.onMount) result.onMount();
                        screenCache[screen] = result;
                    }
                } catch (e) {
                    logger.log(`[Error] Failed to pre-load screen ${screen}: ${e.message}`);
                }
            });
        }
    } catch (e) {
        logger.log('[Error] Failed to initialize dashboard info: ' + e.message);
    }

    // Add no app mask on top (z-index: 200)
    if (maskComponent) {
        appContainer.appendChild(maskComponent.noAppL);
        appContainer.appendChild(maskComponent.noAppR);
    }
    logger.leave('initializeLayout');
}


function render() {
    logger.enter('render', { screen: get('screen'), display: get('display') });
    const screen = get('screen');
    const projectionMapDisplayActive = isProjectionMapDisplayActive();
    const displayMode = getEffectiveDisplayMode();

    // Update app class based on display mode
    if (appContainer) {
        logger.log('Rendering screen:', screen);
        let classes = appContainer.className.split(' ').filter(c => !c.startsWith('display-') && !c.startsWith('theme-') && !c.startsWith('gauge-style-') && c !== 'cluster-disabled' && c !== 'warn-is-active' && c !== 'hide-header' && c !== 'hide-bottom' && c !== 'bar-images-hidden' && c !== 'app-in-dash-disabled' && c !== 'menu-minimized' && c !== 'carplay-in-dash' && c !== 'projection-mirror-in-dash' && c !== 'projection-preparing-d3' && c !== 'projection-map-display-active' && c !== 'projection-card-overlay-active');
        classes.push('display-' + displayMode.toLowerCase());

        if (get('clusterEnabled') === false) {
            classes.push('cluster-disabled');
        }

        const appInDash = get('appInDash');
        if (appInDash === false || appInDash === 'false') {
            classes.push('app-in-dash-disabled');
        }

        if (get('cardId') == 0 || get('warningActive') === true) {
            classes.push('warn-is-active');
        }
        if (get('cardId') == 0) {
            classes.push('menu-minimized');
        }
        if (nativeMockEnabled) {
            classes.push('native-mock-enabled');
        }
        const hiddenBars = get('hiddenBars') || 'Nenhuma';
        if (hiddenBars === 'Superior' || hiddenBars === 'Ambas') {
            classes.push('hide-header');
        }
        if (hiddenBars === 'Inferior' || hiddenBars === 'Ambas') {
            classes.push('hide-bottom');
        }
        // Decorative top/bottom bar artwork is on unless explicitly turned off,
        // so an unresolved setting (pre-bind boot frame) still renders the images
        if (get('barImages') === false) {
            classes.push('bar-images-hidden');
        }

        if (projectionMapDisplayActive) {
            classes.push('theme-mirror-cluster');
            classes.push('projection-mirror-in-dash');
            classes.push('projection-map-display-active');
            if (get('projectionPreparingD3') === true) {
                classes.push('projection-preparing-d3');
            }
            if (isProjectionCardOverlayActive()) {
                classes.push('projection-card-overlay-active');
            }
        }

        if (get('carPlayInDash') === true) {
            classes.push('carplay-in-dash');
        }

        const currentMode = (get('mode') || 'Dark').toLowerCase();
        if (!projectionMapDisplayActive) {
            classes.push('theme-' + currentMode);
        } else {
            classes.push('theme-mirror-cluster');
        }

        const currentGaugeStyle = (get('gaugeStyle') || 'Esportivo').toLowerCase();
        classes.push('gauge-style-' + currentGaugeStyle);

        appContainer.className = classes.join(' ').trim();
        logger.log('App classes:', appContainer.className);
    }


    // Hide all cached components
    Object.values(screenCache).forEach(comp => {
        const el = comp.element || comp;
        el.style.display = 'none';
    });

    // Cleanup previous non-cached component
    if (currentComponent && !Object.values(screenCache).includes(currentComponent)) {
        if (currentComponent.cleanup) {
            try {
                currentComponent.cleanup();
            } catch (e) {
                logger.log('[Error] Failed during component cleanup: ' + e.message);
            }
        }
        const el = currentComponent.element || currentComponent;
        if (el && el.parentNode === menuWrapper) {
            menuWrapper.removeChild(el);
        }
    }

    if (menuWrapper) {
        const cardId = get('cardId');
        const projectionCardOverlayActive = isProjectionCardOverlayActive();
        const rightMenuVisible = (cardId != 0);
        menuWrapper.style.display = (!rightMenuVisible || (projectionMapDisplayActive && !projectionCardOverlayActive)) ? 'none' : 'block';
    }

    if (screenCache[screen]) {
        // Show cached component
        const comp = screenCache[screen];
        const el = comp.element || comp;
        el.style.display = 'block';
        currentComponent = comp;
    } else {
        // Create non-cached component
        let componentResult = null;
        try {
            if (screen === 'regen') {
                componentResult = createRegenScreen();
            } else if (screen === 'display_selection') {
                componentResult = createDisplaySelectionScreen();
            } else if (screen === 'graph' || screen === 'graphs') {
                componentResult = createGraphScreen();
            }
        } catch (e) {
            logger.log('[Error] Failed to create screen component ' + screen + ': ' + e.message);
        }

        if (componentResult) {
            const element = componentResult.element || componentResult;
            const onMount = componentResult.onMount;

            if (menuWrapper) {
                menuWrapper.appendChild(element);
            }

            if (onMount) {
                try {
                    onMount();
                } catch (e) {
                    logger.log('[Error] Failed during component onMount: ' + e.message);
                }
            }

            currentComponent = componentResult;
        } else {
            currentComponent = null;
        }
    }
    logger.leave('render');
}

subscribe('warningActive', () => render());
subscribe('cardId', () => render());
initializeLayout();

// Start rendering and subscribe to listen for screen changes thus triggering new render
subscribe('screen', (screenName) => {
    themeEngine.navigateTo(screenName);
    render();
});
subscribe('display', render);
subscribe('hiddenBars', (val) => {
    console.log(`[STATE TRACE] hiddenBars changed to: ${val}`);
    render();
});
subscribe('mode', render);
subscribe('gaugeStyle', render);
subscribe('barImages', render);

subscribe('clusterEnabled', render);
subscribe('appInDash', render);
subscribe('carPlayInDash', render);
subscribe('projectionMirrorInDash', render);
subscribe('projectionPreparingD3', render);
subscribe('projectionCardOverlayAllowed', render);
render();

// Setup Decentralized Bridge & Key events
const manifest = {
    disableDefaultKeys: true,
    menu: [
        { id: 'option_1', action: 'cycle', key: 'car.drive_setting.esp_enable', values: ['1', '0'] },
        { id: 'option_2', action: 'cycle', key: 'car.ev_setting.power_model_config', values: ['3', '1', '0'] },
        { id: 'option_3', action: 'cycle', key: 'car.drive_setting.drive_mode', values: ['0', '2', '1'] },
        { id: 'option_7', action: 'navigate', screen: 'graph' },
        { id: 'option_5', action: 'cycle', key: 'car.drive_setting.steering_wheel_assist_mode', values: ['2', '0', '1'] },
        { id: 'option_6', action: 'navigate', screen: 'regen' },
        { id: 'option_4', action: 'navigate', screen: 'display_selection' }
    ]
};

let focusController;
let lastKeyTime = 0;
const KEY_DEBOUNCE_MS = 50;

function handleSteeringWheelKey(keyName) {
    const now = Date.now();
    if (now - lastKeyTime < KEY_DEBOUNCE_MS) {
        logger.log(`[Steering Wheel] Debounced duplicate keyevent: ${keyName}`);
        return;
    }
    lastKeyTime = now;

    // Wake up from Clean mode to Normal display mode on any key event
    if (get('display') === 'Clean' && get('screen') !== 'display_selection') {
        setState('display', 'Normal');
        bridge.updateCarData('display', 'Normal');
        logger.log(`[Steering Wheel] Clean display mode active. Returning to Normal display mode on key event: ${keyName}`);
        return; // Consume the key event as a wake-up trigger
    }

    const screen = get('screen');
    logger.log(`Steering wheel event received: ${keyName} on screen: ${screen}`);
    
    if (screen === 'main_menu') {
        if (keyName === 'UP' && focusController) {
            focusController.prev();
            setState('focusedMenuItem', focusController.focusedId);
        } else if (keyName === 'DOWN' && focusController) {
            focusController.next();
            setState('focusedMenuItem', focusController.focusedId);
        } else if (keyName === 'ENTER' && focusController) {
            const activeId = focusController.focusedId;
            const menuItem = manifest.menu.find(item => item.id === activeId);
            if (!menuItem) return;

            if (menuItem.action === 'cycle') {
                const currentVal = bridge.getCarData(menuItem.key);
                const currentIndex = menuItem.values.indexOf(currentVal);
                const nextIndex = (currentIndex + 1) % menuItem.values.length;
                const nextValue = menuItem.values[nextIndex];
                bridge.updateCarData(menuItem.key, nextValue);
            } else if (menuItem.action === 'navigate' && menuItem.screen) {
                setState('screen', menuItem.screen);
            }
        }
    } else if (screen === 'aircon') {
        const focusArea = get('focusArea') || 'fan';
        if (keyName === 'LEFT' || keyName === 'RIGHT' || keyName === 'ENTER') {
            setState('focusArea', focusArea === 'fan' ? 'temp' : 'fan');
        } else if (keyName === 'UP' || keyName === 'DOWN') {
            if (focusArea === 'fan') {
                const currentFan = Number(get('fan')) || 0;
                let nextFan = currentFan;
                if (keyName === 'UP') {
                    nextFan = Math.min(7, currentFan + 1);
                } else {
                    nextFan = Math.max(0, currentFan - 1);
                }
                if (currentFan === 0 && nextFan > 0) {
                    bridge.updateCarData('car.hvac.power_mode', '1');
                } else if (nextFan === 0) {
                    bridge.updateCarData('car.hvac.power_mode', '0');
                }
                bridge.updateCarData('car.hvac.fan_speed', String(nextFan));
            } else {
                const currentTemp = Number(get('temp')) || 22;
                let nextTemp = currentTemp;
                if (keyName === 'UP') {
                    if (currentTemp === 16) nextTemp = 17;
                    else if (currentTemp >= 25) nextTemp = 32;
                    else nextTemp = currentTemp + 1;
                } else {
                    if (currentTemp === 32) nextTemp = 25;
                    else if (currentTemp <= 17) nextTemp = 16;
                    else nextTemp = currentTemp - 1;
                }
                bridge.updateCarData('car.hvac.driver_temperature', String(nextTemp));
            }
        } else if (keyName === 'ENTER_LONG') {
            bridge.triggerSystemAction('CANCEL_MAX_AC');
        } else if (keyName === 'BACK') {
            setState('screen', 'main_menu');
        }
    } else if (screen === 'regen') {
        if (keyName === 'UP' || keyName === 'DOWN') {
            const currentRegen = get('regenMode');
            let nextVal = '0';
            if (keyName === 'UP') {
                if (currentRegen === 'Baixo') nextVal = '0'; // Normal
                else if (currentRegen === 'Normal') nextVal = '1'; // Alto
                else nextVal = '1'; // Alto stays
            } else {
                if (currentRegen === 'Alto') nextVal = '0'; // Normal
                else if (currentRegen === 'Normal') nextVal = '2'; // Baixo
                else nextVal = '2'; // Baixo stays
            }
            bridge.updateCarData('car.ev_setting.energy_recovery_level', nextVal);
        } else if (keyName === 'ENTER_LONG') {
            const onepedal = get('onepedal');
            const nextVal = onepedal ? '0' : '1';
            bridge.updateCarData('car.ev.setting.pedal_control_enable', nextVal);
            setState('onepedal', !onepedal);
        } else if (keyName === 'BACK') {
            setState('screen', 'main_menu');
        }
    } else if (screen === 'display_selection') {
        const displays = ['Normal', 'Reduzido', 'Clean', 'Mapa'];
        let currentIdx = displays.indexOf(get('display'));
        if (currentIdx === -1) currentIdx = 0;
        
        if (keyName === 'UP') {
            currentIdx = (currentIdx - 1 + displays.length) % displays.length;
            const nextDisplay = displays[currentIdx];
            setState('display', nextDisplay);
            bridge.updateCarData('display', nextDisplay);
        } else if (keyName === 'DOWN') {
            currentIdx = (currentIdx + 1) % displays.length;
            const nextDisplay = displays[currentIdx];
            setState('display', nextDisplay);
            bridge.updateCarData('display', nextDisplay);
        } else if (keyName === 'ENTER') {
            setState('screen', 'main_menu');
        } else if (keyName === 'BACK') {
            setState('screen', 'main_menu');
        }
    } else if (screen === 'graph' || screen === 'graphs') {
        const graphs = ['evConsumption', 'gasConsumption', 'carSpeed'];
        let currentIdx = graphs.indexOf(get('currentGraph'));
        if (currentIdx === -1) currentIdx = 0;

        if (keyName === 'UP') {
            currentIdx = (currentIdx - 1 + graphs.length) % graphs.length;
            setState('currentGraph', graphs[currentIdx]);
        } else if (keyName === 'DOWN') {
            currentIdx = (currentIdx + 1) % graphs.length;
            setState('currentGraph', graphs[currentIdx]);
        } else if (keyName === 'BACK') {
            setState('screen', 'main_menu');
        }
    }
}

async function initDecentralizedBridge() {
    if (typeof bridge.reset === 'function') bridge.reset();
    if (typeof themeEngine.reset === 'function') themeEngine.reset();
    await bridge.init();
    
    // Initialize focus cycle with bootstrap helper
    const bootstrapper = bootstrapThemeFromManifest(manifest);
    focusController = bootstrapper.focusController;
    
    // Synchronize focusController index when focusedMenuItem state changes programmatically
    subscribe('focusedMenuItem', (id) => {
        if (focusController) {
            const itemIds = manifest.menu.map(item => item.id);
            const idx = itemIds.indexOf(id);
            if (idx !== -1 && focusController.index !== idx) {
                focusController.setIndex(idx);
            }
        }
    });

    // Boot-time alignment of initial focus index
    const startFocused = get('focusedMenuItem');
    const startIdx = manifest.menu.map(item => item.id).indexOf(startFocused);
    if (startIdx !== -1 && focusController) {
        focusController.setIndex(startIdx);
    }
    
    // Initialize preferences and initial states
    bridge.bindThemeSetting('hiddenBars', 'Nenhuma', setState);
    bridge.bindThemeSetting('mode', 'Dark', setState);
    bridge.bindThemeSetting('gaugeStyle', 'Esportivo', setState);
    bridge.bindThemeSetting('barImages', true, setState);
    const enableOdometer = bridge.getPreference('enableOdometer', 'true') === 'true';
    const enableRevisionWarning = bridge.getPreference('enableRevisionWarning', 'false') === 'true';
    const nextRevisionKm = Number(bridge.getPreference('nextRevisionKm', '0')) || 0;
    const nextRevisionDate = Number(bridge.getPreference('nextRevisionDate', '0')) || 0;
    const fuelUnit = bridge.getPreference('fuelDisplayUnit', 'liters');
    
    setState('enableOdometer', enableOdometer);
    setState('enableRevisionWarning', enableRevisionWarning);
    setState('nextRevisionKm', nextRevisionKm);
    setState('nextRevisionDate', nextRevisionDate);
    setState('fuelDisplayUnit', fuelUnit);
    
    // Sync initial cardId from JNI
    const initialCardId = Number(bridge.getCarData('cardId')) || 1;
    setState('cardId', initialCardId);

    const handleGraphTelemetry = createGraphTelemetryHandler(setState, {
        adjustSpeed: (rawSpeed) => {
            const enabled = bridge.getPreference('enableSpeedAdjustment', 'false') === 'true';
            const offset = parseFloat(bridge.getPreference('speedAdjustmentOffset', '0')) || 0.0;
            return getAdjustedSpeed(rawSpeed, enabled, offset);
        }
    });

    // Subscribe to CAN and virtual telemetry keys
    const keysToSubscribe = [
        KEYS.VEHICLE_SPEED,
        KEYS.ENGINE_SPEED,
        KEYS.INSTANT_FUEL_CONSUMPTION,
        KEYS.ENERGY_OUTPUT_PERCENTAGE,
        KEYS.CHARGE_CURRENT,
        KEYS.BATTERY_VOLTAGE,
        KEYS.INSTANT_ENERGY_CONSUMPTION,
        KEYS.REMAIN_FUEL_PERCENTAGE,
        KEYS.BATTERY_POWER_PERCENTAGE,
        KEYS.FUEL_MODE_REMAIN_ODOMETER,
        KEYS.ELECTRIC_MODE_REMAIN_ODOMETER,
        "car.basic.total_odometer",
        "car.basic.gear_status",
        "car.drive_setting.esp_enable",
        "car.ev_setting.power_model_config",
        "car.drive_setting.drive_mode",
        "car.drive_setting.steering_wheel_assist_mode",
        "car.ev_setting.energy_recovery_level",
        "car.ev.setting.pedal_control_enable",
        "car.hvac.power_mode",
        "car.hvac.fan_speed",
        "car.hvac.driver_temperature",
        "car.hvac.cycle_mode",
        "car.basic.inside_temp",
        "car.basic.outside_temp",
        "car.configure.default_temp_unit",
        
        "app.preferences.enableSpeedAdjustment",
        "app.preferences.speedAdjustmentOffset",
        "app.preferences.enableOdometer",
        "app.preferences.enableRevisionWarning",
        "app.preferences.nextRevisionKm",
        "app.preferences.nextRevisionDate",
        "app.preferences.fuelDisplayUnit",
        
        "app.display.1.active_app",
        "app.display.1.active_app_label",
        "app.display.1.active_app_icon",
        "app.display.3.active_app",
        "app.display.3.active_app_label",
        "app.display.3.active_app_icon",
        "app.launcher.apps",
        "app.navigation.directions",
        
        "app.media.state",
        "app.media.title",
        "app.media.artist",
        "app.media.album",
        "app.media.duration",
        "app.media.position",
        "app.media.album_art",
        "app.phone.state",
        "app.phone.caller_name",
        "app.phone.caller_number",
        "app.phone.call_duration",

        // NOTE: "warningActive" is intentionally NOT subscribed here. It's a
        // theme-computed derived value (see warningHandler.js) that already
        // reaches us as a typed boolean via window.control('warningActive', ...).
        // Subscribing it here as if it were native telemetry double-delivers
        // it through onDataChanged as a STRING ("false"/"true"), which is
        // always truthy and corrupts the boolean state.
        "bsdLeft",
        "bsdRight",
        "carPlayInDash",
        "projectionMirrorInDash",
        "projectionPreparingD3",
        "projectionCardOverlayAllowed"
    ];
    
    bridge.subscribe(keysToSubscribe, (key, value) => {
        if (handleGraphTelemetry(key, value)) return;

        let val = value;
        if (typeof value === 'string' && value.trim() !== '' && !isNaN(value)) {
            val = Number(value);
        }
        
        switch (key) {
            case "car.basic.total_odometer":
                setState('odometer', val);
                break;
            case "car.basic.gear_status":
                setState('gearState', getLabel("car.basic.gear_status", value));
                break;
            case "car.drive_setting.esp_enable":
                setState('espStatus', getLabel("car.drive_setting.esp_enable", value));
                break;
            case "car.ev_setting.power_model_config":
                setState('evMode', getLabel("car.ev_setting.power_model_config", value));
                break;
            case "car.drive_setting.drive_mode":
                setState('drivingMode', getLabel("car.drive_setting.drive_mode", value));
                break;
            case "car.drive_setting.steering_wheel_assist_mode":
                setState('steerMode', getLabel("car.drive_setting.steering_wheel_assist_mode", value));
                break;
            case "car.ev_setting.energy_recovery_level": {
                let regenLabel = "Normal";
                if (value === "0" || value === 0) regenLabel = "Normal";
                else if (value === "1" || value === 1) regenLabel = "Alto";
                else if (value === "2" || value === 2) regenLabel = "Baixo";
                setState('regenMode', regenLabel);
                break;
            }
            case "car.ev.setting.pedal_control_enable":
                setState('onepedal', value === "1" || value === 1 || value === "true" || value === true);
                break;
            case "car.hvac.power_mode":
                setState('power', val);
                break;
            case "car.hvac.fan_speed":
                setState('fan', val);
                break;
            case "car.hvac.driver_temperature":
                setState('temp', val);
                break;
            case "car.hvac.cycle_mode":
                setState('recycle', val);
                break;
            case "car.basic.inside_temp":
                setState('inside_temp', value);
                break;
            case "car.basic.outside_temp":
                setState('outside_temp', value);
                break;
            case "car.configure.default_temp_unit":
                setState('tempUnit', value === "1" ? "°F" : "°C");
                break;
            case KEYS.REMAIN_FUEL_PERCENTAGE:
                setState('fuelPercent', val);
                break;
            case KEYS.BATTERY_POWER_PERCENTAGE:
                setState('batteryPercent', val);
                break;
            case KEYS.FUEL_MODE_REMAIN_ODOMETER:
                setState('fuelRange', val);
                break;
            case KEYS.ELECTRIC_MODE_REMAIN_ODOMETER:
                setState('batteryRange', val);
                break;
            
            // Preference updates
            case "app.preferences.enableSpeedAdjustment": {
                const rawSpeed = bridge.getCarData("car.basic.vehicle_speed");
                const enableAdj = value === 'true';
                const offset = parseFloat(bridge.getPreference('speedAdjustmentOffset', '0')) || 0.0;
                setState('carSpeed', Number(getAdjustedSpeed(rawSpeed, enableAdj, offset)));
                break;
            }
            case "app.preferences.speedAdjustmentOffset": {
                const rawSpeed = bridge.getCarData("car.basic.vehicle_speed");
                const enableAdj = bridge.getPreference('enableSpeedAdjustment', 'false') === 'true';
                const offset = parseFloat(value) || 0.0;
                setState('carSpeed', Number(getAdjustedSpeed(rawSpeed, enableAdj, offset)));
                break;
            }
            case "app.preferences.enableOdometer":
                setState('enableOdometer', value === 'true');
                break;
            case "app.preferences.enableRevisionWarning":
                setState('enableRevisionWarning', value === 'true');
                break;
            case "app.preferences.nextRevisionKm":
                setState('nextRevisionKm', val);
                break;
            case "app.preferences.nextRevisionDate":
                setState('nextRevisionDate', val);
                break;
            case "app.preferences.fuelDisplayUnit":
                setState('fuelDisplayUnit', value);
                break;
                
            default:
                setState(key, val);
                break;
        }
    });
    
    // Register steering wheel physical key listeners
    bridge.subscribeKeys((keyName) => {
        handleSteeringWheelKey(keyName);
    });
}

initDecentralizedBridge().catch(e => console.error("Bridge initialization failed:", e));



// Handle Card ID transitions
subscribe('cardId', (cardId) => {
    logger.log('cardId change:', cardId);

    // Sync with Android bridge for correct app resizing
    if (window.Android && window.Android.setCardId) {
        window.Android.setCardId(cardId);
    }

    // 0 = hide the right menu display
    if (menuWrapper) {
        const projectionMapDisplayActive = isProjectionMapDisplayActive();
        const projectionCardOverlayActive = isProjectionCardOverlayActive();
        const rightMenuVisible = (cardId != 0);
        menuWrapper.style.display = (!rightMenuVisible || (projectionMapDisplayActive && !projectionCardOverlayActive)) ? 'none' : 'block';
    }

    if (cardId == 1) {
        // 1 = go to main regular menu
        setState('screen', 'main_menu');
    } else if (cardId == 3) {
        // 3 = set to AC menu
        setState('screen', 'aircon');
    }
});

// Functions used by Kotlin to trigger interactions
window.showScreen = function (screenName) {
    try {
        logger.enter('window.showScreen', screenName);
        setState('screen', screenName);
        logger.leave('window.showScreen');
    } catch (e) {
        console.error('[Error] Bridge showScreen failed:', e);
    }
};

window.focus = function (item) {
    try {
        logger.enter('window.focus', item);
        const screen = get('screen');
        if (screen === 'main_menu') {
            setState('focusedMenuItem', item);
        } else if (screen === 'aircon') {
            setState('focusArea', item);
        } else if (screen === 'display_selection') {
            setState('displayFocus', item);
        }
        logger.leave('window.focus');
    } catch (e) {
        console.error('[Error] Bridge focus failed:', e);
    }
};

window.control = function (key, value) {
    try {
        if (key !== 'carSpeed' && key !== 'engineRPM') {
            logger.log(`control('${key}', ${value})`);
        }
        logger.enter('window.control', { key, value });
        let val = value;
        if (FRIENDLY_KEY_TO_CAN_KEY[key]) {
            // Raw CAN value pushed under a friendly display key (e.g. espStatus
            // '1') -> translate to its label via the shared car constants table.
            // Covers the native card-entry/snapshot refresh path, which pushes
            // raw values here (the live bridge.subscribe path below already
            // applies getLabel() per-key and is unaffected).
            val = translateFriendlyValue(key, value);
        } else if (typeof value === 'string' && value.trim() !== '' && !isNaN(value)) {
            // Automatically convert numeric strings to numbers for compatibility with components
            val = Number(value);
        }
        setState(key, val);
        // warningActive has its own subscription to render() at line 184, so no need for manual trigger here
        logger.leave('window.control');
    } catch (e) {
        console.error('[Error] Bridge control failed for key ' + key + ':', e);
    }
};

// Backend-driven card change (contract: window.onCardChanged). The backend owns
// the active card and notifies here exclusively — no legacy control('cardId', ...)
// fallback. The subscribe('cardId', ...) handler above reacts to the state change.
// Chained: clusterRuntime's themeEngine already wraps window.onCardChanged to
// dispatch window.onCardTransition; preserve that instead of clobbering it.
(function () {
    const prevOnCardChanged = window.onCardChanged;
    window.onCardChanged = function (cardId) {
        if (typeof prevOnCardChanged === 'function') {
            try { prevOnCardChanged(cardId); } catch (e) { console.error(e); }
        }
        try {
            logger.log('[onCardChanged] cardId:', cardId);
            setState('cardId', Number(cardId));
        } catch (e) {
            console.error('[Error] Bridge onCardChanged failed:', e);
        }
    };
})();

window.__havalProjectionDebug = function () {
    const app = document.getElementById('app');
    const menu = document.querySelector('.dashboard-menu-container');
    const main = document.querySelector('.main-container');
    return {
        carPlayInDash: get('carPlayInDash'),
        projectionMirrorInDash: get('projectionMirrorInDash'),
        projectionPreparingD3: get('projectionPreparingD3'),
        cardId: get('cardId'),
        screen: get('screen'),
        display: get('display'),
        effectiveDisplay: getEffectiveDisplayMode(),
        projectionCardOverlayAllowed: get('projectionCardOverlayAllowed'),
        projectionMapDisplayActive: isProjectionMapDisplayActive(),
        projectionCardOverlayActive: isProjectionCardOverlayActive(),
        appClass: app ? app.className : null,
        menuDisplay: menu ? getComputedStyle(menu).display : null,
        menuVisibility: menu ? getComputedStyle(menu).visibility : null,
        menuOpacity: menu ? getComputedStyle(menu).opacity : null,
        mainDisplay: main ? getComputedStyle(main).display : null,
        mainVisibility: main ? getComputedStyle(main).visibility : null,
        mainOpacity: main ? getComputedStyle(main).opacity : null,
    };
};

window.cleanup = function () {
    try {
        logger.enter('window.cleanup');
        if (currentComponent && currentComponent.cleanup) {
            currentComponent.cleanup();
        }
        logger.leave('window.cleanup');
    } catch (e) {
        console.error('[Error] Bridge cleanup failed:', e);
    }
};
