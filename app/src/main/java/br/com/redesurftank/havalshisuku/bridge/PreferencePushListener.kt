package br.com.redesurftank.havalshisuku.bridge

import android.content.SharedPreferences
import android.util.Log

class PreferencePushListener(
    private val context: IBridgeContext,
    private val pushValue: (String, String) -> Unit
) : SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null || sharedPreferences == null) return
        
        // Must match the identifier ThemeSettingsDialog scopes its saves with
        // (theme.folderName, e.g. "minimalist"), NOT VIRTUAL_CLUSTER_THEME
        // (theme.name display label, e.g. "Minimalist") — those differ in case
        // for every non-Default theme, so this listener never matched the prefix.
        val activeCustomTheme = sharedPreferences.getString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "") ?: ""
        val activeTheme = activeCustomTheme.ifBlank { "Default" }

        // 1. Scoped theme configuration change for the active theme
        val prefix = "theme_config_${activeTheme}_"
        if (key.startsWith(prefix)) {
            val stateVar = key.substring(prefix.length)
            val virtualKey = "app.preferences.$stateVar"
            if (context.subscribedKeys.contains(virtualKey)) {
                val value = try {
                    sharedPreferences.all[key]?.toString() ?: ""
                } catch (e: Exception) {
                    ""
                }
                Log.d("PreferencePushListener", "Scoped Preference changed, pushing: $virtualKey = $value")
                pushValue(virtualKey, value)
                return
            }
        }

        // 2. Unscoped legacy/global preference change
        val virtualKey = "app.preferences.$key"
        if (context.subscribedKeys.contains(virtualKey)) {
            val value = try {
                sharedPreferences.all[key]?.toString() ?: ""
            } catch (e: Exception) {
                ""
            }
            Log.d("PreferencePushListener", "Preference changed, pushing: $virtualKey = $value")
            pushValue(virtualKey, value)
        }
    }
}
