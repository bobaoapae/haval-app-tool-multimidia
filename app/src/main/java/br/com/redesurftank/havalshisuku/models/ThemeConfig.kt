package br.com.redesurftank.havalshisuku.models

data class ThemeConfig(
    val id: String,
    val label: String,
    val type: String, // "boolean", "text", "number", "combo", "color"
    val defaultValue: String,
    val stateVariable: String,
    val options: List<String> = emptyList(),
    /**
     * Optional tab this setting belongs to in the settings dialog. Blank means the
     * default "Geral" tab, which is what every theme written before <group> existed
     * gets - see ThemeSettingsDialog.
     */
    val group: String = ""
)
