package br.com.redesurftank.havalshisuku.radar

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The compact gzipped CSV Impulse keeps per source (the APK seed in assets/radar and the daily
 * download in files/radar use the same format):
 *
 * ```
 * # impulse-radar v1 source=osm updated=2026-10-18T01:38:51Z
 * -22.929078,-43.092103,60,210
 * ```
 * Columns: lat, lon, limit km/h (empty = unknown), enforced travel bearing (empty = unknown or
 * both ways). Lines starting with `#` are comments; the first carries the metadata.
 */
object RadarFile {
    const val VERSION = "v1"

    data class Contents(val source: RadarCamera.Source, val updated: String, val cameras: List<RadarCamera>)

    fun encode(contents: Contents): ByteArray {
        val sb = StringBuilder(contents.cameras.size * 32)
        sb.append("# impulse-radar ").append(VERSION)
            .append(" source=").append(contents.source.code)
            .append(" updated=").append(contents.updated).append('\n')
        for (c in contents.cameras) {
            sb.append(String.format(Locale.US, "%.6f,%.6f,", c.lat, c.lon))
            c.limitKmh?.let { sb.append(it) }
            sb.append(',')
            RadarGeo.roundBearing(c.bearing)?.let { sb.append(it) }
            sb.append('\n')
        }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /**
     * Null when the data isn't ours or can't be read; bad rows are skipped. Gzipped or plain:
     * the build tools unzip `.gz` assets on their own, so both forms can turn up.
     */
    fun decode(input: InputStream): Contents? = runCatching {
        val buffered = java.io.BufferedInputStream(input)
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val stream = if (gzip) GZIPInputStream(buffered) else buffered
        decodeText(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }.getOrNull()

    fun decodeText(text: String): Contents? {
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext()) return null
        val header = lines.next()
        if (!header.startsWith("# impulse-radar $VERSION")) return null
        val meta = header.split(' ').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq > 0) part.substring(0, eq) to part.substring(eq + 1) else null
        }.toMap()
        val source = RadarCamera.Source.fromCode(meta["source"].orEmpty()) ?: return null
        val cameras = ArrayList<RadarCamera>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val p = line.split(',')
            if (p.size < 4) continue
            val lat = p[0].toDoubleOrNull() ?: continue
            val lon = p[1].toDoubleOrNull() ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            cameras.add(RadarCamera(lat, lon, p[2].toIntOrNull(), p[3].toFloatOrNull(), source))
        }
        return Contents(source, meta["updated"].orEmpty(), cameras)
    }
}
