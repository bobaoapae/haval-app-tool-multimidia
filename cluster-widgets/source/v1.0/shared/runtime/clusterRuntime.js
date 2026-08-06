/**
 * Shisuku Cluster Theme Shared Runtime (clusterRuntime.js)
 * Implements the V1 bridge contract, raw steering wheel event routers,
 * reactive cycle hooks, and the local browser simulation harness.
 */

// 1. Thread-safe Local Telemetry & Preference Cache (Browser fallback)
class MockCarState {
    constructor() {
        this.cache = new Map();
        this.subscriptions = new Set();
        this.listeners = new Set();
        this.preferences = new Map();
        // <default> of every <configuration> in theme.xml, keyed by stateVariable.
        // Populated asynchronously by loadThemeDefaults().
        this.themeDefaults = new Map();
        // <DisplayMode> entries from theme.xml, mirroring what the native
        // getThemeDisplayModes() returns on the car.
        this.themeDisplayModes = [];

        // Populate initial realistic mocks (both capitalized legacy and lowercase canonical V1 keys)
        this.cache.set('CAR_BASIC_VEHICLE_SPEED', '0.0');
        this.cache.set('CAR_BASIC_TOTAL_ODOMETER', '11450');
        this.cache.set('CAR_BASIC_GEAR_STATUS', '1'); // 'P'
        this.cache.set('CAR_HVAC_FAN_SPEED', '3');
        this.cache.set('CAR_HVAC_DRIVER_TEMPERATURE', '22.0');
        this.cache.set('CAR_HVAC_POWER_MODE', '1');
        this.cache.set('CAR_HVAC_CYCLE_MODE', '0');
        this.cache.set('CAR_DRIVE_SETTING_ESP_ENABLE', '1');
        this.cache.set('CAR_EV_SETTING_POWER_MODEL_CONFIG', '3'); // 'EV'
        this.cache.set('CAR_DRIVE_SETTING_DRIVE_MODE', '0'); // 'Normal'
        this.cache.set('CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE', '1'); // 'Conforto'
        this.cache.set('CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL', '1'); // 'Normal'

        // Lowercase V1 canonical and virtual keys
        this.cache.set('car.basic.vehicle_speed', '0.0');
        this.cache.set('car.basic.total_odometer', '11450');
        this.cache.set('car.basic.gear_status', '1');
        this.cache.set('car.hvac.fan_speed', '3');
        this.cache.set('car.hvac.driver_temperature', '22.0');
        this.cache.set('car.hvac.power_mode', '1');
        this.cache.set('car.hvac.cycle_mode', '0');
        this.cache.set('car.drive_setting.esp_enable', '1');
        this.cache.set('car.ev_setting.power_model_config', '3');
        this.cache.set('car.drive_setting.drive_mode', '0');
        this.cache.set('car.drive_setting.steering_wheel_assist_mode', '1');
        this.cache.set('car.ev_setting.energy_recovery_level', '1');
        this.cache.set('car.ev.setting.pedal_control_enable', '0');
        this.cache.set('car.ev_setting.power_reserve_config', '1');
        this.cache.set('car.ev_setting.charge_soc_target_config', '50');

        // Virtual App Telemetry Keys
        this.cache.set('app.display.1.active_app', 'com.ts.androidauto.app');
        this.cache.set('app.display.1.active_app_label', 'Android Auto');
        this.cache.set('app.display.1.active_app_icon', 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9Ij24IiBoZWlnaHQ9IjI0Ij48cGF0aCBmaWxsPSIjOENFMDcwIiBkPSJNMTIsMkEyMDQsMjA0LDAsMCwwLDIsMTJBMjA0LDIwNCwwLDAsMCwxMiwyMkEyMDQsMjA0LDAsMCwwLDIyLDEyQTIwNCwyMDQsMCwwLDAsMTIsMlpNMTIsMThBOCw4LDAsMCwxLDQsMTJBOCw4LDAsMCwxLDEyLDRBOCw4LDAsMCwxLDIwLDEyQTgsOCwwLDAsMSwxMiwxOFoiLz48L3N2Zz4=');
        this.cache.set('app.display.3.active_app', '');
        this.cache.set('app.display.3.active_app_label', '');
        this.cache.set('app.launcher.apps', '[]');
        this.cache.set('app.navigation.directions', '{"street": "Av. Paulista", "distance": "200m", "turn": "TURN_RIGHT"}');
        
        // Media Mocks
        this.cache.set('app.media.state', 'playing');
        this.cache.set('app.media.title', 'Smooth Criminal');
        this.cache.set('app.media.artist', 'Michael Jackson');
        this.cache.set('app.media.album', 'Bad');
        this.cache.set('app.media.duration', '257000');
        this.cache.set('app.media.position', '125000');

        // Telephony Mocks
        this.cache.set('app.phone.state', 'idle');
        this.cache.set('app.phone.caller_name', 'Marcelo');
        this.cache.set('app.phone.caller_number', '+55 11 99999-9999');
        this.cache.set('app.phone.call_duration', '0');
    }

    /** Strip the optional `app.preferences.` prefix so both spellings hit one store. */
    static preferenceName(key) {
        if (typeof key !== "string") return null;
        return key.startsWith("app.preferences.")
            ? key.substring("app.preferences.".length)
            : key;
    }

    /** Resolved preference value, or null when nothing (stored or declared) is known. */
    storedPreference(key) {
        const stateVar = MockCarState.preferenceName(key);
        if (!stateVar) return null;
        const cached = localStorage.getItem(`pref_${stateVar}`);
        if (cached !== null) return cached;
        if (this.themeDefaults.has(stateVar)) return this.themeDefaults.get(stateVar);
        if (stateVar === "headerVisible" || stateVar === "enableOdometer") return "true";
        return null;
    }

    /**
     * True only when we can answer for this key. The mock pushes an initial value
     * on subscribe, so answering '0' for keys we know nothing about would overwrite
     * whatever default the theme just bound - that is how every theme.xml setting
     * used to collapse to "0" a moment after boot.
     */
    has(key) {
        return this.cache.has(key) || this.storedPreference(key) !== null;
    }

    get(key) {
        if (this.cache.has(key)) {
            return this.cache.get(key);
        }
        const preference = this.storedPreference(key);
        if (preference !== null) return preference;
        return '0';
    }

    set(key, value) {
        const previous = this.cache.get(key);
        if (previous !== value) {
            this.cache.set(key, value);
            console.log(`[Mock Telemetry] Data Changed: ${key} -> ${value}`);
            if (window.onDataChanged) {
                window.onDataChanged(key, value);
            }
        }
    }

    /**
     * Read the <configurations> declared in theme.xml so local runs start on the
     * same defaults the native Settings UI would apply on the car. Stored
     * preferences still win; this only fills in what the user has never set.
     */
    async loadThemeDefaults(url = './theme.xml') {
        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const xml = new DOMParser().parseFromString(await response.text(), 'text/xml');
            const configs = xml.getElementsByTagName('configuration');
            for (let i = 0; i < configs.length; i++) {
                const tag = (name) => {
                    const nodes = configs[i].getElementsByTagName(name);
                    return nodes.length ? nodes[0].textContent.trim() : '';
                };
                const stateVariable = tag('stateVariable');
                const defaultValue = tag('default');
                if (!stateVariable || defaultValue === '') continue;
                this.themeDefaults.set(stateVariable, defaultValue);
                const id = tag('id');
                if (id) this.themeDefaults.set(id, defaultValue);
            }

            const modes = xml.getElementsByTagName('DisplayMode');
            for (let i = 0; i < modes.length; i++) {
                const attr = (n) => (modes[i].getAttribute(n) || '').trim();
                const name = attr('name');
                const bounds = ['x', 'y', 'width', 'height'].map((k) => Number(attr(k)));
                if (!name || bounds.some((v) => !Number.isFinite(v)) || attr('x') === '') {
                    console.warn('[Mock Bridge] ignoring <DisplayMode>: needs name, x, y, width and height attributes');
                    continue;
                }
                const [x, y, width, height] = bounds;
                this.themeDisplayModes.push({ name, x, y, width, height });
            }

            console.log('[Mock Bridge] theme.xml defaults loaded:',
                Object.fromEntries(this.themeDefaults),
                'displayModes:', this.themeDisplayModes);
        } catch (e) {
            console.warn('[Mock Bridge] could not load theme.xml defaults:', e.message);
        }
        return this.themeDefaults;
    }
}

