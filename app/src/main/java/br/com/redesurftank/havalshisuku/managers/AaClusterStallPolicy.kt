package br.com.redesurftank.havalshisuku.managers

/**
 * Decides when a LIVE AA cluster map has frozen. The Service only times out the FIRST frame of
 * a stream; after that, if the phone simply stops sending CLUSTER frames the map stays LIVE on
 * its last frame forever (reported mid-drive with Waze; stopping and restarting navigation,
 * which re-requests the stream, brings it back). A parked car legitimately produces no new
 * frames, so a restart needs the car to be moving.
 */
object AaClusterStallPolicy {
    /** No new frame for this long counts as a stall. */
    const val STALL_MS = 5_000L
    /** Below this the map may simply not change; don't restart for it. */
    const val MIN_SPEED_KMH = 10f
    /** One restart per stall window; a stream that does not come back is left alone. */
    const val RESTART_BACKOFF_MS = 30_000L

    fun isStalled(frameAgeMs: Long): Boolean = frameAgeMs >= STALL_MS

    fun shouldRestart(frameAgeMs: Long, speedKmh: Float, msSinceLastRestart: Long): Boolean =
        isStalled(frameAgeMs) && speedKmh >= MIN_SPEED_KMH && msSinceLastRestart >= RESTART_BACKOFF_MS
}
