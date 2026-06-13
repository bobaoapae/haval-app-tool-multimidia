import { setState, stateManager } from '../core/state.js';
import { menuItems } from '../core/components/mainMenu.js';
import { SHOW_SCORE_CARD_IN_SIMULATOR } from './testingFlags.js';

window.__AIR_CONTROL_TEST_MODE = true;
setState('enableOdometer', true);
setState('enableRevisionWarning', true);
setState('odometer', 11450);
setState('nextRevisionKm', 12000);
setState('nextRevisionDate', Date.now() + 15 * 24 * 60 * 60 * 1000);
setState('tripAnalysisActive', SHOW_SCORE_CARD_IN_SIMULATOR);
setState('tripAnalysisScore', SHOW_SCORE_CARD_IN_SIMULATOR ? 82 : null);

const focusableAreas = {
    main_menu: menuItems.map(item => item.id),
    ac_control: ['fan', 'temp'],
    regen: ['Baixo', 'Normal', 'Alto'],
    graph: ['evConsumption', 'gasConsumption', 'carSpeed'],
    display_selection: ['title_mask', 'mode_normal', 'mode_reduzido', 'mode_clean', 'mode_mapa']
};
// If running under dev-controls (index.html), add a red background to help identify the environment
if (window.location.pathname.endsWith('index.html') || window.location.pathname === '/' || window.location.pathname.endsWith('/')) {
    console.log('[Dev-Controls] Environment detected');
}

