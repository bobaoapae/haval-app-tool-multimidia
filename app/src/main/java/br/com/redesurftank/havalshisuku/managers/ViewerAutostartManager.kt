package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import java.io.File

/**
 * Opens the Haval H6 3D viewer on the main display as soon as the car boots.
 *
 * This is the simple alternative to taking the HOME role, which is not obtainable on this ROM:
 * `cmd package set-home-activity` is a no-op, and both `pm disable-user` and
 * `IPackageManager.setComponentEnabledSetting` report success while leaving the OEM launcher's
 * home activity enabled. All three were measured on the car. Here we simply launch the viewer
 * over whatever came up, which needs no package-manager cooperation at all.
 *
 * Timing is the whole problem. `BOOT_COMPLETED` arrives before the OEM launcher has settled, so a
 * single `startActivity` at t=0 is routinely overtaken by the launcher finishing its own start.
 * Hence [ATTEMPT_DELAYS_MS]: fire immediately for the best case, then re-check and re-assert over
 * the first few seconds.
 *
 * The retry loop stops as soon as either:
 *
 *  * the viewer is on top — done, or
 *  * the top package CHANGED from whatever was already there when the ladder began — the driver
 *    opened something mid-boot, and stealing focus back would be hostile.
 *
 * The yield rule keys on a CHANGE rather than a fixed allowlist. The first version assumed the OEM
 * launcher was the only thing that could legitimately be on top at boot; measured on the car,
 * `com.beantechs.mediacenter` is what comes up (media auto-resumes), so the ladder yielded at
 * attempt 0 and never called startActivity at all.
 *
 * A boot token (`/proc/sys/kernel/random/boot_id`, read directly rather than through Shizuku,
 * because this runs before Shizuku is necessarily up) makes the whole thing run once per boot, so
 * a service restart mid-drive never yanks the driver back to the viewer.
 */
object ViewerAutostartManager {
    private const val TAG = "VIEWER_AUTOSTART"

    private const val VIEWER_PACKAGE = "com.havalh6.viewer"
    private const val STOCK_LAUNCHER_PACKAGE = "com.beantechs.launcher"

    private const val PREF_BOOT_TOKEN = "viewerAutostartBootToken"
    private const val MAIN_DISPLAY_ID = 0

    /** First shot is immediate; the rest cover the window where the launcher is still settling. */
    private val ATTEMPT_DELAYS_MS = longArrayOf(0, 1_500, 4_000, 8_000)

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    /** Whatever was on top when the ladder began. Anything else later means the driver acted. */
    private var baselineTop: String? = null
    private var baselineCaptured = false

    private fun prefs() =
        App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean =
        prefs().getBoolean(SharedPreferencesKeys.AUTO_START_VIEWER_ON_BOOT.key, false)

    /**
     * Read straight from procfs — [DisplayAppLauncher.currentBootToken] goes through a Shizuku
     * shell, and this runs at `BOOT_COMPLETED` when Shizuku may not have bound yet.
     */
    private fun currentBootToken(): String =
        runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: "unknown"

    /**
     * Entry point. Safe to call more than once per boot and from more than one place — the boot
     * token makes every call after the first a no-op.
     */
    fun onBootCompleted(reason: String) {
        if (!isEnabled()) {
            Log.d(TAG, "[$reason] Viewer autostart is disabled in settings")
            return
        }
        if (running) return

        val token = currentBootToken()
        if (prefs().getString(PREF_BOOT_TOKEN, "") == token) {
            Log.d(TAG, "[$reason] Viewer autostart already ran for this boot")
            return
        }
        prefs().edit().putString(PREF_BOOT_TOKEN, token).apply()

        if (!isViewerInstalled()) {
            Log.e(TAG, "[$reason] Viewer autostart enabled but $VIEWER_PACKAGE is not installed")
            return
        }

        running = true
        baselineTop = null
        baselineCaptured = false
        ClusterPersistentEventLogger.log("viewer_autostart_started", mapOf("reason" to reason))
        ATTEMPT_DELAYS_MS.forEachIndexed { index, delay ->
            handler.postDelayed({ attempt(index) }, delay)
        }
    }

    private fun isViewerInstalled(): Boolean =
        runCatching {
            App.getContext().packageManager.getLaunchIntentForPackage(VIEWER_PACKAGE) != null
        }
            .getOrDefault(false)

    private fun attempt(index: Int) {
        if (!running) return

        // getTopPackageOnDisplay goes through Shizuku and returns null when it cannot tell; a null
        // must not be read as "the driver opened something else", so only a known foreign package
        // stops the loop.
        val top = runCatching { DisplayAppLauncher.getTopPackageOnDisplay(MAIN_DISPLAY_ID) }
            .getOrNull()

        // The first reading is the boot state, whatever it happens to be - the OEM launcher on
        // some boots, the media centre on others. Only a change away from it counts as the driver.
        if (!baselineCaptured && top != null) {
            baselineTop = top
            baselineCaptured = true
            Log.w(TAG, "Boot baseline top package is '$top'")
        }

        when {
            top == VIEWER_PACKAGE -> {
                Log.w(TAG, "Viewer is on top after attempt $index; autostart done")
                ClusterPersistentEventLogger.log(
                    "viewer_autostart_settled",
                    mapOf("attempt" to index)
                )
                running = false
                return
            }
            top != null && top != baselineTop && top != STOCK_LAUNCHER_PACKAGE -> {
                Log.w(TAG, "'$top' replaced '$baselineTop'; leaving it alone and stopping autostart")
                ClusterPersistentEventLogger.log(
                    "viewer_autostart_yielded",
                    mapOf("attempt" to index, "top" to top, "baseline" to baselineTop.orEmpty())
                )
                running = false
                return
            }
        }

        val started = startViewer()
        Log.w(TAG, "Viewer autostart attempt $index (top=$top started=$started)")
        if (index == ATTEMPT_DELAYS_MS.lastIndex) running = false
    }

    private fun startViewer(): Boolean =
        runCatching {
            val context = App.getContext()
            val intent =
                context.packageManager.getLaunchIntentForPackage(VIEWER_PACKAGE)
                    ?: return@runCatching false
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            context.startActivity(intent)
            true
        }
            .onFailure { Log.e(TAG, "startActivity for the viewer failed", it) }
            .getOrDefault(false)

    /** Manual "open it now" for the settings screen — no boot token, no retries. */
    fun launchNow(): Boolean {
        running = false
        handler.removeCallbacksAndMessages(null)
        return startViewer()
    }
}
