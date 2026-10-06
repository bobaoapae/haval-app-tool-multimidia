package br.com.redesurftank.havalshisuku.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import br.com.redesurftank.App
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Whether the Haval H6 3D viewer (`com.havalh6.viewer`) is on this car, and what it can do.
 *
 * Impulse ships before the viewer does, and several Impulse features exist only to serve it — the
 * boot autostart, and the climate hand-off that is being built on top of this. Showing those
 * options on a car with no viewer is how a settings screen fills up with switches that do nothing.
 *
 * Three things are read, all from the PackageManager, none of which launches or binds the viewer:
 *
 *  * **installed** — `getPackageInfo`. Impulse targets SDK 28, so the Android 11 package-visibility
 *    rules do not apply and no `<queries>` entry is needed.
 *  * **enabled** — a disabled app is still installed. `MATCH_DISABLED_COMPONENTS` is what makes the
 *    difference visible; without it a `pm disable-user`d viewer reads as absent, which is a
 *    different thing and deserves different wording on screen.
 *  * **declared API level** — the `impulse.api` meta-data (see [ViewerPresencePolicy]). Presence
 *    alone would light up options that an older viewer cannot answer.
 *
 * The result is cached and refreshed from package broadcasts, so installing the viewer lights the
 * options up without restarting Impulse. [start] must be called once; [ForegroundService] does it.
 */
object ViewerPresence {

    private const val TAG = "VIEWER_PRESENCE"

    /** The one place this package name lives. */
    const val VIEWER_PACKAGE = "com.havalh6.viewer"

    /**
     * Accepted signing certificates (SHA-256, hex, lower case). EMPTY ships as "accept any signer":
     * the viewer's release key is not settled, and a wrong pin here would hide every viewer option
     * on a car that has the app, which is worse than the risk it guards against. The observed
     * digest is logged on every refresh — fill this in from a real install to turn the check on.
     */
    private val PINNED_SIGNATURES = emptySet<String>()

    /** What we know about the viewer right now. */
    data class Status(
        val installed: Boolean = false,
        val enabled: Boolean = false,
        val versionName: String? = null,
        val versionCode: Long = 0L,
        val apiLevel: Int = ViewerPresencePolicy.API_NONE,
        val signatureSha256: String? = null,
        val trusted: Boolean = false
    ) {
        /** @see ViewerPresencePolicy.supports */
        fun supports(minApiLevel: Int): Boolean =
            ViewerPresencePolicy.supports(installed, enabled, trusted, apiLevel, minApiLevel)

        /** Installed but switched off — worth saying out loud, it is not the same as missing. */
        val installedButDisabled: Boolean
            get() = installed && !enabled
    }

    private val listeners = CopyOnWriteArrayList<(Status) -> Unit>()

    @Volatile
    private var cached: Status = Status()

    @Volatile
    private var started = false

    /** Whether [cached] has ever been filled from the PackageManager. See [status]. */
    @Volatile
    private var primed = false

    /**
     * Last known state. Cheap — reads a cached snapshot, never the PackageManager.
     *
     * Except on the FIRST call before [start] has run, where it reads once rather than reporting
     * "not installed". Measured on the car 2026-09-23: after Impulse restarted, the viewer rebound
     * and asked for the climate lease ~1.5 s BEFORE the watcher started, was refused as though the
     * viewer were absent, and nothing retried — the car kept the OEM A/C app with the hand-off on.
     */
    @Synchronized
    fun status(): Status {
        if (!primed) {
            primed = true
            cached = read()
        }
        return cached
    }

    /** Shorthand for the common case. @see ViewerPresencePolicy for the levels. */
    fun supports(minApiLevel: Int): Boolean = cached.supports(minApiLevel)

    /** True when the viewer is installed at all, disabled or not. */
    fun isInstalled(): Boolean = cached.installed

    /**
     * Starts watching. Safe to call more than once.
     *
     * The receiver is registered at RUNTIME rather than in the manifest on purpose: manifest
     * receivers for implicit broadcasts are restricted from Android 8 on, and Impulse already has a
     * long-lived foreground service to hang this off.
     */
    fun start() {
        if (started) return
        started = true

        refresh("start")

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            // Covers pm enable / pm disable-user, which change nothing else about the package.
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }

        ContextCompat.registerReceiver(
            App.getContext(),
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val changed = intent?.data?.schemeSpecificPart
                    if (changed != null && changed != VIEWER_PACKAGE) return
                    refresh(intent?.action ?: "package_event")
                }
            },
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * Re-reads the PackageManager and notifies listeners if anything moved. Called from the package
     * receiver; also useful straight after an install Impulse performed itself.
     */
    fun refresh(reason: String) {
        val next = read()
        val previous = cached
        cached = next
        primed = true
        if (next == previous) return

        Log.w(TAG, "[$reason] $previous -> $next")
        listeners.forEach { listener ->
            runCatching { listener(next) }
                .onFailure { Log.e(TAG, "Listener failed", it) }
        }
    }

    /**
     * Registers [listener] and immediately hands it the current state, so a caller never has to
     * read once and subscribe separately. Returns a function that unregisters it.
     */
    fun addListener(listener: (Status) -> Unit): () -> Unit {
        listeners.add(listener)
        runCatching { listener(cached) }
            .onFailure { Log.e(TAG, "Listener failed on attach", it) }
        return { listeners.remove(listener) }
    }

    private fun read(): Status {
        val pm = App.getContext().packageManager
        val info: PackageInfo = try {
            pm.getPackageInfo(
                VIEWER_PACKAGE,
                PackageManager.GET_META_DATA or
                        PackageManager.GET_SIGNING_CERTIFICATES or
                        PackageManager.MATCH_DISABLED_COMPONENTS
            )
        } catch (e: PackageManager.NameNotFoundException) {
            return Status()
        } catch (t: Throwable) {
            // A PackageManager that throws anything else is not evidence the viewer is absent, but
            // there is nothing better to report, and every caller treats this as "do not offer".
            Log.e(TAG, "Could not read $VIEWER_PACKAGE", t)
            return Status()
        }

        val signature = firstSignatureSha256(info)
        val trusted = ViewerPresencePolicy.isTrusted(signature, PINNED_SIGNATURES)
        if (PINNED_SIGNATURES.isEmpty() && signature != null) {
            Log.w(TAG, "$VIEWER_PACKAGE signer sha256=$signature (not pinned; accepting any)")
        } else if (!trusted) {
            Log.e(TAG, "$VIEWER_PACKAGE is signed by an unknown key ($signature) — features hidden")
        }

        return Status(
            installed = true,
            // applicationInfo is nullable in practice on odd PackageManager states.
            enabled = info.applicationInfo?.enabled ?: false,
            versionName = info.versionName,
            versionCode = info.longVersionCode,
            apiLevel = ViewerPresencePolicy.parseApiLevel(
                info.applicationInfo?.metaData?.get(ViewerPresencePolicy.META_API_LEVEL)
            ),
            signatureSha256 = signature,
            trusted = trusted
        )
    }

    private fun firstSignatureSha256(info: PackageInfo): String? = try {
        val signing = info.signingInfo
        val certificates = when {
            signing == null -> null
            signing.hasMultipleSigners() -> signing.apkContentsSigners
            else -> signing.signingCertificateHistory
        }
        certificates?.firstOrNull()?.toByteArray()?.let { bytes ->
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Could not read the signer of $VIEWER_PACKAGE", t)
        null
    }
}
