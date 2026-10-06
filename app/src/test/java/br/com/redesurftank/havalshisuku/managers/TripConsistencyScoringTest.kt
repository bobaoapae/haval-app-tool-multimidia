package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.TripConsistencyClassification
import br.com.redesurftank.havalshisuku.models.TripTelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripConsistencyScoringTest {
    @Test
    fun smoothTripKeepsHighScoreAndSmoothClassification() {
        val state = listOf(0.0, 8.0, 18.0, 30.0, 42.0, 50.0)
            .foldIndexed(TripScoringState()) { index, previous, speed ->
                TripConsistencyScoring.ingest(
                    previous,
                    TripTelemetrySample(
                        timestampMillis = index * 10_000L,
                        speedKmh = speed,
                        instantEnergyConsumption = 12.0
                    )
                )
            }

        assertEquals(TripConsistencyClassification.SMOOTH, state.classification)
        assertTrue(state.score >= 75)
    }

    @Test
    fun repeatedStrongAccelerationAndBrakeClassifiesAsSporty() {
        val speeds = listOf(0.0, 38.0, 10.0, 52.0, 18.0, 65.0, 25.0, 72.0, 30.0)
        val state = speeds.foldIndexed(TripScoringState()) { index, previous, speed ->
            TripConsistencyScoring.ingest(
                previous,
                TripTelemetrySample(
                    timestampMillis = index * 2_000L,
                    speedKmh = speed
                )
            )
        }

        assertEquals(TripConsistencyClassification.SPORTY, state.classification)
        assertTrue(state.metrics.strongAccelerationCount >= 4)
        assertTrue(state.metrics.strongBrakeCount >= 4)
    }

    @Test
    fun realisticStrongAccelerationCadenceIsCaptured() {
        val speeds = listOf(0.0, 4.0, 9.0, 14.0, 19.0, 25.0)
        val state = speeds.foldIndexed(TripScoringState()) { index, previous, speed ->
            TripConsistencyScoring.ingest(
                previous,
                TripTelemetrySample(
                    timestampMillis = index * 1_000L,
                    speedKmh = speed
                )
            )
        }

        assertTrue(state.metrics.strongAccelerationCount >= 4)
        assertTrue(state.metrics.accelerationScore < 100)
    }

    @Test
    fun smallTelemetrySpeedChangesMoveVariationScoreBelowOneHundred() {
        val speeds = listOf(42.0, 43.5, 41.8, 44.2, 42.7, 45.0)
        val state = speeds.foldIndexed(TripScoringState()) { index, previous, speed ->
            TripConsistencyScoring.ingest(
                previous,
                TripTelemetrySample(
                    timestampMillis = index * 1_000L,
                    speedKmh = speed
                )
            )
        }

        assertTrue(state.metrics.speedVariationScore < 100)
    }

    @Test
    fun lowSpeedStopAndGoClassifiesAsHeavyTraffic() {
        val speeds = listOf(0.0, 12.0, 0.0, 14.0, 0.0, 16.0, 0.0, 10.0, 0.0)
        val state = speeds.foldIndexed(TripScoringState()) { index, previous, speed ->
            TripConsistencyScoring.ingest(
                previous,
                TripTelemetrySample(
                    timestampMillis = index * 20_000L,
                    speedKmh = speed,
                    instantFuelConsumption = 8.0
                )
            )
        }

        assertEquals(TripConsistencyClassification.HEAVY_TRAFFIC, state.classification)
        assertTrue((state.metrics.stopAndGoIndex ?: 0.0) >= TripConsistencyConfig.STOP_AND_GO_PER_MINUTE)
        assertEquals(5, state.metrics.stopAndGoCount)
        assertEquals(0, state.metrics.stopCount)
        assertTrue(state.metrics.speedVariationScore >= 55)
        assertTrue(state.metrics.accelerationScore >= 60)
        assertTrue(state.metrics.brakeRegenScore >= 55)
    }

    @Test
    fun realHeavyTrafficContextDoesNotZeroDrivingScores() {
        val speeds = listOf(0.0, 8.0, 16.0, 6.0, 0.0, 12.0, 24.0, 10.0, 0.0, 18.0, 32.0, 14.0, 0.0)
        val state = speeds.foldIndexed(TripScoringState()) { index, previous, speed ->
            TripConsistencyScoring.ingest(
                previous,
                TripTelemetrySample(
                    timestampMillis = index * 5_000L,
                    speedKmh = speed,
                    instantFuelConsumption = 7.0
                )
            )
        }

        assertEquals(TripConsistencyClassification.HEAVY_TRAFFIC, state.classification)
        assertTrue((state.metrics.averageSpeedKmh ?: 0.0) <= TripConsistencyConfig.LOW_AVERAGE_SPEED_KMH)
        assertTrue((state.metrics.maxSpeedKmh ?: 0.0) <= TripConsistencyConfig.HEAVY_TRAFFIC_MAX_SPEED_KMH)
        assertTrue(state.metrics.stopAndGoIndex ?: 0.0 >= TripConsistencyConfig.STOP_AND_GO_PER_MINUTE)
        assertTrue(state.metrics.speedVariationScore >= 55)
        assertTrue(state.metrics.accelerationScore >= 60)
        assertTrue(state.metrics.brakeRegenScore >= 55)
    }
}
