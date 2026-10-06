package br.com.redesurftank.havalshisuku.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApkInstallReturnTest {

    @Before
    fun clearWait() {
        ApkInstallReturn.resetForTest()
    }

    @Test
    fun onlyTheArmedPackageReturnsOnceBeforeTheDeadline() {
        ApkInstallReturn.remember("com.havalh6.viewer", now = 0L)
        assertFalse(ApkInstallReturn.shouldReturn("com.other.app", now = 500L))
        assertTrue(ApkInstallReturn.shouldReturn("com.havalh6.viewer", now = ApkInstallReturn.TIMEOUT_MS))
        assertFalse(ApkInstallReturn.shouldReturn("com.havalh6.viewer", now = ApkInstallReturn.TIMEOUT_MS))
    }

    @Test
    fun aLateEventDropsTheWait() {
        ApkInstallReturn.remember("com.havalh6.viewer", now = 0L)
        assertFalse(ApkInstallReturn.shouldReturn("com.havalh6.viewer", now = ApkInstallReturn.TIMEOUT_MS + 1))
        assertFalse(ApkInstallReturn.shouldReturn("com.havalh6.viewer", now = ApkInstallReturn.TIMEOUT_MS + 2))
    }

    @Test
    fun shizukuCommandTargetsMainActivity() {
        val args = ApkInstallReturn.shizukuArgs("br.com.redesurftank.havalshisuku")
        assertEquals("am", args[0])
        assertEquals("start", args[1])
        assertEquals(
                "br.com.redesurftank.havalshisuku/br.com.redesurftank.havalshisuku.MainActivity",
                args[3]
        )
        assertTrue(ApkInstallReturn.succeeded("Starting: Intent { cmp=br.com.redesurftank.havalshisuku/.MainActivity }"))
        assertFalse(ApkInstallReturn.succeeded(""))
        assertFalse(ApkInstallReturn.succeeded("Permission Denial: not exported"))
    }

    @Test
    fun shizukuFailureFallsBackToStartActivity() {
        var started = false
        val ok =
                ApkInstallReturn.bringToFront(
                        "br.com.redesurftank.havalshisuku",
                        shizukuReady = { true },
                        run = { "Error: Activity not started" },
                        startActivity = { started = true }
                )
        assertTrue(ok)
        assertTrue(started)
    }

    @Test
    fun shizukuSuccessDoesNotStartTwice() {
        var started = false
        val ok =
                ApkInstallReturn.bringToFront(
                        "br.com.redesurftank.havalshisuku",
                        shizukuReady = { true },
                        run = { "Starting: Intent" },
                        startActivity = { started = true }
                )
        assertTrue(ok)
        assertFalse(started)
    }

}
