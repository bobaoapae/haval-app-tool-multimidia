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

    const val VENDOR_SERVICE_PATH = "/vendor/app/AndroidAutoService/AndroidAutoService.apk"
    const val VENDOR_APP_PATH = "/vendor/app/AndroidAutoApp/AndroidAutoApp.apk"
    
    const val VENDOR_SERVICE_OAT = "/vendor/app/AndroidAutoService/oat"
    const val VENDOR_APP_OAT = "/vendor/app/AndroidAutoApp/oat"

    private fun sh(command: String): String {
        val output = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "$command 2>&1"))
        Log.d(TAG, "sh: $command -> $output")
        return output
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

    private fun isServicePatchInstalled(): Boolean {
        return sh("[ -f '$PATCH_DIR/$SERVICE_APK' ] && echo yes || true").contains("yes")
    }

    fun isPatchInstalled(): Boolean {
        val output = sh("ls -l '$PATCH_DIR' 2>/dev/null")
        val installed = output.contains(APP_APK)
        Log.d(TAG, "isPatchInstalled: $installed")
        return installed
    }

    fun isMounted(): Boolean {
        val vendorAppMd5 = sh("md5sum '$VENDOR_APP_PATH' 2>/dev/null | awk '{print \$1}'").trim()
        val patchAppMd5 = sh("md5sum '$PATCH_DIR/$APP_APK' 2>/dev/null | awk '{print \$1}'").trim()

        val appMounted = vendorAppMd5.isNotEmpty() && vendorAppMd5 == patchAppMd5
        val servicePatchInstalled = isServicePatchInstalled()
        val serviceMounted = if (servicePatchInstalled) {
            val vendorServiceMd5 = sh("md5sum '$VENDOR_SERVICE_PATH' 2>/dev/null | awk '{print \$1}'").trim()
            val patchServiceMd5 = sh("md5sum '$PATCH_DIR/$SERVICE_APK' 2>/dev/null | awk '{print \$1}'").trim()
            vendorServiceMd5.isNotEmpty() && vendorServiceMd5 == patchServiceMd5
        } else {
            true
        }

        val mounted = appMounted && serviceMounted
        Log.d(TAG, "isMounted (MD5): $mounted (Service: $serviceMounted, App: $appMounted)")
        return mounted
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

    fun applyMounts(): Boolean {
        if (!isPatchInstalled()) {
            Log.e(TAG, "Cannot apply mounts: Patches not installed")
            return false
        }
        
        try {
            Log.i(TAG, "Applying bind mounts...")
            // 1. Unmount existing if any (aggressive)
            sh("umount -l '$VENDOR_SERVICE_PATH' 2>/dev/null || true")
            sh("umount -l '$VENDOR_APP_PATH' 2>/dev/null || true")
            sh("[ -d '$VENDOR_SERVICE_OAT' ] && umount -l '$VENDOR_SERVICE_OAT' 2>/dev/null || true")
            sh("[ -d '$VENDOR_APP_OAT' ] && umount -l '$VENDOR_APP_OAT' 2>/dev/null || true")

            // 2. Apply Bind Mounts
            var res1 = "Service patch not bundled"
            if (isServicePatchInstalled()) {
                res1 = sh("mount --bind '$PATCH_DIR/$SERVICE_APK' '$VENDOR_SERVICE_PATH'")
                if (res1.contains("error", ignoreCase = true) || res1.contains("failed", ignoreCase = true)) {
                    Log.e(TAG, "Failed to mount Service APK: $res1")
                }
            }

            val res2 = sh("mount --bind '$PATCH_DIR/$APP_APK' '$VENDOR_APP_PATH'")
            if (res2.contains("error", ignoreCase = true) || res2.contains("failed", ignoreCase = true)) {
                Log.e(TAG, "Failed to mount App APK: $res2")
            }
            
            // Mount empty oat only if target exists
            if (isServicePatchInstalled()) {
                sh("[ -d '$VENDOR_SERVICE_OAT' ] && mount --bind '$PATCH_DIR/empty_oat' '$VENDOR_SERVICE_OAT' || true")
            }
            sh("[ -d '$VENDOR_APP_OAT' ] && mount --bind '$PATCH_DIR/empty_oat' '$VENDOR_APP_OAT' || true")
            
            Log.d(TAG, "Mount Service Result: $res1")
            Log.d(TAG, "Mount App Result: $res2")

            // 3. Wipe dalvik cache to force reload
            sh("rm -f /data/dalvik-cache/arm64/*AndroidAuto* 2>/dev/null || true")

            // 4. Force stop apps
            sh("am force-stop com.ts.androidauto")
            sh("am force-stop com.ts.androidauto.app")

            val success = isMounted()
            if (success) {
                Log.w(TAG, "Patches mounted successfully")
            } else {
                Log.e(TAG, "Mount verification failed. Check if Shizuku has root permissions.")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply mounts", e)
            return false
        }
    }

    fun removeMounts(): Boolean {
        try {
            Log.i(TAG, "Removing mounts...")
            sh("umount -l '$VENDOR_SERVICE_PATH' 2>/dev/null || true")
            sh("umount -l '$VENDOR_APP_PATH' 2>/dev/null || true")
            sh("[ -d '$VENDOR_SERVICE_OAT' ] && umount -l '$VENDOR_SERVICE_OAT' 2>/dev/null || true")
            sh("[ -d '$VENDOR_APP_OAT' ] && umount -l '$VENDOR_APP_OAT' 2>/dev/null || true")

            sh("am force-stop com.ts.androidauto")
            sh("am force-stop com.ts.androidauto.app")
            
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
     * Auto-mount patches if installed but not yet mounted.
     * Designed to be called from ForegroundService on boot after Shizuku is ready.
     * No-op if patches aren't installed or are already mounted.
     */
    fun ensureMounted() {
        try {
            val context = App.getContext()
            val bundledAppMd5 = bundledPatchMd5(context, APP_APK)
            if (bundledAppMd5 == null) {
                Log.e(TAG, "No bundled Android Auto patch available; skipping auto-mount")
                return
            }

            val bundledServiceMd5 = if (hasBundledAsset(context, SERVICE_APK)) {
                bundledPatchMd5(context, SERVICE_APK)
            } else {
                null
            }

            val installedAppMd5 = installedPatchMd5(APP_APK)
            val installedServiceMd5 = installedPatchMd5(SERVICE_APK)
            val needsInstall =
                !isPatchInstalled() ||
                        bundledAppMd5 != installedAppMd5 ||
                        (bundledServiceMd5 != null && bundledServiceMd5 != installedServiceMd5) ||
                        (bundledServiceMd5 == null && isServicePatchInstalled())

            if (needsInstall) {
                Log.i(TAG, "Installing bundled Android Auto patch update...")
                if (!installPatches(context)) {
                    Log.e(TAG, "Cannot auto-mount Android Auto patch: install failed")
                    return
                }
            }

            if (!isMounted() || needsInstall) {
                Log.i(
                    TAG,
                    "Android Auto patch auto-mounting. bundledApp=$bundledAppMd5 installedApp=$installedAppMd5 refreshed=$needsInstall"
                )
                val success = applyMounts()
                Log.i(TAG, "Auto-mount result: $success")
            } else {
                Log.d(TAG, "Patches already mounted, nothing to do")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-mount failed", e)
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
