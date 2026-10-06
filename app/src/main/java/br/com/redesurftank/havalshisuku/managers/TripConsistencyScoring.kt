package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.TripConsistencyClassification
import br.com.redesurftank.havalshisuku.models.TripConsistencyEvent
import br.com.redesurftank.havalshisuku.models.TripConsistencyEventType
import br.com.redesurftank.havalshisuku.models.TripConsistencyMetrics
import br.com.redesurftank.havalshisuku.models.TripConsistencySeverity
import br.com.redesurftank.havalshisuku.models.TripTelemetrySample
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TripConsistencyConfig {
    const val MIN_VALID_SAMPLES_FOR_SCORE = 3
    const val STRONG_ACCELERATION_KMH_PER_S = 3.0
    const val STRONG_ACCELERATION_DELTA_KMH = 2.5
    const val STRONG_BRAKE_KMH_PER_S = -5.5
    const val SPEED_VARIATION_KMH = 8.0
    const val SPEED_VARIATION_RATE_KMH_PER_S = 2.0
    const val SOFT_SPEED_VARIATION_KMH_PER_S = 0.5
    const val SOFT_ACCELERATION_KMH_PER_S = 0.8
    const val SOFT_BRAKE_KMH_PER_S = -1.0
    const val LOW_AVERAGE_SPEED_KMH = 18.0
    const val HEAVY_TRAFFIC_MAX_SPEED_KMH = 45.0
    const val STOP_SPEED_KMH = 3.0
    const val STOP_AND_GO_PER_MINUTE = 0.8
    const val REGEN_CURRENT_THRESHOLD = -8.0
    const val REGEN_ABS_CURRENT_THRESHOLD = 8.0
    const val REGEN_LEVEL_THRESHOLD = 1
    const val MAX_PAUSE_AFTER_IGNITION_OFF_HOURS = 12L
}

data class TripScoringState(
    val metrics: TripConsistencyMetrics = TripConsistencyMetrics(),
    val score: Int = 100,
    val classification: TripConsistencyClassification = TripConsistencyClassification.SMOOTH,
    val events: List<TripConsistencyEvent> = emptyList(),
    val lastSample: TripTelemetrySample? = null,
    val firstSampleMillis: Long? = null,
    val speedSum: Double = 0.0,
    val speedSamples: Int = 0,
    val wasStopped: Boolean = false,
    val stopAndGoCount: Int = 0,
    val speedVariationLoad: Double = 0.0,
    val accelerationLoad: Double = 0.0,
    val brakeLoad: Double = 0.0
)

