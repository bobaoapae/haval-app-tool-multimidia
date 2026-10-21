package br.com.redesurftank.havalshisuku.navigation

/**
 * Lossless value model of the 11 fields of the TS RouteGuidanceInfo Parcelable, in wire order.
 *
 * Field names and types come from the supplied TsCarPlayService.apk (SHA-256
 * 03cc69d166ced0cf0b38e47ef08d0a6cd7b27ab57d6954bd87a23da436c911c7). This is not a Parcel reader
 * or evidence of a live Binder connection. Do not validate, clamp or replace unknown raw values:
 * the original integers, long and nullable strings are needed to interpret future captures.
 */
data class RawCarPlayGuidance(
    val mManeuverType: Int,
    val mDistanceBetweenManeuverDisplayStr: String?,
    val mDistanceBetweenManeuverDisplayUnits: Int,
    val mAfterManeuverRoadName: String?,
    val mDistanceRemainingDisplayStr: String?,
    val mDistanceRemainingDisplayUnits: Int,
    val mTimeRemainingToDestination: Long,
    val mRouteGuidanceState: Int,
    val mJunctionType: Int,
    val mDrivingSide: Int,
    val mJunctionElementExitAngle: Int
) {
    val routeGuidanceState: CarPlayRouteGuidanceState
        get() = CarPlayRouteGuidanceState.fromRaw(mRouteGuidanceState)

    val distanceBetweenManeuverUnits: CarPlayDistanceUnit
        get() = CarPlayDistanceUnit.fromRaw(mDistanceBetweenManeuverDisplayUnits)

    val distanceRemainingUnits: CarPlayDistanceUnit
        get() = CarPlayDistanceUnit.fromRaw(mDistanceRemainingDisplayUnits)

    companion object {
        const val JUNCTION_EXIT_ANGLE_DEFAULT = 181
    }
}

/** Known state codes only; UNKNOWN never replaces the corresponding integer in the raw model. */
enum class CarPlayRouteGuidanceState(val wireValue: Int?) {
    NO_ROUTE(0),
    ROUTE_SET(1),
    ARRIVED(2),
    LOADING(3),
    LOCATING(4),
    REROUTING(5),
    // The OEM constant is spelled PRECEED_TO_ROUTE; this name corrects the spelling only.
    PROCEED_TO_ROUTE(6),
    UNKNOWN(null);

    companion object {
        fun fromRaw(value: Int): CarPlayRouteGuidanceState = when (value) {
            0 -> NO_ROUTE
            1 -> ROUTE_SET
            2 -> ARRIVED
            3 -> LOADING
            4 -> LOCATING
            5 -> REROUTING
            6 -> PROCEED_TO_ROUTE
            else -> UNKNOWN
        }
    }
}

/** Units describe display text, not a numeric distance. Keep an unknown code in the raw model. */
enum class CarPlayDistanceUnit(val wireValue: Int?) {
    KILOMETERS(0),
    MILES(1),
    METERS(2),
    YARDS(3),
    FEET(4),
    UNKNOWN(null);

    companion object {
        fun fromRaw(value: Int): CarPlayDistanceUnit = when (value) {
            0 -> KILOMETERS
            1 -> MILES
            2 -> METERS
            3 -> YARDS
            4 -> FEET
            else -> UNKNOWN
        }
    }
}

/**
 * Prepared, read-only directions for a separate source arbiter, not a published telemetry payload.
 *
 * [active] means eligible guidance, not proof of a connected session. The arbiter owns source,
 * connection and publication. Fields not established by a source must stay null/empty/UNKNOWN.
 * [turnId] is source-specific raw evidence and must not be interpreted as another source's enum.
 */
data class PreparedDirections(
    val active: Boolean,
    val navigationState: String,
    val turnId: Int? = null,
    val turn: String = "UNKNOWN",
    val street: String = "",
    val nextStreet: String = "",
    val distanceText: String = "",
    val distanceMeters: Int? = null,
    val remainingText: String = "",
    val remainingMeters: Int? = null,
    val remainingSeconds: Long? = null,
    val eta: String = "",
    val lanes: List<String> = emptyList(),
    val exitNumber: Int? = null,
    val turnAngle: Int? = null,
    val roundaboutClockwise: Boolean? = null
)

/**
 * Pure preparation policy; no Android APIs, side effects or runtime registration.
 *
 * Loading, locating and unknown states fail closed until real captures justify another policy.
 * Proceed-to-route remains UNKNOWN, not DEPART: route state 6 does not decode maneuver type 6.
 * Distances keep the exact display strings without assuming locale or reconstructing meters.
 * The time unit and angle/driving-side semantics are unverified, so even plausible values do not
 * become seconds, turn angles or roundabout direction. Keep the raw model alongside this result.
 */
object CarPlayGuidanceNormalizer {
    fun normalize(raw: RawCarPlayGuidance?): PreparedDirections {
        if (raw == null) {
            return PreparedDirections(active = false, navigationState = "UNKNOWN")
        }

        val state = raw.routeGuidanceState
        val eligible = when (state) {
            CarPlayRouteGuidanceState.ROUTE_SET,
            CarPlayRouteGuidanceState.REROUTING,
            CarPlayRouteGuidanceState.PROCEED_TO_ROUTE -> true
            CarPlayRouteGuidanceState.NO_ROUTE,
            CarPlayRouteGuidanceState.ARRIVED,
            CarPlayRouteGuidanceState.LOADING,
            CarPlayRouteGuidanceState.LOCATING,
            CarPlayRouteGuidanceState.UNKNOWN -> false
        }

        return PreparedDirections(
            active = eligible,
            navigationState = state.name,
            turnId = raw.mManeuverType,
            nextStreet = raw.mAfterManeuverRoadName.orEmpty(),
            distanceText = raw.mDistanceBetweenManeuverDisplayStr.orEmpty(),
            remainingText = raw.mDistanceRemainingDisplayStr.orEmpty()
        )
    }
}
