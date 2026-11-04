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

    /**
     * Acknowledged cards to re-arm because [raisedCard] has just come up over them.
     *
     * The car queues standing cards: when a new card preempts one the driver already closed,
     * the old one returns once the new one goes away. Observed 2026-09-13 — belt card closed
     * with BACK at 19:39:52, door opened and closed, and the belt card was back on the cluster
     * (the driver pressed BACK on it at 19:43:13) while the badge stayed down, because the
     * acknowledgement was still held against a condition that never cleared.
     *
     * Only a card that newly rises preempts. A standing card rewriting its own value — the belt
     * repainting {1,0,0,0,0} / {1,1,1,1,1} — is not a new card, so it re-arms nothing; that is
     * the loop card-keyed acknowledgement exists to prevent. Cards whose condition has cleared
     * are not re-armed either: there is nothing left for the car to bring back.
     */
    fun cardsToRearm(
            raisedCard: String,
            dismissedCards: Set<String>,
            isCardStanding: (String) -> Boolean
    ): Set<String> =
            dismissedCards.filterTo(mutableSetOf()) {
                it != raisedCard && it !in cardsTheCarDoesNotRequeue && isCardStanding(it)
            }

    /**
     * Cards the car does NOT bring back after a newer card closes, so re-arming them only puts
     * our label up with nothing behind it.
     *
     * The tyre card: on 2026-10-19 20:04 the driver dismissed it, unbuckled (belt card up on car
     * and theme) and buckled again — the cluster cleared completely, but re-arm had restored the
     * tyre acknowledgement and our label stayed. Its per-wheel vector never clears, so this
     * repeated on every belt and door event that day.
     */
    private val cardsTheCarDoesNotRequeue: Set<String> =
            setOf(cardIdFor(CarConstants.CAR_BASIC_TIREPRESS_WARNING.value))

    /**
     * Whether a car.basic.driving_ready_state change is the car switching on.
     *
     * The car re-shows its standing warnings at every power-on, so every acknowledgement must go
     * with it. That includes a soft power cycle, where the head unit only sleeps: on 2026-10-23 the
     * car was switched off at 08:03 and on at 08:06 with the head unit up throughout (uptime 1 h 36
     * min), the cluster re-showed its warnings, and our label stayed down because the tyre
     * acknowledgement was still held in memory. A head-unit reboot was already covered by the
     * boot-count check in [isSameBoot].
     *
     * Only a transition counts: with no previous reading ([wasOff] null) nothing is cleared.
     */
    fun isPowerOn(wasOff: Boolean?, isOff: Boolean): Boolean = wasOff == true && !isOff

    /** One acknowledged card as saved: what every key behind it read when it was dismissed. */
    data class SavedDismissal(val cardId: String, val values: Map<String, String>)

    /**
     * Whether a saved acknowledgement was made during the head unit's current boot.
     *
     * The boot count must be known and match: a power cycle is a new drive, the car re-shows its
     * warnings, and ours must too. Elapsed realtime going backwards also means a reboot. An unknown
     * boot count restores nothing, which only costs the driver one more BACK.
     */
    fun isSameBoot(
            savedBootCount: Int,
            savedElapsedMs: Long,
            bootCount: Int,
            elapsedMs: Long
    ): Boolean =
            savedBootCount >= 0 &&
                    savedBootCount == bootCount &&
                    savedElapsedMs <= elapsedMs

    /**
     * Acknowledgements to restore after the app itself restarts mid-drive (a reinstall, a crash).
     *
     * Acknowledgements live in memory, so every restart used to forget them: a per-wheel tyre
     * vector that never clears came straight back after the 2026-10-19 07:01 and 07:24 restarts,
     * minutes after the driver had dismissed it.
     *
     * A card is restored only if every key behind it still reads exactly what it did when it was
     * dismissed, and it is still standing. While the app was down a condition may have cleared
     * and returned, and that must warn again; an identical reading is the strongest evidence
     * available that it is the same standing condition. The cost lands on the safe side: a belt
     * repainting its seat vector restores nothing, so it asks for one more BACK rather than
     * hiding a warning.
     */
    fun dismissalsToRestore(
            saved: List<SavedDismissal>,
            sameBoot: Boolean,
            valueOf: (String) -> String?
    ): Set<String> {
        if (!sameBoot) return emptySet()
        return saved.filter { dismissal ->
                    dismissal.values.isNotEmpty() &&
                            dismissal.values.all { (key, value) -> (valueOf(key) ?: "") == value } &&
                            dismissal.values.any { (key, value) ->
                                raisesWarningBadge(key, value) { lamp ->
                                    isWarningValueActive(dismissal.values[lamp])
                                }
                            }
                }
                .mapTo(mutableSetOf()) { it.cardId }
    }

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

    /**
     * Per-seat belt vector mapped to the lamp that decides whether the car is showing it.
     *
     * The vector says which seat, not whether there is a warning on screen. It stood at
     * {1,0,0,0,0} for 24 min on 2026-10-18 and 20 min on 2026-10-19 (18:43-19:04) with the belt
     * lamp dark and no card on the cluster, holding our label up and re-arming every dismissed
     * card each time it rose. On a real unbuckle the lamp follows within ~150 ms, driver and
     * passenger alike, so gating costs a genuine warning nothing that can be seen.
     *
     * The tyre vector is deliberately NOT gated: car.ipk_light.tpms_warning has never been seen
     * lit on this car, so a tyre gate could silence a real warning (reverted in ba4444fb).
     */
    val vectorLampGates: Map<String, String> =
            mapOf(
                    CarConstants.CAR_BASIC_SEAT_BELT_WARNING.value to
                            CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR.value
            )

    /**
     * How long a lamp still counts as lit after going dark. The belt lamp blinks at ~1 Hz while
     * driving, dark ~500 ms at a time; without a latch every dark phase would read as the warning
     * clearing and drop the driver's dismissal.
     */
    const val LAMP_OFF_LATCH_MS = 2_000L

    /** Lit now, or lit within [LAMP_OFF_LATCH_MS]. */
    fun isLampLitWithLatch(currentValue: String?, lastLitAtMs: Long?, nowMs: Long): Boolean =
            isWarningValueActive(currentValue) ||
                    (lastLitAtMs != null && nowMs - lastLitAtMs < LAMP_OFF_LATCH_MS)

    /**
     * Whether this key/value pair is one the driver sees as a warning badge.
     *
     * [isLampLit] answers for the lamps in [vectorLampGates]; it must be the latched view, or a
     * blink reads as the warning clearing.
     */
    fun raisesWarningBadge(key: String, value: String?, isLampLit: (String) -> Boolean): Boolean {
        if (key in badgeExemptWarningKeys) return false
        if (!isWarningValueActive(value)) return false
        val lamp = vectorLampGates[key] ?: return true
        return isLampLit(lamp)
    }

    /**
     * Vectors the gate is holding back right now: active, lamp dark. Logged with the badge state
     * so a wrong gate is visible, not silent.
     */
    fun lampGatedKeys(valueOf: (String) -> String?, isLampLit: (String) -> Boolean): List<String> =
            vectorLampGates
                    .filter { (vector, lamp) -> isWarningValueActive(valueOf(vector)) && !isLampLit(lamp) }
                    .keys
                    .toList()

    /**
     * How long a warning must have been standing before BACK may dismiss it — set to match the
     * car's own cooldown, so a press the car ignores is ignored here too and the cluster and our
     * label close on the same press.
     *
     * Measured 2026-10-19 20:56 on the belt card, the driver pressing BACK every ~0.7 s and
     * stopping as the car's card closed, timed from the belt lamp (the same onset this lockout
     * uses): the car ignored presses up to 2.952 s and accepted from 2.989 s, in five rounds.
     * The previous 2.5 s cleared our label one press before the car's card in three of them.
     */
    const val DISMISS_LOCKOUT_MS = 2_970L

    fun isDismissLockedOut(timeSinceWarningMs: Long): Boolean = timeSinceWarningMs < DISMISS_LOCKOUT_MS

    /**
     * Instant used for the BACK dismiss lockout.
     *
     * Prefer the later of per-key CAN onset and "became top card" so a standing seatbelt that
     * was buried under a door warning cannot be cleared the moment the door closes.
     */
    fun dismissLockoutOnsetMs(
            keyOnsetMs: Long?,
            cardTopSinceMs: Long?,
            fallbackMs: Long
    ): Long = listOfNotNull(keyOnsetMs, cardTopSinceMs).maxOrNull() ?: fallbackMs
}
