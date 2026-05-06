package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.listeners.IDataChanged
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.TripConsistencyClassification
import br.com.redesurftank.havalshisuku.models.TripConsistencyEventType
import br.com.redesurftank.havalshisuku.models.TripConsistencyReport
import br.com.redesurftank.havalshisuku.models.TripConsistencySession
import br.com.redesurftank.havalshisuku.models.TripConsistencySeverity
import br.com.redesurftank.havalshisuku.models.TripConsistencyStatus
import br.com.redesurftank.havalshisuku.models.TripTelemetrySample
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Duration
import java.time.Instant
import java.util.UUID

class TripConsistencyManager private constructor() : IDataChanged {
    private val prefs: SharedPreferences =
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var scoringState = TripScoringState()
    private var registered = false

    var currentSession by mutableStateOf(loadSession())
        private set

    var reportHistory by mutableStateOf(loadReportHistory())
        private set

    var lastReport by mutableStateOf(reportHistory.firstOrNull())
        private set

    init {
        scoringState = currentSession?.toScoringState() ?: TripScoringState()
    }

    fun initialize() {
        if (!registered) {
            ServiceManager.getInstance().addDataChangedListener(this)
            registered = true
        }
        restoreClusterIndicator()
    }

    fun startTrip() {
        val now = Instant.now().toString()
        val initialOdometer = readDouble(CarConstants.CAR_BASIC_TOTAL_ODOMETER)
        scoringState = TripScoringState()
        currentSession = TripConsistencySession(
            id = UUID.randomUUID().toString(),
            status = TripConsistencyStatus.ACTIVE,
            startedAt = now,
            initialOdometerKm = initialOdometer,
            currentScore = 100,
            currentClassification = TripConsistencyClassification.SMOOTH,
            createdAt = now,
            updatedAt = now,
            telemetryWarning = !ServiceManager.getInstance().isServicesInitialized
        )
        saveSession()
        setClusterIndicator(true, currentSession?.currentScore)
        ingestSnapshot()
    }

    fun continueTrip() {
        val session = currentSession ?: return
        currentSession = session.copy(
            status = TripConsistencyStatus.ACTIVE,
            pausedAt = null,
            updatedAt = Instant.now().toString()
        )
        saveSession()
        setClusterIndicator(true, currentSession?.currentScore)
        ingestSnapshot()
    }

    fun viewLater() {
        val session = currentSession ?: return
        currentSession = session.copy(
            status = TripConsistencyStatus.WAITING_USER_CONFIRMATION,
            updatedAt = Instant.now().toString()
        )
        saveSession()
        setClusterIndicator(true, session.currentScore)
    }

    fun addNote(note: String) {
        val session = currentSession ?: return
        currentSession = session.copy(notes = note.ifBlank { null }, updatedAt = Instant.now().toString())
        saveSession()
    }

    fun finishTrip(manual: Boolean = true): TripConsistencyReport? {
        val session = currentSession ?: return null
        val now = Instant.now().toString()
        val finalOdometer = readDouble(CarConstants.CAR_BASIC_TOTAL_ODOMETER)
        val distance = calculateDistance(session.initialOdometerKm, finalOdometer)
        val duration = Duration.between(Instant.parse(session.startedAt), Instant.parse(now)).seconds.coerceAtLeast(0)
        val endEvent = TripConsistencyScoring.event(
            if (manual) TripConsistencyEventType.MANUAL_END else TripConsistencyEventType.AUTO_END,
            null,
            if (manual) "Analise encerrada manualmente" else "Analise finalizada por inatividade",
            TripConsistencySeverity.LOW
        )
        val finalEvents = (session.events + endEvent).takeLast(80)
        val report = TripConsistencyReport(
            id = UUID.randomUUID().toString(),
            sessionId = session.id,
            startedAt = session.startedAt,
            endedAt = now,
            durationSeconds = duration,
            distanceKm = distance ?: session.distanceKm,
            score = session.currentScore,
            classification = session.currentClassification,
            classificationLabel = session.currentClassification.label,
            summaryText = session.currentClassification.summary,
            metrics = session.metrics,
            events = finalEvents,
            createdAt = now,
            notes = session.notes
        )
        reportHistory = (listOf(report) + reportHistory.filterNot { it.id == report.id }).take(MAX_REPORT_HISTORY)
        lastReport = reportHistory.firstOrNull()
        currentSession = null
        scoringState = TripScoringState()
        saveReportHistory()
        clearSession()
        setClusterIndicator(false, null)
        return report
    }

    fun clearReport() {
        reportHistory = emptyList()
        lastReport = null
        prefs.edit {
            remove(KEY_REPORT_HISTORY)
            remove(KEY_LAST_REPORT)
        }
    }

    fun deleteReport(reportId: String) {
        reportHistory = reportHistory.filterNot { it.id == reportId }
        lastReport = reportHistory.firstOrNull()
        saveReportHistory()
    }

