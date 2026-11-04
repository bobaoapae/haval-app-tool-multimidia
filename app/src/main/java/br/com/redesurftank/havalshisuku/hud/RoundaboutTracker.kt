package br.com.redesurftank.havalshisuku.hud

import kotlin.math.min

/**
 * Progress round a roundabout from Android Auto's own distance. While approaching, the
 * distance falls toward the entry. On the ring AA sends the same roundabout with the metres
 * left to the exit — that reading jumps up, and it is the whole lap. Progress is
 * 1 - now / that lap, so the left icon closes for the entire exit countdown. A street rename
 * at the entry (the exit road replaces the approach) must not wipe the low reading that
 * proves the jump. Without a jump the controller keeps its own estimate.
 */
class RoundaboutTracker {
    private var key: String? = null
    private var minM = Float.MAX_VALUE

    /** AA's distance to the exit when the car entered the ring; null until then. */
    var startM: Float? = null
        private set

    val entered: Boolean get() = startM != null

    /**
     * Feed every distance AA reports. [step] identifies the roundabout (null for any other
     * maneuver), [meters] AA's distance (null = unknown or stale). True when the entry is
     * detected on this call.
     */
    fun onUpdate(step: String?, meters: Float?): Boolean {
        if (step != key) {
            val previousMin = if (key != null && startM == null) minM else Float.MAX_VALUE
            key = step
            startM = null
            minM = Float.MAX_VALUE
            // The street label often changes to the exit road on the same sample as the jump.
            if (isEntry(previousMin, meters)) {
                startM = meters
                minM = meters!!
                return true
            }
        }
        if (step == null || meters == null) return false
        if (startM == null && isEntry(minM, meters)) {
            startM = meters
            return true
        }
        minM = min(minM, meters)
        return false
    }

    private fun isEntry(low: Float, meters: Float?): Boolean =
        meters != null && low <= ENTRY_M && meters >= low + JUMP_M && meters <= MAX_EXIT_M

    /**
     * 0 at the entry .. 1 at the exit, from AA's distance [currentM] now. A reading well above
     * the lap is the next leg's (AA's last sample of this step after the exit): done, not 0.
     */
    fun progress(currentM: Float): Float {
        val start = startM ?: return 0f
        if (start <= 0f || currentM > start + JUMP_M) return 1f
        return (1f - currentM / start).coerceIn(0f, 1f)
    }

    companion object {
        /** The distance must have come this close before a jump counts. Not only the last 20 m: AA often jumps from further out. */
        const val ENTRY_M = 160f
        /** ...and jump back up by at least this (a far wobble of a few metres does not). */
        const val JUMP_M = 25f
        /** A jumped distance above this is the next approach, not the lap to this exit. */
        const val MAX_EXIT_M = 220f
    }
}
