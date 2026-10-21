package br.com.redesurftank.havalshisuku.navigation

import java.util.Collections

/**
 * Offline preparation only. No Binder, timers, JSON, telemetry, HUD or display side effects.
 *
 * A future adapter must serialize events and publication on one executor, pass a monotonic elapsed
 * clock, schedule [flush] at [nextFlushAtMs], and cancel/re-arm that timer after every event. The
 * existing Android Auto publisher is deliberately NOT wired to this class.
 */
class PreparedNavigationArbiter {
    enum class Source { ANDROID_AUTO, CARPLAY }

    /** Identity, not just its number, is checked; tokens from another arbiter are invalid. */
    class Session internal constructor(val source: Source, val generation: Long)

    data class Snapshot(
        val source: Source?,
        val generation: Long?,
        val directions: PreparedDirections
    )

    data class SessionStarted(val session: Session, val publication: Snapshot?)

    private data class LinkedSource(val session: Session, var latest: PreparedDirections? = null)

    private val linked = mutableMapOf<Source, LinkedSource>()
    private var nextGeneration = 0L
    private var lastNowMs = 0L
    private var lastPublishedAtMs: Long? = null
    private var published = inactiveSnapshot()
    private var pending: Snapshot? = null

    /** Begin a confirmed linked session. Rebinding invalidates that source's previous callbacks. */
    fun connect(source: Source, nowMs: Long): SessionStarted {
        checkTime(nowMs)
        check(nextGeneration < Long.MAX_VALUE) { "Session generation exhausted" }
        val session = Session(source, ++nextGeneration)
        linked[source] = LinkedSource(session)
        return SessionStarted(session, reconcile(nowMs))
    }

    /** An inactive or disconnected non-owner cannot clear the current owner's guidance. */
    fun disconnect(session: Session, nowMs: Long): Snapshot? {
        if (!isCurrent(session)) return null
        checkTime(nowMs)
        linked.remove(session.source)
        return reconcile(nowMs)
    }

    fun onUpdate(session: Session, directions: PreparedDirections, nowMs: Long): Snapshot? {
        if (!isCurrent(session)) return null
        checkTime(nowMs)
        linked.getValue(session.source).latest = freeze(directions)
        return reconcile(nowMs)
    }

    /** Latest committed publication, never an uncommitted throttled update. Safe for late readers. */
    fun snapshot(): Snapshot = published

    /** Absolute monotonic deadline; a burst does not postpone the deadline on every callback. */
    fun nextFlushAtMs(): Long? {
        if (pending == null) return null
        val last = lastPublishedAtMs ?: return null
        return if (last > Long.MAX_VALUE - THROTTLE_MS) Long.MAX_VALUE else last + THROTTLE_MS
    }

    /** A stale scheduled callback is harmless: pending data was invalidated on ownership/reset. */
    fun flush(nowMs: Long): Snapshot? {
        checkTime(nowMs)
        val waiting = pending ?: return null
        val candidate = selected()
        if (candidate != waiting) {
            pending = null
            return null
        }
        val last = lastPublishedAtMs ?: return null
        if (nowMs - last < THROTTLE_MS) return null
        return publish(waiting, nowMs)
    }

    /** Invalidate all sessions, clear both snapshots and trailing state; generations never reset. */
    fun reset(nowMs: Long): Snapshot? {
        checkTime(nowMs)
        linked.clear()
        pending = null
        val wasActive = published.source != null
        published = inactiveSnapshot()
        lastPublishedAtMs = null
        return published.takeIf { wasActive }
    }

    private fun isCurrent(session: Session): Boolean = linked[session.source]?.session === session

    private fun checkTime(nowMs: Long) {
        require(nowMs >= 0L && nowMs >= lastNowMs) { "A nonnegative monotonic elapsed clock is required" }
        lastNowMs = nowMs
    }

    private fun selected(): Snapshot? {
        // Priority is policy, not an inference from arrival order. An idle AA link does not win.
        for (source in listOf(Source.ANDROID_AUTO, Source.CARPLAY)) {
            val candidate = linked[source] ?: continue
            val directions = candidate.latest?.takeIf { it.active } ?: continue
            return Snapshot(source, candidate.session.generation, directions)
        }
        return null
    }

    private fun reconcile(nowMs: Long): Snapshot? {
        val candidate = selected() ?: inactiveSnapshot()
        if (candidate == published) {
            pending = null
            return null
        }
        val ownerChanged = candidate.source != published.source || candidate.generation != published.generation
        val last = lastPublishedAtMs
        if (ownerChanged || identityChanged(candidate.directions, published.directions) ||
            last == null || nowMs - last >= THROTTLE_MS
        ) {
            return publish(candidate, nowMs)
        }
        pending = candidate
        return null
    }

    private fun publish(candidate: Snapshot, nowMs: Long): Snapshot {
        published = candidate
        pending = null
        lastPublishedAtMs = nowMs
        return candidate
    }

    private fun identityChanged(a: PreparedDirections, b: PreparedDirections): Boolean =
        a.active != b.active || a.navigationState != b.navigationState ||
            a.turnId != b.turnId || a.turn != b.turn || a.street != b.street ||
            a.nextStreet != b.nextStreet || a.lanes != b.lanes || a.exitNumber != b.exitNumber ||
            a.turnAngle != b.turnAngle || a.roundaboutClockwise != b.roundaboutClockwise

    private fun freeze(directions: PreparedDirections): PreparedDirections =
        directions.copy(lanes = Collections.unmodifiableList(ArrayList(directions.lanes)))

    private fun inactiveSnapshot() = Snapshot(null, null, PreparedDirections(active = false, navigationState = "INACTIVE"))

    companion object {
        const val THROTTLE_MS = 1_000L
    }
}
