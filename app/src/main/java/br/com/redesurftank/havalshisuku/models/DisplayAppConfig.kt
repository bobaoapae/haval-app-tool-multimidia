package br.com.redesurftank.havalshisuku.models

data class DisplayAppConfig(
    val packageName: String,
    val activityName: String,
    val displayId: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val substituteIcon: String? = null,
    val iconColor: String? = null,
    val customName: String? = null,
    val forceFocus: Boolean = false,
    val overrideThemeDimensions: Boolean = false
)

