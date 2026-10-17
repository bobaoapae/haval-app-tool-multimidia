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
        val DRIVE = Color.rgb(63, 169, 255)
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
                M.TbtStyle.COMPACT -> drawRow(canvas, tbt, M.COMPACT_TOP).also { drawLanes(canvas, tbt.lanes) }
                M.TbtStyle.FAR -> drawRow(canvas, tbt, M.FAR_TOP)
            }
            if (tbt.trip.isNotEmpty()) canvas.drawText(tbt.trip, M.BOX_CENTER_X, M.TRIP_BASELINE, tripPaint)
        }
        f.powerPercent?.let { drawPowerBar(canvas, it) }
        f.rpmTenths?.let { drawRpmBar(canvas, it) }
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
            drawRoundabout(canvas, left, top, size, exitAngle, tbt.roundaboutClockwise)
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

    /** Ring with the entry at the bottom and an arrow at the exit [angle] degrees around it. */
    private fun drawRoundabout(canvas: Canvas, left: Float, top: Float, size: Float, angle: Int, clockwise: Boolean) {
        val scale = size / 24f
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawCircle(M.RB_CX, M.RB_CY, M.RB_R, ringOff)
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

    private fun drawLanes(canvas: Canvas, lanes: List<List<M.LaneArrow>>) {
        val cell = M.laneCellWidth(lanes.size)
        val left = M.BOX_CENTER_X - cell * lanes.size / 2
        val top = M.LANES_TOP
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
        nativePaint.textAlign = Paint.Align.RIGHT
        nativePaint.color = Color.argb(225, 255, 255, 255)
        canvas.drawText(speedKmh.toString(), 300f, 122f, nativePaint)
        nativePaint.textSize = 14f
        nativePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("km/h", 304f, 122f, nativePaint)
        canvas.drawCircle(214f, 150f, 9f, nativeStroke)
        canvas.drawCircle(244f, 150f, 9f, nativeStroke)
        nativePaint.color = Color.rgb(255, 122, 61)
        canvas.drawCircle(276f, 150f, 10f, nativePaint)
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
        active = true, distance = "2.4 km", distanceM = 2_400, turn = "OFF_RAMP_RIGHT",
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

/** Three moments per layout; the Telas previews cycle through them. */
fun previewScenarios(layout: M.Layout): List<PreviewScenario> = when (layout) {
    // The car's own HUD only: what changes is the speed.
    M.Layout.ORIGINAL -> listOf(
        PreviewScenario(42, null, 0, null),
        PreviewScenario(110, null, 0, null),
        PreviewScenario(0, null, 0, null)
    )
    M.Layout.NAVIGATION -> listOf(TOWN_TURN, HIGHWAY_EXIT, ROUNDABOUT_REGEN)
}
