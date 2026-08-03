package br.com.redesurftank.havalshisuku.models

/**
 * Per-display-mode app bounds declared by a theme:
 *
 * ```xml
 * <DisplayModes>
 *     <DisplayMode name="Mapa" x="0" y="0" width="1920" height="720"/>
 * </DisplayModes>
 * ```
 *
 * [name] must match one of the theme's display mode names (Normal, Reduzido,
 * Clean, Mapa, ...). Modes without an entry fall back to AppDefaultPosition.
 */
data class ThemeDisplayMode(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
