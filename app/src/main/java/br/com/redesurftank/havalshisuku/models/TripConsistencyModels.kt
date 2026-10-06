package br.com.redesurftank.havalshisuku.models

enum class TripConsistencyStatus {
    IDLE,
    ACTIVE,
    PAUSED_AFTER_IGNITION_OFF,
    WAITING_USER_CONFIRMATION,
    COMPLETED,
    CANCELLED
}

enum class TripConsistencyClassification(val label: String, val summary: String) {
    SMOOTH("Viagem suave", "Conducao estavel e previsivel, com poucas variacoes bruscas."),
    SPORTY("Viagem esportiva", "Trajeto com perfil mais dinamico, marcado por aceleracoes e frenagens intensas."),
    HEAVY_TRAFFIC("Transito pesado", "Trajeto com muitas paradas e baixa velocidade media, indicando transito intenso.")
}

enum class TripConsistencyEventType {
    STRONG_ACCELERATION,
    STRONG_BRAKE,
    REGEN_EVENT,
    SPEED_VARIATION,
    VEHICLE_OFF,
    VEHICLE_ON,
    MANUAL_END,
    AUTO_END
}

enum class TripConsistencySeverity {
    LOW,
    MEDIUM,
    HIGH
}

data class TripConsistencyMetrics(
    val speedVariationScore: Int = 100,
    val accelerationScore: Int = 100,
    val brakeRegenScore: Int = 100,
    val strongAccelerationCount: Int = 0,
    val strongBrakeCount: Int = 0,
    val regenEventsCount: Int = 0,
    val consumptionScore: Int? = null,
    val stabilityScore: Int = 100,
    val averageSpeedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
    val stopAndGoIndex: Double? = null,
    val smoothnessScore: Int = 100,
    val energyEfficiencyScore: Int? = null,
    val samplesCount: Int = 0,
    val stopCount: Int = 0,
    val stopAndGoCount: Int = 0,
    val speedVariationEvents: Int = 0
)

data class TripConsistencyEvent(
    val id: String,
    val type: TripConsistencyEventType,
    val timestamp: String,
    val value: Double? = null,
    val label: String,
    val severity: TripConsistencySeverity = TripConsistencySeverity.LOW
)

data class TripConsistencySession(
    val id: String,
    val status: TripConsistencyStatus,
    val startedAt: String,
    val endedAt: String? = null,
    val initialOdometerKm: Double? = null,
    val finalOdometerKm: Double? = null,
    val distanceKm: Double? = null,
    val elapsedSeconds: Long = 0,
    val currentScore: Int = 100,
    val currentClassification: TripConsistencyClassification = TripConsistencyClassification.SMOOTH,
    val metrics: TripConsistencyMetrics = TripConsistencyMetrics(),
    val events: List<TripConsistencyEvent> = emptyList(),
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val pausedAt: String? = null,
    val telemetryWarning: Boolean = false
)

data class TripConsistencyReport(
    val id: String,
    val sessionId: String,
    val startedAt: String,
    val endedAt: String,
    val durationSeconds: Long,
    val distanceKm: Double? = null,
    val score: Int,
    val classification: TripConsistencyClassification,
    val classificationLabel: String,
    val summaryText: String,
    val metrics: TripConsistencyMetrics,
    val events: List<TripConsistencyEvent>,
    val createdAt: String,
    val notes: String? = null
)

data class TripTelemetrySample(
    val timestampMillis: Long,
    val speedKmh: Double? = null,
    val odometerKm: Double? = null,
    val instantFuelConsumption: Double? = null,
    val instantEnergyConsumption: Double? = null,
    val regenLevel: Int? = null,
    val batteryCurrent: Double? = null,
    val engineState: String? = null
)
