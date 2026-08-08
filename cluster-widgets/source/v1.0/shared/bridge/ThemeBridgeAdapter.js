/**
 * ThemeBridgeAdapter.js
 * Unified Client-Side Contract Mediator wrapping window.Android defensively.
 * Provides dynamic feature discoverability via getAvailableKeys(), hasKey(), and hasCommand().
 */

import { KEYS } from "../car/carConstants.js";

export class ThemeBridgeAdapter {
    constructor() {
        this.rawBridge = typeof window !== "undefined" ? window.Android : null;
        this.supportedKeys = new Set();
        this.dataListeners = new Map();
        this.keyListeners = new Set();
        this.initialized = false;
    }

    /**
     * Initializes the adapter and queries supported JNI capabilities.
     * Must be called at boot-time before subscribing to metrics.
     */
    async init() {
        if (this.initialized) return;

        if (!this.rawBridge && typeof window !== "undefined") {
            this.rawBridge = window.Android;
        }

        // Local browser runs only: let the mock bridge finish reading theme.xml so
        // bindThemeSetting() below sees the declared <default>s instead of racing them.
        if (typeof window !== "undefined" && window.__MOCK_THEME_DEFAULTS_READY__) {
            try { await window.__MOCK_THEME_DEFAULTS_READY__; } catch (e) { /* mock only */ }
        }

        if (this.rawBridge && typeof this.rawBridge.getAvailableKeys === "function") {
            try {
                const keysJson = this.rawBridge.getAvailableKeys();
                const keys = JSON.parse(keysJson);
                this.supportedKeys = new Set(keys);
                console.log(`[BridgeAdapter] Initialized with ${this.supportedKeys.size} dynamic capabilities.`);
            } catch (e) {
                console.warn("[BridgeAdapter] Failed to parse supported keys JSON. Falling back to v1.0 standard CAN keys.", e);
                this.loadLegacyKeylist();
            }
        } else {
            console.info("[BridgeAdapter] Legacy JNI boundary detected. Loading v1.0 baseline CAN keys.");
            this.loadLegacyKeylist();
        }

        // Hook raw window callbacks back to our internal router defensively (chaining existing handlers)
        // Guarded against multiple bindings in HMR environments while maintaining fresh instance delegation
        if (typeof window !== "undefined") {
            window.__THEME_BRIDGE_INSTANCE__ = this;

            if (!window.__BRIDGE_DATA_CHANGED_BOUND__) {
                window.__BRIDGE_DATA_CHANGED_BOUND__ = true;
                const prevOnDataChanged = window.onDataChanged;
                window.onDataChanged = (key, value) => {
                    console.log(`[onDataChanged TRACE] key=${key} value=${value}`);
                    if (typeof prevOnDataChanged === "function") {
                        try { prevOnDataChanged(key, value); } catch (e) { console.error(e); }
                    }
                    if (window.__THEME_BRIDGE_INSTANCE__) {
                        window.__THEME_BRIDGE_INSTANCE__.dispatchDataChanged(key, value);
                    }
                };
            }

            if (!window.__BRIDGE_KEY_EVENT_BOUND__) {
                window.__BRIDGE_KEY_EVENT_BOUND__ = true;
                const prevOnKeyEvent = window.onKeyEvent;
                window.onKeyEvent = (keyName) => {
                    if (typeof prevOnKeyEvent === "function") {
                        try { prevOnKeyEvent(keyName); } catch (e) { console.error(e); }
                    }
                    if (window.__THEME_BRIDGE_INSTANCE__) {
                        window.__THEME_BRIDGE_INSTANCE__.dispatchKeyEvent(keyName);
                    }
                };
            }
        }

        this.initialized = true;
    }

