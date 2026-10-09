package br.com.redesurftank.havalshisuku.projectors

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import br.com.redesurftank.havalshisuku.managers.AndroidAutoClusterController
import br.com.redesurftank.havalshisuku.managers.ClusterSurfaceOutput
import java.lang.ref.WeakReference

/** D3 video under the existing masks/WebView, with retained consumer ownership. */
object AaClusterVideoHost {
    private const val TAG = "AaClusterVideo"
    val DEFAULT_MAP_BOUNDS = intArrayOf(0, 62, 1920, 658)
    private var parentRef: WeakReference<FrameLayout>? = null
    private var textureView: TextureView? = null
    private var output: ClusterSurfaceOutput? = null
    private var shown = false
    private var generation = 0L
    private var width = 0
    private var height = 0

    fun attachParent(parent: FrameLayout) {
        if (parentRef?.get() !== parent) detachParent()
        parentRef = WeakReference(parent)
        ClusterSurfaceOutput.setCapacityListener { adoptPendingConsumer() }
        ensureView(parent.context, parent)
        AndroidAutoClusterController.onHostAvailable()
    }

    fun detachParent() {
        AndroidAutoClusterController.onSurfaceDestroyed()
        val oldView = textureView
        val parent = parentRef?.get()
        // Keep the per-view listener/owner alive. Its destruction callback must
        // return false even when this global current-view pointer has moved on.
        textureView = null
        output = null
        parentRef = null
        shown = false
        generation++
        if (oldView != null) parent?.removeView(oldView)
    }

    fun show(context: Context): Boolean {
        val parent = parentRef?.get() ?: return false
        ensureView(context, parent)
        val view = textureView ?: return false
        view.visibility = View.VISIBLE
        shown = true
        adoptPendingConsumer()
        return true
    }

    fun hide() {
        textureView?.visibility = View.GONE
        shown = false
    }
    fun isShown(): Boolean = shown
    fun peekSurface(): Surface? = output?.takeIf { it.isAvailable }?.surface
    internal fun peekOutput(): ClusterSurfaceOutput? = output?.takeIf { it.isAvailable }
    internal fun surfaceGeneration(): Long = generation

    /** Capacity can return after an old terminal ACK; no polling or forced release. */
    private fun adoptPendingConsumer() {
        val view = textureView ?: return
        val owner = view.surfaceTextureListener as? TextureOwner ?: return
        if (owner.adoptIfPossible()) {
            output = owner.owned
            generation++
            width = view.width
            height = view.height
            output?.let { AndroidAutoClusterController.onSurfaceAvailable(it, generation) }
        }
    }

    private class TextureOwner(private val view: TextureView) : TextureView.SurfaceTextureListener {
        var owned: ClusterSurfaceOutput? = null
            private set
        private var currentTexture: SurfaceTexture? = null

        fun adoptIfPossible(): Boolean {
            if (view !== textureView || owned != null) return false
            val texture = currentTexture ?: return false
            if (!view.isAvailable || view.surfaceTexture !== texture) return false
            owned = try { ClusterSurfaceOutput.adopt(texture) } catch (failure: RuntimeException) {
                Log.e(TAG, "Cannot adopt CLUSTER consumer", failure)
                null
            }
            return owned != null
        }
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, newWidth: Int, newHeight: Int) {
            currentTexture = texture
            if (view !== textureView) return
            adoptPendingConsumer()
        }
        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, newWidth: Int, newHeight: Int) {
            if (texture !== currentTexture || view !== textureView) return
            if (width != newWidth || height != newHeight) {
                width = newWidth
                height = newHeight
                generation++
                owned?.let { AndroidAutoClusterController.onSurfaceAvailable(it, generation) }
            }
        }
        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            val matchesCurrent = texture === currentTexture
            val retiring = owned?.takeIf { it.texture === texture }
                ?: ClusterSurfaceOutput.retained(texture)
            if (view === textureView && matchesCurrent) {
                output = null
                generation++
                AndroidAutoClusterController.onSurfaceDestroyed()
            }
            if (matchesCurrent) currentTexture = null
            if (owned === retiring) owned = null
            if (retiring == null) return true // Never exposed; framework retains normal ownership.
            // Android 9 has detached its hardware layer before this callback.
            // Return false so forced display loss cannot abandon the consumer
            // while the remote decoder/transport still has a live borrow.
            retiring.detachViewOwner()
            return false
        }
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
            // Framework RenderThread owns updateTexImage and GL attachment.
        }
    }

    private fun ensureView(context: Context, parent: FrameLayout) {
        if (textureView != null) return
        val view = TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.FILL
            )
            isOpaque = false
            visibility = View.GONE
        }
        view.surfaceTextureListener = TextureOwner(view)
        textureView = view
        parent.addView(view, 0)
    }
}
