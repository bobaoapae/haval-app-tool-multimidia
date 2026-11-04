package br.com.redesurftank.havalshisuku.radar

import org.json.JSONObject

/**
 * Who owns the turn-by-turn slot (HUD and theme card) when both a route and a camera are
 * ahead: whichever comes first. A maneuver before the camera keeps the slot (the route may well
 * turn off before the camera); the camera takes it once it is the next thing on the road.
 * Pure, so it is unit-tested.
 */
object RadarPriority {
    /** Android Auto's next maneuver, from app.navigation.directions. */
    data class Nav(val active: Boolean, val meters: Int?)

    val NONE = Nav(false, null)

    /** Reads the directions JSON; anything unreadable means no route. */
    fun parseNav(json: String?): Nav = runCatching {
        val o = JSONObject(json ?: return NONE)
        val active = o.optBoolean("active", false)
        val meters = if (o.has("distance_m") && !o.isNull("distance_m")) o.optInt("distance_m", -1).takeIf { it >= 0 } else null
        Nav(active, meters)
    }.getOrDefault(NONE)

    /** True when the maneuver comes before the camera: the TBT keeps the slot. */
    fun tbtFirst(nav: Nav, radarMeters: Int): Boolean =
        nav.active && nav.meters != null && nav.meters < radarMeters
}

/**
 * Holds back a radar sound while the camera isn't shown (a maneuver comes first) and plays it
 * once the camera takes the slot, so the owner never hears an alert for a camera the screens
 * don't show. Only the strongest held sound plays (near beats approach).
 */
class RadarSoundGate {
    private var pending: RadarAlertEngine.Event? = null

    /**
     * One engine update: [event] from it, whether a camera is targeted at all, and whether it is
     * shown. Returns the sound to play now, if any.
     */
    fun onUpdate(event: RadarAlertEngine.Event?, targeted: Boolean, shown: Boolean): RadarAlertEngine.Event? {
        if (!targeted) {
            pending = null
            return null
        }
        val strongest = stronger(pending, event)
        if (!shown) {
            pending = strongest
            return null
        }
        pending = null
        return strongest
    }

    private fun rank(e: RadarAlertEngine.Event?) = when (e) {
        null -> 0
        RadarAlertEngine.Event.APPROACH -> 1
        RadarAlertEngine.Event.NEAR, RadarAlertEngine.Event.OVERSPEED -> 2
    }

    private fun stronger(a: RadarAlertEngine.Event?, b: RadarAlertEngine.Event?) = if (rank(b) >= rank(a) && b != null) b else a
}
