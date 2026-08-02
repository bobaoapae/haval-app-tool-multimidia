package br.com.redesurftank.havalshisuku.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.redesurftank.havalshisuku.managers.AutoBrightnessManager

class AutoBrightnessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.w("AutoBrightnessReceiver", "onReceive: ${intent.action}")
        // O manager serializa localização, geocode, alarmes e bridge veicular no worker próprio.
        // Se a função estiver desligada, updateSchedule apenas cancela alarmes e não altera brilho.
        AutoBrightnessManager.getInstance().updateSchedule()
    }
}
