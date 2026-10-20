package br.com.redesurftank.havalshisuku.managers

/**
 * Maps Autolink GET_LINK_STATUS to [AndroidAutoTelemetryKeys.SESSION].
 * Status 3/7/8 are linked ([ACTIVATED] / [SHOW_VIDEO] / [AAP_FRX]).
 * 7↔8 flaps do not change the published value. A brief drop to stopped is
 * debounced so a handshake blip does not tear the cluster map down.
 */
object AndroidAutoSessionTelemetry {
    const val DEBOUNCE_STOPPED_MS = 400L
    const val NAVIGATION_EVIDENCE_MAX_AGE_MS = 5_000L

    /** GET_LINK_STATUS value for ACTIVATED, the plain "a phone is linked" state. */
    const val LINK_STATUS_ACTIVATED = 3

    fun isLinkActive(status: Int?): Boolean {
        return status == 3 || status == 7 || status == 8
    }

    fun sessionValue(status: Int?): String {
        return if (isLinkActive(status)) {
            AndroidAutoTelemetryKeys.SESSION_ACTIVE
        } else {
            AndroidAutoTelemetryKeys.SESSION_STOPPED
        }
    }

    /**
     * Autolink can briefly report stopped while AA guidance is still arriving. Fresh guidance
     * keeps the independent CLUSTER output alive; a disconnect wins once guidance becomes stale.
     */
    fun statusWithNavigationEvidence(
        linkStatus: Int?,
        navigationEvidenceRecent: Boolean
    ): Int? {
        return if (!isLinkActive(linkStatus) && navigationEvidenceRecent) {
            LINK_STATUS_ACTIVATED
        } else {
            linkStatus
        }
    }

    fun hasRecentNavigationEvidence(lastActiveAtMs: Long, nowMs: Long): Boolean {
        val ageMs = nowMs - lastActiveAtMs
        return lastActiveAtMs > 0L && ageMs >= 0L && ageMs <= NAVIGATION_EVIDENCE_MAX_AGE_MS
    }

    /**
     * What the existing session loop should hand to [Debouncer].
     *
     * A real link status wins. Wireless Android Auto never binds that service, so a
     * recent DCM "projection active" note keeps the session up. Once that note is
     * gone and the session is still active, feed a non-linked status so the
     * debouncer can publish stopped. Null means "leave the published value alone"
     * (link unreadable, and we were not in a session).
     */
    fun statusForPoll(linkStatus: Int?, dcmEvidenceRecent: Boolean, sessionActive: Boolean): Int? {
        if (linkStatus != null) return linkStatus
        if (dcmEvidenceRecent) return LINK_STATUS_ACTIVATED
        if (sessionActive) return 0
        return null
    }

    class Debouncer {
        private var lastPublished: String? = null
        private var pendingStoppedSinceMs: Long = -1L

        fun onStatus(status: Int?, nowMs: Long): String? {
            val next = sessionValue(status)
            if (next == lastPublished) {
                pendingStoppedSinceMs = -1L
                return null
            }
            if (next == AndroidAutoTelemetryKeys.SESSION_ACTIVE) {
                lastPublished = next
                pendingStoppedSinceMs = -1L
                return next
            }
            if (pendingStoppedSinceMs < 0L) {
                pendingStoppedSinceMs = nowMs
                return null
            }
            if (nowMs - pendingStoppedSinceMs < DEBOUNCE_STOPPED_MS) {
                return null
            }
            lastPublished = next
            pendingStoppedSinceMs = -1L
            return next
        }

        fun publishedValue(): String {
            return lastPublished ?: AndroidAutoTelemetryKeys.SESSION_STOPPED
        }

        fun reset() {
            lastPublished = null
            pendingStoppedSinceMs = -1L
        }
    }
}
