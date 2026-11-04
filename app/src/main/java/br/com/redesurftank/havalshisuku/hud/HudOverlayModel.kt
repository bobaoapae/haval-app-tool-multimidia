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
    const val FAR_TRIP_SIZE = 16f
    /** The far row has no street line (owner, 2026-10-24): the trip line sits right under it. */
    const val FAR_TRIP_BASELINE = FAR_TOP + COMPACT_GLYPH + 21f

    // Trip line ("4.8 km · 12 min" left to the destination) under the big arrow or the lanes,
    // centred in the box: larger than it was (13), smaller than the far row's.
    const val TRIP_BASELINE = 178f
    const val TRIP_SIZE = 15f

    // Destination flag before the trip line: a square box this share of the text size, sitting on
    // the baseline, then a gap; flag + text are centred together in the box.
    const val TRIP_FLAG_SCALE = 0.9f
    const val TRIP_FLAG_GAP = 4f

    /**
     * Left edge of a left-column line (street, maneuver row, trip line or block) [width] wide
     * that must end by [right] (the arcs, or the box): centred on [BOX_CENTER_X] while that
     * fits, else moved left into the HUD's free left edge (owner, 2026-10-24: long trip lines
     * ran into the signs, the maneuver row into the arcs below 200 m), never past [TRIP_MIN_LEFT].
     */
    fun columnLeft(width: Float, right: Float): Float {
        val centred = BOX_CENTER_X - width / 2
        if (centred >= BOX_LEFT && centred + width <= right) return centred
        return (right - width).coerceIn(TRIP_MIN_LEFT, maxOf(TRIP_MIN_LEFT, centred))
    }

    /** Trip line baseline and text size for [style]. */
    fun tripPlacement(style: TbtStyle): Pair<Float, Float> = when (style) {
        TbtStyle.FAR -> FAR_TRIP_BASELINE to FAR_TRIP_SIZE
        else -> TRIP_BASELINE to TRIP_SIZE
    }

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
        val (distance, left) = tripParts(remainingM, remainingS)
        return listOfNotNull(distance, left).joinToString(" · ")
    }

    /**
     * The trip's distance and time left, compact (owner, 2026-10-24: it overflowed with hours):
     * "4.8km", "1h 25m", "12m". "m" is minutes here, so short distances stay in km ("0.9km").
     */
    fun tripParts(remainingM: Int?, remainingS: Int?): Pair<String?, String?> {
        val distance = remainingM?.takeIf { it >= 0 }?.let { m ->
            val km = m / 1000.0
            if (km >= 100) "${groupThousands(km.roundToInt())}km"
            else "${(km * 10).roundToInt() / 10}.${(km * 10).roundToInt() % 10}km"
        }
        val left = remainingS?.takeIf { it >= 0 }?.let { s ->
            if (s < 45) return@let "<1m"
            val minutes = (s / 60.0).roundToInt()
            if (minutes < 60) return@let "${minutes}m"
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
        }
        return distance to left
    }

    /** Unit letters (km, h, m) in the trip text are drawn at this share of the numbers' size. */
    const val TRIP_UNIT_SCALE = 0.68f
    /** A trip line too long to sit centred in the box may start this far left. */
    const val TRIP_MIN_LEFT = 2f

    // Turn hidden (further than "Mostrar próxima manobra"): the trip on two lines, distance over
    // time, with the flag on the left centred on both (owner, 2026-10-24). At the trip line's own
    // size and place, the time on its baseline (owner, 2026-10-25: it came out big and moved).
    /** The two lines are this much bigger than the one-line trip (owner, 2026-10-25: +2). */
    const val TRIP_BLOCK_EXTRA_SIZE = 2f
    /** Second line to first: this many text sizes up. */
    const val TRIP_BLOCK_LINE_STEP = 1.25f
    /** The flag, against the text size (it spans both lines). */
    const val TRIP_BLOCK_FLAG_SCALE = 1.4f
    const val TRIP_BLOCK_GAP = 6f

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

    /**
     * The HUD's icon for a maneuver: [resolveTurnIcon], except the gentle ones (slight, keep,
     * fork, exit ramp) get a 45 deg arrow to their side ("SLIGHT_LEFT"/"SLIGHT_RIGHT") instead of
     * the fork glyph or the 90 deg turn: a 45 deg exit read as a junction (owner, 2026-10-25).
     */
    fun hudIcon(turn: String?): String {
        val token = turn.orEmpty().uppercase()
        val side = when {
            token.endsWith("_RIGHT") -> "RIGHT"
            token.endsWith("_LEFT") -> "LEFT"
            else -> null
        }
        val gentle = token.startsWith("SLIGHT") || token.startsWith("KEEP") || token.startsWith("FORK") || token.startsWith("OFF_RAMP")
        return if (side != null && gentle) "SLIGHT_$side" else resolveTurnIcon(turn)
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
    /** "carregando mapa…": on the right, grey; shown 5 s, hidden 10 s, round and round (owner). */
    const val MAP_PENDING_X = 420f
    const val MAP_PENDING_Y = 188f
    /** Loading bar under the note: width, and its row. */
    const val MAP_PENDING_BAR_W = 84f
    const val MAP_PENDING_BAR_Y = 194f

    /** Next-turn icon before the AR street line: size relative to the text, gap after it. */
    const val AR_STREET_ICON_SCALE = 1.25f
    const val AR_STREET_ICON_GAP = 4f
    /** The turn icon now sits before the distance (owner): size relative to the digits. */
    const val AR_DISTANCE_ICON_SCALE = 0.8f
    const val AR_DISTANCE_ICON_GAP = 6f
    /** While the maneuver is still far (straight arrow), street and distance at this scale. */
    const val AR_FAR_TEXT_SCALE = 0.75f
    /** ...and the distance baseline this much higher, closer to the street line. */
    const val AR_FAR_DISTANCE_RAISE = 8f

    // ---- AR layout: power / RPM arcs round the native speed readout ----
    // Centred on the native speed (measured on the car 2026-10-17: about x 222, y 100), then moved
    // down ~20 px then back up ~8 px at the owner's request (y 112). The power arc is 50 thin
    // radial ticks. 2026-10-26 (owner): circles, not ellipses ("as 1:1 as possible"), each as far
    // from the first digit as from the last, from 140° to 345° (nearly level on the right); RPM a
    // line of 7 segments just inside the power ticks. Solved by simulation against a photo of the
    // car's 119 km/h and the emulator's two-digit speed.

    /** Native speed readout's centre (measured on the car 2026-10-17); the AR arrow sits on it. */
    const val NATIVE_SPEED_CX = 222f
    /** Two-digit speeds: a circle centred on the speed (~7 px from either digit). */
    const val ARC_CX = 222f
    const val ARC_CY = 112f
    /** From 140° (canvas angles, clockwise, bottom-left) to 345° (just above level on the right). */
    const val ARC_START_DEG = 140f
    const val ARC_SWEEP_DEG = 205f
    const val ARC_TICKS = 50

    /** Outer arc, power: ticks from [ARC_POWER_R_IN] to [ARC_POWER_R_OUT]. */
    const val ARC_POWER_R_IN = 47.5f
    const val ARC_POWER_R_OUT = 56f
    const val ARC_POWER_TICK_W = 1.3f

    /**
     * Inner arc, RPM: a line just inside the power ticks (thin ticks crowded the digits), in
     * [ARC_RPM_SEGMENTS] segments of 1000 rpm with [ARC_RPM_GAP_PX] between them, filling
     * continuously (a segment can be partly lit).
     */
    const val ARC_RPM_R = 44.5f
    const val ARC_RPM_W = 2.5f
    const val ARC_RPM_SEGMENTS = 7
    const val ARC_RPM_GAP_PX = 3f
    /** RPM arc full scale. */
    const val ARC_RPM_MAX = 7_000

    /** Canvas angle (degrees) of tick [i] of [ARC_TICKS], at the middle of its slot. */
    fun arcTickDeg(i: Int): Float = ARC_START_DEG + (i + 0.5f) * ARC_SWEEP_DEG / ARC_TICKS

    /**
     * Three-digit speeds (owner, 2026-10-18): from [ARC_WIDE_ON_KMH] the arcs are a bigger circle,
     * same ticks and angles. Back to normal below [ARC_WIDE_OFF_KMH], so cruising at ~100 doesn't
     * make them flicker. 2026-10-26: radius [ARC_WIDE_R_OUT] with the ticks as long as on the
     * normal arcs, as high as the HUD shows (its top at y 40), ~4.5 px from the "1" and the "9".
     */
    const val ARC_WIDE_CX = 230f
    const val ARC_WIDE_CY = 121f
    const val ARC_WIDE_R_OUT = 81f
    const val ARC_WIDE_ON_KMH = 100.0
    const val ARC_WIDE_OFF_KMH = 95.0

    /** Wide arcs for [speedKmh], given whether they were wide ([wasWide]): with hysteresis. */
    fun wideArcs(speedKmh: Double, wasWide: Boolean): Boolean =
        if (wasWide) speedKmh >= ARC_WIDE_OFF_KMH else speedKmh >= ARC_WIDE_ON_KMH

    /**
     * Both arcs a little wider than tall (owner on the car, 2026-10-26: "um pouquinho mais largo,
     * mesmo que saia da proporção"): x radii times this.
     */
    const val ARC_X_SCALE = 1.08f

    /** Radius of a ring [r] out on the normal arcs; on the wide ones the same distance in from the outer edge. */
    fun arcRadius(r: Float, wide: Boolean = false): Float = if (wide) ARC_WIDE_R_OUT - (ARC_POWER_R_OUT - r) else r

    /**
     * Point at canvas angle [deg], [r] out from the centre of the normal arcs (x times
     * [ARC_X_SCALE]). On the wide ones [r] keeps its distance in from the outer edge (see
     * [arcRadius]), so ticks and the RPM line are as long and as far apart as on the normal ones.
     */
    fun arcPoint(deg: Float, r: Float, wide: Boolean = false): Pair<Float, Float> {
        val rad = Math.toRadians(deg.toDouble())
        val rr = arcRadius(r, wide)
        val cx = if (wide) ARC_WIDE_CX else ARC_CX
        val cy = if (wide) ARC_WIDE_CY else ARC_CY
        return cx + (kotlin.math.cos(rad) * rr * ARC_X_SCALE).toFloat() to cy + (kotlin.math.sin(rad) * rr).toFloat()
    }

    /**
     * The RPM segments as (start, sweep) canvas degrees: the whole of each, or with [lit] (0..1
     * of full scale) only its lit part, empty ones left out.
     */
    fun rpmSegments(wide: Boolean, lit: Float = 1f): List<Pair<Float, Float>> {
        val gap = Math.toDegrees((ARC_RPM_GAP_PX / arcRadius(ARC_RPM_R, wide)).toDouble()).toFloat()
        val seg = (ARC_SWEEP_DEG - gap * (ARC_RPM_SEGMENTS - 1)) / ARC_RPM_SEGMENTS
        val f = lit.coerceIn(0f, 1f) * ARC_RPM_SEGMENTS
        return (0 until ARC_RPM_SEGMENTS).mapNotNull { i ->
            val part = (f - i).coerceIn(0f, 1f)
            if (part <= 0f) null else ARC_START_DEG + i * (seg + gap) to seg * part
        }
    }

    /** Lit share of the RPM arc for [tenths] (hundreds of rpm): at least a sliver while turning. */
    fun rpmArcLit(tenths: Int): Float {
        if (tenths <= 0) return 0f
        return (tenths * 100f / ARC_RPM_MAX).coerceIn(0.02f, 1f)
    }

    /**
     * Where the chevrons travel, [ARC_POWER_R_OUT] + [CHEVRON_ARC_GAP] round the arcs. Two
     * digits: the oval the arcs were until 2026-10-26 (centre 218, 1.3x wide), clear of the new
     * circle, so the chevrons stay where the owner tuned them. Three digits: round the big circle;
     * its top is out of the HUD there, so the ring stops at [CHEVRON_RING_MIN_Y] on [side]'s side.
     */
    fun chevronRingPoint(deg: Float, wide: Boolean, side: Int = 1): Pair<Float, Float> {
        if (!wide) {
            val r = ARC_POWER_R_OUT + CHEVRON_ARC_GAP
            val rad = Math.toRadians(deg.toDouble())
            return CHEVRON_RING_CX + (kotlin.math.cos(rad) * r * CHEVRON_RING_X_SCALE).toFloat() to
                ARC_CY + (kotlin.math.sin(rad) * r).toFloat()
        }
        val rr = ARC_WIDE_R_OUT + CHEVRON_ARC_GAP
        val hidden = 90f - Math.toDegrees(kotlin.math.asin(((ARC_WIDE_CY - CHEVRON_RING_MIN_Y) / rr).toDouble())).toFloat()
        val off = deg - 270f
        val d = if (kotlin.math.abs(off) >= hidden) deg else 270f + (if (off > 0f || (off == 0f && side > 0)) hidden else -hidden)
        val rad = Math.toRadians(d.toDouble())
        return ARC_WIDE_CX + (kotlin.math.cos(rad) * rr * ARC_X_SCALE).toFloat() to ARC_WIDE_CY + (kotlin.math.sin(rad) * rr).toFloat()
    }
    const val CHEVRON_RING_CX = 218f
    const val CHEVRON_RING_X_SCALE = 1.3f
    /** Highest a chevron's centre goes on the three-digit ring: the HUD's top (40) + half a chevron. */
    const val CHEVRON_RING_MIN_Y = 47f

    // ---- Approach chevrons ("setas de aproximação"), both HUD layouts.
    // From 100 m: one chevron, still, on the diagonal of the turn's side.
    // From 50 m: it travels to the top edge, turns to point fully left/right and grows to 4.
    // Hidden while the car is stopped. ----
    /** One still chevron from this distance. */
    const val CHEVRON_SHOW_M = 100f
    /** The row starts moving at this distance. */
    const val CHEVRON_ANIM_M = 50f
    /** Row of the flag/limit checks above the arcs (two-digit arc top is ARC_CY - ARC_POWER_R_OUT = 56). */
    const val CHEVRON_CY = 48f
    /** The top edge row they end on, and how far out to the side of the HUD's centre. */
    const val CHEVRON_END_Y = 48f
    const val CHEVRON_CX = 240f
    const val CHEVRON_END_DX = 150f
    /** Gap between the outer arc and the chevrons' start. */
    const val CHEVRON_ARC_GAP = 12f
    /** Where the strip starts on the right (canvas degrees round the arcs); the left mirrors it. */
    const val CHEVRON_SHARP_START_DEG = 330f
    /** Soft turns start lower, so the strip has room to slide up the diagonal. */
    const val CHEVRON_SOFT_START_DEG = 345f
    /** Turns at least this sharp bend the strip to the top edge; softer ones keep the diagonal. */
    const val CHEVRON_SHARP_DEG = 60f
    /** From here to the turn the trail grows behind the lead; before it the lead travels alone. */
    const val CHEVRON_TRAIL_M = 20f
    /** From here all four lie in the row (the lead is far enough along the top edge). */
    const val CHEVRON_ALIGNED_M = 10f
    /** Sharp turn: how far past the curve's corner the lead comes out level, then its run along the top edge. */
    const val CHEVRON_CURVE_EXIT = 10f
    const val CHEVRON_TOP_RUN = 75f
    /** Soft turn: the length of its diagonal, and the highest row its end may reach. */
    const val CHEVRON_SOFT_LEN = 61f
    const val CHEVRON_SOFT_TOP_Y = 50f
    /** "Sempre": the still chevron points straight up from this far, leaning to the diagonal by [CHEVRON_SHOW_M]. */
    const val CHEVRON_ALWAYS_UP_M = 130f
    /** On a roundabout's ring: only this far round it (never while entering, owner 2026-10-23). */
    const val RB_CHEVRON_MIN_PROGRESS = 0.3f

    /** One chevron: centre, pointing (screen degrees, 0 = right, -90 = up), how faded in it is. */
    data class ChevronMark(val x: Float, val y: Float, val dir: Float, val appear: Float = 1f)

    /**
     * The "Sempre" chevron, a direction arrow round the power arc (owner): at [lean] 0 on top
     * of the arc pointing straight up (the turn is still ahead); as the turn nears it travels
     * round the arc to the turn's side, leaning over, and at 1 it is exactly where the
     * near-turn chevrons start, so they take over without a jump.
     */
    fun chevronCompass(side: Int, sharp: Boolean, lean: Float, roadDeg: Float = 0f, turnDeg: Float = 45f, wide: Boolean = false): ChevronMark {
        val l = lean.coerceIn(0f, 1f)
        val start = if (sharp) CHEVRON_SHARP_START_DEG else CHEVRON_SOFT_START_DEG
        val end = if (side > 0) start else 540f - start
        // Far away it follows the road's own bends ([roadDeg], + right) instead of straight up.
        val base = 270f + roadDeg
        val (x, y) = chevronRingPoint(base + (end - base) * l, wide, side)
        // ...and ends exactly as the strip starts (its direction too, a soft turn's own angle).
        val dir0 = chevronStrip(side, sharp, 0f, turnDeg, wide)[0].dir
        val dirBase = -90f + roadDeg
        return ChevronMark(x, y, dirBase + (dir0 - dirBase) * l)
    }

    /** "Sempre": how far ahead the road's bend is read, and the most the arrow turns for it. */
    // 40 m (owner, 2026-10-28: at 100 m it pointed to the next curve while still in this one;
    // on that drive's track, 100 m leaned against the curve being driven 67 times, 40 m 32).
    // Up to 30 degrees (owner, 2026-10-28: a little more on soft curves; it was 15).
    const val CHEVRON_ROAD_AHEAD_M = 40f
    const val CHEVRON_ROAD_MAX_DEG = 30f
    /**
     * ...turning this share of the bend: only the road's trend (owner, 2026-10-27: a quarter of
     * the 2x it had, which followed the curves fully).
     */
    const val CHEVRON_ROAD_GAIN = 0.5f

    /**
     * The bend (degrees, + right) of the matched [route] (car frame metres, x right, y ahead)
     * about [CHEVRON_ROAD_AHEAD_M] on, against its first metres, so the "Sempre" arrow
     * anticipates the road's curves (owner, 2026-10-24). Against the route itself, not the
     * screen: a GPS heading off the street does not tilt it. Null when the route is too short.
     */
    fun roadBendAhead(route: FloatArray): Float? {
        val start = routeHeading(route, 0f, 15f) ?: return null
        val ahead = routeHeading(route, CHEVRON_ROAD_AHEAD_M - 15f, CHEVRON_ROAD_AHEAD_M + 15f) ?: return null
        return (angleDiff(ahead, start) * CHEVRON_ROAD_GAIN).coerceIn(-CHEVRON_ROAD_MAX_DEG, CHEVRON_ROAD_MAX_DEG)
    }

    /** How far either side of the maneuver the route's heading is read for its turn angle. */
    const val CHEVRON_TURN_NEAR_M = 30f
    const val CHEVRON_TURN_FAR_M = 50f

    /**
     * The turn the drawn [route] (car frame metres) really makes around [atM] metres along it
     * (+ right, degrees): its heading 30..50 m after against 30..50 m before, so a junction
     * matched a little off AA's distance still falls between. Null when the route is too short.
     * The chevrons use it instead of the maneuver type (owner, 2026-10-25: a "turn" that is a
     * soft bend on the map should animate as one).
     */
    fun routeTurnAt(route: FloatArray, atM: Float): Float? {
        val b0 = (atM - CHEVRON_TURN_FAR_M).coerceAtLeast(0f)
        val b1 = (atM - CHEVRON_TURN_NEAR_M).coerceAtLeast(b0 + 5f)
        val before = routeHeading(route, b0, b1) ?: return null
        val after = routeHeading(route, atM + CHEVRON_TURN_NEAR_M, atM + CHEVRON_TURN_FAR_M) ?: return null
        return angleDiff(after, before)
    }

    /** Point [s] metres along [route] (x, y pairs), or null past its end. */
    private fun pointAlong(route: FloatArray, s: Float): Pair<Float, Float>? {
        var left = s
        for (i in 0 until route.size / 2 - 1) {
            val dx = route[2 * i + 2] - route[2 * i]
            val dy = route[2 * i + 3] - route[2 * i + 1]
            val len = kotlin.math.hypot(dx, dy)
            if (left <= len && len > 0f) return route[2 * i] + dx * left / len to route[2 * i + 1] + dy * left / len
            left -= len
        }
        return null
    }

    /** The route's heading (degrees, + right of ahead) from [a] to [b] metres along it. */
    private fun routeHeading(route: FloatArray, a: Float, b: Float): Float? {
        val p = pointAlong(route, a) ?: return null
        val q = pointAlong(route, b) ?: return null
        return Math.toDegrees(kotlin.math.atan2((q.first - p.first).toDouble(), (q.second - p.second).toDouble())).toFloat()
    }

    private fun angleDiff(to: Float, from: Float): Float = ((to - from) % 360f + 540f) % 360f - 180f

    /**
     * The lead chevron's way, as x,y points: a sharp turn curves from the diagonal start up to
     * the top row (tangent -45 deg there, level at the end), then runs along the top edge; a
     * soft turn is the straight diagonal.
     */
    private fun chevronWay(side: Int, sharp: Boolean, turnDeg: Float = 45f, wide: Boolean = false): FloatArray {
        val deg = if (sharp) CHEVRON_SHARP_START_DEG else CHEVRON_SOFT_START_DEG
        val (x0, y0) = chevronRingPoint(if (side > 0) deg else 540f - deg, wide, side)
        if (!sharp) {
            // At the turn's own angle (owner, 2026-10-25), shortened to stay in the band.
            val th = Math.toRadians(turnDeg.coerceIn(CHEVRON_MIN_DEG, CHEVRON_SHARP_DEG).toDouble())
            val ux = kotlin.math.sin(th).toFloat()
            val uy = kotlin.math.cos(th).toFloat()
            val len = minOf(CHEVRON_SOFT_LEN, (y0 - CHEVRON_SOFT_TOP_Y) / uy)
            return floatArrayOf(x0, y0, x0 + side * ux * len, y0 - uy * len)
        }
        // Quadratic curve P0 -> P1 with its corner where the start's diagonal meets the top row.
        val rise = y0 - CHEVRON_END_Y
        val cx = x0 + side * rise
        val x1 = cx + side * CHEVRON_CURVE_EXIT
        val n = 24
        val pts = FloatArray((n + 2) * 2)
        for (i in 0..n) {
            val u = i / n.toFloat()
            val a = (1 - u) * (1 - u)
            val b = 2 * u * (1 - u)
            val c = u * u
            pts[2 * i] = a * x0 + b * cx + c * x1
            pts[2 * i + 1] = a * y0 + b * CHEVRON_END_Y + c * CHEVRON_END_Y
        }
        pts[2 * n + 2] = x1 + side * CHEVRON_TOP_RUN
        pts[2 * n + 3] = CHEVRON_END_Y
        return pts
    }

    /** Point and heading (screen degrees) [at] along [way] (clamped to its ends). */
    private fun alongWay(way: FloatArray, at: Float): Triple<Float, Float, Float> {
        var left = at.coerceAtLeast(0f)
        val last = way.size / 2 - 1
        for (i in 0 until last) {
            val dx = way[2 * i + 2] - way[2 * i]
            val dy = way[2 * i + 3] - way[2 * i + 1]
            val len = kotlin.math.hypot(dx, dy)
            if (left <= len || i == last - 1) {
                val f = if (len > 0f) (left / len).coerceIn(0f, 1f) else 0f
                val dir = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
                return Triple(way[2 * i] + dx * f, way[2 * i + 1] + dy * f, dir)
            }
            left -= len
        }
        return Triple(way[0], way[1], 0f)
    }

    private fun wayLength(way: FloatArray, upTo: Int = way.size / 2 - 1): Float {
        var len = 0f
        for (i in 0 until upTo) len += kotlin.math.hypot(way[2 * i + 2] - way[2 * i], way[2 * i + 3] - way[2 * i + 1])
        return len
    }

    /**
     * The chevrons at [intensity] 0 (50 m, one still on the diagonal) .. 1 (the turn), rear
     * first, lead last (owner, 2026-10-23). Over the first 30 m the lead travels alone: a sharp
     * turn's curves up to the top row, turning until it points fully left/right; a soft one's
     * runs up its diagonal. Over the last [CHEVRON_TRAIL_M] the lead keeps going (along the top
     * edge for a sharp turn) and a trail grows behind it, on its own way and fading to the last
     * one, so it is not lined up while still on the curve; by [CHEVRON_ALIGNED_M] all four lie
     * in the row.
     */
    fun chevronStrip(side: Int, sharp: Boolean, intensity: Float, turnDeg: Float = 45f, wide: Boolean = false): List<ChevronMark> {
        val meters = CHEVRON_ANIM_M * (1f - intensity.coerceIn(0f, 1f))
        val way = chevronWay(side, sharp, turnDeg, wide)
        val total = wayLength(way)
        // Where the lone travel ends: the curve's end (sharp), or most of the diagonal (soft).
        val alone = if (sharp) wayLength(way, way.size / 2 - 2) else (total - 3 * CHEVRON_SPACING).coerceAtLeast(0f)
        val lead = if (meters >= CHEVRON_TRAIL_M) {
            val u = (CHEVRON_ANIM_M - meters) / (CHEVRON_ANIM_M - CHEVRON_TRAIL_M)
            alone * u * u * (3 - 2 * u)
        } else {
            // Ease-out, so the row is out on the top edge (aligned) by CHEVRON_ALIGNED_M.
            val u = 1f - meters / CHEVRON_TRAIL_M
            alone + (total - alone) * (1f - (1f - u) * (1f - u))
        }
        // Trail: grows 1 -> 4 from CHEVRON_TRAIL_M to CHEVRON_ALIGNED_M, never behind the start.
        val grown = ((CHEVRON_TRAIL_M - meters) / (CHEVRON_TRAIL_M - CHEVRON_ALIGNED_M)).coerceIn(0f, 1f) * (CHEVRON_COUNT - 1)
        val behind = minOf(grown.toInt(), (lead / CHEVRON_SPACING).toInt(), CHEVRON_COUNT - 1)
        val newest = if (behind == grown.toInt() && grown < CHEVRON_COUNT - 1) ((grown - behind) * 3f).coerceIn(0f, 1f) else 1f
        return List(behind + 1) { i ->
            val k = behind - i // 0 = lead
            val (x, y, dir) = alongWay(way, lead - k * CHEVRON_SPACING)
            // The trail fades to its last chevron; the newest one fades in.
            val fade = 1f - 0.22f * k
            ChevronMark(x, y, dir, if (k == behind && k > 0) fade * newest else fade)
        }
    }

    /**
     * How far right a left-column text line on [baseline] may reach without touching the outer
     * arc (rows the arc doesn't reach: the column's own edge). The street line got this when the
     * arcs grew towards it.
     */
    fun columnRightAt(baseline: Float, textSize: Float, wide: Boolean = false): Float {
        val top = baseline - textSize
        var right = BOX_RIGHT
        for (i in 0 until ARC_TICKS) {
            val (x, y) = arcPoint(arcTickDeg(i), ARC_POWER_R_OUT, wide)
            if (y in top - 2f..baseline + 2f) right = minOf(right, x - 4f)
        }
        return right
    }
    const val CHEVRON_HALF_H = 7f
    /** The map/arrow's destination flag shows from this far (owner, 2026-10-25). */
    const val DEST_FLAG_SHOW_M = 150f
    /** Margin each chevron clears in the text under it, so it reads as on top. */
    const val CHEVRON_CUT_MARGIN = 1.5f
    const val CHEVRON_W = 7f
    const val CHEVRON_STROKE = 4f
    const val CHEVRON_COUNT = 4
    const val CHEVRON_SPACING = 15f
    /** One sweep of the light along the row: slow when just in range, fast at the turn. */
    const val CHEVRON_PERIOD_FAR_MS = 1_100f
    const val CHEVRON_PERIOD_NEAR_MS = 380f
    /** Below this bend (keep, merge: a lane choice, not a turn) there is no side to point to. */
    const val CHEVRON_MIN_DEG = 30f

    /**
     * Chevrons for a maneuver within [CHEVRON_SHOW_M], on either HUD layout. Still until
     * [CHEVRON_ANIM_M], then the row animates. With the map matched, only when ours is the next
     * street on the turn's side (a scripted demo ignores that, the maneuver is the script). Hidden while the car is stopped ([speedKmh] <= 0).
     * [always] (owner option): further away, or where another street comes first, the one still
     * chevron stays by the arcs pointing to the next road.
     */
    fun chevronsFor(tbt: Tbt?, speedKmh: Double = 1.0, scripted: Boolean = false, always: Boolean = false, evenHidden: Boolean = false): Chevrons? {
        if (speedKmh <= 0.0) return null
        if (tbt == null || (tbt.turnHidden && !evenHidden)) return null
        val meters = tbt.guideMeters ?: if (always) Float.MAX_VALUE else return null
        if (meters > CHEVRON_SHOW_M && !always) return null
        // Roundabout (owner): never while entering it, only to show the exit, so on the ring
        // past its first part and within [CHEVRON_ANIM_M] of the exit (no still or "Sempre"
        // arrow there). They point to the exit (right in right-hand traffic). The map's "next
        // street on this side" check does not apply: a roundabout is not that turn.
        val roundaboutExit = tbt.dynamicRoundabout && tbt.rbProgress >= RB_CHEVRON_MIN_PROGRESS && meters <= CHEVRON_ANIM_M
        // "Sempre" (owner, 2026-10-27: it vanished approaching a roundabout and on straight-on
        // steps): the still arrow keeps following the road, upright, where no side is shown.
        val road = if (always) tbt.route?.takeIf { tbt.routeMatched }?.let { roadBendAhead(it) } ?: 0f else 0f
        val followRoad = if (always) Chevrons(1, 0f, animate = false, sharp = false, lean = 0f, roadDeg = road) else null
        if (tbt.dynamicRoundabout && !roundaboutExit) return followRoad
        val notOurs = tbt.routeMatched && !tbt.routeClear && !scripted && !roundaboutExit
        if (notOurs && !always) return null
        val heading = if (tbt.dynamicRoundabout) {
            if (tbt.roundaboutClockwise) -90f else 90f
        } else {
            val meta = tbt.guideAngle ?: return followRoad
            // The map's own angle when the route is on the real streets and agrees on the side:
            // a "turn" that is a soft bend there animates as one (owner, 2026-10-25).
            val map = tbt.route?.takeIf { tbt.routeMatched && meters <= CHEVRON_SHOW_M * 1.5f }
                ?.let { routeTurnAt(it, meters) }
                ?.takeIf { kotlin.math.abs(it) >= CHEVRON_MIN_DEG && (it > 0f) == (meta > 0f) }
            map ?: meta
        }
        if (kotlin.math.abs(heading) < CHEVRON_MIN_DEG) return followRoad
        val side = if (heading > 0f) 1 else -1
        val sharp = kotlin.math.abs(heading) >= CHEVRON_SHARP_DEG
        val animate = meters <= CHEVRON_ANIM_M && !notOurs
        val intensity = if (animate) (1f - meters / CHEVRON_ANIM_M).coerceIn(0f, 1f) else 0f
        // "Sempre" far away: straight up (the turn is still ahead), leaning to the diagonal as it nears.
        val l = ((CHEVRON_ALWAYS_UP_M - meters) / (CHEVRON_ALWAYS_UP_M - CHEVRON_SHOW_M)).coerceIn(0f, 1f)
        return Chevrons(side, intensity, animate, sharp, lean = l * l * (3 - 2 * l), roadDeg = road, turnDeg = kotlin.math.abs(heading))
    }

    /**
     * Lit power ticks, from the left end: one per 2 % of [powerPercent] (drive blue, regen green,
     * both filling from the left); at least one past 0 %, none at 0 %.
     */
    fun arcPowerTicks(percent: Int): Int {
        if (percent == 0) return 0
        return ((kotlin.math.abs(percent) * ARC_TICKS + 50) / 100).coerceIn(1, ARC_TICKS)
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
        val arProgress: Float = 0f,
        /** AR arriving: destination side (-1 left, +1 right, 0 unknown); null on other maneuvers. */
        val arDestination: Int? = null,
        val arPlacement: HudArArrow.Placement = HudArArrow.Placement.CENTER,
        /** AR map: streets round the car, car frame in metres; empty = map off or not loaded. */
        val streets: List<br.com.redesurftank.havalshisuku.map.LocalRoad> = emptyList(),
        /** AR map: the route ahead (metres, car frame), drawn on the streets; null = no map view. */
        val route: FloatArray? = null,
        /** The route was matched to the real roads (not the plain fallback shape). */
        val routeMatched: Boolean = false,
        /** No other street leaves on the turn's side before ours (see RoutePath.Route). */
        val routeClear: Boolean = false,
        /** AR map: screen row of its horizon. */
        val mapHorizon: Float = HudArArrow.MAP_HORIZON_Y,
        /** AR map: the owner's tilt and zoom ([HudArArrow.MapLens]). */
        val mapTilt: Float = 1f,
        val mapZoom: Float = 1f,
        /** AR map on but its area not loaded yet: a note in place of the arrow (owner: no arrows). */
        val mapPending: Boolean = false,
        /** ...and how much of it is loaded (0..1), for the bar under the note. */
        val mapProgress: Float = 0f,
        /** The car is on the roundabout: the street line shows the street instead of the exit. */
        val onRoundabout: Boolean = false,
        /** Distance to this maneuver, either layout. Chevrons use it; null when unknown. */
        val guideMeters: Float? = null,
        /** Bend of this maneuver (+ right). Null on a roundabout (the exit angle is used) or an unknown shape. */
        val guideAngle: Float? = null,
        /** 0 approaching a roundabout, then how far round the ring toward the exit (0..1). */
        val rbProgress: Float = 0f,
        /** Further than [Config.showTurnFromM]: icon, distance, street and lanes are not drawn. */
        val turnHidden: Boolean = false,
        /** The trip line's two parts, for the two-line block while the turn is hidden ("" = none). */
        val tripDistance: String = "",
        val tripTime: String = "",
        /** AR map: grey streets as edge lines ([Config.mapEdges]). */
        val mapEdges: Boolean = false,
        /** Google style ([Config.iconStyle]): the Material Symbols icon to draw; null = ours. */
        val googleIcon: String? = null
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

    /** On the ring, leaving it: about this turn off the ring (+ right, counter-clockwise rings). */
    const val RING_EXIT_TURN_DEG = 70f
    /** Under this bend a maneuver is drawn as the road straight on (RoutePath draws no turn under 20). */
    const val STRAIGHT_ROUTE_DEG = 20f

    /** Exit angle the dynamic icon can draw: 1..360, else null (static icon). */
    fun drawableExitAngle(angle: Int?): Int? = angle?.takeIf { it in 1..360 }

    /**
     * Identity of one roundabout for [RoundaboutTracker]. The exit number and angle stay put
     * when the street label changes to the exit road; without either, fall back to the maneuver id.
     */
    fun roundaboutIdentity(exitNumber: Int?, turnAngle: Int?, clockwise: Boolean?, turnId: Int?): String =
        if (exitNumber != null || (turnAngle != null && turnAngle != 0)) {
            "rb|${exitNumber ?: "-"}|${turnAngle ?: "-"}|${clockwise == true}"
        } else {
            "rb|id|$turnId"
        }

    /** Smallest arc the on-ring icon shrinks to: the exit sits just to the right. */
    const val RB_EXIT_ARC = 55f

    /**
     * Angle to draw while on the ring. [progress] 0 is the full exit; as the car goes round,
     * the remaining arc shrinks until it is [RB_EXIT_ARC], with the exit on the right.
     */
    fun roundaboutShownAngle(angle: Int, progress: Float): Int {
        if (progress <= 0f) return angle
        val remaining = angle * (1f - progress.coerceIn(0f, 1f))
        return remaining.coerceAtLeast(RB_EXIT_ARC).roundToInt().coerceIn(1, angle)
    }

    /**
     * Exit number still to come. Counts down with [progress]. 1 is not shown (the arrow is enough).
     */
    fun roundaboutExitCountdown(exitNumber: Int?, progress: Float): Int? {
        val n = exitNumber ?: return null
        if (n <= 1) return null
        if (progress <= 0f) return n
        val left = kotlin.math.ceil(n * (1f - progress.coerceIn(0f, 1f)) - 1e-4).toInt().coerceIn(1, n)
        return left.takeIf { it > 1 }
    }

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
        val arcs: Boolean = false,
        /** Speed camera ahead: the card in the turn-by-turn box. On Navegação AR the map stays. */
        val radar: Radar? = null,
        /** Three-digit speed: the wide arcs (see [wideArcs]). */
        val wideArcs: Boolean = false,
        /** Approach chevrons above the speed (null = none). */
        val chevrons: Chevrons? = null,
        /** Text face for everything written on the HUD. */
        val font: Font = Font.LIGHT,
        /** What to draw with (already resolved: the blue set in [ColorMode.BLUE]). */
        val colors: Colors = Colors()
    )

    // ---- Colours (owner, 2026-10-26): "Colorido" lets each part be picked; "Azul" draws
    // everything in one blue family. The defaults are the colours the HUD always had. ----

    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    val COLOR_DRIVE = rgb(63, 169, 255)
    val COLOR_REGEN = rgb(80, 230, 120)
    val COLOR_RPM = rgb(255, 64, 64)
    val COLOR_STREET = rgb(200, 205, 215)
    /** The route's deep (far) end; its near end is lighter (see [routeNear]). */
    val COLOR_ROUTE = rgb(25, 105, 255)
    val COLOR_TEXT = rgb(255, 255, 255)
    val COLOR_CHEVRON = rgb(90, 180, 255)

    enum class ColorMode(val key: String) {
        COLORFUL("colorful"),
        BLUE("blue");

        companion object {
            fun fromKey(key: String?): ColorMode? = entries.firstOrNull { it.key == key }
        }
    }

    data class Colors(
        val mode: ColorMode = ColorMode.COLORFUL,
        /** Power arc while driving / regenerating (and the bars). */
        val drive: Int = COLOR_DRIVE,
        val regen: Int = COLOR_REGEN,
        /** RPM arc. */
        val rpm: Int = COLOR_RPM,
        /** Map: the streets round the route, and the route / 3D arrow (the destination flag too). */
        val street: Int = withOpacity(COLOR_STREET, STREET_DEFAULT_OPACITY),
        val route: Int = COLOR_ROUTE,
        /** Text, maneuver icons and lanes. */
        val text: Int = COLOR_TEXT,
        /** The turn warning (approach chevrons). */
        val chevron: Int = COLOR_CHEVRON
    ) {
        /**
         * What the HUD draws with: the picks, or in [ColorMode.BLUE] everything in the first
         * blue swatch, "Azul claro" (owner, 2026-10-26: shades read as white), each part keeping
         * its own opacity.
         */
        fun resolved(): Colors {
            if (mode == ColorMode.COLORFUL) return this
            fun blue(c: Int) = (c and ALPHA_MASK) or (COLOR_DRIVE and RGB_MASK)
            return Colors(mode, blue(drive), blue(regen), blue(rpm), blue(street), blue(route), blue(text), blue(chevron))
        }
    }

    /** "Azul" with the default opacities. */
    val BLUE_COLORS: Colors get() = Colors(mode = ColorMode.BLUE).resolved()

    // Opacity (owner, 2026-10-26): each colour carries it in its alpha byte, 10..100 %. The
    // streets default to [STREET_DEFAULT_OPACITY], which is how they always looked; more makes
    // them more solid. Fades (top, sides, bottom) are multiplied, never replaced.
    const val ALPHA_MASK = -0x1000000
    const val RGB_MASK = 0xFFFFFF
    const val MIN_OPACITY = 0.1f
    const val STREET_DEFAULT_OPACITY = 0.6f

    fun opacityOf(c: Int): Float = ((c ushr 24) and 0xFF) / 255f
    fun withOpacity(c: Int, opacity: Float): Int =
        ((opacity.coerceIn(MIN_OPACITY, 1f) * 255).roundToInt() shl 24) or (c and RGB_MASK)
    fun sameRgb(a: Int, b: Int): Boolean = (a and RGB_MASK) == (b and RGB_MASK)

    /** The picker's swatches: name and colour. */
    val COLOR_SWATCHES: List<Pair<String, Int>> = listOf(
        "Branco" to COLOR_TEXT,
        "Cinza" to COLOR_STREET,
        "Azul claro" to COLOR_DRIVE,
        "Azul céu" to COLOR_CHEVRON,
        "Azul" to COLOR_ROUTE,
        "Ciano" to rgb(0, 220, 230),
        "Verde" to COLOR_REGEN,
        "Amarelo" to rgb(255, 214, 64),
        "Laranja" to rgb(255, 150, 50),
        "Vermelho" to COLOR_RPM,
        "Rosa" to rgb(255, 105, 180),
        "Roxo" to rgb(170, 120, 255)
    )

    /** [a] towards [b] by [t] (0..1), opaque. */
    fun mixColor(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t).toInt().coerceIn(0, 255)
        return rgb(ch(16), ch(8), ch(0))
    }

    /** The route's near (bottom) end: the original light blue for the default, else [route] lightened. */
    fun routeNear(route: Int): Int = if (sameRgb(route, COLOR_ROUTE)) rgb(120, 200, 255) else mixColor(route, COLOR_TEXT, 0.4f)

    /** Maneuver icon sets (owner, 2026-10-25: choose between them). */
    enum class IconStyle(val key: String) {
        CLASSIC("classic"),
        GOOGLE("google");

        companion object {
            fun fromKey(key: String?): IconStyle? = entries.firstOrNull { it.key == key }
        }
    }

    /**
     * Google's icon (Material Symbols name) for a maneuver token: its own icons for ramps and
     * either side's U-turn. Sharp turns take the normal turn, and forks our slight turn with the
     * road going on (see HudTbtIcons): the library's sharp (a step) and fork glyphs read wrong
     * (owner, 2026-10-25, until checked against Google Maps itself). Null for the destination (the checkered flag, as on the trip
     * line) and tokens with no icon.
     */
    fun googleIcon(turn: String?, clockwise: Boolean = false): String? {
        val t = turn.orEmpty().uppercase()
        val side = when {
            t.endsWith("_RIGHT") -> "right"
            t.endsWith("_LEFT") -> "left"
            else -> null
        }
        return when {
            t.isEmpty() || t.startsWith("DESTINATION") -> null
            t.startsWith("ROUNDABOUT") -> if (clockwise) "roundabout_left" else "roundabout_right"
            t.startsWith("U_TURN") -> "u_turn_${side ?: "left"}"
            t.startsWith("MERGE") -> "merge"
            t == "STRAIGHT" || t == "NAME_CHANGE" || t == "DEPART" || t.startsWith("FERRY") -> "straight"
            side == null -> null
            t.startsWith("SHARP") || t.startsWith("TURN") -> "turn_$side"
            t.startsWith("SLIGHT") || t.startsWith("KEEP") -> "turn_slight_$side"
            t.startsWith("FORK") -> "fork_$side"
            t.startsWith("OFF_RAMP") || t.startsWith("ON_RAMP") -> "ramp_$side"
            else -> null
        }
    }

    /**
     * HUD text faces. The head unit's fonts.xml maps sans-serif, -thin and -light all to one
     * regular Roboto (measured 2026-10-24), so each is loaded from its own file in
     * /system/fonts ([file]); the family is the fallback. Thin was too thin on the windshield
     * (owner); the condensed face takes the same weight in less width.
     */
    enum class Font(val key: String, val family: String, val file: String?) {
        LIGHT("light", "sans-serif-light", "Roboto-Light.ttf"),
        REGULAR("regular", "sans-serif", null),
        CONDENSED("condensed", "sans-serif-condensed", "RobotoCondensed-Regular.ttf");

        companion object {
            fun fromKey(key: String?): Font? = entries.firstOrNull { it.key == key }
        }
    }

    /**
     * Chevrons pointing to [side] (+1 right, -1 left). [intensity] 0 is the still diagonal,
     * 1 is at the turn. [animate] is false from 100 m to 50 m (one chevron, no pulse).
     */
    data class Chevrons(
        val side: Int,
        val intensity: Float,
        val animate: Boolean = true,
        val sharp: Boolean = true,
        /** The still chevron's pointing: 0 straight up .. 1 the diagonal ("Sempre" far away). */
        val lean: Float = 1f,
        /** "Sempre" far away: the road's bend ahead (+ right), so the arrow follows the curves. */
        val roadDeg: Float = 0f,
        /** The turn's size (degrees, from the map when it has it): a soft one's strip takes this angle. */
        val turnDeg: Float = 45f
    )

    /** Radar alert for the HUD (see RadarManager): metres to the camera, its limit, over it. */
    data class Radar(val distanceM: Int, val limitKmh: Int?, val over: Boolean)

    // Radar card in the turn-by-turn area: a speed-limit sign (red ring, limit inside, a small
    // camera badge on its lower right) with the distance under it, centred like the big arrow.
    const val RADAR_SIGN = 62f
    const val RADAR_RING = 6f
    const val RADAR_LIMIT_SIZE = 26f
    const val RADAR_LIMIT_SIZE_3DIGITS = 21f
    const val RADAR_BADGE = 24f
    const val RADAR_TEXT_GAP = 26f // sign bottom to distance baseline
    const val RADAR_VALUE_SIZE = 22f
    const val RADAR_UNIT_SIZE = 14f
    const val RADAR_TOP = TBT_TOP + (TBT_BOTTOM - TBT_TOP - (RADAR_SIGN + RADAR_TEXT_GAP)) / 2

    /** "350" + "m", or "1.2" + "km" from 1 km, as the turn-by-turn distance reads. */
    fun radarDistance(meters: Int): Pair<String, String> {
        val m = meters.coerceAtLeast(0)
        if (m < 1_000) return ((m / 10.0).roundToInt() * 10).toString() to "m"
        val tenths = (m / 100.0).roundToInt()
        return "${tenths / 10}.${tenths % 10}" to "km"
    }

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

    /** Power/RPM indicators: none, bars at the bottom corners, or arcs round the native speed. */
    enum class Gauges(val key: String) {
        NONE("none"), BARS("bars"), ARCS("arcs");

        companion object {
            fun fromKey(key: String?): Gauges? = entries.firstOrNull { it.key == key }
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
        /** Power/RPM indicators: none, bottom bars, or arcs round the native speed (one for every style). */
        val gauges: Gauges = Gauges.BARS,
        /** AR: animated chevrons above the speed when close to a turn. */
        val chevrons: Boolean = true,
        /** ...and one still chevron by the arcs at any distance, pointing to the next road. */
        val chevronsAlways: Boolean = false,
        /**
         * ...even while the next maneuver is hidden (further than [showTurnFromM]); with
         * [chevronsAlways] (owner, 2026-10-26: "Sempre", the old "Sempre" became "Com próxima manobra").
         */
        val chevronsEvenHidden: Boolean = false,
        /** AR: arrow in the centre (under the speed) or between the native lane-assist lines. */
        val arPlacement: HudArArrow.Placement = HudArArrow.Placement.CENTER,
        /** AR: real streets round the car from OpenStreetMap, in light grey (beta, off by default). */
        val mapStreets: Boolean = false,
        /** AR map: the grey streets as their edge lines only, instead of filled (owner option). */
        val mapEdges: Boolean = false,
        /**
         * AR map (owner, 2026-10-28, Estilo tab): tilt (more = seen more from above) and zoom
         * (less = the map further away, more of it); 1 = the original view.
         */
        val mapTilt: Float = 1f,
        val mapZoom: Float = 1f,
        /** The HUD's text face (owner, 2026-10-24: Leve by default; thin was too thin). */
        val font: Font = Font.LIGHT,
        /** Maneuver icons: our bold arrows, or Google's (Material Symbols) as in Google Maps. */
        val iconStyle: IconStyle = IconStyle.CLASSIC,
        /**
         * Next maneuver (icon, distance, street, lanes) only from this many metres; further away
         * just the trip line. 0 = always (see [SHOW_TURN_FROM_OPTIONS]). Default 1 km (owner,
         * 2026-10-25); a saved choice keeps its own value.
         */
        val showTurnFromM: Int = 1000,
        /** HUD colours (owner, 2026-10-26; the Cores tab). */
        val colors: Colors = Colors()
    ) {
        val active: Boolean get() = layout != Layout.ORIGINAL

        /** Arcs round the speed. */
        val arcs: Boolean get() = gauges == Gauges.ARCS
    }

    /**
     * The HUD styles in Telas (owner, 2026-10-27): fixed sets of every option, plus the owner's
     * own ([CUSTOM], "Personalizado"), made by changing any of them. The 3D arrows without the
     * map are no longer offered: "Mapa 3D" always has the map (the arrows stay only as what it
     * draws while the streets load or without internet).
     */
    enum class Preset(val key: String) {
        ORIGINAL("original"),
        CUSTOM("custom"),
        MAP("map"),
        BASIC("basic"),
        DISCREET("discreet");

        /** The style's options; null for [CUSTOM] (saved by the owner). */
        val config: Config? get() = when (this) {
            ORIGINAL -> Config(layout = Layout.ORIGINAL)
            CUSTOM -> null
            // The owner's car on 2026-10-27, made the map style.
            MAP -> Config(
                layout = Layout.AR,
                gauges = Gauges.ARCS,
                chevronsAlways = true,
                chevronsEvenHidden = true,
                mapStreets = true,
                mapEdges = true,
                iconStyle = IconStyle.GOOGLE,
                colors = Colors(
                    drive = COLOR_CHEVRON,
                    rpm = rgb(0x83, 0x87, 0x8D),
                    route = rgb(0x00, 0x34, 0xFF),
                    chevron = rgb(0x47, 0x98, 0xDC)
                )
            )
            // The old Navegação layout with its defaults.
            BASIC -> Config(layout = Layout.NAVIGATION, gauges = Gauges.BARS)
            // The least on the glass: the turn only from 300 m, no indicators, all blue (the trip
            // line comes with navigation since 2026-10-28, owner: no switch for it any more).
            DISCREET -> Config(
                layout = Layout.NAVIGATION,
                gauges = Gauges.NONE,
                showTurnFromM = 300,
                iconStyle = IconStyle.GOOGLE,
                colors = Colors(mode = ColorMode.BLUE)
            )
        }

        companion object {
            fun fromKey(key: String?): Preset? = entries.firstOrNull { it.key == key }

            /** Styles with fixed options, in the Telas order (Personalizado goes after Original). */
            val FIXED = listOf(MAP, BASIC, DISCREET)
        }
    }

    /** Choices for [Config.showTurnFromM]: always, then 200 m .. 1 km. */
    val SHOW_TURN_FROM_OPTIONS = listOf(0, 200, 300, 500, 1000)

    /** True when [config] hides a maneuver [meters] away (unknown distance: shown). */
    fun turnHidden(config: Config, meters: Int?): Boolean =
        config.showTurnFromM > 0 && meters != null && meters > config.showTurnFromM

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

    /**
     * The AR map view: with [streets] loaded, the route on the real roads (or the plain fallback
     * shape at the same scale). Roundabouts stay on the map too, so the blue line doesn't vanish
     * on the ring.
     */
    fun withMap(tbt: Tbt, streets: List<br.com.redesurftank.havalshisuku.map.LocalRoad>): Tbt {
        if (streets.isEmpty()) return tbt
        val turn = if (tbt.arDestination != null) 0f else tbt.arAngle
        val rp = br.com.redesurftank.havalshisuku.map.RoutePath
        // Roundabouts (owner, 2026-10-26): round the ring and out of our exit, not just an arrow.
        // On the ring AA's distance is to the exit: follow the ring there and turn out.
        val ring = if (!tbt.dynamicRoundabout) null else if (tbt.rbProgress > 0.02f || tbt.onRoundabout) {
            rp.onRing(streets, tbt.guideMeters ?: tbt.arMeters, tbt.roundaboutClockwise)
                ?: rp.route(streets, tbt.guideMeters ?: tbt.arMeters, if (tbt.roundaboutClockwise) -RING_EXIT_TURN_DEG else RING_EXIT_TURN_DEG)
        } else {
            rp.roundabout(streets, tbt.arMeters, tbt.exitAngle!!.toFloat(), tbt.roundaboutClockwise, tbt.exitNumber)
        }
        // Straight on (a name change, a merge, a junction passed straight): the road ahead, not
        // a stub that ends at the junction (owner, 2026-10-26: round the São Francisco tunnel
        // the map turned into a short arrow). A destination still ends where it is.
        val straight = tbt.arDestination == null && !tbt.dynamicRoundabout && kotlin.math.abs(turn ?: 0f) < STRAIGHT_ROUTE_DEG
        val meters = if (straight) null else tbt.arMeters
        val route = ring
            ?: rp.route(streets, meters, turn)
            ?: rp.synthetic(meters, turn)
        return tbt.copy(streets = streets, route = route.pts, routeMatched = route.matched, routeClear = route.nextOnSide)
    }

    fun tbtFor(d: AndroidAutoNavigationTelemetry.Directions, config: Config = Config()): Tbt? {
        if (!d.active) return null
        val (value, unit) = splitDistance(d.distance, d.distanceM)
        val lanes = d.lanes.map { laneArrows(it) }
        val meters = distanceMeters(d.distance, d.distanceM)
        val icon = hudIcon(d.turn)
        // The trip's first step, "head towards ...", always comes at 0 m: "^ 0 m" read as a
        // turn right here (owner, 2026-10-27). Only the trip line until the first real maneuver.
        val hidden = turnHidden(config, meters) || d.turn.equals("DEPART", ignoreCase = true)
        val exitAngle = if (icon == "ROUNDABOUT") drawableExitAngle(d.turnAngle) else null
        // AR: turns by their bend angle; a roundabout with a known exit gets the 3D ring (angle 0
        // here, the renderer draws it from exitAngle). A maneuver with no shape (a merge with no
        // side, a ferry, a roundabout without its exit) or no distance yet (AA sends the new step
        // before its distance) stays in AR, straight on: dropping to the plain row took the 3D
        // map away at junctions (owner, 2026-10-25).
        val arAngle = if (config.layout != Layout.AR) null else HudArArrow.angleFor(d.turn) ?: 0f
        val style = when {
            // AR keeps the centre arrow with lanes too; the lanes go to the left column.
            arAngle != null -> TbtStyle.AR
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
            // Last section (owner): the next maneuver is the destination, so the trip totals
            // would only repeat the section's distance; the flag by the distance says it instead.
            // With the maneuver hidden the trip line is all there is, so it stays.
            trip = if (config.trip && (icon != "DESTINATION" || hidden)) tripLabel(d.remainingM, d.remainingS) else "",
            turnHidden = hidden,
            googleIcon = if (config.iconStyle == IconStyle.GOOGLE) googleIcon(d.turn, d.roundaboutClockwise == true) else null,
            tripDistance = if (config.trip) tripParts(d.remainingM, d.remainingS).first.orEmpty() else "",
            tripTime = if (config.trip) tripParts(d.remainingM, d.remainingS).second.orEmpty() else "",
            arAngle = arAngle.takeIf { style == TbtStyle.AR },
            arMeters = meters?.toFloat().takeIf { style == TbtStyle.AR },
            street = d.street.trim(),
            exitNumber = d.exitNumber?.takeIf { icon == "ROUNDABOUT" && it > 0 },
            exitAngle = exitAngle,
            roundaboutClockwise = d.roundaboutClockwise == true,
            guideMeters = meters?.toFloat(),
            guideAngle = HudArArrow.angleFor(d.turn),
            arDestination = HudArArrow.destinationSide(d.turn).takeIf { style == TbtStyle.AR },
            arPlacement = config.arPlacement
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
        arDistanceKnown: Boolean = true,
        radar: Radar? = null,
        streets: List<br.com.redesurftank.havalshisuku.map.LocalRoad> = emptyList(),
        wideArcs: Boolean = false,
        mapPending: Boolean = false,
        mapProgress: Float = 0f,
        onRoundabout: Boolean = false,
        speedKmh: Double = 1.0,
        scriptedDemo: Boolean = false
    ): Frame {
        // "Original" layout: the window is only up for the radar, so draw nothing else.
        if (!config.active) return Frame(tbt = null, powerPercent = null, rpmTenths = null, radar = radar, colors = config.colors.resolved())
        // Navegação: the camera takes the turn box. Navegação AR keeps the map (the card sits
        // on the left; the centre picture stays).
        val tbt = if (config.navigation && (radar == null || config.layout == Layout.AR)) {
            directions?.let { tbtFor(it, config) }?.let { tbt ->
                val onRing = tbt.dynamicRoundabout && arProgress > 0.02f
                val arrowMeters = when {
                    tbt.style == TbtStyle.AR && !arDistanceKnown -> null
                    arMetersOverride != null -> arMetersOverride
                    else -> tbt.arMeters
                }
                // On the ring the arrow is drawn here (0 m). Chevrons keep AA's distance to the exit.
                val guide = if (onRing) tbt.guideMeters else arrowMeters
                val adjusted = tbt.copy(
                    guideMeters = guide,
                    rbProgress = if (tbt.icon == "ROUNDABOUT") arProgress.coerceIn(0f, 1f) else 0f
                ).let {
                    // A new step before its distance carries the last one's (0 m at the start of a
                    // trip): its arrow and street, no number until AA sends it (owner, 2026-10-27).
                    if (arDistanceKnown) it else it.copy(distanceValue = "", distanceUnit = "")
                }
                if (adjusted.style != TbtStyle.AR) adjusted
                else withMap(
                    adjusted.copy(
                        arMeters = arrowMeters,
                        arProgress = arProgress,
                        // The map's horizon at the top of the HUD (owner: the default, no switch).
                        mapHorizon = HudArArrow.MAP_HORIZON_TOP_Y,
                        mapTilt = config.mapTilt,
                        mapZoom = config.mapZoom,
                        mapPending = config.mapStreets && mapPending && streets.isEmpty() && !tbt.dynamicRoundabout,
                        mapProgress = mapProgress,
                        onRoundabout = onRoundabout && tbt.exitNumber != null,
                        mapEdges = config.mapEdges
                    ),
                    if (config.mapStreets) streets else emptyList()
                )
            }
        } else null
        return Frame(
            tbt = tbt,
            powerPercent = if (config.powerBar && config.gauges != Gauges.NONE) powerPercent else null,
            rpmTenths = if (config.rpmBar && config.gauges != Gauges.NONE) rpmTenths else null,
            arcs = config.arcs,
            radar = radar,
            wideArcs = wideArcs,
            chevrons = if (config.chevrons) {
                chevronsFor(tbt, speedKmh, scriptedDemo, config.chevronsAlways, config.chevronsAlways && config.chevronsEvenHidden)
            } else null,
            font = config.font,
            colors = config.colors.resolved()
        )
    }

    /**
     * Scripted drive for the debug `hudDemo drive`: what the HUD would get [t] seconds into a
     * 74 s loop at [speed] m/s. Right turn from 300 m (engine on, then regen into the turn), a
     * sharp left with lanes from 270 m (into the RPM red zone, then regen), then a roundabout
     * from 200 m taken at the 3rd exit (left, 270° round a counter-clockwise ring). Each leg
     * ends when its distance reaches 0, so Augusta does not sit on the turn after the 300 m start.
     */
    object DemoDrive {
        const val LOOP_MS = 74_000L
        class Step(
            val directions: AndroidAutoNavigationTelemetry.Directions,
            val powerPercent: Int,
            val rpmTenths: Int?
        )

        private fun lane(shape: Int, used: Boolean) =
            listOf(AndroidAutoNavigationTelemetry.LaneDirection(shape, used))

        fun at(t: Float, speed: Float): Step {
            // 300 m at the scripted speed, then the next street. The old 30 s slot
            // (sized for a 450 m start) left Augusta on 0 m for ~10 s.
            val firstLeg = 300f / speed
            val roundaboutAt = firstLeg + 270f / speed
            val destinationAt = roundaboutAt + 18f
            return if (t < firstLeg) {
                val d = (300f - speed * t).coerceAtLeast(0f)
                Step(
                    directions(d, "TURN_RIGHT", "R. Augusta", 5_200f - speed * t, 700 - t.toInt(), emptyList()),
                    powerPercent = when {
                        t < firstLeg - 10f -> 45
                        t < firstLeg - 4f -> 20
                        else -> -40
                    },
                    rpmTenths = if (t < 12f) 28 else null
                )
            } else if (t >= destinationAt) {
                // Arriving: destination on the right.
                val s = t - destinationAt
                val d = (240f - speed * s).coerceAtLeast(0f)
                Step(
                    directions(d, "DESTINATION_RIGHT", "R. Oscar Freire", 240f - speed * s, 30 - s.toInt(), emptyList()),
                    powerPercent = if (s < 8f) 10 else -35,
                    rpmTenths = null
                )
            } else if (t >= roundaboutAt) {
                val s = t - roundaboutAt
                // To the entry, then (as AA does on the ring) the way round to the exit:
                // 270° on a ~15 m ring.
                val travelled = speed * s
                val wayRound = (270f * Math.PI.toFloat() / 180f) * 15f
                val d = if (travelled < 200f) 200f - travelled else (wayRound - (travelled - 200f)).coerceAtLeast(0f)
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
