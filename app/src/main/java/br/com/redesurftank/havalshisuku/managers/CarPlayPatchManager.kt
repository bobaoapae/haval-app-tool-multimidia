package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import br.com.redesurftank.App
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
            sh("am force-stop com.ts.carplay.app 2>/dev/null || true")
            sh("am force-stop com.ts.carplay 2>/dev/null || true")
            sh("sleep 0.3")
            sh("am startservice -n com.ts.carplay/.CarPlayService 2>/dev/null || true")
            sh("am startservice -n com.ts.carplay.app/.service.CarPlayRemoteService 2>/dev/null || true")
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
        sh("am force-stop com.ts.carplay.app 2>/dev/null || true")
        sh("am force-stop com.ts.carplay 2>/dev/null || true")
        sh("sleep 0.2")
        sh("am startservice -n com.ts.carplay/.CarPlayService 2>/dev/null || true")
        sh("am startservice -n com.ts.carplay.app/.service.CarPlayRemoteService 2>/dev/null || true")
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
