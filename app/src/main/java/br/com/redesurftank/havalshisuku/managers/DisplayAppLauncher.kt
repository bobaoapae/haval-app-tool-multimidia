package br.com.redesurftank.havalshisuku.managers

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
import br.com.redesurftank.havalshisuku.managers.ThemeManager
import br.com.redesurftank.havalshisuku.models.BottomBarState

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

    private const val TAG = "DisplayAppLauncher"

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Memory cache for app bounds per display: packageName -> Map<displayId, bounds>
    private val lastKnownDisplayBounds = mutableMapOf<String, MutableMap<Int, IntArray>>()

    private fun getPrefs() =
        App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

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

    fun deleteConfig(packageName: String) {
        val configs = getAllConfigs().toMutableList()
        configs.removeAll { it.packageName == packageName }
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
        val prefs = getPrefs()
        val alwaysUseTheme = prefs.getBoolean(SharedPreferencesKeys.ALWAYS_USE_THEME_DIMENSIONS.key, true)
        val virtualClusterEnabled = prefs.getBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, true)
        
        var x = config.x
        var y = config.y
        var width = config.width
        var height = config.height

        if (alwaysUseTheme && virtualClusterEnabled && config.displayId == 3) {
            val themeFolderName = prefs.getString(SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, "Default") ?: "Default"
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
        return intArrayOf(x, y, x + width, y + height)
    }

    suspend fun launchApp(config: DisplayAppConfig) {
        withContext(Dispatchers.IO) {
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
            val x = config.x
            val y = config.y
            val right = config.x + config.width
            val bottom = config.y + config.height
            
            val stackId = findStackIdForPackage(config.packageName, config.displayId)
            if (stackId != null) {
                sh("am stack resize $stackId $x $y $right $bottom")
                ServiceManager.getInstance().dispatchServiceManagerEvent(
                    br.com.redesurftank.havalshisuku.models.ServiceManagerEventType.APP_GEOMETRY_CHANGED
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resizing app ${config.packageName}", e)
        }
    }

    /**
     * Kills an app via am force-stop.
     */
    suspend fun killApp(packageName: String) = withContext(Dispatchers.IO) {
        try {
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
            // First try to find if it's already on another display and move it
            val taskInfo = findTaskForPackage(packageName)
            if (taskInfo != null && taskInfo.displayId != 0) {
                // Save current bounds before moving away
                saveCurrentBounds(packageName, taskInfo)

                Log.w(TAG, "Moving stack ${taskInfo.stackId} to display 0")
                val result = sh("am display move-stack ${taskInfo.stackId} 0")
                notifyDisplayStateChanged(taskInfo.displayId)
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
                                Log.w(TAG, "[DISPLAY_MOVE] RESTORE App: $packageName | Bounds: [${cached[0]},${cached[1]},${cached[2]},$y2] (Full Height) | Overscan: ${overscanDp}dp | Mode: 1")
                            } else {
                                Log.w(TAG, "[DISPLAY_MOVE] RESTORE App: $packageName | Bounds: [${cached.joinToString(",")}] | Overscan: ${overscanDp}dp | Mode: 1")
                            }
                            sh("am stack resize ${movedTask.stackId} ${cached[0]} ${cached[1]} ${cached[2]} $y2")
                        } else {
                            val res = getDisplayResolution(0)
                            val effectiveHeight = res.second
                            Log.w(TAG, "[DISPLAY_MOVE] FALLBACK App: $packageName | Bounds: [0,0,${res.first},$effectiveHeight] (Full Height) | Overscan: ${overscanDp}dp | Mode: 1")
                            sh("am stack resize ${movedTask.stackId} 0 0 ${res.first} $effectiveHeight")
                        }
                        
                        // Force BottomBar to re-apply overscan immediately
                        notifyBottomBarUpdate()
                        return@withContext
                    }
                }
            }

            // Standard intent launch is most reliable for the main display
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                Log.w(TAG, "Launching $packageName via Intent")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
            } else if (activityName != null) {
                // Fallback to am start if intent is somehow null
                val escapedActivity = activityName.replace("$", "\\$")
                sh("am force-stop $packageName")
                Thread.sleep(200)
                sh("am start -n $packageName/$escapedActivity --display 0")
            } else {
                Log.e(TAG, "Could not find launch intent for $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching $packageName", e)
        }
    }

    /**
     * Sends the app to the target secondary display with saved bounds.
     * Uses am display move-stack to preserve app state (no kill).
     * If another configured app is already on the target display, brings it back to display 0 first
     * (only 1 app per secondary display is supported).
     */
    suspend fun sendToDisplay(config: DisplayAppConfig) = withContext(Dispatchers.IO) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to display", e)
        }
    }

    /**
     * Moves any other configured app currently on the target display back to display 0.
     * Only 1 app per secondary display is supported by the hardware.
     */
    private fun evictOtherAppsFromDisplay(displayId: Int, excludePackage: String) {
        val configs = getAllConfigs()
        for (cfg in configs) {
            val pkg = cfg.packageName
            if (pkg == excludePackage) continue
            if (cfg.displayId != displayId) continue
            val task = findTaskForPackage(pkg) ?: continue
            if (task.displayId != displayId) continue

            Log.w(TAG, "Evicting $pkg from display $displayId → display 0")
            // Save current bounds before eviction
            saveCurrentBounds(pkg, task)

            val result = sh("am display move-stack ${task.stackId} 0")
            val movedTask = findTaskForPackage(pkg)
            if (movedTask != null && movedTask.displayId == 0) {
                // Mark evicted app as restored
                if (!BottomBarState.restoredApps.contains(pkg)) {
                    BottomBarState.restoredApps.add(pkg)
                }
                val cached = lastKnownDisplayBounds[pkg]?.get(0)
                val overscanPx = getOverscanForPackage(pkg)
                val density = App.getContext().resources.displayMetrics.density
                val overscanDp = (overscanPx / density).toInt()

                sh("am stack set-windowing-mode ${movedTask.stackId} 1")
                
                if (cached != null) {
                    var y2 = cached[3]
                    if (y2 >= 710) y2 = 720
                    Log.w(TAG, "[EVICTION] RESTORE App: $pkg | Bounds: [${cached[0]},${cached[1]},${cached[2]},$y2] (Full Height) | Overscan: ${overscanDp}dp | Mode: 1")
                    sh("am stack resize ${movedTask.stackId} ${cached[0]} ${cached[1]} ${cached[2]} $y2")
                } else {
                    val res = getDisplayResolution(0)
                    val effectiveHeight = res.second
                    Log.w(TAG, "[EVICTION] FALLBACK App: $pkg | Bounds: [0,0,${res.first},$effectiveHeight] (Full Height) | Overscan: ${overscanDp}dp | Mode: 1")
                    sh("am stack resize ${movedTask.stackId} 0 0 ${res.first} $effectiveHeight")
                }
                notifyBottomBarUpdate()
            }
        }
        notifyDisplayStateChanged(displayId)
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
            else -> null
        }
        if (hardcodedOverscan != null) return (hardcodedOverscan * density).toInt()

        // Priority 3: Global Default
        val globalDefault = prefs.getInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 0)
        return (globalDefault * density).toInt()
    }

    fun isAnyAppOnDisplay(displayId: Int): Boolean {
        var currentDisplayId: Int? = null
        for (line in getStackList().lines()) {
            val stackMatch = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
            if (stackMatch != null) {
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull()
            }
            if (currentDisplayId == displayId && Regex("""taskId=\d+:""").containsMatchIn(line)) {
                return true
            }
        }
        return false
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
    data class TaskInfo(val taskId: Int, val stackId: Int, val displayId: Int, val bounds: IntArray? = null)

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
}
