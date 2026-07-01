package br.com.redesurftank.havalshisuku.diagnostics

import android.content.Context
import br.com.redesurftank.havalshisuku.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPOutputStream

sealed class ProblemReportSubmitResult {
    data class Success(
            val reportId: String,
            val githubIssueUrl: String?,
            val chunked: Boolean = false
    ) : ProblemReportSubmitResult()
    data class HttpError(val code: Int, val message: String) : ProblemReportSubmitResult()
    data class NetworkError(val message: String) : ProblemReportSubmitResult()
    object BackendNotConfigured : ProblemReportSubmitResult()
}

object ProblemReportSubmitter {
    private const val PREFS_NAME = "impulse_problem_reporter"
    private const val INSTALLATION_ID_KEY = "installation_id"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 25_000
    private const val DIRECT_UPLOAD_MAX_BYTES = 800
    private const val CHUNK_TEXT_CHARS = 520
    private const val MAX_CHUNK_COUNT = 4_000
    private const val CHUNK_PAYLOAD_ENCODING = "gzip-base64-json"

    private val gson = Gson()

    fun isConfigured(): Boolean {
        return BuildConfig.IMPULSE_REPORT_SUPABASE_URL.isNotBlank() &&
                BuildConfig.IMPULSE_REPORT_SUPABASE_PUBLISHABLE_KEY.isNotBlank() &&
                BuildConfig.IMPULSE_REPORT_FUNCTION_NAME.isNotBlank()
    }

    fun submit(context: Context, report: ProblemReport): ProblemReportSubmitResult {
        if (!isConfigured()) return ProblemReportSubmitResult.BackendNotConfigured

        val payload = buildPayload(context, report)
        val serializedPayload = gson.toJson(payload)
        if (serializedPayload.toByteArray(Charsets.UTF_8).size <= DIRECT_UPLOAD_MAX_BYTES) {
            return submitReportPayload(payload, chunked = false)
        }

        val installationId =
                payload["installationId"] as? String ?: getOrCreateInstallationId(context)
        return submitChunkedPayload(serializedPayload, installationId)
    }

    private fun submitReportPayload(
            payload: Map<String, Any?>,
            chunked: Boolean
    ): ProblemReportSubmitResult {
        val response = postPayload(payload)
        return when (response) {
            is PostResult.NetworkError -> ProblemReportSubmitResult.NetworkError(response.message)
            is PostResult.Response -> {
                if (response.code in 200..299) {
                    parseSuccess(response.body, chunked)
                } else {
                    ProblemReportSubmitResult.HttpError(
                            response.code,
                            response.body.ifBlank { "Erro HTTP" }
                    )
                }
            }
        }
    }

    private fun submitChunkedPayload(
            serializedPayload: String,
            installationId: String
    ): ProblemReportSubmitResult {
        val encodedPayload = encodePayloadForChunkUpload(serializedPayload)
        val chunks = splitPayloadIntoChunks(encodedPayload)
        if (chunks.size > MAX_CHUNK_COUNT) {
            return ProblemReportSubmitResult.NetworkError(
                    "Relatório excede o limite de envio sem perda (${chunks.size}/$MAX_CHUNK_COUNT)"
            )
        }

        val sessionId = UUID.randomUUID().toString()
        chunks.forEachIndexed { index, chunkText ->
            val isFinalChunk = index == chunks.lastIndex
            val chunkPayload =
                    mapOf(
                            "uploadMode" to "chunk",
                            "payloadEncoding" to CHUNK_PAYLOAD_ENCODING,
                            "sessionId" to sessionId,
                            "installationId" to installationId,
                            "chunkIndex" to index,
                            "chunkCount" to chunks.size,
                            "chunkText" to chunkText,
                            "finalChunk" to isFinalChunk
                    )
            val response = postPayload(chunkPayload)
            when (response) {
                is PostResult.NetworkError ->
                        return ProblemReportSubmitResult.NetworkError(response.message)
                is PostResult.Response -> {
                    if (response.code !in 200..299) {
                        return ProblemReportSubmitResult.HttpError(
                                response.code,
                                response.body.ifBlank { "Erro HTTP" }
                        )
                    }
                    if (isFinalChunk) {
                        return parseSuccess(response.body, chunked = true)
                    }
                }
            }
        }

        return ProblemReportSubmitResult.NetworkError("Envio em partes não retornou confirmação")
    }

    private fun postPayload(payload: Map<String, Any?>): PostResult {
        val bodyBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        var connection: HttpURLConnection? = null
        return try {
            connection = openConnection(bodyBytes.size)
            connection.outputStream.use { output -> output.write(bodyBytes) }

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

            PostResult.Response(code, responseText)
        } catch (error: IOException) {
            PostResult.NetworkError(error.message ?: "Falha de rede")
        } catch (error: Exception) {
            PostResult.NetworkError(error.message ?: "Falha ao enviar relatório")
        } finally {
            connection?.disconnect()
        }
    }

    private fun openConnection(contentLength: Int): HttpURLConnection {
        val connection = URL(endpointUrl()).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.useCaches = false
        connection.setFixedLengthStreamingMode(contentLength)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty(
                "apikey",
                BuildConfig.IMPULSE_REPORT_SUPABASE_PUBLISHABLE_KEY
        )
        connection.setRequestProperty(
                "Authorization",
                "Bearer ${BuildConfig.IMPULSE_REPORT_SUPABASE_PUBLISHABLE_KEY}"
        )
        connection.setRequestProperty(
                "User-Agent",
                "ImpulseProblemReporter/${BuildConfig.VERSION_NAME} Android"
        )
        connection.setRequestProperty("Connection", "close")
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

    private fun parseSuccess(responseText: String, chunked: Boolean): ProblemReportSubmitResult {
        val json = JsonParser.parseString(responseText).asJsonObject
        val reportId = json.get("id")?.asString.orEmpty()
        val githubIssueUrl =
                json.get("githubIssueUrl")?.takeUnless { it.isJsonNull }?.asString
        return ProblemReportSubmitResult.Success(
                reportId = reportId,
                githubIssueUrl = githubIssueUrl,
                chunked = chunked
        )
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
                                "debug" to BuildConfig.DEBUG,
                                "latestPreviewVersionName" to
                                        report.input.latestPreviewVersionName,
                                "currentVersionOutdated" to
                                        report.input.currentVersionOutdated,
                                "latestPreviewCheckFailed" to
                                        report.input.latestPreviewCheckFailed
                        ),
                "generatedAt" to report.generatedAt,
                "timeZone" to report.timeZoneId,
                "reportBody" to report.fullBody,
                "logExcerpt" to report.logExcerpt,
                "logFileName" to report.logFileName,
                "logCharCount" to report.logCharCount
        )
    }

    internal fun splitPayloadIntoChunks(
            serializedPayload: String,
            chunkSize: Int = CHUNK_TEXT_CHARS
    ): List<String> {
        if (serializedPayload.isEmpty()) return listOf("")
        return serializedPayload.chunked(chunkSize)
    }

    internal fun encodePayloadForChunkUpload(serializedPayload: String): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(serializedPayload.toByteArray(Charsets.UTF_8))
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
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

    private sealed class PostResult {
        data class Response(val code: Int, val body: String) : PostResult()
        data class NetworkError(val message: String) : PostResult()
    }
}
