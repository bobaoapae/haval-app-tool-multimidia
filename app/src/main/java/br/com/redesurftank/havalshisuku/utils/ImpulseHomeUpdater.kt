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

    private fun archiveSigners(pm: PackageManager, apk: File): Set<String> =
        try {
            val info =
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            signersOf(info?.signingInfo)
        } catch (e: Exception) {
            emptySet()
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
