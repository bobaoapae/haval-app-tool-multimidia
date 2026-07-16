package br.com.redesurftank.havalshisuku.projectors

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.SharedPreferences
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType
import coil.imageLoader
import coil.request.ImageRequest

class InstrumentProjector(outerContext: Context, display: Display) : BaseProjector(outerContext, display) {
    private val TAG = "InstrumentProjector"
    private lateinit var rootLayout: RelativeLayout
    private lateinit var imageView: ImageView
    private var webView: WebView? = null
    
    private var isAnyAppOnDisplay1 = false

    private val sharedPreferences by lazy {
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key ||
            key == SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key ||
            key == SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key ||
            key == SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key ||
            key == SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key
        ) {
            ensureUi {
                applyCustomBackground()
                updateBackgroundVisibility()
            }
        }
    }

    private val eventListener =
        br.com.redesurftank.havalshisuku.listeners.IServiceManagerEvent { event, args ->
            ensureUi {
                when (event) {
                    ServiceManagerEventType.DISPLAY_1_APP_STATE_CHANGED -> {
                        isAnyAppOnDisplay1 = args[0] as Boolean
                        Log.d(TAG, "Display 1 app state changed: $isAnyAppOnDisplay1")
                        updateBackgroundVisibility()
                    }
                    else -> {}
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        rootLayout = RelativeLayout(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        imageView = ImageView(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            isVisible = false
        }

        val wv = WebView(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false // Allow autoplay without user touch
            }
            webViewClient = WebViewClient()
            setBackgroundColor(Color.TRANSPARENT)
            isVisible = false
        }
        
        webView = wv

        rootLayout.addView(imageView)
        rootLayout.addView(wv)
        setContentView(rootLayout)

        isAnyAppOnDisplay1 = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isAnyAppOnDisplay(1)

        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)
        ServiceManager.getInstance().addServiceManagerEventListener(eventListener)

        updateBackgroundVisibility()
        Log.d(TAG, "InstrumentProjector (Display 1 Refresh Layer) created")
    }

    private fun applyCustomBackground() {
        // Default ON: theme wallpaper when available
        val isEnabled = sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
        if (!isEnabled) {
            imageView.isVisible = false
            webView?.isVisible = false
            rootLayout.setBackgroundColor(Color.TRANSPARENT)
            return
        }

        val type = sharedPreferences.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME") ?: "THEME"
        val value = sharedPreferences.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "") ?: ""

        Log.d(TAG, "Applying background type: $type, value: $value")

        when (type) {
            "THEME" -> {
                webView?.isVisible = false
                imageView.isVisible = true
                if (!loadThemeBackground(value)) {
                    // Fall back to solid black if the active theme has no wallpaper
                    imageView.setImageDrawable(null)
                    imageView.setBackgroundColor(Color.BLACK)
                }
            }
            "PRESET" -> {
                webView?.isVisible = false
                imageView.isVisible = true
                if (value.isNotEmpty()) {
                    loadPresetBackground(value)
                } else {
                    imageView.setImageDrawable(null)
                    imageView.setBackgroundColor(Color.BLACK)
                }
            }
            "FILE" -> {
                webView?.isVisible = false
                imageView.isVisible = true
                if (value.isNotEmpty()) {
                    loadFileBackground(value)
                } else {
                    imageView.setImageDrawable(null)
                    imageView.setBackgroundColor(Color.BLACK)
                }
            }
            "IMAGE_URL" -> {
                webView?.isVisible = false
                imageView.isVisible = true
                if (value.isNotEmpty()) {
                    loadRemoteImage(value)
                } else {
                    imageView.setImageDrawable(null)
                    imageView.setBackgroundColor(Color.BLACK)
                }
            }
            "WEB_URL" -> {
                imageView.isVisible = false
                webView?.let { wv ->
                    wv.isVisible = true
                    if (value.isNotEmpty() && wv.url != value) {
                        wv.loadUrl(value)
                    }
                }
            }
            "YOUTUBE" -> {
                imageView.isVisible = false
                webView?.let { wv ->
                    wv.isVisible = true
                    if (value.isNotEmpty()) {
                        val videoId = extractYoutubeVideoId(value)
                        if (videoId != null) {
                            val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&mute=1&loop=1&playlist=$videoId&controls=0&modestbranding=1&rel=0"
                            if (wv.url != embedUrl) {
                                wv.loadUrl(embedUrl)
                            }
                        } else {
                            wv.loadUrl("about:blank")
                        }
                    } else {
                        wv.loadUrl("about:blank")
                    }
                }
            }
            else -> {
                imageView.isVisible = false
                webView?.isVisible = false
                rootLayout.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    private fun loadThemeBackground(relativeHint: String): Boolean {
        return try {
            val file = br.com.redesurftank.havalshisuku.managers.ThemeManager
                .getInstance(context)
                .getActiveThemeBackgroundFile(relativeHint.ifBlank { null })
            if (file == null) {
                Log.w(TAG, "No theme background available (hint=$relativeHint)")
                return false
            }
            loadFileBackground(file.absolutePath)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading theme background", e)
            false
        }
    }

    private fun loadFileBackground(path: String) {
        try {
            val file = java.io.File(path)
            if (!file.exists()) {
                Log.e(TAG, "Background file missing: $path")
                imageView.setImageDrawable(null)
                imageView.setBackgroundColor(Color.BLACK)
                return
            }
            val drawable = android.graphics.drawable.Drawable.createFromPath(file.absolutePath)
            if (drawable != null) {
                imageView.setImageDrawable(drawable)
            } else {
                imageView.setImageDrawable(null)
                imageView.setBackgroundColor(Color.BLACK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading file background: $path", e)
            imageView.setImageDrawable(null)
            imageView.setBackgroundColor(Color.BLACK)
        }
    }

    private fun loadPresetBackground(fileName: String) {
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("backgrounds/$fileName")
            val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
            imageView.setImageDrawable(drawable)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preset background from assets: $fileName", e)
            imageView.setImageDrawable(null)
            imageView.setBackgroundColor(Color.BLACK)
        }
    }

    private fun loadRemoteImage(url: String) {
        try {
            val imageRequest = ImageRequest.Builder(context)
                .data(url)
                .target(imageView)
                .build()
            context.imageLoader.enqueue(imageRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading remote image: $url", e)
            imageView.setImageDrawable(null)
            imageView.setBackgroundColor(Color.BLACK)
        }
    }

    private fun extractYoutubeVideoId(url: String): String? {
        if (url.length == 11) return url
        val patterns = listOf(
            Regex("v=([^&]+)"),
            Regex("youtu\\.be/([^?#]+)"),
            Regex("embed/([^?#]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun updateBackgroundVisibility() {
        val isEnabled = sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
        val isScreenOn = ServiceManager.getInstance().isMainScreenOn
        
        Log.d(TAG, "Visibility check: isAnyAppOnDisplay1=$isAnyAppOnDisplay1, isScreenOn=$isScreenOn, isEnabled=$isEnabled")

        if (isAnyAppOnDisplay1 || !isScreenOn || !isEnabled) {
            rootLayout.isVisible = false
            webView?.onPause()
        } else {
            rootLayout.isVisible = true
            webView?.onResume()
            applyCustomBackground()
        }
    }

    override fun carMainScreenOff() {
        ensureUi {
            updateBackgroundVisibility()
        }
    }

    override fun carMainScreenOn() {
        ensureUi {
            updateBackgroundVisibility()
        }
    }

    override fun onStop() {
        super.onStop()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        ServiceManager.getInstance().removeServiceManagerEventListener(eventListener)
        
        webView?.let { wv ->
            rootLayout.removeView(wv)
            wv.stopLoading()
            wv.clearHistory()
            wv.clearCache(true)
            wv.loadUrl("about:blank")
            wv.onPause()
            wv.removeAllViews()
            wv.destroy()
        }
        webView = null
    }

    override fun cancel() {
        super.cancel()
    }
}
