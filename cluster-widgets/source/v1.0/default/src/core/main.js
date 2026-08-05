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
import { applyAccent, applyIconColor, DEFAULT_ACCENT, DEFAULT_ICON_COLOR } from './accent.js';

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
        return get('navigationDisplayMode') || 'Mapa';
    }
    const appInDash = get('appInDash');
    if (appInDash === true || appInDash === 'left' || appInDash === 'right') {
        return get('appDisplayMode') || 'Normal';
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
    updateAppDimensions();
    const screen = get('screen');
    const projectionMapDisplayActive = isProjectionMapDisplayActive();
    const displayMode = getEffectiveDisplayMode();

    // Update app class based on display mode
    if (appContainer) {
        logger.log('Rendering screen:', screen);
        let classes = appContainer.className.split(' ').filter(c => !c.startsWith('display-') && !c.startsWith('theme-') && !c.startsWith('gauge-style-') && c !== 'cluster-disabled' && c !== 'warn-is-active' && c !== 'hide-header' && c !== 'hide-bottom' && c !== 'bar-images-hidden' && c !== 'app-in-dash-disabled' && c !== 'app-in-dash-active' && c !== 'map-in-dash-active' && c !== 'cluster-bg-applied' && c !== 'menu-minimized' && c !== 'carplay-in-dash' && c !== 'projection-mirror-in-dash' && c !== 'projection-preparing-d3' && c !== 'projection-map-display-active' && c !== 'projection-card-overlay-active' && c !== 'native-mock-enabled');
        classes.push('display-' + displayMode.toLowerCase());

        if (get('clusterEnabled') === false) {
            classes.push('cluster-disabled');
        }

        const appInDash = get('appInDash');
        if (appInDash === false || appInDash === 'false') {
            classes.push('app-in-dash-disabled');
        } else if (appInDash === true || appInDash === 'left' || appInDash === 'right') {
            classes.push('app-in-dash-active');
        }

        if (get('clusterBackground') === true) {
            classes.push('cluster-bg-applied');
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
        if (shouldHideBar('Superior')) {
            classes.push('hide-header');
        }
        if (shouldHideBar('Inferior')) {
            classes.push('hide-bottom');
        }
        // Decorative top/bottom bar artwork is on unless explicitly turned off,
        // so an unresolved setting (pre-bind boot frame) still renders the images
        if (get('barImages') === false) {
            classes.push('bar-images-hidden');
        }

        const isMapInDash = projectionMapDisplayActive || get('mapInDash') === true;
        if (isMapInDash) {
            classes.push('map-in-dash-active');
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

function isDoNotHideOverrideActive() {
    const doNotHide = get('doNotHideBarsOn') || 'Ambos';
    if (doNotHide === 'Ignorar' || doNotHide === 'Nunca') return false;

    const isNavActive = isProjectionMapDisplayActive() || get('mapInDash') === true;
    const appInDashVal = get('appInDash');
    const isAppActive = appInDashVal === true || appInDashVal === 'left' || appInDashVal === 'right';

    if (doNotHide === 'Ambos' && (isNavActive || isAppActive)) return true;
    if (doNotHide === 'Navegação' && isNavActive) return true;
    if (doNotHide === 'Aplicativo' && isAppActive) return true;

    return false;
}

function shouldHideBar(barType) {
    const hiddenBars = get('hiddenBars') || 'Nenhuma';
    const configuredToHide = barType === 'Superior'
        ? (hiddenBars === 'Superior' || hiddenBars === 'Ambas')
        : (hiddenBars === 'Inferior' || hiddenBars === 'Ambas');

    if (!configuredToHide) return false;
    if (isDoNotHideOverrideActive()) return false;

    return true;
}

// <AppDefaultPosition> from theme.xml, i.e. the panel minus the top and bottom bars.
const APP_DEFAULT_BOUNDS = { x: 0, y: 62, width: 1920, height: 596 };

// Display modes this theme offers. Must stay in step with the <options> of
// navigation_display_mode / app_display_mode in theme.xml.
const DISPLAY_MODES = ['Normal', 'Reduzido', 'Clean', 'Mapa'];

// name -> {x, y, width, height} from <DisplayModes> in theme.xml, keyed lowercase.
// Resolved once after the bridge is up (see initDecentralizedBridge) because
// theme.xml cannot change while the theme is running.
let themeDisplayModes = {};

function declaredBoundsFor(displayMode) {
    return themeDisplayModes[String(displayMode).toLowerCase()] || null;
}

function resolveAppBounds(displayMode) {
    const declared = declaredBoundsFor(displayMode);
    if (declared) {
        // An explicit rect is absolute and hiddenBars does not stretch it further:
        // a mode declared as the full 1920x720 plus "Ambas" would otherwise grow
        // past the panel and need clamping rules nobody can predict.
        return { ...declared, source: `DisplayMode "${displayMode}"` };
    }

    let { x, y, width, height } = APP_DEFAULT_BOUNDS;
    if (shouldHideBar('Superior')) {
        y = 0;
        height += 62;
    }
    if (shouldHideBar('Inferior')) {
        height += 62;
    }
    const hiddenBars = get('hiddenBars') || 'Nenhuma';
    return { x, y, width, height, source: `AppDefaultPosition (${hiddenBars})` };
}

function updateAppDimensions() {
    const displayMode = getEffectiveDisplayMode();
    const { x, y, width, height, source } = resolveAppBounds(displayMode);

    if (window.Android && typeof window.Android.setAppDefaultDimensions === 'function') {
        window.Android.setAppDefaultDimensions(x, y, width, height);
    } else {
        console.log(`[AppDimensions] setAppDefaultDimensions(x: ${x}, y: ${y}, w: ${width}, h: ${height}) via ${source}`);
    }

    const devBg = document.querySelector('.dev-background');
    if (!devBg) return;

    let badge = devBg.querySelector('.dev-bg-badge');
    if (!badge) {
        badge = div({ className: 'dev-bg-badge' });
        devBg.appendChild(badge);
    }

    // Projection still mirrors the whole panel unless the theme declares bounds
    // for the effective mode, which keeps today's behaviour for themes that do not.
    const mapFallback = isProjectionMapDisplayActive() && !declaredBoundsFor(displayMode);
    const rect = mapFallback ? { x: 0, y: 0, width: 1920, height: 720 } : { x, y, width, height };

    devBg.style.left = `${rect.x}px`;
    devBg.style.top = `${rect.y}px`;
    devBg.style.width = `${rect.width}px`;
    devBg.style.height = `${rect.height}px`;
    devBg.style.backgroundSize = '100% 100%';
    badge.textContent = mapFallback
        ? 'Map In Dash (fixed 1920x720)'
        : `${displayMode}: x=${rect.x}, y=${rect.y}, w=${rect.width}, h=${rect.height} — ${source}`;
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
subscribe('doNotHideBarsOn', (val) => {
    console.log(`[STATE TRACE] doNotHideBarsOn changed to: ${val}`);
    render();
});
subscribe('mode', render);
subscribe('gaugeStyle', render);
subscribe('barImages', render);
subscribe('navigationDisplayMode', render);
subscribe('appDisplayMode', render);
// Sport and Eco force their own accent while driveModeColors is on. Every other mode
// - Normal, Neve, Areia, Lama - keeps whatever the user picked.
const DRIVE_MODE_ACCENTS = { Sport: '#FF0000', Eco: '#00E676' };

function resolveAccent() {
    const base = get('accentColor') || DEFAULT_ACCENT;
    if (!get('driveModeColors')) return base;
    return DRIVE_MODE_ACCENTS[get('drivingMode')] || base;
}

// Not render(): the accent only rewrites :root custom properties, so a full layout
// rebuild would be wasted work. applyAccent no-ops when the resolved color is
// unchanged, which matters here because drivingMode churns while driving.
const reapplyAccent = () => applyAccent(resolveAccent());
subscribe('accentColor', reapplyAccent);
subscribe('driveModeColors', reapplyAccent);
subscribe('drivingMode', reapplyAccent);

// Menu icons carry their blue in the pixels, so they get their own repaint pass.
subscribe('iconColor', (hex) => applyIconColor(hex || DEFAULT_ICON_COLOR));

// Gauge fills are plain token writes - deliberately independent of the accent.
subscribe('fuelColor', (hex) => {
    document.documentElement.style.setProperty('--fuel-bar-color', hex || '#3B82F6');
});
subscribe('batteryColor', (hex) => {
    document.documentElement.style.setProperty('--battery-bar-color', hex || '#10B981');
});

subscribe('clusterEnabled', render);
subscribe('clusterBackground', render);
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

    // Card 0 is the vehicle's own card: this theme is not what the driver is looking at,
    // so ignore wheel input entirely. The host forwards keys unconditionally by design.
    // Acting on them here changed vehicle state behind the driver's back — UP/DOWN on
    // card 0 raised the AC fan because this handler was still on the aircon screen.
    if (get('cardId') == 0) {
        logger.log(`[Steering Wheel] Ignoring ${keyName}: native card (0) is active`);
        return;
    }

    // Wake up from Clean or Mapa mode to Normal display mode on any key event and show display menu
    const currentDisplay = get('display');
    if ((currentDisplay === 'Clean' || currentDisplay === 'Mapa') && get('screen') !== 'display_selection') {
        setState('display', 'Normal');
        bridge.updateCarData('display', 'Normal');
        setState('screen', 'display_selection');
        logger.log(`[Steering Wheel] ${currentDisplay} display mode active. Returning to Normal display mode and opening display menu on key event: ${keyName}`);
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
        // ENTER only: LEFT/RIGHT are reserved for cluster card navigation and are never
        // delivered to themes, so keying fan/temp focus off them silently did nothing.
        if (keyName === 'ENTER') {
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
                const delta = keyName === 'UP' ? 0.5 : -0.5;
                const nextTemp = Math.min(32, Math.max(16, Math.round((currentTemp + delta) * 2) / 2));
                bridge.updateCarData('car.hvac.driver_temperature', nextTemp.toFixed(1));
            }
        } else if (keyName === 'ENTER_LONG') {
            const currentAuto = Number(get('auto')) || 0;
            const nextAuto = currentAuto === 1 ? 0 : 1;
            setState('auto', nextAuto);
            bridge.updateCarData('car.hvac.auto_enable', String(nextAuto));
            if (nextAuto === 0) {
                const currentFan = Number(get('fan')) || 1;
                bridge.updateCarData('car.hvac.fan_speed', String(currentFan));
            }
            bridge.triggerSystemAction('CANCEL_MAX_AC');
        } else if (keyName === 'BACK_LONG') {
            const currentRecycle = Number(get('recycle')) || 0;
            const nextRecycle = currentRecycle === 1 ? 0 : 1;
            setState('recycle', nextRecycle);
            bridge.updateCarData('car.hvac.cycle_mode', String(nextRecycle));
        }
        // No BACK handling: aircon is card 3's root screen, not somewhere the theme
        // navigated to, so there is nothing to go back to. Leaving on BACK stranded the
        // theme on the main menu while the car was still on card 3 — and BACK is also how
        // a warning gets dismissed, so any warning while on card 3 triggered it.
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
        const displays = DISPLAY_MODES;
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

    // theme.xml is static for the life of the theme, so read the per-mode app
    // bounds once here rather than on every render, then re-render so the very
    // first frame is not stuck on AppDefaultPosition.
    themeDisplayModes = bridge.getThemeDisplayModes();
    const declaredNames = Object.keys(themeDisplayModes);
    if (declaredNames.length > 0) {
        // A name that matches no display mode is inert, so say so rather than
        // letting a typo look like the feature is broken.
        const known = DISPLAY_MODES.map(m => m.toLowerCase());
        declaredNames.filter(n => !known.includes(n)).forEach(n => {
            console.warn(`[AppDimensions] <DisplayMode name="${n}"> matches no display mode of this theme (${DISPLAY_MODES.join(', ')}); it will never apply.`);
        });
        console.log('[AppDimensions] theme.xml <DisplayModes>:', themeDisplayModes);
        render();
    }

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
    bridge.bindThemeSetting('doNotHideBarsOn', 'Ambos', setState);
    bridge.bindThemeSetting('mode', 'Dark', setState);
    bridge.bindThemeSetting('gaugeStyle', 'Esportivo', setState);
    bridge.bindThemeSetting('barImages', true, setState);
    bridge.bindThemeSetting('navigationDisplayMode', 'Mapa', setState);
    bridge.bindThemeSetting('appDisplayMode', 'Normal', setState);
    bridge.bindThemeSetting('display', 'Normal', setState);
    bridge.bindThemeSetting('accentColor', DEFAULT_ACCENT, setState);
    bridge.bindThemeSetting('driveModeColors', false, setState);
    bridge.bindThemeSetting('iconColor', DEFAULT_ICON_COLOR, setState);
    bridge.bindThemeSetting('fuelColor', '#3B82F6', setState);
    bridge.bindThemeSetting('batteryColor', '#10B981', setState);
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
    
    // No initial cardId seeding here. The card is owned by the car and arrives via
    // window.onCardChanged, which the host pushes on page load as well as on every
    // change. Reading it back off the car-data map made this async init a second
    // writer that raced the push: it landed after it and reset the card to 1, so
    // loading the theme while the cluster sat on card 3 dropped to the main menu.
    // (The old `|| 1` also mapped card 0 to 1, since Number('0') is falsy.)

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
        "car.hvac.auto_enable",
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
            case "car.hvac.auto_enable":
                setState('auto', val);
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

    // No echo back to the backend: the card is owned by the car and travels one way,
    // car -> backend -> onCardChanged. Reporting it back gave the backend a second
    // writer for the active card.

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
