package br.com.redesurftank.havalshisuku.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.theme.ImpulseTextSizes
import br.com.redesurftank.havalshisuku.ui.theme.ImpulseTextWeights

/**
 * Preview dinâmico para o diálogo de configuração do Impulse Launcher:
 * - Se nenhum estiver marcado: exibe apenas a imagem do app.
 * - Se a barra inferior estiver marcada (sem gesto): exibe apenas a barra inferior realista na base da tela.
 * - Se o gesto de arrastar para cima estiver marcado: anima o gesto de deslize a partir da barra
 *   e faz a imagem do app surgir suavemente (fade in).
 */
@Composable
fun SwipeUpTutorial(
    enableBar: Boolean = true,
    enableSwipe: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "swipeUpTutorial")
    val t by
        if (enableBar && enableSwipe) {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 2800, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                label = "swipeCycle"
            )
        } else {
            remember { mutableFloatStateOf(0f) }
        }

    // Progresso do gesto de swipe up:
    // 0.00 .. 0.15: dedo surge na barra inferior
    // 0.15 .. 0.45: dedo desliza para cima
    // 0.20 .. 0.50: imagem do app surge (fade in)
    // 0.50 .. 0.78: app totalmente visível
    // 0.78 .. 1.00: fade out e retorno suave
    val swipeProgress = ((t - 0.15f) / 0.30f).coerceIn(0f, 1f)
    val smoothSwipe = swipeProgress * swipeProgress * (3f - 2f * swipeProgress)

    val appAlpha =
        when {
            !enableBar && !enableSwipe -> 1f // Nenhum marcado -> mostra apenas a imagem do app
            !enableSwipe -> 0f // Apenas barra marcada -> app não abre
            else ->
                when {
                    t < 0.20f -> 0f
                    t in 0.20f..0.50f -> ((t - 0.20f) / 0.30f).coerceIn(0f, 1f)
                    t in 0.50f..0.78f -> 1f
                    else -> (1f - (t - 0.78f) / 0.20f).coerceIn(0f, 1f)
                }
        }

    val fingerAlpha =
        when {
            !enableBar || !enableSwipe -> 0f
            t < 0.10f -> (t / 0.10f).coerceIn(0f, 1f)
            t in 0.10f..0.45f -> 1f
            t in 0.45f..0.60f -> (1f - (t - 0.45f) / 0.15f).coerceIn(0f, 1f)
            else -> 0f
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ImpTokens.Hairline, RoundedCornerShape(12.dp))
                .background(Color(0xFF0D0E12))
    ) {
        // 1. Fundo do cockpit / tela da multimídia
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color(0xFF191D27),
                                    Color(0xFF0F1218),
                                    Color(0xFF08090D)
                                )
                        )
                    )
        )

        // 2. Imagem do app (Impulse Launcher)
        if (appAlpha > 0f) {
            Image(
                painter = painterResource(R.drawable.impulse_home_preview),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(appAlpha)
            )
        }

        // 3. Badge de estado superior. A instrução do gesto ("Arraste para abrir o Launcher.") fica
        // fora da ilustração, no SetupOption do diálogo, em tamanho legível.
        if (enableBar && !enableSwipe) {
            Row(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(ImpTokens.Container, RoundedCornerShape(16.dp))
                        .border(1.dp, ImpTokens.Hairline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier =
                        Modifier.size(8.dp)
                            .background(Color(0xFF78E08F), RoundedCornerShape(4.dp))
                )
                Text(
                    "Barra ativa",
                    color = ImpTokens.TextPrimary,
                    fontSize = ImpulseTextSizes.Label,
                    fontWeight = ImpulseTextWeights.Label
                )
            }
        }

        // 4. Gesto de arraste animado (dedo + rastro luminoso)
        if (enableBar && enableSwipe && fingerAlpha > 0f) {
            val barCenterY = 123f
            val targetY = 36f
            val curY = barCenterY - (barCenterY - targetY) * smoothSwipe

            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width * 0.5f
                val startYPx = barCenterY.dp.toPx()
                val curYPx = curY.dp.toPx()
                val trailHeight = (startYPx - curYPx).coerceAtLeast(0f)

                // Rastro vertical ascendente
                if (trailHeight > 4f) {
                    drawRoundRect(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        ImpTokens.Accent.copy(alpha = 0.40f * fingerAlpha),
                                        ImpTokens.Accent.copy(alpha = 0.05f * fingerAlpha)
                                    ),
                                startY = curYPx,
                                endY = startYPx
                            ),
                        topLeft = Offset(centerX - 3.dp.toPx(), curYPx),
                        size = Size(6.dp.toPx(), trailHeight),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }

                // Ponto de toque do gesto
                drawCircle(
                    color = ImpTokens.Accent.copy(alpha = 0.28f * fingerAlpha),
                    radius = 16.dp.toPx(),
                    center = Offset(centerX, curYPx)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.50f * fingerAlpha),
                    radius = 9.dp.toPx(),
                    center = Offset(centerX, curYPx)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f * fingerAlpha),
                    radius = 4.5.dp.toPx(),
                    center = Offset(centerX, curYPx)
                )
            }

            // Seta sutil acompanhando o movimento
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = ImpTokens.Accent.copy(alpha = fingerAlpha),
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = (curY - 24f).dp.toPx()
                            alpha = fingerAlpha
                        }
                        .size(22.dp)
            )
        }

        // 5. Barra Inferior Realista (sem alça horizontal branca fictícia)
        if (enableBar) {
            Box(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(Color(0xFF0F1116))
                        .border(
                            BorderStroke(1.dp, Color(0xFF262A33)),
                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Esquerda: Botão de Apps + Temperatura do motorista
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier =
                                Modifier.size(24.dp)
                                    .background(Color(0xFF1B202A), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Apps,
                                contentDescription = null,
                                tint = ImpTokens.Accent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            "22.0°",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Centro: Controles reais do veículo (sem alça branca)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = Color(0xFFA0A6B2),
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = Color(0xFFA0A6B2),
                            modifier = Modifier.size(16.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color(0xFFA0A6B2),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                "AUTO",
                                color = Color(0xFF78E08F),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Direita: Temperatura do passageiro + Volume
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "22.0°",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFFA0A6B2),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "12",
                                color = Color(0xFFA0A6B2),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
