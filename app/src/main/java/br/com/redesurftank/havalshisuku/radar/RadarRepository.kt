package br.com.redesurftank.havalshisuku.radar

import android.content.Context
import android.util.Log
import java.io.File

/**
 * The cameras in use: the daily download (files/radar) when there is one, else the copy shipped
 * in the APK (assets/radar). Each source is kept in its own file and merged on load, so a day
 * where only one source updates still merges cleanly.
 */
object RadarRepository {
    private const val TAG = "Radar"
    private const val DIR = "radar"

    data class Status(
        val total: Int,
        val osm: Int,
        val antt: Int,
        val osmUpdated: String,
        val anttUpdated: String,
        /** Still the copy from the APK (no successful download yet). */
        val fromApk: Boolean
    )

    @Volatile
    var index: RadarIndex? = null
        private set

    @Volatile
    var status: Status? = null
        private set

    fun fileFor(context: Context, source: RadarCamera.Source): File =
        File(File(context.filesDir, DIR), "${source.name.lowercase()}.csv.gz")

    /** `.gzip`, not `.gz`: the build unzips `.gz` assets and renames them. */
    private fun assetFor(source: RadarCamera.Source) = "$DIR/${source.name.lowercase()}.csv.gzip"

    /** Reads both sources and swaps the index in. Blocking; call off the main thread. */
    fun load(context: Context) {
        val osm = read(context, RadarCamera.Source.OSM)
        val antt = read(context, RadarCamera.Source.ANTT)
        val merged = RadarSources.merge(osm?.first?.cameras.orEmpty(), antt?.first?.cameras.orEmpty())
        index = RadarIndex(merged)
        status = Status(
            total = merged.size,
            osm = osm?.first?.cameras?.size ?: 0,
            antt = antt?.first?.cameras?.size ?: 0,
            osmUpdated = osm?.first?.updated.orEmpty(),
            anttUpdated = antt?.first?.updated.orEmpty(),
            fromApk = osm?.second != false && antt?.second != false
        )
        Log.w(TAG, "loaded ${merged.size} cameras (osm=${status?.osm} antt=${status?.antt} apk=${status?.fromApk})")
    }

    /** Saves a freshly downloaded source atomically (temp file, then rename). */
    fun write(context: Context, contents: RadarFile.Contents) {
        val file = fileFor(context, contents.source)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(RadarFile.encode(contents))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw java.io.IOException("rename failed for ${file.name}")
        }
    }

    /** The source's contents and whether they came from the APK. */
    private fun read(context: Context, source: RadarCamera.Source): Pair<RadarFile.Contents, Boolean>? {
        val file = fileFor(context, source)
        if (file.isFile) {
            file.inputStream().use { RadarFile.decode(it) }?.let { return it to false }
            Log.w(TAG, "unreadable ${file.name}, falling back to the APK copy")
        }
        return runCatching { context.assets.open(assetFor(source)).use { RadarFile.decode(it) } }
            .getOrNull()?.let { it to true }
    }
}