    /**
     * Fallback key list for hosts that cannot answer getAvailableKeys() -- legacy APKs,
     * and the browser dev harness, which has no window.Android at all.
     *
     * This is the whole v1.0 contract surface, not a hand-picked subset. It used to list
     * 15 keys and left out every gauge and graph signal (fuel %, battery %, both ranges,
     * RPM, the four ev_info power readings), so on any host taking this path hasKey()
     * rejected them and the cluster came up with dead gauges. A subset here is a trap: the
     * only safe floor is "everything the contract defines", since an unsupported key on
     * the host side is already handled -- it simply never pushes a value.
     */
    loadLegacyKeylist() {
        this.supportedKeys = new Set([
            // Every CAN + virtual key the shared contract table defines.
            ...Object.values(KEYS),
            // Climate readouts and the unit setting, which predate the KEYS table and are
            // still referenced as literals by the Default theme.
            "car.basic.inside_temp",
            "car.basic.outside_temp",
            "car.configure.default_temp_unit",
            // Host-computed flags pushed under bare names rather than an "app." namespace.
            "bsdLeft",
            "bsdRight",
            "carPlayInDash",
            "projectionMirrorInDash",
            "projectionPreparingD3",
            "projectionCardOverlayAllowed",
            "warningActive",
            "warningDismissed"
        ]);
    }

    /**
     * Checks if a specific CAN-bus or virtual telemetry key is supported.
     * @param {string} key
     * @returns {boolean}
     */
    hasKey(key) {
        if (typeof key !== "string") return false;
        // Theme configuration values are theme-local and have no entry in the host's
        // capability list: the host mints the name from the theme.xml <stateVariable>
        // and only ever pushes it prefixed (PreferencePushListener). The prefix IS the
        // contract, so it is the thing to check.
        if (key.startsWith("app.preferences.")) return true;
        // Everything else must be a capability the host actually advertises. Waving
        // unknown names through is not harmless: ThemeBridgeImpl.subscribe() routes any
        // key that is neither "app.*" nor a known virtual into its CAN branch and hands
        // it to ServiceManager.ensureKeysMonitored(), which registers it with the car's
        // ControlService and keeps it in dynamicallyRegisteredKeys for the life of the
        // process. An unsupported key is a bug in the theme, so let it surface as the
        // suppression warning in subscribe() instead of as junk on the vehicle bus.
        return this.supportedKeys.has(key);
    }

    /**
     * Checks if a system action or control command is supported.
     * Supports both raw action names and command-prefixed namespace checking.
     * @param {string} command
     * @returns {boolean}
     */
    hasCommand(command) {
        return this.supportedKeys.has(command) || this.supportedKeys.has(`command.${command}`);
    }

    /**
     * Subscribes to telemetry changes for a list of keys with a callback function.
     * Automatically filters out unsupported keys to prevent useless overhead or errors.
     * @param {string[]} keys
     * @param {function} callback
     * @returns {function} - Unsubscribe function.
     */
    subscribe(keys, callback) {
        if (!Array.isArray(keys)) keys = [keys];

        const validKeys = keys.filter(k => {
            if (this.hasKey(k)) return true;
            console.log(`[BridgeAdapter] Suppressing subscription to unsupported key capability: ${k}`);
            return false;
        });

        validKeys.forEach(key => {
            if (!this.dataListeners.has(key)) {
                this.dataListeners.set(key, new Set());
            }
            this.dataListeners.get(key).add(callback);
        });

        // Push subscription down to raw JNI bridge
        if (validKeys.length > 0 && this.rawBridge && typeof this.rawBridge.subscribe === "function") {
            try {
                this.rawBridge.subscribe(JSON.stringify(validKeys));
            } catch (e) {
                console.error("[BridgeAdapter] JNI subscription call failed:", e);
            }
        }

        // Return unsubscribe helper
        return () => {
            validKeys.forEach(key => {
                const listeners = this.dataListeners.get(key);
                if (listeners) {
                    listeners.delete(callback);
                    if (listeners.size === 0) {
                        this.dataListeners.delete(key);
                        if (this.rawBridge && typeof this.rawBridge.unsubscribe === "function") {
                            try {
                                this.rawBridge.unsubscribe(JSON.stringify([key]));
                            } catch (e) {
                                console.error("[BridgeAdapter] JNI unsubscribe call failed:", e);
                            }
                        }
                    }
                }
            });
        };
    }

