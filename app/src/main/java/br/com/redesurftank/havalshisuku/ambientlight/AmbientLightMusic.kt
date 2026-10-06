package br.com.redesurftank.havalshisuku.ambientlight

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import br.com.redesurftank.havalshisuku.listeners.IDataChanged
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AudioBeatFrame(
    val bassLevel: Float,
    val beatDetected: Boolean,
    val timestampMs: Long = System.currentTimeMillis(),
    val rawLevel: Float = bassLevel,
    val bassRatio: Float = 1f
)

interface AudioBeatDetector {
    fun analyzeFft(fft: ByteArray): AudioBeatFrame
}

class BassAnalyzer(
    private val beatThreshold: Float = 0.36f,
    private val beatBoost: Float = 1.55f
) : AudioBeatDetector {
    private var baseline = 0.16f
    private var previousBassLevel = 0f
    private var beatArmed = true

    override fun analyzeFft(fft: ByteArray): AudioBeatFrame {
        if (fft.size < MIN_FFT_BYTES) {
            return AudioBeatFrame(bassLevel = 0f, beatDetected = false)
        }

        val bins = ((fft.size - 2) / 2).coerceAtMost(BASS_BIN_COUNT)
        if (bins <= 0) {
            return AudioBeatFrame(bassLevel = 0f, beatDetected = false)
        }

        var lowFrequencyEnergy = 0.0
        repeat(bins) { bin ->
            val index = 2 + (bin * 2)
            val real = fft[index].toInt()
            val imaginary = fft[index + 1].toInt()
            lowFrequencyEnergy += sqrt((real * real + imaginary * imaginary).toDouble())
        }

        val bassLevel = (lowFrequencyEnergy / bins.toDouble() / MAX_FFT_MAGNITUDE).toFloat().coerceIn(0f, 1f)
        val currentBaseline = baseline
        val releaseLevel = max(currentBaseline * 1.10f, beatThreshold * 0.75f)
        if (!beatArmed && bassLevel <= releaseLevel) {
            beatArmed = true
        }
        val risingEdge = bassLevel >= previousBassLevel * 1.22f && bassLevel - previousBassLevel >= 0.06f
        val burstFromFloor = previousBassLevel < currentBaseline * 1.12f && bassLevel >= currentBaseline * beatBoost
        val beatDetected = beatArmed && bassLevel >= beatThreshold && (risingEdge || burstFromFloor)
        if (beatDetected) {
            beatArmed = false
        }
        val baselineWeight = if (beatDetected) 0.04f else 0.08f
        baseline = ((baseline * (1f - baselineWeight)) + (bassLevel * baselineWeight)).coerceIn(0.05f, 0.65f)
        previousBassLevel = bassLevel
        return AudioBeatFrame(bassLevel = bassLevel, beatDetected = beatDetected)
    }

    companion object {
        private const val MIN_FFT_BYTES = 4
        private const val BASS_BIN_COUNT = 10
        private const val MAX_FFT_MAGNITUDE = 128.0
    }
}

