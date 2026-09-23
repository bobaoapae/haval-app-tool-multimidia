package br.com.redesurftank.havalshisuku.services

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import br.com.redesurftank.havalshisuku.api.ImpulseApiCallers
import br.com.redesurftank.havalshisuku.managers.HvacPanelSuppressor
import br.com.redesurftank.havalshisuku.managers.ViewerPresence
import br.com.redesurftank.havalshisuku.managers.ViewerPresencePolicy

/**
 * The seam the H6 3D viewer binds to in order to own the A/C popup.
 *
 * While the viewer holds the lease, [HvacPanelSuppressor] keeps the OEM climate app disabled, so
 * the car's own popup never appears; the viewer shows its own on
 * `car.hvac.panel_display_notify`, which still fires (measured on the car 2026-09-22).
 *
 * The one thing this service exists to get right is **giving the OEM popup back**. `onUnbind` is
 * not enough on its own — a killed viewer never unbinds cleanly — so the client hands over a token
 * and we link to its death. That is an OS callback, not a poll: nothing here runs on a timer.
 *
 * Identity is checked the way the rest of the Impulse API checks it ([ImpulseApiCallers]): the
 * client sends a PendingIntent it created, and `creatorPackage` cannot be forged. `onBind` itself
 * cannot do this — it is called by the system, so the calling uid there is the system's, not the
 * client's — which is why the lease is taken by a MESSAGE and not by binding.
 */
class ViewerClimateBridgeService : Service() {

    companion object {
        private const val TAG = "VIEWER_CLIMATE_BRIDGE"

        /** Lease tag used with [HvacPanelSuppressor]. */
        private const val HOLDER = "viewer"

        /** Client -> Impulse: take the lease. `EXTRA_CALLER` + [KEY_TOKEN] required. */
        const val MSG_TAKE_CLIMATE_CONTROL = 1

        /** Client -> Impulse: drop it. Normal shutdown; death of [KEY_TOKEN] does the same. */
        const val MSG_RELEASE_CLIMATE_CONTROL = 2

        /** Binder the client owns. Impulse links to its death and releases the lease when it dies. */
        const val KEY_TOKEN = "token"

        /**
         * Impulse -> client: whether the OEM A/C app is actually being held disabled right now
         * (`arg1` 1/0). Sent in reply to a request and whenever the state changes afterwards — the
         * user can switch the feature off while the viewer is bound. A viewer that assumed its
         * request was granted would show its popup on top of the car's own.
         */
        const val MSG_CLIMATE_CONTROL_STATE = 3
    }

    private var clientToken: IBinder? = null
    private var client: Messenger? = null
    private var unsubscribe: (() -> Unit)? = null

