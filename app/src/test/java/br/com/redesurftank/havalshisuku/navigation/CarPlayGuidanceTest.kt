package br.com.redesurftank.havalshisuku.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic fixtures only. These tests are not captures or proof of live CarPlay delivery. */
class CarPlayGuidanceTest {
    @Test
    fun rawFieldsKeepTheElevenWirePositionsAndTypes() {
        val raw = RawCarPlayGuidance(
            -17, "distance", -3, "road", "remaining", -6,
            Long.MAX_VALUE, -8, -9, -10, -11
        )

        assertEquals(-17, raw.component1())
        assertEquals("distance", raw.component2())
        assertEquals(-3, raw.component3())
        assertEquals("road", raw.component4())
        assertEquals("remaining", raw.component5())
        assertEquals(-6, raw.component6())
        assertEquals(Long.MAX_VALUE, raw.component7())
        assertEquals(-8, raw.component8())
        assertEquals(-9, raw.component9())
        assertEquals(-10, raw.component10())
        assertEquals(-11, raw.component11())
    }

    @Test
    fun everyKnownRouteStateUsesItsDocumentedWireCode() {
        val states = listOf(
            CarPlayRouteGuidanceState.NO_ROUTE,
            CarPlayRouteGuidanceState.ROUTE_SET,
            CarPlayRouteGuidanceState.ARRIVED,
            CarPlayRouteGuidanceState.LOADING,
            CarPlayRouteGuidanceState.LOCATING,
            CarPlayRouteGuidanceState.REROUTING,
            CarPlayRouteGuidanceState.PROCEED_TO_ROUTE
        )

        states.forEachIndexed { code, state ->
            assertEquals(code, state.wireValue)
            assertEquals(state, CarPlayRouteGuidanceState.fromRaw(code))
            assertEquals(state, fixture().copy(mRouteGuidanceState = code).routeGuidanceState)
        }
        assertNull(CarPlayRouteGuidanceState.UNKNOWN.wireValue)
    }

    @Test
    fun everyKnownDistanceUnitUsesItsDocumentedWireCode() {
        val units = listOf(
            CarPlayDistanceUnit.KILOMETERS,
            CarPlayDistanceUnit.MILES,
            CarPlayDistanceUnit.METERS,
            CarPlayDistanceUnit.YARDS,
            CarPlayDistanceUnit.FEET
        )

        units.forEachIndexed { code, unit ->
            val raw = fixture().copy(
                mDistanceBetweenManeuverDisplayUnits = code,
                mDistanceRemainingDisplayUnits = code
            )
            assertEquals(code, unit.wireValue)
            assertEquals(unit, CarPlayDistanceUnit.fromRaw(code))
            assertEquals(unit, raw.distanceBetweenManeuverUnits)
            assertEquals(unit, raw.distanceRemainingUnits)
            assertNull(CarPlayGuidanceNormalizer.normalize(raw).distanceMeters)
            assertNull(CarPlayGuidanceNormalizer.normalize(raw).remainingMeters)
        }
        assertNull(CarPlayDistanceUnit.UNKNOWN.wireValue)
    }

    @Test
    fun unknownStatesFailClosedWithoutDiscardingTheirOriginalCodes() {
        listOf(Int.MIN_VALUE, -1, 7, 99, Int.MAX_VALUE).forEach { code ->
            val raw = fixture().copy(mRouteGuidanceState = code)
            val normalized = CarPlayGuidanceNormalizer.normalize(raw)

            assertEquals(code, raw.mRouteGuidanceState)
            assertEquals(CarPlayRouteGuidanceState.UNKNOWN, raw.routeGuidanceState)
            assertEquals("UNKNOWN", normalized.navigationState)
            assertFalse("Unexpected eligibility for raw state $code", normalized.active)
        }
    }

    @Test
    fun unknownDistanceUnitsKeepTheirOriginalCodesAndText() {
        listOf(Int.MIN_VALUE, -1, 5, 99, Int.MAX_VALUE).forEach { code ->
            val raw = fixture().copy(
                mDistanceBetweenManeuverDisplayUnits = code,
                mDistanceRemainingDisplayUnits = code
            )
            val normalized = CarPlayGuidanceNormalizer.normalize(raw)

            assertEquals(code, raw.mDistanceBetweenManeuverDisplayUnits)
            assertEquals(code, raw.mDistanceRemainingDisplayUnits)
            assertEquals(CarPlayDistanceUnit.UNKNOWN, raw.distanceBetweenManeuverUnits)
            assertEquals(CarPlayDistanceUnit.UNKNOWN, raw.distanceRemainingUnits)
            assertEquals("1,234", normalized.distanceText)
            assertEquals("1.234", normalized.remainingText)
            assertNull(normalized.distanceMeters)
            assertNull(normalized.remainingMeters)
        }
    }

