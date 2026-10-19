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
                        "1121"
                )
        )
        assertFalse(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value,
                        "1"
                )
        )
        assertFalse(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value,
                        "1"
                )
        )
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_BASIC_SEAT_BELT_WARNING.value,
                        "1"
                )
        )
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_LIGHT_DOOR_WARNING.value,
                        "1"
                )
        )
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_LIGHT_FUEL_LOW.value,
                        "1"
                )
        )
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_IPK_LIGHT_TPMS_WARNING.value,
                        "1"
                )
        )
    }

    @Test
    fun criticalWarningKeysTriggerCriticalWarningFlowOnlyWhenActive() {
        assertTrue(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                        "1"
                )
        )
        assertFalse(
                ClusterWarningPolicy.raisesWarningBadge(
                        CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                        "0"
                )
        )
    }

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
        val tyreCard = ClusterWarningPolicy.cardIdFor(tirepress)
        // The raised card's own repaint must not undo its dismissal, and the door has
        // cleared, so there is nothing for the car to bring back.
        assertEquals(
                setOf(tyreCard),
                ClusterWarningPolicy.cardsToRearm(beltCard, setOf(beltCard, tyreCard, door)) {
                    it != door
                }
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
    fun dismissLockoutUsesLaterOfKeyOnsetAndBecameTop() {
        // Seatbelt armed at t=0; door rose on top; door cleared at t=4000 and seatbelt
        // became top again. BACK at t=4500 must still see a fresh lockout anchor.
        assertEquals(4000L, ClusterWarningPolicy.dismissLockoutOnsetMs(0L, 4000L, -1L))
        assertEquals(5000L, ClusterWarningPolicy.dismissLockoutOnsetMs(5000L, 4000L, -1L))
        assertEquals(123L, ClusterWarningPolicy.dismissLockoutOnsetMs(null, null, 123L))
        assertEquals(50L, ClusterWarningPolicy.dismissLockoutOnsetMs(50L, null, 123L))
    }
}