const mockState = new MockCarState();

// 2. Setup the global window.Android interface bridge stub if running locally
const isBrowser = typeof window !== 'undefined' && (!window.Android || !window.Android.subscribe);

if (isBrowser) {
    console.warn('[Theme Engine] Running in local Browser; attaching telemetry mock harness.');

    // Kick off before anything binds settings; ThemeBridgeAdapter.init() awaits it.
    window.__MOCK_THEME_DEFAULTS_READY__ = mockState.loadThemeDefaults();
    window.__MOCK_CAR_STATE__ = mockState;

    window.Android = {
        // Must mirror ThemeBridgeImpl.getAvailableKeys() in the app. hasKey() rejects
        // anything absent from this list, so a mock that under-reports does not merely
        // look different from the car -- it makes the dev harness suppress real
        // subscriptions and render dead gauges, which reads as a theme bug that does not
        // exist. This list was missing every gauge and graph signal for exactly that reason.
        getAvailableKeys: () => {
            return JSON.stringify([
                "car.basic.vehicle_speed",
                "car.basic.engine_speed",
                "car.basic.instant_fuel_consumption",
                "car.basic.total_odometer",
                "car.basic.gear_status",
                "car.basic.inside_temp",
                "car.basic.outside_temp",
                "car.basic.remain_fuel_percentage",
                "car.ev_info.cur_battery_power_percentage",
                "car.ev_info.fuel_mode_remain_odometer",
                "car.ev_info.electric_mode_remain_odometer",
                "car.configure.default_temp_unit",
                "car.drive_setting.esp_enable",
                "car.ev_setting.power_model_config",
                "car.drive_setting.drive_mode",
                "car.drive_setting.steering_wheel_assist_mode",
                "car.ev_setting.energy_recovery_level",
                "car.ev.setting.pedal_control_enable",
                "car.ev_setting.power_reserve_config",
                "car.ev_setting.charge_soc_target_config",
                "car.ev_info.energy_output_percentage",
                "car.ev_info.cur_charge_current",
                "car.ev_info.power_battery_voltage",
                "car.ev_info.Instant_energy_consumption",
                "car.hvac.power_mode",
                "car.hvac.fan_speed",
                "car.hvac.driver_temperature",
                "car.hvac.cycle_mode",
                "car.hvac.auto_enable",
                "car.hvac.anion_enable",
                "app.display.1.active_app",
                "app.display.1.active_app_label",
                "app.display.1.active_app_icon",
                "app.display.3.active_app",
                "app.display.3.active_app_label",
                "app.display.3.active_app_icon",
                "app.launcher.apps",
                "app.navigation.directions",
                "app.media.state",
                "app.media.title",
                "app.media.artist",
                "app.media.album",
                "app.media.duration",
                "app.media.position",
                "app.media.album_art",
                "app.phone.state",
                "app.phone.caller_name",
                "app.phone.caller_number",
                "app.phone.call_duration",
                "bsdLeft",
                "bsdRight",
                "carPlayInDash",
                "projectionMirrorInDash",
                "projectionPreparingD3",
                "projectionCardOverlayAllowed",
                "warningActive",
                "warningDismissed"
            ]);
        },

        subscribe: (keysJson) => {
            try {
                const keys = JSON.parse(keysJson);
                console.log('[Mock Bridge] subscribed to keys:', keys);
                keys.forEach(key => {
                    mockState.subscriptions.add(key);
                    // Instantly trigger initial data change push, but only for keys we
                    // actually have a value for. Pushing the '0' fallback here would
                    // land after bindThemeSetting() and silently replace the theme's
                    // own default for every setting declared in theme.xml.
                    setTimeout(() => {
                        if (window.onDataChanged && mockState.has(key)) {
                            window.onDataChanged(key, mockState.get(key));
                        }
                    }, 50);
                });
            } catch (e) {
                console.error('[Mock Bridge] subscribe failed:', e);
            }
        },

        unsubscribe: (keysJson) => {
            try {
                const keys = JSON.parse(keysJson);
                console.log('[Mock Bridge] unsubscribed from keys:', keys);
                keys.forEach(key => mockState.subscriptions.delete(key));
            } catch (e) {
                console.error('[Mock Bridge] unsubscribe failed:', e);
            }
        },

        getCarData: (key) => {
            return mockState.get(key);
        },

        updateCarData: (key, value) => {
            mockState.set(key, value);
        },

        launchApp: (packageName, displayId) => {
            console.log(`[Mock Bridge] Symmetrical launchApp: ${packageName} on display ${displayId}`);
            mockState.set(`app.display.${displayId}.active_app`, packageName);
            mockState.set(`app.display.${displayId}.active_app_label`, packageName.split('.').slice(-2, -1)[0] || packageName);
        },

        killApp: (packageName) => {
            console.log(`[Mock Bridge] Symmetrical killApp: ${packageName}`);
            if (mockState.get('app.display.1.active_app') === packageName) {
                mockState.set('app.display.1.active_app', '');
                mockState.set('app.display.1.active_app_label', '');
            }
        },

        triggerSystemAction: (action, payload) => {
            console.log(`[Mock Bridge] System Action Triggered: ${action} with payload '${payload}'`);
            if (action === 'DISMISS_WARNINGS' && window.clearWarnings) {
                window.clearWarnings();
            } else if (action === 'MEDIA_CONTROL') {
                console.log(`[Mock Bridge] Simulated Media Action: ${payload}`);
                if (payload === 'next') {
                    mockState.set('app.media.title', 'Billy Jean');
                } else if (payload === 'play') {
                    mockState.set('app.media.state', 'playing');
                } else if (payload === 'pause') {
                    mockState.set('app.media.state', 'paused');
                }
            }
        },

        savePreference: (key, value) => {
            console.log(`[Mock Bridge] Preference Saved: ${key} -> ${value}`);
            localStorage.setItem(`pref_${key}`, value);
            mockState.preferences.set(key, value);
        },

        setClusterBackground: (type, value) => {
            console.log(`[Mock Bridge] setClusterBackground type=${type} value=${value}`);
            localStorage.setItem('pref_customBackgroundTypeD1', String(type || 'THEME'));
            localStorage.setItem('pref_customBackgroundValueD1', String(value || ''));
            localStorage.setItem('pref_enableCustomBackgroundD1', 'true');
        },

        setThemeBackground: (relativePath) => {
            console.log(`[Mock Bridge] setThemeBackground path=${relativePath}`);
            localStorage.setItem('pref_customBackgroundTypeD1', 'THEME');
            localStorage.setItem('pref_customBackgroundValueD1', String(relativePath || ''));
            localStorage.setItem('pref_enableCustomBackgroundD1', 'true');
        },

        getThemeDisplayModes: () => JSON.stringify(mockState.themeDisplayModes),

        getPreference: (key, defaultValue) => {
            // Stored value first, then the <default> declared in theme.xml, then the
            // caller's fallback - the same precedence the native settings store uses.
            const resolved = mockState.storedPreference(key);
            return resolved !== null ? resolved : defaultValue;
        },

        saveSetting: (key, value) => {
            console.log(`[Mock Bridge] Setting Saved: ${key} -> ${value}`);
        },

        setUseDecentralizedScreens: (enabled) => {
            console.log(`[Mock Bridge] setUseDecentralizedScreens: ${enabled}`);
        }
    };

    // Auto-map browser arrow keys to physical steering wheel button events (Idempotent guard for HMR)
    if (!window.__KEYBOARD_LISTENER_BOUND__) {
        window.__KEYBOARD_LISTENER_BOUND__ = true;
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey || e.altKey || e.metaKey) return;
            
            let physicalKey = null;
            switch (e.key) {
                case 'ArrowUp':
                    physicalKey = e.shiftKey ? 'UP_LONG' : 'UP';
                    break;
                case 'ArrowDown':
                    physicalKey = e.shiftKey ? 'DOWN_LONG' : 'DOWN';
                    break;
                case 'ArrowLeft':
                    physicalKey = e.shiftKey ? 'LEFT_LONG' : 'LEFT';
                    break;
                case 'ArrowRight':
                    physicalKey = e.shiftKey ? 'RIGHT_LONG' : 'RIGHT';
                    break;
                case 'Enter':
                    physicalKey = e.shiftKey ? 'ENTER_LONG' : 'ENTER';
                    break;
                case 'Escape':
                case 'Backspace':
                    physicalKey = e.shiftKey ? 'BACK_LONG' : 'BACK';
                    break;
            }

            if (physicalKey && window.onKeyEvent) {
                e.preventDefault();
                console.log(`[Mock Keyboard] Dispatching steering wheel keyevent: '${physicalKey}'`);
                window.onKeyEvent(physicalKey);
            }
        });
    }

    // Run simple hot simulated telemetry changes for developer preview
    let simulatedSpeed = 0.0;
    let accel = true;
    setInterval(() => {
        if (accel) {
            simulatedSpeed += 1.5;
            if (simulatedSpeed >= 120.0) accel = false;
        } else {
            simulatedSpeed -= 2.0;
            if (simulatedSpeed <= 0.0) {
                simulatedSpeed = 0.0;
                accel = true;
            }
        }
        mockState.set('CAR_BASIC_VEHICLE_SPEED', simulatedSpeed.toFixed(1));
    }, 1500);
}

