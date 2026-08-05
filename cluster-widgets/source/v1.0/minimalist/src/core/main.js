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
import { bridge } from '../../../shared/bridge/ThemeBridgeAdapter.js';
import { translateFriendlyValue, FRIENDLY_KEY_TO_CAN_KEY, KEYS, getLabel } from '../../../shared/car/carConstants.js';
import { createGraphTelemetryHandler, getAdjustedSpeed } from '../../../shared/car/carDerivations.js';

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

function isProjectionMapDisplayActive() {
    return get('projectionMirrorInDash') === true ||
        get('carPlayInDash') === true ||
        get('projectionPreparingD3') === true;
}

// The theme's own visual mode (Normal / Esportivo / Reduzido / Clean), picked in the
// in-cluster "Modos do Tema" menu. It drives the .display-* classes.
// It is NOT the navigation/app mask setting: those only reshape the side masks and the
// app viewport, and feeding them in here made "Clean" fall through to .display-clean,
// the imported Default-theme mode that hides the dial entirely.
function getEffectiveDisplayMode() {
    return get('display') || 'Normal';
}

// Is anything projected onto the cluster right now — a map/mirror, or a plain app?
function isProjectingAnything() {
    const appInDashVal = get('appInDash');
    return isProjectionMapDisplayActive() ||
        get('mapInDash') === true ||
        appInDashVal === true || appInDashVal === 'left' || appInDashVal === 'right';
}

// theme.xml ships both bars as the same four-option combo, phrased as when the bar is
// VISIBLE. The old pair (hide_bottom_bar + do_not_hide_bars_on) was a boolean crossed
// with a negative-logic exception list, which read as a double negative and inverted
// what most people expect: "hide" plus the default "Ambos" actually kept the bar
// whenever something was projected.
const BAR_ALWAYS = 'Sempre';
const BAR_NEVER = 'Nunca';
const BAR_WITH_PROJECTION = 'Só com Projeção';
const BAR_WITHOUT_PROJECTION = 'Só sem Projeção';
const BAR_VISIBILITIES = [BAR_ALWAYS, BAR_NEVER, BAR_WITH_PROJECTION, BAR_WITHOUT_PROJECTION];

// Same accent hazard as the mask modes: these cross the bridge as raw strings, and an
// unaccented "So com Projecao" must not silently fall through to the fallback.
function normalizeBarVisibility(value, fallback) {
    if (typeof value !== 'string' || value.trim() === '') return fallback;
    const needle = foldAccents(value);
    return BAR_VISIBILITIES.find((m) => foldAccents(m) === needle) || fallback;
}

function isBarVisible(stateVar, fallback) {
    const mode = normalizeBarVisibility(get(stateVar), fallback);
    if (mode === BAR_ALWAYS) return true;
    if (mode === BAR_NEVER) return false;
    if (mode === BAR_WITH_PROJECTION) return isProjectingAnything();
    if (mode === BAR_WITHOUT_PROJECTION) return !isProjectingAnything();
    return true;
}

function isTopBarVisible() {
    return isBarVisible('topBarVisibility', BAR_ALWAYS);
}

function isBottomBarVisible() {
    return isBarVisible('bottomBarVisibility', BAR_WITH_PROJECTION);
}

// The top notch is mandatory whenever the top bar is hidden — the menu needs a backdrop
// to stay legible over a map. The bottom one is opt-out.
function isTopNotchVisible() {
    return !isTopBarVisible();
}

function isBottomNotchVisible() {
    if (isBottomBarVisible()) return false;
    const val = get('bottomBarNotch');
    return !(val === false || val === 'false' || val === 0 || val === '0');
}

// projection_keep_visible is a "multi" config: the host stores the chosen options as a
// comma separated list, so the value arrives as one raw string. Each option maps to a
// keep-<slug> class and CSS hides the readouts whose slug is absent while projecting.
// Which readouts survive a map used to be hard-coded in the stylesheet — one rule I had
// written myself for the gauges — which meant every new readout was another exception.
const PROJECTION_CONTENT = [
    { option: 'Gauges', slug: 'gauges' },
    { option: 'Hora', slug: 'hora' },
    { option: 'Marcha', slug: 'marcha' },
    { option: 'HEV', slug: 'hev' },
    { option: 'Regen', slug: 'regen' }
];

