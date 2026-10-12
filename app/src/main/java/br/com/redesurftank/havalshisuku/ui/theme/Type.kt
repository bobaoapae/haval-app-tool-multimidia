package br.com.redesurftank.havalshisuku.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.R

val Michroma = FontFamily(
    Font(R.font.michroma)
)

// IBM Plex Sans (OFL) — corpo/controles do redesign (Rodada 15). Tem tabular figures.
val IbmPlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold)
)

/**
 * Escala de texto para leitura em relance na central (1920x720). Rodada de legibilidade:
 * nenhum texto funcional novo abaixo de 18sp; corpo 22sp; rótulos 20sp; títulos 24-28sp.
 * Valores grandes já adequados (velocidade, score, valores de widgets) ficam como estão.
 */
object ImpulseTextSizes {
    /** Texto auxiliar mínimo permitido (hint, status secundário). */
    val BodyCompact = 18.sp
    /** Rótulos, botões, descrições de configuração. */
    val Label = 20.sp
    /** Corpo padrão. */
    val Body = 22.sp
    /** Título de seção/grupo. */
    val Section = 24.sp
    /** Título de tela/card principal. */
    val Title = 28.sp
    val Unit = 20.sp
    val Value = 40.sp
}

object ImpulseTextWeights {
    val Body = FontWeight.Medium
    val Label = FontWeight.SemiBold
    val Value = FontWeight.Bold
}

/**
 * Tipografia ORIGINAL (3 roles). Continua sendo o default de [HavalShisukuTheme] para as superfícies
 * que não são o app de configurações (barra inferior / Impulse Drive), cujos layouts de tamanho fixo
 * não foram revalidados com a escala legível.
 */
val LegacyTypography = Typography(
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Altura de linha RELATIVA ao tamanho: muitos `Text(fontSize = ...)` herdam o lineHeight do estilo
 * do tema; em `sp` fixo um texto menor ficaria com entrelinha exagerada.
 */
private val ReadableLineHeight = 1.35.em

/** Tipografia legível do app de configurações (escala [ImpulseTextSizes]). Ativada com `readableText = true`. */
val ReadableTypography = Typography(
    bodyLarge = TextStyle(
        fontWeight = ImpulseTextWeights.Body,
        fontSize = ImpulseTextSizes.Body,
        lineHeight = ReadableLineHeight,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = ImpulseTextWeights.Body,
        fontSize = ImpulseTextSizes.Label,
        lineHeight = ReadableLineHeight,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontWeight = ImpulseTextWeights.Body,
        fontSize = ImpulseTextSizes.BodyCompact,
        lineHeight = ReadableLineHeight,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = ImpulseTextWeights.Label,
        fontSize = ImpulseTextSizes.Title,
        lineHeight = ReadableLineHeight,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = ImpulseTextWeights.Label,
        fontSize = ImpulseTextSizes.Section,
        lineHeight = ReadableLineHeight,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontWeight = ImpulseTextWeights.Label,
        fontSize = ImpulseTextSizes.Label,
        lineHeight = ReadableLineHeight,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontWeight = ImpulseTextWeights.Label,
        fontSize = ImpulseTextSizes.Label,
        lineHeight = ReadableLineHeight,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontWeight = ImpulseTextWeights.Label,
        fontSize = ImpulseTextSizes.Label,
        lineHeight = ReadableLineHeight,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontWeight = ImpulseTextWeights.Label,
        fontSize = ImpulseTextSizes.BodyCompact,
        lineHeight = ReadableLineHeight,
        letterSpacing = 0.2.sp
    )
)
