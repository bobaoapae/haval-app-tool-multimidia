import { initTestHarness } from './testing-harness.js';

// Decoupled Simulation Harness for Keyboard and Telemetry Triggers
export function initSimulationHarness(stateManager, menuItems) {
    window.__SIMULATION_STATE_MANAGER__ = stateManager;
    window.__SIMULATION_MENU_ITEMS__ = menuItems;

    const setState = (key, val) => {
        const mgr = window.__SIMULATION_STATE_MANAGER__ || stateManager;
        mgr.set(key, val);
    };

    window.__AIR_CONTROL_TEST_MODE = true;
    setState('enableOdometer', true);
    setState('enableRevisionWarning', true);
    setState('odometer', 11450);
    setState('nextRevisionKm', 12000);
    setState('nextRevisionDate', Date.now() + 15 * 24 * 60 * 60 * 1000);
    setState('inside_temp', 32);
    setState('outside_temp', 28);
    setState('tripAnalysisActive', true);
    setState('tripAnalysisScore', 82);

    const focusableAreas = {
        main_menu: menuItems.map(item => item.id),
        ac_control: ['fan', 'temp'],
        regen: ['Baixo', 'Normal', 'Alto'],
        graph: ['evConsumption', 'gasConsumption', 'carSpeed'],
        display_selection: ['title_mask', 'mode_normal', 'mode_reduzido', 'mode_clean']
    };

    // If running under dev-controls (index.html), add a red background to help identify the environment
    if (window.location.pathname.endsWith('index.html') || window.location.pathname === '/' || window.location.pathname.endsWith('/')) {
        console.log('[Dev-Controls] Environment detected');
    }

    if (!window.__SIMULATION_HARNESS_BOUND__) {
        window.__SIMULATION_HARNESS_BOUND__ = true;
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey || e.altKey || e.metaKey) return;

            const mgr = window.__SIMULATION_STATE_MANAGER__ || stateManager;
            const currentState = mgr.getState();
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
                const currentWarn = mgr.get('warningActive');
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
                const current = mgr.get('bsdLeft');
                console.log('[BSD Debug] Toggling Left BSD to:', !current);
                if (window.updateWarning) {
                    window.updateWarning('car.ipk_info.bsd_lca_warning_reqleft', !current ? '1' : '0');
                } else {
                    setState('bsdLeft', !current);
                }
                return;
            }

            if (e.key.toLowerCase() === 'r') {
                const current = mgr.get('bsdRight');
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

            if (e.key === '[' || e.key === 'PageUp') {
                const currentIndex = cards.indexOf(currentCardId);
                const prevIndex = (currentIndex - 1 + cards.length) % cards.length;
                const targetCard = cards[prevIndex];
                const cardMeaning = { 0: 'Hide Menu', 1: 'Main Menu', 3: 'AC Menu' };
                console.log(`[Card Simulation] Cycle Down -> Card ${targetCard} (${cardMeaning[targetCard]})`);
                setState('cardId', targetCard);
                return;
            }

            if (e.key === ']' || e.key === 'PageDown') {
                const currentIndex = cards.indexOf(currentCardId);
                const nextIndex = (currentIndex + 1) % cards.length;
                const targetCard = cards[nextIndex];
                const cardMeaning = { 0: 'Hide Menu', 1: 'Main Menu', 3: 'AC Menu' };
                console.log(`[Card Simulation] Cycle Up -> Card ${targetCard} (${cardMeaning[targetCard]})`);
                setState('cardId', targetCard);
                return;
            }

            if (e.key === 'Backspace') {
                if (currentScreen !== 'main_menu') {
                    window.showScreen('main_menu');
                }
                return;
            }

            if (currentScreen === 'aircon') {
                if (e.key === ' ') {
                    e.preventDefault();
                    const newAutoModeState = (currentState.auto == 0 ? 1 : 0);
                    setState('auto', newAutoModeState);
                } else if (e.key === 'a') {
                    e.preventDefault();
                    const newModeState = (currentState.maxauto == 0 ? 1 : 0);
                    setState('maxauto', newModeState);
                }
            }

            if (e.key === 'g' || e.key === 'G') {
                const gears = ['P', 'R', 'N', 'D'];
                const currentGear = mgr.getState().gearState;
                const currentIndex = gears.indexOf(currentGear);
                const nextIndex = (currentIndex + 1) % gears.length;
                setState('gearState', gears[nextIndex]);
            }

            if (e.key.toLowerCase() === 'k') {
                const options = [false, true, 'left', 'right'];
                const currentAppInDash = mgr.getState().appInDash;
                let currentIndex = options.indexOf(currentAppInDash);
                if (currentIndex === -1) currentIndex = 0;
                
                const nextIndex = (currentIndex + 1) % options.length;
                const nextValue = options[nextIndex];
                
                console.log(`[Mask Simulation] Cycle appInDash -> ${nextValue}`);
                setState('appInDash', nextValue);
            }

            if (e.key.toLowerCase() === 'c') {
                const current = mgr.getState().clusterEnabled;
                console.log(`[Cluster Simulation] Toggling clusterEnabled to: ${!current}`);
                setState('clusterEnabled', !current);
            }

            if (e.key.toLowerCase() === 'o') {
                const currentOnePedal = mgr.getState().onepedal;
                console.log(`[Mode Simulation] Toggle onepedal -> ${!currentOnePedal}`);
                setState('onepedal', !currentOnePedal);
            }

            if (e.key === '6') {
                const current = mgr.get('enableOdometer');
                console.log(`[Testing] Toggling enableOdometer to: ${!current}`);
                setState('enableOdometer', !current);
            }

            if (e.key === '7') {
                const current = mgr.get('enableRevisionWarning');
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
    }

    let lastValue = 0;
    const smoothingFactor = 0.05; // Less dramatic changes
    let timeToModeChange = 10;
    let simulationPhase = 'idle';
    let currentSpeed = 0;
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
        if (window.__SIMULATION_ACTIVE__ === false) return;
        const mgr = window.__SIMULATION_STATE_MANAGER__ || stateManager;
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
        setState('tripAnalysisScore', Math.max(74, Math.min(99, Math.round(88 - (lastValue / 12) + (currentSpeed / 30)))));

        const randomTarget = Math.floor(Math.random() * 101);
        lastValue = (lastValue * (1 - smoothingFactor)) + (randomTarget * smoothingFactor);

        timeToModeChange--;
        if (timeToModeChange <= 0) {
            const currentMode = mgr.getState().gasConsumptionMode;
            const newMode = (currentMode === 'Running') ? 'Idle' : 'Running';
            setState('gasConsumptionMode', newMode);

            timeToModeChange = Math.floor(Math.random() * 100) + 50;
        }

        const currentMode = mgr.getState().gasConsumptionMode;

        if (currentMode === 'Running') {
            const gasV = Math.round(lastValue) / 3;
            setState('gasConsumption', gasV);
            setState('gasConsumptionIdle', 0);
            // Simulate RPM: if running AND speed > 0, it should be between 800 and 7000
            // Force 0 if speed is 0
            const playsRPM = currentSpeed > 0;
            const simulatedRPM = playsRPM ? 1000 + (currentSpeed * 40) + (Math.random() * 500) : 0;
            setState('engineRPM', Math.round(Math.min(Math.max(simulatedRPM, 0), 7000)));
        } else {
            setState('gasConsumption', 0);
            setState('gasConsumptionIdle', Math.round(lastValue) / 20);
            // If idle, RPM is 800 but ONLY if speed > 0
            const idleRPM = currentSpeed > 0 ? 800 : 0;
            setState('engineRPM', Math.round(idleRPM));
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

    // Initialize the agent-friendly interactive test harness
    initTestHarness(stateManager, menuItems);
}
