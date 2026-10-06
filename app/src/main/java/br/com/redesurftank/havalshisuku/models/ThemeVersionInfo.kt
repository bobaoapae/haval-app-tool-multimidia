package br.com.redesurftank.havalshisuku.models

data class ThemeVersionInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long = 0,
    val updatedAt: String = ""
)
