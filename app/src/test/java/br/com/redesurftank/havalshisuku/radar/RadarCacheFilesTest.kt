package br.com.redesurftank.havalshisuku.radar

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class RadarCacheFilesTest {
    @Test fun `failed rename preserves old cache and removes temporary file`() {
        val dir = Files.createTempDirectory("radar-cache").toFile()
        try {
            val file = File(dir, "osm.csv.gz"); file.writeText("known-good")
            try {
                RadarCacheFiles.write(file, "replacement".toByteArray()) { _, _ -> false }
                fail("rename must fail")
            } catch (_: IOException) { }
            assertEquals("known-good", file.readText())
            assertFalse(File(dir, "osm.csv.gz.tmp").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun `successful replacement stores complete new cache`() {
        val dir = Files.createTempDirectory("radar-cache").toFile()
        try {
            val file = File(dir, "radar/antt.csv.gz")
            RadarCacheFiles.write(file, "first".toByteArray())
            RadarCacheFiles.write(file, "second".toByteArray())
            assertEquals("second", file.readText())
            assertFalse(File(file.parentFile, "antt.csv.gz.tmp").exists())
        } finally { dir.deleteRecursively() }
    }
}
