package br.com.redesurftank.havalshisuku.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens

/**
 * O gesto, em loop: a barra inferior sobe com o dedo e o Impulse Home entra por baixo.
 *
 * Desenhado em Compose de proposito, e nao gravado do emulador. Um GIF gravado envelhece junto com
 * a interface (basta a barra mudar de cor para o tutorial virar mentira), pesa no APK e fica
 * borrado quando esticado no painel de 1920. Isto sao poucos KB de codigo que escalam sozinhos e
 * continuam certos enquanto o GESTO for o mesmo - e o gesto e a unica coisa que o tutorial ensina.
 *
 * O ciclo tem quatro tempos em 2,4 s. A pausa no fim existe para o olho recomecar a ler, mas e
 * curta de proposito: medido em capturas do emulador, uma pausa maior fazia dois tercos dos quadros
 * serem a mesma imagem parada, e o gesto - a unica coisa que o tutorial ensina - virava a excecao.
 *
 * | fracao | o que acontece |
 * |---|---|
 * | 0,00-0,10 | parado, so a barra embaixo; o dedo aparece |
 * | 0,10-0,62 | o dedo sobe, a barra acompanha e some, o app entra por baixo |
 * | 0,62-0,76 | app inteiro na tela, o dedo some |
 * | 0,76-1,00 | pausa antes de recomecar |
 */
@Composable
fun SwipeUpTutorial(modifier: Modifier = Modifier) {
    val cycle = rememberInfiniteTransition(label = "swipe-up")
    val t by
            cycle.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation =
                                            tween(durationMillis = 2400, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "t"
            )

    // Quanto do gesto ja foi feito (0 = barra embaixo, 1 = app inteiro na tela). Suavizado com
    // smoothstep para o movimento comecar e terminar macio sem precisar de keyframes.
    val raw = ((t - 0.10f) / 0.52f).coerceIn(0f, 1f)
    val progress = raw * raw * (3f - 2f * raw)
    val fingerAlpha =
            when {
                t < 0.08f -> (t / 0.08f).coerceIn(0f, 1f)
                t > 0.66f -> (1f - (t - 0.66f) / 0.10f).coerceIn(0f, 1f)
                else -> 1f
            }

    BoxWithConstraints(
            modifier =
                    modifier.fillMaxWidth()
                            .height(132.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ImpTokens.Ground)
    ) {
        val h = maxHeight
        val barHeight = 18.dp
        // A tela do app entra de baixo: fora da vista no inicio, encaixada no fim.
        val appOffset = h * (1f - progress)
        // A barra sobe junto, mas so ate a metade da altura: ela e a alca do gesto, e uma barra
        // encostada no topo deixa de ser lida como barra inferior - vira a moldura do quadro.
        // Passado o curso, ela desaparece, que e o que acontece no carro quando o app assume.
        val barLift = (h - barHeight) * 0.55f * progress
        val barAlpha = (1f - ((progress - 0.70f) / 0.30f)).coerceIn(0f, 1f)

        Image(
                painter = painterResource(R.drawable.impulse_home_preview),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            translationY = appOffset.toPx()
                            alpha = 0.35f + 0.65f * progress
                        }
        )

        // Barra inferior do Impulse: um tracinho claro, como no carro.
        Box(
                modifier =
                        Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(barHeight)
                                .graphicsLayer {
                                    translationY = -barLift.toPx()
                                    alpha = barAlpha
                                }
                                .background(Color(0xFF1B1E25))
        ) {
            Box(
                    modifier =
                            Modifier.align(Alignment.Center)
                                    .fillMaxWidth(0.34f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ImpTokens.TextSecondary)
            )
        }

        // O dedo: um circulo com um rastro curto atras, subindo com a barra.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fa = fingerAlpha * barAlpha
            if (fa <= 0f) return@Canvas
            val x = size.width * 0.5f
            val bottom = size.height - barHeight.toPx() / 2f
            val y = bottom - barLift.toPx()
            drawRoundRect(
                    color = ImpTokens.Accent.copy(alpha = 0.18f * fa),
                    topLeft = Offset(x - 3.dp.toPx(), y),
                    size = Size(6.dp.toPx(), barLift.toPx() + 6.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx())
            )
            drawCircle(
                    color = Color.White.copy(alpha = 0.22f * fa),
                    radius = 13.dp.toPx(),
                    center = Offset(x, y)
            )
            drawCircle(
                    color = Color.White.copy(alpha = 0.85f * fa),
                    radius = 7.dp.toPx(),
                    center = Offset(x, y)
            )
        }
    }
}
