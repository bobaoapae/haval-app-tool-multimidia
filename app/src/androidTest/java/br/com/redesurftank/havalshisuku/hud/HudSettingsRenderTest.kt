package br.com.redesurftank.havalshisuku.hud

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.HudLayoutSection
import br.com.redesurftank.havalshisuku.ui.components.HudNavigationSettingsDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Screenshots of the HUD settings dialog, one per tab, and of the Telas style cards, to review
 * them without the car (owner, 2026-10-25). Set the emulator to the head unit's screen first:
 * `adb shell wm size 1920x720` and `adb shell wm density 160`. Saved next to HudRenderTest's.
 */
@RunWith(AndroidJUnit4::class)
class HudSettingsRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "hud-renders").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        Thread.sleep(800) // the preview's crossfade
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { save(name, it) }
    }

    /** The Telas cards (owner, 2026-10-27): Original, Personalizado second, then the styles. */
    @Test
    fun renderStyleCards() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("hud_render_test", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString(SharedPreferencesKeys.HUD_PRESET.key, HudOverlayModel.Preset.CUSTOM.key)
            .putString(SharedPreferencesKeys.HUD_CUSTOM_BASE.key, HudOverlayModel.Preset.MAP.key)
            .putString(SharedPreferencesKeys.HUD_GAUGES.key, HudOverlayModel.Gauges.BARS.key)
            .commit()
        compose.setContent { HudLayoutSection(modifier = Modifier.width(1700.dp), prefs = prefs) }
        shoot("8_styles_1_cards")
        compose.onNodeWithContentDescription("Configurar estilo Personalizado").performClick()
        shoot("8_styles_2_custom_dialog")
    }

    @Test
    fun renderSettingsDialogMap() {
        compose.setContent {
            var config by remember { mutableStateOf(HudOverlayModel.Preset.MAP.config!!) }
            HudNavigationSettingsDialog(
                title = "Mapa 3D", config = config, defaults = HudOverlayModel.Preset.MAP.config!!, fixed = true,
                onChange = { config = it }, onDismiss = {}
            )
        }
        for ((i, tab) in listOf("Navegação", "Manobra", "Indicadores", "Estilo").withIndex()) {
            compose.onNodeWithText(tab).performClick()
            shoot("7_settings_map_${i + 1}_${tab.lowercase()}")
        }
    }

    /** The Cores tab (owner, 2026-10-26): the style's colours, a few picks, then Azul. */
    @Test
    fun renderSettingsDialogColors() {
        compose.setContent {
            var config by remember { mutableStateOf(HudOverlayModel.Preset.MAP.config!!) }
            HudNavigationSettingsDialog(
                title = "Personalizado · a partir de Mapa 3D", config = config, defaults = HudOverlayModel.Preset.MAP.config!!,
                fixed = false, onChange = { config = it }, onDismiss = {}, onDiscard = {}
            )
        }
        compose.onNodeWithText("Cores").performClick()
        shoot("7_settings_colors_1_default")
        // A pick shows in the preview (it moves to the example where the part is, ~300 ms later).
        compose.onNodeWithText("EV").performClick()
        compose.onNodeWithContentDescription("Laranja").performClick()
        shoot("7_settings_colors_2_ev_orange")
        compose.onNodeWithText("Seta de direção").performClick()
        compose.onNodeWithContentDescription("Rosa").performClick()
        shoot("7_settings_colors_3_chevron_pink")
        compose.onNodeWithText("Azul").performClick()
        shoot("7_settings_colors_4_blue")
    }

    @Test
    fun renderSettingsDialogBasic() {
        compose.setContent {
            var config by remember { mutableStateOf(HudOverlayModel.Preset.BASIC.config!!) }
            HudNavigationSettingsDialog(
                title = "Navegação Básica", config = config, defaults = HudOverlayModel.Preset.BASIC.config!!, fixed = true,
                onChange = { config = it }, onDismiss = {}
            )
        }
        for ((i, tab) in listOf("Navegação", "Manobra", "Estilo").withIndex()) {
            compose.onNodeWithText(tab).performClick()
            shoot("7_settings_basic_${i + 1}_${tab.lowercase()}")
        }
    }
}
