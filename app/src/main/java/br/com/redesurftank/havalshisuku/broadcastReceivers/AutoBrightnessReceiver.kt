package br.com.redesurftank.havalshisuku.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.redesurftank.havalshisuku.managers.AutoBrightnessManager

class AutoBrightnessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.w("AutoBrightnessReceiver", "onReceive: ${intent.action}")
        // Fora da main thread: updateSchedule() pode fazer getLastKnownLocation (binder síncrono).
        // goAsync mantém o receiver vivo enquanto a thread roda. Também trata TIME_SET/TIMEZONE_CHANGED
        // (registrados no manifesto) pra re-armar quando o relógio do carro é corrigido.
        val pending = goAsync()
        Thread {
            try {
                AutoBrightnessManager.getInstance().updateSchedule()
            } catch (e: Exception) {
                Log.e("AutoBrightnessReceiver", "updateSchedule falhou", e)
            } finally {
                pending.finish()
            }
        }.also { it.isDaemon = true }.start()
    }
}
