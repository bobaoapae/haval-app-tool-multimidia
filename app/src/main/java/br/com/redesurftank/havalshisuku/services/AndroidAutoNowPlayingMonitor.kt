package br.com.redesurftank.havalshisuku.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidAutoNowPlayingMonitor(
        context: Context,
        private val scope: CoroutineScope,
        private val onUpdate: (AndroidAutoNowPlayingUpdate) -> Unit
) {
    private val appContext = context.applicationContext
    private val callback = LinkCallbackBinder()
    private val stateLock = Any()
    private var serviceBinder: IBinder? = null
    private var bound = false
    private var bindRetryJob: Job? = null
    private var pollJob: Job? = null
    private var metadataResyncJob: Job? = null
    private var initialMetadataResyncJob: Job? = null
    private var lastProgressSeconds = 0
    private var lastDurationSeconds = 0
    private var lastMusicStatus = MUSIC_STATUS_NOT_START
    private var lastAcceptedProgressAtMs = 0L
    private var lastPlaybackEvidenceAtMs = 0L
    private var lastServiceConnectedAtMs = 0L
    private var lastMetadataResyncAtMs = 0L
    private var hasMetadata = false
    private var lastMetadataSignature: String? = null
    private var lastPublishedSignature: String? = null

    private val deathRecipient =
            IBinder.DeathRecipient {
                Log.w(TAG, "Android Auto media binder died")
                serviceBinder = null
                bound = false
                stopPolling()
                stopInitialMetadataResyncBurst()
                startBindRetry()
                publishClear("binder died")
            }

    private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    serviceBinder = service
                    bound = true
                    stopBindRetry()
                    runCatching { service?.linkToDeath(deathRecipient, 0) }
                    synchronized(stateLock) {
                        lastServiceConnectedAtMs = SystemClock.elapsedRealtime()
                    }
                    registerCallback(service)
                    refreshStatusAndProgress()
                    startPolling()
                    startMetadataResyncWatchdog()
                    startInitialMetadataResyncBurst()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    serviceBinder = null
                    bound = false
                    stopPolling()
                    stopInitialMetadataResyncBurst()
                    startBindRetry()
                    publishClear("service disconnected")
                }
            }

    fun start() {
        if (bound || serviceBinder?.isBinderAlive == true) return
        if (!bindAndroidAutoService()) {
            startBindRetry()
        }
    }

    private fun bindAndroidAutoService(): Boolean {
        val intent =
                Intent(ANDROID_AUTO_SERVICE_ACTION).apply {
                    setPackage(ANDROID_AUTO_SERVICE_PACKAGE)
                }
        val requested =
                runCatching { appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
                        .getOrDefault(false)
        bound = requested
        if (!requested) {
            Log.w(TAG, "Failed to bind Android Auto service for now playing metadata")
        }
        return requested
    }

    fun stop() {
        stopBindRetry()
        stopPolling()
        stopMetadataResyncWatchdog()
        stopInitialMetadataResyncBurst()
        unregisterCallback()
        serviceBinder?.let { binder -> runCatching { binder.unlinkToDeath(deathRecipient, 0) } }
        if (bound) {
            runCatching { appContext.unbindService(connection) }
        }
        bound = false
        serviceBinder = null
        synchronized(stateLock) {
            lastProgressSeconds = 0
            lastDurationSeconds = 0
            lastMusicStatus = MUSIC_STATUS_NOT_START
            lastAcceptedProgressAtMs = 0L
            lastPlaybackEvidenceAtMs = 0L
            lastServiceConnectedAtMs = 0L
            lastMetadataResyncAtMs = 0L
            hasMetadata = false
            lastMetadataSignature = null
            lastPublishedSignature = null
        }
    }

    private fun startBindRetry() {
        if (bindRetryJob != null) return
        bindRetryJob =
                scope.launch(Dispatchers.Main) {
                    while (isActive && serviceBinder?.isBinderAlive != true) {
                        delay(BIND_RETRY_INTERVAL_MS)
                        if (serviceBinder?.isBinderAlive == true) break
                        if (!bound) {
                            bindAndroidAutoService()
                        }
                    }
                    bindRetryJob = null
                }
    }

    private fun stopBindRetry() {
        bindRetryJob?.cancel()
        bindRetryJob = null
    }

    fun next(): Boolean {
        return transactNoArgCommand(TRANSACTION_NEXT, "next")
    }

    fun previous(): Boolean {
        return transactNoArgCommand(TRANSACTION_PREVIOUS, "previous")
    }

    fun play(): Boolean {
        return transactNoArgCommand(TRANSACTION_PLAY, "play")
    }

    fun pause(): Boolean {
        return transactNoArgCommand(TRANSACTION_PAUSE, "pause")
    }

    fun playPause(isCurrentlyPlaying: Boolean): Boolean {
        return if (isCurrentlyPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(targetMs: Long): Boolean {
        Log.i(TAG, "Ignoring Android Auto seek request; progress bar is visual only")
        return false
    }

    private fun registerCallback(binder: IBinder?) {
        if (binder == null) return
        if (transactCallback(TRANSACTION_ADD_LINK_CALLBACK, binder)) {
            Log.i(TAG, "Registered Android Auto media callback")
        } else {
            Log.w(TAG, "Failed to register Android Auto media callback")
        }
    }

    private fun unregisterCallback() {
        val binder = serviceBinder ?: return
        transactCallback(TRANSACTION_REMOVE_LINK_CALLBACK, binder)
    }

    private fun transactCallback(transaction: Int, binder: IBinder): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(LINK_COMMAND_DESCRIPTOR)
            data.writeStrongBinder(callback)
            val sent = binder.transact(transaction, data, reply, 0)
            if (sent) reply.readException()
            sent
        } catch (e: RemoteException) {
            Log.w(TAG, "Android Auto callback transaction $transaction failed", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected Android Auto callback transaction $transaction failure", e)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactNoArgCommand(transaction: Int, label: String): Boolean {
        val binder = serviceBinder ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(LINK_COMMAND_DESCRIPTOR)
            val sent = binder.transact(transaction, data, reply, 0)
            if (sent) reply.readException()
            if (sent) {
                Log.i(TAG, "Android Auto media command sent: $label")
            }
            sent
        } catch (e: RemoteException) {
            Log.w(TAG, "Android Auto media command failed: $label", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected Android Auto media command failure: $label", e)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun startPolling() {
        if (pollJob != null) return
        pollJob =
                scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        refreshStatusAndProgress()
                        delay(PROGRESS_POLL_INTERVAL_MS)
                    }
                }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun startMetadataResyncWatchdog() {
        if (metadataResyncJob != null) return
        metadataResyncJob =
                scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(METADATA_RESYNC_POLL_INTERVAL_MS)
                        refreshStatusAndProgress()
                        val linkActive = isAndroidAutoLinkActive(readLinkStatus())
                        val shouldResync =
                                synchronized(stateLock) {
                                    val now = SystemClock.elapsedRealtime()
                                    shouldResyncCallbackForMissingMetadata(
                                            isLinkActive = linkActive,
                                            musicStatus = lastMusicStatus,
                                            hasMetadata = hasMetadata,
                                            progressSeconds = lastProgressSeconds,
                                            nowMs = now,
                                            lastResyncAtMs = lastMetadataResyncAtMs,
                                            isInBootstrapWindow =
                                                    lastServiceConnectedAtMs > 0L &&
                                                            now - lastServiceConnectedAtMs <=
                                                                    ANDROID_AUTO_INITIAL_METADATA_BOOTSTRAP_MS
                                    )
                                            .also {
                                                if (it) {
                                                    lastMetadataResyncAtMs = now
                                                }
                                            }
                                }
                        if (shouldResync) {
                            Log.i(
                                    TAG,
                                    "Android Auto metadata missing while media is active; re-registering callback"
                            )
                            unregisterCallback()
                            delay(120)
                            registerCallback(serviceBinder)
                        }
                    }
                }
    }

    private fun stopMetadataResyncWatchdog() {
        metadataResyncJob?.cancel()
        metadataResyncJob = null
    }

    private fun startInitialMetadataResyncBurst() {
        initialMetadataResyncJob?.cancel()
        initialMetadataResyncJob =
                scope.launch(Dispatchers.IO) {
                    val delaysMs = longArrayOf(450L, 1_100L, 2_200L, 4_200L, 7_500L)
                    for (delayMs in delaysMs) {
                        delay(delayMs)
                        refreshStatusAndProgress()
                        val linkActive = isAndroidAutoLinkActive(readLinkStatus())
                        val shouldResync =
                                synchronized(stateLock) {
                                    linkActive && !hasMetadata
                                }
                        if (!shouldResync) continue

                        Log.i(
                                TAG,
                                "Android Auto initial metadata missing; refreshing callback registration"
                        )
                        unregisterCallback()
                        delay(90)
                        registerCallback(serviceBinder)
                    }
                    initialMetadataResyncJob = null
                }
    }

    private fun stopInitialMetadataResyncBurst() {
        initialMetadataResyncJob?.cancel()
        initialMetadataResyncJob = null
    }

    private fun refreshStatusAndProgress() {
        val status = readIntTransaction(TRANSACTION_GET_MUSIC_STATUS)
        if (status != null) {
            handleMusicStatus(status, fromPoll = true)
        }

        val progressSeconds = readIntTransaction(TRANSACTION_GET_MEDIA_PROGRESS)
        if (progressSeconds != null) {
            handleMediaProgress(progressSeconds, fromPoll = true)
        }
    }

    private fun readIntTransaction(transaction: Int): Int? {
        val binder = serviceBinder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(LINK_COMMAND_DESCRIPTOR)
            val sent = binder.transact(transaction, data, reply, 0)
            if (!sent) return null
            reply.readException()
            reply.readInt()
        } catch (e: RemoteException) {
            Log.w(TAG, "Android Auto int transaction $transaction failed", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected Android Auto int transaction $transaction failure", e)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun readLinkStatus(): Int? {
        return readIntTransaction(TRANSACTION_GET_LINK_STATUS)
    }

    private inner class LinkCallbackBinder : Binder() {
        private val localInterface =
                object : IInterface {
                    override fun asBinder(): IBinder = this@LinkCallbackBinder
                }

        init {
            attachInterface(localInterface, LINK_CALLBACK_DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(LINK_CALLBACK_DESCRIPTOR)
                    true
                }
                TRANSACTION_ON_MUSIC_STATUS_CHANGED -> {
                    data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                    handleMusicStatus(data.readInt(), fromPoll = false)
                    true
                }
                TRANSACTION_ON_MEDIA_METADATA_CHANGE -> {
                    data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                    val metadata =
                            if (data.readInt() != 0) {
                                readMediaMetadata(data)
                            } else {
                                null
                            }
                    if (metadata != null) {
                        publishMetadata(metadata)
                    } else {
                        publishClear("empty metadata callback")
                    }
                    true
                }
                TRANSACTION_ON_MEDIA_PROGRESS_CHANGE -> {
                    data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                    handleMediaProgress(data.readInt(), fromPoll = false)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    private fun readMediaMetadata(parcel: Parcel): RawAndroidAutoMediaMetadata {
        val song = parcel.readString()
        val album = parcel.readString()
        val artist = parcel.readString()
        val durationSeconds = parcel.readInt()
        val artworkLength = parcel.readInt()
        val albumArt =
                if (artworkLength > 0) {
                    ByteArray(artworkLength).also { parcel.readByteArray(it) }
                } else {
                    null
                }
        val composer = parcel.readString()
        val genre = parcel.readString()
        val year = parcel.readString()
        val trackNumber = parcel.readInt()
        val totalTracks = parcel.readInt()
        val shuffleMode = parcel.readInt()
        val repeatMode = parcel.readInt()
        return RawAndroidAutoMediaMetadata(
                song = song,
                album = album,
                artist = artist,
                durationSeconds = durationSeconds,
                albumArt = albumArt,
                composer = composer,
                genre = genre,
                year = year,
                trackNumber = trackNumber,
                totalTracks = totalTracks,
                shuffleMode = shuffleMode,
                repeatMode = repeatMode
        )
    }

    private fun publishMetadata(metadata: RawAndroidAutoMediaMetadata) {
        val title = metadata.song?.takeIf { it.isNotBlank() }
        val artist = metadata.artist?.takeIf { it.isNotBlank() }
        val album = metadata.album?.takeIf { it.isNotBlank() }
        val durationMs = metadata.durationSeconds.coerceAtLeast(0).toLong() * 1000L
        val artworkKey = metadata.albumArt?.let { "${it.size}:${it.contentHashCode()}" }
        val metadataSignature = mediaSignature(title, artist, album, durationMs, artworkKey)

        synchronized(stateLock) {
            if (
                    metadataSignature != null &&
                            lastMetadataSignature != null &&
                            metadataSignature != lastMetadataSignature
            ) {
                lastProgressSeconds = 0
            }
            hasMetadata = metadataSignature != null
            lastMetadataSignature = metadataSignature
            lastDurationSeconds = metadata.durationSeconds.coerceAtLeast(0)
        }

        scope.launch(Dispatchers.IO) {
            val artwork = decodeArtworkBytes(metadata.albumArt)
            val progressSeconds: Int
            val musicStatus: Int
            synchronized(stateLock) {
                progressSeconds = lastProgressSeconds
                musicStatus = lastMusicStatus
            }

            val update =
                    AndroidAutoNowPlayingUpdate(
                            title = title,
                            artist = artist,
                            album = album,
                            artwork = artwork,
                            artworkKey = artworkKey,
                            metadataSignature = metadataSignature,
                            durationMs = durationMs.takeIf { it > 0L },
                            elapsedMs = progressSeconds.coerceAtLeast(0).toLong() * 1000L,
                            progressUpdatedAtMs = SystemClock.elapsedRealtime(),
                            isPlaying = musicStatus == MUSIC_STATUS_PLAYING
                    )
            if (metadataSignature == null && artwork == null && musicStatus != MUSIC_STATUS_PLAYING) {
                publishClear("blank stopped metadata")
                return@launch
            }

            val logSignature =
                    "${metadataSignature ?: "-"}|${artwork?.width ?: 0}x${artwork?.height ?: 0}"
            synchronized(stateLock) {
                if (lastPublishedSignature != logSignature) {
                    lastPublishedSignature = logSignature
                    Log.i(
                            TAG,
                            "Android Auto media title=${title ?: "-"} artist=${artist ?: "-"} " +
                                    "album=${album ?: "-"} duration=${durationMs}ms " +
                                    "artwork=${artwork?.let { "${it.width}x${it.height}" } ?: "none"}"
                    )
                }
            }

            withContext(Dispatchers.Main) { onUpdate(update) }
        }
    }

    private fun handleMusicStatus(status: Int, fromPoll: Boolean) {
        val normalized = normalizeMusicStatus(status)
        val linkActiveForEmptyState =
                if (normalized == MUSIC_STATUS_NOT_START) {
                    isAndroidAutoLinkActive(readLinkStatus())
                } else {
                    false
                }
        val changed =
                synchronized(stateLock) {
                    if (
                            shouldIgnoreTransientNonPlayingStatus(
                                    previousStatus = lastMusicStatus,
                                    newStatus = normalized,
                                    hasMetadata = hasMetadata,
                                    lastProgressSeconds = lastProgressSeconds,
                                    lastAcceptedProgressAtMs = lastAcceptedProgressAtMs,
                                    nowMs = SystemClock.elapsedRealtime()
                            )
                    ) {
                        Log.d(
                                TAG,
                                "Ignoring transient Android Auto non-playing status=$normalized while progress is advancing"
                        )
                        return
                    }
                    if (
                            normalized == MUSIC_STATUS_NOT_START &&
                                    !hasMetadata &&
                                    shouldHoldMissingMetadataAndroidAutoState(
                                            isLinkActive = linkActiveForEmptyState,
                                            previousStatus = lastMusicStatus,
                                            lastProgressSeconds = lastProgressSeconds,
                                            lastPlaybackEvidenceAtMs = lastPlaybackEvidenceAtMs,
                                            lastServiceConnectedAtMs = lastServiceConnectedAtMs,
                                            nowMs = SystemClock.elapsedRealtime()
                                    )
                    ) {
                        Log.d(
                                TAG,
                                "Holding Android Auto empty metadata state during active link bootstrap"
                        )
                        return
                    }
                    val isChanged = lastMusicStatus != normalized
                    lastMusicStatus = normalized
                    if (normalized == MUSIC_STATUS_PLAYING) {
                        lastPlaybackEvidenceAtMs = SystemClock.elapsedRealtime()
                    }
                    isChanged
                }
        if (!changed && fromPoll) return

        val hasMetadataSnapshot = synchronized(stateLock) { hasMetadata }
        if (normalized == MUSIC_STATUS_NOT_START && !hasMetadataSnapshot) {
            publishClear("music status not started")
            return
        }

        scope.launch(Dispatchers.Main) {
            onUpdate(
                    AndroidAutoNowPlayingUpdate(
                            isPlaying = normalized == MUSIC_STATUS_PLAYING
                    )
            )
        }
    }

    private fun handleMediaProgress(
            progressSeconds: Int,
            fromPoll: Boolean,
            forceAcceptZeroReset: Boolean = false
    ) {
        val normalized = progressSeconds.coerceAtLeast(0)
        val shouldPublish =
                synchronized(stateLock) {
                    if (
                            shouldIgnoreStaleLowProgressSample(
                                    previousProgressSeconds = lastProgressSeconds,
                                    newProgressSeconds = normalized,
                                    durationSeconds = lastDurationSeconds,
                                    isPlaying = lastMusicStatus == MUSIC_STATUS_PLAYING,
                                    hasMetadata = hasMetadata,
                                    forceAcceptProgressReset = forceAcceptZeroReset
                            )
                    ) {
                        Log.d(
                                TAG,
                                "Ignoring stale Android Auto low progress while playing; previous=${lastProgressSeconds}s new=${normalized}s"
                        )
                        return
                    }
                    val changed = lastProgressSeconds != normalized
                    lastProgressSeconds = normalized
                    if (normalized > ANDROID_AUTO_STALE_LOW_PROGRESS_MAX_SECONDS) {
                        lastAcceptedProgressAtMs = SystemClock.elapsedRealtime()
                        lastPlaybackEvidenceAtMs = lastAcceptedProgressAtMs
                    }
                    changed || !fromPoll
                }
        if (!shouldPublish) return

        val hasMetadataSnapshot = synchronized(stateLock) { hasMetadata }
        if (!hasMetadataSnapshot) return

        scope.launch(Dispatchers.Main) {
            onUpdate(
                    AndroidAutoNowPlayingUpdate(
                            elapsedMs = normalized.toLong() * 1000L,
                            progressUpdatedAtMs = SystemClock.elapsedRealtime(),
                            isPlaying =
                                    synchronized(stateLock) {
                                        lastMusicStatus == MUSIC_STATUS_PLAYING
                                    }
                    )
            )
        }
    }

    private fun publishClear(reason: String) {
        Log.i(TAG, "Clearing Android Auto media metadata: $reason")
        scope.launch(Dispatchers.Main) {
            onUpdate(AndroidAutoNowPlayingUpdate(clear = true))
        }
    }

    private fun publishOptimisticProgress(elapsedMs: Long) {
        handleMediaProgress(
                millisToSecondsInt(elapsedMs),
                fromPoll = false,
                forceAcceptZeroReset = true
        )
    }

    private fun millisToSecondsInt(elapsedMs: Long): Int {
        return (elapsedMs.coerceAtLeast(0L) / 1000L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
    }

    private fun decodeArtworkBytes(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val bounds =
                    BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = calculateBitmapSampleSize(bounds, 720, 720)
                    }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let {
                normalizeArtwork(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode Android Auto artwork bytes", e)
            null
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

    private fun mediaSignature(
            title: String?,
            artist: String?,
            album: String?,
            durationMs: Long,
            artworkKey: String?
    ): String? {
        val normalizedTitle = title?.trim().orEmpty()
        val normalizedArtist = artist?.trim().orEmpty()
        val normalizedAlbum = album?.trim().orEmpty()
        val normalizedArtwork = artworkKey?.trim().orEmpty()
        if (normalizedTitle.isBlank() &&
                        normalizedArtist.isBlank() &&
                        normalizedAlbum.isBlank() &&
                        normalizedArtwork.isBlank() &&
                        durationMs <= 0L
        ) {
            return null
        }
        return "$normalizedTitle|$normalizedArtist|$normalizedAlbum|$durationMs|$normalizedArtwork"
    }

    private fun normalizeMusicStatus(status: Int): Int {
        return when (status) {
            MUSIC_STATUS_PLAYING -> MUSIC_STATUS_PLAYING
            MUSIC_STATUS_PAUSED -> MUSIC_STATUS_PAUSED
            else -> MUSIC_STATUS_NOT_START
        }
    }

    private data class RawAndroidAutoMediaMetadata(
            val song: String?,
            val album: String?,
            val artist: String?,
            val durationSeconds: Int,
            val albumArt: ByteArray?,
            val composer: String?,
            val genre: String?,
            val year: String?,
            val trackNumber: Int,
            val totalTracks: Int,
            val shuffleMode: Int,
            val repeatMode: Int
    )

    companion object {
        private const val TAG = "AndroidAutoNowPlaying"
        private const val ANDROID_AUTO_SERVICE_PACKAGE = "com.ts.androidauto.projectionservice"
        private const val ANDROID_AUTO_SERVICE_ACTION = "com.ts.androidauto.action.AndroidAutoService"
        private const val LINK_COMMAND_DESCRIPTOR = "com.ts.androidauto.sdk.aidl.LinkCommand"
        private const val LINK_CALLBACK_DESCRIPTOR = "com.ts.androidauto.sdk.aidl.LinkCallback"
        private const val TRANSACTION_ADD_LINK_CALLBACK = 1
        private const val TRANSACTION_REMOVE_LINK_CALLBACK = 2
        private const val TRANSACTION_NEXT = 24
        private const val TRANSACTION_PREVIOUS = 25
        private const val TRANSACTION_GET_MEDIA_PROGRESS = 26
        private const val TRANSACTION_GET_MUSIC_STATUS = 27
        private const val TRANSACTION_PLAY = 28
        private const val TRANSACTION_PAUSE = 29
        private const val TRANSACTION_GET_LINK_STATUS = 21
        private const val TRANSACTION_ON_MUSIC_STATUS_CHANGED = 4
        private const val TRANSACTION_ON_MEDIA_METADATA_CHANGE = 5
        private const val TRANSACTION_ON_MEDIA_PROGRESS_CHANGE = 6
        private const val MUSIC_STATUS_NOT_START = 0
        private const val MUSIC_STATUS_PLAYING = 1
        private const val MUSIC_STATUS_PAUSED = 2
        private const val PROGRESS_POLL_INTERVAL_MS = 2_000L
        private const val BIND_RETRY_INTERVAL_MS = 3_000L
        private const val METADATA_RESYNC_POLL_INTERVAL_MS = 4_000L
        private const val METADATA_RESYNC_COOLDOWN_MS = 12_000L
        private const val ANDROID_AUTO_INITIAL_METADATA_BOOTSTRAP_MS = 20_000L
        private const val ANDROID_AUTO_MISSING_METADATA_STATE_HOLD_MS = 12_000L
        internal fun shouldIgnoreStaleLowProgressSample(
                previousProgressSeconds: Int,
                newProgressSeconds: Int,
                durationSeconds: Int,
                isPlaying: Boolean,
                hasMetadata: Boolean,
                forceAcceptProgressReset: Boolean
        ): Boolean {
            if (forceAcceptProgressReset) return false
            if (!isPlaying || !hasMetadata) return false
            if (newProgressSeconds > ANDROID_AUTO_STALE_LOW_PROGRESS_MAX_SECONDS) return false
            if (previousProgressSeconds <= ANDROID_AUTO_STALE_LOW_PROGRESS_PREVIOUS_MIN_SECONDS) {
                return false
            }
            if (durationSeconds > 0 && previousProgressSeconds >= durationSeconds - 2) {
                return false
            }
            return true
        }

        internal fun shouldIgnoreTransientNonPlayingStatus(
                previousStatus: Int,
                newStatus: Int,
                hasMetadata: Boolean,
                lastProgressSeconds: Int,
                lastAcceptedProgressAtMs: Long,
                nowMs: Long
        ): Boolean {
            if (previousStatus != MUSIC_STATUS_PLAYING) return false
            if (newStatus == MUSIC_STATUS_PLAYING) return false
            if (!hasMetadata) return false
            if (lastProgressSeconds <= ANDROID_AUTO_STALE_LOW_PROGRESS_PREVIOUS_MIN_SECONDS) {
                return false
            }
            if (lastAcceptedProgressAtMs <= 0L) return false
            return nowMs - lastAcceptedProgressAtMs <=
                    ANDROID_AUTO_NON_PLAYING_STATUS_GRACE_MS
        }

        internal fun shouldResyncCallbackForMissingMetadata(
                isLinkActive: Boolean,
                musicStatus: Int,
                hasMetadata: Boolean,
                progressSeconds: Int,
                nowMs: Long,
                lastResyncAtMs: Long,
                isInBootstrapWindow: Boolean = false
        ): Boolean {
            if (!isLinkActive) return false
            if (hasMetadata) return false
            if (
                    musicStatus != MUSIC_STATUS_PLAYING &&
                            progressSeconds <= 0 &&
                            !isInBootstrapWindow
            ) {
                return false
            }
            if (lastResyncAtMs > 0L && nowMs - lastResyncAtMs < METADATA_RESYNC_COOLDOWN_MS) {
                return false
            }
            return true
        }

        internal fun shouldHoldMissingMetadataAndroidAutoState(
                isLinkActive: Boolean,
                previousStatus: Int,
                lastProgressSeconds: Int,
                lastPlaybackEvidenceAtMs: Long,
                lastServiceConnectedAtMs: Long,
                nowMs: Long
        ): Boolean {
            if (!isLinkActive) return false
            if (previousStatus == MUSIC_STATUS_PLAYING) return true
            if (lastProgressSeconds > 0) return true
            if (
                    lastPlaybackEvidenceAtMs > 0L &&
                            nowMs - lastPlaybackEvidenceAtMs <=
                                    ANDROID_AUTO_MISSING_METADATA_STATE_HOLD_MS
            ) {
                return true
            }
            return lastServiceConnectedAtMs > 0L &&
                    nowMs - lastServiceConnectedAtMs <=
                            ANDROID_AUTO_INITIAL_METADATA_BOOTSTRAP_MS
        }

        private fun isAndroidAutoLinkActive(status: Int?): Boolean {
            return status == 3 || status == 7 || status == 8
        }

        private const val ANDROID_AUTO_STALE_LOW_PROGRESS_MAX_SECONDS = 1
        private const val ANDROID_AUTO_STALE_LOW_PROGRESS_PREVIOUS_MIN_SECONDS = 3
        private const val ANDROID_AUTO_NON_PLAYING_STATUS_GRACE_MS = 5_000L
    }
}

data class AndroidAutoNowPlayingUpdate(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val artwork: Bitmap? = null,
        val artworkKey: String? = null,
        val metadataSignature: String? = null,
        val durationMs: Long? = null,
        val elapsedMs: Long? = null,
        val progressUpdatedAtMs: Long = SystemClock.elapsedRealtime(),
        val isPlaying: Boolean? = null,
        val clear: Boolean = false
)