    @Test
    fun routeSetReroutingAndProceedToRouteAreEligible() {
        listOf(1 to "ROUTE_SET", 5 to "REROUTING", 6 to "PROCEED_TO_ROUTE").forEach { (code, name) ->
            val normalized = CarPlayGuidanceNormalizer.normalize(fixture().copy(mRouteGuidanceState = code))

            assertTrue("Expected eligibility for raw state $code", normalized.active)
            assertEquals(name, normalized.navigationState)
        }
    }

    @Test
    fun noRouteAndArrivedAreInactive() {
        listOf(0 to "NO_ROUTE", 2 to "ARRIVED").forEach { (code, name) ->
            val normalized = CarPlayGuidanceNormalizer.normalize(fixture().copy(mRouteGuidanceState = code))

            assertFalse(normalized.active)
            assertEquals(name, normalized.navigationState)
        }
    }

    @Test
    fun loadingAndLocatingExplicitlyFailClosed() {
        listOf(3 to "LOADING", 4 to "LOCATING").forEach { (code, name) ->
            val normalized = CarPlayGuidanceNormalizer.normalize(fixture().copy(mRouteGuidanceState = code))

            assertFalse(normalized.active)
            assertEquals(name, normalized.navigationState)
        }
    }

    @Test
    fun missingParcelableIsInactiveWithoutInventingRouteEvidence() {
        val normalized = CarPlayGuidanceNormalizer.normalize(null)

        assertEquals(PreparedDirections(active = false, navigationState = "UNKNOWN"), normalized)
    }

    @Test
    fun nullStringsStayNullInRawAndBecomeEmptyOnlyInPreparedDirections() {
        val raw = fixture().copy(
            mDistanceBetweenManeuverDisplayStr = null,
            mAfterManeuverRoadName = null,
            mDistanceRemainingDisplayStr = null
        )
        val normalized = CarPlayGuidanceNormalizer.normalize(raw)

        assertNull(raw.mDistanceBetweenManeuverDisplayStr)
        assertNull(raw.mAfterManeuverRoadName)
        assertNull(raw.mDistanceRemainingDisplayStr)
        assertEquals("", normalized.distanceText)
        assertEquals("", normalized.nextStreet)
        assertEquals("", normalized.remainingText)
        assertTrue(normalized.active)
    }

    @Test
    fun localizedDistancesAndWhitespaceArePreservedWithoutParsing() {
        listOf("", "1,234", "1.234", "1 234", "1\u00a0234", "1,2 km", "1.2 mi", "  400  ", "NaN", "-1", "∞").forEach { text ->
            val raw = fixture().copy(
                mDistanceBetweenManeuverDisplayStr = text,
                mDistanceRemainingDisplayStr = text,
                mAfterManeuverRoadName = "  Rua São João  "
            )
            val normalized = CarPlayGuidanceNormalizer.normalize(raw)

            assertEquals(text, normalized.distanceText)
            assertEquals(text, normalized.remainingText)
            assertEquals("  Rua São João  ", normalized.nextStreet)
            assertNull(normalized.distanceMeters)
            assertNull(normalized.remainingMeters)
        }
    }

    @Test
    fun timeValuesNeverBecomeSecondsOrEtaWithoutUnitEvidence() {
        listOf(Long.MIN_VALUE, -1L, 0L, 1L, 600L, Long.MAX_VALUE).forEach { value ->
            val raw = fixture().copy(mTimeRemainingToDestination = value)
            val normalized = CarPlayGuidanceNormalizer.normalize(raw)

            assertEquals(value, raw.mTimeRemainingToDestination)
            assertNull(normalized.remainingSeconds)
            assertEquals("", normalized.eta)
        }
    }

