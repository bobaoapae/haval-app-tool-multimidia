package br.com.redesurftank.havalshisuku.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

object AndroidAutoDcmRecovery {
    private const val TAG = "AndroidAutoDcmRecovery"
    private const val DCM_PACKAGE = "com.ts.car.dcm.dccontroller"
    private const val DCM_ACTION = "com.ts.car.dcm.dccontroller.connectionmanagerservice.CmService"
    private const val DCM_INTERFACE = "ts.car.dcm.common.aidl.IConnectionManagementService"

    private const val TRANSACTION_GET_DEVICES = 13
    private const val TRANSACTION_CONNECT_PROJECTION = 24
    private const val TRANSACTION_DISCONNECT_PROJECTION = 25
    private const val TRANSACTION_RESET_USB = 28
    private const val TRANSACTION_ENABLE_USB_AOA_FOR_ANDROID_AUTO = 30

    private const val CAP_ANDROID_AUTO_USB = 128
    private const val CAP_ANDROID_AUTO_WIRELESS = 1024
    private const val CAPABILITY_STATE_ACTIVATED = 4
    private const val DISCONNECT_REASON_SWITCH_PROJECTION = 2
    private const val ACTIVATE_SUCCESS = 0
    private const val LOG_SNAPSHOT_CACHE_MS = 3_000L

    @Volatile private var cachedLogSnapshots: List<DcmDeviceSnapshot> = emptyList()
    @Volatile private var cachedLogSnapshotsAtMs: Long = 0L

    suspend fun readDeviceSnapshots(context: Context): List<DcmDeviceSnapshot> {
        val viaBinder = withDcmBinder(context.applicationContext) { binder ->
            readDevices(binder)
        } ?: emptyList()
        if (viaBinder.isNotEmpty()) return viaBinder
        // The OEM ConnDevice Parcelable isn't on our classpath, so the binder read always
        // unmarshals to empty (ClassNotFound at Bundle.keySet). Fall back to parsing the
        // OEM's own DcController "mergeDevices" log lines, which expose every field we need.
        // Cached briefly so the (non-hot) callers don't re-scrape logcat and add Shizuku load.
        return readDevicesFromLogs()
    }

    suspend fun recoverUsbProjection(context: Context, requestedDevId: String?): RecoveryResult {
        val start = SystemClock.elapsedRealtime()
        return withDcmBinder(context.applicationContext) { binder ->
            val explicitDevId = requestedDevId?.trim()?.takeIf { it.isNotEmpty() }
            val devices = if (explicitDevId == null) readDevices(binder) else emptyList()
            val devId =
                    explicitDevId
                            ?: selectUsbAndroidAutoDevice(devices)
                            ?: findRecentUsbAndroidAutoDeviceFromLogs()

            if (devId == null) {
                Log.w(TAG, "aa_usb_recover no USB Android Auto device found devices=$devices")
                return@withDcmBinder RecoveryResult(
                        devId = null,
                        devices = devices,
                            disconnectUsb = null,
                            resetUsb = null,
                            directEnableAoa = null,
                            connectUsbResult = null,
                            elapsedMs = SystemClock.elapsedRealtime() - start
                )
            }

            Log.w(TAG, "aa_usb_recover start devId=$devId devices=$devices")
            val disconnectUsb =
                    transactDisconnectProjection(
                            binder = binder,
                            devId = devId,
                            capability = CAP_ANDROID_AUTO_USB,
                            reason = DISCONNECT_REASON_SWITCH_PROJECTION
                    )
            delay(350L)
            val resetUsb = transactResetUsb(binder, devId)
            delay(4_000L)
            val connectUsbResult =
                    transactConnectProjection(
                            binder = binder,
                            devId = devId,
                            capability = CAP_ANDROID_AUTO_USB,
                            fromUser = true,
                            autoConnectDevice = false,
                            always = false,
                            first = false,
                            capabilityAlreadyChecked = false
                    )
            val result =
                    RecoveryResult(
                            devId = devId,
                            devices = devices,
                            disconnectUsb = disconnectUsb,
                            resetUsb = resetUsb,
                            directEnableAoa = null,
                            connectUsbResult = connectUsbResult,
                            elapsedMs = SystemClock.elapsedRealtime() - start
                    )
            Log.w(TAG, "aa_usb_recover result=$result")
            result
        }
                ?: RecoveryResult(
                        devId = requestedDevId?.trim()?.takeIf { it.isNotEmpty() },
                        devices = emptyList(),
                        disconnectUsb = null,
                        resetUsb = null,
                        directEnableAoa = null,
                        connectUsbResult = null,
                        elapsedMs = SystemClock.elapsedRealtime() - start,
                        bindFailed = true
                )
    }

