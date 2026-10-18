package br.com.redesurftank.havalshisuku.radar

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * Fetches each source and returns parsed cameras. Plain java.net with no Android types, so the
 * same code builds the APK seed on a PC (RadarSeedGenerator test) and runs on the car, where
 * [tls] carries TLS 1.3 (null = the platform's own).
 */
class RadarDownloader(private val userAgent: String, private val tls: SSLSocketFactory? = null) {
    class Result<T>(val value: T?, val error: String?)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        /** Overpass spends most of a minute searching before it sends a byte. */
        const val READ_TIMEOUT_MS = 300_000
    }

    /** All Brazil from the first Overpass server that answers with plausible data. */
    fun fetchOsm(): Result<List<RadarCamera>> {
        val errors = ArrayList<String>()
        for (url in RadarSources.OVERPASS_URLS) {
            try {
                val body = "data=" + URLEncoder.encode(RadarSources.OVERPASS_QUERY, "UTF-8")
                val json = request(url, body, Charsets.UTF_8)
                val cameras = RadarSources.parseOverpass(json)
                if (cameras.size >= RadarSources.MIN_OSM_CAMERAS) return Result(cameras, null)
                errors.add("${host(url)}: só ${cameras.size} radares")
            } catch (t: Throwable) {
                errors.add("${host(url)}: ${t.message ?: t.javaClass.simpleName}")
            }
        }
        return Result(null, "OSM: " + errors.joinToString("; "))
    }

    /** ANTT's newest CSV resource, without downloading the CSV itself. */
    fun fetchAnttResource(): Result<RadarSources.AnttResource> = try {
        val resource = RadarSources.latestAnttCsv(request(RadarSources.ANTT_PACKAGE_URL, null, Charsets.UTF_8))
        if (resource != null) Result(resource, null) else Result(null, "ANTT: nenhum CSV de radares no pacote")
    } catch (t: Throwable) {
        Result(null, "ANTT: ${t.message ?: t.javaClass.simpleName}")
    }

    fun fetchAntt(resource: RadarSources.AnttResource): Result<List<RadarCamera>> = try {
        // The CSV is ISO-8859-1 (checked on the 2026-07 file).
        val cameras = RadarSources.parseAnttCsv(request(resource.url, null, Charsets.ISO_8859_1))
        if (cameras.size >= RadarSources.MIN_ANTT_CAMERAS) Result(cameras, null)
        else Result(null, "ANTT: só ${cameras.size} radares")
    } catch (t: Throwable) {
        Result(null, "ANTT: ${t.message ?: t.javaClass.simpleName}")
    }

    private fun host(url: String) = runCatching { URL(url).host }.getOrDefault(url)

    /** GET, or POST of a form [body]. Throws on any non-200 answer. */
    private fun request(url: String, body: String?, charset: Charset): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        if (tls != null && conn is HttpsURLConnection) conn.sslSocketFactory = tls
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", userAgent)
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            // 429 / 504 are Overpass saying "busy": the caller moves on to the next server.
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            // Android decompresses gzip itself; a plain JVM (the seed generator) may not.
            val stream = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(conn.inputStream) else conn.inputStream
            return stream.bufferedReader(charset).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
