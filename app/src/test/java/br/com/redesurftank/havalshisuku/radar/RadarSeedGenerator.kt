package br.com.redesurftank.havalshisuku.radar

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Not a test: refreshes the radar copy shipped in the APK (app/src/main/assets/radar) with the
 * same download and parsing code the car runs. Skipped unless RADAR_SEED_OUT is set:
 *
 * ```
 * RADAR_SEED_OUT=app/src/main/assets/radar ./gradlew :app:testDebugUnitTest --tests '*RadarSeedGenerator*'
 * ```
 * (scripts/radar/Update-RadarSeed.ps1 does this.) Needs internet; takes about a minute.
 */
class RadarSeedGenerator {
    @Test
    fun generate() {
        val out = System.getenv("RADAR_SEED_OUT")
        assumeTrue("RADAR_SEED_OUT not set", !out.isNullOrBlank())
        val dir = File(out!!).also { it.mkdirs() }
        val downloader = RadarDownloader("Impulse seed generator (radar alerts)")
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

        val osm = downloader.fetchOsm()
        val osmCams = osm.value ?: error(osm.error ?: "OSM failed")
        File(dir, "osm.csv.gzip").writeBytes(RadarFile.encode(RadarFile.Contents(RadarCamera.Source.OSM, stamp, osmCams)))

        val resource = downloader.fetchAnttResource()
        val r = resource.value ?: error(resource.error ?: "ANTT metadata failed")
        val antt = downloader.fetchAntt(r)
        val anttCams = antt.value ?: error(antt.error ?: "ANTT failed")
        File(dir, "antt.csv.gzip").writeBytes(RadarFile.encode(RadarFile.Contents(RadarCamera.Source.ANTT, r.modified, anttCams)))

        val merged = RadarSources.merge(osmCams, anttCams)
        println(
            "radar seed: osm=${osmCams.size} (bearing ${osmCams.count { it.bearing != null }}) " +
                "antt=${anttCams.size} (bearing ${anttCams.count { it.bearing != null }}) merged=${merged.size} antt-resource=${r.id}"
        )
    }
}
