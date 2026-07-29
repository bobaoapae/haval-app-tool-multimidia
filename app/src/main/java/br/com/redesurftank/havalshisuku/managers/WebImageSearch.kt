package br.com.redesurftank.havalshisuku.managers

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Live search against public/free image providers.
 *
 * Wallhaven and Openverse work with no credentials at all (Openverse aggregates Flickr,
 * Wikimedia Commons, NASA, Rawpixel and friends). Unsplash/Pexels/Pixabay require a free
 * personal API key, which the user pastes once in the background dialog and we keep in prefs.
 */
enum class WebImageProvider(
    val id: String,
    val label: String,
    val requiresKey: Boolean,
    val keyPortal: String = ""
) {
    WALLHAVEN("wallhaven", "Wallhaven", false),
    OPENVERSE("openverse", "Openverse", false),
    UNSPLASH("unsplash", "Unsplash", true, "unsplash.com/developers"),
    PEXELS("pexels", "Pexels", true, "pexels.com/api"),
    PIXABAY("pixabay", "Pixabay", true, "pixabay.com/api/docs");

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
    private const val PAGE_SIZE = 24
    private const val USER_AGENT = "HavalImpulse/1.0 (cluster wallpaper picker)"

    /** Fallback term so a provider that cannot list without a query still shows something. */
    private const val DEFAULT_QUERY = "wallpaper"

    // ── API keys ────────────────────────────────────────────────────────────────

    private fun readKeyMap(prefs: SharedPreferences): Map<String, String> {
        val json = prefs.getString(SharedPreferencesKeys.WEB_IMAGE_API_KEYS.key, "{}")
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson<Map<String, String>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing api key map", e)
            emptyMap()
        }
    }

    fun getApiKey(prefs: SharedPreferences, provider: WebImageProvider): String =
        readKeyMap(prefs)[provider.id].orEmpty().trim()

    fun setApiKey(prefs: SharedPreferences, provider: WebImageProvider, key: String) {
        val map = readKeyMap(prefs).toMutableMap()
        val trimmed = key.trim()
        if (trimmed.isBlank()) map.remove(provider.id) else map[provider.id] = trimmed
        prefs.edit { putString(SharedPreferencesKeys.WEB_IMAGE_API_KEYS.key, Gson().toJson(map)) }
    }

    // ── Search ──────────────────────────────────────────────────────────────────

    suspend fun search(
        provider: WebImageProvider,
        query: String,
        page: Int,
        apiKey: String = ""
    ): WebImageSearchResult = withContext(Dispatchers.IO) {
        if (provider.requiresKey && apiKey.isBlank()) {
            throw WebImageSearchException("Informe a chave de API do ${provider.label} para buscar.")
        }
        val safePage = page.coerceAtLeast(1)
        val result = when (provider) {
            WebImageProvider.WALLHAVEN -> searchWallhaven(query, safePage)
            WebImageProvider.OPENVERSE -> searchOpenverse(query, safePage)
            WebImageProvider.UNSPLASH -> searchUnsplash(query, safePage, apiKey)
            WebImageProvider.PEXELS -> searchPexels(query, safePage, apiKey)
            WebImageProvider.PIXABAY -> searchPixabay(query, safePage, apiKey)
        }
        Log.d(
            TAG,
            "search ${provider.id} q='${query.trim()}' page=$safePage -> " +
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

    private fun searchOpenverse(query: String, page: Int): WebImageSearchResult {
        val q = query.trim().ifBlank { DEFAULT_QUERY }
        val url = "https://api.openverse.org/v1/images/?q=${enc(q)}&page=$page" +
                "&page_size=$PAGE_SIZE&mature=false&size=large&aspect_ratio=wide&license_type=all"
        val root = JSONObject(httpGet(url))
        val results = root.optJSONArray("results") ?: JSONArray()
        val images = results.objects().mapNotNull { o ->
            val full = o.optString("url")
            if (full.isBlank()) return@mapNotNull null
            WebImage(
                id = o.optString("id"),
                provider = WebImageProvider.OPENVERSE,
                title = o.optString("title").ifBlank { o.optString("source") },
                thumbUrl = o.optString("thumbnail").ifBlank { full },
                fullUrl = full,
                width = o.optInt("width"),
                height = o.optInt("height"),
                sourcePage = o.optString("foreign_landing_url")
            )
        }
        return WebImageSearchResult(images, page, page < root.optInt("page_count", page))
    }

    private fun searchUnsplash(query: String, page: Int, apiKey: String): WebImageSearchResult {
        val q = query.trim().ifBlank { DEFAULT_QUERY }
        val url = "https://api.unsplash.com/search/photos?query=${enc(q)}" +
                "&per_page=$PAGE_SIZE&page=$page&orientation=landscape"
        val root = JSONObject(httpGet(url, mapOf("Authorization" to "Client-ID $apiKey")))
        val results = root.optJSONArray("results") ?: JSONArray()
        val images = results.objects().mapNotNull { o ->
            val urls = o.optJSONObject("urls") ?: return@mapNotNull null
            val full = urls.optString("full").ifBlank { urls.optString("regular") }
            if (full.isBlank()) return@mapNotNull null
            WebImage(
                id = o.optString("id"),
                provider = WebImageProvider.UNSPLASH,
                title = o.optString("description").ifBlank { o.optString("alt_description") },
                thumbUrl = urls.optString("small").ifBlank { full },
                fullUrl = full,
                width = o.optInt("width"),
                height = o.optInt("height"),
                sourcePage = o.optJSONObject("links")?.optString("html").orEmpty()
            )
        }
        return WebImageSearchResult(images, page, page < root.optInt("total_pages", page))
    }

    private fun searchPexels(query: String, page: Int, apiKey: String): WebImageSearchResult {
        val q = query.trim().ifBlank { DEFAULT_QUERY }
        val url = "https://api.pexels.com/v1/search?query=${enc(q)}" +
                "&per_page=$PAGE_SIZE&page=$page&orientation=landscape"
        val root = JSONObject(httpGet(url, mapOf("Authorization" to apiKey)))
        val photos = root.optJSONArray("photos") ?: JSONArray()
        val images = photos.objects().mapNotNull { o ->
            val src = o.optJSONObject("src") ?: return@mapNotNull null
            val full = src.optString("original").ifBlank { src.optString("large2x") }
            if (full.isBlank()) return@mapNotNull null
            WebImage(
                id = o.optString("id"),
                provider = WebImageProvider.PEXELS,
                title = o.optString("alt").ifBlank { o.optString("photographer") },
                thumbUrl = src.optString("large").ifBlank { full },
                fullUrl = full,
                width = o.optInt("width"),
                height = o.optInt("height"),
                sourcePage = o.optString("url")
            )
        }
        return WebImageSearchResult(images, page, root.optString("next_page").isNotBlank())
    }

    private fun searchPixabay(query: String, page: Int, apiKey: String): WebImageSearchResult {
        val q = query.trim().ifBlank { DEFAULT_QUERY }
        val url = "https://pixabay.com/api/?key=${enc(apiKey)}&q=${enc(q)}" +
                "&image_type=photo&orientation=horizontal&safesearch=true&min_width=1280" +
                "&per_page=$PAGE_SIZE&page=$page"
        val root = JSONObject(httpGet(url))
        val hits = root.optJSONArray("hits") ?: JSONArray()
        val images = hits.objects().mapNotNull { o ->
            val full = o.optString("largeImageURL").ifBlank { o.optString("webformatURL") }
            if (full.isBlank()) return@mapNotNull null
            WebImage(
                id = o.optString("id"),
                provider = WebImageProvider.PIXABAY,
                title = o.optString("tags"),
                thumbUrl = o.optString("webformatURL").ifBlank { full },
                fullUrl = full,
                width = o.optInt("imageWidth"),
                height = o.optInt("imageHeight"),
                sourcePage = o.optString("pageURL")
            )
        }
        val totalHits = root.optInt("totalHits", 0)
        return WebImageSearchResult(images, page, page * PAGE_SIZE < totalHits)
    }

    // ── HTTP ────────────────────────────────────────────────────────────────────

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
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
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

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
        401, 403 -> "Chave de API inválida ou sem permissão."
        429 -> "Limite de buscas do provedor atingido. Tente novamente em alguns minutos."
        in 500..599 -> "O provedor está indisponível no momento (HTTP $code)."
        else -> "Falha na busca (HTTP $code)."
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }
}