    data class RecoveryResult(
            val devId: String?,
            val devices: List<DcmDeviceSnapshot>,
            val disconnectUsb: Boolean?,
            val resetUsb: Boolean?,
            val directEnableAoa: Boolean?,
            val connectUsbResult: Int?,
            val elapsedMs: Long,
            val bindFailed: Boolean = false
    ) {
        val success: Boolean
            get() = connectUsbResult == ACTIVATE_SUCCESS
    }

    data class DcmDeviceSnapshot(
            val key: String,
            val uuid: String?,
            val friendlyName: String?,
            val usbSerial: String?,
            val btAddr: String?,
            val availableCapability: Int?,
            val activeCapability: Int?,
            val deviceConnectedState: Int?,
            val connectedTypeState: String?,
            val activeUsbAndroidAutoState: Int?,
            val activeWirelessAndroidAutoState: Int?
    ) {
        fun hasUsbAndroidAutoCapability(): Boolean {
            return availableCapability?.let { (it and CAP_ANDROID_AUTO_USB) != 0 } == true
        }

        fun hasActiveAndroidAutoProjection(): Boolean {
            return activeUsbAndroidAutoState == CAPABILITY_STATE_ACTIVATED ||
                    activeWirelessAndroidAutoState == CAPABILITY_STATE_ACTIVATED
        }

        override fun toString(): String {
            return "DcmDevice(key=$key,uuid=$uuid,name=$friendlyName,usb=$usbSerial,bt=$btAddr," +
                    "available=$availableCapability,active=$activeCapability,state=$deviceConnectedState," +
                    "connected=$connectedTypeState,aaUsbState=$activeUsbAndroidAutoState," +
                    "aaWirelessState=$activeWirelessAndroidAutoState)"
        }
    }

