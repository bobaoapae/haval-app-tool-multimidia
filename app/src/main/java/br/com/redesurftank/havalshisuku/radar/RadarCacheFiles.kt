package br.com.redesurftank.havalshisuku.radar

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The new cache is written whole to a temp file first, then moved over the old one, so a
 * reader never sees a partial file and a failed move leaves the old one. Never delete it first.
 */
internal object RadarCacheFiles {
    fun write(file: File, bytes: ByteArray, rename: (File, File) -> Boolean = ::replace) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeBytes(bytes)
            if (!rename(tmp, file)) throw IOException("rename failed for ${file.name}")
        } finally {
            tmp.delete()
        }
    }

    /**
     * Moves [from] over [to], replacing it atomically where the filesystem can. File.renameTo
     * did that on Android (Linux rename(2)) but refuses an existing target on Windows, which
     * failed the unit test there.
     */
    private fun replace(from: File, to: File): Boolean = try {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (_: IOException) {
        false
    }
}
