package br.com.redesurftank.havalshisuku.diagnostics

import android.content.Context
import br.com.redesurftank.havalshisuku.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class ProblemReportInput(
        val description: String,
        val carPlayConnected: Boolean,
        val androidAutoConnected: Boolean,
        val mirroredOnD3: Boolean,
        val notMirroredOnD3: Boolean,
        val mapReturnedToNormal: Boolean,
        val acReturnedToMainMenu: Boolean
)

data class ProblemReport(
        val title: String,
        val fullBody: String,
        val generatedAt: String,
        val timeZoneId: String,
        val logFileName: String,
        val logExcerpt: String,
        val logCharCount: Int,
        val input: ProblemReportInput
)

object ProblemReportBuilder {
    private const val LOG_DIRECTORY_NAME = "cluster-diagnostics"
    private const val LOG_FILE_PREFIX = "cluster-events-"
    private const val LOG_FILE_SUFFIX = ".log"
    private const val MAX_PERSISTENT_LOG_CHARS = 1_500_000
    private const val MAX_LOGCAT_CHARS = 20_000
    private const val MAX_LOG_EXCERPT_CHARS = 60_000
    private const val LOGCAT_LINE_LIMIT = 220

    private val logFileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val reportDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
    private val relevantLogcatPattern =
            Regex(
                    "(redesurftank|havalshisuku|InstrumentProjector2|ClusterPersist|ClusterPerf|" +
                            "CARD_FLOW|PERF_EVENT|WEBVIEW_STATE_SYNC|PROJECTION_STATE_PUSH|" +
                            "webview_console|chromium|Console|WebView|AndroidRuntime|FATAL|" +
                            "ANR|OutOfMemoryError|Shizuku|ServiceManager|DisplayAppLauncher|" +
                            "BottomBar|CarPlay|AndroidAuto|UPDATE_SCREEN|STEERING_WHEEL_AC_CONTROL|" +
                            "cluster_card|main_menu|aircon)",
                    RegexOption.IGNORE_CASE
            )

    fun build(context: Context, input: ProblemReportInput, nowMs: Long = System.currentTimeMillis()):
            ProblemReport {
        val generatedAt = reportDateFormat.format(Date(nowMs))
        val timeZoneId = TimeZone.getDefault().id
        val logFile = resolveTodayLogFile(context, nowMs)
        val captureStatus = ClusterPersistentEventLogger.getTodayLogCaptureStatus(context, nowMs)
        val persistentLogText = readLogTail(logFile, MAX_PERSISTENT_LOG_CHARS).orEmpty()
        val logcatText = readLogcatSnapshot()
        val combinedLogs = buildCombinedLogExcerpt(logFile.name, persistentLogText, logcatText)
        val logExcerpt = trimToLastChars(combinedLogs, MAX_LOG_EXCERPT_CHARS)
        val title = buildReportTitle(input.description)
        val fullBody =
                buildFullBody(
                        context = context,
                        input = input,
                        generatedAt = generatedAt,
                        timeZoneId = timeZoneId,
                        logFile = logFile,
                        captureStatus = captureStatus,
                        persistentLogText = persistentLogText,
                        logcatText = logcatText
                )
        return ProblemReport(
                title = title,
                fullBody = fullBody,
                generatedAt = generatedAt,
                timeZoneId = timeZoneId,
                logFileName =
                        if (logcatText.isBlank()) logFile.name else "${logFile.name}; logcat-snapshot",
                logExcerpt = logExcerpt,
                logCharCount = combinedLogs.length,
                input = input.copy(description = input.description.trim())
        )
    }

