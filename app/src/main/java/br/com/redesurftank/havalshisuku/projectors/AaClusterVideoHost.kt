package br.com.redesurftank.havalshisuku.projectors

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/**
 * D3 CLUSTER video layer. Lives inside the Presentation under the native masks
 * and the theme WebView. MAIN [AapActivity] stays on display 0.
 */
object AaClusterVideoHost {
    private const val TAG = "AaClusterVideo"

    /** Clean-mode map band matching Minimalist AppDefaultPosition. */
    val DEFAULT_MAP_BOUNDS = intArrayOf(0, 62, 1920, 658)

    private var parentRef: WeakReference<FrameLayout>? = null
    private var surfaceView: SurfaceView? = null
    private var surface: Surface? = null
    private var shown = false

    fun attachParent(parent: FrameLayout) {
        parentRef = WeakReference(parent)
        ensureView(parent.context, parent)
    }

    fun detachParent() {
        hide()
        val parent = parentRef?.get()
        surfaceView?.let { view ->
            parent?.removeView(view)
        }
        surfaceView = null
        surface = null
        parentRef = null
        shown = false
    }

    fun show(context: Context): Boolean {
        val parent = parentRef?.get() ?: return false
        ensureView(context, parent)
        val view = surfaceView ?: return false
        view.visibility = View.VISIBLE
        shown = true
        return true
    }

    fun hide() {
        surfaceView?.visibility = View.GONE
        shown = false
    }

    fun isShown(): Boolean = shown

    fun peekSurface(): Surface? = surface

    private fun ensureView(context: Context, parent: FrameLayout) {
        if (surfaceView != null) return
        val view = SurfaceView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.FILL
            )
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            holder.addCallback(
                object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        surface = holder.surface
                        Log.i(TAG, "CLUSTER Surface created")
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        surface = holder.surface
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        surface = null
                        Log.i(TAG, "CLUSTER Surface destroyed")
                    }
                }
            )
        }
        surfaceView = view
        // Under native masks (index 0 after this insert) and WebView.
        parent.addView(view, 0)
    }
}
