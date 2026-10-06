package br.com.redesurftank.havalshisuku.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.redesurftank.havalshisuku.api.ImpulseApiCallers
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Resolves a package's task id on behalf of the 3D viewer (`com.havalh6.viewer`).
 *
 * The viewer keeps freeform popups above its own fullscreen launcher by calling
 * `moveTaskToFront`, which its REORDER_TASKS permission already allows. What it
 * cannot do on Android 9 is *discover* a foreign task id: `getRunningTasks`
 * filters other apps out, and `registerTaskStackListener` needs
 * MANAGE_ACTIVITY_STACKS. We reach both through Shizuku, so we look the id up
 * and hand it over.
 *
 * One lookup per popup launch, never per tap: [DisplayAppLauncher.getStackList]
 * is a Shizuku shell round trip (~150-200 ms) and hammering it starves
 * shizuku_server's heap.
 */
class TaskResolveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESOLVE_TASK) return
        // Same gate as the rest of the API: prove the caller is registered.
        ImpulseApiCallers.verify(intent, ACTION_RESOLVE_TASK) ?: return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        if (packageName.isNullOrEmpty()) return
        val slot = intent.getStringExtra(EXTRA_SLOT).orEmpty()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            var taskId = -1
            try {
                taskId = DisplayAppLauncher.findTaskForPackage(packageName)?.taskId ?: -1
            } catch (t: Throwable) {
                Log.w(TAG, "findTaskForPackage failed for $packageName", t)
            }
            try {
                context.sendBroadcast(
                    Intent(ACTION_TASK_RESOLVED).apply {
                        // Targeted: an unscoped reply would let any app hand the
                        // viewer an arbitrary task id to raise.
                        setPackage(VIEWER_PACKAGE)
                        putExtra(EXTRA_PACKAGE, packageName)
                        putExtra(EXTRA_SLOT, slot)
                        putExtra(EXTRA_TASK_ID, taskId)
                    }
                )
                Log.d(TAG, "Resolved $packageName -> task $taskId (slot=$slot)")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to reply for $packageName", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TaskResolveReceiver"
        private const val VIEWER_PACKAGE = "com.havalh6.viewer"

        const val ACTION_RESOLVE_TASK = "br.com.redesurftank.havalshisuku.ACTION_RESOLVE_TASK"
        const val ACTION_TASK_RESOLVED = "br.com.redesurftank.havalshisuku.ACTION_TASK_RESOLVED"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_TASK_ID = "taskId"
    }
}
