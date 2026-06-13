package br.com.redesurftank.havalshisuku

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.ui.components.ImpulseDashboardFullscreenContent
import br.com.redesurftank.havalshisuku.ui.theme.HavalShisukuTheme
import kotlinx.coroutines.flow.distinctUntilChanged

class ImpulseDashboardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        BottomBarState.isDashboardExpanded = true
        BottomBarState.isMenuExpanded = false
        BottomBarState.isSettingsMenuExpanded = false
        BottomBarState.isOverrideMenuExpanded = false
        BottomBarState.activeSliderType = null

        applyCarPlayStyleFullscreen()

        setContent {
            HavalShisukuTheme {
                LaunchedEffect(Unit) {
                    snapshotFlow { BottomBarState.isDashboardExpanded }
                            .distinctUntilChanged()
                            .collect { expanded ->
                                if (!expanded && !isFinishing) {
                                    finish()
                                }
                            }
                }
                ImpulseDashboardFullscreenContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyCarPlayStyleFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyCarPlayStyleFullscreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        BottomBarState.isDashboardExpanded = false
        BottomBarState.isVisible = true
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (isFinishing && BottomBarState.isDashboardExpanded) {
            BottomBarState.isDashboardExpanded = false
            BottomBarState.isVisible = true
        }
        super.onDestroy()
    }

    private fun applyCarPlayStyleFullscreen() {
        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes =
                    window.attributes.apply {
                        layoutInDisplayCutoutMode =
                                WindowManager.LayoutParams
                                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
        }
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ImpulseDashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
