package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
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

    /** Stock Service on this head unit. A different md5 is not swapped. */
    private const val EXPECTED_STOCK_SERVICE_MD5 = "48ffded64e9b485521e3174dcd70db27"
    private const val MOUNT_RESULT = "/data/local/tmp/aa-cluster-mount.result"

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

    private fun installServicePatch(context: Context): Boolean {
        try {
            if (!hasBundledAsset(context, SERVICE_APK)) {
                Log.e(TAG, "Cannot install Android Auto Service patch: missing bundled asset aa_patches/$SERVICE_APK")
                return false
            }

            sh("mkdir -p '$PATCH_DIR'")
            sh("chmod 755 '$PATCH_DIR'")
            sh("mkdir -p '$PATCH_DIR/empty_oat'")
            sh("chmod 755 '$PATCH_DIR/empty_oat'")

            val tempFile = File(context.cacheDir, SERVICE_APK)
            context.assets.open("aa_patches/$SERVICE_APK").use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val destPath = "$PATCH_DIR/$SERVICE_APK"
            val cpOut = sh("cp '${tempFile.absolutePath}' '$destPath'")
            Log.d(TAG, "Copy output for service patch $SERVICE_APK: $cpOut")
            sh("chmod 644 '$destPath'")
            sh("chcon u:object_r:vendor_app_file:s0 '$destPath'")
            tempFile.delete()

            val success = isServicePatchInstalled()
            Log.w(TAG, "Service patch installation success: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install Android Auto Service patch", e)
            return false
        }
    }

    private fun applyAppMount(refreshed: Boolean = false): Boolean {
        if (!isAppPatchInstalled()) {
            Log.e(TAG, "Cannot apply Android Auto visual mount: App patch not installed")
            return false
        }

        return try {
            val verdict = bindMountLikeAutoPanel(APP_APK, VENDOR_APP_PATH)
            // The visual package is an activity. AutoPanel's own note is that
            // restarting it does not drop the GAL link, and v9 restarted it on every
            // mount until 2026-10-13. Without the restart a running App keeps the
            // stock dex, so AA on D0 ignores the bar fit (patch v2.8). The projection
            // service is left running.
            // A refreshed stage is copied over the file that is already bind-mounted, so the
            // script reports "already-mounted" while the running App still has the old dex
            // (2026-10-13 18:33: v2.9 staged, App kept v2.8 until restarted by hand).
            if (verdict == "mounted" || (refreshed && verdict == "already-mounted")) {
                sh("am force-stop $APP_PACKAGE || true")
            }
            val success = verdict == "mounted" || verdict == "already-mounted"
            if (success) {
                Log.w(TAG, "Android Auto visual patch $verdict")
            } else {
                Log.e(TAG, "Android Auto visual mount verification failed: $verdict")
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

        // Refresh the staged App from the APK bundled in this Impulse first, as the
        // boot path does; otherwise "Ativar" re-mounts whatever an older build staged.
        Log.w(TAG, "Applying Android Auto visual App and Service mounts")
        ensureVisualMounted()
        val appMounted = isAppPatchMounted()
        if (!isClusterServiceEnabled()) return appMounted
        val serviceMounted = mountServiceWithApp("USER_MOUNT")
        return appMounted && serviceMounted
    }

    fun isServiceClusterPatchMounted(): Boolean {
        if (!isServicePatchInstalled()) return false
        val vendorServiceMd5 = sh("md5sum '$VENDOR_SERVICE_PATH' 2>/dev/null | awk '{print \$1}'").trim()
        val patchServiceMd5 = installedPatchMd5(SERVICE_APK)
        return vendorServiceMd5.isNotEmpty() && vendorServiceMd5 == patchServiceMd5
    }

    /**
     * Bind-mount the patched Service APK the way AutoPanel does.
     * Does not umount a live bind, does not wipe dalvik when the vendor MD5
     * already matches, and does not restart projectionservice.
     */
    fun applyServiceMountWithoutForceStop(): Boolean {
        if (!isServicePatchInstalled()) {
            Log.e(TAG, "Cannot apply Android Auto Service mount: Service patch not installed")
            return false
        }
        return try {
            val verdict = bindMountLikeAutoPanel(SERVICE_APK, VENDOR_SERVICE_PATH)
            val success = verdict == "mounted" || verdict == "already-mounted"
            if (success) {
                Log.w(TAG, "Android Auto Service patch $verdict; running process is left as-is")
            } else {
                Log.e(TAG, "Android Auto Service mount verification failed: $verdict")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Android Auto Service mount", e)
            false
        }
    }

    /**
     * AutoPanel's aa-cluster-mount.sh. A matching vendor MD5 returns
     * already-mounted and does not touch the running dex.
     */
    private fun bindMountLikeAutoPanel(stageName: String, vendorPath: String): String {
        val script = File(App.getDeviceProtectedContext().filesDir, "aa-cluster-mount.sh")
        App.getContext().assets.open("aa_cluster/aa-cluster-mount.sh").use { input ->
            FileOutputStream(script).use { output -> input.copyTo(output) }
        }
        val token = System.currentTimeMillis().toString()
        val src = "$PATCH_DIR/$stageName"
        sh(
            "sh '${script.absolutePath}' '$src' '$token' none '$vendorPath' '$stageName'"
        )
        val result = sh("cat '$MOUNT_RESULT' 2>/dev/null || true").trim()
        val verdict = result.substringBefore(' ')
        if (!result.endsWith(token)) {
            Log.w(TAG, "Mount result for $stageName did not match token $token: $result")
            return "stale-result"
        }
        Log.w(TAG, "Mount result for $stageName: $verdict")
        return verdict
    }

    fun removeMounts(): Boolean {
        try {
            Log.i(TAG, "Removing mounts...")
            sh("umount -l '$VENDOR_SERVICE_PATH' 2>/dev/null || true")
            sh("umount -l '$VENDOR_APP_PATH' 2>/dev/null || true")
            sh("[ -d '$VENDOR_SERVICE_OAT' ] && umount -l '$VENDOR_SERVICE_OAT' 2>/dev/null || true")
            sh("[ -d '$VENDOR_APP_OAT' ] && umount -l '$VENDOR_APP_OAT' 2>/dev/null || true")

            // Do not force-stop projectionservice: that drops USB accessory mode.
            sh("am force-stop $APP_PACKAGE || true")
            
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
     * Visual patch only. Route start and display moves call this and must not
     * reload the Service.
     */
    fun ensureMounted() {
        ensureVisualMounted()
    }

    /**
     * Boot path for the main Android Auto auto-mount switch, and the same
     * path when that switch is turned on. Mounts the Service with the visual
     * app as soon as Impulse can, before a phone session or a route exists.
     */
    fun ensureMountedWithService() {
        synchronized(this) {
            ensureVisualMounted()
            if (isClusterServiceEnabled()) mountServiceWithApp("BOOT_WITH_AA_PATCH")
        }
    }

    /** "Mapa no cluster": whether the map Service patch is mounted along with the App. */
    fun isClusterServiceEnabled(): Boolean =
        App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            .getBoolean(SharedPreferencesKeys.AA_CLUSTER_SERVICE_AUTO_MOUNT.key, false)

    /**
     * "Mapa no cluster" turned on. Mounts the Service only if Android Auto is not running
     * (the script refuses otherwise); a running session picks it up at the next car start.
     */
    fun mountClusterServiceIfEnabled(): Boolean {
        if (!isClusterServiceEnabled()) return false
        synchronized(this) {
            return mountServiceWithApp("CLUSTER_SWITCH")
        }
    }

    private fun ensureVisualMounted() {
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
                val success = applyAppMount(refreshed = needsInstall)
                Log.i(TAG, "Visual auto-mount result: $success")
            } else {
                Log.d(TAG, "Android Auto visual patch already mounted, nothing to do")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Android Auto visual auto-mount failed", e)
        }
    }

    /**
     * Bind the Service together with the visual patch. AutoPanel's rule: the
     * script mounts and stops. It does not SIGTERM `com.ts.androidauto` and
     * does not `am force-stop` the projection service. A process that already
     * opened the stock dex keeps it until Android Auto starts on its own.
     *
     * The file under a fresh bind has to be stock [EXPECTED_STOCK_SERVICE_MD5].
     */
    private fun mountServiceWithApp(reason: String): Boolean {
        try {
            if (isServiceClusterPatchMounted()) {
                Log.w(TAG, "[$reason] Service already mounted; running process left as-is")
                return true
            }
            val stockMd5 = sh("md5sum < '$VENDOR_SERVICE_PATH' 2>/dev/null | cut -d' ' -f1").trim()
            if (stockMd5.isEmpty()) {
                Log.w(TAG, "[$reason] Service mount skipped: vendor md5 unreadable")
                return false
            }
            if (stockMd5 != EXPECTED_STOCK_SERVICE_MD5) {
                Log.w(TAG, "[$reason] Service mount refused: vendor md5=$stockMd5 expected=$EXPECTED_STOCK_SERVICE_MD5")
                return false
            }
            val context = App.getContext()
            val bundledServiceMd5 = bundledPatchMd5(context, SERVICE_APK)
            if (bundledServiceMd5 == null) {
                Log.w(TAG, "[$reason] No bundled Android Auto Service patch")
                return false
            }
            if (!isServicePatchInstalled() || bundledServiceMd5 != installedPatchMd5(SERVICE_APK)) {
                Log.w(TAG, "[$reason] Installing bundled Android Auto Service patch")
                if (!installServicePatch(context)) {
                    Log.e(TAG, "[$reason] Service patch install failed")
                    return false
                }
            }
            val mounted = applyServiceMountWithoutForceStop()
            Log.w(TAG, "[$reason] Service mount result: $mounted")
            return mounted
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] Service mount failed", e)
            return false
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
