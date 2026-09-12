package br.com.redesurftank.havalshisuku.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.havalshisuku.managers.AndroidAutoClusterController
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry

/**
 * Registers a `LinkCallback` on the vendor projection service purely to receive
 * turn-by-turn, and feeds [AndroidAutoClusterController.onNavigationUpdate].
 *
 * Deliberately separate from [AndroidAutoNowPlayingMonitor]: that one is held
 * off by `ANDROID_AUTO_NOW_PLAYING_MONITOR_ENABLED = false` because AA media is
 * owned by the patched AA App's MediaSession bridge, and re-enabling it to get
 * nav would double-fire the media path. This monitor handles only the
 * navigation transactions and publishes no media state.
 *
 * Transaction codes come from the vendor decompile,
 * `apks/AndroidAutoAdapter_decompiled/.../sdk/aidl/LinkCallback$Stub.smali`.
 */
object AndroidAutoNavigationMonitor {
    private const val TAG = "AaNavMonitor"
    private const val ANDROID_AUTO_SERVICE_PACKAGE = "com.ts.androidauto.projectionservice"
    private const val ANDROID_AUTO_SERVICE_ACTION = "com.ts.androidauto.action.AndroidAutoService"
    private const val LINK_CALLBACK_DESCRIPTOR = "com.ts.androidauto.sdk.aidl.LinkCallback"

    private const val TRANSACTION_ADD_LINK_CALLBACK = 1
    private const val TRANSACTION_REMOVE_LINK_CALLBACK = 2
    private const val TRANSACTION_ON_NAVIGATION_FOCUS_CHANGED = 3
    private const val TRANSACTION_ON_NOTIFY_NAVIGATION_STATE = 7
    private const val TRANSACTION_ON_NOTIFY_NEXT_TURN = 8
    private const val TRANSACTION_ON_NOTIFY_NEXT_TURN_DISTANCE = 9

    // What this head unit actually sends. Codes 7/8/9 stay handled above
    // because other builds do use them, but on this car the route arrives as
    // state + position at ~1 Hz.
    private const val TRANSACTION_ON_NAVIGATION_STATE = 10
    private const val TRANSACTION_ON_NAVIGATION_CURRENT_POSITION = 11

    private const val BIND_RETRY_INTERVAL_MS = 5_000L

    private val accumulator = AndroidAutoNavigationTelemetry.Accumulator()
    private val callback = LinkCallbackBinder()
    private val lock = Any()

    private var appContext: Context? = null
    private var serviceBinder: IBinder? = null
    private var bound = false
    private var started = false
    private var retryScheduled = false

    private val retryHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val deathRecipient =
        IBinder.DeathRecipient {
            Log.w(TAG, "Projection service binder died; will rebind")
            synchronized(lock) {
                serviceBinder = null
                bound = false
            }
            clearNavigation("binder died")
            scheduleRetry()
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                synchronized(lock) {
                    serviceBinder = service
                    bound = true
                }
                runCatching { service?.linkToDeath(deathRecipient, 0) }
                registerCallback(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                synchronized(lock) {
                    serviceBinder = null
                    bound = false
                }
                clearNavigation("service disconnected")
                scheduleRetry()
            }
        }

    fun start(context: Context) {
        synchronized(lock) {
            if (started) return
            started = true
            appContext = context.applicationContext
        }
        Log.w(TAG, "Starting Android Auto navigation monitor")
        bindProjectionService()
    }

