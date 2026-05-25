package br.com.redesurftank.havalshisuku.bridge

import android.content.SharedPreferences
import android.webkit.WebView

interface IBridgeContext {
    val webView: WebView?
    val preferences: SharedPreferences
    val subscribedKeys: MutableSet<String>
    fun runOnUiThread(action: Runnable)
    fun updateWarningUI(isActive: Boolean)
    fun setCardId(cardId: Int)
    fun saveClusterDisplay(value: String)
    fun updateHeartbeat()
}
