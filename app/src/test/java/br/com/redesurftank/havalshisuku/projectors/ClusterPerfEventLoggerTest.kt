package br.com.redesurftank.havalshisuku.projectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClusterPerfEventLoggerTest {
    @Test
    fun parsesSystemCpuTotalsFromProcStatLine() {
        val totals =
                ClusterPerfEventLogger.parseProcStatLine(
                        "cpu  100 20 30 850 10 0 0 0 0 0"
                )

        assertNotNull(totals)
        assertEquals(1010L, totals!!.totalJiffies)
        assertEquals(860L, totals.idleJiffies)
    }

    @Test
    fun parsesProcessUserAndSystemJiffiesFromSelfStatLine() {
        val processJiffies =
                ClusterPerfEventLogger.parseProcessStatLine(
                        "1234 (app process) S 1 2 3 4 5 6 7 8 9 10 40 9 0 0 20 0"
                )

        assertEquals(49L, processJiffies)
    }

    @Test
    fun calculatesCpuDeltaAsPercentOfOneCoreAndSystemUsage() {
        val previous =
                ClusterPerfEventLogger.CpuSample(
                        processJiffies = 100,
                        totalJiffies = 1_000,
                        idleJiffies = 700,
                        elapsedMs = 1_000
                )
        val current =
                ClusterPerfEventLogger.CpuSample(
                        processJiffies = 125,
                        totalJiffies = 1_400,
                        idleJiffies = 900,
                        elapsedMs = 2_000
                )

        val delta = ClusterPerfEventLogger.calculateCpuDelta(previous, current, processorCount = 4)

        assertNotNull(delta)
        assertEquals(25.0, delta!!.processCpuPct, 0.01)
        assertEquals(50.0, delta.systemCpuPct, 0.01)
        assertEquals(1_000L, delta.intervalMs)
    }

    @Test
    fun ignoresInvalidCpuDelta() {
        val previous =
                ClusterPerfEventLogger.CpuSample(
                        processJiffies = 200,
                        totalJiffies = 1_000,
                        idleJiffies = 700,
                        elapsedMs = 1_000
                )
        val current =
                ClusterPerfEventLogger.CpuSample(
                        processJiffies = 150,
                        totalJiffies = 1_200,
                        idleJiffies = 800,
                        elapsedMs = 2_000
                )

        assertNull(ClusterPerfEventLogger.calculateCpuDelta(previous, current, processorCount = 4))
    }
}
