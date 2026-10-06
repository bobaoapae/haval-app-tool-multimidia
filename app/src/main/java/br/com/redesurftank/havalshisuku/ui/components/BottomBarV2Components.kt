package br.com.redesurftank.havalshisuku.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import br.com.redesurftank.havalshisuku.ui.theme.Michroma
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

// ==========================================================================================
// Bottom bar v2 shared building blocks.
//
// Everything here is only used by the v2 ("Moderna") bar. The classic v1 bar does not touch it.
// Motion is deliberately limited to graphicsLayer / draw-phase reads (no recomposition per frame)
// because the head unit is embedded hardware.
// ==========================================================================================

internal val DockAccentBlue = Color(0xFF2196F3)

/** Standard tile motion duration for appear / indicator transitions. */
internal const val DOCK_ANIM_MS = 200

/** Minimum start margin of the v2 content when the left navigation pane is hidden. */
internal val DOCK_MIN_EDGE_PAD = 16.dp

// ---- Layout budget -----------------------------------------------------------------------

/**
 * Fixed width (dp) of each side cluster. Left: Enviar/Trazer tiles (2 x 52 + 4) + gap ([V2_LEFT_TILES_GAP_DP])
 * + driver temp (172) + 24 + seat (44). Right: seat (44) + 24 + passenger temp (172) + 12 +
 * override (44) + 82 trailing. Both are the same width so the dock is exactly centred.
 */
internal const val V2_SIDE_CLUSTER_WIDTH_DP = 378f

/** Gap after the Enviar/Trazer tiles; absorbs the difference between the two clusters. */
internal const val V2_LEFT_TILES_GAP_DP = 30f

/** Width (dp) of the fan / volume controls (icon + level, no chevrons) between each cluster and the dock. */
internal const val V2_FAN_SLOT_WIDTH_DP = 88f

/** Left cluster + fan control: everything that must fit left of the centred dock. */
internal const val V2_LEFT_GROUP_WIDTH_DP = V2_SIDE_CLUSTER_WIDTH_DP + V2_FAN_SLOT_WIDTH_DP

/** Minimum free space (dp) kept between the left group and the centred dock. */
internal const val V2_MIN_LEFT_CLEARANCE_DP = 12f

/**
 * Estimated width (dp) of the centred v2 dock. Tiles are 46dp (back/apps/vehicle/home) or 50dp
 * (projection + apps), separated by 10dp; the dynamic slot adds a 5dp separator and one tile;
 * edit mode swaps vehicle+home for the ~112dp "Concluir" button.
 */
internal fun estimateDockWidthDp(
        appTiles: Int,
        showProjectionTile: Boolean,
        showDynamicSlot: Boolean,
        editMode: Boolean
): Float {
        var width = 46f + 46f
        var items = 2
        if (showProjectionTile) {
                width += 50f
                items++
        }
        width += appTiles.coerceAtLeast(0) * 50f
        items += appTiles.coerceAtLeast(0)
        if (showDynamicSlot) {
                width += 5f + 50f
                items += 2
        }
        if (editMode) {
                width += 112f
                items += 1
        } else {
                width += 46f + 46f
                items += 2
        }
        return width + 10f * (items - 1)
}

/**
 * Free space (dp) between the end of the left group and the start of the centred dock.
 * [contentWidthDp] is the width of the padded content box the dock is centred in.
 * Negative means the two overlap.
 */
internal fun resolveLeftGroupClearanceDp(
        contentWidthDp: Float,
        dockWidthDp: Float,
        leftGroupWidthDp: Float = V2_LEFT_GROUP_WIDTH_DP
): Float = (contentWidthDp - dockWidthDp) / 2f - leftGroupWidthDp

// ---- Press / selection feedback ------------------------------------------------------------

/** Springy press scale. State is read in the graphicsLayer lambda, so it never recomposes. */
@Composable
internal fun Modifier.dockPressScale(pressed: Boolean, pressedScale: Float = 0.92f): Modifier {
        val scale = animateFloatAsState(
                targetValue = if (pressed) pressedScale else 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 700f),
                label = "dockPressScale"
        )
        return this.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
        }
}

/** Tint that eases between idle and active instead of flipping instantly. */
@Composable
internal fun animatedDockTint(
        active: Boolean,
        activeColor: Color = DockAccentBlue,
        idleColor: Color = Color.White.copy(alpha = 0.90f)
): Color {
        val tint by animateColorAsState(
                targetValue = if (active) activeColor else idleColor,
                animationSpec = tween(durationMillis = 150),
                label = "dockTint"
        )
        return tint
}

