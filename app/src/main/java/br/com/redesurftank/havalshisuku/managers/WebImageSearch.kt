package br.com.redesurftank.havalshisuku.managers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Live search against Wallhaven (no API key).
 *
 * Other provider ids remain on [WebImageProvider] only so Biblioteca entries saved in older
 * builds still resolve to a sensible label.
 */
enum class WebImageProvider(
    val id: String,
    val label: String
) {
    WALLHAVEN("wallhaven", "Wallhaven"),
    OPENVERSE("openverse", "Openverse"),
    UNSPLASH("unsplash", "Unsplash"),
    PEXELS("pexels", "Pexels"),
    PIXABAY("pixabay", "Pixabay");

    companion object {
        fun fromId(id: String?): WebImageProvider =
            entries.firstOrNull { it.id == id } ?: WALLHAVEN
    }
}

data class WebImage(
    val id: String,
    val provider: WebImageProvider,
    val title: String,
    val thumbUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val sourcePage: String
) {
    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else ""

    /** Stable key used by the library to know whether this image was already saved. */
    val libraryKey: String
        get() = "${provider.id}:$id"
}

data class WebImageSearchResult(
    val images: List<WebImage>,
    val page: Int,
    val hasMore: Boolean
)

class WebImageSearchException(message: String) : Exception(message)

object WebImageSearch {

    private const val TAG = "WebImageSearch"
    private const val USER_AGENT = "HavalImpulse/1.0 (cluster wallpaper picker)"

    suspend fun search(
        query: String,
        page: Int
    ): WebImageSearchResult = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val result = searchWallhaven(query, safePage)
        Log.d(
            TAG,
            "search wallhaven q='${query.trim()}' page=$safePage -> " +
                    "${result.images.size} imagens (hasMore=${result.hasMore})"
        )
        result
    }

    private fun searchWallhaven(query: String, page: Int): WebImageSearchResult {
        val q = query.trim()
        val url = buildString {
            append("https://wallhaven.cc/api/v1/search?categories=100&purity=100")
            append("&atleast=1280x600&order=desc&page=").append(page)
            if (q.isBlank()) {
                // No query: show what the site is currently ranking highest.
                append("&sorting=toplist&topRange=1M")
            } else {
                append("&sorting=relevance&q=").append(enc(q))
            }
        }
        val root = JSONObject(httpGet(url))
        val data = root.optJSONArray("data") ?: JSONArray()
        val images = data.objects().mapNotNull { o ->
            val full = o.optString("path")
            if (full.isBlank()) return@mapNotNull null
            WebImage(
                id = o.optString("id"),
                provider = WebImageProvider.WALLHAVEN,
                title = o.optString("resolution").ifBlank { o.optString("id") },
                thumbUrl = o.optJSONObject("thumbs")?.optString("large").orEmpty().ifBlank { full },
                fullUrl = full,
                width = o.optInt("dimension_x"),
                height = o.optInt("dimension_y"),
                sourcePage = o.optString("url")
            )
        }
        val meta = root.optJSONObject("meta")
        val hasMore = meta != null && meta.optInt("current_page", page) < meta.optInt("last_page", page)
        return WebImageSearchResult(images, page, hasMore)
    }

    // ── HTTP ────────────────────────────────────────────────────────────────────

    private fun httpGet(url: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                connection.errorStream?.close()
                throw WebImageSearchException(httpErrorMessage(code))
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: WebImageSearchException) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Network failure for $url", e)
            throw WebImageSearchException("Sem conexão com a internet. Verifique a rede do carro.")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected failure for $url", e)
            throw WebImageSearchException("Não foi possível ler a resposta do provedor.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        401, 403 -> "Acesso negado pelo Wallhaven (HTTP $code)."
        429 -> "Limite de buscas do Wallhaven atingido. Tente novamente em alguns minutos."
        in 500..599 -> "O Wallhaven está indisponível no momento (HTTP $code)."
        else -> "Falha na busca (HTTP $code)."
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }
}
