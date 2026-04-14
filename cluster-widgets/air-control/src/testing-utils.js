import { setState, stateManager } from './state.js';
import { menuItems } from './components/mainMenu.js';

const focusableAreas = {
    main_menu: menuItems.map(item => item.id),
    ac_control: ['fan', 'temp'],
    regen: ['Baixo', 'Normal', 'Alto'],
    graph: ['evConsumption', 'gasConsumption', 'carSpeed'],
    speedometer: []
};

document.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.altKey || e.metaKey) return;

    const currentState = stateManager.getState();
    const currentScreen = currentState.screen;

    if (e.key === 'Backspace') {
        if (currentScreen !== 'main_menu') {
            window.showScreen('main_menu');
        }
        return;
    }

    if (currentScreen === 'main_menu') {
        const menuItems = focusableAreas.main_menu;
        const currentIndex = menuItems.indexOf(currentState.focusedMenuItem);

        if (e.key === 'ArrowUp') {
            const prevIndex = (currentIndex - 1 + menuItems.length) % menuItems.length;
            window.focus(menuItems[prevIndex]);
        } else if (e.key === 'ArrowDown') {
            const nextIndex = (currentIndex + 1) % menuItems.length;
            window.focus(menuItems[nextIndex]);

        } else if (e.key === 'Enter') {
            if (currentState.focusedMenuItem === 'option_1') {
                const currentStatus = stateManager.getState().espStatus;
                const newStatus = (currentStatus === 'ON') ? 'OFF' : 'ON';
                setState('espStatus', newStatus);
            } else if (currentState.focusedMenuItem === 'option_2') {
                const modes = ['HEV', 'PHEV', 'EV'];
                const currentMode = stateManager.getState().evMode;
                const currentIndex = modes.indexOf(currentMode);
                const nextIndex = (currentIndex + 1) % modes.length;
                const newMode = modes[nextIndex];
                setState('evMode', newMode);
            } else if (currentState.focusedMenuItem === 'option_3') {
                const modes = ['Normal', 'Eco', 'Sport'];
                const currentMode = stateManager.getState().drivingMode;
                const currentIndex = modes.indexOf(currentMode);
                const nextIndex = (currentIndex + 1) % modes.length;
                const newMode = modes[nextIndex];
                setState('drivingMode', newMode);
            } else if (currentState.focusedMenuItem === 'option_4') {
                window.showScreen('aircon');
            } else if (currentState.focusedMenuItem === 'option_5') {
                const modes = ['Normal', 'Conforto', 'Esportiva'];
                const currentMode = stateManager.getState().steerMode;
                const currentIndex = modes.indexOf(currentMode);
                const nextIndex = (currentIndex + 1) % modes.length;
                const newMode = modes[nextIndex];
                setState('steerMode', newMode);
            } else if (currentState.focusedMenuItem === 'option_6') {
                window.showScreen('regen');
            } else if (currentState.focusedMenuItem === 'option_7') {
                window.showScreen('graph');
            } else if (currentState.focusedMenuItem === 'option_8') {
                window.showScreen('speedometer');
            }


        }
    }

    else if (currentScreen === 'aircon') {
        const focusedArea = currentState.focusArea;

        if (e.key === 'Enter') {
            const controls = focusableAreas.ac_control;
            const currentIndex = controls.indexOf(focusedArea);
            const nextIndex = (currentIndex + 1) % controls.length;
            window.focus(controls[nextIndex]);
        } else if (e.key === ' ') {
            e.preventDefault();
            const newAutoModeState = (currentState.auto == 0 ? 1 : 0);
            setState('auto', newAutoModeState);
        } else if (e.key === 'a') {
            e.preventDefault();
            const newMaxModeState = (currentState.maxauto == 0 ? 1 : 0);
            setState('maxauto', newMaxModeState);
        }

        switch (focusedArea) {
            case 'fan':
                const currentFan = parseInt(currentState.fan, 10) || 0;
                if (e.key === 'ArrowUp' && currentFan < 7) {
                    window.control('fan', String(currentFan + 1));
                } else if (e.key === 'ArrowDown' && currentFan > 0) {
                    window.control('fan', String(currentFan - 1));
                }
                break;

            case 'temp':
                const currentTemp = parseFloat(currentState.temp) || 21.0;
                if (e.key === 'ArrowUp' && currentTemp < 32.0) {
                    window.control('temp', (currentTemp + 0.5).toFixed(1));
                } else if (e.key === 'ArrowDown' && currentTemp > 16.0) {
                    window.control('temp', (currentTemp - 0.5).toFixed(1));
                }
                break;

            default:
                break;
        }
    }

    else if (currentScreen === 'regen') {
        const regenMode = currentState.regenMode;

        if (e.key === 'Enter') {
            window.control('onepedal', !currentState.onepedal);
        } else if (e.key === 'ArrowUp') {
            const controls = focusableAreas.regen;
            const currentIndex = controls.indexOf(regenMode);
            if (currentIndex < 2) {
                const nextIndex = (currentIndex + 1) % controls.length;
                window.control('regenMode', controls[nextIndex]);
            }
        } else if (e.key === 'ArrowDown') {
            const controls = focusableAreas.regen;
            const currentIndex = controls.indexOf(regenMode);
            if (currentIndex > 0) {
                const nextIndex = (currentIndex - 1) % controls.length;
                window.control('regenMode', controls[nextIndex]);
            }
        }
    }
    else if (currentScreen === 'graph') {
        const currentGraph = currentState.currentGraph;

        if ((e.key === 'Enter') || (e.key === 'ArrowDown')) {
            const controls = focusableAreas.graph;
            const currentIndex = controls.indexOf(currentGraph);
            var nextIndex = currentIndex + 1;
            if (nextIndex >= controls.length) nextIndex = 0;
            window.control('currentGraph', controls[nextIndex]);
        } else if (e.key === 'ArrowUp') {
            const controls = focusableAreas.graph;
            const currentIndex = controls.indexOf(currentGraph);
            var nextIndex = currentIndex - 1;
            if (nextIndex < 0) nextIndex = controls.length - 1;
            window.control('currentGraph', controls[nextIndex]);
        }
    }
});

