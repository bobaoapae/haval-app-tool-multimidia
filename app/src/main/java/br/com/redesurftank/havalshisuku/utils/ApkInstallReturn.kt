package br.com.redesurftank.havalshisuku.utils

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * After the system installer is opened, brings Impulse back once that package is actually installed.
 *
 * The confirm screen stays up until PackageManager commits the APK. [arm] remembers one package.
 * `PACKAGE_ADDED` / `PACKAGE_REPLACED` for that package, within [TIMEOUT_MS], moves this app's
 * task to the front. A cancel never sends those broadcasts, so Impulse stays out of the way.
 */
object ApkInstallReturn {
    private const val TAG = "ApkInstallReturn"
    const val TIMEOUT_MS = 3 * 60 * 1000L
    const val ACTIVITY = "br.com.redesurftank.havalshisuku.MainActivity"

    private val lock = Any()
    private var expected: String? = null
    private var deadlineElapsed: Long = 0L
    private var registered = false

    fun returnFlags(): Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

    fun shizukuArgs(applicationId: String): Array<String> =
            arrayOf("am", "start", "-n", "$applicationId/$ACTIVITY", "-f", returnFlags().toString())

    fun succeeded(output: String): Boolean {
        if (output.isBlank()) return false
        return !output.contains("Error", ignoreCase = true) &&
                !output.contains("Exception") &&
                !output.contains("Permission Denial")
    }

    /**
     * True only for the armed package, and only the first event before the deadline.
     * Anything else leaves the wait in place, except an event that arrives after the deadline,
     * which drops the wait.
     */
    fun shouldReturn(changedPackage: String?, now: Long): Boolean {
        if (changedPackage.isNullOrEmpty()) return false
        synchronized(lock) {
            val want = expected ?: return false
            if (now > deadlineElapsed) {
                expected = null
                return false
            }
            if (changedPackage != want) return false
            expected = null
            return true
        }
    }

    internal fun remember(packageName: String, now: Long) {
        synchronized(lock) {
            expected = packageName
            deadlineElapsed = now + TIMEOUT_MS
        }
    }

    fun arm(context: Context, packageName: String, now: Long = SystemClock.elapsedRealtime()) {
        remember(packageName, now)
        synchronized(lock) {
            if (registered) return
            registered = true
        }
        val app = context.applicationContext
        val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val changed = intent.data?.schemeSpecificPart
                        if (!shouldReturn(changed, SystemClock.elapsedRealtime())) return
                        val pending = goAsync()
                        Thread(
                                        {
                                            try {
                                                val ok = bringToFront(ctx)
                                                Log.w(TAG, "Return to Impulse after $changed: $ok")
                                            } finally {
                                                pending.finish()
                                            }
                                        },
                                        "apk-install-return"
                                )
                                .start()
                    }
                }
        try {
            ContextCompat.registerReceiver(
                    app,
                    receiver,
                    IntentFilter().apply {
                        addAction(Intent.ACTION_PACKAGE_ADDED)
                        addAction(Intent.ACTION_PACKAGE_REPLACED)
                        addDataScheme("package")
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (t: Throwable) {
            synchronized(lock) { registered = false }
            Log.e(TAG, "Could not watch package install", t)
        }
    }

    fun bringToFront(context: Context): Boolean =
            bringToFront(
                    context.packageName,
                    { ShizukuUtils.isShizukuAvailable() },
                    { ShizukuUtils.runCommandAndGetOutput(it) },
                    {
                        context.startActivity(
                                Intent()
                                        .setComponent(ComponentName(context.packageName, ACTIVITY))
                                        .addFlags(returnFlags())
                        )
                    }
            )

    fun bringToFront(
            applicationId: String,
            shizukuReady: () -> Boolean,
            run: (Array<String>) -> String,
            startActivity: () -> Unit,
    ): Boolean {
        if (shizukuReady()) {
            val output = run(shizukuArgs(applicationId))
            if (succeeded(output)) return true
        }
        return runCatching {
                    startActivity()
                    true
                }
                .getOrDefault(false)
    }

    internal fun resetForTest() {
        synchronized(lock) {
            expected = null
            deadlineElapsed = 0L
        }
    }
}
