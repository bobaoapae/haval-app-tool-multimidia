package br.com.redesurftank.havalshisuku.hud

import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry
import kotlin.math.roundToInt

/**
 * Plain-Kotlin half of the HUD overlay: what to draw and where, with no Android types, so it is
 * unit-testable.
 *
 * Geometry is in HUD display pixels (display 4096, 480x240). Measured on the owner's car
 * 2026-10-14: only y=40..200 reaches the optics, and the native HUD (speed, ADAS icons, lane
 * keeping) uses everything right of x=180. The overlay lives in the free cells A1..C4
 * (x 0..180, y 40..200) minus a gap on every side.
 *
 * Two parts: turn-by-turn (only while Android Auto guides) in the free box, and two bars on the
 * bottom row: power/regen at the left edge (always) and engine RPM at the right edge (while the
 * engine runs).
 */
object HudOverlayModel {
    const val DISPLAY_ID = 4096

    // Free box, with the gap the owner asked for.
    const val BOX_LEFT = 10f
    const val BOX_RIGHT = 168f
    const val BOX_CENTER_X = (BOX_LEFT + BOX_RIGHT) / 2

    // Turn-by-turn sits in the top part, centred vertically in it.
    const val TBT_TOP = 58f
    const val TBT_BOTTOM = 170f

    // With lanes: arrow and "250 m" shrink to one row centred over the lane row.
    const val COMPACT_GLYPH = 32f
    const val COMPACT_VALUE_SIZE = 22f
    const val COMPACT_UNIT_SIZE = 14f
    const val COMPACT_ROW_GAP = 6f
    const val COMPACT_LANES_GAP = 10f
    const val LANE_ROW_HEIGHT = 36f
    const val LANE_CELL_MAX = 36f

    // No lanes: a big arrow with a small distance under it.
    const val HERO_GLYPH = 64f
    const val HERO_VALUE_SIZE = 20f
    const val HERO_UNIT_SIZE = 14f
    const val HERO_TEXT_GAP = 22f // icon bottom edge to text baseline

    const val UNIT_GAP = 4f

    /** Top of the compact block (row + lanes), centred in the TBT area. */
    const val COMPACT_TOP =
        TBT_TOP + (TBT_BOTTOM - TBT_TOP - (COMPACT_GLYPH + COMPACT_LANES_GAP + LANE_ROW_HEIGHT)) / 2
    const val LANES_TOP = COMPACT_TOP + COMPACT_GLYPH + COMPACT_LANES_GAP

    /** Top of the hero arrow, so arrow + text sit centred in the TBT area. */
    const val HERO_TOP = TBT_TOP + (TBT_BOTTOM - TBT_TOP - (HERO_GLYPH + HERO_TEXT_GAP)) / 2

    // Bars on the bottom row: power at the HUD's left edge, RPM at its right edge (the native
    // HUD leaves the bottom of its right side empty). Layout approved in the browser simulator.
    const val BAR_Y = 192f
    const val BAR_LABEL_BASELINE = 184f
    const val BAR_LABEL_SIZE = 11f

    // Power: centre-zero, one dot per 10 % each side of a subtle zero tick.
    const val POWER_LEFT = 10f
    const val POWER_RIGHT = 118f
    const val POWER_DOTS_PER_SIDE = 10
    const val POWER_DOT_RADIUS = 1.5f
    const val POWER_ZERO_GAP = 4f
    const val POWER_ZERO_TICK = 3f

    // RPM: one segment per 1000 rpm, the 5-6k one red.
    const val RPM_LEFT = 416f
    const val RPM_RIGHT = 470f
    const val RPM_SEGMENTS = 6
    const val RPM_SEGMENT_GAP = 2f
    const val RPM_SEGMENT_HEIGHT = 3f
    const val RPM_RED = 5_000

    /** Power full scale. A confirmar against the car's real limits; tune on a drive. */
    const val DRIVE_MAX_KW = 100.0
    const val REGEN_MAX_KW = 50.0
    /** Below this power reads 0 %, so idle noise doesn't flicker a dot. */
    const val POWER_DEADBAND_KW = 0.5

