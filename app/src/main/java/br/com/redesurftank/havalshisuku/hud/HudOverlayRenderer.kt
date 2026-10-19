package br.com.redesurftank.havalshisuku.hud

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry
import br.com.redesurftank.havalshisuku.hud.HudOverlayModel as M

/**
 * Draws a [HudOverlayModel.Frame] in HUD pixels (480x240). Used by the HUD window itself and by
 * the layout previews in the Telas tab, so a preview always shows what the HUD will draw.
 * Transparent pixels let the native HUD show through, so nothing outside the free areas is
 * painted. Not thread-safe: one instance per thread (paints and scratch rects are reused).
 */
class HudOverlayRenderer {
    private companion object {
        /** Native speed readout in the preview mock: centred on the arcs (measured on the car). */
        const val NATIVE_SPEED_CX = M.NATIVE_SPEED_CX
        /** Chevrons in the Telas previews: a moment with the pulse half way along the row. */
        const val PREVIEW_PULSE_MS = 190L
        /** Map view roads: soft grey under the blue route. */
        const val MAP_ROAD_ALPHA = 215
        const val NATIVE_SPEED_BASELINE = 133f
        val DRIVE = Color.rgb(63, 169, 255)
        val OFF = Color.argb(61, 255, 255, 255)
        /** Arcs: power's unlit ticks stay fairly visible, RPM's fade back (owner's request). */
        val POWER_OFF = Color.argb(90, 255, 255, 255)
        val RPM_OFF = Color.argb(30, 255, 64, 64)
        val REGEN = Color.rgb(80, 230, 120)
        val RED = Color.rgb(255, 64, 64)
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    // Light weight, as the owner asked: bold read too heavy next to the native HUD.
    private val lightFace = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = lightFace
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = lightFace
    }
    private val tripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(217, 255, 255, 255)
        typeface = lightFace
        textSize = M.TRIP_SIZE
        textAlign = Paint.Align.CENTER
    }
    // Regular, not light: it sits inside the ring's hole and has to read at 18 px.
    private val exitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val laneOn = lanePaint(Color.WHITE)
    private val laneOff = lanePaint(Color.argb(90, 255, 255, 255))
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        strokeWidth = 2f
    }
    private val segOff = fillPaint(Color.argb(61, 255, 255, 255))
    private val segDrive = fillPaint(DRIVE)
    private val segRegen = fillPaint(REGEN)
    private val segRpm = fillPaint(Color.WHITE)
    private val segRed = fillPaint(RED)
    private val segRedOff = fillPaint(Color.argb(77, 255, 64, 64))
    private val zeroTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 255, 255, 255)
        strokeWidth = 1f
    }
    // Dynamic roundabout, in its 24 box (scaled like the lanes): bright travelled arc, entry and
    // exit; the rest of the ring dim.
    private val ringOn = lanePaint(Color.WHITE).apply { strokeWidth = M.RB_STROKE }
    private val ringOff = lanePaint(Color.argb(90, 255, 255, 255)).apply { strokeWidth = M.RB_STROKE }
    private val ringOval = RectF(M.RB_CX - M.RB_R, M.RB_CY - M.RB_R, M.RB_CX + M.RB_R, M.RB_CY + M.RB_R)
    private val headPath = Path()
    private val segRect = RectF()
    private val laneBounds = RectF()
    private val arrowBounds = RectF()

    private fun lanePaint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c
        style = Paint.Style.STROKE
        strokeWidth = 2.4f // in the 24x24 lane box, as tbtLanes.js
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun fillPaint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c
        style = Paint.Style.FILL
    }

    /**
     * Clears [canvas] and draws [frame] (nothing when null). Canvas units = HUD pixels. [nowMs]
     * drives the chevron animation.
     */
    fun draw(canvas: Canvas, frame: M.Frame?, nowMs: Long = android.os.SystemClock.uptimeMillis()) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawContent(canvas, frame, nowMs)
        frame?.chevrons?.let { drawChevrons(canvas, it, nowMs) }
    }

    // ---- AR chevrons above the speed ----

    private val chevronPaint = fillPaint(Color.rgb(90, 180, 255))
    private val chevronPath = Path()

    /**
     * A row of chevrons pointing to the turn side, sliding that way as the car closes in; a pulse
     * of light runs along the row towards the turn, faster the closer it is.
     */
    private fun drawChevrons(canvas: Canvas, ch: M.Chevrons, nowMs: Long) {
        val t = ch.intensity
        val period = M.CHEVRON_PERIOD_FAR_MS + (M.CHEVRON_PERIOD_NEAR_MS - M.CHEVRON_PERIOD_FAR_MS) * t
        val phase = (nowMs % period.toLong()) / period
        val appear = (t * 5f).coerceAtMost(1f) // fade in over the first ~10 m
        val base = 0.25f + 0.25f * t
        val (lead, dir, n) = M.chevronPose(ch.side, t)
        val rad = Math.toRadians(dir.toDouble())
        val ux = kotlin.math.cos(rad).toFloat()
        val uy = kotlin.math.sin(rad).toFloat()
        // A right-pointing chevron round the origin, turned to [dir] for each one.
        chevronPath.reset()
        chevronPath.moveTo(-M.CHEVRON_W, -M.CHEVRON_HALF_H)
        chevronPath.lineTo(-(M.CHEVRON_W - M.CHEVRON_STROKE), -M.CHEVRON_HALF_H)
        chevronPath.lineTo(M.CHEVRON_STROKE, 0f)
        chevronPath.lineTo(-(M.CHEVRON_W - M.CHEVRON_STROKE), M.CHEVRON_HALF_H)
        chevronPath.lineTo(-M.CHEVRON_W, M.CHEVRON_HALF_H)
        chevronPath.lineTo(0f, 0f)
        chevronPath.close()
        for (k in 0 until n) {
            // k = 0 trails, k = n-1 is the lead (pulse runs towards it, as before).
            val back = (n - 1 - k) * M.CHEVRON_SPACING
            var d = phase - k.toFloat() / M.CHEVRON_COUNT
            d -= kotlin.math.floor(d)
            val pulse = (1f - d) * (1f - d) * (1f - d)
            chevronPaint.alpha = (255 * appear * (base + (1f - base) * pulse)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(lead.first - ux * back, lead.second - uy * back)
            canvas.rotate(dir)
            canvas.drawPath(chevronPath, chevronPaint)
            canvas.restore()
        }
    }

    private fun drawContent(canvas: Canvas, frame: M.Frame?, nowMs: Long = 0L) {
        val f = frame ?: return
        f.radar?.let { drawRadar(canvas, it) }
        f.tbt?.let { tbt ->
            when (tbt.style) {
                M.TbtStyle.HERO -> drawHero(canvas, tbt)
                M.TbtStyle.COMPACT -> drawRow(canvas, tbt, M.COMPACT_TOP).also { drawLanes(canvas, tbt.lanes, M.LANES_TOP) }
                M.TbtStyle.FAR -> drawRow(canvas, tbt, M.FAR_TOP)
                M.TbtStyle.AR -> {
                    drawAr(canvas, tbt)
                    if (tbt.mapPending && M.mapPendingVisible(nowMs)) drawMapPending(canvas)
                    drawArColumn(canvas, tbt, f.arcs, f.wideArcs) // draws its own trip line
                }
            }
            if (tbt.style != M.TbtStyle.AR) drawNavText(canvas, tbt)
        }
        if (f.arcs) {
            f.powerPercent?.let { drawPowerArc(canvas, it, f.wideArcs) }
            f.rpmTenths?.let { drawRpmArc(canvas, it, f.wideArcs) }
        } else {
            f.powerPercent?.let { drawPowerBar(canvas, it) }
            f.rpmTenths?.let { drawRpmBar(canvas, it) }
        }
    }

    // Radar card: red ring like a speed-limit sign; the limit (and the distance) turn red while
    // the car is over it. No fill: black is transparent on the HUD, white would glare.
    private val radarRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RED
        style = Paint.Style.STROKE
        strokeWidth = M.RADAR_RING
    }
    private val radarLimit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val radarBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeJoin = Paint.Join.ROUND
    }
    private val badgeRect = RectF()

    private fun drawRadar(canvas: Canvas, radar: M.Radar) {
        val r = M.RADAR_SIGN / 2
        val cx = M.BOX_CENTER_X
        val cy = M.RADAR_TOP + r
        canvas.drawCircle(cx, cy, r - M.RADAR_RING / 2, radarRing)
        val limit = radar.limitKmh
        if (limit != null) {
            radarLimit.color = if (radar.over) RED else Color.WHITE
            radarLimit.textSize = if (limit >= 100) M.RADAR_LIMIT_SIZE_3DIGITS else M.RADAR_LIMIT_SIZE
            canvas.drawText(limit.toString(), cx, cy - (radarLimit.descent() + radarLimit.ascent()) / 2, radarLimit)
            drawCamera(canvas, cx + r * 0.78f, cy + r * 0.78f, M.RADAR_BADGE)
        } else {
            // No known limit: the camera itself fills the sign.
            drawCamera(canvas, cx, cy, M.RADAR_SIGN * 0.5f)
        }
        val (value, unit) = M.radarDistance(radar.distanceM)
        setTextSizes(M.RADAR_VALUE_SIZE, M.RADAR_UNIT_SIZE)
        val color = if (radar.over) RED else Color.WHITE
        valuePaint.color = color
        unitPaint.color = color
        val width = valuePaint.measureText(value) + M.UNIT_GAP + unitPaint.measureText(unit)
        val x = cx - width / 2
        val baseline = M.RADAR_TOP + M.RADAR_SIGN + M.RADAR_TEXT_GAP
        canvas.drawText(value, x, baseline, valuePaint)
        canvas.drawText(unit, x + valuePaint.measureText(value) + M.UNIT_GAP, baseline, unitPaint)
        valuePaint.color = Color.WHITE
        unitPaint.color = Color.WHITE
    }

    /** Camera outline centred on [cx], [cy], [size] wide: body, lens and the top bump. */
    private fun drawCamera(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val w = size * 0.8f
        val h = size * 0.55f
        badgeRect.set(cx - w / 2, cy - h / 2 + size * 0.06f, cx + w / 2, cy + h / 2 + size * 0.06f)
        canvas.drawRoundRect(badgeRect, size * 0.08f, size * 0.08f, radarBadge)
        canvas.drawCircle(cx, badgeRect.centerY(), h * 0.3f, radarBadge)
        canvas.drawLine(cx - w * 0.15f, badgeRect.top, cx - w * 0.05f, badgeRect.top - size * 0.1f, radarBadge)
        canvas.drawLine(cx - w * 0.05f, badgeRect.top - size * 0.1f, cx + w * 0.15f, badgeRect.top - size * 0.1f, radarBadge)
        canvas.drawLine(cx + w * 0.15f, badgeRect.top - size * 0.1f, cx + w * 0.25f, badgeRect.top, radarBadge)
    }

    /**
     * Navigation layout text under the turn-by-turn: the street (far row only) and the trip line
     * behind a small destination flag, each fitted to the box width (street cut with an ellipsis,
     * trip shrunk with its flag).
     */
    private fun drawNavText(canvas: Canvas, tbt: M.Tbt) {
        val maxW = M.BOX_RIGHT - M.BOX_LEFT
        val street = M.showsStreet(tbt)
        if (street) {
            navStreetPaint.textSize = M.FAR_STREET_SIZE
            val text = android.text.TextUtils.ellipsize(tbt.street, navStreetPaint, maxW, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, M.BOX_CENTER_X, M.FAR_STREET_BASELINE, navStreetPaint)
        }
        if (tbt.trip.isEmpty()) return
        val (baseline, wanted) = M.tripPlacement(tbt.style, street)
        navTripPaint.textSize = wanted
        val full = wanted * M.TRIP_FLAG_SCALE + M.TRIP_FLAG_GAP + navTripPaint.measureText(tbt.trip)
        val size = if (full > maxW) wanted * maxW / full else wanted
        navTripPaint.textSize = size
        val (flagLeft, textLeft) = M.tripLineLefts(navTripPaint.measureText(tbt.trip), size)
        drawFlag(canvas, flagLeft, baseline, size * M.TRIP_FLAG_SCALE)
        canvas.drawText(tbt.trip, textLeft, baseline, navTripPaint)
    }

    /** Destination flag in a [size] square standing on [baseline]: a pole and a pennant. */
    private fun drawFlag(canvas: Canvas, left: Float, baseline: Float, size: Float) {
        val s = size / 24f
        canvas.save()
        canvas.translate(left, baseline - size)
        canvas.scale(s, s)
        canvas.drawLine(5f, 3f, 5f, 23f, flagPole)
        canvas.drawPath(flagPath, flagFill)
        canvas.restore()
    }

    private val navTripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(217, 255, 255, 255)
        typeface = lightFace
        textAlign = Paint.Align.LEFT
    }
    private val flagFill = fillPaint(Color.argb(217, 255, 255, 255))
    private val flagPole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(217, 255, 255, 255)
        strokeWidth = 2.6f
        strokeCap = Paint.Cap.ROUND
    }
    // Pennant in the 24 box, notched on the fly side like a checkered-flag outline.
    private val flagPath = Path().apply {
        moveTo(5f, 3.5f); lineTo(20f, 3.5f); lineTo(16.5f, 8.5f); lineTo(20f, 13.5f); lineTo(5f, 13.5f); close()
    }

    private val navStreetPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        typeface = lightFace
        textAlign = Paint.Align.CENTER
    }

    // ---- AR layout: left column ----

    private val streetPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        typeface = lightFace
        textAlign = Paint.Align.LEFT
    }

    /**
     * The arrow is in the middle, so the left box carries the driving info: street, a large
     * distance and the trip line; compacted round the lane row when there are lanes.
     */
    private fun drawArColumn(canvas: Canvas, tbt: M.Tbt, arcs: Boolean, wideArcs: Boolean) {
        val lanes = tbt.lanes.isNotEmpty()
        // Still far: street and distance smaller, the turn isn't shown yet either.
        val far = HudArArrow.isFar(tbt.arMeters)
        val scale = if (far) M.AR_FAR_TEXT_SCALE else 1f
        val streetY = if (lanes) M.AR_LANES_STREET_BASELINE else M.AR_STREET_BASELINE
        streetPaint.textSize = scale * if (lanes) M.AR_LANES_STREET_SIZE else M.AR_STREET_SIZE
        val exit = tbt.exitNumber?.let { "${M.exitLabel(it)} saída" }
        // Roundabout (owner, option C): the exit, at full size, until the car is on the ring;
        // then the street it leads to.
        val streetLine = if (exit != null && !(tbt.onRoundabout && tbt.street.isNotEmpty())) exit else tbt.street
        // Centred in the column; with the arcs on, narrow enough to keep clear of them.
        fun lineMaxW(baseline: Float, size: Float) =
            if (arcs) 2f * (M.columnRightAt(baseline, size, wideArcs) - M.BOX_CENTER_X) else M.BOX_RIGHT - M.BOX_LEFT
        if (streetLine.isNotEmpty()) {
            val text = android.text.TextUtils.ellipsize(streetLine, streetPaint, lineMaxW(streetY, streetPaint.textSize), android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, M.BOX_CENTER_X - streetPaint.measureText(text) / 2, streetY, streetPaint)
        }
        if (lanes) setTextSizes(scale * M.AR_LANES_DISTANCE_VALUE_SIZE, scale * M.AR_LANES_DISTANCE_UNIT_SIZE)
        else setTextSizes(scale * M.AR_DISTANCE_VALUE_SIZE, scale * M.AR_DISTANCE_UNIT_SIZE)
        val width = distanceWidth(tbt)
        val distanceY = when {
            lanes -> M.AR_LANES_DISTANCE_BASELINE
            far -> M.AR_DISTANCE_BASELINE - M.AR_FAR_DISTANCE_RAISE
            else -> M.AR_DISTANCE_BASELINE
        }
        // The next turn as an icon before the distance (owner), the pair centred together.
        val iconSize = valuePaint.textSize * M.AR_DISTANCE_ICON_SCALE
        val iconW = if (tbt.icon.isNotEmpty() && width > 0f) iconSize + M.AR_DISTANCE_ICON_GAP else 0f
        val x0 = M.BOX_CENTER_X - (iconW + width) / 2
        if (iconW > 0f) {
            // Centred on the digits' height (~0.72 of the text size).
            val digitsMid = distanceY - valuePaint.textSize * 0.36f
            drawTurn(canvas, tbt, x0, digitsMid - iconSize / 2, iconSize)
        }
        drawDistance(canvas, tbt, x0 + iconW, distanceY)
        if (lanes) drawLanes(canvas, tbt.lanes, M.AR_LANES_TOP)
        if (tbt.trip.isNotEmpty()) {
            // Destination flag before the trip line, as in the Navegação layout; the pair is
            // shrunk together when it would not fit the box.
            val baseline = if (lanes) M.AR_LANES_TRIP_BASELINE else M.AR_TRIP_BASELINE
            val wanted = if (lanes) M.AR_LANES_TRIP_SIZE else M.AR_TRIP_SIZE
            val maxW = M.BOX_RIGHT - M.BOX_LEFT
            navTripPaint.textSize = wanted
            val full = wanted * M.TRIP_FLAG_SCALE + M.TRIP_FLAG_GAP + navTripPaint.measureText(tbt.trip)
            val size = if (full > maxW) wanted * maxW / full else wanted
            navTripPaint.textSize = size
            val (flagLeft, textLeft) = M.tripLineLefts(navTripPaint.measureText(tbt.trip), size)
            drawFlag(canvas, flagLeft, baseline, size * M.TRIP_FLAG_SCALE)
            canvas.drawText(tbt.trip, textLeft, baseline, navTripPaint)
        }
    }

    // ---- AR layout: power / RPM arcs round the native speed ----

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    /** Scratch for drawLines: one batch per colour (lit / off), 4 floats per tick. */
    private val tickLit = FloatArray(M.ARC_TICKS * 4)
    private val tickOff = FloatArray(M.ARC_TICKS * 4)

    /**
     * 100 radial ticks from [rIn] to [rOut]; the first [lit] (from the left end) in [litColor],
     * the rest in [offColor]. Batched into two drawLines calls.
     */
    private fun drawTicks(canvas: Canvas, rIn: Float, rOut: Float, width: Float, lit: Int, litColor: Int, offColor: Int, wide: Boolean) {
        var nLit = 0
        var nOff = 0
        for (i in 0 until M.ARC_TICKS) {
            val deg = M.arcTickDeg(i, wide)
            val (xIn, yIn) = M.arcPoint(deg, rIn, wide)
            val (xOut, yOut) = M.arcPoint(deg, rOut, wide)
            val buf = if (i < lit) tickLit else tickOff
            var k = if (i < lit) nLit else nOff
            buf[k++] = xIn
            buf[k++] = yIn
            buf[k++] = xOut
            buf[k++] = yOut
            if (i < lit) nLit = k else nOff = k
        }
        arcPaint.strokeWidth = width
        if (nOff > 0) {
            arcPaint.color = offColor
            canvas.drawLines(tickOff, 0, nOff, arcPaint)
        }
        if (nLit > 0) {
            arcPaint.color = litColor
            canvas.drawLines(tickLit, 0, nLit, arcPaint)
        }
    }

    /** Outer arc: thick, long ticks; drive blue / regen green, both filling from the left. */
    private fun drawPowerArc(canvas: Canvas, percent: Int, wide: Boolean) {
        drawTicks(
            canvas, M.ARC_POWER_R_IN, M.ARC_POWER_R_OUT, M.ARC_POWER_TICK_W,
            M.arcPowerTicks(percent), if (percent >= 0) DRIVE else REGEN, POWER_OFF, wide
        )
    }

    /** Inner arc: thin, short red ticks over 0..7000 rpm. Hidden with the engine off. */
    private fun drawRpmArc(canvas: Canvas, tenths: Int, wide: Boolean) {
        drawTicks(canvas, M.ARC_RPM_R_IN, M.ARC_RPM_R_OUT, M.ARC_RPM_TICK_W, M.arcRpmTicks(tenths), RED, RPM_OFF, wide)
    }

    // ---- AR arrow (Navegação AR) ----

    private val arPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    /** All pieces of one ribbon in one path, filled once: no seams, no double-blended overlaps. */
    private val arPath = Path().apply { fillType = Path.FillType.WINDING }

    /**
     * One vertical gradient carries both the colour (lighter blue at the base, deeper on top; owner:
     * "slightly darker on top") and the fades at the top and bottom edges, so a wide stretch of
     * ribbon running sideways fades per pixel, not per piece.
     */
    private fun arRgb(y: Float): Int {
        val t = HudArArrow.rowGradient(y)
        return Color.rgb((120 + (25 - 120) * t).toInt(), (200 + (105 - 200) * t).toInt(), 255)
    }

    private fun rowShader(top: Float, rgb: (Float) -> Int): android.graphics.Shader {
        // Transparent at both edges, opaque between the fades (HudArArrow.rowAlpha).
        val ys = floatArrayOf(top, top + (HudArArrow.FADE_TOP_TO - HudArArrow.TOP), HudArArrow.FADE_BOTTOM_FROM, HudArArrow.BOTTOM)
        val alphas = intArrayOf(0, 255, 255, 0)
        val colors = IntArray(ys.size) { i ->
            val c = rgb(ys[i])
            Color.argb(alphas[i], Color.red(c), Color.green(c), Color.blue(c))
        }
        val span = HudArArrow.BOTTOM - top
        return android.graphics.LinearGradient(
            0f, top, 0f, HudArArrow.BOTTOM, colors,
            ys.map { (it - top) / span }.toFloatArray(), android.graphics.Shader.TileMode.CLAMP
        )
    }

    /**
     * The 3D arrow ([HudArArrow]) in the middle of the HUD, see-through ([HudArArrow.OPACITY]):
     * grey ring and passed exits first, the route on top, then its head.
     */
    private fun drawAr(canvas: Canvas, tbt: M.Tbt) {
        // Unknown distance: a plain straight arrow, never a turn drawn as if it were here.
        val meters = tbt.arMeters ?: Float.MAX_VALUE
        val place = tbt.arPlacement
        val route = tbt.route
        // Map view: real roads to the horizon (under the speed, or the HUD's top as an option),
        // drawn in the centre picture's pixels; the RIGHT placement scales it onto the lane
        // lines like the arrow (taller, so less squeezed).
        val map = route != null
        // Map on the right: a plain perspective like the thumbnail's road, narrowed, not stretched.
        val mapRight = map && place == HudArArrow.Placement.RIGHT
        val cx = if (mapRight) HudArArrow.MAP_RIGHT_CX else place.cx
        val sx = if (mapRight) HudArArrow.MAP_RIGHT_SX else place.sx
        val sy = if (mapRight) 1f else place.sy
        val horizon = if (mapRight) HudArArrow.MAP_RIGHT_HORIZON_Y else tbt.mapHorizon
        // Never above the visible band (y 40) once scaled.
        val top = if (map) maxOf(horizon, HudArArrow.BOTTOM - (HudArArrow.BOTTOM - 40f) / sy) else place.top
        val shape = when {
            route != null -> HudArArrow.mapScene(tbt.streets, route, tbt.arDestination, horizon)
            tbt.dynamicRoundabout -> HudArArrow.roundabout(
                meters, tbt.exitAngle!!.toFloat(), tbt.roundaboutClockwise,
                M.passedExitAngles(tbt.exitAngle, tbt.exitNumber), tbt.arProgress, place
            )
            tbt.arDestination != null -> HudArArrow.destination(meters, tbt.arDestination, place)
            else -> HudArArrow.shape(meters, tbt.arAngle ?: 0f, place)
        }
        // Map: the whole panel (in the picture's own units); else the arrow's room.
        val left = if (map) HudArArrow.CX - cx / sx else HudArArrow.CX - maxOf(place.roomLeft, HudArArrow.BASE_W / 2) - 2f
        val right = if (map) HudArArrow.CX + (480f - cx) / sx else HudArArrow.CX + maxOf(place.roomRight, HudArArrow.BASE_W / 2) + 2f
        val opacity = HudArArrow.opacityFor(meters)
        canvas.save()
        // RIGHT / map: the same picture scaled about (CX, BOTTOM).
        canvas.translate(cx, HudArArrow.BOTTOM)
        canvas.scale(sx, sy)
        canvas.translate(-HudArArrow.CX, -HudArArrow.BOTTOM)
        canvas.clipRect(left, top, right, HudArArrow.BOTTOM)
        // Opaque into a layer, then composited once: drawing overlapping pieces translucent
        // would stack into visible stripes at the seams.
        canvas.saveLayerAlpha(left, top, right, HudArArrow.BOTTOM, (opacity * 255).toInt())
        if (shape.gray.isNotEmpty()) {
            arPaint.shader = if (map) mapShader(top, blue = false) else shaderFor(grayShaders, top)
            arPaint.alpha = ((if (map) MAP_ROAD_ALPHA else 140) * shape.grayAlpha).toInt().coerceIn(0, 255)
            arPath.reset()
            for (ribbon in shape.gray) addRibbon(ribbon, top)
            canvas.drawPath(arPath, arPaint)
            // The grey roads fade out to the sides too (before the route goes on top).
            if (map) fadeSides(canvas, left, right, top, HudArArrow.CX)
        }
        arPaint.shader = if (map) mapShader(top, blue = true) else shaderFor(blueShaders, top)
        arPaint.alpha = 255
        arPath.reset()
        addRibbon(shape.blue, top)
        val h = shape.head
        if (h.size == 6) {
            // Same winding as the ribbon's pieces, so the overlap fills once.
            val cross = (h[2] - h[0]) * (h[5] - h[1]) - (h[3] - h[1]) * (h[4] - h[0])
            arPath.moveTo(h[0], h[1])
            if (cross > 0f) { arPath.lineTo(h[2], h[3]); arPath.lineTo(h[4], h[5]) } else { arPath.lineTo(h[4], h[5]); arPath.lineTo(h[2], h[3]) }
            arPath.close()
        }
        canvas.drawPath(arPath, arPaint)
        arPaint.shader = null
        canvas.restore() // layer
        canvas.restore() // transform + clip
        shape.flag?.let { drawArFlag(canvas, it, cx, sx, sy, opacity) }
    }

    private val mapPendingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 190, 195, 205)
        typeface = lightFace
        textAlign = Paint.Align.CENTER
        textSize = 14f
    }

    /** Map on, area not loaded: a grey note on the right (the arrows carry on meanwhile). */
    private fun drawMapPending(canvas: Canvas) {
        canvas.drawText("carregando mapa…", M.MAP_PENDING_X, M.MAP_PENDING_Y, mapPendingPaint)
    }

    // ---- map view fades (owner): far = more see-through; grey roads also fade to the sides; the
    // blue route only a little, so it stays the clearest thing on the map ----

    private val mapGrayShaders = HashMap<Float, android.graphics.Shader>()
    private val mapBlueShaders = HashMap<Float, android.graphics.Shader>()

    private fun mapShader(top: Float, blue: Boolean): android.graphics.Shader =
        (if (blue) mapBlueShaders else mapGrayShaders).getOrPut(top) {
            val span = HudArArrow.BOTTOM - top
            val ys = floatArrayOf(top, top + span * 0.15f, top + span * 0.5f, HudArArrow.BOTTOM - 10f, HudArArrow.BOTTOM)
            val alphas = if (blue) floatArrayOf(0.5f, 0.85f, 1f, 1f, 0.75f) else floatArrayOf(0f, 0.25f, 0.7f, 1f, 0.75f)
            val colors = IntArray(ys.size) { i ->
                val c = if (blue) arRgb(ys[i]) else Color.rgb(200, 205, 215)
                Color.argb((alphas[i] * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
            }
            android.graphics.LinearGradient(
                0f, top, 0f, HudArArrow.BOTTOM, colors,
                ys.map { (it - top) / span }.toFloatArray(), android.graphics.Shader.TileMode.CLAMP
            )
        }

    private val sideMaskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    /** Fades what is already in the layer towards [left] / [right], fully kept round [centre]. */
    private fun fadeSides(canvas: Canvas, left: Float, right: Float, top: Float, centre: Float) {
        val c = ((centre - left) / (right - left)).coerceIn(0.05f, 0.95f)
        sideMaskPaint.shader = android.graphics.LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(Color.argb(40, 0, 0, 0), Color.argb(255, 0, 0, 0), Color.argb(255, 0, 0, 0), Color.argb(40, 0, 0, 0)),
            floatArrayOf(0f, (c - 0.25f).coerceAtLeast(0.01f), (c + 0.25f).coerceAtMost(0.99f), 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(left, top, right, HudArArrow.BOTTOM, sideMaskPaint)
    }

    /** Vertical gradients per band top (the arrow's bands, the map's band up to the horizon). */
    private val blueShaders = HashMap<Float, android.graphics.Shader>()
    private val grayShaders = HashMap<Float, android.graphics.Shader>()

    private fun shaderFor(cache: HashMap<Float, android.graphics.Shader>, top: Float): android.graphics.Shader =
        cache.getOrPut(top) {
            if (cache === blueShaders) rowShader(top) { y -> arRgb(y) } else rowShader(top) { Color.rgb(200, 205, 215) }
        }

    /**
     * The destination flag standing on the road, drawn upright in screen space (the RIGHT
     * placement's stretch would make it tall and thin) and as see-through as the arrow.
     */
    private fun drawArFlag(canvas: Canvas, flag: HudArArrow.Flag, cx: Float, sx: Float, sy: Float, opacity: Float) {
        val fx = cx + (flag.x - HudArArrow.CX) * sx
        val y = HudArArrow.BOTTOM - (HudArArrow.BOTTOM - flag.y) * sy
        if (y <= M.CHEVRON_CY || y > HudArArrow.BOTTOM) return
        val size = (flag.height * kotlin.math.sqrt(sx * sy)).coerceIn(10f, y - 40f)
        // The pennant flies right of the pole: keep it on the panel (RIGHT placement, right side).
        val x = fx.coerceAtMost(480f - size * 0.7f)
        canvas.saveLayerAlpha(x - size, y - size - 2f, x + size, y + 2f, (255 * (0.35f + opacity)).toInt().coerceAtMost(255))
        // drawFlag's pole runs at x 5, y 3..23 of its 24 box: stand it on (x, y).
        drawFlag(canvas, x - 5f * size / 24f, y + size / 24f, size)
        canvas.restore()
    }

    /**
     * One ribbon into [arPath]: a quad between each pair of cross-sections, all wound the same
     * way so the overlaps of neighbouring quads fill once.
     */
    private fun addRibbon(pts: List<HudArArrow.Pt>, top: Float) {
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            if (maxOf(a.ly, a.ry, b.ly, b.ry) <= top || minOf(a.ly, a.ry, b.ly, b.ry) >= HudArArrow.BOTTOM) continue
            val cross = (b.lx - a.lx) * (a.ry - a.ly) - (b.ly - a.ly) * (a.rx - a.lx)
            arPath.moveTo(a.lx, a.ly)
            if (cross >= 0f) {
                arPath.lineTo(b.lx, b.ly); arPath.lineTo(b.rx, b.ry); arPath.lineTo(a.rx, a.ry)
            } else {
                arPath.lineTo(a.rx, a.ry); arPath.lineTo(b.rx, b.ry); arPath.lineTo(b.lx, b.ly)
            }
            arPath.close()
        }
    }

    // ---- turn-by-turn ----

    /** No lanes: big arrow centred, small distance centred under it. */
    private fun drawHero(canvas: Canvas, tbt: M.Tbt) {
        val left = M.BOX_CENTER_X - M.HERO_GLYPH / 2
        val bottom = drawTurn(canvas, tbt, left, M.HERO_TOP, M.HERO_GLYPH)
        tbt.exitNumber?.let { exit ->
            val (cx, cy) = M.ringHoleCenter(left, M.HERO_TOP, M.HERO_GLYPH, tbt.dynamicRoundabout)
            exitPaint.textSize = M.heroExitSize(exit)
            canvas.drawText(exit.toString(), cx, cy - (exitPaint.descent() + exitPaint.ascent()) / 2, exitPaint)
        }
        setTextSizes(M.HERO_VALUE_SIZE, M.HERO_UNIT_SIZE)
        val width = distanceWidth(tbt)
        drawDistance(canvas, tbt, M.BOX_CENTER_X - width / 2, bottom + M.HERO_TEXT_GAP)
    }

    /**
     * Small arrow + "250 m" as one row centred in the box, the text on the arrow's bottom edge.
     * A roundabout exit follows the distance as "2ª", in the unit's size.
     */
    private fun drawRow(canvas: Canvas, tbt: M.Tbt, top: Float) {
        setTextSizes(M.COMPACT_VALUE_SIZE, M.COMPACT_UNIT_SIZE)
        val exit = tbt.exitNumber?.let { M.exitLabel(it) }
        val distWidth = distanceWidth(tbt)
        val exitWidth = exit?.let { unitPaint.measureText(it) } ?: 0f
        val textWidth = distWidth + if (exit != null && distWidth > 0f) M.EXIT_GAP + exitWidth else exitWidth
        val gap = if (textWidth > 0f) M.COMPACT_ROW_GAP else 0f
        val left = M.BOX_CENTER_X - (M.COMPACT_GLYPH + gap + textWidth) / 2
        val bottom = drawTurn(canvas, tbt, left, top, M.COMPACT_GLYPH)
        val textLeft = left + M.COMPACT_GLYPH + gap
        drawDistance(canvas, tbt, textLeft, bottom)
        if (exit != null) {
            val x = textLeft + textWidth - exitWidth
            canvas.drawText(exit, x, bottom, unitPaint)
        }
    }

    /** Draws the maneuver icon in a [size] square; returns the y of its visible bottom edge. */
    private fun drawTurn(canvas: Canvas, tbt: M.Tbt, left: Float, top: Float, size: Float): Float {
        val icon = tbt.icon
        if (icon.isEmpty()) return top + size
        val exitAngle = tbt.exitAngle
        if (tbt.dynamicRoundabout && exitAngle != null) {
            drawRoundabout(canvas, left, top, size, exitAngle, tbt.roundaboutClockwise, tbt.exitNumber)
            return top + (M.RB_ENTRY_BOTTOM + M.RB_STROKE / 2) / 24f * size
        }
        val path = HudTbtIcons.turnPath(icon, size)
        if (path != null) {
            canvas.save()
            canvas.translate(left, top)
            canvas.drawPath(path, fill)
            canvas.restore()
            // The SVGs don't fill their box (TURN_RIGHT ends at 22/24), so use the real edge.
            path.computeBounds(arrowBounds, true)
            return top + arrowBounds.bottom
        }
        glyphPaint.textSize = size * 0.9f
        val baseline = top + size / 2 - (glyphPaint.descent() + glyphPaint.ascent()) / 2
        canvas.drawText(HudTbtIcons.turnGlyph(icon), left + size / 2, baseline, glyphPaint)
        return baseline
    }

    /**
     * Ring with the entry at the bottom and an arrow at the exit [angle] degrees around it; the
     * exits passed before it are short dim stubs (positions approximate, see passedExitAngles).
     */
    private fun drawRoundabout(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        angle: Int,
        clockwise: Boolean,
        exitNumber: Int?
    ) {
        val scale = size / 24f
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawCircle(M.RB_CX, M.RB_CY, M.RB_R, ringOff)
        for (passed in M.passedExitAngles(angle, exitNumber)) {
            val r = Math.toRadians(M.roundaboutExitDegrees(passed, clockwise).toDouble())
            val sx = kotlin.math.cos(r).toFloat()
            val sy = -kotlin.math.sin(r).toFloat()
            canvas.drawLine(M.RB_CX + sx * M.RB_R, M.RB_CY + sy * M.RB_R, M.RB_CX + sx * M.RB_STUB_END, M.RB_CY + sy * M.RB_STUB_END, ringOff)
        }
        // Canvas arcs run clockwise from 3 o'clock; the entry is at 6 o'clock (90).
        canvas.drawArc(ringOval, 90f, if (clockwise) angle.toFloat() else -angle.toFloat(), false, ringOn)
        canvas.drawLine(M.RB_CX, M.RB_ENTRY_BOTTOM, M.RB_CX, M.RB_CY + M.RB_R, ringOn)
        val rad = Math.toRadians(M.roundaboutExitDegrees(angle, clockwise).toDouble())
        val dx = kotlin.math.cos(rad).toFloat()
        val dy = -kotlin.math.sin(rad).toFloat()
        val baseR = M.RB_EXIT_TIP - M.RB_HEAD_LENGTH
        canvas.drawLine(M.RB_CX + dx * M.RB_R, M.RB_CY + dy * M.RB_R, M.RB_CX + dx * (baseR + 0.5f), M.RB_CY + dy * (baseR + 0.5f), ringOn)
        val bx = M.RB_CX + dx * baseR
        val by = M.RB_CY + dy * baseR
        headPath.reset()
        headPath.moveTo(M.RB_CX + dx * M.RB_EXIT_TIP, M.RB_CY + dy * M.RB_EXIT_TIP)
        headPath.lineTo(bx - dy * M.RB_HEAD_HALF_WIDTH, by + dx * M.RB_HEAD_HALF_WIDTH)
        headPath.lineTo(bx + dy * M.RB_HEAD_HALF_WIDTH, by - dx * M.RB_HEAD_HALF_WIDTH)
        headPath.close()
        canvas.drawPath(headPath, fill)
        canvas.restore()
    }

    private fun setTextSizes(value: Float, unit: Float) {
        valuePaint.textSize = value
        unitPaint.textSize = unit
    }

    private fun distanceWidth(tbt: M.Tbt): Float {
        if (tbt.distanceValue.isEmpty()) return 0f
        val unit = if (tbt.distanceUnit.isEmpty()) 0f else M.UNIT_GAP + unitPaint.measureText(tbt.distanceUnit)
        return valuePaint.measureText(tbt.distanceValue) + unit
    }

    private fun drawDistance(canvas: Canvas, tbt: M.Tbt, x: Float, baseline: Float) {
        if (tbt.distanceValue.isEmpty()) return
        canvas.drawText(tbt.distanceValue, x, baseline, valuePaint)
        if (tbt.distanceUnit.isNotEmpty()) {
            val ux = x + valuePaint.measureText(tbt.distanceValue) + M.UNIT_GAP
            canvas.drawText(tbt.distanceUnit, ux, baseline, unitPaint)
        }
    }

    private fun drawLanes(canvas: Canvas, lanes: List<List<M.LaneArrow>>, top: Float) {
        val cell = M.laneCellWidth(lanes.size)
        val left = M.BOX_CENTER_X - cell * lanes.size / 2
        val height = M.LANE_ROW_HEIGHT
        val icon = minOf(cell - 4f, height)
        val scale = icon / 24f
        for (i in 1 until lanes.size) {
            val x = left + i * cell
            var y = top + 2f
            while (y < top + height - 2f) {
                canvas.drawLine(x, y, x, y + 5f, divider)
                y += 10f
            }
        }
        lanes.forEachIndexed { i, arrows ->
            val cx = left + i * cell + cell / 2
            canvas.save()
            canvas.translate(cx - icon / 2, top + (height - icon) / 2)
            canvas.scale(scale, scale)
            // Centre the lane on what it actually draws: a turn arrow reaches to one side of
            // the 24-box, so centring the box left it visibly off its cell.
            canvas.translate(12f - laneCenterX(arrows), 0f)
            for (arrow in arrows) {
                val path = HudTbtIcons.lanePath(arrow.shape) ?: continue
                canvas.save()
                if (arrow.mirrored) {
                    canvas.translate(24f, 0f)
                    canvas.scale(-1f, 1f)
                }
                canvas.drawPath(path, if (arrow.highlighted) laneOn else laneOff)
                canvas.restore()
            }
            canvas.restore()
        }
    }

    /** Horizontal centre of a lane's arrows in the 24-box, mirrored ones included. */
    private fun laneCenterX(arrows: List<M.LaneArrow>): Float {
        laneBounds.setEmpty()
        for (arrow in arrows) {
            val path = HudTbtIcons.lanePath(arrow.shape) ?: continue
            path.computeBounds(arrowBounds, true)
            if (arrow.mirrored) arrowBounds.set(24f - arrowBounds.right, arrowBounds.top, 24f - arrowBounds.left, arrowBounds.bottom)
            if (laneBounds.isEmpty) laneBounds.set(arrowBounds) else laneBounds.union(arrowBounds)
        }
        return if (laneBounds.isEmpty) 12f else laneBounds.centerX()
    }

    // ---- bars ----

    private fun segment(canvas: Canvas, left: Float, width: Float, paint: Paint) {
        val h = M.BAR_HEIGHT
        segRect.set(left, M.BAR_Y - h / 2, left + width, M.BAR_Y + h / 2)
        canvas.drawRoundRect(segRect, 1f, 1f, paint)
    }

    /** Centre-zero at the HUD's left edge: regen segments left (green), drive right (blue). */
    private fun drawPowerBar(canvas: Canvas, percent: Int) {
        val mid = (M.POWER_LEFT + M.POWER_RIGHT) / 2
        val w = M.POWER_SEGMENT_WIDTH
        val lit = M.powerSegments(percent)
        for (i in 0 until M.POWER_SEGMENTS_PER_SIDE) {
            val off = M.powerSegmentOffset(i)
            segment(canvas, mid + off, w, if (lit > 0 && i < lit) segDrive else segOff)
            segment(canvas, mid - off - w, w, if (lit < 0 && i < -lit) segRegen else segOff)
        }
        canvas.drawLine(mid, M.BAR_Y - M.POWER_ZERO_TICK, mid, M.BAR_Y + M.POWER_ZERO_TICK, zeroTick)
    }

    /** At the HUD's right edge, one segment per 1000 rpm; the 5-6k segment is red. */
    private fun drawRpmBar(canvas: Canvas, tenths: Int) {
        val n = M.RPM_SEGMENTS
        val w = (M.RPM_RIGHT - M.RPM_LEFT - M.RPM_SEGMENT_GAP * (n - 1)) / n
        val lit = M.rpmSegments(tenths)
        for (i in 0 until n) {
            val red = M.rpmSegmentIsRed(i)
            val paint = when {
                i < lit -> if (red) segRed else segRpm
                else -> if (red) segRedOff else segOff
            }
            segment(canvas, M.RPM_LEFT + i * (w + M.RPM_SEGMENT_GAP), w, paint)
        }
    }

    // ---- layout previews (Telas tab) ----

    private val nativePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val nativeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val scenePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * A "what you'd see through the windshield" picture of [config] in [scenario]: a road at
     * dusk, the car's own HUD (speed, icons, lane keeping), and the Impulse overlay drawn by this
     * renderer. Only the band that reaches the optics (y 40..200) is shown.
     */
    fun preview(
        config: M.Config,
        scenario: PreviewScenario = previewScenarios(config.layout).first(),
        width: Int = 480,
        height: Int = 160
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / 480f, height / 160f)
        canvas.translate(0f, -40f)
        drawScene(canvas)
        drawNativeHud(canvas, scenario.speedKmh)
        if (config.active) {
            val frame = M.frameFor(
                config, scenario.route, scenario.powerPercent, scenario.rpmTenths,
                wideArcs = M.wideArcs(scenario.speedKmh.toDouble(), wasWide = false)
            )
            drawContent(canvas, frame)
            // A still of the approach chevrons (the sharp-left preview), mid-pulse.
            frame.chevrons?.let { drawChevrons(canvas, it, PREVIEW_PULSE_MS) }
        }
        return bitmap
    }

    private fun drawScene(canvas: Canvas) {
        // Dusk sky down to the horizon, then the road.
        scenePaint.shader = LinearGradient(0f, 40f, 0f, 120f, Color.rgb(46, 62, 86), Color.rgb(18, 24, 34), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 40f, 480f, 120f, scenePaint)
        scenePaint.shader = LinearGradient(0f, 120f, 0f, 200f, Color.rgb(24, 26, 30), Color.rgb(10, 11, 13), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 120f, 480f, 200f, scenePaint)
        scenePaint.shader = null
        // Road surface narrowing to the vanishing point, with lane markings.
        val road = Path().apply {
            moveTo(-60f, 200f); lineTo(225f, 120f); lineTo(255f, 120f); lineTo(540f, 200f); close()
        }
        scenePaint.color = Color.rgb(34, 36, 40)
        canvas.drawPath(road, scenePaint)
        scenePaint.color = Color.argb(70, 255, 255, 255)
        scenePaint.strokeWidth = 2f
        canvas.drawLine(240f, 120f, 240f, 200f, scenePaint)
        canvas.drawLine(225f, 120f, -60f, 200f, scenePaint)
        canvas.drawLine(255f, 120f, 540f, 200f, scenePaint)
        // Distant tail lights.
        scenePaint.color = Color.argb(200, 255, 70, 60)
        canvas.drawCircle(236f, 126f, 1.6f, scenePaint)
        canvas.drawCircle(244f, 126f, 1.6f, scenePaint)
    }

    /** Rough stand-in for the car's own HUD, placed as on the owner's photo. */
    private fun drawNativeHud(canvas: Canvas, speedKmh: Int) {
        nativePaint.typeface = lightFace
        nativePaint.textSize = 58f
        // Speed centred where it sits on the car (see HudOverlayModel.NATIVE_SPEED_CX), km/h after it.
        nativePaint.textAlign = Paint.Align.CENTER
        nativePaint.color = Color.argb(225, 255, 255, 255)
        val speed = speedKmh.toString()
        canvas.drawText(speed, NATIVE_SPEED_CX, NATIVE_SPEED_BASELINE, nativePaint)
        val half = nativePaint.measureText(speed) / 2
        nativePaint.textSize = 14f
        nativePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("km/h", NATIVE_SPEED_CX + half + 4f, NATIVE_SPEED_BASELINE, nativePaint)
        // Icons under the speed, shifted down with it (as on the car).
        canvas.drawCircle(214f, 162f, 9f, nativeStroke)
        canvas.drawCircle(244f, 162f, 9f, nativeStroke)
        nativePaint.color = Color.rgb(255, 122, 61)
        canvas.drawCircle(276f, 162f, 10f, nativePaint)
        nativeStroke.strokeWidth = 4f
        canvas.drawLine(388f, 140f, 404f, 84f, nativeStroke)
        nativeStroke.color = Color.rgb(47, 211, 143)
        canvas.drawLine(458f, 84f, 470f, 140f, nativeStroke)
        nativeStroke.color = Color.WHITE
        nativeStroke.strokeWidth = 2f
        nativePaint.color = Color.WHITE
        canvas.drawRect(420f, 92f, 442f, 114f, nativePaint)
    }
}

