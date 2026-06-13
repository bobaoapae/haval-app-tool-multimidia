package br.com.redesurftank.havalshisuku.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CarPlayLoopLoggerService : Service() {
    private data class TaskSnapshot(
        val stackId: Int,
        val taskId: Int,
        val displayId: Int,
        val packageName: String,
        val activityName: String,
        val bounds: String
    )

    private data class Sample(
        val wallTime: String,
        val elapsedMs: Long,
        val visualState: String,
        val desiredPref: Int,
        val desiredProp: String,
        val usbSummary: String,
        val hostPid: String,
        val appPid: String,
        val tasks: List<TaskSnapshot>
    )

    private data class Transition(
        val atMs: Long,
        val from: String,
        val to: String
    )

    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var sessionDir: File? = null
    private var startedAtMs: Long = 0L
    private var maxDurationMs: Long = DEFAULT_DURATION_MS
    private var sampleIntervalMs: Long = DEFAULT_INTERVAL_MS
    private var running = false
    private var lastVisualState: String? = null
    private var lastLightEvidenceAt = 0L
    private var lastFullEvidenceAt = 0L
    private var lastCleanupAt = 0L
    private var fullEvidenceCount = 0
    private val transitions = ArrayDeque<Transition>()
    private val recentSamples = ArrayDeque<String>()
    private val sampleRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                collectSample()
            } catch (e: Exception) {
                Log.e(TAG, "Sample failed: ${e.message}", e)
                appendLine("errors.log", "${wallTime()} sample failed: ${e.message}")
            }

            val elapsed = SystemClock.elapsedRealtime() - startedAtMs
            if (elapsed >= maxDurationMs) {
                appendLine("events.log", "${wallTime()} auto-stop elapsedMs=$elapsed")
                stopLogger("duration-reached")
                return
            }

            worker?.postDelayed(this, sampleIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        workerThread = HandlerThread("CarPlayLoopLogger")
        workerThread?.start()
        worker = Handler(workerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP, ACTION_STOP_LEGACY -> {
                worker?.post {
                    cleanupOldSessions(force = true)
                    stopLogger("stop-action")
                }
            }
            ACTION_DUMP, ACTION_DUMP_LEGACY -> {
                worker?.post {
                    cleanupOldSessions(force = true)
                    captureEvidence("manual-dump", includeScreens = true)
                }
            }
            else -> {
                val duration = readRequestedDurationMs(intent)
                val interval = intent?.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS)
                    ?: DEFAULT_INTERVAL_MS
                worker?.post { startLogger(duration, interval) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker?.removeCallbacksAndMessages(null)
        running = false
        workerThread?.quitSafely()
        workerThread = null
        worker = null
        super.onDestroy()
    }

    private fun startLogger(durationMs: Long, intervalMs: Long) {
        if (running) {
            cleanupOldSessions(force = true)
            appendLine("events.log", "${wallTime()} start ignored because logger is already running")
            return
        }

        maxDurationMs = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
        sampleIntervalMs = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        startedAtMs = SystemClock.elapsedRealtime()
        lastVisualState = null
        transitions.clear()
        recentSamples.clear()
        lastLightEvidenceAt = 0L
        lastFullEvidenceAt = 0L
        lastCleanupAt = 0L
        fullEvidenceCount = 0
        sessionDir = createSessionDir()
        cleanupOldSessions(force = true)
        running = true

        startForeground(NOTIFICATION_ID, buildNotification())
        writeSessionReadme()
        appendLine(
            "events.log",
            "${wallTime()} start durationMs=$maxDurationMs intervalMs=$sampleIntervalMs dir=${sessionDir?.absolutePath}"
        )
        captureEvidence("start", includeScreens = true)
        worker?.post(sampleRunnable)
    }

    private fun stopLogger(reason: String) {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        worker?.removeCallbacks(sampleRunnable)
        captureEvidence("stop-$reason", includeScreens = false)
        appendLine("events.log", "${wallTime()} stop reason=$reason")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun collectSample() {
        cleanupOldSessions(force = false)
        val stackList = runShell("am stack list")
        val tasks = parseCarPlayTasks(stackList)
        val visualState = classifyVisualState(tasks)
        val desiredPref = readDesiredCarPlayDisplayPref()
        val desiredProp = runShell("getprop persist.haval.carplay.desired_display").trim()
        val usbSummary = summarizeUsb(runShell("dumpsys usb | head -80"))
        val hostPid = runShell("pidof com.ts.carplay").trim().ifEmpty { "-" }
        val appPid = runShell("pidof com.ts.carplay.app").trim().ifEmpty { "-" }
        val sample = Sample(
            wallTime = wallTime(),
            elapsedMs = SystemClock.elapsedRealtime() - startedAtMs,
            visualState = visualState,
            desiredPref = desiredPref,
            desiredProp = desiredProp.ifEmpty { "-" },
            usbSummary = usbSummary,
            hostPid = hostPid,
            appPid = appPid,
            tasks = tasks
        )
        val line = formatSample(sample)
        appendLine("state.log", line)
        recentSamples.addLast(line)
        while (recentSamples.size > MAX_RECENT_SAMPLES) recentSamples.removeFirst()

        val previous = lastVisualState
        if (previous == null) {
            lastVisualState = visualState
            appendLine("events.log", "${wallTime()} initial visual=$visualState")
            return
        }

        if (previous != visualState) {
            lastVisualState = visualState
            recordTransition(previous, visualState)
        }
    }

    private fun recordTransition(from: String, to: String) {
        val now = SystemClock.elapsedRealtime()
        transitions.addLast(Transition(now, from, to))
        while (transitions.isNotEmpty() && now - transitions.first().atMs > LOOP_WINDOW_MS) {
            transitions.removeFirst()
        }

        appendLine(
            "events.log",
            "${wallTime()} transition $from->$to transitionsInWindow=${transitions.size}"
        )

        if (now - lastLightEvidenceAt > LIGHT_EVIDENCE_COOLDOWN_MS) {
            lastLightEvidenceAt = now
            captureEvidence("transition-${sanitizeFilePart(from)}-to-${sanitizeFilePart(to)}", includeScreens = false)
        }

        if (isLikelyLoop() && now - lastFullEvidenceAt > FULL_EVIDENCE_COOLDOWN_MS) {
            lastFullEvidenceAt = now
            captureEvidence("loop-detected", includeScreens = true)
        }
    }

    private fun isLikelyLoop(): Boolean {
        if (transitions.size < LOOP_MIN_TRANSITIONS) return false
        val states = transitions.flatMap { listOf(it.from, it.to) }.filter { it == "D0" || it == "D3" }
        if (!states.contains("D0") || !states.contains("D3")) return false

        var alternating = 0
        for (transition in transitions) {
            if ((transition.from == "D0" && transition.to == "D3") ||
                (transition.from == "D3" && transition.to == "D0")) {
                alternating++
            }
        }
        return alternating >= LOOP_MIN_TRANSITIONS
    }

    private fun captureEvidence(reason: String, includeScreens: Boolean) {
        val dir = sessionDir ?: return
        val stamp = fileTime()
        val safeReason = sanitizeFilePart(reason)
        val prefix = "$stamp-$safeReason"
        appendLine("events.log", "${wallTime()} capture reason=$reason screens=$includeScreens")

        writeText(File(dir, "$prefix-stack.txt"), runShell("am stack list"))
        writeText(File(dir, "$prefix-usb.txt"), runShell("dumpsys usb | head -120"))
        writeText(
            File(dir, "$prefix-window-carplay.txt"),
            runShell("dumpsys window windows | grep -A28 -B6 'com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity' || true")
        )
        writeText(
            File(dir, "$prefix-window-impulse.txt"),
            runShell("dumpsys window windows | grep -A24 -B4 'br.com.redesurftank.havalshisuku' || true")
        )
        writeText(
            File(dir, "$prefix-surface-carplay.txt"),
            runShell("dumpsys SurfaceFlinger | grep -A18 'SurfaceView - com.ts.carplay.app' || true")
        )
        writeText(
            File(dir, "$prefix-logcat.txt"),
            runShell(
                "logcat -d -v time -t 900 | grep -Ei " +
                    "'CarPlay|carplay|cpScreen|NdkMediaCodec|MediaCodec|DisplayAppLauncher|InstrumentProjector2|ActivityManager|ActivityTaskManager|Usb|USB|move-stack|WATCHDOG|CarPlayReconnect' || true"
            )
        )
        writeText(File(dir, "$prefix-recent-state.log"), recentSamples.joinToString(separator = "\n"))

        if (includeScreens && fullEvidenceCount < MAX_FULL_EVIDENCE_CAPTURES) {
            fullEvidenceCount++
            runShell("screencap -d 0 ${shellQuote(File(dir, "$prefix-d0.raw").absolutePath)}")
            runShell("screencap -d 4 ${shellQuote(File(dir, "$prefix-d4.raw").absolutePath)}")
        }
    }

    private fun parseCarPlayTasks(stackList: String): List<TaskSnapshot> {
        val result = mutableListOf<TaskSnapshot>()
        var currentStackId: Int? = null
        var currentDisplayId: Int? = null
        val stackRegex = Regex("""Stack id=(\d+).*displayId=(\d+)""")
        val taskRegex = Regex("""taskId=(\d+):\s*([^/\s]+)/([^\s]+).*bounds=([^\s]+)""")

        for (line in stackList.lines()) {
            val stackMatch = stackRegex.find(line)
            if (stackMatch != null) {
                currentStackId = stackMatch.groupValues[1].toIntOrNull()
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull()
                continue
            }

            val taskMatch = taskRegex.find(line) ?: continue
            val packageName = taskMatch.groupValues[2]
            if (packageName != CARPLAY_PACKAGE) continue
            val stackId = currentStackId ?: continue
            val displayId = currentDisplayId ?: continue
            result.add(
                TaskSnapshot(
                    stackId = stackId,
                    taskId = taskMatch.groupValues[1].toIntOrNull() ?: -1,
                    displayId = displayId,
                    packageName = packageName,
                    activityName = taskMatch.groupValues[3],
                    bounds = taskMatch.groupValues[4]
                )
            )
        }
        return result
    }

    private fun classifyVisualState(tasks: List<TaskSnapshot>): String {
        val onD0 = tasks.any { it.displayId == 0 }
        val onD3 = tasks.any { it.displayId == 3 }
        return when {
            onD0 && onD3 -> "D0+D3"
            onD0 -> "D0"
            onD3 -> "D3"
            tasks.isNotEmpty() -> tasks.joinToString(prefix = "D", separator = "+D") { it.displayId.toString() }
            else -> "NONE"
        }
    }

    private fun formatSample(sample: Sample): String {
        val taskSummary = if (sample.tasks.isEmpty()) {
            "none"
        } else {
            sample.tasks.joinToString(separator = ";") {
                "d=${it.displayId},s=${it.stackId},t=${it.taskId},b=${it.bounds}"
            }
        }
        return "${sample.wallTime} elapsed=${sample.elapsedMs} visual=${sample.visualState} " +
            "desiredPref=${sample.desiredPref} desiredProp=${sample.desiredProp} " +
            "usb=\"${sample.usbSummary}\" hostPid=${sample.hostPid} appPid=${sample.appPid} tasks=\"$taskSummary\""
    }

    private fun summarizeUsb(output: String): String {
        val interesting = listOf("connected=", "configured=", "host_connected=", "kernel_state=")
        return output.lines()
            .map { it.trim().trimEnd(',') }
            .filter { line -> interesting.any { line.startsWith(it) } }
            .joinToString(separator = " ")
            .ifEmpty { "unknown" }
    }

    private fun readDesiredCarPlayDisplayPref(): Int {
        return try {
            App.getDeviceProtectedContext()
                .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
                .getInt(PREF_DESIRED_CARPLAY_DISPLAY_ID, -1)
        } catch (e: Exception) {
            -1
        }
    }

    private fun runShell(command: String): String {
        if (!ShizukuUtils.isShizukuAvailable()) {
            return "Shizuku unavailable"
        }
        return ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "$command 2>&1"))
    }

    private fun createSessionDir(): File {
        val root = getExternalFilesDir("carplay-loop")
            ?: File(filesDir, "carplay-loop")
        val dir = File(root, "session-${fileTime()}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cleanupOldSessions(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCleanupAt < CLEANUP_INTERVAL_MS) return
        lastCleanupAt = now

        val roots = listOfNotNull(
            getExternalFilesDir("carplay-loop"),
            File(filesDir, "carplay-loop").takeIf { it.exists() }
        ).distinctBy { it.absolutePath }
        val cutoff = now - LOG_RETENTION_MS

        for (root in roots) {
            val sessions = root.listFiles { file -> file.isDirectory && file.name.startsWith("session-") }
                ?: continue
            for (session in sessions) {
                if (session == sessionDir) continue
                val sessionTime = parseSessionTimeMs(session) ?: session.lastModified()
                if (sessionTime >= cutoff) continue

                val deleted = session.deleteRecursively()
                appendLine(
                    "events.log",
                    "${wallTime()} cleanup oldSession=${session.name} ageMs=${now - sessionTime} deleted=$deleted"
                )
                if (!deleted) {
                    Log.w(TAG, "Failed to delete old logger session ${session.absolutePath}")
                }
            }
        }
    }

    private fun parseSessionTimeMs(session: File): Long? {
        val value = session.name.removePrefix("session-")
        return try {
            FILE_TIME_FORMAT.get()?.parse(value)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun readRequestedDurationMs(intent: Intent?): Long {
        val explicitMs = intent?.getLongExtra(EXTRA_DURATION_MS, Long.MIN_VALUE)
            ?: Long.MIN_VALUE
        if (explicitMs != Long.MIN_VALUE) return explicitMs

        val explicitMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        return if (explicitMinutes != Int.MIN_VALUE) {
            TimeUnit.MINUTES.toMillis(explicitMinutes.toLong())
        } else {
            DEFAULT_DURATION_MS
        }
    }

    private fun writeSessionReadme() {
        val dir = sessionDir ?: return
        writeText(
            File(dir, "README.txt"),
            """
            CarPlay loop logger session.

            Retention:
            - sessions older than 3 days are deleted on start/dump/stop and periodically while sampling
            - current session is never deleted by cleanup

            RAW screenshots are Android screencap raw files:
            - first 16 bytes: little-endian width, height, format, reserved
            - payload: RGBA

            Convert D4/cluster:
            ffmpeg -hide_banner -loglevel error -y -skip_initial_bytes 16 -f rawvideo -pixel_format rgba -video_size 1920x720 -i <file>-d4.raw -frames:v 1 <file>-d4.jpg

            Start:
            am startservice -n ${packageName}/.services.CarPlayLoopLoggerService -a $ACTION_START --el $EXTRA_DURATION_MS 604800000 --el $EXTRA_INTERVAL_MS 1500

            Stop:
            am startservice -n ${packageName}/.services.CarPlayLoopLoggerService -a $ACTION_STOP

            Manual dump:
            am startservice -n ${packageName}/.services.CarPlayLoopLoggerService -a $ACTION_DUMP
            """.trimIndent()
        )
    }

    private fun appendLine(name: String, line: String) {
        try {
            val dir = sessionDir ?: return
            File(dir, name).appendText(line + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append $name: ${e.message}", e)
        }
    }

    private fun writeText(file: File, text: String) {
        try {
            file.writeText(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${file.name}: ${e.message}", e)
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun sanitizeFilePart(value: String): String {
        return value.lowercase(Locale.US).replace(Regex("""[^a-z0-9._-]+"""), "-").trim('-')
            .ifEmpty { "event" }
    }

    private fun buildNotification(): Notification {
        val dir = sessionDir?.absolutePath ?: "aguardando sessao"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CarPlay loop logger ativo")
            .setContentText(dir.takeLast(64))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CarPlay Loop Logger",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun wallTime(): String = WALL_TIME_FORMAT.get()!!.format(Date())

    private fun fileTime(): String = FILE_TIME_FORMAT.get()!!.format(Date())

    companion object {
        private const val TAG = "CarPlayLoopLogger"
        private const val CHANNEL_ID = "CarPlayLoopLoggerChannel"
        private const val NOTIFICATION_ID = 43
        private const val CARPLAY_PACKAGE = "com.ts.carplay.app"
        private const val PREF_DESIRED_CARPLAY_DISPLAY_ID = "desiredCarPlayDisplayId"
        const val ACTION_START = "br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_START"
        const val ACTION_STOP = "br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_STOP"
        const val ACTION_DUMP = "br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_DUMP"
        private const val ACTION_STOP_LEGACY = "br.com.redesurftank.havalshisuku.CARPLAY_LOOP_LOGGER_STOP"
        private const val ACTION_DUMP_LEGACY = "br.com.redesurftank.havalshisuku.CARPLAY_LOOP_LOGGER_DUMP"
        const val EXTRA_DURATION_MS = "durationMs"
        const val EXTRA_DURATION_MINUTES = "durationMinutes"
        const val EXTRA_INTERVAL_MS = "intervalMs"
        private val DEFAULT_DURATION_MS = TimeUnit.DAYS.toMillis(7)
        private const val MIN_DURATION_MS = 30 * 1000L
        private val MAX_DURATION_MS = TimeUnit.DAYS.toMillis(7)
        private const val DEFAULT_INTERVAL_MS = 1_500L
        private const val MIN_INTERVAL_MS = 1_000L
        private const val MAX_INTERVAL_MS = 10_000L
        private val LOG_RETENTION_MS = TimeUnit.DAYS.toMillis(3)
        private val CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10)
        private const val LOOP_WINDOW_MS = 45_000L
        private const val LOOP_MIN_TRANSITIONS = 4
        private const val LIGHT_EVIDENCE_COOLDOWN_MS = 5_000L
        private const val FULL_EVIDENCE_COOLDOWN_MS = 15_000L
        private const val MAX_FULL_EVIDENCE_CAPTURES = 8
        private const val MAX_RECENT_SAMPLES = 180
        private val WALL_TIME_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        }
        private val FILE_TIME_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        }
    }
}
