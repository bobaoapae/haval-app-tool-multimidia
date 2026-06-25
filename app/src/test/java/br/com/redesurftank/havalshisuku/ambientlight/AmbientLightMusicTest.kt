package br.com.redesurftank.havalshisuku.ambientlight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
