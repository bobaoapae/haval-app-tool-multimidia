import { getState as get, setState, subscribe } from './state.js';
import { createDashboardInfo } from './components/dashboardInfo.js';
import { createAcControlScreen } from './components/aircon/mainAcControl.js';
import { createRegenScreen } from './components/regen/regenControl.js';
import { createDisplaySelectionScreen } from './components/display/themeSelection.js';
import { createMainMenu, menuItems } from './components/mainMenu.js';
import { createMask } from './components/display/mask.js';
import { createGraphScreen, graphList } from './components/graphs/graphs.js';
import { createAjustesMenu } from './components/ajustesMenu.js';
import { createInfoScreen } from './components/info/infoScreen.js';
import { div } from '../../../shared/utils/createElement.js';
import { logger } from '../../../shared/utils/logger.js';
import { initializeConstants } from '../utils/constants.js';
import { initWarningHandler } from './components/warningHandler.js';
import { loadPrefIn, savePref } from '../../../shared/utils/preferences.js';

initializeConstants();
initWarningHandler();

// --- Persist / restore UI state (generic preference bridge) ---
const PREF_FOCUSED_MENU = 'focusedMenuItem';
const PREF_CURRENT_GRAPH = 'currentGraph';
const MENU_IDS = menuItems.map((m) => m.id);
const GRAPH_IDS = graphList.map((g) => g.id);

function restorePersistedUiState() {
    const menuId = loadPrefIn(PREF_FOCUSED_MENU, MENU_IDS, get('focusedMenuItem') || 'option_7');
    const graphId = loadPrefIn(PREF_CURRENT_GRAPH, GRAPH_IDS, get('currentGraph') || 'evConsumption');
    setState('focusedMenuItem', menuId);
    setState('currentGraph', graphId);
    logger.log('[prefs] restored UI', { focusedMenuItem: menuId, currentGraph: graphId });
}

function wirePreferencePersistence() {
    subscribe('focusedMenuItem', (val) => {
        if (val && MENU_IDS.includes(val)) {
            savePref(PREF_FOCUSED_MENU, val);
        }
    });
    subscribe('currentGraph', (val) => {
        if (val && GRAPH_IDS.includes(val)) {
            savePref(PREF_CURRENT_GRAPH, val);
        }
    });
}

// Apply saved menu focus + last graph before layout builds UI
restorePersistedUiState();
wirePreferencePersistence();

/**
 * Share this theme's wallpaper with the native Display-1 background layer.
 * Declared in theme.xml as <background>car-bg.png</background>; frontend
 * announces the relative path so Android can load it from the theme package.
 */
const THEME_BACKGROUND_FILE = 'car-bg.png';
function announceThemeBackground() {
    try {
        if (window.Android && typeof window.Android.setThemeBackground === 'function') {
            window.Android.setThemeBackground(THEME_BACKGROUND_FILE);
            logger.log('[bg] announced theme background via setThemeBackground:', THEME_BACKGROUND_FILE);
        } else if (window.Android && typeof window.Android.setClusterBackground === 'function') {
            window.Android.setClusterBackground('THEME', THEME_BACKGROUND_FILE);
            logger.log('[bg] announced theme background via setClusterBackground:', THEME_BACKGROUND_FILE);
        }
    } catch (e) {
        console.warn('[bg] failed to announce theme background', e);
    }
}
announceThemeBackground();

if (process.env.NODE_ENV === 'development') {
    document.body.style.backgroundColor = 'black';
    import('../utils/testing-utils.js');
}

const appContainer = document.getElementById('app');
let currentComponent = null;
let maskComponent = null;
let menuWrapper = null;
let dashboardCleanup = null;
const screenCache = {};

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
    document.body.classList.add('native-mock-enabled');
}

function initializeLayout() {
    logger.enter('initializeLayout');
    if (!appContainer) {
        logger.leave('initializeLayout');
        return;
    }
    appContainer.innerHTML = '';

    // Add mask background first (z-index: 50)
    try {
        const mask = createMask();
        maskComponent = mask;
        appContainer.appendChild(mask.background);
    } catch (e) {
        console.error('[Error] Failed to create mask: ', e);
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
            const cachedScreens = ['main_menu', 'aircon', 'regen', 'display_selection', 'graph', 'ajustes', 'info'];
            cachedScreens.forEach(screen => {
                try {
                    let result = null;
                    if (screen === 'main_menu') result = createMainMenu();
                    else if (screen === 'aircon') result = createAcControlScreen();
                    else if (screen === 'regen') result = createRegenScreen();
                    else if (screen === 'display_selection') result = createDisplaySelectionScreen();
                    else if (screen === 'graph') result = createGraphScreen();
                    else if (screen === 'ajustes') result = createAjustesMenu();
                    else if (screen === 'info') result = createInfoScreen();

                    if (result) {
                        const element = result.element || result;
                        element.style.display = 'none';
                        // Graph + AC must NOT live under .dashboard-menu-container:
                        // that node has transform + tiny box, which traps absolute
                        // positioning and collapses / misplaces full right-panel UIs.
                        if (screen === 'graph' || screen === 'aircon') {
                            dashElement.appendChild(element);
                        } else {
                            menuWrapper.appendChild(element);
                        }
                        if (result.onMount) result.onMount();
                        screenCache[screen] = result;
                    }
                } catch (e) {
                    console.error(`[Error] Failed to pre-load screen ${screen}:`, e);
                }
            });
        }
    } catch (e) {
        console.error('[Error] Failed to initialize dashboard info: ', e);
    }

    // Side no-app discs removed — mask-panel L/R gradients cover that role
    logger.leave('initializeLayout');
}


