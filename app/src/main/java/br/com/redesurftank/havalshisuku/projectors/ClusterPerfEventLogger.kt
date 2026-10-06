package br.com.redesurftank.havalshisuku.projectors

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.havalshisuku.BuildConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object ClusterPerfEventLogger {
    private const val TAG = "ClusterPerf"
    private const val MAX_DETAIL_VALUE_LENGTH = 160

    private val sequence = AtomicLong(0L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cpuLock = Any()

    @Volatile private var lastCpuSample: CpuSample? = null

    data class CpuTotals(val totalJiffies: Long, val idleJiffies: Long)

    data class CpuSample(
            val processJiffies: Long,
            val totalJiffies: Long,
            val idleJiffies: Long,
            val elapsedMs: Long
    )

    data class CpuDelta(
            val processCpuPct: Double,
            val systemCpuPct: Double,
            val intervalMs: Long,
            val processJiffiesDelta: Long,
            val totalJiffiesDelta: Long
    )

    fun log(event: String, details: Map<String, Any?> = emptyMap()) {
        if (!BuildConfig.DEBUG) return

        val seq = sequence.incrementAndGet()
        val eventAtMs = SystemClock.elapsedRealtime()
        val safeEvent = sanitizeToken(event)

        scope.launch {
            runCatching { buildSnapshot(seq, eventAtMs, safeEvent, details) }
                    .onSuccess { Log.w(TAG, it) }
                    .onFailure { error ->
                        Log.w(
                                TAG,
                                "[PERF_EVENT] seq=$seq event=$safeEvent error=${sanitizeToken(error.javaClass.simpleName)}"
                        )
                    }
        }
    }

    private fun buildSnapshot(
            seq: Long,
            eventAtMs: Long,
            event: String,
            details: Map<String, Any?>
    ): String {
        val runtime = Runtime.getRuntime()
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)

        val heapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
        val heapTotalKb = runtime.totalMemory() / 1024L
        val heapMaxKb = runtime.maxMemory() / 1024L
        val nativeHeapKb = Debug.getNativeHeapAllocatedSize() / 1024L
        val threadCount = readThreadCount()
        val cpuDelta = readCpuDelta()

        return buildString {
            append("[PERF_EVENT]")
            append(" seq=").append(seq)
            append(" event=").append(event)
            append(" tMs=").append(eventAtMs)
            append(" cpuProcPct=").append(cpuDelta?.processCpuPct.formatPct())
            append(" cpuSystemPct=").append(cpuDelta?.systemCpuPct.formatPct())
            append(" cpuIntervalMs=").append(cpuDelta?.intervalMs ?: -1L)
            append(" procJiffiesDelta=").append(cpuDelta?.processJiffiesDelta ?: -1L)
            append(" totalJiffiesDelta=").append(cpuDelta?.totalJiffiesDelta ?: -1L)
            append(" pssKb=").append(memoryInfo.totalPss)
            append(" dalvikPssKb=").append(memoryInfo.dalvikPss)
            append(" nativePssKb=").append(memoryInfo.nativePss)
            append(" otherPssKb=").append(memoryInfo.otherPss)
            append(" heapUsedKb=").append(heapUsedKb)
            append(" heapTotalKb=").append(heapTotalKb)
            append(" heapMaxKb=").append(heapMaxKb)
            append(" nativeHeapKb=").append(nativeHeapKb)
            append(" threads=").append(threadCount)
            val formattedDetails = formatDetails(details)
            if (formattedDetails.isNotEmpty()) {
                append(" ").append(formattedDetails)
            }
        }
    }

    private fun readCpuDelta(): CpuDelta? {
        val current = readCpuSample() ?: return null
        return synchronized(cpuLock) {
            val previous = lastCpuSample
            lastCpuSample = current
            if (previous == null) {
                null
            } else {
                calculateCpuDelta(previous, current, Runtime.getRuntime().availableProcessors())
            }
        }
    }

    private fun readCpuSample(): CpuSample? {
        val procStat = File("/proc/stat").useLines { lines ->
            lines.firstOrNull { it.startsWith("cpu ") }
        } ?: return null
        val cpuTotals = parseProcStatLine(procStat) ?: return null
        val processStat = File("/proc/self/stat").readText()
        val processJiffies = parseProcessStatLine(processStat) ?: return null
        return CpuSample(
                processJiffies = processJiffies,
                totalJiffies = cpuTotals.totalJiffies,
                idleJiffies = cpuTotals.idleJiffies,
                elapsedMs = SystemClock.elapsedRealtime()
        )
    }

    private fun readThreadCount(): Int {
        return File("/proc/self/task").list()?.size ?: -1
    }

    internal fun parseProcStatLine(line: String): CpuTotals? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.firstOrNull() != "cpu") return null

        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null

        val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
        return CpuTotals(totalJiffies = values.sum(), idleJiffies = idle)
    }

    internal fun parseProcessStatLine(line: String): Long? {
        val endOfCommand = line.lastIndexOf(')')
        if (endOfCommand < 0 || endOfCommand + 1 >= line.length) return null

        val fieldsAfterCommand = line.substring(endOfCommand + 1).trim().split(Regex("\\s+"))
        if (fieldsAfterCommand.size <= 12) return null

        val userTime = fieldsAfterCommand[11].toLongOrNull() ?: return null
        val systemTime = fieldsAfterCommand[12].toLongOrNull() ?: return null
        return userTime + systemTime
    }

    internal fun calculateCpuDelta(
            previous: CpuSample,
            current: CpuSample,
            processorCount: Int
    ): CpuDelta? {
        val totalDelta = current.totalJiffies - previous.totalJiffies
        val idleDelta = current.idleJiffies - previous.idleJiffies
        val processDelta = current.processJiffies - previous.processJiffies
        if (totalDelta <= 0L || idleDelta < 0L || processDelta < 0L) return null

        val safeProcessorCount = processorCount.coerceAtLeast(1)
        val processPct = (processDelta.toDouble() / totalDelta.toDouble()) * safeProcessorCount * 100.0
        val systemPct = ((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100.0
        return CpuDelta(
                processCpuPct = processPct,
                systemCpuPct = systemPct,
                intervalMs = (current.elapsedMs - previous.elapsedMs).coerceAtLeast(0L),
                processJiffiesDelta = processDelta,
                totalJiffiesDelta = totalDelta
        )
    }

    private fun formatDetails(details: Map<String, Any?>): String {
        if (details.isEmpty()) return ""
        return details.toSortedMap().entries.joinToString(separator = " ") { (key, value) ->
            "${sanitizeToken(key)}=${sanitizeValue(value)}"
        }
    }

    private fun sanitizeToken(value: String): String {
        return value
                .trim()
                .ifEmpty { "unknown" }
                .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
    }

    private fun sanitizeValue(value: Any?): String {
        val raw = value?.toString() ?: "null"
        return raw
                .trim()
                .ifEmpty { "empty" }
                .replace(Regex("\\s+"), "_")
                .replace('|', '/')
                .take(MAX_DETAIL_VALUE_LENGTH)
    }

    private fun Double?.formatPct(): String {
        return if (this == null) {
            "n/a"
        } else {
            String.format(Locale.US, "%.1f", this)
        }
    }
}
