package br.com.redesurftank.havalshisuku.bridge

import android.content.SharedPreferences
import android.webkit.WebView

interface IBridgeContext {
    val webView: WebView?
    val preferences: SharedPreferences
    val subscribedKeys: MutableSet<String>
    fun runOnUiThread(action: Runnable)
    fun updateWarningUI(isActive: Boolean)
    val isWarningActive: Boolean
    val isWarningDismissed: Boolean
    fun dismissWarnings()
    // No setCardId: the active card is owned by the car and flows one way only,
    // car -> backend -> theme via window.onCardChanged(cardId). A frontend->backend
    // card channel gave currentCard two writers and let a theme's view of the card
    // overwrite the car's.
    fun saveClusterDisplay(value: String)
    fun updateHeartbeat()
    fun refreshDisplayBounds()
    fun setNativeMaskState(maskName: String, visible: Boolean) {}
    fun setNativeMasksConfig(jsonConfig: String) {}
}

