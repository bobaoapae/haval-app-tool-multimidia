package br.com.redesurftank.havalshisuku.utils

import android.util.Log
import br.com.redesurftank.havalshisuku.TAG
import br.com.redesurftank.havalshisuku.models.ReleaseInfo
import br.com.redesurftank.havalshisuku.models.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object ReleaseUpdateChecker {
    private const val RELEASES_URL =
            "https://api.github.com/repos/bobaoapae/haval-app-tool-multimidia/releases"

    suspend fun getAllReleaseInfo(): UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (conn.responseCode != 200) return@withContext UpdateCheckResult(null, null)

                val response =
                        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val releases = JSONArray(response)

                var latestRelease: ReleaseInfo? = null
                var latestPreview: ReleaseInfo? = null

                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    val isPrerelease = release.getBoolean("prerelease")
                    val tag = release.getString("tag_name")
                    val assets = release.getJSONArray("assets")
                    var downloadUrl: String? = null

                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    if (downloadUrl != null) {
                        // optString: release publicada sem descricao devolve null no JSON, e um
                        // getString ali derrubaria a verificacao inteira por causa do texto.
                        val notes = release.optString("body", "").trim()
                        val info = ReleaseInfo(tag, downloadUrl, isPrerelease, notes)
                        // Escolhe a MAIOR versão (compareVersions), NÃO a 1ª da lista: a ordem da API do
                        // GitHub é por created_at = data do commit que a tag aponta, que fura fácil (tag
                        // ancorada num commit antigo / releases por mirror). Sem break precoce. Regressão
                        // reintroduzida na base PR119 — ver memória haval-ota-self-hosted-releases.
                        if (isPrerelease) {
                            if (latestPreview == null || compareVersions(tag, latestPreview!!.tag) > 0) latestPreview = info
                        } else {
                            if (latestRelease == null || compareVersions(tag, latestRelease!!.tag) > 0) latestRelease = info
                        }
                    }

                    if (latestRelease != null && latestPreview != null) break
                }

                UpdateCheckResult(latestRelease, latestPreview)
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching release info", e)
                UpdateCheckResult(null, null)
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = versionParts(v1)
        val parts2 = versionParts(v2)
        val maxSize = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxSize) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a > b) return 1
            if (a < b) return -1
        }

        return 0
    }

    fun isNewer(candidateVersion: String, currentVersion: String): Boolean {
        return compareVersions(candidateVersion, currentVersion) > 0
    }

    private fun versionParts(version: String): List<Int> {
        val clean = version.removePrefix("v").removeSuffix("-preview")
        return clean.split(".")
                .filter { it.isNotBlank() }
                .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }
}