object TripConsistencyScoring {
    fun ingest(previous: TripScoringState, sample: TripTelemetrySample): TripScoringState {
        val speed = sample.speedKmh
        val last = previous.lastSample
        var events = previous.events
        var strongAccelerationCount = previous.metrics.strongAccelerationCount
        var strongBrakeCount = previous.metrics.strongBrakeCount
        var regenEventsCount = previous.metrics.regenEventsCount
        var speedVariationEvents = previous.metrics.speedVariationEvents
        val stopCount = previous.metrics.stopCount
        var stopAndGoCount = previous.metrics.stopAndGoCount.takeIf { it > 0 } ?: previous.stopAndGoCount
        var wasStopped = previous.wasStopped
        var speedVariationLoad = previous.speedVariationLoad
        var accelerationLoad = previous.accelerationLoad
        var brakeLoad = previous.brakeLoad

        if (speed != null && last?.speedKmh != null) {
            val deltaSeconds = max((sample.timestampMillis - last.timestampMillis) / 1000.0, 0.2)
            val deltaSpeed = speed - last.speedKmh
            val accelerationRate = deltaSpeed / deltaSeconds
            val absDeltaSpeed = abs(deltaSpeed)

            val absAccelerationRate = abs(accelerationRate)

            if (absAccelerationRate >= TripConsistencyConfig.SOFT_SPEED_VARIATION_KMH_PER_S) {
                speedVariationLoad += (absAccelerationRate - TripConsistencyConfig.SOFT_SPEED_VARIATION_KMH_PER_S) / 1.5
            }
            if (accelerationRate >= TripConsistencyConfig.SOFT_ACCELERATION_KMH_PER_S) {
                accelerationLoad += (accelerationRate - TripConsistencyConfig.SOFT_ACCELERATION_KMH_PER_S) / 1.8
            }
            if (accelerationRate <= TripConsistencyConfig.SOFT_BRAKE_KMH_PER_S) {
                brakeLoad += (abs(accelerationRate) - abs(TripConsistencyConfig.SOFT_BRAKE_KMH_PER_S)) / 2.0
            }

            when {
                accelerationRate >= TripConsistencyConfig.STRONG_ACCELERATION_KMH_PER_S &&
                    deltaSpeed >= TripConsistencyConfig.STRONG_ACCELERATION_DELTA_KMH -> {
                    strongAccelerationCount += 1
                    events = events + event(
                        TripConsistencyEventType.STRONG_ACCELERATION,
                        accelerationRate,
                        "Aceleracao forte detectada",
                        TripConsistencySeverity.MEDIUM
                    )
                }
                accelerationRate <= TripConsistencyConfig.STRONG_BRAKE_KMH_PER_S -> {
                    strongBrakeCount += 1
                    events = events + event(
                        TripConsistencyEventType.STRONG_BRAKE,
                        accelerationRate,
                        "Frenagem forte detectada",
                        TripConsistencySeverity.MEDIUM
                    )
                }
            }

            if (absDeltaSpeed >= TripConsistencyConfig.SPEED_VARIATION_KMH &&
                absAccelerationRate >= TripConsistencyConfig.SPEED_VARIATION_RATE_KMH_PER_S
            ) {
                speedVariationEvents += 1
                events = events + event(
                    TripConsistencyEventType.SPEED_VARIATION,
                    absDeltaSpeed,
                    "Variacao relevante de velocidade",
                    TripConsistencySeverity.LOW
                )
            }
        }

        val hasRegenCurrent = sample.batteryCurrent?.let {
            it <= TripConsistencyConfig.REGEN_CURRENT_THRESHOLD ||
                abs(it) >= TripConsistencyConfig.REGEN_ABS_CURRENT_THRESHOLD
        } == true
        val hasRegenLevel = (sample.regenLevel ?: 0) >= TripConsistencyConfig.REGEN_LEVEL_THRESHOLD
        if (hasRegenCurrent || hasRegenLevel) {
            regenEventsCount += 1
            events = events + event(
                TripConsistencyEventType.REGEN_EVENT,
                sample.batteryCurrent ?: sample.regenLevel?.toDouble(),
                "Regeneracao relevante",
                TripConsistencySeverity.LOW
            )
        }

        if (speed != null) {
            val stoppedNow = speed <= TripConsistencyConfig.STOP_SPEED_KMH
            if (stoppedNow && !wasStopped) stopAndGoCount += 1
            wasStopped = stoppedNow
        }

        val speedSamples = previous.speedSamples + if (speed != null) 1 else 0
        val speedSum = previous.speedSum + (speed ?: 0.0)
        val averageSpeed = if (speedSamples > 0) speedSum / speedSamples else null
        val maxSpeed = max(previous.metrics.maxSpeedKmh ?: 0.0, speed ?: 0.0).takeIf { speedSamples > 0 }
        val firstSampleMillis = previous.firstSampleMillis ?: sample.timestampMillis
        val elapsedMinutes = elapsedMinutes(firstSampleMillis, sample.timestampMillis)
        val stopAndGoIndex = if (elapsedMinutes > 0.0) stopAndGoCount / elapsedMinutes else 0.0
        val heavyTrafficContext = isHeavyTrafficContext(averageSpeed, maxSpeed, stopAndGoIndex)

        val speedVariationScore = contextualPenaltyScore(
            count = speedVariationEvents,
            countPenalty = 3,
            softLoad = speedVariationLoad,
            softPenalty = 4.0,
            heavyTrafficContext = heavyTrafficContext,
            heavyTrafficFloor = 55
        )
        val accelerationScore = contextualPenaltyScore(
            count = strongAccelerationCount,
            countPenalty = 5,
            softLoad = accelerationLoad,
            softPenalty = 6.0,
            heavyTrafficContext = heavyTrafficContext,
            heavyTrafficFloor = 60
        )
        val brakeScore = contextualPenaltyScore(
            count = strongBrakeCount,
            countPenalty = 5,
            softLoad = brakeLoad + regenEventsCount * 0.35,
            softPenalty = 5.0,
            heavyTrafficContext = heavyTrafficContext,
            heavyTrafficFloor = 55
        )
        val consumptionScore = consumptionScore(sample.instantFuelConsumption, sample.instantEnergyConsumption)
        val stabilityScore = ((speedVariationScore * 0.45) + (accelerationScore * 0.25) + (brakeScore * 0.30)).toInt()
        val contextScore = contextScore(heavyTrafficContext)
        val score = weightedScore(speedVariationScore, accelerationScore, brakeScore, consumptionScore, stabilityScore, contextScore)
        val classification = classify(score, strongAccelerationCount, strongBrakeCount, speedVariationEvents, averageSpeed, maxSpeed, stopAndGoIndex)

        val metrics = TripConsistencyMetrics(
            speedVariationScore = speedVariationScore,
            accelerationScore = accelerationScore,
            brakeRegenScore = brakeScore,
            strongAccelerationCount = strongAccelerationCount,
            strongBrakeCount = strongBrakeCount,
            regenEventsCount = regenEventsCount,
            consumptionScore = consumptionScore,
            stabilityScore = stabilityScore,
            averageSpeedKmh = averageSpeed,
            maxSpeedKmh = maxSpeed,
            stopAndGoIndex = stopAndGoIndex,
            smoothnessScore = ((speedVariationScore + accelerationScore + brakeScore) / 3),
            energyEfficiencyScore = consumptionScore,
            samplesCount = speedSamples,
            stopCount = stopCount,
            stopAndGoCount = stopAndGoCount,
            speedVariationEvents = speedVariationEvents
        )

        return previous.copy(
            metrics = metrics,
            score = score,
            classification = classification,
            events = events.takeLast(80),
            lastSample = sample,
            firstSampleMillis = firstSampleMillis,
            speedSum = speedSum,
            speedSamples = speedSamples,
            wasStopped = wasStopped,
            stopAndGoCount = stopAndGoCount,
            speedVariationLoad = speedVariationLoad,
            accelerationLoad = accelerationLoad,
            brakeLoad = brakeLoad
        )
    }

