package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object AndroidAutoPatchManager {
    private const val TAG = "AA_PATCH_MGR"
    private const val PATCH_DIR = "/data/local/tmp/aa_patches"
    private const val SERVICE_APK = "AndroidAutoService.apk"
    private const val APP_APK = "AndroidAutoApp.apk"
    private const val SERVICE_PACKAGE = "com.ts.androidauto.projectionservice"
    private const val LEGACY_SERVICE_PACKAGE = "com.ts.androidauto"
    private const val APP_PACKAGE = "com.ts.androidauto.app"

    const val VENDOR_SERVICE_PATH = "/vendor/app/AndroidAutoService/AndroidAutoService.apk"
    const val VENDOR_APP_PATH = "/vendor/app/AndroidAutoApp/AndroidAutoApp.apk"
    
    const val VENDOR_SERVICE_OAT = "/vendor/app/AndroidAutoService/oat"
    const val VENDOR_APP_OAT = "/vendor/app/AndroidAutoApp/oat"

    private fun sh(command: String): String {
        val output = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "$command 2>&1"))
        Log.d(TAG, "sh: $command -> $output")
        return output
    }

    private fun forceStopAndroidAutoPackages() {
        sh("am force-stop $SERVICE_PACKAGE || true")
        sh("am force-stop $LEGACY_SERVICE_PACKAGE || true")
        sh("am force-stop $APP_PACKAGE || true")
    }

    private fun hasBundledAsset(context: Context, assetName: String): Boolean {
        return try {
            context.assets.open("aa_patches/$assetName").use { true }
        } catch (e: Exception) {
            false
        }
    }

    private fun bundledPatchMd5(context: Context, assetName: String): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            context.assets.open("aa_patches/$assetName").use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash bundled Android Auto patch $assetName", e)
            null
        }
    }

    private fun installedPatchMd5(assetName: String): String {
        return sh("md5sum '$PATCH_DIR/$assetName' 2>/dev/null | awk '{print \$1}'").trim()
    }

    private fun isAppPatchInstalled(): Boolean {
        return sh("[ -f '$PATCH_DIR/$APP_APK' ] && echo yes || true").contains("yes")
    }

    private fun isServicePatchInstalled(): Boolean {
        return sh("[ -f '$PATCH_DIR/$SERVICE_APK' ] && echo yes || true").contains("yes")
    }

    private fun isAppPatchMounted(): Boolean {
        val vendorAppMd5 = sh("md5sum '$VENDOR_APP_PATH' 2>/dev/null | awk '{print \$1}'").trim()
        val patchAppMd5 = installedPatchMd5(APP_APK)
        val mounted = vendorAppMd5.isNotEmpty() && vendorAppMd5 == patchAppMd5
        Log.d(TAG, "isAppPatchMounted (MD5): $mounted (Vendor: $vendorAppMd5, Patch: $patchAppMd5)")
        return mounted
    }

    fun isPatchInstalled(): Boolean {
        val output = sh("ls -l '$PATCH_DIR' 2>/dev/null")
        val installed = output.contains(APP_APK)
        Log.d(TAG, "isPatchInstalled: $installed")
        return installed
    }

    fun isMounted(): Boolean {
        val appMounted = isAppPatchMounted()
        val servicePatchInstalled = isServicePatchInstalled()
        val serviceMounted = if (servicePatchInstalled) {
            val vendorServiceMd5 = sh("md5sum '$VENDOR_SERVICE_PATH' 2>/dev/null | awk '{print \$1}'").trim()
            val patchServiceMd5 = sh("md5sum '$PATCH_DIR/$SERVICE_APK' 2>/dev/null | awk '{print \$1}'").trim()
            vendorServiceMd5.isNotEmpty() && vendorServiceMd5 == patchServiceMd5
        } else {
            true
        }

        Log.d(TAG, "isMounted (visual MD5): $appMounted (Service patch mounted: $serviceMounted)")
        return appMounted
    }

    fun installPatches(context: Context): Boolean {
        try {
            Log.i(TAG, "Starting patch installation...")
            if (!hasBundledAsset(context, APP_APK)) {
                Log.e(TAG, "Cannot install Android Auto patch: missing bundled asset aa_patches/$APP_APK")
                return false
            }

            sh("mkdir -p '$PATCH_DIR'")
            sh("chmod 755 '$PATCH_DIR'")

            // Create empty oat dir
            sh("mkdir -p '$PATCH_DIR/empty_oat'")
            sh("chmod 755 '$PATCH_DIR/empty_oat'")

            val assets = arrayOf(APP_APK, SERVICE_APK)
            for (assetName in assets) {
                if (!hasBundledAsset(context, assetName)) {
                    if (assetName == SERVICE_APK) {
                        sh("rm -f '$PATCH_DIR/$SERVICE_APK'")
                        Log.i(TAG, "No bundled Service APK patch; keeping stock AndroidAutoService")
                        continue
                    }
                    Log.e(TAG, "Missing mandatory Android Auto patch asset: $assetName")
                    return false
                }

                Log.d(TAG, "Copying asset: $assetName")
                val inputStream = context.assets.open("aa_patches/$assetName")
                val tempFile = File(context.cacheDir, assetName)
                val outputStream = FileOutputStream(tempFile)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                val destPath = "$PATCH_DIR/$assetName"
                val cpOut = sh("cp '${tempFile.absolutePath}' '$destPath'")
                Log.d(TAG, "Copy output for $assetName: $cpOut")
                
                sh("chmod 644 '$destPath'")
                sh("chcon u:object_r:vendor_app_file:s0 '$destPath'")
                tempFile.delete()
            }
            val success = isPatchInstalled()
            Log.i(TAG, "Installation success: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install patches", e)
            return false
        }
    }

    private fun installAppPatch(context: Context): Boolean {
        try {
            if (!hasBundledAsset(context, APP_APK)) {
                Log.e(TAG, "Cannot install Android Auto visual patch: missing bundled asset aa_patches/$APP_APK")
                return false
            }

            sh("mkdir -p '$PATCH_DIR'")
            sh("chmod 755 '$PATCH_DIR'")
            sh("mkdir -p '$PATCH_DIR/empty_oat'")
            sh("chmod 755 '$PATCH_DIR/empty_oat'")

            val tempFile = File(context.cacheDir, APP_APK)
            context.assets.open("aa_patches/$APP_APK").use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val destPath = "$PATCH_DIR/$APP_APK"
            val cpOut = sh("cp '${tempFile.absolutePath}' '$destPath'")
            Log.d(TAG, "Copy output for visual patch $APP_APK: $cpOut")
            sh("chmod 644 '$destPath'")
            sh("chcon u:object_r:vendor_app_file:s0 '$destPath'")
            tempFile.delete()

            val success = isAppPatchInstalled()
            Log.i(TAG, "Visual patch installation success: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install Android Auto visual patch", e)
            return false
        }
    }

    private fun applyAppMount(): Boolean {
        if (!isAppPatchInstalled()) {
            Log.e(TAG, "Cannot apply Android Auto visual mount: App patch not installed")
            return false
        }

        return try {
            Log.i(TAG, "Applying Android Auto visual app bind mount only...")
            sh("mkdir -p '$PATCH_DIR/empty_oat'")
            sh("chmod 755 '$PATCH_DIR/empty_oat'")
            sh("chmod 644 '$PATCH_DIR/$APP_APK'")
            sh("chcon u:object_r:vendor_app_file:s0 '$PATCH_DIR/$APP_APK'")

            sh("umount -l '$VENDOR_APP_PATH' 2>/dev/null || true")
            sh("[ -d '$VENDOR_APP_OAT' ] && umount -l '$VENDOR_APP_OAT' 2>/dev/null || true")

            val mountResult = sh("mount --bind '$PATCH_DIR/$APP_APK' '$VENDOR_APP_PATH'")
            if (mountResult.contains("error", ignoreCase = true) || mountResult.contains("failed", ignoreCase = true)) {
                Log.e(TAG, "Failed to mount Android Auto visual APK: $mountResult")
            }
            sh("[ -d '$VENDOR_APP_OAT' ] && mount --bind '$PATCH_DIR/empty_oat' '$VENDOR_APP_OAT' || true")

            sh("rm -f /data/dalvik-cache/arm64/*AndroidAutoApp* 2>/dev/null || true")
            sh("am force-stop $APP_PACKAGE || true")

            val success = isAppPatchMounted()
            if (success) {
                Log.w(TAG, "Android Auto visual patch mounted successfully")
            } else {
                Log.e(TAG, "Android Auto visual mount verification failed")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Android Auto visual mount", e)
            false
        }
    }

    fun applyMounts(): Boolean {
        if (!isPatchInstalled()) {
            Log.e(TAG, "Cannot apply mounts: Patches not installed")
            return false
        }

        Log.w(TAG, "Applying Android Auto visual mount only; service APK auto/manual UI mount is disabled")
        return applyAppMount()
    }

    fun removeMounts(): Boolean {
        try {
            Log.i(TAG, "Removing mounts...")
            sh("umount -l '$VENDOR_SERVICE_PATH' 2>/dev/null || true")
            sh("umount -l '$VENDOR_APP_PATH' 2>/dev/null || true")
            sh("[ -d '$VENDOR_SERVICE_OAT' ] && umount -l '$VENDOR_SERVICE_OAT' 2>/dev/null || true")
            sh("[ -d '$VENDOR_APP_OAT' ] && umount -l '$VENDOR_APP_OAT' 2>/dev/null || true")

            forceStopAndroidAutoPackages()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove mounts", e)
            return false
        }
    }
    
    fun uninstallPatches(): Boolean {
        Log.i(TAG, "Uninstalling patches...")
        removeMounts()
        sh("rm -rf '$PATCH_DIR'")
        return !isPatchInstalled()
    }
    
    /**
     * Auto-mount the visual Android Auto patch if installed but not yet mounted.
     * Designed to be called from ForegroundService on boot after Shizuku is ready.
     * The service APK is intentionally not auto-mounted because service variants have regressed
     * video startup before; manual controls remain available for explicit diagnostics.
     */
    fun ensureMounted() {
        try {
            val context = App.getContext()
            val bundledAppMd5 = bundledPatchMd5(context, APP_APK)
            if (bundledAppMd5 == null) {
                Log.e(TAG, "No bundled Android Auto visual patch available; skipping auto-mount")
                return
            }

            val installedAppMd5 = installedPatchMd5(APP_APK)
            val needsInstall =
                !isAppPatchInstalled() ||
                        bundledAppMd5 != installedAppMd5

            if (needsInstall) {
                Log.i(TAG, "Installing bundled Android Auto visual patch update...")
                if (!installAppPatch(context)) {
                    Log.e(TAG, "Cannot auto-mount Android Auto visual patch: install failed")
                    return
                }
            }

            if (!isAppPatchMounted() || needsInstall) {
                Log.i(
                    TAG,
                    "Android Auto visual patch auto-mounting. bundledApp=$bundledAppMd5 installedApp=$installedAppMd5 refreshed=$needsInstall"
                )
                val success = applyAppMount()
                Log.i(TAG, "Visual auto-mount result: $success")
            } else {
                Log.d(TAG, "Android Auto visual patch already mounted, nothing to do")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Android Auto visual auto-mount failed", e)
        }
    }

    fun getDiagnostics(): String {
        val id = sh("id")
        val mounts = sh("mount | grep -Ei 'AndroidAuto|aa_patches'")
        val patchFiles = sh("ls -lR '$PATCH_DIR' 2>/dev/null")
        val vendorFiles = sh("ls -lR /vendor/app/AndroidAuto*")
        val sb = StringBuilder()
        sb.append("ID: $id\n\n")
        sb.append("Mounts:\n$mounts\n\n")
        sb.append("Patch Files:\n$patchFiles\n\n")
        sb.append("Vendor Files:\n$vendorFiles\n\n")

        sb.append("--- Integrity Check ---\n")
        sb.append("OAT Mounts:\n")
        val oatCheck1 = sh("ls /vendor/app/AndroidAutoService/oat 2>/dev/null").trim()
        val oatCheck2 = sh("ls /vendor/app/AndroidAutoApp/oat 2>/dev/null").trim()
        sb.append("  Service OAT: ${if (isServicePatchInstalled()) { if (oatCheck1.isEmpty()) "EMPTY (OK)" else "NOT EMPTY (FAILED)" } else "STOCK (OK)"}\n")
        sb.append("  App OAT:     ${if (oatCheck2.isEmpty()) "EMPTY (OK)" else "NOT EMPTY (FAILED)"}\n\n")
        val targets = arrayOf(
            "/vendor/app/AndroidAutoService/AndroidAutoService.apk",
            "/vendor/app/AndroidAutoApp/AndroidAutoApp.apk"
        )
        for (target in targets) {
            val vendorMd5 = sh("md5sum '$target' 2>/dev/null | awk '{print \$1}'").trim()
            val fileName = target.substring(target.lastIndexOf("/") + 1)
            val patchMd5 = sh("md5sum '$PATCH_DIR/$fileName' 2>/dev/null | awk '{print \$1}'").trim()

            sb.append("$fileName:\n")
            sb.append("  Vendor: $vendorMd5\n")
            sb.append("  Patch:  $patchMd5\n")
            if (vendorMd5 == patchMd5 && vendorMd5.isNotEmpty()) {
                sb.append("  Result: MATCH (Mounted)\n")
            } else {
                sb.append("  Result: MISMATCH (Not Mounted or Failed)\n")
            }
        }
        return sb.toString()
    }
}
