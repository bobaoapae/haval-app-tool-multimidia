package br.com.redesurftank.havalshisuku.projectors

import br.com.redesurftank.havalshisuku.models.CarConstants

internal object ClusterWarningPolicy {
    val visualOnlyWarningKeys =
            setOf(
                    CarConstants.CAR_BASIC_SEAT_BELT_WARNING.value,
                    CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR.value,
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value,
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value,
                    CarConstants.CAR_IPK_INFO_WARNING_TTS_NOTIFY.value,
                    CarConstants.CAR_IPK_LIGHT_DOOR_WARNING.value,
                    CarConstants.CAR_IPK_LIGHT_ENGINE_OIL_LOW_PRESSURE_WARNING.value,
                    CarConstants.CAR_IPK_LIGHT_FUEL_LOW.value,
                    CarConstants.CAR_IPK_LIGHT_TPMS_WARNING.value
            )

    /**
     * Momentary alerts. The car raises these for a second or two while something is
     * happening beside or in front of you and then moves on, so they are never replayed
     * from ServiceManager's cache: the cached value is whatever the last event happened to
     * leave behind, and re-asserting it would light an indicator for an event long past
     * with no further telemetry coming to turn it back off.
     */
    val transientWarningKeys =
            setOf(
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value,
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value,
                    CarConstants.CAR_IPK_INFO_WARNING_TTS_NOTIFY.value,
                    CarConstants.CAR_IPK_INFO_DOW_WARNING_REQLEFT.value,
                    CarConstants.CAR_IPK_INFO_DOW_WARNING_REQRIGHT.value,
                    CarConstants.CAR_IPK_INFO_FCTA_WARNING.value,
                    CarConstants.CAR_IPK_INFO_FCW_WARNING.value
            )

    /** The two keys that drive the theme's dedicated blind-spot arrows. */
    val bsdIndicatorKeys =
            setOf(
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value,
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value
            )

    /**
     * Keys that are tracked but never raise the warning badge. Matches the badge set v6
     * shipped, which is the reference for this behaviour.
     *
     * Answers a different question from [visualOnlyWarningKeys] ("does this start the
     * critical-warning flow — onset bookkeeping, cluster takeover?"). A door warning raises
     * the badge but does not take the cluster over, so it belongs in one set and not the
     * other.
     */
    val badgeExemptWarningKeys =
            // Driving-assist alerts: momentary by nature, and the blind-spot pair already has
            // dedicated arrows in the theme. A badge that blinks on every close pass or
            // cross-traffic event is noise, not a warning.
            transientWarningKeys +
                    setOf(
                            // Monitored so a warning is not cleared prematurely (see 321756f)
                            // and so the raw values still reach themes that want to list
                            // individual warnings — but kept off the badge, as in v6.
                            // CAR_BASIC_SEAT_BELT_WARNING already covers the belt; the tyre
                            // pressure/temperature pair and the TPMS keys cover the same
                            // physical condition; maintenance is a persistent service-due nag
                            // that would otherwise hold the badge on indefinitely.
                            CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR.value,
                            CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                            CarConstants.CAR_BASIC_TIRETEMP_WARNING.value,
                            CarConstants.CAR_BASIC_MAINTENANCE_WARNING.value
                    )

    fun isWarningValueActive(value: String?): Boolean {
        if (value == null) return false
        val normalized = value.trim().lowercase()
        return normalized != "0" &&
                normalized != "{0,0,0,0}" &&
                normalized != "{0,0,0,0,0}" &&
                normalized.isNotEmpty() &&
                normalized != "false" &&
                normalized != "null" &&
                normalized != "undefined" &&
                normalized != "unknown" &&
                normalized != "--" &&
                normalized != "nan"
    }

    fun shouldTriggerCriticalWarningFlow(key: String, value: String?): Boolean {
        return key !in visualOnlyWarningKeys && isWarningValueActive(value)
    }

    /** Whether this key/value pair is one the driver sees as a warning badge. */
    fun raisesWarningBadge(key: String, value: String?): Boolean {
        return key !in badgeExemptWarningKeys && isWarningValueActive(value)
    }
}
