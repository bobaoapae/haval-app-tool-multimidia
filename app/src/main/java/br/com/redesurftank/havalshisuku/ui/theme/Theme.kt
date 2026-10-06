package br.com.redesurftank.havalshisuku.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val ReadableDarkColorScheme = DarkColorScheme.copy(
    onBackground = DarkImpulseTextColors.primary,
    onSurface = DarkImpulseTextColors.primary,
    onSurfaceVariant = DarkImpulseTextColors.secondary
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

private val ReadableLightColorScheme = LightColorScheme.copy(
    onBackground = LightImpulseTextColors.primary,
    onSurface = LightImpulseTextColors.primary,
    onSurfaceVariant = LightImpulseTextColors.secondary
)

@Composable
fun HavalShisukuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    // true = escala/paleta de texto legível (app de configurações). false (default) preserva o
    // visual original das demais hosts (BottomBarService, ImpulseDashboardActivity).
    readableText: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> if (readableText) ReadableDarkColorScheme else DarkColorScheme
        else -> if (readableText) ReadableLightColorScheme else LightColorScheme
    }

    CompositionLocalProvider(
        LocalImpulseTextColors provides
            if (darkTheme) DarkImpulseTextColors else LightImpulseTextColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (readableText) ReadableTypography else LegacyTypography,
            content = content
        )
    }
}