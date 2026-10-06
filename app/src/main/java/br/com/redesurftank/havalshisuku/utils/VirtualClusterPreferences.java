package br.com.redesurftank.havalshisuku.utils;

import android.content.SharedPreferences;

import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;

/** Shared effective value for the UI, projector lifetime and display bounds. */
public final class VirtualClusterPreferences {
    private VirtualClusterPreferences() {}

    public static boolean isEnabled(SharedPreferences preferences) {
        // Keep the historical host default for installs that never stored this key.
        // Do not seed/migrate it: an explicitly saved false must always remain off.
        return preferences.getBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.getKey(), true);
    }
}
