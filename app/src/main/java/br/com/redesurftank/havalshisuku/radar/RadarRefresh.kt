package br.com.redesurftank.havalshisuku.radar

import java.security.MessageDigest
import java.util.Locale

/**
 * One refresh attempt, independent of Android and the scheduler. Each source commits its own
 * success only after its cache write succeeds, so retrying a partial failure skips fresh data.
 */
internal class RadarRefresh(
    private val source: Source,
    private val cache: Cache,
    private val clock: () -> Long,
    private val persist: (State) -> Unit
) {
    companion object {
        const val DAY_MS = 24 * 60 * 60_000L
        const val REVALIDATE_MS = 7 * DAY_MS

        fun due(now: Long, last: Long, interval: Long): Boolean =
            last <= 0L || now < last || now - last >= interval

        /** Only positions affect the road snippets. Match the persisted CSV precision. */
        fun geometryKey(cameras: List<RadarCamera>): String {
            val points = cameras.map { String.format(Locale.US, "%.6f,%.6f", it.lat, it.lon) }
                .distinct().sorted()
            val digest = MessageDigest.getInstance("SHA-256")
            points.forEach { digest.update((it + "\n").toByteArray(Charsets.UTF_8)) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun resourceKey(resource: RadarSources.AnttResource): String =
            listOf(resource.id, resource.modified, resource.url).joinToString("\n")
    }

    data class State(
        var osmSuccess: Long = 0L,
        var anttChecked: Long = 0L,
        var anttDownloaded: Long = 0L,
        var anttResource: String = "",
        var roadsSuccess: Long = 0L,
        var roadsGeometry: String = ""
    )

    interface Source {
        fun osm(): RadarDownloader.Result<List<RadarCamera>>
        fun anttResource(): RadarDownloader.Result<RadarSources.AnttResource>
        fun antt(resource: RadarSources.AnttResource): RadarDownloader.Result<List<RadarCamera>>
        fun roads(cameras: List<RadarCamera>, extra: List<RadarCamera>): RadarDownloader.Result<List<RoadSnippet>>
    }

    interface Cache {
        fun hasSource(source: RadarCamera.Source): Boolean
        fun save(contents: RadarFile.Contents)
        fun saveRoads(contents: RadarRoads.Contents)
        fun reload()
        val cameras: List<RadarCamera>
        val osmCameras: List<RadarCamera>
        val anttCameras: List<RadarCamera>
        val roadsUpdated: Long
    }

    enum class Outcome { FRESH, UPDATED, FAILED }
    data class Result(
        val osm: Outcome,
        val antt: Outcome,
        val roads: Outcome,
        val errors: List<String>
    ) {
        val ok: Boolean get() = errors.isEmpty()
        val changed: Boolean get() = listOf(osm, antt, roads).any { it == Outcome.UPDATED }
    }

    private fun osmDue(state: State) = !cache.hasSource(RadarCamera.Source.OSM) ||
        due(clock(), state.osmSuccess, DAY_MS)

    private fun anttDue(state: State) = !cache.hasSource(RadarCamera.Source.ANTT) ||
        due(clock(), state.anttChecked, DAY_MS) || due(clock(), state.anttDownloaded, REVALIDATE_MS)

    private fun roadsDue(state: State): Boolean {
        if (cache.roadsUpdated <= 0L) return true
        val last = maxOf(state.roadsSuccess, cache.roadsUpdated)
        val dirty = state.roadsGeometry != geometryKey(cache.cameras)
        return due(clock(), last, if (dirty) REVALIDATE_MS else RadarRoads.COOLDOWN_MS)
    }

    fun needed(state: State): Boolean = osmDue(state) || anttDue(state) || roadsDue(state)

    fun run(
        state: State,
        force: Boolean,
        retryOsm: Boolean = false,
        retryAntt: Boolean = false,
        timestamp: () -> String
    ): Result {
        val errors = ArrayList<String>()
        fun stage(name: String, work: () -> Outcome): Outcome = try {
            work()
        } catch (e: Exception) {
            errors.add("$name: ${e.message ?: e.javaClass.simpleName}")
            Outcome.FAILED
        }
        fun <T> value(result: RadarDownloader.Result<T>): T =
            result.value ?: throw java.io.IOException(result.error ?: "resposta vazia")

        val osm = stage("OSM") {
            if (!force && !retryOsm && !osmDue(state)) Outcome.FRESH else {
                val cameras = value(source.osm())
                cache.save(RadarFile.Contents(RadarCamera.Source.OSM, timestamp(), cameras))
                state.osmSuccess = clock()
                persist(state)
                Outcome.UPDATED
            }
        }
        val antt = stage("ANTT") {
            if (!force && !retryAntt && !anttDue(state)) Outcome.FRESH else {
                val resource = value(source.anttResource())
                val key = resourceKey(resource)
                val download = force || retryAntt || !cache.hasSource(RadarCamera.Source.ANTT) ||
                    key != state.anttResource || due(clock(), state.anttDownloaded, REVALIDATE_MS)
                if (download) {
                    val cameras = value(source.antt(resource))
                    cache.save(RadarFile.Contents(RadarCamera.Source.ANTT, resource.modified, cameras))
                    state.anttResource = key
                    state.anttDownloaded = clock()
                }
                state.anttChecked = clock()
                persist(state)
                if (download) Outcome.UPDATED else Outcome.FRESH
            }
        }
        // Always reload before deciding on roads: even a previous interrupted attempt may
        // have saved cameras. A load failure must not mark roads fresh for stale input.
        val roads = stage("Vias") {
            cache.reload()
            if (!roadsDue(state)) Outcome.FRESH else {
                val cameras = cache.cameras
                val geometry = geometryKey(cameras)
                val extra = RadarSources.anttOnly(cache.osmCameras, cache.anttCameras)
                val snippets = value(source.roads(cameras, extra))
                cache.saveRoads(RadarRoads.Contents(timestamp(), snippets))
                state.roadsSuccess = clock()
                state.roadsGeometry = geometry
                persist(state)
                cache.reload()
                Outcome.UPDATED
            }
        }
        return Result(osm, antt, roads, errors)
    }
}
