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

    @Test
    fun parsesApkSigningBlockSignersWhenFileExists() {
        val file = java.io.File("../impulse-home.apk")
        if (file.exists()) {
            val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(file)
            assertTrue("Expected signer $signer in $signers", signers.contains(signer))
        }
    }

    @Test
    fun rejectsTamperedApkSigningBlockSignatureWhenFileExists() {
        val file = java.io.File("../impulse-home.apk")
        if (file.exists()) {
            val tempFile = java.io.File.createTempFile("tampered_apk", ".apk")
            try {
                file.copyTo(tempFile, overwrite = true)
                java.io.RandomAccessFile(tempFile, "rw").use { raf ->
                    val len = raf.length()
                    val searchBuf = ByteArray(minOf(len, 65557L).toInt())
                    raf.seek(len - searchBuf.size)
                    raf.readFully(searchBuf)

                    // Localiza EOCD e CD offset
                    var eocdOffsetInBuf = -1
                    for (i in (searchBuf.size - 22) downTo 0) {
                        if (searchBuf[i] == 0x50.toByte() &&
                            searchBuf[i + 1] == 0x4b.toByte() &&
                            searchBuf[i + 2] == 0x05.toByte() &&
                            searchBuf[i + 3] == 0x06.toByte()
                        ) {
                            eocdOffsetInBuf = i
                            break
                        }
                    }
                    val eocdOffset = len - searchBuf.size + eocdOffsetInBuf
                    raf.seek(eocdOffset + 16)
                    val cdOffsetBuf = ByteArray(4)
                    raf.readFully(cdOffsetBuf)
                    val cdOffset = java.nio.ByteBuffer.wrap(cdOffsetBuf)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

                    raf.seek(cdOffset - 24)
                    val blockSizeBuf = ByteArray(8)
                    raf.readFully(blockSizeBuf)
                    val blockSize = java.nio.ByteBuffer.wrap(blockSizeBuf)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN).long
                    val blockStart = cdOffset - 8 - blockSize

                    // Corrompe bytes dentro do payload do scheme v3 (no signedData)
                    val corruptPos = blockStart + 100
                    raf.seek(corruptPos)
                    val b = raf.readByte()
                    raf.seek(corruptPos)
                    raf.writeByte(b.toInt() xor 0xFF)
                }
                val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(tempFile)
                assertFalse("Tampered signature should not be accepted", signers.contains(signer))
            } finally {
                tempFile.delete()
            }
        }
    }
}
