package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.SolidBackgroundSpec
import coil.imageLoader
import coil.request.ImageRequest
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Keeps Display-1 wallpaper and Display-3 native-mask insets in step.
 *
 * D3 insets composite the same still wallpaper D1 paints. Without a ready signal, D3 can show
 * framed insets while D1 is still empty (cold start, async IMAGE_URL, or asymmetric THEME
 * fallback). [shouldHoldNativeMasks] extends the existing "don't frame nothing" gate.
 */
object ClusterBackgroundSync {
    private const val TAG = "ClusterBackgroundSync"

    fun interface Listener {
        fun onD1BackgroundStateChanged()
    }

    /**
     * Resolved still wallpaper for both projectors. [Loading] means IMAGE_URL is in flight;
     * [None] means nothing opaque is expected on D1.
     */
    sealed class StillBackground {
        data class FromFile(val file: File) : StillBackground()
        data class FromAsset(val fileName: String) : StillBackground()
        data class Solid(val spec: SolidBackgroundSpec) : StillBackground()
        data class FromBitmap(val bitmap: Bitmap) : StillBackground()
        object Loading : StillBackground()
        object None : StillBackground()
    }

    private val listeners = CopyOnWriteArraySet<Listener>()

    @Volatile
    private var d1Attached = false

    @Volatile
    private var readyIdentity: String? = null

    @Volatile
    private var remoteBitmap: Bitmap? = null

    @Volatile
    private var remoteUrl: String? = null

    @Volatile
    private var remoteInFlightUrl: String? = null

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun markD1Attached() {
        d1Attached = true
    }

    fun markD1Detached() {
        d1Attached = false
        readyIdentity = null
        notifyListeners()
    }

    fun isD1Attached(): Boolean = d1Attached

