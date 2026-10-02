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
        val remainingS: Int? = null,
        val eta: String = ""
    ) {
        fun identityChanged(other: Directions): Boolean {
            return active != other.active ||
                street != other.street ||
                turn != other.turn ||
                turnId != other.turnId ||
                nextStreet != other.nextStreet ||
                nextTurn != other.nextTurn
        }

        /** `v` is the payload schema; bump only for a breaking change (see themes-contract-v1.md). */
        fun toJson(): String {
            if (!active) {
                return """{"v":$SCHEMA_VERSION,"active":false}"""
            }
            return buildString {
                append('{')
                append("\"v\":").append(SCHEMA_VERSION)
                append(",\"active\":true")
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
                append(",\"eta\":").append(jsonString(eta))
                append('}')
            }
        }
    }

    const val SCHEMA_VERSION = 1

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

    // Vendor maneuver enum, from com/ts/androidauto/sdk/data/NavigationData.smali.
    // This is what IfNavigationStepData.mEvent actually carries — NOT the Google
    // proto NextTurnEnum, which numbers things differently (7 is ON_RAMP there
    // but a normal left turn here). Side is baked into the value, so there is
    // no side field to consult and no angle to infer from.
    const val MANEUVER_UNKNOWN = 0x0
    const val MANEUVER_DEPART = 0x1
    const val MANEUVER_NAME_CHANGE = 0x2
    const val MANEUVER_KEEP_LEFT = 0x3
    const val MANEUVER_KEEP_RIGHT = 0x4
    const val MANEUVER_TURN_SLIGHT_LEFT = 0x5
    const val MANEUVER_TURN_SLIGHT_RIGHT = 0x6
    const val MANEUVER_TURN_NORMAL_LEFT = 0x7
    const val MANEUVER_TURN_NORMAL_RIGHT = 0x8
    const val MANEUVER_TURN_SHARP_LEFT = 0x9
    const val MANEUVER_TURN_SHARP_RIGHT = 0xa
    const val MANEUVER_U_TURN_LEFT = 0xb
    const val MANEUVER_U_TURN_RIGHT = 0xc
    const val MANEUVER_ON_RAMP_SLIGHT_LEFT = 0xd
    const val MANEUVER_ON_RAMP_SLIGHT_RIGHT = 0xe
    const val MANEUVER_ON_RAMP_NORMAL_LEFT = 0xf
    const val MANEUVER_ON_RAMP_NORMAL_RIGHT = 0x10
    const val MANEUVER_ON_RAMP_SHARP_LEFT = 0x11
    const val MANEUVER_ON_RAMP_SHARP_RIGHT = 0x12
    const val MANEUVER_ON_RAMP_U_TURN_LEFT = 0x13
    const val MANEUVER_ON_RAMP_U_TURN_RIGHT = 0x14
    const val MANEUVER_OFF_RAMP_SLIGHT_LEFT = 0x15
    const val MANEUVER_OFF_RAMP_SLIGHT_RIGHT = 0x16
    const val MANEUVER_OFF_RAMP_NORMAL_LEFT = 0x17
    const val MANEUVER_OFF_RAMP_NORMAL_RIGHT = 0x18
    const val MANEUVER_FORK_LEFT = 0x19
    const val MANEUVER_FORK_RIGHT = 0x1a
    const val MANEUVER_MERGE_LEFT = 0x1b
    const val MANEUVER_MERGE_RIGHT = 0x1c
    const val MANEUVER_MERGE_SIDE_UNSPECIFIED = 0x1d
    const val MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CW = 0x20
    const val MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE = 0x21
    const val MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CCW = 0x22
    const val MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE = 0x23
    const val MANEUVER_STRAIGHT = 0x24
    const val MANEUVER_FERRY_BOAT = 0x25
    const val MANEUVER_FERRY_TRAIN = 0x26
    const val MANEUVER_DESTINATION = 0x27
    const val MANEUVER_DESTINATION_STRAIGHT = 0x28
    const val MANEUVER_DESTINATION_LEFT = 0x29
    const val MANEUVER_DESTINATION_RIGHT = 0x2a
    const val MANEUVER_ROUNDABOUT_ENTER_CW = 0x2b
    const val MANEUVER_ROUNDABOUT_EXIT_CW = 0x2c
    const val MANEUVER_ROUNDABOUT_ENTER_CCW = 0x2d
    const val MANEUVER_ROUNDABOUT_EXIT_CCW = 0x2e
    const val MANEUVER_FERRY_BOAT_LEFT = 0x2f
    const val MANEUVER_FERRY_BOAT_RIGHT = 0x30
    const val MANEUVER_FERRY_TRAIN_LEFT = 0x31
    const val MANEUVER_FERRY_TRAIN_RIGHT = 0x32

    // Protos$NavigationStatus$NavigationStatusEnum — what LinkCallback code 7
    // carries (NavigationStatus -> LinkController$5.onStatus ->
    // notifyNavigationState). A 0 is also sent when the device drops.
    const val NAV_STATUS_ACTIVE = 1
    const val NAV_STATUS_INACTIVE = 2
    const val NAV_STATUS_REROUTING = 3

    // Protos$NavigationNextTurnDistanceEvent$DistanceUnits
    const val UNIT_UNKNOWN = 0
    const val UNIT_METERS = 1
    const val UNIT_KILOMETERS = 2
    const val UNIT_KILOMETERS_P1 = 3
    const val UNIT_MILES = 4
    const val UNIT_MILES_P1 = 5
    const val UNIT_FEET = 6
    const val UNIT_YARDS = 7

    /** Theme turn token for a vendor maneuver value. */
    fun turnFromManeuver(maneuver: Int): String = when (maneuver) {
        MANEUVER_DEPART -> "DEPART"
        MANEUVER_NAME_CHANGE -> "NAME_CHANGE"
        MANEUVER_KEEP_LEFT -> "KEEP_LEFT"
        MANEUVER_KEEP_RIGHT -> "KEEP_RIGHT"
        MANEUVER_TURN_SLIGHT_LEFT -> "SLIGHT_LEFT"
        MANEUVER_TURN_SLIGHT_RIGHT -> "SLIGHT_RIGHT"
        MANEUVER_TURN_NORMAL_LEFT -> "TURN_LEFT"
        MANEUVER_TURN_NORMAL_RIGHT -> "TURN_RIGHT"
        MANEUVER_TURN_SHARP_LEFT -> "SHARP_LEFT"
        MANEUVER_TURN_SHARP_RIGHT -> "SHARP_RIGHT"
        MANEUVER_U_TURN_LEFT, MANEUVER_ON_RAMP_U_TURN_LEFT -> "U_TURN_LEFT"
        MANEUVER_U_TURN_RIGHT, MANEUVER_ON_RAMP_U_TURN_RIGHT -> "U_TURN_RIGHT"
        MANEUVER_ON_RAMP_SLIGHT_LEFT,
        MANEUVER_ON_RAMP_NORMAL_LEFT,
        MANEUVER_ON_RAMP_SHARP_LEFT -> "ON_RAMP_LEFT"
        MANEUVER_ON_RAMP_SLIGHT_RIGHT,
        MANEUVER_ON_RAMP_NORMAL_RIGHT,
        MANEUVER_ON_RAMP_SHARP_RIGHT -> "ON_RAMP_RIGHT"
        MANEUVER_OFF_RAMP_SLIGHT_LEFT, MANEUVER_OFF_RAMP_NORMAL_LEFT -> "OFF_RAMP_LEFT"
        MANEUVER_OFF_RAMP_SLIGHT_RIGHT, MANEUVER_OFF_RAMP_NORMAL_RIGHT -> "OFF_RAMP_RIGHT"
        MANEUVER_FORK_LEFT -> "FORK_LEFT"
        MANEUVER_FORK_RIGHT -> "FORK_RIGHT"
        MANEUVER_MERGE_LEFT -> "MERGE_LEFT"
        MANEUVER_MERGE_RIGHT -> "MERGE_RIGHT"
        MANEUVER_MERGE_SIDE_UNSPECIFIED -> "MERGE"
        MANEUVER_ROUNDABOUT_ENTER_CW, MANEUVER_ROUNDABOUT_ENTER_CCW -> "ROUNDABOUT_ENTER"
        MANEUVER_ROUNDABOUT_EXIT_CW, MANEUVER_ROUNDABOUT_EXIT_CCW -> "ROUNDABOUT_EXIT"
        MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CW,
        MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE,
        MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CCW,
        MANEUVER_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE -> "ROUNDABOUT"
        MANEUVER_STRAIGHT -> "STRAIGHT"
        MANEUVER_FERRY_BOAT, MANEUVER_FERRY_BOAT_LEFT, MANEUVER_FERRY_BOAT_RIGHT -> "FERRY_BOAT"
        MANEUVER_FERRY_TRAIN, MANEUVER_FERRY_TRAIN_LEFT, MANEUVER_FERRY_TRAIN_RIGHT -> "FERRY_TRAIN"
        MANEUVER_DESTINATION, MANEUVER_DESTINATION_STRAIGHT -> "DESTINATION"
        MANEUVER_DESTINATION_LEFT -> "DESTINATION_LEFT"
        MANEUVER_DESTINATION_RIGHT -> "DESTINATION_RIGHT"
        else -> ""
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
     * Waze's DestDistanceData often carries `seconds=0` with a real arrival
     * clock in `eta` ("20:38"). Maps fills seconds properly. When seconds are
     * missing or zero while distance remains, derive remaining from the clock.
     * Assumes today's HH:mm; if that is more than a few minutes in the past,
     * rolls to tomorrow (overnight trips).
     */
    fun remainingSecondsFromEtaClock(eta: String, nowMs: Long = System.currentTimeMillis()): Int? {
        val match = ETA_CLOCK_PATTERN.matchEntire(eta.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val target = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var deltaMs = target.timeInMillis - nowMs
        // More than five minutes in the past → treat as tomorrow.
        if (deltaMs < -5L * 60L * 1000L) {
            target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            deltaMs = target.timeInMillis - nowMs
        }
        if (deltaMs < 0L) deltaMs = 0L
        return (deltaMs / 1000L).toInt()
    }

    private val ETA_CLOCK_PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")

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
        private var remainingM: Int? = null
        private var remainingS: Int? = null
        private var eta: String = ""
        private var active: Boolean = false

        @Synchronized
        fun onNextTurn(road: String?, event: Int, turnSide: Int): Directions {
            active = true
            street = road?.trim().orEmpty()
            turn = turnFromManeuver(event)
            turnId = event
            return current()
        }

        @Synchronized
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
         * LinkCallback code 7, the AAP NavigationStatus: ACTIVE=1, INACTIVE=2,
         * REROUTING=3, and 0 when the device drops. Only the end states clear;
         * ACTIVE and REROUTING wait for the route itself (code 10) rather than
         * raising an empty card.
         *
         * This used to clear on 0 alone, so INACTIVE — the value guidance ends
         * with — left the last manoeuvre frozen on screen.
         */
        @Synchronized
        fun onNavigationState(state: Int): Directions {
            if (state == NAV_STATUS_INACTIVE || state == 0) {
                reset()
            }
            return current()
        }

        /**
         * LinkCallback code 10, `onNavigationState`. This is what the car
         * actually sends — codes 7/8/9 never fire on this head unit. A route
         * with no steps means guidance is off.
         */
        @Synchronized
        fun onRouteStep(road: String?, event: Int, hasRoute: Boolean): Directions {
            if (!hasRoute) {
                reset()
                return current()
            }
            active = true
            street = road?.trim().orEmpty()
            turn = turnFromManeuver(event)
            turnId = event
            return current()
        }

        /**
         * LinkCallback code 11, `onNavigationCurrentPosition`. Carries the live
         * distance to the next manoeuvre, already formatted by the OEM.
         */
        /**
         * LinkCallback code 11. Carries distance to the next manoeuvre plus,
         * in its destination list, the trip totals the AA screen shows as
         * "4,8 km · 10:35".
         */
        @Synchronized
        fun onPosition(
            meters: Int,
            displayValue: String?,
            displayUnits: Int,
            remainingMeters: Int? = null,
            remainingSeconds: Int? = null,
            estimatedTime: String? = null,
            nowMs: Long = System.currentTimeMillis()
        ): Directions {
            if (!active) return current()
            distanceM = if (meters >= 0) meters else null
            // The car sends a bare number ("150") with the unit in its own
            // field, so the suffix is appended here rather than shown raw.
            val value = displayValue?.trim().orEmpty()
            distanceText = if (value.isEmpty()) "" else {
                val suffix = unitSuffix(displayUnits)
                if (suffix.isEmpty()) value else "$value $suffix"
            }
            // Trip totals live on DestDistanceData. Waze often sends manoeuvre
            // position ticks without that list; only overwrite when present so
            // remaining / ETA do not blink out between updates.
            if (remainingMeters != null) {
                remainingM = remainingMeters.takeIf { it >= 0 }
            }
            if (estimatedTime != null) {
                eta = estimatedTime.trim()
            }
            if (remainingSeconds != null) {
                // Measured on car with Waze (2026-09-24): DestDistanceData is
                // present with remainingS=0 while eta="20:38" and remainingM>0.
                // Trust positive seconds; treat zero-with-distance as missing
                // and derive from the arrival clock instead of publishing 0.
                val metersLeft = remainingMeters ?: remainingM
                val secondsLookUsable =
                    remainingSeconds > 0 || (remainingSeconds == 0 && (metersLeft == null || metersLeft <= 0))
                if (secondsLookUsable) {
                    remainingS = remainingSeconds
                } else {
                    remainingSecondsFromEtaClock(eta, nowMs)?.let { remainingS = it }
                }
            } else if (remainingS == null && eta.isNotEmpty()) {
                remainingSecondsFromEtaClock(eta, nowMs)?.let { remainingS = it }
            }
            return current()
        }

        @Synchronized
        fun reset() {
            street = ""
            turn = ""
            turnId = null
            distanceText = ""
            distanceM = null
            remainingM = null
            remainingS = null
            eta = ""
            active = false
        }

        @Synchronized
        fun current(): Directions {
            if (!active) return inactive()
            return Directions(
                active = true,
                street = street,
                distance = distanceText,
                distanceM = distanceM,
                turn = turn,
                turnId = turnId,
                remainingM = remainingM,
                remainingS = remainingS,
                eta = eta
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