    @Test
    fun angleSentinelPlausibleAnglesAndExtremeValuesRemainRawOnly() {
        assertEquals(181, RawCarPlayGuidance.JUNCTION_EXIT_ANGLE_DEFAULT)
        listOf(Int.MIN_VALUE, -180, -1, 0, 45, 90, 180, 181, 360, Int.MAX_VALUE).forEach { value ->
            val raw = fixture().copy(mJunctionElementExitAngle = value)

            assertEquals(value, raw.mJunctionElementExitAngle)
            assertNull(CarPlayGuidanceNormalizer.normalize(raw).turnAngle)
        }
    }

    @Test
    fun everyDeclaredManeuverCodeIsPreservedButRemainsUnmapped() {
        (0..53).forEach { code ->
            val normalized = CarPlayGuidanceNormalizer.normalize(fixture().copy(mManeuverType = code))

            assertEquals(code, normalized.turnId)
            assertEquals("UNKNOWN", normalized.turn)
        }
    }

    @Test
    fun negativeAndUnknownManeuverCodesAreNotClampedOrMapped() {
        listOf(Int.MIN_VALUE, -1, 54, 999, Int.MAX_VALUE).forEach { code ->
            val raw = fixture().copy(mManeuverType = code)
            val normalized = CarPlayGuidanceNormalizer.normalize(raw)

            assertEquals(code, raw.mManeuverType)
            assertEquals(code, normalized.turnId)
            assertEquals("UNKNOWN", normalized.turn)
        }
    }

    @Test
    fun proceedToRouteDoesNotGuessDepartOrConfuseStateWithManeuver() {
        val routeStateSix = CarPlayGuidanceNormalizer.normalize(
            fixture().copy(mRouteGuidanceState = 6, mManeuverType = 42)
        )
        val maneuverSix = CarPlayGuidanceNormalizer.normalize(
            fixture().copy(mRouteGuidanceState = 1, mManeuverType = 6)
        )

        assertEquals("PROCEED_TO_ROUTE", routeStateSix.navigationState)
        assertEquals(42, routeStateSix.turnId)
        assertEquals("UNKNOWN", routeStateSix.turn)
        assertEquals("ROUTE_SET", maneuverSix.navigationState)
        assertEquals(6, maneuverSix.turnId)
        assertEquals("UNKNOWN", maneuverSix.turn)
    }

    @Test
    fun junctionAndDrivingSideCannotInventRoundaboutDirectionOrExit() {
        listOf(Int.MIN_VALUE, -1, 0, 1, 2, 3, Int.MAX_VALUE).forEach { value ->
            val raw = fixture().copy(mJunctionType = value, mDrivingSide = value)
            val normalized = CarPlayGuidanceNormalizer.normalize(raw)

            assertEquals(value, raw.mJunctionType)
            assertEquals(value, raw.mDrivingSide)
            assertNull(normalized.roundaboutClockwise)
            assertNull(normalized.exitNumber)
        }
    }

    @Test
    fun unsupportedFieldsRemainAbsentAndNormalizationIsDeterministic() {
        val raw = fixture()
        val original = raw.copy()
        val normalized = CarPlayGuidanceNormalizer.normalize(raw)

        assertEquals("", normalized.street)
        assertEquals("Rua sintética", normalized.nextStreet)
        assertEquals("", normalized.eta)
        assertTrue(normalized.lanes.isEmpty())
        assertNull(normalized.exitNumber)
        assertNull(normalized.turnAngle)
        assertNull(normalized.roundaboutClockwise)
        assertNull(normalized.distanceMeters)
        assertNull(normalized.remainingMeters)
        assertNull(normalized.remainingSeconds)
        assertEquals(normalized, CarPlayGuidanceNormalizer.normalize(raw))
        assertEquals(original, raw)
    }

    private fun fixture() = RawCarPlayGuidance(
        mManeuverType = 7,
        mDistanceBetweenManeuverDisplayStr = "1,234",
        mDistanceBetweenManeuverDisplayUnits = 0,
        mAfterManeuverRoadName = "Rua sintética",
        mDistanceRemainingDisplayStr = "1.234",
        mDistanceRemainingDisplayUnits = 1,
        mTimeRemainingToDestination = 600L,
        mRouteGuidanceState = 1,
        mJunctionType = 0,
        mDrivingSide = 0,
        mJunctionElementExitAngle = RawCarPlayGuidance.JUNCTION_EXIT_ANGLE_DEFAULT
    )
}
