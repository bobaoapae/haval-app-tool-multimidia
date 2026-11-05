/** Optional global UI preference bridge (1.0.2). No telemetry or HVAC writes belong here. */
export function createAcFocusPreference(getAndroid, warn = console.warn) {
    function bridge() {
        const candidate = getAndroid();
        if (!candidate || typeof candidate.getClusterAcFocus !== 'function' ||
            typeof candidate.saveClusterAcFocus !== 'function') {
            warn('[AC focus] Persistence bridge unavailable; using in-memory focus.');
            return null;
        }
        return candidate;
    }
    return {
        available() {
            try {
                const target = getAndroid();
                return !!target && typeof target.getClusterAcFocus === 'function' &&
                    typeof target.saveClusterAcFocus === 'function';
            } catch (_) { return false; }
        },
        restore() {
            try {
                const target = bridge();
                if (!target) return 'fan';
                const value = target.getClusterAcFocus();
                if (value === 'fan' || value === 'temp') return value;
                warn('[AC focus] Invalid stored focus; using fan.');
            } catch (error) { warn('[AC focus] Restore failed; using fan.', error); }
            return 'fan';
        },
        save(focus) {
            if (focus !== 'fan' && focus !== 'temp') return false;
            try {
                const target = bridge();
                if (target && target.saveClusterAcFocus(focus) === true) return true;
                warn('[AC focus] Focus remains in memory; persistence not accepted.');
            } catch (error) { warn('[AC focus] Save failed; focus remains in memory.', error); }
            return false;
        }
    };
}