    /**
     * Registers a callback for physical steering wheel button event presses.
     * @param {function} callback
     * @returns {function} - Unsubscribe function.
     */
    subscribeKeys(callback) {
        this.keyListeners.add(callback);
        return () => this.keyListeners.delete(callback);
    }

    /**
     * Dynamic getter for a single data stream (synchronous fallback).
     * @param {string} key
     * @returns {string}
     */
    getCarData(key) {
        if (this.rawBridge && typeof this.rawBridge.getCarData === "function") {
            try {
                return this.rawBridge.getCarData(key);
            } catch (e) {
                console.error("[BridgeAdapter] getCarData call failed:", e);
            }
        }
        return "0";
    }

    /**
     * Dynamic update request (e.g. cycle menu/HVAC setting).
     * @param {string} key
     * @param {string} value
     */
    updateCarData(key, value) {
        if (this.rawBridge && typeof this.rawBridge.updateCarData === "function") {
            try {
                this.rawBridge.updateCarData(key, String(value));
            } catch (e) {
                console.error("[BridgeAdapter] updateCarData call failed:", e);
            }
        }
    }

    /**
     * Symmetrical Launch App command.
     * @param {string} packageName - System application package ID.
     * @param {number} displayId - Infotainment display ID (e.g. 1 = front passenger, 3 = HUD/secondary).
     */
    launchApp(packageName, displayId = 1) {
        if (this.hasCommand("launchApp") && this.rawBridge && typeof this.rawBridge.launchApp === "function") {
            try {
                this.rawBridge.launchApp(packageName, displayId);
            } catch (e) {
                console.error(`[BridgeAdapter] launchApp failed to launch ${packageName} on display ${displayId}:`, e);
            }
        } else {
            console.warn(`[BridgeAdapter] launchApp command unsupported by JNI. Suppression triggered for: ${packageName}`);
        }
    }

    /**
     * Symmetrical Force Stop / Kill App command.
     * @param {string} packageName
     */
    killApp(packageName) {
        if (this.hasCommand("killApp") && this.rawBridge && typeof this.rawBridge.killApp === "function") {
            try {
                this.rawBridge.killApp(packageName);
            } catch (e) {
                console.error(`[BridgeAdapter] killApp failed for ${packageName}:`, e);
            }
        } else {
            console.warn(`[BridgeAdapter] killApp command unsupported by JNI. Suppression triggered for: ${packageName}`);
        }
    }

    /**
     * Symmetrical System Action Trigger.
     * @param {string} action - Action identifier (e.g. "MEDIA_CONTROL", "BRING_ALL_TO_MAIN", "DISMISS_WARNINGS").
     * @param {string} payload - Custom parameters or controls.
     */
    triggerSystemAction(action, payload = "") {
        if (this.rawBridge && typeof this.rawBridge.triggerSystemAction === "function") {
            try {
                this.rawBridge.triggerSystemAction(action, payload);
            } catch (e) {
                console.error(`[BridgeAdapter] triggerSystemAction failed for '${action}':`, e);
            }
        } else {
            console.warn(`[BridgeAdapter] triggerSystemAction is unsupported or failed. Suppressing: ${action}`);
        }
    }

    /**
     * Gets a saved user preference value from Android SharedPreferences.
     * @param {string} key
     * @param {string} defaultValue
     * @returns {string}
     */
    getPreference(key, defaultValue = "") {
        if (this.rawBridge && typeof this.rawBridge.getPreference === "function") {
            try {
                return this.rawBridge.getPreference(key, defaultValue);
            } catch (e) {
                console.error(`[BridgeAdapter] getPreference failed for ${key}:`, e);
            }
        }
        return defaultValue;
    }

