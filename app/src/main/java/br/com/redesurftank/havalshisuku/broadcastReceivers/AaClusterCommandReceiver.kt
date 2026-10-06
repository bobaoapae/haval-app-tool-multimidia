package br.com.redesurftank.havalshisuku.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.redesurftank.havalshisuku.api.ImpulseApiCallers
import br.com.redesurftank.havalshisuku.managers.AndroidAutoClusterController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Theme-equivalent command for external apps (3D viewer) to show or hide
 * the Android Auto CLUSTER map on display 3. MAIN stays on display 0.
 */
class AaClusterCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AA_CLUSTER) return
        val caller = ImpulseApiCallers.verify(intent, ACTION_AA_CLUSTER) ?: return
        val enabled = parseEnabled(intent) ?: run {
            Log.w(TAG, "Ignoring ACTION_AA_CLUSTER from $caller: missing enabled extra")
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AndroidAutoClusterController.setClusterMapEnabled(enabled, "receiver:$caller")
            } catch (th: Throwable) {
                Log.w(TAG, "ACTION_AA_CLUSTER failed from $caller", th)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AaClusterCommand"
        const val ACTION_AA_CLUSTER = "br.com.redesurftank.havalshisuku.ACTION_AA_CLUSTER"
        const val EXTRA_ENABLED = "enabled"

        fun parseEnabled(intent: Intent): Boolean? {
            if (intent.hasExtra(EXTRA_ENABLED) && intent.extras?.get(EXTRA_ENABLED) is Boolean) {
                return intent.getBooleanExtra(EXTRA_ENABLED, false)
            }
            return parseEnabledString(intent.getStringExtra(EXTRA_ENABLED))
        }

        fun parseEnabledString(asString: String?): Boolean? {
            if (asString == null) return null
            return when (asString.trim().lowercase()) {
                "true", "1", "on" -> true
                "false", "0", "off" -> false
                else -> null
            }
        }
    }
}
