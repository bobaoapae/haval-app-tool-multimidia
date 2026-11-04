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
        /** Overpass spends minutes searching (roads: ~2 min) before it sends a byte. */
        const val READ_TIMEOUT_MS = 600_000
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

    /**
     * Roads under [cameras] (OSM cameras by node, [extra] by position), cut down to the
     * stretches around them. The answer (~30 MB of JSON) goes to [scratch] and is streamed
     * from there, so it is never held in memory. Uses the first Overpass server that answers.
     */
    fun fetchRoads(cameras: List<RadarCamera>, extra: List<RadarCamera>, scratch: java.io.File): Result<List<RoadSnippet>> {
        val errors = ArrayList<String>()
        val body = "data=" + URLEncoder.encode(RadarRoads.overpassQuery(extra.map { it.lat to it.lon }), "UTF-8")
        for (url in RadarSources.OVERPASS_URLS) {
            try {
                download(url, body, scratch)
                val snippets = RadarRoads.buildSnippets({ scratch.bufferedReader(Charsets.UTF_8) }, cameras)
                if (snippets.size >= RadarRoads.MIN_SNIPPETS) return Result(snippets, null)
                errors.add("${host(url)}: só ${snippets.size} trechos")
            } catch (t: Throwable) {
                errors.add("${host(url)}: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                scratch.delete()
            }
        }
        return Result(null, "Vias: " + errors.joinToString("; "))
    }

    private fun host(url: String) = runCatching { URL(url).host }.getOrDefault(url)

    /** POST [body] to [url] and save the answer in [file]. */
    private fun download(url: String, body: String, file: java.io.File) {
        val conn = open(url, body)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            val stream = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(conn.inputStream) else conn.inputStream
            stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, body: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        if (tls != null && conn is HttpsURLConnection) conn.sslSocketFactory = tls
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("User-Agent", userAgent)
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return conn
    }

    /** GET, or POST of a form [body]. Throws on any non-200 answer. */
    private fun request(url: String, body: String?, charset: Charset): String {
        val conn = open(url, body)
        try {
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