let lastValue = 0;
const smoothingFactor = 0.1;
let timeToModeChange = 10;
let simulationPhase = 'idle';
let currentSpeed = 152;
let steadyTimeCounter = 0;
const SIMULATION_INTERVAL = 100;


if (window.simulationInterval) clearInterval(window.simulationInterval);

window.simulationInterval = setInterval(() => {
    switch (simulationPhase) {
        case 'accelerating':
            if (currentSpeed < 200) {
                currentSpeed += 2.0;
            } else {
                currentSpeed = 200;
                simulationPhase = 'decelerating';
            }
            break;

        case 'decelerating':
            if (currentSpeed > 20) {
                currentSpeed -= 5;
            } else {
                currentSpeed = 20;
                simulationPhase = 'steady';
                steadyTimeCounter = 0;
            }
            break;

        case 'steady':
            const STEADY_DURATION_MS = 1000;
            if (steadyTimeCounter * SIMULATION_INTERVAL < STEADY_DURATION_MS) {
                steadyTimeCounter++;
            } else {
                simulationPhase = 'stopping';
            }
            break;

        case 'stopping':
            if (currentSpeed > 0) {
                currentSpeed -= 1;
            } else {
                currentSpeed = 0;
                simulationPhase = 'idle';
                setTimeout(() => {
                    simulationPhase = 'accelerating';
                }, 1000);
            }
            break;

        case 'idle':
        default:
            break;
    }

    setState('carSpeed', Math.max(0, currentSpeed.toFixed(1)));

    const randomTarget = Math.floor(Math.random() * 101);
    lastValue = (lastValue * (1 - smoothingFactor)) + (randomTarget * smoothingFactor);

    timeToModeChange--;
    if (timeToModeChange <= 0) {
        const currentMode = stateManager.getState().gasConsumptionMode;
        const newMode = (currentMode === 'Running') ? 'Idle' : 'Running';
        setState('gasConsumptionMode', newMode);

        timeToModeChange = Math.floor(Math.random() * 100) + 50;
    }

    const currentMode = stateManager.getState().gasConsumptionMode;

    if (currentMode === 'Running') {
        setState('gasConsumption', Math.round(lastValue) / 3);
        setState('gasConsumptionIdle', 0);
    } else {
        setState('gasConsumption', 0);
        setState('gasConsumptionIdle', Math.round(lastValue) / 20);
    }

    setState('evConsumption', Math.round(lastValue) - 50);
    setState('lastRegenValue', Math.round(lastValue));

}, SIMULATION_INTERVAL);

setTimeout(() => {
    simulationPhase = 'accelerating';
}, 5000);


