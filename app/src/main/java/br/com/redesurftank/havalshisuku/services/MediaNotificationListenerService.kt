package br.com.redesurftank.havalshisuku.services

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType
import br.com.redesurftank.havalshisuku.managers.ServiceManager

class MediaNotificationListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var mediaCallback: MediaController.Callback? = null
    private var trackedController: MediaController? = null
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    companion object {
        private val MEDIA_PACKAGES = setOf(
            "com.beantechs.mediacenter.h5.core",
            "com.beantechs.mediacenter",
            "com.onecar.onlinemusic",
            "com.android.car.media",
            "com.spotify.music",
            "com.google.android.youtube.music",
            "com.amazon.music",
            "com.soundcloud.android",
            "com.deezer.android",
        )

        private fun sourceNameFor(pkg: String?) = when (pkg) {
            "com.beantechs.mediacenter.h5.core",
            "com.beantechs.mediacenter" -> "BT"
            "com.onecar.onlinemusic" -> "Online"
            "com.spotify.music" -> "Spotify"
            "com.google.android.youtube.music" -> "YT Music"
            "com.amazon.music" -> "Amazon"
            "com.soundcloud.android" -> "SoundCloud"
            "com.deezer.android" -> "Deezer"
            else -> "Música"
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.w("MediaNLS", "onListenerConnected")
        // Scan existing notifications immediately
        try {
            activeNotifications?.forEach { sbn -> onNotificationPosted(sbn) }
        } catch (e: Exception) {
            Log.e("MediaNLS", "Error scanning active notifications: $e")
        }
        // Also track MediaSessions — works from NLS without MEDIA_CONTENT_CONTROL
        setupSessionTracking()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w("MediaNLS", "onListenerDisconnected")
        teardownSessionTracking()
    }

    private fun setupSessionTracking() {
        try {
            val mgr = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val myComp = ComponentName(this, MediaNotificationListenerService::class.java)
            sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                handler.post { onSessionsChanged(controllers) }
            }
            mgr.addOnActiveSessionsChangedListener(sessionListener!!, myComp)
            onSessionsChanged(mgr.getActiveSessions(myComp))
        } catch (e: Exception) {
            Log.e("MediaNLS", "setupSessionTracking error: $e")
        }
    }

    private fun teardownSessionTracking() {
        handler.removeCallbacks(pollRunnable)
        try {
            val mgr = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
            sessionListener?.let { mgr?.removeOnActiveSessionsChangedListener(it) }
        } catch (_: Exception) {}
        sessionListener = null
        mediaCallback?.let { trackedController?.unregisterCallback(it) }
        mediaCallback = null
        trackedController = null
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            trackedController?.let { dispatchFromMetadata(it.packageName, it.metadata) }
            handler.postDelayed(this, 5000)
        }
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        handler.removeCallbacks(pollRunnable)
        mediaCallback?.let { trackedController?.unregisterCallback(it) }
        mediaCallback = null
        trackedController = null

        val controller = controllers?.firstOrNull() ?: return
        Log.w("MediaNLS", "Tracking session: pkg=${controller.packageName}")
        trackedController = controller

        mediaCallback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                dispatchFromMetadata(controller.packageName, metadata)
            }
        }
        controller.registerCallback(mediaCallback!!)
        dispatchFromMetadata(controller.packageName, controller.metadata)
        // Poll every 2s as fallback — AVRCP BT doesn't always fire onMetadataChanged
        handler.postDelayed(pollRunnable, 2000)
    }

    private fun dispatchFromMetadata(pkg: String?, metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        if (title.isBlank() || title.equals("not provided", ignoreCase = true)) return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val source = sourceNameFor(pkg)
        Log.w("MediaNLS", "Media: source=$source title=$title artist=$artist")
        ServiceManager.getInstance().dispatchServiceManagerEvent(
            ServiceManagerEventType.MEDIA_NOTIFICATION_UPDATE,
            source, title, artist, true
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MEDIA_PACKAGES) return
        // Ignore foreground service / ongoing system notifications ("X está em execução")
        if (sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val source = sourceNameFor(sbn.packageName)
        Log.d("MediaNLS", "Notification: source=$source title=$title")
        ServiceManager.getInstance().dispatchServiceManagerEvent(
            ServiceManagerEventType.MEDIA_NOTIFICATION_UPDATE,
            source, title, artist, true
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in MEDIA_PACKAGES) return
        // Only clear if no other media notification active
        val stillActive = activeNotifications?.any { it.packageName in MEDIA_PACKAGES && it.id != sbn.id }
        if (stillActive == true) return
        ServiceManager.getInstance().dispatchServiceManagerEvent(
            ServiceManagerEventType.MEDIA_NOTIFICATION_UPDATE,
            "", "", "", false
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        teardownSessionTracking()
    }
}