    override fun onDataChanged(key: String, value: String?) {
        when (key) {
            CarConstants.CAR_BASIC_ENGINE_STATE.value -> handleEngineState(value)
            CarConstants.CAR_BASIC_VEHICLE_SPEED.value,
            CarConstants.CAR_BASIC_TOTAL_ODOMETER.value,
            CarConstants.CAR_BASIC_INSTANT_FUEL_CONSUMPTION.value,
            CarConstants.CAR_BASIC_CUR_JOURNEY_AVG_FUEL_CONSUME.value,
            CarConstants.CAR_BASIC_AVG_FUEL_CONSUMPTION.value,
            CarConstants.CAR_EV_INFO_INSTANT_ENERGY_CONSUMPTION.value,
            CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.value,
            CarConstants.CAR_EV_INFO_POWER_BATTERY_CURRENT.value,
            CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value -> {
                if (currentSession?.status == TripConsistencyStatus.ACTIVE) ingestSnapshot()
            }
        }
    }

    private fun handleEngineState(value: String?) {
        val session = currentSession ?: return
        val isOff = value in setOf("-1", "10", "14", "15")
        if (isOff && session.status == TripConsistencyStatus.ACTIVE) {
            val now = Instant.now().toString()
            val metrics = session.metrics.copy(stopCount = session.metrics.stopCount + 1)
            val event = TripConsistencyScoring.event(
                TripConsistencyEventType.VEHICLE_OFF,
                null,
                "Veiculo desligado durante a analise",
                TripConsistencySeverity.LOW
            )
            scoringState = scoringState.copy(metrics = metrics)
            currentSession = session.copy(
                status = TripConsistencyStatus.PAUSED_AFTER_IGNITION_OFF,
                pausedAt = now,
                updatedAt = now,
                metrics = metrics,
                events = (session.events + event).takeLast(80)
            )
            saveSession()
            setClusterIndicator(true, session.currentScore)
        } else if (!isOff && session.status in setOf(TripConsistencyStatus.PAUSED_AFTER_IGNITION_OFF, TripConsistencyStatus.WAITING_USER_CONFIRMATION)) {
            val pausedAt = session.pausedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val withinPauseWindow = pausedAt == null ||
                Duration.between(pausedAt, Instant.now()).toHours() <= TripConsistencyConfig.MAX_PAUSE_AFTER_IGNITION_OFF_HOURS
            if (withinPauseWindow) {
                val event = TripConsistencyScoring.event(
                    TripConsistencyEventType.VEHICLE_ON,
                    null,
                    "Veiculo religado durante a viagem",
                    TripConsistencySeverity.LOW
                )
                currentSession = session.copy(
                    status = TripConsistencyStatus.ACTIVE,
                    pausedAt = null,
                    updatedAt = Instant.now().toString(),
                    events = (session.events + event).takeLast(80)
                )
                saveSession()
                setClusterIndicator(true, currentSession?.currentScore)
            } else {
                finishTrip(manual = false)
            }
        }
    }

    private fun ingestSnapshot() {
        val session = currentSession ?: return
        val sample = TripTelemetrySample(
            timestampMillis = System.currentTimeMillis(),
            speedKmh = readDouble(CarConstants.CAR_BASIC_VEHICLE_SPEED),
            odometerKm = readDouble(CarConstants.CAR_BASIC_TOTAL_ODOMETER),
            instantFuelConsumption = readFuelConsumption(),
            instantEnergyConsumption = readDouble(CarConstants.CAR_EV_INFO_INSTANT_ENERGY_CONSUMPTION),
            regenLevel = readInt(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL),
            batteryCurrent = readDouble(CarConstants.CAR_EV_INFO_POWER_BATTERY_CURRENT)
                ?: readDouble(CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT),
            engineState = ServiceManager.getInstance().getData(CarConstants.CAR_BASIC_ENGINE_STATE.value)
        )

        scoringState = TripConsistencyScoring.ingest(scoringState, sample)
        val now = Instant.now().toString()
        val distance = calculateDistance(session.initialOdometerKm, sample.odometerKm) ?: session.distanceKm
        val elapsed = Duration.between(Instant.parse(session.startedAt), Instant.now()).seconds.coerceAtLeast(0)
        currentSession = session.copy(
            elapsedSeconds = elapsed,
            currentScore = scoringState.score,
            currentClassification = scoringState.classification,
            metrics = scoringState.metrics,
            events = scoringState.events,
            distanceKm = distance,
            updatedAt = now,
            telemetryWarning = sample.speedKmh == null
        )
        saveSession()
        setClusterIndicator(true, scoringState.score)
    }

    private fun restoreClusterIndicator() {
        val active = currentSession?.status in setOf(
            TripConsistencyStatus.ACTIVE,
            TripConsistencyStatus.PAUSED_AFTER_IGNITION_OFF,
            TripConsistencyStatus.WAITING_USER_CONFIRMATION
        )
        setClusterIndicator(active, currentSession?.currentScore)
    }

