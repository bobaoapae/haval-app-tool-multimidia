package br.com.redesurftank.havalshisuku.api

import android.app.PendingIntent
import android.content.Intent
import android.util.Log

/**
 * Caller allowlist for Impulse's outside-facing command API.
 *
 * The receivers are exported and unprotected, so anything installed on the head
 * unit can broadcast to them — and these commands run through Shizuku, which is
 * root here. Callers therefore have to prove who they are.
 *
 * `BroadcastReceiver.getSentFromPackage()` is API 34; this unit is Android 9. A
 * PendingIntent works instead: only the system fills in its creator, so
 * [PendingIntent.getCreatorPackage] cannot be forged by the sender. The caller
 * puts a throwaway PendingIntent in [EXTRA_CALLER] and we read the identity off
 * it.
 *
 * This proves *which package* sent the command, not that the package is genuine —
 * a same-named app signed by another key would pass. That is acceptable for the
 * operations exposed here (moving and resizing windows). Do not widen this API to
 * anything destructive without also pinning the signature.
 */
object ImpulseApiCallers {
    private const val TAG = "ImpulseApi"

    const val EXTRA_CALLER = "caller"

    /** Registered clients. Only the 3D viewer for now. */
    private val ALLOWED = setOf(
        "com.havalh6.viewer",
        "br.com.redesurftank.havalshisuku",
    )

    /** @return the verified caller package, or null if it may not use the API. */
    fun verify(intent: Intent?, action: String): String? {
        val token = intent?.getParcelableExtra<PendingIntent>(EXTRA_CALLER)
        if (token == null) {
            Log.w(TAG, "$action rejected: no caller identity")
            return null
        }
        val pkg = token.creatorPackage
        if (pkg == null || !ALLOWED.contains(pkg)) {
            Log.w(TAG, "$action rejected: $pkg is not a registered client")
            return null
        }
        return pkg
    }
}
