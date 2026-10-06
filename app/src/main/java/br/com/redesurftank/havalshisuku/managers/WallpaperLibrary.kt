package br.com.redesurftank.havalshisuku.managers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import br.com.redesurftank.App
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The user's wallpaper repository: images pushed from the phone (Wi-Fi sync) plus images
 * favorited from the web search, all living in the same folder so they can be picked offline.
 *
 * Web images are downloaded and re-encoded to a cluster-friendly size — the head unit has to
 * decode these with [android.graphics.drawable.Drawable.createFromPath], and Wallhaven originals
 * are routinely 4K PNGs of 10 MB+.
 */
object WallpaperLibrary {

    private const val TAG = "WallpaperLibrary"
    private const val INDEX_FILE_NAME = "wallpaper_library_index.json"
    private const val MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024
    private const val MAX_DIMENSION = 2048
    private const val JPEG_QUALITY = 92
    private const val USER_AGENT = "HavalImpulse/1.0 (cluster wallpaper picker)"

    /** Metadata for one favorited web image, keyed by the file it was saved to. */
    data class WebEntry(
        val fileName: String,
        val providerId: String,
        val remoteId: String,
        val title: String,
        val sourcePage: String,
        val sourceUrl: String,
        val addedAt: Long
    )

    data class LibraryItem(
        val file: File,
        val title: String,
        val providerLabel: String?,
        val sourcePage: String,
        val addedAt: Long
    ) {
        val isFromWeb: Boolean get() = providerLabel != null
    }

    private fun indexFile(): File =
        File(App.getDeviceProtectedContext().filesDir, INDEX_FILE_NAME)

    private fun readIndex(): MutableMap<String, WebEntry> {
        val file = indexFile()
        if (!file.exists()) return mutableMapOf()
        return try {
            val type = object : TypeToken<Map<String, WebEntry>>() {}.type
            Gson().fromJson<Map<String, WebEntry>>(file.readText(), type)
                ?.toMutableMap() ?: mutableMapOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading library index", e)
            mutableMapOf()
        }
    }

    private fun writeIndex(index: Map<String, WebEntry>) {
        try {
            indexFile().writeText(Gson().toJson(index))
        } catch (e: Exception) {
            Log.e(TAG, "Error writing library index", e)
        }
    }

    /** Everything the user can pick offline: phone uploads first-class next to web favorites. */
    fun list(): List<LibraryItem> {
        val index = readIndex()
        return BackgroundSyncServer.listUploadedImages().map { file ->
            val entry = index[file.name]
            LibraryItem(
                file = file,
                title = entry?.title?.takeIf { it.isNotBlank() }
                    ?: file.name.substringBeforeLast("."),
                providerLabel = entry?.let { WebImageProvider.fromId(it.providerId).label },
                sourcePage = entry?.sourcePage.orEmpty(),
                addedAt = entry?.addedAt ?: file.lastModified()
            )
        }
    }

    /** File name a given web image is (or would be) stored under. */
    fun fileNameFor(image: WebImage): String {
        val safeId = image.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
        return "web_${image.provider.id}_$safeId.jpg"
    }

    fun savedFileFor(image: WebImage): File? {
        val file = File(BackgroundSyncServer.getUploadsDir(), fileNameFor(image))
        return file.takeIf { it.exists() }
    }

    fun isSaved(image: WebImage): Boolean = savedFileFor(image) != null

    /** Downloads, downscales and registers a web image. Returns the local file. */
    suspend fun favorite(image: WebImage): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = download(image.fullUrl)
            val bitmap = decodeSampled(bytes)
                ?: throw IOException("Formato de imagem não suportado.")
            val target = File(BackgroundSyncServer.getUploadsDir(), fileNameFor(image))
            try {
                FileOutputStream(target).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                        throw IOException("Falha ao salvar a imagem.")
                    }
                }
            } finally {
                bitmap.recycle()
            }

            val index = readIndex()
            index[target.name] = WebEntry(
                fileName = target.name,
                providerId = image.provider.id,
                remoteId = image.id,
                title = image.title.ifBlank { image.provider.label },
                sourcePage = image.sourcePage,
                sourceUrl = image.fullUrl,
                addedAt = System.currentTimeMillis()
            )
            writeIndex(index)
            target
        }.onFailure { Log.e(TAG, "Error favoriting ${image.fullUrl}", it) }
    }

    fun delete(file: File): Boolean {
        val deleted = BackgroundSyncServer.deleteUploadedImage(file.name)
        if (deleted) {
            val index = readIndex()
            if (index.remove(file.name) != null) writeIndex(index)
        }
        return deleted
    }

    private fun download(url: String): ByteArray {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.errorStream?.close()
                throw IOException("Download falhou (HTTP $code).")
            }
            val declared = connection.contentLength
            if (declared > MAX_DOWNLOAD_BYTES) {
                throw IOException("Imagem muito grande para o sistema do carro.")
            }
            val buffer = ByteArrayOutputStream(if (declared > 0) declared else 1 shl 16)
            val chunk = ByteArray(1 shl 15)
            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                    if (buffer.size() > MAX_DOWNLOAD_BYTES) {
                        throw IOException("Imagem muito grande para o sistema do carro.")
                    }
                }
            }
            return buffer.toByteArray()
        } finally {
            connection?.disconnect()
        }
    }

    /** Decodes with subsampling so the longest edge stays within [MAX_DIMENSION]. */
    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