    private fun setClusterIndicator(active: Boolean, score: Int?) {
        prefs.edit {
            putBoolean(SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_ACTIVE.key, active)
            if (active && score != null) {
                putInt(SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_SCORE.key, score.coerceIn(0, 100))
            } else {
                remove(SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_SCORE.key)
            }
        }
    }

    private fun readDouble(key: CarConstants): Double? =
        ServiceManager.getInstance().getData(key.value)?.replace(',', '.')?.toDoubleOrNull()

    private fun readInt(key: CarConstants): Int? =
        ServiceManager.getInstance().getData(key.value)?.toIntOrNull()

    private fun readFuelConsumption(): Double? {
        val sm = ServiceManager.getInstance()
        val candidates = listOf(
            sm.getData(CarConstants.CAR_BASIC_INSTANT_FUEL_CONSUMPTION.value),
            sm.getData(CarConstants.CAR_BASIC_CUR_JOURNEY_AVG_FUEL_CONSUME.value),
            sm.getData(CarConstants.CAR_BASIC_AVG_FUEL_CONSUMPTION.value)
        )
        return candidates.firstNotNullOfOrNull(::parseFuelConsumption)
    }

    private fun parseFuelConsumption(raw: String?): Double? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() && it != "--" } ?: return null

        if (value.startsWith("{") && value.endsWith("}") && value.contains(",")) {
            val parts = value.substring(1, value.length - 1).split(",")
            if (parts.size >= 2) {
                val metric = parts[0].trim().replace(',', '.').toDoubleOrNull()
                val consumption = parts[1].trim().replace(',', '.').toDoubleOrNull()
                if (metric == 1.0 && consumption != null && consumption > 0.0) return consumption
                if (metric == 4.0 && consumption != null && consumption > 0.0) {
                    return consumption
                }
            }
            return null
        }

        return value.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    private fun calculateDistance(initial: Double?, final: Double?): Double? {
        if (initial == null || final == null || final < initial) return null
        return final - initial
    }

    private fun TripConsistencySession.toScoringState(): TripScoringState =
        TripScoringState(
            metrics = metrics,
            score = currentScore,
            classification = currentClassification,
            events = events,
            speedSum = (metrics.averageSpeedKmh ?: 0.0) * metrics.samplesCount,
            speedSamples = metrics.samplesCount,
            stopAndGoCount = metrics.stopAndGoCount,
            firstSampleMillis = runCatching { Instant.parse(startedAt).toEpochMilli() }.getOrNull()
        )

    private fun loadSession(): TripConsistencySession? =
        prefs.getString(KEY_ACTIVE_SESSION, null)?.let { json ->
            runCatching { gson.fromJson(json, TripConsistencySession::class.java) }
                .onFailure { Log.e(TAG, "Failed to load trip consistency session", it) }
                .getOrNull()
        }

    private fun loadReportHistory(): List<TripConsistencyReport> {
        val historyJson = prefs.getString(KEY_REPORT_HISTORY, null)
        if (!historyJson.isNullOrBlank()) {
            val type = object : TypeToken<List<TripConsistencyReport>>() {}.type
            return runCatching { gson.fromJson<List<TripConsistencyReport>>(historyJson, type).take(MAX_REPORT_HISTORY) }
                .onFailure { Log.e(TAG, "Failed to load trip consistency report", it) }
                .getOrDefault(emptyList())
        }

        return prefs.getString(KEY_LAST_REPORT, null)?.let { json ->
            runCatching { listOf(gson.fromJson(json, TripConsistencyReport::class.java)) }
                .onFailure { Log.e(TAG, "Failed to migrate last trip consistency report", it) }
                .getOrDefault(emptyList())
        } ?: emptyList()
    }

    private fun saveSession() {
        prefs.edit { putString(KEY_ACTIVE_SESSION, gson.toJson(currentSession)) }
    }

    private fun clearSession() {
        prefs.edit { remove(KEY_ACTIVE_SESSION) }
    }

    private fun saveReportHistory() {
        prefs.edit {
            putString(KEY_REPORT_HISTORY, gson.toJson(reportHistory))
            putString(KEY_LAST_REPORT, gson.toJson(lastReport))
        }
    }

    companion object {
        private const val TAG = "TripConsistencyManager"
        private const val MAX_REPORT_HISTORY = 10
        private const val KEY_ACTIVE_SESSION = "tripConsistency.activeSession"
        private const val KEY_LAST_REPORT = "tripConsistency.lastReport"
        private const val KEY_REPORT_HISTORY = "tripConsistency.reportHistory"

        @Volatile private var instance: TripConsistencyManager? = null

        fun getInstance(): TripConsistencyManager =
            instance ?: synchronized(this) {
                instance ?: TripConsistencyManager().also { instance = it }
            }
    }
}