    private fun bindProjectionService() {
        val ctx = synchronized(lock) { appContext } ?: return
        if (synchronized(lock) { serviceBinder?.isBinderAlive == true }) return
        val intent = Intent(ANDROID_AUTO_SERVICE_ACTION).apply {
            setPackage(ANDROID_AUTO_SERVICE_PACKAGE)
        }
        val requested =
            runCatching { ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
                .getOrDefault(false)
        if (!requested) {
            Log.w(TAG, "bindService refused for navigation callback; retrying")
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        synchronized(lock) {
            if (retryScheduled) return
            retryScheduled = true
        }
        retryHandler.postDelayed({
            synchronized(lock) { retryScheduled = false }
            if (synchronized(lock) { serviceBinder?.isBinderAlive != true }) {
                bindProjectionService()
            }
        }, BIND_RETRY_INTERVAL_MS)
    }

    private fun registerCallback(binder: IBinder?) {
        if (binder == null) return
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("com.ts.androidauto.sdk.aidl.LinkCommand")
            data.writeStrongBinder(callback)
            val ok = binder.transact(TRANSACTION_ADD_LINK_CALLBACK, data, reply, 0)
            Log.w(TAG, "Registered navigation LinkCallback ok=$ok")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register navigation LinkCallback", t)
            scheduleRetry()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun clearNavigation(reason: String) {
        Log.w(TAG, "Clearing navigation: $reason")
        accumulator.reset()
        AndroidAutoClusterController.onNavigationUpdate(accumulator.current())
    }

    /**
     * Reads an `IfNavigationData` body in the field order of its `(Parcel)`
     * constructor. The image length is its own int and the byte array is only
     * on the wire when that length is positive — mis-reading that branch
     * desyncs the two ints after it.
     */
    private fun readNextTurn(parcel: Parcel) {
        val road = parcel.readString()
        val turnSide = parcel.readInt()
        val event = parcel.readInt()
        val imageLength = parcel.readInt()
        if (imageLength > 0) {
            parcel.readByteArray(ByteArray(imageLength))
        }
        val turnAngle = parcel.readInt()
        val turnNumber = parcel.readInt()
        Log.w(
            TAG,
            "onNotifyNextTurn road=$road event=$event side=$turnSide " +
                "angle=$turnAngle number=$turnNumber image=$imageLength"
        )
        publish(accumulator.onNextTurn(road, event, turnSide))
    }

    /**
     * `IfNavigationStateData`: a destination string list then a step list. Each
     * list is preceded by its own explicit count int, and the list itself then
     * re-reads its length — the same double-count shape the byte array uses.
     *
     * Only the first step is read, for its road and turn; parsing stops there
     * rather than walking the cue and lane lists, so this never depends on
     * `IfNavigationLaneData`'s layout.
     */
    private fun readNavigationState(parcel: Parcel) {
        val destCount = parcel.readInt()
        if (destCount > 0) {
            val n = parcel.readInt()
            for (i in 0 until maxOf(n, 0)) {
                parcel.readString()
            }
        }
        val stepCount = parcel.readInt()
        var road: String? = null
        var event = 0
        var turnAngle = 0
        var turnNumber = 0
        var hasRoute = false
        if (stepCount > 0) {
            val n = parcel.readInt()
            if (n > 0 && parcel.readInt() != 0) {
                road = parcel.readString()
                event = parcel.readInt()
                turnAngle = parcel.readInt()
                turnNumber = parcel.readInt()
                hasRoute = true
            }
        }
        Log.w(
            TAG,
            "onNavigationState steps=$stepCount road=$road event=$event " +
                "angle=$turnAngle number=$turnNumber"
        )
        publish(accumulator.onRouteStep(road, event, hasRoute))
    }

    /**
     * `IfNavigationPositionData`: display string, metres, units, time, current
     * road, then a list of `IfNavigationDestDistanceData`. The first entry of
     * that list is the trip total the AA screen shows as "4,8 km · 10:35".
     */
    private fun readNavigationPosition(parcel: Parcel) {
        val displayValue = parcel.readString()
        val meters = parcel.readInt()
        val displayUnits = parcel.readInt()
        val timeSeconds = parcel.readLong()
        val currentRoad = parcel.readString()

        var remainingMeters: Int? = null
        var remainingSeconds: Int? = null
        var estimatedTime: String? = null
        val destCount = parcel.readInt()
        if (destCount > 0) {
            val n = parcel.readInt()
            if (n > 0 && parcel.readInt() != 0) {
                // IfNavigationDestDistanceData: value, metres, units, eta, seconds.
                parcel.readString()
                remainingMeters = parcel.readInt()
                parcel.readInt()
                estimatedTime = parcel.readString()
                remainingSeconds = parcel.readLong().toInt()
            }
        }
        Log.w(
            TAG,
            "onNavigationCurrentPosition display=$displayValue m=$meters " +
                "units=$displayUnits s=$timeSeconds road=$currentRoad " +
                "remainingM=$remainingMeters remainingS=$remainingSeconds eta=$estimatedTime"
        )
        publish(
            accumulator.onPosition(
                meters,
                displayValue,
                displayUnits,
                remainingMeters,
                remainingSeconds,
                estimatedTime
            )
        )
    }

    private fun publish(update: AndroidAutoNavigationTelemetry.Directions) {
        AndroidAutoClusterController.onNavigationUpdate(update, SystemClock.elapsedRealtime())
    }

    private class LinkCallbackBinder : Binder() {
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
                TRANSACTION_ON_NOTIFY_NEXT_TURN -> {
                    data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                    if (data.readInt() != 0) {
                        readNextTurn(data)
                    } else {
                        Log.w(TAG, "onNotifyNextTurn with null navigation data")
                    }
                    true
                }
                TRANSACTION_ON_NOTIFY_NEXT_TURN_DISTANCE -> {
                    data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                    val distanceMeters = data.readInt()
                    val timeSeconds = data.readInt()
                    val displayDistanceE3 = data.readInt()
                    val displayDistanceUnit = data.readInt()
                    Log.w(
                        TAG,
                        "onNotifyNextTurnDistance m=$distanceMeters s=$timeSeconds " +
                            "e3=$displayDistanceE3 unit=$displayDistanceUnit"
                    )
                    publish(
                        accumulator.onNextTurnDistance(
                            distanceMeters,
                            displayDistanceE3,
                            displayDistanceUnit
                        )
                    )
                    true
                }
                TRANSACTION_ON_NOTIFY_NAVIGATION_STATE -> {
                    data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                    val state = data.readInt()
                    Log.w(TAG, "onNotifyNavigationState state=$state")
                    publish(accumulator.onNavigationState(state))
                    true
                }
                TRANSACTION_ON_NAVIGATION_STATE -> {
                    data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                    if (data.readInt() != 0) {
                        readNavigationState(data)
                    } else {
                        publish(accumulator.onRouteStep(null, 0, hasRoute = false))
                    }
                    true
                }
                TRANSACTION_ON_NAVIGATION_CURRENT_POSITION -> {
                    data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                    if (data.readInt() != 0) {
                        readNavigationPosition(data)
                    }
                    true
                }
                TRANSACTION_ON_NAVIGATION_FOCUS_CHANGED -> {
                    data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                    Log.w(TAG, "onNavigationFocusChanged focused=${data.readInt() != 0}")
                    true
                }
                else -> {
                    // Media codes 4/5/6 also land here; the AA App owns media, so
                    // they are noted and dropped rather than republished.
                    Log.w(TAG, "Nav monitor ignoring LinkCallback code=$code")
                    super.onTransact(code, data, reply, flags)
                }
            }
        }
    }
}
