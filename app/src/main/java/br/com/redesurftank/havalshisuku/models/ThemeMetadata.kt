package br.com.redesurftank.havalshisuku.models

data class ThemeMetadata(
    val name: String,
    val description: String,
    val version: String,
    val thumbnailUrl: String, // Can be a local path or a remote URL
    val mainFile: String = "index.html",
    val folderName: String = "", // Used to identify the local folder or remote path
    val isLocal: Boolean = false,
    val isDownloaded: Boolean = false,
    val size: Long = 0,
    val x: Int? = null,
    val y: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val hasUpdate: Boolean = false,
    val remoteSha: String? = null,
    val remoteSize: Long? = null
)
