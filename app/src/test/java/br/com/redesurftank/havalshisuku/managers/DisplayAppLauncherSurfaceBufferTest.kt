package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAppLauncherSurfaceBufferTest {
    @Test
    fun parsesStaleOneByOneActiveBuffer() {
        val buffer = DisplayAppLauncher.parseCarPlaySurfaceActiveBufferForTest(
            """
            + Layer 0xb4e9d400 (SurfaceView - com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity#0)
                activeBuffer=[   1x   1:  64,RGBx_8888]
            """.trimIndent()
        )

        assertEquals(1 to 1, buffer)
        assertTrue(DisplayAppLauncher.isCarPlaySurfaceBufferStaleForTest(buffer))
    }

    @Test
    fun parsesHealthyClusterActiveBuffer() {
        val buffer = DisplayAppLauncher.parseCarPlaySurfaceActiveBufferForTest(
            """
            + Layer 0xb4e9d400 (SurfaceView - com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity#0)
                activeBuffer=[1920x 720:1920,Unknown 0x7fa30c06]
            """.trimIndent()
        )

        assertEquals(1920 to 720, buffer)
        assertFalse(DisplayAppLauncher.isCarPlaySurfaceBufferStaleForTest(buffer))
    }

    @Test
    fun parsesNativeScaledClusterActiveBufferAsHealthy() {
        val buffer = DisplayAppLauncher.parseCarPlaySurfaceActiveBufferForTest(
            "activeBuffer=[1904x 704:1920,Unknown 0x7fa30c06]"
        )

        assertEquals(1904 to 704, buffer)
        assertFalse(DisplayAppLauncher.isCarPlaySurfaceBufferStaleForTest(buffer))
    }

    @Test
    fun ignoresOverlayActiveBufferBeforeRealCarPlaySurfaceView() {
        val dump = """
            [SurfaceView - com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity#0] 423.25 0.000 1.000 0.000
            Visible layers (count = 104)
            + BufferLayer (Display Overlays#4)
                  activeBuffer=[   0x   0:   0,Unknown/None], queued-frames=0
            --
            + BufferLayer (SurfaceView - com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity#0)
                  layerStack=   3, z=       -2, pos=(0,0), size=(1904, 704)
                  activeBuffer=[1904x 704:1920,Unknown 0x7fa30c06], queued-frames=0
            + ColorLayer (Background for -SurfaceView - com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity#0)
                  activeBuffer=[   0x   0:   0,Unknown/None], queued-frames=0
        """.trimIndent()

        val firstBufferInRawDump = DisplayAppLauncher.parseCarPlaySurfaceActiveBufferForTest(dump)
        val carPlayBuffer = DisplayAppLauncher.parseCarPlaySurfaceViewActiveBufferForTest(dump)

        assertEquals(0 to 0, firstBufferInRawDump)
        assertTrue(DisplayAppLauncher.isCarPlaySurfaceBufferStaleForTest(firstBufferInRawDump))
        assertEquals(1904 to 704, carPlayBuffer)
        assertFalse(DisplayAppLauncher.isCarPlaySurfaceBufferStaleForTest(carPlayBuffer))
    }

    @Test
    fun missingActiveBufferIsUnknownNotStale() {
        val buffer = DisplayAppLauncher.parseCarPlaySurfaceActiveBufferForTest(
            "SurfaceView - com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity#0"
        )

        assertNull(buffer)
        assertFalse(DisplayAppLauncher.isCarPlaySurfaceBufferStaleForTest(buffer))
    }
}
