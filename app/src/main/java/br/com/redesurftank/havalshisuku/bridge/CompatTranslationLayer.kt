package br.com.redesurftank.havalshisuku.bridge

import android.util.Log
import android.webkit.WebView
import br.com.redesurftank.havalshisuku.models.ThemeMetadata

object CompatTranslationLayer {
    private const val TAG = "CompatTranslation"
    const val CURRENT_BRIDGE_VERSION = "1.0.0"

    fun isBridgeVersionSupported(minBridgeVersion: String?): Boolean {
        if (minBridgeVersion.isNullOrBlank()) return true
        val required = parseSemanticVersion(minBridgeVersion) ?: return false
        val current = parseSemanticVersion(CURRENT_BRIDGE_VERSION) ?: return false
        return current >= required
    }

    private fun parseSemanticVersion(value: String): List<Int>? {
        val normalized = value.trim().removePrefix("v").substringBefore('-')
        val parts = normalized.split('.')
        if (parts.isEmpty() || parts.size > 3) return null
        val parsed = parts.map { it.toIntOrNull() ?: return null }.toMutableList()
        while (parsed.size < 3) parsed.add(0)
        return parsed
    }

    private operator fun List<Int>.compareTo(other: List<Int>): Int {
        for (index in 0..2) {
            val comparison = this[index].compareTo(other[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    fun injectPolyfillsIfNecessary(webView: WebView?, metadata: ThemeMetadata?) {
        if (webView == null) return
        val minVersion = metadata?.minBridgeVersion ?: "0.1.0"

        if (!isBridgeVersionSupported(minVersion)) {
            Log.e(
                TAG,
                "Refusing compatibility injection: theme requires bridge $minVersion, " +
                    "host provides $CURRENT_BRIDGE_VERSION"
            )
            return
        }

        Log.d(TAG, "Checking compat: theme minBridgeVersion = $minVersion, active BRIDGE_VERSION = $CURRENT_BRIDGE_VERSION")

        // We can dynamically inject polyfills to ensure backwards compatibility for older themes
        val jsPolyfills = StringBuilder()

        // Polyfill setUseDecentralizedScreens if theme might call it but JNI removed or deprecated it
        jsPolyfills.append("""
            if (window.Android) {
                if (typeof window.Android.setUseDecentralizedScreens !== 'function') {
                    window.Android.setUseDecentralizedScreens = function(enabled) {
                        console.log("Polyfill: setUseDecentralizedScreens called with " + enabled);
                    };
                }

                // Add robust safe-guarding for other newer JNI methods if the frontend code attempts to use them speculatively
                if (typeof window.Android.getAvailableKeys !== 'function') {
                    window.Android.getAvailableKeys = function() {
                        return JSON.stringify([
                            "car.basic.vehicle_speed",
                            "car.basic.gear_status",
                            "car.basic.inside_temp",
                            "car.basic.outside_temp"
                        ]);
                    };
                }
                if (typeof window.Android.setNativeMaskState !== 'function') {
                    window.Android.setNativeMaskState = function(maskName, visible) {
                        console.log("Polyfill: setNativeMaskState called (" + maskName + ", " + visible + ")");
                    };
                }
                if (typeof window.Android.setNativeMasksConfig !== 'function') {
                    window.Android.setNativeMasksConfig = function(config) {
                        console.log("Polyfill: setNativeMasksConfig called", config);
                    };
                }
            }
        """.trimIndent())

        webView.post {
            try {
                webView.evaluateJavascript("javascript:(function() { $jsPolyfills })()", null)
                Log.d(TAG, "Successfully evaluated compatibility polyfills inside WebView")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inject compatibility polyfills", e)
            }
        }
    }
}
