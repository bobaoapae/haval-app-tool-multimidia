/**
 * Shisuku Theme Engine Dynamic Testing Harness (testing-harness.js)
 * High-fidelity, browser-based dynamic testing sandbox for theme components.
 * Exposes window.__TEST_HARNESS API for AI agents and local manual visual checks.
 */

export function initTestHarness(stateManager, menuItems) {
    const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined';
    if (!isBrowser) return;

    console.log('🤖 [Testing Harness] Initializing Agent Interactive Testing Harness...');

    const registry = [];
    let isRunning = false;
    let uiConsole = null;
    let uiPanel = null;
    let settingsPanel = null;
    let statePanel = null;
    // Helper functions for JNI state mapping inside standard tests
    const getState = (key) => stateManager.get(key);
    const setState = (key, value) => stateManager.set(key, value);

    const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

    // Simulation Engine Top-Level Declarations
    let isSimRunning = false;

    const startSimulation = () => {
        window.__SIMULATION_ACTIVE__ = true;
        isSimRunning = true;

        const simToggleBtn = document.getElementById('harness-sim-toggle');
        const simIcon = document.getElementById('harness-sim-icon');
        const simText = document.getElementById('harness-sim-text');

        if (simToggleBtn) {
            simToggleBtn.style.background = 'rgba(239, 68, 68, 0.15)';
            simToggleBtn.style.border = '1px solid rgba(239, 68, 68, 0.3)';
            simToggleBtn.style.color = '#ef4444';
        }
        if (simIcon) simIcon.textContent = '⏸️';
        if (simText) simText.textContent = 'Pause Sim';
    };

    const stopSimulation = () => {
        window.__SIMULATION_ACTIVE__ = false;
        isSimRunning = false;

        const simToggleBtn = document.getElementById('harness-sim-toggle');
        const simIcon = document.getElementById('harness-sim-icon');
        const simText = document.getElementById('harness-sim-text');

        if (simToggleBtn) {
            simToggleBtn.style.background = 'rgba(168, 85, 247, 0.15)';
            simToggleBtn.style.border = '1px solid rgba(168, 85, 247, 0.3)';
            simToggleBtn.style.color = '#c084fc';
        }
        if (simIcon) simIcon.textContent = '▶️';
        if (simText) simText.textContent = 'Start Sim';
    };

    // Simulated steering wheel key dispatcher
    const dispatchKeyEvent = async (keyName) => {
        if (keyName === 'LEFT' || keyName === 'RIGHT') {
            const cardSequence = [0, 1, 3];
            const currentCard = Number(stateManager.get('cardId'));
            let currentIndex = cardSequence.indexOf(currentCard);
            if (currentIndex === -1) currentIndex = 1; // Default to Menu card (1)

            // LEFT advances card cycle; RIGHT goes back (car wheel mapping)
            let nextIndex;
            if (keyName === 'LEFT') {
                nextIndex = (currentIndex + 1) % cardSequence.length;
            } else {
                nextIndex = (currentIndex - 1 + cardSequence.length) % cardSequence.length;
            }
            const nextCard = cardSequence[nextIndex];
            console.log(`[Simulator Card Simulation] Cycling card from ${currentCard} to ${nextCard}`);
            stateManager.set('cardId', nextCard);
        }

        if (window.onKeyEvent) {
            console.log(`[Testing Harness] Dispatching steering key: ${keyName}`);
            window.onKeyEvent(keyName);
            await delay(120); // Wait for transition render
        } else {
            console.warn('[Testing Harness] window.onKeyEvent not bound.');
        }
    };

    // Logging helper inside the test panel
    const log = (msg, type = 'info') => {
        const time = new Date().toLocaleTimeString();
        console.log(`[Testing Suite] [${type.toUpperCase()}] ${msg}`);
        if (uiConsole) {
            const line = document.createElement('div');
            line.style.padding = '3px 0';
            line.style.borderBottom = '1px solid rgba(255, 255, 255, 0.03)';
            line.style.fontSize = '12px';
            line.style.fontFamily = 'monospace';

            if (type === 'pass') {
                line.style.color = '#38bdf8'; // Neo Cyan Pass
                line.innerHTML = `<span style="color: #4ade80;">[PASS]</span> <span style="color: #94a3b8;">${time}</span> — ${msg}`;
            } else if (type === 'fail') {
                line.style.color = '#f87171'; // Amber-Red Fail
                line.innerHTML = `<span style="color: #ef4444; font-weight: bold;">[FAIL]</span> <span style="color: #94a3b8;">${time}</span> — ${msg}`;
            } else {
                line.style.color = '#e2e8f0';
                line.innerHTML = `<span style="color: #38bdf8;">[INFO]</span> <span style="color: #94a3b8;">${time}</span> — ${msg}`;
            }
            uiConsole.appendChild(line);
            uiConsole.scrollTop = uiConsole.scrollHeight;
        }
    };

    // Assertion engine
    class Assertor {
        constructor(testName, assertionsList) {
            this.testName = testName;
            this.assertions = assertionsList;
        }

        assert(condition, message) {
            if (condition) {
                log(message, 'pass');
                this.assertions.push({ message, passed: true });
            } else {
                log(`Assertion Failed: ${message}`, 'fail');
                this.assertions.push({ message, passed: false, error: 'Condition failed' });
                throw new Error(`Assertion failed: ${message}`);
            }
        }

        assertEqual(actual, expected, message) {
            if (actual === expected || String(actual) === String(expected)) {
                log(`${message} (Val: ${actual})`, 'pass');
                this.assertions.push({ message, passed: true });
            } else {
                const err = `Expected ${expected} but got ${actual}`;
                log(`Assertion Failed: ${message} — ${err}`, 'fail');
                this.assertions.push({ message, passed: false, error: err });
                throw new Error(`Assertion failed: ${message} (${err})`);
            }
        }
    }

    // Dynamic runner interface
    const runTest = async (test) => {
        log(`Running: "${test.name}"...`);
        const assertions = [];
        const assertor = new Assertor(test.name, assertions);

        const helpers = {
            getState,
            setState,
            dispatchKeyEvent,
            assert: (cond, msg) => assertor.assert(cond, msg),
            assertEqual: (act, exp, msg) => assertor.assertEqual(act, exp, msg),
            delay,
            log: (msg) => log(msg, 'info')
        };

        try {
            await test.fn(helpers);
            test.status = 'pass';
            test.assertions = assertions;
            log(`Success: "${test.name}" finished cleanly.`, 'pass');
        } catch (e) {
            test.status = 'fail';
            test.assertions = assertions;
            test.error = e.message;
            log(`Failed: "${test.name}" errored: ${e.message}`, 'fail');
        }
    };

    // Pre-registered Core Integration Scenarios
    const registerTest = (testCase) => {
        if (!testCase.name || !testCase.fn) {
            console.error('[Testing Harness] Invalid test case rejected:', testCase);
            return;
        }
        testCase.status = 'idle';
        testCase.assertions = [];
        registry.push(testCase);
        updateUI();
    };

    // --- STANDARD VALIDATION TEST SUITES ---

    // 1. Menu Traversal Audit
    registerTest({
        name: "MainMenu Key Traversal Audit",
        fn: async (h) => {
            // Setup pre-condition
            h.setState('screen', 'main_menu');
            h.setState('cardId', 1);
            h.setState('focusedMenuItem', 'option_4');
            await h.delay(50);

            // Capture starting focus item
            const startingFocus = 'option_4';
            h.log(`Starting focus target: ${startingFocus}`);

            // Simulate DOWN key event
            await h.dispatchKeyEvent('DOWN');
            const secondFocus = h.getState('focusedMenuItem');
            h.assertEqual(secondFocus, 'option_1', "Focus should shift down to option_1 specifically.");

            // Simulate UP key event
            await h.dispatchKeyEvent('UP');
            const finalFocus = h.getState('focusedMenuItem');
            h.assertEqual(finalFocus, 'option_4', "Focus should shift back UP to starting option_4");
        }
    });

    // 2. Settings Toggle Cycle JNI Bridge Audit
    registerTest({
        name: "Settings Toggle Cycle JNI Bridge Audit",
        fn: async (h) => {
            // Setup pre-condition
            h.setState('screen', 'main_menu');
            h.setState('focusedMenuItem', 'option_1'); // ESP Switch option

            const prevEsp = h.getState('espStatus') || 'ON';
            h.log(`Initial ESP JNI status is: ${prevEsp}`);

            // Trigger click
            await h.dispatchKeyEvent('ENTER');
            const nextEsp = h.getState('espStatus');
            h.assert(nextEsp !== prevEsp, `ESP flip should trigger JNI sync state: ${nextEsp}`);

            // Restore ESP status
            await h.dispatchKeyEvent('ENTER');
            h.assertEqual(h.getState('espStatus'), prevEsp, `ESP status returned to original state.`);
        }
    });

    // 3. Display Template Switch & Overlay Clean Audit
    registerTest({
        name: "Display Template Selection & Clean Recovery Audit",
        fn: async (h) => {
            h.setState('screen', 'main_menu');
            h.setState('focusedMenuItem', 'option_4'); // Theme selection

            // Go into display screen
            await h.dispatchKeyEvent('ENTER');
            h.assertEqual(h.getState('screen'), 'display_selection', "Screen transitioned to display settings.");

            // Force dynamic template switch
            h.setState('display', 'Clean');
            h.log('Triggered Clean display template.');

            // Verify app classes applied correctly
            const app = document.getElementById('app');
            if (app) {
                h.assert(app.classList.contains('display-clean'), "App DOM class contains 'display-clean'");
                h.assert(app.classList.contains('warn-is-active'), "App is in warning state overlay wrapper");
            }

            // Clean mode exit check (any steering key should restore Normal)
            await h.dispatchKeyEvent('DOWN');
            h.assertEqual(h.getState('display'), 'Normal', "Clean template exited back to Normal upon keypress.");

            // Go back to main menu
            h.setState('screen', 'main_menu');
        }
    });

    // 4. Aircon Fan and Temp Limits Protection Audit
    registerTest({
        name: "Aircon Fan and Temp Limits Protection Audit",
        fn: async (h) => {
            // Settle inside AC screen
            h.setState('screen', 'aircon');
            h.setState('focusArea', 'fan');

            // Test Fan Lower Boundary
            h.setState('fan', '1');
            await h.dispatchKeyEvent('DOWN'); // goes to 0
            h.assertEqual(h.getState('fan'), '0', "Fan speed reduced to minimum (0)");

            await h.dispatchKeyEvent('DOWN'); // should stay 0
            h.assertEqual(h.getState('fan'), '0', "Fan speed blocked at minimum boundary (0)");

            // Test Fan Upper Boundary
            h.setState('fan', '6');
            await h.dispatchKeyEvent('UP'); // goes to 7
            h.assertEqual(h.getState('fan'), '7', "Fan speed increased to maximum (7)");

            await h.dispatchKeyEvent('UP'); // should stay 7
            h.assertEqual(h.getState('fan'), '7', "Fan speed blocked at maximum boundary (7)");

            // Test Temperature Boundary
            h.setState('focusArea', 'temp');
            h.setState('temp', '17');
            await h.dispatchKeyEvent('DOWN'); // goes to 16
            h.assertEqual(h.getState('temp'), '16', "Temperature reduced to low bound limit (16°C)");

            await h.dispatchKeyEvent('DOWN'); // stays 16
            h.assertEqual(h.getState('temp'), '16', "Temperature bounds prevent dropping under 16°C");

            h.setState('temp', '24');
            await h.dispatchKeyEvent('UP'); // should jump/rise
            h.assert(Number(h.getState('temp')) > 24, "Temperature increases successfully");

            h.setState('screen', 'main_menu');
        }
    });

    // 5. Warning Intercept Suppression Audit
    registerTest({
        name: "Warning Alerts Navigation Suppression Audit",
        fn: async (h) => {
            h.setState('screen', 'main_menu');
            h.setState('cardId', 1);

            // Injected dynamic high-priority CAN warning
            h.setState('warningActive', true);
            h.setState('cardId', 0); // minimizing standard display cards
            h.log("Warning status activated. Cards minimized.");

            const app = document.getElementById('app');
            if (app) {
                h.assert(app.classList.contains('warn-is-active'), "App DOM contains warning wrapper style active.");
            }

            // Restore normal
            h.setState('warningActive', false);
            h.setState('cardId', 1);
            h.log("Warning state neutralized. Menu card restored.");
            await h.delay(100);
            h.assertEqual(h.getState('screen'), 'main_menu', "Returned safely to active Main Menu screen.");
        }
    });

    // 6. Regen Recovery and One-Pedal Navigation Audit
    registerTest({
        name: "Regen Recovery and One-Pedal Navigation Audit",
        fn: async (h) => {
            // Setup pre-condition: navigate to regen
            h.setState('screen', 'regen');
            h.setState('regenMode', 'Normal');
            h.setState('onepedal', false);
            await h.delay(50);

            h.assertEqual(h.getState('regenMode'), 'Normal', "Initial recovery mode should be Normal");

            // 1. Test UP cycle (Normal -> Alto)
            await h.dispatchKeyEvent('UP');
            h.assertEqual(h.getState('regenMode'), 'Alto', "Recovery level shifts UP to Alto");

            // 2. Test DOWN cycle (Alto -> Normal)
            await h.dispatchKeyEvent('DOWN');
            h.assertEqual(h.getState('regenMode'), 'Normal', "Recovery level shifts DOWN to Normal");

            // 3. Test DOWN cycle (Normal -> Baixo)
            await h.dispatchKeyEvent('DOWN');
            h.assertEqual(h.getState('regenMode'), 'Baixo', "Recovery level shifts DOWN to Baixo");

            // 4. Test One-Pedal toggling requires a LONG press; short ENTER is a no-op
            h.assertEqual(h.getState('onepedal'), false, "One-Pedal starts disabled");
            await h.dispatchKeyEvent('ENTER');
            h.assertEqual(h.getState('onepedal'), false, "Short ENTER press does not activate One-Pedal");

            await h.dispatchKeyEvent('ENTER_LONG');
            h.assertEqual(h.getState('onepedal'), true, "Long ENTER press activates One-Pedal");

            await h.dispatchKeyEvent('ENTER_LONG');
            h.assertEqual(h.getState('onepedal'), false, "Long ENTER press deactivates One-Pedal");

            // 5. Return to main menu
            await h.dispatchKeyEvent('BACK');
            h.assertEqual(h.getState('screen'), 'main_menu', "Returned safely to Main Menu screen via BACK key");
        }
    });

    // 7. Theme Settings and Header Visible Suppression Audit
    registerTest({
        name: "Theme Settings and Header Visible Suppression Audit",
        fn: async (h) => {
            h.setState('screen', 'main_menu');
            h.setState('cardId', 1);
            h.log("DEBUG: pref_headerVisible in localStorage: " + window.localStorage.getItem('pref_headerVisible'));
            h.log("DEBUG: headerVisible state is: " + h.getState('headerVisible') + " (type: " + typeof h.getState('headerVisible') + ")");

            // 1. Assert initial state is true
            h.assertEqual(h.getState('headerVisible') !== false, true, "Initially, headerVisible should be true (default)");

            const app = document.getElementById('app');
            if (app) {
                h.assert(!app.classList.contains('hide-header'), "Root #app should NOT have class 'hide-header'");
            }

            // 2. Toggle to false via simulated JNI Push
            h.log("Simulating JNI preference push: app.preferences.headerVisible -> false");
            if (window.onDataChanged) {
                window.onDataChanged("app.preferences.headerVisible", "false");
            }
            await h.delay(100);

            h.assertEqual(h.getState('headerVisible'), false, "State headerVisible should resolve to false");
            if (app) {
                h.assert(app.classList.contains('hide-header'), "Root #app should successfully append 'hide-header' class");
            }

            // 3. Restore back to true via JNI Push
            h.log("Simulating JNI preference push: app.preferences.headerVisible -> true");
            if (window.onDataChanged) {
                window.onDataChanged("app.preferences.headerVisible", "true");
            }
            await h.delay(100);

            h.assertEqual(h.getState('headerVisible'), true, "State headerVisible should return to true");
            if (app) {
                h.assert(!app.classList.contains('hide-header'), "Root #app should remove class 'hide-header'");
            }
        }
    });

    // --- CORE TEST RUNNER EXECUTION ---
    const runTestSuite = async () => {
        if (isRunning) return;
        isRunning = true;

        console.log('[Testing Harness] Starting test suite run...');
        if (uiConsole) uiConsole.innerHTML = '';
        log('Starting Shisuku Automated Theme Test Suite...', 'info');

        // Reset all statuses
        registry.forEach(t => {
            t.status = 'idle';
            t.assertions = [];
            t.error = null;
        });
        updateUI();

        // Sequential run to make visual transitions realistic and observable
        for (let i = 0; i < registry.length; i++) {
            const test = registry[i];
            test.status = 'running';
            updateUI();

            await runTest(test);

            updateUI();
            await delay(400); // Small visually satisfying delay between runs
        }

        isRunning = false;
        log('Testing complete! All cases resolved.', 'info');
        updateUI();

        // Return structured results for programmatically reading agents
        return getTestResults();
    };

    const getTestResults = () => {
        return registry.map(t => ({
            name: t.name,
            status: t.status,
            assertions: t.assertions,
            error: t.error || null
        }));
    };

    // --- PREMIUM GLASSMORPHIC DOM UI PANEL ---
    const injectUI = () => {
        if (document.getElementById('testing-console-harness')) return;

        // Container wrapper for Testing Console
        uiPanel = document.createElement('div');
        uiPanel.id = 'testing-console-harness';

        // Container wrapper for Theme Settings Simulator
        settingsPanel = document.createElement('div');
        settingsPanel.id = 'testing-settings-harness';

        // CSS Style Injections for glassmorphic visual panel
        const styles = document.createElement('style');
        styles.innerHTML = `
            #testing-console-harness,
            #testing-settings-harness,
            #testing-state-harness,
            #testing-console-harness *,
            #testing-settings-harness *,
            #testing-state-harness *,
            #testing-shortcuts-harness * {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif !important;
                box-sizing: border-box;
            }
            #testing-console-harness, #testing-settings-harness, #testing-state-harness, #testing-shortcuts-harness {
                position: fixed;
                bottom: 20px;
                height: 480px;
                max-height: calc(100vh - 40px);
                background: rgba(15, 23, 42, 0.85); /* Modern deep glassmorphic navy slate */
                backdrop-filter: blur(20px);
                -webkit-backdrop-filter: blur(20px);
                border: 1px solid rgba(255, 255, 255, 0.08);
                box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.05);
                border-radius: 16px;
                z-index: 10000;
                color: #e2e8f0;
                overflow: hidden;
                display: flex;
                flex-direction: column;
                transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
            }
            #testing-console-harness {
                right: 20px;
                width: 780px;
            }
            #testing-settings-harness {
                right: 820px;
                width: 340px;
            }
            #testing-state-harness {
                left: 20px;
                width: 480px;
                height: 560px;
            }
            #testing-shortcuts-harness {
                left: 520px;
                width: 340px;
            }
            #testing-console-harness.minimized {
                width: 50px;
                height: 50px;
                border-radius: 25px;
                overflow: hidden;
                bottom: 20px;
                right: 20px;
                background: rgba(15, 23, 42, 0.95);
                border: 1px solid rgba(56, 189, 248, 0.4);
                box-shadow: 0 0 20px rgba(56, 189, 248, 0.25);
                cursor: pointer;
            }
            #testing-settings-harness.minimized {
                width: 50px;
                height: 50px;
                border-radius: 25px;
                overflow: hidden;
                bottom: 20px;
                right: 80px;
                background: rgba(15, 23, 42, 0.95);
                border: 1px solid rgba(56, 189, 248, 0.4);
                box-shadow: 0 0 20px rgba(56, 189, 248, 0.25);
                cursor: pointer;
            }
            #testing-state-harness.minimized {
                width: 50px;
                height: 50px;
                border-radius: 25px;
                overflow: hidden;
                bottom: 20px;
                left: 20px;
                background: rgba(15, 23, 42, 0.95);
                border: 1px solid rgba(168, 85, 247, 0.4);
                box-shadow: 0 0 20px rgba(168, 85, 247, 0.25);
                cursor: pointer;
            }
            #testing-shortcuts-harness.minimized {
                width: 50px;
                height: 50px;
                border-radius: 25px;
                overflow: hidden;
                bottom: 20px;
                left: 520px;
                background: rgba(15, 23, 42, 0.95);
                border: 1px solid rgba(14, 165, 233, 0.4);
                box-shadow: 0 0 20px rgba(14, 165, 233, 0.25);
                cursor: pointer;
            }
            .harness-header {
                padding: 14px 16px;
                background: rgba(255, 255, 255, 0.02);
                border-bottom: 1px solid rgba(255, 255, 255, 0.05);
                display: flex;
                align-items: center;
                justify-content: space-between;
                font-weight: 600;
                letter-spacing: 0.5px;
                font-size: 13px;
                text-transform: uppercase;
                cursor: pointer;
                user-select: none;
            }
            .harness-body {
                padding: 16px;
                display: flex;
                flex-direction: column;
                gap: 12px;
                flex: 1;
                overflow-y: auto;
            }
            .harness-tests-list {
                display: flex;
                flex-direction: column;
                gap: 8px;
            }
            .harness-test-item {
                background: rgba(255, 255, 255, 0.02);
                border: 1px solid rgba(255, 255, 255, 0.04);
                border-radius: 8px;
                padding: 10px 12px;
                display: flex;
                align-items: center;
                justify-content: space-between;
                font-size: 13px;
                transition: all 0.2s;
            }
            .harness-test-item.running {
                border-color: rgba(56, 189, 248, 0.3);
                background: rgba(56, 189, 248, 0.04);
            }
            .harness-status-dot {
                width: 8px;
                height: 8px;
                border-radius: 50%;
                background: #64748b;
                display: inline-block;
                transition: all 0.2s;
            }
            .harness-status-dot.running {
                background: #38bdf8;
                box-shadow: 0 0 8px #38bdf8;
                animation: pulse 1s infinite alternate;
            }
            .harness-status-dot.pass {
                background: #10b981;
                box-shadow: 0 0 8px #10b981;
            }
            .harness-status-dot.fail {
                background: #ef4444;
                box-shadow: 0 0 8px #ef4444;
            }
            .harness-console {
                background: rgba(10, 15, 30, 0.6);
                border: 1px solid rgba(255, 255, 255, 0.04);
                border-radius: 8px;
                padding: 10px;
                flex: 1;
                min-height: 100px;
                overflow-y: auto;
                font-size: 11px;
                display: flex;
                flex-direction: column;
                gap: 4px;
                color: #cbd5e1;
            }
            .harness-controls {
                display: flex;
                gap: 10px;
                margin-top: auto;
            }
            .harness-btn {
                background: linear-gradient(135deg, #38bdf8, #0ea5e9);
                color: #0f172a;
                border: none;
                border-radius: 8px;
                padding: 10px 16px;
                font-size: 12px;
                font-weight: 600;
                cursor: pointer;
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 6px;
                transition: all 0.2s ease-in-out;
                width: 100%;
            }
            .harness-btn:hover {
                filter: brightness(1.1);
                box-shadow: 0 0 15px rgba(56, 189, 248, 0.3);
            }
            .harness-btn:disabled {
                opacity: 0.5;
                cursor: not-allowed;
            }
            .minimized-indicator {
                display: none;
                width: 100%;
                height: 100%;
                align-items: center;
                justify-content: center;
                font-size: 18px;
                font-weight: bold;
                color: #38bdf8;
                user-select: none;
            }
            #testing-console-harness.minimized .minimized-indicator,
            #testing-settings-harness.minimized .minimized-indicator,
            #testing-state-harness.minimized .minimized-indicator,
            #testing-shortcuts-harness.minimized .minimized-indicator {
                display: flex;
            }
            #testing-console-harness.minimized .harness-header,
            #testing-console-harness.minimized .harness-body,
            #testing-settings-harness.minimized .harness-header,
            #testing-settings-harness.minimized .harness-body,
            #testing-state-harness.minimized .harness-header,
            #testing-state-harness.minimized .harness-body,
            #testing-shortcuts-harness.minimized .harness-header,
            #testing-shortcuts-harness.minimized .harness-body {
                display: none;
            }

            /* Mock Projected Display 3 App Style - Removed in favor of direct dev-background maps resizing */
            .mock-projected-app {
                display: none !important;
            }
            .harness-settings-panel {
                background: rgba(255, 255, 255, 0.01);
                border: 1px solid rgba(255, 255, 255, 0.04);
                border-radius: 10px;
                padding: 14px 16px;
                display: flex;
                flex-direction: column;
                gap: 12px;
            }
            .harness-setting-row {
                display: flex;
                align-items: center;
                justify-content: space-between;
                font-size: 13px;
            }
            .harness-switch-label {
                color: #94a3b8;
                font-weight: 500;
            }

            /* Premium Modern Toggle/Switch Styles */
            .harness-switch {
                position: relative;
                display: inline-block;
                width: 36px;
                height: 20px;
            }
            .harness-switch input {
                opacity: 0;
                width: 0;
                height: 0;
            }
            .harness-slider {
                position: absolute;
                cursor: pointer;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background-color: rgba(255, 255, 255, 0.1);
                border: 1px solid rgba(255, 255, 255, 0.15);
                transition: .2s ease-in-out;
                border-radius: 20px;
            }
            .harness-slider:before {
                position: absolute;
                content: "";
                height: 12px;
                width: 12px;
                left: 3px;
                bottom: 3px;
                background-color: #94a3b8;
                transition: .2s ease-in-out;
                border-radius: 50%;
            }
            .harness-switch input:checked + .harness-slider {
                background-color: #38bdf8;
                border-color: #38bdf8;
            }
            .harness-switch input:checked + .harness-slider:before {
                transform: translateX(16px);
                background-color: #0f172a;
            }
        `;
        document.head.appendChild(styles);

        // Core UI Structure - Console Panel
        uiPanel.innerHTML = `
            <div class="minimized-indicator" title="Toggled via key [T]">🧪</div>
            <div class="harness-header" id="harness-header-bar">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span style="display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #10b981; box-shadow: 0 0 6px #10b981;"></span>
                    Agent Testing Console
                </div>
                <div style="font-size: 11px; opacity: 0.5;">[T] Toggle</div>
            </div>
            <div class="harness-body" style="flex-direction: row; gap: 16px; overflow-y: hidden; height: 100%;">
                <!-- Left Column: Test Plan and Controls -->
                <div style="flex: 1; display: flex; flex-direction: column; gap: 12px; height: 100%; overflow-y: hidden;">
                    <div class="harness-tests-list" id="harness-list-container" style="flex: 1; overflow-y: auto;"></div>
                    <div class="harness-controls" style="margin-top: auto; padding-top: 8px;">
                        <button class="harness-btn" id="harness-run-btn" style="width: 100%; justify-content: center; display: flex; align-items: center;">
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" style="margin-right: 4px;"><path d="M8 5v14l11-7z"/></svg>
                            Run Test Suite
                        </button>
                    </div>
                </div>
                <!-- Right Column: Console Output -->
                <div style="flex: 1.2; display: flex; flex-direction: column; height: 100%;">
                    <div class="harness-console" id="harness-console-output" style="flex: 1; height: 100%; max-height: 100%;">
                        <div style="opacity: 0.4; font-style: italic; text-align: center; margin-top: 140px; font-size: 12px;">Harness ready. Run tests to see output logs.</div>
                    </div>
                </div>
            </div>
        `;

        // Core UI Structure - Settings Panel
        settingsPanel.innerHTML = `
            <div class="minimized-indicator" title="Toggled via key [T]">⚙️</div>
            <div class="harness-header" id="harness-settings-header-bar">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span style="display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #38bdf8; box-shadow: 0 0 6px #38bdf8;"></span>
                    Theme Settings Simulator
                </div>
            </div>
            <div class="harness-body">
                <div class="harness-settings-panel" id="dynamic-harness-settings-container">
                    <div style="font-size: 11px; font-weight: 600; text-transform: uppercase; color: #38bdf8; margin-bottom: 8px; letter-spacing: 0.5px;">
                        Configurações do Tema (Simulador)
                    </div>
                    <div style="font-size: 12px; color: #94a3b8; font-style: italic;" id="settings-loading-indicator">Carregando configurações...</div>
                </div>
                <div style="margin-top: auto; padding: 12px; border-radius: 8px; background: rgba(56, 189, 248, 0.04); border: 1px solid rgba(56, 189, 248, 0.08); font-size: 12px; color: #94a3b8; line-height: 1.4;">
                    <div style="font-weight: 600; color: #38bdf8; margin-bottom: 4px;">Info</div>
                    Alterar opções neste simulador emite eventos JNI Preference Push em tempo real, exatamente como no painel físico do Android.
                </div>
            </div>
        `;

        // Container wrapper for State Management Simulator
        statePanel = document.createElement('div');
        statePanel.id = 'testing-state-harness';

        // Core UI Structure - State Panel
        statePanel.innerHTML = `
            <div class="minimized-indicator" title="Toggled via key [T]">📊</div>
            <div class="harness-header" id="harness-state-header-bar">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span style="display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #a855f7; box-shadow: 0 0 6px #a855f7;"></span>
                    State Management Simulator
                </div>
            </div>
            <div class="harness-body" style="overflow-y: auto;">
                <div style="font-size: 11px; font-weight: 600; text-transform: uppercase; color: #a855f7; margin-bottom: 12px; letter-spacing: 0.5px; display: flex; justify-content: space-between; align-items: center;">
                    <span>Telemetria e Estados JNI</span>
                    <button id="harness-sim-toggle" style="background: rgba(168, 85, 247, 0.15); border: 1px solid rgba(168, 85, 247, 0.3); color: #c084fc; padding: 4px 10px; font-size: 12px; border-radius: 14px; font-weight: bold; cursor: pointer; display: flex; align-items: center; gap: 4px; transition: all 0.2s; outline: none; line-height: 1.2;">
                        <span id="harness-sim-icon">⏸️</span> <span id="harness-sim-text">Pause Sim</span>
                    </button>
                </div>

                <div style="display: flex; gap: 16px; flex: 1;">
                    <!-- Column 1: Core Telemetry -->
                    <div style="flex: 1; display: flex; flex-direction: column; gap: 10px;">
                        <div style="font-size: 10px; font-weight: 700; color: #64748b; border-bottom: 1px solid rgba(255,255,255,0.05); padding-bottom: 4px; text-transform: uppercase;">Telemetria Básica</div>

                        <!-- Speed -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>Velocidade (carSpeed)</span>
                                <span id="val-carSpeed" style="color: #a855f7; font-weight: bold;">0 km/h</span>
                            </div>
                            <input type="range" id="state-range-carSpeed" min="0" max="220" value="0" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>

                        <!-- RPM -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>Engine RPM (engineRPM)</span>
                                <span id="val-engineRPM" style="color: #a855f7; font-weight: bold;">0 RPM</span>
                            </div>
                            <input type="range" id="state-range-engineRPM" min="0" max="8000" value="0" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>

                        <!-- EV Power kW -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>Potência / regeneração (evPowerKw)</span>
                                <span id="val-evPowerKw" style="color: #a855f7; font-weight: bold;">0 kW</span>
                            </div>
                            <input type="range" id="state-range-evPowerKw" min="-100" max="300" value="0" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>

                        <!-- EV Power % -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>EV Power % (evPowerFactor)</span>
                                <span id="val-evPowerFactor" style="color: #a855f7; font-weight: bold;">0%</span>
                            </div>
                            <input type="range" id="state-range-evPowerFactor" min="-200" max="200" value="0" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>

                        <!-- Odometer -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>Odometer (odometer)</span>
                            </div>
                            <input type="number" id="state-num-odometer" value="11450" style="background: rgba(15, 23, 42, 0.4); border: 1px solid rgba(255,255,255,0.08); padding: 4px 8px; border-radius: 6px; font-size: 11px; color: #fff; width: 100%;">
                        </div>

                        <!-- Fuel Percent -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>Combustível (fuelPercent)</span>
                                <span id="val-fuelPercent" style="color: #a855f7; font-weight: bold;">50%</span>
                            </div>
                            <input type="range" id="state-range-fuelPercent" min="0" max="100" value="50" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>

                        <!-- Battery Percent -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>Bateria (batteryPercent)</span>
                                <span id="val-batteryPercent" style="color: #a855f7; font-weight: bold;">60%</span>
                            </div>
                            <input type="range" id="state-range-batteryPercent" min="0" max="100" value="60" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>

                        <!-- Inside Temp -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>Temp Interna (inside_temp)</span>
                                <span id="val-inside_temp" style="color: #a855f7; font-weight: bold;">32°C</span>
                            </div>
                            <input type="range" id="state-range-inside_temp" min="-10" max="45" value="32" step="0.5" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>

                        <!-- Outside Temp -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>Temp Externa (outside_temp)</span>
                                <span id="val-outside_temp" style="color: #a855f7; font-weight: bold;">28°C</span>
                            </div>
                            <input type="range" id="state-range-outside_temp" min="-10" max="45" value="28" step="0.5" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>
                    </div>

                    <!-- Column 2: Status & Modes -->
                    <div style="flex: 1; display: flex; flex-direction: column; gap: 10px;">
                        <div style="font-size: 10px; font-weight: 700; color: #64748b; border-bottom: 1px solid rgba(255,255,255,0.05); padding-bottom: 4px; text-transform: uppercase;">Modos e Estados</div>

                        <!-- Card ID -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Card ID (cardId)</span>
                            <div id="state-pill-cardId" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="0" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">Hide (0)</button>
                                <button data-val="1" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">Menu (1)</button>
                                <button data-val="3" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">AC (3)</button>
                            </div>
                        </div>

                        <!-- Gear -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Marcha (gearState)</span>
                            <div id="state-pill-gearState" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="P" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 10px; border-radius: 10px; cursor: pointer; font-weight: bold;">P</button>
                                <button data-val="R" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 10px; border-radius: 10px; cursor: pointer; font-weight: bold;">R</button>
                                <button data-val="N" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 10px; border-radius: 10px; cursor: pointer; font-weight: bold;">N</button>
                                <button data-val="D" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 10px; border-radius: 10px; cursor: pointer; font-weight: bold;">D</button>
                            </div>
                        </div>

                        <!-- Driving Mode -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Modo Direção (drivingMode)</span>
                            <div id="state-pill-drivingMode" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="Normal" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">Norm</button>
                                <button data-val="Eco" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">Eco</button>
                                <button data-val="Sport" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">Sport</button>
                            </div>
                        </div>

                        <!-- EV Mode -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>EV Mode (evMode)</span>
                            <div id="state-pill-evMode" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="HEV" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">HEV</button>
                                <button data-val="EVP" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">EVP</button>
                                <button data-val="EV" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">EV</button>
                            </div>
                        </div>

                        <!-- Steer Mode -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Direção (steerMode)</span>
                            <div id="state-pill-steerMode" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="Conforto" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">Conf</button>
                                <button data-val="Normal" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">Norm</button>
                                <button data-val="Esportivo" style="background: transparent; border: none; color: #94a3b8; padding: 2px 6px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">Espt</button>
                            </div>
                        </div>

                        <!-- Clock Time -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Horário (clockTime)</span>
                            <input type="text" id="state-txt-clockTime" value="19:46" style="background: rgba(15, 23, 42, 0.4); border: 1px solid rgba(255,255,255,0.08); padding: 4px 8px; border-radius: 6px; font-size: 11px; color: #fff; width: 60px; text-align: center;">
                        </div>

                        <!-- ESP Status -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Controle Estabilidade (espStatus)</span>
                            <div id="state-pill-espStatus" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="ON" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">ON</button>
                                <button data-val="OFF" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">OFF</button>
                            </div>
                        </div>

                        <!-- Odometer Visible Toggle -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Odometer Visível (enableOdometer)</span>
                            <label class="harness-switch">
                                <input type="checkbox" id="state-switch-enableOdometer" checked>
                                <span class="harness-slider"></span>
                            </label>
                        </div>

                        <!-- Revision Visible Toggle -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Aviso Revisão (enableRevisionWarning)</span>
                            <label class="harness-switch">
                                <input type="checkbox" id="state-switch-enableRevisionWarning" checked>
                                <span class="harness-slider"></span>
                            </label>
                        </div>

                        <!-- Next Revision Km -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Próx Revisão Km (nextRevisionKm)</span>
                            <input type="number" id="state-num-nextRevisionKm" value="12000" style="background: rgba(15, 23, 42, 0.4); border: 1px solid rgba(255,255,255,0.08); padding: 4px 8px; border-radius: 6px; font-size: 11px; color: #fff; width: 70px;">
                        </div>

                        <!-- Next Revision Date -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>Próx Revisão Data (nextRevisionDate)</span>
                            <input type="date" id="state-date-nextRevisionDate" style="background: rgba(15, 23, 42, 0.4); border: 1px solid rgba(255,255,255,0.08); padding: 4px 6px; border-radius: 6px; font-size: 11px; color: #fff; width: 115px; outline: none; border-color: rgba(255,255,255,0.15);">
                        </div>

                        <div style="font-size: 10px; font-weight: 700; color: #64748b; border-bottom: 1px solid rgba(255,255,255,0.05); padding-bottom: 4px; margin-top: 8px; text-transform: uppercase;">AC / HVAC</div>

                        <!-- AC Power -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>AC Power (power)</span>
                            <div id="state-pill-power" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="0" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">OFF</button>
                                <button data-val="1" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">ON</button>
                            </div>
                        </div>

                        <!-- AUTO -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>AUTO (auto)</span>
                            <div id="state-pill-auto" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="0" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">OFF</button>
                                <button data-val="1" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">ON</button>
                            </div>
                        </div>

                        <!-- MAX AUTO -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>MAX AUTO (maxauto)</span>
                            <div id="state-pill-maxauto" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="0" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">OFF</button>
                                <button data-val="1" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">ON</button>
                            </div>
                        </div>

                        <!-- Recycle -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>RECYCLE (recycle)</span>
                            <div id="state-pill-recycle" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="0" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">OFF</button>
                                <button data-val="1" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">ON</button>
                            </div>
                        </div>

                        <!-- Fan -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>Fan (fan)</span>
                                <span id="val-fan" style="color: #a855f7; font-weight: bold;">0</span>
                            </div>
                            <input type="range" id="state-range-fan" min="0" max="7" value="0" step="1" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>

                        <!-- HVAC Temp -->
                        <div style="display: flex; flex-direction: column; gap: 2px;">
                            <div style="display: flex; justify-content: space-between; font-size: 11px;">
                                <span>AC Temp (temp)</span>
                                <span id="val-temp" style="color: #a855f7; font-weight: bold;">22°C</span>
                            </div>
                            <input type="range" id="state-range-temp" min="16" max="32" value="22" step="0.5" style="width: 100%; height: 4px; border-radius: 2px; outline: none; cursor: pointer; accent-color: #a855f7;">
                        </div>

                        <!-- Focus Area -->
                        <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px;">
                            <span>AC Focus (focusArea)</span>
                            <div id="state-pill-focusArea" style="display: flex; gap: 2px; background: rgba(15,23,42,0.4); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 2px;">
                                <button data-val="fan" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">FAN</button>
                                <button data-val="temp" style="background: transparent; border: none; color: #94a3b8; padding: 2px 8px; font-size: 9px; border-radius: 10px; cursor: pointer; font-weight: bold;">TEMP</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        const setupStateSync = () => {
            const bindRange = (key, unit = '') => {
                const slider = document.getElementById(`state-range-${key}`);
                const display = document.getElementById(`val-${key}`);

                if (slider && display) {
                    slider.addEventListener('input', (e) => {
                        const val = e.target.value;
                        display.textContent = `${val}${unit}`;
                        stateManager.set(key, Number(val));
                    });

                    stateManager.subscribe(key, (val) => {
                        if (val === undefined || val === null) return;
                        slider.value = val;
                        const displayVal = key === 'engineRPM' ? Math.round(Number(val)) : val;
                        display.textContent = `${displayVal}${unit}`;
                    });
                }
            };

            const bindPill = (key, { numeric = false } = {}) => {
                const container = document.getElementById(`state-pill-${key}`);
                if (!container) return;

                const buttons = container.querySelectorAll('button');
                buttons.forEach(btn => {
                    btn.addEventListener('click', () => {
                        const raw = btn.getAttribute('data-val');
                        const val = numeric ? Number(raw) : raw;
                        stateManager.set(key, val);
                    });
                });

                const paint = (val) => {
                    if (val === undefined || val === null) return;
                    const str = String(val);
                    buttons.forEach(btn => {
                        if (btn.getAttribute('data-val') === str) {
                            btn.style.background = 'rgba(168, 85, 247, 0.25)';
                            btn.style.color = '#c084fc';
                            btn.style.boxShadow = '0 0 6px rgba(168, 85, 247, 0.3)';
                        } else {
                            btn.style.background = 'transparent';
                            btn.style.color = '#94a3b8';
                            btn.style.boxShadow = 'none';
                        }
                    });
                };
                stateManager.subscribe(key, paint);
                paint(stateManager.get(key));
            };

            bindRange('carSpeed', ' km/h');
            bindRange('engineRPM', ' RPM');
            bindRange('evPowerKw', ' kW');
            bindRange('evPowerFactor', '%');
            bindRange('fuelPercent', '%');
            bindRange('batteryPercent', '%');
            bindRange('inside_temp', '°C');
            bindRange('outside_temp', '°C');
            bindRange('fan', '');
            bindRange('temp', '°C');

            const odoInput = document.getElementById('state-num-odometer');
            if (odoInput) {
                odoInput.addEventListener('input', (e) => {
                    const val = Number(e.target.value);
                    stateManager.set('odometer', val);
                });
                stateManager.subscribe('odometer', (val) => {
                    if (val !== undefined && val !== null) {
                        odoInput.value = val;
                    }
                });
            }

            const clockInput = document.getElementById('state-txt-clockTime');
            if (clockInput) {
                clockInput.addEventListener('input', (e) => {
                    stateManager.set('clockTime', e.target.value);
                });
                stateManager.subscribe('clockTime', (val) => {
                    if (val !== undefined && val !== null) {
                        clockInput.value = val;
                    }
                });
            }

            // Bind Switch Helper
            const bindSwitch = (id, key) => {
                const sw = document.getElementById(id);
                if (sw) {
                    sw.addEventListener('change', (e) => {
                        stateManager.set(key, e.target.checked);
                    });
                    stateManager.subscribe(key, (val) => {
                        sw.checked = val === true || val === 'true';
                    });
                }
            };

            bindSwitch('state-switch-enableOdometer', 'enableOdometer');
            bindSwitch('state-switch-enableRevisionWarning', 'enableRevisionWarning');

            // Bind Next Revision Km
            const nextRevKmInput = document.getElementById('state-num-nextRevisionKm');
            if (nextRevKmInput) {
                nextRevKmInput.addEventListener('input', (e) => {
                    stateManager.set('nextRevisionKm', Number(e.target.value));
                });
                stateManager.subscribe('nextRevisionKm', (val) => {
                    if (val !== undefined && val !== null) {
                        nextRevKmInput.value = val;
                    }
                });
            }

            // Bind Next Revision Date Picker (user friendly conversion to timestamp)
            const revisionDateInput = document.getElementById('state-date-nextRevisionDate');
            if (revisionDateInput) {
                revisionDateInput.addEventListener('change', (e) => {
                    const dateVal = e.target.value; // YYYY-MM-DD
                    if (dateVal) {
                        const timestamp = new Date(dateVal + 'T00:00:00').getTime();
                        stateManager.set('nextRevisionDate', timestamp);
                    }
                });
                stateManager.subscribe('nextRevisionDate', (val) => {
                    if (val) {
                        const date = new Date(Number(val));
                        if (!isNaN(date.getTime())) {
                            const yyyy = date.getFullYear();
                            const mm = String(date.getMonth() + 1).padStart(2, '0');
                            const dd = String(date.getDate()).padStart(2, '0');
                            revisionDateInput.value = `${yyyy}-${mm}-${dd}`;
                        }
                    }
                });
            }

            bindPill('gearState');
            bindPill('drivingMode');
            bindPill('evMode');
            bindPill('steerMode');
            bindPill('espStatus');
            // AC / HVAC (numeric 0/1 flags from state.js)
            bindPill('power', { numeric: true });
            bindPill('auto', { numeric: true });
            bindPill('maxauto', { numeric: true });
            bindPill('recycle', { numeric: true });
            bindPill('focusArea');

            const dashSwitch = document.getElementById('sim-control-appInDash');
            if (dashSwitch) {
                dashSwitch.addEventListener('change', (e) => {
                    const checked = e.target.checked;
                    stateManager.set('appInDash', checked);
                });
                stateManager.subscribe('appInDash', (val) => {
                    dashSwitch.checked = val === true || val === 'true' || val === 'left' || val === 'right';
                });
            }

            const navSwitch = document.getElementById('sim-control-carPlayInDash');
            if (navSwitch) {
                navSwitch.addEventListener('change', (e) => {
                    const checked = e.target.checked;
                    stateManager.set('carPlayInDash', checked);
                    stateManager.set('projectionMirrorInDash', checked);
                });
                stateManager.subscribe('carPlayInDash', (val) => {
                    navSwitch.checked = val === true || val === 'true';
                });
                stateManager.subscribe('projectionMirrorInDash', (val) => {
                    navSwitch.checked = val === true || val === 'true' || stateManager.get('carPlayInDash') === true;
                });
            }

            // Toggle Simulation loop button
            const simToggleBtn = document.getElementById('harness-sim-toggle');
            if (simToggleBtn) {
                simToggleBtn.addEventListener('click', () => {
                    console.log('[Simulator Click] Pause/Start SIM clicked. isSimRunning:', isSimRunning);
                    if (isSimRunning) {
                        stopSimulation();
                    } else {
                        startSimulation();
                    }
                });

                // Auto-start simulation on launch
                startSimulation();
            }

            // Bind steering key buttons
            const shortcutBtns = shortcutsPanel.querySelectorAll('.harness-shortcut-btn');
            shortcutBtns.forEach(btn => {
                btn.addEventListener('click', () => {
                    const key = btn.getAttribute('data-key');
                    console.log(`[Simulator Button] Clicking steering key: ${key}`);
                    dispatchKeyEvent(key);
                });
            });

            // Bind action buttons
            const actionBtns = shortcutsPanel.querySelectorAll('.harness-action-btn');
            actionBtns.forEach(btn => {
                btn.addEventListener('click', () => {
                    const action = btn.getAttribute('data-action');
                    console.log(`[Simulator Button] Executing action: ${action}`);

                    const mgr = window.__SIMULATION_STATE_MANAGER__ || stateManager;

                    // These stand in for the backend, which owns warningActive/bsd* and pushes
                    // them as typed booleans. updateWarning() only records a raw value now —
                    // the theme derives nothing from it.
                    if (action === 'toggle-warn') {
                        const currentVal = mgr.get('warningActive');
                        mgr.set('warningActive', !currentVal);
                        mgr.set('cardId', !currentVal ? 0 : 1);
                    } else if (action === 'toggle-bsd-l') {
                        mgr.set('bsdLeft', !mgr.get('bsdLeft'));
                    } else if (action === 'toggle-bsd-r') {
                        mgr.set('bsdRight', !mgr.get('bsdRight'));
                    } else if (action === 'toggle-dash') {
                        const options = [false, true, 'left', 'right'];
                        const currentVal = mgr.get('appInDash');
                        let idx = options.indexOf(currentVal);
                        if (idx === -1) idx = 0;
                        const nextVal = options[(idx + 1) % options.length];
                        mgr.set('appInDash', nextVal);
                    } else if (action === 'toggle-odo') {
                        const currentVal = mgr.get('enableOdometer');
                        mgr.set('enableOdometer', !currentVal);
                    } else if (action === 'toggle-revision') {
                        const currentVal = mgr.get('enableRevisionWarning');
                        mgr.set('enableRevisionWarning', !currentVal);
                    } else if (action === 'cycle-maint') {
                        const modes = ['km', 'date', 'none'];
                        if (!window.maintenanceMode) window.maintenanceMode = 'km';
                        const idx = modes.indexOf(window.maintenanceMode);
                        window.maintenanceMode = modes[(idx + 1) % modes.length];

                        if (window.maintenanceMode === 'none') {
                            mgr.set('enableRevisionWarning', false);
                            mgr.set('nextRevisionKm', 999999);
                            mgr.set('nextRevisionDate', 0);
                        } else if (window.maintenanceMode === 'km') {
                            mgr.set('enableRevisionWarning', true);
                            mgr.set('nextRevisionKm', 12000);
                            mgr.set('nextRevisionDate', Date.now() + 60 * 24 * 60 * 60 * 1000);
                        } else if (window.maintenanceMode === 'date') {
                            mgr.set('enableRevisionWarning', true);
                            mgr.set('nextRevisionKm', 20000);
                            mgr.set('nextRevisionDate', Date.now() + 15 * 24 * 60 * 60 * 1000);
                        }
                    } else if (action === 'toggle-collapse') {
                        toggleConsoleMinimize();
                        toggleSettingsMinimize();
                        toggleStateMinimize();
                        toggleShortcutsMinimize();
                    }
                });
            });

            // Custom binding for cardId to keep it numeric
            const cardIdContainer = document.getElementById('state-pill-cardId');
            if (cardIdContainer) {
                const buttons = cardIdContainer.querySelectorAll('button');
                buttons.forEach(btn => {
                    btn.addEventListener('click', () => {
                        const val = Number(btn.getAttribute('data-val'));
                        stateManager.set('cardId', val);
                    });
                });

                stateManager.subscribe('cardId', (val) => {
                    if (val === undefined || val === null) return;
                    buttons.forEach(btn => {
                        if (Number(btn.getAttribute('data-val')) === Number(val)) {
                            btn.style.background = 'rgba(168, 85, 247, 0.25)';
                            btn.style.color = '#c084fc';
                            btn.style.boxShadow = '0 0 6px rgba(168, 85, 247, 0.3)';
                        } else {
                            btn.style.background = 'transparent';
                            btn.style.color = '#94a3b8';
                            btn.style.boxShadow = 'none';
                        }
                    });
                });
            }
        };

        // Container wrapper for Shortcuts Simulator
        const shortcutsPanel = document.createElement('div');
        shortcutsPanel.id = 'testing-shortcuts-harness';

        shortcutsPanel.innerHTML = `
            <div class="minimized-indicator" title="Toggled via key [T]">🎮</div>
            <div class="harness-header" id="harness-shortcuts-header-bar">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span style="display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #0ea5e9; box-shadow: 0 0 6px #0ea5e9;"></span>
                    Atalhos e Controles
                </div>
            </div>
            <div class="harness-body" style="overflow-y: auto; gap: 14px;">
                <!-- Section 1: Steering Wheel Keys -->
                <div style="display: flex; flex-direction: column; gap: 8px;">
                    <div style="font-size: 10px; font-weight: 700; color: #64748b; border-bottom: 1px solid rgba(255,255,255,0.05); padding-bottom: 4px; text-transform: uppercase; letter-spacing: 0.5px;">Teclas do Volante (Emulação)</div>

                    <!-- Steering Pad Layout Grid -->
                    <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; justify-items: center; margin: 4px 0;">
                        <div></div>
                        <button class="harness-btn harness-shortcut-btn" data-key="UP" style="background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); color: #fff; padding: 6px; border-radius: 8px; cursor: pointer; font-size: 11px; width: 100%; font-weight: bold;">🔼 UP</button>
                        <div></div>

                        <button class="harness-btn harness-shortcut-btn" data-key="LEFT" style="background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); color: #fff; padding: 6px; border-radius: 8px; cursor: pointer; font-size: 11px; width: 100%; font-weight: bold;">◀️ LEFT</button>
                        <button class="harness-btn harness-shortcut-btn" data-key="ENTER" style="background: rgba(56, 189, 248, 0.15); border: 1px solid rgba(56, 189, 248, 0.3); color: #38bdf8; padding: 6px; border-radius: 8px; cursor: pointer; font-size: 11px; width: 100%; font-weight: bold;">🆗 ENTER</button>
                        <button class="harness-btn harness-shortcut-btn" data-key="RIGHT" style="background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); color: #fff; padding: 6px; border-radius: 8px; cursor: pointer; font-size: 11px; width: 100%; font-weight: bold;">RIGHT ▶️</button>

                        <div></div>
                        <button class="harness-btn harness-shortcut-btn" data-key="DOWN" style="background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); color: #fff; padding: 6px; border-radius: 8px; cursor: pointer; font-size: 11px; width: 100%; font-weight: bold;">🔽 DOWN</button>
                        <button class="harness-btn harness-shortcut-btn" data-key="BACK" style="background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.25); color: #f87171; padding: 6px; border-radius: 8px; cursor: pointer; font-size: 11px; width: 100%; font-weight: bold;">↩️ BACK</button>
                    </div>
                </div>

                <!-- Section 2: Keyboard Trigger Actions -->
                <div style="display: flex; flex-direction: column; gap: 6px;">
                    <div style="font-size: 10px; font-weight: 700; color: #64748b; border-bottom: 1px solid rgba(255,255,255,0.05); padding-bottom: 4px; text-transform: uppercase; letter-spacing: 0.5px;">Ações Rápidas do Simulador</div>

                    <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px; margin-top: 4px;">
                        <button class="harness-btn harness-action-btn" data-action="toggle-bsd-l" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.06); padding: 6px; border-radius: 6px; color: #e2e8f0; font-size: 11px; cursor: pointer; text-align: left; font-weight: 500;">⬅️ BSD Left (L)</button>
                        <button class="harness-btn harness-action-btn" data-action="toggle-bsd-r" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.06); padding: 6px; border-radius: 6px; color: #e2e8f0; font-size: 11px; cursor: pointer; text-align: left; font-weight: 500;">➡️ BSD Right (R)</button>
                        <button class="harness-btn harness-action-btn" data-action="toggle-odo" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.06); padding: 6px; border-radius: 6px; color: #e2e8f0; font-size: 11px; cursor: pointer; text-align: left; font-weight: 500;">💡 Odometer (6)</button>
                        <button class="harness-btn harness-action-btn" data-action="cycle-maint" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.06); padding: 6px; border-radius: 6px; color: #e2e8f0; font-size: 11px; cursor: pointer; text-align: left; font-weight: 500;">📆 Revision (M)</button>
                        <button class="harness-btn harness-action-btn" data-action="toggle-warn" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.06); padding: 6px; border-radius: 6px; color: #e2e8f0; font-size: 11px; cursor: pointer; text-align: left; font-weight: 500; grid-column: span 2;">⚠️ Warning (W)</button>
                    </div>
                </div>

                <!-- Section 3: App in Dash & Navigation Toggles -->
                <div style="display: flex; flex-direction: column; gap: 6px; margin-top: 4px;">
                    <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px; padding: 8px; border-radius: 8px; background: rgba(14, 165, 233, 0.05); border: 1px solid rgba(14, 165, 233, 0.15);">
                        <div style="display: flex; flex-direction: column;">
                            <span style="font-weight: bold; color: #38bdf8;">App no Painel (appInDash)</span>
                            <span style="font-size: 9px; color: #94a3b8;">Simular outro app (ex: YouTube, Spotify) no cluster</span>
                        </div>
                        <label class="harness-switch">
                            <input type="checkbox" id="sim-control-appInDash">
                            <span class="harness-slider" style="accent-color: #0ea5e9;"></span>
                        </label>
                    </div>

                    <div style="display: flex; justify-content: space-between; align-items: center; font-size: 11px; padding: 8px; border-radius: 8px; background: rgba(16, 185, 129, 0.05); border: 1px solid rgba(16, 185, 129, 0.15);">
                        <div style="display: flex; flex-direction: column;">
                            <span style="font-weight: bold; color: #34d399;">Navegação no Painel (carPlayInDash)</span>
                            <span style="font-size: 9px; color: #94a3b8;">Simular mapa/navegação (CarPlay / AA) no cluster</span>
                        </div>
                        <label class="harness-switch">
                            <input type="checkbox" id="sim-control-carPlayInDash">
                            <span class="harness-slider" style="accent-color: #10b981;"></span>
                        </label>
                    </div>
                </div>
            </div>
        `;

        // Mock projected app container removed as map resizing is handled natively on .dev-background

        document.body.appendChild(uiPanel);
        document.body.appendChild(settingsPanel);
        document.body.appendChild(statePanel);
        document.body.appendChild(shortcutsPanel);
        setupStateSync();
        uiConsole = document.getElementById('harness-console-output');

        // Dynamic theme.xml configurations loader and renderer
        const loadThemeXmlAndBuildUi = async () => {
            const container = document.getElementById('dynamic-harness-settings-container');
            let configsList = [];

            try {
                const response = await fetch('./theme.xml');
                if (!response.ok) throw new Error('Failed to load theme.xml');
                const xmlText = await response.text();

                const parser = new DOMParser();
                const xmlDoc = parser.parseFromString(xmlText, "text/xml");
                const configElements = xmlDoc.getElementsByTagName("configuration");

                for (let i = 0; i < configElements.length; i++) {
                    const el = configElements[i];
                    const getTagVal = (tagName, fallback = "") => {
                        const tags = el.getElementsByTagName(tagName);
                        return tags.length > 0 ? tags[0].textContent.trim() : fallback;
                    };

                    const id = getTagVal("id");
                    const label = getTagVal("label");
                    const type = getTagVal("type", "text").toLowerCase();
                    const defaultValue = getTagVal("default");
                    const stateVariable = getTagVal("stateVariable");
                    const optionsVal = getTagVal("options");

                    if (id && stateVariable) {
                        const options = optionsVal ? optionsVal.split(",").map(o => o.trim()) : [];
                        configsList.push({ id, label, type, defaultValue, stateVariable, options });
                    }
                }
            } catch (err) {
                console.warn('[Testing Harness] Could not load/parse theme.xml, no configuration UI will be built:', err.message);
                configsList = [];
            }

            // Clear loading indicator
            const loading = document.getElementById('settings-loading-indicator');
            if (loading) loading.remove();

            // Build elements
            configsList.forEach(config => {
                const row = document.createElement('div');
                row.className = 'harness-setting-row';
                row.style.marginTop = '10px';
                row.style.display = 'flex';
                row.style.justifyContent = 'space-between';
                row.style.alignItems = 'center';

                const labelSpan = document.createElement('span');
                labelSpan.className = 'harness-switch-label';
                labelSpan.textContent = `${config.label} (${config.stateVariable})`;
                row.appendChild(labelSpan);

                let controlNode = null;
                const initialVal = getState(config.stateVariable);
                const currentVal = initialVal !== undefined ? initialVal : config.defaultValue;

                // Shared by every control type below. This used to be declared inside the
                // boolean branch, so the combo pills and the select referenced a name that
                // was not in scope and threw on change - every dropdown setting was dead.
                const saveConfigVal = (val) => {
                    console.log(`[Simulator Setting] Saved ${config.stateVariable} (${config.id}) -> ${val}`);
                    const strVal = String(val);
                    if (window.Android && typeof window.Android.savePreference === 'function') {
                        window.Android.savePreference(config.stateVariable, strVal);
                        window.Android.savePreference(`app.preferences.${config.stateVariable}`, strVal);
                        window.Android.savePreference(config.id, strVal);
                        window.Android.savePreference(`app.preferences.${config.id}`, strVal);
                    }
                    if (window.onDataChanged) {
                        window.onDataChanged(`app.preferences.${config.stateVariable}`, strVal);
                        window.onDataChanged(`app.preferences.${config.id}`, strVal);
                        window.onDataChanged(config.stateVariable, strVal);
                        window.onDataChanged(config.id, strVal);
                    }
                    if (stateManager && typeof stateManager.setState === 'function') {
                        stateManager.setState(config.stateVariable, val);
                        stateManager.setState(config.id, val);
                    }
                };

                if (config.type === 'boolean') {
                    const label = document.createElement('label');
                    label.className = 'harness-switch';

                    const input = document.createElement('input');
                    input.type = 'checkbox';
                    input.id = `sim-control-${config.id}`;
                    input.checked = currentVal === true || currentVal === 'true';

                    const slider = document.createElement('span');
                    slider.className = 'harness-slider';

                    label.appendChild(input);
                    label.appendChild(slider);
                    row.appendChild(label);
                    controlNode = input;

                    input.addEventListener('change', (e) => {
                        saveConfigVal(e.target.checked);
                    });
                } else if (config.type === 'combo') {
                    if (config.options.length === 2) {
                        const pillContainer = document.createElement('div');
                        pillContainer.id = `sim-control-${config.id}`;
                        pillContainer.style.cssText = 'display: flex; background: rgba(15, 23, 42, 0.6); border: 1px solid rgba(56, 189, 248, 0.2); border-radius: 20px; padding: 2px; align-items: center; position: relative; gap: 2px;';

                        const btn1 = document.createElement('button');
                        btn1.textContent = config.options[0];
                        btn1.style.cssText = 'background: transparent; border: none; color: #94a3b8; padding: 4px 12px; font-size: 11px; font-weight: 600; border-radius: 18px; cursor: pointer; transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1); outline: none; white-space: nowrap; line-height: 1.2;';

                        const btn2 = document.createElement('button');
                        btn2.textContent = config.options[1];
                        btn2.style.cssText = 'background: transparent; border: none; color: #94a3b8; padding: 4px 12px; font-size: 11px; font-weight: 600; border-radius: 18px; cursor: pointer; transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1); outline: none; white-space: nowrap; line-height: 1.2;';

                        pillContainer.appendChild(btn1);
                        pillContainer.appendChild(btn2);
                        row.appendChild(pillContainer);

                        let activeVal = currentVal;

                        const updatePills = (val) => {
                            activeVal = val;
                            if (val === config.options[0]) {
                                btn1.style.background = 'rgba(56, 189, 248, 0.25)';
                                btn1.style.color = '#38bdf8';
                                btn1.style.boxShadow = '0 0 8px rgba(56, 189, 248, 0.3)';
                                btn2.style.background = 'transparent';
                                btn2.style.color = '#94a3b8';
                                btn2.style.boxShadow = 'none';
                            } else {
                                btn2.style.background = 'rgba(56, 189, 248, 0.25)';
                                btn2.style.color = '#38bdf8';
                                btn2.style.boxShadow = '0 0 8px rgba(56, 189, 248, 0.3)';
                                btn1.style.background = 'transparent';
                                btn1.style.color = '#94a3b8';
                                btn1.style.boxShadow = 'none';
                            }
                        };

                        updatePills(currentVal);

                        // Ensure state is initialized with resolved default so pill is visually active on first load
                        if (initialVal === undefined && currentVal) {
                            saveConfigVal(currentVal);
                        }

                        const saveVal = (val) => {
                            saveConfigVal(val);
                            updatePills(val);
                        };

                        btn1.addEventListener('click', () => saveVal(config.options[0]));
                        btn2.addEventListener('click', () => saveVal(config.options[1]));

                        controlNode = {
                            set value(val) {
                                updatePills(val);
                            },
                            get value() {
                                return activeVal;
                            }
                        };
                    } else {
                        const select = document.createElement('select');
                        select.id = `sim-control-${config.id}`;
                        select.style.cssText = 'background: #1e293b; color: #f8fafc; border: 1px solid rgba(56, 189, 248, 0.2); padding: 4px 8px; border-radius: 4px; font-size: 12px; outline: none; cursor: pointer; max-width: 140px;';

                        config.options.forEach(opt => {
                            const optionNode = document.createElement('option');
                            optionNode.value = opt;
                            optionNode.textContent = opt;
                            if (opt === currentVal) optionNode.selected = true;
                            select.appendChild(optionNode);
                        });

                        row.appendChild(select);
                        controlNode = select;

                        select.addEventListener('change', (e) => {
                            saveConfigVal(e.target.value);
                        });
                    }
                } else {
                    const input = document.createElement('input');
                    input.id = `sim-control-${config.id}`;
                    // 'color' gets the browser's native picker, which stands in for the
                    // app's Compose color dialog while running in the simulator.
                    input.type = config.type === 'number' ? 'number'
                        : config.type === 'color' ? 'color'
                            : 'text';
                    input.value = currentVal;
                    input.style.cssText = 'background: #1e293b; color: #f8fafc; border: 1px solid rgba(56, 189, 248, 0.2); padding: 4px 8px; border-radius: 4px; font-size: 12px; outline: none; max-width: 120px;';

                    row.appendChild(input);
                    controlNode = input;

                    input.addEventListener('input', (e) => {
                        const nextVal = e.target.value;
                        console.log(`[Simulator Setting] Input ${config.stateVariable} -> ${nextVal}`);
                        if (window.Android && typeof window.Android.savePreference === 'function') {
                            window.Android.savePreference(config.stateVariable, nextVal);
                            window.Android.savePreference(`app.preferences.${config.stateVariable}`, nextVal);
                        }
                        if (window.onDataChanged) {
                            window.onDataChanged(`app.preferences.${config.stateVariable}`, nextVal);
                        }
                    });
                }

                container.appendChild(row);

                // Reactive sync from state change to control element
                stateManager.subscribe(config.stateVariable, (val) => {
                    if (!controlNode) return;
                    if (config.type === 'boolean') {
                        controlNode.checked = val !== false && val !== 'false';
                    } else {
                        controlNode.value = val !== undefined ? val : '';
                    }
                });
            });
        };

        loadThemeXmlAndBuildUi();

        // Minimize toggles
        const toggleConsoleMinimize = () => {
            uiPanel.classList.toggle('minimized');
        };
        const toggleSettingsMinimize = () => {
            settingsPanel.classList.toggle('minimized');
        };
        const toggleStateMinimize = () => {
            statePanel.classList.toggle('minimized');
        };
        const toggleShortcutsMinimize = () => {
            shortcutsPanel.classList.toggle('minimized');
        };

        document.getElementById('harness-header-bar').addEventListener('click', toggleConsoleMinimize);
        uiPanel.querySelector('.minimized-indicator').addEventListener('click', toggleConsoleMinimize);

        document.getElementById('harness-settings-header-bar').addEventListener('click', toggleSettingsMinimize);
        settingsPanel.querySelector('.minimized-indicator').addEventListener('click', toggleSettingsMinimize);

        document.getElementById('harness-state-header-bar').addEventListener('click', toggleStateMinimize);
        statePanel.querySelector('.minimized-indicator').addEventListener('click', toggleStateMinimize);

        document.getElementById('harness-shortcuts-header-bar').addEventListener('click', toggleShortcutsMinimize);
        shortcutsPanel.querySelector('.minimized-indicator').addEventListener('click', toggleShortcutsMinimize);

        // Run suite trigger
        const runBtn = document.getElementById('harness-run-btn');
        runBtn.addEventListener('click', async () => {
            runBtn.disabled = true;
            await runTestSuite();
            runBtn.disabled = false;
        });

        // Add keyboard keydown listener for simulated keys and [T] to toggle panels
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey || e.altKey || e.metaKey) return;

            // If user is focused on an input/select/textarea element, do not capture arrow keys or letter keys
            const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
            if (activeTag === 'input' || activeTag === 'select' || activeTag === 'textarea') {
                return;
            }

            if (e.key.toLowerCase() === 't') {
                e.preventDefault();
                toggleConsoleMinimize();
                toggleSettingsMinimize();
                toggleStateMinimize();
                toggleShortcutsMinimize();
            } else if (e.key === 'ArrowLeft') {
                e.preventDefault();
                console.log(`[Keyboard Intercept] ArrowLeft -> LEFT`);
                dispatchKeyEvent('LEFT');
            } else if (e.key === 'ArrowRight') {
                e.preventDefault();
                console.log(`[Keyboard Intercept] ArrowRight -> RIGHT`);
                dispatchKeyEvent('RIGHT');
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                console.log(`[Keyboard Intercept] ArrowUp -> UP`);
                dispatchKeyEvent('UP');
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                console.log(`[Keyboard Intercept] ArrowDown -> DOWN`);
                dispatchKeyEvent('DOWN');
            } else if (e.key === 'Enter') {
                e.preventDefault();
                console.log(`[Keyboard Intercept] Enter -> ENTER`);
                dispatchKeyEvent('ENTER');
            } else if (e.key === 'Backspace' || e.key === 'Escape') {
                e.preventDefault();
                console.log(`[Keyboard Intercept] ${e.key} -> BACK`);
                dispatchKeyEvent('BACK');
            }
        });

        updateUI();
    };

    // UI renderer refresher
    function updateUI() {
        if (!uiPanel) return;

        const listContainer = document.getElementById('harness-list-container');
        if (listContainer) {
            listContainer.innerHTML = '';
            registry.forEach(test => {
                const item = document.createElement('div');
                item.className = `harness-test-item ${test.status === 'running' ? 'running' : ''}`;

                let dotClass = '';
                if (test.status === 'running') dotClass = 'running';
                else if (test.status === 'pass') dotClass = 'pass';
                else if (test.status === 'fail') dotClass = 'fail';

                item.innerHTML = `
                    <div style="font-weight: 500; display: flex; align-items: center; gap: 8px;">
                        <span class="harness-status-dot ${dotClass}"></span>
                        ${test.name}
                    </div>
                    <div style="font-size: 11px; opacity: 0.6; font-family: monospace;">
                        ${test.status.toUpperCase()}
                    </div>
                `;
                listContainer.appendChild(item);
            });
        }

        const runBtn = document.getElementById('harness-run-btn');
        if (runBtn) {
            runBtn.disabled = isRunning;
        }
    }

    // Inject panel into active browser context
    injectUI();

    // --- PUBLIC PROGRAMMATIC INTERFACE (AGENT & BRIDGES HOOKS) ---
    window.__TEST_HARNESS = {
        runTestSuite,
        registerTest,
        getTestResults,
        getRegistry: () => registry,
        injectKeystroke: (key) => dispatchKeyEvent(key),
        stateManager,
        menuItems,
        getState,
        setState,
        toggleSimulation: () => {
            if (isSimRunning) {
                stopSimulation();
            } else {
                startSimulation();
            }
        },
        startSimulation,
        stopSimulation
    };

    console.log('✅ [Testing Harness] Exposed window.__TEST_HARNESS API interface contract.');
}
