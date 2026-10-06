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
import br.com.redesurftank.havalshisuku.managers.AndroidAutoClusterController
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
    private var generation = 0L
    private var surfaceFormat = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var configured = false

    fun attachParent(parent: FrameLayout) {
        if (parentRef?.get() !== parent) detachParent()
        parentRef = WeakReference(parent)
        ensureView(parent.context, parent)
        AndroidAutoClusterController.onHostAvailable()
    }

    fun detachParent() {
        AndroidAutoClusterController.onSurfaceDestroyed()
        hide()
        val parent = parentRef?.get()
        surfaceView?.let { view ->
            parent?.removeView(view)
        }
        surfaceView = null
        surface = null
        generation++
        configured = false
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

    fun peekSurface(): Surface? = if (configured) surface else null

    internal fun surfaceGeneration(): Long = generation

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
                        if (surfaceView?.holder !== holder) return
                        surface = holder.surface
                        generation++
                        configured = false
                        Log.i(TAG, "CLUSTER Surface created")
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        if (surfaceView?.holder !== holder) return
                        surface = holder.surface
                        if (!configured || surfaceFormat != format || surfaceWidth != width || surfaceHeight != height) {
                            generation++
                            surfaceFormat = format
                            surfaceWidth = width
                            surfaceHeight = height
                            configured = true
                        }
                        AndroidAutoClusterController.onSurfaceAvailable(holder.surface, generation)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        if (surfaceView?.holder !== holder) return
                        surface = null
                        generation++
                        configured = false
                        AndroidAutoClusterController.onSurfaceDestroyed()
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