    fun identityFromPrefs(prefs: SharedPreferences): String {
        val enabled = prefs.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
        val type =
                prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME")
                        ?: "THEME"
        val value =
                prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "") ?: ""
        val theme =
                prefs.getString(SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "") ?: ""
        return "$enabled|$type|$value|$theme"
    }

    fun markD1Ready(identity: String) {
        if (readyIdentity == identity) return
        readyIdentity = identity
        notifyListeners()
    }

    fun markD1NotReady(identity: String? = null) {
        val previous = readyIdentity ?: return
        if (identity != null && previous != identity) return
        readyIdentity = null
        notifyListeners()
    }

    fun isD1Ready(identity: String): Boolean = readyIdentity == identity

    /**
     * True when D3 must keep native masks down until D1 has painted the current still wallpaper.
     *
     * Skips the hold when D1 is not attached (stealth / display absent), when an app intentionally
     * covers D1, or when no still wallpaper is expected (disabled, WEB_URL, THEME without file).
     */
    fun shouldHoldNativeMasks(prefs: SharedPreferences, appOnDisplay1: Boolean): Boolean {
        return shouldHoldNativeMasks(
                d1Attached = d1Attached,
                appOnDisplay1 = appOnDisplay1,
                stillWallpaperExpected = stillWallpaperExpected(prefs),
                d1Ready = isD1Ready(identityFromPrefs(prefs))
        )
    }

    /** Pure gate used by unit tests and by [shouldHoldNativeMasks]. */
    fun shouldHoldNativeMasks(
            d1Attached: Boolean,
            appOnDisplay1: Boolean,
            stillWallpaperExpected: Boolean,
            d1Ready: Boolean
    ): Boolean {
        if (!d1Attached) return false
        if (appOnDisplay1) return false
        if (!stillWallpaperExpected) return false
        return !d1Ready
    }

    /**
     * Whether prefs imply an opaque still image/color on D1 (not a live WEB_URL page).
     * Pure preference/file existence — does not require the D1 Presentation to have painted yet.
     */
    fun stillWallpaperExpected(prefs: SharedPreferences): Boolean {
        val enabled = prefs.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
        if (!enabled) return false

        val type =
                (prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME")
                                ?: "THEME")
                        .trim()
                        .uppercase()
        val value =
                (prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "") ?: "")
                        .trim()

        return when (type) {
            "THEME" ->
                    ThemeManager.getInstance(br.com.redesurftank.App.getContext())
                            .getActiveThemeBackgroundFile(value.ifBlank { null }) != null
            "PRESET",
            "FILE",
            "IMAGE_URL" -> value.isNotEmpty()
            SolidBackgroundSpec.TYPE -> true
            "WEB_URL" -> false
            else -> false
        }
    }

    /**
     * Same source D1 and D3 must use. IMAGE_URL returns [StillBackground.FromBitmap] when cached,
     * [StillBackground.Loading] while a fetch is in flight (and schedules Coil), or [StillBackground.None]
     * when the URL is blank.
     */
    fun resolveStillBackground(
            context: Context,
            prefs: SharedPreferences,
            enqueueRemoteIfNeeded: Boolean = true
    ): StillBackground {
        val enabled = prefs.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
        if (!enabled) return StillBackground.None

        val type =
                (prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME")
                                ?: "THEME")
                        .trim()
                        .uppercase()
        val value =
                (prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "") ?: "")
                        .trim()

        return try {
            when (type) {
                "THEME" -> {
                    val file =
                            ThemeManager.getInstance(context)
                                    .getActiveThemeBackgroundFile(value.ifBlank { null })
                    if (file != null && file.exists()) StillBackground.FromFile(file)
                    else StillBackground.None
                }
                "FILE" -> {
                    if (value.isEmpty()) return StillBackground.None
                    val file = File(value)
                    if (file.exists()) StillBackground.FromFile(file) else StillBackground.None
                }
                "PRESET" -> {
                    if (value.isEmpty()) StillBackground.None
                    else StillBackground.FromAsset(value)
                }
                "IMAGE_URL" -> {
                    if (value.isEmpty()) return StillBackground.None
                    val cached = remoteBitmap
                    if (cached != null && !cached.isRecycled && remoteUrl == value) {
                        return StillBackground.FromBitmap(cached)
                    }
                    if (enqueueRemoteIfNeeded) {
                        enqueueRemote(context, value)
                    }
                    if (remoteInFlightUrl == value) StillBackground.Loading
                    else StillBackground.None
                }
                SolidBackgroundSpec.TYPE -> {
                    val spec = SolidBackgroundSpec.parse(value) ?: SolidBackgroundSpec.DEFAULT
                    StillBackground.Solid(spec)
                }
                else -> StillBackground.None
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve still background type=$type", e)
            StillBackground.None
        }
    }

    /** Decode for mask composition (1920×720 COLOR bitmap when solid). */
    fun decodeStillBitmap(
            context: Context,
            prefs: SharedPreferences,
            enqueueRemoteIfNeeded: Boolean = true
    ): Bitmap? {
        return when (val resolved = resolveStillBackground(context, prefs, enqueueRemoteIfNeeded)) {
            is StillBackground.FromFile ->
                    BitmapFactory.decodeFile(resolved.file.absolutePath)
            is StillBackground.FromAsset ->
                    context.assets.open("backgrounds/${resolved.fileName}").use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
            is StillBackground.Solid -> buildSolidBackgroundBitmap(resolved.spec)
            is StillBackground.FromBitmap -> resolved.bitmap
            StillBackground.Loading,
            StillBackground.None -> null
        }
    }

    fun buildSolidBackgroundBitmap(spec: SolidBackgroundSpec): Bitmap {
        val width = 1920
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(spec.color)

        if (spec.vignette > 0) {
            val alpha = (spec.vignette * 255 / 100).coerceIn(0, 255)
            val paint =
                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        shader =
                                android.graphics.RadialGradient(
                                        width / 2f,
                                        height / 2f,
                                        width * 0.62f,
                                        intArrayOf(
                                                Color.TRANSPARENT,
                                                Color.TRANSPARENT,
                                                Color.argb(alpha, 0, 0, 0)
                                        ),
                                        floatArrayOf(0f, 0.45f, 1f),
                                        android.graphics.Shader.TileMode.CLAMP
                                )
                    }
            canvas.save()
            canvas.scale(1f, height.toFloat() / width, width / 2f, height / 2f)
            canvas.drawRect(
                    0f,
                    height / 2f - width,
                    width.toFloat(),
                    height / 2f + width,
                    paint
            )
            canvas.restore()
        }
        return bitmap
    }

    /** Test seam: reset all mutable state between unit tests. */
    internal fun resetForTests() {
        d1Attached = false
        readyIdentity = null
        remoteBitmap = null
        remoteUrl = null
        remoteInFlightUrl = null
        listeners.clear()
    }

    private fun enqueueRemote(context: Context, url: String) {
        if (remoteInFlightUrl == url) return
        remoteInFlightUrl = url
        try {
            val request =
                    ImageRequest.Builder(context)
                            .data(url)
                            .allowHardware(false)
                            .target(
                                    onSuccess = { drawable ->
                                        remoteInFlightUrl = null
                                        val bmp =
                                                (drawable as? android.graphics.drawable.BitmapDrawable)
                                                        ?.bitmap
                                        if (bmp != null && !bmp.isRecycled) {
                                            remoteBitmap = bmp
                                            remoteUrl = url
                                            notifyListeners()
                                        } else {
                                            Log.w(TAG, "Remote background was not a bitmap: $url")
                                        }
                                    },
                                    onError = {
                                        remoteInFlightUrl = null
                                        Log.w(TAG, "Failed to load remote background: $url")
                                        notifyListeners()
                                    }
                            )
                            .build()
            context.imageLoader.enqueue(request)
        } catch (e: Exception) {
            remoteInFlightUrl = null
            Log.w(TAG, "Could not enqueue remote background: $url", e)
        }
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            try {
                listener.onD1BackgroundStateChanged()
            } catch (e: Exception) {
                Log.w(TAG, "Listener failed", e)
            }
        }
    }
}
