package br.com.redesurftank.havalshisuku.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.redesurftank.havalshisuku.ImpulseDashboardActivity
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.BottomBarContent
import br.com.redesurftank.havalshisuku.ui.components.BottomBarMenus
import br.com.redesurftank.havalshisuku.ui.theme.HavalShisukuTheme
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Proxy
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

class BottomBarService : LifecycleService() {

    private var mWindowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var menuComposeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var isMenuWindowAdded = false

    private var monitoringJob: Job? = null
    private var autoHideJob: Job? = null
    private var lastPackage: String? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var mediaAccessMonitorJob: Job? = null
    private var mediaMetadataPublishJob: Job? = null
    private var carPlayNowPlayingMonitor: CarPlayNowPlayingMonitor? = null
    private var androidAutoNowPlayingMonitor: AndroidAutoNowPlayingMonitor? = null
    private var carPlayUsbDisconnectMonitorJob: Job? = null
    private var lastCarPlayUsbReadyState: Boolean? = null
    private val mediaControllerLock = Any()
    private val mediaControllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val androidAutoMediaCommandLock = Any()
    private var lastMediaDebugSignature: String? = null
    private var lastCarPlayMediaSignature: String? = null
    private var lastAndroidAutoMediaSignature: String? = null
    private var lastAndroidAutoMediaCommandName: String? = null
    private var lastAndroidAutoMediaCommandAtMs: Long = 0L

    data class BarSettings(val overscan: Int, val yOffset: Int)

    // Hardcoded overrides for density-aware apps that auto-scale overscan
    // These values are in DP and will be scaled by density
    /*
    private val APP_OVERRIDES =
            mapOf(
                    "com.google.android.youtube" to BarSettings(0, 0),
                    "com.google.android.apps.maps" to BarSettings(0, 60),
                    "com.google.android.apps.youtube.music" to BarSettings(0, 0),
                    "com.google.android.apps.messaging" to BarSettings(60, 0),
                    "deezer.android.app" to BarSettings(60, 0),
            )
    */

    private val IGNORE_PACKAGES =
            setOf<String>(
                    // "com.beantechs.applist",
                    // "com.beantechs.mediacenter"
                    )

    private val BOTTOM_BAR_BASE_HEIGHT_DP = 60f
    private val REFERENCE_OVERSCAN = 20

    override fun onCreate() {
        android.util.Log.e("BottomBarService", "SERVICE ONCREATE - STARTING")
        super.onCreate()
        instance = this

        // Initialize state from SharedPreferences
        val prefs =
                br.com.redesurftank.App.getDeviceProtectedContext()
                        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        BottomBarState.autoHideEnabled =
                prefs.getBoolean(SharedPreferencesKeys.BOTTOM_BAR_AUTO_HIDE.key, false)

        BottomBarState.isVisible = true

        // Initial check for Frida status
        updateFridaStatus(prefs)

        showBottomBar()
        observeMenuState()
        observeDashboardActivityState()
        observeVisibility()
        observeAutoHide()
        registerUpdateReceiver()
        startMediaMetadataMonitoring()
        startMediaAccessMonitoring()
        startCarPlayNowPlayingMonitoring()
        startCarPlayUsbDisconnectMonitoring()
        startAndroidAutoNowPlayingMonitoring()
        startDynamicOverscanMonitoring()
        ensureAccessibilityServiceEnabled()
        // startAppMonitoring() // Disabled: Legacy focus watchdog replaced by permanent Frida hook

        // Initial timer start
        resetAutoHideTimer()

    }


