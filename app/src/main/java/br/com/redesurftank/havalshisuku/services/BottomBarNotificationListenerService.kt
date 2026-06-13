package br.com.redesurftank.havalshisuku.services

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import br.com.redesurftank.havalshisuku.models.BottomBarState
import kotlin.math.roundToInt

class BottomBarNotificationListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName == packageName) return
        if (!isMediaNotification(notification)) return
        if (shouldPreserveProjectionMedia(sbn.packageName)) return

        val artwork = resolveNotificationArtwork(notification)

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()

        if (title.isNullOrBlank() && text.isNullOrBlank() && artwork == null) return

        mainHandler.post {
            val trackChanged =
                    BottomBarState.mediaPackageName != sbn.packageName ||
                            BottomBarState.mediaTitle != title?.takeIf { it.isNotBlank() } ||
                            BottomBarState.mediaArtist != text?.takeIf { it.isNotBlank() }

            BottomBarState.mediaTitle = title?.takeIf { it.isNotBlank() }
            BottomBarState.mediaArtist = text?.takeIf { it.isNotBlank() }
            BottomBarState.mediaAlbum =
                    subText?.takeIf { it.isNotBlank() } ?: summary?.takeIf { it.isNotBlank() }
            if (artwork != null || trackChanged) {
                BottomBarState.mediaArtwork = artwork
            }
            BottomBarState.mediaPackageName = sbn.packageName
            BottomBarState.mediaIsPlaying = true
            BottomBarState.mediaDurationMs = 0L
            BottomBarState.mediaElapsedMs = 0L
            BottomBarState.mediaProgressUpdatedAtMs = 0L
            BottomBarState.mediaCanSeek = false
        }
    }

    private fun isMediaNotification(notification: Notification): Boolean {
        return notification.category == Notification.CATEGORY_TRANSPORT ||
                notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
    }

    private fun resolveNotificationArtwork(notification: Notification): Bitmap? {
        val extras = notification.extras
        val picture = extras.get(Notification.EXTRA_PICTURE)
        if (picture is Bitmap) return normalizeArtwork(picture)

        val largeIcon = extras.get(Notification.EXTRA_LARGE_ICON)
        val largeIconBitmap =
                when (largeIcon) {
                    is Bitmap -> largeIcon
                    is android.graphics.drawable.Icon -> iconToBitmap(largeIcon)
                    else -> iconToBitmap(notification.getLargeIcon())
                }
        if (largeIconBitmap != null) return normalizeArtwork(largeIconBitmap)

        val bigLargeIcon = extras.get(Notification.EXTRA_LARGE_ICON_BIG)
        return when (bigLargeIcon) {
            is Bitmap -> normalizeArtwork(bigLargeIcon)
            is android.graphics.drawable.Icon -> iconToBitmap(bigLargeIcon)
            else -> null
        }
    }

    private fun iconToBitmap(icon: android.graphics.drawable.Icon?): Bitmap? {
        val drawable = icon?.loadDrawable(this) ?: return null
        return drawableToBitmap(drawable)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return normalizeArtwork(it) }
        }

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 256
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 256
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return normalizeArtwork(bitmap)
    }

    private fun normalizeArtwork(bitmap: Bitmap): Bitmap {
        val maxDimension = 720
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun shouldPreserveProjectionMedia(candidatePackageName: String): Boolean {
        val currentPackageName = BottomBarState.mediaPackageName ?: return false
        if (!isProjectionMediaPackage(currentPackageName)) return false
        if (isProjectionMediaPackage(candidatePackageName)) return false
        return candidatePackageName in PROJECTION_MEDIA_FALLBACK_PACKAGES
    }

    private fun isProjectionMediaPackage(packageName: String): Boolean {
        return packageName == "com.ts.carplay" ||
                packageName == "com.ts.carplay.app" ||
                packageName == "com.ts.androidauto" ||
                packageName == "com.ts.androidauto.app" ||
                packageName == "com.ts.androidauto.projectionservice"
    }

    companion object {
        private val PROJECTION_MEDIA_FALLBACK_PACKAGES =
                setOf(
                        "com.android.bluetooth",
                        "com.beantechs.mediacenter",
                        "com.beantechs.mediacenter.h5.core",
                        "com.onecar.onlinemusic"
                )
    }
}