// Same accent hazard as the other combos, plus the split: an entry saved as "Hora" or
// "hora" or with stray spaces must all land on the same slug.
function getProjectionKeepSlugs() {
    const raw = get('projectionKeepVisible');
    // Only a genuinely absent value falls back to "keep everything", and that is just the
    // boot frame before bindThemeSetting seeds the theme.xml default. An empty *string*
    // is a saved selection with nothing ticked, which must hide everything — the two are
    // indistinguishable once stored, so the distinction has to be made here.
    if (typeof raw !== 'string') {
        return PROJECTION_CONTENT.map((c) => c.slug);
    }
    const chosen = raw.split(',').map(foldAccents).filter(Boolean);
    return PROJECTION_CONTENT
        .filter((c) => chosen.includes(foldAccents(c.option)))
        .map((c) => c.slug);
}

// One-shot, read-only migration off the retired hide_bottom_bar + do_not_hide_bars_on
// pair. Nothing is written back: if the host still holds the old keys we just derive an
// equivalent starting value, and the first time the user touches the new combo the app
// persists it under the new stateVariable and this stops mattering.
// Must read the host's stored preferences, not state: bindSetting has already seeded
// bottomBarVisibility with either a stored value or the theme.xml default, and after
// that the two are indistinguishable. An empty getPreference means the user has never
// saved the new setting, which is the only case where the old pair still speaks for them.
function migrateLegacyBarSettings() {
    if (bridge.getPreference('bottomBarVisibility', '') !== '') return;

    const legacyHide = bridge.getPreference('hideBottomBar', '');
    if (legacyHide === '') return;   // nothing stored under the old pair either

    if (legacyHide !== 'true' && legacyHide !== '1') {
        setState('bottomBarVisibility', BAR_ALWAYS);
        return;
    }
    // "Ignorar"/"Nunca" meant no exception, so the bar was hidden unconditionally.
    // Every other value kept it while something was projected.
    const legacyExcept = bridge.getPreference('doNotHideBarsOn', 'Ambos');
    setState('bottomBarVisibility',
        (legacyExcept === 'Ignorar' || legacyExcept === 'Nunca') ? BAR_NEVER : BAR_WITH_PROJECTION);
}

// theme.xml ships these as combos of "Padrão, Reduzido, Clean". Values cross the bridge
// as raw strings, so normalize the accent before comparing — an unaccented "Padrao"
// would otherwise be treated as Clean and blow the viewport out to the full 1920.
const MASK_MODES = ['Padrão', 'Reduzido', 'Clean'];
const COMBINING_MARKS = /[̀-ͯ]/g;

function foldAccents(s) {
    return s.trim().normalize('NFD').replace(COMBINING_MARKS, '').toLowerCase();
}

function normalizeMaskMode(value, fallback) {
    if (typeof value !== 'string' || value.trim() === '') return fallback;
    const needle = foldAccents(value);
    return MASK_MODES.find((m) => foldAccents(m) === needle) || fallback;
}

function getEffectiveMaskMode() {
    if (isProjectionMapDisplayActive()) {
        return normalizeMaskMode(get('navigationDisplayMode') ?? get('navigation_display_mode'), 'Clean');
    }
    const appInDashVal = get('appInDash');
    const isAppActive = appInDashVal === true || appInDashVal === 'left' || appInDashVal === 'right';
    if (isAppActive) {
        return normalizeMaskMode(get('appDisplayMode') ?? get('app_display_mode'), 'Padrão');
    }
    return 'Padrão';
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
    document.body.classList.add('native-mock-enabled');
}

function initializeLayout() {
    logger.enter('initializeLayout');
    if (!appContainer) {
        logger.leave('initializeLayout');
        return;
    }
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
            const cachedScreens = ['main_menu', 'aircon', 'display_selection', 'graph', 'ajustes', 'info'];
            cachedScreens.forEach(screen => {
                try {
                    let result = null;
                    if (screen === 'main_menu') result = createMainMenu();
                    else if (screen === 'aircon') result = createAcControlScreen();
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
                    console.error('[Error] Failed to pre-load screen ' + screen + ':', e);
                }
            });
        }
    } catch (e) {
        console.error('[Error] Failed to initialize dashboard info: ', e);
    }

    // The mask element only exists now, so any source resolved before this point still
    // has to be painted onto it.
    applyBackdropFill();

    // Side no-app discs removed — mask-panel L/R gradients cover that role
    logger.leave('initializeLayout');
}


