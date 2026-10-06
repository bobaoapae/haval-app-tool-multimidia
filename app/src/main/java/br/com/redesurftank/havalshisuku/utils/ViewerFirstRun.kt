package br.com.redesurftank.havalshisuku.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.util.Log

/**
 * Grants the launcher what it needs before the first open, so its own permission screens stay down.
 *
 * Runtime permissions go through `pm grant`. Overlay, the notification listener, and the
 * accessibility service are app-ops / secure settings. Existing Impulse entries in those settings
 * are kept.
 */
object ViewerFirstRun {
    private const val TAG = "ViewerFirstRun"
    const val EXIT_MARKER = "__EC:"
    const val OVERLAY = "android.permission.SYSTEM_ALERT_WINDOW"
    const val NOTIFICATION_LISTENER = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    const val ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"

    fun sameComponent(left: String, right: String): Boolean {
        val a = splitComponent(left) ?: return left == right
        val b = splitComponent(right) ?: return left == right
        return a == b
    }

    /** Secure-setting value after [component] is included. Null when nothing should be written. */
    fun withComponent(current: String?, component: String?): String? {
        if (component.isNullOrBlank()) return null
        val existing = settingEntries(current)
        if (existing.any { sameComponent(it, component) }) return null
        return (existing + component).joinToString(":")
    }

    /** True when every component already enabled is still present in [updated]. */
    fun keepsExisting(current: String?, updated: String): Boolean {
        val next = settingEntries(updated)
        return settingEntries(current).all { old -> next.any { sameComponent(it, old) } }
    }

    /**
     * Splits a shell command that prints [EXIT_MARKER] and its exit code on the last line.
     * Missing marker means the command did not finish in a way we can trust.
     */
    fun splitCommandResult(raw: String): Pair<String, Boolean> {
        val idx = raw.lastIndexOf(EXIT_MARKER)
        if (idx < 0) return raw.trim() to false
        val code = raw.substring(idx + EXIT_MARKER.length).trim().lineSequence().firstOrNull()?.toIntOrNull()
        return raw.substring(0, idx).trim() to (code == 0)
    }

    fun grantCommands(packageName: String, permissions: List<String>, allowOverlay: Boolean): List<Array<String>> {
        val commands = permissions.map { tracked("pm", "grant", packageName, it) }.toMutableList()
        if (allowOverlay) {
            commands.add(tracked("appops", "set", packageName, "SYSTEM_ALERT_WINDOW", "allow"))
        }
        return commands
    }

    /** Runs [args] and prints [EXIT_MARKER] plus the exit code, so a failed command is visible in stdout. */
    fun tracked(vararg args: String): Array<String> {
        val script = args.joinToString(" ") { SilentApkInstall.shellQuote(it) }
        return arrayOf("sh", "-c", "$script; ec=\$?; echo $EXIT_MARKER\$ec; exit \$ec")
    }

    fun putSecureCommand(key: String, value: String): Array<String> =
            tracked("settings", "put", "secure", key, value)

    fun settingsGetCommand(key: String): Array<String> = tracked("settings", "get", "secure", key)

    fun notificationCommand(component: String, listeners: String): Array<String> {
        val quotedComponent = SilentApkInstall.shellQuote(component)
        val quotedListeners = SilentApkInstall.shellQuote(listeners)
        // The listener allow-list is best-effort. The exit code stays that of `settings put`,
        // so a failed write is not hidden by `|| true`.
        val script =
                "settings put secure enabled_notification_listeners $quotedListeners; ec=\$?; " +
                        "cmd notification allow_listener $quotedComponent 0 >/dev/null 2>&1 || true; " +
                        "echo $EXIT_MARKER\$ec; exit \$ec"
        return arrayOf("sh", "-c", script)
    }

