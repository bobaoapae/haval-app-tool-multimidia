package br.com.redesurftank.havalshisuku.managers

/**
 * Turn-by-turn JSON for [AndroidAutoTelemetryKeys.DIRECTIONS].
 * Immediate publish on turn / street / active. Distance and remaining
 * fields are throttled to ~1 Hz with a trailing commit.
 */
object AndroidAutoNavigationTelemetry {
    const val THROTTLE_MS = 1_000L

    data class Directions(
        val active: Boolean,
        val street: String = "",
        val distance: String = "",
        val distanceM: Int? = null,
        val turn: String = "",
        val turnId: Int? = null,
        val nextStreet: String = "",
        val nextDistanceM: Int? = null,
        val nextTurn: String? = null,
        val remainingM: Int? = null,
        val remainingS: Int? = null
    ) {
        fun identityChanged(other: Directions): Boolean {
            return active != other.active ||
                street != other.street ||
                turn != other.turn ||
                turnId != other.turnId ||
                nextStreet != other.nextStreet ||
                nextTurn != other.nextTurn
        }

        fun toJson(): String {
            if (!active) {
                return """{"active":false}"""
            }
            return buildString {
                append('{')
                append("\"active\":true")
                append(",\"street\":").append(jsonString(street))
                append(",\"distance\":").append(jsonString(distance))
                append(",\"distance_m\":").append(jsonNumber(distanceM))
                append(",\"turn\":").append(jsonString(turn))
                append(",\"turn_id\":").append(jsonNumber(turnId))
                append(",\"next_street\":").append(jsonString(nextStreet))
                append(",\"next_distance_m\":").append(jsonNumber(nextDistanceM))
                append(",\"next_turn\":").append(nextTurn?.let { jsonString(it) } ?: "null")
                append(",\"remaining_m\":").append(jsonNumber(remainingM))
                append(",\"remaining_s\":").append(jsonNumber(remainingS))
                append('}')
            }
        }
    }

    fun inactive(): Directions = Directions(active = false)

    fun fromTurnEnum(raw: String?, turnId: Int?): String {
        val token = raw?.trim().orEmpty()
        if (token.isNotEmpty()) {
            return token.uppercase().replace(' ', '_')
        }
        return when (turnId) {
            1 -> "TURN_LEFT"
            2 -> "TURN_RIGHT"
            3 -> "STRAIGHT"
            4 -> "U_TURN"
            5 -> "ROUNDABOUT"
            6 -> "FORK"
            7 -> "MERGE"
            8 -> "EXIT"
            9 -> "DESTINATION"
            else -> ""
        }
    }

    class Publisher {
        private var lastPublished: Directions? = null
        private var lastThrottleAtMs: Long = 0L
        private var pending: Directions? = null

        fun onUpdate(next: Directions, nowMs: Long): String? {
            val previous = lastPublished
            if (previous == null || next.identityChanged(previous)) {
                lastPublished = next
                lastThrottleAtMs = nowMs
                pending = null
                return next.toJson()
            }
            if (next == previous) {
                pending = null
                return null
            }
            pending = next
            if (nowMs - lastThrottleAtMs >= THROTTLE_MS) {
                lastPublished = next
                lastThrottleAtMs = nowMs
                pending = null
                return next.toJson()
            }
            return null
        }

        fun flushPending(nowMs: Long): String? {
            val held = pending ?: return null
            lastPublished = held
            lastThrottleAtMs = nowMs
            pending = null
            return held.toJson()
        }

        fun lastJson(): String {
            return lastPublished?.toJson() ?: inactive().toJson()
        }

        fun reset() {
            lastPublished = null
            lastThrottleAtMs = 0L
            pending = null
        }
    }

    private fun jsonNumber(value: Int?): String = value?.toString() ?: "null"

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$escaped\""
    }
}