    private suspend fun <T> withDcmBinder(context: Context, block: suspend (IBinder) -> T): T? {
        val deferred = CompletableDeferred<IBinder?>()
        val bound = AtomicBoolean(false)
        val connection =
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        Log.w(TAG, "DCM service connected name=$name binder=${service != null}")
                        deferred.complete(service)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.w(TAG, "DCM service disconnected name=$name")
                        if (!deferred.isCompleted) deferred.complete(null)
                    }
                }
        val intent = Intent(DCM_ACTION).setPackage(DCM_PACKAGE)
        return try {
            val didBind = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            bound.set(didBind)
            if (!didBind) {
                Log.w(TAG, "DCM bindService returned false")
                null
            } else {
                val binder = withTimeoutOrNull(2_500L) { deferred.await() }
                if (binder == null) {
                    Log.w(TAG, "DCM bind timed out")
                    null
                } else {
                    block(binder)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "DCM binder operation failed", t)
            null
        } finally {
            if (bound.get()) {
                runCatching { context.unbindService(connection) }
            }
        }
    }

    private fun readDevices(binder: IBinder): List<DcmDeviceSnapshot> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DCM_INTERFACE)
            if (!binder.transact(TRANSACTION_GET_DEVICES, data, reply, 0)) {
                Log.w(TAG, "getDevices transact returned false")
                return emptyList()
            }
            reply.readException()
            if (reply.readInt() == 0) return emptyList()
            val bundle = Bundle.CREATOR.createFromParcel(reply)
            val connDeviceClass = runCatching {
                Class.forName("ts.car.dcm.common.data.ConnDevice")
            }.getOrNull()
            if (connDeviceClass != null) {
                bundle.classLoader = connDeviceClass.classLoader
            }
            bundle.keySet().mapNotNull { key ->
                val value = runCatching { bundle.get(key) }.getOrNull() ?: return@mapNotNull null
                DcmDeviceSnapshot(
                        key = key,
                        uuid = invokeString(value, "getDeviceUuid"),
                        friendlyName = invokeString(value, "getFriendlyName"),
                        usbSerial = invokeString(value, "getUsbSerialNumber"),
                        btAddr = invokeString(value, "getBtAddr"),
                        availableCapability = invokeInt(value, "getAvailableCapabilitys"),
                        activeCapability = invokeInt(value, "getActiveCapability"),
                        deviceConnectedState = invokeInt(value, "getDeviceConnectedState"),
                        connectedTypeState =
                                runCatching { value.javaClass.getMethod("getConnectedTypeState").invoke(value) }
                                        .getOrNull()
                                        ?.toString(),
                        activeUsbAndroidAutoState =
                                invokeInt(value, "getActiveCapabilityState", CAP_ANDROID_AUTO_USB),
                        activeWirelessAndroidAutoState =
                                invokeInt(value, "getActiveCapabilityState", CAP_ANDROID_AUTO_WIRELESS)
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getDevices failed", t)
            emptyList()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun selectUsbAndroidAutoDevice(devices: List<DcmDeviceSnapshot>): String? {
        return devices.firstOrNull { it.hasUsbAndroidAutoCapability() && it.usbSerial != null }?.uuid
                ?: devices.firstOrNull { it.hasUsbAndroidAutoCapability() }?.uuid
                ?: devices.firstOrNull { it.usbSerial != null }?.uuid
    }

    private fun findRecentUsbAndroidAutoDeviceFromLogs(): String? {
        val logs =
                ShizukuUtils.runCommandAndGetOutput(
                        arrayOf(
                                "logcat",
                                "-d",
                                "-v",
                                "time",
                                "-t",
                                "1200"
                        )
                )
        val patterns =
                listOf(
                        Regex("onProjectionConnectionChanged, devId:([A-Za-z0-9_-]+), capa:128"),
                        Regex("ProjectionLinkParamter \\{mDevId='([^']+)', mCapability='128'"),
                        Regex("ActiveState Projection Service start timeout\\. dev:([A-Za-z0-9_-]+)")
                )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.findAll(logs).lastOrNull()?.groupValues?.getOrNull(1)
        }
    }

    /**
     * Reconstructs device snapshots from the OEM's own `DcController: mergeDevices ... Device{...}`
     * log lines. Used when the binder read cannot unmarshal (ConnDevice class missing). The scrape
     * is filtered to a single tag (few lines) and cached briefly, so it stays cheap.
     */
    private fun readDevicesFromLogs(): List<DcmDeviceSnapshot> {
        val now = SystemClock.elapsedRealtime()
        val cachedAt = cachedLogSnapshotsAtMs
        if (cachedAt != 0L &&
                (now - cachedAt) in 0..LOG_SNAPSHOT_CACHE_MS &&
                cachedLogSnapshots.isNotEmpty()) {
            return cachedLogSnapshots
        }
        val logs = runCatching {
            ShizukuUtils.runCommandAndGetOutput(
                arrayOf("logcat", "-d", "DcController:E", "*:S")
            )
        }.getOrNull().orEmpty()
        if (logs.isBlank()) return cachedLogSnapshots
        val byKey = LinkedHashMap<String, DcmDeviceSnapshot>()
        logs.lineSequence().forEach { line ->
            if (!line.contains("mergeDevices") || !line.contains("Device{")) return@forEach
            val snap = parseDeviceLogLine(line) ?: return@forEach
            byKey[snap.key] = snap
        }
        val result = byKey.values.toList()
        if (result.isNotEmpty()) {
            cachedLogSnapshots = result
            cachedLogSnapshotsAtMs = now
        }
        return result
    }

    private fun parseDeviceLogLine(line: String): DcmDeviceSnapshot? {
        fun str(name: String): String? =
            Regex("$name='([^']*)'").find(line)?.groupValues?.getOrNull(1)?.takeIf {
                it.isNotBlank() && it != "null"
            }

        fun int(name: String): Int? =
            Regex("$name=(-?\\d+)").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val id = str("mId")
        val bt = str("mBtAddr")
        val key = bt ?: id ?: return null
        val capa = parseCapaState(
            Regex("mActiveCapaState=\\{([^}]*)\\}").find(line)?.groupValues?.getOrNull(1).orEmpty()
        )
        return DcmDeviceSnapshot(
            key = key,
            uuid = id,
            friendlyName = str("mFriendlyName"),
            usbSerial = str("mUsbSerialNumber"),
            btAddr = bt,
            availableCapability = int("mAvailableCapability"),
            activeCapability = int("mActiveCapability"),
            deviceConnectedState = int("mDeviceConnectedState"),
            connectedTypeState =
                Regex("mConnectedTypeState=\\{([^}]*)\\}").find(line)?.groupValues?.getOrNull(1),
            activeUsbAndroidAutoState = capa[CAP_ANDROID_AUTO_USB],
            activeWirelessAndroidAutoState = capa[CAP_ANDROID_AUTO_WIRELESS]
        )
    }

    private fun parseCapaState(raw: String): Map<Int, Int> {
        if (raw.isBlank()) return emptyMap()
        val map = HashMap<Int, Int>()
        raw.split(",").forEach { part ->
            val kv = part.trim().split("=")
            if (kv.size == 2) {
                val k = kv[0].trim().toIntOrNull()
                val v = kv[1].trim().toIntOrNull()
                if (k != null && v != null) map[k] = v
            }
        }
        return map
    }

    private fun transactDisconnectProjection(
            binder: IBinder,
            devId: String,
            capability: Int,
            reason: Int
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DCM_INTERFACE)
            data.writeString(devId)
            data.writeInt(capability)
            data.writeInt(reason)
            val sent = binder.transact(TRANSACTION_DISCONNECT_PROJECTION, data, reply, 0)
            if (!sent) return false
            reply.readException()
            reply.readInt() != 0
        } catch (t: Throwable) {
            Log.w(TAG, "disconnectProjection failed devId=$devId capability=$capability", t)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactResetUsb(binder: IBinder, devId: String): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DCM_INTERFACE)
            data.writeString(devId)
            val sent = binder.transact(TRANSACTION_RESET_USB, data, reply, 0)
            if (!sent) return false
            reply.readException()
            reply.readInt() != 0
        } catch (t: Throwable) {
            Log.w(TAG, "resetUsb failed devId=$devId", t)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactConnectProjection(
            binder: IBinder,
            devId: String,
            capability: Int,
            fromUser: Boolean,
            autoConnectDevice: Boolean,
            always: Boolean,
            first: Boolean,
            capabilityAlreadyChecked: Boolean
    ): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DCM_INTERFACE)
            data.writeInt(1)
            data.writeString(devId)
            data.writeInt(capability)
            data.writeBooleanArray(
                    booleanArrayOf(
                            fromUser,
                            autoConnectDevice,
                            always,
                            first,
                            capabilityAlreadyChecked
                    )
            )
            val sent = binder.transact(TRANSACTION_CONNECT_PROJECTION, data, reply, 0)
            if (!sent) return 4
            reply.readException()
            reply.readInt()
        } catch (t: Throwable) {
            Log.w(TAG, "connectProjection failed devId=$devId capability=$capability", t)
            4
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactEnableUsbAoaForAndroidAuto(
            binder: IBinder,
            devId: String,
            serialNumber: String,
            mode: Int,
            enable: Boolean
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DCM_INTERFACE)
            data.writeString(devId)
            data.writeString(serialNumber)
            data.writeInt(mode)
            data.writeInt(if (enable) 1 else 0)
            val sent = binder.transact(TRANSACTION_ENABLE_USB_AOA_FOR_ANDROID_AUTO, data, reply, 0)
            if (!sent) return false
            reply.readException()
            reply.readInt() != 0
        } catch (t: Throwable) {
            Log.w(
                    TAG,
                    "enableUsbAoaForAndroidAuto failed devId=$devId serialNumber=$serialNumber",
                    t
            )
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun invokeString(target: Any, methodName: String): String? {
        return runCatching { target.javaClass.getMethod(methodName).invoke(target) as? String }.getOrNull()
    }

    private fun invokeInt(target: Any, methodName: String): Int? {
        return runCatching { target.javaClass.getMethod(methodName).invoke(target) as? Int }.getOrNull()
    }

    private fun invokeInt(target: Any, methodName: String, intArg: Int): Int? {
        return runCatching {
            target.javaClass.getMethod(methodName, Integer.TYPE).invoke(target, intArg) as? Int
        }.getOrNull()
    }
}