// 3. Core Reactivity & Subscription Registry
class ThemeEngineClass {
    constructor() {
        this.dataListeners = new Map();
        this.keyListeners = new Set();
        this.beforeKeyDispatchHook = null;
        this.currentScreen = 'main_menu';
        this.activeCard = 1;

        // Route raw global callbacks to engine defensively (chaining existing handlers)
        // Guarded against multiple bindings in HMR environments while maintaining fresh instance delegation
        window.__THEME_ENGINE_INSTANCE__ = this;

        if (!window.__ENGINE_KEY_EVENT_BOUND__) {
            window.__ENGINE_KEY_EVENT_BOUND__ = true;
            const prevOnKeyEvent = window.onKeyEvent;
            window.onKeyEvent = (keyName) => {
                if (typeof prevOnKeyEvent === "function") {
                    try { prevOnKeyEvent(keyName); } catch (e) { console.error(e); }
                }
                if (window.__THEME_ENGINE_INSTANCE__) {
                    window.__THEME_ENGINE_INSTANCE__.dispatchKeyEvent(keyName);
                }
            };
        }

        if (!window.__ENGINE_DATA_CHANGED_BOUND__) {
            window.__ENGINE_DATA_CHANGED_BOUND__ = true;
            const prevOnDataChanged = window.onDataChanged;
            window.onDataChanged = (key, value) => {
                if (typeof prevOnDataChanged === "function") {
                    try { prevOnDataChanged(key, value); } catch (e) { console.error(e); }
                }
                if (window.__THEME_ENGINE_INSTANCE__) {
                    window.__THEME_ENGINE_INSTANCE__.dispatchDataChanged(key, value);
                }
            };
        }

        if (!window.__ENGINE_CARD_CHANGED_BOUND__) {
            window.__ENGINE_CARD_CHANGED_BOUND__ = true;
            const prevOnCardChanged = window.onCardChanged;
            window.onCardChanged = (cardId) => {
                if (typeof prevOnCardChanged === "function") {
                    try { prevOnCardChanged(cardId); } catch (e) { console.error(e); }
                }
                if (window.__THEME_ENGINE_INSTANCE__) {
                    window.__THEME_ENGINE_INSTANCE__.dispatchCardChanged(cardId);
                }
            };
        }

        // Enable decentralized screens on Android
        if (window.Android && window.Android.setUseDecentralizedScreens) {
            window.Android.setUseDecentralizedScreens(true);
        }
    }

