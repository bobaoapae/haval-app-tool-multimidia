package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import com.google.gson.JsonParser
import java.io.File
import kotlin.math.roundToInt

internal data class UsbMediaSnapshot(
        val title: String?,
        val path: String?,
        val durationMs: Long,
        val positionMs: Long,
        val isPlaying: Boolean
)

internal data class UsbMediaTags(
        val artist: String? = null,
        val album: String? = null,
        val artwork: Bitmap? = null,
        val durationMs: Long = 0L
)

/**
 * Fallback reader for OEM USB metadata that is not exposed by the MediaCenter Binder.
 *
 * The primary path remains IPlayService in [br.com.redesurftank.havalshisuku.services.BottomBarService].
 * This reader only reads the two fixed MediaCenter preference files and probes a validated, bounded
 * media file when the Binder omits tags or artwork.
 */
internal class UsbMediaInfoReader(private val context: Context) {
    private var cachedPath: String? = null
    private var cachedTags: UsbMediaTags = UsbMediaTags()

    fun readSnapshot(requireActiveSource: Boolean = true): UsbMediaSnapshot? {
        if (requireActiveSource && activeSource() != USB_MEDIA_SOURCE) return null
        val xml = readFixedFile(MEDIA_LAST_MODE_PREFS) ?: return null
        return UsbMediaSnapshotParser.parseTrack(xml)
    }

    fun activeSource(): Int? {
        val xml = readFixedFile(MEDIA_INFO_PREFS) ?: return null
        return UsbMediaSnapshotParser.parseActiveSource(xml)
    }

    fun readTags(path: String?): UsbMediaTags {
        val normalizedPath = path?.trim()?.takeIf { UsbMediaSnapshotParser.isSafeMediaPath(it) }
                ?: return UsbMediaTags()
        if (normalizedPath == cachedPath) return cachedTags

        val tags = readTagsUncached(normalizedPath)
        cachedPath = normalizedPath
        cachedTags = tags
        return tags
    }

    fun clearCache() {
        cachedPath = null
        cachedTags = UsbMediaTags()
    }

    private fun readFixedFile(path: String): String? {
        return runCatching {
                    ShizukuUtils.runCommandAndGetOutput(
                                    arrayOf("head", "-c", MAX_PREF_BYTES.toString(), path)
                            )
                            ?.takeIf { it.isNotBlank() && it.length < MAX_PREF_BYTES }
                }
                .getOrNull()
    }

    private fun readTagsUncached(path: String): UsbMediaTags {
        val size = readFileSize(path) ?: return UsbMediaTags()
        if (size <= 0L || size > MAX_MEDIA_PROBE_BYTES) return UsbMediaTags()

        val probeFile = runCatching { File.createTempFile("usb-track-", ".probe", context.cacheDir) }
                .getOrNull() ?: return UsbMediaTags()
        return try {
            // Arguments are passed directly to Shizuku's process API; no shell interpolation occurs.
            probeFile.setReadable(true, false)
            probeFile.setWritable(true, false)
            ShizukuUtils.runCommandAndGetOutput(arrayOf("cp", path, probeFile.absolutePath))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("chmod", "644", probeFile.absolutePath))
            if (!probeFile.isFile || probeFile.length() <= 0L || probeFile.length() > MAX_MEDIA_PROBE_BYTES) {
                return UsbMediaTags()
            }
            extractTags(probeFile)
        } catch (_: Throwable) {
            UsbMediaTags()
        } finally {
            runCatching { probeFile.delete() }
        }
    }

    private fun readFileSize(path: String): Long? {
        return runCatching {
                    ShizukuUtils.runCommandAndGetOutput(arrayOf("stat", "-c", "%s", path))
                            ?.lineSequence()
                            ?.firstOrNull()
                            ?.trim()
                            ?.toLongOrNull()
                }
                .getOrNull()
    }

    private fun extractTags(file: File): UsbMediaTags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val artist =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
            val album =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
            val durationMs =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                            ?.coerceAtLeast(0L)
                            ?: 0L
            val artwork = decodeArtwork(retriever.embeddedPicture)
            UsbMediaTags(artist = artist, album = album, artwork = artwork, durationMs = durationMs)
        } catch (_: Throwable) {
            UsbMediaTags()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeArtwork(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty() || bytes.size > MAX_EMBEDDED_ART_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_ARTWORK_DIMENSION * 2 ||
                        bounds.outHeight / sampleSize > MAX_ARTWORK_DIMENSION * 2
        ) {
            sampleSize *= 2
        }
        val decoded =
                BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize }
                ) ?: return null
        val largest = maxOf(decoded.width, decoded.height)
        if (largest <= MAX_ARTWORK_DIMENSION) return decoded
        val scale = MAX_ARTWORK_DIMENSION.toFloat() / largest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private companion object {
        private const val USB_MEDIA_SOURCE = 2
        private const val MAX_PREF_BYTES = 512 * 1024
        private const val MAX_MEDIA_PROBE_BYTES = 128L * 1024L * 1024L
        private const val MAX_EMBEDDED_ART_BYTES = 8 * 1024 * 1024
        private const val MAX_ARTWORK_DIMENSION = 720
        private const val MEDIA_INFO_PREFS =
                "/data/user_de/0/com.beantechs.mediacenter/shared_prefs/media_info_preference.xml"
        private const val MEDIA_LAST_MODE_PREFS =
                "/data/user_de/0/com.beantechs.mediacenter/shared_prefs/media_info_last_mode.xml"
    }
}

