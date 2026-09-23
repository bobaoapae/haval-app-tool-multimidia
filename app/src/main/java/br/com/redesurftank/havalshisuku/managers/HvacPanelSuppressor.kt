package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils

/**
 * Holds the OEM HVAC app (`com.beantechs.hvac`) disabled while something has a lease on it, so the
 * car's own A/C popup stays away and the viewer can show its own.
 *
 * Measured on the car 2026-09-22, and this is what makes the whole approach acceptable:
 *
 *  * physical A/C buttons and the temperature knob still actuate with the app disabled;
 *  * `car.hvac.panel_display_notify` still fires, so the trigger for our own popup survives;
 *  * the popup is launched by the system bar, not by the HVAC app, and that launch simply fails
 *    (a caught ActivityNotFoundException) — nothing crashes;
 *  * what IS lost is the nav-bar A/C tab, which opens the same missing screen.
 *
 * So a suppression that gets stuck costs the A/C SCREEN, never the A/C. That is the failure this is
 * designed around, and there are three ways back:
 *
 *  1. the lease drops when the viewer unbinds OR its process dies (the bound service links to the
 *     client's token), which is an event from the OS, not a poll;
 *  2. Impulse reconciles on service start;
 *  3. and on BOOT_COMPLETED, which is the only cover for "Impulse was killed and the car rebooted",
 *     because `pm disable-user` persists across reboots.
 *
 * [ServiceManager.updateData] also suspends this app briefly around its OWN writes, and its resume
 * runs `pm enable` unconditionally — so that path checks [isHeld] first, or it would undo a lease
 * held here on the first climate write from a theme.
 */
object HvacPanelSuppressor {

    private const val TAG = "HVAC_SUPPRESSOR"

    /** The OEM climate app. Also read by [ServiceManager] for its own short suspensions. */
    const val HVAC_PACKAGE = "com.beantechs.hvac"

    /** Holders of the lease, by tag (e.g. "viewer"). A lease is held while this is not empty. */
    private val holders = mutableSetOf<String>()

    private fun prefs() =
        App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    /** The user's opt-in. Without it a bound viewer changes nothing. */
    fun isFeatureEnabled(): Boolean =
        prefs().getBoolean(SharedPreferencesKeys.VIEWER_CLIMATE_HANDOFF.key, false)

    /**
     * "We disabled it and have not put it back."
     *
     * Written with `commit()`, not `apply()`: it has to be on disk BEFORE the disable runs, or a
     * process killed in between leaves a disabled HVAC app with nothing recording that we did it —
     * and the reconcile would then leave it disabled forever.
     */
    private fun markerSet(): Boolean =
        prefs().getBoolean(SharedPreferencesKeys.HVAC_SUPPRESSED_BY_APP.key, false)

    private fun setMarker(value: Boolean) {
        prefs().edit().putBoolean(SharedPreferencesKeys.HVAC_SUPPRESSED_BY_APP.key, value).commit()
    }

    /** True while the OEM app is being held disabled by this object. */
    @Synchronized
    fun isHeld(): Boolean =
        HvacSuppressionPolicy.leaseHeld(holders, isFeatureEnabled())

    /** Takes the lease for [holder]. Idempotent per holder. */
    @Synchronized
    fun acquire(holder: String) {
        if (holders.add(holder)) Log.w(TAG, "Lease taken by '$holder' (holders=$holders)")
        apply("acquire:$holder")
    }

    /** Drops [holder]'s lease. The OEM app comes back when the last holder goes. */
    @Synchronized
    fun release(holder: String) {
        if (holders.remove(holder)) Log.w(TAG, "Lease dropped by '$holder' (holders=$holders)")
        apply("release:$holder")
    }

    /**
     * Re-applies the decision without changing the holders. Call at service start, at boot, and
     * whenever the user's toggle changes — this is what recovers a car that was left with the OEM
     * app disabled by a crash or a reboot.
     */
    @Synchronized
    fun reconcile(reason: String) {
        apply("reconcile:$reason")
    }

    /**
     * `pm list packages -e` lists ENABLED packages only, so a match is positive confirmation. An
     * unreadable result returns false — "not confirmed" — which keeps the marker and makes the next
     * reconcile try again. Erring the other way would abandon a car with no A/C screen.
     */
    private fun isHvacEnabled(): Boolean = runCatching {
        val out = ShizukuUtils.runCommandAndGetOutput(
            arrayOf("pm", "list", "packages", "-e", HVAC_PACKAGE)
        )
        out != null && out.lineSequence().any { it.trim() == "package:$HVAC_PACKAGE" }
    }.getOrDefault(false)

    private fun apply(reason: String) {
        val held = HvacSuppressionPolicy.leaseHeld(holders, isFeatureEnabled())
        when (HvacSuppressionPolicy.decide(held, markerSet())) {
            HvacSuppressionPolicy.Action.NONE -> Unit

            HvacSuppressionPolicy.Action.DISABLE -> {
                // Marker first: a kill between here and the enable must read as "still disabled".
                setMarker(true)
                Log.w(TAG, "[$reason] Disabling $HVAC_PACKAGE")
                ClusterPersistentEventLogger.log(
                    "hvac_suppress_on",
                    mapOf("reason" to reason, "holders" to holders.joinToString(","))
                )
                ShizukuUtils.runCommandAndGetOutput(
                    arrayOf("pm", "disable-user", "--user", "0", HVAC_PACKAGE)
                )
                ShizukuUtils.runCommandAndGetOutput(arrayOf("am", "force-stop", HVAC_PACKAGE))
            }

            HvacSuppressionPolicy.Action.ENABLE -> {
                Log.w(TAG, "[$reason] Re-enabling $HVAC_PACKAGE")
                ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "enable", HVAC_PACKAGE))

                // Clear the marker only once the package is CONFIRMED enabled. A `pm enable` that
                // silently did nothing - Shizuku not bound yet is the realistic case, since this
                // runs at service start - would otherwise clear the one record that a later
                // reconcile needs, and the car would keep its A/C screen missing for good.
                val enabled = isHvacEnabled()
                if (enabled) {
                    setMarker(false)
                } else {
                    Log.e(TAG, "[$reason] $HVAC_PACKAGE still disabled after pm enable; keeping marker")
                }
                ClusterPersistentEventLogger.log(
                    "hvac_suppress_off",
                    mapOf("reason" to reason, "confirmed" to enabled.toString())
                )
            }
        }
    }
}
