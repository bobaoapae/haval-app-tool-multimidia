package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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
    private const val CLUSTER_MOUNT_POLL_MS = 3_000L
    private const val CLUSTER_SESSION_SETTLE_MS = 15_000L
    private const val CLUSTER_CERT_CHECK_DELAY_MS = 8_000L

    private val clusterWatchArmed = AtomicBoolean(false)
    private val clusterMountThread = HandlerThread("aa-cluster-mount").apply { start() }
    private val clusterMountHandler = Handler(clusterMountThread.looper)
    private var settledPid: String? = null
    private var settledSinceElapsedMs = 0L

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

    /** Blocking read-only checks. Call from an IO dispatcher, never during composition. */
    fun readPatchStatus(): AndroidAutoPatchStatus = AndroidAutoPatchStatus(
        readComponentStatus(APP_APK, VENDOR_APP_PATH),
        readComponentStatus(SERVICE_APK, VENDOR_SERVICE_PATH)
    )

    private fun readComponentStatus(assetName: String, vendorPath: String): AndroidAutoPatchStatus.Component {
        val staged = readFileEvidence("$PATCH_DIR/$assetName")
        val vendor = if (staged.presence == AndroidAutoPatchStatus.Presence.PRESENT) {
            readFileEvidence(vendorPath)
        } else {
            AndroidAutoPatchStatus.FileEvidence.unknown()
        }
        return AndroidAutoPatchStatus.Component.compare(staged, vendor)
    }

    private fun readFileEvidence(path: String): AndroidAutoPatchStatus.FileEvidence {
        val parent = path.substringBeforeLast('/')
        // Absence is conclusive only with an accessible parent, or an accessible ancestor
        // proving PATCH_DIR itself absent. Shell/access errors must not become "not installed".
        val absentPatchDir = if (parent == PATCH_DIR) {
            "elif [ ! -e '$PATCH_DIR' ] && [ ! -L '$PATCH_DIR' ] && " +
                    "[ -d '/data/local/tmp' ] && [ -r '/data/local/tmp' ] && [ -x '/data/local/tmp' ]; " +
                    "then echo AA_PATCH_ABSENT; "
        } else ""
        return try {
            val output = sh(
                "if [ -f '$path' ]; then echo AA_PATCH_PRESENT; md5sum '$path'; " +
                        "elif [ ! -e '$path' ] && [ ! -L '$path' ] && " +
                        "[ -d '$parent' ] && [ -r '$parent' ] && [ -x '$parent' ]; " +
                        "then echo AA_PATCH_ABSENT; " + absentPatchDir +
                        "else echo AA_PATCH_UNKNOWN; fi"
            )
            AndroidAutoPatchStatus.FileEvidence.fromShellOutput(path, output)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read Android Auto file evidence: $path", e)
            AndroidAutoPatchStatus.FileEvidence.unknown()
        }
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

        Log.w(TAG, "Applying Android Auto visual App mount")
        return applyAppMount()
    }

    fun isServiceClusterPatchMounted(): Boolean {
        if (!isServicePatchInstalled()) return false
        val vendorServiceMd5 = sh("md5sum '$VENDOR_SERVICE_PATH' 2>/dev/null | awk '{print \$1}'").trim()
        val patchServiceMd5 = installedPatchMd5(SERVICE_APK)
        return vendorServiceMd5.isNotEmpty() && vendorServiceMd5 == patchServiceMd5
    }

    /**
     * Bind-mount the patched Service APK. Does **not** force-stop
     * projectionservice — that drops USB accessory mode. Load on next AA start.
     */
    fun applyServiceMountWithoutForceStop(): Boolean {
        if (!isServicePatchInstalled()) {
            Log.e(TAG, "Cannot apply Android Auto Service mount: Service patch not installed")
            return false
        }
        return try {
            Log.i(TAG, "Applying Android Auto Service bind mount (no projectionservice force-stop)")
            sh("mkdir -p '$PATCH_DIR/empty_oat'")
            sh("chmod 755 '$PATCH_DIR/empty_oat'")
            sh("chmod 644 '$PATCH_DIR/$SERVICE_APK'")
            sh("chcon u:object_r:vendor_app_file:s0 '$PATCH_DIR/$SERVICE_APK'")

            sh("umount -l '$VENDOR_SERVICE_PATH' 2>/dev/null || true")
            sh("[ -d '$VENDOR_SERVICE_OAT' ] && umount -l '$VENDOR_SERVICE_OAT' 2>/dev/null || true")
            // After the lazy umount, the vendor path is the stock APK. The
            // staged file has to keep that mtime or the head unit rejects it.
            sh("touch -r '$VENDOR_SERVICE_PATH' '$PATCH_DIR/$SERVICE_APK' 2>/dev/null || true")

            val mountResult = sh("mount --bind '$PATCH_DIR/$SERVICE_APK' '$VENDOR_SERVICE_PATH'")
            if (mountResult.contains("error", ignoreCase = true) || mountResult.contains("failed", ignoreCase = true)) {
                Log.e(TAG, "Failed to mount Android Auto Service APK: $mountResult")
            }
            sh("[ -d '$VENDOR_SERVICE_OAT' ] && mount --bind '$PATCH_DIR/empty_oat' '$VENDOR_SERVICE_OAT' || true")
            sh("rm -f /data/dalvik-cache/arm64/*AndroidAutoService* /data/dalvik-cache/arm64/*com.ts.androidauto* 2>/dev/null || true")

            val success = isServiceClusterPatchMounted()
            if (success) {
                Log.w(TAG, "Android Auto Service CLUSTER patch mounted; takes effect on next AA session")
            } else {
                Log.e(TAG, "Android Auto Service mount verification failed")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Android Auto Service mount", e)
            false
        }
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
     * Auto-mount the visual Android Auto app after Shizuku is ready.
     * The cluster Service is a separate opt-in: [ensureClusterServiceAutoMount].
     */
    fun ensureMounted() {
        ensureVisualMounted()
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
                val success = applyAppMount()
                Log.i(TAG, "Visual auto-mount result: $success")
            } else {
                Log.d(TAG, "Android Auto visual patch already mounted, nothing to do")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Android Auto visual auto-mount failed", e)
        }
    }

    private fun isClusterServiceAutoMountEnabled(): Boolean {
        return App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            .getBoolean(SharedPreferencesKeys.AA_CLUSTER_SERVICE_AUTO_MOUNT.key, false)
    }

    private fun setClusterServiceAutoMountEnabled(enabled: Boolean) {
        App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SharedPreferencesKeys.AA_CLUSTER_SERVICE_AUTO_MOUNT.key, enabled)
            .apply()
    }

    /**
     * Restores the cluster Service after reboot, once. The visual Auto-montar
     * switch does not call this.
     *
     * Stock Android Auto has to finish its handshake first. The client
     * certificate is decrypted from the vehicle model and year, and a reload
     * before that push fails the same way on the patched Service and on stock.
     * One SIGTERM after the phone session has been up on a stable pid. Never
     * `am force-stop` or `am startservice`.
     *
     * The vendor file underneath the bind has to be the known stock build
     * [EXPECTED_STOCK_SERVICE_MD5]. A different firmware is left alone.
     * If the reloaded process hits the certificate crash, the bind comes off
     * and this opt-in turns itself off so the next boot stays on stock.
     */
    fun ensureClusterServiceAutoMount() {
        if (!isClusterServiceAutoMountEnabled()) return
        if (!clusterWatchArmed.compareAndSet(false, true)) return
        clusterMountHandler.post { pollClusterServiceMount() }
    }

    private fun pollClusterServiceMount() {
        try {
            if (!isClusterServiceAutoMountEnabled()) return
            if (isServiceClusterPatchMounted()) {
                Log.w(TAG, "CLUSTER service already mounted; running process left as-is")
                return
            }
            if (!isAndroidAutoSessionSettled()) {
                settledPid = null
                clusterMountHandler.postDelayed({ pollClusterServiceMount() }, CLUSTER_MOUNT_POLL_MS)
                return
            }
            val pid = androidAutoPid()
            if (pid == null) {
                settledPid = null
                clusterMountHandler.postDelayed({ pollClusterServiceMount() }, CLUSTER_MOUNT_POLL_MS)
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (pid != settledPid) {
                settledPid = pid
                settledSinceElapsedMs = now
                Log.w(TAG, "CLUSTER mount waiting until pid=$pid has held the session for ${CLUSTER_SESSION_SETTLE_MS}ms")
                clusterMountHandler.postDelayed({ pollClusterServiceMount() }, CLUSTER_MOUNT_POLL_MS)
                return
            }
            if (now - settledSinceElapsedMs < CLUSTER_SESSION_SETTLE_MS) {
                clusterMountHandler.postDelayed({ pollClusterServiceMount() }, CLUSTER_MOUNT_POLL_MS)
                return
            }
            val stockMd5 = vendorServiceMd5()
            if (stockMd5.isEmpty()) {
                clusterMountHandler.postDelayed({ pollClusterServiceMount() }, CLUSTER_MOUNT_POLL_MS)
                return
            }
            if (stockMd5 != EXPECTED_STOCK_SERVICE_MD5) {
                Log.w(TAG, "CLUSTER mount refused: stock Service md5=$stockMd5 expected=$EXPECTED_STOCK_SERVICE_MD5")
                return
            }
            val context = App.getContext()
            val bundledServiceMd5 = bundledPatchMd5(context, SERVICE_APK)
            if (bundledServiceMd5 == null) {
                Log.w(TAG, "No bundled Android Auto Service patch; cluster map stays on stock")
                return
            }
            if (!isServicePatchInstalled() || bundledServiceMd5 != installedPatchMd5(SERVICE_APK)) {
                Log.w(TAG, "Installing bundled Android Auto Service CLUSTER patch")
                if (!installServicePatch(context)) {
                    Log.e(TAG, "Cannot auto-mount Android Auto Service patch: install failed")
                    clusterMountHandler.postDelayed({ pollClusterServiceMount() }, CLUSTER_MOUNT_POLL_MS)
                    return
                }
            }
            val mounted = applyServiceMountWithoutForceStop()
            Log.w(TAG, "Service CLUSTER auto-mount result: $mounted")
            if (!mounted) return
            val since = SimpleDateFormat("MM-dd HH:mm:ss.000", Locale.US).format(Date())
            reloadAndroidAutoServiceProcess("CLUSTER_AFTER_SESSION")
            clusterMountHandler.postDelayed({ rollbackClusterMountIfCertFailed(since) }, CLUSTER_CERT_CHECK_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Android Auto Service CLUSTER auto-mount failed", e)
        }
    }

    private fun isAndroidAutoSessionSettled(): Boolean {
        if (DisplayAppLauncher.hasRecentAndroidAutoDcmProjectionActiveEvidenceForSession()) return true
        val link = DisplayAppLauncher.readAndroidAutoLinkStatusIfAlreadyBound("CLUSTER_MOUNT")
        return link == AndroidAutoSessionTelemetry.LINK_STATUS_ACTIVATED
    }

    private fun vendorServiceMd5(): String {
        return sh("md5sum '$VENDOR_SERVICE_PATH' 2>/dev/null | awk '{print \$1}'").trim()
    }

    private fun androidAutoPid(): String? {
        val pid = sh("pidof com.ts.androidauto 2>/dev/null || true").trim().split(Regex("\\s+")).firstOrNull()
        return pid?.takeIf { it.toIntOrNull() != null }
    }

    private fun rollbackClusterMountIfCertFailed(sinceLogTimestamp: String) {
        val crash = sh(
            "logcat -d -t '$sinceLogTimestamp' 2>/dev/null | grep -E 'Failed to set client certificate|SSL initialization failed' | tail -n 3"
        ).trim()
        if (crash.isEmpty()) {
            Log.w(TAG, "CLUSTER service reload stayed up; certificate crash not seen")
            return
        }
        Log.w(TAG, "CLUSTER mount rolled back after certificate crash: $crash")
        sh("umount -l '$VENDOR_SERVICE_PATH' 2>/dev/null || true")
        sh("[ -d '$VENDOR_SERVICE_OAT' ] && umount -l '$VENDOR_SERVICE_OAT' 2>/dev/null || true")
        setClusterServiceAutoMountEnabled(false)
        Log.w(TAG, "CLUSTER service opt-in turned off; next boot stays on the stock Service")
    }

    private fun reloadAndroidAutoServiceProcess(reason: String) {
        val pidList = sh("pidof com.ts.androidauto 2>/dev/null || true")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (pidList.isEmpty() || pidList.any { it.toIntOrNull() == null }) {
            Log.w(TAG, "[$reason] com.ts.androidauto is not running; CLUSTER patch loads on its next start")
            return
        }
        Log.w(
            TAG,
            "[$reason] SIGTERM com.ts.androidauto pid=${pidList.joinToString(",")} so the mounted CLUSTER Service loads"
        )
        sh("kill -TERM ${pidList.joinToString(" ")} 2>/dev/null || true")
        try {
            Thread.sleep(500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val restarted = sh("pidof com.ts.androidauto 2>/dev/null || true").trim()
        if (restarted.isEmpty()) {
            Log.w(TAG, "[$reason] com.ts.androidauto has not returned yet; mount left in place")
        } else {
            Log.w(TAG, "[$reason] com.ts.androidauto restarted pid=$restarted")
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

        sb.append("--- File evidence (not runtime validation) ---\n")
        sb.append("${AndroidAutoPatchStatus.RUNTIME_NOTICE}\n\n")
        sb.append("OAT Mounts:\n")
        val oatCheck1 = sh("ls /vendor/app/AndroidAutoService/oat 2>/dev/null").trim()
        val oatCheck2 = sh("ls /vendor/app/AndroidAutoApp/oat 2>/dev/null").trim()
        sb.append("  Service OAT output (empty is not mount proof): $oatCheck1\n")
        sb.append("  App OAT output (empty is not mount proof): $oatCheck2\n\n")
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
            val validMd5 = Regex("[0-9a-fA-F]{32}")
            if (!validMd5.matches(vendorMd5) || !validMd5.matches(patchMd5)) {
                sb.append("  Result: UNKNOWN (missing or invalid checksum)\n")
            } else if (vendorMd5.equals(patchMd5, ignoreCase = true)) {
                sb.append("  Result: MD5 MATCH (file evidence only)\n")
            } else {
                sb.append("  Result: MD5 DIFFERENT (file evidence only)\n")
            }
        }
        return sb.toString()
    }
}
