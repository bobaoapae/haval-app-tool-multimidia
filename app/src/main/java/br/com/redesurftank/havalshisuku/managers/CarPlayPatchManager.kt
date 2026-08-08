package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object CarPlayPatchManager {
    private const val TAG = "CARPLAY_PATCH_MGR"
    private const val PATCH_DIR = "/data/local/tmp/carplay_patches"
    private const val APP_APK = "TsCarPlayApp.apk"
    private const val SERVICE_APK = "TsCarPlayService.apk"
    private const val PATCH_RUNTIME_ENABLED = true
    private const val DISABLED_REASON =
        "CarPlay HVAC focus patch runtime disabled by feature flag"

    const val SYSTEM_APP_PATH = "/system/app/TsCarPlayApp/TsCarPlayApp.apk"
    const val SYSTEM_SERVICE_PATH = "/vendor/app/TsCarPlayService/TsCarPlayService.apk"
    private const val SYSTEM_APP_OAT = "/system/app/TsCarPlayApp/oat"
    private const val SYSTEM_SERVICE_OAT = "/vendor/app/TsCarPlayService/oat"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val APP_LIST_PACKAGE = "com.beantechs.applist"
    private const val SYSTEM_UI_REFRESH_OVERLAY = "com.android.systemui.theme.dark"
    private const val PROCESS_RELOAD_GRACE_MS = 500L
    private const val PROCESS_RESTART_TIMEOUT_MS = 3_000L
    private const val PROCESS_RESTART_POLL_MS = 100L
    private const val OEM_CLIENT_REFRESH_SETTLE_MS = 1_000L

    private data class BundledPatchInstallResult(
        val success: Boolean,
        val refreshed: Boolean
    )

    private data class MountResult(
        val success: Boolean,
        val changed: Boolean
    )

    private fun sh(command: String): String {
        val output = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "$command 2>&1"))
        Log.d(TAG, "sh: $command -> $output")
        return output
    }

    fun isPatchInstalled(): Boolean {
        val output = sh("ls -l '$PATCH_DIR' 2>/dev/null || true")
        val serviceInstalled = output.contains(SERVICE_APK)
        val appInstalled = output.contains(APP_APK)
        Log.d(
            TAG,
            "isPatchInstalled: service=$serviceInstalled app=$appInstalled (ls output: $output)"
        )
        return serviceInstalled && appInstalled
    }

    fun isMounted(): Boolean {
        val serviceMounted = isApkMounted(SYSTEM_SERVICE_PATH, "$PATCH_DIR/$SERVICE_APK", "vendor-service")
        val appMounted = isApkMounted(SYSTEM_APP_PATH, "$PATCH_DIR/$APP_APK", "visual-app")
        Log.d(TAG, "isMounted: service=$serviceMounted app=$appMounted")
        return serviceMounted && appMounted
    }

    private fun isApkMounted(systemPath: String, patchPath: String, label: String): Boolean {
        val systemMd5 = readRemoteMd5(systemPath)
        val patchMd5 = readRemoteMd5(patchPath)
        val mounted = systemMd5 == patchMd5 && systemMd5.isNotEmpty()
        Log.d(TAG, "isApkMounted[$label]: $mounted (System: $systemMd5, Patch: $patchMd5)")
        return mounted
    }

    private fun readRemoteMd5(path: String): String {
        val output = sh("md5sum '$path' 2>/dev/null || true")
        return Regex("(?i)\\b[0-9a-f]{32}\\b").find(output)?.value.orEmpty()
    }

    private fun assetMd5(context: Context, apkName: String): String {
        val digest = MessageDigest.getInstance("MD5")
        context.assets.open("carplay_patches/$apkName").use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun ensureBundledPatchesInstalled(context: Context): BundledPatchInstallResult {
        return try {
            val bundledServiceMd5 = assetMd5(context, SERVICE_APK)
            val bundledAppMd5 = assetMd5(context, APP_APK)
            val installedServiceMd5 = readRemoteMd5("$PATCH_DIR/$SERVICE_APK")
            val installedAppMd5 = readRemoteMd5("$PATCH_DIR/$APP_APK")
            val refreshed =
                bundledServiceMd5.isBlank() ||
                    bundledAppMd5.isBlank() ||
                    bundledServiceMd5 != installedServiceMd5 ||
                    bundledAppMd5 != installedAppMd5
            if (refreshed) {
                Log.w(
                    TAG,
                    "Refreshing CarPlay HVAC focus patches. " +
                        "service installed=$installedServiceMd5 bundled=$bundledServiceMd5; " +
                        "app installed=$installedAppMd5 bundled=$bundledAppMd5"
                )
                BundledPatchInstallResult(installPatches(context), true)
            } else {
                Log.d(
                    TAG,
                    "Bundled CarPlay HVAC focus patches already installed: " +
                        "service=$bundledServiceMd5 app=$bundledAppMd5"
                )
                BundledPatchInstallResult(true, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify bundled CarPlay HVAC focus patches", e)
            BundledPatchInstallResult(false, false)
        }
    }

    fun installPatches(context: Context): Boolean {
        if (!PATCH_RUNTIME_ENABLED) {
            Log.w(TAG, "installPatches skipped. $DISABLED_REASON")
            return false
        }

        try {
            Log.i(TAG, "Starting CarPlay HVAC focus patch installation...")
            sh("mkdir -p '$PATCH_DIR'")
            sh("chmod 755 '$PATCH_DIR'")
            sh("mkdir -p '$PATCH_DIR/empty_oat'")
            sh("chmod 755 '$PATCH_DIR/empty_oat'")

            if (!copyAssetToPatchDir(context, APP_APK, "u:object_r:system_file:s0")) return false
            if (!copyAssetToPatchDir(context, SERVICE_APK, "u:object_r:vendor_app_file:s0")) return false

            val success = isPatchInstalled()
            Log.i(TAG, "Installation success: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install CarPlay patches", e)
            return false
        }
    }

    private fun copyAssetToPatchDir(context: Context, apkName: String, preferredContext: String): Boolean {
        Log.d(TAG, "Copying asset: $apkName")
        val inputStream = context.assets.open("carplay_patches/$apkName")
        val tempFile = File(context.cacheDir, apkName)
        val outputStream = FileOutputStream(tempFile)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        val destPath = "$PATCH_DIR/$apkName"
        val cpOut = sh("cp '${tempFile.absolutePath}' '$destPath'")
        Log.d(TAG, "Copy output for $apkName: $cpOut")

        sh("chmod 644 '$destPath'")
        sh("chcon '$preferredContext' '$destPath' || chcon u:object_r:system_file:s0 '$destPath' || chcon u:object_r:vendor_app_file:s0 '$destPath' || true")
        tempFile.delete()
        return true
    }

    fun applyMounts(): Boolean {
        return applyMountsDetailed().success
    }

    private fun applyMountsDetailed(): MountResult {
        if (!PATCH_RUNTIME_ENABLED) {
            Log.w(TAG, "applyMounts skipped. $DISABLED_REASON")
            return MountResult(false, false)
        }

        if (!isPatchInstalled()) {
            Log.e(TAG, "Cannot apply mounts: CarPlay patches not installed")
            return MountResult(false, false)
        }

        try {
            Log.i(TAG, "Applying CarPlay HVAC focus bind mounts...")

            // Do not force-stop CarPlay while applying mounts. ensureMounted() performs a
            // separate idle-only process reload after a successful boot mount.
            mountApk(SYSTEM_APP_PATH, "$PATCH_DIR/$APP_APK", "visual-app")
            mountApk(SYSTEM_SERVICE_PATH, "$PATCH_DIR/$SERVICE_APK", "vendor-service")
            mountOatIfPresent(SYSTEM_APP_OAT, "visual-app-oat")
            mountOatIfPresent(SYSTEM_SERVICE_OAT, "vendor-service-oat")
            sh("rm -f /data/dalvik-cache/arm/*TsCarPlay* /data/dalvik-cache/arm64/*TsCarPlay* 2>/dev/null || true")
            Log.w(TAG, "CarPlay HVAC focus mounts applied. Patched dex requires process reload; active sessions are left untouched.")

            val success = isMounted()
            if (success) {
                Log.w(TAG, "CarPlay HVAC focus patches mounted successfully")
            } else {
                Log.e(TAG, "CarPlay HVAC focus mount verification failed. Check if Shizuku has root permissions.")
            }
            return MountResult(success, success)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply CarPlay mounts", e)
            return MountResult(false, false)
        }
    }

    private fun mountApk(systemPath: String, patchPath: String, label: String) {
        sh("umount -l '$systemPath' 2>/dev/null || true")
        val res = sh("mount --bind '$patchPath' '$systemPath'")
        if (res.contains("error", ignoreCase = true) || res.contains("failed", ignoreCase = true)) {
            Log.e(TAG, "Failed to mount $label APK at $systemPath: $res")
        } else {
            Log.d(TAG, "Mount $label result: $res")
        }
    }

    private fun mountOatIfPresent(oatPath: String, label: String) {
        sh("[ -d '$oatPath' ] && umount -l '$oatPath' 2>/dev/null || true")
        val res = sh("[ -d '$oatPath' ] && mount --bind '$PATCH_DIR/empty_oat' '$oatPath' || true")
        if (res.contains("error", ignoreCase = true) || res.contains("failed", ignoreCase = true)) {
            Log.e(TAG, "Failed to mount $label at $oatPath: $res")
        } else {
            Log.d(TAG, "Mount $label result: $res")
        }
    }

    fun removeMounts(): Boolean {
        try {
            Log.i(TAG, "Removing CarPlay HVAC focus mounts...")
            sh("umount -l '$SYSTEM_APP_PATH' 2>/dev/null || true")
            sh("umount -l '$SYSTEM_SERVICE_PATH' 2>/dev/null || true")
            sh("[ -d '$SYSTEM_APP_OAT' ] && umount -l '$SYSTEM_APP_OAT' 2>/dev/null || true")
            sh("[ -d '$SYSTEM_SERVICE_OAT' ] && umount -l '$SYSTEM_SERVICE_OAT' 2>/dev/null || true")
            Log.w(TAG, "CarPlay patches unmounted. REBOOT REQUIRED to drop patched dex from memory.")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove CarPlay mounts", e)
            return false
        }
    }

    fun uninstallPatches(): Boolean {
        Log.i(TAG, "Uninstalling CarPlay patches...")
        removeMounts()
        sh("rm -rf '$PATCH_DIR'")
        return !isPatchInstalled()
    }

    /**
     * Auto-mount patches if installed but not yet mounted.
     * Designed to be called from ForegroundService on boot after Shizuku is ready.
     * Refreshes bundled HVAC focus patches before mounting.
     */
    fun ensureMounted() {
        if (!PATCH_RUNTIME_ENABLED) {
            Log.w(TAG, "ensureMounted skipped. $DISABLED_REASON")
            return
        }

        try {
            var mountChanged = false
            val bundledPatchInstall = ensureBundledPatchesInstalled(App.getContext())
            if (!bundledPatchInstall.success) {
                Log.e(TAG, "Skipping CarPlay HVAC focus auto-mount because bundled patch install failed")
                return
            }
            if (bundledPatchInstall.refreshed) {
                Log.i(TAG, "Bundled CarPlay HVAC focus patches refreshed; re-applying mounts...")
                val result = applyMountsDetailed()
                mountChanged = result.changed
                Log.i(TAG, "Auto-remount refreshed CarPlay result: ${result.success}")
            } else if (isPatchInstalled() && !isMounted()) {
                Log.i(TAG, "CarPlay HVAC focus patches installed but not mounted — auto-mounting...")
                val result = applyMountsDetailed()
                mountChanged = result.changed
                Log.i(TAG, "Auto-mount CarPlay result: ${result.success}")
            } else if (isMounted()) {
                Log.d(TAG, "CarPlay HVAC focus patches already mounted, nothing to do")
            } else {
                Log.d(TAG, "No CarPlay HVAC focus patch installed, skipping auto-mount")
            }

            if (mountChanged) {
                reloadCarPlayProcessesAfterPatchMount("AUTO_MOUNT_AFTER_BOOT")
            }
        } catch (e: Exception) {
            Log.e(TAG, "CarPlay auto-mount failed", e)
        }
    }

    private fun reloadCarPlayProcessesAfterPatchMount(reason: String) {
        val visualTaskOnDisplay0 = DisplayAppLauncher.isCarPlayOnDisplay(0)
        val visualTaskOnDisplay3 = DisplayAppLauncher.isCarPlayOnDisplay(3)
        val visualTaskActive = visualTaskOnDisplay0 || visualTaskOnDisplay3

        if (visualTaskActive) {
            Log.w(
                TAG,
                "[$reason] CarPlay visual task is active; reloading visual and host so mounted HVAC focus patches are loaded"
            )
            reloadBoundProcessWithoutForceStop(
                packageName = "com.ts.carplay.app",
                serviceComponent = "com.ts.carplay.app/.service.CarPlayRemoteService",
                reason = reason
            )
            val hostReloaded = reloadBoundProcessWithoutForceStop(
                packageName = "com.ts.carplay",
                serviceComponent = "com.ts.carplay/.CarPlayService",
                reason = reason
            )
            if (hostReloaded) {
                refreshOemCarPlayClientsAfterHostReload(reason)
            }
            if (visualTaskOnDisplay3) {
                sh("am start --display 3 --windowingMode 5 --activity-multiple-task -f 0x18000000 -n com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity 2>/dev/null || true")
            } else if (visualTaskOnDisplay0) {
                sh("am stack start 0 -f 0x14000000 -n com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity 2>/dev/null || true")
            }
            return
        }

        Log.w(
            TAG,
            "[$reason] Reloading idle CarPlay processes so mounted HVAC focus patches are loaded in memory"
        )
        reloadBoundProcessWithoutForceStop(
            packageName = "com.ts.carplay.app",
            serviceComponent = "com.ts.carplay.app/.service.CarPlayRemoteService",
            reason = reason
        )
        val hostReloaded = reloadBoundProcessWithoutForceStop(
            packageName = "com.ts.carplay",
            serviceComponent = "com.ts.carplay/.CarPlayService",
            reason = reason
        )
        if (hostReloaded) {
            refreshOemCarPlayClientsAfterHostReload(reason)
        }
    }

    /**
     * Reloads patched code without putting the package in Android's force-stopped state.
     *
     * `am force-stop` permanently marks every existing service connection as DEAD until the
     * client binds again. A direct process signal preserves ActivityManager's BIND_AUTO_CREATE
     * records and reconnects clients to the replacement host. The OEM CarPlay SDK still caches
     * its inner manager Binder, so the affected native clients are renewed separately after the
     * host replacement.
     */
    private fun reloadBoundProcessWithoutForceStop(
        packageName: String,
        serviceComponent: String,
        reason: String
    ): Boolean {
        val originalPids = readProcessIds(packageName)
        ClusterPersistentEventLogger.log(
            "carplay_patch_process_reload_started",
            mapOf(
                "reason" to reason,
                "package" to packageName,
                "originalPids" to originalPids.joinToString(","),
                "strategy" to "signal_preserve_bind"
            )
        )

        originalPids.forEach { pid ->
            sh("kill -TERM $pid 2>/dev/null || true")
        }

        if (originalPids.isNotEmpty()) {
            Thread.sleep(PROCESS_RELOAD_GRACE_MS)
            readProcessIds(packageName)
                .filter { it in originalPids }
                .forEach { pid -> sh("kill -KILL $pid 2>/dev/null || true") }
        }

        var replacementPids =
            if (originalPids.isEmpty()) emptySet()
            else waitForReplacementProcess(packageName, originalPids)
        var startFallbackUsed = false
        if (replacementPids.isEmpty()) {
            startFallbackUsed = true
            sh("am startservice -n $serviceComponent 2>/dev/null || true")
            replacementPids = waitForReplacementProcess(packageName, originalPids)
        }

        val replacementObserved = replacementPids.isNotEmpty()
        Log.w(
            TAG,
            "[$reason] Bind-preserving CarPlay reload package=$packageName " +
                "originalPids=$originalPids replacementPids=$replacementPids " +
                "startFallbackUsed=$startFallbackUsed"
        )
        ClusterPersistentEventLogger.log(
            "carplay_patch_process_reload_finished",
            mapOf(
                "reason" to reason,
                "package" to packageName,
                "originalPids" to originalPids.joinToString(","),
                "replacementPids" to replacementPids.joinToString(","),
                "replacementObserved" to replacementObserved,
                "startFallbackUsed" to startFallbackUsed
            )
        )
        return replacementObserved
    }

    /**
     * Renews the two OEM clients that cache a CarPlayManager Binder across host replacement.
     *
     * SystemUI is not restarted. Toggling and immediately restoring its built-in resource
     * overlay makes FragmentHostManager recreate only the navigation view, whose detach/attach
     * lifecycle clears the stale manager. AppList is a separate process and can be renewed with
     * a direct signal; if it was not active, its next normal launch creates a fresh manager.
     */
    private fun refreshOemCarPlayClientsAfterHostReload(reason: String) {
        Thread.sleep(OEM_CLIENT_REFRESH_SETTLE_MS)
        refreshSystemUiCarPlayView(reason)
        refreshAppListCarPlayManager(reason)
    }

    private fun refreshSystemUiCarPlayView(reason: String) {
        val systemUiPidsBefore = readProcessIds(SYSTEM_UI_PACKAGE)
        val overlayListing = sh("cmd overlay list '$SYSTEM_UI_PACKAGE' 2>/dev/null || true")
        val overlayInitiallyEnabled =
            parseOverlayEnabled(overlayListing, SYSTEM_UI_REFRESH_OVERLAY)

        if (overlayInitiallyEnabled == null) {
            Log.e(TAG, "[$reason] SystemUI refresh overlay state unavailable; preserving native menu")
            ClusterPersistentEventLogger.log(
                "carplay_native_client_refresh_skipped",
                mapOf(
                    "reason" to reason,
                    "client" to SYSTEM_UI_PACKAGE,
                    "cause" to "overlay_state_unavailable"
                )
            )
            return
        }

        val toggleAction = if (overlayInitiallyEnabled) "disable" else "enable"
        val restoreAction = if (overlayInitiallyEnabled) "enable" else "disable"
        try {
            sh("cmd overlay $toggleAction --user 0 '$SYSTEM_UI_REFRESH_OVERLAY' 2>/dev/null || true")
            Thread.sleep(OEM_CLIENT_REFRESH_SETTLE_MS)
        } finally {
            sh("cmd overlay $restoreAction --user 0 '$SYSTEM_UI_REFRESH_OVERLAY' 2>/dev/null || true")
            Thread.sleep(OEM_CLIENT_REFRESH_SETTLE_MS)
        }

        val overlayRestored =
            parseOverlayEnabled(
                sh("cmd overlay list '$SYSTEM_UI_PACKAGE' 2>/dev/null || true"),
                SYSTEM_UI_REFRESH_OVERLAY
            ) == overlayInitiallyEnabled
        val systemUiPidsAfter = readProcessIds(SYSTEM_UI_PACKAGE)
        val systemUiPidPreserved =
            systemUiPidsBefore.isNotEmpty() && systemUiPidsBefore == systemUiPidsAfter
        val navigationBarPresent =
            sh("dumpsys window windows 2>/dev/null | grep -F 'NavigationBar' | head -n 1")
                .contains("NavigationBar")

        Log.w(
            TAG,
            "[$reason] SystemUI CarPlay view refreshed; pidPreserved=$systemUiPidPreserved " +
                "navigationBarPresent=$navigationBarPresent overlayRestored=$overlayRestored"
        )
        ClusterPersistentEventLogger.log(
            "carplay_native_client_refreshed",
            mapOf(
                "reason" to reason,
                "client" to SYSTEM_UI_PACKAGE,
                "pidBefore" to systemUiPidsBefore.joinToString(","),
                "pidAfter" to systemUiPidsAfter.joinToString(","),
                "pidPreserved" to systemUiPidPreserved,
                "navigationBarPresent" to navigationBarPresent,
                "overlayRestored" to overlayRestored
            )
        )
    }

    private fun refreshAppListCarPlayManager(reason: String) {
        val originalPids = readProcessIds(APP_LIST_PACKAGE)
        if (originalPids.isEmpty()) {
            ClusterPersistentEventLogger.log(
                "carplay_native_client_refresh_skipped",
                mapOf(
                    "reason" to reason,
                    "client" to APP_LIST_PACKAGE,
                    "cause" to "process_not_running"
                )
            )
            return
        }

        originalPids.forEach { pid -> sh("kill -TERM $pid 2>/dev/null || true") }
        Thread.sleep(PROCESS_RELOAD_GRACE_MS)
        readProcessIds(APP_LIST_PACKAGE)
            .filter { it in originalPids }
            .forEach { pid -> sh("kill -KILL $pid 2>/dev/null || true") }

        val replacementPids = waitForReplacementProcess(APP_LIST_PACKAGE, originalPids)
        Log.w(
            TAG,
            "[$reason] AppList CarPlay manager renewed; originalPids=$originalPids " +
                "replacementPids=$replacementPids"
        )
        ClusterPersistentEventLogger.log(
            "carplay_native_client_refreshed",
            mapOf(
                "reason" to reason,
                "client" to APP_LIST_PACKAGE,
                "pidBefore" to originalPids.joinToString(","),
                "pidAfter" to replacementPids.joinToString(","),
                "replacementObserved" to replacementPids.isNotEmpty()
            )
        )
    }

    private fun waitForReplacementProcess(packageName: String, originalPids: Set<Int>): Set<Int> {
        val deadline = SystemClock.elapsedRealtime() + PROCESS_RESTART_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val currentPids = readProcessIds(packageName)
            val replacements = replacementProcessIds(originalPids, currentPids)
            if (replacements.isNotEmpty()) return replacements
            Thread.sleep(PROCESS_RESTART_POLL_MS)
        }
        return replacementProcessIds(originalPids, readProcessIds(packageName))
    }

    private fun readProcessIds(packageName: String): Set<Int> {
        return parseProcessIds(sh("pidof '$packageName' 2>/dev/null || true"))
    }

    internal fun parseProcessIds(output: String): Set<Int> {
        return output
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull { token -> token.toIntOrNull()?.takeIf { it > 0 } }
            .toSet()
    }

    internal fun replacementProcessIds(originalPids: Set<Int>, currentPids: Set<Int>): Set<Int> {
        return currentPids - originalPids
    }

    internal fun parseOverlayEnabled(output: String, packageName: String): Boolean? {
        val marker =
            Regex("(?m)^\\[([x ])]\\s+${Regex.escape(packageName)}\\s*$")
                .find(output)
                ?.groupValues
                ?.getOrNull(1)
        return when (marker) {
            "x" -> true
            " " -> false
            else -> null
        }
    }

    fun getDiagnostics(): String {
        val id = sh("id")
        val mounts = sh("mount | grep -Ei 'TsCarPlay|carplay_patches' || true")
        val patchFiles = sh("ls -lR '$PATCH_DIR' 2>/dev/null || true")
        val vendorServiceFiles = sh("ls -la /vendor/app/TsCarPlayService /vendor/app/TsCarPlayService/oat 2>/dev/null || true")
        val systemAppFiles = sh("ls -la /system/app/TsCarPlayApp /system/app/TsCarPlayApp/oat 2>/dev/null || true")
        val sb = StringBuilder()
        sb.append("ID: $id\n\n")
        sb.append("Mounts:\n$mounts\n\n")
        sb.append("Patch Files:\n$patchFiles\n\n")
        sb.append("Vendor CarPlay Service:\n$vendorServiceFiles\n\n")
        sb.append("System CarPlay App:\n$systemAppFiles\n\n")

        sb.append("--- Integrity Check ---\n")
        appendIntegrity(sb, APP_APK, SYSTEM_APP_PATH)
        appendIntegrity(sb, SERVICE_APK, SYSTEM_SERVICE_PATH)
        return sb.toString()
    }

    private fun appendIntegrity(sb: StringBuilder, apkName: String, systemPath: String) {
        val systemMd5 = readRemoteMd5(systemPath)
        val patchMd5 = readRemoteMd5("$PATCH_DIR/$apkName")
        sb.append("$apkName:\n")
        sb.append("  System: $systemMd5\n")
        sb.append("  Patch:  $patchMd5\n")
        if (systemMd5 == patchMd5 && systemMd5.isNotEmpty()) {
            sb.append("  Result: MATCH (Mounted)\n")
        } else {
            sb.append("  Result: MISMATCH (Not Mounted or Failed)\n")
        }
    }
}
