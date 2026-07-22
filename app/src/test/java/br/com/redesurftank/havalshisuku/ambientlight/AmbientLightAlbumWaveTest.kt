package br.com.redesurftank.havalshisuku.ambientlight

import br.com.redesurftank.havalshisuku.services.AlbumBackgroundColors
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientLightAlbumWaveTest {
    @Test
    fun musicModeDefaultsToBassForExistingUsers() {
        assertEquals(AmbientLightMusicMode.BASS, AmbientLightMusicMode.fromStored(null))
        assertEquals(AmbientLightMusicMode.BASS, AmbientLightMusicMode.fromStored("unknown"))
    }

    @Test
    fun musicModeReadsAlbumWaveFromPreferences() {
        assertEquals(AmbientLightMusicMode.ALBUM_WAVE, AmbientLightMusicMode.fromStored("ALBUM_WAVE"))
        assertEquals(AmbientLightMusicMode.ALBUM_WAVE, AmbientLightMusicMode.fromStored("album_wave"))
    }

    @Test
    fun albumEffectDefaultsToLedLampPrintMode() {
        assertEquals(AmbientLightAlbumEffect.FORWARD_6_BU, AmbientLightAlbumEffect.fromStored(null))
        assertEquals(13, AmbientLightAlbumEffect.FORWARD_6_BU.ledLampModeId)
        assertEquals("ble_mode", AmbientLightAlbumEffect.FORWARD_6_BU.ledLampSource)
    }

    @Test
    fun albumEffectReadsStoredPresetIgnoringCase() {
        assertEquals(
            AmbientLightAlbumEffect.BACKWARD_6_CN,
            AmbientLightAlbumEffect.fromStored("backward_6_cn")
        )
    }

    @Test
    fun albumOutputModeDefaultsToAnimatedAndReadsStoredStatic() {
        assertEquals(AmbientLightAlbumOutputMode.ANIMATED, AmbientLightAlbumOutputMode.fromStored(null))
        assertEquals(AmbientLightAlbumOutputMode.ANIMATED, AmbientLightAlbumOutputMode.fromStored("unknown"))
        assertEquals(AmbientLightAlbumOutputMode.STATIC, AmbientLightAlbumOutputMode.fromStored("static"))
    }

    @Test
    fun albumEffectOutputCanTargetBleAndDmxSeparately() {
        assertTrue(AmbientLightOutput.BLE.includesBle())
        assertTrue(!AmbientLightOutput.BLE.includesDmx())
        assertTrue(AmbientLightOutput.DMX.includesDmx())
        assertTrue(!AmbientLightOutput.DMX.includesBle())
        assertTrue(AmbientLightOutput.BOTH.includesBle())
        assertTrue(AmbientLightOutput.BOTH.includesDmx())
    }

    @Test
    fun pausedMediaWithAlbumContentKeepsAlbumColorActive() {
        val snapshot =
            AlbumWaveMediaSnapshot(
                packageName = "com.spotify.music",
                title = "Track",
                artist = "Artist",
                album = "Album",
                artwork = null,
                isPlaying = false
            )

        assertTrue(snapshot.hasMusic)
        assertTrue(snapshot.hasAlbumContent)
        assertTrue(snapshot.shouldHoldAlbumColor)
        assertTrue(!snapshot.shouldAnimateAlbumWave)
    }

    @Test
    fun packageOnlyPausedMediaDoesNotCountAsActiveAlbum() {
        val snapshot =
            AlbumWaveMediaSnapshot(
                packageName = "com.spotify.music",
                title = null,
                artist = null,
                album = null,
                artwork = null,
                isPlaying = false
            )

        assertTrue(snapshot.hasMusic)
        assertTrue(!snapshot.hasAlbumContent)
        assertTrue(!snapshot.shouldHoldAlbumColor)
        assertTrue(!snapshot.shouldAnimateAlbumWave)
    }

    @Test
    fun albumWavePaletteUsesAlbumBackgroundTones() {
        val colors =
            AlbumBackgroundColors(
                primary = argb(0x22, 0x66, 0xAA),
                secondary = argb(0xAA, 0x44, 0x22),
                accent = argb(0x44, 0xDD, 0xEE),
                dark = argb(0x04, 0x09, 0x14)
            )

        val palette = AlbumWaveColorMapper.paletteFor(colors)

        assertTrue(palette.size >= 3)
        assertTrue(palette.any { it.b > it.r && it.b > it.g })
        assertTrue(palette.any { it.r > it.b })
        assertTrue(palette.all { maxOf(it.r, it.g, it.b) >= 96 })
    }

    @Test
    fun staticAlbumColorUsesVisibleBaseAlbumTone() {
        val palette = listOf(
            LedColor(20, 40, 90),
            LedColor(230, 60, 40)
        )

        val color = AlbumWaveColorMapper.staticAlbumColor(palette)

        assertTrue(color.b > color.r)
        assertTrue(maxOf(color.r, color.g, color.b) >= 72)
    }

    @Test
    fun albumWaveColorInterpolatesInsteadOfJumpingPaletteIndexes() {
        val palette = listOf(
            LedColor(220, 30, 40),
            LedColor(30, 80, 230),
            LedColor(40, 220, 140)
        )

        val colors = (0..10).map { AlbumWaveColorMapper.waveColorAt(palette, it) }

        assertTrue(colors.distinct().size >= 8)
        assertTrue(colors.any { it.r > it.b && it.r > it.g })
        assertTrue(colors.any { it.b > it.r && it.b > it.g })
        assertTrue(colors.zipWithNext().all { (from, to) -> channelDistance(from, to) < 360 })
    }

    @Test
    fun ledLampSixColorPresetUsesAlbumPaletteInBothDirections() {
        val palette = listOf(
            LedColor(220, 30, 40),
            LedColor(30, 80, 230),
            LedColor(40, 220, 140),
            LedColor(210, 190, 30)
        )

        val forward = (0..8).map { AlbumWaveColorMapper.waveColorAt(palette, it, AmbientLightAlbumEffect.FORWARD_6_BU) }
        val backward = (0..8).map { AlbumWaveColorMapper.waveColorAt(palette, it, AmbientLightAlbumEffect.BACKWARD_6_BU) }

        assertTrue(forward.distinct().size >= 5)
        assertTrue(backward.distinct().size >= 5)
        assertTrue(forward != backward)
        assertTrue((forward + backward).all { maxOf(it.r, it.g, it.b) >= 72 })
    }

    @Test
    fun albumEffectSpeedControlsStepDelayLikeLedLampSlider() {
        val slow = AlbumWaveColorMapper.stepDelayMs(1)
        val middle = AlbumWaveColorMapper.stepDelayMs(50)
        val fast = AlbumWaveColorMapper.stepDelayMs(100)

        assertTrue(slow > middle)
        assertTrue(middle > fast)
        assertEquals(slow, AlbumWaveColorMapper.stepDelayMs(-10))
        assertEquals(fast, AlbumWaveColorMapper.stepDelayMs(140))
    }

    @Test
    fun albumWaveColorModulatesBrightnessForVisibleCrest() {
        val palette = listOf(
            LedColor(220, 40, 20),
            LedColor(30, 70, 230),
            LedColor(40, 210, 120)
        )

        val intensities = (0..14).map { AlbumWaveColorMapper.intensity(AlbumWaveColorMapper.waveColorAt(palette, it)) }

        assertTrue((intensities.maxOrNull() ?: 0f) - (intensities.minOrNull() ?: 1f) > 0.12f)
    }

    private fun channelDistance(from: LedColor, to: LedColor): Int {
        return abs(from.r - to.r) + abs(from.g - to.g) + abs(from.b - to.b)
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
