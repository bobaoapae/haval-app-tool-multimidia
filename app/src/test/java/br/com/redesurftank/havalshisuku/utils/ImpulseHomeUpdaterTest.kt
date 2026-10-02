package br.com.redesurftank.havalshisuku.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpulseHomeUpdaterTest {
    private val sha = "6098004714394ce834c2d59e4d88915d0ff5c06b564aa412508a4f98f2a2de1a"
    private val signer = "d672ec52abe9743b3ab05d29888841dc9214b43759e42c8fdd7aaa31830ee186"

    private fun json(
        channel: String = "stable",
        url: String = "https://github.com/netseek/impulse-home/releases/download/v1.0.0/impulse-home.apk",
        hash: String = sha
    ) =
        """{"versionName":"1.0.0","versionCode":10000,"apkUrl":"$url","sha256":"$hash",
        "signerSha256":"$signer","bytes":54947871,"channel":"$channel","impulseApi":2}"""

    @Test
    fun parsesPublishedManifest() {
        val m = ImpulseHomeUpdater.parseManifest(json())
        assertNotNull(m)
        assertEquals(10000L, m!!.versionCode)
        assertEquals(2, m.impulseApi)
        assertEquals(signer, m.signerSha256)
    }

    @Test
    fun rejectsNonStableChannel() = assertNull(ImpulseHomeUpdater.parseManifest(json(channel = "beta")))

    @Test
    fun rejectsForeignApkHost() =
        assertNull(ImpulseHomeUpdater.parseManifest(json(url = "https://evil.example/app.apk")))

    @Test
    fun rejectsMalformedHashAndGarbage() {
        assertNull(ImpulseHomeUpdater.parseManifest(json(hash = "abc")))
        assertNull(ImpulseHomeUpdater.parseManifest("not json"))
        assertNull(ImpulseHomeUpdater.parseManifest("{}"))
    }

    @Test
    fun updateComparesVersionCode() {
        val m = ImpulseHomeUpdater.parseManifest(json())!!
        assertTrue(ImpulseHomeUpdater.isUpdateAvailable(9999, m))
        assertFalse(ImpulseHomeUpdater.isUpdateAvailable(10000, m))
        assertFalse(ImpulseHomeUpdater.isUpdateAvailable(10001, m))
    }

    @Test
    fun signerMatchRequiresExpectedFingerprint() {
        assertTrue(ImpulseHomeUpdater.signerMatches(setOf("aa", signer), signer.uppercase()))
        assertFalse(ImpulseHomeUpdater.signerMatches(setOf("aa"), signer))
        assertFalse(ImpulseHomeUpdater.signerMatches(emptySet(), signer))
    }

    @Test
    fun sha256OfKnownBytes() =
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ImpulseHomeUpdater.sha256Hex("abc".toByteArray())
        )
}
