package br.com.redesurftank.havalshisuku.managers

/**
 * Synthetic telemetry keys owned by Impulse, not the vehicle CAN service.
 * [ServiceManager.dispatchAllData] must re-emit these from cache on snapshot.
 */
object AndroidAutoTelemetryKeys {
    const val SESSION = "app.androidauto.session"
    const val DIRECTIONS = "app.navigation.directions"

    const val SESSION_ACTIVE = "active"
    const val SESSION_STOPPED = "stopped"

    @JvmField
    val SYNTHETIC_KEYS: Array<String> = arrayOf(SESSION, DIRECTIONS)
}
