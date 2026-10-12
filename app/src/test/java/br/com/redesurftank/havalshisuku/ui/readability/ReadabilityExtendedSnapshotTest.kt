package br.com.redesurftank.havalshisuku.ui.readability

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.TripConsistencyClassification
import br.com.redesurftank.havalshisuku.models.TripConsistencyEvent
import br.com.redesurftank.havalshisuku.models.TripConsistencyEventType
import br.com.redesurftank.havalshisuku.models.TripConsistencyMetrics
import br.com.redesurftank.havalshisuku.models.TripConsistencyReport
import br.com.redesurftank.havalshisuku.models.TripConsistencySession
import br.com.redesurftank.havalshisuku.models.TripConsistencyStatus
import com.google.gson.Gson
import br.com.redesurftank.havalshisuku.ui.components.TripConsistencyScreen
import br.com.redesurftank.havalshisuku.ui.navigation.MainScreen
import br.com.redesurftank.havalshisuku.ui.screens.BasicSettingsTab
import br.com.redesurftank.havalshisuku.ui.screens.CurrentValuesTab
import br.com.redesurftank.havalshisuku.ui.screens.DisplayAppConfigDialog
import br.com.redesurftank.havalshisuku.ui.screens.ImpulseHomeSetupDialog
import br.com.redesurftank.havalshisuku.ui.screens.TelasTab
import br.com.redesurftank.havalshisuku.ui.theme.HavalShisukuTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cobertura extra dos snapshots de legibilidade (estados com dados fake, rolagem e navegação real).
 * Só usa APIs existentes antes e depois da rodada (o "before" é gerado a partir do commit base).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "w1920dp-h720dp-land-mdpi")
class ReadabilityExtendedSnapshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Before
    fun setUp() {
        ReadabilityHarness.installAppSingleton(ApplicationProvider.getApplicationContext())
    }

    private fun prefs() =
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    private fun start(content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent(content)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1000)
    }

    private fun snap(name: String, content: @Composable () -> Unit) {
        start { CarFrame(content) }
        composeRule.onRoot().captureRoboImage(ReadabilityHarness.path(name))
    }

    private fun settle(ms: Long = 1500) = composeRule.mainClock.advanceTimeBy(ms)

    /** Navegação real (rail 260dp + recuo de 100dp em 1920 de largura), sem a moldura de teste. */
    @Test
    fun navigationSettings() {
        start {
            HavalShisukuTheme(darkTheme = true, dynamicColor = false, readableText = true) {
                Box(Modifier.size(1920.dp, 720.dp)) { MainScreen() }
            }
        }
        composeRule.onRoot().captureRoboImage(ReadabilityHarness.path("14-navigation-settings"))
    }

    @Test
    fun navigationFeatures() {
        start {
            HavalShisukuTheme(darkTheme = true, dynamicColor = false, readableText = true) {
                Box(Modifier.size(1920.dp, 720.dp)) { MainScreen(initialScreen = "Recursos") }
            }
        }
        composeRule.onRoot().captureRoboImage(ReadabilityHarness.path("14b-navigation-features"))
    }

    @Test
    fun tripActive() {
        // Load a fixture through the manager's existing persistence path; no wall clock reads.
        val timestamp = "2026-08-06T12:00:00Z"
        val session = TripConsistencySession(
            id = "readability-session",
            status = TripConsistencyStatus.ACTIVE,
            startedAt = timestamp,
            createdAt = timestamp,
            updatedAt = timestamp,
            telemetryWarning = true
        )
        prefs().edit().putString("tripConsistency.activeSession", Gson().toJson(session)).commit()
        snap("15-trip-active") { TripConsistencyScreen() }
    }

    @Test
    fun tripHistoryAndRules() {
        val timestamp = "2026-08-06T12:00:00Z"
        val classification = TripConsistencyClassification.SMOOTH
        val report = TripConsistencyReport(
            id = "readability-report",
            sessionId = "readability-session",
            startedAt = timestamp,
            endedAt = timestamp,
            durationSeconds = 0,
            score = 100,
            classification = classification,
            classificationLabel = classification.label,
            summaryText = classification.summary,
            metrics = TripConsistencyMetrics(),
            events = listOf(TripConsistencyEvent(
                id = "readability-manual-end",
                type = TripConsistencyEventType.MANUAL_END,
                timestamp = timestamp,
                label = "Analise encerrada manualmente"
            )),
            createdAt = timestamp
        )
        prefs().edit().putString("tripConsistency.reportHistory", Gson().toJson(listOf(report))).commit()
        start { CarFrame { TripConsistencyScreen() } }
        composeRule.onRoot().captureRoboImage(ReadabilityHarness.path("15b-trip-history"))
        // O rótulo mudou de "Como funciona" para "Regras" na rodada de legibilidade.
        val rulesLabel =
            if (composeRule.onAllNodesWithText("Regras").fetchSemanticsNodes().isEmpty()) "Como funciona"
            else "Regras"
        composeRule.onNodeWithText(rulesLabel).performClick()
        settle()
        composeRule.onRoot().captureRoboImage(ReadabilityHarness.path("15c-trip-rules"))
    }

    @Test
    fun currentValuesRows() {
        val sm = ServiceManager.getInstance()
        @Suppress("UNCHECKED_CAST")
        val cache = ServiceManager::class.java.getDeclaredField("dataCache")
            .apply { isAccessible = true }.get(sm) as MutableMap<String, String>
        cache["car.basic.vehicle_speed"] = "72"
        cache["car.basic.total_odometer"] = "12843"
        cache["car.hvac.driver_temperature"] = "22.0"
        cache["car.hvac.fan_speed"] = "3"
        cache["car.energy.battery_state_of_charge"] = "68"
        cache["car.basic.chassis_wheel_pressure_front_left_with_a_very_long_key_name"] = "2.6"
        cache["car.drive.mode"] = "Eco / Conforto / Esportivo (valor longo para validar corte)"
        snap("16-current-values-rows") { CurrentValuesTab() }
    }

    @Test
    fun displayAppConfigDialogScrolled() {
        start { CarFrame { DisplayAppConfigDialog(existingConfig = null, onDismiss = {}, onSave = {}) } }
        composeRule.onNode(isDialog()).performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.05f) }
        settle()
        composeRule.onNode(isDialog()).captureRoboImage(ReadabilityHarness.path("17-display-app-config-scrolled"))
    }

    @Test
    fun settingsPerformanceScrolled() {
        start { CarFrame { BasicSettingsTab() } }
        composeRule.onAllNodesWithText("Performance").onFirst().performClick()
        settle()
        composeRule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(9)
        settle()
        composeRule.onRoot().captureRoboImage(ReadabilityHarness.path("18-settings-performance-scrolled"))
    }

    @Test
    fun setupDialogBarAlreadyActive() {
        prefs().edit().putBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, true).commit()
        start { CarFrame { ImpulseHomeSetupDialog(canOpen = false, onDismiss = {}) } }
        composeRule.onNode(isDialog()).captureRoboImage(ReadabilityHarness.path("19-setup-dialog-bar-active"))
    }

    @Test
    fun telasClusterDisabled() {
        prefs().edit().putBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, false).commit()
        snap("20-telas-cluster-disabled") { TelasTab() }
    }
}