/** One moment shown in a layout preview: the car's speed, the route (if any) and bar readings. */
data class PreviewScenario(
    val speedKmh: Int,
    val route: AndroidAutoNavigationTelemetry.Directions?,
    val powerPercent: Int,
    val rpmTenths: Int?
)

private fun lane(vararg arrows: Pair<Int, Boolean>) =
    arrows.map { (shape, used) -> AndroidAutoNavigationTelemetry.LaneDirection(shape, used) }

/** Town: right turn in 250 m on a three-lane road, light EV drive, engine off. */
private val TOWN_TURN = PreviewScenario(
    speedKmh = 42,
    route = AndroidAutoNavigationTelemetry.Directions(
        active = true, distance = "250 m", distanceM = 250, turn = "TURN_RIGHT",
        remainingM = 4_800, remainingS = 720,
        lanes = listOf(lane(1 to false), lane(1 to false), lane(5 to true, 1 to false))
    ),
    powerPercent = 18,
    rpmTenths = null
)

/** Highway: exit 2.4 km ahead (small arrow when far), hybrid pulling with the engine on. */
private val HIGHWAY_EXIT = PreviewScenario(
    speedKmh = 110,
    route = AndroidAutoNavigationTelemetry.Directions(
        active = true, distance = "2.4 km", distanceM = 2_400, turn = "OFF_RAMP_RIGHT", street = "Rod. dos Bandeirantes",
        remainingM = 27_300, remainingS = 1_260
    ),
    powerPercent = 62,
    rpmTenths = 31
)

