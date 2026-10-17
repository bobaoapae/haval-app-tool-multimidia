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
        const val NATIVE_SPEED_CX = M.ARC_CX
        const val NATIVE_SPEED_BASELINE = 141f
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

    /** Clears [canvas] and draws [frame] (nothing when null). Canvas units = HUD pixels. */
    fun draw(canvas: Canvas, frame: M.Frame?) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawContent(canvas, frame)
    }

    private fun drawContent(canvas: Canvas, frame: M.Frame?) {
        val f = frame ?: return
        f.tbt?.let { tbt ->
            when (tbt.style) {
                M.TbtStyle.HERO -> drawHero(canvas, tbt)
                M.TbtStyle.COMPACT -> drawRow(canvas, tbt, M.COMPACT_TOP).also { drawLanes(canvas, tbt.lanes, M.LANES_TOP) }
                M.TbtStyle.FAR -> drawRow(canvas, tbt, M.FAR_TOP)
                M.TbtStyle.AR -> {
                    drawAr(canvas, tbt)
                    drawArColumn(canvas, tbt) // draws its own trip line
                }
            }
            if (tbt.style != M.TbtStyle.AR) drawNavText(canvas, tbt)
        }
        if (f.arcs) {
            f.powerPercent?.let { drawPowerArc(canvas, it) }
            f.rpmTenths?.let { drawRpmArc(canvas, it) }
        } else {
            f.powerPercent?.let { drawPowerBar(canvas, it) }
            f.rpmTenths?.let { drawRpmBar(canvas, it) }
        }
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
    private fun drawArColumn(canvas: Canvas, tbt: M.Tbt) {
        val lanes = tbt.lanes.isNotEmpty()
        val streetY = if (lanes) M.AR_LANES_STREET_BASELINE else M.AR_STREET_BASELINE
        streetPaint.textSize = if (lanes) M.AR_LANES_STREET_SIZE else M.AR_STREET_SIZE
        if (tbt.street.isNotEmpty()) {
            val maxW = M.BOX_RIGHT - M.BOX_LEFT
            val text = android.text.TextUtils.ellipsize(tbt.street, streetPaint, maxW, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, M.BOX_CENTER_X - streetPaint.measureText(text) / 2, streetY, streetPaint)
        }
        if (lanes) setTextSizes(M.AR_LANES_DISTANCE_VALUE_SIZE, M.AR_LANES_DISTANCE_UNIT_SIZE)
        else setTextSizes(M.AR_DISTANCE_VALUE_SIZE, M.AR_DISTANCE_UNIT_SIZE)
        val width = distanceWidth(tbt)
        drawDistance(canvas, tbt, M.BOX_CENTER_X - width / 2, if (lanes) M.AR_LANES_DISTANCE_BASELINE else M.AR_DISTANCE_BASELINE)
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
    private fun drawTicks(canvas: Canvas, rIn: Float, rOut: Float, width: Float, lit: Int, litColor: Int, offColor: Int) {
        var nLit = 0
        var nOff = 0
        for (i in 0 until M.ARC_TICKS) {
            val rad = Math.toRadians(M.arcTickDeg(i).toDouble())
            val c = kotlin.math.cos(rad).toFloat()
            val sn = kotlin.math.sin(rad).toFloat()
            val buf = if (i < lit) tickLit else tickOff
            var k = if (i < lit) nLit else nOff
            buf[k++] = M.ARC_CX + c * rIn
            buf[k++] = M.ARC_CY + sn * rIn
            buf[k++] = M.ARC_CX + c * rOut
            buf[k++] = M.ARC_CY + sn * rOut
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
    private fun drawPowerArc(canvas: Canvas, percent: Int) {
        drawTicks(
            canvas, M.ARC_POWER_R_IN, M.ARC_POWER_R_OUT, M.ARC_POWER_TICK_W,
            M.arcPowerTicks(percent), if (percent >= 0) DRIVE else REGEN, POWER_OFF
        )
    }

    /** Inner arc: thin, short red ticks over 0..7000 rpm. Hidden with the engine off. */
    private fun drawRpmArc(canvas: Canvas, tenths: Int) {
        drawTicks(canvas, M.ARC_RPM_R_IN, M.ARC_RPM_R_OUT, M.ARC_RPM_TICK_W, M.arcRpmTicks(tenths), RED, RPM_OFF)
    }

    // ---- AR arrow (Navegação AR) ----

    private val arPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arSeam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
    }
    private val arPath = Path()

    /** Deep blue at the base to light blue at the top, faded out towards the top edge. */
    /** Lighter blue at the base to deeper blue at the top (owner: "slightly darker on top"). */
    private fun arColor(y: Float, alpha: Float): Int {
        val t = HudArArrow.rowGradient(y)
        return Color.argb(
            (alpha * 255).toInt(),
            (120 + (25 - 120) * t).toInt(),
            (200 + (105 - 200) * t).toInt(),
            255
        )
    }

    /**
     * The 3D arrow ([HudArArrow]) in the middle of the HUD, see-through ([HudArArrow.OPACITY]),
     * then the distance in the left box. Each segment is a quad laid perpendicular to the
     * on-screen path, coloured by its row.
     */
    private fun drawAr(canvas: Canvas, tbt: M.Tbt) {
        val shape = HudArArrow.shape(tbt.arMeters ?: 0f, tbt.arAngle ?: 0f)
        val left = HudArArrow.CX - maxOf(HudArArrow.ROOM_LEFT, HudArArrow.BASE_W / 2) - 2f
        val right = HudArArrow.CX + maxOf(HudArArrow.ROOM_RIGHT, HudArArrow.BASE_W / 2) + 2f
        canvas.save()
        canvas.clipRect(left, HudArArrow.TOP, right, HudArArrow.BOTTOM)
        // Opaque into a layer, then composited once at OPACITY: drawing overlapping pieces
        // translucent would stack into visible stripes at the seams.
        canvas.saveLayerAlpha(left, HudArArrow.TOP, right, HudArArrow.BOTTOM, (HudArArrow.OPACITY * 255).toInt())

        // Stem: one trapezoid, straight sides, coloured by row through a vertical gradient.
        val top = shape.stemTop
        val halfTop = HudArArrow.stemWidthAt(top) / 2
        val halfBase = HudArArrow.BASE_W / 2
        arPath.reset()
        arPath.moveTo(HudArArrow.CX - halfBase, HudArArrow.BOTTOM)
        arPath.lineTo(HudArArrow.CX + halfBase, HudArArrow.BOTTOM)
        arPath.lineTo(HudArArrow.CX + halfTop, top)
        arPath.lineTo(HudArArrow.CX - halfTop, top)
        arPath.close()
        arPaint.color = Color.WHITE
        arPaint.shader = stemShader
        canvas.drawPath(arPath, arPaint)
        arPaint.shader = null

        // Arm: quads laid perpendicular to the on-screen path, faded in with the bend.
        val pts = shape.arm
        fun normal(i: Int): Pair<Float, Float> {
            val a = pts[maxOf(0, i - 1)]
            val b = pts[minOf(pts.size - 1, i + 1)]
            val len = kotlin.math.hypot(b.x - a.x, b.y - a.y).takeIf { it > 0f } ?: 1f
            return -(b.y - a.y) / len to (b.x - a.x) / len
        }
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val alpha = HudArArrow.rowAlpha(minOf(a.y, b.y)) * shape.armAlpha
            if (alpha <= 0f) continue
            val (nax, nay) = normal(i)
            val (nbx, nby) = normal(i + 1)
            arPath.reset()
            arPath.moveTo(a.x + nax * a.w / 2, a.y + nay * a.w / 2)
            arPath.lineTo(b.x + nbx * b.w / 2, b.y + nby * b.w / 2)
            arPath.lineTo(b.x - nbx * b.w / 2, b.y - nby * b.w / 2)
            arPath.lineTo(a.x - nax * a.w / 2, a.y - nay * a.w / 2)
            arPath.close()
            val color = arColor((a.y + b.y) / 2, alpha)
            arPaint.color = color
            arSeam.color = color
            canvas.drawPath(arPath, arPaint)
            canvas.drawPath(arPath, arSeam) // hide the seams between quads
        }
        shape.head?.let { h ->
            val alpha = HudArArrow.rowAlpha(pts.last().y) * shape.armAlpha
            if (alpha > 0f) {
                arPath.reset()
                arPath.moveTo(h[0], h[1])
                arPath.lineTo(h[2], h[3])
                arPath.lineTo(h[4], h[5])
                arPath.close()
                arPaint.color = arColor(pts.last().y, alpha)
                canvas.drawPath(arPath, arPaint)
            }
        }
        canvas.restore() // layer
        canvas.restore() // clip
    }

    /**
     * Stem colour by row, same as [arColor]: lighter at the base, deeper at the top. Fades out
     * over the bottom of the band (so the optics' edge doesn't crop it hard) and over the last
     * few rows at the top (so the clip there doesn't show).
     */
    private val stemShader = run {
        val span = HudArArrow.BOTTOM - HudArArrow.TOP
        LinearGradient(
            0f, HudArArrow.BOTTOM, 0f, HudArArrow.TOP,
            intArrayOf(
                Color.argb(0, 120, 200, 255),
                Color.argb(255, 108, 188, 255),
                Color.argb(255, 32, 112, 255),
                Color.argb(0, 25, 105, 255)
            ),
            floatArrayOf(
                0f,
                (HudArArrow.BOTTOM - HudArArrow.FADE_BOTTOM_FROM) / span,
                (HudArArrow.BOTTOM - HudArArrow.FADE_TOP_TO) / span,
                1f
            ),
            Shader.TileMode.CLAMP
        )
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
            drawContent(canvas, M.frameFor(config, scenario.route, scenario.powerPercent, scenario.rpmTenths))
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
        // Speed centred where it sits on the car (see HudOverlayModel.ARC_CX), km/h after it.
        nativePaint.textAlign = Paint.Align.CENTER
        nativePaint.color = Color.argb(225, 255, 255, 255)
        val speed = speedKmh.toString()
        canvas.drawText(speed, NATIVE_SPEED_CX, NATIVE_SPEED_BASELINE, nativePaint)
        val half = nativePaint.measureText(speed) / 2
        nativePaint.textSize = 14f
        nativePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("km/h", NATIVE_SPEED_CX + half + 4f, NATIVE_SPEED_BASELINE, nativePaint)
        // Icons under the speed, shifted down with it (as on the car).
        canvas.drawCircle(214f, 170f, 9f, nativeStroke)
        canvas.drawCircle(244f, 170f, 9f, nativeStroke)
        nativePaint.color = Color.rgb(255, 122, 61)
        canvas.drawCircle(276f, 170f, 10f, nativePaint)
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
