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
     * Keys that never raise the warning badge — they have their own dedicated indicator in
     * the theme (the BSD arrows) or are a text notification rather than a warning.
     *
     * Deliberately much narrower than [visualOnlyWarningKeys], which answers a different
     * question: "does this key start the critical-warning flow (onset bookkeeping, cluster
     * takeover)?". A seat belt or door warning raises the badge but does not take the
     * cluster over, so it belongs in one set and not the other. This set is the backend
     * mirror of VISUAL_ONLY_KEYS in the themes' warningHandler.js — keep the two in step.
     */
    val badgeExemptWarningKeys =
            setOf(
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value,
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value,
                    CarConstants.CAR_IPK_INFO_WARNING_TTS_NOTIFY.value
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
