package br.com.redesurftank.havalshisuku.hud

import android.content.Context
import android.graphics.Canvas
import android.view.View

/**
 * The HUD window's content: draws one [HudOverlayModel.Frame] through [HudOverlayRenderer] on a
 * transparent canvas, so the native HUD shows through everywhere else.
 */
class HudOverlayView(context: Context) : View(context) {
    private val renderer = HudOverlayRenderer()
    private var frame: HudOverlayModel.Frame? = null

    /** Null clears the overlay. Redraws only when the frame actually changed. */
    fun show(next: HudOverlayModel.Frame?) {
        if (next == frame) return
        frame = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        renderer.draw(canvas, frame)
        // Chevrons animate: keep redrawing (~30 fps) only while they are up.
        if (frame?.chevrons != null) postInvalidateDelayed(CHEVRON_FRAME_MS)
    }

    private companion object {
        const val CHEVRON_FRAME_MS = 33L
    }
}
