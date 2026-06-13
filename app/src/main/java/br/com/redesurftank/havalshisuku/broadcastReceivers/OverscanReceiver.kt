package br.com.redesurftank.havalshisuku.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys

class OverscanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = br.com.redesurftank.App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

        when (intent.action) {
            "br.com.redesurftank.havalshisuku.ACTION_UPDATE_OVERSCAN" -> {
                val value = intent.getIntExtra("value", 60)
                prefs.edit().putInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, value).apply()
                Log.d("OverscanReceiver", "Global overscan preference updated to $value — reapplying Display 0 bounds")

                // v2.3: re-resize every Impulse-managed Display 0 app so the
                // new overscan actually shrinks them. wm overscan handles the
                // long tail of unmanaged apps; Impulse-managed apps need an
                // explicit am stack resize because our bounds otherwise
                // override the system overscan.
                DisplayAppLauncher.reapplyDisplay0BoundsForOverscanAsync()
            }
        }
    }
}
