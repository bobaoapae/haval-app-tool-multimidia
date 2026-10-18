package br.com.redesurftank.havalshisuku.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarSourcesTest {
    /** Shape of a real Overpass answer (ids/coords trimmed): a plain camera, one in degrees, one forward on a road. */
    private val overpass = """
        {"version":0.6,"elements":[
          {"type":"node","id":1,"lat":-22.90,"lon":-43.10,"tags":{"highway":"speed_camera","maxspeed":"60"}},
          {"type":"node","id":2,"lat":-23.71,"lon":-46.53,"tags":{"highway":"speed_camera","maxspeed":"50 km/h","direction":"210"}},
          {"type":"node","id":3,"lat":-22.9010,"lon":-43.1000,"tags":{"highway":"speed_camera","maxspeed":"80","direction":"backward"}},
          {"type":"node","id":4,"lat":-22.95,"lon":-43.15,"tags":{"highway":"speed_camera","maxspeed":"BR:urban","direction":"NE"}},
          {"type":"way","id":10,"nodes":[100,3,101],
           "geometry":[{"lat":-22.9020,"lon":-43.1000},{"lat":-22.9010,"lon":-43.1000},{"lat":-22.9000,"lon":-43.1000}],
           "tags":{"highway":"primary"}}
        ]}
    """.trimIndent()

    @Test
    fun `overpass cameras keep limit and direction, forward and backward follow the road`() {
        val cams = RadarSources.parseOverpass(overpass)
        assertEquals(4, cams.size)
        val plain = cams[0]
        assertEquals(60, plain.limitKmh)
        assertNull(plain.bearing)
        assertEquals(50, cams[1].limitKmh)
        assertEquals(210f, cams[1].bearing!!, 0.01f)
        // The road runs south -> north (bearing 0); backward = traffic heading south.
        assertEquals(180f, cams[2].bearing!!, 1f)
        assertNull(cams[3].limitKmh)
        assertEquals(45f, cams[3].bearing!!, 0.01f)
    }

    private val anttCsv = """
        concessionaria;ano_do_pnv_snv;tipo_de_radar;rodovia;uf;km_m;Municipio;tipo_de_pista;sentido;situacao;data_da_inativacao;latitude;longitude;velocidade_leve;velocidade_pesado
        X;2019;Controlador;BR-101;RJ;10,0;A;Principal;Crescente;Ativo;;-22,0000;-41,0000;60;60
        X;2019;Controlador;BR-101;RJ;12,0;A;Principal;Decrescente;Ativo;;-21,9820;-41,0000;80;60
        X;2019;Redutor;BR-101;RJ;14,0;A;Principal;Crescente/Decrescente;Ativo;;-21,9640;-41,0000;40;40
        X;2019;Controlador;BR-101;RJ;16,0;A;Principal;Crescente;Inativo;2025-01-01;-21,9460;-41,0000;60;60
        X;2019;Controlador;BR-999;RJ;5,0;A;Principal;Crescente;Ativo;;0;0;60;60
    """.trimIndent()

    @Test
    fun `antt rows give limit and a compass bearing from the km direction`() {
        val cams = RadarSources.parseAnttCsv(anttCsv)
        // Inactive and zero-coordinate rows dropped.
        assertEquals(3, cams.size)
        // km grows northwards here: Crescente = north, Decrescente = south, both = unknown.
        assertEquals(0f, RadarGeo.angleDiff(cams[0].bearing!!, 0f), 2f)
        assertEquals(80, cams[1].limitKmh)
        assertEquals(0f, RadarGeo.angleDiff(cams[1].bearing!!, 180f), 2f)
        assertNull(cams[2].bearing)
        assertTrue(cams.all { it.source == RadarCamera.Source.ANTT })
    }

    @Test
    fun `newest radar csv is picked from the ckan package`() {
        val json = """
            {"success":true,"result":{"resources":[
              {"id":"a","name":"Dados dos Radares","format":"CSV","url":"https://x/a.csv","last_modified":"2026-07-28T11:00:20"},
              {"id":"b","name":"Dados dos Radares","format":"CSV","url":"https://x/b.csv","last_modified":"2026-08-28T11:00:20"},
              {"id":"c","name":"Dicionário de Dados - Dados dos Radares","format":"CSV","url":"https://x/c.csv","last_modified":"2026-09-28T11:00:20"},
              {"id":"d","name":"Dados dos Radares","format":"JSON","url":"https://x/d.json","last_modified":"2026-09-28T11:00:20"}
            ]}}
        """.trimIndent()
        val r = RadarSources.latestAnttCsv(json)
        assertNotNull(r)
        assertEquals("b", r!!.id)
        assertEquals("https://x/b.csv", r.url)
    }

    @Test
    fun `merge drops the osm duplicate, keeps osm limit when antt lacks it, keeps the rest`() {
        val osmDup = RadarCamera(-22.0, -41.0, 50, 5f, RadarCamera.Source.OSM)
        val osmOther = RadarCamera(-22.5, -41.5, 60, null, RadarCamera.Source.OSM)
        // ~30 m north of the OSM one, same direction, no limit.
        val antt = RadarCamera(-21.99973, -41.0, null, 0f, RadarCamera.Source.ANTT)
        val merged = RadarSources.merge(listOf(osmDup, osmOther), listOf(antt))
        assertEquals(2, merged.size)
        val m = merged.first { it.source == RadarCamera.Source.ANTT }
        assertEquals(50, m.limitKmh)
        assertEquals(0f, m.bearing!!, 0.01f)
        assertTrue(merged.contains(osmOther))
    }

    @Test
    fun `merge keeps both when the directions disagree (one camera per carriageway)`() {
        val osm = RadarCamera(-22.0, -41.0, 60, 180f, RadarCamera.Source.OSM)
        val antt = RadarCamera(-21.99973, -41.0, 60, 0f, RadarCamera.Source.ANTT)
        assertEquals(2, RadarSources.merge(listOf(osm), listOf(antt)).size)
    }

    @Test
    fun `radar file round-trips`() {
        val cams = listOf(
            RadarCamera(-22.929078, -43.092103, 60, 210f, RadarCamera.Source.OSM),
            RadarCamera(-23.1, -46.5, null, null, RadarCamera.Source.OSM)
        )
        val bytes = RadarFile.encode(RadarFile.Contents(RadarCamera.Source.OSM, "2026-10-18T00:00:00Z", cams))
        val back = RadarFile.decode(bytes.inputStream())
        assertNotNull(back)
        assertEquals(RadarCamera.Source.OSM, back!!.source)
        assertEquals("2026-10-18T00:00:00Z", back.updated)
        assertEquals(cams, back.cameras)
        // The build tools unzip .gz assets: the plain text must read the same.
        val plain = java.util.zip.GZIPInputStream(bytes.inputStream()).readBytes()
        assertEquals(cams, RadarFile.decode(plain.inputStream())!!.cameras)
    }

    @Test
    fun `limits and compass parse defensively`() {
        assertEquals(60, RadarGeo.parseLimit("60"))
        assertEquals(40, RadarGeo.parseLimit("40;60"))
        assertNull(RadarGeo.parseLimit("BR:urban"))
        assertNull(RadarGeo.parseLimit("5"))
        assertEquals(270f, RadarGeo.parseCompass("W")!!, 0.01f)
        assertEquals(350f, RadarGeo.parseCompass("-10")!!, 0.01f)
        assertNull(RadarGeo.parseCompass("both"))
    }

    @Test
    fun `index finds only cameras within the radius`() {
        val near = RadarCamera(-22.9, -43.1, 60, null, RadarCamera.Source.OSM)
        val far = RadarCamera(-22.95, -43.1, 60, null, RadarCamera.Source.OSM) // ~5.5 km
        val index = RadarIndex(listOf(near, far))
        val hits = ArrayList<RadarCamera>()
        index.forEachNear(-22.901, -43.1, 1_000.0) { _, c, _ -> hits.add(c) }
        assertEquals(listOf(near), hits)
    }
}
