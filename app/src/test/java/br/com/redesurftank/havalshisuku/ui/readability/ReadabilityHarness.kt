package br.com.redesurftank.havalshisuku.ui.readability

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.theme.HavalShisukuTheme
import br.com.redesurftank.havalshisuku.ui.theme.IbmPlexSans

/**
 * Infra dos snapshots de legibilidade (Roborazzi + Robolectric, JVM puro).
 *
 * Tela da central: 1920x720 landscape. A densidade fisica real da central e "A confirmar"; o fixture usa
 * mdpi/fontScale 1.0 (1dp = 1px) porque o app so usa unidades logicas (dp/sp).
 *
 * Nao inicia [App.onCreate] (servicos, WebView, migracoes): o `Application` do teste e o
 * `android.app.Application` do Robolectric e [installAppSingleton] so preenche o singleton estatico
 * para que telas que leem `App.getDeviceProtectedContext()` possam ser compostas.
 */
internal object ReadabilityHarness {
    const val OUTPUT_DIR = "build/outputs/readability-snapshots"

    fun path(name: String) = "$OUTPUT_DIR/$name.png"

    fun installAppSingleton(app: Application) {
        val appCls = App::class.java
        appCls.getDeclaredField("sApplication").apply { isAccessible = true }.set(null, app)
        appCls.getDeclaredField("deviceProtectedContext").apply { isAccessible = true }.set(null, null)
    }
}

/** Moldura 1920x720 igual ao AppNavigation: rail de 260dp (vazio) + area de conteudo com padding 16dp e IBM Plex Sans. */
@Composable
internal fun CarFrame(content: @Composable () -> Unit) {
    HavalShisukuTheme(darkTheme = true, dynamicColor = false, readableText = true) {
        Row(Modifier.size(1920.dp, 720.dp).background(ImpTokens.Ground)) {
            Spacer(Modifier.width(260.dp).fillMaxHeight().background(Color(0xFF0D0E12)))
            Box(
                Modifier.weight(1f).fillMaxHeight().background(ImpTokens.Ground)
                    .padding(AppDimensions.ContentPadding)
            ) {
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = IbmPlexSans)
                ) { content() }
            }
        }
    }
}
