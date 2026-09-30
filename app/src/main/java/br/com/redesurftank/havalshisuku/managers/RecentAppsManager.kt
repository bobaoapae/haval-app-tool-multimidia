package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import br.com.redesurftank.App
import org.json.JSONArray

/**
 * Manages the persistent list of recent apps displayed in the revamped bottom bar dock.
 * Keeps up to 3 most recently opened packages, seeded with sensible defaults.
 */
object RecentAppsManager {
    private const val PREFS_KEY = "bottom_bar_recent_apps"
    private const val MAX_RECENTS = 3

    val DEFAULT_RECENTS = listOf(
        "com.google.android.apps.maps",
        "com.spotify.music",
        "com.waze"
    )

    private val ignoredPackages = setOf(
        "br.com.redesurftank.havalshisuku",
        "android",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.settings",
        "com.beantechs.launcher",
        "com.beantechs.applist"
    )

    val recentApps = mutableStateListOf<String>()

    init {
        loadRecents()
    }

    fun loadRecents() {
        try {
            val context = App.getDeviceProtectedContext()
            val prefs = context.getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(PREFS_KEY, null)
            val list = mutableListOf<String>()
            if (!jsonStr.isNullOrEmpty()) {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val pkg = array.optString(i)
                    if (pkg.isNotEmpty() && !ignoredPackages.contains(pkg) && !list.contains(pkg)) {
                        list.add(pkg)
                    }
                }
            }
            if (list.isEmpty()) {
                list.addAll(DEFAULT_RECENTS)
            }
            recentApps.clear()
            recentApps.addAll(list.take(MAX_RECENTS))
        } catch (_: Exception) {
            recentApps.clear()
            recentApps.addAll(DEFAULT_RECENTS)
        }
    }

    fun recordAppLaunch(packageName: String?) {
        if (packageName.isNullOrBlank() || ignoredPackages.contains(packageName)) return
        val current = recentApps.toMutableList()
        current.remove(packageName)
        current.add(0, packageName)
        val trimmed = current.take(MAX_RECENTS)

        recentApps.clear()
        recentApps.addAll(trimmed)

        try {
            val context = App.getDeviceProtectedContext()
            val prefs = context.getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            val jsonArray = JSONArray(trimmed)
            prefs.edit().putString(PREFS_KEY, jsonArray.toString()).apply()
        } catch (_: Exception) {}
    }
}