/** Blue underline marking the foreground app; grows / shrinks from the centre. */
@Composable
internal fun DockActiveIndicator(active: Boolean, modifier: Modifier = Modifier) {
        val width by animateDpAsState(
                targetValue = if (active) 18.dp else 0.dp,
                animationSpec = tween(durationMillis = DOCK_ANIM_MS, easing = FastOutSlowInEasing),
                label = "dockIndicatorWidth"
        )
        if (width > 0.dp) {
                Box(
                        modifier = modifier
                                .size(width = width, height = 2.5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(DockAccentBlue)
                )
        }
}

// ---- Dock edit mode ----------------------------------------------------------------------

/**
 * Rotation (degrees) for the edit-mode wobble. The infinite transition only exists while
 * [enabled], so the idle bar does not tick a frame clock. [phaseMs] de-syncs neighbouring tiles.
 */
@Composable
internal fun rememberDockJiggle(enabled: Boolean, phaseMs: Int = 0): State<Float> {
        if (!enabled) return remember { mutableStateOf(0f) }
        val transition = rememberInfiniteTransition(label = "dockJiggle")
        return transition.animateFloat(
                initialValue = -1.1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 170, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(phaseMs)
                ),
                label = "jiggleRotation"
        )
}

/** One-shot fade + scale-in for tiles that join the dock (edit slots, dynamic app). */
@Composable
internal fun Modifier.dockAppear(): Modifier {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
                progress.animateTo(1f, tween(DOCK_ANIM_MS, easing = FastOutSlowInEasing))
        }
        return this.graphicsLayer {
                val p = progress.value
                alpha = p
                scaleX = 0.8f + 0.2f * p
                scaleY = 0.8f + 0.2f * p
        }
}

/** Round edit-mode badge (remove / pin) with a 24dp touch area and a pop-in animation. */
@Composable
internal fun DockEditBadge(
        visible: Boolean,
        color: Color,
        icon: ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        AnimatedVisibility(
                visible = visible,
                enter = scaleIn(tween(DOCK_ANIM_MS), initialScale = 0.4f) + fadeIn(tween(DOCK_ANIM_MS)),
                exit = scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(120)),
                modifier = modifier
        ) {
                Box(
                        modifier = Modifier.size(24.dp).clickable(onClick = onClick),
                        contentAlignment = Alignment.Center
                ) {
                        Box(
                                modifier = Modifier.size(18.dp).clip(CircleShape).background(color),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = contentDescription,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                )
                        }
                }
        }
}

// ---- Fan icon -----------------------------------------------------------------------------

private const val FAN_LEVELS = 7

// Swirl 3-blade rotor: wide curved blades (64-unit grid, hub at the origin, blade pointing up).
private const val FAN_BLADE = "M0 -4 C-9 -8 -11 -20 -2 -26.5 C7 -31 13 -22 9 -14.5 C7 -10 4 -7 0 -4 Z"

/**
 * CoffeeOS-style fan: three wide swirl blades inside a ring of 7 segments, one per strength level. The
 * segments fill in order up to [speed] (0..7), the rest stay dim; level 0 dims everything. The ring
 * never rotates; only the blades do ([rotationDegrees] is a lambda so a spinning fan only re-runs the
 * draw phase). Paths are built once per size ([drawWithCache]); the ring fill eases between levels.
 */
@Composable
fun CoffeeOsFanIcon(
        speed: Int = 7,
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = Color.White,
        rotationDegrees: () -> Float = { 0f }
) {
        val level = animateFloatAsState(
                targetValue = speed.coerceIn(0, FAN_LEVELS).toFloat(),
                animationSpec = tween(durationMillis = 180),
                label = "fanLevel"
        )
        val isOff = speed <= 0
        Box(
                modifier = modifier.drawWithCache {
                        val u = size.minDimension / 64f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val blade = PathParser().parsePathString(FAN_BLADE).toPath()
                        val ringRadius = 29f * u
                        val ringStroke = Stroke(width = 3.4f * u, cap = StrokeCap.Round)
                        val ringTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
                        val ringSize = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2)
                        onDrawBehind {
                                // Level ring: 7 arcs, starting at 12 o'clock, clockwise.
                                val gap = 11f
                                val sweep = (360f - gap * FAN_LEVELS) / FAN_LEVELS
                                for (i in 0 until FAN_LEVELS) {
                                        val fill = (level.value - i).coerceIn(0f, 1f)
                                        drawArc(
                                                color = tint.copy(alpha = tint.alpha * (FAN_DIM_ALPHA + (1f - FAN_DIM_ALPHA) * fill)),
                                                startAngle = -90f + gap / 2f + i * (sweep + gap),
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                topLeft = ringTopLeft,
                                                size = ringSize,
                                                style = ringStroke
                                        )
                                }
                                val bladeColor = tint.copy(alpha = tint.alpha * if (isOff) 0.45f else 1f)
                                withTransform({
                                        translate(center.x, center.y)
                                        scale(u * 0.80f, u * 0.80f, pivot = Offset.Zero)
                                        rotate(degrees = rotationDegrees(), pivot = Offset.Zero)
                                }) {
                                        for (b in 0 until 3) {
                                                rotate(degrees = b * 120f, pivot = Offset.Zero) {
                                                        drawPath(blade, bladeColor)
                                                }
                                        }
                                        drawCircle(bladeColor, radius = 5.4f, center = Offset.Zero)
                                }
                        }
                }
        )
}