    /**
     * Per-display-mode app bounds declared in the active theme's theme.xml.
     * Returns a name -> {x, y, width, height} map, keyed lowercase so lookups are
     * case-insensitive. Empty when the theme declares no <DisplayModes>, which
     * means every mode uses AppDefaultPosition.
     * @returns {Record<string, {x: number, y: number, width: number, height: number}>}
     */
    getThemeDisplayModes() {
        if (!this.rawBridge || typeof this.rawBridge.getThemeDisplayModes !== "function") {
            return {};
        }
        try {
            const parsed = JSON.parse(this.rawBridge.getThemeDisplayModes() || "[]");
            const modes = {};
            parsed.forEach((entry) => {
                if (!entry || typeof entry.name !== "string") return;
                const bounds = ["x", "y", "width", "height"].map((k) => Number(entry[k]));
                if (bounds.some((v) => !Number.isFinite(v))) {
                    console.warn(`[BridgeAdapter] <DisplayMode name="${entry.name}"> has non-numeric bounds; ignoring.`);
                    return;
                }
                const [x, y, width, height] = bounds;
                modes[entry.name.trim().toLowerCase()] = { x, y, width, height };
            });
            return modes;
        } catch (e) {
            console.error("[BridgeAdapter] getThemeDisplayModes failed:", e);
            return {};
        }
    }

    /**
     * Where the Display-1 wallpaper comes from, for themes that need to repaint a slice
     * of it from display 3 (see `.mask-bottom-backdrop`).
     *
     * `{kind: "IMAGE", url}` must be painted into a 1920x720 box with `background-size:
     * cover; background-position: center`, which is the CENTER_CROP fit the host applies
     * on display 1; anything else and the copy will not line up.
     *
     * The two empty answers are deliberately different, because a theme should act on
     * them differently: `null` is the host stating there is nothing to copy (background
     * disabled, or a live web page as wallpaper), and `undefined` is a host that predates
     * this call and cannot answer at all — the only case where guessing at the wallpaper
     * from the theme's own package is better than doing nothing.
     *
     * @returns {{kind: "IMAGE", url: string} | {kind: "COLOR", color: string, vignette: number} | null | undefined}
     */
    getClusterBackdropSource() {
        if (!this.rawBridge || typeof this.rawBridge.getClusterBackdropSource !== "function") {
            return undefined;
        }
        try {
            const parsed = JSON.parse(this.rawBridge.getClusterBackdropSource() || "{}");
            if (!parsed || typeof parsed !== "object") return null;
            if (parsed.kind === "IMAGE") {
                return typeof parsed.url === "string" && parsed.url !== ""
                    ? { kind: "IMAGE", url: parsed.url }
                    : null;
            }
            if (parsed.kind === "COLOR") {
                const vignette = Number(parsed.vignette);
                return typeof parsed.color === "string" && parsed.color !== ""
                    ? { kind: "COLOR", color: parsed.color, vignette: Number.isFinite(vignette) ? vignette : 0 }
                    : null;
            }
            return null;
        } catch (e) {
            console.error("[BridgeAdapter] getClusterBackdropSource failed:", e);
            return null;
        }
    }

    /**
     * Persists a user preference value to Android SharedPreferences.
     * @param {string} key
     * @param {string} value
     */
    savePreference(key, value) {
        if (this.rawBridge && typeof this.rawBridge.savePreference === "function") {
            try {
                this.rawBridge.savePreference(key, String(value));
            } catch (e) {
                console.error(`[BridgeAdapter] savePreference failed for ${key} -> ${value}:`, e);
            }
        }
    }

