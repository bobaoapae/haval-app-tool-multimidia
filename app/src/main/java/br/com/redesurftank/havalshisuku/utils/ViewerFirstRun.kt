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
        val existing =
                normalize(current)
                        ?.split(":")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        .orEmpty()
        if (existing.any { sameComponent(it, component) }) return null
        return (existing + component).joinToString(":")
    }

    fun grantCommands(packageName: String, permissions: List<String>, allowOverlay: Boolean): List<Array<String>> {
        val commands = permissions.map { arrayOf("pm", "grant", packageName, it) }.toMutableList()
        if (allowOverlay) {
            commands.add(arrayOf("appops", "set", packageName, "SYSTEM_ALERT_WINDOW", "allow"))
        }
        return commands
    }

    fun putSecureCommand(key: String, value: String): Array<String> =
            arrayOf("sh", "-c", "settings put secure $key ${SilentApkInstall.shellQuote(value)}")

    fun notificationCommand(component: String, listeners: String): Array<String> {
        val quotedComponent = SilentApkInstall.shellQuote(component)
        val quotedListeners = SilentApkInstall.shellQuote(listeners)
        return arrayOf(
                "sh",
                "-c",
                "settings put secure enabled_notification_listeners $quotedListeners; " +
                        "cmd notification allow_listener $quotedComponent 0 >/dev/null 2>&1 || true"
        )
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
        return prepare(
                packageName,
                requested,
                services,
                isDangerous = { permission -> isDangerous(pm, permission) },
                shizukuReady = { ShizukuUtils.isShizukuAvailable() },
                run = { ShizukuUtils.runCommandAndGetOutput(it) }
        )
    }

    fun prepare(
            packageName: String,
            requested: List<String>,
            services: List<Pair<String, String>>,
            isDangerous: (String) -> Boolean,
            shizukuReady: () -> Boolean,
            run: (Array<String>) -> String,
    ): Boolean {
        if (!shizukuReady()) {
            Log.w(TAG, "Shizuku unavailable; launcher grants skipped")
            return false
        }
        val runtime = requested.filter { it != OVERLAY && isDangerous(it) }
        grantCommands(packageName, runtime, requested.contains(OVERLAY)).forEach { run(it) }

        val listener =
                services.firstOrNull { it.second == NOTIFICATION_LISTENER }?.let { component(packageName, it.first) }
        val accessibility =
                services.firstOrNull { it.second == ACCESSIBILITY }?.let { component(packageName, it.first) }

        withComponent(run(arrayOf("settings", "get", "secure", "enabled_notification_listeners")), listener)
                ?.let { run(notificationCommand(listener!!, it)) }
        val a11y =
                withComponent(
                        run(arrayOf("settings", "get", "secure", "enabled_accessibility_services")),
                        accessibility
                )
        if (a11y != null) {
            run(putSecureCommand("enabled_accessibility_services", a11y))
            run(arrayOf("settings", "put", "secure", "accessibility_enabled", "1"))
        }
        Log.w(TAG, "Launcher grants applied for $packageName")
        return true
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