    /**
     * A client whose request was verified but refused because the user had not enabled the
     * hand-off. A viewer asks once, when it binds, so without this the setting could be switched on
     * and nothing would happen until the viewer was restarted — which is exactly what was measured
     * on the car 2026-09-23.
     */
    private var pendingToken: IBinder? = null
    private var unsubscribeFeature: (() -> Unit)? = null

    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "Viewer died while holding the climate lease; restoring the OEM A/C app")
        releaseLease("client_death")
    }

    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { message -> handle(message) }
    )

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        // Ordinary teardown. A crash does not come through here, hence the death recipient.
        releaseLease("unbind")
        return false
    }

    override fun onDestroy() {
        releaseLease("service_destroyed")
        unsubscribeFeature?.invoke()
        unsubscribeFeature = null
        pendingToken = null
        super.onDestroy()
    }

    private fun handle(message: Message): Boolean {
        when (message.what) {
            MSG_TAKE_CLIMATE_CONTROL -> takeLease(message)
            MSG_RELEASE_CLIMATE_CONTROL -> releaseLease("client_request")
            else -> return false
        }
        return true
    }

    /**
     * Grants a lease that was only refused because the feature was off, the moment the user turns
     * it on. The client is still bound and still alive — it asked once and is waiting.
     */
    private fun watchFeatureToggle() {
        if (unsubscribeFeature != null) return
        unsubscribeFeature = HvacPanelSuppressor.addFeatureListener { enabled ->
            val token = pendingToken
            if (!enabled || token == null) return@addFeatureListener
            if (!ViewerPresence.supports(ViewerPresencePolicy.API_CLIMATE_HANDOFF)) {
                return@addFeatureListener
            }
            pendingToken = null
            clientToken = token
            runCatching { token.linkToDeath(deathRecipient, 0) }
                .onFailure {
                    Log.w(TAG, "Waiting viewer had already died; not taking the lease", it)
                    clientToken = null
                    return@addFeatureListener
                }
            Log.w(TAG, "Climate lease granted after the user enabled the hand-off")
            unsubscribe?.invoke()
            unsubscribe = HvacPanelSuppressor.addListener { active -> reportState(active) }
            HvacPanelSuppressor.acquire(HOLDER)
        }
    }

    private fun reportState(active: Boolean) {
        val target = client ?: return
        runCatching {
            target.send(Message.obtain(null, MSG_CLIMATE_CONTROL_STATE, if (active) 1 else 0, 0))
        }.onFailure {
            // The viewer went away without unbinding; the death recipient handles the lease.
            Log.w(TAG, "Could not report climate state to the viewer", it)
        }
    }

    private fun takeLease(message: Message) {
        val data: Bundle? = message.data
        client = message.replyTo
        val caller = data?.getParcelable<PendingIntent>(ImpulseApiCallers.EXTRA_CALLER)
        val pkg = caller?.creatorPackage
        if (pkg == null || pkg != ViewerPresence.VIEWER_PACKAGE) {
            Log.w(TAG, "Climate lease refused: caller is '$pkg'")
            // Every refusal is answered, so the viewer knows the OEM popup is still the car's job.
            reportState(false)
            return
        }
        // Same gate the settings screen uses, so a viewer that predates the hand-off - or one
        // signed by a key we do not accept - cannot take the OEM A/C app away.
        if (!ViewerPresence.supports(
                br.com.redesurftank.havalshisuku.managers.ViewerPresencePolicy.API_CLIMATE_HANDOFF
            )
        ) {
            Log.w(TAG, "Climate lease refused: ${ViewerPresence.status()}")
            reportState(false)
            return
        }
        if (!HvacPanelSuppressor.isFeatureEnabled()) {
            Log.w(TAG, "Climate lease refused for now: the user has not enabled the hand-off")
            // Remember the request so switching the setting on takes effect immediately.
            pendingToken = data.getBinder(KEY_TOKEN)
            watchFeatureToggle()
            reportState(false)
            return
        }

        val token = data.getBinder(KEY_TOKEN)
        if (token == null) {
            // Without a token a dead viewer would leave the OEM app disabled until the next
            // reconcile. Refusing is better than taking a lease we cannot reliably drop.
            Log.w(TAG, "Climate lease refused: no death token supplied")
            reportState(false)
            return
        }

        releaseLease("release_before_retake")
        clientToken = token
        runCatching { token.linkToDeath(deathRecipient, 0) }
            .onFailure {
                // The client died between sending and here.
                Log.w(TAG, "Viewer token was already dead; not taking the lease", it)
                clientToken = null
                return
            }

        pendingToken = null
        Log.w(TAG, "Climate lease granted to $pkg")
        // Follow the real suppression state from here on: the user can switch the feature off
        // while the viewer is bound, and the viewer has to stop showing its own popup then.
        unsubscribe?.invoke()
        unsubscribe = HvacPanelSuppressor.addListener { active -> reportState(active) }
        HvacPanelSuppressor.acquire(HOLDER)
    }

    private fun releaseLease(reason: String) {
        val token = clientToken
        if (token != null) {
            runCatching { token.unlinkToDeath(deathRecipient, 0) }
            clientToken = null
        }
        unsubscribe?.invoke()
        unsubscribe = null
        HvacPanelSuppressor.release(HOLDER)
        Log.w(TAG, "Climate lease released ($reason)")
        // A viewer that asked to release is still alive and wants to know it may stop assuming it
        // owns the popup; one that died cannot be told, which costs nothing.
        reportState(false)
        if (reason != "release_before_retake") client = null
    }
}
