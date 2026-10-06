package br.com.redesurftank.havalshisuku.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.redesurftank.havalshisuku.services.BottomBarService
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils

class BottomBarBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action || "android.intent.action.QUICKBOOT_POWERON" == action) {
            val prefs = br.com.redesurftank.App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, false)
            
            if (!isEnabled) {
                Log.d("BottomBarBootReceiver", "Bottom bar is disabled, skipping")
                return
            }

            Log.d("BottomBarBootReceiver", "Boot completed, setting up bottom bar")
            
            // Inicia o serviço da barra
            val serviceIntent = Intent(context, BottomBarService::class.java)
            try {
                if (android.provider.Settings.canDrawOverlays(context)) {
                    context.startService(serviceIntent)
                } else {
                    Log.e("BottomBarBootReceiver", "Overlay permission not granted, cannot start BottomBarService")
                }
            } catch (e: Exception) {
                Log.e("BottomBarBootReceiver", "Failed to start BottomBarService", e)
            }

            // Aplica o overscan via Shizuku
            Thread {
                try {
                    val overscan = prefs.getInt(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 20)
                    val result = ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,$overscan"))

                    if (result.isNotEmpty()) {
                        Log.d("BottomBarBootReceiver", "Overscan applied successfully: $result")
                    } else {
                        Log.w("BottomBarBootReceiver", "Overscan command returned empty, check Shizuku status")
                    }
                } catch (e: Exception) {
                    Log.e("BottomBarBootReceiver", "Failed to apply overscan", e)
                }
            }.start()
        }
    }
}
