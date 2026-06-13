package br.com.redesurftank.havalshisuku.managers

import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.DisplayAppConfig
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.view.KeyEvent
import br.com.redesurftank.havalshisuku.BuildConfig
import br.com.redesurftank.havalshisuku.managers.ThemeManager
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.services.BottomBarService
import java.util.Locale
import java.util.regex.Pattern

data class ResolvedAppInfo(
    val label: String,
    val icon: android.graphics.drawable.Drawable?
)

object DisplayAppLauncher {

    /**
     * Attempts to launch Android Auto using common system package names.
     */
    fun launchAndroidAuto(context: Context) {
        val packages = listOf(
            "com.google.android.projection.gearhead",
            "com.google.android.apps.auto"
        )
        for (pkg in packages) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        }
        Log.e("DisplayAppLauncher", "Android Auto package not found")
    }

    /**
     * Attempts to launch CarPlay (or the car interface app) using common Haval/system package names.
     */
    fun launchCarPlay(context: Context) {
        val packages = listOf(
            "com.beantechs.carlink", // Common for Haval
            "com.zjinnova.zlink",
            "com.apple.ottocast"
        )
        for (pkg in packages) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        }
        Log.e("DisplayAppLauncher", "CarPlay/CarLink package not found")
    }

    /**
     * Pre-defined apps that are commonly used on Haval TS multimedia systems but might not be in the launcher.
     */
    val PREDEFINED_APPS = listOf(
        DisplayAppConfig(
            packageName = "com.ts.androidauto.app",
            activityName = "com.ts.androidauto.app.display.AapActivity",
            displayId = 3, // Default to Cluster
            x = 0,
            y = 0,
            width = 1920,
            height = 720,
            customName = "Android Auto"
        ),
        DisplayAppConfig(
            packageName = "com.ts.carplay.app",
            activityName = "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity",
            displayId = 3, // Default to Cluster
            x = 0,
            y = 0,
            width = 1920,
            height = 720,
            customName = "Apple CarPlay"
        ),
        DisplayAppConfig(
            packageName = "com.android.settings",
            activityName = "com.android.settings.Settings",
            displayId = 0,
            x = 0,
            y = 0,
            width = 1920,
            height = 720,
            substituteIcon = "settings",
            customName = "Configurações"
        )
    )

    private const val TAG = "DisplayAppLauncher"
    private const val ANDROID_AUTO_PACKAGE = "com.ts.androidauto.app"
    private const val ANDROID_AUTO_SERVICE_PACKAGE = "com.ts.androidauto.projectionservice"
    private const val ANDROID_AUTO_ACTIVITY = "com.ts.androidauto.app.display.AapActivity"
    private const val ANDROID_AUTO_SERVICE = "com.ts.androidauto.projectionservice/.AndroidAutoService"
    private const val ANDROID_AUTO_REMOTE_SERVICE = "com.ts.androidauto.app/.AndroidAutoRemoteUiService"
    private const val ANDROID_AUTO_LINK_SERVICE_ACTION = "com.ts.androidauto.action.AndroidAutoService"
    private const val ANDROID_AUTO_LINK_SERVICE_CLASS = "com.ts.androidauto.projectionservice.AndroidAutoService"
    private const val ANDROID_AUTO_LINK_COMMAND_INTERFACE = "com.ts.androidauto.sdk.aidl.LinkCommand"
    private const val ANDROID_AUTO_LINK_COMMAND_SEND_KEY_EVENT_TRANSACTION = 0x0a
    private const val ANDROID_AUTO_LINK_COMMAND_GET_LINK_STATUS_TRANSACTION = 0x15
    private const val ANDROID_AUTO_LINK_COMMAND_NEXT_TRANSACTION = 0x18
    private const val ANDROID_AUTO_LINK_COMMAND_PREVIOUS_TRANSACTION = 0x19
    private const val ANDROID_AUTO_LINK_COMMAND_GET_MEDIA_PROGRESS_TRANSACTION = 0x1a
    private const val ANDROID_AUTO_LINK_COMMAND_PLAY_TRANSACTION = 0x1c
    private const val ANDROID_AUTO_LINK_COMMAND_PAUSE_TRANSACTION = 0x1d
    private const val ANDROID_AUTO_LINK_COMMAND_GET_MUSIC_STATUS_TRANSACTION = 0x1b
    private const val ANDROID_AUTO_LINK_COMMAND_SUBSCRIBE_HMI_KEYS_TRANSACTION = 0x31
    private const val ANDROID_AUTO_START_FLAGS = "0x18000000"
    private const val PREF_DESIRED_ANDROID_AUTO_DISPLAY_ID = "desiredAndroidAutoDisplayId"
    private const val ANDROID_AUTO_CLUSTER_GUARD_COOLDOWN_MS = 2_500L
    private const val ANDROID_AUTO_WINDOW_FOCUS_GUARD_COOLDOWN_MS = 4_000L
    private const val ANDROID_AUTO_MEDIA_KEY_COOLDOWN_MS = 650L
    private const val ANDROID_AUTO_TOGGLE_MEDIA_KEY_COOLDOWN_MS = 2_000L
    private const val ANDROID_AUTO_POST_NATIVE_PANEL_FOCUS_COOLDOWN_MS = 1_500L
    private const val ANDROID_AUTO_NATIVE_PANEL_ACTIVE_FOCUS_COOLDOWN_MS = 1_500L
    private const val ANDROID_AUTO_NATIVE_MEDIA_KEY_UP_DELAY_MS = 70L
    private const val ANDROID_AUTO_NATIVE_MEDIA_KEY_BIND_WAIT_MS = 180L
    private const val ANDROID_AUTO_NATIVE_PLAYBACK_SETTLE_MS = 520L
    private const val ANDROID_AUTO_STEERING_MEDIA_FOCUS_KEEPALIVE_START_DELAY_MS = 3_000L
    private const val ANDROID_AUTO_STEERING_MEDIA_FOCUS_KEEPALIVE_INTERVAL_MS = 3_000L
    private const val ANDROID_AUTO_USB_DISCONNECT_CLEANUP_COOLDOWN_MS = 10_000L
    private const val ANDROID_AUTO_OEM_INPUT_ECHO_BLOCK_MS = 2_500L
    private const val ANDROID_AUTO_CLUSTER_MEDIA_COMMAND_PREVIOUS = 1
    private const val ANDROID_AUTO_CLUSTER_MEDIA_COMMAND_NEXT = 2
    private const val ANDROID_AUTO_AAP_HARDKEY_MEDIA_PLAY_PAUSE = 6
    private const val ANDROID_AUTO_AAP_HARDKEY_MEDIA_PLAY = 7
    private const val ANDROID_AUTO_AAP_HARDKEY_MEDIA_PAUSE = 8
    private const val ANDROID_AUTO_AAP_HARDKEY_MEDIA_PREVIOUS = 9
    private const val ANDROID_AUTO_AAP_HARDKEY_MEDIA_NEXT = 10
    private const val ANDROID_AUTO_OEM_INPUT_MEDIA_PREVIOUS = 0x3ea
    private const val ANDROID_AUTO_OEM_INPUT_MEDIA_NEXT = 0x3eb
    private const val ANDROID_AUTO_OEM_INPUT_MEDIA_PLAY_PAUSE = 0x3ec
    private const val ANDROID_AUTO_OEM_INPUT_MEDIA_FALLBACK_ENABLED = false
    private const val ANDROID_AUTO_PREVIOUS_NEXT_OEM_ONLY_ROUTE_ENABLED = true
    private const val ANDROID_AUTO_MUSIC_STATUS_NOT_START = 0
    private const val ANDROID_AUTO_MUSIC_STATUS_PLAYING = 1
    private const val ANDROID_AUTO_MUSIC_STATUS_PAUSED = 2
    private enum class AndroidAutoMediaKeySource {
        STEERING_INPUT,
        CLUSTER_CALLBACK
    }
    private const val CARPLAY_PACKAGE = "com.ts.carplay.app"
    private const val CARPLAY_ACTIVITY = "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"
    private const val CARPLAY_HOST_PROCESS = "com.ts.carplay"
    private const val CARPLAY_HOST_SERVICE = "com.ts.carplay/.CarPlayService"
    private const val CARPLAY_HOST_SERVICE_CLASS = "com.ts.carplay.CarPlayService"
    private const val CARPLAY_SERVICE_INTERFACE = "com.ts.carplay.common.aidl.ICarPlayService"
    private const val CARPLAY_SERVICE_REQUEST_UI_TRANSACTION = 20
    private const val CARPLAY_SERVICE_GET_LINK_STATUS_TRANSACTION = 29
    private const val CARPLAY_LINK_STATUS_ACTIVATED = 2
    private const val CARPLAY_LAUNCH_MODE_NORMAL = 0
    private const val CARPLAY_REMOTE_SERVICE = "com.ts.carplay.app/.service.CarPlayRemoteService"
    private const val CARPLAY_START_FLAGS = "0x18000000"
    private const val PREF_DESIRED_CARPLAY_DISPLAY_ID = "desiredCarPlayDisplayId"
    private const val PREF_CARPLAY_BOOT_AUTOSTART_BOOT_TOKEN = "carPlayBootAutostartBootToken"
    private const val CARPLAY_REFRESH_RENDER_ACTION = "br.com.redesurftank.havalshisuku.carplay.REFRESH_RENDER"
    private const val CARPLAY_HEALTH_TRANSITION_GRACE_SEC = 1.2
    private const val CARPLAY_HEALTH_RECENT_WINDOW_SEC = 2.2
    private const val CARPLAY_HEALTH_CODEC_NOISE_THRESHOLD = 6
    private const val CARPLAY_HEALTH_SURFACE_NOISE_THRESHOLD = 4
    private const val CARPLAY_CLUSTER_GUARD_COOLDOWN_MS = 3_500L
    private const val CARPLAY_WINDOW_FOCUS_GUARD_COOLDOWN_MS = 8_000L
    private const val CARPLAY_RESTORE_PROBE_INTERVAL_MS = 300L
    private const val CARPLAY_RESTORE_REQUIRED_DISPLAY0_MS = 800L
    private const val CARPLAY_RESTORE_MAX_WAIT_MS = 3_000L
    private const val CARPLAY_CLUSTER_WATCHDOG_START_DELAY_MS = 4_000L
    private const val CARPLAY_CLUSTER_WATCHDOG_INTERVAL_MS = 1_000L
    private const val CARPLAY_BOOT_AUTOSTART_ATTEMPTS = 30
    private const val CARPLAY_BOOT_AUTOSTART_INTERVAL_MS = 2_000L
    private const val CARPLAY_CLUSTER_TARGET_BOOT_GRACE_MS = 65_000L
    private const val CARPLAY_MAIN_DUPLICATE_CLEANUP_COOLDOWN_MS = 3_500L
    private const val CARPLAY_SURFACE_PROBE_COOLDOWN_MS = 1_200L
    private const val CARPLAY_SURFACE_REASSERT_COOLDOWN_MS = 3_500L
    private const val CARPLAY_WATCHDOG_RESTORE_COOLDOWN_MS = 3_500L
    private const val CARPLAY_MISSING_VISUAL_RESTORE_WINDOW_MS = 60_000L
    private const val CARPLAY_RECONNECT_D0_OBSERVATION_WINDOW_MS = 30_000L
    private const val CARPLAY_VIDEO_FOCUS_PULSE_COOLDOWN_MS = 4_500L
    private const val CARPLAY_VIDEO_FOCUS_AFTER_D3_HANDOFF_GRACE_MS = 2_500L
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val CARPLAY_SYSTEM_UI_ICON_WATCHDOG_START_DELAY_MS = 12_000L
    private const val CARPLAY_SYSTEM_UI_ICON_WATCHDOG_INTERVAL_MS = 10_000L
    private const val CARPLAY_SYSTEM_UI_ICON_RECOVERY_COOLDOWN_MS = 120_000L
    private const val CARPLAY_SYSTEM_UI_ICON_DISCONNECT_REFRESH_COOLDOWN_MS = 30_000L
    private const val CARPLAY_SYSTEM_UI_ICON_RECENT_RELEVANT_WINDOW_MS = 5 * 60_000L
    private const val CARPLAY_SYSTEM_UI_ICON_MISSING_BIND_SAMPLES = 3
    private const val CARPLAY_SYSTEM_UI_ICON_STATIONARY_SPEED_KMH = 0.5

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastCarPlayClusterGuardAt = 0L
    @Volatile private var lastCarPlayWindowFocusGuardAt = 0L
    @Volatile private var carPlayClusterWatchdogStarted = false
    @Volatile private var carPlaySystemUiIconWatchdogStarted = false
    @Volatile private var lastCarPlaySystemUiIconRecoveryAt = 0L
    @Volatile private var lastCarPlaySystemUiIconDisconnectRefreshAt = 0L
    @Volatile private var lastCarPlaySystemUiIconRelevantAt = 0L
    @Volatile private var lastCarPlaySystemUiIconUsbConfiguredState: Boolean? = null
    @Volatile private var carPlaySystemUiMissingBindCount = 0
    @Volatile private var lastCarPlayMainDuplicateCleanupAt = 0L
    @Volatile private var lastCarPlaySurfaceProbeAt = 0L
    @Volatile private var lastCarPlaySurfaceReassertAt = 0L
    @Volatile private var lastCarPlayWatchdogRestoreAt = 0L
    @Volatile private var lastCarPlayClusterVisualSeenAt = 0L
    @Volatile private var carPlayClusterTargetBootGraceUntil = 0L
    @Volatile private var lastProjectionUsbConfiguredAt = 0L
    @Volatile private var lastProjectionUsbDisconnectedAt = 0L
    @Volatile private var lastProjectionUsbConfiguredState: Boolean? = null
    @Volatile private var carPlayMainDisplayReconnectSeenAt = 0L
    @Volatile private var lastCarPlayVideoFocusPulseAt = 0L
    @Volatile private var lastCarPlayClusterHandoffAt = 0L
    @Volatile private var lastAndroidAutoClusterGuardAt = 0L
    @Volatile private var lastAndroidAutoWindowFocusGuardAt = 0L
    @Volatile private var lastAndroidAutoMediaKeyAt = 0L
    @Volatile private var lastAndroidAutoMediaKeyCode = 0
    @Volatile private var lastAndroidAutoPostNativePanelFocusAt = 0L
    @Volatile private var lastAndroidAutoNativePanelActiveFocusAt = 0L
    @Volatile private var lastAndroidAutoUsbDisconnectCleanupAt = 0L
    @Volatile private var androidAutoSteeringMediaFocusKeepAliveStarted = false
    @Volatile private var androidAutoOemInputEchoKeyCode = 0
    @Volatile private var androidAutoOemInputEchoBlockUntil = 0L
    @Volatile private var lastAndroidAutoAccessibilityToggleKeyCode = 0
    @Volatile private var lastAndroidAutoAccessibilityToggleKeyAt = 0L
    @Volatile private var blockedAndroidAutoAccessibilityToggleKeyCode = 0
    private val androidAutoLinkCommandLock = Any()
    @Volatile private var androidAutoLinkCommandBinder: IBinder? = null
    @Volatile private var androidAutoLinkCommandBindingStarted = false
    private val carPlayServiceLock = Any()
    @Volatile private var carPlayServiceBinder: IBinder? = null
    @Volatile private var carPlayServiceBindingStarted = false

    private val androidAutoLinkCommandConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            synchronized(androidAutoLinkCommandLock) {
                androidAutoLinkCommandBinder = service
                androidAutoLinkCommandBindingStarted = true
            }
            Log.w(TAG, "[ANDROID_AUTO_LINK_COMMAND_BIND] Connected to $name")
            scope.launch {
                subscribeAndroidAutoHmiKeys("ANDROID_AUTO_LINK_COMMAND_BIND")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(androidAutoLinkCommandLock) {
                androidAutoLinkCommandBinder = null
                androidAutoLinkCommandBindingStarted = false
            }
            Log.w(TAG, "[ANDROID_AUTO_LINK_COMMAND_BIND] Disconnected from $name")
        }
    }

    private val carPlayServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            synchronized(carPlayServiceLock) {
                carPlayServiceBinder = service
                carPlayServiceBindingStarted = true
            }
            Log.w(TAG, "[CARPLAY_SERVICE_BIND] Connected to $name")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(carPlayServiceLock) {
                carPlayServiceBinder = null
                carPlayServiceBindingStarted = false
            }
            Log.w(TAG, "[CARPLAY_SERVICE_BIND] Disconnected from $name")
        }
    }

    // Memory cache for app bounds per display: packageName -> Map<displayId, bounds>
    private val lastKnownDisplayBounds = mutableMapOf<String, MutableMap<Int, IntArray>>()

    private data class CarPlayHealth(
        val hasIssue: Boolean,
        val hasCodecIssue: Boolean,
        val hasNullSurface: Boolean,
        val sessionDisconnected: Boolean,
        val evidence: String
    )

    private enum class ExistingClusterCarPlayAction {
        FULL_REFRESH,
        VIDEO_FOCUS_ONLY,
        EXISTING_CLUSTER_VIDEO_FOCUS_ONLY,
        VERIFY_ONLY,
        SURFACE_REASSERT_IF_STALE
    }

    private enum class ExistingClusterAndroidAutoAction {
        FULLSCREEN_AND_FOCUS,
        VERIFY_ONLY
    }

    private enum class CarPlayRestorePostStartMode {
        FULL_RENDER_FOCUS,
        FULLSCREEN_ONLY
    }

    private enum class CarPlaySystemUiServiceConnectionState {
        HEALTHY,
        DEAD,
        MISSING
    }

    private data class ProjectionDisplayToggleDecision(
        val packageName: String,
        val sourceDisplayId: Int,
        val targetDisplayId: Int
    ) {
        val debugString: String
            get() = "$packageName:$sourceDisplayId->$targetDisplayId"
    }

    private fun getPrefs() =
        App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    @JvmStatic
    fun ensureDefaultDesktopShortcuts() {
        val prefs = getPrefs()
        val alreadySeeded =
            prefs.getBoolean(SharedPreferencesKeys.ANDROID_SETTINGS_SHORTCUT_SEEDED.key, false)
        val configs = getAllConfigs()
        val hasAndroidSettings = configs.any { it.packageName == "com.android.settings" }

        if (alreadySeeded) return

        if (!hasAndroidSettings) {
            val androidSettingsConfig =
                PREDEFINED_APPS.first { it.packageName == "com.android.settings" }
            val updatedConfigs = configs.toMutableList().apply { add(androidSettingsConfig) }
            prefs.edit()
                .putString(SharedPreferencesKeys.DISPLAY_APP_CONFIGS.key, gson.toJson(updatedConfigs))
                .putBoolean(SharedPreferencesKeys.ANDROID_SETTINGS_SHORTCUT_SEEDED.key, true)
                .apply()
            Log.i(TAG, "Seeded Android Settings shortcut on the default desktop")
        } else {
            prefs.edit()
                .putBoolean(SharedPreferencesKeys.ANDROID_SETTINGS_SHORTCUT_SEEDED.key, true)
                .apply()
        }
    }

    private fun isCarPlayPackage(packageName: String): Boolean = packageName == CARPLAY_PACKAGE

    private fun isAndroidAutoPackage(packageName: String): Boolean = packageName == ANDROID_AUTO_PACKAGE

    private fun isCarPlayLikePackage(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return normalized == CARPLAY_PACKAGE ||
                normalized == "com.ts.carplay" ||
                normalized.contains("carplay") ||
                normalized.contains("carlink") ||
                normalized.contains("zlink")
    }

    private fun isAndroidAutoLikePackage(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return normalized == ANDROID_AUTO_PACKAGE ||
                normalized == ANDROID_AUTO_SERVICE_PACKAGE ||
                normalized.contains("androidauto") ||
                normalized.contains("gearhead")
    }

    fun isProjectionMirrorPackage(packageName: String): Boolean {
        return isCarPlayLikePackage(packageName) || isAndroidAutoLikePackage(packageName)
    }

    private fun rememberCarPlayDisplayTarget(displayId: Int, reason: String) {
        getPrefs().edit()
            .putInt(PREF_DESIRED_CARPLAY_DISPLAY_ID, displayId)
            .apply()
        syncCarPlayDesiredDisplayProperty(displayId, reason)
        Log.w(TAG, "[$reason] Desired CarPlay display set to $displayId")
    }

    internal fun rememberCarPlayDisplayTargetForOrchestrator(displayId: Int, reason: String) {
        rememberCarPlayDisplayTarget(displayId, reason)
    }

    private fun syncCarPlayDesiredDisplayProperty(displayId: Int, reason: String) {
        if (displayId != 0 && displayId != 3) return
        sh("setprop persist.haval.carplay.desired_display $displayId")
        Log.w(TAG, "[$reason] Desired CarPlay display property set to $displayId")
    }

    private fun currentBootToken(): String {
        val output = sh("cat /proc/sys/kernel/random/boot_id 2>/dev/null || true").trim()
        return Regex("[0-9a-fA-F-]{16,}").find(output)?.value
            ?: "unknown-${System.currentTimeMillis()}"
    }

    private fun rememberAndroidAutoDisplayTarget(displayId: Int, reason: String) {
        getPrefs().edit()
            .putInt(PREF_DESIRED_ANDROID_AUTO_DISPLAY_ID, displayId)
            .apply()
        Log.w(TAG, "[$reason] Desired Android Auto display set to $displayId")
    }

    fun isAndroidAutoDesiredOnCluster(): Boolean {
        return getPrefs().getInt(PREF_DESIRED_ANDROID_AUTO_DISPLAY_ID, -1) == 3
    }

    fun isCarPlayDesiredOnCluster(): Boolean {
        return getPrefs().getInt(PREF_DESIRED_CARPLAY_DISPLAY_ID, -1) == 3
    }

    fun isCarPlayOnDisplay(displayId: Int): Boolean {
        if (findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId) != null) return true
        if (findTaskMatchingOnDisplay(displayId, ::isCarPlayLikePackage) != null) return true

        val topPackage = getTopPackageOnDisplay(displayId)
        if (topPackage != null && isCarPlayLikePackage(topPackage)) return true

        return false
    }

    fun isAndroidAutoOnDisplay(displayId: Int): Boolean {
        if (!hasAndroidAutoVisualOnDisplay(displayId)) return false
        return isAndroidAutoProjectionSessionReady("IS_ANDROID_AUTO_ON_DISPLAY_$displayId")
    }

    fun isProjectionMirrorOnDisplay(displayId: Int): Boolean {
        if (isCarPlayOnDisplay(displayId)) return true
        if (isAndroidAutoOnDisplay(displayId)) return true

        val topPackage = getTopPackageOnDisplay(displayId)
        return topPackage != null && isProjectionMirrorPackage(topPackage)
    }

    fun resolveActiveProjectionPackageForDisplay(displayId: Int): String? {
        if (findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId) != null) return CARPLAY_PACKAGE
        if (findTaskMatchingOnDisplay(displayId, ::isCarPlayLikePackage) != null) return CARPLAY_PACKAGE

        if (hasAndroidAutoVisualOnDisplay(displayId)) {
            if (isAndroidAutoProjectionSessionReady("RESOLVE_ACTIVE_AA_DISPLAY_$displayId")) {
                return ANDROID_AUTO_PACKAGE
            }
            Log.w(
                TAG,
                "[RESOLVE_ACTIVE_AA_DISPLAY_$displayId] Ignoring stale Android Auto visual task; USB/link is not active"
            )
            return null
        }

        val topPackage = getTopPackageOnDisplay(displayId) ?: return null
        return when {
            isCarPlayLikePackage(topPackage) -> CARPLAY_PACKAGE
            isAndroidAutoLikePackage(topPackage) -> {
                if (isAndroidAutoProjectionSessionReady("RESOLVE_ACTIVE_AA_TOP_DISPLAY_$displayId")) {
                    ANDROID_AUTO_PACKAGE
                } else {
                    Log.w(
                        TAG,
                        "[RESOLVE_ACTIVE_AA_TOP_DISPLAY_$displayId] Ignoring stale Android Auto top package; USB/link is not active"
                    )
                    null
                }
            }
            else -> null
        }
    }

    private fun hasAndroidAutoVisualOnDisplay(displayId: Int): Boolean {
        if (findTaskForPackageOnDisplay(ANDROID_AUTO_PACKAGE, displayId) != null) return true
        if (findTaskMatchingOnDisplay(displayId, ::isAndroidAutoLikePackage) != null) return true

        val topPackage = getTopPackageOnDisplay(displayId)
        return topPackage != null && isAndroidAutoLikePackage(topPackage)
    }

    fun toggleActiveProjectionDisplayFromSteeringWheel(reason: String) {
        scope.launch {
            toggleActiveProjectionDisplay(reason)
        }
    }

    private suspend fun toggleActiveProjectionDisplay(reason: String) {
        val mainProjection = resolveActiveProjectionPackageForDisplay(0)
        val clusterProjection = resolveActiveProjectionPackageForDisplay(3)
        val decision = resolveProjectionDisplayToggleDecision(
            mainProjectionPackage = mainProjection,
            clusterProjectionPackage = clusterProjection
        )
            ?: resolveBackgroundProjectionDisplayToggleDecision()
            ?: resolveKnownConnectedProjectionDisplayToggleDecision(reason)

        if (decision == null) {
            Log.w(
                TAG,
                "[$reason] No active/background/connected CarPlay or Android Auto found; skipping toggle"
            )
            return
        }

        Log.w(TAG, "[$reason] Toggling projection display: ${decision.debugString}")
        when (decision.packageName) {
            CARPLAY_PACKAGE -> {
                val config = getCarPlayConfigForDisplay(decision.targetDisplayId)
                if (decision.targetDisplayId == 0) {
                    CarPlayDisplayOrchestrator.openOnMain(
                        config,
                        "${reason}_CARPLAY_TO_D0"
                    )
                } else {
                    sendToDisplay(config.copy(displayId = decision.targetDisplayId))
                }
            }
            ANDROID_AUTO_PACKAGE -> {
                val config = getAndroidAutoConfigForDisplay(decision.targetDisplayId)
                if (decision.targetDisplayId == 0) {
                    startAndroidAutoOnDisplay(config, "${reason}_ANDROID_AUTO_TO_D0")
                } else {
                    sendToDisplay(config.copy(displayId = decision.targetDisplayId))
                }
            }
        }
    }

    private fun resolveProjectionDisplayToggleDecision(
        mainProjectionPackage: String?,
        clusterProjectionPackage: String?
    ): ProjectionDisplayToggleDecision? {
        normalizeProjectionPackage(mainProjectionPackage)?.let {
            return ProjectionDisplayToggleDecision(
                packageName = it,
                sourceDisplayId = 0,
                targetDisplayId = 3
            )
        }

        normalizeProjectionPackage(clusterProjectionPackage)?.let {
            return ProjectionDisplayToggleDecision(
                packageName = it,
                sourceDisplayId = 3,
                targetDisplayId = 0
            )
        }

        return null
    }

    private fun resolveBackgroundProjectionDisplayToggleDecision(): ProjectionDisplayToggleDecision? {
        findTaskMatching { packageName, displayId ->
            displayId == 0 && isCarPlayLikePackage(packageName)
        }?.let {
            return ProjectionDisplayToggleDecision(CARPLAY_PACKAGE, 0, 3)
        }
        findTaskMatching { packageName, displayId ->
            displayId == 0 && isAndroidAutoLikePackage(packageName)
        }?.let {
            return ProjectionDisplayToggleDecision(ANDROID_AUTO_PACKAGE, 0, 3)
        }
        findTaskMatching { packageName, displayId ->
            displayId == 3 && isCarPlayLikePackage(packageName)
        }?.let {
            return ProjectionDisplayToggleDecision(CARPLAY_PACKAGE, 3, 0)
        }
        findTaskMatching { packageName, displayId ->
            displayId == 3 && isAndroidAutoLikePackage(packageName)
        }?.let {
            return ProjectionDisplayToggleDecision(ANDROID_AUTO_PACKAGE, 3, 0)
        }
        return null
    }

    private suspend fun resolveKnownConnectedProjectionDisplayToggleDecision(
        reason: String
    ): ProjectionDisplayToggleDecision? {
        resolveProjectionPackageFromBottomBarState()?.let { packageName ->
            return resolveProjectionDisplayToggleDecisionFromKnownPackage(
                packageName = packageName,
                sourceDisplayId = resolveDesiredProjectionSourceDisplay(packageName)
            )
        }

        if (androidAutoLinkCommandBinder?.isBinderAlive != true) {
            ensureAndroidAutoLinkCommandBound("${reason}_AA_BACKGROUND_BIND")
            delay(ANDROID_AUTO_NATIVE_MEDIA_KEY_BIND_WAIT_MS)
        }
        val androidAutoLinkStatus = readAndroidAutoLinkStatus("${reason}_AA_BACKGROUND")
        if (isAndroidAutoLinkActiveForToggle(androidAutoLinkStatus)) {
            return resolveProjectionDisplayToggleDecisionFromKnownPackage(
                packageName = ANDROID_AUTO_PACKAGE,
                sourceDisplayId = resolveDesiredProjectionSourceDisplay(ANDROID_AUTO_PACKAGE)
            )
        }

        if (isProjectionUsbConfigured() && isCarPlayProjectionProcessAlive()) {
            return resolveProjectionDisplayToggleDecisionFromKnownPackage(
                packageName = CARPLAY_PACKAGE,
                sourceDisplayId = resolveDesiredProjectionSourceDisplay(CARPLAY_PACKAGE)
            )
        }

        return null
    }

    private fun resolveProjectionPackageFromBottomBarState(): String? {
        return normalizeProjectionPackage(BottomBarState.mediaPackageName)
            ?: normalizeProjectionPackage(BottomBarState.activeClusterProjectionPackage)
            ?: normalizeProjectionPackage(BottomBarState.selectedPackage)
    }

    private fun resolveProjectionDisplayToggleDecisionFromKnownPackage(
        packageName: String,
        sourceDisplayId: Int
    ): ProjectionDisplayToggleDecision? {
        val normalizedPackage = normalizeProjectionPackage(packageName) ?: return null
        val normalizedSourceDisplayId = if (sourceDisplayId == 3) 3 else 0
        return ProjectionDisplayToggleDecision(
            packageName = normalizedPackage,
            sourceDisplayId = normalizedSourceDisplayId,
            targetDisplayId = if (normalizedSourceDisplayId == 3) 0 else 3
        )
    }

    private fun resolveDesiredProjectionSourceDisplay(packageName: String): Int {
        return when (normalizeProjectionPackage(packageName)) {
            CARPLAY_PACKAGE -> if (isCarPlayDesiredOnCluster()) 3 else 0
            ANDROID_AUTO_PACKAGE -> if (isAndroidAutoDesiredOnCluster()) 3 else 0
            else -> 0
        }
    }

    private fun isAndroidAutoLinkActiveForToggle(status: Int?): Boolean {
        return status == 3 || status == 7 || status == 8
    }

    private fun isCarPlayProjectionProcessAlive(): Boolean {
        val hostPid = sh("pidof $CARPLAY_HOST_PROCESS 2>/dev/null || true").trim()
        val appPid = sh("pidof $CARPLAY_PACKAGE 2>/dev/null || true").trim()
        return isProjectionProcessPidOutputAliveForTest(hostPid) ||
                isProjectionProcessPidOutputAliveForTest(appPid)
    }

    private fun normalizeProjectionPackage(packageName: String?): String? {
        if (packageName == null) return null
        return when {
            isCarPlayLikePackage(packageName) -> CARPLAY_PACKAGE
            isAndroidAutoLikePackage(packageName) -> ANDROID_AUTO_PACKAGE
            else -> null
        }
    }

    internal fun resolveProjectionDisplayToggleDecisionForTest(
        mainProjectionPackage: String?,
        clusterProjectionPackage: String?
    ): String? {
        return resolveProjectionDisplayToggleDecision(
            mainProjectionPackage = mainProjectionPackage,
            clusterProjectionPackage = clusterProjectionPackage
        )?.debugString
    }

    internal fun resolveProjectionDisplayToggleDecisionFromKnownPackageForTest(
        packageName: String,
        sourceDisplayId: Int
    ): String? {
        return resolveProjectionDisplayToggleDecisionFromKnownPackage(
            packageName = packageName,
            sourceDisplayId = sourceDisplayId
        )?.debugString
    }

    internal fun isAndroidAutoLinkActiveForToggleForTest(status: Int?): Boolean {
        return isAndroidAutoLinkActiveForToggle(status)
    }

    private fun getAndroidAutoConfigForDisplay(
        displayId: Int,
        source: DisplayAppConfig? = null
    ): DisplayAppConfig {
        val base = source
            ?: getAppConfig(ANDROID_AUTO_PACKAGE)
            ?: PREDEFINED_APPS.first { it.packageName == ANDROID_AUTO_PACKAGE }
        val res = getDisplayResolution(displayId)
        return base.copy(
            activityName = ANDROID_AUTO_ACTIVITY,
            displayId = displayId,
            x = 0,
            y = 0,
            width = res.first,
            height = res.second,
            overrideThemeDimensions = true
        )
    }

    private fun configureAndroidAutoProjection(reason: String) {
        Log.w(TAG, "[$reason] Preparing Android Auto projection services")
        sh("am startservice -n $ANDROID_AUTO_SERVICE")
        sh("am startservice -n $ANDROID_AUTO_REMOTE_SERVICE")
        ensureAndroidAutoLinkCommandBound("${reason}_LINK_COMMAND")
    }

    private fun sendAndroidAutoFocus(displayId: Int, reason: String) {
        Log.w(TAG, "[$reason] Sending Android Auto video focus for display $displayId")
        sh("am broadcast -a ts.car.androidauto.view_state --es state foreground --ei displayId $displayId")
        sh("am broadcast -a com.ts.androidauto.action.AndroidAutoService --es \"command\" \"requestVideoFocus\" --ei \"displayId\" $displayId")
    }

    private fun ensureAndroidAutoLinkCommandBound(reason: String): Boolean {
        val currentBinder = androidAutoLinkCommandBinder
        if (currentBinder != null && currentBinder.isBinderAlive) return true

        synchronized(androidAutoLinkCommandLock) {
            val lockedBinder = androidAutoLinkCommandBinder
            if (lockedBinder != null && lockedBinder.isBinderAlive) return true
            if (lockedBinder != null && !lockedBinder.isBinderAlive) {
                androidAutoLinkCommandBinder = null
                androidAutoLinkCommandBindingStarted = false
            }
            if (androidAutoLinkCommandBindingStarted) return false

            val intent = Intent(ANDROID_AUTO_LINK_SERVICE_ACTION).apply {
                component = ComponentName(
                    ANDROID_AUTO_SERVICE_PACKAGE,
                    ANDROID_AUTO_LINK_SERVICE_CLASS
                )
            }

            return try {
                androidAutoLinkCommandBindingStarted =
                    App.getContext().bindService(
                        intent,
                        androidAutoLinkCommandConnection,
                        Context.BIND_AUTO_CREATE
                    )
                Log.w(
                    TAG,
                    "[$reason] Android Auto LinkCommand bind requested: $androidAutoLinkCommandBindingStarted"
                )
                androidAutoLinkCommandBinder?.isBinderAlive == true
            } catch (e: Exception) {
                androidAutoLinkCommandBindingStarted = false
                Log.e(TAG, "[$reason] Failed to bind Android Auto LinkCommand service", e)
                false
            }
        }
    }

    private fun ensureCarPlayServiceBound(reason: String): Boolean {
        val currentBinder = carPlayServiceBinder
        if (currentBinder != null && currentBinder.isBinderAlive) return true

        synchronized(carPlayServiceLock) {
            val lockedBinder = carPlayServiceBinder
            if (lockedBinder != null && lockedBinder.isBinderAlive) return true
            if (lockedBinder != null && !lockedBinder.isBinderAlive) {
                carPlayServiceBinder = null
                carPlayServiceBindingStarted = false
            }
            if (carPlayServiceBindingStarted) return false

            val intent = Intent().apply {
                component = ComponentName(CARPLAY_HOST_PROCESS, CARPLAY_HOST_SERVICE_CLASS)
            }

            return try {
                carPlayServiceBindingStarted =
                    App.getContext().bindService(
                        intent,
                        carPlayServiceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                Log.w(TAG, "[$reason] CarPlay service bind requested: $carPlayServiceBindingStarted")
                carPlayServiceBinder?.isBinderAlive == true
            } catch (e: Exception) {
                carPlayServiceBindingStarted = false
                Log.e(TAG, "[$reason] Failed to bind CarPlay service", e)
                false
            }
        }
    }

    private fun transactCarPlayServiceSync(
        transactionCode: Int,
        reason: String,
        writePayload: (Parcel) -> Unit = {}
    ): Boolean {
        val binder = carPlayServiceBinder
        if (binder == null || !binder.isBinderAlive) {
            ensureCarPlayServiceBound("${reason}_BIND")
            return false
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(CARPLAY_SERVICE_INTERFACE)
            writePayload(data)
            val sent = binder.transact(transactionCode, data, reply, 0)
            if (sent) reply.readException()
            sent
        } catch (e: Exception) {
            synchronized(carPlayServiceLock) {
                carPlayServiceBinder = null
                carPlayServiceBindingStarted = false
            }
            Log.e(TAG, "[$reason] CarPlay service transact failed", e)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactCarPlayServiceInt(
        transactionCode: Int,
        reason: String
    ): Int? {
        val binder = carPlayServiceBinder
        if (binder == null || !binder.isBinderAlive) {
            ensureCarPlayServiceBound("${reason}_BIND")
            return null
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(CARPLAY_SERVICE_INTERFACE)
            val sent = binder.transact(transactionCode, data, reply, 0)
            if (!sent) return null
            reply.readException()
            reply.readInt()
        } catch (e: Exception) {
            synchronized(carPlayServiceLock) {
                carPlayServiceBinder = null
                carPlayServiceBindingStarted = false
            }
            Log.e(TAG, "[$reason] CarPlay service int transact failed", e)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun readCarPlayLinkStatus(reason: String): Int? {
        return transactCarPlayServiceInt(
            CARPLAY_SERVICE_GET_LINK_STATUS_TRANSACTION,
            "${reason}_GET_LINK_STATUS"
        )
    }

    private suspend fun requestCarPlayUiIfLinkActivated(reason: String): Boolean {
        repeat(6) { attempt ->
            if (carPlayServiceBinder?.isBinderAlive == true) return@repeat
            ensureCarPlayServiceBound("${reason}_BIND_$attempt")
            delay(120L)
        }

        val linkStatus = readCarPlayLinkStatus(reason)
        if (linkStatus != CARPLAY_LINK_STATUS_ACTIVATED) {
            Log.w(TAG, "[$reason] Skipping CarPlay requestUi; linkStatus=${linkStatus ?: "UNKNOWN"}")
            return false
        }

        val sent =
            transactCarPlayServiceSync(
                CARPLAY_SERVICE_REQUEST_UI_TRANSACTION,
                "${reason}_REQUEST_UI"
            ) { data ->
                data.writeInt(CARPLAY_LAUNCH_MODE_NORMAL)
            }
        Log.w(TAG, "[$reason] CarPlay requestUi normal sent=$sent")
        return sent
    }

    private fun transactAndroidAutoLinkCommand(
        transactionCode: Int,
        reason: String,
        writePayload: (Parcel) -> Unit
    ): Boolean {
        val binder = androidAutoLinkCommandBinder
        if (binder == null || !binder.isBinderAlive) {
            ensureAndroidAutoLinkCommandBound(reason)
            return false
        }

        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ANDROID_AUTO_LINK_COMMAND_INTERFACE)
            writePayload(data)
            val sent = binder.transact(transactionCode, data, null, IBinder.FLAG_ONEWAY)
            if (!sent) {
                Log.w(TAG, "[$reason] Android Auto LinkCommand transact returned false")
            }
            sent
        } catch (e: Exception) {
            synchronized(androidAutoLinkCommandLock) {
                androidAutoLinkCommandBinder = null
                androidAutoLinkCommandBindingStarted = false
            }
            Log.e(TAG, "[$reason] Android Auto LinkCommand transact failed", e)
            false
        } finally {
            data.recycle()
        }
    }

    private fun transactAndroidAutoLinkCommandSync(
        transactionCode: Int,
        reason: String,
        writePayload: (Parcel) -> Unit = {}
    ): Boolean {
        val binder = androidAutoLinkCommandBinder
        if (binder == null || !binder.isBinderAlive) {
            ensureAndroidAutoLinkCommandBound(reason)
            return false
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ANDROID_AUTO_LINK_COMMAND_INTERFACE)
            writePayload(data)
            val sent = binder.transact(transactionCode, data, reply, 0)
            if (sent) {
                reply.readException()
            } else {
                Log.w(TAG, "[$reason] Android Auto LinkCommand sync transact returned false")
            }
            sent
        } catch (e: Exception) {
            synchronized(androidAutoLinkCommandLock) {
                androidAutoLinkCommandBinder = null
                androidAutoLinkCommandBindingStarted = false
            }
            Log.e(TAG, "[$reason] Android Auto LinkCommand sync transact failed", e)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactAndroidAutoLinkCommandInt(
        transactionCode: Int,
        reason: String,
        writePayload: (Parcel) -> Unit = {}
    ): Int? {
        val binder = androidAutoLinkCommandBinder
        if (binder == null || !binder.isBinderAlive) {
            ensureAndroidAutoLinkCommandBound(reason)
            return null
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ANDROID_AUTO_LINK_COMMAND_INTERFACE)
            writePayload(data)
            val sent = binder.transact(transactionCode, data, reply, 0)
            if (!sent) {
                Log.w(TAG, "[$reason] Android Auto LinkCommand int transact returned false")
                null
            } else {
                reply.readException()
                reply.readInt()
            }
        } catch (e: Exception) {
            synchronized(androidAutoLinkCommandLock) {
                androidAutoLinkCommandBinder = null
                androidAutoLinkCommandBindingStarted = false
            }
            Log.e(TAG, "[$reason] Android Auto LinkCommand int transact failed", e)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun subscribeAndroidAutoHmiKeys(reason: String): Boolean {
        val subscribed = transactAndroidAutoLinkCommandSync(
            ANDROID_AUTO_LINK_COMMAND_SUBSCRIBE_HMI_KEYS_TRANSACTION,
            "${reason}_SUBSCRIBE_HMI_KEYS"
        )
        Log.w(TAG, "[$reason] Android Auto HMI key subscription requested: $subscribed")
        return subscribed
    }

    private fun readAndroidAutoLinkStatus(reason: String): Int? {
        return transactAndroidAutoLinkCommandInt(
            ANDROID_AUTO_LINK_COMMAND_GET_LINK_STATUS_TRANSACTION,
            "${reason}_GET_LINK_STATUS"
        )
    }

    private fun readAndroidAutoLinkStatusIfAlreadyBound(reason: String): Int? {
        val binder = androidAutoLinkCommandBinder
        if (binder == null || !binder.isBinderAlive) return null
        return transactAndroidAutoLinkCommandInt(
            ANDROID_AUTO_LINK_COMMAND_GET_LINK_STATUS_TRANSACTION,
            "${reason}_GET_LINK_STATUS_BOUND"
        )
    }

    private fun isAndroidAutoProjectionSessionReady(reason: String): Boolean {
        if (isProjectionUsbConfigured()) return true

        val linkStatus = readAndroidAutoLinkStatusIfAlreadyBound(reason)
        if (isAndroidAutoLinkActiveForToggle(linkStatus)) return true

        Log.w(
            TAG,
            "[$reason] Android Auto projection session is not ready; " +
                    "usbConfigured=false linkStatus=${describeAndroidAutoLinkStatus(linkStatus)}"
        )
        return false
    }

    fun isAndroidAutoProjectionSessionReadyForMedia(reason: String): Boolean {
        if (isAndroidAutoProjectionSessionReady(reason)) return true
        val visualTask =
            findTaskMatching { packageName, _ ->
                isAndroidAutoLikePackage(packageName)
            }
        if (visualTask != null) {
            Log.w(
                TAG,
                "[$reason] Treating Android Auto visual task on display ${visualTask.displayId} " +
                        "as active media-session evidence while USB/link status is unavailable"
            )
            return true
        }
        return false
    }

    fun isAndroidAutoProjectionLinkActiveIfAlreadyBoundForMedia(reason: String): Boolean {
        return isAndroidAutoLinkActiveForToggle(readAndroidAutoLinkStatusIfAlreadyBound(reason))
    }

    private fun readAndroidAutoMusicStatus(reason: String): Int? {
        return transactAndroidAutoLinkCommandInt(
            ANDROID_AUTO_LINK_COMMAND_GET_MUSIC_STATUS_TRANSACTION,
            "${reason}_GET_MUSIC_STATUS"
        )
    }

    private fun readAndroidAutoMediaProgress(reason: String): Int? {
        return transactAndroidAutoLinkCommandInt(
            ANDROID_AUTO_LINK_COMMAND_GET_MEDIA_PROGRESS_TRANSACTION,
            "${reason}_GET_MEDIA_PROGRESS"
        )
    }

    private fun describeAndroidAutoLinkStatus(status: Int?): String {
        return when (status) {
            null -> "UNKNOWN(null)"
            -1 -> "NO_DEVICE_OR_POWER(-1)"
            0 -> "INIT(0)"
            1 -> "AVAILABLE(1)"
            2 -> "ACTIVATING(2)"
            3 -> "ACTIVATED(3)"
            4 -> "DEACTIVATING(4)"
            5 -> "DEACTIVATED(5)"
            6 -> "CONNECT_FAILED(6)"
            7 -> "SHOW_VIDEO(7)"
            8 -> "AAP_FRX(8)"
            9 -> "AAP_USERSWITCH(9)"
            10 -> "CONNECT_ERROR(10)"
            else -> "UNKNOWN($status)"
        }
    }

    private fun describeAndroidAutoMusicStatus(status: Int?): String {
        return when (status) {
            null -> "UNKNOWN(null)"
            0 -> "NOT_START(0)"
            1 -> "PLAYING(1)"
            2 -> "PAUSED(2)"
            else -> "UNKNOWN($status)"
        }
    }

    private fun startAndroidAutoActivity(displayId: Int, reason: String) {
        val escapedActivity = ANDROID_AUTO_ACTIVITY.replace("$", "\\$")
        val command = if (displayId == 0) {
            "am start -n $ANDROID_AUTO_PACKAGE/$escapedActivity --display 0 --windowingMode 1 -f 0x14000000"
        } else {
            "am start --display $displayId --windowingMode 5 --activity-multiple-task -f $ANDROID_AUTO_START_FLAGS -n $ANDROID_AUTO_PACKAGE/$escapedActivity"
        }
        Log.w(TAG, "[$reason] Starting Android Auto activity on display $displayId")
        sh(command)
    }

    private fun notifyAndroidAutoDisplayHandoff(displayId: Int, previousDisplay: Int?) {
        notifyDisplayStateChanged(displayId)
        if (previousDisplay != null && previousDisplay != displayId) {
            notifyDisplayStateChanged(previousDisplay)
        }
        if (displayId == 0) {
            notifyDisplayStateChanged(3)
            notifyBottomBarUpdate()
        }
    }

    private fun resizeAndFocusAndroidAuto(
        taskInfo: TaskInfo,
        displayId: Int,
        bounds: IntArray,
        reason: String
    ) {
        if (displayId == 0) {
            sh("am stack set-windowing-mode ${taskInfo.stackId} 1")
        }
        if (!taskInfo.bounds.contentEqualsOrNull(bounds)) {
            sh("am stack resize ${taskInfo.stackId} ${bounds[0]} ${bounds[1]} ${bounds[2]} ${bounds[3]}")
        } else {
            Log.w(
                TAG,
                "[$reason] Android Auto stack ${taskInfo.stackId} already fullscreen; skipping resize"
            )
        }
        Thread.sleep(160)
        sendAndroidAutoFocus(displayId, reason)
    }

    private fun ensureAndroidAutoFullscreenAndFocus(
        taskInfo: TaskInfo,
        displayId: Int,
        reason: String
    ) {
        resizeAndFocusAndroidAuto(
            taskInfo,
            displayId,
            getAndroidAutoDisplayBounds(displayId),
            reason
        )
    }

    private fun bringOtherTaskInStackToFront(stackId: Int, excludePackage: String, reason: String): Boolean {
        val otherTask = findOtherTaskInStack(stackId, excludePackage)
        if (otherTask == null) {
            Log.w(TAG, "[$reason] No sibling task found in stack $stackId")
            return false
        }

        val escapedActivity = otherTask.activityName.replace("$", "\\$")
        Log.w(
            TAG,
            "[$reason] Bringing sibling task ${otherTask.taskId} (${otherTask.packageName}) to front before projection display handoff"
        )
        sh("am start -n ${otherTask.packageName}/$escapedActivity --display ${otherTask.displayId} -f 0x14000000")
        return true
    }

    private fun bringNonProjectionTaskOnDisplayToFront(displayId: Int, reason: String): Boolean {
        val task = findFirstNonProjectionTaskOnDisplay(displayId)
        if (task == null) {
            Log.w(TAG, "[$reason] No non-projection task found on display $displayId to defocus CarPlay")
            return false
        }

        val escapedActivity = task.activityName.replace("$", "\\$")
        Log.w(
            TAG,
            "[$reason] Bringing display $displayId task ${task.taskId} (${task.packageName}) to front before CarPlay retarget"
        )
        sh("am start -n ${task.packageName}/$escapedActivity --display $displayId --windowingMode 1 -f 0x14000000")
        return true
    }

    private fun closeAndroidAutoVisualStacks(reason: String, exceptStackId: Int? = null) {
        val tasks = findAllTasksForPackage(ANDROID_AUTO_PACKAGE)
        tasks
            .filter { it.stackId != exceptStackId }
            .map { it.stackId to it.displayId }
            .distinct()
            .forEach { (stackId, displayId) ->
                val tasksInStack = countTasksInStack(stackId)
                if (tasksInStack > 1) {
                    Log.w(
                        TAG,
                        "[$reason] Keeping mixed Android Auto stack $stackId on display $displayId ($tasksInStack tasks)"
                    )
                    return@forEach
                }
                Log.w(TAG, "[$reason] Removing duplicate Android Auto visual stack $stackId from display $displayId")
                sh("am stack remove $stackId")
            }
    }

    fun cleanupStaleAndroidAutoVisualStacksIfDisconnected(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastAndroidAutoUsbDisconnectCleanupAt < ANDROID_AUTO_USB_DISCONNECT_CLEANUP_COOLDOWN_MS) {
            return
        }
        lastAndroidAutoUsbDisconnectCleanupAt = now

        if (isProjectionUsbConfigured()) return

        val linkStatus = readAndroidAutoLinkStatusIfAlreadyBound("${reason}_AA_STALE_CLEANUP")
        if (isAndroidAutoLinkActiveForToggle(linkStatus)) return

        val tasks = findAllTasksForPackage(ANDROID_AUTO_PACKAGE)
        if (tasks.isEmpty()) return

        Log.w(
            TAG,
            "[$reason] Removing stale Android Auto visual stack(s) because USB is disconnected " +
                    "and linkStatus=${describeAndroidAutoLinkStatus(linkStatus)}"
        )
        closeAndroidAutoVisualStacks("${reason}_STALE_AA_USB_DISCONNECTED")
        notifyDisplayStateChanged(3)
        notifyBottomBarUpdate()
    }

    private suspend fun startAndroidAutoOnDisplay(
        sourceConfig: DisplayAppConfig,
        reason: String
    ) {
        val config = getAndroidAutoConfigForDisplay(sourceConfig.displayId, sourceConfig)
        val displayId = config.displayId
        val bounds = getEffectiveBounds(config)
        val previousDisplay = findTaskForPackage(ANDROID_AUTO_PACKAGE)?.displayId

        rememberAndroidAutoDisplayTarget(displayId, reason)
        AndroidAutoPatchManager.ensureMounted()
        configureAndroidAutoProjection(reason)

        if (displayId != 0) {
            evictOtherAppsFromDisplay(displayId, ANDROID_AUTO_PACKAGE)
            BottomBarState.restoredApps.remove(ANDROID_AUTO_PACKAGE)
        } else if (!BottomBarState.restoredApps.contains(ANDROID_AUTO_PACKAGE)) {
            BottomBarState.restoredApps.add(ANDROID_AUTO_PACKAGE)
        }

        var targetTask = findTaskForPackageOnDisplay(ANDROID_AUTO_PACKAGE, displayId)
        if (targetTask != null) {
            resizeAndFocusAndroidAuto(targetTask, displayId, bounds, "${reason}_ALREADY_ON_TARGET")
            closeAndroidAutoVisualStacks("${reason}_ALREADY_ON_TARGET_CLEAN_DUPLICATES", exceptStackId = targetTask.stackId)
            notifyAndroidAutoDisplayHandoff(displayId, previousDisplay)
            return
        }

        val currentTask = findTaskForPackage(ANDROID_AUTO_PACKAGE)
        if (currentTask != null && currentTask.displayId != displayId) {
            saveCurrentBounds(ANDROID_AUTO_PACKAGE, currentTask)
            val tasksInStack = countTasksInStack(currentTask.stackId)

            if (tasksInStack > 1) {
                Log.w(
                    TAG,
                    "[$reason] Android Auto is in mixed stack ${currentTask.stackId} ($tasksInStack tasks); re-targeting activity without moving sibling apps"
                )
                bringOtherTaskInStackToFront(currentTask.stackId, ANDROID_AUTO_PACKAGE, reason)
                Thread.sleep(220)
                startAndroidAutoActivity(displayId, "${reason}_MIXED_STACK_START")
            } else {
                Log.w(TAG, "[$reason] Moving Android Auto stack ${currentTask.stackId} to display $displayId")
                val result = sh("am display move-stack ${currentTask.stackId} $displayId")
                if (result.contains("Exception") || result.contains("Error")) {
                    Log.e(TAG, "[$reason] Android Auto move-stack failed: $result")
                    startAndroidAutoActivity(displayId, "${reason}_MOVE_FAILED_START")
                }
            }
        } else {
            startAndroidAutoActivity(displayId, "${reason}_START")
        }

        Thread.sleep(700)
        targetTask = findTaskForPackageOnDisplay(ANDROID_AUTO_PACKAGE, displayId)

        if (targetTask == null) {
            val wrongDisplayTask = findTaskForPackage(ANDROID_AUTO_PACKAGE)
            if (wrongDisplayTask != null && wrongDisplayTask.displayId != displayId) {
                Log.w(
                    TAG,
                    "[$reason] Android Auto remained on display ${wrongDisplayTask.displayId}; retrying with visual app restart only"
                )
            } else {
                Log.w(TAG, "[$reason] Android Auto task not found; retrying with visual app restart")
            }

            // Last resort for a black/stuck visual Activity. Do not force-stop
            // com.ts.androidauto so the phone-side projection service can recover.
            sh("am force-stop $ANDROID_AUTO_PACKAGE")
            Thread.sleep(650)
            configureAndroidAutoProjection("${reason}_VISUAL_RESTART")
            startAndroidAutoActivity(displayId, "${reason}_VISUAL_RESTART")
            Thread.sleep(900)
            targetTask = findTaskForPackageOnDisplay(ANDROID_AUTO_PACKAGE, displayId)
        }

        if (targetTask != null) {
            resizeAndFocusAndroidAuto(targetTask, displayId, bounds, "${reason}_POST_START")
            closeAndroidAutoVisualStacks("${reason}_POST_START_CLEAN_DUPLICATES", exceptStackId = targetTask.stackId)

            CoroutineScope(Dispatchers.IO).launch {
                delay(500)
                sendAndroidAutoFocus(displayId, "${reason}_POST_START_P1")
                delay(900)
                sendAndroidAutoFocus(displayId, "${reason}_POST_START_P2")
            }
        } else {
            Log.e(TAG, "[$reason] Android Auto task was not found on display $displayId after recovery")
        }

        notifyAndroidAutoDisplayHandoff(displayId, previousDisplay)
    }

    private fun getCarPlayDisplayBounds(displayId: Int): IntArray {
        val res = getDisplayResolution(displayId)
        return intArrayOf(0, 0, res.first, res.second)
    }

    private fun getAndroidAutoDisplayBounds(displayId: Int): IntArray {
        val res = getDisplayResolution(displayId)
        return intArrayOf(0, 0, res.first, res.second)
    }

    internal fun getCarPlayConfigForDisplay(
        displayId: Int,
        source: DisplayAppConfig? = null
    ): DisplayAppConfig {
        val base = source
            ?: getAppConfig(CARPLAY_PACKAGE)
            ?: PREDEFINED_APPS.first { it.packageName == CARPLAY_PACKAGE }
        val res = getDisplayResolution(displayId)
        return base.copy(
            activityName = CARPLAY_ACTIVITY,
            displayId = displayId,
            x = 0,
            y = 0,
            width = res.first,
            height = res.second,
            overrideThemeDimensions = true
        )
    }

    private fun configureCarPlayProjection(reason: String) {
        Log.w(TAG, "[$reason] Preparing CarPlay projection services")
        sh("setprop persist.haval.carplay.video.height 720")
        startCarPlayProjectionServiceIfProcessMissing(
            processName = CARPLAY_HOST_PROCESS,
            serviceName = CARPLAY_HOST_SERVICE,
            reason = "${reason}_HOST"
        )
        startCarPlayProjectionServiceIfProcessMissing(
            processName = CARPLAY_PACKAGE,
            serviceName = CARPLAY_REMOTE_SERVICE,
            reason = "${reason}_REMOTE"
        )
    }

    private fun startCarPlayProjectionServiceIfProcessMissing(
        processName: String,
        serviceName: String,
        reason: String
    ) {
        val pidOutput = sh("pidof $processName 2>/dev/null || true").trim()
        if (isProjectionProcessPidOutputAliveForTest(pidOutput)) {
            Log.w(
                TAG,
                "[$reason] CarPlay process $processName already alive (pid=$pidOutput); skipping $serviceName start"
            )
            return
        }

        Log.w(TAG, "[$reason] CarPlay process $processName is not alive; starting $serviceName")
        sh("am startservice -n $serviceName")
    }

    internal fun isProjectionProcessPidOutputAliveForTest(pidOutput: String): Boolean {
        return pidOutput
            .trim()
            .split(Regex("\\s+"))
            .any { pid -> (pid.toLongOrNull() ?: 0L) > 0L }
    }

    private fun sendCarPlayFocus(displayId: Int, reason: String) {
        Log.w(TAG, "[$reason] Sending CarPlay video focus for display $displayId")
        sh("am broadcast -a ts.car.carplay.view_state --es state foreground --ei displayId $displayId")
        sh("am broadcast -a com.ts.carplay.action.VIDEO_FOCUS_CHANGE --es \"focus\" \"$CARPLAY_PACKAGE\" --ei \"displayId\" $displayId")
    }

    private fun sendCarPlayVideoFocusOnly(displayId: Int, reason: String) {
        Log.w(TAG, "[$reason] Sending lite CarPlay video focus for display $displayId")
        sh("am broadcast -a com.ts.carplay.action.VIDEO_FOCUS_CHANGE --es \"focus\" \"$CARPLAY_PACKAGE\" --ei \"displayId\" $displayId")
    }

    private fun sendCarPlayRenderRefresh(displayId: Int, reason: String) {
        Log.w(TAG, "[$reason] Requesting CarPlay render refresh for display $displayId")
        sh("am broadcast -a $CARPLAY_REFRESH_RENDER_ACTION --ei displayId $displayId")
    }

    @Synchronized
    fun startCarPlayClusterContractWatchdog() {
        if (carPlayClusterWatchdogStarted) return
        carPlayClusterWatchdogStarted = true

        scope.launch {
            val desiredDisplay = getPrefs().getInt(PREF_DESIRED_CARPLAY_DISPLAY_ID, -1)
            if (desiredDisplay == 3 && findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3) == null) {
                carPlayClusterTargetBootGraceUntil =
                    System.currentTimeMillis() + CARPLAY_CLUSTER_TARGET_BOOT_GRACE_MS
                syncCarPlayDesiredDisplayProperty(
                    3,
                    "CARPLAY_CLUSTER_WATCHDOG_START_PENDING_CLUSTER_TARGET"
                )
                Log.w(
                    TAG,
                    "[CARPLAY_CLUSTER_WATCHDOG_START_PENDING_CLUSTER_TARGET] Preserving desired D3 target " +
                            "during boot USB/autostart grace; D0 may be used only as a staging display"
                )
            } else {
                syncCarPlayDesiredDisplayProperty(
                    desiredDisplay,
                    "CARPLAY_CLUSTER_WATCHDOG_START"
                )
            }
            delay(CARPLAY_CLUSTER_WATCHDOG_START_DELAY_MS)
            while (true) {
                try {
                    enforceCarPlayClusterContractFromWatchdog()
                } catch (e: Exception) {
                    Log.e(TAG, "[CARPLAY_CLUSTER_WATCHDOG] Failed to verify CarPlay task placement", e)
                }
                delay(CARPLAY_CLUSTER_WATCHDOG_INTERVAL_MS)
            }
        }
        Log.w(TAG, "[CARPLAY_CLUSTER_WATCHDOG] Started")
    }

    @Synchronized
    fun startCarPlaySystemUiIconWatchdog() {
        if (carPlaySystemUiIconWatchdogStarted) return
        carPlaySystemUiIconWatchdogStarted = true

        scope.launch {
            delay(CARPLAY_SYSTEM_UI_ICON_WATCHDOG_START_DELAY_MS)
            while (true) {
                try {
                    recoverCarPlaySystemUiIconIfNeeded()
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] Failed to verify SystemUI CarPlay bind",
                        e
                    )
                }
                delay(CARPLAY_SYSTEM_UI_ICON_WATCHDOG_INTERVAL_MS)
            }
        }
        Log.w(TAG, "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] Started")
    }

    @Synchronized
    fun startAndroidAutoSteeringMediaFocusKeepAlive() {
        if (androidAutoSteeringMediaFocusKeepAliveStarted) return
        androidAutoSteeringMediaFocusKeepAliveStarted = true

        scope.launch {
            delay(ANDROID_AUTO_STEERING_MEDIA_FOCUS_KEEPALIVE_START_DELAY_MS)
            while (true) {
                try {
                    pulseAndroidAutoFocusIfLiveOnCluster("AA_STEERING_MEDIA_KEEPALIVE")
                } catch (e: Exception) {
                    Log.e(TAG, "[AA_STEERING_MEDIA_KEEPALIVE] Failed to pulse Android Auto focus", e)
                }
                delay(ANDROID_AUTO_STEERING_MEDIA_FOCUS_KEEPALIVE_INTERVAL_MS)
            }
        }
        Log.w(TAG, "[AA_STEERING_MEDIA_KEEPALIVE] Started")
    }

    private suspend fun recoverCarPlaySystemUiIconIfNeeded() {
        val now = System.currentTimeMillis()
        val usbConfigured = isProjectionUsbConfigured()
        if (!usbConfigured) {
            recoverCarPlaySystemUiIconAfterUsbDisconnectIfNeeded(now)
            carPlaySystemUiMissingBindCount = 0
            return
        }

        lastCarPlaySystemUiIconUsbConfiguredState = true

        val carPlayRelevant = isCarPlaySystemUiIconRelevantWhenUsbConfigured()
        if (!carPlayRelevant) {
            carPlaySystemUiMissingBindCount = 0
            return
        }
        lastCarPlaySystemUiIconRelevantAt = now

        val serviceDump = sh("dumpsys activity services com.ts.carplay 2>/dev/null || true")
        val connectionState = resolveCarPlaySystemUiServiceConnectionState(serviceDump)
        carPlaySystemUiMissingBindCount =
            if (connectionState == CarPlaySystemUiServiceConnectionState.MISSING) {
                carPlaySystemUiMissingBindCount + 1
            } else {
                0
            }

        val speedKmh = readVehicleSpeedKmh()
        if (
            !shouldRecoverCarPlaySystemUiIcon(
                connectionState = connectionState,
                carPlayRelevant = true,
                speedKmh = speedKmh,
                missingBindSamples = carPlaySystemUiMissingBindCount,
                now = now,
                lastRecoveryAt = lastCarPlaySystemUiIconRecoveryAt
            )
        ) {
            if (connectionState != CarPlaySystemUiServiceConnectionState.HEALTHY) {
                Log.w(
                    TAG,
                    "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] Waiting; state=$connectionState " +
                            "missingSamples=$carPlaySystemUiMissingBindCount speed=${speedKmh ?: "unknown"}"
                )
            }
            return
        }

        val oldPid = sh("pidof $SYSTEM_UI_PACKAGE 2>/dev/null || true").trim()
        if (oldPid.isEmpty()) {
            Log.w(
                TAG,
                "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] SystemUI pid not found; skipping icon recovery"
            )
            return
        }

        lastCarPlaySystemUiIconRecoveryAt = now
        Log.w(
            TAG,
            "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] Restarting SystemUI to restore native CarPlay icon; " +
                    "state=$connectionState missingSamples=$carPlaySystemUiMissingBindCount " +
                    "speed=${speedKmh ?: "unknown"} oldPid=$oldPid"
        )
        sh("kill -9 $oldPid 2>/dev/null || true")
        delay(2_500L)

        val newPid = sh("pidof $SYSTEM_UI_PACKAGE 2>/dev/null || true").trim()
        val newDump = sh("dumpsys activity services com.ts.carplay 2>/dev/null || true")
        val newState = resolveCarPlaySystemUiServiceConnectionState(newDump)
        carPlaySystemUiMissingBindCount =
            if (newState == CarPlaySystemUiServiceConnectionState.MISSING) 1 else 0
        Log.w(
            TAG,
            "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] SystemUI recovery result; newPid=$newPid " +
                    "connectionState=$newState"
        )
    }

    private suspend fun recoverCarPlaySystemUiIconAfterUsbDisconnectIfNeeded(now: Long) {
        val previousUsbConfigured = lastCarPlaySystemUiIconUsbConfiguredState
        lastCarPlaySystemUiIconUsbConfiguredState = false

        val speedKmh = readVehicleSpeedKmh()
        if (
            !shouldRefreshCarPlaySystemUiIconAfterUsbDisconnect(
                previousUsbConfigured = previousUsbConfigured,
                lastRelevantAt = lastCarPlaySystemUiIconRelevantAt,
                speedKmh = speedKmh,
                now = now,
                lastDisconnectRefreshAt = lastCarPlaySystemUiIconDisconnectRefreshAt
            )
        ) {
            return
        }

        val oldPid = sh("pidof $SYSTEM_UI_PACKAGE 2>/dev/null || true").trim()
        if (oldPid.isEmpty()) {
            Log.w(
                TAG,
                "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] SystemUI pid not found; skipping disconnect icon refresh"
            )
            return
        }

        lastCarPlaySystemUiIconDisconnectRefreshAt = now
        Log.w(
            TAG,
            "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] Restarting SystemUI after CarPlay USB disconnect " +
                    "to refresh native icon; previousUsbConfigured=$previousUsbConfigured " +
                    "lastRelevantAgeMs=${now - lastCarPlaySystemUiIconRelevantAt} " +
                    "speed=${speedKmh ?: "unknown"} oldPid=$oldPid"
        )
        sh("kill -9 $oldPid 2>/dev/null || true")
        delay(2_500L)

        val newPid = sh("pidof $SYSTEM_UI_PACKAGE 2>/dev/null || true").trim()
        Log.w(
            TAG,
            "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] Disconnect icon refresh result; newPid=$newPid"
        )
    }

    private fun isCarPlaySystemUiIconRelevantWhenUsbConfigured(): Boolean {
        val hasVisualTask = isCarPlayOnDisplay(0) || isCarPlayOnDisplay(3)
        val carPlayAppPid = sh("pidof $CARPLAY_PACKAGE 2>/dev/null || true").trim()
        val carPlayHostPid = sh("pidof $CARPLAY_HOST_PROCESS 2>/dev/null || true").trim()
        val serviceDump = sh("dumpsys activity services $CARPLAY_HOST_PROCESS 2>/dev/null || true")
        val linkStatus = readCarPlayLinkStatus("CARPLAY_SYSTEM_UI_ICON_RELEVANCE")
        val relevant =
            isCarPlaySystemUiIconRelevantForState(
                hasVisualTask = hasVisualTask,
                appPidAlive = isProjectionProcessPidOutputAliveForTest(carPlayAppPid),
                hostPidAlive = isProjectionProcessPidOutputAliveForTest(carPlayHostPid),
                serviceDump = serviceDump,
                linkStatus = linkStatus
            )

        if (relevant && !hasVisualTask) {
            Log.w(
                TAG,
                "[CARPLAY_SYSTEM_UI_ICON_WATCHDOG] CarPlay icon relevant without visual task; " +
                        "hostPid=${carPlayHostPid.ifBlank { "-" }} appPid=${carPlayAppPid.ifBlank { "-" }} " +
                        "linkStatus=${linkStatus ?: "UNKNOWN"}"
            )
        }
        return relevant
    }

    private fun isCarPlaySystemUiIconRelevantForState(
        hasVisualTask: Boolean,
        appPidAlive: Boolean,
        hostPidAlive: Boolean,
        serviceDump: String,
        linkStatus: Int?
    ): Boolean {
        if (hasVisualTask || appPidAlive) return true
        if (linkStatus == CARPLAY_LINK_STATUS_ACTIVATED) return true
        if (serviceDump.contains(CARPLAY_HOST_SERVICE)) return true
        return hostPidAlive
    }

    private fun readVehicleSpeedKmh(): Double? {
        val raw =
            ServiceManager.getInstance()
                .getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.value)
        return parseVehicleSpeedKmh(raw)
    }

    internal fun parseVehicleSpeedKmh(rawSpeed: String?): Double? {
        if (rawSpeed.isNullOrBlank()) return null
        return Regex("""-?\d+(?:[.,]\d+)?""")
            .find(rawSpeed)
            ?.value
            ?.replace(',', '.')
            ?.toDoubleOrNull()
    }

    private fun resolveCarPlaySystemUiServiceConnectionState(
        serviceDump: String
    ): CarPlaySystemUiServiceConnectionState {
        var pendingCarPlayConnectionDead: Boolean? = null
        serviceDump.lineSequence().forEach { line ->
            val isCarPlayConnection =
                line.contains("ConnectionRecord{") && line.contains(CARPLAY_HOST_SERVICE)
            if (isCarPlayConnection) {
                val dead = Regex("""\bDEAD\b""").containsMatchIn(line)
                if (line.contains(SYSTEM_UI_PACKAGE)) {
                    return if (dead) {
                        CarPlaySystemUiServiceConnectionState.DEAD
                    } else {
                        CarPlaySystemUiServiceConnectionState.HEALTHY
                    }
                }
                pendingCarPlayConnectionDead = dead
                return@forEach
            }

            val pendingDead = pendingCarPlayConnectionDead
            if (
                pendingDead != null &&
                    line.contains("binding=AppBindRecord") &&
                    line.contains(CARPLAY_HOST_SERVICE)
            ) {
                if (line.contains(SYSTEM_UI_PACKAGE)) {
                    return if (pendingDead) {
                        CarPlaySystemUiServiceConnectionState.DEAD
                    } else {
                        CarPlaySystemUiServiceConnectionState.HEALTHY
                    }
                }
                pendingCarPlayConnectionDead = null
                return@forEach
            }

            if (line.contains(CARPLAY_HOST_SERVICE) && line.contains(SYSTEM_UI_PACKAGE)) {
                return if (Regex("""\bDEAD\b""").containsMatchIn(line)) {
                    CarPlaySystemUiServiceConnectionState.DEAD
                } else {
                    CarPlaySystemUiServiceConnectionState.HEALTHY
                }
            }
        }
        return CarPlaySystemUiServiceConnectionState.MISSING
    }

    internal fun resolveCarPlaySystemUiServiceConnectionStateForTest(serviceDump: String): String {
        return resolveCarPlaySystemUiServiceConnectionState(serviceDump).name
    }

    internal fun isCarPlaySystemUiIconRelevantForTest(
        hasVisualTask: Boolean,
        appPidAlive: Boolean,
        hostPidAlive: Boolean,
        serviceDump: String,
        linkStatus: Int?
    ): Boolean {
        return isCarPlaySystemUiIconRelevantForState(
            hasVisualTask = hasVisualTask,
            appPidAlive = appPidAlive,
            hostPidAlive = hostPidAlive,
            serviceDump = serviceDump,
            linkStatus = linkStatus
        )
    }

    private fun shouldRecoverCarPlaySystemUiIcon(
        connectionState: CarPlaySystemUiServiceConnectionState,
        carPlayRelevant: Boolean,
        speedKmh: Double?,
        missingBindSamples: Int,
        now: Long,
        lastRecoveryAt: Long
    ): Boolean {
        if (!carPlayRelevant) return false
        if (connectionState == CarPlaySystemUiServiceConnectionState.HEALTHY) return false
        if (speedKmh == null || speedKmh > CARPLAY_SYSTEM_UI_ICON_STATIONARY_SPEED_KMH) return false
        if (now - lastRecoveryAt < CARPLAY_SYSTEM_UI_ICON_RECOVERY_COOLDOWN_MS) return false
        if (
            connectionState == CarPlaySystemUiServiceConnectionState.MISSING &&
                missingBindSamples < CARPLAY_SYSTEM_UI_ICON_MISSING_BIND_SAMPLES
        ) {
            return false
        }
        return true
    }

    private fun shouldRefreshCarPlaySystemUiIconAfterUsbDisconnect(
        previousUsbConfigured: Boolean?,
        lastRelevantAt: Long,
        speedKmh: Double?,
        now: Long,
        lastDisconnectRefreshAt: Long
    ): Boolean {
        val hadRecentCarPlayContext =
            lastRelevantAt > 0L &&
                    now - lastRelevantAt <= CARPLAY_SYSTEM_UI_ICON_RECENT_RELEVANT_WINDOW_MS
        if (previousUsbConfigured != true && !hadRecentCarPlayContext) return false
        if (speedKmh == null || speedKmh > CARPLAY_SYSTEM_UI_ICON_STATIONARY_SPEED_KMH) return false
        if (now - lastDisconnectRefreshAt < CARPLAY_SYSTEM_UI_ICON_DISCONNECT_REFRESH_COOLDOWN_MS) {
            return false
        }
        return true
    }

    internal fun shouldRefreshCarPlaySystemUiIconAfterUsbDisconnectForTest(
        previousUsbConfigured: Boolean?,
        lastRelevantAt: Long,
        speedKmh: Double?,
        now: Long,
        lastDisconnectRefreshAt: Long
    ): Boolean {
        return shouldRefreshCarPlaySystemUiIconAfterUsbDisconnect(
            previousUsbConfigured = previousUsbConfigured,
            lastRelevantAt = lastRelevantAt,
            speedKmh = speedKmh,
            now = now,
            lastDisconnectRefreshAt = lastDisconnectRefreshAt
        )
    }

    internal fun shouldRecoverCarPlaySystemUiIconForTest(
        connectionStateName: String,
        carPlayRelevant: Boolean,
        speedKmh: Double?,
        missingBindSamples: Int,
        now: Long,
        lastRecoveryAt: Long
    ): Boolean {
        return shouldRecoverCarPlaySystemUiIcon(
            connectionState = CarPlaySystemUiServiceConnectionState.valueOf(connectionStateName),
            carPlayRelevant = carPlayRelevant,
            speedKmh = speedKmh,
            missingBindSamples = missingBindSamples,
            now = now,
            lastRecoveryAt = lastRecoveryAt
        )
    }

    fun startCarPlayMainDisplayBootAutostart() {
        scope.launch {
            val bootToken = currentBootToken()
            val prefs = getPrefs()
            if (prefs.getString(PREF_CARPLAY_BOOT_AUTOSTART_BOOT_TOKEN, "") == bootToken) {
                Log.w(TAG, "[BOOT_USB_CARPLAY_D0_AUTOSTART] Already evaluated for this boot")
                return@launch
            }

            repeat(CARPLAY_BOOT_AUTOSTART_ATTEMPTS) { attempt ->
                val preserveClusterTarget =
                    prefs.getInt(PREF_DESIRED_CARPLAY_DISPLAY_ID, -1) == 3
                val mainTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 0)
                if (mainTask != null) {
                    if (preserveClusterTarget) {
                        syncCarPlayDesiredDisplayProperty(
                            3,
                            "BOOT_USB_CARPLAY_D0_ALREADY_VISIBLE_KEEP_CLUSTER_TARGET"
                        )
                        Log.w(
                            TAG,
                            "[BOOT_USB_CARPLAY_D0_ALREADY_VISIBLE_KEEP_CLUSTER_TARGET] CarPlay is visible " +
                                    "on D0 as boot staging; preserving desired D3 target"
                        )
                    } else {
                        rememberCarPlayDisplayTarget(0, "BOOT_USB_CARPLAY_D0_ALREADY_VISIBLE")
                    }
                    prefs.edit()
                        .putString(PREF_CARPLAY_BOOT_AUTOSTART_BOOT_TOKEN, bootToken)
                        .apply()
                    Log.w(
                        TAG,
                        "[BOOT_USB_CARPLAY_D0_AUTOSTART] CarPlay already visible on D0 stack ${mainTask.stackId}"
                    )
                    return@launch
                }

                if (!isProjectionUsbConfigured()) {
                    Log.w(
                        TAG,
                        "[BOOT_USB_CARPLAY_D0_AUTOSTART] USB not configured on attempt ${attempt + 1}; waiting"
                    )
                    delay(CARPLAY_BOOT_AUTOSTART_INTERVAL_MS)
                    return@repeat
                }

                val clusterTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3)
                if (clusterTask != null) {
                    Log.w(
                        TAG,
                        "[BOOT_USB_CARPLAY_D0_AUTOSTART] CarPlay appeared on D3 after boot; moving to D0"
                    )
                } else {
                    Log.w(
                        TAG,
                        "[BOOT_USB_CARPLAY_D0_AUTOSTART] USB configured and no CarPlay visual task; starting on D0"
                    )
                }

                CarPlayDisplayOrchestrator.openOnMain(
                    getCarPlayConfigForDisplay(0),
                    "BOOT_USB_CARPLAY_D0_AUTOSTART",
                    rememberTarget = !preserveClusterTarget
                )

                val startedMainTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 0)
                if (startedMainTask != null) {
                    prefs.edit()
                        .putString(PREF_CARPLAY_BOOT_AUTOSTART_BOOT_TOKEN, bootToken)
                        .apply()
                    Log.w(
                        TAG,
                        "[BOOT_USB_CARPLAY_D0_AUTOSTART] CarPlay confirmed on D0 stack ${startedMainTask.stackId}"
                    )
                    return@launch
                }

                Log.w(
                    TAG,
                    "[BOOT_USB_CARPLAY_D0_AUTOSTART] Start attempt ${attempt + 1} did not create D0 task; retrying"
                )
                delay(CARPLAY_BOOT_AUTOSTART_INTERVAL_MS)
            }

            Log.w(TAG, "[BOOT_USB_CARPLAY_D0_AUTOSTART] Finished without confirmed D0 CarPlay task")
        }
    }

    private suspend fun enforceCarPlayClusterContractFromWatchdog() {
        reconcileCarPlayClusterTargetFromRealTask()
        if (!isCarPlayDesiredOnCluster()) return
        if (deferCarPlayClusterGuardDuringOrchestratedHandoff("CARPLAY_CLUSTER_WATCHDOG_PREPARING_D3")) return

        val usbConfigured = observeProjectionUsbConfigured("CARPLAY_CLUSTER_WATCHDOG_USB")
        val tasks = findAllTasksForPackage(CARPLAY_PACKAGE)
        if (tasks.isEmpty()) {
            carPlayMainDisplayReconnectSeenAt = 0L
            val now = System.currentTimeMillis()
            val bootGraceActive = now <= carPlayClusterTargetBootGraceUntil
            if (!usbConfigured && bootGraceActive) {
                Log.w(
                    TAG,
                    "[CARPLAY_CLUSTER_WATCHDOG_NO_TASK_BOOT_GRACE] Desired D3 target is pending " +
                            "during boot, but USB is not configured yet; keeping target without recreating visual task"
                )
                return
            }

            if (
                !bootGraceActive &&
                        !isMissingCarPlayVisualRestoreEligible("CARPLAY_CLUSTER_WATCHDOG_NO_TASK")
            ) {
                clearStaleCarPlayClusterTarget("CARPLAY_CLUSTER_WATCHDOG_NO_TASK_STALE_TARGET")
                return
            }
            if (now - lastCarPlayWatchdogRestoreAt < CARPLAY_WATCHDOG_RESTORE_COOLDOWN_MS) {
                Log.w(
                    TAG,
                    "[CARPLAY_CLUSTER_WATCHDOG] Skipping missing-task restore because cooldown is active"
                )
                return
            }
            lastCarPlayWatchdogRestoreAt = now
            Log.w(
                TAG,
                "[CARPLAY_CLUSTER_WATCHDOG_NO_TASK] Desired CarPlay target is cluster 3 but no visual task is active; recreating cluster visual task"
            )
            recreateMissingCarPlayVisualTaskOnCluster("CARPLAY_CLUSTER_WATCHDOG_NO_TASK")
            return
        }

        val clusterTask = tasks.firstOrNull { it.displayId == 3 }
        val mainTask = tasks.firstOrNull { it.displayId == 0 }

        if (clusterTask != null) {
            carPlayMainDisplayReconnectSeenAt = 0L
            markCarPlayClusterVisualSeen("CARPLAY_CLUSTER_WATCHDOG")
            if (getTopPackageOnDisplay(0) == App.getContext().packageName) {
                reassertCarPlayClusterSurfaceIfStale(
                    clusterTask,
                    "CARPLAY_CLUSTER_WATCHDOG_SELF_D0"
                )
            }
        }

        if (clusterTask != null && mainTask != null && clusterTask.stackId != mainTask.stackId) {
            cleanupMainDisplayCarPlayDuplicate(mainTask, clusterTask, "CARPLAY_CLUSTER_WATCHDOG")
            return
        }

        if (clusterTask == null && mainTask != null) {
            val now = System.currentTimeMillis()
            val firstMainDisplayObservation = carPlayMainDisplayReconnectSeenAt == 0L
            if (firstMainDisplayObservation) {
                carPlayMainDisplayReconnectSeenAt = now
                Log.w(
                    TAG,
                    "[CARPLAY_CLUSTER_WATCHDOG_DIRECT_D0_SEEN] CarPlay appeared on display 0 " +
                            "while desired target is cluster 3"
                )
            }

            val reconnectRestore =
                isWithinCarPlayUsbReconnectGrace(now, CARPLAY_RECONNECT_D0_OBSERVATION_WINDOW_MS)
            if (reconnectRestore) {
                val sinceConfigured = now - lastProjectionUsbConfiguredAt
                val sinceMainSeen = now - carPlayMainDisplayReconnectSeenAt
                Log.w(
                    TAG,
                    "[CARPLAY_CLUSTER_WATCHDOG_DIRECT_RECONNECT_STAGING] CarPlay is on display 0 " +
                            "after USB reconnect; deferring automatic D3 restore during reconnect grace " +
                            "(sinceConfigured=${sinceConfigured}ms, sinceMainSeen=${sinceMainSeen}ms)"
                )
                return
            }

            if (now - lastCarPlayWatchdogRestoreAt < CARPLAY_WATCHDOG_RESTORE_COOLDOWN_MS) {
                Log.w(
                    TAG,
                    "[CARPLAY_CLUSTER_WATCHDOG] Skipping direct restore because cooldown is active"
                )
                return
            }
            lastCarPlayWatchdogRestoreAt = now
            Log.w(
                TAG,
                "[CARPLAY_CLUSTER_WATCHDOG_DIRECT] CarPlay is on display 0 while desired target is cluster 3; " +
                        "restoring visual task to cluster without video broadcasts"
            )
            restoreCarPlayFromMainDisplayToCluster(
                mainTask,
                "CARPLAY_CLUSTER_WATCHDOG_DIRECT",
                postStartMode = CarPlayRestorePostStartMode.FULLSCREEN_ONLY
            )
        }
    }

    private fun observeProjectionUsbConfigured(reason: String): Boolean {
        val configured = isProjectionUsbConfigured()
        val previous = lastProjectionUsbConfiguredState
        val now = System.currentTimeMillis()

        if (previous != configured) {
            Log.w(
                TAG,
                "[$reason] Projection USB configured changed from ${previous ?: "UNKNOWN"} to $configured"
            )
            if (configured) {
                lastProjectionUsbConfiguredAt = now
            } else {
                lastProjectionUsbDisconnectedAt = now
                carPlayMainDisplayReconnectSeenAt = 0L
            }
            lastProjectionUsbConfiguredState = configured
        }

        return configured
    }

    private fun isWithinCarPlayUsbReconnectGrace(now: Long, graceMs: Long): Boolean {
        return shouldDeferCarPlayReconnectRestoreForTest(
            now = now,
            lastDisconnectedAt = lastProjectionUsbDisconnectedAt,
            lastConfiguredAt = lastProjectionUsbConfiguredAt,
            graceMs = graceMs
        )
    }

    internal fun isWithinCarPlayUsbReconnectGraceForTest(
        now: Long,
        lastDisconnectedAt: Long,
        lastConfiguredAt: Long,
        graceMs: Long
    ): Boolean {
        return lastDisconnectedAt > 0L &&
                lastConfiguredAt > lastDisconnectedAt &&
                now - lastConfiguredAt in 0..graceMs
    }

    internal fun shouldDeferCarPlayReconnectRestoreForTest(
        now: Long,
        lastDisconnectedAt: Long,
        lastConfiguredAt: Long,
        graceMs: Long
    ): Boolean {
        return isWithinCarPlayUsbReconnectGraceForTest(
            now = now,
            lastDisconnectedAt = lastDisconnectedAt,
            lastConfiguredAt = lastConfiguredAt,
            graceMs = graceMs
        )
    }

    internal fun shouldMoveMainCarPlayStackToClusterForTest(
        mainTaskDisplayId: Int,
        tasksInStack: Int,
        hasClusterTask: Boolean
    ): Boolean {
        return mainTaskDisplayId == 0 &&
                tasksInStack == 1 &&
                !hasClusterTask
    }

    private fun reconcileCarPlayClusterTargetFromRealTask() {
        if (CarPlayDisplayOrchestrator.isMainHandoffInProgress()) {
            Log.w(
                TAG,
                "[CARPLAY_CLUSTER_WATCHDOG_RECONCILE_TARGET] Skipping target sync while orchestrator is returning CarPlay to D0"
            )
            return
        }

        val clusterTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3) ?: return
        val mainTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 0)
        if (mainTask != null) return

        val desiredDisplay = getPrefs().getInt(PREF_DESIRED_CARPLAY_DISPLAY_ID, -1)
        if (desiredDisplay == 3) return

        Log.w(
            TAG,
            "[CARPLAY_CLUSTER_WATCHDOG_RECONCILE_TARGET] CarPlay is only on D3 " +
                    "(stack ${clusterTask.stackId}) but desired target is $desiredDisplay; syncing target to D3"
        )
        rememberCarPlayDisplayTarget(3, "CARPLAY_CLUSTER_WATCHDOG_RECONCILE_TARGET")
    }

    private fun markCarPlayClusterVisualSeen(reason: String) {
        lastCarPlayClusterVisualSeenAt = System.currentTimeMillis()
        Log.d(TAG, "[$reason] CarPlay visual confirmed on cluster; missing-task restore is armed")
    }

    private fun isMissingCarPlayVisualRestoreEligible(reason: String): Boolean {
        val lastSeenAt = lastCarPlayClusterVisualSeenAt
        val ageMs = if (lastSeenAt > 0L) System.currentTimeMillis() - lastSeenAt else Long.MAX_VALUE
        if (ageMs <= CARPLAY_MISSING_VISUAL_RESTORE_WINDOW_MS) {
            return true
        }

        Log.w(
            TAG,
            "[$reason] Skipping missing CarPlay visual restore because no recent cluster visual was observed; " +
                    "prevents post-reboot Impulse launch loop"
        )
        return false
    }

    private fun clearStaleCarPlayClusterTarget(reason: String) {
        if (getPrefs().getInt(PREF_DESIRED_CARPLAY_DISPLAY_ID, -1) != 3) return

        getPrefs().edit()
            .putInt(PREF_DESIRED_CARPLAY_DISPLAY_ID, 0)
            .apply()
        lastCarPlayClusterVisualSeenAt = 0L
        syncCarPlayDesiredDisplayProperty(0, reason)
        Log.w(
            TAG,
            "[$reason] Clearing stale desired CarPlay cluster target because no recent cluster visual/session was observed"
        )
    }

    private fun cleanupMainDisplayCarPlayDuplicate(
        mainTask: TaskInfo,
        clusterTask: TaskInfo,
        reason: String
    ) {
        val now = System.currentTimeMillis()
        if (now - lastCarPlayMainDuplicateCleanupAt < CARPLAY_MAIN_DUPLICATE_CLEANUP_COOLDOWN_MS) {
            Log.w(
                TAG,
                "[$reason] Skipping display 0 CarPlay duplicate cleanup because cooldown is active"
            )
            return
        }
        lastCarPlayMainDuplicateCleanupAt = now

        val tasksInStack = countTasksInStack(mainTask.stackId)
        if (tasksInStack > 1) {
            Log.w(
                TAG,
                "[$reason] Preserving mixed CarPlay stack ${mainTask.stackId} on display 0 " +
                        "($tasksInStack tasks); cluster stack ${clusterTask.stackId} remains active"
            )
            return
        }

        Log.w(
            TAG,
            "[$reason] Removing duplicate CarPlay stack ${mainTask.stackId} from display 0; " +
                    "cluster stack ${clusterTask.stackId} remains active"
        )
        sh("am stack remove ${mainTask.stackId}")
        notifyCarPlayDisplayHandoff(3, 0)
    }

    private fun startCarPlayActivity(
        displayId: Int,
        windowingMode: Int,
        escapedActivity: String,
        reason: String
    ): String {
        Log.w(TAG, "[$reason] Starting CarPlay on display $displayId")
        if (displayId == 0) {
            // `am start --display 0` can crash ActivityManager on this firmware, and
            // `am stack start 0` creates empty stacks before the Activity is ready.
            // A plain explicit start is the stable display-0 clean-start path; live
            // D3 -> D0 handoff is handled earlier with move-stack.
            return sh(
                "am start -f 0x14000000 -n $CARPLAY_PACKAGE/$escapedActivity"
            )
        }
        return sh(
            "am start --display $displayId --windowingMode $windowingMode " +
                    "--activity-multiple-task -f $CARPLAY_START_FLAGS " +
                    "-n $CARPLAY_PACKAGE/$escapedActivity"
        )
    }

    private fun wasTopMostInstanceReused(commandOutput: String): Boolean {
        val normalized = commandOutput.lowercase()
        return normalized.contains("activity not started") &&
                (
                        normalized.contains("currently running top-most instance") ||
                                normalized.contains("current task has been brought to the front") ||
                                normalized.contains("brought to the front")
                        )
    }

    private fun currentEpochSeconds(): Double = System.currentTimeMillis() / 1000.0

    private fun logcatEpoch(line: String): Double? {
        val token = line.trim().split(Regex("\\s+"), limit = 2).firstOrNull() ?: return null
        return token.toDoubleOrNull()
    }

    private fun inspectCarPlayHealthSince(sinceEpoch: Double, reason: String): CarPlayHealth {
        val logs = sh(
            "logcat -d -v threadtime,epoch -t 800 | grep -Ei " +
                    "'cpScreen|NdkMediaCodec|MediaCodec|jsurface|setSurface|isCarPlayConnected|mCarPlayConnected|notifyConnectedStatusChange|UsbCarplay|DeviceConnectedState|CarPlaySession|CarPlayIconManager|MC-driver|DcController' || true"
        )
        if (logs.isBlank()) {
            return CarPlayHealth(false, false, false, false, "")
        }

        val recentLines = logs.lines()
            .mapNotNull { line ->
                val ts = logcatEpoch(line) ?: return@mapNotNull null
                if (ts >= sinceEpoch - 1.0) line else null
            }

        if (recentLines.isEmpty()) {
            return CarPlayHealth(false, false, false, false, "")
        }

        // Require *sustained* evidence. Native camera/AVM/HVAC focus grabs routinely
        // produce 1-3 transient `jsurface NULL` + `dequeueInputBuffer invalid bufidx-1`
        // lines while the host renegotiates the route — the decoder recovers on its
        // own within a few frames. Treating those bursts as failure was triggering the
        // destructive visual-recovery path during normal camera/AVM events, producing
        // the user-visible cluster black-out and 3->0->3 bounce.
        // Thresholds derived from live capture during AVM transitions: healthy bursts
        // peaked at 3 codec lines / 2 surface lines; chronic failures sustained 5+ per
        // category.
        val codecIssueCount = recentLines.count { line ->
            line.contains("invalid bufidx", ignoreCase = true) ||
                    line.contains("sf error code: -38", ignoreCase = true) ||
                    line.contains("errcode=-19", ignoreCase = true) ||
                    ((line.contains("NdkMediaCodec", ignoreCase = true) ||
                            line.contains("MediaCodec", ignoreCase = true) ||
                            line.contains("cpScreen", ignoreCase = true)) &&
                            (line.contains("error", ignoreCase = true) ||
                                    line.contains("fail", ignoreCase = true) ||
                                    line.contains("invalid", ignoreCase = true)))
        }
        val nullSurfaceCount = recentLines.count { line ->
            line.contains("jsurface is NULL", ignoreCase = true) ||
                    (line.contains("setSurface", ignoreCase = true) &&
                            line.contains("NULL", ignoreCase = true))
        }
        val hasCodecIssue = codecIssueCount >= CARPLAY_HEALTH_CODEC_NOISE_THRESHOLD
        val hasNullSurface = nullSurfaceCount >= CARPLAY_HEALTH_SURFACE_NOISE_THRESHOLD
        val sessionDisconnected = recentLines.any { line ->
            line.contains("isCarPlayConnected=false", ignoreCase = true) ||
                    line.contains("mCarPlayConnected=== false", ignoreCase = true) ||
                    line.contains("mIsCarPlayConnected === false", ignoreCase = true) ||
                    line.contains("mDeviceConnectedState=0", ignoreCase = true)
        }

        val evidence = recentLines.takeLast(12).joinToString(" | ")
        val health = CarPlayHealth(
            hasIssue = hasCodecIssue || hasNullSurface || sessionDisconnected,
            hasCodecIssue = hasCodecIssue,
            hasNullSurface = hasNullSurface,
            sessionDisconnected = sessionDisconnected,
            evidence = evidence
        )

        if (health.hasIssue) {
            Log.e(
                TAG,
                "[$reason] CarPlay render/session issue detected: " +
                        "codec=${health.hasCodecIssue}, nullSurface=${health.hasNullSurface}, " +
                        "sessionDisconnected=${health.sessionDisconnected}, evidence=[${health.evidence}]"
            )
        }

        return health
    }

    private fun inspectRecentCarPlayHealth(
        sinceEpoch: Double,
        reason: String
    ): CarPlayHealth {
        val recentSince = maxOf(
            sinceEpoch + CARPLAY_HEALTH_TRANSITION_GRACE_SEC,
            currentEpochSeconds() - CARPLAY_HEALTH_RECENT_WINDOW_SEC
        )
        return inspectCarPlayHealthSince(recentSince, reason)
    }

    internal fun parseCarPlaySurfaceActiveBufferForTest(output: String): Pair<Int, Int>? {
        val match = Regex("""activeBuffer=\[\s*(\d+)\s*x\s*(\d+)""").find(output) ?: return null
        val width = match.groupValues[1].toIntOrNull() ?: return null
        val height = match.groupValues[2].toIntOrNull() ?: return null
        return width to height
    }

    internal fun parseCarPlaySurfaceViewActiveBufferForTest(
        output: String,
        surfaceLayerName: String = "SurfaceView - $CARPLAY_PACKAGE/$CARPLAY_ACTIVITY"
    ): Pair<Int, Int>? {
        return parseCarPlaySurfaceActiveBufferForTest(
            extractCarPlaySurfaceLayerBlockForTest(output, surfaceLayerName) ?: return null
        )
    }

    internal fun extractCarPlaySurfaceLayerBlockForTest(
        output: String,
        surfaceLayerName: String = "SurfaceView - $CARPLAY_PACKAGE/$CARPLAY_ACTIVITY"
    ): String? {
        val lines = output.lineSequence().toList()
        val start = lines.indexOfFirst { it.contains("+ BufferLayer ($surfaceLayerName") }
        if (start < 0) return null

        val block = mutableListOf<String>()
        for (i in start until lines.size) {
            val line = lines[i]
            if (i != start && line.startsWith("+ ")) break
            block.add(line)
        }
        return block.joinToString("\n")
    }

    internal fun isCarPlaySurfaceBufferStaleForTest(buffer: Pair<Int, Int>?): Boolean {
        if (buffer == null) return false
        return buffer.first <= 1 || buffer.second <= 1
    }

    private fun inspectCarPlayClusterSurfaceBuffer(reason: String): Pair<Int, Int>? {
        val surfacePrefix = "SurfaceView - $CARPLAY_PACKAGE/$CARPLAY_ACTIVITY"
        val output = sh(
            "dumpsys SurfaceFlinger | grep -A24 -F '$surfacePrefix' || true"
        )
        val surfaceBlock = extractCarPlaySurfaceLayerBlockForTest(output, surfacePrefix).orEmpty()
        val activeBufferLine = surfaceBlock
            .lineSequence()
            .firstOrNull { it.contains("activeBuffer=") }
            ?.trim()
            ?: "none"
        val buffer = parseCarPlaySurfaceViewActiveBufferForTest(output, surfacePrefix)
        Log.w(
            TAG,
            "[$reason] CarPlay D3 Surface activeBuffer=${buffer?.first}x${buffer?.second}; evidence=[$activeBufferLine]"
        )
        return buffer
    }

    private suspend fun reassertCarPlayClusterSurfaceIfStale(
        clusterTask: TaskInfo,
        reason: String
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCarPlaySurfaceProbeAt < CARPLAY_SURFACE_PROBE_COOLDOWN_MS) {
            Log.w(TAG, "[$reason] Skipping CarPlay D3 Surface probe because cooldown is active")
            return false
        }
        lastCarPlaySurfaceProbeAt = now

        val before = inspectCarPlayClusterSurfaceBuffer("${reason}_SURFACE_CHECK")
        if (!isCarPlaySurfaceBufferStaleForTest(before)) {
            Log.w(
                TAG,
                "[$reason] CarPlay live on D3 stack ${clusterTask.stackId}; Surface buffer is not stale, no reassert"
            )
            return false
        }

        if (now - lastCarPlaySurfaceReassertAt < CARPLAY_SURFACE_REASSERT_COOLDOWN_MS) {
            Log.w(TAG, "[$reason] Skipping CarPlay D3 Surface reassert because cooldown is active")
            return false
        }
        lastCarPlaySurfaceReassertAt = now

        Log.w(
            TAG,
            "[$reason] CarPlay D3 Surface is stale (${before?.first}x${before?.second}); " +
                    "reasserting existing D3 Activity without video-focus, resize or force-stop"
        )
        sh("setprop persist.haval.carplay.video.height 720")
        sendCarPlayRenderRefresh(3, "${reason}_RENDER_REFRESH")
        delay(250)
        startCarPlayActivity(
            displayId = 3,
            windowingMode = 5,
            escapedActivity = CARPLAY_ACTIVITY.replace("$", "\\$"),
            reason = "${reason}_START_EXISTING_D3"
        )
        delay(850)
        inspectCarPlayClusterSurfaceBuffer("${reason}_SURFACE_AFTER_REASSERT")
        return true
    }

    private suspend fun pulseCarPlayClusterVideoFocusIfSafe(
        clusterTask: TaskInfo,
        reason: String,
        probeSurfaceBeforeFocus: Boolean = true
    ): Boolean {
        val now = System.currentTimeMillis()
        val sinceHandoff = now - lastCarPlayClusterHandoffAt
        if (
            lastCarPlayClusterHandoffAt > 0L &&
                    sinceHandoff < CARPLAY_VIDEO_FOCUS_AFTER_D3_HANDOFF_GRACE_MS
        ) {
            Log.w(
                TAG,
                "[$reason] Skipping CarPlay video-focus pulse; D3 handoff grace active (${sinceHandoff}ms)"
            )
            return false
        }

        if (now - lastCarPlayVideoFocusPulseAt < CARPLAY_VIDEO_FOCUS_PULSE_COOLDOWN_MS) {
            Log.w(TAG, "[$reason] Skipping CarPlay video-focus pulse because cooldown is active")
            return false
        }

        if (probeSurfaceBeforeFocus) {
            val buffer = inspectCarPlayClusterSurfaceBuffer("${reason}_SURFACE_BEFORE_FOCUS")
            if (isCarPlaySurfaceBufferStaleForTest(buffer)) {
                Log.w(TAG, "[$reason] Surface is stale before focus pulse; using stale-surface reassert instead")
                return reassertCarPlayClusterSurfaceIfStale(clusterTask, "${reason}_STALE_SURFACE")
            }
        } else {
            Log.w(TAG, "[$reason] Skipping SurfaceFlinger probe for D0 window-focus lite pulse")
        }

        lastCarPlayVideoFocusPulseAt = now
        Log.w(
            TAG,
            "[$reason] CarPlay live on D3 stack ${clusterTask.stackId}; sending delayed video-focus pulse only"
        )
        sendCarPlayVideoFocusOnly(3, reason)
        return true
    }

    private fun recreateCarPlayVisualTask(
        displayId: Int,
        bounds: IntArray,
        windowingMode: Int,
        escapedActivity: String,
        reason: String
    ): TaskInfo? {
        Log.w(TAG, "[$reason] Recreating CarPlay visual activity without restarting host")
        configureCarPlayProjection("${reason}_PREPARE")
        val existingTargetTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)
        if (displayId == 0) {
            closeCarPlayVisualStacks("${reason}_CLEAN_START")
            Thread.sleep(250)
        } else {
            Log.w(
                TAG,
                "[$reason] Preserving existing CarPlay visual stack until display $displayId Surface is ready"
            )
        }

        startCarPlayActivity(displayId, windowingMode, escapedActivity, "${reason}_START")
        Thread.sleep(900)

        var recreatedTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)
        if (
                displayId != 0 &&
                        existingTargetTask != null &&
                        recreatedTask != null &&
                        recreatedTask.stackId == existingTargetTask.stackId
        ) {
            Log.w(
                    TAG,
                    "[$reason] CarPlay visual recovery reused existing target stack ${existingTargetTask.stackId}; removing stale visual stack and retrying clean start"
            )
            sh("am stack remove ${existingTargetTask.stackId}")
            Thread.sleep(300)
            startCarPlayActivity(
                    displayId,
                    windowingMode,
                    escapedActivity,
                    "${reason}_RETRY_AFTER_TARGET_REMOVE"
            )
            Thread.sleep(900)
            recreatedTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)
        }
        if (recreatedTask != null) {
            resizeAndFocusCarPlay(recreatedTask, displayId, bounds, "${reason}_POST_START")
            closeCarPlayVisualStacks("${reason}_POST_START_CLEAN_DUPLICATES", exceptStackId = recreatedTask.stackId)
            recreatedTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId) ?: recreatedTask
        } else {
            Log.e(TAG, "[$reason] CarPlay task was not found after visual recreation")
        }
        return recreatedTask
    }

    private fun recoverCarPlayRenderIfNeeded(
        displayId: Int,
        bounds: IntArray,
        windowingMode: Int,
        escapedActivity: String,
        sinceEpoch: Double,
        reason: String
    ): TaskInfo? {
        // Hard rule: if the CarPlay Activity is alive on the target display, never run the
        // destructive visual-recovery path. `jsurface is NULL` / `dequeueInputBuffer invalid
        // bufidx-1` are normal transient decoder noise during native camera/AVM/HVAC focus
        // grabs — the host renegotiates the route on its own within a couple of frames.
        // Recreating the stack at this moment was producing the very black-out + 3->0->3
        // bounce users reported (logs: AVM_PREVIEW_STATUS_1_..._RESTORE_CLUSTER_VISUAL_RECOVERY
        // tearing down stack 22, then `am start` returning "delivered to currently running
        // top-most instance", then `am stack remove` of the live stack). If the target Activity
        // is alive, keep the native video route untouched and bail out.
        val liveTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)
        if (liveTask != null) {
            Log.w(
                TAG,
                "[$reason] CarPlay Activity alive on display $displayId (stack ${liveTask.stackId}); " +
                        "verify-only, no render refresh or video-focus broadcast"
            )
            return liveTask
        }

        sendCarPlayRenderRefresh(displayId, "${reason}_RENDER_REFRESH_RETRY")
        sendCarPlayFocus(displayId, "${reason}_FOCUS_RETRY")
        Thread.sleep(650)

        var health = inspectRecentCarPlayHealth(sinceEpoch, "${reason}_HEALTH_CHECK_AFTER_RETRY")
        if (!health.hasIssue) return null

        // Visual recreation is only worth attempting when evidence points to decoder/surface failure.
        // Session disconnect alone is often a USB/transport state and should not trigger restarts.
        if (!health.hasCodecIssue && !health.hasNullSurface) {
            Log.w(
                TAG,
                "[$reason] Skipping CarPlay visual recovery: session disconnect without codec/surface failure evidence"
            )
            return null
        }

        // Even with codec/surface evidence, double-check the task hasn't reappeared on the
        // target display during the retry window — by the time we get here, the native UI
        // transition may have completed and the Activity is fine again.
        val taskAfterRetry = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)
        if (taskAfterRetry != null) {
            Log.w(
                TAG,
                "[$reason] CarPlay Activity reappeared on display $displayId (stack ${taskAfterRetry.stackId}) after retry; skipping visual recovery"
            )
            return taskAfterRetry
        }

        val visualRecoveryTask = recreateCarPlayVisualTask(
            displayId = displayId,
            bounds = bounds,
            windowingMode = windowingMode,
            escapedActivity = escapedActivity,
            reason = "${reason}_VISUAL_RECOVERY"
        )
        if (visualRecoveryTask != null) {
            Thread.sleep(850)
            health = inspectRecentCarPlayHealth(sinceEpoch, "${reason}_VISUAL_RECOVERY_HEALTH_CHECK")
            if (!health.hasIssue || (!health.hasCodecIssue && !health.hasNullSurface)) {
                Log.w(TAG, "[$reason] CarPlay visual recovery succeeded without host restart")
                return visualRecoveryTask
            }
        }

        Log.e(
            TAG,
            "[$reason] CarPlay decoder/surface errors persisted after visual recovery; " +
                    "skipping host restart during handoff to avoid USB/session reset"
        )
        return visualRecoveryTask
    }

    private fun closeCarPlayVisualStacks(reason: String, exceptStackId: Int? = null): Set<Int> {
        val tasks = findAllTasksForPackage(CARPLAY_PACKAGE)
        if (tasks.isEmpty()) return emptySet()

        val affectedDisplays = mutableSetOf<Int>()
        tasks
            .filter { it.stackId != exceptStackId }
            .map { it.stackId to it.displayId }
            .distinct()
            .forEach { (stackId, displayId) ->
                affectedDisplays.add(displayId)
                val tasksInStack = countTasksInStack(stackId)
                if (tasksInStack > 1) {
                    Log.w(
                        TAG,
                        "[$reason] Preserving mixed CarPlay stack $stackId on display $displayId ($tasksInStack tasks)"
                    )
                    return@forEach
                }
                Log.w(TAG, "[$reason] Removing CarPlay visual stack $stackId from display $displayId")
                sh("am stack remove $stackId")
            }
        return affectedDisplays
    }

    private fun notifyCarPlayDisplayHandoff(displayId: Int, previousDisplay: Int?) {
        if (displayId == 3) {
            lastCarPlayClusterHandoffAt = System.currentTimeMillis()
        }
        notifyDisplayStateChanged(displayId)
        if (previousDisplay != null && previousDisplay != displayId) {
            notifyDisplayStateChanged(previousDisplay)
        }
        if (displayId == 0) {
            notifyDisplayStateChanged(3)
            notifyBottomBarUpdate()
        }
    }

    private fun markCarPlayClusterHandoffStarted(reason: String) {
        lastCarPlayClusterHandoffAt = System.currentTimeMillis()
        Log.w(TAG, "[$reason] D3 handoff guard started; delaying CarPlay video-focus pulses")
    }

    private fun resizeAndFocusCarPlay(
        taskInfo: TaskInfo,
        displayId: Int,
        bounds: IntArray,
        reason: String
    ) {
        if (displayId == 0) {
            sh("am stack set-windowing-mode ${taskInfo.stackId} 1")
        }
        sh("am stack resize ${taskInfo.stackId} ${bounds[0]} ${bounds[1]} ${bounds[2]} ${bounds[3]}")
        sendCarPlayRenderRefresh(displayId, reason)
        Thread.sleep(120)
        sendCarPlayFocus(displayId, reason)
    }

    private fun ensureCarPlayFullscreenWithoutVideoBroadcasts(
        taskInfo: TaskInfo,
        displayId: Int,
        bounds: IntArray,
        reason: String
    ) {
        if (!taskInfo.bounds.contentEqualsOrNull(bounds)) {
            sh("am stack resize ${taskInfo.stackId} ${bounds[0]} ${bounds[1]} ${bounds[2]} ${bounds[3]}")
        } else {
            Log.w(
                TAG,
                "[$reason] CarPlay stack ${taskInfo.stackId} already fullscreen; skipping resize"
            )
        }
        Log.w(
            TAG,
            "[$reason] Keeping native video route untouched for display $displayId"
        )
    }

    private fun IntArray?.contentEqualsOrNull(other: IntArray): Boolean {
        return this != null && this.contentEquals(other)
    }

    private fun deferCarPlayClusterGuardDuringOrchestratedHandoff(reason: String): Boolean {
        if (!CarPlayDisplayOrchestrator.isClusterHandoffInProgress()) return false
        Log.w(
            TAG,
            "[$reason] Deferring CarPlay cluster guard because orchestrator is already preparing D3"
        )
        return true
    }

    private suspend fun restoreCarPlayFromMainDisplayToCluster(
        mainTask: TaskInfo,
        reason: String,
        postStartMode: CarPlayRestorePostStartMode = CarPlayRestorePostStartMode.FULLSCREEN_ONLY
    ): TaskInfo? {
        val escapedActivity = CARPLAY_ACTIVITY.replace("$", "\\$")
        val bounds = getCarPlayDisplayBounds(3)

        Log.w(
            TAG,
            "[$reason] Restoring CarPlay from display 0 stack ${mainTask.stackId} to cluster 3 without force-stop"
        )
        markCarPlayClusterHandoffStarted(reason)
        configureCarPlayProjection("${reason}_PREPARE")
        requestCarPlayUiIfLinkActivated("${reason}_PRE_START")

        // Defocus the display-0 CarPlay Activity first. If CarPlay remains the
        // top-most Activity on D0, ActivityManager often reuses that instance and
        // returns "currently running top-most instance" instead of creating D3.
        bringNonProjectionTaskOnDisplayToFront(0, "${reason}_DEFOCUS_DISPLAY0")
        delay(300)

        var startResult = startCarPlayActivity(
            displayId = 3,
            windowingMode = 5,
            escapedActivity = escapedActivity,
            reason = "${reason}_START_CLUSTER"
        )
        delay(900)

        var clusterTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3)
        if (clusterTask == null && wasTopMostInstanceReused(startResult)) {
            Log.w(
                TAG,
                "[$reason] ActivityManager reused display-0 CarPlay; defocusing once more and retrying cluster start"
            )
            bringNonProjectionTaskOnDisplayToFront(0, "${reason}_RETRY_DEFOCUS_DISPLAY0")
            delay(350)
            configureCarPlayProjection("${reason}_RETRY_PREPARE")
            startResult = startCarPlayActivity(
                displayId = 3,
                windowingMode = 5,
                escapedActivity = escapedActivity,
                reason = "${reason}_RETRY_START_CLUSTER"
            )
            delay(900)
            clusterTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3)
        }

        if (clusterTask == null) {
            Log.e(
                TAG,
                "[$reason] Failed to restore CarPlay to cluster 3; startResult=[$startResult]"
            )
            return null
        }

        when (postStartMode) {
            CarPlayRestorePostStartMode.FULL_RENDER_FOCUS -> {
                resizeAndFocusCarPlay(
                    clusterTask,
                    3,
                    bounds,
                    "${reason}_POST_START"
                )
            }
            CarPlayRestorePostStartMode.FULLSCREEN_ONLY -> {
                ensureCarPlayFullscreenWithoutVideoBroadcasts(
                    clusterTask,
                    3,
                    bounds,
                    "${reason}_POST_START_NO_VIDEO_BROADCAST"
                )
                delay(650)
                reassertCarPlayClusterSurfaceIfStale(
                    clusterTask,
                    "${reason}_POST_START_STALE_SURFACE_GUARD"
                )
            }
        }
        closeCarPlayVisualStacks(
            "${reason}_CLEAN_DISPLAY0_DUPLICATE",
            exceptStackId = clusterTask.stackId
        )
        notifyCarPlayDisplayHandoff(3, 0)
        return findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3) ?: clusterTask
    }

    internal fun isProjectionUsbStateReady(rawState: String): Boolean {
        return rawState
            .lineSequence()
            .map { it.trim().uppercase(Locale.US) }
            .any { state -> state == "CONFIGURED" || state == "CONNECTED" }
    }

    private fun isProjectionUsbConfigured(): Boolean {
        val state = sh("cat /sys/class/android_usb/android0/state 2>/dev/null || true").trim()
        return isProjectionUsbStateReady(state)
    }

    private suspend fun recreateMissingCarPlayVisualTaskOnCluster(reason: String): TaskInfo? {
        if (!isProjectionUsbConfigured()) {
            Log.w(TAG, "[$reason] Skipping CarPlay visual recreate because USB is not configured")
            return null
        }

        val escapedActivity = CARPLAY_ACTIVITY.replace("$", "\\$")
        val bounds = getCarPlayDisplayBounds(3)

        Log.w(TAG, "[$reason] Recreating missing CarPlay visual task on cluster 3 without force-stop")
        configureCarPlayProjection("${reason}_PREPARE")
        requestCarPlayUiIfLinkActivated("${reason}_PRE_START")
        bringNonProjectionTaskOnDisplayToFront(0, "${reason}_DEFOCUS_DISPLAY0")
        delay(300)

        startCarPlayActivity(
            displayId = 3,
            windowingMode = 5,
            escapedActivity = escapedActivity,
            reason = "${reason}_START_CLUSTER"
        )
        delay(900)

        val clusterTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3)
        if (clusterTask == null) {
            Log.e(TAG, "[$reason] Failed to recreate missing CarPlay visual task on cluster 3")
            return null
        }

        resizeAndFocusCarPlay(clusterTask, 3, bounds, "${reason}_POST_START")
        notifyCarPlayDisplayHandoff(3, null)
        return findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3) ?: clusterTask
    }

    private suspend fun moveMainCarPlayStackToClusterIfSafe(
        mainTask: TaskInfo,
        bounds: IntArray,
        reason: String
    ): TaskInfo? {
        val tasksInStack = countTasksInStack(mainTask.stackId)
        val existingClusterTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3)
        if (
            !shouldMoveMainCarPlayStackToClusterForTest(
                mainTaskDisplayId = mainTask.displayId,
                tasksInStack = tasksInStack,
                hasClusterTask = existingClusterTask != null
            )
        ) {
            Log.w(
                TAG,
                "[$reason] D0->D3 live stack move is not eligible " +
                        "(display=${mainTask.displayId}, tasksInStack=$tasksInStack, hasClusterTask=${existingClusterTask != null}); using Activity start path"
            )
            return null
        }

        Log.w(
            TAG,
            "[$reason] Moving clean live CarPlay stack ${mainTask.stackId} from D0 to D3 to preserve native Surface"
        )
        evictOtherAppsFromDisplay(3, CARPLAY_PACKAGE)
        BottomBarState.restoredApps.remove(CARPLAY_PACKAGE)
        configureCarPlayProjection("${reason}_MOVE_STACK_PREPARE")
        val moveResult = sh("am display move-stack ${mainTask.stackId} 3")
        if (moveResult.contains("Error", ignoreCase = true) || moveResult.contains("Exception", ignoreCase = true)) {
            Log.e(TAG, "[$reason] D0->D3 move-stack reported failure: $moveResult")
        }
        delay(700)

        var movedTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3)
        if (movedTask == null) {
            Log.e(TAG, "[$reason] D0->D3 move-stack did not leave CarPlay on D3; falling back to Activity start path")
            return null
        }

        ensureCarPlayFullscreenWithoutVideoBroadcasts(
            movedTask,
            3,
            bounds,
            "${reason}_MOVE_STACK_D3_NO_VIDEO_BROADCAST"
        )
        closeCarPlayVisualStacks("${reason}_MOVE_STACK_CLEAN_DUPLICATES", exceptStackId = movedTask.stackId)
        movedTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3) ?: movedTask
        delay(650)
        reassertCarPlayClusterSurfaceIfStale(
            movedTask,
            "${reason}_MOVE_STACK_STALE_SURFACE_GUARD"
        )
        notifyCarPlayDisplayHandoff(3, 0)
        Log.w(TAG, "[$reason] CarPlay live stack moved to D3 as stack ${movedTask.stackId}")
        return movedTask
    }

    private suspend fun restoreOrRefreshCarPlayClusterContract(
        reason: String,
        existingClusterAction: ExistingClusterCarPlayAction
    ) {
        if (!isCarPlayDesiredOnCluster()) return
        if (deferCarPlayClusterGuardDuringOrchestratedHandoff(reason)) return

        if (existingClusterAction == ExistingClusterCarPlayAction.FULL_REFRESH) {
            configureCarPlayProjection("${reason}_KEEPALIVE")
        }

        val clusterTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3)
        if (clusterTask != null) {
            markCarPlayClusterVisualSeen(reason)
            when (existingClusterAction) {
                ExistingClusterCarPlayAction.FULL_REFRESH -> {
                    resizeAndFocusCarPlay(
                        clusterTask,
                        3,
                        getCarPlayDisplayBounds(3),
                        "${reason}_REFRESH_CLUSTER"
                    )
                }
                ExistingClusterCarPlayAction.SURFACE_REASSERT_IF_STALE -> {
                    reassertCarPlayClusterSurfaceIfStale(clusterTask, reason)
                }
                ExistingClusterCarPlayAction.VIDEO_FOCUS_ONLY -> {
                    pulseCarPlayClusterVideoFocusIfSafe(clusterTask, reason)
                }
                ExistingClusterCarPlayAction.EXISTING_CLUSTER_VIDEO_FOCUS_ONLY -> {
                    pulseCarPlayClusterVideoFocusIfSafe(
                        clusterTask,
                        reason,
                        probeSurfaceBeforeFocus = false
                    )
                }
                ExistingClusterCarPlayAction.VERIFY_ONLY -> {
                    // Contract rule 21: when the real CarPlay task is on cluster 3, the
                    // guard must not steal the video route during the first D3 frame.
                    // D0->D3 startup stays verify-only because early focus pulses were
                    // producing the dirty/washed frame. Native D0 focus grabs can use
                    // VIDEO_FOCUS_ONLY later, after grace/cooldown checks.
                    Log.w(
                        TAG,
                        "[$reason] CarPlay live on display 3 (stack ${clusterTask.stackId}); guard verify-only, no broadcast"
                    )
                }
            }
            return
        }

        if (existingClusterAction == ExistingClusterCarPlayAction.EXISTING_CLUSTER_VIDEO_FOCUS_ONLY) {
            Log.w(
                TAG,
                "[$reason] D0 window-focus guard found no CarPlay task on display 3; " +
                        "skipping restore/recreate for generic window event"
            )
            return
        }

        val mainTask = waitForSustainedCarPlayOnMainDisplay(reason)
        if (mainTask == null) {
            if (findAllTasksForPackage(CARPLAY_PACKAGE).isEmpty()) {
                val missingVisualReason = "${reason}_RESTORE_MISSING_VISUAL"
                if (isMissingCarPlayVisualRestoreEligible(missingVisualReason)) {
                    recreateMissingCarPlayVisualTaskOnCluster(missingVisualReason)
                } else {
                    clearStaleCarPlayClusterTarget("${missingVisualReason}_STALE_TARGET")
                }
            }
            return
        }

        Log.w(
            TAG,
            "[$reason] Desired CarPlay target is cluster 3 but visual task sustained on display 0 " +
                    "(stack ${mainTask.stackId}); restoring to cluster"
        )
        restoreCarPlayFromMainDisplayToCluster(
            mainTask,
            "${reason}_RESTORE_CLUSTER",
            postStartMode = CarPlayRestorePostStartMode.FULLSCREEN_ONLY
        )
    }

    private suspend fun waitForSustainedCarPlayOnMainDisplay(reason: String): TaskInfo? {
        val deadline = System.currentTimeMillis() + CARPLAY_RESTORE_MAX_WAIT_MS
        var firstMainSeenAt: Long? = null
        var lastMainTask: TaskInfo? = null

        while (System.currentTimeMillis() <= deadline) {
            val clusterTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 3)
            if (clusterTask != null) {
                Log.w(
                    TAG,
                    "[$reason] CarPlay re-appeared on cluster 3 (stack ${clusterTask.stackId}); skipping restore"
                )
                return null
            }

            val mainTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 0)
            if (mainTask != null) {
                lastMainTask = mainTask
                val firstSeen = firstMainSeenAt ?: System.currentTimeMillis().also { firstMainSeenAt = it }
                val sustainedMs = System.currentTimeMillis() - firstSeen
                if (sustainedMs >= CARPLAY_RESTORE_REQUIRED_DISPLAY0_MS) {
                    return mainTask
                }
                Log.w(
                    TAG,
                    "[$reason] CarPlay visible on display 0 for ${sustainedMs}ms; waiting for sustained state before restore"
                )
            } else {
                if (firstMainSeenAt != null) {
                    Log.w(TAG, "[$reason] CarPlay left display 0 before restore threshold; resetting probe")
                }
                firstMainSeenAt = null
                lastMainTask = null
                Log.w(TAG, "[$reason] Desired CarPlay target is cluster 3 but no visual task is active; probing")
            }

            delay(CARPLAY_RESTORE_PROBE_INTERVAL_MS)
        }

        if (lastMainTask != null) {
            Log.w(
                TAG,
                "[$reason] CarPlay was seen on display 0 but did not remain stable long enough; skipping restore"
            )
        } else {
            Log.w(TAG, "[$reason] Desired CarPlay target is cluster 3 but no visual task became active")
        }
        return null
    }

    fun preserveCarPlayClusterContract(reason: String) {
        if (!isCarPlayDesiredOnCluster()) return

        val now = System.currentTimeMillis()
        if (now - lastCarPlayClusterGuardAt < CARPLAY_CLUSTER_GUARD_COOLDOWN_MS) {
            Log.w(TAG, "[$reason] Skipping CarPlay cluster guard because cooldown is active")
            return
        }
        lastCarPlayClusterGuardAt = now
        val action = if (shouldPulseCarPlayVideoFocusForContract(reason)) {
            ExistingClusterCarPlayAction.VIDEO_FOCUS_ONLY
        } else {
            ExistingClusterCarPlayAction.VERIFY_ONLY
        }

        scope.launch {
            // D0->D3 post-start stays verify-only. Native D0 focus grabs such as
            // AC/app/AVM can leave D3 black with a healthy buffer; those get a
            // delayed VIDEO_FOCUS_ONLY pulse after the D3 route has settled.
            delay(900)
            restoreOrRefreshCarPlayClusterContract(
                "${reason}_CONTRACT_PRIMARY",
                action
            )

            delay(1800)
            restoreOrRefreshCarPlayClusterContract(
                "${reason}_CONTRACT_VERIFY",
                action
            )
        }
    }

    private fun shouldPulseCarPlayVideoFocusForContract(reason: String): Boolean {
        return reason.startsWith("AVM_PREVIEW_STATUS_") ||
                reason.startsWith("HVAC_PANEL_DISPLAY_") ||
                reason.startsWith("SERVICE_OPEN_APP_") ||
                reason.startsWith("OPEN_AVM_ONCE_") ||
                reason.startsWith("LAUNCH_MAIN_AFTER_")
    }

    private fun resolveCarPlayWindowFocusGuardAction(
        packageName: String,
        selfPackageName: String
    ): ExistingClusterCarPlayAction? {
        if (isProjectionMirrorPackage(packageName)) return null
        return if (packageName == selfPackageName) {
            ExistingClusterCarPlayAction.SURFACE_REASSERT_IF_STALE
        } else {
            ExistingClusterCarPlayAction.EXISTING_CLUSTER_VIDEO_FOCUS_ONLY
        }
    }

    internal fun resolveCarPlayWindowFocusGuardActionForTest(
        packageName: String,
        selfPackageName: String
    ): String? {
        return resolveCarPlayWindowFocusGuardAction(packageName, selfPackageName)?.name
    }

    private fun isAndroidAutoClusterPreservationEligible(): Boolean {
        val activeProjection = resolveActiveProjectionPackageForDisplay(3)
        if (activeProjection == CARPLAY_PACKAGE) return false
        if (!isAndroidAutoProjectionSessionReady("AA_CLUSTER_PRESERVATION_ELIGIBLE")) return false
        return isAndroidAutoDesiredOnCluster() || activeProjection == ANDROID_AUTO_PACKAGE
    }

    private fun resolveAndroidAutoWindowFocusGuardAction(
        packageName: String,
        selfPackageName: String
    ): ExistingClusterAndroidAutoAction? {
        if (isProjectionMirrorPackage(packageName)) return null
        if (packageName == selfPackageName) return ExistingClusterAndroidAutoAction.VERIFY_ONLY
        if (isNativeDisplayZeroPanelPackage(packageName)) return ExistingClusterAndroidAutoAction.VERIFY_ONLY
        return ExistingClusterAndroidAutoAction.FULLSCREEN_AND_FOCUS
    }

    private fun isNativeDisplayZeroPanelPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase(Locale.US)
        return normalized == "com.beantechs.hvac" ||
                normalized == "com.beantechs.launcher" ||
                normalized == "com.beantechs.applist" ||
                normalized.contains("hvac") ||
                normalized.contains("avm") ||
                normalized.contains("camera") ||
                normalized.contains("backcamera")
    }

    internal fun resolveAndroidAutoWindowFocusGuardActionForTest(
        packageName: String,
        selfPackageName: String
    ): String? {
        return resolveAndroidAutoWindowFocusGuardAction(packageName, selfPackageName)?.name
    }

    fun preserveAndroidAutoClusterContract(reason: String) {
        preserveAndroidAutoClusterContract(
            reason = reason,
            action = ExistingClusterAndroidAutoAction.FULLSCREEN_AND_FOCUS,
            primaryDelayMs = 450L,
            verifyDelayMs = 950L
        )
    }

    fun preserveAndroidAutoNativePanelContract(reason: String) {
        preserveAndroidAutoClusterContract(
            reason = reason,
            action = ExistingClusterAndroidAutoAction.VERIFY_ONLY,
            primaryDelayMs = 1_600L,
            verifyDelayMs = 2_200L
        )
    }

    fun pulseAndroidAutoFocusAfterNativePanelExit(reason: String) {
        if (!isAndroidAutoClusterPreservationEligible()) return

        val now = System.currentTimeMillis()
        if (now - lastAndroidAutoPostNativePanelFocusAt < ANDROID_AUTO_POST_NATIVE_PANEL_FOCUS_COOLDOWN_MS) {
            Log.w(TAG, "[$reason] Skipping Android Auto post-native-panel focus pulse because cooldown is active")
            return
        }
        lastAndroidAutoPostNativePanelFocusAt = now

        scope.launch {
            pulseAndroidAutoFocusIfLiveOnCluster("${reason}_AA_POST_NATIVE_PANEL_FOCUS_PRIMARY")

            delay(180)
            pulseAndroidAutoFocusIfLiveOnCluster("${reason}_AA_POST_NATIVE_PANEL_FOCUS_FAST_VERIFY")

            delay(420)
            pulseAndroidAutoFocusIfLiveOnCluster("${reason}_AA_POST_NATIVE_PANEL_FOCUS_LATE_VERIFY")
        }
    }

    fun pulseAndroidAutoFocusDuringNativePanel(reason: String) {
        if (!isAndroidAutoClusterPreservationEligible()) return

        val now = System.currentTimeMillis()
        if (now - lastAndroidAutoNativePanelActiveFocusAt < ANDROID_AUTO_NATIVE_PANEL_ACTIVE_FOCUS_COOLDOWN_MS) {
            Log.w(TAG, "[$reason] Skipping Android Auto native-panel active focus pulse because cooldown is active")
            return
        }
        lastAndroidAutoNativePanelActiveFocusAt = now

        scope.launch {
            delay(260)
            pulseAndroidAutoFocusIfLiveOnCluster("${reason}_AA_NATIVE_PANEL_ACTIVE_FOCUS_PRIMARY")

            delay(620)
            pulseAndroidAutoFocusIfLiveOnCluster("${reason}_AA_NATIVE_PANEL_ACTIVE_FOCUS_VERIFY")
        }
    }

    private suspend fun pulseAndroidAutoFocusIfLiveOnCluster(reason: String): Boolean {
        val activeProjection = resolveActiveProjectionPackageForDisplay(3)
        if (activeProjection == CARPLAY_PACKAGE) {
            Log.w(TAG, "[$reason] Skipping Android Auto focus pulse because CarPlay is active on cluster 3")
            return false
        }

        if (!isAndroidAutoProjectionSessionReady("${reason}_SESSION_READY")) {
            Log.w(TAG, "[$reason] Skipping Android Auto focus pulse because USB/link is not active")
            cleanupStaleAndroidAutoVisualStacksIfDisconnected("${reason}_SESSION_NOT_READY")
            return false
        }

        val clusterTask = findTaskForPackageOnDisplay(ANDROID_AUTO_PACKAGE, 3)
        if (clusterTask == null) {
            Log.w(TAG, "[$reason] Skipping Android Auto focus pulse because no live D3 task was found")
            return false
        }

        rememberAndroidAutoDisplayTarget(3, reason)
        Log.w(TAG, "[$reason] Android Auto live on D3 stack ${clusterTask.stackId}; sending focus pulse only")
        sendAndroidAutoFocus(3, reason)
        return true
    }

    private fun preserveAndroidAutoClusterContract(
        reason: String,
        action: ExistingClusterAndroidAutoAction,
        primaryDelayMs: Long,
        verifyDelayMs: Long
    ) {
        if (!isAndroidAutoClusterPreservationEligible()) return

        val now = System.currentTimeMillis()
        if (now - lastAndroidAutoClusterGuardAt < ANDROID_AUTO_CLUSTER_GUARD_COOLDOWN_MS) {
            Log.w(TAG, "[$reason] Skipping Android Auto cluster guard because cooldown is active")
            return
        }
        lastAndroidAutoClusterGuardAt = now

        scope.launch {
            delay(primaryDelayMs)
            restoreOrRefreshAndroidAutoClusterContract("${reason}_AA_CONTRACT_PRIMARY", action)

            delay(verifyDelayMs)
            restoreOrRefreshAndroidAutoClusterContract("${reason}_AA_CONTRACT_VERIFY", action)
        }
    }

    private fun preserveAndroidAutoClusterContractAfterWindowChange(packageName: String) {
        if (!isAndroidAutoClusterPreservationEligible()) return

        val selfPackageName = App.getContext().packageName
        val action = resolveAndroidAutoWindowFocusGuardAction(packageName, selfPackageName) ?: return

        val now = System.currentTimeMillis()
        if (now - lastAndroidAutoWindowFocusGuardAt < ANDROID_AUTO_WINDOW_FOCUS_GUARD_COOLDOWN_MS) {
            Log.w(TAG, "[WINDOW_CHANGE_$packageName] Skipping Android Auto window guard because cooldown is active")
            return
        }
        lastAndroidAutoWindowFocusGuardAt = now

        val safePackage = packageName.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)
        val primaryDelayMs = if (action == ExistingClusterAndroidAutoAction.VERIFY_ONLY) 1_600L else 500L
        val verifyDelayMs = if (action == ExistingClusterAndroidAutoAction.VERIFY_ONLY) 2_200L else 1_200L
        scope.launch {
            delay(primaryDelayMs)
            restoreOrRefreshAndroidAutoClusterContract(
                "WINDOW_CHANGE_${safePackage}_AA_CONTRACT_PRIMARY",
                action
            )

            delay(verifyDelayMs)
            restoreOrRefreshAndroidAutoClusterContract(
                "WINDOW_CHANGE_${safePackage}_AA_CONTRACT_VERIFY",
                action
            )
        }
    }

    private suspend fun restoreOrRefreshAndroidAutoClusterContract(
        reason: String,
        action: ExistingClusterAndroidAutoAction
    ) {
        val activeProjection = resolveActiveProjectionPackageForDisplay(3)
        if (activeProjection == CARPLAY_PACKAGE) {
            Log.w(TAG, "[$reason] Skipping Android Auto guard because CarPlay is active on cluster 3")
            return
        }

        val clusterTask = findTaskForPackageOnDisplay(ANDROID_AUTO_PACKAGE, 3)
        if (clusterTask != null) {
            rememberAndroidAutoDisplayTarget(3, reason)
            Log.w(
                TAG,
                "[$reason] Android Auto live on D3 stack ${clusterTask.stackId}; action=$action"
            )
            if (action == ExistingClusterAndroidAutoAction.FULLSCREEN_AND_FOCUS) {
                ensureAndroidAutoFullscreenAndFocus(clusterTask, 3, reason)
                closeAndroidAutoVisualStacks("${reason}_CLEAN_DUPLICATES", exceptStackId = clusterTask.stackId)
                notifyAndroidAutoDisplayHandoff(3, clusterTask.displayId)
            }
            return
        }

        if (!isAndroidAutoDesiredOnCluster()) {
            Log.w(TAG, "[$reason] No Android Auto D3 task and D3 is not the desired AA target")
            return
        }

        val currentTask = findTaskForPackage(ANDROID_AUTO_PACKAGE)
        if (currentTask != null) {
            Log.w(
                TAG,
                "[$reason] Android Auto is on display ${currentTask.displayId}; restoring visual task to cluster 3"
            )
        } else {
            Log.w(TAG, "[$reason] Desired Android Auto target is cluster 3 but no visual task is active; recreating")
        }

        startAndroidAutoOnDisplay(
            getAndroidAutoConfigForDisplay(3),
            "${reason}_RESTORE_CLUSTER"
        )
    }

    private fun preserveCarPlayClusterContractAfterWindowChange(packageName: String) {
        if (!isCarPlayDesiredOnCluster()) return

        val selfPackageName = App.getContext().packageName
        val action = resolveCarPlayWindowFocusGuardAction(packageName, selfPackageName) ?: return
        val now = System.currentTimeMillis()
        if (now - lastCarPlayWindowFocusGuardAt < CARPLAY_WINDOW_FOCUS_GUARD_COOLDOWN_MS) {
            Log.w(TAG, "[WINDOW_CHANGE_$packageName] Skipping CarPlay window guard because cooldown is active")
            return
        }
        lastCarPlayWindowFocusGuardAt = now

        val isSelfPackage = packageName == selfPackageName
        val safePackage = packageName.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)
        scope.launch {
            // Native D0 window-focus events can leave the D3 video black even with a
            // healthy activeBuffer. Use a delayed lite focus pulse there, while the
            // Haval app self-focus path still only reasserts if the Surface is stale.
            delay(if (isSelfPackage) 650 else 1100)
            restoreOrRefreshCarPlayClusterContract(
                "WINDOW_CHANGE_${safePackage}_CONTRACT_PRIMARY",
                action
            )

            delay(if (isSelfPackage) 1500 else 2200)
            restoreOrRefreshCarPlayClusterContract(
                "WINDOW_CHANGE_${safePackage}_CONTRACT_VERIFY",
                action
            )
        }
    }

    private fun isAndroidAutoMediaControlKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MUTE ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
                keyCode == ANDROID_AUTO_OEM_INPUT_MEDIA_PLAY_PAUSE
    }

    private fun isAndroidAutoSteeringSkipKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
    }

    private fun isAndroidAutoSteeringPlaybackKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                keyCode == ANDROID_AUTO_OEM_INPUT_MEDIA_PLAY_PAUSE
    }

    private fun isAndroidAutoSteeringMuteKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MUTE ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
    }

    private fun isAndroidAutoSteeringToggleKey(keyCode: Int): Boolean {
        return isAndroidAutoSteeringPlaybackKey(keyCode) ||
                isAndroidAutoSteeringMuteKey(keyCode)
    }

    private fun isAndroidAutoMediaControlAction(action: Int): Boolean {
        return action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP
    }

    private fun isAndroidAutoMediaInputProbeKey(keyCode: Int): Boolean {
        return isAndroidAutoMediaControlKey(keyCode)
    }

    private fun mapAndroidAutoClusterMediaCommandToKeyCode(command: Int): Int? {
        return when (command) {
            ANDROID_AUTO_CLUSTER_MEDIA_COMMAND_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            ANDROID_AUTO_CLUSTER_MEDIA_COMMAND_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> null
        }
    }

    private fun mapAndroidAutoMediaKeyToAapHardkeyOrdinal(keyCode: Int): Int? {
        return when (keyCode) {
            ANDROID_AUTO_OEM_INPUT_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> ANDROID_AUTO_AAP_HARDKEY_MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY -> ANDROID_AUTO_AAP_HARDKEY_MEDIA_PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> ANDROID_AUTO_AAP_HARDKEY_MEDIA_PAUSE
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> ANDROID_AUTO_AAP_HARDKEY_MEDIA_PREVIOUS
            KeyEvent.KEYCODE_MEDIA_NEXT -> ANDROID_AUTO_AAP_HARDKEY_MEDIA_NEXT
            else -> null
        }
    }

    private fun mapAndroidAutoMediaKeyToOemInputKeyCode(keyCode: Int): Int? {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> ANDROID_AUTO_OEM_INPUT_MEDIA_PREVIOUS
            KeyEvent.KEYCODE_MEDIA_NEXT -> ANDROID_AUTO_OEM_INPUT_MEDIA_NEXT
            ANDROID_AUTO_OEM_INPUT_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> ANDROID_AUTO_OEM_INPUT_MEDIA_PLAY_PAUSE
            else -> null
        }
    }

    private fun shouldUseAndroidAutoOemOnlyMediaRoute(keyCode: Int): Boolean {
        if (!ANDROID_AUTO_OEM_INPUT_MEDIA_FALLBACK_ENABLED) return false
        if (!ANDROID_AUTO_PREVIOUS_NEXT_OEM_ONLY_ROUTE_ENABLED) return false
        return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
    }

    private fun shouldUseAndroidAutoSteeringAppCommandRoute(
        keyCode: Int,
        action: Int,
        source: AndroidAutoMediaKeySource
    ): Boolean {
        if (source != AndroidAutoMediaKeySource.STEERING_INPUT) return false
        if (action != KeyEvent.ACTION_UP) return false
        return isAndroidAutoSteeringSkipKey(keyCode)
    }

    private fun shouldSuppressAndroidAutoSteeringMediaInjection(
        source: AndroidAutoMediaKeySource
    ): Boolean {
        return source == AndroidAutoMediaKeySource.STEERING_INPUT
    }

    private fun shouldSkipAndroidAutoOemInputFallbackEcho(
        keyCode: Int,
        action: Int,
        now: Long,
        echoKeyCode: Int,
        echoBlockUntil: Long
    ): Boolean {
        if (!isAndroidAutoMediaControlAction(action)) return false
        return echoKeyCode == keyCode && now <= echoBlockUntil
    }

    private fun markAndroidAutoOemInputFallbackEchoBlock(keyCode: Int, reason: String) {
        androidAutoOemInputEchoKeyCode = keyCode
        androidAutoOemInputEchoBlockUntil = System.currentTimeMillis() + ANDROID_AUTO_OEM_INPUT_ECHO_BLOCK_MS
        Log.w(
            TAG,
            "[$reason] Blocking OEM media fallback echo for keyCode=$keyCode " +
                    "until=$androidAutoOemInputEchoBlockUntil"
        )
    }

    private fun resolveAndroidAutoPlaybackTransactionForState(
        keyCode: Int,
        musicStatusBefore: Int?,
        musicStatusAfterNative: Int?,
        progressBefore: Int?,
        progressAfterNative: Int?,
        mediaIsPlaying: Boolean
    ): Int? {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PAUSE -> ANDROID_AUTO_LINK_COMMAND_PAUSE_TRANSACTION
            KeyEvent.KEYCODE_MEDIA_PLAY -> ANDROID_AUTO_LINK_COMMAND_PLAY_TRANSACTION
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                when (musicStatusAfterNative) {
                    ANDROID_AUTO_MUSIC_STATUS_PAUSED -> {
                        if (
                            musicStatusBefore == ANDROID_AUTO_MUSIC_STATUS_PAUSED &&
                                !mediaIsPlaying
                        ) {
                            return ANDROID_AUTO_LINK_COMMAND_PLAY_TRANSACTION
                        }
                        return null
                    }
                    ANDROID_AUTO_MUSIC_STATUS_PLAYING -> {
                        if (musicStatusBefore == ANDROID_AUTO_MUSIC_STATUS_PAUSED) {
                            return null
                        }
                    }
                }

                when (musicStatusAfterNative ?: musicStatusBefore) {
                    ANDROID_AUTO_MUSIC_STATUS_PLAYING ->
                        ANDROID_AUTO_LINK_COMMAND_PAUSE_TRANSACTION
                    ANDROID_AUTO_MUSIC_STATUS_PAUSED ->
                        if (mediaIsPlaying) {
                            null
                        } else {
                            ANDROID_AUTO_LINK_COMMAND_PLAY_TRANSACTION
                        }
                    else ->
                        if (
                            mediaIsPlaying ||
                                isAndroidAutoProgressAdvancing(progressBefore, progressAfterNative) ||
                                hasAndroidAutoPositiveProgress(progressBefore, progressAfterNative)
                        ) {
                            ANDROID_AUTO_LINK_COMMAND_PAUSE_TRANSACTION
                        } else {
                            ANDROID_AUTO_LINK_COMMAND_PLAY_TRANSACTION
                        }
                }
            }
            else -> null
        }
    }

    private fun isAndroidAutoProgressAdvancing(progressBefore: Int?, progressAfter: Int?): Boolean {
        if (progressBefore == null || progressAfter == null) return false
        return progressAfter > progressBefore
    }

    private fun hasAndroidAutoPositiveProgress(progressBefore: Int?, progressAfter: Int?): Boolean {
        return (progressBefore ?: 0) > 0 || (progressAfter ?: 0) > 0
    }

    private fun isAndroidAutoActiveForMediaControl(): Boolean {
        val activeProjection = resolveActiveProjectionPackageForDisplay(3)
        if (isAndroidAutoActiveForMediaControlForState(
            activeProjectionPackage = activeProjection,
            mediaPackageName = BottomBarState.mediaPackageName,
            activeClusterProjectionPackage = BottomBarState.activeClusterProjectionPackage,
            androidAutoLinkStatus = null,
            androidAutoTaskPresent = false,
            androidAutoSessionReady = isAndroidAutoProjectionSessionReady("AA_MEDIA_ACTIVE_STATE")
        )) {
            return true
        }

        val linkStatus = readAndroidAutoLinkStatus("AA_MEDIA_ACTIVE")
        if (isAndroidAutoActiveForMediaControlForState(
            activeProjectionPackage = null,
            mediaPackageName = null,
            activeClusterProjectionPackage = null,
            androidAutoLinkStatus = linkStatus,
            androidAutoTaskPresent = false
        )) {
            return true
        }

        return isAndroidAutoActiveForMediaControlForState(
            activeProjectionPackage = null,
            mediaPackageName = null,
            activeClusterProjectionPackage = null,
            androidAutoLinkStatus = null,
            androidAutoTaskPresent = findTaskMatching { packageName, _ ->
                isAndroidAutoLikePackage(packageName)
            } != null,
            androidAutoSessionReady = isAndroidAutoProjectionSessionReady("AA_MEDIA_ACTIVE_TASK")
        )
    }

    fun shouldLogAndroidAutoMediaInputProbe(keyCode: Int): Boolean {
        return isAndroidAutoMediaInputProbeKey(keyCode)
    }

    fun shouldLogAndroidAutoClusterCallbackProbe(msgId: Int): Boolean {
        return msgId in 130..140 && isAndroidAutoActiveForMediaControl()
    }

    fun handleAndroidAutoClusterMediaCommand(command: Int): Boolean {
        val keyCode = mapAndroidAutoClusterMediaCommandToKeyCode(command) ?: return false
        if (!shouldConsumeAndroidAutoClusterMediaCallback(
                isAndroidAutoActive = isAndroidAutoActiveForMediaControl(),
                command = command
            )
        ) {
            return false
        }
        Log.w(
            TAG,
            "[AA_CLUSTER_MEDIA_CALLBACK_$command] Ignoring ambiguous cluster media callback " +
                "keyCode=$keyCode while Android Auto is active"
        )
        return true
    }

    private fun shouldConsumeAndroidAutoClusterMediaCallback(
        isAndroidAutoActive: Boolean,
        command: Int
    ): Boolean {
        return isAndroidAutoActive && mapAndroidAutoClusterMediaCommandToKeyCode(command) != null
    }

    fun handleAndroidAutoSteeringMediaKey(keyCode: Int, action: Int): Boolean {
        return handleAndroidAutoMediaControlKey(
            keyCode = keyCode,
            action = action,
            source = AndroidAutoMediaKeySource.STEERING_INPUT
        )
    }

    fun shouldConsumeAndroidAutoAccessibilityMediaKey(keyCode: Int, action: Int): Boolean {
        val now = System.currentTimeMillis()
        val active = isAndroidAutoActiveForMediaControl()
        val shouldConsume = shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
            isAndroidAutoActive = active,
            keyCode = keyCode,
            action = action,
            now = now,
            lastKeyCode = lastAndroidAutoAccessibilityToggleKeyCode,
            lastHandledAt = lastAndroidAutoAccessibilityToggleKeyAt,
            blockedKeyCode = blockedAndroidAutoAccessibilityToggleKeyCode,
            cooldownMs = ANDROID_AUTO_TOGGLE_MEDIA_KEY_COOLDOWN_MS
        )

        if (active && isAndroidAutoSteeringToggleKey(keyCode) && isAndroidAutoMediaControlAction(action)) {
            if (shouldConsume) {
                if (action == KeyEvent.ACTION_DOWN) {
                    blockedAndroidAutoAccessibilityToggleKeyCode = keyCode
                } else if (blockedAndroidAutoAccessibilityToggleKeyCode == keyCode) {
                    blockedAndroidAutoAccessibilityToggleKeyCode = 0
                }
                Log.w(
                    TAG,
                    "[AA_ACCESSIBILITY_MEDIA_${keyCode}_ACTION_${action}] Consuming Android Auto toggle key"
                )
            } else {
                if (action == KeyEvent.ACTION_UP) {
                    lastAndroidAutoAccessibilityToggleKeyCode = keyCode
                    lastAndroidAutoAccessibilityToggleKeyAt = now
                    if (blockedAndroidAutoAccessibilityToggleKeyCode == keyCode) {
                        blockedAndroidAutoAccessibilityToggleKeyCode = 0
                    }
                }
                Log.w(
                    TAG,
                    "[AA_ACCESSIBILITY_MEDIA_${keyCode}_ACTION_${action}] Passing Android Auto toggle key"
                )
            }
        }

        return shouldConsume
    }

    private fun handleAndroidAutoMediaControlKey(
        keyCode: Int,
        action: Int,
        source: AndroidAutoMediaKeySource
    ): Boolean {
        if (!isAndroidAutoMediaControlAction(action) || !isAndroidAutoMediaControlKey(keyCode)) {
            return false
        }

        val now = System.currentTimeMillis()
        val active = isAndroidAutoActiveForMediaControl()
        if (active && shouldSkipAndroidAutoOemInputFallbackEcho(
                keyCode = keyCode,
                action = action,
                now = now,
                echoKeyCode = androidAutoOemInputEchoKeyCode,
                echoBlockUntil = androidAutoOemInputEchoBlockUntil
            )
        ) {
            Log.w(TAG, "[AA_STEERING_MEDIA_$keyCode] Skipping OEM input fallback echo")
            return true
        }

        if (!active) {
            return false
        }

        val useAppCommandRoute = shouldUseAndroidAutoSteeringAppCommandRoute(keyCode, action, source)
        if (shouldSuppressAndroidAutoSteeringMediaInjection(source) && !useAppCommandRoute) {
            Log.w(
                TAG,
                "[AA_STEERING_MEDIA_${keyCode}_ACTION_${action}] Android Auto physical media key observed; " +
                    "relying on native headunit route only"
            )
            return true
        }

        val shouldHandle = shouldHandleAndroidAutoMediaControlKeyForTest(
            isAndroidAutoClusterActive = true,
            keyCode = keyCode,
            action = action,
            now = now,
            lastKeyCode = lastAndroidAutoMediaKeyCode,
            lastHandledAt = lastAndroidAutoMediaKeyAt,
            cooldownMs = ANDROID_AUTO_MEDIA_KEY_COOLDOWN_MS
        )

        if (!shouldHandle) {
            Log.w(TAG, "[AA_STEERING_MEDIA_$keyCode] Skipping duplicate Android Auto media key")
            return true
        }

        lastAndroidAutoMediaKeyCode = keyCode
        lastAndroidAutoMediaKeyAt = now

        val reasonPrefix = "AA_STEERING_MEDIA_${keyCode}_ACTION_${action}"
        if (useAppCommandRoute) {
            Log.w(
                TAG,
                "[$reasonPrefix] Android Auto physical media key observed; " +
                        "using app command route"
            )
            scope.launch {
                val sent = sendAndroidAutoSteeringAppMediaCommand(
                    keyCode = keyCode,
                    reason = "${reasonPrefix}_STEERING_APP"
                )
                Log.w(TAG, "[$reasonPrefix] Android Auto steering app media command sent=$sent")
            }
            return true
        }

        scope.launch {
            sendAndroidAutoFocus(3, "${reasonPrefix}_FOCUS_BEFORE")
            delay(80)
            val sentNative = sendAndroidAutoNativeMediaKeySequence(
                keyCode,
                "${reasonPrefix}_NATIVE"
            )
            if (!sentNative) {
                Log.w(
                    TAG,
                    "[$reasonPrefix] Android Auto native media key unavailable; falling back to input keyevent"
                )
                sh("input keyevent $keyCode")
            }
            delay(220)
            sendAndroidAutoFocus(3, "${reasonPrefix}_FOCUS_AFTER")
        }
        return true
    }

    private fun isAndroidAutoActiveForMediaControlForState(
        activeProjectionPackage: String?,
        mediaPackageName: String?,
        activeClusterProjectionPackage: String?,
        androidAutoLinkStatus: Int?,
        androidAutoTaskPresent: Boolean,
        androidAutoSessionReady: Boolean = true
    ): Boolean {
        val hasAndroidAutoState =
            activeProjectionPackage == ANDROID_AUTO_PACKAGE ||
                normalizeProjectionPackage(mediaPackageName) == ANDROID_AUTO_PACKAGE ||
                normalizeProjectionPackage(activeClusterProjectionPackage) == ANDROID_AUTO_PACKAGE

        return (hasAndroidAutoState && androidAutoSessionReady) ||
            isAndroidAutoLinkActiveForToggle(androidAutoLinkStatus) ||
            (androidAutoTaskPresent && androidAutoSessionReady)
    }

    private suspend fun sendAndroidAutoSteeringAppMediaCommand(
        keyCode: Int,
        reason: String
    ): Boolean {
        sendAndroidAutoFocus(3, "${reason}_FOCUS_BEFORE")
        delay(80)
        val sent = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                BottomBarService.sendAndroidAutoProjectionMediaNext() ||
                        sendAndroidAutoNativeMediaDirectCommand(keyCode, "${reason}_DIRECT_FALLBACK")
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                BottomBarService.sendAndroidAutoProjectionMediaPrevious() ||
                        sendAndroidAutoNativeMediaDirectCommand(keyCode, "${reason}_DIRECT_FALLBACK")
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                sendAndroidAutoNativePlaybackDirectCommand(keyCode, "${reason}_DIRECT_PLAYBACK")
            }
            else -> false
        }
        delay(180)
        sendAndroidAutoFocus(3, "${reason}_FOCUS_AFTER")
        return sent
    }

    suspend fun sendAndroidAutoDashboardPlaybackCommand(mediaIsPlayingHint: Boolean): Boolean {
        return sendAndroidAutoNativePlaybackDirectCommand(
            keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            reason = "AA_DASHBOARD_PLAYBACK",
            mediaIsPlayingHint = mediaIsPlayingHint
        )
    }

    private suspend fun sendAndroidAutoNativePlaybackDirectCommand(
        keyCode: Int,
        reason: String,
        mediaIsPlayingHint: Boolean = BottomBarState.mediaIsPlaying
    ): Boolean {
        if (androidAutoLinkCommandBinder?.isBinderAlive != true) {
            ensureAndroidAutoLinkCommandBound("${reason}_BIND")
            delay(ANDROID_AUTO_NATIVE_MEDIA_KEY_BIND_WAIT_MS)
        }

        val musicStatusBefore = readAndroidAutoMusicStatus("${reason}_BEFORE")
        val progressBefore = readAndroidAutoMediaProgress("${reason}_BEFORE")
        delay(ANDROID_AUTO_NATIVE_PLAYBACK_SETTLE_MS)
        val musicStatusAfterNative = readAndroidAutoMusicStatus("${reason}_NATIVE_AFTER")
        val progressAfterNative = readAndroidAutoMediaProgress("${reason}_NATIVE_AFTER")
        val transactionCode =
            resolveAndroidAutoPlaybackTransactionForState(
                keyCode = keyCode,
                musicStatusBefore = musicStatusBefore,
                musicStatusAfterNative = musicStatusAfterNative,
                progressBefore = progressBefore,
                progressAfterNative = progressAfterNative,
                mediaIsPlaying = mediaIsPlayingHint
            )
        if (transactionCode == null) {
            Log.w(
                TAG,
                "[$reason] Android Auto playback key keyCode=$keyCode already handled by native route " +
                        "before=${describeAndroidAutoMusicStatus(musicStatusBefore)} " +
                        "afterNative=${describeAndroidAutoMusicStatus(musicStatusAfterNative)} " +
                        "progressBefore=${progressBefore ?: -1} progressAfter=${progressAfterNative ?: -1}"
            )
            return true
        }

        val commandLabel =
            if (transactionCode == ANDROID_AUTO_LINK_COMMAND_PAUSE_TRANSACTION) {
                "pause"
            } else {
                "play"
            }
        val sent = transactAndroidAutoLinkCommandSync(transactionCode, "${reason}_${commandLabel.uppercase()}")
        delay(120)
        val musicStatusAfter = readAndroidAutoMusicStatus("${reason}_AFTER")
        Log.w(
            TAG,
            "[$reason] Android Auto direct playback key keyCode=$keyCode command=$commandLabel " +
                    "sent=$sent before=${describeAndroidAutoMusicStatus(musicStatusBefore)} " +
                    "afterNative=${describeAndroidAutoMusicStatus(musicStatusAfterNative)} " +
                    "after=${describeAndroidAutoMusicStatus(musicStatusAfter)} " +
                    "progressBefore=${progressBefore ?: -1} progressAfter=${progressAfterNative ?: -1}"
        )
        return sent
    }

    private suspend fun sendAndroidAutoAapMediaKeySequenceOnly(
        keyCode: Int,
        reason: String
    ): Boolean {
        if (androidAutoLinkCommandBinder?.isBinderAlive != true) {
            ensureAndroidAutoLinkCommandBound("${reason}_BIND")
            delay(ANDROID_AUTO_NATIVE_MEDIA_KEY_BIND_WAIT_MS)
        }

        val aapHardkeyOrdinal = mapAndroidAutoMediaKeyToAapHardkeyOrdinal(keyCode)
        if (aapHardkeyOrdinal == null) {
            Log.w(TAG, "[$reason] Android Auto media key $keyCode has no AAP hardkey mapping")
            return false
        }

        val downSent = sendAndroidAutoNativeMediaKeyEvent(
            aapHardkeyOrdinal,
            KeyEvent.ACTION_DOWN,
            "${reason}_DOWN"
        )
        delay(ANDROID_AUTO_NATIVE_MEDIA_KEY_UP_DELAY_MS)
        val upSent = sendAndroidAutoNativeMediaKeyEvent(
            aapHardkeyOrdinal,
            KeyEvent.ACTION_UP,
            "${reason}_UP"
        )
        val sent = downSent && upSent
        Log.w(
            TAG,
            "[$reason] Android Auto AAP-only steering media key keyCode=$keyCode " +
                    "aapHardkey=$aapHardkeyOrdinal sent=$sent down=$downSent up=$upSent"
        )
        return sent
    }

    private suspend fun sendAndroidAutoNativeMediaKeySequence(
        keyCode: Int,
        reason: String
    ): Boolean {
        if (androidAutoLinkCommandBinder?.isBinderAlive != true) {
            ensureAndroidAutoLinkCommandBound("${reason}_BIND")
            delay(ANDROID_AUTO_NATIVE_MEDIA_KEY_BIND_WAIT_MS)
        }

        val linkStatusBefore = readAndroidAutoLinkStatus("${reason}_BEFORE")
        val musicStatusBefore = readAndroidAutoMusicStatus("${reason}_BEFORE")
        Log.w(
            TAG,
            "[$reason] Android Auto media native state before keyCode=$keyCode " +
                    "link=${describeAndroidAutoLinkStatus(linkStatusBefore)} " +
                    "music=${describeAndroidAutoMusicStatus(musicStatusBefore)}"
        )

        if (shouldUseAndroidAutoOemOnlyMediaRoute(keyCode)) {
            val oemInputSent = sendAndroidAutoOemMediaInputFallback(keyCode, "${reason}_OEM_ONLY")
            delay(120)
            val linkStatusAfter = readAndroidAutoLinkStatus("${reason}_AFTER")
            val musicStatusAfter = readAndroidAutoMusicStatus("${reason}_AFTER")
            Log.w(
                TAG,
                "[$reason] Android Auto native media key keyCode=$keyCode route=OEM_ONLY " +
                        "sent=$oemInputSent direct=false aap=false oemInput=$oemInputSent"
            )
            Log.w(
                TAG,
                "[$reason] Android Auto media native state after keyCode=$keyCode " +
                        "link=${describeAndroidAutoLinkStatus(linkStatusAfter)} " +
                        "music=${describeAndroidAutoMusicStatus(musicStatusAfter)}"
            )
            return oemInputSent
        }

        val directCommandSent = sendAndroidAutoNativeMediaDirectCommand(keyCode, reason)

        val aapHardkeyOrdinal = mapAndroidAutoMediaKeyToAapHardkeyOrdinal(keyCode)
        if (aapHardkeyOrdinal == null) {
            delay(120)
            val linkStatusAfter = readAndroidAutoLinkStatus("${reason}_AFTER")
            val musicStatusAfter = readAndroidAutoMusicStatus("${reason}_AFTER")
            Log.w(
                TAG,
                "[$reason] Android Auto media native state after keyCode=$keyCode " +
                        "link=${describeAndroidAutoLinkStatus(linkStatusAfter)} " +
                        "music=${describeAndroidAutoMusicStatus(musicStatusAfter)}"
            )
            Log.w(TAG, "[$reason] Android Auto media key $keyCode has no AAP hardkey mapping")
            return directCommandSent
        }

        val downSent = sendAndroidAutoNativeMediaKeyEvent(
            aapHardkeyOrdinal,
            KeyEvent.ACTION_DOWN,
            "${reason}_DOWN"
        )
        delay(ANDROID_AUTO_NATIVE_MEDIA_KEY_UP_DELAY_MS)
        val upSent = sendAndroidAutoNativeMediaKeyEvent(
            aapHardkeyOrdinal,
            KeyEvent.ACTION_UP,
            "${reason}_UP"
        )
        val aapSent = downSent && upSent
        val oemInputSent = sendAndroidAutoOemMediaInputFallback(keyCode, "${reason}_OEM_INPUT")
        val sent = directCommandSent || aapSent || oemInputSent
        delay(120)
        val linkStatusAfter = readAndroidAutoLinkStatus("${reason}_AFTER")
        val musicStatusAfter = readAndroidAutoMusicStatus("${reason}_AFTER")
        Log.w(
            TAG,
            "[$reason] Android Auto native media key keyCode=$keyCode aapHardkey=$aapHardkeyOrdinal " +
                    "sent=$sent direct=$directCommandSent aap=$aapSent oemInput=$oemInputSent " +
                    "down=$downSent up=$upSent"
        )
        Log.w(
            TAG,
            "[$reason] Android Auto media native state after keyCode=$keyCode " +
                    "link=${describeAndroidAutoLinkStatus(linkStatusAfter)} " +
                    "music=${describeAndroidAutoMusicStatus(musicStatusAfter)}"
        )
        return sent
    }

    private suspend fun sendAndroidAutoOemMediaInputFallback(keyCode: Int, reason: String): Boolean {
        if (!ANDROID_AUTO_OEM_INPUT_MEDIA_FALLBACK_ENABLED) {
            Log.w(TAG, "[$reason] Android Auto OEM media input fallback disabled")
            return false
        }

        val oemInputKeyCode = mapAndroidAutoMediaKeyToOemInputKeyCode(keyCode)
        if (oemInputKeyCode == null) {
            Log.w(TAG, "[$reason] Android Auto media key $keyCode has no OEM input mapping")
            return false
        }

        markAndroidAutoOemInputFallbackEchoBlock(keyCode, reason)
        val output = withContext(Dispatchers.IO) {
            sh("input keyevent $oemInputKeyCode")
        }
        val failed = output.contains("Error", ignoreCase = true) ||
                output.contains("Unknown", ignoreCase = true) ||
                output.contains("usage:", ignoreCase = true)
        Log.w(
            TAG,
            "[$reason] Android Auto OEM media input fallback keyCode=$keyCode " +
                    "oemKey=$oemInputKeyCode sent=${!failed}"
        )
        delay(80)
        return !failed
    }

    private fun sendAndroidAutoNativeMediaDirectCommand(keyCode: Int, reason: String): Boolean {
        val transactionCode = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> ANDROID_AUTO_LINK_COMMAND_NEXT_TRANSACTION
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> ANDROID_AUTO_LINK_COMMAND_PREVIOUS_TRANSACTION
            else -> return false
        }

        val sent = transactAndroidAutoLinkCommandSync(
            transactionCode,
            "${reason}_DIRECT"
        )
        Log.w(TAG, "[$reason] Android Auto native direct media command keyCode=$keyCode sent=$sent")
        return sent
    }

    private fun sendAndroidAutoNativeMediaKeyEvent(
        aapHardkeyOrdinal: Int,
        action: Int,
        reason: String
    ): Boolean {
        return transactAndroidAutoLinkCommand(
            ANDROID_AUTO_LINK_COMMAND_SEND_KEY_EVENT_TRANSACTION,
            reason
        ) { data ->
            data.writeInt(aapHardkeyOrdinal)
            data.writeInt(action)
        }
    }

    internal fun shouldHandleAndroidAutoMediaControlKeyForTest(
        isAndroidAutoClusterActive: Boolean,
        keyCode: Int,
        action: Int,
        now: Long,
        lastKeyCode: Int,
        lastHandledAt: Long,
        cooldownMs: Long = ANDROID_AUTO_MEDIA_KEY_COOLDOWN_MS
    ): Boolean {
        if (!isAndroidAutoClusterActive) return false
        if (!isAndroidAutoMediaControlAction(action)) return false
        if (!isAndroidAutoMediaControlKey(keyCode)) return false
        return !(lastHandledAt > 0L &&
                lastKeyCode == keyCode &&
                now - lastHandledAt in 0..cooldownMs)
    }

    internal fun shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
        isAndroidAutoActive: Boolean,
        keyCode: Int,
        action: Int,
        now: Long,
        lastKeyCode: Int,
        lastHandledAt: Long,
        blockedKeyCode: Int = 0,
        cooldownMs: Long = ANDROID_AUTO_MEDIA_KEY_COOLDOWN_MS
    ): Boolean {
        if (!isAndroidAutoActive) return false
        if (!isAndroidAutoMediaControlAction(action)) return false
        if (!isAndroidAutoSteeringToggleKey(keyCode)) return false
        return action == KeyEvent.ACTION_UP
    }

    internal fun mapAndroidAutoClusterMediaCommandForTest(command: Int): Int? {
        return mapAndroidAutoClusterMediaCommandToKeyCode(command)
    }

    internal fun shouldConsumeAndroidAutoClusterMediaCallbackForTest(
        isAndroidAutoActive: Boolean,
        command: Int
    ): Boolean {
        return shouldConsumeAndroidAutoClusterMediaCallback(isAndroidAutoActive, command)
    }

    internal fun mapAndroidAutoMediaKeyToAapHardkeyOrdinalForTest(keyCode: Int): Int? {
        return mapAndroidAutoMediaKeyToAapHardkeyOrdinal(keyCode)
    }

    internal fun mapAndroidAutoMediaKeyToOemInputKeyCodeForTest(keyCode: Int): Int? {
        return mapAndroidAutoMediaKeyToOemInputKeyCode(keyCode)
    }

    internal fun isAndroidAutoOemInputMediaFallbackEnabledForTest(): Boolean {
        return ANDROID_AUTO_OEM_INPUT_MEDIA_FALLBACK_ENABLED
    }

    internal fun shouldUseAndroidAutoOemOnlyMediaRouteForTest(keyCode: Int): Boolean {
        return shouldUseAndroidAutoOemOnlyMediaRoute(keyCode)
    }

    internal fun shouldUseAndroidAutoSteeringAppCommandRouteForTest(
        keyCode: Int,
        action: Int,
        isSteeringInput: Boolean
    ): Boolean {
        val source = if (isSteeringInput) {
            AndroidAutoMediaKeySource.STEERING_INPUT
        } else {
            AndroidAutoMediaKeySource.CLUSTER_CALLBACK
        }
        return shouldUseAndroidAutoSteeringAppCommandRoute(keyCode, action, source)
    }

    internal fun shouldSuppressAndroidAutoSteeringMediaInjectionForTest(
        isSteeringInput: Boolean
    ): Boolean {
        val source = if (isSteeringInput) {
            AndroidAutoMediaKeySource.STEERING_INPUT
        } else {
            AndroidAutoMediaKeySource.CLUSTER_CALLBACK
        }
        return shouldSuppressAndroidAutoSteeringMediaInjection(source)
    }

    internal fun resolveAndroidAutoPlaybackTransactionForTest(
        keyCode: Int,
        musicStatus: Int?,
        mediaIsPlaying: Boolean
    ): Int? {
        return resolveAndroidAutoPlaybackTransactionForState(
            keyCode = keyCode,
            musicStatusBefore = musicStatus,
            musicStatusAfterNative = musicStatus,
            progressBefore = null,
            progressAfterNative = null,
            mediaIsPlaying = mediaIsPlaying
        )
    }

    internal fun resolveAndroidAutoPlaybackTransactionForObservedStateForTest(
        keyCode: Int,
        musicStatusBefore: Int?,
        musicStatusAfterNative: Int?,
        progressBefore: Int?,
        progressAfterNative: Int?,
        mediaIsPlaying: Boolean
    ): Int? {
        return resolveAndroidAutoPlaybackTransactionForState(
            keyCode = keyCode,
            musicStatusBefore = musicStatusBefore,
            musicStatusAfterNative = musicStatusAfterNative,
            progressBefore = progressBefore,
            progressAfterNative = progressAfterNative,
            mediaIsPlaying = mediaIsPlaying
        )
    }

    internal fun isAndroidAutoActiveForMediaControlForTest(
        activeProjectionPackage: String?,
        mediaPackageName: String?,
        activeClusterProjectionPackage: String?,
        androidAutoLinkStatus: Int? = null,
        androidAutoTaskPresent: Boolean = false,
        androidAutoSessionReady: Boolean = true
    ): Boolean {
        return isAndroidAutoActiveForMediaControlForState(
            activeProjectionPackage = activeProjectionPackage,
            mediaPackageName = mediaPackageName,
            activeClusterProjectionPackage = activeClusterProjectionPackage,
            androidAutoLinkStatus = androidAutoLinkStatus,
            androidAutoTaskPresent = androidAutoTaskPresent,
            androidAutoSessionReady = androidAutoSessionReady
        )
    }

    internal fun describeAndroidAutoLinkStatusForTest(status: Int?): String {
        return describeAndroidAutoLinkStatus(status)
    }

    internal fun describeAndroidAutoMusicStatusForTest(status: Int?): String {
        return describeAndroidAutoMusicStatus(status)
    }

    internal fun shouldSkipAndroidAutoOemInputFallbackEchoForTest(
        keyCode: Int,
        action: Int,
        now: Long,
        echoKeyCode: Int,
        echoBlockUntil: Long
    ): Boolean {
        return shouldSkipAndroidAutoOemInputFallbackEcho(
            keyCode = keyCode,
            action = action,
            now = now,
            echoKeyCode = echoKeyCode,
            echoBlockUntil = echoBlockUntil
        )
    }

    internal suspend fun startCarPlayOnDisplay(
        sourceConfig: DisplayAppConfig,
        reason: String,
        rememberTarget: Boolean = true
    ) {
        val config = getCarPlayConfigForDisplay(sourceConfig.displayId, sourceConfig)
        val displayId = config.displayId
        val bounds = getCarPlayDisplayBounds(displayId)
        val previousDisplay = findTaskForPackage(CARPLAY_PACKAGE)?.displayId
        val handoffStartedEpoch = currentEpochSeconds()
        val escapedActivity = CARPLAY_ACTIVITY.replace("$", "\\$")
        val windowingMode = if (displayId == 0) 1 else 5

        if (rememberTarget) {
            rememberCarPlayDisplayTarget(displayId, reason)
        }

        if (displayId == 3) {
            markCarPlayClusterHandoffStarted(reason)
            val mainTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 0)
            if (mainTask != null) {
                moveMainCarPlayStackToClusterIfSafe(mainTask, bounds, reason)?.let {
                    return
                }
            }
        }

        val currentTask = findTaskForPackage(CARPLAY_PACKAGE)
        if (displayId != 0 && currentTask != null && currentTask.displayId != displayId) {
            saveCurrentBounds(CARPLAY_PACKAGE, currentTask)
            val tasksInStack = countTasksInStack(currentTask.stackId)
            if (tasksInStack > 1) {
                Log.w(
                    TAG,
                    "[$reason] CarPlay is in mixed stack ${currentTask.stackId} ($tasksInStack tasks); bringing sibling task to front before retargeting"
                )
                if (bringOtherTaskInStackToFront(currentTask.stackId, CARPLAY_PACKAGE, reason)) {
                    Thread.sleep(220)
                }
            } else if (currentTask.displayId == 0) {
                Log.w(
                    TAG,
                    "[$reason] Defocusing display-0 CarPlay before D3 start to avoid ActivityManager task reuse"
                )
                bringNonProjectionTaskOnDisplayToFront(0, "${reason}_DEFOCUS_DISPLAY0_BEFORE_CLUSTER_START")
                Thread.sleep(300)
            }
        }

        if (displayId != 0) {
            evictOtherAppsFromDisplay(displayId, CARPLAY_PACKAGE)
            BottomBarState.restoredApps.remove(CARPLAY_PACKAGE)
        } else if (!BottomBarState.restoredApps.contains(CARPLAY_PACKAGE)) {
            BottomBarState.restoredApps.add(CARPLAY_PACKAGE)
        }

        configureCarPlayProjection(reason)
        requestCarPlayUiIfLinkActivated("${reason}_PRE_START")

        if (displayId == 0) {
            val liveSecondaryTask = findAllTasksForPackage(CARPLAY_PACKAGE)
                .firstOrNull { it.displayId != 0 }
            if (liveSecondaryTask != null) {
                Log.w(
                    TAG,
                    "[$reason] Moving live CarPlay stack ${liveSecondaryTask.stackId} from display ${liveSecondaryTask.displayId} to display 0"
                )
                sh("am display move-stack ${liveSecondaryTask.stackId} 0")
                Thread.sleep(700)

                var movedTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 0)
                if (movedTask != null) {
                    ensureCarPlayFullscreenWithoutVideoBroadcasts(
                        movedTask,
                        displayId,
                        bounds,
                        "${reason}_MOVE_TO_MAIN_NO_VIDEO_BROADCAST"
                    )
                    closeCarPlayVisualStacks("${reason}_MOVE_TO_MAIN_CLEAN_DUPLICATES", exceptStackId = movedTask.stackId)
                    movedTask = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, 0) ?: movedTask
                    Log.w(TAG, "[$reason] CarPlay live stack restored on display 0 as stack ${movedTask.stackId}")
                    notifyCarPlayDisplayHandoff(displayId, previousDisplay)
                    return
                }

                Log.e(
                    TAG,
                    "[$reason] move-stack did not place CarPlay on display 0; falling back to clean recreate"
                )
            }
        }

        if (displayId == 0) {
            closeCarPlayVisualStacks("${reason}_CLEAN_START")
            Thread.sleep(250)
        } else {
            Log.w(
                TAG,
                "[$reason] Preserving existing CarPlay visual stack until display $displayId Surface is ready"
            )
        }

        Log.w(TAG, "[$reason] Starting CarPlay on display $displayId fullscreen=[${bounds.joinToString(",")}]")
        var startResult = startCarPlayActivity(displayId, windowingMode, escapedActivity, reason)

        Thread.sleep(700)
        var taskInfo = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)

        if (taskInfo == null) {
            val wrongDisplayTask = findTaskForPackage(CARPLAY_PACKAGE)
            if (wrongDisplayTask != null && wrongDisplayTask.displayId != displayId) {
                Log.w(
                    TAG,
                    "[$reason] CarPlay reopened on display ${wrongDisplayTask.displayId}; recreating once on $displayId"
                )
                val reusedTopMost = wasTopMostInstanceReused(startResult)
                if (displayId != 0 && reusedTopMost) {
                    val tasksInStack = countTasksInStack(wrongDisplayTask.stackId)
                    if (tasksInStack > 1 && bringOtherTaskInStackToFront(wrongDisplayTask.stackId, CARPLAY_PACKAGE, "${reason}_RETRY_AFTER_SIBLING_FRONT")) {
                        Thread.sleep(220)
                        configureCarPlayProjection("${reason}_RETRY_AFTER_SIBLING_FRONT")
                        startResult = startCarPlayActivity(
                            displayId,
                            windowingMode,
                            escapedActivity,
                            "${reason}_RETRY_AFTER_SIBLING_FRONT"
                        )
                        Thread.sleep(700)
                        taskInfo = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)
                    }

                    if (
                        taskInfo == null &&
                                bringNonProjectionTaskOnDisplayToFront(
                                    wrongDisplayTask.displayId,
                                    "${reason}_RETRY_AFTER_TOPMOST_REUSE"
                                )
                    ) {
                        Thread.sleep(220)
                        configureCarPlayProjection("${reason}_RETRY_AFTER_TOPMOST_REUSE")
                        startResult = startCarPlayActivity(
                            displayId,
                            windowingMode,
                            escapedActivity,
                            "${reason}_RETRY_AFTER_TOPMOST_REUSE"
                        )
                        Thread.sleep(700)
                        taskInfo = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)
                    }

                    if (taskInfo == null) {
                        Log.e(
                            TAG,
                            "[$reason] ActivityManager reused the top-most CarPlay instance on display ${wrongDisplayTask.displayId}; no task was created on display $displayId"
                        )
                        notifyCarPlayDisplayHandoff(displayId, previousDisplay)
                        return
                    }
                }

                if (taskInfo != null) {
                    // Retry after sibling front succeeded.
                } else if (displayId == 0) {
                    sh("am stack remove ${wrongDisplayTask.stackId}")
                    Thread.sleep(250)
                    configureCarPlayProjection("${reason}_RETRY")
                    startResult = startCarPlayActivity(displayId, windowingMode, escapedActivity, "${reason}_RETRY")
                    Thread.sleep(700)
                    taskInfo = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)
                } else {
                    Log.w(
                        TAG,
                        "[$reason] Preserving wrong-display CarPlay stack ${wrongDisplayTask.stackId} during retry"
                    )
                    if (!reusedTopMost) {
                        configureCarPlayProjection("${reason}_RETRY")
                        startResult = startCarPlayActivity(displayId, windowingMode, escapedActivity, "${reason}_RETRY")
                        Thread.sleep(700)
                        taskInfo = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId)
                    } else {
                        Log.e(
                            TAG,
                            "[$reason] Skipping aggressive retry because CarPlay remained top-most on display ${wrongDisplayTask.displayId}"
                        )
                    }
                }
            }
        }

        if (taskInfo != null) {
            if (displayId == 3) {
                ensureCarPlayFullscreenWithoutVideoBroadcasts(
                    taskInfo,
                    displayId,
                    bounds,
                    "${reason}_POST_START_NO_VIDEO_BROADCAST"
                )
            } else {
                resizeAndFocusCarPlay(taskInfo, displayId, bounds, "${reason}_POST_START")
            }
            closeCarPlayVisualStacks("${reason}_POST_START_CLEAN_DUPLICATES", exceptStackId = taskInfo.stackId)
            taskInfo = findTaskForPackageOnDisplay(CARPLAY_PACKAGE, displayId) ?: taskInfo
            if (displayId == 3) {
                delay(650)
                reassertCarPlayClusterSurfaceIfStale(
                    taskInfo,
                    "${reason}_POST_START_STALE_SURFACE_GUARD"
                )
            }
        } else {
            Log.e(TAG, "[$reason] CarPlay task was not found on display $displayId after start")
        }

        recoverCarPlayRenderIfNeeded(
            displayId = displayId,
            bounds = bounds,
            windowingMode = windowingMode,
            escapedActivity = escapedActivity,
            sinceEpoch = handoffStartedEpoch,
            reason = reason
        )

        notifyCarPlayDisplayHandoff(displayId, previousDisplay)
    }

    /**
     * Resolves the label and icon for a given package name, handling pre-defined apps as first-class items.
     */
    fun resolveAppInfo(context: Context, packageName: String, customName: String? = null): ResolvedAppInfo {
        val pm = context.packageManager

        // 1. Determine Label
        val label = when {
            !customName.isNullOrBlank() -> customName
            packageName.contains("androidauto", ignoreCase = true) ||
            packageName.contains("gearhead", ignoreCase = true) -> "Android Auto"
            packageName.contains("carplay", ignoreCase = true) ||
            packageName.contains("carlink", ignoreCase = true) ||
            packageName.contains("zlink", ignoreCase = true) -> "Apple CarPlay"
            packageName.equals("com.google.android.youtube", ignoreCase = true) -> "YouTube"
            packageName.equals("com.google.android.apps.youtube.music", ignoreCase = true) -> "YouTube Music"
            else -> {
                try {
                    val info = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    packageName
                }
            }
        }

        // 2. Determine Icon
        // Prioritize our custom icons for known system/predefined apps
        val icon = when {
            packageName.contains("androidauto", ignoreCase = true) ||
            packageName.contains("gearhead", ignoreCase = true) -> context.getDrawable(R.drawable.ic_android_auto_default)
            packageName.contains("carplay", ignoreCase = true) ||
            packageName.contains("carlink", ignoreCase = true) ||
            packageName.contains("zlink", ignoreCase = true) -> context.getDrawable(R.drawable.ic_carplay_default)
            packageName.equals("com.google.android.youtube", ignoreCase = true) -> context.getDrawable(R.drawable.ic_youtube_default)
            packageName.equals("com.google.android.apps.youtube.music", ignoreCase = true) -> context.getDrawable(R.drawable.ic_youtube_music_default)
            packageName.startsWith("com.beantech", ignoreCase = true) -> context.getDrawable(R.drawable.ic_gwm)
            else -> {
                try {
                    pm.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    null
                }
            }
        }

        return ResolvedAppInfo(label, icon)
    }

    fun getAllConfigs(): List<DisplayAppConfig> {
        val json = getPrefs().getString(SharedPreferencesKeys.DISPLAY_APP_CONFIGS.key, null)
            ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DisplayAppConfig>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            // Fallback: try to load as Map for backward compatibility
            try {
                val mapType = object : TypeToken<Map<String, DisplayAppConfig>>() {}.type
                val map: Map<String, DisplayAppConfig> = gson.fromJson(json, mapType)
                map.values.toList()
            } catch (e2: Exception) {
                Log.e(TAG, "Error loading configs", e)
                emptyList()
            }
        }
    }

    fun getAppConfig(packageName: String): DisplayAppConfig? {
        return getAllConfigs().find { it.packageName == packageName }
    }

    fun saveConfig(config: DisplayAppConfig) {
        val configs = getAllConfigs().toMutableList()
        val index = configs.indexOfFirst { it.packageName == config.packageName }
        if (index >= 0) {
            configs[index] = config
        } else {
            configs.add(config)
        }
        getPrefs().edit()
            .putString(SharedPreferencesKeys.DISPLAY_APP_CONFIGS.key, gson.toJson(configs))
            .apply()
    }

    /**
     * Resolves the main activity for a package and creates a default config if it doesn't exist.
     * Defaults to Display 3 (Cluster) with full screen dimensions.
     */
    fun getOrCreateDefaultConfig(context: Context, packageName: String, save: Boolean = true): DisplayAppConfig? {
        val existing = getAllConfigs().find { it.packageName == packageName }
        if (existing != null) return existing

        // Check predefined apps first (they might not have a launcher intent)
        val predefined = PREDEFINED_APPS.find { it.packageName == packageName }
        if (predefined != null) {
            if (save) saveConfig(predefined)
            return predefined
        }

        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return null
        val activityName = intent.component?.className ?: return null

        val config = DisplayAppConfig(
            packageName = packageName,
            activityName = activityName,
            displayId = 3,
            x = 0,
            y = 0,
            width = 1920,
            height = 720,
            substituteIcon = if (packageName.startsWith("com.beantech")) "gwm" else null
        )
        if (save) saveConfig(config)
        return config
    }

    fun deleteConfig(packageName: String) {
        val configs = getAllConfigs().toMutableList()
        configs.removeAll { it.packageName == packageName }
        getPrefs().edit()
            .putString(SharedPreferencesKeys.DISPLAY_APP_CONFIGS.key, gson.toJson(configs))
            .apply()
    }

    fun saveAllConfigs(configs: List<DisplayAppConfig>) {
        getPrefs().edit()
            .putString(SharedPreferencesKeys.DISPLAY_APP_CONFIGS.key, gson.toJson(configs))
            .apply()
    }

    fun moveConfigUp(packageName: String) {
        val configs = getAllConfigs().toMutableList()
        val index = configs.indexOfFirst { it.packageName == packageName }
        if (index > 0) {
            val config = configs.removeAt(index)
            configs.add(index - 1, config)
            getPrefs().edit()
                .putString(SharedPreferencesKeys.DISPLAY_APP_CONFIGS.key, gson.toJson(configs))
                .apply()
        }
    }

    fun moveConfigDown(packageName: String) {
        val configs = getAllConfigs().toMutableList()
        val index = configs.indexOfFirst { it.packageName == packageName }
        if (index >= 0 && index < configs.size - 1) {
            val config = configs.removeAt(index)
            configs.add(index + 1, config)
            getPrefs().edit()
                .putString(SharedPreferencesKeys.DISPLAY_APP_CONFIGS.key, gson.toJson(configs))
                .apply()
        }
    }

    private fun sh(cmd: String): String {
        Log.w(TAG, "CMD: $cmd")
        val out = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "$cmd 2>&1"))
        Log.w(TAG, "OUT: [$out]")
        return out
    }

    fun getEffectiveBounds(config: DisplayAppConfig): IntArray {
        if (isCarPlayPackage(config.packageName)) {
            return getCarPlayDisplayBounds(config.displayId)
        }
        if (isAndroidAutoPackage(config.packageName)) {
            return getAndroidAutoDisplayBounds(config.displayId)
        }

        val prefs = getPrefs()
        val virtualClusterEnabled = prefs.getBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, true)

        var x = config.x
        var y = config.y
        var width = config.width
        var height = config.height

        if (!config.overrideThemeDimensions && virtualClusterEnabled && config.displayId == 3) {
            val themeFolderName = prefs.getString(SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, "Básico") ?: "Básico"
            if (themeFolderName == "Default" || themeFolderName == "Básico" || themeFolderName == "Light") {
                x = 0
                y = 62
                width = 1920
                height = 596
            } else {
                val themeManager = ThemeManager.getInstance(App.getContext())
                val metadata = themeManager.getThemeMetadata(themeFolderName)
                if (metadata != null && metadata.x != null && metadata.y != null && metadata.width != null && metadata.height != null) {
                    x = metadata.x!!
                    y = metadata.y!!
                    width = metadata.width!!
                    height = metadata.height!!
                }
            }
        }

        // v2.3: Subtract overscan from Display 0 apps so the bottom bar
        // shrinks the app window instead of overlaying its bottom strip.
        // System-level `wm overscan` already handles apps NOT managed by
        // Impulse; but when Impulse calls `am stack resize` explicitly
        // (e.g. for AA), our bounds override the system overscan unless
        // we subtract it here too. overrideThemeDimensions is the opt-out
        // for users who want their raw configured bounds.
        // Gated on the persistent bar being enabled — if the user turned
        // the bar off, no overscan should be applied even if stale pref
        // values are still on disk.
        if (!config.overrideThemeDimensions && config.displayId == 0) {
            val barEnabled = prefs.getBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, false)
            if (barEnabled) {
                val overscanPx = getOverscanForPackage(config.packageName)
                if (overscanPx > 0 && height > overscanPx) {
                    height -= overscanPx
                }
            }
        }

        return intArrayOf(x, y, x + width, y + height)
    }

    suspend fun launchApp(config: DisplayAppConfig) {
        withContext(Dispatchers.IO) {
            if (isCarPlayPackage(config.packageName)) {
                CarPlayDisplayOrchestrator.start(config, "LAUNCH_APP")
                return@withContext
            }
            if (isAndroidAutoPackage(config.packageName)) {
                startAndroidAutoOnDisplay(config, "LAUNCH_APP")
                return@withContext
            }

            // Any fresh launch clears the 'Restored' status to ensure standard layout
            // SnapshotStateList operations are thread-safe and can run here
            if (BottomBarState.restoredApps.contains(config.packageName)) {
                BottomBarState.restoredApps.remove(config.packageName)
            }

            try {
                val bounds = getEffectiveBounds(config)
                val x = bounds[0]
                val y = bounds[1]
                val right = bounds[2]
                val bottom = bounds[3]

                val escapedActivity = config.activityName.replace("$", "\\$")
                val isOwnPackage = config.packageName == App.getContext().packageName

                // Evict any other app already on this display before fresh launch
                if (config.displayId != 0) {
                    evictOtherAppsFromDisplay(config.displayId, config.packageName)
                }

                // Already on target display — just resize
                val existingStack = findStackIdForPackage(config.packageName, config.displayId)
                if (existingStack != null) {
                    // If launching on secondary display, remove from restored state
                    if (config.displayId != 0 && BottomBarState.restoredApps.contains(config.packageName)) {
                        BottomBarState.restoredApps.remove(config.packageName)
                    }
                    sh("am stack resize $existingStack $x $y $right $bottom")
                    notifyDisplayStateChanged(config.displayId)
                    return@withContext
                }

                // Force-stop + start fresh on target display
                if (!isOwnPackage) {
                    // If launching on secondary display, remove from restored state
                    if (config.displayId != 0 && BottomBarState.restoredApps.contains(config.packageName)) {
                        BottomBarState.restoredApps.remove(config.packageName)
                    }
                    sh("am force-stop ${config.packageName}")
                    Thread.sleep(200)
                    sh("am start -n ${config.packageName}/$escapedActivity --display ${config.displayId} --windowingMode 5")
                } else {
                    Log.w(TAG, "Skipping force-stop/start for own package ${config.packageName}")
                }

                Thread.sleep(300)
                val newStackId = findStackIdForPackage(config.packageName, config.displayId)
                if (newStackId != null) {
                    sh("am stack resize $newStackId $x $y $right $bottom")
                } else {
                    Log.w(TAG, "Could not find stack for ${config.packageName} on display ${config.displayId}")
                }
                notifyDisplayStateChanged(config.displayId)

                // Trigger focus poke if this is Android Auto or CarPlay on a secondary display
                if (config.packageName == "com.ts.androidauto.app" || config.packageName == "com.ts.carplay.app") {
                    syncInterconnectionFocus("MANUAL_LAUNCH")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error launching app ${config.packageName}", e)
            }
        }
    }

    /**
     * Resizes an already-running app on its target display. Used for live preview slider updates.
     */
    suspend fun resizeApp(config: DisplayAppConfig) = withContext(Dispatchers.IO) {
        try {
            val bounds = when {
                isCarPlayPackage(config.packageName) -> getCarPlayDisplayBounds(config.displayId)
                isAndroidAutoPackage(config.packageName) -> getAndroidAutoDisplayBounds(config.displayId)
                else -> intArrayOf(config.x, config.y, config.x + config.width, config.y + config.height)
            }
            val x = bounds[0]
            val y = bounds[1]
            val right = bounds[2]
            val bottom = bounds[3]

            val stackId = findStackIdForPackage(config.packageName, config.displayId)
            if (stackId != null) {
                sh("am stack resize $stackId $x $y $right $bottom")
                ServiceManager.getInstance().dispatchServiceManagerEvent(
                    br.com.redesurftank.havalshisuku.models.ServiceManagerEventType.APP_GEOMETRY_CHANGED
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error resizing app ${config.packageName}", e)
            }
        }
    }

    /**
     * v2.3: Subtracts the configured overscan from a Display 0 bounds
     * height if the persistent bar is enabled. Used by code paths that
     * compute Display 0 fullscreen-restore bounds from cached state or
     * raw display resolution (launchAnyApp DISPLAY_MOVE RESTORE,
     * evictOtherAppsFromDisplay RESTORE, bringAllToMainDisplay) — those
     * paths don't flow through getEffectiveBounds so we apply the same
     * adjustment here. Returns the bottom edge (y2) after adjustment.
     */
    private fun applyOverscanToDisplay0Height(packageName: String, y2: Int): Int {
        val prefs = getPrefs()
        val barEnabled = prefs.getBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, false)
        if (!barEnabled) return y2
        val overscanPx = getOverscanForPackage(packageName)
        if (overscanPx <= 0) return y2
        val adjusted = y2 - overscanPx
        return if (adjusted > 0) adjusted else y2
    }

    /**
     * v2.3: Re-applies effective bounds to every running Display 0 app
     * after the overscan setting changes. For apps Impulse explicitly
     * manages via `am stack resize`, our bounds override the system-level
     * `wm overscan` — so when overscan changes we have to re-resize
     * ourselves. Unmanaged apps are still handled by `wm overscan` and
     * don't need to flow through here.
     *
     * Skips com.android.systemui and any app with overrideThemeDimensions=true.
     */
    suspend fun reapplyDisplay0BoundsForOverscan() = withContext(Dispatchers.IO) {
        try {
            val stackList = getStackList()
            val stackIds = getAllStackIdsOnDisplay(0)
            if (stackIds.isEmpty()) return@withContext

            for (stackId in stackIds) {
                val pkg = findPackageNameForStack(stackId, stackList) ?: continue
                if (pkg == "com.android.systemui") continue
                if (pkg == App.getContext().packageName) continue // skip self

                // Only resize apps that Impulse manages (have an effective config).
                // getOrCreateDefaultConfig falls back to a sensible Display 0 default
                // for known packages but returns null if there's no launch intent.
                val config = getOrCreateDefaultConfig(App.getContext(), pkg, save = false) ?: continue
                if (config.displayId != 0) continue
                if (config.overrideThemeDimensions) continue

                val bounds = getEffectiveBounds(config)
                Log.w(TAG, "[v2.3 OVERSCAN_REAPPLY] $pkg | stack $stackId | bounds=[${bounds.joinToString(",")}]")
                sh("am stack resize $stackId ${bounds[0]} ${bounds[1]} ${bounds[2]} ${bounds[3]}")
            }

            ServiceManager.getInstance().dispatchServiceManagerEvent(
                br.com.redesurftank.havalshisuku.models.ServiceManagerEventType.APP_GEOMETRY_CHANGED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in reapplyDisplay0BoundsForOverscan", e)
        }
    }

    @JvmStatic
    fun reapplyDisplay0BoundsForOverscanAsync() {
        scope.launch { reapplyDisplay0BoundsForOverscan() }
    }

    /**
     * Kills an app via am force-stop.
     */
    suspend fun killApp(packageName: String) = withContext(Dispatchers.IO) {
        try {
            if (isCarPlayPackage(packageName)) {
                closeCarPlayVisualStacks("KILL_APP_KEEP_CARPLAY_SESSION")
                configureCarPlayProjection("KILL_APP_KEEP_CARPLAY_SESSION")
                notifyDisplayStateChanged(3)
                return@withContext
            }
            if (isAndroidAutoPackage(packageName)) {
                rememberAndroidAutoDisplayTarget(0, "KILL_APP_ANDROID_AUTO")
                closeAndroidAutoVisualStacks("KILL_APP_KEEP_ANDROID_AUTO_SERVICE")
                notifyDisplayStateChanged(3)
                return@withContext
            }

            sh("am force-stop $packageName")
            notifyDisplayStateChanged(3) // Check Display 3 specifically as it's our focus
        } catch (e: Exception) {
            Log.e(TAG, "Error killing $packageName", e)
        }
    }

    @JvmStatic
    fun killAppAsync(packageName: String) {
        scope.launch {
            killApp(packageName)
        }
    }

    private fun notifyBottomBarUpdate() {
        Log.w(TAG, "Triggering immediate BottomBar overscan refresh")
        val intent = Intent("br.com.redesurftank.havalshisuku.UPDATE_BAR_POSITION")
            .setPackage(App.getContext().packageName)
        App.getContext().sendBroadcast(intent)
    }

    /**
     * Brings the app to the main display (0) for user interaction.
     * Uses am display move-stack + resize to fullscreen.
     */
    suspend fun launchOnMainDisplay(config: DisplayAppConfig) = launchAnyApp(App.getContext(), config.packageName, config.activityName)

    /**
     * More robust launch for the main display using package manager intents.
     */
    suspend fun launchAnyApp(context: Context, packageName: String, activityName: String? = null) = withContext(Dispatchers.IO) {
        try {
            if (isCarPlayPackage(packageName)) {
                CarPlayDisplayOrchestrator.openOnMain(
                    getCarPlayConfigForDisplay(0),
                    "LAUNCH_MAIN_ICON"
                )
                return@withContext
            }
            if (isAndroidAutoPackage(packageName)) {
                startAndroidAutoOnDisplay(getAndroidAutoConfigForDisplay(0), "LAUNCH_MAIN_ICON")
                return@withContext
            }

            // First try to find if it's already on another display and move it
            val taskInfo = findTaskForPackage(packageName)
            if (taskInfo != null && taskInfo.displayId != 0) {
                // Save current bounds before moving away
                saveCurrentBounds(packageName, taskInfo)

                Log.w(TAG, "Moving stack ${taskInfo.stackId} to display 0")
                val result = sh("am display move-stack ${taskInfo.stackId} 0")
                notifyDisplayStateChanged(taskInfo.displayId)

                // Explicitly bring to front after move to ensure it's visible on Display 0
                sh("am stack move-task-to-front ${taskInfo.taskId}")

                if (!result.contains("Exception") && !result.contains("Error")) {
                    val movedTask = findTaskForPackage(packageName)
                    if (movedTask != null && movedTask.displayId == 0) {
                        // Mark as restored to enable 3x overscan sync
                        if (!BottomBarState.restoredApps.contains(packageName)) {
                            BottomBarState.restoredApps.add(packageName)
                        }
                        // Restore cached bounds for Display 0 if available, fallback to display resolution
                        val cached = lastKnownDisplayBounds[packageName]?.get(0)
                        val overscanPx = getOverscanForPackage(packageName)
                        val density = App.getContext().resources.displayMetrics.density
                        val overscanDp = (overscanPx / density).toInt()

                        // Try to reset to Fullscreen mode for Display 0 (Standard behavior)
                        sh("am stack set-windowing-mode ${movedTask.stackId} 1")

                        if (cached != null) {
                            var y2 = cached[3]
                            if (y2 >= 710) {
                                y2 = 720
                            }
                            // v2.3: respect overscan when restoring to Display 0 fullscreen
                            y2 = applyOverscanToDisplay0Height(packageName, y2)
                            Log.w(TAG, "[DISPLAY_MOVE] RESTORE App: $packageName | Bounds: [${cached[0]},${cached[1]},${cached[2]},$y2] | Overscan: ${overscanDp}dp | Mode: 1")
                            sh("am stack resize ${movedTask.stackId} ${cached[0]} ${cached[1]} ${cached[2]} $y2")
                        } else {
                            val res = getDisplayResolution(0)
                            // v2.3: respect overscan
                            val effectiveHeight = applyOverscanToDisplay0Height(packageName, res.second)
                            Log.w(TAG, "[DISPLAY_MOVE] FALLBACK App: $packageName | Bounds: [0,0,${res.first},$effectiveHeight] | Overscan: ${overscanDp}dp | Mode: 1")
                            sh("am stack resize ${movedTask.stackId} 0 0 ${res.first} $effectiveHeight")
                        }

                        // Bring AA to the foreground with focus flags after the move.
                        // am stack move-task-to-front above does not always reorder the
                        // focused stack — without -f 0x14000000 (FLAG_ACTIVITY_NEW_TASK |
                        // FLAG_ACTIVITY_REORDER_TO_FRONT) the previously-focused app stays
                        // on top.
                        val predefined = PREDEFINED_APPS.find { it.packageName == packageName }
                        val escapedActivityForFront = (activityName ?: predefined?.activityName)?.replace("$", "\\$")
                        if (escapedActivityForFront != null) {
                            sh("am start -n $packageName/$escapedActivityForFront --display 0 --windowingMode 1 -f 0x14000000")
                        }

                        // Force BottomBar to re-apply overscan immediately
                        notifyBottomBarUpdate()
                        preserveCarPlayClusterContract("LAUNCH_MAIN_AFTER_MOVE_$packageName")
                        preserveAndroidAutoClusterContract("LAUNCH_MAIN_AFTER_MOVE_$packageName")
                        return@withContext
                    }
                }
            }

            // Standard intent launch is most reliable for the main display
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                Log.w(TAG, "Launching $packageName via Intent")
                intent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )

                // If task exists on display 0, bring it to front
                // Newer Android versions don't support 'am stack move-task-to-front'
                // Re-launching with flags is the most compatible way
                context.startActivity(intent)
            } else {
                // No launcher intent (some system apps don't expose one — e.g.,
                // com.ts.androidauto.app's AapActivity has no MAIN/LAUNCHER
                // filter). Fall back to the explicit activityName from the
                // caller, or look it up from PREDEFINED_APPS / saved configs.
                val resolvedActivity = activityName
                    ?: PREDEFINED_APPS.find { it.packageName == packageName }?.activityName
                    ?: getAppConfig(packageName)?.activityName
                if (resolvedActivity != null) {
                    val escapedActivity = resolvedActivity.replace("$", "\\$")
                    Log.w(TAG, "Launching $packageName via am start (no launcher intent, using activity $resolvedActivity)")
                    // Use -f 0x14000000 (NEW_TASK | REORDER_TO_FRONT) so the
                    // activity actually grabs focus on Display 0 instead of
                    // sitting behind whatever stack was previously focused.
                    sh("am start -n $packageName/$escapedActivity --display 0 --windowingMode 1 -f 0x14000000")
                } else {
                    Log.e(TAG, "Could not launch $packageName: no launcher intent and no known activityName")
                }
            }
            preserveCarPlayClusterContract("LAUNCH_MAIN_AFTER_START_$packageName")
            preserveAndroidAutoClusterContract("LAUNCH_MAIN_AFTER_START_$packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching $packageName", e)
        }
    }

    /**
     * Finds the package name for the first task in a given stack by parsing the stack list.
     */
    private fun findPackageNameForStack(stackId: Int, stackList: String): String? {
        var inTargetStack = false
        for (line in stackList.lines()) {
            val stackMatch = Regex("""Stack id=(\d+)""").find(line)
            if (stackMatch != null) {
                inTargetStack = stackMatch.groupValues[1].toIntOrNull() == stackId
                continue
            }
            if (inTargetStack) {
                if (line.contains("Stack id=")) break

                val taskMatch = Regex("""taskId=(\d+):\s*([^/]+)/""").find(line)
                if (taskMatch != null) {
                    return taskMatch.groupValues[2]
                }
            }
        }
        return null
    }

    /**
     * Brings all applications from secondary displays (1 and 3) back to the main display (0).
     *
     * Returns the list of package names that were moved (empty list if no apps were
     * found on secondary displays). Callers can use this to decide whether to do a
     * follow-up "launch my preferred app" — when something was actually moved, that
     * app deserves focus and a follow-up launch would steal it. When nothing was
     * moved, the caller can safely launch a preferred app on top.
     */
    suspend fun bringAllToMainDisplay(): List<String> = withContext(Dispatchers.IO) {
        val movedPackages = mutableListOf<String>()
        val stackList = getStackList()
        val displaysToEvict = setOf(1, 3)
        val stacksToMove = mutableListOf<Pair<Int, Int>>() // stackId, displayId

        var currentDisplayId: Int? = null
        for (line in stackList.lines()) {
            val stackMatch = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
            if (stackMatch != null) {
                val stackId = stackMatch.groupValues[1].toIntOrNull()
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull()
                if (stackId != null && currentDisplayId != null && displaysToEvict.contains(currentDisplayId)) {
                    stacksToMove.add(stackId to currentDisplayId)
                }
            }
        }

        if (stacksToMove.isEmpty()) {
            Log.w(TAG, "No stacks found on displays 1 or 3 to move")
            return@withContext movedPackages
        }

        for ((stackId, displayId) in stacksToMove) {
            val pkg = findPackageNameForStack(stackId, stackList)
            Log.w(TAG, "Moving stack $stackId ($pkg) from display $displayId to display 0")

            if (pkg != null && isCarPlayPackage(pkg)) {
                movedPackages.add(pkg)
                CarPlayDisplayOrchestrator.openOnMain(
                    getCarPlayConfigForDisplay(0),
                    "BRING_ALL_TO_MAIN_CARPLAY"
                )
                continue
            }
            if (pkg != null && isAndroidAutoPackage(pkg)) {
                movedPackages.add(pkg)
                startAndroidAutoOnDisplay(getAndroidAutoConfigForDisplay(0), "BRING_ALL_TO_MAIN_ANDROID_AUTO")
                continue
            }

            val result = sh("am display move-stack $stackId 0")
            if (result.contains("Exception") || result.contains("Error")) {
                Log.e(TAG, "Failed to move stack $stackId: $result")
                continue
            }

            if (pkg != null) movedPackages.add(pkg)

            // Bring to front immediately after move.
            // -f 0x14000000 = FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_REORDER_TO_FRONT.
            // Without these flags the activity gets attached to Display 0 but the
            // previously-focused stack stays on top — user sees the other app
            // keep running, AA invisible in background.
            if (pkg != null) {
                val config = PREDEFINED_APPS.find { it.packageName == pkg }
                val activity = config?.activityName?.replace("$", "\\$")
                if (activity != null) {
                    // Force fullscreen and bring to front (with focus flags)
                    sh("am start -n $pkg/$activity --display 0 --windowingMode 1 -f 0x14000000")
                } else {
                    // Fallback to simple intent launch
                    val intent = App.getContext().packageManager.getLaunchIntentForPackage(pkg)
                    intent?.let {
                        it.addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                        App.getContext().startActivity(it)
                    }
                }
            }

            if (pkg != null) {
                if (!BottomBarState.restoredApps.contains(pkg)) {
                    withContext(Dispatchers.Main) {
                        BottomBarState.restoredApps.add(pkg)
                    }
                }
            }

            val res = getDisplayResolution(0)
            // v2.3: respect overscan when restoring to Display 0 fullscreen
            val effectiveHeight = if (pkg != null) applyOverscanToDisplay0Height(pkg, res.second) else res.second
            sh("am stack resize $stackId 0 0 ${res.first} $effectiveHeight")
        }

        displaysToEvict.forEach { notifyDisplayStateChanged(it) }
        notifyBottomBarUpdate()
        movedPackages
    }


    /**
     * Sends the app to the target secondary display with saved bounds.
     * Uses am display move-stack to preserve app state (no kill).
     * If another configured app is already on the target display, brings it back to display 0 first
     * (only 1 app per secondary display is supported).
     */
    suspend fun sendToDisplay(config: DisplayAppConfig) = withContext(Dispatchers.IO) {
        try {
            if (isCarPlayPackage(config.packageName)) {
                CarPlayDisplayOrchestrator.start(config, "SEND_TO_DISPLAY")
                return@withContext
            }
            if (isAndroidAutoPackage(config.packageName)) {
                startAndroidAutoOnDisplay(config, "SEND_TO_DISPLAY")
                return@withContext
            }

            val bounds = getEffectiveBounds(config)

            // Already on target display — just resize
            val existing = findStackIdForPackage(config.packageName, config.displayId)
            if (existing != null) {
                sh("am stack resize $existing ${bounds[0]} ${bounds[1]} ${bounds[2]} ${bounds[3]}")
                return@withContext
            }

            // Evict any other configured app already on this display → move it back to display 0
            evictOtherAppsFromDisplay(config.displayId, config.packageName)

            val taskInfo = findTaskForPackage(config.packageName)
            if (taskInfo == null) {
                // App not running — launch fresh
                launchApp(config)

                return@withContext
            }

            // Concurrency/state safeguard: if the task is already physically running on the target display,
            // skip the move-stack IPC call entirely to avoid IllegalArgumentException / race conditions.
            if (taskInfo.displayId == config.displayId) {
                Log.w(TAG, "Stack ${taskInfo.stackId} is already on target display ${config.displayId}, skipping move-stack and performing direct resize")
                sh("am stack resize ${taskInfo.stackId} ${bounds[0]} ${bounds[1]} ${bounds[2]} ${bounds[3]}")
                return@withContext
            }

            // Save current bounds before moving away from current display
            saveCurrentBounds(config.packageName, taskInfo)

            // Safety: check this stack only has our app's task
            val tasksInStack = countTasksInStack(taskInfo.stackId)
            if (tasksInStack > 1) {
                Log.w(TAG, "Stack ${taskInfo.stackId} has $tasksInStack tasks, falling back to launchApp")
                launchApp(config)

                return@withContext
            }

            // Move the app's stack to the target display (preserves state!)
            Log.w(TAG, "Moving stack ${taskInfo.stackId} to display ${config.displayId}")
            val result = sh("am display move-stack ${taskInfo.stackId} ${config.displayId}")
            if (result.contains("Exception") || result.contains("Error")) {
                Log.w(TAG, "move-stack failed: $result, falling back to launchApp")
                launchApp(config)

                return@withContext
            }

            // Remove from restored state since it is now on a secondary display
            if (BottomBarState.restoredApps.contains(config.packageName)) {
                BottomBarState.restoredApps.remove(config.packageName)
            }

            // Resize with configured bounds
            Thread.sleep(200)
            val stackId = findStackIdForPackage(config.packageName, config.displayId)
            if (stackId != null) {
                sh("am stack resize $stackId ${bounds[0]} ${bounds[1]} ${bounds[2]} ${bounds[3]}")
            }

            Log.w(TAG, "App moved to display ${config.displayId} with bounds, state preserved")
            notifyDisplayStateChanged(config.displayId)

            // Trigger focus poke immediately after move with a small delay for stability
            if (config.packageName == "com.ts.androidauto.app" || config.packageName == "com.ts.carplay.app") {
                CoroutineScope(Dispatchers.IO).launch {
                    delay(500)
                    syncInterconnectionFocus("MANUAL_MOVE_P1")
                    delay(1000)
                    syncInterconnectionFocus("MANUAL_MOVE_P2")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to display", e)
        }
    }

    /**
     * Moves any other app currently on the target display back to display 0.
     * Only 1 app per secondary display is supported by the hardware.
     */
    private fun evictOtherAppsFromDisplay(displayId: Int, excludePackage: String) {
        if (displayId == 0) return

        val stackList = getStackList()
        val stacksToEvict = mutableListOf<Int>()

        var currentDisplayId: Int? = null
        for (line in stackList.lines()) {
            val m = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
            if (m != null) {
                val sId = m.groupValues[1].toIntOrNull()
                currentDisplayId = m.groupValues[2].toIntOrNull()
                if (sId != null && currentDisplayId == displayId) {
                    stacksToEvict.add(sId)
                }
            }
        }

        if (stacksToEvict.isEmpty()) return

        for (stackId in stacksToEvict) {
            val pkg = findPackageNameForStack(stackId, stackList)
            if (pkg == null || pkg == excludePackage || pkg == "com.android.systemui") continue

            if (isCarPlayPackage(pkg)) {
                Log.w(TAG, "Evicting CarPlay visual stack $stackId from display $displayId without force-stop")
                sh("am stack remove $stackId")
                continue
            }
            if (isAndroidAutoPackage(pkg)) {
                Log.w(TAG, "Evicting Android Auto stack $stackId from display $displayId to display 0 without stopping projection service")
                rememberAndroidAutoDisplayTarget(0, "EVICTION_ANDROID_AUTO")
                val task = findTaskForPackage(pkg)
                if (task != null) {
                    saveCurrentBounds(pkg, task)
                }
                val result = sh("am display move-stack $stackId 0")
                if (result.contains("Exception") || result.contains("Error")) {
                    Log.e(TAG, "Failed to evict Android Auto: $result")
                    continue
                }

                val movedTask = findTaskForPackageOnDisplay(ANDROID_AUTO_PACKAGE, 0)
                if (movedTask != null) {
                    val res = getDisplayResolution(0)
                    val effectiveHeight = applyOverscanToDisplay0Height(pkg, res.second)
                    resizeAndFocusAndroidAuto(
                        movedTask,
                        0,
                        intArrayOf(0, 0, res.first, effectiveHeight),
                        "EVICTION_ANDROID_AUTO"
                    )
                }

                if (!BottomBarState.restoredApps.contains(pkg)) {
                    BottomBarState.restoredApps.add(pkg)
                }
                continue
            }

            Log.w(TAG, "Evicting $pkg (stack $stackId) from display $displayId → display 0")

            val task = findTaskForPackage(pkg)
            if (task != null) {
                saveCurrentBounds(pkg, task)
            }

            val result = sh("am display move-stack $stackId 0")
            if (result.contains("Exception") || result.contains("Error")) {
                Log.e(TAG, "Failed to evict $pkg: $result")
                continue
            }

            // Bring to front on display 0
            if (task != null) {
                val config = PREDEFINED_APPS.find { it.packageName == pkg }
                val activity = config?.activityName?.replace("$", "\\$")
                if (activity != null) {
                    sh("am start -n $pkg/$activity --display 0 --windowingMode 1")
                } else {
                    val intent = App.getContext().packageManager.getLaunchIntentForPackage(pkg)
                    intent?.let {
                        it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        App.getContext().startActivity(it)
                    }
                }

                // Mark evicted app as restored
                if (!BottomBarState.restoredApps.contains(pkg)) {
                    BottomBarState.restoredApps.add(pkg)
                }

                val cached = lastKnownDisplayBounds[pkg]?.get(0)
                val overscanPx = getOverscanForPackage(pkg)
                val density = App.getContext().resources.displayMetrics.density
                val overscanDp = (overscanPx / density).toInt()

                if (cached != null) {
                    var y2 = cached[3]
                    if (y2 >= 710) y2 = 720
                    // v2.3: respect overscan
                    y2 = applyOverscanToDisplay0Height(pkg, y2)
                    Log.w(TAG, "[EVICTION] RESTORE App: $pkg | Bounds: [${cached[0]},${cached[1]},${cached[2]},$y2] | Overscan: ${overscanDp}dp | Mode: 1")
                    sh("am stack resize $stackId ${cached[0]} ${cached[1]} ${cached[2]} $y2")
                } else {
                    val res = getDisplayResolution(0)
                    // v2.3: respect overscan
                    val effectiveHeight = applyOverscanToDisplay0Height(pkg, res.second)
                    Log.w(TAG, "[EVICTION] FALLBACK App: $pkg | Bounds: [0,0,${res.first},$effectiveHeight] | Overscan: ${overscanDp}dp | Mode: 1")
                    sh("am stack resize $stackId 0 0 ${res.first} $effectiveHeight")
                }
            }
        }
        notifyDisplayStateChanged(displayId)
        notifyBottomBarUpdate()
    }

    // --- Stack parsing helpers ---

    private fun saveCurrentBounds(packageName: String, taskInfo: TaskInfo? = null) {
        val info = taskInfo ?: findTaskForPackage(packageName) ?: return
        if (info.bounds != null) {
            val packageMap = lastKnownDisplayBounds.getOrPut(packageName) { mutableMapOf() }
            packageMap[info.displayId] = info.bounds
            Log.w(TAG, "SAVED bounds for $packageName on display ${info.displayId}: [${info.bounds.joinToString(",")}]")
        } else {
            Log.w(TAG, "NO BOUNDS captured for $packageName on display ${info.displayId} (was null)")
        }
    }

    private data class BarSettings(val overscan: Int, val yOffset: Int)

    private fun getOverscanForPackage(packageName: String): Int {
        val prefs = getPrefs()
        val density = App.getContext().resources.displayMetrics.density

        // Priority 1: Dynamic Overrides from SharedPreferences (User defined)
        val overridesJson = prefs.getString(SharedPreferencesKeys.BOTTOM_BAR_OVERRIDES.key, null)
        if (overridesJson != null) {
            try {
                val type = object : TypeToken<Map<String, BarSettings>>() {}.type
                val overrides: Map<String, BarSettings> = gson.fromJson(overridesJson, type)
                val settings = overrides[packageName]
                if (settings != null) {
                    val overscanValueRaw = settings.overscan
                    val overscanValuePx = (overscanValueRaw * density).toInt()
                    val yOffsetPx = (settings.yOffset * density).toInt()

                    Log.w(
                        "BottomBarService",
                        "[OVERSCAN_SYNC] App: $packageName | Overscan: ${overscanValueRaw}dp(${overscanValuePx}px) | Offset: ${settings.yOffset}dp(${yOffsetPx}px) | Visible: ${BottomBarState.isVisible}"
                    )
                    return overscanValuePx
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing BOTTOM_BAR_OVERRIDES for $packageName", e)
            }
        }

        // Priority 2: Hardcoded App Overrides (matching BottomBarService)
        val hardcodedOverscan = when (packageName) {
            "com.google.android.apps.messaging", "deezer.android.app" -> 60
            // AA's projected UI tends to put navigation controls right at
            // the bottom edge; the default 20dp bar overlap is too tight
            // and clips the AA system bar. 30dp keeps everything visible.
            "com.ts.androidauto.app" -> 30
            else -> null
        }
        if (hardcodedOverscan != null) return (hardcodedOverscan * density).toInt()

        // Priority 3: Global Default
        val globalDefault = prefs.getInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 0)
        return (globalDefault * density).toInt()
    }

    fun isAnyAppOnDisplay(displayId: Int): Boolean {
        return getTopPackageOnDisplay(displayId) != null
    }

    fun getTopPackageOnDisplay(displayId: Int): String? {
        try {
            val stackList = getStackList()
            var currentDisplayId: Int? = null
            val regex = Regex("""taskId=\d+:\s*([a-zA-Z0-9._]+)/""")

            for (line in stackList.lines()) {
                val stackMatch = Regex("""displayId=(\d+)""").find(line)
                if (stackMatch != null) {
                    currentDisplayId = stackMatch.groupValues[1].toIntOrNull()
                }
                if (currentDisplayId == displayId) {
                    val match = regex.find(line)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
            }

            // Fallback to dumpsys if am stack list is not helping
            val output = ShizukuUtils.runCommandAndGetOutput(
                arrayOf("sh", "-c", "dumpsys activity activities | sed -n '/Display #$displayId/,/Display #/p' | grep -E 'mResumedActivity|mCurrentFocus|mFocusedActivity'")
            )
            val regex2 = Regex("""([a-zA-Z0-9._]+)/[.${'$'}a-zA-Z0-9._]+""")
            val match = regex2.find(output)
            return match?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top package for display $displayId", e)
        }
        return null
    }

    fun notifyDisplayStateChanged(displayId: Int) {
        scope.launch {
            // Check multiple times with increasing delays to ensure system has updated stack state
            val delays = listOf(0L, 500L, 1000L)
            for (d in delays) {
                if (d > 0) delay(d)
                val isActive = isAnyAppOnDisplay(displayId)
                val eventType = when (displayId) {
                    1 -> br.com.redesurftank.havalshisuku.models.ServiceManagerEventType.DISPLAY_1_APP_STATE_CHANGED
                    3 -> br.com.redesurftank.havalshisuku.models.ServiceManagerEventType.DISPLAY_3_APP_STATE_CHANGED
                    else -> null
                }

                if (eventType != null) {
                    Log.w(TAG, "Display $displayId app state changed (delay $d): isActive=$isActive")
                    ServiceManager.getInstance().dispatchServiceManagerEvent(eventType, isActive)
                }

                // If we found it active, we're likely done with launch updates
                if (isActive) break
            }
        }
    }

    private fun getStackList(): String {
        return ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "am stack list 2>&1"))
    }

    private data class StackInfo(val stackId: Int, val windowingMode: String, val isFreeform: Boolean)
    private data class StackTaskInfo(
        val taskId: Int,
        val packageName: String,
        val activityName: String,
        val displayId: Int
    )
    data class TaskInfo(val taskId: Int, val stackId: Int, val displayId: Int, val bounds: IntArray? = null)

    private fun findTaskMatchingOnDisplay(displayId: Int, matcher: (String) -> Boolean): TaskInfo? {
        return findTaskMatching { packageName, taskDisplayId ->
            taskDisplayId == displayId && matcher(packageName)
        }
    }

    private fun findTaskMatching(matcher: (String, Int) -> Boolean): TaskInfo? {
        var currentStackId: Int? = null
        var currentDisplayId: Int? = null
        var currentBounds: IntArray? = null

        for (line in getStackList().lines()) {
            val stackMatch = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
            if (stackMatch != null) {
                currentStackId = stackMatch.groupValues[1].toIntOrNull()
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull()
                val boundsMatch = Regex("""[m]?bounds=\[(\d+),(\d+)\]\[(\d+),(\d+)\]""").find(line)
                currentBounds = if (boundsMatch != null) {
                    intArrayOf(
                        boundsMatch.groupValues[1].toInt(),
                        boundsMatch.groupValues[2].toInt(),
                        boundsMatch.groupValues[3].toInt(),
                        boundsMatch.groupValues[4].toInt()
                    )
                } else {
                    currentDisplayId?.let { id ->
                        val res = getDisplayResolution(id)
                        intArrayOf(0, 0, res.first, res.second)
                    }
                }
                continue
            }

            val taskMatch = Regex("""taskId=(\d+):\s*([a-zA-Z0-9._]+)/""").find(line)
            if (taskMatch != null && currentStackId != null && currentDisplayId != null) {
                val taskId = taskMatch.groupValues[1].toIntOrNull() ?: continue
                val packageName = taskMatch.groupValues[2]
                val displayIdForTask = currentDisplayId ?: continue
                if (matcher(packageName, displayIdForTask)) {
                    return TaskInfo(taskId, currentStackId!!, displayIdForTask, currentBounds)
                }
            }
        }

        return null
    }

    private fun findAllTasksForPackage(packageName: String, stackList: String = getStackList()): List<TaskInfo> {
        val tasks = mutableListOf<TaskInfo>()
        var currentStackId: Int? = null
        var currentDisplayId: Int? = null
        var currentBounds: IntArray? = null

        for (line in stackList.lines()) {
            val stackMatch = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
            if (stackMatch != null) {
                currentStackId = stackMatch.groupValues[1].toIntOrNull()
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull()
                val bMatch = Regex("""[m]?bounds=\[(\d+),(\d+)\]\[(\d+),(\d+)\]""").find(line)
                currentBounds = if (bMatch != null) {
                    intArrayOf(
                        bMatch.groupValues[1].toInt(),
                        bMatch.groupValues[2].toInt(),
                        bMatch.groupValues[3].toInt(),
                        bMatch.groupValues[4].toInt()
                    )
                } else {
                    currentDisplayId?.let { id ->
                        val res = getDisplayResolution(id)
                        intArrayOf(0, 0, res.first, res.second)
                    }
                }
                continue
            }

            val taskMatch = Regex("""taskId=(\d+):\s*\Q$packageName\E/""").find(line)
            if (taskMatch != null && currentStackId != null && currentDisplayId != null) {
                val taskId = taskMatch.groupValues[1].toIntOrNull() ?: continue
                tasks.add(TaskInfo(taskId, currentStackId, currentDisplayId, currentBounds))
            }
        }
        return tasks
    }

    private fun findTaskForPackageOnDisplay(packageName: String, displayId: Int): TaskInfo? {
        return findAllTasksForPackage(packageName).firstOrNull { it.displayId == displayId }
    }

    fun findTaskForPackage(packageName: String): TaskInfo? {
        var currentStackId: Int? = null
        var currentDisplayId: Int? = null
        var currentBounds: IntArray? = null

        val stackList = getStackList()
        for (line in stackList.lines()) {
            val stackMatch = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
            if (stackMatch != null) {
                currentStackId = stackMatch.groupValues[1].toIntOrNull()
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull()

                // Debug: Log the stack line to see bounds format
                if (line.contains("bounds=")) {
                    Log.d(TAG, "Found stack line with bounds: $line")
                }

                // Try to extract bounds if present in the stack line
                // We check both "bounds=" and "mBounds=" as different Android versions use different labels
                val bMatch = Regex("""[m]?bounds=\[(\d+),(\d+)\]\[(\d+),(\d+)\]""").find(line)
                if (bMatch != null) {
                    currentBounds = intArrayOf(
                        bMatch.groupValues[1].toInt(),
                        bMatch.groupValues[2].toInt(),
                        bMatch.groupValues[3].toInt(),
                        bMatch.groupValues[4].toInt()
                    )
                } else {
                    // Start with display resolution as default for the stack if no bounds specified
                    currentDisplayId?.let { id ->
                        val res = getDisplayResolution(id)
                        currentBounds = intArrayOf(0, 0, res.first, res.second)
                    }
                }
            }

            val taskMatch = Regex("""taskId=(\d+):\s*\Q$packageName\E/""").find(line)
            if (taskMatch != null && currentStackId != null && currentDisplayId != null) {
                val taskId = taskMatch.groupValues[1].toIntOrNull()

                // Final check: the task line ITSELF might have the bounds in some scenarios
                val tbMatch = Regex("""[m]?bounds=\[(\d+),(\d+)\]\[(\d+),(\d+)\]""").find(line)
                if (tbMatch != null) {
                    currentBounds = intArrayOf(
                        tbMatch.groupValues[1].toInt(),
                        tbMatch.groupValues[2].toInt(),
                        tbMatch.groupValues[3].toInt(),
                        tbMatch.groupValues[4].toInt()
                    )
                }

                if (taskId != null) return TaskInfo(taskId, currentStackId, currentDisplayId, currentBounds)
            }
        }
        return null
    }

    fun findFirstTasksForPackages(packageNames: Collection<String>): Map<String, TaskInfo> {
        val uniquePackages = packageNames.filter { it.isNotBlank() }.toSet()
        if (uniquePackages.isEmpty()) return emptyMap()

        val stackList = getStackList()
        val result = mutableMapOf<String, TaskInfo>()
        uniquePackages.forEach { packageName ->
            findAllTasksForPackage(packageName, stackList).firstOrNull()?.let { task ->
                result[packageName] = task
            }
        }
        return result
    }

    private fun findStackInfoForPackage(packageName: String, displayId: Int): StackInfo? {
        var currentStackId: Int? = null
        var currentDisplayId: Int? = null
        var currentWindowingMode: String? = null

        for (line in getStackList().lines()) {
            val stackMatch = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
            if (stackMatch != null) {
                currentStackId = stackMatch.groupValues[1].toIntOrNull()
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull()
                currentWindowingMode = null
            }
            val wmMatch = Regex("""mWindowingMode=(\S+)""").find(line)
            if (wmMatch != null && currentWindowingMode == null) {
                currentWindowingMode = wmMatch.groupValues[1].trimEnd('}')
            }
            if (currentDisplayId == displayId && currentStackId != null &&
                Regex("""taskId=\d+:\s*\Q$packageName\E/""").containsMatchIn(line)) {
                val wm = currentWindowingMode ?: "unknown"
                return StackInfo(currentStackId, wm, wm == "freeform")
            }
        }
        return null
    }

    private fun findStackIdForPackage(packageName: String, displayId: Int): Int? {
        return findStackInfoForPackage(packageName, displayId)?.stackId
    }

    private fun getAllStackIdsOnDisplay(displayId: Int): Set<Int> {
        val ids = mutableSetOf<Int>()
        for (line in getStackList().lines()) {
            val m = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line) ?: continue
            val stackId = m.groupValues[1].toIntOrNull() ?: continue
            val dId = m.groupValues[2].toIntOrNull() ?: continue
            if (dId == displayId) ids.add(stackId)
        }
        return ids
    }

    /**
     * Counts how many tasks are in a specific stack.
     * Used to verify a stack only has our target app before move-stack.
     */
    private fun countTasksInStack(stackId: Int): Int {
        var inTargetStack = false
        var count = 0
        for (line in getStackList().lines()) {
            val stackMatch = Regex("""Stack id=(\d+)""").find(line)
            if (stackMatch != null) {
                inTargetStack = stackMatch.groupValues[1].toIntOrNull() == stackId
            }
            if (inTargetStack && Regex("""taskId=\d+:""").containsMatchIn(line)) {
                count++
            }
        }
        return count
    }

    private fun findOtherTaskInStack(stackId: Int, excludePackage: String): StackTaskInfo? {
        var inTargetStack = false
        var currentDisplayId: Int? = null
        for (line in getStackList().lines()) {
            val stackMatch = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
            if (stackMatch != null) {
                inTargetStack = stackMatch.groupValues[1].toIntOrNull() == stackId
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull()
                continue
            }

            if (!inTargetStack) continue

            val taskMatch = Regex("""taskId=(\d+):\s*([^/]+)/([^\s]+)""").find(line) ?: continue
            val taskId = taskMatch.groupValues[1].toIntOrNull() ?: continue
            val packageName = taskMatch.groupValues[2]
            val activityName = taskMatch.groupValues[3]
            if (packageName != excludePackage) {
                return StackTaskInfo(
                    taskId = taskId,
                    packageName = packageName,
                    activityName = activityName,
                    displayId = currentDisplayId ?: 0
                )
            }
        }
        return null
    }

    private fun findFirstNonProjectionTaskOnDisplay(displayId: Int): StackTaskInfo? {
        var currentDisplayId: Int? = null
        for (line in getStackList().lines()) {
            val stackMatch = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
            if (stackMatch != null) {
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull()
                continue
            }

            if (currentDisplayId != displayId) continue

            val taskMatch = Regex("""taskId=(\d+):\s*([^/]+)/([^\s]+)""").find(line) ?: continue
            val taskId = taskMatch.groupValues[1].toIntOrNull() ?: continue
            val packageName = taskMatch.groupValues[2]
            val activityName = taskMatch.groupValues[3]
            if (
                packageName == "com.android.systemui" ||
                packageName == App.getContext().packageName ||
                isProjectionMirrorPackage(packageName)
            ) {
                continue
            }

            return StackTaskInfo(
                taskId = taskId,
                packageName = packageName,
                activityName = activityName,
                displayId = displayId
            )
        }
        return null
    }

    suspend fun enableFreeformMode() = withContext(Dispatchers.IO) {
        try {
            sh("settings put global enable_freeform_support 1")
            sh("settings put global force_resizable_activities 1")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling freeform mode", e)
        }

    }

    // Cooldown to prevent restart loops
    private val recentlyFixed = HashMap<String, Long>()
    private const val COOLDOWN_MS = 10_000L

    /**
     * Called by AccessibilityService when any app window changes.
     * Only fixes apps in split-screen-secondary mode (broken for resize).
     * fullscreen mode works fine after move-stack.
     */
    fun onAppWindowChanged(packageName: String) {
        preserveCarPlayClusterContractAfterWindowChange(packageName)
        preserveAndroidAutoClusterContractAfterWindowChange(packageName)

        val config = getAllConfigs().find { it.packageName == packageName } ?: return
        if (config.displayId == 0) return

        val now = System.currentTimeMillis()
        val lastFixed = recentlyFixed[packageName] ?: 0
        if (now - lastFixed < COOLDOWN_MS) return

        scope.launch {
            Thread.sleep(500)

            val info = findStackInfoForPackage(packageName, config.displayId) ?: return@launch

            if (info.windowingMode == "split-screen-secondary") {
                recentlyFixed[packageName] = System.currentTimeMillis()
                Log.w(TAG, "Detected $packageName in ${info.windowingMode}, restarting in freeform")

                val bounds = getEffectiveBounds(config)
                val x = bounds[0]
                val y = bounds[1]
                val right = bounds[2]
                val bottom = bounds[3]

                val escapedActivity = config.activityName.replace("$", "\\$")
                sh("am force-stop $packageName")
                Thread.sleep(200)
                sh("am start -n $packageName/$escapedActivity --display ${config.displayId} --windowingMode 5")
                Thread.sleep(300)
                val stackId = findStackIdForPackage(packageName, config.displayId)
                if (stackId != null) {
                    sh("am stack resize $stackId $x $y $right $bottom")
                }
            }
        }
    }

    fun getDisplayResolution(displayId: Int): Pair<Int, Int> {
        val dm = App.getDeviceProtectedContext()
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(displayId) ?: return Pair(1920, 720)
        val mode = display.mode
        return Pair(mode.physicalWidth, mode.physicalHeight)
    }

    fun hasAnyForceFocusApp(): Boolean = getAllConfigs().any { it.forceFocus }

    fun syncInterconnectionFocus(triggerSource: String) {
        val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)


        val forceFocusConfigs = getAllConfigs().filter { it.forceFocus }
        if (forceFocusConfigs.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            for (config in forceFocusConfigs) {
                val taskInfo = findTaskForPackage(config.packageName)
                if (taskInfo == null) continue
                if (taskInfo.displayId != 1 && taskInfo.displayId != 3) continue

                Log.w("FOCUS_SYNC", "Executing focus poke (Trigger: $triggerSource, Display: ${taskInfo.displayId}, App: ${config.packageName})")
                if (isCarPlayPackage(config.packageName)) {
                    configureCarPlayProjection("FOCUS_SYNC_$triggerSource")
                    sendCarPlayFocus(taskInfo.displayId, "FOCUS_SYNC_$triggerSource")
                } else if (isAndroidAutoPackage(config.packageName)) {
                    configureAndroidAutoProjection("FOCUS_SYNC_$triggerSource")
                    sendAndroidAutoFocus(taskInfo.displayId, "FOCUS_SYNC_$triggerSource")
                } else {
                    val sb = StringBuilder()
                    // 1. CARPLAY focus broadcast
                    sb.append("am broadcast -a com.ts.carplay.action.VIDEO_FOCUS_CHANGE --es \"focus\" \"${config.packageName}\" --ei \"displayId\" ${taskInfo.displayId}; ")
                    // 2. ANDROID AUTO focus broadcast
                    sb.append("am broadcast -a com.ts.androidauto.action.AndroidAutoService --es \"command\" \"requestVideoFocus\" --ei \"displayId\" ${taskInfo.displayId}; ")
                    // 3. Force activity to front with aggressive flags (NEW_TASK | REORDER_TO_FRONT | CLEAR_TOP)
                    val escapedActivity = config.activityName.replace("$", "\\$")
                    sb.append("am start -n ${config.packageName}/$escapedActivity --display ${taskInfo.displayId} --windowingMode 1 -f 0x14000000; ")
                    sh(sb.toString())
                }
            }
        }
    }


    fun discoverAndroidAutoBroadcasts(): List<String> {
        val discoveredActions = mutableSetOf<String>()
        val packages = listOf(
            "com.ts.androidauto.app",
            "com.ts.androidauto.projectionservice",
            "com.ts.androidauto",
            "com.ts.carplay.app",
            "com.ts.carplay"
        )

        packages.forEach { pkg ->
            try {
                val output = ShizukuUtils.runCommandAndGetOutput(arrayOf("dumpsys", "package", pkg))
                // Improved regex to capture actions
                val regex = Regex("""Action:\s+"([^"]+)"""")
                regex.findAll(output).forEach { match ->
                    val action = match.groupValues[1]
                    if (action.contains("androidauto") || action.contains("carplay") || action.contains("mirror") || action.contains("link")) {
                        discoveredActions.add(action)
                    }
                }

                // Also look for actions that don't have quotes
                val regex2 = Regex("""action\s+([a-zA-Z0-9._]+)""")
                regex2.findAll(output).forEach { match ->
                    val action = match.groupValues[1]
                    if (action.contains("androidauto") || action.contains("carplay") || action.contains("mirror") || action.contains("link")) {
                        discoveredActions.add(action)
                    }
                }
            } catch (e: Exception) {
                Log.e("AA_DISCOVERY", "Error discovering broadcasts for $pkg", e)
            }
        }

        // Add some hardcoded ones that we found via adb
        discoveredActions.add("ts.car.androidauto.view_state")
        discoveredActions.add("com.ts.androidauto.adapter.resource.RECEIVER_CLICK_ACTION")

        Log.w("AA_DISCOVERY", "v2 Discovered Actions: ${discoveredActions.joinToString(", ")}")
        return discoveredActions.toList()
    }


}
