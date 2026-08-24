package br.com.redesurftank.havalshisuku.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.redesurftank.havalshisuku.managers.ServiceManager

/**
 * Writes an allowlisted vehicle setting on behalf of the 3D viewer
 * (`com.havalh6.viewer`).
 *
 * The viewer can read telemetry via `EVENT_CHANGED`, but it has no Shizuku
 * path to the CAN bus. Impulse already exposes the same keys through
 * [br.com.redesurftank.havalshisuku.bridge.ThemeBridgeImpl.updateCarData];
 * this receiver is that write, as an explicit broadcast, so the viewer's
 * MODES widget can tap a chip and set drive / EV / steer / regen / ESP.
 *
 * Keep [WRITABLE_CAR_KEYS] in sync with ThemeBridgeImpl's writable set.
 */
class CarDataWriteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UPDATE_CAR_DATA) return
        val key = intent.getStringExtra(EXTRA_KEY).orEmpty()
        val value = intent.getStringExtra(EXTRA_VALUE).orEmpty()
        if (key.isEmpty() || key !in WRITABLE_CAR_KEYS) {
            Log.w(TAG, "Blocked write to non-allowlisted vehicle key: $key")
            return
        }
        if (value.length > 64) {
            Log.w(TAG, "Blocked oversized write for $key")
            return
        }
        try {
            ServiceManager.getInstance().updateData(key, value)
        } catch (t: Throwable) {
            Log.w(TAG, "updateData failed for $key", t)
        }
    }

    companion object {
        private const val TAG = "CarDataWriteReceiver"
        const val ACTION_UPDATE_CAR_DATA =
            "br.com.redesurftank.havalshisuku.ACTION_UPDATE_CAR_DATA"
        const val EXTRA_KEY = "key"
        const val EXTRA_VALUE = "value"

        private val WRITABLE_CAR_KEYS = setOf(
            "car.drive_setting.esp_enable",
            "car.ev_setting.power_model_config",
            "car.ev_setting.power_reserve_config",
            "car.ev_setting.charge_soc_target_config",
            "car.drive_setting.drive_mode",
            "car.drive_setting.steering_wheel_assist_mode",
            "car.ev_setting.energy_recovery_level",
            "car.ev.setting.pedal_control_enable",
            "car.hvac.power_mode",
            "car.hvac.fan_speed",
            "car.hvac.driver_temperature",
            "car.hvac.cycle_mode",
            "car.hvac.auto_enable",
            "car.hvac.anion_enable"
        )
    }
}
