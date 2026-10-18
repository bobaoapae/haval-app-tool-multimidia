package br.com.redesurftank.havalshisuku.radar

import kotlin.math.roundToInt

/**
 * Decides, fix by fix, which camera is ahead and when to alert. Pure Kotlin (no Android), so
 * the whole behaviour is unit-tested; RadarManager feeds it GPS fixes and the car's speed.
 *
 * A camera becomes the target when it is within the approach distance, roughly straight ahead
 * of the car's heading, and (when its direction is known) enforces our direction of travel.
 * The target is then held until it is passed (the distance grows again) or left behind, and a
 * passed camera never alerts twice on the same pass.
 */
class RadarAlertEngine {
    data class Fix(
        val lat: Double,
        val lon: Double,
        /** GPS course over ground, degrees; null when the receiver gives none. */
        val bearing: Float?,
        /** m/s; null when unknown. */
        val speedMps: Float?,
        val timeMs: Long
    )

    data class Config(
        val approachM: Int = 500,
        val nearM: Int = 150
    )

    enum class Phase { APPROACH, NEAR }

    enum class Event {
        /** A camera ahead came within the approach distance. */
        APPROACH,
        /** The target came within the near distance. */
        NEAR,
        /** The car went over the target's limit while it was being alerted (once per camera). */
        OVERSPEED
    }

    data class Alert(
        val camera: RadarCamera,
        val distanceM: Int,
        val limitKmh: Int?,
        val over: Boolean,
        val phase: Phase
    )

    data class Update(val alert: Alert?, val event: Event?)

    private class Target(val camera: RadarCamera, var minDistance: Double, var phase: Phase, var over: Boolean, var awayFixes: Int = 0)

    companion object {
        /** Below this the GPS course is noise, so the last good heading is kept. */
        const val MIN_HEADING_SPEED_MPS = 2f
        /** Ahead means within this angle of the heading... */
        const val AHEAD_DEG = 25f
        /** ...or this one when very close, where GPS error swings the angle. */
        const val AHEAD_CLOSE_DEG = 50f
        const val CLOSE_M = 80.0
        /** A camera whose direction is known must point within this of our heading. */
        const val DIRECTION_MATCH_DEG = 50f
        /** Distance growing this much past its minimum = passed. */
        const val PASSED_GROWTH_M = 25.0
        /**
         * Fixes in a row with the target well off to the side before dropping it (turned onto a
         * cross street: the camera then sits at ~90°). Gentler curves are left to the
         * distance-growth rule, so a camera round a bend is not dropped.
         */
        const val AWAY_FIXES = 3
        const val AWAY_DEG = 80f
        /** A passed camera stays muted this long, or until this far past the approach distance. */
        const val PASSED_MUTE_MS = 10 * 60_000L
        const val PASSED_CLEAR_EXTRA_M = 500.0
        /** Without a fix moving at least this far, a heading is derived from movement. */
        const val MOVE_FOR_HEADING_M = 15.0
    }

    private var heading: Float? = null
    private var lastMoveLat = Double.NaN
    private var lastMoveLon = Double.NaN
    private var target: Target? = null
    private val passed = HashMap<RadarCamera, Long>()

    fun reset() {
        heading = null
        lastMoveLat = Double.NaN
        lastMoveLon = Double.NaN
        target = null
        passed.clear()
    }