document.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.altKey || e.metaKey) return;

    const currentState = stateManager.getState();
    const currentScreen = currentState.screen;
    const currentCardId = (currentState.cardId !== undefined) ? currentState.cardId : 1;
    const cards = [0, 1, 3];

    // If in Clean mode, any key (except modifiers already handled) restores Normal mode
    if (currentState.display === 'Clean') {
        console.log('[Clean Mode] Exit via key press:', e.key);
        window.control('display', 'Normal');
        return;
    }

    if (e.key.toLowerCase() === 'w') {
        const currentWarn = stateManager.get('warningActive');
        console.log('[Warning Debug] Toggling warningActive to:', !currentWarn);
        if (window.updateWarning) {
            window.updateWarning('fake.warning', !currentWarn ? '1' : '0');
        } else {
            setState('warningActive', !currentWarn);
        }
        // If we are activating warning, hide cards
        if (!currentWarn) {
            setState('cardId', 0);
        } else {
            setState('cardId', 1);
        }
        return;
    }

    if (e.key.toLowerCase() === 'l') {
        const current = stateManager.get('bsdLeft');
        console.log('[BSD Debug] Toggling Left BSD to:', !current);
        if (window.updateWarning) {
            window.updateWarning('car.ipk_info.bsd_lca_warning_reqleft', !current ? '1' : '0');
        } else {
            setState('bsdLeft', !current);
        }
        return;
    }

    if (e.key.toLowerCase() === 'r') {
        const current = stateManager.get('bsdRight');
        console.log('[BSD Debug] Toggling Right BSD to:', !current);
        if (window.updateWarning) {
            window.updateWarning('car.ipk_info.bsd_lca_warning_reqright', !current ? '1' : '0');
        } else {
            setState('bsdRight', !current);
        }
        return;
    }

    if (e.key === 'Escape') {
        console.log('[Warning Debug] Force clear warningActive');
        setState('warningActive', false);
    }

    if (e.key === 'ArrowRight') {
        const currentIndex = cards.indexOf(currentCardId);
        const nextIndex = (currentIndex + 1) % cards.length;
        const targetCard = cards[nextIndex];
        const mapCardActive = currentState.projectionMirrorInDash === true || currentState.carPlayInDash === true;
        const cardMeaning = { 0: 'Hide Menu', 1: 'Main Menu', 3: mapCardActive ? 'Map Display' : 'AC Menu' };
        console.log(`[Card Simulation] Cycle Up -> Card ${targetCard} (${cardMeaning[targetCard]})`);
        setState('cardId', targetCard);
        return;
    }

    if (e.key === 'ArrowLeft') {
        const currentIndex = cards.indexOf(currentCardId);
        const prevIndex = (currentIndex - 1 + cards.length) % cards.length;
        const targetCard = cards[prevIndex];
        const mapCardActive = currentState.projectionMirrorInDash === true || currentState.carPlayInDash === true;
        const cardMeaning = { 0: 'Hide Menu', 1: 'Main Menu', 3: mapCardActive ? 'Map Display' : 'AC Menu' };
        console.log(`[Card Simulation] Cycle Down -> Card ${targetCard} (${cardMeaning[targetCard]})`);
        setState('cardId', targetCard);
        return;
    }

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
                const modes = ['EV', 'EVP', 'HEV'];
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
                window.showScreen('display_selection');
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
            }
        }
    }

    else if (currentScreen === 'aircon') {
        const focusedArea = currentState.focusArea;

        if (e.key === 'Enter') {
            if (currentState.impulseauto == 1) {
                window.focus('temp');
            } else {
                const controls = focusableAreas.ac_control;
                const currentIndex = controls.indexOf(focusedArea);
                const nextIndex = (currentIndex + 1) % controls.length;
                window.focus(controls[nextIndex]);
            }
        } else if (e.key === ' ') {
            e.preventDefault();
            const newAutoModeState = (currentState.auto == 0 ? 1 : 0);
            setState('auto', newAutoModeState);
        } else if (e.key === 'a') {
            e.preventDefault();
            const newModeState = (currentState.maxauto == 0 ? 1 : 0);
            setState('maxauto', newModeState);
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
                if (currentState.impulseauto == 1) {
                    const currentTargetTemp = parseFloat(currentState.targetTemp) || 21.0;
                    if (e.key === 'ArrowUp' && currentTargetTemp < 32.0) {
                        window.control('targetTemp', (currentTargetTemp + 0.5).toFixed(1));
                    } else if (e.key === 'ArrowDown' && currentTargetTemp > 16.0) {
                        window.control('targetTemp', (currentTargetTemp - 0.5).toFixed(1));
                    }
                } else {
                    const currentTemp = parseFloat(currentState.temp) || 21.0;
                    if (e.key === 'ArrowUp' && currentTemp < 32.0) {
                        window.control('temp', (currentTemp + 0.5).toFixed(1));
                    } else if (e.key === 'ArrowDown' && currentTemp > 16.0) {
                        window.control('temp', (currentTemp - 0.5).toFixed(1));
                    }
                }
                break;

            default:
                break;
        }
    }

    else if (currentScreen === 'regen') {
        const regenMode = currentState.regenMode;

        if (e.key === 'Enter') {
            const nextValue = !currentState.onepedal;
            console.log(`[Regen Simulation] Toggle onepedal via Enter -> ${nextValue}`);
            window.control('onepedal', nextValue);
        } else if (e.key === 'ArrowUp') {
            const controls = focusableAreas.regen;
            const currentIndex = controls.indexOf(regenMode);
            const nextIndex = (currentIndex + 1) % controls.length;
            window.control('regenMode', controls[nextIndex]);
        } else if (e.key === 'ArrowDown') {
            const controls = focusableAreas.regen;
            const currentIndex = controls.indexOf(regenMode);
            const prevIndex = (currentIndex - 1 + controls.length) % controls.length;
            window.control('regenMode', controls[prevIndex]);
        }
    }
    else if (currentScreen === 'graph') {
        const currentGraph = currentState.currentGraph;

        if ((e.key === 'Enter') || (e.key === 'ArrowDown')) {
            const controls = focusableAreas.graph;
            const currentIndex = controls.indexOf(currentGraph);
            const nextIndex = (currentIndex + 1) % controls.length;
            window.control('currentGraph', controls[nextIndex]);
        } else if (e.key === 'ArrowUp') {
            const controls = focusableAreas.graph;
            const currentIndex = controls.indexOf(currentGraph);
            const prevIndex = (currentIndex - 1 + controls.length) % controls.length;
            window.control('currentGraph', controls[prevIndex]);
        }
    }
    else if (currentScreen === 'display_selection') {
        const controls = focusableAreas.display_selection;
        const currentFocus = currentState.displayFocus || 'mode_normal';
        const currentIndex = Math.max(0, controls.indexOf(currentFocus));

        if (e.key === 'ArrowUp') {
            let prevIndex = (currentIndex - 1 + controls.length) % controls.length;
            // Skip title
            if (controls[prevIndex] === 'title_mask') {
                prevIndex = (prevIndex - 1 + controls.length) % controls.length;
            }
            window.focus(controls[prevIndex]);
        } else if (e.key === 'ArrowDown') {
            let nextIndex = (currentIndex + 1) % controls.length;
            // Skip title
            if (controls[nextIndex] === 'title_mask') {
                nextIndex = (nextIndex + 1) % controls.length;
            }
            window.focus(controls[nextIndex]);
        } else if (e.key === 'Enter') {
            if (currentFocus.startsWith('mode_')) {
                const newDisplay = currentFocus.replace('mode_', '');
                const formattedDisplay = newDisplay.charAt(0).toUpperCase() + newDisplay.slice(1);
                window.control('display', formattedDisplay);
                if (window.Android && window.Android.saveSetting) {
                    window.Android.saveSetting('currentClusterDisplay', formattedDisplay);
                }
            }
        }
    }

    if (e.key === 'g' || e.key === 'G') {
        const gears = ['P', 'R', 'N', 'D'];
        const currentGear = stateManager.getState().gearState;
        const currentIndex = gears.indexOf(currentGear);
        const nextIndex = (currentIndex + 1) % gears.length;
        setState('gearState', gears[nextIndex]);
    }

    if (e.key.toLowerCase() === 'k') {
        const options = [false, true, 'left', 'right'];
        const currentAppInDash = stateManager.getState().appInDash;
        let currentIndex = options.indexOf(currentAppInDash);
        if (currentIndex === -1) currentIndex = 0;

        const nextIndex = (currentIndex + 1) % options.length;
        const nextValue = options[nextIndex];

        console.log(`[Mask Simulation] Cycle appInDash -> ${nextValue}`);
        setState('appInDash', nextValue);
    }

    if (e.key.toLowerCase() === 'c') {
        const current = stateManager.getState().clusterEnabled;
        console.log(`[Cluster Simulation] Toggling clusterEnabled to: ${!current}`);
        setState('clusterEnabled', !current);
    }

    if (e.key.toLowerCase() === 'o') {
        const currentOnePedal = stateManager.getState().onepedal;
        console.log(`[Mode Simulation] Toggle onepedal -> ${!currentOnePedal}`);
        setState('onepedal', !currentOnePedal);
    }

    if (e.key === '6') {
        const current = stateManager.get('enableOdometer');
        console.log(`[Testing] Toggling enableOdometer to: ${!current}`);
        setState('enableOdometer', !current);
    }

    if (e.key === '7') {
        const current = stateManager.get('enableRevisionWarning');
        console.log(`[Testing] Toggling enableRevisionWarning to: ${!current}`);
        setState('enableRevisionWarning', !current);
    }

    if (e.key.toLowerCase() === 'm') {
        const modes = ['km', 'date', 'none'];
        if (!window.maintenanceMode) window.maintenanceMode = 'km';
        const currentIndex = modes.indexOf(window.maintenanceMode);
        const nextIndex = (currentIndex + 1) % modes.length;
        window.maintenanceMode = modes[nextIndex];
        console.log(`[Maintenance Simulation] Toggle Mode -> ${window.maintenanceMode}`);

        if (window.maintenanceMode === 'none') {
            setState('enableRevisionWarning', false);
            setState('nextRevisionKm', 999999);
            setState('nextRevisionDate', 0);
        } else if (window.maintenanceMode === 'km') {
            setState('enableRevisionWarning', true);
            setState('nextRevisionKm', 12000);
            setState('nextRevisionDate', Date.now() + 60 * 24 * 60 * 60 * 1000);
        } else if (window.maintenanceMode === 'date') {
            setState('enableRevisionWarning', true);
            setState('nextRevisionKm', 20000);
            setState('nextRevisionDate', Date.now() + 15 * 24 * 60 * 60 * 1000);
        }
    }
});

