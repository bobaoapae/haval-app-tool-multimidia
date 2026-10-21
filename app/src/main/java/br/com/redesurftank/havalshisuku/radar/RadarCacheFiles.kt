package br.com.redesurftank.havalshisuku.radar

import java.io.File
import java.io.IOException

/** Same-directory rename replaces the old file atomically on Android. Never delete it first. */
internal object RadarCacheFiles {
    fun write(file: File, bytes: ByteArray, rename: (File, File) -> Boolean = { a, b -> a.renameTo(b) }) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeBytes(bytes)
            if (!rename(tmp, file)) throw IOException("rename failed for ${file.name}")
        } finally {
            tmp.delete()
        }
    }
}
