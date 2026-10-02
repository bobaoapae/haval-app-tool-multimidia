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
 * Forces a package's window to a rect, for clients that cannot do it themselves.
 *
 * Two things on this ROM need it, both measured:
 *  - Tapping the freeform caption's maximize moves the task into the fullscreen
 *    stack but leaves its bounds at the old freeform rect, so the app keeps
 *    painting small inside a black screen.
 *  - Some apps (YouTube) ignore launch bounds and reopen at their remembered
 *    rect, so a slot launch lands in the wrong place.
 *
 * Both are fixed by `am stack resize`, which needs MANAGE_ACTIVITY_STACKS — we
 * have it through Shizuku, the caller does not.
 *
 * Deliberately typed: the caller sends a package and four ints, never a command
 * string. Nothing here reaches a shell that the caller controls.
 */
class TaskBoundsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_TASK_BOUNDS) return
        val caller = ImpulseApiCallers.verify(intent, ACTION_SET_TASK_BOUNDS) ?: return

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        if (packageName.isNullOrEmpty()) return
        val l = intent.getIntExtra(EXTRA_LEFT, -1)
        val t = intent.getIntExtra(EXTRA_TOP, -1)
        val r = intent.getIntExtra(EXTRA_RIGHT, -1)
        val b = intent.getIntExtra(EXTRA_BOTTOM, -1)
        if (l < 0 || t < 0 || r <= l || b <= t) {
            Log.w(TAG, "Ignoring $packageName: bad rect $l,$t,$r,$b")
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = DisplayAppLauncher.findTaskForPackage(packageName)
                if (task == null) {
                    Log.w(TAG, "No task for $packageName (from $caller)")
                } else {
                    DisplayAppLauncher.resizeStackForClient(task.stackId, l, t, r, b)
                    Log.w(TAG, "Resized $packageName stack=${task.stackId} to $l,$t,$r,$b (from $caller)")
                }
            } catch (th: Throwable) {
                Log.w(TAG, "Resize failed for $packageName", th)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TaskBoundsReceiver"
        const val ACTION_SET_TASK_BOUNDS = "br.com.redesurftank.havalshisuku.ACTION_SET_TASK_BOUNDS"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LEFT = "l"
        const val EXTRA_TOP = "t"
        const val EXTRA_RIGHT = "r"
        const val EXTRA_BOTTOM = "b"
    }
}
