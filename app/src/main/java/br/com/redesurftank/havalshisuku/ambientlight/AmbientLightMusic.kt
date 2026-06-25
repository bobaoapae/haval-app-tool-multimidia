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
    val timestampMs: Long = System.currentTimeMillis()
)

interface AudioBeatDetector {
    fun analyzeFft(fft: ByteArray): AudioBeatFrame
}

class BassAnalyzer(
    private val beatThreshold: Float = 0.38f
) : AudioBeatDetector {
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
        return AudioBeatFrame(bassLevel = bassLevel, beatDetected = bassLevel >= beatThreshold)
    }

    companion object {
        private const val MIN_FFT_BYTES = 4
        private const val BASS_BIN_COUNT = 10
        private const val MAX_FFT_MAGNITUDE = 128.0
    }
}

class MicrophoneBassAnalyzer(
    private val beatThreshold: Float = 0.045f,
    private val beatBoost: Float = 1.35f
) {
    private var baseline = 0.015f

    fun analyzePcm16(samples: ShortArray, readCount: Int): AudioBeatFrame {
        if (readCount <= 0) {
            return AudioBeatFrame(bassLevel = 0f, beatDetected = false)
        }

        var sumSquares = 0.0
        repeat(readCount.coerceAtMost(samples.size)) { index ->
            val normalized = samples[index].toDouble() / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }

        val rms = sqrt(sumSquares / readCount.toDouble()).toFloat().coerceIn(0f, 1f)
        baseline = ((baseline * 0.92f) + (rms * 0.08f)).coerceIn(0.008f, 0.6f)
        val relativeLevel = (rms / (baseline * 2.2f)).coerceIn(0f, 1f)
        val beatDetected = rms >= beatThreshold || (rms >= baseline * beatBoost && relativeLevel >= 0.32f)
        return AudioBeatFrame(bassLevel = relativeLevel, beatDetected = beatDetected)
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
    private var nativeFallbackJob: Job? = null
    private var nativeBeatListener: IDataChanged? = null
    private var previousRhythmicSwitch: String? = null
    private var changedRhythmicSwitch = false
    private var nativeBeatReceived = false
    private var lastBeatElapsedMs = 0L

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        if (startNativeBeatBridge()) return
        startDigitalCapture()
    }

    private fun startDigitalCapture() {
        if (!hasAudioPermission()) {
            val message = "Permissao RECORD_AUDIO ausente"
            Log.w(TAG, "music visualizer unavailable: $message")
            controller.updateMusicDebug(active = false, bassLevel = 0f, error = message)
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
            controller.updateMusicDebug(active = true, bassLevel = 0f, error = null, captureSource = SOURCE_DIGITAL)
            Log.i(TAG, "music visualizer started captureRate=$captureRate captureSize=$captureSize")
        }.onFailure {
            running = false
            visualizer = null
            val message = it.message ?: it::class.java.simpleName
            controller.updateMusicDebug(active = false, bassLevel = 0f, error = message)
            Log.w(TAG, "music visualizer unavailable, trying microphone fallback: $message", it)
            startMicrophoneFallback(message)
        }
    }

    fun stop() {
        if (!running && visualizer == null && audioRecord == null) return
        running = false
        pulseJob?.cancel()
        pulseJob = null
        nativeFallbackJob?.cancel()
        nativeFallbackJob = null
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
        nativeBeatReceived = true
        nativeFallbackJob?.cancel()
        nativeFallbackJob = null
        val frame = NativeMusicFrequencyParser.parse(value)
        handleBeatFrame(frame, SOURCE_OEM)
    }

    private fun handleBeatFrame(frame: AudioBeatFrame, source: String) {
        controller.updateMusicDebug(active = true, bassLevel = frame.bassLevel, captureSource = source)
        if (!frame.beatDetected) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastBeatElapsedMs < MIN_BEAT_INTERVAL_MS) return
        lastBeatElapsedMs = now
        controller.updateMusicDebug(
            active = true,
            bassLevel = frame.bassLevel,
            lastBeatElapsedMs = now,
            captureSource = source
        )
        Log.i(TAG, "music beat source=$source bass=${(frame.bassLevel * 100).roundToInt()}%")
        triggerBassPulse(frame)
    }

    private fun startNativeBeatBridge(): Boolean =
        runCatching {
            previousRhythmicSwitch = serviceManager.getData(KEY_RHYTHMIC_SWITCH)
            changedRhythmicSwitch = previousRhythmicSwitch != "1"
            if (changedRhythmicSwitch) {
                serviceManager.updateData(KEY_RHYTHMIC_SWITCH, "1")
                Log.i(TAG, "native rhythm switch enabled previous=$previousRhythmicSwitch")
            }

            nativeBeatReceived = false
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
            Log.i(TAG, "native music frequency bridge started, waiting for $KEY_SYNC_MUSIC_FREQ")
            nativeFallbackJob?.cancel()
            nativeFallbackJob =
                scope.launch {
                    delay(NATIVE_BRIDGE_FALLBACK_DELAY_MS)
                    if (!running || nativeBeatListener == null || nativeBeatReceived) return@launch

                    Log.w(TAG, "native music frequency unavailable, falling back to local capture")
                    controller.updateMusicDebug(
                        active = true,
                        bassLevel = 0f,
                        error = "Central sem sync_music_freq; usando fallback local",
                        captureSource = SOURCE_OEM
                    )
                    nativeFallbackJob = null
                    stopNativeBeatBridge(restoreSwitch = true)
                    running = false
                    startDigitalCapture()
                }
            true
        }.getOrElse {
            Log.w(TAG, "native music frequency bridge unavailable: ${it.message}", it)
            stopNativeBeatBridge(restoreSwitch = true)
            false
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
        nativeBeatReceived = false
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneFallback(reason: String) {
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

            val samples = ShortArray(bufferBytes / BYTES_PER_SAMPLE)
            record.startRecording()
            audioRecord = record
            running = true
            controller.updateMusicDebug(
                active = true,
                bassLevel = 0f,
                error = "Fallback microfone: $reason",
                captureSource = SOURCE_MICROPHONE
            )
            Log.i(TAG, "music microphone fallback started sampleRate=$MICROPHONE_SAMPLE_RATE bufferBytes=$bufferBytes")
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
            running = false
            runCatching { audioRecord?.release() }
            audioRecord = null
            val message = it.message ?: it::class.java.simpleName
            controller.updateMusicDebug(active = false, bassLevel = 0f, error = "Microfone indisponivel: $message")
            Log.w(TAG, "music microphone fallback unavailable: $message", it)
        }
    }

    private fun triggerBassPulse(frame: AudioBeatFrame) {
        pulseJob?.cancel()
        pulseJob =
            scope.launch {
                val settings = settingsProvider()
                if (!settings.enabled || !settings.musicAnimationEnabled || !controller.isConnected()) return@launch

                val base = baseColorProvider().coerce()
                val accent = bassAccentFor(base, frame.bassLevel)
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
                if (!running || !currentSettings.enabled || !currentSettings.musicAnimationEnabled || !controller.isConnected()) {
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

    private fun bassAccentFor(base: LedColor, bassLevel: Float): LedColor {
        val intensity = bassLevel.coerceIn(0.35f, 1f)
        val target =
            if (base == AmbientLightProtocol.WHITE) {
                AmbientLightProtocol.SOFT_BLUE
            } else {
                AmbientLightProtocol.WHITE
            }
        return mix(base, target, 0.35f + (intensity * 0.45f))
    }

    private fun mix(from: LedColor, to: LedColor, ratio: Float): LedColor {
        val safeRatio = ratio.coerceIn(0f, 1f)
        return LedColor(
            r = (from.r + ((to.r - from.r) * safeRatio)).roundToInt(),
            g = (from.g + ((to.g - from.g) * safeRatio)).roundToInt(),
            b = (from.b + ((to.b - from.b) * safeRatio)).roundToInt()
        ).coerce()
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "AmbientLight"
        private const val SOURCE_OEM = "Central"
        private const val SOURCE_DIGITAL = "Digital"
        private const val SOURCE_MICROPHONE = "Microfone"
        private val KEY_RHYTHMIC_SWITCH = CarConstants.CAR_LIGHT_SETTING_AMBIENT_LIGHT_RHYTHMIC_SWITCH.value
        private val KEY_SYNC_MUSIC_FREQ = CarConstants.CAR_LIGHT_SETTING_AMBIENT_LIGHT_SYNC_MUSIC_FREQ.value
        private const val DEFAULT_CAPTURE_SIZE = 512
        private const val DEFAULT_CAPTURE_RATE = 12_000
        private const val MICROPHONE_SAMPLE_RATE = 8_000
        private const val MICROPHONE_BUFFER_SAMPLES = 1024
        private const val BYTES_PER_SAMPLE = 2
        private const val MICROPHONE_READ_RETRY_DELAY_MS = 40L
        private const val NATIVE_BRIDGE_FALLBACK_DELAY_MS = 3_000L
        private const val MIN_BEAT_INTERVAL_MS = 220L
        private const val PULSE_RETURN_DELAY_MS = 80L
    }
}
