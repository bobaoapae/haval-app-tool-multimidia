package br.com.redesurftank.havalshisuku.hud

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import br.com.redesurftank.havalshisuku.BuildConfig

/**
 * Transparent, non-focusable window on the physical HUD (display 4096). Started through Shizuku
 * by [HudOverlayController]: the HUD is a private display, so neither a Presentation nor a plain
 * startActivity from the app can reach it.
 *
 * The HUD keeps the last frame it received after our window goes away (seen on the car), so
 * [dismiss] paints an empty frame before finishing; finishing straight away would leave the
 * last arrow stuck on the windshield.
 */
class HudOverlayActivity : Activity() {
    private lateinit var view: HudOverlayView
    private var dismissing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never take focus or touch from D0: steering keys and the bottom bar follow focus.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        view = HudOverlayView(this)
        setContentView(view)
        Log.w(TAG, "created on display=${windowManager.defaultDisplay.displayId}")
        // Debug builds only: `--es hudDemo on|nolanes|off` puts a sample frame up for layout checks.
        if (BuildConfig.DEBUG) intent?.getStringExtra(EXTRA_DEMO)?.let { HudOverlayController.demo(it) }
        HudOverlayController.attach(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (!BuildConfig.DEBUG) return
        val demo = intent?.getStringExtra(EXTRA_DEMO) ?: return
        HudOverlayController.demo(demo)
        HudOverlayController.attach(this)
    }

    fun show(frame: HudOverlayModel.Frame?) {
        if (dismissing) return
        view.show(frame)
    }

    fun dismiss() {
        if (dismissing) return
        dismissing = true
        view.show(null)
        // Let one empty frame reach the HUD, then go.
        view.postOnAnimation {
            view.postDelayed({
                finish()
                overridePendingTransition(0, 0)
            }, CLEAR_FRAME_DELAY_MS)
        }
    }

    override fun onDestroy() {
        HudOverlayController.detach(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HudOverlay"
        private const val EXTRA_DEMO = "hudDemo"
        private const val CLEAR_FRAME_DELAY_MS = 150L
    }
}