class MicrophoneBassAnalyzer(
    private val beatThreshold: Float = 0.014f,
    private val beatBoost: Float = 1.65f,
    private val sampleRate: Int = 8_000
) {
    private var baseline = 0.010f
    private var previousBassScore = 0f
    private var beatArmed = true

    fun analyzePcm16(samples: ShortArray, readCount: Int): AudioBeatFrame {
        if (readCount <= 0) {
            return AudioBeatFrame(bassLevel = 0f, beatDetected = false)
        }

        val count = readCount.coerceAtMost(samples.size)
        var sumSquares = 0.0
        repeat(count) { index ->
            val normalized = samples[index].toDouble() / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }

        val rms = sqrt(sumSquares / count.toDouble()).toFloat().coerceIn(0f, 1f)
        if (rms < MICROPHONE_NOISE_FLOOR) {
            previousBassScore = 0f
            beatArmed = true
            baseline = ((baseline * 0.94f) + (0.006f * 0.06f)).coerceIn(0.006f, 0.5f)
            return AudioBeatFrame(bassLevel = 0f, beatDetected = false, rawLevel = rms, bassRatio = 0f)
        }

        val bassMagnitude = averageMagnitude(samples, count, BASS_FREQUENCIES)
        val referenceMagnitude = averageMagnitude(samples, count, REFERENCE_FREQUENCIES)
        val bassRatio = (bassMagnitude / (bassMagnitude + referenceMagnitude + EPSILON)).coerceIn(0f, 1f)
        val bassScore = (bassMagnitude * bassRatio).coerceIn(0f, 1f)
        val currentBaseline = baseline
        val relativeLevel = (bassScore / (currentBaseline * 2.4f)).coerceIn(0f, 1f)
        val risingEdge = bassScore >= previousBassScore * 1.24f && bassScore - previousBassScore >= 0.004f
        val burstFromFloor = previousBassScore < currentBaseline * 1.25f && bassScore >= currentBaseline * beatBoost
        val releaseLevel = max(currentBaseline * 1.35f, beatThreshold * 0.70f)
        if (!beatArmed && bassScore <= releaseLevel) {
            beatArmed = true
        }
        val beatDetected =
            beatArmed &&
                bassScore >= beatThreshold &&
                bassRatio >= MINIMUM_BASS_RATIO &&
                relativeLevel >= 0.30f &&
                (risingEdge || burstFromFloor)
        if (beatDetected) {
            beatArmed = false
        }
        val baselineWeight = if (beatDetected) 0.03f else 0.08f
        baseline = ((baseline * (1f - baselineWeight)) + (bassScore * baselineWeight)).coerceIn(0.006f, 0.5f)
        previousBassScore = bassScore
        return AudioBeatFrame(
            bassLevel = relativeLevel,
            beatDetected = beatDetected,
            rawLevel = bassScore,
            bassRatio = bassRatio
        )
    }

    private fun averageMagnitude(samples: ShortArray, count: Int, frequencies: IntArray): Float {
        if (count <= 0) return 0f
        var total = 0.0
        frequencies.forEach { frequency ->
            total += goertzelMagnitude(samples, count, frequency)
        }
        return (total / frequencies.size.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun goertzelMagnitude(samples: ShortArray, count: Int, frequency: Int): Double {
        val omega = (TWO_PI * frequency.toDouble()) / sampleRate.toDouble()
        val coefficient = 2.0 * kotlin.math.cos(omega)
        var q0: Double
        var q1 = 0.0
        var q2 = 0.0
        repeat(count) { index ->
            q0 = coefficient * q1 - q2 + (samples[index].toDouble() / Short.MAX_VALUE.toDouble())
            q2 = q1
            q1 = q0
        }
        val power = q1 * q1 + q2 * q2 - coefficient * q1 * q2
        return ((2.0 * sqrt(power.coerceAtLeast(0.0))) / count.toDouble()).coerceIn(0.0, 1.0)
    }

    companion object {
        private const val EPSILON = 0.000001f
        private const val MICROPHONE_NOISE_FLOOR = 0.006f
        private const val MINIMUM_BASS_RATIO = 0.52f
        private const val TWO_PI = 6.283185307179586
        private val BASS_FREQUENCIES = intArrayOf(45, 60, 75, 95, 120, 155)
        private val REFERENCE_FREQUENCIES = intArrayOf(260, 360, 520, 760, 1_100, 1_600)
    }
}

object NativeMusicFrequencyParser {
    private val numberRegex = Regex("-?\\d+")

    fun parse(value: String?): AudioBeatFrame {
        val values =
            numberRegex.findAll(value.orEmpty())
                .mapNotNull { it.value.toIntOrNull() }
                .toList()
        if (values.isEmpty()) {
            return AudioBeatFrame(bassLevel = 0f, beatDetected = false)
        }

        val bass = values.take(BASS_BINS).maxOrNull()?.coerceIn(0, 100) ?: 0
        val level = (bass / 100f).coerceIn(0f, 1f)
        return AudioBeatFrame(bassLevel = level, beatDetected = level >= BEAT_THRESHOLD)
    }

    private const val BASS_BINS = 3
    private const val BEAT_THRESHOLD = 0.10f
}

class MusicVisualizerController(
    private val context: Context,
    private val controller: AmbientLightBleController,
    private val scope: CoroutineScope,
    private val settingsProvider: () -> AmbientLightConfig,
    private val baseColorProvider: () -> LedColor,
    private val detector: AudioBeatDetector = BassAnalyzer()
) {
    private val appContext = context.applicationContext
    private val serviceManager = ServiceManager.getInstance()
    private val microphoneDetector = MicrophoneBassAnalyzer()
    private var visualizer: Visualizer? = null
    private var audioRecord: AudioRecord? = null
    private var pulseJob: Job? = null
    private var microphoneJob: Job? = null
    private var digitalFallbackJob: Job? = null
    private var nativeBeatListener: IDataChanged? = null
    private var previousRhythmicSwitch: String? = null
    private var changedRhythmicSwitch = false
    private var lastBeatElapsedMs = 0L
    private var localCaptureActive = false
    private var lastSignalLogElapsedMs = 0L

    @Volatile
    private var lastDigitalSignalElapsedMs = 0L

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        localCaptureActive = false
        lastSignalLogElapsedMs = 0L
        lastDigitalSignalElapsedMs = 0L
        lastBeatElapsedMs = 0L
        Log.w(TAG, "music sync starting: native bridge plus digital capture")
        startNativeBeatBridge()
        startDigitalCapture()
    }

    private fun startDigitalCapture() {
        if (!hasAudioPermission()) {
            val message = "Permissao RECORD_AUDIO ausente"
            Log.w(TAG, "music visualizer unavailable: $message")
            val nativeActive = nativeBeatListener != null
            controller.updateMusicDebug(
                active = nativeActive,
                bassLevel = 0f,
                error = if (nativeActive) "$message; aguardando sync_music_freq" else message,
                captureSource = if (nativeActive) SOURCE_OEM else SOURCE_NONE
            )
            if (!nativeActive) running = false
            return
        }
        runCatching {
            val captureRange = Visualizer.getCaptureSizeRange()
            val captureSize = captureRange[1].coerceAtMost(DEFAULT_CAPTURE_SIZE).coerceAtLeast(captureRange[0])
            val captureRate = Visualizer.getMaxCaptureRate().coerceAtMost(DEFAULT_CAPTURE_RATE)
            val nextVisualizer =
                Visualizer(0).apply {
                    enabled = false
                    setCaptureSize(captureSize)
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) = Unit

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) {
                                handleFft(fft)
                            }
                        },
                        captureRate,
                        false,
                        true
                    )
                    enabled = true
                }
            visualizer = nextVisualizer
            running = true
            localCaptureActive = true
            controller.updateMusicDebug(active = true, bassLevel = 0f, error = null, captureSource = SOURCE_DIGITAL)
            Log.w(TAG, "music visualizer started captureRate=$captureRate captureSize=$captureSize")
            scheduleMicrophoneFallbackIfDigitalSilent("sem sinal digital util")
        }.onFailure {
            visualizer = null
            localCaptureActive = false
            val message = it.message ?: it::class.java.simpleName
            controller.updateMusicDebug(
                active = nativeBeatListener != null,
                bassLevel = 0f,
                error = message,
                captureSource = if (nativeBeatListener != null) SOURCE_OEM else SOURCE_NONE
            )
            Log.w(TAG, "music visualizer unavailable, trying microphone fallback: $message", it)
            startMicrophoneFallback(message)
        }
    }

    fun stop() {
        if (!running && visualizer == null && audioRecord == null && nativeBeatListener == null) return
        running = false
        localCaptureActive = false
        lastDigitalSignalElapsedMs = 0L
        pulseJob?.cancel()
        pulseJob = null
        digitalFallbackJob?.cancel()
        digitalFallbackJob = null
        stopNativeBeatBridge(restoreSwitch = true)
        microphoneJob?.cancel()
        microphoneJob = null
        runCatching {
            visualizer?.enabled = false
        }
        runCatching { visualizer?.release() }
        visualizer = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        controller.updateMusicDebug(active = false)
        Log.i(TAG, "music visualizer stopped")
    }

    fun isRunning(): Boolean = running

    private fun handleFft(fft: ByteArray?) {
        if (!running || fft == null) return
        val frame = detector.analyzeFft(fft)
        handleBeatFrame(frame, SOURCE_DIGITAL)
    }

    private fun handlePcm(samples: ShortArray, readCount: Int) {
        if (!running) return
        val frame = microphoneDetector.analyzePcm16(samples, readCount)
        handleBeatFrame(frame, SOURCE_MICROPHONE)
    }

    private fun handleNativeMusicFrequency(value: String?) {
        if (!running) return
        val frame = NativeMusicFrequencyParser.parse(value)
        if (localCaptureActive) return
        handleBeatFrame(frame, SOURCE_OEM)
    }

    private fun handleBeatFrame(frame: AudioBeatFrame, source: String) {
        val now = SystemClock.elapsedRealtime()
        if (source == SOURCE_DIGITAL && frame.bassLevel >= DIGITAL_SIGNAL_FLOOR) {
            lastDigitalSignalElapsedMs = now
            stopMicrophoneFallbackIfRunning("sinal digital restaurado")
        }
        val digitalPrimaryActive =
            source == SOURCE_MICROPHONE &&
                lastDigitalSignalElapsedMs > 0L &&
                now - lastDigitalSignalElapsedMs < BACKUP_SOURCE_SUPPRESSION_MS

        logSignalSnapshot(source, frame, digitalPrimaryActive)
        if (digitalPrimaryActive) return

        controller.updateMusicDebug(active = true, bassLevel = frame.bassLevel, captureSource = source)
        if (!frame.beatDetected) return

        if (now - lastBeatElapsedMs < MIN_BEAT_INTERVAL_MS) return
        lastBeatElapsedMs = now
        controller.updateMusicDebug(
            active = true,
            bassLevel = frame.bassLevel,
            lastBeatElapsedMs = now,
            captureSource = source
        )
        Log.w(
            TAG,
            "music beat source=$source bass=${(frame.bassLevel * 100).roundToInt()}% ratio=${(frame.bassRatio * 100).roundToInt()}% raw=${(frame.rawLevel * 100).roundToInt()}%"
        )
        triggerBassPulse(frame)
    }

    private fun startNativeBeatBridge() {
        runCatching {
            if (nativeBeatListener != null) return
            previousRhythmicSwitch = serviceManager.getData(KEY_RHYTHMIC_SWITCH)
            changedRhythmicSwitch = previousRhythmicSwitch != "1"
            if (changedRhythmicSwitch) {
                serviceManager.updateData(KEY_RHYTHMIC_SWITCH, "1")
                Log.i(TAG, "native rhythm switch enabled previous=$previousRhythmicSwitch")
            }

            nativeBeatListener =
                IDataChanged { key, value ->
                    if (key == KEY_SYNC_MUSIC_FREQ) {
                        handleNativeMusicFrequency(value)
                    }
                }.also { serviceManager.addDataChangedListener(it) }
            running = true
            controller.updateMusicDebug(
                active = true,
                bassLevel = 0f,
                error = "Aguardando sync_music_freq da central",
                captureSource = SOURCE_OEM
            )
            Log.w(TAG, "native music frequency bridge started as secondary source, waiting for $KEY_SYNC_MUSIC_FREQ")
        }.onFailure {
            Log.w(TAG, "native music frequency bridge unavailable: ${it.message}", it)
            stopNativeBeatBridge(restoreSwitch = true)
        }
    }

    private fun stopNativeBeatBridge(restoreSwitch: Boolean) {
        nativeBeatListener?.let { serviceManager.removeDataChangedListener(it) }
        nativeBeatListener = null
        if (restoreSwitch && changedRhythmicSwitch) {
            serviceManager.updateData(KEY_RHYTHMIC_SWITCH, previousRhythmicSwitch ?: "0")
            Log.i(TAG, "native rhythm switch restored value=${previousRhythmicSwitch ?: "0"}")
        }
        changedRhythmicSwitch = false
        previousRhythmicSwitch = null
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneFallback(reason: String) {
        if (audioRecord != null) return
        runCatching {
            val minBufferBytes =
                AudioRecord.getMinBufferSize(
                    MICROPHONE_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
            require(minBufferBytes > 0) { "AudioRecord buffer invalido: $minBufferBytes" }
            val bufferBytes = max(minBufferBytes, MICROPHONE_BUFFER_SAMPLES * BYTES_PER_SAMPLE)
            val record =
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    MICROPHONE_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
            require(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord nao inicializou" }

            val samples = ShortArray(MICROPHONE_READ_SAMPLES)
            record.startRecording()
            audioRecord = record
            running = true
            localCaptureActive = true
            controller.updateMusicDebug(
                active = true,
                bassLevel = 0f,
                error = "Fallback microfone: $reason",
                captureSource = SOURCE_MICROPHONE
            )
            Log.w(TAG, "music microphone capture started sampleRate=$MICROPHONE_SAMPLE_RATE bufferBytes=$bufferBytes reason=$reason")
            microphoneJob =
                scope.launch {
                    while (running && audioRecord == record) {
                        val read = record.read(samples, 0, samples.size)
                        if (read > 0) {
                            handlePcm(samples, read)
                        } else {
                            delay(MICROPHONE_READ_RETRY_DELAY_MS)
                        }
                    }
                }
        }.onFailure {
            localCaptureActive = false
            running = nativeBeatListener != null
            runCatching { audioRecord?.release() }
            audioRecord = null
            val message = it.message ?: it::class.java.simpleName
            controller.updateMusicDebug(
                active = nativeBeatListener != null,
                bassLevel = 0f,
                error =
                    if (nativeBeatListener != null) {
                        "Microfone indisponivel: $message; aguardando sync_music_freq"
                    } else {
                        "Microfone indisponivel: $message"
                    },
                captureSource = if (nativeBeatListener != null) SOURCE_OEM else SOURCE_NONE
            )
            Log.w(TAG, "music microphone fallback unavailable: $message", it)
        }
    }

    private fun scheduleMicrophoneFallbackIfDigitalSilent(reason: String) {
        digitalFallbackJob?.cancel()
        digitalFallbackJob =
            scope.launch {
                delay(DIGITAL_SILENCE_FALLBACK_DELAY_MS)
                val now = SystemClock.elapsedRealtime()
                val digitalSignalAgeMs =
                    if (lastDigitalSignalElapsedMs == 0L) {
                        Long.MAX_VALUE
                    } else {
                        now - lastDigitalSignalElapsedMs
                    }
                if (running && visualizer != null && audioRecord == null && digitalSignalAgeMs >= DIGITAL_SILENCE_FALLBACK_DELAY_MS) {
                    Log.w(TAG, "music digital signal absent, enabling microphone fallback reason=$reason")
                    startMicrophoneFallback(reason)
                }
            }
    }

    private fun stopMicrophoneFallbackIfRunning(reason: String) {
        val record = audioRecord ?: return
        microphoneJob?.cancel()
        microphoneJob = null
        runCatching { record.stop() }
        runCatching { record.release() }
        audioRecord = null
        localCaptureActive = visualizer != null
        Log.w(TAG, "music microphone fallback stopped reason=$reason")
    }

    private fun triggerBassPulse(frame: AudioBeatFrame) {
        pulseJob?.cancel()
        pulseJob =
            scope.launch {
                val settings = settingsProvider()
                if (
                    !settings.enabled ||
                    !settings.musicAnimationEnabled ||
                    settings.musicMode != AmbientLightMusicMode.BASS ||
                    !controller.isConnected()
                ) {
                    Log.w(
                        TAG,
                        "music beat skipped enabled=${settings.enabled} music=${settings.musicAnimationEnabled} mode=${settings.musicMode.name} connected=${controller.isConnected()}"
                    )
                    return@launch
                }

                val base = baseColorProvider().coerce()
                val accent = BassPulseColorMapper.accentFor(base, frame.bassLevel)
                controller.setRgb(
                    accent.r,
                    accent.g,
                    accent.b,
                    settings.colorOrder,
                    settings.bleColorOrder,
                    settings.output
                )
                delay(PULSE_RETURN_DELAY_MS)

                val currentSettings = settingsProvider()
                if (
                    !running ||
                    !currentSettings.enabled ||
                    !currentSettings.musicAnimationEnabled ||
                    currentSettings.musicMode != AmbientLightMusicMode.BASS ||
                    !controller.isConnected()
                ) {
                    return@launch
                }
                controller.setRgb(
                    base.r,
                    base.g,
                    base.b,
                    currentSettings.colorOrder,
                    currentSettings.bleColorOrder,
                    currentSettings.output
                )
            }
    }

    private fun logSignalSnapshot(source: String, frame: AudioBeatFrame, backupSuppressed: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSignalLogElapsedMs < SIGNAL_LOG_INTERVAL_MS) return
        lastSignalLogElapsedMs = now
        Log.w(
            TAG,
            "music signal source=$source bass=${(frame.bassLevel * 100).roundToInt()}% ratio=${(frame.bassRatio * 100).roundToInt()}% raw=${(frame.rawLevel * 100).roundToInt()}% beat=${frame.beatDetected} backupSuppressed=$backupSuppressed connected=${controller.isConnected()}"
        )
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "AmbientLight"
        private const val SOURCE_NONE = "Nenhum"
        private const val SOURCE_OEM = "Central"
        private const val SOURCE_DIGITAL = "Digital"
        private const val SOURCE_MICROPHONE = "Microfone"
        private val KEY_RHYTHMIC_SWITCH = CarConstants.CAR_LIGHT_SETTING_AMBIENT_LIGHT_RHYTHMIC_SWITCH.value
        private val KEY_SYNC_MUSIC_FREQ = CarConstants.CAR_LIGHT_SETTING_AMBIENT_LIGHT_SYNC_MUSIC_FREQ.value
        private const val DEFAULT_CAPTURE_SIZE = 512
        private const val DEFAULT_CAPTURE_RATE = 12_000
        private const val MICROPHONE_SAMPLE_RATE = 8_000
        private const val MICROPHONE_BUFFER_SAMPLES = 512
        private const val MICROPHONE_READ_SAMPLES = 512
        private const val BYTES_PER_SAMPLE = 2
        private const val MICROPHONE_READ_RETRY_DELAY_MS = 40L
        private const val DIGITAL_SIGNAL_FLOOR = 0.06f
        private const val DIGITAL_SILENCE_FALLBACK_DELAY_MS = 3_000L
        private const val BACKUP_SOURCE_SUPPRESSION_MS = 1_200L
        private const val MIN_BEAT_INTERVAL_MS = 380L
        private const val PULSE_RETURN_DELAY_MS = 120L
        private const val SIGNAL_LOG_INTERVAL_MS = 1_000L
    }
}

object BassPulseColorMapper {
    private val BASS_ACCENT = LedColor(255, 48, 0)

    fun accentFor(base: LedColor, bassLevel: Float): LedColor {
        val safeBase = base.coerce()
        val intensity = bassLevel.coerceIn(0.35f, 1f)
        val target =
            if (safeBase.r >= 180 && safeBase.g <= 80 && safeBase.b <= 80) {
                AmbientLightProtocol.YELLOW
            } else {
                BASS_ACCENT
            }
        return mix(safeBase, target, 0.45f + (intensity * 0.45f))
    }

    fun mix(from: LedColor, to: LedColor, ratio: Float): LedColor {
        val safeRatio = ratio.coerceIn(0f, 1f)
        return LedColor(
            r = (from.r + ((to.r - from.r) * safeRatio)).roundToInt(),
            g = (from.g + ((to.g - from.g) * safeRatio)).roundToInt(),
            b = (from.b + ((to.b - from.b) * safeRatio)).roundToInt()
        ).coerce()
    }
}
