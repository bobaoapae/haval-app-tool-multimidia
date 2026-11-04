package br.com.redesurftank.havalshisuku.radar

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Decides, fix by fix, which camera is ahead and when to alert. Pure Kotlin (no Android), so
 * the whole behaviour is unit-tested; RadarManager feeds it GPS fixes and the car's speed.
 *
 * A camera only counts when the car is on the camera's own road, travelling along it in a
 * direction the camera enforces, with the camera ahead and the distance along the road
 * shrinking over two fixes in a row. The road comes from OSM (RadarRoads): the car must be
 * within [ON_ROAD_M] of it, heading along it. A camera without a road falls back to a strict
 * straight-line test (it must sit almost on the car's heading line).
 *
 * The target is then held until it is passed, or the car leaves its road, and a passed camera
 * never alerts twice on the same pass. Distances are along the road.
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
        /** Metres to the camera along its road (straight line for a camera without a road). */
        val distanceM: Int,
        val limitKmh: Int?,
        val over: Boolean,
        val phase: Phase,
        /** Matched on its OSM road (false = straight-line fallback). For the durable trace. */
        val onRoad: Boolean = false
    )

    data class Update(val alert: Alert?, val event: Event?)

    private class Target(val camera: RadarCamera, val road: CameraRoad?, var minAlong: Double, var phase: Phase, var over: Boolean, var offFixes: Int = 0)

    /** Where a camera is for the car right now. */
    private sealed class Where {
        class Ahead(val along: Double) : Where()
        class Behind(val by: Double) : Where()
        /** Not on the camera's road, not along it, or the camera enforces the other way. */
        object Off : Where()
    }

    companion object {
        /** Below this the GPS course is noise, so the last good heading is kept. */
        const val MIN_HEADING_SPEED_MPS = 2f
        /** On the camera's road: this close to it (plus [ROAD_SLACK_DEG] past the stored stretch)... */
        const val ON_ROAD_M = 20.0
        /** ...heading within this of the road's direction (either way along it). */
        const val ALIGN_DEG = 35f
        /** Looser while a camera is already the target, so GPS wobble doesn't drop it. */
        const val HOLD_ON_ROAD_M = 35.0
        const val HOLD_ALIGN_DEG = 50f
        /** Past the stored stretch the road is assumed straight; allow this much bend. */
        const val ROAD_SLACK_DEG = 4.0
        /** No road: the camera must sit this close to the heading line (plus [LINE_SLACK_DEG]). */
        const val LINE_M = 15.0
        const val LINE_SLACK_DEG = 2.0
        /** No road: a camera whose direction is known must point within this of our heading. */
        const val DIRECTION_MATCH_DEG = 50f
        /** The distance must drop this much between two fixes before a camera alerts ("closing in"). */
        const val CLOSING_M = 2.0
        /** Distance growing this much past its minimum = passed (or turned back). */
        const val PASSED_GROWTH_M = 25.0
        /** Fixes in a row off the camera's road before the target is dropped (turned off). */
        const val OFF_FIXES = 3
        /** A passed camera stays muted this long, or until this far past the approach distance. */
        const val PASSED_MUTE_MS = 10 * 60_000L
        const val PASSED_CLEAR_EXTRA_M = 500.0
        /** Without a GPS course, a heading is derived once the car moved this far. */
        const val MOVE_FOR_HEADING_M = 15.0
        /**
         * Subtracted from the database distance before every decision. A camera 220 m ahead
         * is 200 m for the alert, the near warning, passed, and the number on the sign.
         */
        const val POSITION_EARLY_M = 20.0
    }

    private var heading: Float? = null
    private var lastMoveLat = Double.NaN
    private var lastMoveLon = Double.NaN
    private var target: Target? = null
    private val passed = HashMap<RadarCamera, Long>()
    /** Cameras that qualified on the last fix, with their distance then (for "closing in"). */
    private var candidates = HashMap<RadarCamera, Double>()

    fun reset() {
        heading = null
        lastMoveLat = Double.NaN
        lastMoveLon = Double.NaN
        target = null
        passed.clear()
        candidates = HashMap()
    }

    /** One GPS fix. [carSpeedKmh] is the car's own speedometer (decides "over the limit"). */
    fun onFix(fix: Fix, carSpeedKmh: Double?, config: Config, index: RadarIndex?): Update {
        updateHeading(fix)
        clearPassed(fix, config)
        var event: Event? = null
        var along: Double? = null
        target?.let { t ->
            when (val w = where(fix, t.camera, t.road, hold = true)) {
                is Where.Ahead -> {
                    t.offFixes = 0
                    if (w.along > t.minAlong + PASSED_GROWTH_M || w.along > config.approachM + PASSED_CLEAR_EXTRA_M) {
                        drop(t, fix)
                    } else {
                        if (w.along < t.minAlong) t.minAlong = w.along
                        along = w.along
                    }
                }
                // Owner: the camera is taken as really [POSITION_EARLY_M] before the database
                // point, so reaching that point is passing it (the old 0 m could linger past it).
                is Where.Behind -> drop(t, fix)
                Where.Off -> {
                    // Parked or crawling, the heading is stale: keep the target as it was.
                    if (isMoving(fix)) t.offFixes++
                    if (t.offFixes >= OFF_FIXES) drop(t, fix) else along = t.minAlong
                }
            }
            val held = target
            val a = along
            if (held != null && a != null && held.phase == Phase.APPROACH && a <= config.nearM) {
                held.phase = Phase.NEAR
                event = Event.NEAR
            }
        }
        if (target == null && index != null) {
            acquire(fix, config, index)?.let { (camera, road, d) ->
                val phase = if (d <= config.nearM) Phase.NEAR else Phase.APPROACH
                target = Target(camera, road, d, phase, over = false)
                along = d
                event = if (phase == Phase.NEAR) Event.NEAR else Event.APPROACH
            }
        }
        val t = target ?: return Update(null, event)
        val limit = t.camera.limitKmh
        val over = limit != null && carSpeedKmh != null && carSpeedKmh > limit
        if (over && !t.over && event == null) event = Event.OVERSPEED
        // One OVERSPEED per camera; the alert itself still shows the live state.
        if (over) t.over = true
        return Update(Alert(t.camera, (along ?: t.minAlong).roundToInt(), limit, over, t.phase, onRoad = t.road != null), event)
    }

    private fun drop(t: Target, fix: Fix) {
        passed[t.camera] = fix.timeMs
        target = null
        candidates.clear()
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

    /** Where [camera] is for the car; [hold] uses the looser limits of a held target. */
    private fun where(fix: Fix, camera: RadarCamera, road: CameraRoad?, hold: Boolean): Where {
        val h = heading ?: return Where.Off
        return if (road != null) whereOnRoad(fix, h, road, hold) else whereOnLine(fix, h, camera)
    }

    private fun whereOnRoad(fix: Fix, h: Float, road: CameraRoad, hold: Boolean): Where {
        val p = road.snippet.project(fix.lat, fix.lon)
        val limit = (if (hold) HOLD_ON_ROAD_M else ON_ROAD_M) + p.beyondM * tan(Math.toRadians(ROAD_SLACK_DEG))
        if (p.perpM > limit) return Where.Off
        val align = if (hold) HOLD_ALIGN_DEG else ALIGN_DEG
        val travel = when {
            RadarGeo.angleDiff(h, p.dirDeg) <= align -> 1
            RadarGeo.angleDiff(h, p.dirDeg + 180f) <= align -> -1
            else -> return Where.Off
        }
        if (road.allowed != 0 && road.allowed != travel) return Where.Off
        val ahead = (road.sCamera - p.s) * travel
        return early(ahead)
    }

    /** No road known: the camera must lie (almost) on the car's heading line. */
    private fun whereOnLine(fix: Fix, h: Float, camera: RadarCamera): Where {
        if (camera.bearing != null && RadarGeo.angleDiff(camera.bearing, h) > DIRECTION_MATCH_DEG) return Where.Off
        val d = RadarGeo.distanceM(fix.lat, fix.lon, camera.lat, camera.lon)
        val rel = Math.toRadians(RadarGeo.angleDiff(RadarGeo.bearingDeg(fix.lat, fix.lon, camera.lat, camera.lon), h).toDouble())
        val forward = d * cos(rel)
        if (forward < 0) return early(forward)
        val lateral = d * sin(rel)
        if (lateral > LINE_M + forward * tan(Math.toRadians(LINE_SLACK_DEG))) return Where.Off
        return early(forward)
    }

    /** Database metres, with [POSITION_EARLY_M] already taken off, before any alert decision. */
    private fun early(ahead: Double): Where {
        val at = ahead - POSITION_EARLY_M
        return if (at >= 0.0) Where.Ahead(at) else Where.Behind(-at)
    }

    /** The nearest camera that qualified on this fix and on the last one, closing in. */
    private fun acquire(fix: Fix, config: Config, index: RadarIndex): Triple<RadarCamera, CameraRoad?, Double>? {
        if (heading == null || !isMoving(fix)) {
            candidates.clear()
            return null
        }
        val now = HashMap<RadarCamera, Double>()
        var best: Triple<RadarCamera, CameraRoad?, Double>? = null
        // Look far enough to see a camera whose told distance is still inside the approach setting.
        index.forEachNear(fix.lat, fix.lon, config.approachM + POSITION_EARLY_M) { i, cam, _ ->
            if (passed.containsKey(cam)) return@forEachNear
            val road = index.roadOf(i)
            val w = where(fix, cam, road, hold = false) as? Where.Ahead ?: return@forEachNear
            if (w.along > config.approachM) return@forEachNear
            now[cam] = w.along
            val before = candidates[cam] ?: return@forEachNear
            if (w.along > before - CLOSING_M) return@forEachNear
            val b = best
            if (b == null || w.along < b.third) best = Triple(cam, road, w.along)
        }
        candidates = now
        return best
    }

    private fun clearPassed(fix: Fix, config: Config) {
        if (passed.isEmpty()) return
        val far = config.approachM + PASSED_CLEAR_EXTRA_M
        passed.entries.removeAll { (cam, at) ->
            fix.timeMs - at > PASSED_MUTE_MS || RadarGeo.distanceM(fix.lat, fix.lon, cam.lat, cam.lon) > far
        }
    }
}