function render() {
    logger.enter('render', { screen: get('screen'), display: get('display') });
    const screen = get('screen');
    const displayMode = get('display') || 'Normal';

    const cardId = get('cardId');
    const isCard0 = cardId == 0 || cardId === '0';

    // Update app class based on display mode
    if (appContainer) {
        logger.log('Rendering screen:', screen);
        let classes = appContainer.className.split(' ').filter(c =>
            !c.startsWith('display-') &&
            !c.startsWith('theme-') &&
            c !== 'cluster-disabled' &&
            c !== 'warn-is-active' &&
            c !== 'card-0-active'
        );
        classes.push('display-' + displayMode.toLowerCase());

        if (get('clusterEnabled') === false) {
            classes.push('cluster-disabled');
        }

        // Only real warnings hide chrome
        if (get('warningDismissed') !== true && get('warningActive') === true) {
            classes.push('warn-is-active');
        }
        // Card 0: hide right mask + any right-side menu content
        if (isCard0) {
            classes.push('card-0-active');
        }
        if (nativeMockEnabled) {
            classes.push('native-mock-enabled');
        }

        appContainer.className = classes.join(' ').trim();
        document.body.classList.toggle('card-0-active', isCard0);
        logger.log('App classes:', appContainer.className);
    }


    // Hide all cached components
    Object.values(screenCache).forEach(comp => {
        const el = comp.element || comp;
        el.style.display = 'none';
    });

    // Card 0: no right-side menus/previews (top main menu still allowed)
    if (isCard0) {
        if (screenCache['main_menu']) {
            const comp = screenCache['main_menu'];
            (comp.element || comp).style.display = 'block';
            currentComponent = comp;
        } else {
            currentComponent = null;
        }
        logger.leave('render');
        return;
    }

    if (screen === 'main_menu') {
        // Always show the main menu
        if (screenCache['main_menu']) {
            const comp = screenCache['main_menu'];
            (comp.element || comp).style.display = 'block';
        }

        // Show the corresponding preview/sub-menu component on the right based on focused main menu item
        const focused = get('focusedMenuItem');
        let previewScreen = null;
        if (focused === 'option_7') previewScreen = 'graph';
        else if (focused === 'option_ajustes') previewScreen = 'ajustes';
        else if (focused === 'option_6') previewScreen = 'regen';
        else if (focused === 'option_info') previewScreen = 'info';

        if (previewScreen && screenCache[previewScreen]) {
            const comp = screenCache[previewScreen];
            (comp.element || comp).style.display = 'block';
            currentComponent = comp;
        } else {
            currentComponent = screenCache['main_menu'];
        }
    } else {
        if (screenCache[screen]) {
            const comp = screenCache[screen];
            const el = comp.element || comp;
            el.style.display = 'block';
            currentComponent = comp;
        } else {
            currentComponent = null;
        }
    }
    logger.leave('render');
}

subscribe('warningActive', () => render());
subscribe('warningDismissed', () => render());
subscribe('cardId', () => render());
initializeLayout();

// Start rendering and subscribe to listen for screen changes thus triggering new render
subscribe('screen', render);
subscribe('display', render);
subscribe('focusedMenuItem', render);
subscribe('menuFocusArea', render);

// Informações is view-only: never enter sub-focus (Enter is a no-op)
subscribe('menuFocusArea', (area) => {
    if (area === 'sub' && get('focusedMenuItem') === 'option_info') {
        setState('menuFocusArea', 'main');
    }
});
subscribe('focusedMenuItem', (item) => {
    if (item === 'option_info' && get('menuFocusArea') === 'sub') {
        setState('menuFocusArea', 'main');
    }
});

subscribe('clusterEnabled', render);
render();



// Handle Card ID transitions
subscribe('cardId', (cardId) => {
    logger.log('cardId change:', cardId);

    if (cardId == 1 || cardId == 3) {
        setState('warningDismissed', false);
    }

    // Sync with Android bridge for correct app resizing
    if (window.Android && window.Android.setCardId) {
        window.Android.setCardId(cardId);
    }

    // Top menu container stays mounted; card 0 hides right content via .card-0-active
    if (menuWrapper) {
        menuWrapper.style.display = 'block';
    }

    if (cardId == 0 || cardId == 1) {
        // 0 = no right menus/mask; 1 = main menu + previews
        setState('screen', 'main_menu');
    } else if (cardId == 3) {
        // 3 = AC screen
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
            const focusArea = get('menuFocusArea');
            if (focusArea === 'sub') {
                const focusedMain = get('focusedMenuItem');
                if (focusedMain === 'option_ajustes') {
                    setState('focusedAjustesItem', item);
                } else if (focusedMain === 'option_4') {
                    setState('displayFocus', item);
                } else if (focusedMain === 'option_6') {
                    setState('regenMode', item);
                }
            } else {
                setState('focusedMenuItem', item);
            }
        } else if (screen === 'aircon') {
            setState('focusArea', item);
        } else if (screen === 'display_selection') {
            setState('displayFocus', item);
        } else if (screen === 'regen') {
            setState('regenMode', item);
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
        // Automatically convert numeric strings to numbers for compatibility with components
        if (typeof value === 'string' && value.trim() !== '' && !isNaN(value)) {
            val = Number(value);
        }
        setState(key, val);
        // warningActive has its own subscription to render() at line 184, so no need for manual trigger here
        logger.leave('window.control');
    } catch (e) {
        console.error('[Error] Bridge control failed for key ' + key + ':', e);
    }
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
