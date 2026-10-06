package br.com.redesurftank.havalshisuku.utils

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import br.com.redesurftank.havalshisuku.TAG
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Manifesto publicado pelo Impulse Launcher (viewer 3D) em cada release: `latest.json` no canal
 * estavel. Diferente do apps.json do catalogo, traz hash e assinatura esperados — o Impulse so
 * entrega o APK ao instalador se os dois conferirem.
 *
 * `impulseApi` e o nivel que o viewer declara em `impulse.api` (ver ViewerPresencePolicy); e
 * informativo e NAO e comparavel com `app.impulse.api_version` — nao bloqueia instalacao.
 */
data class HomeManifest(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val sha256: String,
    val signerSha256: String,
    val bytes: Long,
    val impulseApi: Int
)

sealed class HomeVerifyResult {
    object Ok : HomeVerifyResult()
    data class Failed(val reason: String) : HomeVerifyResult()
}

object ImpulseHomeUpdater {
    const val MANIFEST_URL =
        "https://github.com/netseek/impulse-home/releases/download/channel-stable/latest.json"

    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val ALLOWED_APK_PREFIX = "https://github.com/netseek/impulse-home/releases/download/"

    /** Devolve null se o JSON estiver incompleto, nao for do canal stable ou tiver URL/hash suspeitos. */
    fun parseManifest(json: String): HomeManifest? =
        try {
            val o = JSONObject(json)
            val apkUrl = o.getString("apkUrl")
            val sha = o.getString("sha256").lowercase()
            val signer = o.getString("signerSha256").lowercase()
            if (o.optString("channel", "") != "stable") null
            else if (!apkUrl.startsWith(ALLOWED_APK_PREFIX)) null
            else if (!HEX64.matches(sha) || !HEX64.matches(signer)) null
            else
                HomeManifest(
                    versionName = o.getString("versionName"),
                    versionCode = o.getLong("versionCode"),
                    apkUrl = apkUrl,
                    sha256 = sha,
                    signerSha256 = signer,
                    bytes = o.optLong("bytes", -1L),
                    impulseApi = o.optInt("impulseApi", 0)
                )
        } catch (e: Exception) {
            null
        }

    fun isUpdateAvailable(installedVersionCode: Long, manifest: HomeManifest): Boolean =
        manifest.versionCode > installedVersionCode

    /** Pura: o app instalado so atualiza por cima se a assinatura for a esperada. */
    fun signerMatches(installedSigners: Set<String>, expected: String): Boolean =
        expected.lowercase() in installedSigners

