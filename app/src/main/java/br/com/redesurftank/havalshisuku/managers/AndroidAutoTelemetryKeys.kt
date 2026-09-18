package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.PowerFlow

/**
 * Synthetic telemetry keys owned by Impulse, not the vehicle CAN service.
 * [ServiceManager.dispatchAllData] must re-emit these from cache on snapshot.
 */
object AndroidAutoTelemetryKeys {
    const val SESSION = "app.androidauto.session"
    const val DIRECTIONS = "app.navigation.directions"

    const val SESSION_ACTIVE = "active"
    const val SESSION_STOPPED = "stopped"

    /**
     * Version of Impulse's external API (EVENT_CHANGED keys, command actions and
     * extras). Always published on snapshot. Bump only for a breaking change;
     * additive keys, extras and JSON fields stay on the same version.
     */
    const val API_VERSION = "app.impulse.api_version"
    const val API_VERSION_VALUE = "1"

    @JvmField
    val SYNTHETIC_KEYS: Array<String> = arrayOf(SESSION, DIRECTIONS, PowerFlow.KEY_FLOW, PowerFlow.KEY_ICE)
}
