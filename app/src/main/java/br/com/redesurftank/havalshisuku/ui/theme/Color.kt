package br.com.redesurftank.havalshisuku.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ---------------------------------------------------------------------------
// Paleta semântica de texto — legibilidade em relance na central (1920x720, ~65 cm).
// Contraste WCAG calculado sobre o painel do app (Container #12141A / Card #13151A no escuro,
// #F8FAFC no claro). Hierarquia por COR e PESO, nunca por opacidade do texto.
//
//   Escuro  primário #F5F7FA 17,2:1 | secundário #C7D1DB 11,9:1 | terciário #AAB7C4 9,0:1 | desativado #8795A3 6,0:1
//   Claro   primário #111B27 16,6:1 | secundário #3D4B5A  8,5:1 | terciário #526171 6,1:1 | desativado #667283 4,7:1
//
// `AppColors` e `ImpTokens` (ui/components) são aliases constantes da paleta escura: continuam
// utilizáveis em Canvas/callbacks (fora de composição). Em composables novos prefira
// `LocalImpulseTextColors.current`, que acompanha o tema claro/escuro.
// ---------------------------------------------------------------------------
@Immutable
data class ImpulseTextColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    /** Texto de item desativado: continua legível (>= 4,5:1), só perde para o terciário. */
    val disabled: Color,
    /** Alerta/erro em texto (>= 4,5:1 sobre o painel). */
    val danger: Color
)

val DarkImpulseTextColors = ImpulseTextColors(
    primary = Color(0xFFF5F7FA),
    secondary = Color(0xFFC7D1DB),
    tertiary = Color(0xFFAAB7C4),
    disabled = Color(0xFF8795A3),
    danger = Color(0xFFFF6B76)
)

val LightImpulseTextColors = ImpulseTextColors(
    primary = Color(0xFF111B27),
    secondary = Color(0xFF3D4B5A),
    tertiary = Color(0xFF526171),
    disabled = Color(0xFF667283),
    danger = Color(0xFFB3261E)
)

val LocalImpulseTextColors = staticCompositionLocalOf { DarkImpulseTextColors }
