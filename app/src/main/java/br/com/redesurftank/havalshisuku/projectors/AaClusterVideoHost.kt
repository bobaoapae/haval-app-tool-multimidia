package br.com.redesurftank.havalshisuku.projectors

import android.content.Context
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
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
    /** Same 70% line apps on D3 clear while the native card or a warning is up. */
    private const val NATIVE_CARD_LEFT = (PANEL_WIDTH * 0.7f).toInt()

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

    /**
     * While the native card is up the map stops at the 70% line. Instead of a hard edge there,
     * the last [CARD_EDGE_FADE_WIDTH] px fade the map to fully transparent, so it dissolves into
     * the car's card. Purely visual: the stream, its clip and the D3 mask hole are unchanged.
     */
    internal const val CARD_EDGE_FADE_WIDTH = 154
    private var mapLayer: MapLayer? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Map fade in once frames flow, and out before it hides; matches the D3 mask crossfade. */
    private const val MAP_FADE_MS = 300L
    /** True once the remote side renders frames; until then the shown map stays transparent. */
    private var live = false

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
            syncFades()
        } else {
            mainHandler.post {
                textureView?.let { applyStreamTransform(it) }
                syncFades()
            }
        }
    }

    /**
     * Visible map window on D3 (the native-mask hole) as (left, top, right, bottom).
     * The user's override wins. Otherwise the map spans the full panel width (Google's
     * guidance card shows at its right edge, x≈1570–1910), or stops at the
     * native-card line while that card is up, which also hides Google's card. Top and
     * bottom follow the theme's default cluster app rect — the rect a regular app sent
     * to D1/D3 gets — else [DEFAULT_MAP_BOUNDS].
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
        return intArrayOf(0, top, if (nativeCardShown) NATIVE_CARD_LEFT else PANEL_WIDTH, bottom)
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
        val parent = parentRef?.get()
        // Keep the per-view listener/owner alive. Its destruction callback must
        // return false even when this global current-view pointer has moved on.
        textureView = null
        output = null
        parentRef = null
        shown = false
        live = false
        generation++
        mapLayer?.let { parent?.removeView(it) }
        mapLayer = null
        rightFade = null
        leftFade = null
    }

    fun show(context: Context): Boolean {
        val parent = parentRef?.get() ?: return false
        ensureView(context, parent)
        val view = textureView ?: return false
        applyStreamTransform(view)
        val wasShown = shown
        layerViews().forEach { it.animate().cancel() }
        if (!wasShown) setLayerAlpha(if (live) 1f else 0f)
        view.visibility = View.VISIBLE
        shown = true
        syncFades()
        adoptPendingConsumer()
        return true
    }

    /**
     * Shown before the first frame, the map view is an empty transparent layer while its edge
     * fades are opaque black, which flashed dark bands before the map arrived. It now stays
     * transparent until the stream is live, then fades in. Any thread.
     */
    fun setLive(isLive: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setLive(isLive) }
            return
        }
        if (live == isLive) return
        live = isLive
        if (isLive && shown) {
            layerViews().forEach { it.animate().alpha(1f).setDuration(MAP_FADE_MS).withLayer().start() }
        }
    }

    fun hide() {
        val wasShown = shown
        shown = false
        live = false
        val view = textureView
        if (!wasShown || view == null || view.visibility != View.VISIBLE) {
            view?.visibility = View.GONE
            syncFades()
            return
        }
        // Fade the last frame out, then drop the views; show() cancels this if it comes back.
        layerViews().forEach { layer ->
            layer.animate().alpha(0f).setDuration(MAP_FADE_MS).withLayer().withEndAction {
                if (!shown) {
                    layer.visibility = View.GONE
                    layer.alpha = 1f
                }
            }.start()
        }
    }

    private fun layerViews(): List<View> = listOfNotNull(textureView, leftFade, rightFade)

    private fun setLayerAlpha(alpha: Float) {
        layerViews().forEach { it.alpha = alpha }
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
        matrix.postTranslate(VEHICLE_CENTER_SHIFT.toFloat(), 0f)
        view.setTransform(matrix)
    }

    /** On card 0 the clip already stops before Google's card; the right fade is not needed. */
    private fun syncFades() {
        leftFade?.visibility = if (shown) View.VISIBLE else View.GONE
        rightFade?.visibility = if (shown && !nativeCardShown) View.VISIBLE else View.GONE
        val edge = textureView?.clipBounds?.right ?: NATIVE_CARD_LEFT
        mapLayer?.setEdgeFade(if (shown && nativeCardShown) edge else -1)
    }

    /** Fade band [end - CARD_EDGE_FADE_WIDTH, end] for a map edge at [end]. */
    internal fun cardEdgeFadeBand(end: Int): Pair<Int, Int> =
        (end - CARD_EDGE_FADE_WIDTH).coerceAtLeast(0) to end

    /**
     * Holds the map and its edge fades. With an edge set it renders through a hardware layer
     * and erases the band before the edge with a transparent gradient (DST_OUT); the layer
     * exists only while that fade is on (card 0).
     */
    private class MapLayer(context: Context) : FrameLayout(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        private var fadeEnd = -1

        init {
            setWillNotDraw(false)
        }

        fun setEdgeFade(end: Int) {
            if (fadeEnd == end) return
            fadeEnd = end
            if (end >= 0) {
                val (start, stop) = cardEdgeFadeBand(end)
                paint.shader = LinearGradient(
                    start.toFloat(), 0f, stop.toFloat(), 0f,
                    0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP
                )
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else {
                paint.shader = null
                setLayerType(View.LAYER_TYPE_NONE, null)
            }
            invalidate()
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (fadeEnd < 0) return
            val (start, _) = cardEdgeFadeBand(fadeEnd)
            // Clamped shader: fully erased from the edge onwards.
            canvas.drawRect(start.toFloat(), 0f, width.toFloat(), height.toFloat(), paint)
        }
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
        // The container takes the video's old slot: lowest child, under the masks and the
        // theme WebView. The edge fades sit directly above the video inside it.
        val layer = MapLayer(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        mapLayer = layer
        layer.addView(view)
        val black = 0xFF000000.toInt()
        leftFade = fade(context, 0, LEFT_FADE_WIDTH, intArrayOf(black, black, 0)).also { layer.addView(it) }
        rightFade = fade(context, RIGHT_FADE_LEFT, PANEL_WIDTH - RIGHT_FADE_LEFT, intArrayOf(0, black, black))
            .also { layer.addView(it) }
        parent.addView(layer, 0)
    }
}
