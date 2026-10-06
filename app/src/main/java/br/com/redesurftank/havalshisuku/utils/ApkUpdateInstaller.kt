package br.com.redesurftank.havalshisuku.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApkUpdateInstaller {
    suspend fun downloadUpdateApk(
            context: Context,
            url: String,
            onProgress: suspend (Float) -> Unit = {}
    ): File {
        return withContext(Dispatchers.IO) {
            val file = File(context.getExternalFilesDir(null), "update.apk")
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                val length = connection.contentLength
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        var total = 0
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            total += bytesRead
                            if (length > 0) {
                                withContext(Dispatchers.Main) {
                                    onProgress(total.toFloat() / length)
                                }
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            file
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun buildInstallIntent(context: Context, file: File): Intent {
        val uri =
                FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildUnknownSourcesIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}