internal object UsbMediaSnapshotParser {
    private val allowedRoots =
            listOf("/storage/", "/mnt/media_rw/", "/mnt/usb/", "/mnt/runtime/")
    private val allowedExtensions =
            setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "ape")

    fun parseActiveSource(xml: String): Int? {
        return extractAttribute(xml, "media_source")?.toIntOrNull()
    }

    fun parseTrack(xml: String): UsbMediaSnapshot? {
        val rawJson = extractString(xml, "last_play_media_info2") ?: return null
        val json = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull() ?: return null
        val title = json.stringOrNull("title")
        val path = json.stringOrNull("path")
        val durationMs =
                (extractAttribute(xml, "playDuration2")?.toLongOrNull()
                                ?: json.longOrNull("duration")
                                ?: 0L)
                        .coerceAtLeast(0L)
        val positionMs =
                (extractAttribute(xml, "playPosition2")?.toLongOrNull() ?: 0L)
                        .coerceAtLeast(0L)
                        .let { position ->
                            if (durationMs > 0L) position.coerceAtMost(durationMs) else position
                        }
        val isPlaying =
                when (extractAttribute(xml, "isPlaying2")?.trim()?.lowercase()) {
                    "true", "1" -> true
                    else -> false
                }
        if (title == null && path == null && durationMs <= 0L) return null
        return UsbMediaSnapshot(title, path, durationMs, positionMs, isPlaying)
    }

    fun isSafeMediaPath(path: String): Boolean {
        if (path.length !in 2..1024 || !path.startsWith('/')) return false
        if (path.any { it.isISOControl() }) return false
        if (!allowedRoots.any(path::startsWith)) return false
        val segments = path.split('/')
        if (segments.any { it == "." || it == ".." }) return false
        val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in allowedExtensions
    }

    private fun extractAttribute(xml: String, name: String): String? {
        val tag =
                Regex(
                                """<[^>]+\bname\s*=\s*[\"']${Regex.escape(name)}[\"'][^>]*>""",
                                RegexOption.IGNORE_CASE
                        )
                        .find(xml)
                        ?.value
                        ?: return null
        return Regex("""\bvalue\s*=\s*[\"']([^\"']*)[\"']""", RegexOption.IGNORE_CASE)
                .find(tag)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::unescapeXml)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
    }

    private fun extractString(xml: String, name: String): String? {
        return Regex(
                        """<string\b[^>]*\bname\s*=\s*[\"']${Regex.escape(name)}[\"'][^>]*>([\s\S]*?)</string>""",
                        RegexOption.IGNORE_CASE
                )
                .find(xml)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::unescapeXml)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
    }

    private fun unescapeXml(value: String): String {
        return value.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
    }

    private fun com.google.gson.JsonObject.stringOrNull(name: String): String? {
        return runCatching {
                    get(name)
                            ?.takeUnless { it.isJsonNull }
                            ?.asString
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                }
                .getOrNull()
    }

    private fun com.google.gson.JsonObject.longOrNull(name: String): Long? {
        return runCatching { get(name)?.takeUnless { it.isJsonNull }?.asLong }.getOrNull()
    }
}
