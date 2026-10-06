package br.com.redesurftank.havalshisuku.ui.readability

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.FeaturesHubScreen
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.components.SettingCard
import br.com.redesurftank.havalshisuku.ui.components.TripConsistencyScreen
import br.com.redesurftank.havalshisuku.ui.screens.AutoMountRow
import br.com.redesurftank.havalshisuku.ui.screens.BasicSettingsTab
import br.com.redesurftank.havalshisuku.ui.screens.CardButton
import br.com.redesurftank.havalshisuku.ui.screens.ClusterBackgroundSettingsDialog
import br.com.redesurftank.havalshisuku.ui.screens.CurrentValuesTab
import br.com.redesurftank.havalshisuku.ui.screens.DisplayAppConfigDialog
import br.com.redesurftank.havalshisuku.ui.screens.FeatureCard
import br.com.redesurftank.havalshisuku.ui.screens.ImpulseHomeSetupDialog
import br.com.redesurftank.havalshisuku.ui.screens.InformacoesTab
import br.com.redesurftank.havalshisuku.ui.screens.InstallAppsTab
import br.com.redesurftank.havalshisuku.ui.screens.ProblemReportTab
import br.com.redesurftank.havalshisuku.ui.screens.StartupSlotRow
import br.com.redesurftank.havalshisuku.ui.screens.TelasTab
import br.com.redesurftank.havalshisuku.ui.components.GroupedSettingsLayout
import br.com.redesurftank.havalshisuku.ui.components.SettingItem
import br.com.redesurftank.havalshisuku.ui.components.SettingsGroups
import br.com.redesurftank.havalshisuku.ui.screens.performanceSettingItems
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Snapshots JVM de legibilidade do app (tela da central 1920x720). Ver [ReadabilityHarness].
 * Gravar: ./gradlew :app:testDebugUnitTest --tests 'ReadabilitySnapshotTest' -Proborazzi.test.record=true
 * Saida: app/build/outputs/readability-snapshots (um PNG por teste).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "w1920dp-h720dp-land-mdpi")
class ReadabilitySnapshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Before
    fun setUp() {
        ReadabilityHarness.installAppSingleton(ApplicationProvider.getApplicationContext())
    }

    private fun snap(name: String, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { CarFrame(content) }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.onRoot().captureRoboImage(ReadabilityHarness.path(name))
    }

    private fun snapDialog(name: String, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { CarFrame(content) }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.onNode(isDialog()).captureRoboImage(ReadabilityHarness.path(name))
    }

    private fun prefs() =
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    @Test
    fun settingsGrouped() = snap("01-settings-grouped") { GroupedSettingsLayout(fakeSettingItems()) }

    @Test
    fun settingsPerformance() = snap("02-settings-performance") {
        prefs().edit().putBoolean(SharedPreferencesKeys.ADVANCE_USE.key, true).commit()
        GroupedSettingsLayout(performanceSettingItems(prefs()))
    }

    @Test
    fun featuresHub() = snap("03-features-hub") { FeaturesHubScreen() }

    @Test
    fun settingCards() = snap("04-setting-cards") {
        Column {
            SettingCard("Fechar janelas ao desligar", "Fecha os vidros quando o carro é desligado.", true, {})
            SettingCard("Recurso indisponível", "Não disponível neste veículo.", false, {}, enabled = false)
            SettingCard("Volume por velocidade", "Ajusta o volume conforme a velocidade.", true, {}, sliderValue = 40, sliderRange = 0..100, sliderStep = 5, onSliderChange = {}, sliderLabel = "Sensibilidade: 40%")
        }
    }

    @Test
    fun featureCards() = snap("05-feature-cards") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Textos iguais aos de InstallAppsScreen (fixture; a tela real depende de PackageManager/rede).
            Box(Modifier.weight(1f)) { FeatureCard(
                icon = Icons.Default.Shield, iconTint = ImpTokens.Accent, highlighted = true,
                title = "Android Auto Patch",
                subtitle = "Melhora a projeção do Android Auto no cluster.",
                status = "Ativo", statusTint = ImpTokens.Accent, subtitleBelowTitle = true,
                extra = { AutoMountRow(checked = true, onCheckedChange = {}) }
            ) { CardButton("Desinstalar", Color(0xFF3A3F47)) {} } }
            Box(Modifier.weight(1f)) { FeatureCard(
                icon = Icons.Default.PhoneIphone, iconTint = Color.White, highlighted = false,
                title = "Apple CarPlay Patch",
                subtitle = "Melhora a projeção do CarPlay no cluster.",
                status = "Não instalado", subtitleBelowTitle = true
            ) { CardButton("Instalar", ImpTokens.Accent) {} } }
            Box(Modifier.weight(1f)) { FeatureCard(
                icon = Icons.Default.DirectionsCar, iconTint = Color.White, highlighted = false,
                title = "Impulse Launcher",
                subtitle = "Painel 3D com widgets e controles",
                status = "v1.2.3"
            ) { CardButton("Abrir", ImpTokens.Accent) {} } }
            Box(Modifier.weight(1f)) { FeatureCard(
                icon = Icons.Default.Settings, iconTint = Color.White, highlighted = false,
                title = "Abrir ao ligar",
                subtitle = "",
                status = null,
                extra = { StartupSlotRow(label = "Tela principal", packageName = "") }
            ) { CardButton("Editar", ImpTokens.Accent) {} } }
        }
    }

    @Test
    fun impulseHomeSetupDialog() = snapDialog("06-impulse-home-setup-dialog") {
        ImpulseHomeSetupDialog(canOpen = true, onDismiss = {})
    }

    @Test
    fun settingsBasic() = snap("07-settings-basic") { BasicSettingsTab() }

    @Test
    fun settingsBasicGroups() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { CarFrame { BasicSettingsTab() } }
        composeRule.mainClock.advanceTimeBy(1000)
        listOf(
            "Climatização & conforto" to "07b-settings-basic-climate",
            "Conforto & conveniência" to "07c-settings-basic-comfort",
            "Segurança & avisos" to "07d-settings-basic-safety",
            "Tela & som" to "07e-settings-basic-display",
            "Recursos & integração" to "07f-settings-basic-features",
            "Performance" to "07g-settings-basic-performance",
        ).forEach { (group, file) ->
            composeRule.onAllNodesWithText(group).onFirst().performClick()
            composeRule.mainClock.advanceTimeBy(1500)
            composeRule.onRoot().captureRoboImage(ReadabilityHarness.path(file))
        }
    }

    @Test
    fun clusterBackgroundDialog() = snapDialog("09b-cluster-background-dialog") {
        val scope = rememberCoroutineScope()
        ClusterBackgroundSettingsDialog(
            prefs = prefs(),
            localAssetList = listOf("bg1.jpg", "bg2.jpg"),
            scope = scope,
            onDismiss = {}
        )
    }

    @Test
    fun displayAppConfigDialog() = snapDialog("09c-display-app-config-dialog") {
        DisplayAppConfigDialog(existingConfig = null, onDismiss = {}, onSave = {})
    }

    @Test
    fun tripConsistency() = snap("08-trip-consistency") { TripConsistencyScreen() }

    @Test
    fun telas() = snap("09-telas") { TelasTab() }

    @Test
    fun informacoes() = snap("10-informacoes") { InformacoesTab() }

    @Test
    fun installApps() = snap("11-install-apps") { InstallAppsTab() }

    @Test
    fun problemReport() = snap("12-problem-report") { ProblemReportTab() }

    @Test
    fun currentValues() = snap("13-current-values") { CurrentValuesTab() }
}

internal fun fakeSettingItems(): List<SettingItem> = listOf(
    SettingItem("Fechar janelas ao desligar", "Fecha os vidros quando o carro é desligado.", true, {}, group = SettingsGroups.SHUTDOWN),
    SettingItem("Fechar teto solar ao desligar", "Fecha o teto solar quando o carro é desligado.", false, {}, group = SettingsGroups.SHUTDOWN),
    SettingItem("Volume por velocidade", "Ajusta o volume conforme a velocidade.", true, {}, sliderValue = 40, sliderRange = 0..100, sliderStep = 5, onSliderChange = {}, sliderLabel = "Sensibilidade: 40%", group = SettingsGroups.SPEED),
    SettingItem("Recurso indisponível", "Não disponível neste veículo.", false, {}, enabled = false, group = SettingsGroups.SPEED),
    SettingItem("Modo de condução", "", true, {}, group = SettingsGroups.DRIVE),
    SettingItem("Abrir ajustes avançados", "Toque para abrir.", false, {}, hideSwitch = true, group = SettingsGroups.FEATURES),
)
