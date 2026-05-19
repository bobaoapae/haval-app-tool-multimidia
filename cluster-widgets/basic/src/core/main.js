import { getState as get, setState, subscribe } from './state.js';
import { createDashboardInfo } from './components/dashboardInfo.js';
import { createAcControlScreen } from './components/aircon/mainAcControl.js';
import { createRegenScreen } from './components/regen/regenControl.js';
import { createDisplaySelectionScreen } from './components/display/themeSelection.js';
import { createMainMenu } from './components/mainMenu.js';
import { createMask } from './components/display/mask.js';
import { createGraphScreen } from './components/graphs/graphs.js';
import { div } from '../utils/createElement.js';
import { logger } from '../utils/logger.js';
import { initializeConstants } from '../utils/constants.js';
import { initWarningHandler } from './components/warningHandler.js';

initializeConstants();
initWarningHandler();

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
    const displayMode = get('display') || 'Normal';

    // Update app class based on display mode
    if (appContainer) {
        logger.log('Rendering screen:', screen);
        let classes = appContainer.className.split(' ').filter(c => !c.startsWith('display-') && !c.startsWith('theme-') && c !== 'cluster-disabled' && c !== 'warn-is-active');
        classes.push('display-' + displayMode.toLowerCase());

        if (get('clusterEnabled') === false) {
            classes.push('cluster-disabled');
        }

        if (get('cardId') == 0 || get('warningActive') === true) {
            classes.push('warn-is-active');
        }
        if (nativeMockEnabled) {
            classes.push('native-mock-enabled');
        }

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
subscribe('screen', render);
subscribe('display', render);

subscribe('clusterEnabled', render);
// subscribe('cardId', render); // REMOVED: Triggers double-render as cardId listener already sets screen
render();



// Handle Card ID transitions
subscribe('cardId', (cardId) => {
    logger.log('cardId change:', cardId);

    // Sync with Android bridge for correct app resizing
    if (window.Android && window.Android.setCardId) {
        window.Android.setCardId(cardId);
    }

    // 0 = hide the right menu display
    if (menuWrapper) {
        menuWrapper.style.display = (cardId == 0) ? 'none' : 'block';
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
