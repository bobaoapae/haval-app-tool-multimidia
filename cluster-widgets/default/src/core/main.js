import { getState as get, setState, subscribe } from './state.js';
import { createDashboardInfo } from './components/dashboardInfo.js';
import { createAcControlScreen } from './components/aircon/mainAcControl.js';
import { createRegenScreen } from './components/regen/regenControl.js';
import { createDisplaySelectionScreen } from './components/display/themeSelection.js';
import { createMainMenu } from './components/mainMenu.js';
import { createMediaScreen } from './components/media/mediaControl.js';
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

function isProjectionMapDisplayActive() {
    return get('projectionMirrorInDash') === true ||
        get('carPlayInDash') === true ||
        get('projectionPreparingD3') === true;
}

function isProjectionCardOverlayActive(cardId = get('cardId'), screen = get('screen')) {
    if (!isProjectionMapDisplayActive()) {
        return false;
    }
    return (cardId == 1 && isMainMenuSessionScreen(screen)) ||
        (cardId == 3 && screen === 'aircon');
}

function isMainMenuDetailScreen(screen) {
    return screen === 'graph' || screen === 'graphs' || screen === 'regen' || screen === 'display_selection';
}

function isMainMenuSessionScreen(screen) {
    return screen === 'main_menu' || isMainMenuDetailScreen(screen);
}

function isMainMenuSessionVisible(cardId, screen) {
    if (isProjectionMapDisplayActive()) {
        return false;
    }
    return get('mainMenuSessionActive') === true &&
        isMainMenuSessionScreen(screen) &&
        cardId == 1;
}

function isWarningUiActive(cardId, screen) {
    if (isMainMenuSessionVisible(cardId, screen)) {
        return false;
    }
    if (cardId == 3 && screen === 'aircon') {
        return false;
    }
    if (get('warningActive') === true) {
        return true;
    }
    return get('warningDismissed') !== true && cardId == 0;
}

function isRightMenuVisible(cardId, screen) {
    const projectionMapDisplayActive = isProjectionMapDisplayActive();
    const projectionCardOverlayActive = isProjectionCardOverlayActive();
    if (projectionMapDisplayActive) {
        return projectionCardOverlayActive;
    }
    return isMainMenuSessionVisible(cardId, screen) || !(cardId == 0 && get('warningDismissed') !== true);
}

function getEffectiveDisplayMode() {
    // Projection on display 3 temporarily uses the Mapa layout without
    // persisting over the user's saved display choice.
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
        let classes = appContainer.className.split(' ').filter(c => !c.startsWith('display-') && !c.startsWith('theme-') && !c.startsWith('screen-') && c !== 'cluster-disabled' && c !== 'warn-is-active' && c !== 'carplay-in-dash' && c !== 'projection-mirror-in-dash' && c !== 'projection-preparing-d3' && c !== 'projection-map-display-active' && c !== 'projection-card-overlay-active');
        classes.push('display-' + displayMode.toLowerCase());
        classes.push('screen-' + String(screen).replace(/_/g, '-'));

        if (get('clusterEnabled') === false) {
            classes.push('cluster-disabled');
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

        if (isWarningUiActive(get('cardId'), screen)) {
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

    if (menuWrapper) {
        const projectionCardOverlayActive = isProjectionCardOverlayActive();
        const rightMenuVisible = isRightMenuVisible(get('cardId'), screen);
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
            } else if (screen === 'media') {
                componentResult = createMediaScreen();
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
subscribe('warningDismissed', () => render());
initializeLayout();

// Start rendering and subscribe to listen for screen changes thus triggering new render
subscribe('screen', render);
subscribe('display', render);

subscribe('clusterEnabled', render);
subscribe('carPlayInDash', render);
subscribe('projectionMirrorInDash', render);
subscribe('projectionPreparingD3', render);
// cardId renders are handled after card-specific state sync below to avoid
// rendering once with stale screen/session state and again after setState().
render();



// Handle Card ID transitions
subscribe('cardId', (cardId) => {
    logger.log('cardId change:', cardId);
    let screenUpdatedByCard = false;

    if (cardId == 1 || cardId == 3) {
        setState('warningDismissed', false);
    }
    if (cardId == 1) {
        setState('mainMenuSessionActive', true);
    } else if (cardId == 3) {
        setState('mainMenuSessionActive', false);
    } else if (cardId == 0) {
        setState('mainMenuSessionActive', false);
    }

    // Sync with Android bridge for correct app resizing
    if (window.Android && window.Android.setCardId) {
        window.Android.setCardId(cardId);
    }

    // 0 = hide the right menu display
    if (menuWrapper) {
        const projectionMapDisplayActive = isProjectionMapDisplayActive();
        const projectionCardOverlayActive = isProjectionCardOverlayActive();
        const rightMenuVisible = isRightMenuVisible(cardId, get('screen'));
        menuWrapper.style.display = (!rightMenuVisible || (projectionMapDisplayActive && !projectionCardOverlayActive)) ? 'none' : 'block';
    }

    if (cardId == 1) {
        // 1 = go to main regular menu
        if (get('screen') !== 'main_menu') {
            screenUpdatedByCard = true;
            setState('screen', 'main_menu');
        }
    } else if (cardId == 3) {
        // 3 = set to AC menu
        if (get('screen') !== 'aircon') {
            screenUpdatedByCard = true;
            setState('screen', 'aircon');
        }
    }

    if (!screenUpdatedByCard) {
        render();
    }
});

// Functions used by Kotlin to trigger interactions
window.showScreen = function (screenName) {
    try {
        logger.enter('window.showScreen', screenName);
        if (isMainMenuSessionScreen(screenName)) {
            setState('mainMenuSessionActive', true);
        } else {
            setState('mainMenuSessionActive', false);
        }
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
        if (typeof value === 'string' && value.trim() !== '' && !isNaN(value)) {
            val = Number(value);
        }
        if (key === 'mediaFavorites' && typeof value === 'string') {
            try { val = JSON.parse(value); } catch (_) { val = []; }
        }
        setState(key, val);
        // warningActive has its own subscription to render() at line 184, so no need for manual trigger here
        logger.leave('window.control');
    } catch (e) {
        console.error('[Error] Bridge control failed for key ' + key + ':', e);
    }
};

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
        mainMenuSessionActive: get('mainMenuSessionActive'),
        display: get('display'),
        effectiveDisplay: getEffectiveDisplayMode(),
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
