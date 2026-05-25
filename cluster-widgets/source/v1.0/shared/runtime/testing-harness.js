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
    let toggleBtn = null;

    // Helper functions for JNI state mapping inside standard tests
    const getState = (key) => stateManager.get(key);
    const setState = (key, value) => stateManager.set(key, value);

    const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

    // Simulated steering wheel key dispatcher
    const dispatchKeyEvent = async (keyName) => {
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

            // 4. Test One-Pedal toggling on short ENTER
            h.assertEqual(h.getState('onepedal'), false, "One-Pedal starts disabled");
            await h.dispatchKeyEvent('ENTER');
            h.assertEqual(h.getState('onepedal'), true, "Short ENTER press activates One-Pedal");
            
            await h.dispatchKeyEvent('ENTER');
            h.assertEqual(h.getState('onepedal'), false, "Short ENTER press deactivates One-Pedal");

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
            #testing-console-harness *, 
            #testing-settings-harness * {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif !important;
                box-sizing: border-box;
            }
            #testing-console-harness, #testing-settings-harness {
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
                width: 380px;
            }
            #testing-settings-harness {
                right: 420px;
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
                height: 120px;
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
            #testing-settings-harness.minimized .minimized-indicator {
                display: flex;
            }
            #testing-console-harness.minimized .harness-header,
            #testing-console-harness.minimized .harness-body,
            #testing-settings-harness.minimized .harness-header,
            #testing-settings-harness.minimized .harness-body {
                display: none;
            }
            @keyframes pulse {
                from { opacity: 0.4; }
                to { opacity: 1; }
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
            <div class="harness-body">
                <div class="harness-tests-list" id="harness-list-container"></div>
                <div class="harness-console" id="harness-console-output">
                    <div style="opacity: 0.4; font-style: italic; text-align: center; margin-top: 40px; font-size: 12px;">Harness ready. Run tests to see output logs.</div>
                </div>
                <div class="harness-controls">
                    <button class="harness-btn" id="harness-run-btn">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" style="margin-right: 4px;"><path d="M8 5v14l11-7z"/></svg>
                        Run Test Suite
                    </button>
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
                <div class="harness-settings-panel">
                    <div style="font-size: 11px; font-weight: 600; text-transform: uppercase; color: #38bdf8; margin-bottom: 8px; letter-spacing: 0.5px;">
                        Configurações do Tema (Simulador)
                    </div>
                    <div class="harness-setting-row">
                        <span class="harness-switch-label">Cabeçalho Visível (headerVisible)</span>
                        <label class="harness-switch">
                            <input type="checkbox" id="sim-checkbox-header" />
                            <span class="harness-slider"></span>
                        </label>
                    </div>
                </div>
                <div style="margin-top: auto; padding: 12px; border-radius: 8px; background: rgba(56, 189, 248, 0.04); border: 1px solid rgba(56, 189, 248, 0.08); font-size: 12px; color: #94a3b8; line-height: 1.4;">
                    <div style="font-weight: 600; color: #38bdf8; margin-bottom: 4px;">Info</div>
                    Alterar opções neste simulador emite eventos JNI Preference Push em tempo real, exatamente como no painel físico do Android.
                </div>
            </div>
        `;

        document.body.appendChild(uiPanel);
        document.body.appendChild(settingsPanel);
        uiConsole = document.getElementById('harness-console-output');

        // Wire checkbox listeners to simulate backend Preference Push
        const headerChk = document.getElementById('sim-checkbox-header');
        headerChk.checked = getState('headerVisible') !== false;
        headerChk.addEventListener('change', (e) => {
            const nextVal = e.target.checked;
            console.log(`[Simulator Setting] Toggled headerVisible -> ${nextVal}`);
            // Symmetrical push dispatch matching native ServiceManager pushes:
            if (window.onDataChanged) {
                window.onDataChanged("app.preferences.headerVisible", String(nextVal));
            }
        });

        // Sync harness checkbox with external JNI pushes dynamically
        stateManager.subscribe('headerVisible', (val) => {
            if (headerChk) headerChk.checked = val !== false;
        });

        // Minimize toggles
        const toggleConsoleMinimize = () => {
            uiPanel.classList.toggle('minimized');
        };
        const toggleSettingsMinimize = () => {
            settingsPanel.classList.toggle('minimized');
        };

        document.getElementById('harness-header-bar').addEventListener('click', toggleConsoleMinimize);
        uiPanel.querySelector('.minimized-indicator').addEventListener('click', toggleConsoleMinimize);

        document.getElementById('harness-settings-header-bar').addEventListener('click', toggleSettingsMinimize);
        settingsPanel.querySelector('.minimized-indicator').addEventListener('click', toggleSettingsMinimize);

        // Run suite trigger
        const runBtn = document.getElementById('harness-run-btn');
        runBtn.addEventListener('click', async () => {
            runBtn.disabled = true;
            await runTestSuite();
            runBtn.disabled = false;
        });

        // Add keyboard keydown listener for [T] to toggle the panels
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey || e.altKey || e.metaKey) return;
            if (e.key.toLowerCase() === 't') {
                e.preventDefault();
                toggleConsoleMinimize();
                toggleSettingsMinimize();
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
        setState
    };

    console.log('✅ [Testing Harness] Exposed window.__TEST_HARNESS API interface contract.');
}