function render() {
    logger.enter('render', { screen: get('screen'), display: get('display') });
    updateAppDimensions();
    const screen = get('screen');
    const displayMode = getEffectiveDisplayMode();

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
            c !== 'card-0-active' &&
            c !== 'hide-bottom-bar' &&
            c !== 'hide-top-bar' &&
            c !== 'show-top-notch' &&
            c !== 'show-bottom-notch' &&

            c !== 'projecting' &&
            !c.startsWith('keep-') &&
            c !== 'hide-regen-icon' &&
            c !== 'hide-rpm-icon' &&
            c !== 'nav-mask-reduced' &&
            c !== 'nav-mask-clean' &&
            c !== 'nav-mask-default' &&
            c !== 'projection-map-display-active' &&
            c !== 'carplay-in-dash' &&
            c !== 'app-in-dash-active' &&
            c !== 'map-in-dash-active' &&
            c !== 'native-mock-enabled'
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

        // theme.xml settings. Only the "off" side gets a class, so the boot frame
        // that renders before bindThemeSetting resolves already matches the defaults
        // (bars shown, regen and RPM indicators shown).
        if (!isBottomBarVisible()) {
            classes.push('hide-bottom-bar');
        }
        if (!isTopBarVisible()) {
            classes.push('hide-top-bar');
        }
        // A hidden bar leaves its notch behind to keep the menu / time-gear legible.
        if (isTopNotchVisible()) {
            classes.push('show-top-notch');
        }
        if (isBottomNotchVisible()) {
            classes.push('show-bottom-notch');
        }

        // While anything is projected, projection_keep_visible decides which readouts
        // stay. One flag class plus one keep-<slug> per chosen option, so the stylesheet
        // states each readout as a symmetric rule instead of a hard-coded exception.
        if (isProjectingAnything()) {
            classes.push('projecting');
            getProjectionKeepSlugs().forEach((slug) => classes.push('keep-' + slug));
        }
        if (get('showRegenIcon') === false) {
            classes.push('hide-regen-icon');
        }
        if (get('showRpmIcon') === false) {
            classes.push('hide-rpm-icon');
        }
        const isProjActive = isProjectionMapDisplayActive();
        const appInDashVal = get('appInDash');
        const isAppActive = appInDashVal === true || appInDashVal === 'left' || appInDashVal === 'right';

        const isMapInDash = isProjActive || get('mapInDash') === true;
        if (isMapInDash) {
            classes.push('map-in-dash-active');
        }
        if (isProjActive) {
            classes.push('projection-map-display-active');
            classes.push('carplay-in-dash');
        }
        if (isAppActive) {
            classes.push('app-in-dash-active');
        }

        const maskMode = getEffectiveMaskMode();
        if (maskMode === 'Reduzido') {
            classes.push('nav-mask-reduced');
        } else if (maskMode === 'Clean') {
            classes.push('nav-mask-clean');
        } else {
            classes.push('nav-mask-default');
        }

        appContainer.className = classes.join(' ').trim();
        document.body.classList.toggle('card-0-active', isCard0);
        document.body.classList.toggle('projection-map-display-active', isProjActive);
        document.body.classList.toggle('carplay-in-dash', isProjActive);
        document.body.classList.toggle('app-in-dash-active', isAppActive);
        document.body.classList.toggle('nav-mask-reduced', maskMode === 'Reduzido');
        document.body.classList.toggle('nav-mask-clean', maskMode === 'Clean');
        document.body.classList.toggle('nav-mask-default', maskMode === 'Padrão');
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

function updateAppDimensions() {
    const effectiveMaskMode = getEffectiveMaskMode();

    // Vertical insets must match the real mask geometry in night.style.css:
    // .mask-top-bar is 95px and .mask-bottom-bar is 60px. The old flat 62/62
    // left the app 33px under the top bar and 2px past the bottom one.
    const TOP_MASK = 95;
    const BOTTOM_MASK = 60;

    // A hidden bar hands its strip back to the app, notch or not — the notch is drawn
    // over the app rather than reserving space, so hiding a bar actually buys room.
    const topInset = isTopBarVisible() ? TOP_MASK : 0;
    const bottomInset = isBottomBarVisible() ? BOTTOM_MASK : 0;

    let x = 0;
    let y = topInset;
    let width = 1920;
    let height = 720 - topInset - bottomInset;

    if (effectiveMaskMode === 'Padrão') {
        x = 400;
        width = 1120;
    } else if (effectiveMaskMode === 'Reduzido') {
        x = 280;
        width = 1360;
    } else {
        x = 0;
        width = 1920;
    }

    if (window.Android && typeof window.Android.setAppDefaultDimensions === 'function') {
        window.Android.setAppDefaultDimensions(x, y, width, height);
    } else {
        console.log(`[AppDimensions] Minimalist theme setAppDefaultDimensions(x: ${x}, y: ${y}, w: ${width}, h: ${height})`);
    }

    const isMapActive = isProjectionMapDisplayActive();
    // TODO: Review map app resizing in future. For now, map background stays 1920x720.

    const devBg = document.querySelector('.dev-background');
    if (devBg) {
        let badge = devBg.querySelector('.dev-bg-badge');
        if (!badge) {
            badge = div({ className: 'dev-bg-badge' });
            devBg.appendChild(badge);
        }

        if (isMapActive) {
            devBg.style.left = '0px';
            devBg.style.top = '0px';
            devBg.style.width = '1920px';
            devBg.style.height = '720px';
            devBg.style.backgroundSize = '100% 100%';
            badge.textContent = 'Map In Dash (Fixed 1920x720)';
        } else {
            devBg.style.left = `${x}px`;
            devBg.style.top = `${y}px`;
            devBg.style.width = `${width}px`;
            devBg.style.height = `${height}px`;
            devBg.style.backgroundSize = '100% 100%';
            badge.textContent = `App In Dash: x=${x}, y=${y}, w=${width}, h=${height} (${effectiveMaskMode})`;
        }
    }
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
// Both bars can resolve to "visible only with/without projection", so anything that
// changes what is on the cluster has to re-run the visibility check.
subscribe('mapInDash', render);
subscribe('showRegenIcon', render);
subscribe('showRpmIcon', render);
render();



// Handle Card ID transitions
subscribe('cardId', (cardId) => {
    logger.log('cardId change:', cardId);

    if (cardId == 1 || cardId == 3) {
        setState('warningDismissed', false);
    }

    // No echo back to the backend: the card is owned by the car and travels one way,
    // car -> backend -> onCardChanged. Reporting it back gave the backend a second
    // writer for the active card.

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
        if (FRIENDLY_KEY_TO_CAN_KEY[key]) {
            // Raw CAN value pushed under a friendly display key (e.g. espStatus
            // '1') -> translate to its label via the shared car constants table.
            val = translateFriendlyValue(key, value);
        } else if (typeof value === 'string' && value.trim() !== '' && !isNaN(value)) {
            // Automatically convert numeric strings to numbers for compatibility with components
            val = Number(value);
        }
        setState(key, val);
        if (key === 'clusterBackground') {
            refreshBackdropSource();
            render();
        }
        // warningActive has its own subscription to render() at line 184, so no need for manual trigger here
        logger.leave('window.control');
    } catch (e) {
        console.error('[Error] Bridge control failed for key ' + key + ':', e);
    }
};

// ===== Backend-driven card change (contract: window.onCardChanged) =====
// The backend owns the active card and notifies the frontend exclusively here;
// there is no legacy control('cardId', ...) fallback. The existing cardId
// subscribers react to the state change (render + screen selection + bridge echo).
window.onCardChanged = function (cardId) {
    try {
        logger.log('[onCardChanged] cardId:', cardId);
        setState('cardId', Number(cardId));
    } catch (e) {
        console.error('[Error] Bridge onCardChanged failed:', e);
    }
};

// ===== Steering wheel key handling (decentralized navigation) =====
// The backend forwards raw wheel inputs via window.onKeyEvent(keyName) and no longer
// injects focus/screen changes itself. The theme owns all navigation. Car settings are
// committed through window.Android.updateCarData using raw CAN values (mirroring the
// Default theme); the backend then echoes the applied value back via control(...).
const AJUSTES_CAR_KEYS = {
    ajuste_driving: { key: 'car.drive_setting.drive_mode', values: ['0', '2', '1'] },
    ajuste_ev: { key: 'car.ev_setting.power_model_config', values: ['3', '1', '0'] },
    ajuste_steer: { key: 'car.drive_setting.steering_wheel_assist_mode', values: ['2', '0', '1'] },
    ajuste_regen: { key: 'car.ev_setting.energy_recovery_level', values: ['2', '0', '1'] },
    ajuste_esp: { key: 'car.drive_setting.esp_enable', values: ['1', '0'] }
};
const AJUSTES_ORDER = ['ajuste_driving', 'ajuste_ev', 'ajuste_steer', 'ajuste_regen', 'ajuste_esp'];
const GRAPH_NAV_IDS = graphList.map((g) => g.id);

let lastKeyTimeMs = 0;
const KEY_DEBOUNCE_MS = 50;

function getCurrentCanValueFromState(carKey) {
    switch (carKey) {
        case 'car.drive_setting.esp_enable':
            return get('espStatus') === 'ON' ? '1' : '0';
        case 'car.ev_setting.power_model_config': {
            const mode = String(get('evMode')).toUpperCase();
            if (mode === 'EV' || mode === 'MODO EV') return '3';
            if (mode === 'EVP' || mode === 'PRIOR. EV') return '1';
            return '0';
        }
        case 'car.drive_setting.drive_mode': {
            const mode = String(get('drivingMode')).toLowerCase();
            if (mode === 'sport') return '1';
            if (mode === 'eco') return '2';
            return '0';
        }
        case 'car.drive_setting.steering_wheel_assist_mode': {
            const mode = String(get('steerMode')).toLowerCase();
            if (mode === 'conforto') return '2';
            if (mode === 'esportiva') return '1';
            return '0';
        }
        case 'car.ev_setting.energy_recovery_level': {
            const mode = String(get('regenMode')).toLowerCase();
            if (mode === 'alto') return '1';
            if (mode === 'baixo') return '2';
            return '0';
        }
        default:
            return '';
    }
}

function androidUpdateCarData(key, value) {
    try {
        if (window.Android && typeof window.Android.updateCarData === 'function') {
            window.Android.updateCarData(key, String(value));
        } else {
            handleSettingsTelemetry(key, String(value));
        }
    } catch (e) {
        console.error('[onKeyEvent] updateCarData failed:', key, value, e);
    }
}

// Cycle a CAN setting by reading its current raw value from the bridge, so the
// index stays correct even though the UI state may hold a translated label.
function cycleCarSetting(carKey, values, direction) {
    let current = '';
    try {
        if (window.Android && typeof window.Android.getCarData === 'function') {
            current = String(window.Android.getCarData(carKey));
        } else {
            current = getCurrentCanValueFromState(carKey);
        }
    } catch (e) {
        current = '';
    }
    let idx = values.indexOf(current);
    if (idx === -1) idx = 0;
    const step = direction >= 0 ? 1 : -1;
    const nextIdx = (idx + step + values.length) % values.length;
    androidUpdateCarData(carKey, values[nextIdx]);
}

function handleMainMenuKey(keyName) {
    const focusArea = get('menuFocusArea') || 'main';
    const menuIds = menuItems.map((m) => m.id);
    const focused = get('focusedMenuItem');

    if (focusArea === 'main') {
        const idx = Math.max(0, menuIds.indexOf(focused));
        if (keyName === 'UP') {
            setState('focusedMenuItem', menuIds[(idx - 1 + menuIds.length) % menuIds.length]);
        } else if (keyName === 'DOWN') {
            setState('focusedMenuItem', menuIds[(idx + 1) % menuIds.length]);
        } else if (keyName === 'ENTER') {
            if (focused === 'option_info') return; // Informações is view-only
            setState('menuFocusArea', 'sub');
            if (focused === 'option_ajustes') {
                setState('focusedAjustesItem', 'ajuste_driving');
            }
        }
        return;
    }

    // Sub-focus: behaviour depends on which main item is active
    if (focused === 'option_ajustes') {
        const idx = Math.max(0, AJUSTES_ORDER.indexOf(get('focusedAjustesItem') || 'ajuste_driving'));
        if (keyName === 'UP') {
            setState('focusedAjustesItem', AJUSTES_ORDER[(idx - 1 + AJUSTES_ORDER.length) % AJUSTES_ORDER.length]);
        } else if (keyName === 'DOWN') {
            setState('focusedAjustesItem', AJUSTES_ORDER[(idx + 1) % AJUSTES_ORDER.length]);
        } else if (keyName === 'ENTER') {
            const cfg = AJUSTES_CAR_KEYS[get('focusedAjustesItem') || 'ajuste_driving'];
            if (cfg) cycleCarSetting(cfg.key, cfg.values, 1);
        }
    } else if (focused === 'option_6') {
        // Regeneração: UP/DOWN change recovery level, ENTER toggles one-pedal
        if (keyName === 'UP') {
            cycleCarSetting('car.ev_setting.energy_recovery_level', ['2', '0', '1'], 1); // Baixo -> Normal -> Alto
        } else if (keyName === 'DOWN') {
            cycleCarSetting('car.ev_setting.energy_recovery_level', ['2', '0', '1'], -1);
        } else if (keyName === 'ENTER_LONG') {
            const next = get('onepedal') ? '0' : '1';
            androidUpdateCarData('car.ev.setting.pedal_control_enable', next);
            setState('onepedal', !get('onepedal'));
        }
    } else if (focused === 'option_7') {
        // Gráficos: cycle the visible graph (pure UI state)
        if (!GRAPH_NAV_IDS.length) return;
        const idx = Math.max(0, GRAPH_NAV_IDS.indexOf(get('currentGraph')));
        if (keyName === 'UP') {
            setState('currentGraph', GRAPH_NAV_IDS[(idx - 1 + GRAPH_NAV_IDS.length) % GRAPH_NAV_IDS.length]);
        } else if (keyName === 'DOWN' || keyName === 'ENTER') {
            setState('currentGraph', GRAPH_NAV_IDS[(idx + 1) % GRAPH_NAV_IDS.length]);
        }
    }
}

function handleAirconKey(keyName) {
    const focusArea = get('focusArea') || 'fan';
    const isAcPowerOn = Number(get('power')) > 0;

    // LEFT/RIGHT are reserved for backend-driven card navigation and are not
    // forwarded to the theme, so ENTER toggles the fan/temp focus here.
    if (keyName === 'ENTER') {
        if (!isAcPowerOn) {
            setState('focusArea', 'fan');
            return;
        }
        setState('focusArea', focusArea === 'fan' ? 'temp' : 'fan');
        return;
    }
    if (keyName === 'BACK_LONG') {
        const currentRecycle = Number(get('recycle')) || 0;
        const nextRecycle = currentRecycle === 1 ? 0 : 1;
        setState('recycle', nextRecycle);
        androidUpdateCarData('car.hvac.cycle_mode', String(nextRecycle));
        return;
    }
    if (keyName === 'ENTER_LONG') {
        const currentAuto = Number(get('auto')) || 0;
        const nextAuto = currentAuto === 1 ? 0 : 1;
        setState('auto', nextAuto);
        androidUpdateCarData('car.hvac.auto_enable', String(nextAuto));
        if (nextAuto === 0) {
            const currentFan = parseInt(get('fan'), 10) || 1;
            androidUpdateCarData('car.hvac.fan_speed', String(currentFan));
        }
        try {
            if (window.Android && typeof window.Android.triggerSystemAction === 'function') {
                window.Android.triggerSystemAction('CANCEL_MAX_AC');
            }
        } catch (e) {
            console.error('[onKeyEvent] triggerSystemAction failed:', e);
        }
        return;
    }
    if (keyName !== 'UP' && keyName !== 'DOWN') return;

    if (focusArea === 'fan') {
        const currentFan = parseInt(get('fan'), 10) || 0;
        const nextFan = keyName === 'UP' ? Math.min(7, currentFan + 1) : Math.max(0, currentFan - 1);
        if (currentFan === 0 && nextFan > 0) {
            androidUpdateCarData('car.hvac.power_mode', '1');
        } else if (nextFan === 0) {
            androidUpdateCarData('car.hvac.power_mode', '0');
        }
        androidUpdateCarData('car.hvac.fan_speed', String(nextFan));
    } else {
        // The HVAC setpoint advances in half-degree increments.
        const currentTemp = parseFloat(get('temp')) || 22;
        const delta = keyName === 'UP' ? 0.5 : -0.5;
        const nextTemp = Math.min(32, Math.max(16, Math.round((currentTemp + delta) * 2) / 2));
        androidUpdateCarData('car.hvac.driver_temperature', nextTemp.toFixed(1));
    }
}

function handleSteeringWheelKey(keyName) {
    try {
        const now = Date.now();
        if (now - lastKeyTimeMs < KEY_DEBOUNCE_MS) {
            return;
        }
        lastKeyTimeMs = now;

        // Card 0 is the vehicle's own card: this theme is not what the driver is looking
        // at, so ignore wheel input entirely. The host forwards keys unconditionally by
        // design. Acting on them here changed vehicle state behind the driver's back —
        // UP/DOWN on card 0 raised the AC fan because this handler was still on the
        // aircon screen.
        if (get('cardId') == 0) {
            logger.log('[onKeyEvent] ignoring', keyName, ': native card (0) is active');
            return;
        }

        const screen = get('screen');
        logger.log('[onKeyEvent]', keyName, 'screen:', screen);

        // Clean display mode: any key wakes back to Normal and is consumed
        if (get('display') === 'Clean' && screen !== 'display_selection') {
            setState('display', 'Normal');
            androidUpdateCarData('display', 'Normal');
            return;
        }

        // BACK: leave sub-focus first, then return dynamic screens to the main menu.
        // aircon is tied to card 3 (backend-driven), so BACK does not force it away.
        if (keyName === 'BACK' || keyName === 'BACK_LONG') {
            if (screen === 'aircon' && keyName === 'BACK_LONG') {
                handleAirconKey(keyName);
                return;
            }
            if (screen === 'main_menu' && get('menuFocusArea') === 'sub') {
                setState('menuFocusArea', 'main');
            } else if (screen !== 'main_menu' && screen !== 'aircon') {
                setState('screen', 'main_menu');
            }
            return;
        }

        if (screen === 'main_menu') {
            handleMainMenuKey(keyName);
        } else if (screen === 'aircon') {
            handleAirconKey(keyName);
        }
    } catch (e) {
        console.error('[Error] Bridge onKeyEvent failed:', e);
    }
}
window.onKeyEvent = handleSteeringWheelKey;

// Route steering wheel keys through the shared ThemeBridgeAdapter (matches the
// Default theme) instead of clobbering window.onKeyEvent directly. This gives
// Minimalist dynamic capability discovery (getAvailableKeys) and safe chaining
// with any other listener bound to the same global hook.
// Contract (THEME_GUIDE "Client-Driven Subscriptions"): native only streams
// live updates for CAN keys a theme explicitly subscribes to via
// Android.subscribe(). Without this, these settings only ever get a one-shot
// refresh from the native card-entry snapshot and never update again live —
// onepedal in particular has NO native push path at all outside this channel.
const SETTINGS_KEYS_TO_SUBSCRIBE = [
    KEYS.ESP_ENABLE,
    KEYS.POWER_MODEL_CONFIG,
    KEYS.DRIVE_MODE,
    KEYS.STEER_ASSIST_MODE,
    KEYS.REGEN_LEVEL,
    KEYS.PEDAL_CONTROL_ENABLE,
    KEYS.HVAC_POWER,
    KEYS.HVAC_FAN_SPEED,
    KEYS.HVAC_DRIVER_TEMP,
    KEYS.HVAC_CYCLE_MODE,
    KEYS.HVAC_AUTO,
    KEYS.HVAC_ANION
];

const GRAPH_KEYS_TO_SUBSCRIBE = [
    KEYS.VEHICLE_SPEED,
    KEYS.ENGINE_SPEED,
    KEYS.INSTANT_FUEL_CONSUMPTION,
    KEYS.ENERGY_OUTPUT_PERCENTAGE,
    KEYS.CHARGE_CURRENT,
    KEYS.BATTERY_VOLTAGE,
    KEYS.INSTANT_ENERGY_CONSUMPTION
];

const GAUGE_KEYS_TO_SUBSCRIBE = [
    KEYS.GEAR_STATUS,
    KEYS.REMAIN_FUEL_PERCENTAGE,
    KEYS.BATTERY_POWER_PERCENTAGE,
    KEYS.FUEL_MODE_REMAIN_ODOMETER,
    KEYS.ELECTRIC_MODE_REMAIN_ODOMETER
];

const handleGraphTelemetry = createGraphTelemetryHandler(setState, {
    adjustSpeed: (rawSpeed) => {
        const enabled = bridge.getPreference('enableSpeedAdjustment', 'false') === 'true';
        const offset = parseFloat(bridge.getPreference('speedAdjustmentOffset', '0')) || 0.0;
        return getAdjustedSpeed(rawSpeed, enabled, offset);
    }
});

function handleSettingsTelemetry(key, value) {
    switch (key) {
        case KEYS.ESP_ENABLE:
            setState('espStatus', getLabel(KEYS.ESP_ENABLE, value));
            break;
        case KEYS.POWER_MODEL_CONFIG:
            setState('evMode', getLabel(KEYS.POWER_MODEL_CONFIG, value));
            break;
        case KEYS.DRIVE_MODE:
            setState('drivingMode', getLabel(KEYS.DRIVE_MODE, value));
            break;
        case KEYS.STEER_ASSIST_MODE:
            setState('steerMode', getLabel(KEYS.STEER_ASSIST_MODE, value));
            break;
        case KEYS.REGEN_LEVEL:
            setState('regenMode', getLabel(KEYS.REGEN_LEVEL, value));
            break;
        case KEYS.PEDAL_CONTROL_ENABLE:
            setState('onepedal', value === "1" || value === 1 || value === "true" || value === true);
            break;
        case KEYS.GEAR_STATUS:
            setState('gearState', getLabel(KEYS.GEAR_STATUS, value));
            break;
        case KEYS.REMAIN_FUEL_PERCENTAGE:
            setState('fuelPercent', Number(value));
            break;
        case KEYS.BATTERY_POWER_PERCENTAGE:
            setState('batteryPercent', Number(value));
            break;
        case KEYS.FUEL_MODE_REMAIN_ODOMETER:
            setState('fuelRange', Number(value));
            break;
        case KEYS.ELECTRIC_MODE_REMAIN_ODOMETER:
            setState('batteryRange', Number(value));
            break;
        case KEYS.HVAC_POWER: {
            const p = Number(value);
            setState('power', p);
            if (p === 0) setState('focusArea', 'fan');
            break;
        }
        case KEYS.HVAC_FAN_SPEED:
            setState('fan', Number(value));
            break;
        case KEYS.HVAC_DRIVER_TEMP:
            setState('temp', Number(value));
            break;
        case KEYS.HVAC_CYCLE_MODE:
            setState('recycle', Number(value));
            break;
        case KEYS.HVAC_AUTO:
            setState('auto', Number(value));
            break;
        case KEYS.HVAC_ANION:
            setState('aion', Number(value));
            break;
        default:
            break;
    }
}

async function initMinimalistBridge() {
    if (typeof bridge.reset === 'function') bridge.reset();
    await bridge.init();

    // theme.xml <configurations>.
    //
    // Bind the <stateVariable> name and nothing else. The app stores every config as
    // theme_config_<folder>_<stateVariable> (TelasScreen) and pushes it as
    // app.preferences.<stateVariable> (PreferencePushListener), so that is the only
    // name that ever carries a value.
    //
    // This used to also bind the snake_case theme.xml id. That second bind re-read a
    // key nothing writes, missed, and handed the *default* to a callback that wrote it
    // straight over the value the first bind had just loaded — so every setting came up
    // at its default and only took effect once the user toggled it and a live push
    // arrived. Subscribing before the read matters too: bindThemeSetting seeds state
    // synchronously, and a subscription registered afterwards never sees it.
    const bindSetting = (stateVar, defVal) => {
        subscribe(stateVar, render);
        bridge.bindThemeSetting(stateVar, defVal, setState);
    };

    // Defaults must mirror theme.xml, otherwise the boot frame renders the wrong mask
    // until the host delivers the stored value.
    bindSetting('topBarVisibility', 'Sempre');
    bindSetting('bottomBarVisibility', 'Só com Projeção');
    bindSetting('bottomBarNotch', true);

    bindSetting('projectionKeepVisible', 'Gauges, Hora, Marcha, HEV, Regen');
    bindSetting('showRegenIcon', true);
    bindSetting('showRpmIcon', true);
    bindSetting('navigationDisplayMode', 'Clean');
    bindSetting('appDisplayMode', 'Padrão');
    bindSetting('display', 'Normal');

    migrateLegacyBarSettings();
    render();

    subscribe('carPlayInDash', render);
    subscribe('projectionMirrorInDash', render);
    subscribe('projectionPreparingD3', render);
    subscribe('appInDash', render);
    subscribe('clusterBackground', render);
    bridge.subscribeKeys(handleSteeringWheelKey);
    bridge.subscribe(
        [...SETTINGS_KEYS_TO_SUBSCRIBE, ...GRAPH_KEYS_TO_SUBSCRIBE, ...GAUGE_KEYS_TO_SUBSCRIBE],
        (key, value) => {
            if (!handleGraphTelemetry(key, value)) {
                handleSettingsTelemetry(key, value);
            }
        }
    );
}
initMinimalistBridge().catch((e) => console.error('[Bridge] init failed:', e));

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
