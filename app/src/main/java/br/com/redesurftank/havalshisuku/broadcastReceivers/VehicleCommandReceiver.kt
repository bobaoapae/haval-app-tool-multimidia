package br.com.redesurftank.havalshisuku.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.redesurftank.havalshisuku.api.ImpulseApiCallers
import br.com.redesurftank.havalshisuku.managers.ServiceManager

/**
 * Body-control commands for the 3D viewer's car hotspots.
 *
 * These go through [ServiceManager.invokeVehicleCommand], which calls the
 * voice-adapter [com.beantechs.voice.adapter.IVehicle] binder — the same path
 * Impulse already uses to close windows/sunroof on speed / mirror fold.
 */
class VehicleCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_VEHICLE_COMMAND) return
        val caller = ImpulseApiCallers.verify(intent, ACTION_VEHICLE_COMMAND) ?: return
        val command = intent.getStringExtra(EXTRA_COMMAND).orEmpty().trim().lowercase()
        if (command !in NO_VALUE_COMMANDS && command !in LEVEL_COMMANDS) {
            Log.w(TAG, "Rejected unsupported command=$command from $caller")
            return
        }
        val suppliedValue = intent.getStringExtra(EXTRA_VALUE)
        val value = when (command) {
            in NO_VALUE_COMMANDS -> {
                if (!suppliedValue.isNullOrEmpty()) {
                    Log.w(TAG, "Rejected unexpected value for $command from $caller")
                    return
                }
                null
            }
            else -> canonicalLevel(suppliedValue) ?: run {
                Log.w(TAG, "Rejected invalid level for $command from $caller")
                return
            }
        }
        try {
            val ok = ServiceManager.getInstance().invokeVehicleCommand(command, value)
            Log.w(TAG, "command=$command value=$value ok=$ok from=$caller")
        } catch (t: Throwable) {
            Log.w(TAG, "command=$command failed", t)
        }
    }

    companion object {
        private const val TAG = "VehicleCommandReceiver"
        const val ACTION_VEHICLE_COMMAND =
            "br.com.redesurftank.havalshisuku.ACTION_VEHICLE_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_VALUE = "value"

        /** Commands that must not carry a value payload. */
        private val NO_VALUE_COMMANDS = setOf(
            "open_windows", "close_windows", "toggle_windows",
            "open_sunroof", "close_sunroof", "toggle_sunroof",
            "open_curtain", "close_curtain", "toggle_curtain",
            "toggle_doors_all", "toggle_trunk",
            "toggle_door_fl", "toggle_door_fr", "toggle_door_rl", "toggle_door_rr",
        )

        /** Commands whose value is a UI percentage, inclusive. */
        private val LEVEL_COMMANDS = setOf("set_curtain_level", "set_sunroof_level")

        /** Reject malformed values rather than treating them as a request to close. */
        private fun canonicalLevel(raw: String?): String? {
            if (raw.isNullOrEmpty() || raw.any { it !in '0'..'9' }) return null
            val level = raw.toIntOrNull() ?: return null
            return if (level in 0..100) level.toString() else null
        }
    }
}
