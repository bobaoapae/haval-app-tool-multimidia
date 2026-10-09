package br.com.redesurftank.havalshisuku.projectors

import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.api.AaClusterProtocol
import br.com.redesurftank.havalshisuku.managers.AndroidAutoClusterController
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.managers.ClusterSurfaceOutput
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import java.lang.ref.WeakReference

/** D3 video under the existing masks/WebView, with retained consumer ownership. */
object AaClusterVideoHost {
    private const val TAG = "AaClusterVideo"
    val DEFAULT_MAP_BOUNDS = intArrayOf(0, 62, 1920, 658)
    private const val PANEL_WIDTH = 1920
    private const val PANEL_HEIGHT = 720
    /** Google draws its guidance card at the stream's right edge (x≈1570–1910, zero insets). */
    private const val GOOGLE_CARD_LEFT = 1560
    /** Same 70% line apps on D3 clear while the native card or a warning is up. */
    private const val NATIVE_CARD_LEFT = (PANEL_WIDTH * 0.7f).toInt()

    @Volatile private var nativeCardShown = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Re-reads [mapBounds] (theme bounds changed). Any thread. */
    fun refreshWindow() {
        mainHandler.post { textureView?.let { applyStreamTransform(it) } }
    }

    /** Pulls the map's right edge in while the car's native card is shown. Any thread. */
    fun setNativeCardShown(shown: Boolean) {
        if (nativeCardShown == shown) return
        nativeCardShown = shown
        if (Looper.myLooper() == Looper.getMainLooper()) {
            textureView?.let { applyStreamTransform(it) }
        } else {
            mainHandler.post { textureView?.let { applyStreamTransform(it) } }
        }
    }

    /**
     * Visible map window on D3 (the native-mask hole) as (left, top, right, bottom).
     * The user's override wins. Otherwise the map spans from the left edge to just
     * before Google's guidance card (or to the native-card line while that card is
     * up), with top/bottom from the theme's default cluster app rect — the rect a
     * regular app sent to D1/D3 gets — else [DEFAULT_MAP_BOUNDS].
     */
    fun mapBounds(): IntArray {
        val custom = App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            .getString(SharedPreferencesKeys.AA_CLUSTER_MAP_CUSTOM_BOUNDS.key, null)
        parseBounds(custom)?.let { return it }
        val theme = try { DisplayAppLauncher.themeClusterAppBounds() } catch (e: RuntimeException) { null }
        val (top, bottom) = if (theme != null && theme[2] > 0 && theme[3] > 0) {
            theme[1] to theme[1] + theme[3]
        } else {
            DEFAULT_MAP_BOUNDS[1] to DEFAULT_MAP_BOUNDS[3]
        }
        return intArrayOf(0, top, if (nativeCardShown) NATIVE_CARD_LEFT else GOOGLE_CARD_LEFT, bottom)
    }

    internal fun parseBounds(value: String?): IntArray? {
        val parts = value?.split(',')?.map { it.trim().toIntOrNull() } ?: return null
        if (parts.size != 4 || parts.any { it == null }) return null
        val (l, t, r, b) = parts.map { it!! }
        return if (l >= 0 && t >= 0 && r - l >= 100 && b - t >= 100) intArrayOf(l, t, r, b) else null
    }
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
        applyStreamTransform(view)
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
            applyStreamTransform(view)
            if (view !== textureView) return
            adoptPendingConsumer()
        }
        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, newWidth: Int, newHeight: Int) {
            if (texture !== currentTexture || view !== textureView) return
            applyStreamTransform(view)
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

    /**
     * Maps the stream 1:1 onto the D3 panel (centre-crop, so the 1920x1080 frame's
     * margins fall off) and clips the view to [mapBounds]. Nothing outside the map
     * window is ever drawn, so Google's edge card and logo stay hidden even under a
     * translucent theme panel. The view itself always fills the panel: moving only
     * the clip keeps the SurfaceTexture size fixed, so a card change never restarts
     * the decoder.
     */
    private fun applyStreamTransform(view: TextureView) {
        val parent = view.parent as? View
        val panelW = parent?.width?.takeIf { it > 0 } ?: PANEL_WIDTH
        val panelH = parent?.height?.takeIf { it > 0 } ?: PANEL_HEIGHT
        val bounds = mapBounds()
        val clip = Rect(bounds[0], bounds[1], bounds[2], bounds[3])
        if (view.clipBounds != clip) {
            view.clipBounds = clip
            Log.w(TAG, "CLUSTER map window=${bounds.joinToString(",")}")
        }
        val sw = AaClusterProtocol.STREAM_WIDTH.toFloat()
        val sh = AaClusterProtocol.STREAM_HEIGHT.toFloat()
        val crop = maxOf(panelW / sw, panelH / sh)
        // TextureView stretches the buffer to the view; undo that per axis.
        val matrix = Matrix()
        matrix.setScale(sw * crop / panelW, sh * crop / panelH, panelW / 2f, panelH / 2f)
        view.setTransform(matrix)
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