    fun classify(
        score: Int,
        strongAccelerationCount: Int,
        strongBrakeCount: Int,
        speedVariationEvents: Int,
        averageSpeedKmh: Double?,
        maxSpeedKmh: Double?,
        stopAndGoIndex: Double?
    ): TripConsistencyClassification {
        val avg = averageSpeedKmh ?: 0.0
        val maxSpeed = maxSpeedKmh ?: 0.0
        val stopGo = stopAndGoIndex ?: 0.0
        if (avg in 0.1..TripConsistencyConfig.LOW_AVERAGE_SPEED_KMH &&
            maxSpeed <= TripConsistencyConfig.HEAVY_TRAFFIC_MAX_SPEED_KMH &&
            stopGo >= TripConsistencyConfig.STOP_AND_GO_PER_MINUTE
        ) {
            return TripConsistencyClassification.HEAVY_TRAFFIC
        }
        if (strongAccelerationCount >= 4 || strongBrakeCount >= 4 || speedVariationEvents >= 7) {
            return TripConsistencyClassification.SPORTY
        }
        return if (score >= 75) TripConsistencyClassification.SMOOTH else TripConsistencyClassification.SPORTY
    }

    private fun weightedScore(
        speedVariationScore: Int,
        accelerationScore: Int,
        brakeScore: Int,
        consumptionScore: Int?,
        stabilityScore: Int,
        contextScore: Int
    ): Int {
        val weighted = listOfNotNull(
            speedVariationScore to 0.25,
            accelerationScore to 0.20,
            brakeScore to 0.20,
            consumptionScore?.let { it to 0.15 },
            stabilityScore to 0.15,
            contextScore to 0.05
        )
        val totalWeight = weighted.sumOf { it.second }
        return (weighted.sumOf { it.first * it.second } / totalWeight).toInt().coerceIn(0, 100)
    }

    private fun contextualPenaltyScore(
        count: Int,
        countPenalty: Int,
        softLoad: Double,
        softPenalty: Double,
        heavyTrafficContext: Boolean,
        heavyTrafficFloor: Int
    ): Int {
        val contextLoad = if (heavyTrafficContext) softLoad * 0.30 else softLoad
        val contextCountPenalty = if (heavyTrafficContext) max(1, countPenalty / 2) else countPenalty
        val score = (100 - count * contextCountPenalty - (contextLoad * softPenalty).toInt()).coerceIn(0, 100)
        return if (heavyTrafficContext) max(score, heavyTrafficFloor) else score
    }

    private fun consumptionScore(fuel: Double?, energy: Double?): Int? {
        val fuelScore = fuel?.takeIf { it > 0.0 }?.let { (100 - (it * 4)).toInt().coerceIn(35, 100) }
        val energyScore = energy?.takeIf { it > 0.0 }?.let { (100 - (it * 2.5)).toInt().coerceIn(35, 100) }
        return listOfNotNull(fuelScore, energyScore).takeIf { it.isNotEmpty() }?.average()?.toInt()
    }

    private fun contextScore(heavyTrafficContext: Boolean): Int = if (heavyTrafficContext) 85 else 100

    private fun isHeavyTrafficContext(averageSpeed: Double?, maxSpeed: Double?, stopAndGoIndex: Double): Boolean =
        (averageSpeed ?: 0.0) <= TripConsistencyConfig.LOW_AVERAGE_SPEED_KMH &&
            (maxSpeed ?: 0.0) <= TripConsistencyConfig.HEAVY_TRAFFIC_MAX_SPEED_KMH &&
            stopAndGoIndex >= TripConsistencyConfig.STOP_AND_GO_PER_MINUTE

    private fun elapsedMinutes(firstMillis: Long?, currentMillis: Long): Double {
        if (firstMillis == null) return 0.0
        return max((currentMillis - firstMillis) / 60000.0, 0.0)
    }

    fun event(
        type: TripConsistencyEventType,
        value: Double?,
        label: String,
        severity: TripConsistencySeverity
    ): TripConsistencyEvent = TripConsistencyEvent(
        id = UUID.randomUUID().toString(),
        type = type,
        timestamp = Instant.now().toString(),
        value = value,
        label = label,
        severity = severity
    )
}
