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
        private val loadedFaces = java.util.concurrent.ConcurrentHashMap<M.Font, Typeface>()

        /** The face's own file when the system has it (see [M.Font]), else its family. */
        fun typefaceFor(font: M.Font): Typeface = loadedFaces.getOrPut(font) {
            font.file?.let { java.io.File("/system/fonts", it) }?.takeIf { it.isFile }
                ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
                ?: Typeface.create(font.family, Typeface.NORMAL)
        }

        /** Native speed readout in the preview mock: centred on the arcs (measured on the car). */
        const val NATIVE_SPEED_CX = M.NATIVE_SPEED_CX
        /** Chevrons in the Telas previews: a moment with the pulse half way along the row. */
        const val PREVIEW_PULSE_MS = 190L
        /** Flag pennant in drawFlag's 24 box, next to the pole at x 5. */
        const val FLAG_L = 5f
        const val FLAG_T = 3.5f
        const val FLAG_R = 21f
        const val FLAG_B = 14f
        /** The destination flag on the map/arrow: size factor, largest size, and alpha (min + span x arrow opacity). */
        const val AR_FLAG_SCALE = 0.6f
        const val AR_FLAG_MIN = 8f
        const val AR_FLAG_MAX = 20f
        /** Its pole and outline, in the flag's 24 box (the white trip flag: 2.6 and 0.7). */
        const val AR_FLAG_POLE = 1.6f
        const val AR_FLAG_OUTLINE = 0.45f
        const val AR_FLAG_ALPHA_MIN = 0.3f
        const val AR_FLAG_ALPHA_SPAN = 0.5f
        /** Map view roads: soft grey under the blue route. */
        const val MAP_ROAD_ALPHA = 215
        /** Map view: streets and route fade together over the last few px, to this share. */
        const val MAP_BOTTOM_FADE_PX = 5f
        const val MAP_BOTTOM_FADE_TO = 0.5f
        /** Edge-line roads: stroke width (half of it stays, outside the road) and its alpha. */
        const val MAP_EDGE_STROKE = 3f
        const val MAP_EDGE_ALPHA = 255
        const val NATIVE_SPEED_BASELINE = 131f
        /** Preview mock of the car's speed (see drawNativeHud): digit cell, sizes, 3-digit centre. */
        const val NATIVE_DIGIT_CELL = 46f
        const val NATIVE_DIGIT_SIZE = 55f
        const val NATIVE_UNIT_SIZE = 14f
        const val NATIVE_SPEED_CX_3DIGITS = 227f
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
        drawWithChevrons(canvas, frame, nowMs, nowMs)
    }

    /**
     * The content, then the chevrons on top. Translucent over white text they read as behind it
     * (owner, 2026-10-25), so the content goes into a layer first and each chevron cuts a thin
     * margin out of it before it is drawn.
     */
    private fun drawWithChevrons(canvas: Canvas, frame: M.Frame?, nowMs: Long, chevronMs: Long) {
        val ch = frame?.chevrons
        if (ch == null) {
            drawContent(canvas, frame, nowMs)
            return
        }
        val layer = canvas.saveLayer(0f, 0f, 480f, 240f, null)
        drawContent(canvas, frame, nowMs)
        // Three-digit speeds: round the big arc circle (only where the arcs are drawn).
        val wide = frame.arcs && frame.wideArcs
        for (mark in chevronMarks(ch, wide)) placeChevron(canvas, mark, chevronCut)
        canvas.restoreToCount(layer)
        drawChevrons(canvas, ch, chevronMs, wide)
    }

    /** Clears a chevron's shape, a little larger, from what is under it (see drawWithChevrons). */
    private val chevronCut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = M.CHEVRON_CUT_MARGIN * 2
        strokeJoin = Paint.Join.ROUND
        xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private fun placeChevron(canvas: Canvas, mark: M.ChevronMark, paint: Paint) {
        canvas.save()
        canvas.translate(mark.x, mark.y)
        canvas.rotate(mark.dir)
        canvas.drawPath(chevronPath, paint)
        canvas.restore()
    }

    /** The chevrons to draw for [ch]: the animated strip, or the still direction arrow. */
    private fun chevronMarks(ch: M.Chevrons, wide: Boolean): List<M.ChevronMark> =
        if (ch.animate) M.chevronStrip(ch.side, ch.sharp, ch.intensity, ch.turnDeg, wide)
        else listOf(M.chevronCompass(ch.side, ch.sharp, ch.lean, ch.roadDeg, ch.turnDeg, wide))

    // ---- AR chevrons above the speed ----

    private val chevronPaint = fillPaint(Color.rgb(90, 180, 255))
    /** A right-pointing chevron round the origin, turned to each one's direction. */
    private val chevronPath = Path().apply {
        moveTo(-M.CHEVRON_W, -M.CHEVRON_HALF_H)
        lineTo(-(M.CHEVRON_W - M.CHEVRON_STROKE), -M.CHEVRON_HALF_H)
        lineTo(M.CHEVRON_STROKE, 0f)
        lineTo(-(M.CHEVRON_W - M.CHEVRON_STROKE), M.CHEVRON_HALF_H)
        lineTo(-M.CHEVRON_W, M.CHEVRON_HALF_H)
        lineTo(0f, 0f)
        close()
    }

    /**
     * A row of chevrons pointing to the turn side, sliding that way as the car closes in; a pulse
     * of light runs along the row towards the turn, faster the closer it is.
     */
    private fun drawChevrons(canvas: Canvas, ch: M.Chevrons, nowMs: Long, wide: Boolean) {
        val t = if (ch.animate) ch.intensity else 0f
        val period = M.CHEVRON_PERIOD_FAR_MS + (M.CHEVRON_PERIOD_NEAR_MS - M.CHEVRON_PERIOD_FAR_MS) * t
        val phase = if (ch.animate) (nowMs % period.toLong()) / period else 0f
        val appear = 1f
        val base = if (ch.animate) 0.45f + 0.4f * t else 0.9f
        // Still: the direction arrow round the arc ("Sempre" far away; on the diagonal from 100 m).
        val marks = chevronMarks(ch, wide)
        for ((k, mark) in marks.withIndex()) {
            // k = 0 trails, the last is the lead (pulse runs towards it, as before).
            var d = phase - k.toFloat() / M.CHEVRON_COUNT
            d -= kotlin.math.floor(d)
            val pulse = (1f - d) * (1f - d) * (1f - d)
            chevronPaint.alpha = (255 * chevronOpacity * appear * mark.appear * (base + (1f - base) * pulse)).toInt().coerceIn(0, 255)
            placeChevron(canvas, mark, chevronPaint)
        }
    }

    private var face: M.Font? = null

    /** The owner's text face on every text paint (the ring's exit number and the radar keep theirs). */
    private fun applyFont(font: M.Font) {
        if (font == face) return
        face = font
        val tf = typefaceFor(font)
        for (p in listOf(valuePaint, unitPaint, tripPaint, navTripPaint, streetPaint, mapPendingPaint)) p.typeface = tf
    }

    private var colors: M.Colors? = null
    private var textColor = Color.WHITE
    private var driveColor = DRIVE
    private var regenColor = REGEN
    private var rpmColor = RED
    private var rpmOffColor = RPM_OFF
    private var streetColor = Color.rgb(200, 205, 215)
    private var routeNear = Color.rgb(120, 200, 255)
    private var routeFar = M.COLOR_ROUTE

    /**
     * The owner's colours (Cores tab) on the paints: text and icons keep each paint's own alpha;
     * the map's gradients are rebuilt.
     */
    /** Each text paint's own alpha, before the owner's opacity multiplies it. */
    private val baseAlpha = HashMap<Paint, Int>()
    /** Opacities (0..1) where a paint's alpha is set while drawing. */
    private var chevronOpacity = 1f
    private var routeOpacity = 1f
    /** The streets against their default opacity (1 = as they always were). */
    private var streetGain = 1f

    private fun applyColors(c: M.Colors) {
        if (c == colors) return
        colors = c
        textColor = c.text
        val textOpacity = M.opacityOf(c.text)
        for (p in listOf(
            fill, valuePaint, unitPaint, tripPaint, exitPaint, glyphPaint, laneOn, laneOff, divider, ringOn, ringOff,
            navTripPaint, flagFill, flagPole, flagOutline, streetPaint, radarLimit, radarBadge
        )) {
            val a = baseAlpha.getOrPut(p) { p.alpha }
            p.color = ((a * textOpacity).toInt().coerceIn(0, 255) shl 24) or (c.text and M.RGB_MASK)
        }
        driveColor = c.drive
        regenColor = c.regen
        segDrive.color = c.drive
        segRegen.color = c.regen
        rpmColor = c.rpm
        rpmOffColor = ((Color.alpha(RPM_OFF) * M.opacityOf(c.rpm)).toInt() shl 24) or (c.rpm and M.RGB_MASK)
        streetColor = c.street or M.ALPHA_MASK
        streetGain = M.opacityOf(c.street) / M.STREET_DEFAULT_OPACITY
        routeNear = M.routeNear(c.route)
        routeFar = c.route or M.ALPHA_MASK
        routeOpacity = M.opacityOf(c.route)
        chevronPaint.color = c.chevron or M.ALPHA_MASK
        chevronOpacity = M.opacityOf(c.chevron)
        mapGrayShaders.clear()
        mapBlueShaders.clear()
        blueShaders.clear()
        grayShaders.clear()
    }

    private fun drawContent(canvas: Canvas, frame: M.Frame?, nowMs: Long = 0L) {
        val f = frame ?: return
        applyFont(f.font)
        applyColors(f.colors)
        f.tbt?.let { tbt ->
            when (tbt.style) {
                // Further than the owner's "show from": only the trip line (drawNavText).
                M.TbtStyle.HERO -> if (f.radar == null && !tbt.turnHidden) drawHero(canvas, tbt)
                M.TbtStyle.COMPACT -> if (f.radar == null && !tbt.turnHidden) drawRow(canvas, tbt, M.COMPACT_TOP).also { drawLanes(canvas, tbt.lanes, M.LANES_TOP) }
                M.TbtStyle.FAR -> if (f.radar == null && !tbt.turnHidden) drawRow(canvas, tbt, M.FAR_TOP)
                M.TbtStyle.AR -> {
                    // The map stays while the radar card is up. The left column shares that
                    // card's box, so it steps aside until the camera is gone.
                    drawAr(canvas, tbt)
                    if (f.radar == null) {
                        if (tbt.mapPending) drawMapPending(canvas, tbt.mapProgress, nowMs)
                        drawArColumn(canvas, tbt, f.arcs, f.wideArcs) // draws its own trip line
                    }
                }
            }
            if (tbt.style != M.TbtStyle.AR && f.radar == null) {
                if (tbt.turnHidden) drawTripBlock(canvas, tbt, arcs = false, wideArcs = false) else drawNavText(canvas, tbt)
            }
        }
        f.radar?.let { drawRadar(canvas, it) }
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
            radarLimit.color = if (radar.over) RED else textColor
            radarLimit.textSize = if (limit >= 100) M.RADAR_LIMIT_SIZE_3DIGITS else M.RADAR_LIMIT_SIZE
            canvas.drawText(limit.toString(), cx, cy - (radarLimit.descent() + radarLimit.ascent()) / 2, radarLimit)
            drawCamera(canvas, cx + r * 0.78f, cy + r * 0.78f, M.RADAR_BADGE)
        } else {
            // No known limit: the camera itself fills the sign.
            drawCamera(canvas, cx, cy, M.RADAR_SIGN * 0.5f)
        }
        val (value, unit) = M.radarDistance(radar.distanceM)
        setTextSizes(M.RADAR_VALUE_SIZE, M.RADAR_UNIT_SIZE)
        val color = if (radar.over) RED else textColor
        valuePaint.color = color
        unitPaint.color = color
        val width = valuePaint.measureText(value) + M.UNIT_GAP + unitPaint.measureText(unit)
        val x = cx - width / 2
        val baseline = M.RADAR_TOP + M.RADAR_SIGN + M.RADAR_TEXT_GAP
        canvas.drawText(value, x, baseline, valuePaint)
        canvas.drawText(unit, x + valuePaint.measureText(value) + M.UNIT_GAP, baseline, unitPaint)
        valuePaint.color = textColor
        unitPaint.color = textColor
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
        if (tbt.trip.isEmpty()) return
        val (baseline, wanted) = M.tripPlacement(tbt.style)
        drawTripLine(canvas, tbt.trip, baseline, wanted, M.BOX_RIGHT)
    }

    /**
     * Flag + trip text on [baseline], units smaller ([M.TRIP_UNIT_SCALE]). Centred in the box
     * while it fits; longer, it starts further left (down to [M.TRIP_MIN_LEFT]) before it is
     * shrunk to end by [right] (owner, 2026-10-24: with hours it ran into the signs).
     */
    private fun drawTripLine(canvas: Canvas, trip: String, baseline: Float, wanted: Float, right: Float) {
        fun full(size: Float) = size * M.TRIP_FLAG_SCALE + M.TRIP_FLAG_GAP + richWidth(trip, size)
        val room = right - M.TRIP_MIN_LEFT
        val size = if (full(wanted) > room) wanted * room / full(wanted) else wanted
        val left = M.columnLeft(full(size), right)
        drawFlag(canvas, left, baseline, size * M.TRIP_FLAG_SCALE)
        drawRich(canvas, trip, left + size * M.TRIP_FLAG_SCALE + M.TRIP_FLAG_GAP, baseline, size)
    }

    /** Runs of [text]: unit letters (true) apart from numbers, spaces and signs. */
    private fun richRuns(text: String): List<Pair<String, Boolean>> {
        val runs = ArrayList<Pair<String, Boolean>>()
        val sb = StringBuilder()
        var unit = false
        for (c in text) {
            val u = c.isLetter()
            if (sb.isNotEmpty() && u != unit) { runs += sb.toString() to unit; sb.clear() }
            unit = u
            sb.append(c)
        }
        if (sb.isNotEmpty()) runs += sb.toString() to unit
        return runs
    }

    private fun richWidth(text: String, size: Float): Float {
        var w = 0f
        for ((run, unit) in richRuns(text)) {
            navTripPaint.textSize = if (unit) size * M.TRIP_UNIT_SCALE else size
            w += navTripPaint.measureText(run)
        }
        return w
    }

    private fun drawRich(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float) {
        var at = x
        for ((run, unit) in richRuns(text)) {
            navTripPaint.textSize = if (unit) size * M.TRIP_UNIT_SCALE else size
            canvas.drawText(run, at, baseline, navTripPaint)
            at += navTripPaint.measureText(run)
        }
    }

    /**
     * Turn hidden: the trip as a block (owner, 2026-10-24): distance over time, the flag on the
     * left centred on both lines, the whole block centred in the box (left of the arcs on AR).
     */
    private fun drawTripBlock(canvas: Canvas, tbt: M.Tbt, arcs: Boolean, wideArcs: Boolean) {
        val lines = listOf(tbt.tripDistance, tbt.tripTime).filter { it.isNotEmpty() }
        if (lines.isEmpty()) return
        // The trip line's own size and baseline; the first line sits above it.
        val (base, lineSize) = if (tbt.style == M.TbtStyle.AR) M.AR_TRIP_BASELINE to M.AR_TRIP_SIZE else M.tripPlacement(tbt.style)
        val wanted = lineSize + M.TRIP_BLOCK_EXTRA_SIZE
        val baselines = if (lines.size == 2) listOf(base - wanted * M.TRIP_BLOCK_LINE_STEP, base) else listOf(base)
        fun rightAt(b: Float, size: Float) = if (arcs) M.columnRightAt(b, size, wideArcs) else M.BOX_RIGHT
        var size = wanted
        var flag = wanted * M.TRIP_BLOCK_FLAG_SCALE
        fun width() = flag + M.TRIP_BLOCK_GAP + lines.maxOf { richWidth(it, size) }
        val right = baselines.minOf { rightAt(it, size) }
        val room = right - M.TRIP_MIN_LEFT
        if (width() > room) {
            val k = room / width()
            size *= k
            flag *= k
        }
        val left = M.columnLeft(width(), right)
        // Flag centred on the block: from the first line's cap height to the last baseline.
        val top = baselines.first() - size * 0.72f
        val mid = (top + baselines.last()) / 2
        drawFlag(canvas, left, mid + flag / 2, flag)
        lines.forEachIndexed { i, line -> drawRich(canvas, line, left + flag + M.TRIP_BLOCK_GAP, baselines[i], size) }
    }

    /** Destination flag in a [size] square standing on [baseline]: a pole and a pennant. */
    private fun drawFlag(
        canvas: Canvas,
        left: Float,
        baseline: Float,
        size: Float,
        pole: Paint = flagPole,
        outline: Paint = flagOutline,
        checks: Paint = flagFill
    ) {
        val s = size / 24f
        canvas.save()
        canvas.translate(left, baseline - size)
        canvas.scale(s, s)
        canvas.drawLine(5f, 3f, 5f, 23f, pole)
        canvas.drawRect(FLAG_L, FLAG_T, FLAG_R, FLAG_B, outline)
        canvas.drawPath(flagChecks, checks)
        canvas.restore()
    }

    // The map's flag (owner, 2026-10-25): in the route's own blue, so it reads as part of the road
    // and not as the white trip flag on the left; thinner pole and outline, it is small.
    private val arFlagPole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = AR_FLAG_POLE
        strokeCap = Paint.Cap.ROUND
    }
    private val arFlagOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AR_FLAG_OUTLINE
    }
    private val arFlagChecks = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

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
    private val flagOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(217, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
    }
    // Pennant in the 24 box (owner): a flat-edged rectangle, chequered like a finish flag
    // (4 x 3 squares, every other one lit; the outline keeps the dark ones reading as flag).
    private val flagChecks = Path().apply {
        val cw = (FLAG_R - FLAG_L) / 4f
        val ch = (FLAG_B - FLAG_T) / 3f
        for (r in 0 until 3) for (c in 0 until 4) {
            if ((r + c) % 2 == 0) addRect(FLAG_L + c * cw, FLAG_T + r * ch, FLAG_L + (c + 1) * cw, FLAG_T + (r + 1) * ch, Path.Direction.CW)
        }
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
        // Hidden (further than the owner's "show from"): just the trip line, where it sits without lanes.
        if (tbt.turnHidden) {
            drawTripBlock(canvas, tbt, arcs, wideArcs)
            return
        }
        val lanes = tbt.lanes.isNotEmpty()
        drawArManeuver(canvas, tbt, arcs, wideArcs, lanes)
        if (tbt.trip.isNotEmpty()) {
            // Destination flag before the trip line, as in the Navegação layout.
            val baseline = if (lanes) M.AR_LANES_TRIP_BASELINE else M.AR_TRIP_BASELINE
            val wanted = if (lanes) M.AR_LANES_TRIP_SIZE else M.AR_TRIP_SIZE
            val right = if (arcs) M.columnRightAt(baseline, wanted, wideArcs) else M.BOX_RIGHT
            drawTripLine(canvas, tbt.trip, baseline, wanted, right)
        }
    }

    /** The AR column's maneuver: street (or exit) line, icon + distance, lanes. */
    private fun drawArManeuver(canvas: Canvas, tbt: M.Tbt, arcs: Boolean, wideArcs: Boolean, lanes: Boolean) {
        // Still far: street and distance smaller, the turn isn't shown yet either.
        val far = HudArArrow.isFar(tbt.arMeters)
        val scale = if (far) M.AR_FAR_TEXT_SCALE else 1f
        val streetY = if (lanes) M.AR_LANES_STREET_BASELINE else M.AR_STREET_BASELINE
        streetPaint.textSize = scale * if (lanes) M.AR_LANES_STREET_SIZE else M.AR_STREET_SIZE
        val exit = M.roundaboutExitCountdown(tbt.exitNumber, tbt.rbProgress)?.let { "${M.exitLabel(it)} saída" }
        // Roundabout (owner, option C): the exit, at full size, until the car is on the ring;
        // then the street it leads to. Far away no street name (owner, 2026-10-24); the exit stays.
        val streetLine = if (exit != null && !(tbt.onRoundabout && tbt.street.isNotEmpty())) exit else if (far) "" else tbt.street
        // Clear of the arcs: centred while it fits, else moved into the free left edge (M.columnLeft).
        fun rightAt(baseline: Float, size: Float) = if (arcs) M.columnRightAt(baseline, size, wideArcs) else M.BOX_RIGHT
        if (streetLine.isNotEmpty()) {
            val right = rightAt(streetY, streetPaint.textSize)
            val text = android.text.TextUtils.ellipsize(streetLine, streetPaint, right - M.TRIP_MIN_LEFT, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, M.columnLeft(streetPaint.measureText(text), right), streetY, streetPaint)
        }
        if (lanes) setTextSizes(scale * M.AR_LANES_DISTANCE_VALUE_SIZE, scale * M.AR_LANES_DISTANCE_UNIT_SIZE)
        else setTextSizes(scale * M.AR_DISTANCE_VALUE_SIZE, scale * M.AR_DISTANCE_UNIT_SIZE)
        // On the ring the street line is the road, so the countdown sits with the icon, after
        // the distance ("40 m  2ª"), and drops out at 1. The hole of this small icon cannot hold it.
        val exitMark = if (tbt.onRoundabout) {
            M.roundaboutExitCountdown(tbt.exitNumber, tbt.rbProgress)?.let { M.exitLabel(it) }
        } else null
        val distanceY = when {
            lanes -> M.AR_LANES_DISTANCE_BASELINE
            far -> M.AR_DISTANCE_BASELINE - M.AR_FAR_DISTANCE_RAISE
            else -> M.AR_DISTANCE_BASELINE
        }
        // The next turn as an icon before the distance (owner), the pair centred together;
        // under 200 m the big "110 m" ran into the arcs (owner, 2026-10-24): it moves into the
        // free left edge first, and only shrinks when it still does not fit.
        var width = 0f
        var exitWidth = 0f
        var exitExtra = 0f
        var iconSize = 0f
        var iconW = 0f
        fun measure() {
            width = distanceWidth(tbt)
            exitWidth = exitMark?.let { unitPaint.measureText(it) } ?: 0f
            exitExtra = if (exitMark != null && width > 0f) M.EXIT_GAP + exitWidth else exitWidth
            iconSize = valuePaint.textSize * M.AR_DISTANCE_ICON_SCALE
            iconW = if (tbt.icon.isNotEmpty() && width > 0f) iconSize + M.AR_DISTANCE_ICON_GAP else 0f
        }
        measure()
        val right = rightAt(distanceY, valuePaint.textSize)
        val room = right - M.TRIP_MIN_LEFT
        if (iconW + width + exitExtra > room) {
            val k = room / (iconW + width + exitExtra)
            setTextSizes(valuePaint.textSize * k, unitPaint.textSize * k)
            measure()
        }
        val x0 = M.columnLeft(iconW + width + exitExtra, right)
        if (iconW > 0f) {
            // Centred on the digits' height (~0.72 of the text size).
            val digitsMid = distanceY - valuePaint.textSize * 0.36f
            drawTurn(canvas, tbt, x0, digitsMid - iconSize / 2, iconSize)
        }
        drawDistance(canvas, tbt, x0 + iconW, distanceY)
        if (exitMark != null) {
            val x = x0 + iconW + width + if (width > 0f) M.EXIT_GAP else 0f
            canvas.drawText(exitMark, x, distanceY, unitPaint)
        }
        if (lanes) drawLanes(canvas, tbt.lanes, M.AR_LANES_TOP)
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
            val deg = M.arcTickDeg(i)
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
            M.arcPowerTicks(percent), if (percent >= 0) driveColor else regenColor, POWER_OFF, wide
        )
    }

    /** Inner arc: thin, short red ticks over 0..7000 rpm. Hidden with the engine off. */
    private fun drawRpmArc(canvas: Canvas, tenths: Int, wide: Boolean) {
        // 7 segments of a line just inside the power ticks (owner, 2026-10-26), lit from the left end.
        val cx = if (wide) M.ARC_WIDE_CX else M.ARC_CX
        val cy = if (wide) M.ARC_WIDE_CY else M.ARC_CY
        val r = M.arcRadius(M.ARC_RPM_R, wide)
        rpmOval.set(cx - r * M.ARC_X_SCALE, cy - r, cx + r * M.ARC_X_SCALE, cy + r)
        arcPaint.strokeWidth = M.ARC_RPM_W
        arcPaint.color = rpmOffColor
        for ((start, sweep) in M.rpmSegments(wide)) canvas.drawArc(rpmOval, start, sweep, false, arcPaint)
        arcPaint.color = rpmColor
        for ((start, sweep) in M.rpmSegments(wide, M.rpmArcLit(tenths))) canvas.drawArc(rpmOval, start, sweep, false, arcPaint)
    }
    private val rpmOval = android.graphics.RectF()

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
        // The route's colours (Cores tab): light near, deep far; the default is the original blue.
        return M.mixColor(routeNear, routeFar, HudArArrow.rowGradient(y))
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
            route != null -> HudArArrow.mapScene(tbt.streets, route, tbt.arDestination, horizon, tbt.mapTilt, tbt.mapZoom)
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
            arPaint.alpha = ((if (map) MAP_ROAD_ALPHA else 140) * shape.grayAlpha * streetGain).toInt().coerceIn(0, 255)
            arPath.reset()
            for (ribbon in shape.gray) addRibbon(ribbon, top)
            if (map && tbt.mapEdges) {
                // Edge lines (owner option): stroke every piece, then cut the roads' insides back
                // out of the layer (nothing else is in it yet). What stays is the outline of all
                // the roads merged: no lines across the pieces or where two streets meet.
                arPaint.style = Paint.Style.STROKE
                arPaint.strokeWidth = MAP_EDGE_STROKE
                arPaint.strokeJoin = Paint.Join.ROUND
                arPaint.alpha = (MAP_EDGE_ALPHA * shape.grayAlpha * streetGain).toInt().coerceIn(0, 255)
                canvas.drawPath(arPath, arPaint)
                arPaint.style = Paint.Style.FILL
                canvas.drawPath(arPath, edgeCutPaint)
            } else {
                canvas.drawPath(arPath, arPaint)
            }
        }
        arPaint.shader = if (map) mapShader(top, blue = true) else shaderFor(blueShaders, top)
        arPaint.alpha = (255 * routeOpacity).toInt().coerceIn(0, 255)
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
        // Top fade is in the shader. Sides fade here, the blue route included, so the HUD
        // stays clear toward the edges. The map fades harder than the plain arrow.
        fadeSides(canvas, left, right, top, HudArArrow.CX, edge = if (map) 0 else 60, keep = if (map) 0.16f else 0.32f)
        canvas.restore() // layer
        canvas.restore() // transform + clip
        // The flag only in the last stretch (owner, 2026-10-25): further out it just competes.
        if (meters <= M.DEST_FLAG_SHOW_M) shape.flag?.let { drawArFlag(canvas, it, cx, sx, sy, opacity) }
    }

    private val mapPendingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 190, 195, 205)
        typeface = lightFace
        textAlign = Paint.Align.CENTER
        textSize = 14f
    }

    private val mapBarTrack = fillPaint(Color.argb(60, 190, 195, 205))
    private val mapBarFill = fillPaint(Color.argb(150, 190, 195, 205))

    /**
     * Map on, area not loaded: a steady grey note on the right with a bar of how much of it is
     * in (owner, 2026-10-24); while nothing has arrived a short segment sweeps along the bar.
     * The arrows carry on meanwhile.
     */
    private fun drawMapPending(canvas: Canvas, progress: Float, nowMs: Long) {
        canvas.drawText("carregando mapa", M.MAP_PENDING_X, M.MAP_PENDING_Y, mapPendingPaint)
        val left = M.MAP_PENDING_X - M.MAP_PENDING_BAR_W / 2
        val y = M.MAP_PENDING_BAR_Y
        canvas.drawRect(left, y, left + M.MAP_PENDING_BAR_W, y + 2f, mapBarTrack)
        if (progress > 0f) {
            canvas.drawRect(left, y, left + M.MAP_PENDING_BAR_W * progress.coerceIn(0f, 1f), y + 2f, mapBarFill)
        } else {
            val seg = M.MAP_PENDING_BAR_W / 4
            val x = left + (M.MAP_PENDING_BAR_W - seg) * ((nowMs % 2_000L) / 2_000f)
            canvas.drawRect(x, y, x + seg, y + 2f, mapBarFill)
        }
    }

    // ---- map view fades (owner): far = more see-through; grey roads also fade to the sides; the
    // blue route only a little, so it stays the clearest thing on the map ----

    private val mapGrayShaders = HashMap<Float, android.graphics.Shader>()
    private val mapBlueShaders = HashMap<Float, android.graphics.Shader>()

    private fun mapShader(top: Float, blue: Boolean): android.graphics.Shader =
        (if (blue) mapBlueShaders else mapGrayShaders).getOrPut(top) {
            val span = HudArArrow.BOTTOM - top
            // The bottom fade is the same for streets and route, over the last few px (owner,
            // 2026-10-26: the grey reached the bottom while the blue faded, which looked odd).
            val ys = floatArrayOf(top, top + span * 0.32f, top + span * 0.58f, HudArArrow.BOTTOM - MAP_BOTTOM_FADE_PX, HudArArrow.BOTTOM)
            // More solid streets than the default (owner's opacity): once their paint is at full,
            // the gradient's own stops rise too; 0 at the top stays 0, so it still fades out.
            val lift = if (blue) 1f else maxOf(1f, MAP_ROAD_ALPHA * streetGain / 255f)
            val alphas = if (blue) floatArrayOf(0.08f, 0.4f, 0.85f, 1f, MAP_BOTTOM_FADE_TO)
            else floatArrayOf(0f, 0.12f, 0.45f, 0.85f, 0.85f * MAP_BOTTOM_FADE_TO).map { (it * lift).coerceAtMost(1f) }.toFloatArray()
            val colors = IntArray(ys.size) { i ->
                val c = if (blue) arRgb(ys[i]) else streetColor
                Color.argb((alphas[i] * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
            }
            android.graphics.LinearGradient(
                0f, top, 0f, HudArArrow.BOTTOM, colors,
                ys.map { (it - top) / span }.toFloatArray(), android.graphics.Shader.TileMode.CLAMP
            )
        }

    private val sideMaskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    /** Clears the inside of the grey roads in the layer, leaving their stroked edges. */
    private val edgeCutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    /** Fades what is already in the layer towards [left] / [right]. Fully kept within [keep] of [centre]. */
    private fun fadeSides(canvas: Canvas, left: Float, right: Float, top: Float, centre: Float, edge: Int, keep: Float) {
        val c = ((centre - left) / (right - left)).coerceIn(0.05f, 0.95f)
        sideMaskPaint.shader = android.graphics.LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(Color.argb(edge, 0, 0, 0), Color.argb(255, 0, 0, 0), Color.argb(255, 0, 0, 0), Color.argb(edge, 0, 0, 0)),
            floatArrayOf(0f, (c - keep).coerceIn(0.02f, 0.48f), (c + keep).coerceIn(0.52f, 0.98f), 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(left, top, right, HudArArrow.BOTTOM, sideMaskPaint)
    }

    /** Vertical gradients per band top (the arrow's bands, the map's band up to the horizon). */
    private val blueShaders = HashMap<Float, android.graphics.Shader>()
    private val grayShaders = HashMap<Float, android.graphics.Shader>()

    private fun shaderFor(cache: HashMap<Float, android.graphics.Shader>, top: Float): android.graphics.Shader =
        cache.getOrPut(top) {
            if (cache === blueShaders) rowShader(top) { y -> arRgb(y) } else rowShader(top) { streetColor }
        }

    /**
     * The destination flag standing on the road, drawn upright in screen space (the RIGHT
     * placement's stretch would make it tall and thin) and as see-through as the arrow.
     */
    private fun drawArFlag(canvas: Canvas, flag: HudArArrow.Flag, cx: Float, sx: Float, sy: Float, opacity: Float) {
        val raw = cx + (flag.x - HudArArrow.CX) * sx
        val y = HudArArrow.BOTTOM - (HudArArrow.BOTTOM - flag.y) * sy
        if (y <= M.CHEVRON_CY || y > HudArArrow.BOTTOM) return
        // Smaller and see-through (owner): it marks the spot, it should not cover the road.
        val size = (flag.height * kotlin.math.sqrt(sx * sy) * AR_FLAG_SCALE).coerceIn(AR_FLAG_MIN, minOf(AR_FLAG_MAX, y - 40f))
        // The pennant flies away from the road: keep that side of it on the panel.
        val x = if (flag.pennant < 0) raw.coerceAtLeast(size * 0.7f) else raw.coerceAtMost(480f - size * 0.7f)
        canvas.saveLayerAlpha(x - size, y - size - 2f, x + size, y + 2f, (255 * routeOpacity * (AR_FLAG_ALPHA_MIN + AR_FLAG_ALPHA_SPAN * opacity)).toInt().coerceIn(0, 255))
        canvas.save()
        if (flag.pennant < 0) {
            canvas.translate(x, 0f)
            canvas.scale(-1f, 1f)
            canvas.translate(-x, 0f)
        }
        // drawFlag's pole runs at x 5, y 3..23 of its 24 box: stand it on (x, y). Route blue.
        val blue = arRgb(y)
        arFlagPole.color = blue
        arFlagOutline.color = blue
        arFlagChecks.color = blue
        drawFlag(canvas, x - 5f * size / 24f, y + size / 24f, size, arFlagPole, arFlagOutline, arFlagChecks)
        canvas.restore()
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
        M.roundaboutExitCountdown(tbt.exitNumber, tbt.rbProgress)?.let { exit ->
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
        val exit = M.roundaboutExitCountdown(tbt.exitNumber, tbt.rbProgress)?.let { M.exitLabel(it) }
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
        // Last section: the destination flag (as on the trip line) instead of the round glyph.
        if (icon == "DESTINATION") {
            drawFlag(canvas, left, top + size, size)
            return top + size
        }
        val exitAngle = tbt.exitAngle
        if (tbt.dynamicRoundabout && exitAngle != null) {
            drawRoundabout(
                canvas, left, top, size,
                M.roundaboutShownAngle(exitAngle, tbt.rbProgress),
                tbt.roundaboutClockwise, tbt.exitNumber
            )
            return top + (M.RB_ENTRY_BOTTOM + M.RB_STROKE / 2) / 24f * size
        }
        // Google style: its Material Symbols icon (the dynamic ring and the flag stay ours).
        tbt.googleIcon?.let { HudTbtIcons.googlePath(it, size) }?.let { g ->
            canvas.save()
            canvas.translate(left, top)
            canvas.drawPath(g, fill)
            canvas.restore()
            g.computeBounds(arrowBounds, true)
            return top + arrowBounds.bottom
        }
        if (icon == "SLIGHT_RIGHT" || icon == "SLIGHT_LEFT") {
            // The straight arrow turned 45 deg to the side (see M.hudIcon).
            val straight = HudTbtIcons.turnPath("STRAIGHT", size)
            if (straight != null) {
                slightMatrix.setRotate(if (icon == "SLIGHT_RIGHT") 45f else -45f, size / 2, size / 2)
                slightPath.set(straight)
                slightPath.transform(slightMatrix)
                canvas.save()
                canvas.translate(left, top)
                canvas.drawPath(slightPath, fill)
                canvas.restore()
                slightPath.computeBounds(arrowBounds, true)
                return top + arrowBounds.bottom
            }
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
    private val slightMatrix = android.graphics.Matrix()
    private val slightPath = Path()

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
    private val nativeFace = Typeface.create("sans-serif", Typeface.NORMAL)
    private val nativeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val scenePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * A "what you'd see through the windshield" picture of [config] in [scenario]: a road at
     * dusk, the car's own HUD (speed, icons, lane keeping), and the Impulse overlay drawn by this
     * renderer. Only the band that reaches the optics (y 40..200) is shown. [overlayOnly]: just
     * the overlay on black, as the HUD draws it (the settings' map examples, owner 2026-10-28:
     * behind the road and the car's speed the map's options couldn't be told apart).
     */
    fun preview(
        config: M.Config,
        scenario: PreviewScenario = previewScenarios(config.layout).first(),
        width: Int = 480,
        height: Int = 160,
        overlayOnly: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / 480f, height / 160f)
        canvas.translate(0f, -40f)
        if (overlayOnly) {
            canvas.drawColor(Color.BLACK)
        } else {
            drawScene(canvas)
            drawNativeHud(canvas, scenario.speedKmh)
        }
        if (config.active) {
            val frame = M.frameFor(
                config, scenario.route, scenario.powerPercent, scenario.rpmTenths,
                wideArcs = M.wideArcs(scenario.speedKmh.toDouble(), wasWide = false),
                streets = if (config.mapStreets) previewStreets(scenario) else emptyList(),
                speedKmh = scenario.speedKmh.toDouble()
            )
            // A still of the approach chevrons (the sharp-left preview), mid-pulse.
            drawWithChevrons(canvas, frame, 0L, PREVIEW_PULSE_MS)
        }
        return bitmap
    }

    /**
     * "Mapa completo" in the preview: the road ahead and a cross street at the turn, so the map
     * (and its colours) show without GPS. Only while the turn is further than the chevrons' range:
     * on these made-up streets the turn doesn't read as "the next street on that side", which
     * would hide the chevrons the near example is there to show. None on a roundabout.
     */
    private fun previewStreets(scenario: PreviewScenario): List<br.com.redesurftank.havalshisuku.map.LocalRoad> {
        val dirs = scenario.route ?: return emptyList()
        if (dirs.turn?.startsWith("ROUNDABOUT") == true) return emptyList()
        if (scenario === AR_MAP) {
            // A small grid: streets either side before the turn, the turn's street, one beyond.
            val road = { pts: FloatArray, w: Float -> br.com.redesurftank.havalshisuku.map.LocalRoad(pts, w) }
            return listOf(
                road(floatArrayOf(0f, -40f, 0f, 120f, 0f, 700f), 1f),
                road(floatArrayOf(-200f, 25f, 0f, 25f), 0.6f),
                road(floatArrayOf(-200f, 70f, 0f, 70f, 300f, 70f), 0.8f),
                road(floatArrayOf(0f, 120f, 200f, 170f), 0.6f),
                road(floatArrayOf(-300f, 200f, 0f, 200f, 300f, 200f), 0.8f),
                road(floatArrayOf(-60f, 25f, -60f, 400f), 0.6f)
            )
        }
        val d = dirs.distanceM?.toFloat()?.takeIf { it > M.CHEVRON_SHOW_M } ?: return emptyList()
        val ahead = br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(0f, -40f, 0f, 120f, 0f, 700f), 1f)
        if (d > 600f) return listOf(ahead)
        return listOf(ahead, br.com.redesurftank.havalshisuku.map.LocalRoad(floatArrayOf(-300f, d, 0f, d, 300f, d), 0.8f))
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
        nativePaint.typeface = nativeFace
        nativePaint.textSize = NATIVE_DIGIT_SIZE
        // The car draws each digit centred in a fixed-width cell (so a "1" stands apart), the
        // number centred where it sits on the car, km/h right after it. Measured on a photo of
        // 119 km/h through the old arcs' geometry (owner, 2026-10-26): 46 px cells centred at 227,
        // ~39 px tall digits on baseline 131, a regular (not light) weight, km/h from x 297.
        nativePaint.textAlign = Paint.Align.CENTER
        nativePaint.color = Color.argb(225, 255, 255, 255)
        val speed = speedKmh.toString()
        val cx = if (speed.length >= 3) NATIVE_SPEED_CX_3DIGITS else NATIVE_SPEED_CX
        val left = cx - speed.length * NATIVE_DIGIT_CELL / 2
        speed.forEachIndexed { i, d ->
            canvas.drawText(d.toString(), left + (i + 0.5f) * NATIVE_DIGIT_CELL, NATIVE_SPEED_BASELINE, nativePaint)
        }
        nativePaint.textSize = NATIVE_UNIT_SIZE
        nativePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("km/h", left + speed.length * NATIVE_DIGIT_CELL + 1f, NATIVE_SPEED_BASELINE, nativePaint)
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

/**
 * AR: a right turn 70 m on, among a few streets (see [HudOverlayRenderer.previewStreets]): the
 * map's own example for the settings' pictures (tilt, zoom, edges...), not in the cycle. Near,
 * so the map is big and nearly opaque (owner, 2026-10-28: at 250 and 140 m the streets were
 * too faint and small to tell the options apart).
 */
internal val AR_MAP = PreviewScenario(52, arTurn("TURN_RIGHT", 70, "70 m", "R. Haddock Lobo"), 15, null)

/** Three moments per layout; the Telas previews cycle through them. */
/** What a settings change affects, to show it on the example where it is visible. */
enum class PreviewFocus { ARROW, CHEVRONS, FAR, GAUGES, MAP }

/** The example that shows [focus] for [layout] (the settings' (i) pictures); [PreviewFocus.MAP] has its own. */
fun previewScenarioFor(layout: M.Layout, focus: PreviewFocus?): PreviewScenario =
    if (focus == PreviewFocus.MAP && layout == M.Layout.AR) AR_MAP
    else previewScenarios(layout).let { it[(focus?.let { f -> previewFocusIndex(layout, f) } ?: 0).coerceIn(0, it.size - 1)] }

/** Index into [previewScenarios] of the example that shows [focus] for [layout]. */
fun previewFocusIndex(layout: M.Layout, focus: PreviewFocus): Int {
    val list = previewScenarios(layout)
    return when (focus) {
        // The sharp left at 10 m is the one with chevrons; the bending turn shows the arrow.
        PreviewFocus.CHEVRONS -> if (layout == M.Layout.AR) list.indexOf(AR_AT_TURN) else 0
        PreviewFocus.ARROW -> if (layout == M.Layout.AR) list.indexOf(AR_BENDING) else 0
        // The cycle's map with the most road: the far turn.
        PreviewFocus.MAP -> if (layout == M.Layout.AR) list.indexOf(AR_FAR) else 0
        // The highway exit 2.4 km away is the far one (small arrow).
        PreviewFocus.FAR -> list.indexOf(HIGHWAY_EXIT).takeIf { it >= 0 } ?: list.indexOf(AR_FAR).takeIf { it >= 0 } ?: 0
        // One with the engine running, so both power and RPM show.
        PreviewFocus.GAUGES -> list.indexOfFirst { it.rpmTenths != null }.takeIf { it >= 0 } ?: 0
    }.coerceAtLeast(0)
}

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