/** Roundabout in 80 m, 2nd exit straight across (dynamic ring, "2" inside) while braking: regen, engine off. */
private val ROUNDABOUT_REGEN = PreviewScenario(
    speedKmh = 35,
    route = AndroidAutoNavigationTelemetry.Directions(
        active = true, distance = "80 m", distanceM = 80, turn = "ROUNDABOUT",
        remainingM = 2_100, remainingS = 360, exitNumber = 2, turnAngle = 180, roundaboutClockwise = false
    ),
    powerPercent = -45,
    rpmTenths = null
)

private fun arTurn(
    turn: String,
    meters: Int,
    distance: String,
    street: String,
    lanes: List<List<AndroidAutoNavigationTelemetry.LaneDirection>> = emptyList()
) = AndroidAutoNavigationTelemetry.Directions(
    active = true, distance = distance, distanceM = meters, turn = turn, street = street,
    remainingM = 4_800, remainingS = 720, lanes = lanes
)

/** AR: a right turn still 650 m away (straight wedge), cruising in EV. */
private val AR_FAR = PreviewScenario(64, arTurn("TURN_RIGHT", 650, "650 m", "Av. Paulista"), 22, null)

/** AR: 90 m from the turn with lane guidance, the arm growing, engine running. */
private val AR_BENDING = PreviewScenario(
    38,
    arTurn("TURN_RIGHT", 90, "90 m", "R. Manuel da Nóbrega", listOf(lane(1 to false), lane(1 to false), lane(5 to true, 1 to false))),
    12,
    24
)

/** AR: at a sharp left while regenerating. */
private val AR_AT_TURN = PreviewScenario(18, arTurn("SHARP_LEFT", 10, "10 m", "R. Augusta"), -45, null)

/** Three moments per layout; the Telas previews cycle through them. */
fun previewScenarios(layout: M.Layout): List<PreviewScenario> = when (layout) {
    // The car's own HUD only: what changes is the speed.
    M.Layout.ORIGINAL -> listOf(
        PreviewScenario(42, null, 0, null),
        PreviewScenario(110, null, 0, null),
        PreviewScenario(0, null, 0, null)
    )
    M.Layout.NAVIGATION -> listOf(TOWN_TURN, HIGHWAY_EXIT, ROUNDABOUT_REGEN)
    // Far straight arrow, the turn bending in, then right at the corner.
    M.Layout.AR -> listOf(AR_FAR, AR_BENDING, AR_AT_TURN)
}
