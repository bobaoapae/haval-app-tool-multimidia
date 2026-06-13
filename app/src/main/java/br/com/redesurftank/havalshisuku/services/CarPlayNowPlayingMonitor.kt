package br.com.redesurftank.havalshisuku.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.havalshisuku.BuildConfig
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarPlayNowPlayingMonitor(
        context: Context,
        private val scope: CoroutineScope,
        private val onUpdate: (CarPlayNowPlayingUpdate) -> Unit
) {
    private val appContext = context.applicationContext
    private val callback = NowPlayingCallback()
    private var serviceBinder: IBinder? = null
    private var bound = false
    private var subscriptionWatchdogJob: Job? = null
    private var callbackRegistered = false
    private var lastServiceBindAttemptAtMs = 0L
    private var lastSubscriptionAttemptAtMs = 0L
    private var lastUpdateAtMs = 0L

    private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    serviceBinder = service
                    bound = service != null
                    callbackRegistered = false
                    startNowPlayingUpdates(service, "service connected")
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    serviceBinder = null
                    bound = false
                    callbackRegistered = false
                    publishClear("service disconnected")
                }

                override fun onBindingDied(name: ComponentName?) {
                    serviceBinder = null
                    bound = false
                    callbackRegistered = false
                    debugLog { "CarPlay now playing service binding died: $name" }
                }

                override fun onNullBinding(name: ComponentName?) {
                    serviceBinder = null
                    bound = false
                    callbackRegistered = false
                    debugLog { "CarPlay now playing service returned null binding: $name" }
                }
            }

    fun start() {
        startSubscriptionWatchdog()
        bindCarPlayService("start")
        ensureNowPlayingSubscription("start")
    }

    fun stop() {
        subscriptionWatchdogJob?.cancel()
        subscriptionWatchdogJob = null
        stopNowPlayingUpdates()
        if (bound) {
            runCatching { appContext.unbindService(connection) }
        }
        bound = false
        serviceBinder = null
        callbackRegistered = false
    }

    private fun bindCarPlayService(reason: String): Boolean {
        val binderAlive = serviceBinder?.isBinderAlive == true
        val nowMs = SystemClock.elapsedRealtime()
        if (
                !shouldRetryCarPlayServiceBindForTest(
                        nowMs = nowMs,
                        lastServiceBindAttemptAtMs = lastServiceBindAttemptAtMs,
                        binderAlive = binderAlive
                )
        ) {
            return binderAlive
        }

        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
            serviceBinder = null
            callbackRegistered = false
        }

        lastServiceBindAttemptAtMs = nowMs
        val explicitIntent =
                Intent().apply {
                    component = ComponentName(CARPLAY_PACKAGE_NAME, CARPLAY_SERVICE_CLASS_NAME)
                }
        val explicitBound =
                runCatching { appContext.bindService(explicitIntent, connection, Context.BIND_AUTO_CREATE) }
                        .getOrDefault(false)

        val actionBound =
                if (explicitBound) {
                    false
                } else {
                    val actionIntent =
                            Intent(CARPLAY_SERVICE_ACTION).apply {
                                setPackage(CARPLAY_PACKAGE_NAME)
                            }
                    runCatching { appContext.bindService(actionIntent, connection, Context.BIND_AUTO_CREATE) }
                            .getOrDefault(false)
                }

        bound = explicitBound || actionBound
        debugLog {
                "CarPlay now playing service bind requested: reason=$reason " +
                        "explicit=$explicitBound action=$actionBound"
        }
        if (!bound) {
            debugLog { "Failed to bind CarPlay service for now playing metadata: $reason" }
        }
        return bound
    }

    fun seekTo(elapsedMs: Long): Boolean {
        val binder = serviceBinder ?: return false
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(CARPLAY_SERVICE_DESCRIPTOR)
            data.writeLong(elapsedMs.coerceAtLeast(0L))
            binder.transact(TRANSACTION_SET_SONG_ELAPSED_TIME, data, null, IBinder.FLAG_ONEWAY)
        } catch (e: RemoteException) {
            debugLog { "Failed to seek CarPlay song elapsed time: ${e.message}" }
            false
        } catch (e: Exception) {
            debugLog { "Unexpected failure seeking CarPlay song elapsed time: ${e.message}" }
            false
        } finally {
            data.recycle()
        }
    }

    fun next(): Boolean {
        return sendPlaybackHid(IAP_HID_PLAYBACK_NEXT, "next")
    }

    fun previous(): Boolean {
        return sendPlaybackHid(IAP_HID_PLAYBACK_PREVIOUS, "previous")
    }

    fun playPause(isCurrentlyPlaying: Boolean): Boolean {
        return if (isCurrentlyPlaying) {
            sendPlaybackHid(IAP_HID_PLAYBACK_PAUSE, "pause")
        } else {
            sendPlaybackHid(IAP_HID_PLAYBACK_PLAY, "play")
        }
    }

    private fun sendPlaybackHid(keyCode: Int, label: String): Boolean {
        if (!sendHidEventOverIap(keyCode, HID_ACTION_DOWN, label)) return false
        scope.launch(Dispatchers.IO) {
            delay(HID_KEY_PRESS_MS)
            sendHidEventOverIap(keyCode, HID_ACTION_UP, "$label release")
        }
        return true
    }

    private fun sendHidEventOverIap(keyCode: Int, keyAction: Int, label: String): Boolean {
        val binder = serviceBinder ?: return false
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(CARPLAY_SERVICE_DESCRIPTOR)
            data.writeInt(keyCode)
            data.writeInt(keyAction)
            val sent =
                    binder.transact(
                            TRANSACTION_SEND_HID_EVENT_OVER_IAP,
                            data,
                            null,
                            IBinder.FLAG_ONEWAY
            )
            if (sent) {
                debugLog { "CarPlay playback HID sent: $label key=$keyCode action=$keyAction" }
            }
            sent
        } catch (e: RemoteException) {
            debugLog { "Failed to send CarPlay playback HID: $label: ${e.message}" }
            false
        } catch (e: Exception) {
            debugLog { "Unexpected failure sending CarPlay playback HID: $label: ${e.message}" }
            false
        } finally {
            data.recycle()
        }
    }

    private fun startSubscriptionWatchdog() {
        if (subscriptionWatchdogJob != null) return
        subscriptionWatchdogJob =
                scope.launch(Dispatchers.Main) {
                    while (isActive) {
                        bindCarPlayService("watchdog")
                        ensureNowPlayingSubscription("watchdog")
                        delay(NOW_PLAYING_SUBSCRIPTION_WATCHDOG_MS)
                    }
                }
    }

    private fun ensureNowPlayingSubscription(reason: String) {
        val binder = serviceBinder
        val binderAlive = binder?.isBinderAlive == true
        val nowMs = SystemClock.elapsedRealtime()
        if (!binderAlive) {
            callbackRegistered = false
            return
        }
        if (
                !shouldRefreshNowPlayingSubscriptionForTest(
                        nowMs = nowMs,
                        lastSubscriptionAttemptAtMs = lastSubscriptionAttemptAtMs,
                        lastUpdateAtMs = lastUpdateAtMs,
                        callbackRegistered = callbackRegistered,
                        binderAlive = true
                )
        ) {
            return
        }
        debugLog { "Refreshing CarPlay now playing callback: $reason" }
        stopNowPlayingUpdates()
        startNowPlayingUpdates(binder, reason)
    }

    private fun startNowPlayingUpdates(binder: IBinder?, reason: String) {
        if (binder == null) return
        val data = Parcel.obtain()
        try {
            lastSubscriptionAttemptAtMs = SystemClock.elapsedRealtime()
            data.writeInterfaceToken(CARPLAY_SERVICE_DESCRIPTOR)
            data.writeStrongBinder(callback)
            callbackRegistered =
                    binder.transact(TRANSACTION_START_NOW_PLAYING, data, null, IBinder.FLAG_ONEWAY)
            if (callbackRegistered) {
                debugLog { "Registered CarPlay now playing callback: $reason" }
            } else {
                debugLog { "CarPlay now playing callback registration returned false: $reason" }
            }
        } catch (e: RemoteException) {
            callbackRegistered = false
            debugLog { "Failed to register CarPlay now playing callback: ${e.message}" }
        } catch (e: Exception) {
            callbackRegistered = false
            debugLog { "Unexpected failure registering CarPlay now playing callback: ${e.message}" }
        } finally {
            data.recycle()
        }
    }

    private fun stopNowPlayingUpdates() {
        val binder = serviceBinder ?: return
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(CARPLAY_SERVICE_DESCRIPTOR)
            data.writeStrongBinder(callback)
            binder.transact(TRANSACTION_STOP_NOW_PLAYING, data, null, IBinder.FLAG_ONEWAY)
            callbackRegistered = false
        } catch (e: Exception) {
            debugLog { "Failed to unregister CarPlay now playing callback: ${e.message}" }
        } finally {
            data.recycle()
        }
    }

    private inner class NowPlayingCallback : Binder() {
        private val localInterface =
                object : IInterface {
                    override fun asBinder(): IBinder = this@NowPlayingCallback
                }

        init {
            attachInterface(localInterface, NOW_PLAYING_CALLBACK_DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(NOW_PLAYING_CALLBACK_DESCRIPTOR)
                    true
                }
                TRANSACTION_NOW_PLAYING_UPDATE -> {
                    data.enforceInterface(NOW_PLAYING_CALLBACK_DESCRIPTOR)
                    val update =
                            if (data.readInt() != 0) {
                                readNowPlayingInfo(data)
                            } else {
                                null
                            }
                    if (update != null) {
                        lastUpdateAtMs = SystemClock.elapsedRealtime()
                        callbackRegistered = true
                        publishNowPlaying(update)
                    } else {
                        lastUpdateAtMs = SystemClock.elapsedRealtime()
                        callbackRegistered = true
                        publishClear("empty now playing update")
                    }
                    true
                }
                TRANSACTION_PLAYBACK_LIST_UPDATE -> {
                    data.enforceInterface(NOW_PLAYING_CALLBACK_DESCRIPTOR)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    private fun readNowPlayingInfo(parcel: Parcel): RawNowPlayingInfo {
        val title = parcel.readString()
        val artist = parcel.readString()
        val durationMs = parcel.readLong()
        val artworkPath = parcel.readString()
        val playbackStatus = parcel.readInt()
        val elapsedMs = parcel.readLong()
        val shuffleMode = parcel.readInt()
        val repeatMode = parcel.readInt()
        val artworkSize = parcel.readInt()
        val artworkData =
                if (artworkSize > 0) {
                    ByteArray(artworkSize).also { parcel.readByteArray(it) }
                } else {
                    null
                }
        return RawNowPlayingInfo(
                title = title,
                artist = artist,
                durationMs = durationMs,
                artworkPath = artworkPath,
                playbackStatus = playbackStatus,
                elapsedMs = elapsedMs,
                shuffleMode = shuffleMode,
                repeatMode = repeatMode,
                artworkData = artworkData
        )
    }

    private fun publishNowPlaying(info: RawNowPlayingInfo) {
        scope.launch(Dispatchers.IO) {
            val artwork =
                    decodeArtworkBytes(info.artworkData)
                            ?: decodeArtworkPath(info.artworkPath)
            val title = info.title?.takeIf { it.isNotBlank() }
            val artist = info.artist?.takeIf { it.isNotBlank() }
            val isPlaying = info.playbackStatus == PLAYBACK_STATE_PLAYING
            if (title == null && artist == null && artwork == null && !isPlaying) {
                publishClear("blank stopped now playing update")
                return@launch
            }
            val update =
                    CarPlayNowPlayingUpdate(
                            title = title,
                            artist = artist,
                            artwork = artwork,
                            artworkPath = info.artworkPath?.takeIf { it.isNotBlank() },
                            durationMs = info.durationMs.takeIf { it > 0L } ?: 0L,
                            elapsedMs = info.elapsedMs.takeIf { it >= 0L } ?: 0L,
                            isPlaying = isPlaying
                    )
            debugLog {
                "CarPlay now playing title=${update.title ?: "-"} artist=${update.artist ?: "-"} " +
                        "artwork=${artwork?.let { "${it.width}x${it.height}" } ?: "none"} " +
                        "path=${update.artworkPath ?: "-"} playing=${update.isPlaying}"
            }
            withContext(Dispatchers.Main) {
                onUpdate(update)
            }
        }
    }

    private fun publishClear(reason: String) {
        debugLog { "Clearing CarPlay now playing metadata: $reason" }
        scope.launch(Dispatchers.Main) {
            onUpdate(CarPlayNowPlayingUpdate(clear = true))
        }
    }

    private fun decodeArtworkBytes(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val bounds =
                    BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = calculateBitmapSampleSize(bounds, 720, 720)
                    }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let {
                normalizeArtwork(it)
            }
        } catch (e: Exception) {
            debugLog { "Failed to decode CarPlay artwork bytes: ${e.message}" }
            null
        }
    }

    private fun decodeArtworkPath(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(path)
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return null

            if (scheme.isNullOrBlank()) {
                return BitmapFactory.decodeFile(path)?.let { normalizeArtwork(it) }
            }

            val bounds =
                    BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = calculateBitmapSampleSize(bounds, 720, 720)
                    }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }?.let { normalizeArtwork(it) }
        } catch (e: SecurityException) {
            debugLog { "CarPlay artwork path denied: $path" }
            null
        } catch (e: Exception) {
            debugLog { "Failed to decode CarPlay artwork path: $path: ${e.message}" }
            null
        }
    }

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }

    private fun calculateBitmapSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
    ): Int {
        var inSampleSize = 1
        val height = options.outHeight
        val width = options.outWidth
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight &&
                            halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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

    private data class RawNowPlayingInfo(
            val title: String?,
            val artist: String?,
            val durationMs: Long,
            val artworkPath: String?,
            val playbackStatus: Int,
            val elapsedMs: Long,
            val shuffleMode: Int,
            val repeatMode: Int,
            val artworkData: ByteArray?
    )

    companion object {
        private const val TAG = "CarPlayNowPlayingMonitor"
        private const val CARPLAY_PACKAGE_NAME = "com.ts.carplay"
        private const val CARPLAY_SERVICE_CLASS_NAME = "com.ts.carplay.CarPlayService"
        private const val CARPLAY_SERVICE_ACTION = "com.ts.carplay.action.CarPlayService"
        private const val CARPLAY_SERVICE_DESCRIPTOR = "com.ts.carplay.common.aidl.ICarPlayService"
        private const val NOW_PLAYING_CALLBACK_DESCRIPTOR =
                "com.ts.carplay.common.aidl.INowPlayingUpdateCallback"
        private const val TRANSACTION_START_NOW_PLAYING = 9
        private const val TRANSACTION_STOP_NOW_PLAYING = 10
        private const val TRANSACTION_SEND_HID_EVENT_OVER_IAP = 25
        private const val TRANSACTION_SET_SONG_ELAPSED_TIME = 31
        private const val TRANSACTION_NOW_PLAYING_UPDATE = 1
        private const val TRANSACTION_PLAYBACK_LIST_UPDATE = 2
        private const val PLAYBACK_STATE_PLAYING = 1
        private const val IAP_HID_PLAYBACK_PLAY = 1
        private const val IAP_HID_PLAYBACK_PAUSE = 2
        private const val IAP_HID_PLAYBACK_NEXT = 4
        private const val IAP_HID_PLAYBACK_PREVIOUS = 8
        private const val HID_ACTION_DOWN = 0
        private const val HID_ACTION_UP = 1
        private const val HID_KEY_PRESS_MS = 90L
        private const val NOW_PLAYING_SUBSCRIPTION_WATCHDOG_MS = 5_000L
        private const val NOW_PLAYING_SERVICE_BIND_RETRY_MS = 5_000L
        private const val NOW_PLAYING_SUBSCRIPTION_RETRY_MS = 15_000L
        private const val NOW_PLAYING_UPDATE_STALE_MS = 20_000L

        internal fun shouldRetryCarPlayServiceBindForTest(
                nowMs: Long,
                lastServiceBindAttemptAtMs: Long,
                binderAlive: Boolean
        ): Boolean {
            if (binderAlive) return false
            if (lastServiceBindAttemptAtMs <= 0L) return true
            val elapsedSinceAttempt = nowMs - lastServiceBindAttemptAtMs
            return elapsedSinceAttempt !in 0 until NOW_PLAYING_SERVICE_BIND_RETRY_MS
        }

        internal fun shouldRefreshNowPlayingSubscriptionForTest(
                nowMs: Long,
                lastSubscriptionAttemptAtMs: Long,
                lastUpdateAtMs: Long,
                callbackRegistered: Boolean,
                binderAlive: Boolean
        ): Boolean {
            if (!binderAlive) return false
            val elapsedSinceAttempt = nowMs - lastSubscriptionAttemptAtMs
            if (
                    lastSubscriptionAttemptAtMs > 0L &&
                            elapsedSinceAttempt in 0 until NOW_PLAYING_SUBSCRIPTION_RETRY_MS
            ) {
                return false
            }
            if (!callbackRegistered) return true
            if (lastUpdateAtMs <= 0L) return true
            val elapsedSinceUpdate = nowMs - lastUpdateAtMs
            return elapsedSinceUpdate >= NOW_PLAYING_UPDATE_STALE_MS
        }
    }
}

data class CarPlayNowPlayingUpdate(
        val title: String? = null,
        val artist: String? = null,
        val artwork: Bitmap? = null,
        val artworkPath: String? = null,
        val durationMs: Long = 0L,
        val elapsedMs: Long = 0L,
        val isPlaying: Boolean = false,
        val clear: Boolean = false
)