private const val FAN_DIM_ALPHA = 0.26f

/** How long the blades keep spinning after the fan is used. */
private const val FAN_SPIN_BURST_MS = 3000L

/**
 * Fan icon used by the v2 fan control. The blades only spin for a few seconds after the fan is
 * used (level change, power change, or [kick] bumped by a tap), then glide to rest on a
 * blade-symmetric angle. The first composition (bar just shown) never spins. The ring stays still.
 */
@Composable
internal fun SpinningFanIndicator(
        speed: Int,
        isPowerOn: Boolean,
        tint: Color,
        kick: Int = 0,
        modifier: Modifier = Modifier
) {
        val rotation = remember { Animatable(0f) }
        var primed by remember { mutableStateOf(false) }
        LaunchedEffect(speed, isPowerOn, kick) {
                val shouldSpin = primed && isPowerOn && speed > 0
                primed = true
                if (shouldSpin) {
                        val periodMs = (2600 - speed.coerceIn(1, FAN_LEVELS) * 320).coerceAtLeast(450)
                        withTimeoutOrNull(FAN_SPIN_BURST_MS) {
                                while (true) {
                                        rotation.animateTo(rotation.value + 360f, tween(periodMs, easing = LinearEasing))
                                }
                        }
                }
                // Blades are 90-degree symmetric: settle on the next multiple of 90.
                val rest = ceil(rotation.value / 90f) * 90f
                if (rest != rotation.value) {
                        rotation.animateTo(rest, tween(if (shouldSpin) 700 else 300, easing = LinearOutSlowInEasing))
                }
                rotation.snapTo(rest % 360f)
        }
        CoffeeOsFanIcon(
                speed = if (isPowerOn) speed else 0,
                modifier = modifier.size(38.dp),
                tint = tint,
                rotationDegrees = { rotation.value }
        )
}

/**
 * Fan level set as a subscript at the icon's lower-right corner (0 -> "OFF"). Changing it gives feedback: the value cross-fades, pops
 * (spring scale) and flashes the accent colour for a moment. The first composition stays quiet.
 */
@Composable
internal fun FanLevelLabel(speed: Int, isPowerOn: Boolean, color: Color, modifier: Modifier = Modifier) {
        val text = if (!isPowerOn || speed == 0) "OFF" else "$speed"
        var seen by remember { mutableStateOf(false) }
        var flash by remember { mutableStateOf(false) }
        val pop = remember { Animatable(1f) }
        LaunchedEffect(text) {
                if (seen) {
                        flash = true
                        pop.snapTo(1.35f)
                        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 450f))
                        kotlinx.coroutines.delay(250)
                        flash = false
                } else {
                        seen = true
                }
        }
        val shown by animateColorAsState(
                targetValue = if (flash) DockAccentBlue else color,
                animationSpec = tween(durationMillis = 140),
                label = "fanLevelColor"
        )
        Box(modifier = modifier.size(width = 30.dp, height = 14.dp), contentAlignment = Alignment.BottomStart) {
                AnimatedContent(
                        targetState = text,
                        transitionSpec = { (fadeIn(tween(120)) togetherWith fadeOut(tween(90))).using(SizeTransform(clip = false)) },
                        label = "fanLevelLabel"
                ) { value ->
                        Text(
                                text = value,
                                maxLines = 1,
                                softWrap = false,
                                style = TextStyle(
                                        fontFamily = Michroma,
                                        fontSize = if (value == "OFF") 8.sp else 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = shown
                                ),
                                modifier = Modifier.graphicsLayer {
                                        scaleX = pop.value
                                        scaleY = pop.value
                                }
                        )
                }
        }
}

