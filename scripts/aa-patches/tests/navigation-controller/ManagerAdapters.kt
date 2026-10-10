package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType

/** Minimal dependency adapters, not implementations of vendor/session protocols. */
object AndroidAutoTelemetryKeys {
    const val SESSION = "session"
    const val SESSION_STOPPED = "stopped"
    const val SESSION_ACTIVE = "active"
    const val DIRECTIONS = "directions"
}

object AndroidAutoSessionTelemetry {
    const val LINK_STATUS_ACTIVATED = 1
    // The test supplies stable link evidence; it does not test the vendor/session policy.
    fun statusForPoll(linkStatus: Int?, dcmEvidenceRecent: Boolean, sessionActive: Boolean): Int? {
        if (linkStatus != null) return linkStatus
        if (dcmEvidenceRecent) return LINK_STATUS_ACTIVATED
        if (sessionActive) return 0
        return null
    }
    class Debouncer {
        fun onStatus(status: Int?, nowMs: Long): String? = when (status) {
            null -> null
            LINK_STATUS_ACTIVATED -> AndroidAutoTelemetryKeys.SESSION_ACTIVE
            else -> AndroidAutoTelemetryKeys.SESSION_STOPPED
        }
        fun reset() {}
    }
}

object AndroidAutoNavigationTelemetry {
    const val THROTTLE_MS = 300L
    data class Directions(val active: Boolean) {
        fun toJson() = "{\"active\":$active}"
    }
    fun inactive() = Directions(false)
    class Publisher {
        private var json = inactive().toJson()
        fun lastJson() = json
        fun onUpdate(update: Directions, nowMs: Long): String {
            json = update.toJson()
            return json
        }
        fun flushPending(nowMs: Long): String? = null
        fun reset() { json = inactive().toJson() }
    }
}

object DisplayAppLauncher {
    fun readAndroidAutoLinkStatusIfAlreadyBound(source: String): Int? =
        if (AndroidAutoClusterController.isSessionActive()) AndroidAutoSessionTelemetry.LINK_STATUS_ACTIVATED else null
    fun hasRecentAndroidAutoDcmProjectionActiveEvidenceForSession() = false
}

class ClusterSurfaceOutput(val isAvailable: Boolean = true)

class AndroidAutoClusterClient(context: Any, callback: (Int, String) -> Unit) {
    companion object { val outputs = mutableListOf<ClusterSurfaceOutput?>() }
    fun setOutput(output: ClusterSurfaceOutput?) { outputs += output }
}

class ServiceManager private constructor() {
    companion object {
        private val instance = ServiceManager()
        fun getInstance() = instance
    }
    val telemetry = mutableListOf<Pair<String, String>>()
    fun dispatchTelemetryOnly(key: String, value: String) { telemetry += key to value }
    fun dispatchServiceManagerEvent(type: ServiceManagerEventType, enabled: Boolean) {}
}
