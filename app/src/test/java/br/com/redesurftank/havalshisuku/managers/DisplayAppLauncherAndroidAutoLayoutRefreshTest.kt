package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayAppLauncherAndroidAutoLayoutRefreshTest {
    @Test
    fun shortensFullscreenHeightByOnePixel() {
        val nudged = DisplayAppLauncher.androidAutoUnchangedBoundsRefreshRect(intArrayOf(0, 0, 1920, 720))

        assertArrayEquals(intArrayOf(0, 0, 1920, 719), nudged)
    }

    @Test
    fun keepsHorizontalOffsetWhileShorteningHeight() {
        val nudged = DisplayAppLauncher.androidAutoUnchangedBoundsRefreshRect(intArrayOf(135, 0, 1920, 720))

        assertArrayEquals(intArrayOf(135, 0, 1920, 719), nudged)
    }

    @Test
    fun refusesABoundsRectThatCannotShrink() {
        assertNull(DisplayAppLauncher.androidAutoUnchangedBoundsRefreshRect(intArrayOf(0, 0, 1920, 1)))
        assertNull(DisplayAppLauncher.androidAutoUnchangedBoundsRefreshRect(intArrayOf(0, 0, 1920)))
    }
}
