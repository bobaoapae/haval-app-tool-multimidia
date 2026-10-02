package br.com.redesurftank.havalshisuku.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuCommandStatsTest {

    @Test
    fun `commandHead uses the sh -c script and collapses digits`() {
        assertEquals(
            "dumpsys activity activities",
            ShizukuCommandStats.commandHead(
                arrayOf("sh", "-c", "dumpsys activity activities | sed -n '/Display #3/,/x/p'")
            )
        )
        assertEquals("am stack list", ShizukuCommandStats.commandHead(arrayOf("sh", "-c", "am stack list 2>&1")))
        assertEquals("wm overscan #,#,#,#", ShizukuCommandStats.commandHead(arrayOf("wm", "overscan", "0,0,0,72")))
        assertEquals("<empty>", ShizukuCommandStats.commandHead(arrayOf()))
    }

    @Test
    fun `callerOf skips helper and foreign frames and keeps three app frames`() {
        val stack = arrayOf(
            StackTraceElement("java.lang.Thread", "getStackTrace", "Thread.java", 1),
            StackTraceElement("br.com.x.utils.ShizukuUtils", "recordStats", "ShizukuUtils.java", 10),
            StackTraceElement("br.com.x.utils.ShizukuUtils", "runCommandAndGetOutput", "ShizukuUtils.java", 20),
            StackTraceElement("br.com.x.managers.DisplayAppLauncher", "getStackList", "D.kt", 8778),
            StackTraceElement("kotlin.Result", "x", "R.kt", 1),
            StackTraceElement("br.com.x.managers.DisplayAppLauncher", "hasAppsOnSecondaryDisplays", "D.kt", 8234),
            StackTraceElement("br.com.x.ui.components.BottomBarUIKt\$Bar\$1", "invokeSuspend", "B.kt", 2914),
            StackTraceElement("br.com.x.Other", "deeper", "O.kt", 5),
        )
        assertEquals(
            "DisplayAppLauncher.getStackList:8778 < DisplayAppLauncher.hasAppsOnSecondaryDisplays:8234" +
                " < BottomBarUIKt\$Bar\$1.invokeSuspend:2914",
            ShizukuCommandStats.callerOf(stack, "br.com.x")
        )
        assertEquals("<unknown>", ShizukuCommandStats.callerOf(arrayOf(), "br.com.x"))
    }

    @Test
    fun `report is emitted by the first command after the window and resets counts`() {
        val stats = ShizukuCommandStats()
        assertNull(stats.record("a", 10, false, 0))
        assertNull(stats.record("a", 30, false, 1_000))
        assertNull(stats.record("b", -1, true, 59_999))

        val report = stats.record("b", 5, false, 60_000)
        assertNotNull(report)
        report!!
        assertTrue(report, report.startsWith("window=60s total=3 rate=3.0/min failures=1 keys=2"))
        // "a" sorted first (2 commands), with avg over timed samples.
        val lines = report.lines()
        assertTrue(lines[1], lines[1].contains("2 (2.0/min) avg=20ms max=30ms fail=0  a"))
        assertTrue(lines[2], lines[2].contains("1 (1.0/min) avg=-1ms max=0ms fail=1  b"))

        // The command that closed the window opens the next one.
        val next = stats.record("c", 1, false, 120_000)!!
        assertTrue(next, next.startsWith("window=60s total=1 "))
        assertTrue(next, next.contains("  b"))
    }

    @Test
    fun `distinct keys are capped`() {
        val stats = ShizukuCommandStats()
        for (i in 0 until ShizukuCommandStats.MAX_KEYS + 50) stats.record("k$i", 1, false, 0)
        val report = stats.buildReport(60_000, 0)
        assertTrue(report, report.contains("keys=${ShizukuCommandStats.MAX_KEYS + 1}"))
        assertTrue(report, report.contains("total=${ShizukuCommandStats.MAX_KEYS + 50}"))
    }
}