    subscribeData(keys, callback) {
        if (!Array.isArray(keys)) keys = [keys];
        keys.forEach(key => {
            if (!this.dataListeners.has(key)) {
                this.dataListeners.set(key, new Set());
            }
            this.dataListeners.get(key).add(callback);
        });

        // Register keys with Android backend
        if (window.Android && window.Android.subscribe) {
            window.Android.subscribe(JSON.stringify(keys));
        }

        return () => {
            keys.forEach(key => {
                const listeners = this.dataListeners.get(key);
                if (listeners) {
                    listeners.delete(callback);
                    if (listeners.size === 0) {
                        this.dataListeners.delete(key);
                        if (window.Android && window.Android.unsubscribe) {
                            window.Android.unsubscribe(JSON.stringify([key]));
                        }
                    }
                }
            });
        };
    }

    subscribeKeys(callback) {
        this.keyListeners.add(callback);
        return () => this.keyListeners.delete(callback);
    }

    onBeforeKeyDispatch(hookFn) {
        this.beforeKeyDispatchHook = hookFn;
    }

    dispatchKeyEvent(keyName) {
        // Speculation / Override interception hook
        if (this.beforeKeyDispatchHook) {
            const prevented = this.beforeKeyDispatchHook(keyName) === false;
            if (prevented) {
                console.log(`[Theme Engine] KeyEvent '${keyName}' intercepted and suppressed by Theme Override.`);
                return;
            }
        }

        this.keyListeners.forEach(listener => {
            try {
                listener(keyName);
            } catch (e) {
                console.error('[Theme Engine] Error in keyListener callback:', e);
            }
        });
    }