    /**
     * Applies grants for [packageName]. Returns false when Shizuku is down or the package cannot
     * be read; the caller can still launch.
     */
    fun prepare(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val info =
                try {
                    pm.getPackageInfo(
                            packageName,
                            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "Package not readable: $packageName", t)
                    return false
                }
        val requested = info.requestedPermissions?.toList().orEmpty()
        val services = info.services?.map { it.name.orEmpty() to it.permission.orEmpty() }.orEmpty()
        val ok =
                prepare(
                        packageName,
                        requested,
                        services,
                        isDangerous = { permission -> isDangerous(pm, permission) },
                        shizukuReady = { ShizukuUtils.isShizukuAvailable() },
                        run = { ShizukuUtils.runCommandAndGetOutput(it) }
                )
        Log.w(TAG, if (ok) "Launcher grants applied for $packageName" else "Launcher grants incomplete for $packageName")
        return ok
    }

    fun prepare(
            packageName: String,
            requested: List<String>,
            services: List<Pair<String, String>>,
            isDangerous: (String) -> Boolean,
            shizukuReady: () -> Boolean,
            run: (Array<String>) -> String,
    ): Boolean {
        if (!shizukuReady()) return false
        var ok = true
        val runtime = requested.filter { it != OVERLAY && isDangerous(it) }
        grantCommands(packageName, runtime, requested.contains(OVERLAY)).forEach { command ->
            if (!splitCommandResult(run(command)).second) ok = false
        }

        val listener =
                services.firstOrNull { it.second == NOTIFICATION_LISTENER }?.let { component(packageName, it.first) }
        val accessibility =
                services.firstOrNull { it.second == ACCESSIBILITY }?.let { component(packageName, it.first) }

        if (!enableListedService(
                        "enabled_notification_listeners",
                        listener,
                        run,
                        write = { value -> notificationCommand(listener.orEmpty(), value) }
                )
        ) {
            ok = false
        }
        if (!enableListedService(
                        "enabled_accessibility_services",
                        accessibility,
                        run,
                        write = { value -> putSecureCommand("enabled_accessibility_services", value) },
                        afterListed = { splitCommandResult(run(tracked("settings", "put", "secure", "accessibility_enabled", "1"))).second }
                )
        ) {
            ok = false
        }
        return ok
    }

    /**
     * Reads a colon-separated secure setting and appends [component] only when that read succeeded
     * and the previous entries are still in the new value. A failed read does not write.
     * [afterListed] still runs when the component was already present, so a half-finished
     * accessibility toggle is retried next time.
     */
    private fun enableListedService(
            settingKey: String,
            component: String?,
            run: (Array<String>) -> String,
            write: (String) -> Array<String>,
            afterListed: () -> Boolean = { true },
    ): Boolean {
        if (component.isNullOrBlank()) return true
        val (current, readOk) = splitCommandResult(run(settingsGetCommand(settingKey)))
        if (!readOk) return false
        val updated = withComponent(current, component)
        if (updated != null) {
            if (!keepsExisting(current, updated)) return false
            val (_, wrote) = splitCommandResult(run(write(updated)))
            if (!wrote) return false
        }
        return afterListed()
    }

    private fun isDangerous(pm: PackageManager, permission: String): Boolean {
        val info =
                try {
                    pm.getPermissionInfo(permission, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    return false
                }
        val base = info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        return base == PermissionInfo.PROTECTION_DANGEROUS
    }

    private fun component(packageName: String, className: String): String {
        val cls = if (className.startsWith(".")) packageName + className else className
        return "$packageName/$cls"
    }

    private fun settingEntries(value: String?): List<String> =
            normalize(value)?.split(":")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private fun normalize(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return null
        return trimmed
    }

    private fun splitComponent(raw: String): Pair<String, String>? {
        val slash = raw.indexOf('/')
        if (slash <= 0 || slash >= raw.lastIndex) return null
        val pkg = raw.substring(0, slash)
        var cls = raw.substring(slash + 1)
        if (cls.startsWith(".")) cls = pkg + cls
        return pkg to cls
    }
}
