package br.com.redesurftank.havalshisuku.utils

import java.io.File

/**
 * Holds downloaded APKs only for the current process. [clear] runs from [br.com.redesurftank.App]
 * on every start, so a restart drops them. A second install in the same session reuses the file.
 *
 * Downloads land in a `.part` file and only take the cache name in [publish], so a stopped
 * download is not offered again as a complete APK.
 */
object SessionApkCache {
    const val DIR_NAME = "session-apks"

    fun directory(cacheDir: File): File = File(cacheDir, DIR_NAME)

    @JvmStatic
    fun clear(cacheDir: File, externalFilesDir: File? = null) {
        directory(cacheDir).deleteRecursively()
        externalFilesDir?.listFiles()?.orEmpty()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) file.delete()
        }
    }

    fun apk(cacheDir: File, key: String): File {
        val safe = key.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        val dir = directory(cacheDir)
        dir.mkdirs()
        return File(dir, "$safe.apk")
    }

    fun partial(finished: File): File = File(finished.parentFile, finished.name + ".part")

    /** Moves a finished download onto the cache name. An empty or missing part stays unpublished. */
    fun publish(partial: File, finished: File): Boolean {
        if (!partial.isFile || partial.length() <= 0L) return false
        if (finished.exists() && !finished.delete()) return false
        return partial.renameTo(finished)
    }

    fun keyForUrl(url: String): String = ImpulseHomeUpdater.sha256Hex(url.toByteArray(Charsets.UTF_8))

    /** True when [file] is a finished download of [sha256]. A missing hash only checks the file exists. */
    fun matches(file: File, sha256: String? = null): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (sha256.isNullOrEmpty()) return true
        return ImpulseHomeUpdater.sha256Hex(file) == sha256.lowercase()
    }
}
