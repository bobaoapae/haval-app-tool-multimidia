package br.com.redesurftank.havalshisuku.hud

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import br.com.redesurftank.havalshisuku.BuildConfig

/**
 * Door into the physical HUD (display 4096). It is a private display, so neither a Presentation
 * nor a plain startActivity from the app can reach it; [HudOverlayController] starts this
 * activity there through Shizuku.
 *
 * Normally the activity only lends its Display: the controller adds a window-manager overlay
 * on it and the activity finishes at once. An activity left on 4096 is the focused app with a
 * non-focusable window, and Android then blames us for the next key press ("no window has
 * focus") and ANRs after 5 s, as seen on the car. If the overlay can't be added, the activity
 * hosts the view itself as a fallback, and the controller hands focus back to D0.
 *
 * The HUD keeps the last frame it received after a window goes away, so [dismiss] paints an
 * empty frame before finishing.
 */
class HudOverlayActivity : Activity() {
    private var view: HudOverlayView? = null
    private var dismissing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never take focus or touch from D0: steering keys and the bottom bar follow focus.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        Log.w(TAG, "created on display=${windowManager.defaultDisplay.displayId}")
        // Debug builds only: `--es hudDemo on|nolanes|far|drive|off` puts a sample frame up for layout checks.
        if (BuildConfig.DEBUG) intent?.getStringExtra(EXTRA_DEMO)?.let { HudOverlayController.demo(it) }
        if (HudOverlayController.adoptDisplay(this)) {
            finishQuietly()
            return
        }
        view = HudOverlayView(this).also { setContentView(it) }
        HudOverlayController.attach(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) intent?.getStringExtra(EXTRA_DEMO)?.let { HudOverlayController.demo(it) }
        if (view == null) {
            if (HudOverlayController.adoptDisplay(this)) finishQuietly()
            return
        }
        HudOverlayController.attach(this)
    }

    fun show(frame: HudOverlayModel.Frame?) {
        if (dismissing) return
        view?.show(frame)
    }

    fun dismiss() {
        if (dismissing) return
        dismissing = true
        val v = view ?: return finishQuietly()
        v.show(null)
        // Let one empty frame reach the HUD, then go.
        v.postOnAnimation { v.postDelayed({ finishQuietly() }, HudOverlayController.CLEAR_FRAME_DELAY_MS) }
    }

    private fun finishQuietly() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (view != null) HudOverlayController.detach(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HudOverlay"
        private const val EXTRA_DEMO = "hudDemo"
    }
}
