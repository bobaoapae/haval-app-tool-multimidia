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
        val fromApk: Boolean,
        /** Cameras tied to their OSM road (the rest use the straight-line fallback). */
        val withRoad: Int = 0,
        val roadsUpdated: String = ""
    )

    /** The sources as last loaded, for the monthly roads refresh (which needs every camera). */
    @Volatile
    var osmCameras: List<RadarCamera> = emptyList()
        private set
    @Volatile
    var anttCameras: List<RadarCamera> = emptyList()
        private set

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

    fun roadsFile(context: Context): File = File(File(context.filesDir, DIR), "roads.csv.gz")
    private const val ROADS_ASSET = "$DIR/roads.csv.gzip"

    /** Reads both sources and swaps the index in. Blocking; call off the main thread. */
    fun load(context: Context) {
        val osm = read(context, RadarCamera.Source.OSM)
        val antt = read(context, RadarCamera.Source.ANTT)
        osmCameras = osm?.first?.cameras.orEmpty()
        anttCameras = antt?.first?.cameras.orEmpty()
        val merged = RadarSources.merge(osmCameras, anttCameras)
        val roads = readRoads(context)
        val tied = RadarRoads.tie(merged, roads?.snippets.orEmpty())
        index = RadarIndex(merged, tied)
        status = Status(
            total = merged.size,
            osm = osm?.first?.cameras?.size ?: 0,
            antt = antt?.first?.cameras?.size ?: 0,
            osmUpdated = osm?.first?.updated.orEmpty(),
            anttUpdated = antt?.first?.updated.orEmpty(),
            fromApk = osm?.second != false && antt?.second != false,
            withRoad = tied.count { it != null },
            roadsUpdated = roads?.updated.orEmpty()
        )
        Log.w(TAG, "loaded ${merged.size} cameras (osm=${status?.osm} antt=${status?.antt} apk=${status?.fromApk} road=${status?.withRoad} roads=${roads?.snippets?.size})")
        // Logcat rolls over within minutes on the car: keep a durable trace of what loaded.
        br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger.log(
            "radar_loaded",
            mapOf("total" to merged.size, "road" to status?.withRoad, "roads" to roads?.snippets?.size, "apk" to status?.fromApk)
        )
    }

    /** Saves freshly built road stretches atomically. */
    fun writeRoads(context: Context, contents: RadarRoads.Contents) = writeAtomically(roadsFile(context), RadarRoads.encode(contents))

    private fun readRoads(context: Context): RadarRoads.Contents? {
        val file = roadsFile(context)
        if (file.isFile) {
            file.inputStream().use { RadarRoads.decode(it) }?.let { return it }
            Log.w(TAG, "unreadable ${file.name}, falling back to the APK copy")
        }
        return runCatching { context.assets.open(ROADS_ASSET).use { RadarRoads.decode(it) } }.getOrNull()
    }

    /** Saves a freshly downloaded source atomically (temp file, then rename). */
    fun write(context: Context, contents: RadarFile.Contents) = writeAtomically(fileFor(context, contents.source), RadarFile.encode(contents))

    private fun writeAtomically(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
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