// ---- Enviar / Trazer ----------------------------------------------------------------------

/** Compact width (dp) of an Enviar/Trazer tile; the wide width is this plus half of the left gap. */
internal const val V2_TRANSFER_COMPACT_WIDTH_DP = 52f

/** Smallest left gap (dp) that is worth widening the tiles into. */
internal const val V2_MIN_WIDE_GAP_DP = 28f

/**
 * Moves the foreground app between the main display and the cluster.
 * [ChevronDirection.LEFT] = "Enviar" (towards the cluster), [ChevronDirection.RIGHT] = "Trazer".
 *
 * [widthProgress] morphs the tile: 0 = compact (arrow with its caption underneath, 52dp), 1 = wide
 * ([wideWidthDp]: arrow beside the caption). Wide is used when the left navigation pane is hidden; compact
 * when the pane or Android Auto / CarPlay takes the left of the screen. The arrow nudges in the
 * direction of travel on tap and when Trazer becomes available.
 */
@Composable
internal fun ClusterTransferTile(
        direction: ChevronDirection,
        enabled: Boolean,
        description: String,
        widthProgress: Float,
        wideWidthDp: Float,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val pressBg by animateColorAsState(
                targetValue = if (pressed && enabled) DockAccentBlue.copy(alpha = 0.35f) else Color.Transparent,
                animationSpec = tween(durationMillis = if (pressed) 50 else 300),
                label = "transferBg"
        )
        val tint = animatedDockTint(
                active = pressed && enabled,
                idleColor = if (enabled) Color.White.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.28f)
        )

        val scope = rememberCoroutineScope()
        val sweep = remember { Animatable(0f) }
        suspend fun playSweep() {
                sweep.snapTo(0f)
                sweep.animateTo(1f, tween(420, easing = LinearEasing))
                sweep.snapTo(0f)
        }
        var wasEnabled by remember { mutableStateOf(enabled) }
        LaunchedEffect(enabled) {
                if (enabled && !wasEnabled) playSweep()
                wasEnabled = enabled
        }

        val progress = widthProgress.coerceIn(0f, 1f)
        val toCluster = direction == ChevronDirection.LEFT
        val tileWidth = (V2_TRANSFER_COMPACT_WIDTH_DP + (wideWidthDp - V2_TRANSFER_COMPACT_WIDTH_DP) * progress).dp
        val caption = if (toCluster) "ENVIAR" else "TRAZER"
        val captionStyle = TextStyle(
                fontFamily = Michroma,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = tint
        )

        Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                        .dockPressScale(pressed && enabled)
                        .size(width = tileWidth, height = 46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(pressBg)
                        .semantics { this.contentDescription = description }
                        .clickable(
                                enabled = enabled,
                                interactionSource = interaction,
                                indication = null
                        ) {
                                scope.launch { playSweep() }
                                onClick()
                        }
        ) {
                if (progress < 0.99f) {
                        // Compact: long arrow with the caption underneath.
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.graphicsLayer { alpha = 1f - progress }
                        ) {
                                TransferArrow(toCluster, tint, { sweep.value }, Modifier.size(width = 30.dp, height = 18.dp))
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(text = caption, maxLines = 1, softWrap = false, style = captionStyle)
                        }
                }
                if (progress > 0.01f) {
                        // Wide: arrow beside the caption.
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.graphicsLayer { alpha = progress }
                        ) {
                                val arrow = @Composable {
                                        TransferArrow(toCluster, tint, { sweep.value }, Modifier.size(width = 28.dp, height = 20.dp))
                                }
                                if (toCluster) {
                                        arrow()
                                        Text(text = caption, maxLines = 1, softWrap = false, style = captionStyle.copy(fontSize = 10.sp))
                                } else {
                                        Text(text = caption, maxLines = 1, softWrap = false, style = captionStyle.copy(fontSize = 10.sp))
                                        arrow()
                                }
                        }
                }
        }
}