    /**
     * Signed power share: drive as % of [DRIVE_MAX_KW], regen as -% of [REGEN_MAX_KW]. Matches
     * PowerFlowMapper's sign: battery V x I > 0 is discharge (drive).
     */
    fun powerPercent(kw: Double): Int {
        if (!kw.isFinite() || kw > -POWER_DEADBAND_KW && kw < POWER_DEADBAND_KW) return 0
        return if (kw > 0) {
            (kw / DRIVE_MAX_KW * 100).roundToInt().coerceAtMost(100)
        } else {
            -(-kw / REGEN_MAX_KW * 100).roundToInt().coerceAtMost(100)
        }
    }

    /** Lit power dots from [powerPercent], signed: one per 10 %, at least one past the deadband. */
    fun powerDots(percent: Int): Int {
        if (percent == 0) return 0
        val n = ((kotlin.math.abs(percent) + 5) / 10).coerceIn(1, POWER_DOTS_PER_SIDE)
        return if (percent > 0) n else -n
    }

    /** Distance of power dot [i] (0 = nearest the zero tick) from the bar's centre. */
    fun powerDotOffset(i: Int): Float {
        val mid = (POWER_LEFT + POWER_RIGHT) / 2
        val first = POWER_ZERO_GAP + POWER_DOT_RADIUS
        val step = (mid - POWER_LEFT - POWER_DOT_RADIUS - first) / (POWER_DOTS_PER_SIDE - 1)
        return first + i * step
    }

    /**
     * RPM in tenths of a thousand (32 = 3.2k), the unit of the "3.2 kRPM" label; null hides the
     * bar (engine off, or a sentinel like 65535).
     */
    fun rpmTenths(rpm: Double, engineOn: Boolean): Int? {
        if (!engineOn || !rpm.isFinite() || rpm < 0 || rpm > 8_000) return null
        return (rpm / 100).roundToInt()
    }

    /** Lit RPM segments: one per 1000 rpm, rounded, at least one while the engine turns. */
    fun rpmSegments(tenths: Int): Int {
        if (tenths <= 0) return 0
        return ((tenths + 5) / 10).coerceIn(1, RPM_SEGMENTS)
    }

    /** Whether segment [i] (0-based, 1000 rpm each) sits in the red zone. */
    fun rpmSegmentIsRed(i: Int): Boolean = (i + 1) * 1000 > RPM_RED

    /** "3.2 kRPM". */
    fun rpmLabel(tenths: Int): String = "${tenths / 10}.${tenths % 10} kRPM"

    /**
     * Same fold as `resolveTurnIcon` in cluster-widgets/source/v1.0/shared/tbt/tbtTurnIcons.js,
     * so the HUD and the cluster card pick the same artwork for a maneuver.
     */
    fun resolveTurnIcon(turn: String?): String {
        val token = turn.orEmpty().uppercase()
        if (token.isEmpty()) return ""
        if (token in KNOWN_ICONS) return token
        if (token == "NAME_CHANGE" || token == "DEPART") return "STRAIGHT"
        if (token.startsWith("U_TURN")) return "U_TURN"
        if (token.startsWith("ROUNDABOUT")) return "ROUNDABOUT"
        if (token.startsWith("DESTINATION")) return "DESTINATION"
        if (token.startsWith("FERRY")) return "STRAIGHT"
        if (token.startsWith("FORK")) return "FORK"
        if (token.startsWith("MERGE")) return "MERGE"
        if (token.startsWith("OFF_RAMP")) return "EXIT"
        if (token.startsWith("ON_RAMP")) return "MERGE"
        if (token.endsWith("_LEFT")) return "TURN_LEFT"
        if (token.endsWith("_RIGHT")) return "TURN_RIGHT"
        return token
    }

    /** Tokens with artwork in tbtTurnIcons.js (SVG or text glyph). */
    private val KNOWN_ICONS = setOf(
        "TURN_RIGHT", "TURN_LEFT", "STRAIGHT", "ROUNDABOUT", "U_TURN", "MERGE", "FORK",
        "EXIT", "DESTINATION"
    )

    enum class LaneShape { STRAIGHT, SLIGHT, NORMAL, SHARP, UTURN }

    /** One lane arrow: right-hand shape, whether to mirror it left, whether the route uses it. */
    data class LaneArrow(val shape: LaneShape, val mirrored: Boolean, val highlighted: Boolean)

