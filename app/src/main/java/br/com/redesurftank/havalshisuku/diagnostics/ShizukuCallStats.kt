package br.com.redesurftank.havalshisuku.diagnostics

import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Counts every command Impulse pushes through Shizuku, grouped by command shape AND
 * calling site, and flushes one summary line per minute to the persistent diagnostics
 * log.
 *
 * Why this exists: on 2026-08-19 the head unit was measured at ~43 process forks/second
 * (`/proc/stat`), while a read of the code only accounted for a handful. Every Shizuku
 * `sh -c "am stack list"` costs three processes on this ROM — `sh -c` -> `sh
 * /system/bin/am` -> `cmd activity` — so the fork counter alone cannot say *who* is
 * responsible. logcat can't answer it either: there is no persistent logd here and the
 * buffer rolls in ~5 minutes, so by the time a symptom is noticed the evidence is gone.
 * This gives a durable, greppable per-caller ranking instead.
 *
 * Cost: one map lookup, two atomic adds, and a shallow stack walk per call — negligible
 * next to forking three processes. Gated on [ClusterPersistentEventLogger
 * .isDiagnosticLoggingAvailable] so it compiles out to an early return in a build with
 * diagnostics off.
 *
 * Read it back with:
 * `grep shizuku_call_stats <files-dir>/cluster-diagnostics/cluster-events-YYYYMMDD.log`
 */
object ShizukuCallStats {

    private const val TAG = "ShizukuCallStats"

    /** One summary line per minute keeps the log greppable; the file is shared with cluster events. */
    private const val FLUSH_INTERVAL_MS = 60_000L

    /** Only the loudest callers are worth a line; the tail is summarised by `distinct`. */
    private const val MAX_ENTRIES_PER_FLUSH = 12

    /** Hard ceiling so a pathological caller (unique arg per call) can't grow the map without bound. */
    private const val MAX_TRACKED_KEYS = 200

    /** Deep stacks are possible; we only need the first frame outside this file. */
    private const val CALLER_FRAME_SCAN_LIMIT = 16

    /**
     * Leading argv tokens worth keeping in a key: lowercase words and dashes only.
     * Deliberately excludes '.' and '/' so package names and paths terminate the key
     * instead of exploding it into one bucket per argument.
     */
    private val PLAIN_TOKEN = Regex("^[a-z][a-z0-9-]*$")

    private val WHITESPACE = Regex("\\s+")

    private class Slot {
        val calls = AtomicInteger()
        val totalMs = AtomicLong()
    }

    private val slots = ConcurrentHashMap<String, Slot>()
    private val flushStarted = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Records one Shizuku exec. [elapsedMs] is wall time for the synchronous portion, so
     * for the fire-and-forget variant it measures spawn cost only — hence [async], which
     * keeps those in their own bucket rather than skewing the blocking averages.
     */
    @JvmStatic
    @JvmOverloads
    fun record(command: Array<String>?, elapsedMs: Long, async: Boolean = false) {
        if (!ClusterPersistentEventLogger.isDiagnosticLoggingAvailable()) return
        if (command == null || command.isEmpty()) return

        ensureFlushLoop()

        val key = buildKey(command, async)
        val slot =
                slots[key]
                        ?: if (slots.size >= MAX_TRACKED_KEYS) {
                            return
                        } else {
                            slots.computeIfAbsent(key) { Slot() }
                        }
        slot.calls.incrementAndGet()
        slot.totalMs.addAndGet(if (elapsedMs > 0L) elapsedMs else 0L)
    }

    private fun ensureFlushLoop() {
        if (!flushStarted.compareAndSet(false, true)) return
        scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                runCatching { flush() }
                        .onFailure { Log.w(TAG, "Falha ao publicar estatisticas do Shizuku", it) }
            }
        }
    }

    /**
     * Drains the window and emits one event. Counters are reset with getAndSet rather
     * than by clearing the map, so a concurrent call in flight is at worst attributed to
     * the next window instead of being dropped.
     */
    private fun flush() {
        var totalCalls = 0
        var totalMs = 0L
        val rows = ArrayList<Triple<String, Int, Long>>(slots.size)

        for ((key, slot) in slots) {
            val calls = slot.calls.getAndSet(0)
            if (calls == 0) continue
            val ms = slot.totalMs.getAndSet(0L)
            totalCalls += calls
            totalMs += ms
            rows.add(Triple(key, calls, ms))
        }
        if (rows.isEmpty()) return

        rows.sortByDescending { it.second }

        val details = LinkedHashMap<String, Any?>()
        details["windowMs"] = FLUSH_INTERVAL_MS
        details["calls"] = totalCalls
        details["callsPerSec"] =
                String.format(Locale.US, "%.2f", totalCalls * 1000.0 / FLUSH_INTERVAL_MS)
        details["busyMs"] = totalMs
        details["distinct"] = rows.size

        rows.take(MAX_ENTRIES_PER_FLUSH).forEachIndexed { index, (key, calls, ms) ->
            details[String.format(Locale.US, "c%02d", index + 1)] = "$key|n=$calls|ms=$ms"
        }

        ClusterPersistentEventLogger.log("shizuku_call_stats", details)
    }

    private fun buildKey(command: Array<String>, async: Boolean): String {
        val shape = normalizeCommand(command)
        val suffix = if (async) "~async" else ""
        return "$shape$suffix@${callerTag()}"
    }

    /**
     * Collapses an argv into a stable bucket: `["sh","-c","am stack list 2>&1"]` and
     * `["sh","-c","am stack resize 12 0 0 1920 720"]` become `am_stack_list` and
     * `am_stack_resize`. The `sh -c` wrapper is unwrapped because otherwise almost
     * everything in this codebase lands in one bucket.
     */
    internal fun normalizeCommand(command: Array<String>): String {
        val tokens: List<String> =
                if (command.size >= 3 && command[1] == "-c" && command[0].endsWith("sh")) {
                    command[2].trim().split(WHITESPACE)
                } else {
                    command.toList()
                }

        val kept = ArrayList<String>(3)
        for (token in tokens) {
            if (token.isEmpty()) continue
            if (isShellBreak(token)) break
            if (kept.isEmpty()) {
                // Keep the basename so /system/bin/am and am share a bucket.
                kept.add(token.substringAfterLast('/'))
                continue
            }
            if (kept.size >= 3) break
            if (!PLAIN_TOKEN.matches(token)) break
            kept.add(token)
        }
        return if (kept.isEmpty()) "unknown" else kept.joinToString("_")
    }

    /** Stops key building at the first redirection or pipeline token. */
    private fun isShellBreak(token: String): Boolean {
        if (token == "&&" || token == "||" || token == ";") return true
        val first = token[0]
        return first == '|' || first == '>' || first == '<' || token.startsWith("2>")
    }

    /** First frame outside this object and ShizukuUtils, e.g. `DisplayAppLauncher.getStackList:8546`. */
    private fun callerTag(): String {
        val frames = Throwable().stackTrace
        val limit = minOf(frames.size, CALLER_FRAME_SCAN_LIMIT)
        for (index in 0 until limit) {
            val frame = frames[index]
            val className = frame.className
            if (className.contains("ShizukuCallStats")) continue
            if (className.endsWith("ShizukuUtils")) continue
            val simple = className.substringAfterLast('.').substringBefore('$')
            return "$simple.${frame.methodName}:${frame.lineNumber}"
        }
        return "unknown"
    }
}
