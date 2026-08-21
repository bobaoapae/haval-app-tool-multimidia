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
     * The test is what the car itself puts on screen. Several CAN keys track a condition at
     * a finer grain than the cluster ever displays; treating one of those as a warning puts
     * the badge up with nothing behind it, and — since such a flag tends not to clear — keeps
     * it up. Prefer the ipk_light.* lamp keys, which mirror what the driver actually sees.
     *
     * Exempting is not free either: an exempt key can never flip warningActive, so it stops
     * appearing in the `holding` diagnostics. That is why warning_state_changed also logs
     * `activeRaw`, which includes exempt keys — otherwise the one you most need to inspect is
     * the one you cannot see.
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

                            // Per-wheel flag vectors, not warning lamps. Observed live as
                            // car.basic.tirepress_warning={0,0,1,0} — third wheel flagged —
                            // while car.ipk_light.tpms_warning, the lamp the driver actually
                            // sees, stayed off. The car tracks a per-wheel condition here that
                            // it deliberately does not escalate into a visible warning.
                            //
                            // {0,0,1,0} is correctly "active" by [isWarningValueActive], so the
                            // badge went up with nothing on screen behind it. And because the
                            // flag persists (measured stable for 17+ minutes, not a value that
                            // flickers), it never clears: every real warning that arrives voids
                            // all acknowledgements, and once that one is dismissed this alone
                            // keeps the badge lit. The visible tyre warning is the lamp pair
                            // above. v6 excluded both of these; keep them out.
                            CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                            CarConstants.CAR_BASIC_TIRETEMP_WARNING.value
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

    /** Whether this key/value pair is one the driver sees as a warning badge. */
    fun raisesWarningBadge(key: String, value: String?): Boolean {
        return key !in badgeExemptWarningKeys && isWarningValueActive(value)
    }
}