    private fun registerUpdateReceiver() {
        val filter =
                android.content.IntentFilter("br.com.redesurftank.havalshisuku.UPDATE_BAR_POSITION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
    }

    private val updateReceiver =
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: android.content.Intent?) {
                    val overscan = intent?.getIntExtra("overscan", -1) ?: -1
                    val offset = intent?.getIntExtra("offset", -101) ?: -101

                    if (overscan != -1 || offset != -101) {
                        // Real-time update from Apply button
                        val currentPackage = BottomBarState.currentPackage
                        if (currentPackage.isNotEmpty()) {
                            val settings =
                                    BarSettings(
                                            overscan =
                                                    if (overscan != -1) overscan
                                                    else (currentAppSettings?.overscan ?: 0),
                                            yOffset =
                                                    if (offset != -101) offset
                                                    else (currentAppSettings?.yOffset ?: 0)
                                    )
                            currentAppSettings = settings
                            applyAppSettings(settings)
                        }
                    } else {
                        // Reload from SharedPreferences (Save button or generic refresh)
                        lastPackage = null // Force reload in monitoring loop
                    }
                }
            }

    private fun observeAutoHide() {
        lifecycleScope.launch {
            // Reset timer on any state change that might indicate activity
            snapshotFlow {
                listOf(
                        BottomBarState.isVisible,
                        BottomBarState.isDashboardExpanded,
                        BottomBarState.isMenuExpanded,
                        BottomBarState.isSettingsMenuExpanded,
                        BottomBarState.isOverrideMenuExpanded,
                        BottomBarState.activeSliderType != null
                )
            }
                    .collectLatest { resetAutoHideTimer() }
        }
    }

    fun resetAutoHideTimer() {
        autoHideJob?.cancel()
        if (!BottomBarState.autoHideEnabled || !BottomBarState.isVisible) return

        autoHideJob =
                lifecycleScope.launch {
                    delay(30000) // 30 seconds
                    if (BottomBarState.isVisible &&
                                    !BottomBarState.isDashboardExpanded &&
                                    !BottomBarState.isMenuExpanded &&
                                    !BottomBarState.isSettingsMenuExpanded &&
                                    !BottomBarState.isOverrideMenuExpanded &&
                                    BottomBarState.activeSliderType == null
                    ) {
                        BottomBarState.isVisible = false
                    }
                }
    }

    private fun ensureAccessibilityServiceEnabled() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Wait for Shizuku
                var retry = 0
                while (!ShizukuUtils.isShizukuAvailable() && retry < 20) {
                    delay(1000)
                    retry++
                }

                if (!ShizukuUtils.isShizukuAvailable()) return@launch

                val currentServices =
                        ShizukuUtils.runCommandAndGetOutput(
                                        arrayOf(
                                                "sh",
                                                "-c",
                                                "settings get secure enabled_accessibility_services"
                                        )
                                )
                                .trim()
                val ourService =
                        "${packageName}/br.com.redesurftank.havalshisuku.services.AccessibilityService"

                if (!currentServices.contains(ourService)) {
                    val newServices =
                            if (currentServices == "null" || currentServices.isEmpty()) {
                                ourService
                            } else {
                                "$currentServices:$ourService"
                            }
                    ShizukuUtils.runCommandAndGetOutput(
                            arrayOf(
                                    "sh",
                                    "-c",
                                    "settings put secure enabled_accessibility_services '$newServices'"
                            )
                    )
                    ShizukuUtils.runCommandAndGetOutput(
                            arrayOf("sh", "-c", "settings put secure accessibility_enabled 1")
                    )
                    Log.w("AccessibilityService", "Auto-enabled accessibility service via Shizuku")
                } else {
                    Log.d("AccessibilityService", "Accessibility service is already enabled")
                }
            } catch (e: Exception) {
                Log.e("AccessibilityService", "Failed to auto-enable accessibility service", e)
            }
        }
    }

    private var currentAppSettings: BarSettings? = null

    private fun startDynamicOverscanMonitoring() {
        monitoringJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    while (isActive) {
                        try {
                            val currentPackage = getTopPackageOnDisplay(0)
                            val activeClusterProjectionPackage =
                                    DisplayAppLauncher.resolveActiveProjectionPackageForDisplay(3)
                            if (currentPackage != null) {
                                withContext(Dispatchers.Main) {
                                    BottomBarState.activeClusterProjectionPackage =
                                            activeClusterProjectionPackage ?: ""
                                    if (activeClusterProjectionPackage != null &&
                                                    BottomBarState.selectedPackage !=
                                                            activeClusterProjectionPackage
                                    ) {
                                        BottomBarState.selectedPackage =
                                                activeClusterProjectionPackage
                                    }
                                    if (BottomBarState.currentPackage != currentPackage) {
                                        BottomBarState.currentPackage = currentPackage
                                        // Auto-select the current app if it's not a launcher or in the ignore list
                                        if (activeClusterProjectionPackage == null &&
                                                        !IGNORE_PACKAGES.contains(currentPackage) &&
                                                        !isLauncher(currentPackage)
                                        ) {
                                            BottomBarState.selectedPackage = currentPackage
                                        }
                                    }
                                }
                            } else {
                                // If we can't find Display 0 package, use tool package as fallback
                                // to apply default overscan
                                withContext(Dispatchers.Main) {
                                    BottomBarState.activeClusterProjectionPackage =
                                            activeClusterProjectionPackage ?: ""
                                    if (activeClusterProjectionPackage != null) {
                                        BottomBarState.selectedPackage =
                                                activeClusterProjectionPackage
                                    }
                                    BottomBarState.currentPackage =
                                            this@BottomBarService.packageName
                                }
                            }

                            // Background Cleanup: Remove apps that are no longer running from the
                            // restored set
                            if (BottomBarState.restoredApps.isNotEmpty()) {
                                val stackList =
                                        ShizukuUtils.runCommandAndGetOutput(
                                                arrayOf("am", "stack", "list")
                                        )
                                val missingApps =
                                        BottomBarState.restoredApps.filter { pkg ->
                                            !stackList.contains(pkg)
                                        }
                                if (missingApps.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        BottomBarState.restoredApps.removeAll(missingApps)
                                    }
                                }
                            }

                            val prefs =
                                    br.com.redesurftank.App.getDeviceProtectedContext()
                                            .getSharedPreferences(
                                                    "haval_prefs",
                                                    Context.MODE_PRIVATE
                                            )

                            if (currentPackage != null && currentPackage != lastPackage) {
                                lastPackage = currentPackage

                                // Default overscan is back to REFERENCE_OVERSCAN (60)
                                val storedDefault =
                                        prefs.getInt(
                                                SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN
                                                        .key,
                                                REFERENCE_OVERSCAN
                                        )

                                // Also update autoHideEnabled from prefs
                                withContext(Dispatchers.Main) {
                                    BottomBarState.autoHideEnabled =
                                            prefs.getBoolean(
                                                    SharedPreferencesKeys.BOTTOM_BAR_AUTO_HIDE.key,
                                                    false
                                            )
                                }

                                val settings = getSettingsForPackage(currentPackage, storedDefault)
                                currentAppSettings = settings
                                applyAppSettings(settings)
                            }

                            // Update Frida status reactive to switches
                            updateFridaStatus(prefs)
                        } catch (e: Exception) {
                            Log.e("BottomBarService", "Error in monitoring loop", e)
                        }
                        delay(1000)
                    }
                }
    }

    private fun updateFridaStatus(prefs: android.content.SharedPreferences) {
        val hooksEnabled = prefs.getBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.key, false)

        // Only require the main Frida switch to be enabled as requested
        val switchesOn = hooksEnabled

        lifecycleScope.launch(Dispatchers.IO) {
            // UI shows Frida menu if main switch is ON
            withContext(Dispatchers.Main) { BottomBarState.isFridaRunning = switchesOn }
        }
    }

    private fun startMediaMetadataMonitoring() {
        if (mediaSessionManager != null) return
        val manager =
                getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        mediaSessionManager = manager
        val notificationListener =
                getMediaNotificationListenerComponent()
                        .takeIf { isMediaNotificationListenerEnabled() }

        val listener =
                MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    updateMediaControllers(controllers.orEmpty())
                }
        mediaSessionsListener = listener

        try {
            val controllers = manager.getActiveSessions(notificationListener)
            updateMediaControllers(controllers.orEmpty())
            manager.addOnActiveSessionsChangedListener(listener, notificationListener)
            Log.i("BottomBarService", "Media session metadata listener registered")
        } catch (e: SecurityException) {
            Log.w(
                    "BottomBarService",
                    "Media session metadata unavailable: MEDIA_CONTENT_CONTROL or notification listener not granted"
            )
            mediaSessionManager = null
            mediaSessionsListener = null
            clearMediaState()
            requestNotificationListenerEnableForMedia()
        } catch (e: Exception) {
            Log.e("BottomBarService", "Failed to start media metadata monitoring", e)
            mediaSessionManager = null
            mediaSessionsListener = null
            clearMediaState()
        }
    }

    private fun startMediaAccessMonitoring() {
        if (mediaAccessMonitorJob != null) return
        mediaAccessMonitorJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    while (isActive) {
                        val changed = ensureNotificationListenerEnabledForMedia()
                        if (changed || mediaSessionManager == null) {
                            withContext(Dispatchers.Main) { restartMediaMetadataMonitoring() }
                        }
                        delay(30_000)
                    }
                }
    }

    private fun startCarPlayNowPlayingMonitoring() {
        if (carPlayNowPlayingMonitor != null) return
        carPlayNowPlayingMonitor =
                CarPlayNowPlayingMonitor(this, lifecycleScope) { update ->
                    if (update.clear) {
                        clearCarPlayMediaState("now playing cleared")
                        return@CarPlayNowPlayingMonitor
                    }
                    val previousSignature = lastCarPlayMediaSignature
                    val nextSignature =
                            carPlayMediaSignature(update.title, update.artist, update.artworkPath)
                    val sourceChanged = BottomBarState.mediaPackageName != CARPLAY_MEDIA_PACKAGE
                    val trackChanged =
                            nextSignature != null &&
                                    previousSignature != null &&
                                    nextSignature != previousSignature

                    if (update.title != null) {
                        BottomBarState.mediaTitle = update.title
                    } else if (sourceChanged) {
                        BottomBarState.mediaTitle = null
                    }

                    if (update.artist != null) {
                        BottomBarState.mediaArtist = update.artist
                    } else if (sourceChanged) {
                        BottomBarState.mediaArtist = null
                    }

                    if (sourceChanged || trackChanged) {
                        BottomBarState.mediaAlbum = null
                    }

                    if (sourceChanged || trackChanged || update.artwork != null) {
                        BottomBarState.mediaArtwork = update.artwork
                    }

                    BottomBarState.mediaPackageName = CARPLAY_MEDIA_PACKAGE
                    BottomBarState.mediaIsPlaying = update.isPlaying
                    updateMediaProgressState(
                            durationMs = update.durationMs,
                            elapsedMs = update.elapsedMs,
                            canSeek = update.durationMs > 0L
                    )
                    if (nextSignature != null) {
                        lastCarPlayMediaSignature = nextSignature
                    }
                }
                        .also { it.start() }
    }

    private fun startCarPlayUsbDisconnectMonitoring() {
        if (carPlayUsbDisconnectMonitorJob != null) return
        carPlayUsbDisconnectMonitorJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    while (isActive) {
                        val rawState = readProjectionUsbState()
                        if (rawState != null) {
                            val usbReady = isProjectionUsbReadyForMedia(rawState)
                            val previous = lastCarPlayUsbReadyState
                            if (previous != usbReady) {
                                Log.i(
                                        "BottomBarService",
                                        "Projection USB media state changed from " +
                                                "${previous ?: "UNKNOWN"} to $usbReady " +
                                                "raw=${rawState.lineSequence().firstOrNull()?.trim().orEmpty()}"
                                )
                                lastCarPlayUsbReadyState = usbReady
                            }
                            if (!usbReady) {
                                DisplayAppLauncher
                                        .cleanupStaleAndroidAutoVisualStacksIfDisconnected(
                                                "projection USB disconnected"
                                        )
                                withContext(Dispatchers.Main) {
                                    if (shouldClearCarPlayMediaOnUsbState(
                                                    BottomBarState.mediaPackageName,
                                                    rawState
                                            )
                                    ) {
                                        clearCarPlayMediaState("projection USB disconnected")
                                    }
                                    if (shouldClearAndroidAutoMediaOnUsbState(
                                                    BottomBarState.mediaPackageName,
                                                    rawState,
                                                    androidAutoSessionReady =
                                                            isAndroidAutoMediaSessionReadyForDashboard()
                                            )
                                    ) {
                                        clearAndroidAutoMediaState("projection USB disconnected")
                                    }
                                }
                            }
                        }
                        delay(CARPLAY_USB_MEDIA_STATE_POLL_MS)
                    }
                }
    }

    private fun readProjectionUsbState(): String? {
        val localState =
                runCatching { File(PROJECTION_USB_STATE_PATH).readText().trim() }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
        if (localState != null) return localState

        if (!ShizukuUtils.isShizukuAvailable()) return null
        return ShizukuUtils.runCommandAndGetOutput(
                        arrayOf("sh", "-c", "cat $PROJECTION_USB_STATE_PATH 2>/dev/null || true")
                )
                .trim()
                .takeIf { it.isNotBlank() }
    }

    private fun startAndroidAutoNowPlayingMonitoring() {
        if (androidAutoNowPlayingMonitor != null) return
        androidAutoNowPlayingMonitor =
                AndroidAutoNowPlayingMonitor(this, lifecycleScope) { update ->
                    if (!isAndroidAutoMediaSessionReadyForDashboard()) {
                        clearAndroidAutoMediaState("Android Auto projection session not ready")
                        return@AndroidAutoNowPlayingMonitor
                    }

                    if (update.clear) {
                        if (DisplayAppLauncher.isAndroidAutoProjectionSessionReadyForMedia("AA_NOW_PLAYING_CLEAR")) {
                            Log.w(
                                    "BottomBarService",
                                    "Holding Android Auto media metadata during active projection clear"
                            )
                            if (isAndroidAutoMediaPackage(BottomBarState.mediaPackageName)) {
                                BottomBarState.mediaIsPlaying = false
                            }
                            return@AndroidAutoNowPlayingMonitor
                        }
                        clearAndroidAutoMediaState("now playing cleared")
                        return@AndroidAutoNowPlayingMonitor
                    }

                    val sourceChanged =
                            !isAndroidAutoMediaPackage(BottomBarState.mediaPackageName)
                    val previousSignature = lastAndroidAutoMediaSignature
                    val nextSignature = update.metadataSignature
                    val trackChanged =
                            nextSignature != null &&
                                    previousSignature != null &&
                                    nextSignature != previousSignature
                    val hasMetadataUpdate =
                            update.title != null ||
                                    update.artist != null ||
                                    update.album != null ||
                                    update.artwork != null ||
                                    update.durationMs != null ||
                                    nextSignature != null
                    val hasPlaybackUpdate =
                            update.isPlaying != null ||
                                    update.elapsedMs != null ||
                                    update.durationMs != null

                    if (!hasMetadataUpdate && sourceChanged) {
                        return@AndroidAutoNowPlayingMonitor
                    }

                    if (update.title != null) {
                        BottomBarState.mediaTitle = update.title
                    } else if (sourceChanged && (hasMetadataUpdate || hasPlaybackUpdate)) {
                        BottomBarState.mediaTitle = null
                    }

                    if (update.artist != null) {
                        BottomBarState.mediaArtist = update.artist
                    } else if (sourceChanged && (hasMetadataUpdate || hasPlaybackUpdate)) {
                        BottomBarState.mediaArtist = null
                    }

                    if (update.album != null) {
                        BottomBarState.mediaAlbum = update.album
                    } else if ((sourceChanged || trackChanged) && (hasMetadataUpdate || hasPlaybackUpdate)) {
                        BottomBarState.mediaAlbum = null
                    }

                    if (update.artwork != null) {
                        BottomBarState.mediaArtwork = update.artwork
                    } else if ((sourceChanged || trackChanged) && hasMetadataUpdate) {
                        Log.d(
                                "BottomBarService",
                                "Holding Android Auto artwork until new bitmap or session clear arrives"
                        )
                    }

                    BottomBarState.mediaPackageName = ANDROID_AUTO_MEDIA_PACKAGE
                    update.isPlaying?.let { BottomBarState.mediaIsPlaying = it }

                    val durationMs = update.durationMs ?: BottomBarState.mediaDurationMs
                    val elapsedMs = update.elapsedMs ?: BottomBarState.mediaElapsedMs
                    updateMediaProgressState(
                            durationMs = durationMs,
                            elapsedMs = elapsedMs,
                            updatedAtMs = update.progressUpdatedAtMs,
                            canSeek = false
                    )

                    if (nextSignature != null) {
                        lastAndroidAutoMediaSignature = nextSignature
                    }
                }
                        .also { it.start() }
    }

    private fun isAndroidAutoMediaSessionReadyForDashboard(): Boolean {
        val rawUsbState = readProjectionUsbState()
        if (rawUsbState != null) {
            if (isProjectionUsbReadyForMedia(rawUsbState)) return true
            if (DisplayAppLauncher.isAndroidAutoProjectionLinkActiveIfAlreadyBoundForMedia(
                            "AA_NOW_PLAYING_UPDATE"
                    )
            ) {
                return true
            }
            return DisplayAppLauncher.isAndroidAutoProjectionSessionReadyForMedia(
                    "AA_NOW_PLAYING_UPDATE_WIRELESS"
            )
        }
        return DisplayAppLauncher.isAndroidAutoProjectionSessionReadyForMedia("AA_NOW_PLAYING_UPDATE")
    }

    private suspend fun ensureNotificationListenerEnabledForMedia(): Boolean {
        if (isMediaNotificationListenerEnabled()) return false
        return try {
            var retry = 0
            while (!ShizukuUtils.isShizukuAvailable() && retry < 20) {
                delay(1000)
                retry++
            }
            if (!ShizukuUtils.isShizukuAvailable()) return false

            val component = getMediaNotificationListenerComponent().flattenToString()
            val current =
                    ShizukuUtils.runCommandAndGetOutput(
                                    arrayOf(
                                            "sh",
                                            "-c",
                                            "settings get secure enabled_notification_listeners"
                                    )
                            )
                            .trim()
            val existing =
                    current.takeIf { it.isNotBlank() && it != "null" }
                            ?.split(":")
                            ?.filter { it.isNotBlank() }
                            ?: emptyList()
            if (existing.contains(component)) return false

            val next = (existing + component).distinct().joinToString(":")
            ShizukuUtils.runCommandAndGetOutput(
                    arrayOf(
                            "sh",
                            "-c",
                            "settings put secure enabled_notification_listeners '$next'; " +
                                    "cmd notification allow_listener '$component' 0 >/dev/null 2>&1 || true; " +
                                    "settings put secure enabled_notification_listeners '$next'"
                    )
            )
            Log.i(
                    "BottomBarService",
                    "Notification listener enabled for media metadata: $component"
            )
            true
        } catch (e: Exception) {
            Log.e("BottomBarService", "Failed to enable notification listener for media", e)
            false
        }
    }

    private fun restartMediaMetadataMonitoring() {
        stopMediaMetadataMonitoring(clearState = false)
        startMediaMetadataMonitoring()
    }

    private fun requestNotificationListenerEnableForMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (ensureNotificationListenerEnabledForMedia()) {
                withContext(Dispatchers.Main) { restartMediaMetadataMonitoring() }
            }
        }
    }

    private fun getMediaNotificationListenerComponent(): ComponentName {
        return ComponentName(this, BottomBarNotificationListenerService::class.java)
    }

    private fun isMediaNotificationListenerEnabled(): Boolean {
        val component = getMediaNotificationListenerComponent()
        val enabled =
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                        ?: return false
        return enabled.split(":").any {
            ComponentName.unflattenFromString(it)?.let { parsed ->
                parsed.packageName == component.packageName &&
                        parsed.className == component.className
            } == true
        }
    }

    private fun updateMediaControllers(controllers: List<MediaController>) {
        synchronized(mediaControllerLock) {
            mediaControllerCallbacks.forEach { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
            mediaControllerCallbacks.clear()

            controllers.forEach { controller ->
                val callback =
                        object : MediaController.Callback() {
                            override fun onMetadataChanged(metadata: MediaMetadata?) {
                                publishBestMediaState()
                            }

                            override fun onPlaybackStateChanged(state: PlaybackState?) {
                                publishBestMediaState()
                            }

                            override fun onSessionDestroyed() {
                                publishBestMediaState()
                            }
                        }
                runCatching {
                    controller.registerCallback(callback)
                    mediaControllerCallbacks[controller] = callback
                }
            }
        }
        publishBestMediaState()
    }

    private fun publishBestMediaState() {
        mediaMetadataPublishJob?.cancel()
        mediaMetadataPublishJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    val controllers =
                            synchronized(mediaControllerLock) {
                                mediaControllerCallbacks.keys.toList()
                            }
                    val selected =
                            controllers
                                    .filterNot { it.packageName == "com.android.server.telecom" }
                                    .sortedWith(
                                            compareByDescending<MediaController> {
                                                it.playbackState?.state ==
                                                        PlaybackState.STATE_PLAYING
                                            }
                                                    .thenByDescending {
                                                        hasUsableMediaMetadata(it.metadata)
                                                    }
                                    )
                                    .firstOrNull {
                                        hasUsableMediaMetadata(it.metadata) ||
                                                it.playbackState != null
                                    }

                    if (selected == null) {
                        withContext(Dispatchers.Main) { clearMediaState(preserveCarPlay = true) }
                        return@launch
                    }

                    val metadata = selected.metadata
                    val hasMetadata = hasUsableMediaMetadata(metadata)
                    val title =
                            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                                    ?: metadata?.description?.title?.toString()
                    val artist =
                            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                                    ?: metadata?.description?.subtitle?.toString()
                    val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    val artwork = resolveMediaArtwork(metadata)
                    val playbackState = selected.playbackState
                    val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
                    val durationMs =
                            metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                                    ?.takeIf { it > 0L }
                                    ?: 0L
                    val elapsedMs = playbackState?.position?.takeIf { it >= 0L } ?: 0L
                    val progressUpdatedAtMs =
                            playbackState?.lastPositionUpdateTime?.takeIf { it > 0L }
                                    ?: SystemClock.elapsedRealtime()
                    val canSeek =
                            durationMs > 0L &&
                                    ((playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) !=
                                            0L
                    val packageName = selected.packageName
                    logMediaSelection(packageName, title, artist, album, artwork, metadata)

                    withContext(Dispatchers.Main) {
                        val androidAutoFallbackPackage =
                                resolveAndroidAutoProjectionFallbackMediaPackage(packageName, hasMetadata)
                        if (androidAutoFallbackPackage != null) {
                            BottomBarState.mediaTitle = title?.takeIf { it.isNotBlank() }
                            BottomBarState.mediaArtist = artist?.takeIf { it.isNotBlank() }
                            BottomBarState.mediaAlbum = album?.takeIf { it.isNotBlank() }
                            BottomBarState.mediaArtwork = artwork
                            BottomBarState.mediaPackageName = androidAutoFallbackPackage
                            BottomBarState.mediaIsPlaying = isPlaying
                            updateMediaProgressState(
                                    durationMs = durationMs,
                                    elapsedMs = elapsedMs,
                                    updatedAtMs = progressUpdatedAtMs,
                                    canSeek = false
                            )
                            Log.i(
                                    "BottomBarService",
                                    "Using fallback media metadata for Android Auto " +
                                            "source=$packageName title=${title ?: "-"}"
                            )
                            return@withContext
                        }
                        if (shouldKeepProjectionMediaState(packageName, hasMetadata)) {
                            return@withContext
                        }
                        if (!hasMetadata &&
                                        isProjectionMediaPackage(BottomBarState.mediaPackageName)
                        ) {
                            return@withContext
                        }
                        BottomBarState.mediaTitle = title?.takeIf { it.isNotBlank() }
                        BottomBarState.mediaArtist = artist?.takeIf { it.isNotBlank() }
                        BottomBarState.mediaAlbum = album?.takeIf { it.isNotBlank() }
                        BottomBarState.mediaArtwork = artwork
                        BottomBarState.mediaPackageName = packageName
                        BottomBarState.mediaIsPlaying = isPlaying
                        updateMediaProgressState(
                                durationMs = durationMs,
                                elapsedMs = elapsedMs,
                                updatedAtMs = progressUpdatedAtMs,
                                canSeek = canSeek
                        )
                    }
                }
    }

    private fun hasUsableMediaMetadata(metadata: MediaMetadata?): Boolean {
        if (metadata == null) return false
        return !metadata.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() ||
                !metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrBlank() ||
                hasMediaArtworkReference(metadata) ||
                metadata.description?.title != null
    }

    private fun hasMediaArtworkReference(metadata: MediaMetadata?): Boolean {
        if (metadata == null) return false
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null ||
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) != null ||
                metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) != null ||
                !metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI).isNullOrBlank() ||
                !metadata.getString(MediaMetadata.METADATA_KEY_ART_URI).isNullOrBlank() ||
                !metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).isNullOrBlank() ||
                metadata.description?.iconBitmap != null ||
                metadata.description?.iconUri != null
    }

    private fun resolveMediaArtwork(metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null
        val bitmap =
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                        ?: metadata.description?.iconBitmap
        if (bitmap != null) return normalizeMediaArtwork(bitmap)

        val artworkUri =
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                        ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                        ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                        ?: metadata.description?.iconUri?.toString()
        return decodeMediaArtworkUri(artworkUri)
    }

    private fun normalizeMediaArtwork(bitmap: Bitmap): Bitmap {
        val maxDimension = 720
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun decodeMediaArtworkUri(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return null

            if (scheme.isNullOrBlank()) {
                BitmapFactory.decodeFile(uriString)?.let { return normalizeMediaArtwork(it) }
                return null
            }

            val bounds =
                    BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = calculateBitmapSampleSize(bounds, 720, 720)
                    }
            val decoded =
                    contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
            decoded?.let { normalizeMediaArtwork(it) }
        } catch (e: SecurityException) {
            Log.w("BottomBarService", "Media artwork URI denied: $uriString")
            null
        } catch (e: Exception) {
            Log.w("BottomBarService", "Failed to decode media artwork URI: $uriString", e)
            null
        }
    }

    private fun calculateBitmapSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
    ): Int {
        var inSampleSize = 1
        val height = options.outHeight
        val width = options.outWidth
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight &&
                            halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun logMediaSelection(
            packageName: String,
            title: String?,
            artist: String?,
            album: String?,
            artwork: Bitmap?,
            metadata: MediaMetadata?
    ) {
        val artworkSize = artwork?.let { "${it.width}x${it.height}" } ?: "none"
        val signature = "$packageName|$title|$artist|$album|$artworkSize"
        if (signature == lastMediaDebugSignature) return
        lastMediaDebugSignature = signature
        Log.i(
                "BottomBarService",
                "Media selected package=$packageName title=${title ?: "-"} artist=${artist ?: "-"} " +
                        "album=${album ?: "-"} artwork=$artworkSize " +
                        "uri=${describeMediaArtworkUri(metadata) ?: "-"}"
        )
    }

    private fun describeMediaArtworkUri(metadata: MediaMetadata?): String? {
        if (metadata == null) return null
        return metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                ?: metadata.description?.iconUri?.toString()
    }

    private fun clearMediaState(preserveCarPlay: Boolean = false) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (preserveCarPlay && isProjectionMediaPackage(BottomBarState.mediaPackageName)) {
                return@launch
            }
            if (!preserveCarPlay) {
                lastCarPlayMediaSignature = null
                lastAndroidAutoMediaSignature = null
            }
            BottomBarState.mediaTitle = null
            BottomBarState.mediaArtist = null
            BottomBarState.mediaAlbum = null
            BottomBarState.mediaArtwork = null
            BottomBarState.mediaPackageName = null
            BottomBarState.mediaIsPlaying = false
            resetMediaProgressState()
        }
    }

    private fun clearCarPlayMediaState(reason: String) {
        lastCarPlayMediaSignature = null
        if (!isCarPlayMediaPackage(BottomBarState.mediaPackageName)) return
        Log.i("BottomBarService", "Clearing CarPlay media state: $reason")
        BottomBarState.mediaTitle = null
        BottomBarState.mediaArtist = null
        BottomBarState.mediaAlbum = null
        BottomBarState.mediaArtwork = null
        BottomBarState.mediaPackageName = null
        BottomBarState.mediaIsPlaying = false
        resetMediaProgressState()
    }

    private fun clearAndroidAutoMediaState(reason: String) {
        lastAndroidAutoMediaSignature = null
        synchronized(androidAutoMediaCommandLock) {
            lastAndroidAutoMediaCommandName = null
            lastAndroidAutoMediaCommandAtMs = 0L
        }
        if (!isAndroidAutoMediaPackage(BottomBarState.mediaPackageName)) return
        Log.i("BottomBarService", "Clearing Android Auto media state: $reason")
        BottomBarState.mediaTitle = null
        BottomBarState.mediaArtist = null
        BottomBarState.mediaAlbum = null
        BottomBarState.mediaArtwork = null
        BottomBarState.mediaPackageName = null
        BottomBarState.mediaIsPlaying = false
        resetMediaProgressState()
    }

    private fun updateMediaProgressState(
            durationMs: Long,
            elapsedMs: Long,
            updatedAtMs: Long = SystemClock.elapsedRealtime(),
            canSeek: Boolean
    ) {
        val normalizedDuration = durationMs.coerceAtLeast(0L)
        val normalizedElapsed =
                if (normalizedDuration > 0L) {
                    elapsedMs.coerceIn(0L, normalizedDuration)
                } else {
                    elapsedMs.coerceAtLeast(0L)
                }
        BottomBarState.mediaDurationMs = normalizedDuration
        BottomBarState.mediaElapsedMs = normalizedElapsed
        BottomBarState.mediaProgressUpdatedAtMs =
                if (normalizedDuration > 0L || normalizedElapsed > 0L) updatedAtMs else 0L
        BottomBarState.mediaCanSeek = canSeek && normalizedDuration > 0L
    }

    private fun resetMediaProgressState() {
        BottomBarState.mediaDurationMs = 0L
        BottomBarState.mediaElapsedMs = 0L
        BottomBarState.mediaProgressUpdatedAtMs = 0L
        BottomBarState.mediaCanSeek = false
    }

    private fun seekMediaTo(targetMs: Long) {
        val packageName = BottomBarState.mediaPackageName
        val durationMs = BottomBarState.mediaDurationMs
        if (durationMs <= 0L) return
        val normalizedTarget = targetMs.coerceIn(0L, durationMs)

        lifecycleScope.launch(Dispatchers.IO) {
            val handled =
                    if (isCarPlayMediaPackage(packageName)) {
                        carPlayNowPlayingMonitor?.seekTo(normalizedTarget) == true
                    } else if (isAndroidAutoMediaPackage(packageName)) {
                        Log.i("BottomBarService", "Ignoring Android Auto seek; progress bar is visual only")
                        false
                    } else {
                        seekAndroidMediaController(packageName, normalizedTarget)
                    }
            if (handled) {
                withContext(Dispatchers.Main) {
                    updateMediaProgressState(
                            durationMs = durationMs,
                            elapsedMs = normalizedTarget,
                            canSeek = BottomBarState.mediaCanSeek
                    )
                }
            }
        }
    }

    private fun skipMedia(forward: Boolean) {
        val packageName = BottomBarState.mediaPackageName
        lifecycleScope.launch(Dispatchers.IO) {
            val handled =
                    when {
                        isCarPlayMediaPackage(packageName) ->
                                if (forward) {
                                    carPlayNowPlayingMonitor?.next() == true
                                } else {
                                    carPlayNowPlayingMonitor?.previous() == true
                                }
                        isAndroidAutoMediaPackage(packageName) ->
                                if (forward) {
                                    sendAndroidAutoProjectionMediaCommand(forward = true)
                                } else {
                                    sendAndroidAutoProjectionMediaCommand(forward = false)
                                }
                        else -> skipAndroidMediaController(packageName, forward)
                    }
            if (handled) {
                Log.i(
                        "BottomBarService",
                        "Media ${if (forward) "next" else "previous"} command sent for ${packageName ?: "active session"}"
                )
            }
        }
    }

    private fun toggleMediaPlayback() {
        val packageName = BottomBarState.mediaPackageName
        val isPlaying = BottomBarState.mediaIsPlaying
        lifecycleScope.launch(Dispatchers.IO) {
            val handled =
                    when {
                        isCarPlayMediaPackage(packageName) ->
                                carPlayNowPlayingMonitor?.playPause(isPlaying) == true
                        isAndroidAutoMediaPackage(packageName) ->
                                sendAndroidAutoProjectionPlaybackCommand(isPlaying)
                        else -> toggleAndroidMediaController(packageName, isPlaying)
                    }
            if (handled) {
                Log.i(
                        "BottomBarService",
                        "Media ${if (isPlaying) "pause" else "play"} command sent for ${packageName ?: "active session"}"
                )
            } else {
                Log.w(
                        "BottomBarService",
                        "Media ${if (isPlaying) "pause" else "play"} command not handled for ${packageName ?: "active session"}"
                )
            }
        }
    }

    private fun seekAndroidMediaController(packageName: String?, targetMs: Long): Boolean {
        val controllers =
                synchronized(mediaControllerLock) {
                    mediaControllerCallbacks.keys.toList()
                }
        val selected =
                controllers.firstOrNull {
                    it.packageName == packageName &&
                            ((it.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) !=
                                    0L
                }
                        ?: controllers.firstOrNull {
                            ((it.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) !=
                                    0L
                        }
                        ?: return false

        return runCatching {
                    selected.transportControls.seekTo(targetMs.coerceAtLeast(0L))
                    true
                }
                .getOrElse {
                    Log.w("BottomBarService", "Failed to seek Android media session", it)
                    false
                }
    }

    private fun skipAndroidMediaController(packageName: String?, forward: Boolean): Boolean {
        val action =
                if (forward) {
                    PlaybackState.ACTION_SKIP_TO_NEXT
                } else {
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS
                }
        val controllers =
                synchronized(mediaControllerLock) {
                    mediaControllerCallbacks.keys.toList()
                }
        val selected =
                controllers.firstOrNull {
                    it.packageName == packageName &&
                            ((it.playbackState?.actions ?: 0L) and action) != 0L
                }
                        ?: controllers.firstOrNull {
                            ((it.playbackState?.actions ?: 0L) and action) != 0L
                        }
                        ?: return false

        return runCatching {
                    if (forward) {
                        selected.transportControls.skipToNext()
                    } else {
                        selected.transportControls.skipToPrevious()
                    }
                    true
                }
                .getOrElse {
                    Log.w(
                            "BottomBarService",
                            "Failed to skip Android media session ${if (forward) "next" else "previous"}",
                            it
                    )
                    false
                }
    }

    private fun toggleAndroidMediaController(packageName: String?, isPlaying: Boolean): Boolean {
        val action =
                if (isPlaying) {
                    PlaybackState.ACTION_PAUSE
                } else {
                    PlaybackState.ACTION_PLAY
                }
        val controllers =
                synchronized(mediaControllerLock) {
                    mediaControllerCallbacks.keys.toList()
                }
        val selected =
                controllers.firstOrNull {
                    it.packageName == packageName &&
                            ((it.playbackState?.actions ?: 0L) and action) != 0L
                }
                        ?: controllers.firstOrNull {
                            ((it.playbackState?.actions ?: 0L) and action) != 0L
                        }
                        ?: return false

        return runCatching {
                    if (isPlaying) {
                        selected.transportControls.pause()
                    } else {
                        selected.transportControls.play()
                    }
                    true
                }
                .getOrElse {
                    Log.w(
                            "BottomBarService",
                            "Failed to ${if (isPlaying) "pause" else "play"} Android media session",
                            it
                    )
                    false
                }
    }

    private fun carPlayMediaSignature(title: String?, artist: String?, artworkPath: String?): String? {
        val normalizedTitle = title?.trim().orEmpty()
        val normalizedArtist = artist?.trim().orEmpty()
        val normalizedArtworkPath = artworkPath?.trim().orEmpty()
        if (normalizedTitle.isBlank() &&
                        normalizedArtist.isBlank() &&
                        normalizedArtworkPath.isBlank()
        ) {
            return null
        }
        return "$normalizedTitle|$normalizedArtist|$normalizedArtworkPath"
    }

    private fun isCarPlayMediaPackage(packageName: String?): Boolean {
        return isCarPlayMediaPackageName(packageName)
    }

    private fun isAndroidAutoMediaPackage(packageName: String?): Boolean {
        return packageName == ANDROID_AUTO_MEDIA_PACKAGE ||
                packageName == ANDROID_AUTO_MEDIA_APP_PACKAGE ||
                packageName == ANDROID_AUTO_MEDIA_SERVICE_PACKAGE
    }

    private fun isProjectionMediaPackage(packageName: String?): Boolean {
        return isCarPlayMediaPackage(packageName) || isAndroidAutoMediaPackage(packageName)
    }

    private fun shouldKeepProjectionMediaState(
            candidatePackageName: String,
            candidateHasMetadata: Boolean
    ): Boolean {
        val currentPackageName = BottomBarState.mediaPackageName
        if (!isProjectionMediaPackage(currentPackageName)) return false
        if (isProjectionMediaPackage(candidatePackageName)) return false
        if (!candidateHasMetadata) return true
        return candidatePackageName in PROJECTION_MEDIA_FALLBACK_PACKAGES
    }

    private fun resolveAndroidAutoProjectionFallbackMediaPackage(
            candidatePackageName: String,
            candidateHasMetadata: Boolean
    ): String? {
        if (
                isCarPlayMediaPackage(BottomBarState.mediaPackageName) ||
                        isCarPlayMediaPackage(BottomBarState.activeClusterProjectionPackage)
        ) {
            return null
        }
        return if (
                shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                        candidatePackageName = candidatePackageName,
                        candidateHasMetadata = candidateHasMetadata,
                        currentMediaPackageName = BottomBarState.mediaPackageName,
                        activeClusterProjectionPackage =
                                BottomBarState.activeClusterProjectionPackage,
                        androidAutoSessionReady =
                                DisplayAppLauncher.isAndroidAutoProjectionSessionReadyForMedia(
                                        "AA_MEDIA_FALLBACK"
                                )
                )
        ) {
            ANDROID_AUTO_MEDIA_PACKAGE
        } else {
            null
        }
    }

    private fun stopMediaMetadataMonitoring(clearState: Boolean = true) {
        mediaMetadataPublishJob?.cancel()
        mediaMetadataPublishJob = null
        val manager = mediaSessionManager
        val listener = mediaSessionsListener
        if (manager != null && listener != null) {
            runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
        }
        synchronized(mediaControllerLock) {
            mediaControllerCallbacks.forEach { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
            mediaControllerCallbacks.clear()
        }
        mediaSessionsListener = null
        mediaSessionManager = null
        if (clearState) clearMediaState()
    }

    private fun getTopPackageOnDisplay(displayId: Int): String? {
        return DisplayAppLauncher.getTopPackageOnDisplay(displayId)
    }

    private fun resolveProjectionPackage(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        val normalized = packageName.lowercase()
        return when {
            normalized == "com.ts.carplay.app" ||
                    normalized == "com.ts.carplay" ||
                    normalized.contains("carplay") ||
                    normalized.contains("carlink") ||
                    normalized.contains("zlink") -> "com.ts.carplay.app"
            normalized == "com.ts.androidauto.app" ||
                    normalized == "com.ts.androidauto.projectionservice" ||
                    normalized.contains("androidauto") ||
                    normalized.contains("gearhead") -> "com.ts.androidauto.app"
            else -> null
        }
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent =
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_HOME)
        val launchers = packageManager.queryIntentActivities(intent, 0)
        return launchers.any { it.activityInfo.packageName == packageName }
    }

    private fun getSettingsForPackage(packageName: String, defaultOverscan: Int): BarSettings {
        val prefs =
                br.com.redesurftank.App.getDeviceProtectedContext()
                        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

        val dynamicOverridesJson =
                prefs.getString(SharedPreferencesKeys.BOTTOM_BAR_OVERRIDES.key, null)
        val dynamicOverrides: Map<String, BarSettings> =
                if (dynamicOverridesJson != null) {
                    try {
                        val type = object : TypeToken<Map<String, BarSettings>>() {}.type
                        Gson().fromJson(dynamicOverridesJson, type)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }

        // Priority: Dynamic Overrides -> Default
        return dynamicOverrides[packageName]
                // ?: APP_OVERRIDES[packageName]
                ?: BarSettings(overscan = defaultOverscan, yOffset = 0)
    }

    private fun applyAppSettings(settings: BarSettings) {
        val wm = mWindowManager ?: return
        val cv = composeView ?: return
        val lp = params ?: return
        val density = this.resources.displayMetrics.density

        if (!BottomBarState.isVisible) {
            Log.d(
                    "BottomBarService",
                    "Bottom bar hidden, ignoring dynamic overscan request: ${settings.overscan}"
            )
            lifecycleScope.launch(Dispatchers.IO) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,0"))
            }
            return
        }

        val isRestored = lastPackage != null && BottomBarState.restoredApps.contains(lastPackage)
        val multiplier = if (isRestored) 3.0f else 1.0f

        val overscanValueRaw = settings.overscan
        val overscanValuePx = (overscanValueRaw.toFloat() * density * multiplier).toInt()
        val yOffsetPx = (settings.yOffset * density).toInt()

        Log.w(
                "BottomBarService",
                "[OVERSCAN_SYNC] App: $lastPackage | Overscan: ${overscanValueRaw}dp(${overscanValuePx}px) | Offset: ${settings.yOffset}dp(${yOffsetPx}px) | Visible: ${BottomBarState.isVisible}"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,$overscanValuePx"))
            withContext(Dispatchers.Main) {
                // Apply custom yOffset relative to the logical bottom (where y=0 is the edge)
                lp.y = yOffsetPx
                try {
                    wm.updateViewLayout(cv, lp)
                } catch (e: Exception) {
                    Log.e(
                            "BottomBarService",
                            "Error updating window layout during app settings change",
                            e
                    )
                }
            }
        }
    }

    private fun observeVisibility() {
        lifecycleScope.launch {
            snapshotFlow { BottomBarState.isVisible }.collectLatest { visible ->
                updateBarVisibility(visible)
                // Force recompute touchable regions
                composeView?.requestLayout()
                menuComposeView?.requestLayout()
            }
        }
        // Periodic invalidation to keep touchable regions in sync
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                composeView?.requestLayout()
                menuComposeView?.requestLayout()
            }
        }
    }

    private fun observeMenuState() {
        lifecycleScope.launch {
            snapshotFlow {
                BottomBarState.isMenuExpanded ||
                        BottomBarState.isSettingsMenuExpanded ||
                        BottomBarState.isOverrideMenuExpanded ||
                        BottomBarState.activeSliderType != null
            }
                    .collectLatest { expanded ->
                        updateMenuWindow(expanded)
                        // Force recompute touchable regions when menu state changes
                        composeView?.requestLayout()
                        menuComposeView?.requestLayout()
                    }
        }
    }

    private fun observeDashboardActivityState() {
        lifecycleScope.launch {
            snapshotFlow { BottomBarState.isDashboardExpanded }
                    .distinctUntilChanged()
                    .collectLatest { expanded ->
                        if (expanded) {
                            launchDashboardActivity()
                        }
                        composeView?.requestLayout()
                        menuComposeView?.requestLayout()
                    }
        }
    }

    private fun launchDashboardActivity() {
        try {
            startActivity(
                    ImpulseDashboardActivity.createIntent(this)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                            )
            )
        } catch (e: Exception) {
            Log.e("BottomBarService", "Error launching fullscreen dashboard activity", e)
        }
    }

    private fun updateBarVisibility(visible: Boolean) {
        val wm = mWindowManager ?: return
        val cv = composeView ?: return
        val lp = params ?: return

        val density = resources.displayMetrics.density

        lifecycleScope.launch(Dispatchers.IO) {
            val overscanCmd: Array<String>
            if (visible) {
                val settings =
                        currentAppSettings
                                ?: run {
                                    val prefs =
                                            br.com.redesurftank.App.getDeviceProtectedContext()
                                                    .getSharedPreferences(
                                                            "haval_prefs",
                                                            Context.MODE_PRIVATE
                                                    )
                                    val storedDefault =
                                            prefs.getInt(
                                                    SharedPreferencesKeys
                                                            .PERSISTENT_BOTTOM_BAR_OVERSCAN
                                                            .key,
                                                    REFERENCE_OVERSCAN
                                            )
                                    BarSettings(overscan = storedDefault, yOffset = 0)
                                }

                val isRestored =
                        lastPackage != null && BottomBarState.restoredApps.contains(lastPackage)
                val multiplier = if (isRestored) 3.0f else 1.0f

                val overscanValuePx = (settings.overscan.toFloat() * density * multiplier).toInt()
                val yOffsetPx = (settings.yOffset * density).toInt()

                withContext(Dispatchers.Main) {
                    lp.height = (60 * density).toInt()
                    lp.y = 0
                }
                overscanCmd = arrayOf("wm", "overscan", "0,0,0,$overscanValuePx")
            } else {
                withContext(Dispatchers.Main) {
                    BottomBarState.isDashboardExpanded = false
                    BottomBarState.isMenuExpanded = false
                    BottomBarState.isSettingsMenuExpanded = false
                    BottomBarState.isOverrideMenuExpanded = false
                    BottomBarState.activeSliderType = null
                }
                // Trigger zone - keep 40dp (20dp on screen) area touchable
                withContext(Dispatchers.Main) {
                    lp.height = (60 * density).toInt()
                    lp.y = -(20 * density).toInt()
                }
                overscanCmd = arrayOf("wm", "overscan", "0,0,0,0")
            }

            ShizukuUtils.runCommandAndGetOutput(overscanCmd)

            withContext(Dispatchers.Main) {
                try {
                    wm.updateViewLayout(cv, lp)
                } catch (e: Exception) {
                    Log.e("BottomBarService", "Error updating window layout", e)
                }
            }
        }
    }

    private fun updateMenuWindow(show: Boolean) {
        val wm = mWindowManager ?: return
        val mv = menuComposeView ?: return
        val mp = menuParams ?: return
        val displayMetrics = android.util.DisplayMetrics()
        wm.defaultDisplay?.getRealMetrics(displayMetrics)

        if (!isMenuWindowAdded) {
            try {
                // Initialize as hidden if first added
                if (!show) {
                    mp.width = 0
                    mp.height = 0
                    mp.flags = mp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
                wm.addView(mv, mp)
                isMenuWindowAdded = true
            } catch (e: Exception) {
                Log.e("BottomBarService", "Error adding menu window", e)
                return
            }
        }

        try {
            if (show) {
                val realWidth = displayMetrics.widthPixels.takeIf { it > 0 }
                val realHeight = displayMetrics.heightPixels.takeIf { it > 0 }
                val appWidth = resources.displayMetrics.widthPixels.takeIf { it > 0 }
                val leftInset = ((realWidth ?: 0) - (appWidth ?: 0)).coerceAtLeast(0)

                mp.width = realWidth ?: WindowManager.LayoutParams.MATCH_PARENT
                mp.height = realHeight ?: WindowManager.LayoutParams.MATCH_PARENT
                mp.x = -leftInset
                mp.y = 0
                mp.gravity = Gravity.TOP or Gravity.START
                mp.flags = mp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                mp.width = 0
                mp.height = 0
                mp.flags = mp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            wm.updateViewLayout(mv, mp)
        } catch (e: Exception) {
            Log.e("BottomBarService", "Error updating menu window layout", e)
        }
    }

    private fun showBottomBar() {
        mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HavalShisuku)

        composeView =
                ComposeView(themedContext)
                        .apply {
                            setContent { HavalShisukuTheme { BottomBarContent() } }
                            setupTouchableRegions(this, isMenuWindow = false)
                        }
                        .also { it.setupForService() }

        menuComposeView =
                ComposeView(themedContext)
                        .apply {
                            setContent { HavalShisukuTheme { BottomBarMenus() } }
                            setupTouchableRegions(this, isMenuWindow = true)
                        }
                        .also { it.setupForService() }

        val density = resources.displayMetrics.density
        val barHeight = (BOTTOM_BAR_BASE_HEIGHT_DP * density).toInt()

        val layoutType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                }

        params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                barHeight,
                                layoutType,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            // Immersive mode flags to hide system bars
                            systemUiVisibility =
                                    (android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE or
                                            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                            android.view.View
                                                    .SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

                            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            // On show, we derive y from the currently applied overscan value if
                            // possible,
                            // but setting it to -defaultOverscan below in show logic.
                            // For initial params, we can use 0 and it will be updated.
                            y = 0
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                layoutInDisplayCutoutMode =
                                        WindowManager.LayoutParams
                                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                        }

        menuParams =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                layoutType,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            // Immersive mode flags to hide system bars
                            systemUiVisibility =
                                    (android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE or
                                            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                            android.view.View
                                                    .SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

                            gravity = Gravity.TOP or Gravity.START
                            y = 0
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                layoutInDisplayCutoutMode =
                                        WindowManager.LayoutParams
                                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                        }

        if (android.provider.Settings.canDrawOverlays(this)) {
            try {
                mWindowManager?.addView(composeView, params)
                val settings =
                        currentAppSettings
                                ?: run {
                                    val prefs =
                                            br.com.redesurftank.App.getDeviceProtectedContext()
                                                    .getSharedPreferences(
                                                            "haval_prefs",
                                                            Context.MODE_PRIVATE
                                                    )
                                    val storedDefault =
                                            prefs.getInt(
                                                    SharedPreferencesKeys
                                                            .PERSISTENT_BOTTOM_BAR_OVERSCAN
                                                            .key,
                                                    REFERENCE_OVERSCAN
                                            )
                                    BarSettings(overscan = storedDefault, yOffset = 0)
                                }

                val overscanValuePx = (settings.overscan * density).toInt()
                val yOffsetPx = (settings.yOffset * density).toInt()

                val lp = params
                if (lp != null) {
                    lp.y = yOffsetPx
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    ShizukuUtils.runCommandAndGetOutput(
                            arrayOf("wm", "overscan", "0,0,0,$overscanValuePx")
                    )
                }
            } catch (e: Exception) {
                Log.e("BottomBarService", "Error adding views", e)
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    private fun setupTouchableRegions(composeView: ComposeView, isMenuWindow: Boolean = false) {
        val observer = composeView.viewTreeObserver
        try {
            val listenerClass =
                    Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val infoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val setTouchableInsetsMethod =
                    infoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val touchableRegionField = infoClass.getField("touchableRegion")

            val proxy =
                    Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) {
                            _,
                            method,
                            args ->
                        if (method.name == "onComputeInternalInsets") {
                            val info = args[0]
                            // 3 is TOUCHABLE_INSETS_REGION
                            setTouchableInsetsMethod.invoke(info, 3)
                            val region = touchableRegionField.get(info) as Region
                            region.setEmpty()

                            val density = resources.displayMetrics.density
                            val displayMetrics = android.util.DisplayMetrics()
                            mWindowManager?.defaultDisplay?.getRealMetrics(displayMetrics)
                            val windowWidth = displayMetrics.widthPixels

                            if (isMenuWindow) {
                                // Menu window is MATCH_PARENT (full screen height)
                                val anyMenuExpanded =
                                        BottomBarState.isMenuExpanded ||
                                                BottomBarState.isDashboardExpanded ||
                                                BottomBarState.isSettingsMenuExpanded ||
                                                BottomBarState.isOverrideMenuExpanded ||
                                                BottomBarState.activeSliderType != null
                                Log.d(
                                        "BottomBarService",
                                        "TouchRegion[MENU] anyMenuExpanded=$anyMenuExpanded"
                                )
                                if (anyMenuExpanded) {
                                    val screenHeight = displayMetrics.heightPixels
                                    region.union(Rect(0, 0, windowWidth, screenHeight))
                                }
                            } else {
                                // Bar window is 60dp tall
                                val windowHeight = (60 * density).toInt()
                                val topHandleHeight = (15 * density).toInt()
                                val hiddenTriggerHeight = (40 * density).toInt()
                                val visibleBarTouchHeight = (80 * density).toInt()

                                Log.d(
                                        "BottomBarService",
                                        "TouchRegion[BAR] isVisible=${BottomBarState.isVisible}, windowWidth=$windowWidth, windowHeight=$windowHeight, visibleBarTouchHeight=$visibleBarTouchHeight"
                                )

                                if (BottomBarState.isVisible) {
                                    // Main Bar touchable area - full width, bottom 80dp
                                    region.union(
                                            Rect(
                                                    0,
                                                    windowHeight - visibleBarTouchHeight,
                                                    windowWidth,
                                                    windowHeight
                                            )
                                    )
                                    // Top Handle for swipe gesture
                                    region.union(Rect(0, 0, windowWidth, topHandleHeight))
                                } else {
                                    // Hidden: only a small trigger zone at the bottom for swipe-up
                                    region.union(
                                            Rect(
                                                    0,
                                                    windowHeight - hiddenTriggerHeight,
                                                    windowWidth,
                                                    windowHeight
                                            )
                                    )
                                }
                            }
                        }
                        null
                    }

            val addMethod =
                    observer.javaClass.getMethod(
                            "addOnComputeInternalInsetsListener",
                            listenerClass
                    )
            addMethod.invoke(observer, proxy)
        } catch (e: Exception) {
            Log.e("BottomBarService", "Failed to setup touchable regions via reflection", e)
        }
    }

    private fun ComposeView.setupForService() {
        this.setViewTreeLifecycleOwner(this@BottomBarService)
        val viewModelStore = ViewModelStore()
        this.setViewTreeViewModelStoreOwner(
                object : ViewModelStoreOwner {
                    override val viewModelStore: ViewModelStore = viewModelStore
                }
        )
        val savedStateRegistryOwner =
                object : SavedStateRegistryOwner {
                    private val lifecycleRegistry = this@BottomBarService.lifecycle
                    private val savedStateRegistryController =
                            SavedStateRegistryController.create(this)
                    override val lifecycle = lifecycleRegistry
                    override val savedStateRegistry =
                            savedStateRegistryController.savedStateRegistry
                    init {
                        savedStateRegistryController.performRestore(null)
                    }
                }
        this.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        autoHideJob?.cancel()
        mediaAccessMonitorJob?.cancel()
        carPlayUsbDisconnectMonitorJob?.cancel()
        carPlayUsbDisconnectMonitorJob = null
        stopMediaMetadataMonitoring()
        carPlayNowPlayingMonitor?.stop()
        carPlayNowPlayingMonitor = null
        androidAutoNowPlayingMonitor?.stop()
        androidAutoNowPlayingMonitor = null
        if (instance === this) {
            instance = null
        }
        unregisterReceiver(updateReceiver)
        super.onDestroy()
        ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "size", "reset"))
        ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,0"))
        try {
            composeView?.let { mWindowManager?.removeView(it) }
            if (isMenuWindowAdded) {
                menuComposeView?.let { mWindowManager?.removeView(it) }
            }
        } catch (e: Exception) {}
    }

    companion object {
        private const val CARPLAY_MEDIA_PACKAGE = "com.ts.carplay"
        private const val CARPLAY_MEDIA_APP_PACKAGE = "com.ts.carplay.app"
        private const val ANDROID_AUTO_MEDIA_PACKAGE = "com.ts.androidauto"
        private const val ANDROID_AUTO_MEDIA_APP_PACKAGE = "com.ts.androidauto.app"
        private const val ANDROID_AUTO_MEDIA_SERVICE_PACKAGE = "com.ts.androidauto.projectionservice"
        private const val ANDROID_AUTO_MEDIA_SKIP_COMMAND_COOLDOWN_MS = 650L
        private const val ANDROID_AUTO_MEDIA_TOGGLE_COMMAND_COOLDOWN_MS = 2_000L
        private const val PROJECTION_USB_STATE_PATH = "/sys/class/android_usb/android0/state"
        private const val CARPLAY_USB_MEDIA_STATE_POLL_MS = 1_500L
        private val PROJECTION_MEDIA_FALLBACK_PACKAGES =
                setOf(
                        "com.android.bluetooth",
                        "com.beantechs.mediacenter",
                        "com.beantechs.mediacenter.h5.core",
                        "com.onecar.onlinemusic"
                )
        @Volatile private var instance: BottomBarService? = null

        internal fun isProjectionUsbReadyForMedia(rawState: String): Boolean {
            return rawState
                    .lineSequence()
                    .map { it.trim().uppercase() }
                    .any { state -> state == "CONFIGURED" || state == "CONNECTED" }
        }

        internal fun shouldClearCarPlayMediaOnUsbState(
                packageName: String?,
                rawState: String
        ): Boolean {
            return isCarPlayMediaPackageName(packageName) &&
                    !isProjectionUsbReadyForMedia(rawState)
        }

        internal fun shouldClearAndroidAutoMediaOnUsbState(
                packageName: String?,
                rawState: String,
                androidAutoSessionReady: Boolean = false
        ): Boolean {
            return isAndroidAutoMediaPackageName(packageName) &&
                    !androidAutoSessionReady &&
                    !isProjectionUsbReadyForMedia(rawState)
        }

        internal fun shouldUseAndroidAutoProjectionFallbackMediaPackageForTest(
                candidatePackageName: String,
                candidateHasMetadata: Boolean,
                currentMediaPackageName: String?,
                activeClusterProjectionPackage: String?,
                androidAutoSessionReady: Boolean
        ): Boolean {
            if (!candidateHasMetadata) return false
            if (isCarPlayMediaPackageName(currentMediaPackageName)) return false
            if (isCarPlayMediaPackageName(activeClusterProjectionPackage)) return false
            if (isProjectionMediaPackageName(candidatePackageName)) return false
            if (candidatePackageName !in PROJECTION_MEDIA_FALLBACK_PACKAGES) return false
            return isAndroidAutoMediaPackageName(currentMediaPackageName) ||
                    isAndroidAutoMediaPackageName(activeClusterProjectionPackage) ||
                    androidAutoSessionReady
        }

        internal fun shouldAcceptAndroidAutoMediaCommandForTest(
                nowMs: Long,
                lastCommandAtMs: Long,
                cooldownMs: Long = ANDROID_AUTO_MEDIA_SKIP_COMMAND_COOLDOWN_MS
        ): Boolean {
            if (lastCommandAtMs <= 0L) return true
            return nowMs - lastCommandAtMs !in 0..cooldownMs
        }

        private fun isCarPlayMediaPackageName(packageName: String?): Boolean {
            return packageName == CARPLAY_MEDIA_PACKAGE || packageName == CARPLAY_MEDIA_APP_PACKAGE
        }

        private fun isAndroidAutoMediaPackageName(packageName: String?): Boolean {
            return packageName == ANDROID_AUTO_MEDIA_PACKAGE ||
                    packageName == ANDROID_AUTO_MEDIA_APP_PACKAGE ||
                    packageName == ANDROID_AUTO_MEDIA_SERVICE_PACKAGE
        }

        private fun isProjectionMediaPackageName(packageName: String?): Boolean {
            return isCarPlayMediaPackageName(packageName) || isAndroidAutoMediaPackageName(packageName)
        }

        fun seekCurrentMediaTo(targetMs: Long) {
            instance?.seekMediaTo(targetMs)
        }

        fun skipCurrentMediaNext() {
            instance?.skipMedia(forward = true)
        }

        fun skipCurrentMediaPrevious() {
            instance?.skipMedia(forward = false)
        }

        fun toggleCurrentMediaPlayback() {
            instance?.toggleMediaPlayback()
        }

        fun sendAndroidAutoProjectionMediaNext(): Boolean {
            return instance?.sendAndroidAutoProjectionMediaCommand(forward = true) == true
        }

        fun sendAndroidAutoProjectionMediaPrevious(): Boolean {
            return instance?.sendAndroidAutoProjectionMediaCommand(forward = false) == true
        }
    }

    private fun sendAndroidAutoProjectionMediaCommand(forward: Boolean): Boolean {
        val commandName = if (forward) "next" else "previous"
        if (!shouldSendAndroidAutoMediaCommand(
                        commandName,
                        ANDROID_AUTO_MEDIA_SKIP_COMMAND_COOLDOWN_MS
                )
        ) {
            return false
        }
        return if (forward) {
            androidAutoNowPlayingMonitor?.next() == true
        } else {
            androidAutoNowPlayingMonitor?.previous() == true
        }
    }

    private suspend fun sendAndroidAutoProjectionPlaybackCommand(isPlaying: Boolean): Boolean {
        val commandName = if (isPlaying) "pause" else "play"
        if (!shouldSendAndroidAutoMediaCommand(
                        commandName,
                        ANDROID_AUTO_MEDIA_TOGGLE_COMMAND_COOLDOWN_MS
                )
        ) {
            return false
        }
        return DisplayAppLauncher.sendAndroidAutoDashboardPlaybackCommand(isPlaying)
    }

    private fun shouldSendAndroidAutoMediaCommand(commandName: String, cooldownMs: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        val accepted =
                synchronized(androidAutoMediaCommandLock) {
                    val shouldAccept =
                            shouldAcceptAndroidAutoMediaCommandForTest(
                                    nowMs = now,
                                    lastCommandAtMs = lastAndroidAutoMediaCommandAtMs,
                                    cooldownMs = cooldownMs
                            )
                    if (shouldAccept) {
                        lastAndroidAutoMediaCommandName = commandName
                        lastAndroidAutoMediaCommandAtMs = now
                    }
                    shouldAccept
                }
        if (!accepted) {
            Log.w(
                    "BottomBarService",
                    "Ignoring duplicate Android Auto media command: " +
                            "$commandName after $lastAndroidAutoMediaCommandName " +
                            "cooldownMs=$cooldownMs"
            )
        }
        return accepted
    }
}
