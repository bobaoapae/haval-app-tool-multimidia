package br.com.redesurftank.havalshisuku.bridge

import android.util.Log
import android.webkit.WebView
import br.com.redesurftank.havalshisuku.models.ThemeMetadata

object CompatTranslationLayer {
    private const val TAG = "CompatTranslation"
    const val CURRENT_BRIDGE_VERSION = "1.0.0"

    fun injectPolyfillsIfNecessary(webView: WebView?, metadata: ThemeMetadata?) {
        if (webView == null) return
        val minVersion = metadata?.minBridgeVersion ?: "0.1.0"
        
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