    /** Google `LaneDirection.Shape` number to artwork, as SHAPE_BY_NUMBER in tbtLanes.js. */
    fun laneArrow(shape: Int, highlighted: Boolean): LaneArrow? = when (shape) {
        1 -> LaneArrow(LaneShape.STRAIGHT, false, highlighted)
        2 -> LaneArrow(LaneShape.SLIGHT, true, highlighted)
        3 -> LaneArrow(LaneShape.SLIGHT, false, highlighted)
        4 -> LaneArrow(LaneShape.NORMAL, true, highlighted)
        5 -> LaneArrow(LaneShape.NORMAL, false, highlighted)
        6 -> LaneArrow(LaneShape.SHARP, true, highlighted)
        7 -> LaneArrow(LaneShape.SHARP, false, highlighted)
        8 -> LaneArrow(LaneShape.UTURN, true, highlighted)
        9 -> LaneArrow(LaneShape.UTURN, false, highlighted)
        else -> null
    }

    /**
     * Arrows of one lane in draw order: unused first so the route's arrow sits on top. A lane
     * with nothing recognisable still holds its slot with a dim straight arrow, as in tbtLanes.js.
     */
    fun laneArrows(lane: List<AndroidAutoNavigationTelemetry.LaneDirection>): List<LaneArrow> {
        val known = lane.mapNotNull { laneArrow(it.shape, it.highlighted) }
        if (known.isEmpty()) return listOf(LaneArrow(LaneShape.STRAIGHT, false, false))
        return known.filter { !it.highlighted } + known.filter { it.highlighted }
    }

    /** Lane cell width: as wide as allowed, shrinking so every lane fits the box. */
    fun laneCellWidth(laneCount: Int): Float {
        if (laneCount <= 0) return 0f
        return minOf(LANE_CELL_MAX, (BOX_RIGHT - BOX_LEFT) / laneCount)
    }

    /** "250 m" -> ("250", "m"); "1.2 km" -> ("1.2", "km"). Falls back to metres. */
    fun splitDistance(distance: String, distanceM: Int?): Pair<String, String> {
        val text = distance.trim()
        if (text.isNotEmpty()) {
            val cut = text.lastIndexOf(' ')
            if (cut > 0) return text.substring(0, cut).trim() to text.substring(cut + 1).trim()
            return text to ""
        }
        if (distanceM != null && distanceM >= 0) return distanceM.toString() to "m"
        return "" to ""
    }

    private val STACK_LINE = Regex("""^Stack id=\d+ .*\bdisplayId=(\d+)""")
    private val TASK_LINE = Regex("""^\s+taskId=(\d+):""")

    /**
     * Top task of [displayId] from `am stack list`, which lists each display's stacks top first,
     * or -1. Used to hand focus back to D0 after the HUD window opens.
     */
    fun topTaskOnDisplay(stackList: String, displayId: Int): Int {
        var inDisplay = false
        for (line in stackList.lineSequence()) {
            val stack = STACK_LINE.find(line)
            if (stack != null) {
                inDisplay = stack.groupValues[1].toIntOrNull() == displayId
                continue
            }
            if (!inDisplay) continue
            TASK_LINE.find(line)?.let { return it.groupValues[1].toIntOrNull() ?: -1 }
        }
        return -1
    }

    /** Turn-by-turn part. Lanes empty = hero layout, otherwise compact. */
    data class Tbt(
        val icon: String,
        val distanceValue: String,
        val distanceUnit: String,
        val lanes: List<List<LaneArrow>>
    )

    /**
     * Everything the view draws. Bars are quantised to what their labels show (whole %, 0.1k
     * rpm), so equal frames are skipped and the high-rate CAN feed only redraws on a visible change.
     */
    data class Frame(val tbt: Tbt?, val powerPercent: Int, val rpmTenths: Int?)

    fun tbtFor(d: AndroidAutoNavigationTelemetry.Directions): Tbt? {
        if (!d.active) return null
        val (value, unit) = splitDistance(d.distance, d.distanceM)
        return Tbt(
            icon = resolveTurnIcon(d.turn),
            distanceValue = value,
            distanceUnit = unit,
            lanes = d.lanes.map { laneArrows(it) }
        )
    }
}
