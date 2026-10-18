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
 * bottom row: power/regen at the left edge and engine RPM at the right edge (while the engine
 * runs). Each can be switched off in the Navigation layout's settings ([Config]).
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

    // Roundabout exit number. In the big arrow it sits in the ring's hole: the hole's centre and
    // radius as fractions of the ROUNDABOUT icon's box (486.039 viewBox in HudTbtIcons /
    // tbtTurnIcons.js: centre 296.897,182.665, inner radius 81.16). The small arrow's hole is too
    // small to read, so the compact and far rows put "2ª" after the distance instead.
    const val RING_HOLE_CX = 296.897f / 486.039f
    const val RING_HOLE_CY = 182.665f / 486.039f
    const val RING_HOLE_R = 81.16f / 486.039f
    // Dynamic roundabout, drawn when AA sends the exit angle: ring, entry at the bottom, the
    // travelled arc and an arrow at the real exit. In a 24 box like the other icons; same geometry
    // as roundaboutSvg in tbtTurnIcons.js. The exit number sits in the ring's centre.
    const val RB_CX = 12f
    const val RB_CY = 12f
    const val RB_R = 5f
    const val RB_STROKE = 2.4f
    const val RB_ENTRY_BOTTOM = 22.5f
    const val RB_EXIT_TIP = 11f
    const val RB_HEAD_LENGTH = 4f
    const val RB_HEAD_HALF_WIDTH = 3.2f
    // Exits passed before ours: short dim stubs off the ring, as far out as this.
    const val RB_STUB_END = 7.8f
    // Closer than this the stubs read as noise, so none are drawn.
    const val RB_STUB_MIN_GAP = 25f

    const val HERO_EXIT_SIZE = 18f
    const val HERO_EXIT_SIZE_2DIGITS = 13f
    const val EXIT_GAP = 6f

    /** Top of the compact block (row + lanes), centred in the TBT area. */
    const val COMPACT_TOP =
        TBT_TOP + (TBT_BOTTOM - TBT_TOP - (COMPACT_GLYPH + COMPACT_LANES_GAP + LANE_ROW_HEIGHT)) / 2
    const val LANES_TOP = COMPACT_TOP + COMPACT_GLYPH + COMPACT_LANES_GAP

    /** Top of the hero arrow, so arrow + text sit centred in the TBT area. */
    const val HERO_TOP = TBT_TOP + (TBT_BOTTOM - TBT_TOP - (HERO_GLYPH + HERO_TEXT_GAP)) / 2

    // No lanes and still >= 1 km away (optional): a small arrow + distance row, like the compact
    // row without lanes; the big arrow only comes back for the last kilometre. Under the row: the
    // street the maneuver leads onto, then a larger trip line, all pulled up close to the arrow.
    const val FAR_THRESHOLD_M = 1_000
    const val FAR_TOP = 70f
    const val FAR_STREET_SIZE = 13f
    const val FAR_STREET_BASELINE = FAR_TOP + COMPACT_GLYPH + 18f
    const val FAR_TRIP_SIZE = 16f
    const val FAR_TRIP_BASELINE = FAR_STREET_BASELINE + 21f
    /** No street from AA: the trip line moves up into the street's place. */
    const val FAR_TRIP_BASELINE_NO_STREET = FAR_TOP + COMPACT_GLYPH + 21f

    // Trip line ("4.8 km · 12 min" left to the destination) under the big arrow or the lanes,
    // centred in the box: larger than it was (13), smaller than the far row's.
    const val TRIP_BASELINE = 178f
    const val TRIP_SIZE = 15f

    // Destination flag before the trip line: a square box this share of the text size, sitting on
    // the baseline, then a gap; flag + text are centred together in the box.
    const val TRIP_FLAG_SCALE = 0.9f
    const val TRIP_FLAG_GAP = 4f

    /**
     * Left edges of the flag and of the text for a trip line [textWidth] wide at [size], so the
     * whole line (flag included) is centred on [BOX_CENTER_X].
     */
    fun tripLineLefts(textWidth: Float, size: Float): Pair<Float, Float> {
        val flag = size * TRIP_FLAG_SCALE
        val left = BOX_CENTER_X - (flag + TRIP_FLAG_GAP + textWidth) / 2
        return left to left + flag + TRIP_FLAG_GAP
    }

    /** Trip line baseline and text size for [style]; the far row's depends on a street showing. */
    fun tripPlacement(style: TbtStyle, hasStreet: Boolean): Pair<Float, Float> = when (style) {
        TbtStyle.FAR -> (if (hasStreet) FAR_TRIP_BASELINE else FAR_TRIP_BASELINE_NO_STREET) to FAR_TRIP_SIZE
        else -> TRIP_BASELINE to TRIP_SIZE
    }

    /** The far row shows the street under the arrow; the big arrow and the lane rows don't. */
    fun showsStreet(tbt: Tbt): Boolean = tbt.style == TbtStyle.FAR && tbt.street.isNotEmpty()

    // Bars on the bottom row of the visible band, no labels: power at the HUD's left edge, RPM at
    // its right edge (the native HUD leaves the bottom of both sides empty). Approved in the
    // browser simulator (scripts/hud-simulator).
    const val BAR_Y = 195f
    const val BAR_HEIGHT = 4f

    // Power: centre-zero, one segment per 10 % each side of a subtle zero tick. Centred under the
    // turn-by-turn (BOX_CENTER_X), so the bar lines up with the arrow and trip line above it.
    const val POWER_HALF_WIDTH = 54f
    const val POWER_LEFT = BOX_CENTER_X - POWER_HALF_WIDTH
    const val POWER_RIGHT = BOX_CENTER_X + POWER_HALF_WIDTH
    const val POWER_SEGMENTS_PER_SIDE = 10
    const val POWER_SEGMENT_GAP = 1.5f
    const val POWER_ZERO_GAP = 4f
    const val POWER_ZERO_TICK = 4f

    // RPM: one segment per 1000 rpm, the 5-6k one red.
    const val RPM_LEFT = 416f
    const val RPM_RIGHT = 470f
    const val RPM_SEGMENTS = 6
    const val RPM_SEGMENT_GAP = 2f
    const val RPM_RED = 5_000

    /** Power full scale. A confirmar against the car's real limits; tune on a drive. */
    const val DRIVE_MAX_KW = 100.0
    const val REGEN_MAX_KW = 50.0
    /** Below this power reads 0 %, so idle noise doesn't light a segment. */
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

    /** Lit power segments from [powerPercent], signed: one per 10 %, at least one past the deadband. */
    fun powerSegments(percent: Int): Int {
        if (percent == 0) return 0
        val n = ((kotlin.math.abs(percent) + 5) / 10).coerceIn(1, POWER_SEGMENTS_PER_SIDE)
        return if (percent > 0) n else -n
    }

    /** Width of one power segment. */
    val POWER_SEGMENT_WIDTH: Float
        get() {
            val half = (POWER_RIGHT - POWER_LEFT) / 2 - POWER_ZERO_GAP
            return (half - POWER_SEGMENT_GAP * (POWER_SEGMENTS_PER_SIDE - 1)) / POWER_SEGMENTS_PER_SIDE
        }

    /** Inner edge of power segment [i] (0 = nearest the zero tick), as a distance from the bar's centre. */
    fun powerSegmentOffset(i: Int): Float = POWER_ZERO_GAP + i * (POWER_SEGMENT_WIDTH + POWER_SEGMENT_GAP)

    /**
     * RPM in tenths of a thousand (32 = 3.2k): fine enough to redraw only on a visible change;
     * null hides the bar (engine off, or a sentinel like 65535).
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

    /**
     * Trip line: "4.8 km · 12 min" left to the destination, or whichever half exists; "" when
     * neither does. Same rules as formatRemainingDistance / formatTripEta in the cluster card
     * (tbtTurnIcons.js), so the HUD and the card's DISTÂNCIA / TEMPO read the same.
     */
    fun tripLabel(remainingM: Int?, remainingS: Int?): String {
        val distance = remainingM?.takeIf { it >= 0 }?.let { m ->
            if (m < 1000) return@let "$m m"
            val km = m / 1000.0
            if (km >= 100) "${groupThousands(km.roundToInt())} km"
            else "${(km * 10).roundToInt() / 10}.${(km * 10).roundToInt() % 10} km"
        }
        val left = remainingS?.takeIf { it >= 0 }?.let { s ->
            if (s < 45) return@let "<1 min"
            val minutes = (s / 60.0).roundToInt()
            if (minutes < 60) return@let "$minutes min"
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0) "$hours h" else "$hours h $rest min"
        }
        return listOfNotNull(distance, left).joinToString(" · ")
    }

    /** pt-BR grouping, as groupThousands in tbtTurnIcons.js: 1234 -> "1.234". */
    private fun groupThousands(n: Int): String =
        n.toString().reversed().chunked(3).joinToString(".").reversed()

    /** Metres to the maneuver: the host's number, else parsed from "1.2 km" / "250 m". */
    fun distanceMeters(distance: String, distanceM: Int?): Int? {
        if (distanceM != null && distanceM >= 0) return distanceM
        val (value, unit) = splitDistance(distance, null)
        val n = value.replace(',', '.').toDoubleOrNull() ?: return null
        return when (unit.lowercase()) {
            "km" -> (n * 1000).roundToInt()
            "m" -> n.roundToInt()
            else -> null
        }
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

    /** How the turn-by-turn block is laid out. */
    enum class TbtStyle {
        /** No lanes: big arrow, small distance under it. */
        HERO,
        /** Lanes: small arrow + distance row centred over the lane row. */
        COMPACT,
        /** No lanes, still >= [FAR_THRESHOLD_M] away, "smaller arrow when far" on: the row alone. */
        FAR,
        /** "Navegação AR" layout, no lanes, a maneuver with a ribbon shape: the 3D arrow ([HudArArrow]). */
        AR
    }

    // ---- AR layout: left column (no big arrow there; the arrow is in the middle) ----

    /** Left column, no lanes: street, a large distance, the trip line. */
    const val AR_STREET_BASELINE = 80f
    const val AR_STREET_SIZE = 16f
    const val AR_DISTANCE_BASELINE = 128f
    const val AR_DISTANCE_VALUE_SIZE = 38f
    const val AR_DISTANCE_UNIT_SIZE = 20f
    const val AR_TRIP_BASELINE = 160f
    const val AR_TRIP_SIZE = 16f

    /** Left column with lanes: the same, compacted around the lane row. */
    const val AR_LANES_STREET_BASELINE = 74f
    const val AR_LANES_STREET_SIZE = 14f
    const val AR_LANES_DISTANCE_BASELINE = 104f
    const val AR_LANES_DISTANCE_VALUE_SIZE = 28f
    const val AR_LANES_DISTANCE_UNIT_SIZE = 16f
    const val AR_LANES_TOP = 114f
    const val AR_LANES_TRIP_BASELINE = 172f
    const val AR_LANES_TRIP_SIZE = 14f
    /** While the maneuver is still far (straight arrow), street and distance at this scale. */
    const val AR_FAR_TEXT_SCALE = 0.75f
    /** ...and the distance baseline this much higher, closer to the street line. */
    const val AR_FAR_DISTANCE_RAISE = 8f

    // ---- AR layout: power / RPM arcs round the native speed readout ----
    // Centred on the native speed (measured on the car 2026-10-17: about x 222, y 100), then moved
    // down ~20 px then back up ~8 px at the owner's request (y 112). Each arc is 50 thin
    // radial ticks over 195° (150°..345°): 15° shorter at each end than the 225° version, so it
    // stops well short of the native "km/h".

    const val ARC_CX = 222f
    const val ARC_CY = 112f
    /** 195°: from 150° (canvas angles, clockwise, bottom-left) to 345° (just above the right). */
    const val ARC_START_DEG = 150f
    const val ARC_SWEEP_DEG = 195f
    const val ARC_TICKS = 50

    /** Outer arc, power: thicker, longer ticks. */
    const val ARC_POWER_R_IN = 44f
    const val ARC_POWER_R_OUT = 52f
    const val ARC_POWER_TICK_W = 1.3f

    /** Inner arc, RPM: thinner, shorter ticks. */
    const val ARC_RPM_R_IN = 35f
    const val ARC_RPM_R_OUT = 39f
    const val ARC_RPM_TICK_W = 0.8f
    /** RPM arc full scale. */
    const val ARC_RPM_MAX = 7_000

    /** Canvas angle (degrees) of tick [i] of [ARC_TICKS], at the middle of its slot. */
    fun arcTickDeg(i: Int): Float = ARC_START_DEG + (i + 0.5f) * ARC_SWEEP_DEG / ARC_TICKS

    /**
     * Lit power ticks, from the left end: one per 2 % of [powerPercent] (drive blue, regen green,
     * both filling from the left); at least one past 0 %, none at 0 %.
     */
    fun arcPowerTicks(percent: Int): Int {
        if (percent == 0) return 0
        return ((kotlin.math.abs(percent) * ARC_TICKS + 50) / 100).coerceIn(1, ARC_TICKS)
    }

    /** Lit RPM ticks, from the left end, over 0..[ARC_RPM_MAX]; at least one while turning. */
    fun arcRpmTicks(tenths: Int): Int {
        if (tenths <= 0) return 0
        return (tenths * 100 * ARC_TICKS / ARC_RPM_MAX.toFloat()).roundToInt().coerceIn(1, ARC_TICKS)
    }

    /**
     * Turn-by-turn part. [trip] (remaining distance · time) is "" when off or unknown. For
     * [TbtStyle.AR], [arAngle] is the maneuver's bend and [arMeters] the distance that places the
     * corner (the controller interpolates it between Android Auto updates when near).
     * [exitNumber] is the roundabout exit to take, only with the ROUNDABOUT icon.
     */
    data class Tbt(
        val icon: String,
        val distanceValue: String,
        val distanceUnit: String,
        val lanes: List<List<LaneArrow>>,
        val style: TbtStyle,
        val trip: String,
        val arAngle: Float? = null,
        val arMeters: Float? = null,
        /** Road the maneuver leads onto (shown by the AR layout's left column). */
        val street: String = "",
        val exitNumber: Int? = null,
        /** Degrees travelled around the ring (1..360); set only with the ROUNDABOUT icon. */
        val exitAngle: Int? = null,
        val roundaboutClockwise: Boolean = false,
        /** AR roundabout: how much of the way round has been driven (0..1), estimated. */
        val arProgress: Float = 0f
    ) {
        /** Draw the roundabout with its real exit instead of the static icon. */
        val dynamicRoundabout: Boolean get() = icon == "ROUNDABOUT" && exitAngle != null
    }

    /**
     * Where the exit leaves the ring, as a maths angle (degrees, 0 = right, 90 = up). The entry
     * is at the bottom (270); [angle] is travelled in the roundabout's direction: counter-clockwise
     * on screen for right-hand traffic (90 = right exit, 180 = straight, 270 = left), clockwise
     * for left-hand traffic. A confirmar against a real drive (see aa_nav_roundabout).
     */
    fun roundaboutExitDegrees(angle: Int, clockwise: Boolean): Float =
        roundaboutExitDegrees(angle.toFloat(), clockwise)

    fun roundaboutExitDegrees(angle: Float, clockwise: Boolean): Float {
        val deg = if (clockwise) 270f - angle else 270f + angle
        return ((deg % 360f) + 360f) % 360f
    }

    /**
     * Travel angles (as [angle]) of the exits passed before ours, for the dim stubs. AA sends
     * only our exit, so the count is real (exit 3 passes two) but the positions are not: they
     * are spread evenly between the entry and our exit. Empty for the first exit, without an
     * exit number, or when they would sit closer than [RB_STUB_MIN_GAP].
     */
    fun passedExitAngles(angle: Int, exitNumber: Int?): List<Float> {
        val n = exitNumber ?: return emptyList()
        if (n < 2) return emptyList()
        val step = angle.toFloat() / n
        if (step < RB_STUB_MIN_GAP) return emptyList()
        return (1 until n).map { step * it }
    }

    /** Exit angle the dynamic icon can draw: 1..360, else null (static icon). */
    fun drawableExitAngle(angle: Int?): Int? = angle?.takeIf { it in 1..360 }

    /** Exit after the distance in the compact / far rows: 2 -> "2ª" (saída). */
    fun exitLabel(exitNumber: Int): String = "${exitNumber}ª"

    /** Text size of the exit in the big arrow's ring: two digits shrink to stay inside the hole. */
    fun heroExitSize(exitNumber: Int): Float = if (exitNumber >= 10) HERO_EXIT_SIZE_2DIGITS else HERO_EXIT_SIZE

    /**
     * Centre of the ring's hole for an icon drawn at [left], [top] in a [size] square: the static
     * icon's hole, or the dynamic ring's centre.
     */
    fun ringHoleCenter(left: Float, top: Float, size: Float, dynamic: Boolean = false): Pair<Float, Float> =
        if (dynamic) (left + RB_CX / 24f * size) to (top + RB_CY / 24f * size)
        else (left + RING_HOLE_CX * size) to (top + RING_HOLE_CY * size)

    /**
     * Everything the view draws; null parts are switched off or have no data. Bars are quantised
     * (whole %, 0.1k rpm), so equal frames are skipped and the high-rate CAN feed only redraws on
     * a visible change.
     */
    data class Frame(
        val tbt: Tbt?,
        val powerPercent: Int?,
        val rpmTenths: Int?,
        /** AR layout: power and RPM as arcs round the native speed instead of the bottom bars. */
        val arcs: Boolean = false
    )

    /** HUD layouts the owner picks from in the Telas tab. */
    enum class Layout(val key: String) {
        /** The car's own HUD only; Impulse draws nothing. */
        ORIGINAL("original"),
        /** Turn-by-turn + power/RPM bars. */
        NAVIGATION("navigation"),
        /** Navigation with the 3D perspective arrow in place of the big arrow. */
        AR("ar");

        companion object {
            fun fromKey(key: String?): Layout? = entries.firstOrNull { it.key == key }
        }
    }

    /** Owner's choices for the Navigation layout; everything on by default. */
    data class Config(
        val layout: Layout = Layout.ORIGINAL,
        val navigation: Boolean = true,
        val smallArrowWhenFar: Boolean = true,
        val trip: Boolean = true,
        val powerBar: Boolean = true,
        val rpmBar: Boolean = true,
        /** Power/RPM as arcs round the native speed instead of the bottom bars, per layout. */
        val navArcs: Boolean = false,
        val arArcs: Boolean = true
    ) {
        val active: Boolean get() = layout != Layout.ORIGINAL

        /** Arcs for the current layout. */
        val arcs: Boolean get() = when (layout) {
            Layout.NAVIGATION -> navArcs
            Layout.AR -> arArcs
            Layout.ORIGINAL -> false
        }
    }

    /**
     * Android Auto sends a new maneuver (code 10) before its distance (code 11), so for a moment
     * the new step carries the last one's (~0 m) and the AR arrow would show the turn as if it
     * were here. True from such a step change until the distance moves.
     */
    fun distanceStale(
        previous: AndroidAutoNavigationTelemetry.Directions?,
        update: AndroidAutoNavigationTelemetry.Directions,
        wasStale: Boolean
    ): Boolean {
        if (previous == null) return false
        val sameDistance = previous.distanceM == update.distanceM && previous.distance == update.distance
        val newStep = previous.turnId != update.turnId || previous.turn != update.turn || previous.street != update.street
        return if (newStep) sameDistance else sameDistance && wasStale
    }

    fun tbtFor(d: AndroidAutoNavigationTelemetry.Directions, config: Config = Config()): Tbt? {
        if (!d.active) return null
        val (value, unit) = splitDistance(d.distance, d.distanceM)
        val lanes = d.lanes.map { laneArrows(it) }
        val meters = distanceMeters(d.distance, d.distanceM)
        val icon = resolveTurnIcon(d.turn)
        val exitAngle = if (icon == "ROUNDABOUT") drawableExitAngle(d.turnAngle) else null
        // AR: turns by their bend angle; a roundabout with a known exit gets the 3D ring (angle 0
        // here, the renderer draws it from exitAngle).
        val arAngle = if (config.layout != Layout.AR) null
        else HudArArrow.angleFor(d.turn) ?: if (exitAngle != null) 0f else null
        val style = when {
            // AR keeps the centre arrow with lanes too; the lanes go to the left column.
            arAngle != null && meters != null -> TbtStyle.AR
            lanes.isNotEmpty() -> TbtStyle.COMPACT
            config.smallArrowWhenFar && meters != null && meters >= FAR_THRESHOLD_M -> TbtStyle.FAR
            else -> TbtStyle.HERO
        }
        return Tbt(
            icon = icon,
            distanceValue = value,
            distanceUnit = unit,
            lanes = lanes,
            style = style,
            trip = if (config.trip) tripLabel(d.remainingM, d.remainingS) else "",
            arAngle = arAngle.takeIf { style == TbtStyle.AR },
            arMeters = meters?.toFloat().takeIf { style == TbtStyle.AR },
            street = d.street.trim(),
            exitNumber = d.exitNumber?.takeIf { icon == "ROUNDABOUT" && it > 0 },
            exitAngle = exitAngle,
            roundaboutClockwise = d.roundaboutClockwise == true
        )
    }

    /** The frame for [config], from the latest guidance and bar readings. */
    fun frameFor(
        config: Config,
        directions: AndroidAutoNavigationTelemetry.Directions?,
        powerPercent: Int,
        rpmTenths: Int?,
        arMetersOverride: Float? = null,
        arProgress: Float = 0f,
        arDistanceKnown: Boolean = true
    ): Frame = Frame(
        tbt = if (config.navigation) {
            directions?.let { tbtFor(it, config) }?.let { tbt ->
                if (tbt.style != TbtStyle.AR) tbt
                else tbt.copy(
                    arMeters = if (arDistanceKnown) arMetersOverride ?: tbt.arMeters else null,
                    arProgress = arProgress
                )
            }
        } else null,
        powerPercent = if (config.powerBar) powerPercent else null,
        rpmTenths = if (config.rpmBar) rpmTenths else null,
        arcs = config.arcs
    )

    /**
     * Scripted drive for the debug `hudDemo drive`: what the HUD would get [t] seconds into a
     * 66 s loop at [speed] m/s. Right turn from 450 m (engine on, then regen into the turn), a
     * sharp left with lanes from 270 m (into the RPM red zone, then regen), then a roundabout
     * from 200 m taken at the 3rd exit (left, 270° round a counter-clockwise ring).
     */
    object DemoDrive {
        const val LOOP_MS = 66_000L
        class Step(
            val directions: AndroidAutoNavigationTelemetry.Directions,
            val powerPercent: Int,
            val rpmTenths: Int?
        )

        private fun lane(shape: Int, used: Boolean) =
            listOf(AndroidAutoNavigationTelemetry.LaneDirection(shape, used))

        fun at(t: Float, speed: Float): Step {
            val firstLeg = 30f
            return if (t < firstLeg) {
                val d = (450f - speed * t).coerceAtLeast(0f)
                Step(
                    directions(d, "TURN_RIGHT", "R. Augusta", 5_200f - speed * t, 700 - t.toInt(), emptyList()),
                    powerPercent = when {
                        t < 10f -> 45
                        t < 20f -> 20
                        else -> -40
                    },
                    rpmTenths = if (t < 12f) 28 else null
                )
            } else if (t >= 48f) {
                val s = t - 48f
                val d = (200f - speed * s).coerceAtLeast(0f)
                Step(
                    directions(d, "ROUNDABOUT", "Av. Brasil", 3_900f - speed * s, 600 - s.toInt(), emptyList())
                        .copy(exitNumber = 3, turnAngle = 270, roundaboutClockwise = false),
                    powerPercent = if (s < 6f) 30 else -30,
                    rpmTenths = null
                )
            } else {
                val s = t - firstLeg
                val d = (270f - speed * s).coerceAtLeast(0f)
                Step(
                    directions(
                        d, "SHARP_LEFT", "Av. Paulista", 4_750f - speed * s, 670 - s.toInt(),
                        listOf(lane(4, true), lane(1, false), lane(1, false))
                    ),
                    powerPercent = if (s < 8f) 85 else -25,
                    rpmTenths = when {
                        s < 3f -> 35
                        s < 8f -> 56
                        else -> null
                    }
                )
            }
        }

        private fun directions(
            meters: Float,
            turn: String,
            street: String,
            remainingM: Float,
            remainingS: Int,
            lanes: List<List<AndroidAutoNavigationTelemetry.LaneDirection>>
        ): AndroidAutoNavigationTelemetry.Directions {
            val rounded = (meters / 10f).roundToInt() * 10
            return AndroidAutoNavigationTelemetry.Directions(
                active = true,
                distance = "$rounded m",
                distanceM = meters.roundToInt(),
                turn = turn,
                street = street,
                remainingM = remainingM.roundToInt(),
                remainingS = remainingS,
                lanes = lanes
            )
        }
    }
}
