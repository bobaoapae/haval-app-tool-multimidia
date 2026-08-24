package br.com.redesurftank.havalshisuku.projectors

import br.com.redesurftank.havalshisuku.models.CarConstants

internal object ClusterWarningPolicy {
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
     * Keys that are tracked but never raise the warning badge.
     *
     * The test is what the car itself puts on screen, and the lamp keys are not that test.
     * car.basic.tirepress_warning={0,0,1,0} drives a visible tyre card while
     * car.ipk_light.tpms_warning — the amber telltale — stays off: the card and the lamp are
     * separate things with separate thresholds. Judging visibility by the lamp wrongly
     * exempted the key that was actually on screen.
     *
     * A persisting flag is not evidence of an invisible warning either. These stay set after
     * the driver clears the card, because the underlying condition is still true; that is what
     * [cardIdFor]-keyed acknowledgement is for.
     *
     * Exempting is not free: an exempt key can never flip warningActive, so it stops appearing
     * in the `holding` diagnostics. That is why warning_state_changed also logs `activeRaw`,
     * which includes exempt keys — otherwise the one you most need to inspect is the one you
     * cannot see.
     */
    val badgeExemptWarningKeys =
            // Driving-assist alerts: momentary by nature, and the blind-spot pair already has
            // dedicated arrows in the theme. A badge that blinks on every close pass or
            // cross-traffic event is noise, not a warning.
            transientWarningKeys +
                    setOf(
                            // A persistent service-due nag rather than a fault: it would hold
                            // the badge up indefinitely between services. Still monitored, so
                            // themes that want to list it can.
                            CarConstants.CAR_BASIC_MAINTENANCE_WARNING.value,

                            // No observation of this one driving a card yet, so it stays out
                            // until seen on screen: an exempt key can only ever under-report,
                            // which is the safer failure. It is already in the tyre card
                            // grouping below for when that observation arrives.
                            CarConstants.CAR_BASIC_TIRETEMP_WARNING.value
                    )

    /**
     * CAN keys grouped by the warning card the driver actually sees.
     *
     * The car reports one condition over several keys — a belt fault sets both
     * car.basic.seat_belt_warning and the ipk_light indicator — but shows a single card, and
     * one BACK closes a single card. Dismissal is therefore per card, so one press clears one
     * card rather than needing a press per CAN key.
     *
     * A key not listed here is its own card.
     */
    private val warningCardGroups: List<Set<String>> =
            listOf(
                    setOf(
                            CarConstants.CAR_BASIC_SEAT_BELT_WARNING.value,
                            CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR.value
                    ),
                    setOf(
                            CarConstants.CAR_BASIC_TPMS_WARNING.value,
                            CarConstants.CAR_IPK_LIGHT_TPMS_WARNING.value,
                            CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                            CarConstants.CAR_BASIC_TIRETEMP_WARNING.value
                    ),
                    setOf(
                            CarConstants.CAR_BASIC_ENGINE_OIL_LOW_PRESSURE_WARNING.value,
                            CarConstants.CAR_BASIC_OIL_LOW_WARNING.value,
                            CarConstants.CAR_IPK_LIGHT_ENGINE_OIL_LOW_PRESSURE_WARNING.value
                    )
            )

    /** Every key behind the same card as [key]; the key alone when it has no group. */
    fun cardKeysFor(key: String): Set<String> =
            warningCardGroups.firstOrNull { key in it } ?: setOf(key)

    /**
     * Stable identity for the card [key] belongs to, for recording what the driver has
     * acknowledged.
     *
     * Acknowledgement is held against the card, never against the key's value. The car
     * rewrites these values while a condition stands — a belt fault was observed alternating
     * between {1,0,0,0,0} and {1,1,1,1,1} as the cluster changed which seats it highlighted —
     * and treating each value as a distinct warning made every repaint look like a brand new
     * fault, undoing the driver's dismissal seconds after they made it.
     */
    fun cardIdFor(key: String): String = cardKeysFor(key).minOrNull() ?: key

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

    /** Whether this key/value pair is one the driver sees as a warning badge. */
    fun raisesWarningBadge(key: String, value: String?): Boolean {
        return key !in badgeExemptWarningKeys && isWarningValueActive(value)
    }
}
