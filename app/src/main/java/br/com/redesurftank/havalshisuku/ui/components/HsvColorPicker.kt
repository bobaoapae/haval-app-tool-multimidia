package br.com.redesurftank.havalshisuku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Saturation/brightness map plus a hue strip - the shared core of every color picker
 * in the app. Callers own the HSV state and decide what else to show around it
 * (previews, hex readouts, RGB sliders, presets).
 *
 * [onCommit] fires on tap and at the end of a drag rather than on every pointer move,
 * so callers can persist without flooding SharedPreferences (and, for theme settings,
 * without pushing a WebView update per pixel dragged).
 */
@Composable
fun HsvColorPicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onHueChange: (Float) -> Unit,
    onSatValChange: (Float, Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 150.dp,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSatValChange(
                            (offset.x / size.width).coerceIn(0f, 1f),
                            (1f - offset.y / size.height).coerceIn(0f, 1f)
                        )
                        onCommit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onCommit() }) { change, _ ->
                        onSatValChange(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        )
                    }
                }
        ) {
            val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(saturation * size.width, (1f - brightness) * size.height),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                        onCommit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onCommit() }) { change, _ ->
                        onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                    }
                }
        ) {
            val hues = (0..6).map {
                Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)))
            }
            drawRect(Brush.horizontalGradient(hues))
            val markerX = (hue / 360f) * size.width
            drawLine(
                color = Color.White,
                start = Offset(markerX, 0f),
                end = Offset(markerX, size.height),
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}