    /**
     * Resets the engine's listeners and hooks for HMR reload scenarios.
     */
    reset() {
        this.dataListeners.clear();
        this.keyListeners.clear();
        this.beforeKeyDispatchHook = null;
    }

    dispatchDataChanged(key, value) {
        const listeners = this.dataListeners.get(key);
        if (listeners) {
            listeners.forEach(callback => {
                try {
                    callback(key, value);
                } catch (e) {
                    console.error('[Theme Engine] Error in data callback:', e);
                }
            });
        }
    }

    dispatchCardChanged(cardId) {
        this.activeCard = cardId;
        console.log(`[Theme Engine] Card Changed: ${cardId}`);
        // Custom templates can register window.onCardTransition to catch layouts
        if (window.onCardTransition) {
            window.onCardTransition(cardId);
        }
    }

    navigateTo(screenName) {
        this.currentScreen = screenName;
        console.log(`[Theme Engine] Screen Navigation -> ${screenName}`);
        if (window.onScreenTransition) {
            window.onScreenTransition(screenName);
        }
    }
}

export const themeEngine = new ThemeEngineClass();

// 4. Primitives: useFocusCycle
export function useFocusCycle(itemIds, options = {}) {
    const namespace = options.namespace || 'global';
    const loop = options.loop !== false;
    const focusClass = options.focusClass || 'is-focused';
    const onFocusChange = options.onFocusChange || null;

    // Load persisted state from preferences
    const storageKey = `focus_${namespace}`;
    let currentIndex = 0;
    const persistedValue = window.Android.getPreference(storageKey, '');
    const persistedIndex = itemIds.indexOf(persistedValue);
    if (persistedIndex !== -1) {
        currentIndex = persistedIndex;
    }

    const updateDomStyles = () => {
        itemIds.forEach((id, index) => {
            const elements = document.querySelectorAll(`[data-menu-id="${id}"]`);
            elements.forEach(el => {
                if (index === currentIndex) {
                    el.classList.add(focusClass);
                } else {
                    el.classList.remove(focusClass);
                }
            });
        });
    };

    const setIndex = (index) => {
        let targetIndex = index;
        if (loop) {
            targetIndex = (targetIndex + itemIds.length) % itemIds.length;
        } else {
            targetIndex = Math.max(0, Math.min(itemIds.length - 1, targetIndex));
        }

        if (currentIndex !== targetIndex) {
            currentIndex = targetIndex;
            const focusedId = itemIds[currentIndex];
            window.Android.savePreference(storageKey, focusedId);
            updateDomStyles();
            if (onFocusChange) {
                onFocusChange(focusedId, currentIndex);
            }
        }
    };

    // Initialize layout hooks
    setTimeout(updateDomStyles, 100);

    return {
        next: () => setIndex(currentIndex + 1),
        prev: () => setIndex(currentIndex - 1),
        get focusedId() { return itemIds[currentIndex]; },
        get index() { return currentIndex; },
        setIndex
    };
}

