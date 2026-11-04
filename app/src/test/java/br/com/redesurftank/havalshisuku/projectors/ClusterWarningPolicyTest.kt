package br.com.redesurftank.havalshisuku.projectors

import br.com.redesurftank.havalshisuku.models.CarConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterWarningPolicyTest {
    @Test
    fun activeValuesAreRecognized() {
        assertTrue(ClusterWarningPolicy.isWarningValueActive("1121"))
        assertTrue(ClusterWarningPolicy.isWarningValueActive("1"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("0"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("{0,0,0,0}"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("{0,0,0,0,0}"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("false"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive(""))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("null"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("undefined"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("unknown"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("--"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("NaN"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive(null))
    }

    @Test
    fun visualOnlyWarningKeysDoNotTriggerCriticalWarningFlow() {
        assertFalse(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_INFO_WARNING_TTS_NOTIFY.value,
                        "1121",
                        noLampLit
                )
        )
        assertFalse(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value,
                        "1",
                        noLampLit
                )
        )
        assertFalse(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value,
                        "1",
                        noLampLit
                )
        )
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_BASIC_SEAT_BELT_WARNING.value,
                        "1",
                        lampLit
                )
        )
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_LIGHT_DOOR_WARNING.value,
                        "1",
                        noLampLit
                )
        )
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_LIGHT_FUEL_LOW.value,
                        "1",
                        noLampLit
                )
        )
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_LIGHT_TPMS_WARNING.value,
                        "1",
                        noLampLit
                )
        )
    }

    @Test
    fun criticalWarningKeysTriggerCriticalWarningFlowOnlyWhenActive() {
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                        "1",
                        noLampLit
                )
        )
        assertFalse(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                        "0",
                        noLampLit
                )
        )
    }

    private val noLampLit: (String) -> Boolean = { false }
    private val lampLit: (String) -> Boolean = { true }

    private val belt = CarConstants.CAR_BASIC_SEAT_BELT_WARNING.value
    private val door = CarConstants.CAR_IPK_LIGHT_DOOR_WARNING.value
    private val tirepress = CarConstants.CAR_BASIC_TIREPRESS_WARNING.value

    @Test
    fun keysBehindOneCardShareOneIdentity() {
        assertEquals(
                ClusterWarningPolicy.cardIdFor(belt),
                ClusterWarningPolicy.cardIdFor(
                        CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR.value
                )
        )
        assertEquals(
                ClusterWarningPolicy.cardIdFor(tirepress),
                ClusterWarningPolicy.cardIdFor(CarConstants.CAR_IPK_LIGHT_TPMS_WARNING.value)
        )
        assertEquals(setOf(door), ClusterWarningPolicy.cardKeysFor(door))
    }

    @Test
    fun aNewCardRearmsStandingCardsItPreempts() {
        // 2026-09-13 19:39–19:43: belt card closed with BACK, door opened over it, and the
        // belt card was back on the cluster when the door closed.
        val beltCard = ClusterWarningPolicy.cardIdFor(belt)
        assertEquals(
                setOf(beltCard),
                ClusterWarningPolicy.cardsToRearm(door, setOf(beltCard)) { true }
        )
    }

    @Test
    fun rearmLeavesTheRaisedCardAndClearedCardsAlone() {
        val beltCard = ClusterWarningPolicy.cardIdFor(belt)
        val oilCard = ClusterWarningPolicy.cardIdFor(CarConstants.CAR_BASIC_OIL_LOW_WARNING.value)
        // The raised card's own repaint must not undo its dismissal, and the door has
        // cleared, so there is nothing for the car to bring back.
        assertEquals(
                setOf(oilCard),
                ClusterWarningPolicy.cardsToRearm(beltCard, setOf(beltCard, oilCard, door)) {
                    it != door
                }
        )
    }

    @Test
    fun theTyreCardIsNeverRearmed() {
        // 2026-10-19 20:04: belt off and on again; the cluster cleared, the tyre card stayed gone.
        val beltCard = ClusterWarningPolicy.cardIdFor(belt)
        val tyreCard = ClusterWarningPolicy.cardIdFor(tirepress)
        assertEquals(
                emptySet<String>(),
                ClusterWarningPolicy.cardsToRearm(beltCard, setOf(tyreCard)) { true }
        )
        // The belt card is still re-armed when a door preempts it (2026-09-13).
        assertEquals(
                setOf(beltCard),
                ClusterWarningPolicy.cardsToRearm(door, setOf(beltCard, tyreCard)) { true }
        )
    }

    @Test
    fun aSavedDismissalBelongsOnlyToTheBootItWasMadeIn() {
        // Reinstall mid-drive: same boot, the clock kept running.
        assertTrue(ClusterWarningPolicy.isSameBoot(2579, 1_000L, 2579, 5_000L))
        // Power cycle: a new drive, and the car re-shows its warnings.
        assertFalse(ClusterWarningPolicy.isSameBoot(2579, 1_000L, 2580, 5_000L))
        // Elapsed time went backwards: rebooted, whatever the counter says.
        assertFalse(ClusterWarningPolicy.isSameBoot(2579, 9_000L, 2579, 5_000L))
        // Boot count unknown: restore nothing.
        assertFalse(ClusterWarningPolicy.isSameBoot(-1, 1_000L, -1, 5_000L))
    }

    @Test
    fun aStandingCardWithIdenticalReadingsSurvivesARestart() {
        // 2026-10-19: tyre dismissed at 07:07:56, app reinstalled at 07:24:12, the vector never
        // moved, and the card came straight back.
        val tyreCard = ClusterWarningPolicy.cardIdFor(tirepress)
        val lamp = CarConstants.CAR_IPK_LIGHT_TPMS_WARNING.value
        val readings = mapOf(tirepress to "{0,0,1,0}", lamp to "0")
        val saved = listOf(ClusterWarningPolicy.SavedDismissal(tyreCard, readings))

        assertEquals(
                setOf(tyreCard),
                ClusterWarningPolicy.dismissalsToRestore(saved, true) { readings[it] }
        )
        // Never across a reboot.
        assertEquals(
                emptySet<String>(),
                ClusterWarningPolicy.dismissalsToRestore(saved, false) { readings[it] }
        )
    }

    @Test
    fun aChangedOrClearedCardIsNotRestored() {
        val beltCard = ClusterWarningPolicy.cardIdFor(belt)
        val indicator = CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR.value
        val saved =
                listOf(
                        ClusterWarningPolicy.SavedDismissal(
                                beltCard,
                                mapOf(belt to "{1,0,0,0,0}", indicator to "1")
                        )
                )
        // The seat vector was repainted: not provably the same warning, so it shows again.
        assertEquals(
                emptySet<String>(),
                ClusterWarningPolicy.dismissalsToRestore(saved, true) {
                    mapOf(belt to "{1,1,1,1,1}", indicator to "1")[it]
                }
        )
        // A key with no value yet can't be compared, so nothing is restored on it.
        assertEquals(
                emptySet<String>(),
                ClusterWarningPolicy.dismissalsToRestore(saved, true) { null }
        )
        // Saved while already clear, and still clear: nothing standing to acknowledge.
        val cleared =
                listOf(
                        ClusterWarningPolicy.SavedDismissal(
                                beltCard,
                                mapOf(belt to "0", indicator to "0")
                        )
                )
        assertEquals(
                emptySet<String>(),
                ClusterWarningPolicy.dismissalsToRestore(cleared, true) {
                    mapOf(belt to "0", indicator to "0")[it]
                }
        )
    }

    @Test
    fun beltVectorRaisesOnlyWhileItsLampIsLit() {
        // 2026-10-19 18:43-19:04: {1,0,0,0,0} with the lamp dark and nothing on the cluster.
        assertFalse(ClusterWarningPolicy.raisesWarningBadge(belt, "{1,0,0,0,0}", noLampLit))
        assertTrue(ClusterWarningPolicy.raisesWarningBadge(belt, "{1,0,0,0,0}", lampLit))
        // The lamp itself is never gated.
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR.value,
                        "1",
                        noLampLit
                )
        )
        // The tyre vector stays ungated: its lamp has never been seen lit (ba4444fb).
        assertTrue(ClusterWarningPolicy.raisesWarningBadge(tirepress, "{0,0,1,0}", noLampLit))
    }

    @Test
    fun lampLatchCoversTheBlinkButNotARealClear() {
        // Lit now.
        assertTrue(ClusterWarningPolicy.isLampLitWithLatch("1", null, 10_000L))
        // A ~500 ms dark phase of the 1 Hz blink: still lit.
        assertTrue(ClusterWarningPolicy.isLampLitWithLatch("0", 10_000L, 10_500L))
        // Dark past the latch: genuinely off.
        assertFalse(ClusterWarningPolicy.isLampLitWithLatch("0", 10_000L, 12_001L))
        // Never lit.
        assertFalse(ClusterWarningPolicy.isLampLitWithLatch("0", null, 10_000L))
    }

    @Test
    fun lampGatedKeysNamesTheHeldBackBeltVector() {
        val values = mapOf(belt to "{1,0,0,0,0}")
        assertEquals(listOf(belt), ClusterWarningPolicy.lampGatedKeys({ values[it] }, noLampLit))
        assertEquals(emptyList<String>(), ClusterWarningPolicy.lampGatedKeys({ values[it] }, lampLit))
    }

    @Test
    fun dismissLockoutMatchesTheCarsMeasuredCooldown() {
        // 2026-10-19 20:56, five belt rounds, ms from the lamp: the press the car ignored last,
        // then the press that closed its card.
        val ignoredByCar = listOf(2952L, 2592L, 2230L, 2560L, 2330L)
        val acceptedByCar = listOf(3761L, 3746L, 2994L, 3278L, 2989L)
        ignoredByCar.forEach { assertTrue("$it ms", ClusterWarningPolicy.isDismissLockedOut(it)) }
        acceptedByCar.forEach { assertFalse("$it ms", ClusterWarningPolicy.isDismissLockedOut(it)) }
    }

    @Test
    fun onlyAnOffToOnTransitionCountsAsPowerOn() {
        // 2026-10-23 08:06: switched back on after a soft power-off.
        assertTrue(ClusterWarningPolicy.isPowerOn(wasOff = true, isOff = false))
        // Still on, or still off: nothing to clear.
        assertFalse(ClusterWarningPolicy.isPowerOn(wasOff = false, isOff = false))
        assertFalse(ClusterWarningPolicy.isPowerOn(wasOff = true, isOff = true))
        // Switching off clears nothing; the car re-shows on the way back on.
        assertFalse(ClusterWarningPolicy.isPowerOn(wasOff = false, isOff = true))
        // No previous reading: not a transition.
        assertFalse(ClusterWarningPolicy.isPowerOn(wasOff = null, isOff = false))
    }

    @Test
    fun dismissLockoutUsesLaterOfKeyOnsetAndBecameTop() {
        // Seatbelt armed at t=0; door rose on top; door cleared at t=4000 and seatbelt
        // became top again. BACK at t=4500 must still see a fresh lockout anchor.
        assertEquals(4000L, ClusterWarningPolicy.dismissLockoutOnsetMs(0L, 4000L, -1L))
        assertEquals(5000L, ClusterWarningPolicy.dismissLockoutOnsetMs(5000L, 4000L, -1L))
        assertEquals(123L, ClusterWarningPolicy.dismissLockoutOnsetMs(null, null, 123L))
        assertEquals(50L, ClusterWarningPolicy.dismissLockoutOnsetMs(50L, null, 123L))
    }
}
