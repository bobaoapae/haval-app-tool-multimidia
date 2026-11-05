/** Browser-only global AC focus preference mock. */
export function createClusterAcFocusMock(getStorage) {
    const key = 'haval_mock_cluster_ac_focus';
    let focus = 'fan';
    return {
        getClusterAcFocus() {
            try {
                const storage = getStorage();
                if (storage) focus = storage.getItem(key) === 'temp' ? 'temp' : 'fan';
            } catch (_) { /* Retain memory while browser storage is unavailable. */ }
            return focus;
        },
        saveClusterAcFocus(value) {
            if (value !== 'fan' && value !== 'temp') return false;
            focus = value;
            try { getStorage()?.setItem(key, value); }
            catch (_) { /* Retain memory while browser storage is unavailable. */ }
            return true;
        }
    };
}
