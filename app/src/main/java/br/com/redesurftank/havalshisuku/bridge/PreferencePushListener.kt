package br.com.redesurftank.havalshisuku.bridge

import android.content.SharedPreferences
import android.util.Log

class PreferencePushListener(
    private val context: IBridgeContext,
    private val pushValue: (String, String) -> Unit
) : SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null || sharedPreferences == null) return
        
        val activeTheme = sharedPreferences.getString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, "Default") ?: "Default"
        
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
