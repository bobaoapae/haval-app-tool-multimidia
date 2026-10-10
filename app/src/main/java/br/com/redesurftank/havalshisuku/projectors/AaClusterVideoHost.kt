package br.com.redesurftank.havalshisuku.projectors

import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
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
    @Volatile private var nativeCardShown = false

    /** Edge fades over the map (under the theme): hide Google's guidance card and logo. */
    private const val RIGHT_FADE_LEFT = 1400
    /**
     * Google offsets its map focus left of its right-edge card: the vehicle sits at
     * x≈785 of the 1920 band, not 960. Shift the stream right so the vehicle is
     * centred; the card moves mostly past the panel edge and the uncovered left
     * strip falls under the opaque part of the left fade.
     */
    private const val VEHICLE_CENTER_SHIFT = 175
    private const val LEFT_FADE_WIDTH = 520
    private var rightFade: View? = null
    private var leftFade: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Re-reads [mapBounds] (theme bounds changed). Any thread. */
    fun refreshWindow(onBoundsApplied: ((IntArray) -> Unit)? = null) {
        val refresh = Runnable {
            val bounds = mapBounds()
            textureView?.let { applyStreamTransform(it, bounds) }
            // The hole must use exactly the clip just applied, in this same UI turn.
            onBoundsApplied?.invoke(bounds)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run()
        else mainHandler.post(refresh)
    }

    /** Pulls the map's right edge in while the car's native card is shown. Any thread. */
    fun setNativeCardShown(shown: Boolean, onBoundsApplied: ((IntArray) -> Unit)? = null) {
        val update = Runnable {
            if (nativeCardShown != shown) {
                nativeCardShown = shown
                refreshWindow(onBoundsApplied)
                syncFades()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) update.run()
        else mainHandler.post(update)
    }

    /**
     * Visible map window on D3 (the native-mask hole) as (left, top, right, bottom).
     * The user's override wins within the panel and native-card exclusion. Otherwise
     * the map spans the full panel width (Google's
     * guidance card shows at its right edge, x≈1570–1910), or stops at the
     * native-card line while that card is up, which also hides Google's card. Top and
     * bottom follow the theme's default cluster app rect — the rect a regular app sent
     * to D1/D3 gets — else [DEFAULT_MAP_BOUNDS].
     */
    fun mapBounds(): IntArray {
        val custom = App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            .getString(SharedPreferencesKeys.AA_CLUSTER_MAP_CUSTOM_BOUNDS.key, null)
        val theme = try { DisplayAppLauncher.themeClusterAppBounds() } catch (e: RuntimeException) { null }
        return AaClusterGeometry.resolve(custom, theme, nativeCardShown)
    }

    internal fun parseBounds(value: String?): IntArray? {
        return AaClusterGeometry.parseCustom(value)
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
        rightFade?.let { parent?.removeView(it) }
        leftFade?.let { parent?.removeView(it) }
        rightFade = null
        leftFade = null
    }

    fun show(context: Context): Boolean {
        val parent = parentRef?.get() ?: return false
        ensureView(context, parent)
        val view = textureView ?: return false
        applyStreamTransform(view)
        view.visibility = View.VISIBLE
        shown = true
        syncFades()
        adoptPendingConsumer()
        return true
    }

    fun hide() {
        textureView?.visibility = View.GONE
        shown = false
        syncFades()
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
    private fun applyStreamTransform(view: TextureView, bounds: IntArray = mapBounds()) {
        val parent = view.parent as? View
        val panelW = parent?.width?.takeIf { it > 0 } ?: PANEL_WIDTH
        val panelH = parent?.height?.takeIf { it > 0 } ?: PANEL_HEIGHT
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
        matrix.postTranslate(VEHICLE_CENTER_SHIFT.toFloat(), 0f)
        view.setTransform(matrix)
    }

    /** On card 0 the clip already stops before Google's card; the right fade is not needed. */
    private fun syncFades() {
        leftFade?.visibility = if (shown) View.VISIBLE else View.GONE
        rightFade?.visibility = if (shown && !nativeCardShown) View.VISIBLE else View.GONE
    }

    private fun fade(context: Context, left: Int, width: Int, colors: IntArray) =
        View(context).apply {
            layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                leftMargin = left
                gravity = Gravity.TOP or Gravity.START
            }
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
            visibility = View.GONE
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
        val black = 0xFF000000.toInt()
        // Directly above the video, still under the theme WebView.
        leftFade = fade(context, 0, LEFT_FADE_WIDTH, intArrayOf(black, black, 0)).also { parent.addView(it, 1) }
        rightFade = fade(context, RIGHT_FADE_LEFT, PANEL_WIDTH - RIGHT_FADE_LEFT, intArrayOf(0, black, black))
            .also { parent.addView(it, 2) }
    }
}
