package br.com.redesurftank.havalshisuku.utils

import android.util.Log
import java.io.File

/**
 * Installs a verified APK with `pm install` through Shizuku, so the system installer never opens.
 *
 * Installing straight from the app's external-files dir is denied for `system_server` on this head
 * unit. That denial is ignored only while SELinux is permissive. A copy in `/data/local/tmp` is the
 * path that installed cleanly. The copy is removed whether `pm install` succeeds or not.
 */
object SilentApkInstall {
    private const val TAG = "SilentApkInstall"
    const val STAGE_DIR = "/data/local/tmp"

    fun stagedPath(packageName: String): String {
        val safe = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "$STAGE_DIR/$safe.apk"
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    fun installScript(sourcePath: String, stagedPath: String): String {
        val src = shellQuote(sourcePath)
        val dest = shellQuote(stagedPath)
        return "cp $src $dest && chmod 644 $dest && pm install -r -t $dest; ec=\$?; rm -f $dest; exit \$ec"
    }

    fun succeeded(output: String): Boolean =
        output.contains("Success") && !output.contains("Failure")

    fun install(
            source: File,
            packageName: String,
            shizukuReady: () -> Boolean = { ShizukuUtils.isShizukuAvailable() },
            run: (Array<String>) -> String = { ShizukuUtils.runCommandAndGetOutput(it) },
            log: (String) -> Unit = { Log.i(TAG, it) },
    ): Boolean {
        if (!source.isFile) {
            log("APK missing: ${source.absolutePath}")
            return false
        }
        if (!shizukuReady()) {
            log("Shizuku unavailable; silent install skipped")
            return false
        }
        val script = installScript(source.absolutePath, stagedPath(packageName))
        val output = run(arrayOf("sh", "-c", script))
        val ok = succeeded(output)
        log(
                if (ok) "Silent install succeeded for $packageName"
                else "Silent install failed for $packageName: $output"
        )
        return ok
    }
}
