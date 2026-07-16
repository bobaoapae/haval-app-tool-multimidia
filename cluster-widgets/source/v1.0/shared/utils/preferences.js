/**
 * Generic theme preference helpers (Android SharedPreferences / browser localStorage).
 *
 * Themes can persist any string key/value and recover it on the next cold start.
 * Complex values should be JSON-encoded by the caller.
 *
 * Native bridge:
 *   window.Android.savePreference(key, value)
 *   window.Android.getPreference(key, defaultValue)
 *
 * Browser / simulator fallback:
 *   localStorage under LS_PREFIX + key
 */

const LS_PREFIX = 'haval_theme_pref_';

function hasAndroidPrefs() {
    return !!(
        typeof window !== 'undefined' &&
        window.Android &&
        typeof window.Android.getPreference === 'function' &&
        typeof window.Android.savePreference === 'function'
    );
}

/**
 * @param {string} key
 * @param {string} [fallback='']
 * @returns {string}
 */
export function loadPref(key, fallback = '') {
    const def = fallback == null ? '' : String(fallback);
    try {
        if (hasAndroidPrefs()) {
            const v = window.Android.getPreference(key, def);
            return v == null ? def : String(v);
        }
        if (typeof localStorage !== 'undefined') {
            const cached = localStorage.getItem(LS_PREFIX + key);
            return cached !== null ? cached : def;
        }
    } catch (e) {
        console.warn('[prefs] loadPref failed:', key, e);
    }
    return def;
}

/**
 * @param {string} key
 * @param {string|number|boolean} value
 */
export function savePref(key, value) {
    const str = value == null ? '' : String(value);
    try {
        if (hasAndroidPrefs()) {
            window.Android.savePreference(key, str);
            return;
        }
        if (typeof localStorage !== 'undefined') {
            localStorage.setItem(LS_PREFIX + key, str);
        }
    } catch (e) {
        console.warn('[prefs] savePref failed:', key, e);
    }
}

/**
 * Load a preference only if it is one of the allowed values.
 * @param {string} key
 * @param {string[]} allowed
 * @param {string} fallback
 * @returns {string}
 */
export function loadPrefIn(key, allowed, fallback) {
    const raw = loadPref(key, fallback);
    return Array.isArray(allowed) && allowed.includes(raw) ? raw : fallback;
}

/**
 * JSON helpers (optional convenience for themes).
 * @param {string} key
 * @param {*} fallback
 */
export function loadPrefJson(key, fallback = null) {
    const raw = loadPref(key, '');
    if (!raw) return fallback;
    try {
        return JSON.parse(raw);
    } catch (e) {
        console.warn('[prefs] loadPrefJson parse failed:', key, e);
        return fallback;
    }
}

/**
 * @param {string} key
 * @param {*} value
 */
export function savePrefJson(key, value) {
    try {
        savePref(key, JSON.stringify(value));
    } catch (e) {
        console.warn('[prefs] savePrefJson failed:', key, e);
    }
}
