package br.com.redesurftank.havalshisuku.diagnostics

import android.content.Context
import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class ClusterLogCaptureStatus(
        val diagnosticLoggingAvailable: Boolean,
        val enabled: Boolean,
        val directoryPath: String,
        val filePath: String,
        val fileName: String,
        val fileExists: Boolean,
        val fileSizeBytes: Long,
        val lastModifiedMs: Long
) {
    val captureActive: Boolean
        get() = diagnosticLoggingAvailable && enabled
}

object ClusterPersistentEventLogger {
    private const val TAG = "ClusterPersist"
    private const val PREFS_NAME = "cluster_persistent_event_logger"
    private const val CAPTURE_ENABLED_KEY = "capture_enabled"
    private const val DIRECTORY_NAME = "cluster-diagnostics"
    private const val FILE_PREFIX = "cluster-events-"
    private const val FILE_SUFFIX = ".log"
    private const val RETENTION_DAYS = 3
    private const val CLEANUP_INTERVAL_MS = 60_000L
    private const val MAX_DETAIL_VALUE_LENGTH = 600

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileLock = Any()
    private val lineTimestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    @Volatile private var lastCleanupAtMs = 0L

    @JvmStatic
    fun log(event: String) {
        log(event, emptyMap<String, Any?>())
    }

    @JvmStatic
    fun logText(event: String, message: String) {
        log(event, mapOf("message" to message))
    }

    @JvmStatic
    fun log(event: String, details: Map<String, *>) {
        if (!isDiagnosticLoggingAvailable()) return

        val context =
                runCatching { App.getDeviceProtectedContext() }
                        .getOrNull()
                        ?: return
        if (!isPersistentCaptureEnabled(context)) return

        val nowMs = System.currentTimeMillis()
        val elapsedMs = SystemClock.elapsedRealtime()
        val safeEvent = sanitizeToken(event)
        val safeDetails = formatDetails(details)

        scope.launch {
            runCatching {
                        val dir = resolveLogDir(context)
                        synchronized(fileLock) {
                            cleanupExpiredLogsIfNeeded(dir, nowMs)
                            val file = File(dir, buildFileName(nowMs))
                            file.appendText(
                                    buildLine(nowMs, elapsedMs, safeEvent, safeDetails) + "\n",
                                    Charsets.UTF_8
                            )
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Failed to persist cluster event $safeEvent: ${error.message}")
                    }
        }
    }

    @JvmStatic
    fun getLogDirectoryPath(): String? {
        val context =
                runCatching { App.getDeviceProtectedContext() }
                        .getOrNull()
                        ?: return null
        return resolveLogDir(context, create = false).absolutePath
    }

    @JvmStatic
    fun isPersistentCaptureEnabled(context: Context): Boolean {
        if (!isDiagnosticLoggingAvailable()) return false
        return runCatching {
                    deviceProtectedContext(context)
                            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .getBoolean(CAPTURE_ENABLED_KEY, true)
                }
                .getOrDefault(false)
    }

    @JvmStatic
    fun isDiagnosticLoggingAvailable(): Boolean {
        return BuildConfig.DEBUG || BuildConfig.IMPULSE_REPORT_DIAGNOSTICS_ENABLED
    }

    @JvmStatic
    fun setPersistentCaptureEnabled(context: Context, enabled: Boolean) {
        deviceProtectedContext(context)
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(CAPTURE_ENABLED_KEY, enabled)
                .apply()
    }

    @JvmStatic
    fun getTodayLogCaptureStatus(
            context: Context,
            nowMs: Long = System.currentTimeMillis()
    ): ClusterLogCaptureStatus {
        val dir = resolveLogDir(context, create = false)
        val fileName = buildFileName(nowMs)
        val file = File(dir, fileName)
        return ClusterLogCaptureStatus(
                diagnosticLoggingAvailable = isDiagnosticLoggingAvailable(),
                enabled = isPersistentCaptureEnabled(context),
                directoryPath = dir.absolutePath,
                filePath = file.absolutePath,
                fileName = fileName,
                fileExists = file.isFile,
                fileSizeBytes = if (file.isFile) file.length() else 0L,
                lastModifiedMs = if (file.isFile) file.lastModified() else 0L
        )
    }

    private fun resolveLogDir(context: Context, create: Boolean = true): File {
        val safeContext = deviceProtectedContext(context)
        val baseDir = safeContext.getExternalFilesDir(null) ?: safeContext.filesDir
        return File(baseDir, DIRECTORY_NAME).apply {
            if (create && !exists()) {
                mkdirs()
            }
        }
    }

    private fun deviceProtectedContext(context: Context): Context {
        return context.createDeviceProtectedStorageContext()
    }

    private fun buildLine(
            nowMs: Long,
            elapsedMs: Long,
            event: String,
            details: String
    ): String {
        return buildString {
            append(lineTimestampFormat.format(Date(nowMs)))
            append(" epochMs=").append(nowMs)
            append(" elapsedMs=").append(elapsedMs)
            append(" event=").append(event)
            if (details.isNotEmpty()) {
                append(" ").append(details)
            }
        }
    }

    private fun buildFileName(nowMs: Long): String {
        return FILE_PREFIX + fileDateFormat.format(Date(nowMs)) + FILE_SUFFIX
    }

    private fun cleanupExpiredLogsIfNeeded(dir: File, nowMs: Long) {
        if (nowMs - lastCleanupAtMs < CLEANUP_INTERVAL_MS) return
        lastCleanupAtMs = nowMs

        dir.listFiles()?.forEach { file ->
            if (shouldDeleteLogFile(file.name, nowMs)) {
                runCatching { file.delete() }
            }
        }
    }

    internal fun shouldDeleteLogFile(fileName: String, nowMs: Long): Boolean {
        val fileStartMs = parseLogFileStartMs(fileName) ?: return false
        return fileStartMs < retentionCutoffStartMs(nowMs)
    }

    internal fun retentionCutoffStartMs(nowMs: Long): Long {
        val calendar =
                Calendar.getInstance(Locale.US).apply {
                    timeInMillis = nowMs
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -(RETENTION_DAYS - 1))
                }
        return calendar.timeInMillis
    }

    private fun parseLogFileStartMs(fileName: String): Long? {
        if (!fileName.startsWith(FILE_PREFIX) || !fileName.endsWith(FILE_SUFFIX)) return null
        val datePart = fileName.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX)
        if (!Regex("""\d{8}""").matches(datePart)) return null
        return runCatching {
                    val parsed = fileDateFormat.parse(datePart) ?: return null
                    Calendar.getInstance(Locale.US).apply {
                        time = parsed
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                .getOrNull()
    }

    private fun formatDetails(details: Map<String, *>): String {
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
}
