package br.com.redesurftank.havalshisuku.models

data class ThemeConfig(
    val id: String,
    val label: String,
    val type: String, // "boolean", "text", "number", "combo"
    val defaultValue: String,
    val stateVariable: String,
    val options: List<String> = emptyList()
)