    private fun buildFullBody(
            context: Context,
            input: ProblemReportInput,
            generatedAt: String,
            timeZoneId: String,
            logFile: File,
            captureStatus: ClusterLogCaptureStatus,
            persistentLogText: String?,
            logcatText: String?
    ): String {
        return buildString {
            appendLine("## Relato do usuario")
            appendLine(input.description.trim())
            appendLine()
            appendLine("> O relato acima deve citar a data, hora e minutos aproximados do incidente.")
            appendLine()
            appendLine("## App")
            appendLine("- Pacote: ${context.packageName}")
            appendLine("- Versao: ${BuildConfig.VERSION_NAME}")
            appendLine("- Version code: ${BuildConfig.VERSION_CODE}")
            appendLine("- Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine("- Debug: ${BuildConfig.DEBUG}")
            appendLine("- Relatorio gerado em: $generatedAt")
            appendLine("- Timezone: $timeZoneId")
            appendLine()
            appendLine("## Log persistente do dia atual")
            appendLine("- Arquivo esperado: ${logFile.absolutePath}")
            appendLine("- Captura persistente ativa: ${yesNo(captureStatus.captureActive)}")
            appendLine("- Build debug/internal: ${yesNo(captureStatus.debugBuild)}")
            appendLine("- Arquivo existe: ${yesNo(captureStatus.fileExists)}")
            appendLine("- Tamanho do arquivo: ${captureStatus.fileSizeBytes} bytes")
            appendLine("- Observacao: o app anexa somente o log persistente do dia atual para manter o envio leve.")
            appendLine()
            if (persistentLogText.isNullOrBlank()) {
                appendLine("Log do dia nao encontrado ou vazio.")
            } else {
                appendLine("```text")
                appendLine(persistentLogText.take(MAX_PERSISTENT_LOG_CHARS))
                appendLine("```")
            }
            appendLine()
            appendLine("## Logcat recente no momento do envio")
            appendLine("- Observacao: snapshot filtrado e limitado; nao substitui o log persistente.")
            appendLine()
            if (logcatText.isNullOrBlank()) {
                appendLine("Logcat recente nao disponivel ou vazio.")
            } else {
                appendLine("```text")
                appendLine(logcatText.take(MAX_LOGCAT_CHARS))
                appendLine("```")
            }
        }
    }

    private fun resolveTodayLogFile(context: Context, nowMs: Long): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(File(baseDir, LOG_DIRECTORY_NAME), buildLogFileName(nowMs))
    }

    private fun readLogTail(file: File, maxChars: Int): String? {
        if (!file.isFile) return null
        return runCatching { trimToLastChars(file.readText(Charsets.UTF_8), maxChars) }.getOrNull()
    }

    private fun readLogcatSnapshot(): String {
        if (!BuildConfig.DEBUG) return ""
        return runCatching {
                    val process =
                            ProcessBuilder("logcat", "-d", "-v", "time", "-t", "320")
                                    .redirectErrorStream(true)
                                    .start()
                    val completed = process.waitFor(2, TimeUnit.SECONDS)
                    if (!completed) {
                        process.destroy()
                        return@runCatching ""
                    }
                    val raw =
                            process.inputStream.bufferedReader(Charsets.UTF_8).use {
                                it.readText()
                            }
                    filterRelevantLogcatSnapshot(raw)
                }
                .getOrDefault("")
    }

    internal fun filterRelevantLogcatSnapshot(rawLogcat: String): String {
        val lines =
                rawLogcat
                        .lineSequence()
                        .map { it.trimEnd() }
                        .filter { it.isNotBlank() && relevantLogcatPattern.containsMatchIn(it) }
                        .toList()
                        .takeLast(LOGCAT_LINE_LIMIT)
        return trimToLastChars(lines.joinToString(separator = "\n"), MAX_LOGCAT_CHARS)
    }

    internal fun buildCombinedLogExcerpt(
            logFileName: String,
            persistentLogText: String,
            logcatText: String
    ): String {
        return buildString {
            appendLine("## Log persistente do dia atual: $logFileName")
            if (persistentLogText.isBlank()) {
                appendLine("Log do dia nao encontrado ou vazio.")
            } else {
                appendLine(persistentLogText)
            }
            appendLine()
            appendLine("## Logcat recente no momento do envio")
            if (logcatText.isBlank()) {
                appendLine("Logcat recente nao disponivel ou vazio.")
            } else {
                appendLine(logcatText)
            }
        }
    }

    internal fun buildReportTitle(description: String): String {
        val clean = description.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return "[Relato] Problema no Impulse"
        return "[Relato] " + clean.take(72)
    }

    internal fun buildLogFileName(nowMs: Long): String {
        return LOG_FILE_PREFIX + logFileDateFormat.format(Date(nowMs)) + LOG_FILE_SUFFIX
    }

    internal fun trimToLastChars(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return "[log truncado; mostrando os ultimos $maxChars caracteres]\n" +
                text.takeLast(maxChars)
    }

    private fun yesNo(value: Boolean): String = if (value) "Sim" else "Nao"
}
