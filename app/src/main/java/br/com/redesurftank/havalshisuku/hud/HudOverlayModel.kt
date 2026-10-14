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
 * Two parts: turn-by-turn (only while Android Auto guides) on top, and two dotted bars at the
 * bottom that are always on: power/regen, and engine RPM while the engine runs.
 */
object HudOverlayModel {
    const val DISPLAY_ID = 4096

    // Free box, with the gap the owner asked for.
    const val BOX_LEFT = 10f
    const val BOX_RIGHT = 168f
    const val BOX_CENTER_X = (BOX_LEFT + BOX_RIGHT) / 2

    // Turn-by-turn sits in the top part, centred vertically in it.
    const val TBT_TOP = 58f
    const val TBT_BOTTOM = 168f

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

    // Dotted bars under the TBT area (owner gave up ~15 px of the bottom margin for them).
    const val BAR_DOTS = 20
    const val BAR_DOT_RADIUS = 1.8f
    const val POWER_BAR_Y = 180f
    const val RPM_BAR_Y = 190f

    /** Bar full scale. A confirmar against the car's real limits; tune by eye. */
    const val DRIVE_MAX_KW = 100.0
    const val REGEN_MAX_KW = 50.0
    const val RPM_MAX = 5_000.0
    /** Below this the power bar shows nothing, so idle noise doesn't flicker a dot. */
    const val POWER_DEADBAND_KW = 0.5

    /** x of dot [i] (0..BAR_DOTS-1), spread edge to edge of the box. */
    fun barDotX(i: Int): Float {
        val left = BOX_LEFT + BAR_DOT_RADIUS
        val right = BOX_RIGHT - BAR_DOT_RADIUS
        return left + (right - left) * i / (BAR_DOTS - 1)
    }

    /**
     * Lit power dots, signed from the bar's centre: positive = drive (right half), negative =
     * regen (left half), each half [BAR_DOTS]/2 dots. Matches PowerFlowMapper's sign: battery
     * V x I > 0 is discharge.
     */
    fun powerDots(kw: Double): Int {
        if (!kw.isFinite() || kw > -POWER_DEADBAND_KW && kw < POWER_DEADBAND_KW) return 0
        val half = BAR_DOTS / 2
        return if (kw > 0) {
            (kw / DRIVE_MAX_KW * half).roundToInt().coerceIn(1, half)
        } else {
            -(-kw / REGEN_MAX_KW * half).roundToInt().coerceIn(1, half)
        }
    }

    /** Lit RPM dots, left to right; null hides the bar (engine off or reading invalid). */
    fun rpmDots(rpm: Double, engineOn: Boolean): Int? {
        if (!engineOn || !rpm.isFinite() || rpm < 0 || rpm > 8_000) return null
        return (rpm / RPM_MAX * BAR_DOTS).roundToInt().coerceIn(0, BAR_DOTS)
    }

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
     * Everything the view draws. Bars are quantised to dots, so equal frames are skipped and the
     * high-rate CAN feed only redraws when a dot actually changes.
     */
    data class Frame(val tbt: Tbt?, val powerDots: Int, val rpmDots: Int?)

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