let lastValue = 0;
const smoothingFactor = 0.05; // Less dramatic changes
let timeToModeChange = 10;
let simulationPhase = 'idle';
let currentSpeed = 150;
let steadyTimeCounter = 0;
const SIMULATION_INTERVAL = 100;

// Fuel and Battery Animation Constants
const MAX_FUEL_RANGE = 700;
const MAX_BATTERY_RANGE = 170;
const DECREASE_TIME_MS = 30000;
const INCREASE_TIME_MS = 5000;

let fuelBatteryPhase = 'decreasing';
let animationTimeCounter = 0;


if (window.simulationInterval) clearInterval(window.simulationInterval);

window.simulationInterval = setInterval(() => {
    switch (simulationPhase) {
        case 'accelerating':
            if (currentSpeed < 150) {
                currentSpeed += 2.0;
            } else {
                currentSpeed = 150;
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
                }, 5000);
            }
            break;

        case 'idle':
        default:
            break;
    }

    setState('carSpeed', Math.max(0, currentSpeed.toFixed(1)));
    if (SHOW_SCORE_CARD_IN_SIMULATOR) {
        setState('tripAnalysisScore', Math.max(74, Math.min(99, Math.round(88 - (lastValue / 12) + (currentSpeed / 30)))));
    }

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
        const gasV = Math.round(lastValue) / 3;
        setState('gasConsumption', gasV);
        setState('gasConsumptionIdle', 0);
        // Simulate RPM: if running AND speed > 0, it should be between 800 and 7000
        // Force 0 if speed is 0
        const playsRPM = currentSpeed > 0;
        const simulatedRPM = playsRPM ? 1000 + (currentSpeed * 40) + (Math.random() * 500) : 0;
        setState('engineRPM', Math.min(Math.max(simulatedRPM, 0), 7000));
    } else {
        setState('gasConsumption', 0);
        setState('gasConsumptionIdle', Math.round(lastValue) / 20);
        // If idle, RPM is 800 but ONLY if speed > 0
        const idleRPM = currentSpeed > 0 ? 800 : 0;
        setState('engineRPM', idleRPM);
    }

    // Simulate EV power factor: -100 to +100 % (for power ring)
    const powerFactor = Math.round(lastValue * 2) - 100;
    setState('evPowerFactor', powerFactor * 2);
    // Simulate EV power in kW: ±120 kW range (for graph)
    if (powerFactor > 0) setState('evPowerKw', Math.round(powerFactor * 4 * Math.abs(currentSpeed) / 100));
    else setState('evPowerKw', Math.round(powerFactor));
    setState('lastRegenValue', Math.round(lastValue));

    // Fuel and Battery Animation Logic
    animationTimeCounter += SIMULATION_INTERVAL;

    let percent = 100;
    if (fuelBatteryPhase === 'decreasing') {
        percent = 100 - (animationTimeCounter / DECREASE_TIME_MS) * 100;
        if (animationTimeCounter >= DECREASE_TIME_MS) {
            percent = 0;
            fuelBatteryPhase = 'increasing';
            animationTimeCounter = 0;
        }
    } else {
        percent = (animationTimeCounter / INCREASE_TIME_MS) * 100;
        if (animationTimeCounter >= INCREASE_TIME_MS) {
            percent = 100;
            fuelBatteryPhase = 'decreasing';
            animationTimeCounter = 0;
        }
    }

    const currentFuelPercent = Math.max(0, Math.min(100, Math.round(percent)));
    const currentBatteryPercent = Math.max(0, Math.min(100, Math.round(percent)));

    setState('fuelPercent', currentFuelPercent);
    setState('batteryPercent', currentBatteryPercent);
    setState('fuelRange', Math.round((currentFuelPercent / 100) * MAX_FUEL_RANGE));
    setState('batteryRange', Math.round((currentBatteryPercent / 100) * MAX_BATTERY_RANGE));

    // Odometer Simulation
    if (!window.simulatedOdo) window.simulatedOdo = 11450.5; // Start near revision
    if (currentSpeed > 0) {
        // km/h to km/step: (speed * interval_ms) / (1000 * 3600)
        const delta = (currentSpeed * SIMULATION_INTERVAL) / 3600000;
        window.simulatedOdo += delta;
    }
    setState('odometer', Math.floor(window.simulatedOdo));



}, SIMULATION_INTERVAL);

setTimeout(() => {
    simulationPhase = 'accelerating';
}, 5000);
