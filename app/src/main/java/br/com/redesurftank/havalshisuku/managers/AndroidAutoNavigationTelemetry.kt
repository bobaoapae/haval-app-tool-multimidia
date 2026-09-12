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

    // AAP NextTurnEnum, read from the vendor decompile
    // (Protos$NavigationNextTurnEvent$NextTurnEnum). These are the values the
    // head unit actually sends on LinkCallback code 8 — not the invented 1..9
    // numbering [fromTurnEnum] accepts for the theme-lab mock.
    const val EVENT_UNKNOWN = 0
    const val EVENT_DEPART = 1
    const val EVENT_NAME_CHANGE = 2
    const val EVENT_SLIGHT_TURN = 3
    const val EVENT_TURN = 4
    const val EVENT_SHARP_TURN = 5
    const val EVENT_U_TURN = 6
    const val EVENT_ON_RAMP = 7
    const val EVENT_OFF_RAMP = 8
    const val EVENT_FORK = 9
    const val EVENT_MERGE = 10
    const val EVENT_ROUNDABOUT_ENTER = 11
    const val EVENT_ROUNDABOUT_EXIT = 12
    const val EVENT_ROUNDABOUT_ENTER_AND_EXIT = 13
    const val EVENT_STRAIGHT = 14
    const val EVENT_FERRY_BOAT = 16
    const val EVENT_FERRY_TRAIN = 17
    const val EVENT_DESTINATION = 19

    // Protos$NavigationNextTurnEvent$TurnSide
    const val SIDE_LEFT = 1
    const val SIDE_RIGHT = 2
    const val SIDE_UNSPECIFIED = 3

    // Protos$NavigationNextTurnDistanceEvent$DistanceUnits
    const val UNIT_UNKNOWN = 0
    const val UNIT_METERS = 1
    const val UNIT_KILOMETERS = 2
    const val UNIT_KILOMETERS_P1 = 3
    const val UNIT_MILES = 4
    const val UNIT_MILES_P1 = 5
    const val UNIT_FEET = 6
    const val UNIT_YARDS = 7

    /**
     * `IfNavigationStepData` carries no turn side, only a signed angle, so the
     * side is inferred: negative turns left, positive turns right. Only events
     * that actually take a side consult it.
     */
    fun sideFromAngle(event: Int, turnAngle: Int): Int {
        return when {
            turnAngle < 0 -> SIDE_LEFT
            turnAngle > 0 -> SIDE_RIGHT
            else -> SIDE_UNSPECIFIED
        }
    }

    /** Turn token for the theme, from the real AAP event + side pair. */
    fun turnFromEvent(event: Int, turnSide: Int): String {
        val side = when (turnSide) {
            SIDE_LEFT -> "LEFT"
            SIDE_RIGHT -> "RIGHT"
            else -> ""
        }
        fun sided(base: String): String = if (side.isEmpty()) base else "${base}_$side"
        return when (event) {
            EVENT_DEPART -> "DEPART"
            EVENT_NAME_CHANGE -> "NAME_CHANGE"
            EVENT_SLIGHT_TURN -> sided("SLIGHT")
            EVENT_TURN -> sided("TURN")
            EVENT_SHARP_TURN -> sided("SHARP")
            EVENT_U_TURN -> "U_TURN"
            EVENT_ON_RAMP -> sided("ON_RAMP")
            EVENT_OFF_RAMP -> sided("OFF_RAMP")
            EVENT_FORK -> sided("FORK")
            EVENT_MERGE -> sided("MERGE")
            EVENT_ROUNDABOUT_ENTER -> "ROUNDABOUT_ENTER"
            EVENT_ROUNDABOUT_EXIT -> "ROUNDABOUT_EXIT"
            EVENT_ROUNDABOUT_ENTER_AND_EXIT -> "ROUNDABOUT"
            EVENT_STRAIGHT -> "STRAIGHT"
            EVENT_FERRY_BOAT -> "FERRY_BOAT"
            EVENT_FERRY_TRAIN -> "FERRY_TRAIN"
            EVENT_DESTINATION -> "DESTINATION"
            else -> ""
        }
    }

    /** Unit suffix for a [DistanceUnits] value; empty when unknown. */
    fun unitSuffix(displayUnits: Int): String = when (displayUnits) {
        UNIT_METERS -> "m"
        UNIT_KILOMETERS, UNIT_KILOMETERS_P1 -> "km"
        UNIT_MILES, UNIT_MILES_P1 -> "mi"
        UNIT_FEET -> "ft"
        UNIT_YARDS -> "yd"
        else -> ""
    }

    /**
     * Display distance string. [displayDistanceE3] is the display value scaled
     * by 1000, so 1200 with [UNIT_KILOMETERS_P1] reads "1.2 km".
     */
    fun formatDistance(displayDistanceE3: Int, displayDistanceUnit: Int): String {
        if (displayDistanceE3 <= 0) return ""
        val suffix = when (displayDistanceUnit) {
            UNIT_METERS -> "m"
            UNIT_KILOMETERS, UNIT_KILOMETERS_P1 -> "km"
            UNIT_MILES, UNIT_MILES_P1 -> "mi"
            UNIT_FEET -> "ft"
            UNIT_YARDS -> "yd"
            else -> return ""
        }
        val oneDecimal =
            displayDistanceUnit == UNIT_KILOMETERS_P1 || displayDistanceUnit == UNIT_MILES_P1
        return if (oneDecimal) {
            val tenths = (displayDistanceE3 + 50) / 100
            "${tenths / 10}.${tenths % 10} $suffix"
        } else {
            "${(displayDistanceE3 + 500) / 1000} $suffix"
        }
    }

    /**
     * Merges the two halves of a manoeuvre. The head unit sends the turn and
     * street on LinkCallback code 8 and the distance to it on code 9, as
     * separate transactions, so neither alone is a publishable state.
     */
    class Accumulator {
        private var street: String = ""
        private var turn: String = ""
        private var turnId: Int? = null
        private var distanceText: String = ""
        private var distanceM: Int? = null
        private var active: Boolean = false

        fun onNextTurn(road: String?, event: Int, turnSide: Int): Directions {
            active = true
            street = road?.trim().orEmpty()
            turn = turnFromEvent(event, turnSide)
            turnId = event
            return current()
        }

        fun onNextTurnDistance(
            distanceMeters: Int,
            displayDistanceE3: Int,
            displayDistanceUnit: Int
        ): Directions {
            // Distance can arrive before the first turn of a route; treat it as
            // guidance running so the strip is not held back a whole manoeuvre.
            active = true
            distanceM = if (distanceMeters >= 0) distanceMeters else null
            distanceText = formatDistance(displayDistanceE3, displayDistanceUnit)
            return current()
        }

        /**
         * LinkCallback code 7. The enum is not in the decompile; 0 is taken as
         * "no guidance" and anything else as running. The raw value is logged
         * at the call site so a car run can correct this.
         */
        fun onNavigationState(state: Int): Directions {
            if (state == 0) {
                reset()
            } else {
                active = true
            }
            return current()
        }

        /**
         * LinkCallback code 10, `onNavigationState`. This is what the car
         * actually sends — codes 7/8/9 never fire on this head unit. A route
         * with no steps means guidance is off.
         */
        fun onRouteStep(road: String?, event: Int, turnAngle: Int, hasRoute: Boolean): Directions {
            if (!hasRoute) {
                reset()
                return current()
            }
            active = true
            street = road?.trim().orEmpty()
            turn = turnFromEvent(event, sideFromAngle(event, turnAngle))
            turnId = event
            return current()
        }

        /**
         * LinkCallback code 11, `onNavigationCurrentPosition`. Carries the live
         * distance to the next manoeuvre, already formatted by the OEM.
         */
        fun onPosition(meters: Int, displayValue: String?, displayUnits: Int): Directions {
            if (!active) return current()
            distanceM = if (meters >= 0) meters else null
            // The car sends a bare number ("150") with the unit in its own
            // field, so the suffix is appended here rather than shown raw.
            val value = displayValue?.trim().orEmpty()
            distanceText = if (value.isEmpty()) "" else {
                val suffix = unitSuffix(displayUnits)
                if (suffix.isEmpty()) value else "$value $suffix"
            }
            return current()
        }

        fun reset() {
            street = ""
            turn = ""
            turnId = null
            distanceText = ""
            distanceM = null
            active = false
        }

        fun current(): Directions {
            if (!active) return inactive()
            return Directions(
                active = true,
                street = street,
                distance = distanceText,
                distanceM = distanceM,
                turn = turn,
                turnId = turnId
            )
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
