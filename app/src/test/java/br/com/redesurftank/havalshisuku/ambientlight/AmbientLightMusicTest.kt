package br.com.redesurftank.havalshisuku.ambientlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AmbientLightMusicTest {
    @Test
    fun bassAnalyzerIgnoresSilentFft() {
        val frame = BassAnalyzer().analyzeFft(ByteArray(64))

        assertFalse(frame.beatDetected)
        assertTrue(frame.bassLevel == 0f)
    }

    @Test
    fun bassAnalyzerDetectsLowFrequencyBeat() {
        val fft = ByteArray(64)
        repeat(6) { bin ->
            val index = 2 + (bin * 2)
            fft[index] = 100
            fft[index + 1] = 80
        }

        val frame = BassAnalyzer().analyzeFft(fft)

        assertTrue(frame.beatDetected)
        assertTrue(frame.bassLevel > 0.38f)
    }

    @Test
    fun bassAnalyzerIgnoresModerateLowFrequencyEnergy() {
        val fft = ByteArray(64)
        repeat(6) { bin ->
            val index = 2 + (bin * 2)
            fft[index] = 50
            fft[index + 1] = 40
        }

        val frame = BassAnalyzer().analyzeFft(fft)

        assertFalse(frame.beatDetected)
        assertTrue(frame.bassLevel < 0.36f)
    }

    @Test
    fun bassAnalyzerDoesNotRepeatSteadyLowFrequencyBeat() {
        val analyzer = BassAnalyzer()
        val fft = ByteArray(64)
        repeat(6) { bin ->
            val index = 2 + (bin * 2)
            fft[index] = 100
            fft[index + 1] = 80
        }

        assertTrue(analyzer.analyzeFft(fft).beatDetected)
        assertFalse(analyzer.analyzeFft(fft).beatDetected)
    }

    @Test
    fun nativeMusicFrequencyParserUsesLowFrequencyBins() {
        val frame = NativeMusicFrequencyParser.parse("{4,64,0,3,56,5,4}")

        assertTrue(frame.beatDetected)
        assertTrue(frame.bassLevel >= 0.64f)
    }

    @Test
    fun nativeMusicFrequencyParserIgnoresStopFlag() {
        val frame = NativeMusicFrequencyParser.parse("{0,0,0,0,0,0,0}")

        assertFalse(frame.beatDetected)
        assertTrue(frame.bassLevel == 0f)
    }

    @Test
    fun microphoneBassAnalyzerIgnoresQuietCabinNoise() {
        val samples = ShortArray(512) { 80 }

        val frame = MicrophoneBassAnalyzer().analyzePcm16(samples, samples.size)

        assertFalse(frame.beatDetected)
        assertTrue(frame.bassLevel < 0.25f)
    }

    @Test
    fun microphoneBassAnalyzerDetectsAudiblePulse() {
        val samples = sineSamples(frequencyHz = 75.0, amplitude = 4200)

        val frame = MicrophoneBassAnalyzer().analyzePcm16(samples, samples.size)

        assertTrue(frame.beatDetected)
        assertTrue(frame.bassLevel > 0.6f)
        assertTrue(frame.bassRatio > 0.52f)
    }

    @Test
    fun microphoneBassAnalyzerIgnoresLoudMidrangePulse() {
        val samples = sineSamples(frequencyHz = 800.0, amplitude = 6000)

        val frame = MicrophoneBassAnalyzer().analyzePcm16(samples, samples.size)

        assertFalse(frame.beatDetected)
        assertTrue(frame.bassRatio < 0.52f)
    }

    @Test
    fun microphoneBassAnalyzerDoesNotTreatSteadyVolumeAsRepeatedBeat() {
        val analyzer = MicrophoneBassAnalyzer()
        val samples = sineSamples(frequencyHz = 75.0, amplitude = 4200)

        analyzer.analyzePcm16(samples, samples.size)
        val frame = analyzer.analyzePcm16(samples, samples.size)

        assertFalse(frame.beatDetected)
        assertTrue(frame.bassLevel > 0.6f)
    }

    @Test
    fun microphoneBassAnalyzerRearmsAfterVolumeDrops() {
        val analyzer = MicrophoneBassAnalyzer()
        val pulse = sineSamples(frequencyHz = 75.0, amplitude = 4200)
        val quiet = ShortArray(512) { 80 }

        assertTrue(analyzer.analyzePcm16(pulse, pulse.size).beatDetected)
        assertFalse(analyzer.analyzePcm16(pulse, pulse.size).beatDetected)
        assertFalse(analyzer.analyzePcm16(quiet, quiet.size).beatDetected)
        assertTrue(analyzer.analyzePcm16(pulse, pulse.size).beatDetected)
    }

    @Test
    fun bassPulseColorMapperMovesBlueBaseTowardWarmAccent() {
        val base = AmbientLightProtocol.SOFT_BLUE

        val accent = BassPulseColorMapper.accentFor(base, 1f)

        assertTrue(accent.r > base.r)
        assertTrue(accent.b < base.b)
    }

    @Test
    fun bassPulseColorMapperKeepsRedBaseVisibleWithYellowAccent() {
        val accent = BassPulseColorMapper.accentFor(AmbientLightProtocol.RED, 1f)

        assertEquals(255, accent.r)
        assertTrue(accent.g > 100)
        assertEquals(0, accent.b)
    }

    @Test
    fun bassPulseColorMapperScalesPulseWithBassLevel() {
        val base = AmbientLightProtocol.SOFT_BLUE

        val low = BassPulseColorMapper.accentFor(base, 0.35f)
        val high = BassPulseColorMapper.accentFor(base, 1f)

        assertTrue(high.r > low.r)
        assertTrue(high.b < low.b)
    }

    private fun sineSamples(
        frequencyHz: Double,
        amplitude: Int,
        sampleRate: Int = 8_000,
        size: Int = 512
    ): ShortArray =
        ShortArray(size) { index ->
            (sin((2.0 * PI * frequencyHz * index.toDouble()) / sampleRate.toDouble()) * amplitude)
                .toInt()
                .toShort()
        }
}
