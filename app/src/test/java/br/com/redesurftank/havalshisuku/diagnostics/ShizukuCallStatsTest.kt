package br.com.redesurftank.havalshisuku.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The counter is only useful if calls that differ solely by argument land in the same
 * bucket — otherwise every resize gets its own row and the ranking is noise. These are
 * the real argv shapes used by DisplayAppLauncher, BottomBarService and ServiceManager.
 */
class ShizukuCallStatsTest {

    private fun key(vararg command: String) =
            ShizukuCallStats.normalizeCommand(arrayOf(*command))

    @Test
    fun unwrapsShellWrapperAndDropsRedirection() {
        assertEquals("am_stack_list", key("sh", "-c", "am stack list 2>&1"))
    }

    @Test
    fun resizesWithDifferentBoundsShareOneBucket() {
        val a = key("sh", "-c", "am stack resize 12 0 0 1920 720")
        val b = key("sh", "-c", "am stack resize 7 575 217 1711 493")
        assertEquals("am_stack_resize", a)
        assertEquals(a, b)
    }

    @Test
    fun packageArgumentsDoNotExplodeTheKey() {
        val a = key("sh", "-c", "am force-stop com.ts.androidauto.app")
        val b = key("sh", "-c", "am force-stop deezer.android.app")
        assertEquals("am_force-stop", a)
        assertEquals(a, b)
    }

    @Test
    fun stopsAtFlagsSoLaunchesAggregate() {
        val a = key("sh", "-c", "am start -n com.foo/.Main --display 3 --windowingMode 5")
        val b = key("sh", "-c", "am start -n other.pkg/.Act --display 0")
        assertEquals("am_start", a)
        assertEquals(a, b)
    }

    @Test
    fun stopsAtPipeline() {
        assertEquals(
                "dumpsys_audio",
                key("sh", "-c", "dumpsys audio 2>/dev/null | grep -i 'USAGE_AAUTO_MEDIA' || true")
        )
    }

    @Test
    fun keepsThreeLevelsOfSubcommand() {
        assertEquals(
                "settings_put_secure",
                key("sh", "-c", "settings put secure enabled_notification_listeners 'x'")
        )
    }

    @Test
    fun bareArgvIsHandledWithoutShellWrapper() {
        assertEquals("wm_overscan", key("wm", "overscan", "0,0,0,39"))
        assertEquals("pm_list_packages", key("pm", "list", "packages", "com.termux"))
    }

    @Test
    fun absolutePathCollapsesToBasename() {
        assertEquals("am_stack_list", key("sh", "-c", "/system/bin/am stack list"))
    }

    @Test
    fun emptyOrBlankCommandDoesNotCrash() {
        assertEquals("unknown", key("sh", "-c", "   "))
    }
}
