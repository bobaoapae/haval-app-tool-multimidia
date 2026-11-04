package br.com.redesurftank.havalshisuku.radar

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RadarRefreshTest {
    private val day = RadarRefresh.DAY_MS
    private val camera = RadarCamera(-22.0, -43.0, 60, null, RadarCamera.Source.OSM)
    private val added = camera.copy(lat = -23.0)
    private val road = RoadSnippet(0, doubleArrayOf(-22.001, -21.999), doubleArrayOf(-43.0, -43.0))
    private val resource = RadarSources.AnttResource("same-id", "https://example.test/radars.csv", "2026-10-20")
    private fun <T> good(value: T) = RadarDownloader.Result(value, null)

    private inner class Fixture {
        var now = 100 * day
        val state = RadarRefresh.State(now, now, now, RadarRefresh.resourceKey(resource), now,
            RadarRefresh.geometryKey(listOf(camera)))
        var persisted = state.copy()
        var osmCalls = 0
        var metadataCalls = 0
        var anttCalls = 0
        var roadsCalls = 0
        var metadata = resource
        var osmResult = good(listOf(camera))
        var anttResult = good(listOf(camera.copy(source = RadarCamera.Source.ANTT)))
        var roadsResult = good(listOf(road))
        var metadataError = false
        var osmThrows = false
        var failWrite: RadarCamera.Source? = null
        var failRoadWrite = false
        var osmCache = listOf(camera)
        var anttCache = emptyList<RadarCamera>()
        var savedRoads = listOf(road)
        var validOsm = true
        var validAntt = true
        var roadTimestamp = now
        var activeCameras = listOf(camera)
        val source = object : RadarRefresh.Source {
            override fun osm(): RadarDownloader.Result<List<RadarCamera>> {
                osmCalls++
                if (osmThrows) throw IOException("OSM fetch failed")
                return osmResult
            }
            override fun anttResource(): RadarDownloader.Result<RadarSources.AnttResource> {
                metadataCalls++
                return if (metadataError) RadarDownloader.Result(null, "metadata failed") else good(metadata)
            }
            override fun antt(resource: RadarSources.AnttResource): RadarDownloader.Result<List<RadarCamera>> {
                anttCalls++
                return anttResult
            }
            override fun roads(cameras: List<RadarCamera>, extra: List<RadarCamera>): RadarDownloader.Result<List<RoadSnippet>> {
                roadsCalls++
                return roadsResult
            }
        }
        val cache = object : RadarRefresh.Cache {
            override fun hasSource(source: RadarCamera.Source) = if (source == RadarCamera.Source.OSM) validOsm else validAntt
            override fun save(contents: RadarFile.Contents) {
                if (contents.source == failWrite) throw IOException("disk full")
                if (contents.source == RadarCamera.Source.OSM) { osmCache = contents.cameras; validOsm = true }
                else { anttCache = contents.cameras; validAntt = true }
            }
            override fun saveRoads(contents: RadarRoads.Contents) {
                if (failRoadWrite) throw IOException("roads disk full")
                savedRoads = contents.snippets
                roadTimestamp = now
            }
            override fun reload() { activeCameras = RadarSources.merge(osmCache, anttCache) }
            override val cameras get() = activeCameras
            override val osmCameras get() = osmCache
            override val anttCameras get() = anttCache
            override val roadsUpdated get() = roadTimestamp
        }
        fun runner() = RadarRefresh(source, cache, { now }, { persisted = it.copy() })
        fun run(force: Boolean = false) = runner().run(state, force) { "2026-10-21T12:00:00Z" }
        fun oldRoads(days: Int) { roadTimestamp = now - days * day; state.roadsSuccess = roadTimestamp }
    }

    @Test fun `same ANTT id with changed date downloads`() {
        val f = Fixture(); f.state.anttChecked -= day
        f.metadata = resource.copy(modified = "2026-10-21")
        assertTrue(f.run().ok)
        assertEquals(1, f.anttCalls)
        assertEquals(RadarRefresh.resourceKey(f.metadata), f.persisted.anttResource)
    }

    @Test fun `same ANTT id with changed URL downloads`() {
        val f = Fixture(); f.state.anttChecked -= day
        f.metadata = resource.copy(url = "https://example.test/revised.csv")
        f.run(); assertEquals(1, f.anttCalls)
    }

    @Test fun `unchanged ANTT metadata does not reset CSV age`() {
        val f = Fixture(); f.state.anttChecked -= day; f.state.anttDownloaded -= 6 * day
        val downloaded = f.state.anttDownloaded
        assertTrue(f.run().ok)
        assertEquals(0, f.anttCalls)
        assertEquals(downloaded, f.persisted.anttDownloaded)
        assertEquals(f.now, f.persisted.anttChecked)
    }

    @Test fun `unchanged metadata revalidates CSV at seven days`() {
        val f = Fixture(); f.state.anttChecked -= day; f.state.anttDownloaded -= 7 * day
        f.run(); assertEquals(1, f.anttCalls); assertEquals(f.now, f.persisted.anttDownloaded)
    }

    @Test fun `failed ANTT after OSM success retries only ANTT`() {
        val f = Fixture(); f.state.osmSuccess -= day; f.state.anttChecked -= day; f.metadataError = true
        val oldCheck = f.state.anttChecked
        val first = f.run()
        assertFalse(first.ok); assertEquals(RadarRefresh.Outcome.UPDATED, first.osm)
        assertEquals(RadarRefresh.Outcome.FAILED, first.antt)
        assertEquals(oldCheck, f.persisted.anttChecked)
        f.now += 5 * 60_000; f.metadataError = false
        assertTrue(f.run().ok)
        assertEquals(1, f.osmCalls); assertEquals(2, f.metadataCalls)
    }

    @Test fun `failed roads retry does not refetch successful camera sources`() {
        val f = Fixture(); f.state.osmSuccess -= day; f.state.anttChecked -= day
        f.oldRoads(30); f.roadsResult = RadarDownloader.Result(null, "Overpass busy")
        assertFalse(f.run().ok)
        f.now += 5 * 60_000; f.roadsResult = good(listOf(road))
        assertTrue(f.run().ok)
        assertEquals(1, f.osmCalls); assertEquals(1, f.metadataCalls); assertEquals(2, f.roadsCalls)
    }

    @Test fun `source write failure preserves cache and allows other sources`() {
        val f = Fixture(); f.failWrite = RadarCamera.Source.OSM
        f.osmResult = good(listOf(added)); f.state.osmSuccess -= day; f.state.anttChecked -= day
        f.metadata = resource.copy(modified = "new")
        val oldOsm = f.state.osmSuccess
        assertFalse(f.run().ok)
        assertEquals(listOf(camera), f.osmCache); assertEquals(oldOsm, f.persisted.osmSuccess)
        assertEquals(1, f.anttCalls); assertEquals(f.now, f.persisted.anttDownloaded)
    }

    @Test fun `ANTT failed write does not commit metadata or clocks`() {
        val f = Fixture(); f.state.anttChecked -= day
        val old = f.state.copy()
        f.failWrite = RadarCamera.Source.ANTT; f.metadata = resource.copy(modified = "new")
        assertFalse(f.run().ok)
        assertEquals(old.anttResource, f.state.anttResource)
        assertEquals(old.anttChecked, f.state.anttChecked)
        assertEquals(old.anttDownloaded, f.state.anttDownloaded)
        assertTrue(f.anttCache.isEmpty())
    }

    @Test fun `download failure preserves cache and version`() {
        val f = Fixture(); f.state.anttChecked -= day; f.metadata = resource.copy(modified = "new")
        f.anttResult = RadarDownloader.Result(null, "invalid CSV")
        assertFalse(f.run().ok)
        assertEquals(RadarRefresh.resourceKey(resource), f.state.anttResource)
        assertTrue(f.anttCache.isEmpty())
    }

    @Test fun `fetch exception does not suppress next source`() {
        val f = Fixture(); f.state.osmSuccess -= day; f.state.anttChecked -= day; f.osmThrows = true
        val result = f.run()
        assertFalse(result.ok); assertEquals(1, f.metadataCalls)
        assertTrue(result.errors.single().contains("OSM fetch failed"))
    }

    @Test fun `manual force refreshes cameras but respects roads cap`() {
        val f = Fixture(); f.state.roadsGeometry = "unknown"
        assertTrue(f.run(force = true).ok)
        assertEquals(1, f.osmCalls); assertEquals(1, f.anttCalls); assertEquals(0, f.roadsCalls)
        f.now += 5 * 60_000; f.run()
        assertEquals(1, f.osmCalls); assertEquals(1, f.anttCalls)
    }

    @Test fun `geometry change waits seven days not thirty`() {
        val f = Fixture(); f.osmCache = listOf(camera, added); f.activeCameras = f.osmCache; f.oldRoads(6)
        f.run(); assertEquals(0, f.roadsCalls)
        f.oldRoads(7); f.run(); assertEquals(1, f.roadsCalls)
        assertEquals(RadarRefresh.geometryKey(f.activeCameras), f.persisted.roadsGeometry)
    }

    @Test fun `freshly downloaded camera geometry affects roads in same attempt`() {
        val f = Fixture(); f.oldRoads(8); f.state.osmSuccess -= day; f.osmResult = good(listOf(camera, added))
        f.run(); assertEquals(1, f.roadsCalls)
        assertEquals(RadarRefresh.geometryKey(listOf(camera, added)), f.persisted.roadsGeometry)
    }

    @Test fun `stable geometry stays monthly`() {
        val f = Fixture(); f.oldRoads(29); f.run(); assertEquals(0, f.roadsCalls)
        f.oldRoads(30); f.run(); assertEquals(1, f.roadsCalls)
    }

    @Test fun `legacy unknown fingerprint uses seed age not upgrade time`() {
        val f = Fixture(); f.state.roadsGeometry = ""; f.state.roadsSuccess = 0
        f.roadTimestamp = f.now - 6 * day; f.run(); assertEquals(0, f.roadsCalls)
        f.roadTimestamp = f.now - 7 * day; f.run(); assertEquals(1, f.roadsCalls)
    }

    @Test fun `roads write failure keeps baseline dirty across restart`() {
        val f = Fixture(); f.osmCache = listOf(added); f.activeCameras = f.osmCache; f.oldRoads(8)
        f.failRoadWrite = true; val before = f.state.copy()
        assertFalse(f.run().ok)
        assertEquals(before.roadsGeometry, f.state.roadsGeometry)
        assertEquals(before.roadsSuccess, f.state.roadsSuccess)
        assertEquals(listOf(road), f.savedRoads)
        f.failRoadWrite = false
        val restarted = RadarRefresh(f.source, f.cache, { f.now }, {})
        assertTrue(restarted.needed(f.state.copy()))
        assertTrue(restarted.run(f.state.copy(), false) { "now" }.ok)
        assertEquals(2, f.roadsCalls)
    }

    @Test fun `geometry fingerprint ignores order duplicates source limit and direction`() {
        val expected = RadarRefresh.geometryKey(listOf(camera, added))
        assertEquals(expected, RadarRefresh.geometryKey(listOf(added, camera, camera)))
        assertEquals(expected, RadarRefresh.geometryKey(listOf(added, camera.copy(limitKmh = 90, bearing = 180f, source = RadarCamera.Source.ANTT))))
        assertEquals(expected, RadarRefresh.geometryKey(listOf(added, camera.copy(lat = camera.lat + 0.0000001))))
        assertNotEquals(expected, RadarRefresh.geometryKey(listOf(camera)))
    }

    @Test fun `missing or corrupt sources bypass successful cooldown`() {
        val f = Fixture(); f.validOsm = false; f.validAntt = false
        assertTrue(f.runner().needed(f.state))
        assertTrue(f.run().ok); assertEquals(1, f.osmCalls); assertEquals(1, f.anttCalls)
    }

    @Test fun `future timestamps and empty state cannot suppress updates`() {
        val f = Fixture(); f.state.osmSuccess = f.now + day; f.state.anttChecked = f.now + day
        f.state.anttDownloaded = f.now + day; f.state.roadsSuccess = f.now + day
        assertTrue(f.run().ok)
        assertEquals(1, f.osmCalls); assertEquals(1, f.anttCalls); assertEquals(1, f.roadsCalls)
        assertTrue(RadarRefresh.due(1, 0, day))
    }

    @Test fun `failed forced refresh retries only failed source despite fresh clocks`() {
        val f = Fixture(); f.anttResult = RadarDownloader.Result(null, "ANTT offline")
        val first = f.run(force = true)
        assertFalse(first.ok)
        assertEquals(RadarRefresh.Outcome.FAILED, first.antt)
        f.now += 5 * 60_000; f.anttResult = good(listOf(camera.copy(source = RadarCamera.Source.ANTT)))
        val retry = f.runner().run(f.state, false,
            retryOsm = first.osm == RadarRefresh.Outcome.FAILED,
            retryAntt = first.antt == RadarRefresh.Outcome.FAILED) { "now" }
        assertTrue(retry.ok)
        assertEquals(1, f.osmCalls); assertEquals(2, f.anttCalls)
    }

    @Test fun `CSV maximum age wins over a recent metadata check`() {
        val f = Fixture(); f.state.anttDownloaded -= 7 * day
        assertTrue(f.runner().needed(f.state))
        f.run(); assertEquals(1, f.anttCalls)
    }

    @Test fun `unavailable roads do not inherit a stale successful timestamp`() {
        val f = Fixture(); f.roadTimestamp = 0
        assertTrue(f.runner().needed(f.state))
        f.run(); assertEquals(1, f.roadsCalls)
    }

    @Test fun `all fresh sources need no network`() {
        val f = Fixture(); assertFalse(f.runner().needed(f.state))
        val result = f.run(); assertTrue(result.ok); assertFalse(result.changed)
        assertEquals(0, f.osmCalls + f.metadataCalls + f.anttCalls + f.roadsCalls)
    }
}
