import {getState as get, setState, subscribe} from './state.js';
import {createMainMenu} from './components/mainMenu.js';
import {createAcControlScreen, updateProgressRings as updateProgressRingsAC} from "./components/aircon/mainAcControl.js";
import {createRegenScreen, updateProgressRings as updateProgressRingsRegen } from "./components/regen/regenControl.js";
import {createGraphScreen } from "./components/graphs/graphs.js";
import {createSpeedometerScreen} from "./components/speedometer/speedometer.js";
import { div } from './utils/createElement.js';

if (process.env.NODE_ENV === 'development') {
    import('./testing-utils.js');
}

const appContainer = document.getElementById('app');
let currentComponent = null;

function render() {
    const screen = get('screen');

    if (currentComponent && currentComponent.cleanup) {
        currentComponent.cleanup();
    }

    if (appContainer && appContainer.innerHTML) {
        appContainer.innerHTML = '';
    }

    if (screen === 'main_menu') {
        currentComponent = createMainMenu();
    } else if (screen === 'aircon') {
        currentComponent = createAcControlScreen();
    } else if (screen === 'regen') {
        currentComponent = createRegenScreen();
    } else if (screen === 'graph') {
        currentComponent = createGraphScreen();
    } else if (screen === 'speedometer') {
        currentComponent = createSpeedometerScreen();
    }

    if (currentComponent) {
        const element = currentComponent.element || currentComponent;
        const onMount = currentComponent.onMount;
        currentComponent = element;
        appContainer.appendChild(element);
        if (onMount) {
            onMount();
        }
    }
}

// Start rendering and subscribe to listen for screen changes thus triggering new render
subscribe('screen', render);
render();


// Functions used by Kotlin to trigger interactions
window.showScreen = function(screenName) {
    setState('screen', screenName);
};

window.focus = function(item) {
    const screen = get('screen');
    if (screen === 'main_menu') {
        setState('focusedMenuItem', item);
    } else if (screen === 'aircon') {
        setState('focusArea', item);
    }
};

window.control = function(key, value) {
    setState(key, value);
};

window.cleanup = function() {
    if (currentComponent && currentComponent.cleanup) {
        currentComponent.cleanup();
    }
};