    fun fetchManifest(): HomeManifest? {
        var conn: HttpURLConnection? = null
        return try {
            conn =
                URL("$MANIFEST_URL?rnd=${System.currentTimeMillis()}").openConnection()
                    as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) null
            else parseManifest(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) {
            Log.w(TAG, "Impulse Home manifest unavailable", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(file.inputStream()).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** SHA-256 dos certificados que assinam o pacote instalado (vazio se nao instalado). */
    fun installedSigners(pm: PackageManager, packageName: String): Set<String> =
        try {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            signersOf(info.signingInfo)
        } catch (e: Exception) {
            emptySet()
        }

    fun archiveSigners(pm: PackageManager, apk: File): Set<String> {
        val pmSigners = try {
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val info = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            val fromSigningInfo = signersOf(info?.signingInfo)
            if (fromSigningInfo.isNotEmpty()) fromSigningInfo
            else {
                @Suppress("DEPRECATION")
                val sigs = info?.signatures
                if (!sigs.isNullOrEmpty()) {
                    sigs.map { sha256Hex(it.toByteArray()) }.toSet()
                } else emptySet()
            }
        } catch (e: Exception) {
            emptySet()
        }

        if (pmSigners.isNotEmpty()) return pmSigners

        return parseApkSigningBlockSigners(apk)
    }

    /**
     * Extrai os fingerprints SHA-256 dos certificados do APK Signing Block (v2/v3).
     * Essencial no Android 9 (API 28), onde getPackageArchiveInfo tem incompatibilidade com
     * APKs assinados exclusivamente com o esquema v3 / rotation lineage.
     */
    private const val MAX_APK_SIGNING_BLOCK_SIZE = 32 * 1024 * 1024L // 32 MB
    private const val MAX_PAIR_SIZE = 10 * 1024 * 1024L // 10 MB
    private const val MAX_SIGNER_SIZE = 5 * 1024 * 1024 // 5 MB
    private const val MAX_CERT_SIZE = 64 * 1024 // 64 KB
    private const val MAX_SIGNATURE_SIZE = 4096 // 4 KB
    private const val CHUNK_SIZE_BYTES = 1048576 // 1 MB

    /**
     * Extrai fingerprints SHA-256 dos certificados do APK Signing Block (esquemas v2 e v3)
     * com validação criptográfica completa:
     * 1. Limites estritos de tamanho de bloco e pares (proteção contra OOM/malformação);
     * 2. Correspondência de tamanho entre header e footer do APK Signing Block;
     * 3. Validação matemática da assinatura digital em `signedData` usando a chave pública do certificado leaf;
     * 4. Validação da integridade de conteúdo (Merkle tree chunk digests) vinculada à assinatura.
     */
    fun parseApkSigningBlockSigners(apk: File): Set<String> {
        return try {
            java.io.RandomAccessFile(apk, "r").use { raf ->
                val len = raf.length()
                if (len < 22) return emptySet()

                val maxSearch = minOf(len, 65557L).toInt()
                val searchBuf = ByteArray(maxSearch)
                raf.seek(len - maxSearch)
                raf.readFully(searchBuf)

                var eocdOffsetInBuf = -1
                for (i in (maxSearch - 22) downTo 0) {
                    if (searchBuf[i] == 0x50.toByte() &&
                        searchBuf[i + 1] == 0x4b.toByte() &&
                        searchBuf[i + 2] == 0x05.toByte() &&
                        searchBuf[i + 3] == 0x06.toByte()
                    ) {
                        eocdOffsetInBuf = i
                        break
                    }
                }
                if (eocdOffsetInBuf == -1) return emptySet()

                val eocdOffset = len - maxSearch + eocdOffsetInBuf
                raf.seek(eocdOffset + 16)
                val cdOffsetBuf = ByteArray(4)
                raf.readFully(cdOffsetBuf)
                val cdOffset = java.nio.ByteBuffer.wrap(cdOffsetBuf)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

                if (cdOffset < 32 || cdOffset > eocdOffset) return emptySet()

                raf.seek(cdOffset - 16)
                val magic = ByteArray(16)
                raf.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "APK Sig Block 42") return emptySet()

                raf.seek(cdOffset - 24)
                val blockSizeBuf = ByteArray(8)
                raf.readFully(blockSizeBuf)
                val blockSize = java.nio.ByteBuffer.wrap(blockSizeBuf)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).long

                // Valida limites do tamanho do bloco (AOSP specification)
                if (blockSize < 24 || blockSize > MAX_APK_SIGNING_BLOCK_SIZE || blockSize > cdOffset - 8) {
                    return emptySet()
                }
                val blockStart = cdOffset - 8 - blockSize
                if (blockStart < 0) return emptySet()

                // Verifica se o tamanho no cabeçalho do bloco coincide com o rodapé
                raf.seek(blockStart)
                val headerBlockSizeBuf = ByteArray(8)
                raf.readFully(headerBlockSizeBuf)
                val headerBlockSize = java.nio.ByteBuffer.wrap(headerBlockSizeBuf)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).long
                if (headerBlockSize != blockSize) return emptySet()

                val results = mutableSetOf<String>()
                var pos = blockStart + 8
                val blockEnd = cdOffset - 24
                while (pos < blockEnd) {
                    if (blockEnd - pos < 12) break
                    raf.seek(pos)
                    val pairHeader = ByteArray(12)
                    raf.readFully(pairHeader)
                    val bb = java.nio.ByteBuffer.wrap(pairHeader).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val pairLen = bb.long
                    val id = bb.int.toLong() and 0xFFFFFFFFL

                    // Valida limites do par ID-valor
                    if (pairLen < 4 || pairLen > (blockEnd - pos - 8) || pairLen > MAX_PAIR_SIZE) {
                        break
                    }

                    // ID 0x7109871a (v2) ou 0xf05368c0 (v3)
                    if (id == 0x7109871aL || id == 0xf05368c0L) {
                        val pairData = ByteArray((pairLen - 4).toInt())
                        raf.seek(pos + 12)
                        raf.readFully(pairData)
                        extractCertSignersFromSchemeBlock(
                            raf = raf,
                            blockStart = blockStart,
                            cdOffset = cdOffset,
                            eocdOffset = eocdOffset,
                            fileLen = len,
                            data = pairData,
                            isV3 = (id == 0xf05368c0L),
                            results = results
                        )
                    }
                    pos += 8 + pairLen
                }
                results
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse APK signing block", e)
            emptySet()
        }
    }

    private fun extractCertSignersFromSchemeBlock(
        raf: java.io.RandomAccessFile,
        blockStart: Long,
        cdOffset: Long,
        eocdOffset: Long,
        fileLen: Long,
        data: ByteArray,
        isV3: Boolean,
        results: MutableSet<String>
    ) {
        try {
            val buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            if (buf.remaining() < 4) return
            val signersLen = buf.int
            if (signersLen <= 0 || signersLen > buf.remaining() || signersLen > MAX_SIGNER_SIZE) return
            val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")

            while (buf.remaining() >= 4) {
                val signerLen = buf.int
                if (signerLen <= 0 || signerLen > buf.remaining() || signerLen > MAX_SIGNER_SIZE) break
                val signerEnd = buf.position() + signerLen

                if (buf.remaining() < 4) {
                    buf.position(signerEnd)
                    continue
                }
                val signedDataLen = buf.int
                if (signedDataLen <= 0 || signedDataLen > buf.remaining() || signedDataLen > (signerEnd - buf.position())) {
                    buf.position(signerEnd)
                    continue
                }
                val signedDataBytes = ByteArray(signedDataLen)
                buf.get(signedDataBytes)

                // Em v3, existem minSdkVersion e maxSdkVersion (8 bytes) antes de signatures
                if (isV3) {
                    if (buf.remaining() < 8) {
                        buf.position(signerEnd)
                        continue
                    }
                    buf.getInt() // minSdkVersion
                    buf.getInt() // maxSdkVersion
                }

                // Bloco de signatures
                if (buf.remaining() < 4) {
                    buf.position(signerEnd)
                    continue
                }
                val signaturesLen = buf.int
                if (signaturesLen <= 0 || signaturesLen > buf.remaining() || signaturesLen > (signerEnd - buf.position())) {
                    buf.position(signerEnd)
                    continue
                }
                val signaturesEnd = buf.position() + signaturesLen
                val signaturesList = mutableListOf<Pair<Int, ByteArray>>()
                while (buf.position() < signaturesEnd && buf.remaining() >= 8) {
                    val sigLen = buf.int
                    if (sigLen < 8 || sigLen > buf.remaining() || sigLen > (signaturesEnd - buf.position())) break
                    val sigEnd = buf.position() + sigLen
                    val sigAlgo = buf.int
                    val sigBytesLen = buf.int
                    if (sigBytesLen <= 0 || sigBytesLen > MAX_SIGNATURE_SIZE || sigBytesLen > (sigEnd - buf.position())) {
                        buf.position(sigEnd)
                        continue
                    }
                    val sigBytes = ByteArray(sigBytesLen)
                    buf.get(sigBytes)
                    signaturesList.add(Pair(sigAlgo, sigBytes))
                    buf.position(sigEnd)
                }

                // Public key
                if (buf.remaining() < 4) {
                    buf.position(signerEnd)
                    continue
                }
                val publicKeyLen = buf.int
                if (publicKeyLen <= 0 || publicKeyLen > MAX_SIGNATURE_SIZE || publicKeyLen > (signerEnd - buf.position())) {
                    buf.position(signerEnd)
                    continue
                }
                val publicKeyBytes = ByteArray(publicKeyLen)
                buf.get(publicKeyBytes)

                // Extrai digests e certificados de dentro de signedData
                val sBuf = java.nio.ByteBuffer.wrap(signedDataBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                if (sBuf.remaining() < 4) {
                    buf.position(signerEnd)
                    continue
                }
                val digestsLen = sBuf.int
                if (digestsLen < 0 || digestsLen > sBuf.remaining()) {
                    buf.position(signerEnd)
                    continue
                }
                val digestsEnd = sBuf.position() + digestsLen
                val expectedDigests = mutableMapOf<Int, ByteArray>()
                while (sBuf.position() < digestsEnd && sBuf.remaining() >= 8) {
                    val dLen = sBuf.int
                    if (dLen < 8 || dLen > sBuf.remaining()) break
                    val dEnd = sBuf.position() + dLen
                    val algo = sBuf.int
                    val hashLen = sBuf.int
                    if (hashLen <= 0 || hashLen > 64 || hashLen > sBuf.remaining()) {
                        sBuf.position(dEnd)
                        continue
                    }
                    val hash = ByteArray(hashLen)
                    sBuf.get(hash)
                    expectedDigests[algo] = hash
                    sBuf.position(dEnd)
                }
                sBuf.position(digestsEnd)

                if (sBuf.remaining() < 4) {
                    buf.position(signerEnd)
                    continue
                }
                val certsLen = sBuf.int
                val certsEnd = sBuf.position() + certsLen
                if (certsLen <= 0 || certsEnd > sBuf.capacity()) {
                    buf.position(signerEnd)
                    continue
                }

                val certs = mutableListOf<Pair<ByteArray, java.security.cert.X509Certificate>>()
                while (sBuf.position() < certsEnd && sBuf.remaining() >= 4) {
                    val certLen = sBuf.int
                    if (certLen <= 0 || certLen > MAX_CERT_SIZE || certLen > (certsEnd - sBuf.position())) break
                    val certBytes = ByteArray(certLen)
                    sBuf.get(certBytes)
                    try {
                        val cert = certFactory.generateCertificate(java.io.ByteArrayInputStream(certBytes)) as? java.security.cert.X509Certificate
                        if (cert != null) {
                            certs.add(Pair(certBytes, cert))
                        }
                    } catch (_: Exception) {}
                }

                if (certs.isEmpty()) {
                    buf.position(signerEnd)
                    continue
                }

                // Validação criptográfica da assinatura:
                // O certificado leaf (certs[0]) deve ter chave pública igual a publicKeyBytes
                val leafCert = certs[0].second
                val certPublicKeyBytes = leafCert.publicKey.encoded
                if (!java.util.Arrays.equals(certPublicKeyBytes, publicKeyBytes)) {
                    buf.position(signerEnd)
                    continue
                }

                // 1. Verifica se ao menos uma assinatura válida assina signedDataBytes com a chave pública
                var verifiedAlgo = -1
                for ((algoId, sigBytes) in signaturesList) {
                    if (verifySignature(algoId, leafCert.publicKey, signedDataBytes, sigBytes)) {
                        verifiedAlgo = algoId
                        break
                    }
                }

                if (verifiedAlgo == -1) {
                    buf.position(signerEnd)
                    continue
                }

                // 2. Validação da integridade de conteúdo vinculada à assinatura (Merkle tree chunk digests)
                val expectedDigest = expectedDigests[verifiedAlgo]
                if (expectedDigest != null) {
                    val contentOk = verifyContentDigest(
                        raf = raf,
                        blockStart = blockStart,
                        cdOffset = cdOffset,
                        eocdOffset = eocdOffset,
                        fileLen = fileLen,
                        algoId = verifiedAlgo,
                        expectedDigest = expectedDigest
                    )
                    if (!contentOk) {
                        buf.position(signerEnd)
                        continue
                    }
                }

                for ((certBytes, _) in certs) {
                    results.add(sha256Hex(certBytes))
                }

                buf.position(signerEnd)
            }
        } catch (_: Exception) {}
    }

    private fun verifyContentDigest(
        raf: java.io.RandomAccessFile,
        blockStart: Long,
        cdOffset: Long,
        eocdOffset: Long,
        fileLen: Long,
        algoId: Int,
        expectedDigest: ByteArray
    ): Boolean {
        val jcaDigest = when (algoId) {
            0x0102, 0x0104, 0x0202 -> "SHA-512"
            0x0101, 0x0103, 0x0201, 0x0301, 0x0421, 0x0423, 0x0425 -> "SHA-256"
            else -> return false
        }
        return try {
            val sec1Len = blockStart
            val sec2Len = eocdOffset - cdOffset
            val sec3Len = fileLen - eocdOffset
            if (sec1Len < 0 || sec2Len < 0 || sec3Len < 22) return false

            val chunks1 = (sec1Len + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES
            val chunks2 = (sec2Len + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES
            val chunks3 = (sec3Len + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES
            val totalChunksLong = chunks1 + chunks2 + chunks3
            if (totalChunksLong > Int.MAX_VALUE / 1024) return false
            val totalChunks = totalChunksLong.toInt()

            val allChunkHashes = ByteArray(5 + totalChunks * expectedDigest.size)
            allChunkHashes[0] = 0x5a
            java.nio.ByteBuffer.wrap(allChunkHashes, 1, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(totalChunks)

            val prefix = ByteArray(5)
            prefix[0] = 0xa5.toByte()

            var chunkIdx = 0
            fun digestSection(start: Long, size: Long, modifyEocd: Boolean = false): Boolean {
                var offset = 0L
                val buf = ByteArray(CHUNK_SIZE_BYTES)
                while (offset < size) {
                    val chunkSize = minOf(CHUNK_SIZE_BYTES.toLong(), size - offset).toInt()
                    raf.seek(start + offset)
                    raf.readFully(buf, 0, chunkSize)
                    if (modifyEocd && offset == 0L) {
                        if (chunkSize < 20) return false
                        // Offset 16 no EoCD é o deslocamento do Central Directory
                        java.nio.ByteBuffer.wrap(buf, 16, 4)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(blockStart.toInt())
                    }
                    java.nio.ByteBuffer.wrap(prefix, 1, 4)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(chunkSize)
                    val md = java.security.MessageDigest.getInstance(jcaDigest)
                    md.update(prefix)
                    md.update(buf, 0, chunkSize)
                    val chunkDigest = md.digest()
                    if (chunkDigest.size != expectedDigest.size) return false
                    System.arraycopy(
                        chunkDigest,
                        0,
                        allChunkHashes,
                        5 + chunkIdx * expectedDigest.size,
                        expectedDigest.size
                    )
                    chunkIdx++
                    offset += chunkSize
                }
                return true
            }

            if (!digestSection(0, sec1Len)) return false
            if (!digestSection(cdOffset, sec2Len)) return false
            if (!digestSection(eocdOffset, sec3Len, modifyEocd = true)) return false

            val topMd = java.security.MessageDigest.getInstance(jcaDigest)
            val actualDigest = topMd.digest(allChunkHashes)
            expectedDigest.contentEquals(actualDigest)
        } catch (_: Exception) {
            false
        }
    }

    private fun verifySignature(
        algoId: Int,
        publicKey: java.security.PublicKey,
        data: ByteArray,
        signature: ByteArray
    ): Boolean {
        val (jcaAlgo, paramSpec) = when (algoId) {
            0x0101 -> "SHA256withRSA/PSS" to java.security.spec.PSSParameterSpec(
                "SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256, 32, 1
            )
            0x0102 -> "SHA512withRSA/PSS" to java.security.spec.PSSParameterSpec(
                "SHA-512", "MGF1", java.security.spec.MGF1ParameterSpec.SHA512, 64, 1
            )
            0x0103, 0x0421 -> "SHA256withRSA" to null
            0x0104 -> "SHA512withRSA" to null
            0x0201, 0x0423 -> "SHA256withECDSA" to null
            0x0202 -> "SHA512withECDSA" to null
            0x0301, 0x0425 -> "SHA256withDSA" to null
            else -> return false
        }
        return try {
            val sig = java.security.Signature.getInstance(jcaAlgo)
            sig.initVerify(publicKey)
            if (paramSpec != null) {
                sig.setParameter(paramSpec)
            }
            sig.update(data)
            sig.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    private fun signersOf(si: android.content.pm.SigningInfo?): Set<String> {
        if (si == null) return emptySet()
        val certs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return certs.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    /** Baixa para [dest] e confere tamanho, SHA-256 e assinatura. Apaga o arquivo se falhar. */
    fun downloadAndVerify(
        pm: PackageManager,
        manifest: HomeManifest,
        dest: File,
        onProgress: (Float) -> Unit
    ): HomeVerifyResult {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(manifest.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            val length = conn.contentLengthLong
            var total = 0L
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        total += n
                        if (length > 0) onProgress(total.toFloat() / length)
                    }
                }
            }
            if (manifest.bytes > 0 && total != manifest.bytes)
                return fail(dest, "tamanho diferente do manifesto")
            if (sha256Hex(dest) != manifest.sha256) return fail(dest, "hash SHA-256 nao confere")
            val signers = archiveSigners(pm, dest)
            if (!signerMatches(signers, manifest.signerSha256))
                return fail(dest, "assinatura do APK nao confere")
            return HomeVerifyResult.Ok
        } catch (e: Exception) {
            Log.w(TAG, "Impulse Home download failed", e)
            return fail(dest, "falha no download")
        } finally {
            conn?.disconnect()
        }
    }

    private fun fail(dest: File, reason: String): HomeVerifyResult {
        dest.delete()
        Log.w(TAG, "Impulse Home APK rejected: $reason")
        return HomeVerifyResult.Failed(reason)
    }

    @Suppress("DEPRECATION")
    fun installedVersionCode(pm: PackageManager, packageName: String): Long? =
        try {
            val info = pm.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else info.versionCode.toLong()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
}
