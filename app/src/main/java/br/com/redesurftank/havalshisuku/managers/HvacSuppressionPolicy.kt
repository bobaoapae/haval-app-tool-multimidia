package br.com.redesurftank.havalshisuku.managers

/**
 * When the OEM HVAC app should be disabled, and when it must be put back. Free of Android types so
 * it can be unit tested — the failure this guards against is a car left with no A/C screen, which
 * is not something to discover by trying it.
 *
 * Measured on the car 2026-09-22: with `com.beantechs.hvac` disabled the physical A/C controls
 * still actuate and `car.hvac.panel_display_notify` still fires; only the OEM popup stops, because
 * the system bar's `startActivity` for it fails harmlessly. So the worst case of a stuck
 * suppression is "no OEM A/C screen", not "no A/C" — but it survives a reboot (`pm disable-user` is
 * persisted), which is why [decide] is driven by a PERSISTED marker rather than by memory alone.
 */
object HvacSuppressionPolicy {

    enum class Action {
        /** State already matches; touch nothing. */
        NONE,

        /** `pm disable-user` + `am force-stop`, then record the marker. */
        DISABLE,

        /** `pm enable`, then clear the marker. */
        ENABLE
    }

    /**
     * The single decision, used by every caller — taking a lease, dropping one, Impulse starting,
     * and BOOT_COMPLETED.
     *
     * @param leaseHeld someone (today: a bound viewer) wants the OEM popup suppressed right now.
     * @param markerSet the persisted "we disabled it and have not put it back" flag. It is written
     * BEFORE the disable and cleared AFTER the enable, so a process killed mid-way is recovered as
     * "still disabled", which is the safe direction: the reconcile then enables.
     */
    fun decide(leaseHeld: Boolean, markerSet: Boolean): Action = when {
        leaseHeld && !markerSet -> Action.DISABLE
        !leaseHeld && markerSet -> Action.ENABLE
        else -> Action.NONE
    }

    /**
     * Whether a lease is held, given the holders currently registered. Kept here rather than
     * inlined so "no holders means release" is covered by a test — this is the line that decides
     * whether a car gets its A/C screen back.
     */
    fun leaseHeld(holders: Set<String>, featureEnabled: Boolean): Boolean =
        featureEnabled && holders.isNotEmpty()
}