// 5. Primitives: useValueCycle
export function useValueCycle(key, cycleValues, options = {}) {
    const onValueChange = options.onValueChange || null;
    let currentValue = window.Android.getCarData(key) || cycleValues[0];

    const unsubscribe = themeEngine.subscribeData([key], (k, val) => {
        currentValue = val;
        updateDomLabels();
        if (onValueChange) {
            onValueChange(val);
        }
    });

    const updateDomLabels = () => {
        const elements = document.querySelectorAll(`[data-value-key="${key}"]`);
        elements.forEach(el => {
            // Write current state value as dynamic dataset
            el.dataset.currentValue = currentValue;
            
            // If the element has labels, update label output
            const labelMapAttr = el.getAttribute('data-value-labels');
            if (labelMapAttr) {
                try {
                    const labels = JSON.parse(labelMapAttr.replace(/'/g, '"'));
                    const matchLabel = labels[currentValue] || currentValue;
                    el.textContent = matchLabel;
                } catch (e) {
                    el.textContent = currentValue;
                }
            } else {
                el.textContent = currentValue;
            }
        });
    };

    const cycle = () => {
        const currentIndex = cycleValues.indexOf(currentValue);
        const nextIndex = (currentIndex + 1) % cycleValues.length;
        const nextValue = cycleValues[nextIndex];
        window.Android.updateCarData(key, nextValue);
    };

    // Initialize layout hooks
    setTimeout(updateDomLabels, 100);

    return {
        cycle,
        get value() { return currentValue; },
        unsubscribe
    };
}

// 6. Automated Manifest Bootstrapper (Level 1 Manifest Engine)
export function bootstrapThemeFromManifest(manifest) {
    console.log('[Theme Engine] Bootstrapping Headless Manifest Layout...');

    const menuItems = manifest.menu || [];
    const itemIds = menuItems.map(item => item.id);

    // Initialize core focus cycle
    const focusController = useFocusCycle(itemIds, {
        namespace: 'manifest_menu',
        onFocusChange: (id) => {
            console.log(`[Manifest Menu] Focused: ${id}`);
        }
    });

    // Wire focusedMenuItem state subscriber to synchronize focus cycle with programmatically changed states
    themeEngine.subscribeData('focusedMenuItem', (key, id) => {
        const idx = itemIds.indexOf(id);
        if (idx !== -1 && focusController.index !== idx) {
            focusController.setIndex(idx);
        }
    });

    // Wire value cyclers to each interactive setting
    const cyclers = {};
    menuItems.forEach(item => {
        if (item.action === 'cycle' && item.key && item.values) {
            cyclers[item.id] = useValueCycle(item.key, item.values);
        }
    });

    // Global Keyevent Traversal Router (only if not disabled by manifest)
    if (manifest.disableDefaultKeys !== true) {
        themeEngine.subscribeKeys((keyName) => {
            // Only monitor keys when we are on the main menu
            if (themeEngine.currentScreen !== 'main_menu') return;

            if (keyName === 'UP') {
                focusController.prev();
            } else if (keyName === 'DOWN') {
                focusController.next();
            } else if (keyName === 'ENTER') {
                const activeId = focusController.focusedId;
                const menuItem = menuItems.find(item => item.id === activeId);
                if (!menuItem) return;

                if (menuItem.action === 'cycle' && cyclers[activeId]) {
                    cyclers[activeId].cycle();
                } else if (menuItem.action === 'navigate' && menuItem.screen) {
                    themeEngine.navigateTo(menuItem.screen);
                }
            }
        });
    }

    // Expose layout controller
    return {
        focusController,
        cyclers
    };
}