/** Long arrow (shaft + open head) pointing left for "Enviar" and right for "Trazer"; nudges while [sweep] plays. */
@Composable
private fun TransferArrow(
        toCluster: Boolean,
        tint: Color,
        sweep: () -> Float,
        modifier: Modifier = Modifier
) {
        Canvas(
                modifier = modifier.graphicsLayer {
                        val nudge = kotlin.math.sin(Math.PI.toFloat() * sweep()) * 4.dp.toPx()
                        translationX = if (toCluster) -nudge else nudge
                }
        ) {
                val w = size.width
                val h = size.height
                val stroke = 2.4.dp.toPx()
                val edge = 1.2.dp.toPx()
                val tail = if (toCluster) w - edge else edge
                val tip = if (toCluster) edge else w - edge
                val headDx = (if (toCluster) 1f else -1f) * minOf(7.dp.toPx(), w * 0.32f)
                val headDy = minOf(6.dp.toPx(), h * 0.34f)
                drawLine(tint, Offset(tail, h / 2f), Offset(tip, h / 2f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawPath(
                        Path().apply {
                                moveTo(tip + headDx, h / 2f - headDy)
                                lineTo(tip, h / 2f)
                                lineTo(tip + headDx, h / 2f + headDy)
                        },
                        color = tint,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
        }
}

// ---- Drive-mode wheel ---------------------------------------------------------------------

/** Badge shown on the vehicle-modes button for the current drive mode. Normal has no badge. */
internal enum class DriveModeBadge(val label: String) {
        ECO("Eco"),
        SPORT("Sport"),
        SNOW("Neve"),
        SAND("Areia"),
        MUD("Lama")
}

/** Drive-mode values as used by the settings menu: 0 Normal, 1 Sport, 2 Eco, 3 Neve, 4 Areia, 5 Lama. */
internal fun resolveDriveModeBadge(driveMode: String?): DriveModeBadge? = when (driveMode) {
        "1" -> DriveModeBadge.SPORT
        "2" -> DriveModeBadge.ECO
        "3" -> DriveModeBadge.SNOW
        "4" -> DriveModeBadge.SAND
        "5" -> DriveModeBadge.MUD
        else -> null
}

internal fun driveModeDescription(driveMode: String?): String =
        "Modos do veículo" + (resolveDriveModeBadge(driveMode)?.let { ": ${it.label}" } ?: "")

// Symbol outlines, centred on (0, 0) in a +-7 unit box (SVG path syntax).
private const val SYMBOL_FLAME =
        "M0 -6.8 C3.6 -3.2 5 -0.6 5 2 A5 5 0 0 1 -5 2 C-5 0 -3.7 -1.4 -2.5 -2.5 C-2.3 -0.6 -1.4 0.3 -0.4 0.3 C-1 -2.3 -0.7 -4.6 0 -6.8 Z"
private const val SYMBOL_LEAF =
        "M-4.8 4.8 C-5.8 -2.5 -0.8 -6 5.2 -5.6 C5.6 0.6 1.9 5.6 -4.8 4.8 Z"
private const val SYMBOL_DUNES = "M-6.8 4.6 L-2.2 -2.4 L1.4 2.4 L3.6 -0.4 L6.8 4.6 Z"
private const val SYMBOL_DROP =
        "M0 -6.6 C3.2 -2.2 4.8 0 4.8 2.6 A4.8 4.8 0 0 1 -4.8 2.6 C-4.8 0 -3.2 -2.2 0 -6.6 Z"

/**
 * Steering wheel for the vehicle-modes button. The wheel is the fixed identity of the button; a
 * small badge in the top-right corner tells the current drive mode (none for Normal). The wheel
 * eases towards the lower-left while a badge is present so the pair stays visually balanced, and
 * the badge pops in/out when the mode changes.
 */
@Composable
internal fun DriveModeWheelIcon(
        driveMode: String?,
        tint: Color,
        modifier: Modifier = Modifier
) {
        val badge = resolveDriveModeBadge(driveMode)
        val shift = animateFloatAsState(
                targetValue = if (badge != null) 1f else 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "wheelShift"
        )
        Box(
                modifier = modifier
                        .size(30.dp)
                        .semantics { this.contentDescription = driveModeDescription(driveMode) }
        ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                        val u = size.minDimension / 64f
                        val p = shift.value
                        val cx = (32f - 2.5f * p) * u
                        val cy = (32f + 2.5f * p) * u
                        val scale = (1f - 0.08f * p)
                        val r = 24f * u * scale
                        val strokeW = 4.4f * u * scale
                        val c = Offset(cx, cy)
                        drawCircle(tint, radius = r, center = c, style = Stroke(width = strokeW))
                        val spokes = Path().apply {
                                moveTo(cx - 24.5f * u * scale, cy - 1.5f * u * scale)
                                quadraticTo(cx - 12.5f * u * scale, cy - 4.5f * u * scale, cx - 6.2f * u * scale, cy - 0.6f * u * scale)
                                moveTo(cx + 24.5f * u * scale, cy - 1.5f * u * scale)
                                quadraticTo(cx + 12.5f * u * scale, cy - 4.5f * u * scale, cx + 6.2f * u * scale, cy - 0.6f * u * scale)
                                moveTo(cx, cy + 7f * u * scale)
                                lineTo(cx, cy + 24.5f * u * scale)
                        }
                        drawPath(spokes, tint, style = Stroke(width = strokeW, cap = StrokeCap.Round))
                        drawCircle(tint, radius = 7f * u * scale, center = c)
                        // 12 o'clock marker.
                        drawLine(
                                DockAccentBlue,
                                Offset(cx, cy - r - strokeW * 0.45f),
                                Offset(cx, cy - r + strokeW * 0.45f),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                        )
                }
                AnimatedContent(
                        targetState = badge,
                        transitionSpec = {
                                (scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.3f) + fadeIn(tween(120))) togetherWith
                                        (scaleOut(tween(110), targetScale = 0.3f) + fadeOut(tween(110)))
                        },
                        modifier = Modifier.align(Alignment.TopEnd),
                        label = "driveModeBadge"
                ) { current ->
                        if (current == null) {
                                Box(modifier = Modifier.size(15.dp))
                        } else {
                                DriveModeBadgeGlyph(current, Modifier.size(15.dp))
                        }
                }
        }
}

@Composable
private fun DriveModeBadgeGlyph(badge: DriveModeBadge, modifier: Modifier = Modifier) {
        Canvas(modifier = modifier) {
                val u = size.minDimension / 30f
                val c = Offset(size.width / 2f, size.height / 2f)
                // Black separator ring so the badge reads as cut out of the wheel.
                drawCircle(Color.Black, radius = size.minDimension / 2f, center = c)
                drawCircle(DockAccentBlue, radius = size.minDimension / 2f - 1.4f * u, center = c)
                withTransform({
                        translate(c.x, c.y)
                        scale(u * 1.35f, u * 1.35f, pivot = Offset.Zero)
                }) {
                        when (badge) {
                                DriveModeBadge.SPORT -> drawPath(parseSymbol(SYMBOL_FLAME), Color.White)
                                DriveModeBadge.ECO -> {
                                        drawPath(parseSymbol(SYMBOL_LEAF), Color.White)
                                        drawLine(DockAccentBlue, Offset(-4.4f, 4.4f), Offset(1.4f, -1.4f), strokeWidth = 1.1f, cap = StrokeCap.Round)
                                }
                                DriveModeBadge.SAND -> drawPath(parseSymbol(SYMBOL_DUNES), Color.White)
                                DriveModeBadge.MUD -> drawPath(parseSymbol(SYMBOL_DROP), Color.White)
                                DriveModeBadge.SNOW -> {
                                        val arm = 6f
                                        for (a in 0 until 3) {
                                                val ang = Math.toRadians(a * 60.0 + 90.0)
                                                val dx = (kotlin.math.cos(ang) * arm).toFloat()
                                                val dy = (kotlin.math.sin(ang) * arm).toFloat()
                                                drawLine(Color.White, Offset(-dx, -dy), Offset(dx, dy), strokeWidth = 1.7f, cap = StrokeCap.Round)
                                        }
                                }
                        }
                }
        }
}

private fun parseSymbol(data: String): Path = PathParser().parsePathString(data).toPath()

// ---- Car writes off the main thread -----------------------------------------------------------

private val carWriteExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BottomBarCarWrite").apply { isDaemon = true }
}

/**
 * Sends a car write from a single background thread, in call order.
 *
 * [ServiceManager.updateData] is synchronous: for HVAC keys the first write also checks the
 * foreground app, runs `pm disable-user` / `am force-stop` through Shizuku and sleeps 150 ms before
 * the request goes out. Called from a tap or a drag it froze the bar, so the optimistic UI update
 * (already applied by the caller) was not drawn until the car call returned. One thread keeps
 * related writes ordered (for example fan speed, then power).
 */
internal fun br.com.redesurftank.havalshisuku.managers.ServiceManager.updateDataAsync(key: String, value: String) {
        carWriteExecutor.execute {
                try {
                        updateData(key, value)
                } catch (t: Throwable) {
                        android.util.Log.e("BottomBarV2", "Async car write failed for $key", t)
                }
        }
}