    /** One GPS fix. [carSpeedKmh] is the car's own speedometer (decides "over the limit"). */
    fun onFix(fix: Fix, carSpeedKmh: Double?, config: Config, index: RadarIndex?): Update {
        updateHeading(fix)
        clearPassed(fix, config)
        var event: Event? = null
        val current = target
        if (current != null) {
            val d = RadarGeo.distanceM(fix.lat, fix.lon, current.camera.lat, current.camera.lon)
            if (isPassed(current, d, fix, config)) {
                passed[current.camera] = fix.timeMs
                target = null
            } else {
                if (d < current.minDistance) current.minDistance = d
                if (current.phase == Phase.APPROACH && d <= config.nearM) {
                    current.phase = Phase.NEAR
                    event = Event.NEAR
                }
            }
        }
        if (target == null && index != null) {
            acquire(fix, config, index)?.let { (camera, d) ->
                val phase = if (d <= config.nearM) Phase.NEAR else Phase.APPROACH
                target = Target(camera, d, phase, over = false)
                event = if (phase == Phase.NEAR) Event.NEAR else Event.APPROACH
            }
        }
        val t = target ?: return Update(null, event)
        val d = RadarGeo.distanceM(fix.lat, fix.lon, t.camera.lat, t.camera.lon)
        val limit = t.camera.limitKmh
        val over = limit != null && carSpeedKmh != null && carSpeedKmh > limit
        if (over && !t.over && event == null) event = Event.OVERSPEED
        // Once over, stays over for this camera's events (one OVERSPEED per camera); the alert
        // itself still shows the live state.
        if (over) t.over = true
        return Update(Alert(t.camera, d.roundToInt(), limit, over, t.phase), event)
    }

    private fun updateHeading(fix: Fix) {
        val speed = fix.speedMps
        if (fix.bearing != null && speed != null && speed >= MIN_HEADING_SPEED_MPS) {
            heading = RadarGeo.normalize(fix.bearing)
            lastMoveLat = fix.lat
            lastMoveLon = fix.lon
            return
        }
        // No usable course: derive it from how far we moved since the last known point.
        if (lastMoveLat.isNaN()) {
            lastMoveLat = fix.lat
            lastMoveLon = fix.lon
            return
        }
        if (RadarGeo.distanceM(lastMoveLat, lastMoveLon, fix.lat, fix.lon) >= MOVE_FOR_HEADING_M) {
            heading = RadarGeo.bearingDeg(lastMoveLat, lastMoveLon, fix.lat, fix.lon)
            lastMoveLat = fix.lat
            lastMoveLon = fix.lon
        }
    }

    private fun isMoving(fix: Fix): Boolean = (fix.speedMps ?: 0f) >= MIN_HEADING_SPEED_MPS

    private fun isPassed(t: Target, d: Double, fix: Fix, config: Config): Boolean {
        if (d > t.minDistance + PASSED_GROWTH_M) return true
        if (d > config.approachM + PASSED_CLEAR_EXTRA_M) return true
        val h = heading ?: return false
        if (!isMoving(fix) || d < CLOSE_M) {
            t.awayFixes = 0
            return false
        }
        val rel = RadarGeo.angleDiff(RadarGeo.bearingDeg(fix.lat, fix.lon, t.camera.lat, t.camera.lon), h)
        t.awayFixes = if (rel > AWAY_DEG) t.awayFixes + 1 else 0
        return t.awayFixes >= AWAY_FIXES
    }

    private fun acquire(fix: Fix, config: Config, index: RadarIndex): Pair<RadarCamera, Double>? {
        val h = heading ?: return null
        if (!isMoving(fix)) return null
        var best: RadarCamera? = null
        var bestD = Double.MAX_VALUE
        index.forEachNear(fix.lat, fix.lon, config.approachM.toDouble()) { _, cam, d ->
            if (d >= bestD || passed.containsKey(cam)) return@forEachNear
            val rel = RadarGeo.angleDiff(RadarGeo.bearingDeg(fix.lat, fix.lon, cam.lat, cam.lon), h)
            if (rel > (if (d < CLOSE_M) AHEAD_CLOSE_DEG else AHEAD_DEG)) return@forEachNear
            if (cam.bearing != null && RadarGeo.angleDiff(cam.bearing, h) > DIRECTION_MATCH_DEG) return@forEachNear
            best = cam
            bestD = d
        }
        return best?.let { it to bestD }
    }

    private fun clearPassed(fix: Fix, config: Config) {
        if (passed.isEmpty()) return
        val far = config.approachM + PASSED_CLEAR_EXTRA_M
        passed.entries.removeAll { (cam, at) ->
            fix.timeMs - at > PASSED_MUTE_MS || RadarGeo.distanceM(fix.lat, fix.lon, cam.lat, cam.lon) > far
        }
    }
}
