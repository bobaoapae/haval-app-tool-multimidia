package br.com.redesurftank.havalshisuku.utils

import android.util.Log
import br.com.redesurftank.havalshisuku.TAG
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Atualizador do "AutoPanel Shizuku" (barra de navegacao inferior customizada, via Shizuku).
 *
 * As releases ficam no repositorio publico leonardovin/shizuku-bottom-bar-releases. A API do GitHub
 * devolve, por asset, o tamanho e o digest SHA-256. O certificado de assinatura esperado fica FIXO
 * aqui no codigo (nunca vem da rede). O resultado e um [HomeManifest], entao o download e a
 * verificacao reaproveitam [ImpulseHomeUpdater.downloadAndVerify] sem alteracoes.
 */
object AutoPanelUpdater {
    const val PACKAGE = "dev.leonardovin.havalbottombar"
    const val RELEASES_LATEST_URL =
        "https://api.github.com/repos/leonardovin/shizuku-bottom-bar-releases/releases/latest"

    /** SHA-256 do certificado de release do AutoPanel. Fixo: nunca lido da rede. */
    const val SIGNER_SHA256 = "fb8c7e31045ffafb5cf0131951249e7617cd5104b9fa5e54db395e306c1cb168"

    private const val ALLOWED_APK_PREFIX =
        "https://github.com/leonardovin/shizuku-bottom-bar-releases/releases/download/"

    private val DIGEST = Regex("^sha256:([0-9a-f]{64})$")
    private val TAG_SEMVER = Regex("^v\\d+\\.\\d+\\.\\d+.*")

    /**
     * Converte a resposta de `releases/latest` em manifesto. Devolve null se a tag nao for semver,
     * se o asset `haval-bottom-bar-<tag>.apk` faltar, se a URL sair do repositorio esperado ou se o
     * digest nao for `sha256:` + 64 hex minusculos.
     */
    fun parseLatestRelease(json: String): HomeManifest? {
        return try {
            val o = JSONObject(json)
            val tag = o.getString("tag_name")
            if (!TAG_SEMVER.matches(tag)) return null
            val wanted = "haval-bottom-bar-$tag.apk"
            val assets = o.getJSONArray("assets")
            var found: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == wanted) {
                    found = a
                    break
                }
            }
            if (found == null) return null
            val url = found.getString("browser_download_url")
            val sha = DIGEST.matchEntire(found.getString("digest"))?.groupValues?.get(1)
            if (!url.startsWith(ALLOWED_APK_PREFIX) || sha == null) null
            else
                HomeManifest(
                    versionName = tag,
                    // O AutoPanel e comparado por versionName; versionCode nao e usado aqui.
                    versionCode = 0L,
                    apkUrl = url,
                    sha256 = sha,
                    signerSha256 = SIGNER_SHA256,
                    bytes = found.optLong("size", -1L),
                    impulseApi = 0
                )
        } catch (e: Exception) {
            null
        }
    }

    fun fetchLatest(): HomeManifest? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(RELEASES_LATEST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) null
            else parseLatestRelease(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) {
            Log.w(TAG, "AutoPanel release unavailable", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Sem versao instalada nao ha o que atualizar. */
    fun isUpdateAvailable(installedVersionName: String?, manifest: HomeManifest): Boolean =
        installedVersionName != null &&
            ReleaseUpdateChecker.compareVersions(installedVersionName, manifest.versionName) < 0
}
