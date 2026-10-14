package br.com.redesurftank.havalshisuku.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import br.com.redesurftank.havalshisuku.hud.HudOverlayModel as M

/**
 * Draws one [HudOverlayModel.Frame] on a transparent canvas. Transparent pixels let the native
 * HUD show through (proven on the car), so nothing outside the free box is ever painted.
 */
class HudOverlayView(context: Context) : View(context) {
    private companion object {
        val DRIVE = Color.rgb(63, 169, 255)
        val REGEN = Color.rgb(80, 230, 120)
        val RED = Color.rgb(255, 64, 64)
    }

    private var frame: M.Frame? = null

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
    private val dotOff = dotPaint(Color.argb(61, 255, 255, 255))
    private val dotDrive = dotPaint(DRIVE)
    private val dotRegen = dotPaint(REGEN)
    private val segOn = dotPaint(Color.WHITE)
    private val segRed = dotPaint(RED)
    private val segRedOff = dotPaint(Color.argb(77, 255, 64, 64))
    private val zeroTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 255, 255, 255)
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = M.BAR_LABEL_SIZE
        textAlign = Paint.Align.CENTER
    }
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

    private fun dotPaint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c
        style = Paint.Style.FILL
    }

    /** Null clears the overlay. Redraws only when the frame actually changed. */
    fun show(next: M.Frame?) {
        if (next == frame) return
        frame = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val f = frame ?: return
        f.tbt?.let { if (it.lanes.isEmpty()) drawHero(canvas, it) else drawCompact(canvas, it) }
        drawPowerBar(canvas, f.powerPercent)
        f.rpmTenths?.let { drawRpmBar(canvas, it) }
    }

    // ---- turn-by-turn ----

    /** No lanes: big arrow centred, small distance centred under it. */
    private fun drawHero(canvas: Canvas, tbt: M.Tbt) {
        val left = M.BOX_CENTER_X - M.HERO_GLYPH / 2
        val bottom = drawTurn(canvas, tbt.icon, left, M.HERO_TOP, M.HERO_GLYPH)
        setTextSizes(M.HERO_VALUE_SIZE, M.HERO_UNIT_SIZE)
        val width = distanceWidth(tbt)
        drawDistance(canvas, tbt, M.BOX_CENTER_X - width / 2, bottom + M.HERO_TEXT_GAP)
    }

    /** Lanes: small arrow + "250 m" as one row, centred over the lane row. */
    private fun drawCompact(canvas: Canvas, tbt: M.Tbt) {
        setTextSizes(M.COMPACT_VALUE_SIZE, M.COMPACT_UNIT_SIZE)
        val textWidth = distanceWidth(tbt)
        val gap = if (textWidth > 0f) M.COMPACT_ROW_GAP else 0f
        val rowWidth = M.COMPACT_GLYPH + gap + textWidth
        val left = M.BOX_CENTER_X - rowWidth / 2
        val bottom = drawTurn(canvas, tbt.icon, left, M.COMPACT_TOP, M.COMPACT_GLYPH)
        // Baseline on the arrow's bottom edge.
        drawDistance(canvas, tbt, left + M.COMPACT_GLYPH + gap, bottom)
        drawLanes(canvas, tbt.lanes)
    }

    /** Draws the maneuver icon in a [size] square; returns the y of its visible bottom edge. */
    private fun drawTurn(canvas: Canvas, icon: String, left: Float, top: Float, size: Float): Float {
        if (icon.isEmpty()) return top + size
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

    /** Centre-zero at the HUD's left edge: regen dots left (green), drive dots right (blue). */
    private fun drawPowerBar(canvas: Canvas, percent: Int) {
        val mid = (M.POWER_LEFT + M.POWER_RIGHT) / 2
        val lit = M.powerDots(percent)
        for (i in 0 until M.POWER_DOTS_PER_SIDE) {
            val off = M.powerDotOffset(i)
            canvas.drawCircle(mid + off, M.BAR_Y, M.POWER_DOT_RADIUS, if (lit > 0 && i < lit) dotDrive else dotOff)
            canvas.drawCircle(mid - off, M.BAR_Y, M.POWER_DOT_RADIUS, if (lit < 0 && i < -lit) dotRegen else dotOff)
        }
        canvas.drawLine(mid, M.BAR_Y - M.POWER_ZERO_TICK, mid, M.BAR_Y + M.POWER_ZERO_TICK, zeroTick)
        labelPaint.color = when {
            percent > 0 -> DRIVE
            percent < 0 -> REGEN
            else -> Color.WHITE
        }
        canvas.drawText("$percent%", mid, M.BAR_LABEL_BASELINE, labelPaint)
    }

    /** At the HUD's right edge, one segment per 1000 rpm; the 5-6k segment is red. */
    private fun drawRpmBar(canvas: Canvas, tenths: Int) {
        val n = M.RPM_SEGMENTS
        val w = (M.RPM_RIGHT - M.RPM_LEFT - M.RPM_SEGMENT_GAP * (n - 1)) / n
        val h = M.RPM_SEGMENT_HEIGHT
        val lit = M.rpmSegments(tenths)
        for (i in 0 until n) {
            val red = M.rpmSegmentIsRed(i)
            val paint = when {
                i < lit -> if (red) segRed else segOn
                else -> if (red) segRedOff else dotOff
            }
            val x = M.RPM_LEFT + i * (w + M.RPM_SEGMENT_GAP)
            segRect.set(x, M.BAR_Y - h / 2, x + w, M.BAR_Y + h / 2)
            canvas.drawRoundRect(segRect, h / 2, h / 2, paint)
        }
        labelPaint.color = if (tenths * 100 > M.RPM_RED) RED else Color.WHITE
        canvas.drawText(M.rpmLabel(tenths), (M.RPM_LEFT + M.RPM_RIGHT) / 2, M.BAR_LABEL_BASELINE, labelPaint)
    }
}
