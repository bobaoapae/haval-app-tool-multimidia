package br.com.redesurftank.havalshisuku.diagnostics

import android.content.Context
import br.com.redesurftank.havalshisuku.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed class ProblemReportSubmitResult {
    data class Success(val reportId: String, val githubIssueUrl: String?) : ProblemReportSubmitResult()
    data class HttpError(val code: Int, val message: String) : ProblemReportSubmitResult()
    data class NetworkError(val message: String) : ProblemReportSubmitResult()
    object BackendNotConfigured : ProblemReportSubmitResult()
}

object ProblemReportSubmitter {
    private const val PREFS_NAME = "impulse_problem_reporter"
    private const val INSTALLATION_ID_KEY = "installation_id"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 25_000

    private val gson = Gson()

    fun isConfigured(): Boolean {
        return BuildConfig.IMPULSE_REPORT_SUPABASE_URL.isNotBlank() &&
                BuildConfig.IMPULSE_REPORT_SUPABASE_PUBLISHABLE_KEY.isNotBlank() &&
                BuildConfig.IMPULSE_REPORT_FUNCTION_NAME.isNotBlank()
    }

    fun submit(context: Context, report: ProblemReport): ProblemReportSubmitResult {
        if (!isConfigured()) return ProblemReportSubmitResult.BackendNotConfigured

        val payload = buildPayload(context, report)
        val body = gson.toJson(payload)
        return try {
            val connection = openConnection()
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val responseText =
                    runCatching {
                                val stream =
                                        if (code in 200..299) connection.inputStream
                                        else connection.errorStream
                                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                                        .orEmpty()
                            }
                            .getOrDefault("")

            if (code in 200..299) {
                parseSuccess(responseText)
            } else {
                ProblemReportSubmitResult.HttpError(code, responseText.ifBlank { "Erro HTTP" })
            }
        } catch (error: IOException) {
            ProblemReportSubmitResult.NetworkError(error.message ?: "Falha de rede")
        } catch (error: Exception) {
            ProblemReportSubmitResult.NetworkError(error.message ?: "Falha ao enviar relatório")
        }
    }

    private fun openConnection(): HttpURLConnection {
        val connection = URL(endpointUrl()).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty(
                "apikey",
                BuildConfig.IMPULSE_REPORT_SUPABASE_PUBLISHABLE_KEY
        )
        connection.setRequestProperty(
                "X-Impulse-Version",
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
        return connection
    }

    private fun endpointUrl(): String {
        val baseUrl = BuildConfig.IMPULSE_REPORT_SUPABASE_URL.trimEnd('/')
        val functionName = BuildConfig.IMPULSE_REPORT_FUNCTION_NAME.trim('/')
        return "$baseUrl/functions/v1/$functionName"
    }

    private fun parseSuccess(responseText: String): ProblemReportSubmitResult {
        val json = JsonParser.parseString(responseText).asJsonObject
        val reportId = json.get("id")?.asString.orEmpty()
        val githubIssueUrl =
                json.get("githubIssueUrl")?.takeUnless { it.isJsonNull }?.asString
        return ProblemReportSubmitResult.Success(reportId = reportId, githubIssueUrl = githubIssueUrl)
    }

    private fun buildPayload(context: Context, report: ProblemReport): Map<String, Any?> {
        return mapOf(
                "installationId" to getOrCreateInstallationId(context),
                "title" to report.title,
                "description" to report.input.description,
                "context" to
                        mapOf(
                                "carPlayConnected" to report.input.carPlayConnected,
                                "androidAutoConnected" to report.input.androidAutoConnected,
                                "mirroredOnD3" to report.input.mirroredOnD3,
                                "notMirroredOnD3" to report.input.notMirroredOnD3,
                                "mapReturnedToNormal" to report.input.mapReturnedToNormal,
                                "acReturnedToMainMenu" to report.input.acReturnedToMainMenu
                        ),
                "app" to
                        mapOf(
                                "packageName" to context.packageName,
                                "versionName" to BuildConfig.VERSION_NAME,
                                "versionCode" to BuildConfig.VERSION_CODE,
                                "buildType" to BuildConfig.BUILD_TYPE,
                                "debug" to BuildConfig.DEBUG
                        ),
                "generatedAt" to report.generatedAt,
                "timeZone" to report.timeZoneId,
                "reportBody" to report.fullBody,
                "logExcerpt" to report.logExcerpt,
                "logFileName" to report.logFileName,
                "logCharCount" to report.logCharCount
        )
    }

    private fun getOrCreateInstallationId(context: Context): String {
        val prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(INSTALLATION_ID_KEY, null)
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(INSTALLATION_ID_KEY, generated).apply()
        return generated
    }
}
