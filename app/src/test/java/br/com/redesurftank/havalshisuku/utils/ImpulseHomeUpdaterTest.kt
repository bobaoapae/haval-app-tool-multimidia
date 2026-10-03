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

    private fun loadMandatoryFixture(): java.io.File {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/minimal-v3-signed.apk.bytes")
            ?: throw IllegalStateException("Mandatory fixture fixtures/minimal-v3-signed.apk.bytes not found in test classpath")
        val temp = java.io.File.createTempFile("fixture_v3", ".apk")
        temp.deleteOnExit()
        stream.use { input ->
            java.io.FileOutputStream(temp).use { output ->
                input.copyTo(output)
            }
        }
        return temp
    }

    private val fixtureSigner = "7e00ad11a25d1e24c7ea609123510b332b663cf0a3f655219666c2209533ee2a"

    @Test
    fun mandatoryValidSignedApk_verifiesFullSignatureAndIntegrity() {
        val apk = loadMandatoryFixture()
        try {
            val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(apk)
            assertTrue("Expected signer $fixtureSigner in $signers", signers.contains(fixtureSigner))
        } finally {
            apk.delete()
        }
    }

    @Test
    fun mandatoryAdulteratedApk_contentTampered_rejected() {
        val apk = loadMandatoryFixture()
        try {
            // Corrompe 1 byte do conteúdo ZIP (antes do signing block)
            java.io.RandomAccessFile(apk, "rw").use { raf ->
                raf.seek(50)
                val b = raf.readByte()
                raf.seek(50)
                raf.writeByte(b.toInt() xor 0xFF)
            }
            val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(apk)
            assertTrue("Tampered content must be rejected by content digest check", signers.isEmpty())
        } finally {
            apk.delete()
        }
    }

    @Test
    fun mandatoryAdulteratedApk_signatureTampered_rejected() {
        val apk = loadMandatoryFixture()
        try {
            // Corrompe 1 byte dentro da assinatura digital no signing block
            java.io.RandomAccessFile(apk, "rw").use { raf ->
                val len = raf.length()
                raf.seek(len - 100) // próximo ao final do signing block / CD
                val b = raf.readByte()
                raf.seek(len - 100)
                raf.writeByte(b.toInt() xor 0xFF)
            }
            val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(apk)
            assertFalse("Tampered signature must not be accepted", signers.contains(fixtureSigner))
        } finally {
            apk.delete()
        }
    }

    @Test
    fun mandatoryMalformedApk_mismatchedBlockSize_rejected() {
        val apk = loadMandatoryFixture()
        try {
            java.io.RandomAccessFile(apk, "rw").use { raf ->
                // Localiza EOCD e CD offset
                val len = raf.length()
                val buf = ByteArray(minOf(len, 1024L).toInt())
                raf.seek(len - buf.size)
                raf.readFully(buf)
                var eocdOffsetInBuf = -1
                for (i in (buf.size - 22) downTo 0) {
                    if (buf[i] == 0x50.toByte() && buf[i + 1] == 0x4b.toByte() &&
                        buf[i + 2] == 0x05.toByte() && buf[i + 3] == 0x06.toByte()
                    ) {
                        eocdOffsetInBuf = i
                        break
                    }
                }
                val eocdOffset = len - buf.size + eocdOffsetInBuf
                raf.seek(eocdOffset + 16)
                val cdOffsetBuf = ByteArray(4)
                raf.readFully(cdOffsetBuf)
                val cdOffset = java.nio.ByteBuffer.wrap(cdOffsetBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

                raf.seek(cdOffset - 24)
                val blockSizeBuf = ByteArray(8)
                raf.readFully(blockSizeBuf)
                val blockSize = java.nio.ByteBuffer.wrap(blockSizeBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN).long
                val blockStart = cdOffset - 8 - blockSize

                // Altera o tamanho no cabeçalho do bloco para divergir do rodapé
                raf.seek(blockStart)
                raf.writeLong(java.lang.Long.reverseBytes(blockSize + 100))
            }
            val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(apk)
            assertTrue("Mismatched header/footer block size must be rejected", signers.isEmpty())
        } finally {
            apk.delete()
        }
    }

    @Test
    fun mandatoryMalformedApk_excessiveBlockSize_rejected() {
        val apk = loadMandatoryFixture()
        try {
            java.io.RandomAccessFile(apk, "rw").use { raf ->
                val len = raf.length()
                // Altera o footer blockSize para 100 MB (> 32 MB permitido)
                raf.seek(len - 100)
                // Procura magic "APK Sig Block 42"
                val search = ByteArray(minOf(len, 1024L).toInt())
                raf.seek(len - search.size)
                raf.readFully(search)
                val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
                var magicIdx = -1
                for (i in 0 until (search.size - magic.size)) {
                    var match = true
                    for (j in magic.indices) {
                        if (search[i + j] != magic[j]) { match = false; break }
                    }
                    if (match) { magicIdx = i; break }
                }
                if (magicIdx != -1) {
                    val magicPos = len - search.size + magicIdx
                    raf.seek(magicPos - 8)
                    raf.writeLong(java.lang.Long.reverseBytes(100 * 1024 * 1024L))
                }
            }
            val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(apk)
            assertTrue("Excessive block size must be rejected", signers.isEmpty())
        } finally {
            apk.delete()
        }
    }

    @Test
    fun mandatoryMalformedApk_corruptedMagic_rejected() {
        val apk = loadMandatoryFixture()
        try {
            java.io.RandomAccessFile(apk, "rw").use { raf ->
                val len = raf.length()
                val search = ByteArray(minOf(len, 1024L).toInt())
                raf.seek(len - search.size)
                raf.readFully(search)
                val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
                var magicIdx = -1
                for (i in 0 until (search.size - magic.size)) {
                    var match = true
                    for (j in magic.indices) {
                        if (search[i + j] != magic[j]) { match = false; break }
                    }
                    if (match) { magicIdx = i; break }
                }
                if (magicIdx != -1) {
                    val magicPos = len - search.size + magicIdx
                    raf.seek(magicPos)
                    raf.write("CORRUPTED_MAGIC!".toByteArray(Charsets.US_ASCII))
                }
            }
            val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(apk)
            assertTrue("Corrupted magic must be rejected", signers.isEmpty())
        } finally {
            apk.delete()
        }
    }

    @Test
    fun mandatoryMalformedApk_truncatedFile_rejected() {
        val temp = java.io.File.createTempFile("truncated", ".apk")
        try {
            temp.writeBytes(ByteArray(50) { it.toByte() })
            val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(temp)
            assertTrue("Truncated file must be rejected", signers.isEmpty())
        } finally {
            temp.delete()
        }
    }

    @Test
    fun parsesApkSigningBlockSignersWhenFileExists() {
        val file = java.io.File("../impulse-home.apk")
        if (file.exists()) {
            val signers = ImpulseHomeUpdater.parseApkSigningBlockSigners(file)
            assertTrue("Expected signer $signer in $signers", signers.contains(signer))
        }
    }
}