    /**
     * Set the Display-1 cluster wallpaper via the v1.0 JNI bridge.
     * @param {string} type - THEME | PRESET | IMAGE_URL | FILE | YOUTUBE | WEB_URL
     * @param {string} value - type-specific path/url
     */
    setClusterBackground(type, value = "") {
        if (this.rawBridge && typeof this.rawBridge.setClusterBackground === "function") {
            try {
                this.rawBridge.setClusterBackground(String(type || "THEME"), String(value || ""));
            } catch (e) {
                console.error("[BridgeAdapter] setClusterBackground failed:", e);
            }
        } else {
            console.info("[BridgeAdapter] setClusterBackground unsupported (dev/browser).");
        }
    }

    /**
     * Announce the active theme package wallpaper (relative path).
     * Applied only when native mode is THEME.
     * @param {string} relativePath - e.g. "car-bg.png"
     */
    setThemeBackground(relativePath) {
        if (this.rawBridge && typeof this.rawBridge.setThemeBackground === "function") {
            try {
                this.rawBridge.setThemeBackground(String(relativePath || ""));
            } catch (e) {
                console.error("[BridgeAdapter] setThemeBackground failed:", e);
            }
        } else if (this.rawBridge && typeof this.rawBridge.setClusterBackground === "function") {
            // Fallback for older partial bridges
            this.setClusterBackground("THEME", relativePath);
        } else {
            console.info("[BridgeAdapter] setThemeBackground unsupported (dev/browser).");
        }
    }

    /**
     * Resets the adapter's listeners for HMR reload scenarios.
     */
    reset() {
        this.dataListeners.clear();
        this.keyListeners.clear();
    }

    // --- Internal Router Dispatchers ---
    dispatchDataChanged(key, value) {
        const listeners = this.dataListeners.get(key);
        if (listeners) {
            listeners.forEach(callback => {
                try {
                    callback(key, value);
                } catch (e) {
                    console.error(`[BridgeAdapter] Error in telemetry callback for key ${key}:`, e);
                }
            });
        }
        // No prefixed/bare alias dispatch here on purpose. It only ever matched because
        // bindThemeSetting used to register a listener under both spellings, so a single
        // push ran the same callback twice; nothing on the host emits the bare form.
    }

    dispatchKeyEvent(keyName) {
        this.keyListeners.forEach(listener => {
            try {
                listener(keyName);
            } catch (e) {
                console.error("[BridgeAdapter] Error in physical keyboard event callback:", e);
            }
        });
    }

    /**
     * Symmetrical Helper to bind a theme configuration setting to a state.
     * Loads the initial preference, sets it, and subscribes to real-time pushes.
     * @param {string} key - Setting state key.
     * @param {any} defaultValue - Default setting value.
     * @param {function} setStateFn - State writer (e.g. setState).
     */
    bindThemeSetting(key, defaultValue, setStateFn) {
        const isBool = typeof defaultValue === "boolean";
        const isNum = typeof defaultValue === "number";

        // 1. Load initial value from scoped preferences
        const raw = this.getPreference(key, String(defaultValue));
        const val = isBool ? (raw === "true") : (isNum ? Number(raw) : raw);
        console.log(`[BridgeAdapter DEBUG] bindThemeSetting key=${key} defaultValue=${defaultValue} raw=${raw} val=${val}`);
        setStateFn(key, val);

        // 2. Register the live preference push listener.
        //
        // Only the prefixed form. PreferencePushListener pushes exactly one name for a
        // theme config -- "app.preferences.<stateVariable>" -- and ThemeBridgeImpl echoes
        // subscriptions back under the same string the theme subscribed with, so the bare
        // name never arrives. Subscribing it anyway sent every stateVariable down to the
        // native subscribe(), which read it as a CAN signal and registered it with the
        // car's ControlService (22 bogus names across the two themes).
        const updateFn = (k, v) => {
            const parsed = isBool ? (v === "true" || v === true || v === "1" || v === 1) : (isNum ? Number(v) : v);
            setStateFn(key, parsed);
        };
        this.subscribe(`app.preferences.${key}`, updateFn);
    }
}

// Global Single Instance export
export const bridge = new ThemeBridgeAdapter();
