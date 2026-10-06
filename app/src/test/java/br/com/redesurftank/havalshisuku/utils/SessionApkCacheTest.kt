package br.com.redesurftank.havalshisuku.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionApkCacheTest {

    @Test
    fun clearDropsTheSessionDirAndLeftoverExternalApks() {
        val root = File.createTempFile("cache", "").apply { delete(); mkdirs() }
        val external = File(root, "external").apply { mkdirs() }
        val cache = File(root, "cache").apply { mkdirs() }
        try {
            val kept = File(external, "notes.txt").apply { writeText("keep") }
            File(external, "old.apk").writeBytes(byteArrayOf(1))
            SessionApkCache.apk(cache, "com.havalh6.viewer").writeBytes(byteArrayOf(2))
            SessionApkCache.clear(cache, external)
            assertFalse(SessionApkCache.directory(cache).exists())
            assertFalse(File(external, "old.apk").exists())
            assertTrue(kept.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun keyStaysInsideTheSessionDir() {
        val cache = File.createTempFile("cache", "").apply { delete(); mkdirs() }
        try {
            val file = SessionApkCache.apk(cache, "com.havalh6.viewer/1.0.1")
            assertEquals(File(cache, "session-apks/com.havalh6.viewer_1.0.1.apk"), file)
            assertTrue(file.parentFile?.exists() == true)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun publishOnlyPromotesAFinishedDownload() {
        val cache = File.createTempFile("cache", "").apply { delete(); mkdirs() }
        try {
            val finished = SessionApkCache.apk(cache, "catalog-app")
            val part = SessionApkCache.partial(finished)
            part.writeBytes(ByteArray(0))
            assertFalse(SessionApkCache.publish(part, finished))
            assertFalse(SessionApkCache.matches(finished))
            part.writeBytes(byteArrayOf(9, 9))
            assertTrue(SessionApkCache.publish(part, finished))
            assertTrue(SessionApkCache.matches(finished))
            assertFalse(part.exists())
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun hashMatchReusesTheFileAndAMismatchDoesNot() {
        val cache = File.createTempFile("cache", "").apply { delete(); mkdirs() }
        try {
            val file = SessionApkCache.apk(cache, "launcher")
            file.writeText("apk-bytes")
            val hash = ImpulseHomeUpdater.sha256Hex(file)
            assertTrue(SessionApkCache.matches(file, hash.uppercase()))
            assertFalse(SessionApkCache.matches(file, "ab"))
            assertFalse(SessionApkCache.matches(File(file.parentFile, "missing.apk"), hash))
            file.writeBytes(ByteArray(0))
            assertFalse(SessionApkCache.matches(file, null))
        } finally {
            cache.deleteRecursively()
        }
    }
}
