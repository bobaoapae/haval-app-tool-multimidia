package br.com.redesurftank.havalshisuku.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
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
import br.com.redesurftank.havalshisuku.BuildConfig
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.listeners.IDataChanged
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.BottomBarContent
import br.com.redesurftank.havalshisuku.ui.components.BottomBarMenus
import br.com.redesurftank.havalshisuku.ui.theme.HavalShisukuTheme
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import com.beantechs.mediacenter.core_common.data.MediaInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Proxy
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.lsposed.hiddenapibypass.HiddenApiBypass

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
    // Timestamp (elapsedRealtime) of the last real CarPlay now-playing update. An
    // actively streaming CarPlay session is the authoritative "still connected"
    // signal — far more reliable than the USB gadget state file, which reads
    // "not connected" for wireless CarPlay and flaps transiently even when wired.
    // The USB-disconnect monitor uses this to avoid wiping live media on a false
    // USB "disconnect" read (that false positive was the album-art/progress flicker).
    @Volatile private var lastCarPlayNowPlayingUpdateAtMs: Long = 0L
    private var nativeMediaStateListener: IDataChanged? = null
    private var androidAutoMonitorRefreshJob: Job? = null
    private var audioMuteStateListener: IDataChanged? = null
    private val mediaControllerLock = Any()
    private val mediaControllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val androidAutoMediaCommandLock = Any()
    private val androidAutoPauseOemAudioFocusLock = Any()
    private var nativeMediaCenterServiceConnection: ServiceConnection? = null
    private var nativeMediaCenterSourceMonitorJob: Job? = null
    private var dashboardProjectionRestoreJob: Job? = null
    @Volatile private var dashboardControlFocusRestoreSuppressedUntilMs: Long = 0L
    @Volatile private var nativeMediaCenterServiceBinder: IBinder? = null
    @Volatile private var nativeMediaCenterPlayServiceBinder: IBinder? = null
    @Volatile private var nativeMediaCenterCurrentSource: Int? = null
    @Volatile private var nativeMediaCenterCurrentAudioSource: Int? = null
    private var lastMediaDebugSignature: String? = null
    private var lastCarPlayMediaSignature: String? = null
    private var lastAndroidAutoMediaSignature: String? = null
    private var lastNativeAndroidAutoMediaInfoSignature: String? = null
    private var lastAndroidAutoMediaCommandName: String? = null
    private var lastAndroidAutoMediaCommandAtMs: Long = 0L
    @Volatile private var androidAutoProgressRegressionAllowedUntilMs: Long = 0L
    private var cachedProjectionUsbState: String? = null
    private var cachedProjectionUsbStateAtMs: Long = 0L
    @Volatile private var cachedAndroidAutoMediaSessionReady = false
    @Volatile private var cachedAndroidAutoMediaSessionReadyAtMs: Long = 0L
    @Volatile private var nativeRadioProtectionUntilMs: Long = 0L
    @Volatile private var lastNativeRadioProtectionReason: String? = null
    @Volatile private var androidAutoPauseOemAudioFocusHeldUntilMs: Long = 0L
    private var androidAutoPauseOemAudioFocusManager: Any? = null
    private var androidAutoPauseOemAudioFocusRequest: Any? = null
    private var androidAutoPauseOemAudioFocusListener: Any? = null
    private var androidAutoPauseOemAndroidAudioManager: AudioManager? = null
    private var androidAutoPauseOemAndroidAudioFocusRequest: AudioFocusRequest? = null
    private var androidAutoPauseOemAndroidAudioFocusRefreshJob: Job? = null
    @Volatile private var nativeAndroidAutoMediaCenterIsPlayingGuess: Boolean = true
    @Volatile private var nativeAndroidAutoPlaybackCommandTarget: Boolean? = null
    @Volatile private var nativeAndroidAutoPlaybackCommandTargetAtMs: Long = 0L
    @Volatile private var nativeAndroidAutoPlaybackCommandTargetElapsedMs: Long = 0L
    @Volatile private var androidAutoMuteTargetGeneration: Int = 0
    @Volatile private var androidAutoMuteRestoreVolume: Int? = null

    data class BarSettings(val overscan: Int, val yOffset: Int)

    private data class AndroidAutoPauseOemAudioFocusHandle(
            val clientUid: Int,
            val usage: Int,
            val flags: Int,
            val clientId: String,
            val packageName: String
    )

    private data class AndroidAutoPauseOemAudioFocusHidlResult(
            val retval: Int,
            val requestResult: Int
    )

    private data class AndroidAutoPauseAndroidAudioFocusRegistration(
            val manager: AudioManager,
            val request: AudioFocusRequest,
            val listener: AudioManager.OnAudioFocusChangeListener,
            val clientId: String,
            val requestResult: Int
    )

    private data class NativeMediaCenterMediaInfo(
            val mediaSource: Int,
            val title: String?,
            val artist: String?,
            val album: String?,
            val imageUrl: String?,
            val imageBitmap: Bitmap?,
            val durationMs: Long
    ) {
        val hasMetadata: Boolean
            get() =
                    !title.isNullOrBlank() ||
                            !artist.isNullOrBlank() ||
                            !album.isNullOrBlank() ||
                            !imageUrl.isNullOrBlank() ||
                            imageBitmap != null ||
                            durationMs > 0L
    }

    private data class NativeMediaCenterPlayState(
            val mediaSource: Int,
            val state: Int,
            val durationMs: Long,
            val elapsedMs: Long
    ) {
        val isPlaying: Boolean?
            get() =
                    when (state) {
                        NATIVE_MEDIA_CENTER_STATE_PLAYING -> true
                        NATIVE_MEDIA_CENTER_STATE_PAUSED,
                        NATIVE_MEDIA_CENTER_STATE_STOPPED,
                        NATIVE_MEDIA_CENTER_STATE_COMPLETED -> false
                        else -> null
                    }

        val progressElapsedMs: Long?
            get() = elapsedMs.takeIf { it > 0L || durationMs > 0L }
    }

    private data class AndroidAutoPlaybackCommandDecision(
            val effectiveIsPlaying: Boolean,
            val audioPlaybackActiveAtResolve: Boolean
    )

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
        startNativeMediaProtectionMonitoring()
        startNativeMediaCenterSourceMonitoring()
        startCarPlayUsbDisconnectMonitoring()
        if (isAndroidAutoNowPlayingMonitorEnabled()) {
            startAndroidAutoNowPlayingMonitoring()
            startAndroidAutoMonitorRefreshLoop()
        } else {
            Log.w(
                    "BottomBarService",
                    "Android Auto now playing monitor disabled for pause diagnostics"
            )
        }
        startAudioMuteStateMonitoring()
        startDynamicOverscanMonitoring()
        ensureAccessibilityServiceEnabled()
        // startAppMonitoring() // Disabled: Legacy focus watchdog replaced by permanent Frida hook

        // Initial timer start
        resetAutoHideTimer()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.w("BottomBarService", "onStartCommand action=${intent?.action ?: "null"} startId=$startId")
        if (intent?.action == ACTION_DEBUG_MEDIA_COMMAND) {
            val debugIntent = Intent(intent)
            val command = debugIntent.getStringExtra(EXTRA_DEBUG_MEDIA_COMMAND)?.trim().orEmpty()
            Log.w(DEBUG_MEDIA_TAG, "enqueue debug media command=$command startId=$startId")
            Thread(
                    {
                        handleDebugMediaCommand(debugIntent)
                    },
                    "BottomBarDebug-$command"
            ).start()
            return START_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleDebugMediaCommand(intent: Intent) {
        val command = intent.getStringExtra(EXTRA_DEBUG_MEDIA_COMMAND)?.trim().orEmpty()
        if (command == "state") {
            logDebugMediaState("before_$command")
        } else {
            logDebugMediaCommandStart(command)
        }
        DisplayAppLauncher.mapAndroidAutoHardKeyPolicyDebugCommand(command)?.let { request ->
            lifecycleScope.launch(Dispatchers.IO) {
                val handled =
                        DisplayAppLauncher.sendAndroidAutoHardKeyPolicyMediaCommand(
                                request = request,
                                reason = "DEBUG_MEDIA_AA_HARDKEY_POLICY"
                        )
                Log.w(
                        DEBUG_MEDIA_TAG,
                        "command=$command route=hardkey_policy keyCode=${request.keyCode} " +
                                "targetDisplay=${request.targetDisplayId ?: "resolved"} handled=$handled"
                )
            }
            return
        }
        when (command) {
            "state" -> Unit
            "card_toggle" -> toggleMediaPlayback()
            "card_next" -> skipMedia(forward = true)
            "card_prev" -> skipMedia(forward = false)
            "aa_next" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val handled = sendAndroidAutoProjectionMediaCommand(forward = true)
                    Log.w(DEBUG_MEDIA_TAG, "command=aa_next handled=$handled")
                }
            }
            "aa_prev" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val handled = sendAndroidAutoProjectionMediaCommand(forward = false)
                    Log.w(DEBUG_MEDIA_TAG, "command=aa_prev handled=$handled")
                }
            }
            "aa_pause" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val handled = sendAndroidAutoProjectionPlaybackCommand(isPlaying = true)
                    Log.w(DEBUG_MEDIA_TAG, "command=aa_pause handled=$handled")
                }
            }
            "aa_play" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val handled = sendAndroidAutoProjectionPlaybackCommand(isPlaying = false)
                    Log.w(DEBUG_MEDIA_TAG, "command=aa_play handled=$handled")
                }
            }
            "aa_pause_aap" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val handled =
                            DisplayAppLauncher.sendAndroidAutoDashboardPlaybackAapCommand(
                                    mediaIsPlayingHint = true
                            )
                    Log.w(DEBUG_MEDIA_TAG, "command=aa_pause_aap handled=$handled")
                }
            }
            "aa_play_aap" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val handled =
                            DisplayAppLauncher.sendAndroidAutoDashboardPlaybackAapCommand(
                                    mediaIsPlayingHint = false
                            )
                    Log.w(DEBUG_MEDIA_TAG, "command=aa_play_aap handled=$handled")
                }
            }
            "aa_toggle_aap" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val handled = DisplayAppLauncher.sendAndroidAutoDashboardPlaybackToggleAapCommand()
                    Log.w(DEBUG_MEDIA_TAG, "command=aa_toggle_aap handled=$handled")
                }
            }
            "aa_pause_mc" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val handled =
                            sendNativeMediaCenterAndroidAutoPlaybackCommand(
                                    targetPlaying = false,
                                    reason = "DEBUG_MEDIA_AA_PAUSE_MC"
                            )
                    Log.w(DEBUG_MEDIA_TAG, "command=aa_pause_mc handled=$handled")
                }
            }
            "aa_play_mc" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val handled =
                            sendNativeMediaCenterAndroidAutoPlaybackCommand(
                                    targetPlaying = true,
                                    reason = "DEBUG_MEDIA_AA_PLAY_MC"
                            )
                    Log.w(DEBUG_MEDIA_TAG, "command=aa_play_mc handled=$handled")
                }
            }
            "aa_focus_hold" -> {
                val handled = requestAndroidAutoPauseOemAudioFocus("DEBUG_MEDIA_AA_FOCUS_HOLD")
                Log.w(DEBUG_MEDIA_TAG, "command=aa_focus_hold handled=$handled")
            }
            "aa_focus_release" -> {
                val handled = abandonAndroidAutoPauseOemAudioFocus("DEBUG_MEDIA_AA_FOCUS_RELEASE")
                Log.w(DEBUG_MEDIA_TAG, "command=aa_focus_release handled=$handled")
            }
            "aa_mute" -> {
                val handled = toggleAndroidAutoMuteFromIntercept()
                Log.w(DEBUG_MEDIA_TAG, "command=aa_mute handled=$handled")
            }
            "aa_usb_recover" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result =
                            AndroidAutoDcmRecovery.recoverUsbProjection(
                                    context = applicationContext,
                                    requestedDevId = intent.getStringExtra(EXTRA_DEBUG_MEDIA_DEV_ID)
                            )
                    Log.w(
                            DEBUG_MEDIA_TAG,
                            "command=aa_usb_recover success=${result.success} " +
                                    "bindFailed=${result.bindFailed} devId=${result.devId} " +
                                    "disconnectUsb=${result.disconnectUsb} resetUsb=${result.resetUsb} " +
                                    "directEnableAoa=${result.directEnableAoa} " +
                                    "connectUsbResult=${result.connectUsbResult} " +
                                    "elapsedMs=${result.elapsedMs} devices=${result.devices}"
                    )
                }
            }
            "aa_to_d0" ->
                    DisplayAppLauncher.requestAndroidAutoDisplayForDebug(
                            displayId = 0,
                            reason = "DEBUG_MEDIA_AA_TO_D0"
                    )
            "aa_to_d3" ->
                    DisplayAppLauncher.requestAndroidAutoDisplayForDebug(
                            displayId = 3,
                            reason = "DEBUG_MEDIA_AA_TO_D3"
                    )
            else -> Log.w(DEBUG_MEDIA_TAG, "unknown debug media command: $command")
        }
    }

    private fun logDebugMediaCommandStart(command: String) {
        val now = SystemClock.elapsedRealtime()
        val protectionRemainingMs = (nativeRadioProtectionUntilMs - now).coerceAtLeast(0L)
        Log.w(
                DEBUG_MEDIA_TAG,
                "[before_$command] mediaPackage=${BottomBarState.mediaPackageName} " +
                        "title=${BottomBarState.mediaTitle} playing=${BottomBarState.mediaIsPlaying} " +
                        "durationMs=${BottomBarState.mediaDurationMs} " +
                        "elapsedMs=${BottomBarState.mediaElapsedMs} " +
                        "progressUpdatedAtMs=${BottomBarState.mediaProgressUpdatedAtMs} " +
                        "canSeek=${BottomBarState.mediaCanSeek} " +
                        "muted=${BottomBarState.mediaIsMuted} monitorAlive=${androidAutoNowPlayingMonitor != null} " +
                        "nativeRadioProtectionMs=$protectionRemainingMs " +
                        "nativeRadioReason=$lastNativeRadioProtectionReason " +
                        "nativeSource=$nativeMediaCenterCurrentSource " +
                        "nativeAudioSource=$nativeMediaCenterCurrentAudioSource"
        )
    }

    private fun logDebugMediaState(reason: String) {
        val now = SystemClock.elapsedRealtime()
        val protectionRemainingMs = (nativeRadioProtectionUntilMs - now).coerceAtLeast(0L)
        Log.w(
                DEBUG_MEDIA_TAG,
                "[$reason] mediaPackage=${BottomBarState.mediaPackageName} " +
                        "title=${BottomBarState.mediaTitle} playing=${BottomBarState.mediaIsPlaying} " +
                        "durationMs=${BottomBarState.mediaDurationMs} " +
                        "elapsedMs=${BottomBarState.mediaElapsedMs} " +
                        "progressUpdatedAtMs=${BottomBarState.mediaProgressUpdatedAtMs} " +
                        "canSeek=${BottomBarState.mediaCanSeek} " +
                        "muted=${BottomBarState.mediaIsMuted} monitorAlive=${androidAutoNowPlayingMonitor != null} " +
                        "monitorLink=${androidAutoNowPlayingMonitor?.isLinkActive()} " +
                        "nativeRadioProtectionMs=$protectionRemainingMs " +
                        "nativeRadioReason=$lastNativeRadioProtectionReason " +
                        "nativeSource=$nativeMediaCenterCurrentSource " +
                        "nativeAudioSource=$nativeMediaCenterCurrentAudioSource " +
                        DisplayAppLauncher.describeAndroidAutoLinkCommandStateForDebug(reason)
        )
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
                    // Mark the CarPlay session as alive: a real now-playing update just
                    // arrived. The USB-disconnect monitor consults this before wiping
                    // media so a transient/erroneous USB "disconnect" read can't clear
                    // artwork + progress out from under an actively playing session.
                    lastCarPlayNowPlayingUpdateAtMs = SystemClock.elapsedRealtime()
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

                    // Do NOT null the artwork just because the track/source changed: CarPlay's
                    // AIDL callback frequently reports the new title/artist a beat before the new
                    // artwork bytes arrive in a follow-up update, so clearing here made the
                    // Impulse dashboard's album art flash off and back on on every track change.
                    // Only replace it once a real bitmap shows up (mirrors the Android Auto
                    // now-playing path below, which already "holds" the last artwork this way);
                    // it still gets wiped by the explicit update.clear branch above when the
                    // CarPlay session actually ends.
                    if (update.artwork != null) {
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
                                val carPlayStreamingAlive =
                                        SystemClock.elapsedRealtime() -
                                                lastCarPlayNowPlayingUpdateAtMs <
                                                CARPLAY_NOWPLAYING_ALIVE_WINDOW_MS
                                withContext(Dispatchers.Main) {
                                    if (!carPlayStreamingAlive &&
                                                    shouldClearCarPlayMediaOnUsbState(
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
        val now = SystemClock.elapsedRealtime()
        if (
                cachedProjectionUsbStateAtMs > 0L &&
                        now - cachedProjectionUsbStateAtMs in 0..PROJECTION_USB_STATE_CACHE_MS
        ) {
            return cachedProjectionUsbState
        }

        val localState =
                runCatching { File(PROJECTION_USB_STATE_PATH).readText().trim() }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
        if (localState != null) {
            cachedProjectionUsbState = localState
            cachedProjectionUsbStateAtMs = now
            return localState
        }

        val shizukuState =
                if (ShizukuUtils.isShizukuAvailable()) {
                    ShizukuUtils.runCommandAndGetOutput(
                        arrayOf("sh", "-c", "cat $PROJECTION_USB_STATE_PATH 2>/dev/null || true")
                    )
                            .trim()
                            .takeIf { it.isNotBlank() }
                } else {
                    null
                }
        cachedProjectionUsbState = shizukuState
        cachedProjectionUsbStateAtMs = now
        return shizukuState
    }

    private fun startNativeMediaProtectionMonitoring() {
        if (nativeMediaStateListener != null) return

        val serviceManager = ServiceManager.getInstance()
        val radioPlayStateKey = CarConstants.SYS_RADIO_PLAY_STATE.getValue()
        val radioChannelInfoKey = CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.getValue()
        val radioRdsChannelInfoKey = CarConstants.SYS_RADIO_RDS_CUR_CHANNEL_INFO.getValue()
        val audioSourceAppKey = CarConstants.SYS_BASIC_AUDIO_SOURCE_APP.getValue()

        fun handleSignal(key: String, value: String?, fromInitialSnapshot: Boolean) {
            val shouldProtect =
                    shouldProtectNativeRadioForTest(
                            key = key,
                            value = value,
                            fromInitialSnapshot = fromInitialSnapshot
                    )
            if (!shouldProtect) return

            lifecycleScope.launch(Dispatchers.Main) {
                activateNativeRadioProtection(
                        "native media signal key=$key value=${value?.take(64) ?: "null"}"
                )
            }
        }

        nativeMediaStateListener =
                object : IDataChanged {
                    override fun onDataChanged(key: String, value: String?) {
                        if (
                                key == radioPlayStateKey ||
                                        key == radioChannelInfoKey ||
                                        key == radioRdsChannelInfoKey ||
                                        key == audioSourceAppKey
                        ) {
                            handleSignal(key, value, fromInitialSnapshot = false)
                        }
                    }
                }
                        .also { serviceManager.addDataChangedListener(it) }

        // Avoid treating stale tuned-channel cache as active radio on service startup.
        handleSignal(radioPlayStateKey, serviceManager.getData(radioPlayStateKey), true)
        handleSignal(audioSourceAppKey, serviceManager.getData(audioSourceAppKey), true)
    }

    private fun startNativeMediaCenterSourceMonitoring() {
        if (nativeMediaCenterSourceMonitorJob != null) return
        bindNativeMediaCenterService()
        nativeMediaCenterSourceMonitorJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    while (isActive) {
                        refreshNativeMediaCenterSourceSnapshot()
                        delay(NATIVE_MEDIA_CENTER_SOURCE_POLL_MS)
                    }
                }
    }

    private fun bindNativeMediaCenterService() {
        if (nativeMediaCenterServiceBinder?.isBinderAlive == false) {
            resetNativeMediaCenterBindersForRebind("service binder is not alive before bind")
        }
        if (nativeMediaCenterServiceConnection != null) return

        val connection =
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        nativeMediaCenterServiceBinder = service
                        nativeMediaCenterPlayServiceBinder = null
                        Log.i("BottomBarService", "Native MediaCenter service connected")
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        nativeMediaCenterServiceBinder = null
                        nativeMediaCenterPlayServiceBinder = null
                        nativeMediaCenterCurrentSource = null
                        nativeMediaCenterCurrentAudioSource = null
                        Log.w("BottomBarService", "Native MediaCenter service disconnected")
                    }
                }

        nativeMediaCenterServiceConnection = connection
        val intent =
                Intent().setComponent(
                        ComponentName(
                                NATIVE_MEDIA_CENTER_PACKAGE,
                                NATIVE_MEDIA_CENTER_SERVICE_CLASS
                        )
                )
        val bound =
                runCatching { applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
                        .getOrElse {
                            Log.w("BottomBarService", "Unable to bind Native MediaCenter service", it)
                            false
                        }
        if (!bound) {
            nativeMediaCenterServiceConnection = null
            Log.w("BottomBarService", "Native MediaCenter service bind returned false")
        }
    }

    private fun resetNativeMediaCenterBindersForRebind(reason: String) {
        nativeMediaCenterServiceBinder = null
        nativeMediaCenterPlayServiceBinder = null
        nativeMediaCenterCurrentSource = null
        nativeMediaCenterCurrentAudioSource = null
        val connection = nativeMediaCenterServiceConnection
        nativeMediaCenterServiceConnection = null
        if (connection != null) {
            runCatching { applicationContext.unbindService(connection) }
                    .onFailure {
                        Log.w(
                                "BottomBarService",
                                "Unable to unbind stale Native MediaCenter service: $reason",
                                it
                        )
                    }
        }
        Log.w("BottomBarService", "Native MediaCenter binders reset for rebind: $reason")
    }

    private suspend fun refreshNativeMediaCenterSourceSnapshot() {
        val playBinder = resolveNativeMediaCenterPlayServiceBinder() ?: return
        val currentSource =
                readNativeMediaCenterInt(
                        playBinder,
                        NATIVE_MEDIA_CENTER_TRANSACTION_GET_CURRENT_SOURCE
                )
        val currentAudioSource =
                readNativeMediaCenterInt(
                        playBinder,
                        NATIVE_MEDIA_CENTER_TRANSACTION_GET_CURRENT_AUDIO_SOURCE
                )

        if (
                currentSource != nativeMediaCenterCurrentSource ||
                        currentAudioSource != nativeMediaCenterCurrentAudioSource
        ) {
            Log.i(
                    "BottomBarService",
                    "Native MediaCenter source source=$currentSource audioSource=$currentAudioSource"
            )
        }

        nativeMediaCenterCurrentSource = currentSource
        nativeMediaCenterCurrentAudioSource = currentAudioSource

        val nativeAndroidAutoActive =
                shouldUseNativeAndroidAutoMediaCenterMetadataForTest(
                        currentSource = currentSource,
                        currentAudioSource = currentAudioSource
                )
        if (nativeAndroidAutoActive) {
            if (!isAndroidAutoMediaTransportReadyForDashboard()) {
                withContext(Dispatchers.Main) {
                    clearAndroidAutoMediaState(
                            "native MediaCenter Android Auto source without active projection session"
                    )
                }
                return
            }
            clearNativeRadioProtectionForAndroidAutoSource("native MediaCenter source snapshot")
            val mediaInfo =
                    readNativeMediaCenterMediaInfoBySource(
                            playBinder,
                            NATIVE_MEDIA_CENTER_ANDROID_AUTO_SOURCE
                    )
            val playState =
                    readNativeMediaCenterPlayStateBySource(
                            playBinder,
                            NATIVE_MEDIA_CENTER_ANDROID_AUTO_SOURCE
                    )
            val artwork = mediaInfo?.let { resolveNativeMediaCenterArtwork(it) }
            withContext(Dispatchers.Main) {
                if (mediaInfo?.hasMetadata == true) {
                    applyNativeAndroidAutoMediaCenterMetadata(
                            mediaInfo = mediaInfo,
                            artwork = artwork,
                            playState = playState,
                            reason = "native MediaCenter source snapshot"
                    )
                } else {
                    if (
                            !applyNativeAndroidAutoMediaCenterPlayState(
                                    playState = playState,
                                    reason = "native MediaCenter source snapshot without metadata"
                            )
                    ) {
                        preserveNativeAndroidAutoMediaCenterState("native MediaCenter source snapshot")
                    }
                }
            }
        }

        if (shouldProtectNativeMediaCenterSourceForTest(currentSource, currentAudioSource)) {
            withContext(Dispatchers.Main) {
                activateNativeRadioProtection(
                        "native MediaCenter source=$currentSource audioSource=$currentAudioSource"
                )
            }
        }
    }

    private fun resolveNativeMediaCenterPlayServiceBinder(): IBinder? {
        nativeMediaCenterPlayServiceBinder?.let {
            if (it.isBinderAlive) return it
            nativeMediaCenterPlayServiceBinder = null
            Log.w("BottomBarService", "Native MediaCenter play binder is not alive")
        }

        val serviceBinder = nativeMediaCenterServiceBinder ?: run {
            bindNativeMediaCenterService()
            return null
        }
        if (!serviceBinder.isBinderAlive) {
            resetNativeMediaCenterBindersForRebind("service binder is not alive")
            bindNativeMediaCenterService()
            return null
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(NATIVE_MEDIA_CENTER_SERVICE_DESCRIPTOR)
            data.writeInt(NATIVE_MEDIA_CENTER_BINDER_PLAY_SERVICE)
            val ok =
                    serviceBinder.transact(
                            NATIVE_MEDIA_CENTER_TRANSACTION_QUERY_BINDER,
                            data,
                            reply,
                            0
                    )
            if (!ok) return null
            reply.readException()
            reply.readStrongBinder()?.also {
                nativeMediaCenterPlayServiceBinder = it
                Log.i("BottomBarService", "Native MediaCenter play binder resolved")
            }
        } catch (e: Exception) {
            nativeMediaCenterPlayServiceBinder = null
            Log.w("BottomBarService", "Unable to resolve Native MediaCenter play binder", e)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun readNativeMediaCenterPlayStateBySource(
            playBinder: IBinder,
            source: Int
    ): NativeMediaCenterPlayState? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(NATIVE_MEDIA_CENTER_PLAY_SERVICE_DESCRIPTOR)
            data.writeInt(source)
            val ok =
                    playBinder.transact(
                            NATIVE_MEDIA_CENTER_TRANSACTION_GET_PLAY_STATE_BY_SOURCE,
                            data,
                            reply,
                            0
                    )
            if (!ok) return null
            reply.readException()
            if (reply.readInt() == 0) return null

            val mediaSource = reply.readInt()
            val state = reply.readInt()
            val durationMs = reply.readLong().coerceAtLeast(0L)
            reply.readLong() // buffered position
            reply.readFloat() // speed
            reply.readLong() // tcp speed
            reply.readString() // error
            val elapsedMs = reply.readLong().coerceAtLeast(0L)
            NativeMediaCenterPlayState(
                    mediaSource = mediaSource,
                    state = state,
                    durationMs = durationMs,
                    elapsedMs = elapsedMs
            )
        } catch (e: Exception) {
            nativeMediaCenterPlayServiceBinder = null
            Log.w(
                    "BottomBarService",
                    "Unable to read Native MediaCenter Android Auto play state source=$source",
                    e
            )
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun readNativeMediaCenterInt(playBinder: IBinder, transactionCode: Int): Int? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(NATIVE_MEDIA_CENTER_PLAY_SERVICE_DESCRIPTOR)
            val ok = playBinder.transact(transactionCode, data, reply, 0)
            if (!ok) return null
            reply.readException()
            reply.readInt()
        } catch (e: Exception) {
            nativeMediaCenterPlayServiceBinder = null
            Log.w(
                    "BottomBarService",
                    "Unable to read Native MediaCenter source transaction=$transactionCode",
                    e
            )
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun readNativeMediaCenterMediaInfoBySource(
            playBinder: IBinder,
            source: Int
    ): NativeMediaCenterMediaInfo? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(NATIVE_MEDIA_CENTER_PLAY_SERVICE_DESCRIPTOR)
            data.writeInt(source)
            val ok =
                    playBinder.transact(
                            NATIVE_MEDIA_CENTER_TRANSACTION_GET_PLAY_MEDIA_INFO_BY_SOURCE,
                            data,
                            reply,
                            0
                    )
            if (!ok) return null
            reply.readException()
            if (reply.readInt() == 0) return null

            val mediaSource = reply.readInt()
            reply.readByte()
            val serializableClassName = reply.readString()
            if (serializableClassName != null) {
                reply.createByteArray()
            }
            val parcelableClassName = reply.readString()
            if (parcelableClassName == null) {
                Log.w(
                        "BottomBarService",
                        "Native MediaCenter Android Auto metadata returned empty value source=$source"
                )
                return null
            }
            val value = MediaInfo(reply)
            NativeMediaCenterMediaInfo(
                    mediaSource = mediaSource,
                    title = value.title?.takeIf { it.isNotBlank() },
                    artist = value.author?.takeIf { it.isNotBlank() },
                    album = value.albumId?.takeIf { it.isNotBlank() },
                    imageUrl = value.imageUrl?.takeIf { it.isNotBlank() },
                    imageBitmap = value.imageBitmap,
                    durationMs = value.duration.takeIf { it > 0L } ?: 0L
            )
        } catch (e: Exception) {
            nativeMediaCenterPlayServiceBinder = null
            Log.w(
                    "BottomBarService",
                    "Unable to read Native MediaCenter Android Auto metadata source=$source",
                    e
            )
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun activateNativeRadioProtection(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (clearNativeRadioProtectionForAndroidAutoSource(reason, now)) {
            return
        }
        val wasInactive = nativeRadioProtectionUntilMs <= now
        nativeRadioProtectionUntilMs = now + NATIVE_RADIO_PROTECTION_HOLD_MS
        lastNativeRadioProtectionReason = reason

        if (wasInactive) {
            Log.i(
                    "BottomBarService",
                    "Native radio media protection enabled for ${NATIVE_RADIO_PROTECTION_HOLD_MS}ms: $reason"
            )
        }

        stopAndroidAutoNowPlayingMonitoring("native radio active")
        clearAndroidAutoMediaState("native radio active")
    }

    private fun clearNativeRadioProtectionForAndroidAutoSource(
            reason: String,
            now: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        if (
                !shouldIgnoreNativeRadioProtectionForAndroidAutoSourceForTest(
                        currentSource = nativeMediaCenterCurrentSource,
                        currentAudioSource = nativeMediaCenterCurrentAudioSource
                )
        ) {
            return false
        }

        val hadProtection = nativeRadioProtectionUntilMs > now
        nativeRadioProtectionUntilMs = 0L
        if (hadProtection) {
            Log.i(
                    "BottomBarService",
                    "Ignoring native radio protection while Android Auto MediaCenter source is active: " +
                            "$reason source=$nativeMediaCenterCurrentSource " +
                            "audioSource=$nativeMediaCenterCurrentAudioSource " +
                            "previousReason=$lastNativeRadioProtectionReason"
            )
        }
        lastNativeRadioProtectionReason =
                "ignored while Android Auto MediaCenter source active: $reason"
        return true
    }

    private fun isNativeRadioProtectionActive(queryNativeGuard: Boolean = true): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (nativeRadioProtectionUntilMs > now) {
            if (clearNativeRadioProtectionForAndroidAutoSource("active guard check", now)) {
                return false
            }
            return true
        }
        if (
                shouldProtectNativeMediaCenterSourceForTest(
                        nativeMediaCenterCurrentSource,
                        nativeMediaCenterCurrentAudioSource
                )
        ) {
            lifecycleScope.launch(Dispatchers.Main) {
                activateNativeRadioProtection(
                        "native MediaCenter cached source=$nativeMediaCenterCurrentSource " +
                                "audioSource=$nativeMediaCenterCurrentAudioSource"
                )
            }
            return true
        }
        if (isNativeAndroidAutoMediaCenterActive()) return false
        if (!queryNativeGuard) return false
        if (DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMedia("BOTTOM_BAR_NATIVE_RADIO_GUARD")) {
            lifecycleScope.launch(Dispatchers.Main) {
                activateNativeRadioProtection("native radio guard fetch")
            }
            return true
        }
        return false
    }

    private fun stopAndroidAutoNowPlayingMonitoring(reason: String) {
        val monitor = androidAutoNowPlayingMonitor ?: return
        Log.i("BottomBarService", "Stopping Android Auto now playing monitor: $reason")
        monitor.stop()
        androidAutoNowPlayingMonitor = null
    }

    private fun startAndroidAutoMonitorRefreshLoop() {
        if (!isAndroidAutoNowPlayingMonitorEnabled()) {
            stopAndroidAutoNowPlayingMonitoring("disabled for pause diagnostics")
            androidAutoMonitorRefreshJob?.cancel()
            androidAutoMonitorRefreshJob = null
            return
        }
        if (androidAutoMonitorRefreshJob != null) return
        androidAutoMonitorRefreshJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    while (isActive) {
                        if (
                                !isNativeRadioProtectionActive(queryNativeGuard = true) &&
                                        isAndroidAutoMediaSessionReadyForDashboard()
                        ) {
                            withContext(Dispatchers.Main) {
                                startAndroidAutoNowPlayingMonitoring(skipReadinessCheck = true)
                            }
                        }
                        delay(ANDROID_AUTO_MONITOR_REFRESH_INTERVAL_MS)
                    }
                }
    }

    private fun startAndroidAutoNowPlayingMonitoring(skipReadinessCheck: Boolean = false) {
        if (!isAndroidAutoNowPlayingMonitorEnabled()) {
            stopAndroidAutoNowPlayingMonitoring("disabled for pause diagnostics")
            return
        }
        if (androidAutoNowPlayingMonitor != null) return
        if (isNativeRadioProtectionActive(queryNativeGuard = true)) return
        if (!skipReadinessCheck && !isAndroidAutoMediaSessionReadyForDashboard()) return
        androidAutoNowPlayingMonitor =
                AndroidAutoNowPlayingMonitor(this, lifecycleScope) { update ->
                    if (isNativeRadioProtectionActive(queryNativeGuard = true)) {
                        stopAndroidAutoNowPlayingMonitoring("native radio active during update")
                        clearAndroidAutoMediaState("native radio active during Android Auto update")
                        return@AndroidAutoNowPlayingMonitor
                    }

                    if (!isAndroidAutoMediaSessionReadyForDashboard()) {
                        clearAndroidAutoMediaState("Android Auto projection session not ready")
                        return@AndroidAutoNowPlayingMonitor
                    }

                    if (update.clear) {
                        val projectionSessionReady =
                                DisplayAppLauncher.isAndroidAutoProjectionSessionReadyForMedia(
                                        "AA_NOW_PLAYING_CLEAR"
                                )
                        if (
                                !shouldApplyAndroidAutoNowPlayingClearForTest(
                                        projectionSessionReady = projectionSessionReady,
                                        clearReason = update.clearReason
                                )
                        ) {
                            Log.w(
                                    "BottomBarService",
                                    "Holding Android Auto media metadata during active projection clear " +
                                            "reason=${update.clearReason ?: "unknown"}"
                            )
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
                    update.isPlaying
                            ?.let {
                                resolveNativeAndroidAutoPlaybackState(
                                        incomingIsPlaying = it,
                                        incomingElapsedMs = update.elapsedMs,
                                        reason = "Android Auto now playing update"
                                )
                            }
                            ?.let { BottomBarState.mediaIsPlaying = it }

                    val durationMs = update.durationMs ?: BottomBarState.mediaDurationMs
                    val elapsedMs = update.elapsedMs ?: BottomBarState.mediaElapsedMs
                    updateMediaProgressState(
                            durationMs = durationMs,
                            elapsedMs = elapsedMs,
                            updatedAtMs = update.progressUpdatedAtMs,
                            canSeek = false,
                            allowProgressRegression = sourceChanged || trackChanged
                    )

                    if (nextSignature != null) {
                        lastAndroidAutoMediaSignature = nextSignature
                    }
                }
                        .also { it.start() }
    }

    private fun isAndroidAutoNowPlayingMonitorEnabled(): Boolean =
            ANDROID_AUTO_NOW_PLAYING_MONITOR_ENABLED

    private fun isAndroidAutoMediaSessionReadyForDashboard(): Boolean {
        if (isNativeRadioProtectionActive(queryNativeGuard = true)) {
            cachedAndroidAutoMediaSessionReady = false
            cachedAndroidAutoMediaSessionReadyAtMs = SystemClock.elapsedRealtime()
            return false
        }

        val now = SystemClock.elapsedRealtime()
        if (
                cachedAndroidAutoMediaSessionReadyAtMs > 0L &&
                        now - cachedAndroidAutoMediaSessionReadyAtMs in
                                0..ANDROID_AUTO_MEDIA_SESSION_READY_CACHE_MS
        ) {
            return cachedAndroidAutoMediaSessionReady
        }

        if (androidAutoNowPlayingMonitor?.isLinkActive() == true) {
            cachedAndroidAutoMediaSessionReady = true
            cachedAndroidAutoMediaSessionReadyAtMs = now
            return true
        }

        val rawUsbState = readProjectionUsbState()
        if (rawUsbState != null) {
            if (isProjectionUsbReadyForMedia(rawUsbState)) {
                cachedAndroidAutoMediaSessionReady = true
                cachedAndroidAutoMediaSessionReadyAtMs = now
                return true
            }
            if (DisplayAppLauncher.isAndroidAutoProjectionLinkActiveIfAlreadyBoundForMedia(
                            "AA_NOW_PLAYING_UPDATE"
                    )
            ) {
                cachedAndroidAutoMediaSessionReady = true
                cachedAndroidAutoMediaSessionReadyAtMs = now
                return true
            }
            if (DisplayAppLauncher.hasAndroidAutoVisualTaskForMedia()) {
                cachedAndroidAutoMediaSessionReady = true
                cachedAndroidAutoMediaSessionReadyAtMs = now
                return true
            }
            if (DisplayAppLauncher.hasActiveAndroidAutoAudioPlaybackForMedia("AA_NOW_PLAYING_AUDIO")) {
                cachedAndroidAutoMediaSessionReady = true
                cachedAndroidAutoMediaSessionReadyAtMs = now
                return true
            }
            cachedAndroidAutoMediaSessionReady = false
            cachedAndroidAutoMediaSessionReadyAtMs = now
            return false
        }
        if (DisplayAppLauncher.hasAndroidAutoVisualTaskForMedia()) {
            cachedAndroidAutoMediaSessionReady = true
            cachedAndroidAutoMediaSessionReadyAtMs = now
            return true
        }
        if (DisplayAppLauncher.hasActiveAndroidAutoAudioPlaybackForMedia("AA_NOW_PLAYING_AUDIO")) {
            cachedAndroidAutoMediaSessionReady = true
            cachedAndroidAutoMediaSessionReadyAtMs = now
            return true
        }
        val ready =
                DisplayAppLauncher.isAndroidAutoProjectionSessionReadyForMedia(
                        "AA_NOW_PLAYING_UPDATE"
                )
        cachedAndroidAutoMediaSessionReady = ready
        cachedAndroidAutoMediaSessionReadyAtMs = now
        return ready
    }

    private fun isAndroidAutoMediaTransportReadyForDashboard(): Boolean {
        if (isNativeRadioProtectionActive(queryNativeGuard = true)) return false
        if (androidAutoNowPlayingMonitor?.isLinkActive() == true) return true
        readProjectionUsbState()?.let { rawState ->
            if (isProjectionUsbReadyForMedia(rawState)) return true
        }
        if (
                DisplayAppLauncher.isAndroidAutoProjectionLinkActiveIfAlreadyBoundForMedia(
                        "AA_MEDIA_TRANSPORT"
                )
        ) {
            return true
        }
        if (DisplayAppLauncher.hasActiveAndroidAutoAudioPlaybackForMedia("AA_MEDIA_TRANSPORT_AUDIO")) {
            return true
        }
        return shouldUseNativeAndroidAutoMediaCenterMetadataForTest(
                nativeMediaCenterCurrentSource,
                nativeMediaCenterCurrentAudioSource
        )
    }

    private fun startAudioMuteStateMonitoring() {
        if (audioMuteStateListener != null) return
        val serviceManager = ServiceManager.getInstance()
        val mediaMuteKey = CarConstants.SYS_SETTINGS_AUDIO_MEDIA_MUTE_STATE.getValue()
        val mainMuteKey = CarConstants.SYS_SETTINGS_AUDIO_MUTE_STATE.getValue()

        fun syncMuteState() {
            readCurrentAudioMuteState()?.let { BottomBarState.mediaIsMuted = it }
        }

        syncMuteState()

        audioMuteStateListener =
                object : IDataChanged {
                    override fun onDataChanged(key: String, value: String?) {
                        if (key == mediaMuteKey || key == mainMuteKey) {
                            syncMuteState()
                        }
                    }
                }
                        .also { serviceManager.addDataChangedListener(it) }
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
                        val preserved =
                                withContext(Dispatchers.Main) {
                                    preserveNativeAndroidAutoMediaCenterState("no active media session")
                                }
                        if (!preserved) {
                            withContext(Dispatchers.Main) { clearMediaState(preserveCarPlay = true) }
                        }
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

                    if (isCarPlayMediaPackage(packageName) && !hasMetadata) {
                        // The CarPlay bridge exposes itself as a normal MediaSession too, and it
                        // sends transient blank metadata blips between real updates (e.g. during
                        // track transitions). shouldKeepProjectionMediaState() lets CarPlay's own
                        // updates through unconditionally, which bypasses
                        // CarPlayNowPlayingMonitor's debounce on this path — mirror that debounce
                        // here so a blank blip doesn't wipe the Impulse dashboard's album artwork.
                        // A subsequent real update cancels mediaMetadataPublishJob and supersedes
                        // this delay before it applies.
                        delay(CARPLAY_MEDIA_SESSION_BLANK_DEBOUNCE_MS)
                    }

                    withContext(Dispatchers.Main) {
                        val androidAutoFallbackPackage =
                                resolveAndroidAutoProjectionFallbackMediaPackage(packageName, hasMetadata)
                        if (androidAutoFallbackPackage != null) {
                            val currentPackageName = BottomBarState.mediaPackageName
                            val fallbackTrackChanged =
                                    !isAndroidAutoMediaPackage(currentPackageName) ||
                                            hasKnownMediaFieldChanged(
                                                    BottomBarState.mediaTitle,
                                                    title
                                            ) ||
                                            hasKnownMediaFieldChanged(
                                                    BottomBarState.mediaArtist,
                                                    artist
                                            ) ||
                                            hasKnownMediaFieldChanged(
                                                    BottomBarState.mediaAlbum,
                                                    album
                                            )
                            BottomBarState.mediaTitle = title?.takeIf { it.isNotBlank() }
                            BottomBarState.mediaArtist = artist?.takeIf { it.isNotBlank() }
                            BottomBarState.mediaAlbum = album?.takeIf { it.isNotBlank() }
                            BottomBarState.mediaArtwork = artwork
                            BottomBarState.mediaPackageName = androidAutoFallbackPackage
                            if (
                                    shouldApplyAndroidAutoProjectionFallbackPlaybackStateForTest(
                                            currentMediaPackageName = currentPackageName,
                                            fallbackIsPlaying = isPlaying
                                    )
                            ) {
                                BottomBarState.mediaIsPlaying = isPlaying
                            }
                            updateMediaProgressState(
                                    durationMs = durationMs,
                                    elapsedMs = elapsedMs,
                                    updatedAtMs = progressUpdatedAtMs,
                                    canSeek = false,
                                    allowProgressRegression = fallbackTrackChanged
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
            if (preserveNativeAndroidAutoMediaCenterState("clear media state")) {
                return@launch
            }
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
        lastNativeAndroidAutoMediaInfoSignature = null
        synchronized(androidAutoMediaCommandLock) {
            lastAndroidAutoMediaCommandName = null
            lastAndroidAutoMediaCommandAtMs = 0L
        }
        if (preserveNativeAndroidAutoMediaCenterState(reason)) {
            return
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
            canSeek: Boolean,
            allowProgressRegression: Boolean = false
    ) {
        val normalizedDuration = durationMs.coerceAtLeast(0L)
        val normalizedAllowProgressRegression =
                allowProgressRegression ||
                        shouldAllowPendingAndroidAutoProgressRegressionForCurrentState()
        val normalizedElapsed =
                if (normalizedDuration > 0L) {
                    elapsedMs.coerceIn(0L, normalizedDuration)
                } else {
                    elapsedMs.coerceAtLeast(0L)
                }
        if (
                shouldPreserveAndroidAutoProgressRegressionForTest(
                        currentMediaPackageName = BottomBarState.mediaPackageName,
                        previousElapsedMs = BottomBarState.mediaElapsedMs,
                        incomingElapsedMs = normalizedElapsed,
                        previousDurationMs = BottomBarState.mediaDurationMs,
                        durationMs = normalizedDuration,
                        isPlaying = BottomBarState.mediaIsPlaying,
                        allowProgressRegression = normalizedAllowProgressRegression
                )
        ) {
            if (normalizedDuration > 0L) {
                BottomBarState.mediaDurationMs = normalizedDuration
            }
            BottomBarState.mediaCanSeek = canSeek && BottomBarState.mediaDurationMs > 0L
            Log.d(
                    "BottomBarService",
                    "Holding Android Auto media progress regression " +
                            "previous=${BottomBarState.mediaElapsedMs} incoming=$normalizedElapsed"
            )
            return
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

    private fun markAndroidAutoProgressRegressionAllowed(reason: String) {
        androidAutoProgressRegressionAllowedUntilMs =
                SystemClock.elapsedRealtime() +
                        ANDROID_AUTO_PROGRESS_EXPLICIT_COMMAND_RESET_WINDOW_MS
        Log.d(
                "BottomBarService",
                "Allowing Android Auto progress regression after $reason " +
                        "until=$androidAutoProgressRegressionAllowedUntilMs"
        )
    }

    private fun resetAndroidAutoProgressAfterExplicitTrackCommand(reason: String) {
        val currentMediaPackageName = BottomBarState.mediaPackageName
        val nativeAndroidAutoMediaCenterActive = isNativeAndroidAutoMediaCenterActive()
        val previousElapsedMs = BottomBarState.mediaElapsedMs
        val durationMs = BottomBarState.mediaDurationMs
        if (
                !shouldResetAndroidAutoProgressAfterExplicitTrackCommandForTest(
                        currentMediaPackageName = currentMediaPackageName,
                        nativeAndroidAutoMediaCenterActive = nativeAndroidAutoMediaCenterActive
                )
        ) {
            Log.w(
                    "BottomBarService",
                    "Skipped Android Auto media progress reset after $reason " +
                            "mediaPackage=$currentMediaPackageName " +
                            "nativeMediaCenterActive=$nativeAndroidAutoMediaCenterActive " +
                            "elapsedMs=$previousElapsedMs durationMs=$durationMs"
            )
            return
        }
        updateMediaProgressState(
                durationMs = durationMs,
                elapsedMs = 0L,
                updatedAtMs = SystemClock.elapsedRealtime(),
                canSeek = false,
                allowProgressRegression = true
        )
        Log.w(
                "BottomBarService",
                "Reset Android Auto media progress after $reason " +
                        "previousElapsedMs=$previousElapsedMs durationMs=$durationMs"
        )
    }

    private fun shouldAllowPendingAndroidAutoProgressRegressionForCurrentState(): Boolean {
        if (
                !isAndroidAutoMediaPackage(BottomBarState.mediaPackageName) &&
                        !isNativeAndroidAutoMediaCenterActive()
        ) {
            return false
        }
        val allowedUntilMs = androidAutoProgressRegressionAllowedUntilMs
        if (allowedUntilMs <= 0L) return false
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs <= allowedUntilMs) return true
        androidAutoProgressRegressionAllowedUntilMs = 0L
        return false
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
                    canSeek = BottomBarState.mediaCanSeek,
                    allowProgressRegression = true
            )
                }
            }
        }
    }

    private fun skipMedia(forward: Boolean) {
        val packageName = BottomBarState.mediaPackageName
        lifecycleScope.launch(Dispatchers.IO) {
            val useAndroidAutoLinkRoute = shouldUseAndroidAutoProjectionRouteForCard(packageName)
            if (useAndroidAutoLinkRoute) {
                Log.i(
                        "BottomBarService",
                        "Routing dashboard Android Auto media ${if (forward) "next" else "previous"} " +
                                "through LinkCommand " +
                                "source=$nativeMediaCenterCurrentSource " +
                                "audioSource=$nativeMediaCenterCurrentAudioSource " +
                                "package=${packageName ?: "none"}"
                )
            }
            val handled =
                    when {
                        useAndroidAutoLinkRoute ->
                                sendAndroidAutoProjectionMediaCommand(forward)
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
                if (useAndroidAutoLinkRoute) {
                    withContext(Dispatchers.Main) {
                        BottomBarState.mediaPackageName = ANDROID_AUTO_MEDIA_PACKAGE
                    }
                }
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
            val useAndroidAutoProjectionRoute = shouldUseAndroidAutoProjectionRouteForCard(packageName)
            if (useAndroidAutoProjectionRoute) {
                Log.i(
                        "BottomBarService",
                        "Routing dashboard Android Auto media ${if (isPlaying) "pause" else "play"} " +
                                "through LinkCommand package=${packageName ?: "none"} " +
                                "source=$nativeMediaCenterCurrentSource " +
                                "audioSource=$nativeMediaCenterCurrentAudioSource"
                )
            }
            val handled =
                    when {
                        useAndroidAutoProjectionRoute ->
                                sendAndroidAutoProjectionPlaybackCommand(isPlaying)
                        isCarPlayMediaPackage(packageName) ->
                                carPlayNowPlayingMonitor?.playPause(isPlaying) == true
                        else -> toggleAndroidMediaController(packageName, isPlaying)
            }
            if (handled) {
                if (useAndroidAutoProjectionRoute) {
                    withContext(Dispatchers.Main) {
                        BottomBarState.mediaPackageName = ANDROID_AUTO_MEDIA_PACKAGE
                    }
                }
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

    private fun shouldUseNativeAndroidAutoMediaCenterRoute(packageName: String?): Boolean {
        if (isCarPlayMediaPackage(packageName)) return false
        return isNativeAndroidAutoMediaCenterActive() && isAndroidAutoMediaTransportReadyForDashboard()
    }

    private fun shouldUseAndroidAutoProjectionRouteForCard(packageName: String?): Boolean {
        if (isCarPlayMediaPackage(packageName)) return false
        if (isAndroidAutoMediaPackage(packageName)) return isAndroidAutoMediaTransportReadyForDashboard()
        if (isNativeAndroidAutoMediaCenterActive()) return isAndroidAutoMediaTransportReadyForDashboard()
        if (packageName != null) return false
        if (isNativeRadioProtectionActive(queryNativeGuard = false)) return false
        return isAndroidAutoMediaTransportReadyForDashboard()
    }

    private fun isNativeAndroidAutoMediaCenterActive(): Boolean {
        return shouldUseNativeAndroidAutoMediaCenterMetadataForTest(
                nativeMediaCenterCurrentSource,
                nativeMediaCenterCurrentAudioSource
        )
    }

    private fun markNativeAndroidAutoPlaybackCommandTarget(targetPlaying: Boolean) {
        val nowMs = SystemClock.elapsedRealtime()
        nativeAndroidAutoMediaCenterIsPlayingGuess = targetPlaying
        nativeAndroidAutoPlaybackCommandTarget = targetPlaying
        nativeAndroidAutoPlaybackCommandTargetAtMs = nowMs
        nativeAndroidAutoPlaybackCommandTargetElapsedMs = BottomBarState.mediaElapsedMs
        BottomBarState.mediaPackageName = ANDROID_AUTO_MEDIA_PACKAGE
        BottomBarState.mediaIsPlaying = targetPlaying
        updateMediaProgressState(
                durationMs = BottomBarState.mediaDurationMs,
                elapsedMs = BottomBarState.mediaElapsedMs,
                updatedAtMs = nowMs,
                canSeek = false
        )
    }

    private fun resolveNativeAndroidAutoPlaybackState(
            incomingIsPlaying: Boolean?,
            incomingElapsedMs: Long?,
            reason: String
    ): Boolean? {
        val targetPlaying = nativeAndroidAutoPlaybackCommandTarget
        val targetAtMs = nativeAndroidAutoPlaybackCommandTargetAtMs
        val targetElapsedMs = nativeAndroidAutoPlaybackCommandTargetElapsedMs
        val nowMs = SystemClock.elapsedRealtime()
        val shouldHold =
                shouldHoldNativeAndroidAutoPlaybackCommandTargetForTest(
                        targetPlaying = targetPlaying,
                        targetAtMs = targetAtMs,
                        targetElapsedMs = targetElapsedMs,
                        incomingIsPlaying = incomingIsPlaying,
                        incomingElapsedMs = incomingElapsedMs,
                        nowMs = nowMs
                )
        if (shouldHold && targetPlaying != null) {
            if (incomingIsPlaying != null && incomingIsPlaying != targetPlaying) {
                Log.w(
                        "BottomBarService",
                        "Holding Android Auto playback command target=$targetPlaying " +
                                "over stale native state=$incomingIsPlaying reason=$reason"
                )
            }
            return targetPlaying
        }
        if (targetPlaying != null) {
            nativeAndroidAutoPlaybackCommandTarget = null
            nativeAndroidAutoPlaybackCommandTargetAtMs = 0L
            nativeAndroidAutoPlaybackCommandTargetElapsedMs = 0L
        }
        return incomingIsPlaying
    }

    private fun preserveNativeAndroidAutoMediaCenterState(reason: String): Boolean {
        if (!isNativeAndroidAutoMediaCenterActive()) return false
        if (!isAndroidAutoMediaTransportReadyForDashboard()) return false
        if (isCarPlayMediaPackage(BottomBarState.mediaPackageName)) return false

        val wasAndroidAuto = isAndroidAutoMediaPackage(BottomBarState.mediaPackageName)
        BottomBarState.mediaPackageName = ANDROID_AUTO_MEDIA_PACKAGE
        if (!wasAndroidAuto) {
            BottomBarState.mediaIsPlaying = nativeAndroidAutoMediaCenterIsPlayingGuess
        }
        if (!wasAndroidAuto) {
            Log.i(
                    "BottomBarService",
                    "Preserving Android Auto media state from native MediaCenter: $reason " +
                            "source=$nativeMediaCenterCurrentSource audioSource=$nativeMediaCenterCurrentAudioSource " +
                            "playingGuess=$nativeAndroidAutoMediaCenterIsPlayingGuess"
            )
        }
        return true
    }

    private fun resolveNativeMediaCenterArtwork(mediaInfo: NativeMediaCenterMediaInfo): Bitmap? {
        mediaInfo.imageBitmap?.let { return normalizeMediaArtwork(it) }
        return decodeMediaArtworkUri(mediaInfo.imageUrl)
    }

    private fun applyNativeAndroidAutoMediaCenterMetadata(
            mediaInfo: NativeMediaCenterMediaInfo,
            artwork: Bitmap?,
            playState: NativeMediaCenterPlayState?,
            reason: String
    ): Boolean {
        if (!isNativeAndroidAutoMediaCenterActive()) return false
        if (!isAndroidAutoMediaTransportReadyForDashboard()) return false
        if (isCarPlayMediaPackage(BottomBarState.mediaPackageName)) return false

        val wasAndroidAuto = isAndroidAutoMediaPackage(BottomBarState.mediaPackageName)
        val sourceChanged = !wasAndroidAuto
        val stateTrackChanged =
                wasAndroidAuto &&
                        (hasKnownMediaFieldChanged(BottomBarState.mediaTitle, mediaInfo.title) ||
                                hasKnownMediaFieldChanged(
                                        BottomBarState.mediaArtist,
                                        mediaInfo.artist
                                ) ||
                                hasKnownMediaFieldChanged(BottomBarState.mediaAlbum, mediaInfo.album))
        val previousSignature = lastAndroidAutoMediaSignature
        val nextSignature = nativeAndroidAutoMediaSignature(mediaInfo, artwork)
        val trackChanged =
                nextSignature != null &&
                        previousSignature != null &&
                        nextSignature != previousSignature
        val explicitTrackCommandReset = shouldAllowPendingAndroidAutoProgressRegressionForCurrentState()
        val shouldResetProgress =
                sourceChanged || trackChanged || stateTrackChanged || explicitTrackCommandReset
        val matchingPlayState =
                playState?.takeIf {
                    shouldAcceptNativeAndroidAutoPlayStateForTest(
                            playStateMediaSource = it.mediaSource,
                            mediaInfoSource = mediaInfo.mediaSource
                    )
                }

        if (mediaInfo.title != null) {
            BottomBarState.mediaTitle = mediaInfo.title
        } else if (sourceChanged) {
            BottomBarState.mediaTitle = null
        }

        if (mediaInfo.artist != null) {
            BottomBarState.mediaArtist = mediaInfo.artist
        } else if (sourceChanged) {
            BottomBarState.mediaArtist = null
        }

        if (mediaInfo.album != null) {
            BottomBarState.mediaAlbum = mediaInfo.album
        } else if (sourceChanged || trackChanged) {
            BottomBarState.mediaAlbum = null
        }

        if (artwork != null) {
            BottomBarState.mediaArtwork = artwork
        } else if (sourceChanged) {
            BottomBarState.mediaArtwork = null
        } else if (trackChanged && mediaInfo.imageBitmap == null && mediaInfo.imageUrl == null) {
            Log.d(
                    "BottomBarService",
                    "Holding Android Auto artwork until native MediaCenter provides a new bitmap"
            )
        }

        BottomBarState.mediaPackageName = ANDROID_AUTO_MEDIA_PACKAGE
        val nativeIsPlaying =
                resolveNativeAndroidAutoPlaybackState(
                        incomingIsPlaying = matchingPlayState?.isPlaying,
                        incomingElapsedMs = matchingPlayState?.progressElapsedMs,
                        reason = reason
                )
        if (nativeIsPlaying != null) {
            nativeAndroidAutoMediaCenterIsPlayingGuess = nativeIsPlaying
            BottomBarState.mediaIsPlaying = nativeIsPlaying
        } else if (!wasAndroidAuto) {
            BottomBarState.mediaIsPlaying = nativeAndroidAutoMediaCenterIsPlayingGuess
        }

        val durationMs =
                matchingPlayState?.durationMs?.takeIf { it > 0L }
                        ?: mediaInfo.durationMs.takeIf { it > 0L }
                        ?: BottomBarState.mediaDurationMs
        val nowMs = SystemClock.elapsedRealtime()
        val elapsedMs =
                resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = BottomBarState.mediaElapsedMs,
                        durationMs = durationMs,
                        progressUpdatedAtMs = BottomBarState.mediaProgressUpdatedAtMs,
                        nowMs = nowMs,
                        isPlaying = BottomBarState.mediaIsPlaying,
                        trackChanged = shouldResetProgress,
                        nativeElapsedMs = matchingPlayState?.progressElapsedMs
                )
        updateMediaProgressState(
                durationMs = durationMs,
                elapsedMs = elapsedMs,
                updatedAtMs = nowMs,
                canSeek = false,
                allowProgressRegression = shouldResetProgress
        )

        if (nextSignature != null) {
            lastAndroidAutoMediaSignature = nextSignature
        }
        logNativeAndroidAutoMediaInfo(mediaInfo, artwork, reason)
        return true
    }

    private fun applyNativeAndroidAutoMediaCenterPlayState(
            playState: NativeMediaCenterPlayState?,
            reason: String
    ): Boolean {
        if (!isNativeAndroidAutoMediaCenterActive()) return false
        if (!isAndroidAutoMediaTransportReadyForDashboard()) return false
        if (isCarPlayMediaPackage(BottomBarState.mediaPackageName)) return false
        val matchingPlayState =
                playState?.takeIf {
                    shouldAcceptNativeAndroidAutoPlayStateForTest(
                            playStateMediaSource = it.mediaSource,
                            mediaInfoSource = null
                    )
                } ?: return false
        val nativeIsPlaying =
                resolveNativeAndroidAutoPlaybackState(
                        incomingIsPlaying = matchingPlayState.isPlaying,
                        incomingElapsedMs = matchingPlayState.progressElapsedMs,
                        reason = reason
                ) ?: return false

        nativeAndroidAutoMediaCenterIsPlayingGuess = nativeIsPlaying
        BottomBarState.mediaPackageName = ANDROID_AUTO_MEDIA_PACKAGE
        BottomBarState.mediaIsPlaying = nativeIsPlaying

        val durationMs = matchingPlayState.durationMs.takeIf { it > 0L } ?: BottomBarState.mediaDurationMs
        val nowMs = SystemClock.elapsedRealtime()
        val explicitTrackCommandReset = shouldAllowPendingAndroidAutoProgressRegressionForCurrentState()
        val elapsedMs =
                resolveNativeAndroidAutoProgressElapsedForTest(
                        previousElapsedMs = BottomBarState.mediaElapsedMs,
                        durationMs = durationMs,
                        progressUpdatedAtMs = BottomBarState.mediaProgressUpdatedAtMs,
                        nowMs = nowMs,
                        isPlaying = nativeIsPlaying,
                        trackChanged = explicitTrackCommandReset,
                        nativeElapsedMs = matchingPlayState.progressElapsedMs
                )
        updateMediaProgressState(
                durationMs = durationMs,
                elapsedMs = elapsedMs,
                updatedAtMs = nowMs,
                canSeek = false,
                allowProgressRegression = explicitTrackCommandReset
        )
        Log.i(
                "BottomBarService",
                "Native MediaCenter Android Auto playback $reason " +
                        "source=${matchingPlayState.mediaSource} state=${matchingPlayState.state} " +
                        "playing=$nativeIsPlaying durationMs=$durationMs elapsedMs=$elapsedMs"
        )
        return true
    }

    private fun hasKnownMediaFieldChanged(current: String?, incoming: String?): Boolean {
        return current != null && incoming != null && current != incoming
    }

    private fun nativeAndroidAutoMediaSignature(
            mediaInfo: NativeMediaCenterMediaInfo,
            artwork: Bitmap?
    ): String? {
        val artworkSize = artwork?.let { "${it.width}x${it.height}" }.orEmpty()
        val title = mediaInfo.title.orEmpty()
        val artist = mediaInfo.artist.orEmpty()
        val album = mediaInfo.album.orEmpty()
        if (
                title.isBlank() &&
                        artist.isBlank() &&
                        album.isBlank() &&
                        artworkSize.isBlank() &&
                        mediaInfo.durationMs <= 0L
        ) {
            return null
        }
        return "$title|$artist|$album|${mediaInfo.durationMs}|$artworkSize"
    }

    private fun logNativeAndroidAutoMediaInfo(
            mediaInfo: NativeMediaCenterMediaInfo,
            artwork: Bitmap?,
            reason: String
    ) {
        val artworkSize = artwork?.let { "${it.width}x${it.height}" } ?: "none"
        val signature =
                "${mediaInfo.mediaSource}|${mediaInfo.title}|${mediaInfo.artist}|${mediaInfo.album}|" +
                        "${mediaInfo.durationMs}|$artworkSize"
        if (signature == lastNativeAndroidAutoMediaInfoSignature) return
        lastNativeAndroidAutoMediaInfoSignature = signature
        Log.i(
                "BottomBarService",
                "Native MediaCenter Android Auto metadata $reason " +
                        "source=${mediaInfo.mediaSource} title=${mediaInfo.title ?: "-"} " +
                        "artist=${mediaInfo.artist ?: "-"} album=${mediaInfo.album ?: "-"} " +
                        "durationMs=${mediaInfo.durationMs} artwork=$artworkSize"
        )
    }

    private fun readNativeAndroidAutoMediaCenterIsPlaying(): Boolean? {
        if (!isAndroidAutoMediaTransportReadyForDashboard()) return null
        val playBinder = resolveNativeMediaCenterPlayServiceBinder() ?: return null
        val playState =
                readNativeMediaCenterPlayStateBySource(
                                playBinder,
                                NATIVE_MEDIA_CENTER_ANDROID_AUTO_SOURCE
                        )
                        ?.takeIf {
                            shouldAcceptNativeAndroidAutoPlayStateForTest(
                                    playStateMediaSource = it.mediaSource,
                                    mediaInfoSource = null
                            )
                        }
        return resolveNativeAndroidAutoPlaybackState(
                incomingIsPlaying = playState?.isPlaying,
                incomingElapsedMs = playState?.progressElapsedMs,
                reason = "native playback state read"
        )
    }

    private fun sendAndroidAutoProjectionPlaybackCommandFromIntercept(isPlaying: Boolean): Boolean {
        if (isNativeRadioProtectionActive()) {
            clearAndroidAutoMediaState("native radio active before intercepted playback")
            return false
        }
        if (DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMedia("AA_INTERCEPT_PLAYBACK")) {
            return false
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val handled = sendAndroidAutoProjectionPlaybackCommand(isPlaying)
            if (handled) {
                withContext(Dispatchers.Main) {
                    BottomBarState.mediaPackageName = ANDROID_AUTO_MEDIA_PACKAGE
                }
                Log.i(
                        "BottomBarService",
                        "Android Auto intercepted media ${if (isPlaying) "pause" else "play"} command sent"
                )
            } else {
                Log.w(
                        "BottomBarService",
                        "Android Auto intercepted media ${if (isPlaying) "pause" else "play"} command not handled"
                )
            }
        }
        return true
    }

    private fun toggleAndroidAutoMuteFromIntercept(): Boolean {
        if (isNativeRadioProtectionActive()) {
            clearAndroidAutoMediaState("native radio active before intercepted mute")
            return false
        }
        if (DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMedia("AA_INTERCEPT_MUTE")) {
            return false
        }

        val targetMuted = !BottomBarState.mediaIsMuted
        val generation = ++androidAutoMuteTargetGeneration
        lifecycleScope.launch(Dispatchers.IO) {
            val restoreVolume =
                    if (targetMuted) {
                        readCurrentMediaVolume()?.takeIf { it > 0 }?.also {
                            androidAutoMuteRestoreVolume = it
                        }
                    } else {
                        androidAutoMuteRestoreVolume ?: ANDROID_AUTO_MUTE_RESTORE_DEFAULT_VOLUME
                    }
            requestNativeAudioMuteState(targetMuted, "Android Auto native mute direct set")
            if (targetMuted) {
                requestNativeMediaVolume(0, "Android Auto mute volume floor")
            } else {
                requestNativeMediaVolume(restoreVolume ?: ANDROID_AUTO_MUTE_RESTORE_DEFAULT_VOLUME, "Android Auto unmute restore volume")
                androidAutoMuteRestoreVolume = null
            }
            delay(ANDROID_AUTO_MUTE_DIRECT_SET_VERIFY_DELAY_MS)
            val directSetMuted = readCurrentAudioMuteStateWithVolume(targetMuted)
            if (directSetMuted != targetMuted) {
                Log.w(
                        "BottomBarService",
                        "Android Auto native mute direct set did not verify target=$targetMuted " +
                                "read=$directSetMuted; falling back to native toggle"
                )
                requestNativeAudioMuteToggle("Android Auto native mute toggle fallback")
            }
            withContext(Dispatchers.Main) {
                BottomBarState.mediaIsMuted = targetMuted
            }
            Log.w(
                    "BottomBarService",
                    "Android Auto native mute requested target=$targetMuted"
            )
            launch { enforceAndroidAutoMuteTargetAfterDelay(targetMuted, generation, 3_800L) }
            launch { enforceAndroidAutoMuteTargetAfterDelay(targetMuted, generation, 7_200L) }
        }
        return true
    }

    private suspend fun enforceAndroidAutoMuteTargetAfterDelay(
            targetMuted: Boolean,
            generation: Int,
            delayMs: Long
    ) {
        delay(delayMs)
        if (generation != androidAutoMuteTargetGeneration) return
        val readMuted = readCurrentAudioMuteStateWithVolume(targetMuted)
        val currentMuted = readMuted ?: BottomBarState.mediaIsMuted
        Log.w(
                "BottomBarService",
                "Android Auto native mute target check target=$targetMuted current=$currentMuted " +
                        "read=$readMuted visual=${BottomBarState.mediaIsMuted} delayMs=$delayMs"
        )
        withContext(Dispatchers.Main) {
            BottomBarState.mediaIsMuted = currentMuted
        }
        if (currentMuted == targetMuted) return
        requestNativeAudioMuteState(targetMuted, "Android Auto mute target reassert direct set")
        if (targetMuted) {
            requestNativeMediaVolume(0, "Android Auto mute target reassert volume floor")
        } else {
            requestNativeMediaVolume(
                    androidAutoMuteRestoreVolume ?: ANDROID_AUTO_MUTE_RESTORE_DEFAULT_VOLUME,
                    "Android Auto unmute target reassert restore volume"
            )
        }
        withContext(Dispatchers.Main) {
            BottomBarState.mediaIsMuted = targetMuted
        }
        Log.w(
                "BottomBarService",
                "Android Auto native mute target reasserted target=$targetMuted delayMs=$delayMs"
        )
    }

    private fun requestNativeMediaVolume(volume: Int, reason: String) {
        val value = volume.coerceIn(0, ANDROID_AUTO_MUTE_RESTORE_MAX_VOLUME).toString()
        ServiceManager.getInstance().updateData(
                CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue(),
                value
        )
        Log.w("BottomBarService", "$reason volume=$value")
    }

    private fun requestNativeAudioMuteState(targetMuted: Boolean, reason: String) {
        val value = resolveNativeAudioMuteStateValueForTest(targetMuted)
        val serviceManager = ServiceManager.getInstance()
        serviceManager.updateData(
                CarConstants.SYS_SETTINGS_AUDIO_MEDIA_MUTE_STATE.getValue(),
                value
        )
        serviceManager.updateData(
                CarConstants.SYS_SETTINGS_AUDIO_MUTE_STATE.getValue(),
                value
        )
        Log.w("BottomBarService", "$reason target=$targetMuted value=$value")
    }

    private fun requestNativeAudioMuteToggle(reason: String) {
        ServiceManager.getInstance().updateData(
                CarConstants.SYS_SETTINGS_AUDIO_MUTE_ADJUST_ACTION.getValue(),
                NATIVE_AUDIO_MUTE_TOGGLE_ACTION
        )
        Log.w("BottomBarService", "$reason action=$NATIVE_AUDIO_MUTE_TOGGLE_ACTION")
    }

    private fun readCurrentAudioMuteStateWithVolume(targetMuted: Boolean? = null): Boolean? {
        val muted = readCurrentAudioMuteState(targetMuted)
        val volume = readCurrentMediaVolume()
        return resolveAudioMuteStateWithVolumeForTest(
                mutedState = muted,
                mediaVolume = volume,
                targetMuted = targetMuted
        )
    }

    private fun readCurrentAudioMuteState(targetMuted: Boolean? = null): Boolean? {
        val serviceManager = ServiceManager.getInstance()
        val mediaMuteKey = CarConstants.SYS_SETTINGS_AUDIO_MEDIA_MUTE_STATE.getValue()
        val mainMuteKey = CarConstants.SYS_SETTINGS_AUDIO_MUTE_STATE.getValue()
        val mediaStates =
                listOf(
                                serviceManager.getUpdatedData(mediaMuteKey),
                                serviceManager.getData(mediaMuteKey)
                        )
                        .mapNotNull(::parseAudioMuteState)
        val mainStates =
                listOf(
                                serviceManager.getUpdatedData(mainMuteKey),
                                serviceManager.getData(mainMuteKey)
                        )
                        .mapNotNull(::parseAudioMuteState)
        return resolveAudioMuteStateWithSystemMuteFallbackForTest(
                mediaStates = mediaStates,
                systemStates = mainStates,
                targetMuted = targetMuted
        )
    }

    private fun readCurrentMediaVolume(): Int? {
        val serviceManager = ServiceManager.getInstance()
        val mediaVolumeKey = CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue()
        return listOf(
                        serviceManager.getUpdatedData(mediaVolumeKey),
                        serviceManager.getData(mediaVolumeKey)
                )
                .firstNotNullOfOrNull { it?.toIntOrNull() }
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
        return shouldKeepProjectionMediaStateForTest(
                currentMediaPackageName = currentPackageName,
                candidatePackageName = candidatePackageName,
                candidateHasMetadata = candidateHasMetadata,
                androidAutoSessionReady =
                        DisplayAppLauncher.isAndroidAutoProjectionSessionReadyForMedia(
                                "AA_MEDIA_KEEP"
                        )
        )
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
                                ),
                        nativeAndroidAutoMediaCenterActive = isNativeAndroidAutoMediaCenterActive()
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

    private fun restoreBarAfterExternalFocus(packageName: String?, reason: String) {
        if (!shouldRestoreBarAfterExternalFocusForTest(
                        packageName = packageName,
                        ownPackageName = this.packageName,
                        isVisible = BottomBarState.isVisible,
                        isDashboardExpanded = BottomBarState.isDashboardExpanded,
                        dashboardControlFocusRestoreSuppressed =
                                isDashboardControlFocusRestoreSuppressed()
                )
        ) {
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            Log.w(
                    "BottomBarService",
                    "[$reason] Restoring bottom bar after external focus package=$packageName"
            )
            BottomBarState.isDashboardExpanded = false
            BottomBarState.isVisible = true
            BottomBarState.isMenuExpanded = false
            BottomBarState.isSettingsMenuExpanded = false
            BottomBarState.isOverrideMenuExpanded = false
            BottomBarState.activeSliderType = null
            composeView?.requestLayout()
            menuComposeView?.requestLayout()
        }
    }

    private fun suppressDashboardControlFocusRestore(reason: String) {
        dashboardControlFocusRestoreSuppressedUntilMs =
                SystemClock.elapsedRealtime() + DASHBOARD_CONTROL_FOCUS_SUPPRESS_MS
        Log.d(
                "BottomBarService",
                "[$reason] Suppressing dashboard focus restore for ${DASHBOARD_CONTROL_FOCUS_SUPPRESS_MS}ms"
        )
    }

    private fun isDashboardControlFocusRestoreSuppressed(): Boolean {
        return shouldSuppressDashboardFocusRestoreForControlForTest(
                nowMs = SystemClock.elapsedRealtime(),
                suppressedUntilMs = dashboardControlFocusRestoreSuppressedUntilMs,
                isDashboardExpanded = BottomBarState.isDashboardExpanded
        )
    }

    private fun restoreDashboardAfterProjectionHandoff(reason: String) {
        if (!shouldAutoOpenDashboardAfterProjectionHandoffForTest(
                        isVisible = BottomBarState.isVisible,
                        isDashboardExpanded = BottomBarState.isDashboardExpanded
                )
        ) {
            return
        }

        dashboardProjectionRestoreJob?.cancel()
        dashboardProjectionRestoreJob =
                lifecycleScope.launch(Dispatchers.Main) {
                    delay(PROJECTION_D3_DASHBOARD_RESTORE_DELAY_MS)
                    if (!shouldAutoOpenDashboardAfterProjectionHandoffForTest(
                                    isVisible = BottomBarState.isVisible,
                                    isDashboardExpanded = BottomBarState.isDashboardExpanded
                            )
                    ) {
                        return@launch
                    }

                    Log.w(
                            "BottomBarService",
                            "[$reason] Reopening dashboard after projection handoff to D3"
                    )
                    BottomBarState.isVisible = true
                    BottomBarState.isDashboardExpanded = true
                    BottomBarState.isMenuExpanded = false
                    BottomBarState.isSettingsMenuExpanded = false
                    BottomBarState.isOverrideMenuExpanded = false
                    BottomBarState.activeSliderType = null
                    launchDashboardActivity()
                }
    }

    private fun toggleImpulseDashboardFromExternal(reason: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            val targetExpanded = !BottomBarState.isDashboardExpanded
            Log.w(
                    "BottomBarService",
                    "[$reason] Toggling Impulse dashboard targetExpanded=$targetExpanded"
            )
            BottomBarState.isVisible = true
            BottomBarState.isDashboardExpanded = targetExpanded
            BottomBarState.isMenuExpanded = false
            BottomBarState.isSettingsMenuExpanded = false
            BottomBarState.isOverrideMenuExpanded = false
            BottomBarState.activeSliderType = null
            composeView?.requestLayout()
            menuComposeView?.requestLayout()
            if (targetExpanded) {
                launchDashboardActivity()
            }
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
                            } else if (BottomBarState.isDashboardExpanded) {
                                Log.d(
                                        "BottomBarService",
                                        "TouchRegion[BAR] empty while dashboard is expanded"
                                )
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
        androidAutoMonitorRefreshJob?.cancel()
        androidAutoMonitorRefreshJob = null
        nativeMediaCenterSourceMonitorJob?.cancel()
        nativeMediaCenterSourceMonitorJob = null
        dashboardProjectionRestoreJob?.cancel()
        dashboardProjectionRestoreJob = null
        abandonAndroidAutoPauseOemAudioFocus("BottomBarService destroy")
        nativeMediaCenterServiceConnection?.let { connection ->
            runCatching { applicationContext.unbindService(connection) }
        }
        nativeMediaCenterServiceConnection = null
        nativeMediaCenterServiceBinder = null
        nativeMediaCenterPlayServiceBinder = null
        nativeMediaCenterCurrentSource = null
        nativeMediaCenterCurrentAudioSource = null
        stopMediaMetadataMonitoring()
        carPlayNowPlayingMonitor?.stop()
        carPlayNowPlayingMonitor = null
        androidAutoNowPlayingMonitor?.stop()
        androidAutoNowPlayingMonitor = null
        nativeMediaStateListener?.let { ServiceManager.getInstance().removeDataChangedListener(it) }
        nativeMediaStateListener = null
        audioMuteStateListener?.let { ServiceManager.getInstance().removeDataChangedListener(it) }
        audioMuteStateListener = null
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
        private const val DEBUG_MEDIA_TAG = "BottomBarDebug"
        private const val ACTION_DEBUG_MEDIA_COMMAND =
                "br.com.redesurftank.havalshisuku.DEBUG_MEDIA_COMMAND"
        private const val EXTRA_DEBUG_MEDIA_COMMAND = "command"
        private const val EXTRA_DEBUG_MEDIA_DEV_ID = "devId"
        private const val CARPLAY_MEDIA_PACKAGE = "com.ts.carplay"
        private const val CARPLAY_MEDIA_APP_PACKAGE = "com.ts.carplay.app"
        private const val CARPLAY_MEDIA_SESSION_BLANK_DEBOUNCE_MS = 1_500L
        private const val ANDROID_AUTO_MEDIA_PACKAGE = "com.ts.androidauto"
        private const val ANDROID_AUTO_MEDIA_APP_PACKAGE = "com.ts.androidauto.app"
        private const val ANDROID_AUTO_MEDIA_SERVICE_PACKAGE = "com.ts.androidauto.projectionservice"
        private const val ANDROID_AUTO_MEDIA_SKIP_COMMAND_COOLDOWN_MS = 650L
        private const val ANDROID_AUTO_MEDIA_TOGGLE_COMMAND_COOLDOWN_MS = 2_000L
        private const val ANDROID_AUTO_PLAYBACK_VERIFY_DELAY_MS = 1_350L
        private const val ANDROID_AUTO_PAUSE_SUSTAIN_VERIFY_MS = 6_000L
        private const val ANDROID_AUTO_PAUSE_SUSTAIN_POLL_MS = 1_000L
        private const val ANDROID_AUTO_MUSIC_STATUS_PLAYING = 1
        private const val ANDROID_AUTO_MUSIC_STATUS_PAUSED = 2
        private const val ANDROID_AUTO_PROGRESS_EXPLICIT_COMMAND_RESET_WINDOW_MS = 4_000L
        private const val DASHBOARD_CONTROL_FOCUS_SUPPRESS_MS = 1_500L
        private const val PROJECTION_USB_STATE_PATH = "/sys/class/android_usb/android0/state"
        private const val CARPLAY_USB_MEDIA_STATE_POLL_MS = 1_500L
        // If CarPlay pushed a real now-playing update within this window, treat the
        // session as alive and ignore a USB "disconnect" read. The CarPlay bridge
        // keep-alives at ~2Hz, so any genuine disconnect goes stale well within this
        // window and the USB monitor still clears media as a backup.
        private const val CARPLAY_NOWPLAYING_ALIVE_WINDOW_MS = 4_000L
        private const val PROJECTION_USB_STATE_CACHE_MS = 3_000L
        private const val ANDROID_AUTO_MEDIA_SESSION_READY_CACHE_MS = 1_500L
        private const val NATIVE_AUDIO_MUTE_TOGGLE_ACTION = "2"
        private const val ANDROID_AUTO_MUTE_DIRECT_SET_VERIFY_DELAY_MS = 450L
        private const val ANDROID_AUTO_MUTE_RESTORE_DEFAULT_VOLUME = 8
        private const val ANDROID_AUTO_MUTE_RESTORE_MAX_VOLUME = 30
        private const val NATIVE_RADIO_PROTECTION_HOLD_MS = 12_000L
        private const val ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_USAGE = 12
        private const val ANDROID_AUTO_OEM_AUDIO_FOCUS_REQUEST_GRANTED = 1
        private const val ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_VERIFY_DELAY_MS = 1_200L
        private const val ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_HOLD_MAX_MS = 300_000L
        private const val ANDROID_AUTO_PAUSE_ANDROID_AUDIO_FOCUS_REFRESH_MS = 1_500L
        private const val ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_CLIENT_ID =
                "br.com.redesurftank.havalshisuku.android_auto_pause_focus"
        private const val ANDROID_AUTO_ANDROID_AUDIO_FOCUS_HOLD_MARKER =
                "android.media.AudioManager.pause_focus_hold"
        private const val ANDROID_AUTO_OEM_AUDIO_EXT_INTERFACE =
                "vendor.ts.audioext@2.0::IAudioExtService"
        private const val ANDROID_AUTO_OEM_AUDIO_EXT_SERVICE = "default"
        private const val ANDROID_AUTO_OEM_AUDIO_EXT_RESULT_OK = 0
        private const val ANDROID_AUTO_OEM_AUDIO_EXT_RESULT_DUMMY = -4
        private const val ANDROID_AUTO_OEM_AUDIO_FOCUS_TRANSACTION_REQUEST = 47
        private const val ANDROID_AUTO_OEM_AUDIO_FOCUS_TRANSACTION_ABANDON = 48
        private const val ANDROID_AUTO_OEM_AUDIO_FOCUS_INFO_BLOB_SIZE = 64
        private const val NATIVE_MEDIA_CENTER_PACKAGE = "com.beantechs.mediacenter"
        private const val NATIVE_MEDIA_CENTER_SERVICE_CLASS =
                "com.beantechs.mediacenter.mediacentermodel.MediaCenterService"
        private const val NATIVE_MEDIA_CENTER_SERVICE_DESCRIPTOR =
                "com.beantechs.mediacenter.mediacentermodel.IMediaCenterService"
        private const val NATIVE_MEDIA_CENTER_PLAY_SERVICE_DESCRIPTOR =
                "com.beantechs.mediacenter.mediacentermodel.IPlayService"
        private const val NATIVE_MEDIA_CENTER_BINDER_PLAY_SERVICE = 2
        private const val NATIVE_MEDIA_CENTER_TRANSACTION_QUERY_BINDER = 1
        private const val NATIVE_MEDIA_CENTER_TRANSACTION_GET_PLAY_STATE_BY_SOURCE = 19
        private const val NATIVE_MEDIA_CENTER_TRANSACTION_GET_PLAY_MEDIA_INFO_BY_SOURCE = 22
        private const val NATIVE_MEDIA_CENTER_TRANSACTION_GET_CURRENT_SOURCE = 25
        private const val NATIVE_MEDIA_CENTER_TRANSACTION_GET_CURRENT_AUDIO_SOURCE = 26
        private const val NATIVE_MEDIA_CENTER_TRANSACTION_PAUSE_MEDIA_BY_SOURCE = 27
        private const val NATIVE_MEDIA_CENTER_TRANSACTION_RESUME_MEDIA_BY_SOURCE = 28
        private const val NATIVE_MEDIA_CENTER_COMMAND_BIND_WAIT_MS = 180L
        private const val NATIVE_MEDIA_CENTER_SOURCE_POLL_MS = 1_500L
        private const val PROJECTION_D3_DASHBOARD_RESTORE_DELAY_MS = 650L
        private const val ANDROID_SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val NATIVE_LAUNCHER_PACKAGE = "com.beantechs.launcher"
        private const val NATIVE_VEHICLE_CENTER_PACKAGE = "com.beantechs.vehiclecenter"
        private const val NATIVE_MEDIA_CENTER_LOCAL_RADIO_SOURCE = 10
        private const val NATIVE_MEDIA_CENTER_LOCAL_RADIO_MIN_SOURCE = 10
        private const val NATIVE_MEDIA_CENTER_LOCAL_RADIO_MAX_SOURCE = 14
        private const val NATIVE_MEDIA_CENTER_ANDROID_AUTO_SOURCE = 402
        private const val NATIVE_MEDIA_CENTER_STATE_IDLE = 0
        private const val NATIVE_MEDIA_CENTER_STATE_PLAYING = 3
        private const val NATIVE_MEDIA_CENTER_STATE_PAUSED = 4
        private const val NATIVE_MEDIA_CENTER_STATE_STOPPED = 5
        private const val NATIVE_MEDIA_CENTER_STATE_COMPLETED = 6
        private const val ANDROID_AUTO_NOW_PLAYING_MONITOR_ENABLED = false
        private const val ANDROID_AUTO_MONITOR_REFRESH_INTERVAL_MS = 2_000L
        private const val ANDROID_AUTO_STALE_LOW_PROGRESS_MAX_MS = 1_000L
        private const val ANDROID_AUTO_STALE_LOW_PROGRESS_PREVIOUS_MIN_MS = 3_000L
        private const val ANDROID_AUTO_PROGRESS_REGRESSION_TOLERANCE_MS = 1_500L
        private const val NATIVE_ANDROID_AUTO_PLAYBACK_COMMAND_TARGET_HOLD_MS = 45_000L
        private const val NATIVE_ANDROID_AUTO_PLAYBACK_COMMAND_TARGET_ESCAPE_MS = 8_000L
        private const val NATIVE_ANDROID_AUTO_PLAYBACK_COMMAND_TARGET_ESCAPE_PROGRESS_MS = 3_000L
        private val PROJECTION_MEDIA_FALLBACK_PACKAGES =
                setOf(
                        "com.android.bluetooth",
                        "com.onecar.onlinemusic"
                )
        private val NATIVE_ANDROID_AUTO_MEDIA_CENTER_FALLBACK_PACKAGES =
                setOf(
                        "com.beantechs.mediacenter",
                        "com.beantechs.mediacenter.h5.core"
                )
        private val DASHBOARD_PASSIVE_EXTERNAL_FOCUS_PACKAGES =
                setOf(
                        ANDROID_SYSTEM_UI_PACKAGE,
                        NATIVE_LAUNCHER_PACKAGE,
                        NATIVE_MEDIA_CENTER_PACKAGE,
                        "com.beantechs.mediacenter.h5.core",
                        NATIVE_VEHICLE_CENTER_PACKAGE,
                        CARPLAY_MEDIA_PACKAGE,
                        CARPLAY_MEDIA_APP_PACKAGE,
                        ANDROID_AUTO_MEDIA_PACKAGE,
                        ANDROID_AUTO_MEDIA_APP_PACKAGE,
                        ANDROID_AUTO_MEDIA_SERVICE_PACKAGE
                )
        @Volatile private var instance: BottomBarService? = null

        fun requestBarRestoreAfterExternalFocus(packageName: String?, reason: String) {
            instance?.restoreBarAfterExternalFocus(packageName, reason)
        }

        fun requestDashboardRestoreAfterProjectionHandoff(reason: String) {
            instance?.restoreDashboardAfterProjectionHandoff(reason)
        }

        fun suppressDashboardControlFocusRestore(reason: String = "dashboard_control") {
            instance?.suppressDashboardControlFocusRestore(reason)
        }

        @JvmStatic
        fun requestImpulseDashboardToggleFromSteeringWheel(reason: String): Boolean {
            val service = instance
            if (service == null) {
                Log.w("BottomBarService", "[$reason] Cannot toggle dashboard: service unavailable")
                return false
            }
            service.toggleImpulseDashboardFromExternal(reason)
            return true
        }

        internal fun shouldRestoreBarAfterExternalFocusForTest(
                packageName: String?,
                ownPackageName: String,
                isVisible: Boolean,
                isDashboardExpanded: Boolean,
                dashboardControlFocusRestoreSuppressed: Boolean = false
        ): Boolean {
            val normalizedPackageName = packageName?.trim()
            if (normalizedPackageName.isNullOrBlank()) return false
            if (normalizedPackageName == ownPackageName) return false
            if (isDashboardExpanded && dashboardControlFocusRestoreSuppressed) return false
            if (isDashboardExpanded &&
                            normalizedPackageName in DASHBOARD_PASSIVE_EXTERNAL_FOCUS_PACKAGES
            ) {
                return false
            }
            return isVisible || isDashboardExpanded
        }

        internal fun shouldSuppressDashboardFocusRestoreForControlForTest(
                nowMs: Long,
                suppressedUntilMs: Long,
                isDashboardExpanded: Boolean
        ): Boolean {
            return isDashboardExpanded && nowMs <= suppressedUntilMs
        }

        internal fun shouldAutoOpenDashboardAfterProjectionHandoffForTest(
                isVisible: Boolean,
                isDashboardExpanded: Boolean
        ): Boolean {
            return isVisible || isDashboardExpanded
        }

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
                androidAutoSessionReady: Boolean,
                nativeAndroidAutoMediaCenterActive: Boolean = false
        ): Boolean {
            if (!candidateHasMetadata) return false
            if (isCarPlayMediaPackageName(currentMediaPackageName)) return false
            if (isCarPlayMediaPackageName(activeClusterProjectionPackage)) return false
            if (isProjectionMediaPackageName(candidatePackageName)) return false
            val isPhoneSessionFallback = candidatePackageName in PROJECTION_MEDIA_FALLBACK_PACKAGES
            if (isPhoneSessionFallback && !androidAutoSessionReady) return false
            val allowedFallback =
                    isPhoneSessionFallback ||
                            (nativeAndroidAutoMediaCenterActive &&
                                    candidatePackageName in
                                            NATIVE_ANDROID_AUTO_MEDIA_CENTER_FALLBACK_PACKAGES)
            if (!allowedFallback) return false
            return isAndroidAutoMediaPackageName(currentMediaPackageName) ||
                    isAndroidAutoMediaPackageName(activeClusterProjectionPackage) ||
                    nativeAndroidAutoMediaCenterActive
        }

        internal fun shouldKeepProjectionMediaStateForTest(
                currentMediaPackageName: String?,
                candidatePackageName: String,
                candidateHasMetadata: Boolean,
                androidAutoSessionReady: Boolean
        ): Boolean {
            if (!isProjectionMediaPackageName(currentMediaPackageName)) return false
            if (isProjectionMediaPackageName(candidatePackageName)) return false
            if (
                    isAndroidAutoMediaPackageName(currentMediaPackageName) &&
                            candidateHasMetadata &&
                            candidatePackageName in PROJECTION_MEDIA_FALLBACK_PACKAGES &&
                            !androidAutoSessionReady
            ) {
                return false
            }
            if (!candidateHasMetadata) return true
            return candidatePackageName in PROJECTION_MEDIA_FALLBACK_PACKAGES
        }

        internal fun shouldApplyAndroidAutoProjectionFallbackPlaybackStateForTest(
                currentMediaPackageName: String?,
                fallbackIsPlaying: Boolean
        ): Boolean {
            if (!isAndroidAutoMediaPackageName(currentMediaPackageName)) return true
            return fallbackIsPlaying
        }

        internal fun shouldApplyAndroidAutoNowPlayingClearForTest(
                projectionSessionReady: Boolean,
                clearReason: String? = null
        ): Boolean {
            if (isForcedAndroidAutoNowPlayingClearReasonForTest(clearReason)) return true
            return !projectionSessionReady
        }

        internal fun isForcedAndroidAutoNowPlayingClearReasonForTest(
                clearReason: String?
        ): Boolean {
            val normalized = clearReason?.lowercase().orEmpty()
            return normalized.contains("link inactive") ||
                    normalized.contains("binder died") ||
                    normalized.contains("service disconnected")
        }

        internal fun shouldAcceptAndroidAutoMediaCommandForTest(
                nowMs: Long,
                lastCommandAtMs: Long,
                cooldownMs: Long = ANDROID_AUTO_MEDIA_SKIP_COMMAND_COOLDOWN_MS
        ): Boolean {
            if (lastCommandAtMs <= 0L) return true
            return nowMs - lastCommandAtMs !in 0..cooldownMs
        }

        internal fun shouldUseAndroidAutoPlaybackDirectFallbackForTest(
                monitorAvailable: Boolean
        ): Boolean {
            return !monitorAvailable
        }

        internal fun shouldVerifyAndroidAutoPlaybackTargetForTest(
                targetPlaying: Boolean,
                musicStatus: Int?,
                audioPlaybackActive: Boolean,
                allowStoppedAudioAsPause: Boolean = false
        ): Boolean {
            return if (targetPlaying) {
                audioPlaybackActive || musicStatus == ANDROID_AUTO_MUSIC_STATUS_PLAYING
            } else {
                !audioPlaybackActive &&
                        (musicStatus == ANDROID_AUTO_MUSIC_STATUS_PAUSED ||
                                (allowStoppedAudioAsPause &&
                                        musicStatus != ANDROID_AUTO_MUSIC_STATUS_PLAYING))
            }
        }

        internal fun resolveAndroidAutoPlaybackCommandStateForTest(
                visualIsPlaying: Boolean,
                nativeIsPlaying: Boolean?,
                musicStatus: Int?,
                audioPlaybackActive: Boolean? = null,
                nativeMediaCenterRouteActive: Boolean = false,
                nativePlaybackGuess: Boolean = false
        ): Boolean {
            if (audioPlaybackActive == true) return true
            if (musicStatus == ANDROID_AUTO_MUSIC_STATUS_PLAYING) return true
            if (musicStatus == ANDROID_AUTO_MUSIC_STATUS_PAUSED) return false
            if (nativeIsPlaying == true) return true
            if (nativeMediaCenterRouteActive && !visualIsPlaying && audioPlaybackActive == false) {
                return nativePlaybackGuess
            }
            return visualIsPlaying
        }

        internal fun shouldUseAndroidAutoPlaybackToggleForInconclusiveStateForTest(
                nativeMediaCenterRouteActive: Boolean,
                musicStatus: Int?,
                audioPlaybackActive: Boolean?
        ): Boolean {
            return false
        }

        internal fun shouldReleaseAndroidAutoPauseOemAudioFocusForTest(
                targetPlaying: Boolean
        ): Boolean {
            return targetPlaying
        }

        internal fun shouldRequireAndroidAutoPauseSustainedVerificationForTest(
                targetPlaying: Boolean
        ): Boolean {
            return false
        }

        internal fun shouldUseAndroidAutoMediaButtonFallbackForPlaybackForTest(
                targetPlaying: Boolean
        ): Boolean {
            return true
        }

        internal fun shouldSkipAndroidAutoAapPauseFallbackAfterDirectRouteForTest(
                targetPlaying: Boolean,
                directHandled: Boolean
        ): Boolean {
            return false
        }

        internal fun shouldHoldNativeAndroidAutoPlaybackCommandTargetForTest(
                targetPlaying: Boolean?,
                targetAtMs: Long,
                targetElapsedMs: Long,
                incomingIsPlaying: Boolean?,
                incomingElapsedMs: Long?,
                nowMs: Long
        ): Boolean {
            if (targetPlaying == null || targetAtMs <= 0L) return false
            val ageMs = nowMs - targetAtMs
            if (ageMs !in 0..NATIVE_ANDROID_AUTO_PLAYBACK_COMMAND_TARGET_HOLD_MS) return false
            if (incomingIsPlaying == null) return true
            if (incomingIsPlaying == targetPlaying) return true
            val incomingProgressDeltaMs =
                    incomingElapsedMs?.let { it - targetElapsedMs } ?: 0L
            val externalPlaybackProgressed =
                    ageMs >= NATIVE_ANDROID_AUTO_PLAYBACK_COMMAND_TARGET_ESCAPE_MS &&
                            incomingProgressDeltaMs >=
                                    NATIVE_ANDROID_AUTO_PLAYBACK_COMMAND_TARGET_ESCAPE_PROGRESS_MS
            return !externalPlaybackProgressed
        }

        internal fun shouldAcceptNativeAndroidAutoPlayStateForTest(
                playStateMediaSource: Int,
                mediaInfoSource: Int?
        ): Boolean {
            return playStateMediaSource == NATIVE_MEDIA_CENTER_ANDROID_AUTO_SOURCE ||
                    playStateMediaSource == mediaInfoSource ||
                    playStateMediaSource == 0
        }

        internal fun estimateMediaElapsedForProgressForTest(
                elapsedMs: Long,
                durationMs: Long,
                progressUpdatedAtMs: Long,
                nowMs: Long,
                isPlaying: Boolean
        ): Long {
            val normalizedDuration = durationMs.coerceAtLeast(0L)
            val normalizedElapsed =
                    if (normalizedDuration > 0L) {
                        elapsedMs.coerceIn(0L, normalizedDuration)
                    } else {
                        elapsedMs.coerceAtLeast(0L)
                    }
            val elapsedDeltaMs =
                    if (isPlaying && normalizedDuration > 0L && progressUpdatedAtMs > 0L) {
                        (nowMs - progressUpdatedAtMs).coerceAtLeast(0L)
                    } else {
                        0L
                    }
            val estimatedElapsed = normalizedElapsed + elapsedDeltaMs
            return if (normalizedDuration > 0L) {
                estimatedElapsed.coerceIn(0L, normalizedDuration)
            } else {
                estimatedElapsed.coerceAtLeast(0L)
            }
        }

        internal fun resolveNativeAndroidAutoProgressElapsedForTest(
                previousElapsedMs: Long,
                durationMs: Long,
                progressUpdatedAtMs: Long,
                nowMs: Long,
                isPlaying: Boolean,
                trackChanged: Boolean,
                nativeElapsedMs: Long?
        ): Long {
            val normalizedDuration = durationMs.coerceAtLeast(0L)
            nativeElapsedMs?.let { elapsedMs ->
                val normalizedNativeElapsed =
                        if (normalizedDuration > 0L) {
                            elapsedMs.coerceIn(0L, normalizedDuration)
                        } else {
                            elapsedMs.coerceAtLeast(0L)
                        }
                if (
                        shouldPreserveAndroidAutoProgressRegressionForTest(
                                currentMediaPackageName = ANDROID_AUTO_MEDIA_PACKAGE,
                                previousElapsedMs = previousElapsedMs,
                                incomingElapsedMs = normalizedNativeElapsed,
                                previousDurationMs = normalizedDuration,
                                durationMs = normalizedDuration,
                                isPlaying = isPlaying,
                                allowProgressRegression = trackChanged
                        )
                ) {
                    return estimateMediaElapsedForProgressForTest(
                            elapsedMs = previousElapsedMs,
                            durationMs = normalizedDuration,
                            progressUpdatedAtMs = progressUpdatedAtMs,
                            nowMs = nowMs,
                            isPlaying = isPlaying
                    )
                }
                return normalizedNativeElapsed
            }
            if (trackChanged) return 0L
            return estimateMediaElapsedForProgressForTest(
                    elapsedMs = previousElapsedMs,
                    durationMs = normalizedDuration,
                    progressUpdatedAtMs = progressUpdatedAtMs,
                    nowMs = nowMs,
                    isPlaying = isPlaying
            )
        }

        internal fun shouldPreserveAndroidAutoProgressRegressionForTest(
                currentMediaPackageName: String?,
                previousElapsedMs: Long,
                incomingElapsedMs: Long,
                previousDurationMs: Long,
                durationMs: Long,
                isPlaying: Boolean,
                allowProgressRegression: Boolean
        ): Boolean {
            if (allowProgressRegression) return false
            if (!isAndroidAutoMediaPackageName(currentMediaPackageName)) return false

            val previousElapsed = previousElapsedMs.coerceAtLeast(0L)
            val incomingElapsed = incomingElapsedMs.coerceAtLeast(0L)
            if (!isPlaying && incomingElapsed > ANDROID_AUTO_STALE_LOW_PROGRESS_MAX_MS) {
                return false
            }
            if (previousElapsed <= ANDROID_AUTO_STALE_LOW_PROGRESS_PREVIOUS_MIN_MS) return false
            if (durationMs > 0L && previousElapsed >= durationMs - 2_000L) return false
            if (
                    previousDurationMs > 0L &&
                            durationMs > 0L &&
                            isMeaningfulDurationChangeForProgress(previousDurationMs, durationMs)
            ) {
                return false
            }
            if (incomingElapsed <= ANDROID_AUTO_STALE_LOW_PROGRESS_MAX_MS) return true
            return incomingElapsed + ANDROID_AUTO_PROGRESS_REGRESSION_TOLERANCE_MS <
                    previousElapsed
        }

        internal fun shouldResetAndroidAutoProgressAfterExplicitTrackCommandForTest(
                currentMediaPackageName: String?,
                nativeAndroidAutoMediaCenterActive: Boolean
        ): Boolean {
            return isAndroidAutoMediaPackageName(currentMediaPackageName) ||
                    nativeAndroidAutoMediaCenterActive
        }

        private fun isMeaningfulDurationChangeForProgress(previousDurationMs: Long, durationMs: Long): Boolean {
            val delta = durationMs - previousDurationMs
            return delta > 2_000L || delta < -2_000L
        }

        internal fun parseAudioMuteState(rawValue: String?): Boolean? {
            return when (rawValue?.trim()?.lowercase()) {
                "1", "true", "on", "muted", "mute" -> true
                "0", "false", "off", "unmuted", "unmute" -> false
                else -> null
            }
        }

        internal fun resolveAudioMuteStateForTargetForTest(
                states: List<Boolean>,
                targetMuted: Boolean?
        ): Boolean? {
            if (states.isEmpty()) return null
            return when (targetMuted) {
                true -> if (states.any { !it }) false else true
                false -> if (states.any { it }) true else false
                null -> states.first()
            }
        }

        internal fun resolveAudioMuteStateWithSystemMuteFallbackForTest(
                mediaStates: List<Boolean>,
                systemStates: List<Boolean>,
                targetMuted: Boolean?
        ): Boolean? {
            val combined = systemStates + mediaStates
            if (combined.isEmpty()) return null
            return when (targetMuted) {
                true -> combined.any { it }
                false -> if (combined.any { it }) true else false
                null -> if (combined.any { it }) true else false
            }
        }

        internal fun resolveAudioMuteStateWithVolumeForTest(
                mutedState: Boolean?,
                mediaVolume: Int?,
                targetMuted: Boolean?
        ): Boolean? {
            if (mediaVolume == 0) return true
            if (targetMuted == false && mediaVolume != null && mediaVolume > 0) return false
            return mutedState
        }

        internal fun resolveNativeAudioMuteStateValueForTest(targetMuted: Boolean): String {
            return if (targetMuted) "1" else "0"
        }

        internal fun shouldProtectNativeRadioForTest(
                key: String,
                value: String?,
                fromInitialSnapshot: Boolean
        ): Boolean {
            val normalized = value?.trim().orEmpty()
            if (normalized.isBlank()) return false

            return when (key) {
                CarConstants.SYS_RADIO_PLAY_STATE.getValue() ->
                        normalized == "1" ||
                                normalized.equals("playing", ignoreCase = true) ||
                                normalized.equals("play", ignoreCase = true)
                CarConstants.SYS_BASIC_AUDIO_SOURCE_APP.getValue() ->
                        normalized.lowercase().contains("radio")
                CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.getValue(),
                CarConstants.SYS_RADIO_RDS_CUR_CHANNEL_INFO.getValue() ->
                        !fromInitialSnapshot && hasUsableRadioChannelInfo(normalized)
                else -> false
            }
        }

        internal fun shouldProtectNativeMediaCenterSourceForTest(
                currentSource: Int?,
                currentAudioSource: Int?
        ): Boolean {
            return isNativeMediaCenterLocalRadioSource(currentSource) ||
                    isNativeMediaCenterLocalRadioSource(currentAudioSource)
        }

        internal fun shouldIgnoreNativeRadioProtectionForAndroidAutoSourceForTest(
                currentSource: Int?,
                currentAudioSource: Int?
        ): Boolean {
            return shouldUseNativeAndroidAutoMediaCenterMetadataForTest(
                    currentSource = currentSource,
                    currentAudioSource = currentAudioSource
            ) && !shouldProtectNativeMediaCenterSourceForTest(currentSource, currentAudioSource)
        }

        internal fun shouldUseNativeAndroidAutoMediaCenterMetadataForTest(
                currentSource: Int?,
                currentAudioSource: Int?
        ): Boolean {
            return isNativeMediaCenterAndroidAutoSource(currentSource) ||
                    isNativeMediaCenterAndroidAutoSource(currentAudioSource)
        }

        private fun isNativeMediaCenterLocalRadioSource(source: Int?): Boolean {
            return source != null &&
                    source in NATIVE_MEDIA_CENTER_LOCAL_RADIO_MIN_SOURCE..NATIVE_MEDIA_CENTER_LOCAL_RADIO_MAX_SOURCE
        }

        private fun isNativeMediaCenterAndroidAutoSource(source: Int?): Boolean {
            return source == NATIVE_MEDIA_CENTER_ANDROID_AUTO_SOURCE
        }

        private fun hasUsableRadioChannelInfo(value: String): Boolean {
            if (value == "{}" || value == "[]" || value == "0") return false
            val digits = Regex("""\d+""").findAll(value).mapNotNull { it.value.toLongOrNull() }.toList()
            return digits.any { it > 0L }
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

        suspend fun sendAndroidAutoProjectionMediaNext(): Boolean {
            val service = instance ?: return false
            return service.sendAndroidAutoProjectionMediaCommand(forward = true)
        }

        suspend fun sendAndroidAutoProjectionMediaPrevious(): Boolean {
            val service = instance ?: return false
            return service.sendAndroidAutoProjectionMediaCommand(forward = false)
        }

        fun sendAndroidAutoProjectionPlaybackCommandFromIntercept(isPlaying: Boolean): Boolean {
            return instance?.sendAndroidAutoProjectionPlaybackCommandFromIntercept(isPlaying) == true
        }

        suspend fun sendAndroidAutoNativeMediaCenterPlaybackTarget(
                targetPlaying: Boolean,
                reason: String
        ): Boolean {
            val service = instance
            if (service == null) {
                Log.w(
                        "BottomBarService",
                        "[$reason] Android Auto MediaCenter playback unavailable: service null"
                )
                return false
            }
            if (!service.isNativeAndroidAutoMediaCenterActive() ||
                    !service.isAndroidAutoMediaTransportReadyForDashboard()
            ) {
                Log.w(
                        "BottomBarService",
                        "[$reason] Android Auto MediaCenter playback unavailable: route inactive"
                )
                return false
            }
            return service.sendNativeMediaCenterAndroidAutoPlaybackCommandAndHoldTarget(
                    targetPlaying = targetPlaying,
                    reason = reason,
                    verifyTarget = false
            )
        }

        fun isNativeAndroidAutoMediaCenterRouteActive(): Boolean {
            return instance?.isNativeAndroidAutoMediaCenterActive() == true
        }

        fun getAndroidAutoNativeMediaCenterIsPlaying(): Boolean? {
            return instance?.readNativeAndroidAutoMediaCenterIsPlaying()
        }

        fun markAndroidAutoTrackCommandProgressReset(reason: String): Boolean {
            val service = instance ?: return false
            service.markAndroidAutoProgressRegressionAllowed(reason)
            service.resetAndroidAutoProgressAfterExplicitTrackCommand(reason)
            return true
        }

        fun toggleAndroidAutoMuteFromIntercept(): Boolean {
            return instance?.toggleAndroidAutoMuteFromIntercept() == true
        }
    }

    private suspend fun sendAndroidAutoProjectionMediaCommand(forward: Boolean): Boolean {
        val commandName = if (forward) "next" else "previous"
        if (isNativeRadioProtectionActive()) {
            clearAndroidAutoMediaState("native radio active before Android Auto $commandName")
            return false
        }
        if (DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMedia("AA_BOTTOM_BAR_$commandName")) {
            return false
        }

        if (!shouldSendAndroidAutoMediaCommand(
                        commandName,
                        ANDROID_AUTO_MEDIA_SKIP_COMMAND_COOLDOWN_MS
                )
        ) {
            return false
        }
        if (!DisplayAppLauncher.prepareAndroidAutoMediaCommandTarget("AA_BOTTOM_BAR_$commandName")) {
            return false
        }

        val preferAapRoute =
                DisplayAppLauncher.shouldPreferAndroidAutoAapMediaKeyRouteForCommand(
                        "AA_BOTTOM_BAR_$commandName"
                )
        val sent =
                if (preferAapRoute) {
                    DisplayAppLauncher.sendAndroidAutoDashboardSkipAapCommand(
                            forward,
                            alreadyPrepared = true
                    )
                } else if (forward) {
                    androidAutoNowPlayingMonitor?.next() == true ||
                            DisplayAppLauncher.sendAndroidAutoDashboardSkipAapCommand(
                                    forward,
                                    alreadyPrepared = true
                            )
                } else {
                    androidAutoNowPlayingMonitor?.previous() == true ||
                            DisplayAppLauncher.sendAndroidAutoDashboardSkipAapCommand(
                                    forward,
                                    alreadyPrepared = true
                            )
                }
        if (sent) {
            val resetReason =
                    if (preferAapRoute) {
                        "Android Auto AAP $commandName"
                    } else {
                        "Android Auto LinkCommand/AAP $commandName"
                    }
            markAndroidAutoTrackCommandProgressReset(resetReason)
        }
        return sent
    }

    private suspend fun sendAndroidAutoProjectionPlaybackCommand(isPlaying: Boolean): Boolean {
        val decision = resolveAndroidAutoPlaybackCommandDecision(isPlaying)
        val effectiveIsPlaying = decision.effectiveIsPlaying
        val commandName =
                if (effectiveIsPlaying) {
                    "pause"
                } else {
                    "play"
                }
        if (isNativeRadioProtectionActive()) {
            clearAndroidAutoMediaState("native radio active before Android Auto $commandName")
            return false
        }
        if (DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMedia("AA_BOTTOM_BAR_$commandName")) {
            return false
        }

        if (!shouldSendAndroidAutoMediaCommand(
                        commandName,
                        ANDROID_AUTO_MEDIA_TOGGLE_COMMAND_COOLDOWN_MS
                )
        ) {
            return false
        }
        val targetPlaying = !effectiveIsPlaying
        val allowStoppedAudioAsPause =
                !targetPlaying && decision.audioPlaybackActiveAtResolve
        if (isNativeAndroidAutoMediaCenterActive() && isAndroidAutoMediaTransportReadyForDashboard()) {
            val nativeMediaCenterHandled =
                    sendNativeMediaCenterAndroidAutoPlaybackCommandAndHoldTarget(
                            targetPlaying = targetPlaying,
                            reason = "AA_BOTTOM_BAR_${commandName}_MC",
                            verifyTarget = true,
                            allowStoppedAudioAsPause = allowStoppedAudioAsPause
                    )
            if (nativeMediaCenterHandled) {
                return true
            }
            Log.w(
                    "BottomBarService",
                    "Android Auto MediaCenter $commandName route did not send target=$targetPlaying; " +
                            "skipping active LinkCommand/AAP playback fallback"
            )
            return false
        }
        if (
            !DisplayAppLauncher.prepareAndroidAutoMediaCommandTarget(
                reason = "AA_BOTTOM_BAR_$commandName",
                requestVideoFocus =
                        DisplayAppLauncher
                                .shouldRequestAndroidAutoMediaCommandVideoFocusForPlaybackTarget(
                                        targetPlaying = targetPlaying
                                )
            )
        ) {
            return false
        }
        if (shouldReleaseAndroidAutoPauseOemAudioFocusForTest(targetPlaying)) {
            abandonAndroidAutoPauseOemAudioFocus("Android Auto playback $commandName")
        }
        val directHandled = DisplayAppLauncher.sendAndroidAutoDashboardPlaybackCommand(effectiveIsPlaying)
        if (directHandled &&
                verifyAndMarkAndroidAutoPlaybackTarget(
                        targetPlaying = targetPlaying,
                        reason = "Android Auto explicit $commandName",
                        allowStoppedAudioAsPause = allowStoppedAudioAsPause
                )
        ) {
            return true
        }

        Log.w(
                "BottomBarService",
                "Android Auto $commandName direct route did not verify target=$targetPlaying; " +
                        "skipping PLAY_PAUSE toggle fallback"
        )

        if (
                shouldSkipAndroidAutoAapPauseFallbackAfterDirectRouteForTest(
                        targetPlaying = targetPlaying,
                        directHandled = directHandled
                )
        ) {
            Log.w(
                    "BottomBarService",
                    "Android Auto pause direct route was sent but not verified; " +
                            "skipping AAP pause fallback to avoid USB/BT duplex media toggles"
            )
            return false
        }

        Log.w(
                "BottomBarService",
                "Android Auto $commandName trying explicit AAP target route"
        )

        val aapHandled = DisplayAppLauncher.sendAndroidAutoDashboardPlaybackAapCommand(effectiveIsPlaying)
        if (aapHandled &&
                verifyAndMarkAndroidAutoPlaybackTarget(
                        targetPlaying = targetPlaying,
                        reason = "Android Auto explicit AAP $commandName",
                        allowStoppedAudioAsPause = allowStoppedAudioAsPause
                )
        ) {
            return true
        }

        val mediaButtonHandled =
                if (shouldUseAndroidAutoMediaButtonFallbackForPlaybackForTest(targetPlaying)) {
                    sendAndroidAutoPlaybackMediaButtonFallback(
                            targetPlaying = targetPlaying,
                            reason = "Android Auto media button fallback $commandName"
                    )
                } else {
                    Log.w(
                            "BottomBarService",
                            "Skipping generic media button fallback for Android Auto pause; " +
                                    "it can target the headunit media session instead of Android Auto"
                    )
                    false
                }
        if (mediaButtonHandled &&
                verifyAndMarkAndroidAutoPlaybackTarget(
                        targetPlaying = targetPlaying,
                        reason = "Android Auto media button $commandName",
                        allowStoppedAudioAsPause = allowStoppedAudioAsPause
                )
        ) {
            return true
        }

        return false
    }

    private fun requestAndroidAutoPauseOemAudioFocus(reason: String): Boolean {
        return synchronized(androidAutoPauseOemAudioFocusLock) {
            val now = SystemClock.elapsedRealtime()
            if (androidAutoPauseOemAudioFocusRequest != null &&
                    androidAutoPauseOemAudioFocusHeldUntilMs > now
            ) {
                Log.w(
                        "BottomBarService",
                        "$reason OEM audio focus already held until=$androidAutoPauseOemAudioFocusHeldUntilMs"
                )
                return@synchronized true
            }

            val androidAudioFocusRegistration =
                    requestAndroidAutoPauseAndroidAudioFocusRegistration(reason)
            if (androidAudioFocusRegistration?.requestResult ==
                            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            ) {
                androidAutoPauseOemAudioFocusManager =
                        ANDROID_AUTO_ANDROID_AUDIO_FOCUS_HOLD_MARKER
                androidAutoPauseOemAudioFocusRequest = androidAudioFocusRegistration
                androidAutoPauseOemAudioFocusHeldUntilMs =
                        now + ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_HOLD_MAX_MS
                Log.w(
                        "BottomBarService",
                        "$reason Android audio focus hold active clientId=" +
                                androidAudioFocusRegistration.clientId
                )
                startAndroidAutoPauseAndroidAudioFocusRefresh(reason)
                return@synchronized true
            }

            val hidlHandle =
                    buildAndroidAutoPauseOemAudioFocusHandle(
                            androidAudioFocusRegistration?.clientId
                                    ?: ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_CLIENT_ID
                    )
            if (requestAndroidAutoPauseOemAudioFocusViaHidl(hidlHandle, reason, now)) {
                return@synchronized true
            }
            abandonAndroidAutoPauseAndroidAudioFocusRegistration(
                    "$reason HIDL OEM audio focus request did not hold"
            )

            runCatching {
                        val managerClass = Class.forName("ts.car.audio.AudioExtManager")
                        val requestClass = Class.forName("ts.car.audio.AudioFocusRequest")
                        val builderClass = Class.forName("ts.car.audio.AudioFocusRequest\$Builder")
                        val listenerInterface =
                                Class.forName(
                                        "ts.car.audio.AudioExtManager\$OnAudioFocusChangeListener"
                                )
                        val listener =
                                Proxy.newProxyInstance(
                                        listenerInterface.classLoader,
                                        arrayOf(listenerInterface)
                                ) { _, method, args ->
                                    if (method.name == "onAudioFocusChange") {
                                        Log.w(
                                                "BottomBarService",
                                                "Android Auto OEM audio focus listener state=" +
                                                        (args?.firstOrNull() ?: "null")
                                        )
                                    }
                                    null
                                }
                        val builder = builderClass.getConstructor().newInstance()
                        builderClass
                                .getMethod("setUsage", Int::class.javaPrimitiveType)
                                .invoke(builder, ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_USAGE)
                        builderClass
                                .getMethod("setOnAudioFocusChangeListener", listenerInterface)
                                .invoke(builder, listener)
                        val request = builderClass.getMethod("build").invoke(builder)
                        val manager =
                                managerClass
                                        .getMethod("getInstance", Context::class.java)
                                        .invoke(null, applicationContext)
                        val result =
                                managerClass
                                        .getMethod("requestAudioFocus", requestClass)
                                        .invoke(manager, request) as? Int
                        val granted = result == ANDROID_AUTO_OEM_AUDIO_FOCUS_REQUEST_GRANTED
                        Log.w(
                                "BottomBarService",
                                "$reason OEM audio focus request usage=" +
                                        "$ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_USAGE result=$result " +
                                        "granted=$granted"
                        )
                        if (granted) {
                            androidAutoPauseOemAudioFocusManager = manager
                            androidAutoPauseOemAudioFocusRequest = request
                            androidAutoPauseOemAudioFocusListener = listener
                            androidAutoPauseOemAudioFocusHeldUntilMs =
                                    now + ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_HOLD_MAX_MS
                        }
                        granted
                    }
                    .getOrElse {
                        androidAutoPauseOemAudioFocusManager = null
                        androidAutoPauseOemAudioFocusRequest = null
                        androidAutoPauseOemAudioFocusListener = null
                        androidAutoPauseOemAudioFocusHeldUntilMs = 0L
                        Log.w("BottomBarService", "$reason OEM audio focus request failed", it)
                        false
                    }
        }
    }

    private fun abandonAndroidAutoPauseOemAudioFocus(reason: String): Boolean {
        return synchronized(androidAutoPauseOemAudioFocusLock) {
            val manager = androidAutoPauseOemAudioFocusManager
            val request = androidAutoPauseOemAudioFocusRequest
            if (request is AndroidAutoPauseAndroidAudioFocusRegistration) {
                val abandoned = abandonAndroidAutoPauseAndroidAudioFocusRegistration(reason)
                androidAutoPauseOemAudioFocusManager = null
                androidAutoPauseOemAudioFocusRequest = null
                androidAutoPauseOemAudioFocusListener = null
                androidAutoPauseOemAudioFocusHeldUntilMs = 0L
                return@synchronized abandoned
            }
            if (request is AndroidAutoPauseOemAudioFocusHandle) {
                val abandoned = abandonAndroidAutoPauseOemAudioFocusViaHidl(request, reason)
                abandonAndroidAutoPauseAndroidAudioFocusRegistration(reason)
                androidAutoPauseOemAudioFocusManager = null
                androidAutoPauseOemAudioFocusRequest = null
                androidAutoPauseOemAudioFocusListener = null
                androidAutoPauseOemAudioFocusHeldUntilMs = 0L
                return@synchronized abandoned
            }
            if (manager == null || request == null) {
                androidAutoPauseOemAudioFocusHeldUntilMs = 0L
                abandonAndroidAutoPauseAndroidAudioFocusRegistration(reason)
                return@synchronized false
            }

            runCatching {
                        val managerClass = Class.forName("ts.car.audio.AudioExtManager")
                        val requestClass = Class.forName("ts.car.audio.AudioFocusRequest")
                        val result =
                                managerClass
                                        .getMethod("abandonAudioFocus", requestClass)
                                        .invoke(manager, request) as? Int
                        Log.w(
                                "BottomBarService",
                                "$reason OEM audio focus abandon result=$result"
                        )
                        result == ANDROID_AUTO_OEM_AUDIO_FOCUS_REQUEST_GRANTED
                    }
                    .getOrElse {
                        Log.w("BottomBarService", "$reason OEM audio focus abandon failed", it)
                        false
                    }
                    .also {
                        androidAutoPauseOemAudioFocusManager = null
                        androidAutoPauseOemAudioFocusRequest = null
                        androidAutoPauseOemAudioFocusListener = null
                        androidAutoPauseOemAudioFocusHeldUntilMs = 0L
                    }
        }
    }

    private fun requestAndroidAutoPauseAndroidAudioFocusRegistration(
            reason: String
    ): AndroidAutoPauseAndroidAudioFocusRegistration? {
        val manager = getSystemService(AudioManager::class.java) ?: return null
        val listener =
                AudioManager.OnAudioFocusChangeListener { focusChange ->
                    Log.w(
                            "BottomBarService",
                            "$reason Android audio focus listener change=$focusChange"
                    )
                }
        val request =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                                AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .build()
                        )
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(listener)
                        .build()
        val result = manager.requestAudioFocus(request)
        val clientId = manager.toString() + listener.toString()
        Log.w(
                "BottomBarService",
                "$reason Android audio focus registration result=$result clientId=$clientId"
        )
        androidAutoPauseOemAndroidAudioManager = manager
        androidAutoPauseOemAndroidAudioFocusRequest = request
        androidAutoPauseOemAudioFocusListener = listener
        return AndroidAutoPauseAndroidAudioFocusRegistration(
                manager = manager,
                request = request,
                listener = listener,
                clientId = clientId,
                requestResult = result
        )
    }

    private fun abandonAndroidAutoPauseAndroidAudioFocusRegistration(reason: String): Boolean {
        stopAndroidAutoPauseAndroidAudioFocusRefresh()
        val manager = androidAutoPauseOemAndroidAudioManager
        val request = androidAutoPauseOemAndroidAudioFocusRequest
        if (manager == null || request == null) {
            androidAutoPauseOemAndroidAudioManager = null
            androidAutoPauseOemAndroidAudioFocusRequest = null
            return false
        }
        val result =
                runCatching { manager.abandonAudioFocusRequest(request) }
                        .getOrElse {
                            Log.w("BottomBarService", "$reason Android audio focus abandon failed", it)
                            AudioManager.AUDIOFOCUS_REQUEST_FAILED
                        }
        Log.w("BottomBarService", "$reason Android audio focus abandon result=$result")
        androidAutoPauseOemAndroidAudioManager = null
        androidAutoPauseOemAndroidAudioFocusRequest = null
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun startAndroidAutoPauseAndroidAudioFocusRefresh(reason: String) {
        stopAndroidAutoPauseAndroidAudioFocusRefresh()
        androidAutoPauseOemAndroidAudioFocusRefreshJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    while (true) {
                        delay(ANDROID_AUTO_PAUSE_ANDROID_AUDIO_FOCUS_REFRESH_MS)
                        var shouldContinue = true
                        val result =
                                synchronized(androidAutoPauseOemAudioFocusLock) {
                                    val now = SystemClock.elapsedRealtime()
                                    val activeHold =
                                            (androidAutoPauseOemAudioFocusRequest
                                                    is AndroidAutoPauseAndroidAudioFocusRegistration) &&
                                                    androidAutoPauseOemAudioFocusHeldUntilMs > now
                                    val manager = androidAutoPauseOemAndroidAudioManager
                                    val request = androidAutoPauseOemAndroidAudioFocusRequest
                                    if (!activeHold || manager == null || request == null) {
                                        shouldContinue = false
                                        null
                                    } else {
                                        runCatching { manager.requestAudioFocus(request) }
                                                .getOrElse {
                                                    Log.w(
                                                            "BottomBarService",
                                                            "$reason Android audio focus refresh failed",
                                                            it
                                                    )
                                                    AudioManager.AUDIOFOCUS_REQUEST_FAILED
                                                }
                                    }
                                }
                        if (!shouldContinue) break
                        Log.w(
                                "BottomBarService",
                                "$reason Android audio focus refresh result=$result"
                        )
                    }
                }
    }

    private fun stopAndroidAutoPauseAndroidAudioFocusRefresh() {
        androidAutoPauseOemAndroidAudioFocusRefreshJob?.cancel()
        androidAutoPauseOemAndroidAudioFocusRefreshJob = null
    }

    private fun buildAndroidAutoPauseOemAudioFocusHandle(
            clientId: String
    ): AndroidAutoPauseOemAudioFocusHandle {
        return AndroidAutoPauseOemAudioFocusHandle(
                clientUid = applicationInfo.uid,
                usage = ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_USAGE,
                flags = 0,
                clientId = clientId,
                packageName = packageName
        )
    }

    private fun requestAndroidAutoPauseOemAudioFocusViaHidl(
            handle: AndroidAutoPauseOemAudioFocusHandle,
            reason: String,
            nowMs: Long
    ): Boolean {
        return runCatching {
                    val result =
                            transactAndroidAutoPauseOemAudioFocus(
                                    transactionCode =
                                            ANDROID_AUTO_OEM_AUDIO_FOCUS_TRANSACTION_REQUEST,
                                    handle = handle
                            )
                    val granted =
                            isAndroidAutoOemAudioExtRetvalOk(result.retval) &&
                                    result.requestResult ==
                                            ANDROID_AUTO_OEM_AUDIO_FOCUS_REQUEST_GRANTED
                    Log.w(
                            "BottomBarService",
                            "$reason HIDL OEM audio focus request usage=${handle.usage} " +
                                    "retval=${result.retval} requestResult=${result.requestResult} " +
                                    "granted=$granted clientId=${handle.clientId}"
                    )
                    if (granted) {
                        androidAutoPauseOemAudioFocusManager = ANDROID_AUTO_OEM_AUDIO_EXT_INTERFACE
                        androidAutoPauseOemAudioFocusRequest = handle
                        androidAutoPauseOemAudioFocusListener = null
                        androidAutoPauseOemAudioFocusHeldUntilMs =
                                nowMs + ANDROID_AUTO_PAUSE_OEM_AUDIO_FOCUS_HOLD_MAX_MS
                    }
                    granted
                }
                .getOrElse {
                    Log.w("BottomBarService", "$reason HIDL OEM audio focus request failed", it)
                    false
                }
    }

    private fun abandonAndroidAutoPauseOemAudioFocusViaHidl(
            handle: AndroidAutoPauseOemAudioFocusHandle,
            reason: String
    ): Boolean {
        return runCatching {
                    val result =
                            transactAndroidAutoPauseOemAudioFocus(
                                    transactionCode =
                                            ANDROID_AUTO_OEM_AUDIO_FOCUS_TRANSACTION_ABANDON,
                                    handle = handle
                            )
                    val abandoned =
                            isAndroidAutoOemAudioExtRetvalOk(result.retval) &&
                                    result.requestResult ==
                                            ANDROID_AUTO_OEM_AUDIO_FOCUS_REQUEST_GRANTED
                    Log.w(
                            "BottomBarService",
                            "$reason HIDL OEM audio focus abandon usage=${handle.usage} " +
                                    "retval=${result.retval} requestResult=${result.requestResult} " +
                                    "abandoned=$abandoned clientId=${handle.clientId}"
                    )
                    abandoned
                }
                .getOrElse {
                    Log.w("BottomBarService", "$reason HIDL OEM audio focus abandon failed", it)
                    false
                }
    }

    private fun transactAndroidAutoPauseOemAudioFocus(
            transactionCode: Int,
            handle: AndroidAutoPauseOemAudioFocusHandle
    ): AndroidAutoPauseOemAudioFocusHidlResult {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/os/")
        val hwBinderClass = Class.forName("android.os.HwBinder")
        val hwParcelClass = Class.forName("android.os.HwParcel")
        val hwBlobClass = Class.forName("android.os.HwBlob")
        val iHwBinderClass = Class.forName("android.os.IHwBinder")
        val remote =
                runCatching {
                            invokeHidden(
                                    hwBinderClass,
                                    null,
                                    "getService",
                                    ANDROID_AUTO_OEM_AUDIO_EXT_INTERFACE,
                                    ANDROID_AUTO_OEM_AUDIO_EXT_SERVICE,
                                    true
                            )
                        }
                        .getOrElse {
                            invokeHidden(
                                    hwBinderClass,
                                    null,
                                    "getService",
                                    ANDROID_AUTO_OEM_AUDIO_EXT_INTERFACE,
                                    ANDROID_AUTO_OEM_AUDIO_EXT_SERVICE
                            )
                        }
                        ?: error("IAudioExtService HIDL remote is null")
        val requestParcel = newHiddenInstance(hwParcelClass)
        val replyParcel = newHiddenInstance(hwParcelClass)
        try {
            invokeHidden(
                    hwParcelClass,
                    requestParcel,
                    "writeInterfaceToken",
                    ANDROID_AUTO_OEM_AUDIO_EXT_INTERFACE
            )
            writeAndroidAutoPauseOemAudioFocusInfoToParcel(
                    parcel = requestParcel,
                    hwParcelClass = hwParcelClass,
                    hwBlobClass = hwBlobClass,
                    handle = handle
            )
            if (transactionCode == ANDROID_AUTO_OEM_AUDIO_FOCUS_TRANSACTION_REQUEST) {
                invokeHidden(hwParcelClass, requestParcel, "writeStrongBinder", null)
            }
            invokeHidden(iHwBinderClass, remote, "transact", transactionCode, requestParcel, replyParcel, 0)
            invokeHidden(hwParcelClass, replyParcel, "verifySuccess")
            invokeHidden(hwParcelClass, requestParcel, "releaseTemporaryStorage")
            val retval = (invokeHidden(hwParcelClass, replyParcel, "readInt32") as Number).toInt()
            val requestResult =
                    (invokeHidden(hwParcelClass, replyParcel, "readInt32") as Number).toInt()
            return AndroidAutoPauseOemAudioFocusHidlResult(retval, requestResult)
        } finally {
            runCatching { invokeHidden(hwParcelClass, replyParcel, "release") }
            runCatching { invokeHidden(hwParcelClass, requestParcel, "release") }
        }
    }

    private fun writeAndroidAutoPauseOemAudioFocusInfoToParcel(
            parcel: Any,
            hwParcelClass: Class<*>,
            hwBlobClass: Class<*>,
            handle: AndroidAutoPauseOemAudioFocusHandle
    ) {
        val blob = newHiddenInstance(hwBlobClass, ANDROID_AUTO_OEM_AUDIO_FOCUS_INFO_BLOB_SIZE)
        invokeHidden(hwBlobClass, blob, "putInt32", 0L, handle.clientUid)
        invokeHidden(hwBlobClass, blob, "putInt32", 4L, handle.usage)
        invokeHidden(hwBlobClass, blob, "putInt32", 8L, handle.flags)
        invokeHidden(hwBlobClass, blob, "putString", 16L, handle.clientId)
        invokeHidden(hwBlobClass, blob, "putString", 32L, handle.packageName)
        invokeHidden(hwBlobClass, blob, "putInt32", 48L, -1)
        invokeHidden(hwBlobClass, blob, "putInt32", 52L, -1)
        invokeHidden(hwBlobClass, blob, "putInt32", 56L, -1)
        invokeHidden(hwParcelClass, parcel, "writeBuffer", blob)
    }

    private fun newHiddenInstance(clazz: Class<*>, vararg args: Any?): Any {
        return HiddenApiBypass.newInstance(clazz, *args)
    }

    private fun invokeHidden(clazz: Class<*>, target: Any?, methodName: String, vararg args: Any?): Any? {
        return HiddenApiBypass.invoke(clazz, target, methodName, *args)
    }

    private fun isAndroidAutoOemAudioExtRetvalOk(retval: Int): Boolean {
        return retval == ANDROID_AUTO_OEM_AUDIO_EXT_RESULT_OK ||
                retval == ANDROID_AUTO_OEM_AUDIO_EXT_RESULT_DUMMY
    }

    private fun resolveAndroidAutoPlaybackCommandDecision(
            currentIsPlaying: Boolean
    ): AndroidAutoPlaybackCommandDecision {
        val nativeIsPlaying = readNativeAndroidAutoMediaCenterIsPlaying()
        val musicStatus = androidAutoNowPlayingMonitor?.readMusicStatusForCommand()
        val audioPlaybackActive =
                DisplayAppLauncher.hasActiveAndroidAutoAudioPlaybackForMedia(
                        "AA_PLAYBACK_RESOLVE",
                        forceRefresh = true
                )
        val nativeMediaCenterRouteActive = isNativeAndroidAutoMediaCenterActive()
        val resolved =
                resolveAndroidAutoPlaybackCommandStateForTest(
                        visualIsPlaying = currentIsPlaying,
                        nativeIsPlaying = nativeIsPlaying,
                        musicStatus = musicStatus,
                        audioPlaybackActive = audioPlaybackActive,
                        nativeMediaCenterRouteActive = nativeMediaCenterRouteActive,
                        nativePlaybackGuess = nativeAndroidAutoMediaCenterIsPlayingGuess
                )
        Log.w(
                "BottomBarService",
                "Android Auto playback command state resolved visual=$currentIsPlaying " +
                        "native=$nativeIsPlaying status=$musicStatus audioActive=$audioPlaybackActive " +
                        "nativeRoute=$nativeMediaCenterRouteActive effective=$resolved"
        )
        return AndroidAutoPlaybackCommandDecision(
                effectiveIsPlaying = resolved,
                audioPlaybackActiveAtResolve = audioPlaybackActive
        )
    }

    private fun sendAndroidAutoPlaybackMediaButtonFallback(
            targetPlaying: Boolean,
            reason: String
    ): Boolean {
        val keyCode =
                if (targetPlaying) {
                    KeyEvent.KEYCODE_MEDIA_PLAY
                } else {
                    KeyEvent.KEYCODE_MEDIA_PAUSE
                }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            Log.w("BottomBarService", "$reason unavailable: AudioManager is null")
            return false
        }
        return runCatching {
                    val downTime = SystemClock.uptimeMillis()
                    audioManager.dispatchMediaKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
                    audioManager.dispatchMediaKeyEvent(
                            KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0)
                    )
                    Log.w(
                            "BottomBarService",
                            "$reason dispatched keyCode=$keyCode targetPlaying=$targetPlaying"
                    )
                    true
                }
                .getOrElse {
                    Log.w("BottomBarService", "$reason failed", it)
                    false
                }
    }

    private suspend fun verifyAndMarkAndroidAutoPlaybackTarget(
            targetPlaying: Boolean,
            reason: String,
            allowStoppedAudioAsPause: Boolean
    ): Boolean {
        val verified =
                verifyAndroidAutoPlaybackTarget(
                        targetPlaying = targetPlaying,
                        reason = reason,
                        allowStoppedAudioAsPause = allowStoppedAudioAsPause
                )
        if (!verified) return false

        if (shouldRequireAndroidAutoPauseSustainedVerificationForTest(targetPlaying) &&
                !verifyAndroidAutoPauseTargetSustained(
                        reason = reason,
                        allowStoppedAudioAsPause = allowStoppedAudioAsPause
                )
        ) {
            Log.w(
                    "BottomBarService",
                    "$reason rejected because Android Auto pause did not remain stable"
            )
            return false
        }

        markNativeAndroidAutoPlaybackCommandTarget(targetPlaying)
        return true
    }

    private suspend fun verifyAndroidAutoPlaybackTarget(
            targetPlaying: Boolean,
            reason: String,
            allowStoppedAudioAsPause: Boolean = false
    ): Boolean {
        delay(ANDROID_AUTO_PLAYBACK_VERIFY_DELAY_MS)
        return readAndroidAutoPlaybackTargetVerified(
                targetPlaying = targetPlaying,
                reason = reason,
                allowStoppedAudioAsPause = allowStoppedAudioAsPause
        )
    }

    private suspend fun verifyAndroidAutoPauseTargetSustained(
            reason: String,
            allowStoppedAudioAsPause: Boolean
    ): Boolean {
        val deadlineMs = SystemClock.elapsedRealtime() + ANDROID_AUTO_PAUSE_SUSTAIN_VERIFY_MS
        var sample = 1
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            val waitMs =
                    minOf(
                            ANDROID_AUTO_PAUSE_SUSTAIN_POLL_MS,
                            deadlineMs - SystemClock.elapsedRealtime()
                    )
            if (waitMs > 0L) {
                delay(waitMs)
            }
            val verified =
                    readAndroidAutoPlaybackTargetVerified(
                            targetPlaying = false,
                            reason = "$reason sustained#$sample",
                            allowStoppedAudioAsPause = allowStoppedAudioAsPause
                    )
            if (!verified) return false
            sample += 1
        }
        return true
    }

    private fun readAndroidAutoPlaybackTargetVerified(
            targetPlaying: Boolean,
            reason: String,
            allowStoppedAudioAsPause: Boolean = false
    ): Boolean {
        val musicStatus = androidAutoNowPlayingMonitor?.readMusicStatusForCommand()
        val progressSeconds = androidAutoNowPlayingMonitor?.readMediaProgressForCommand()
        val audioPlaybackActive =
                DisplayAppLauncher.hasActiveAndroidAutoAudioPlaybackForMedia(
                        "${reason}_VERIFY",
                        forceRefresh = true
                )
        val verified =
                shouldVerifyAndroidAutoPlaybackTargetForTest(
                        targetPlaying = targetPlaying,
                        musicStatus = musicStatus,
                        audioPlaybackActive = audioPlaybackActive,
                        allowStoppedAudioAsPause = allowStoppedAudioAsPause
                )
        Log.w(
                "BottomBarService",
                "$reason verify targetPlaying=$targetPlaying status=$musicStatus " +
                        "progress=$progressSeconds audioActive=$audioPlaybackActive " +
                        "allowStoppedAudioAsPause=$allowStoppedAudioAsPause verified=$verified"
        )
        return verified
    }

    private suspend fun sendNativeMediaCenterAndroidAutoPlaybackCommand(
            targetPlaying: Boolean,
            reason: String
    ): Boolean {
        val transactionCode =
                if (targetPlaying) {
                    NATIVE_MEDIA_CENTER_TRANSACTION_RESUME_MEDIA_BY_SOURCE
                } else {
                    NATIVE_MEDIA_CENTER_TRANSACTION_PAUSE_MEDIA_BY_SOURCE
                }
        return sendNativeMediaCenterMediaCommandBySource(
                transactionCode = transactionCode,
                source = NATIVE_MEDIA_CENTER_ANDROID_AUTO_SOURCE,
                reason = reason
        )
    }

    private suspend fun sendNativeMediaCenterAndroidAutoPlaybackCommandAndHoldTarget(
            targetPlaying: Boolean,
            reason: String,
            verifyTarget: Boolean,
            allowStoppedAudioAsPause: Boolean = false
    ): Boolean {
        val sent =
                sendNativeMediaCenterAndroidAutoPlaybackCommand(
                        targetPlaying = targetPlaying,
                        reason = reason
                )
        if (!sent) return false

        markNativeAndroidAutoPlaybackCommandTarget(targetPlaying)
        if (verifyTarget) {
            val verified =
                    verifyAndroidAutoPlaybackTarget(
                            targetPlaying = targetPlaying,
                            reason = "$reason verify",
                            allowStoppedAudioAsPause = allowStoppedAudioAsPause
                    )
            Log.w(
                    "BottomBarService",
                    "$reason native MediaCenter target=$targetPlaying sent=true verified=$verified"
            )
        }
        return true
    }

    private suspend fun sendNativeMediaCenterMediaCommandBySource(
            transactionCode: Int,
            source: Int,
            reason: String
    ): Boolean {
        if (nativeMediaCenterServiceBinder?.isBinderAlive != true) {
            bindNativeMediaCenterService()
            delay(NATIVE_MEDIA_CENTER_COMMAND_BIND_WAIT_MS)
        }
        var playBinder = resolveNativeMediaCenterPlayServiceBinder()
        if (playBinder == null) {
            bindNativeMediaCenterService()
            delay(NATIVE_MEDIA_CENTER_COMMAND_BIND_WAIT_MS)
            playBinder = resolveNativeMediaCenterPlayServiceBinder()
        }
        if (playBinder == null) {
            Log.w("BottomBarService", "$reason native MediaCenter play binder unavailable")
            return false
        }
        if (transactNativeMediaCenterMediaCommand(playBinder, transactionCode, source, reason)) {
            return true
        }

        bindNativeMediaCenterService()
        delay(NATIVE_MEDIA_CENTER_COMMAND_BIND_WAIT_MS)
        val retryBinder = resolveNativeMediaCenterPlayServiceBinder()
        if (retryBinder == null) {
            Log.w("BottomBarService", "$reason native MediaCenter play binder unavailable after retry")
            return false
        }
        return transactNativeMediaCenterMediaCommand(retryBinder, transactionCode, source, reason)
    }

    private fun transactNativeMediaCenterMediaCommand(
            playBinder: IBinder,
            transactionCode: Int,
            source: Int,
            reason: String
    ): Boolean {
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(NATIVE_MEDIA_CENTER_PLAY_SERVICE_DESCRIPTOR)
            data.writeInt(source)
            val sent = playBinder.transact(transactionCode, data, null, IBinder.FLAG_ONEWAY)
            Log.w(
                    "BottomBarService",
                    "$reason native MediaCenter command transaction=$transactionCode " +
                            "source=$source sent=$sent"
            )
            sent
        } catch (e: Exception) {
            resetNativeMediaCenterBindersForRebind("$reason native MediaCenter command failed")
            Log.w("BottomBarService", "$reason native MediaCenter command failed", e)
            false
        } finally {
            data.recycle()
        }
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
