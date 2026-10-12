package br.com.redesurftank.havalshisuku.ui.readability

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import br.com.redesurftank.havalshisuku.ui.components.GroupedSettingsLayout
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
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
 * Tema CLARO do Material (darkTheme = false) com o readableText ativo. O app usa superfícies escuras
 * fixas (ImpTokens), então este snapshot só prova que nenhum texto fica ilegível com o tema claro
 * do sistema. Só existe "after" (a flag readableText não existe no commit base).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "w1920dp-h720dp-land-mdpi")
class ReadabilityLightThemeSnapshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Before
    fun setUp() {
        ReadabilityHarness.installAppSingleton(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun settingsGroupedLightTheme() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            HavalShisukuTheme(darkTheme = false, dynamicColor = false, readableText = true) {
                Box(
                    Modifier.size(1920.dp, 720.dp).background(ImpTokens.Ground)
                        .padding(AppDimensions.ContentPadding)
                ) { GroupedSettingsLayout(fakeSettingItems()) }
            }
        }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.onRoot().captureRoboImage(ReadabilityHarness.path("21-settings-grouped-light-theme"))
    }
}
