package br.com.redesurftank.havalshisuku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import coil.compose.AsyncImage

/**
 * Card de destaque da aba "Instalar Apps" — um por coluna da grade, para os quatro caberem na
 * mesma linha: os dois patches de projecao, o Impulse Launcher e o "abrir ao ligar".
 *
 * Icone/preview e titulo em cima, com o slot extra no meio; botoes de acao embaixo a esquerda
 * e descricao com versao/status a direita dos botoes (ou descricao abaixo do titulo para projecao).
 */
@Composable
fun FeatureCard(
    icon: ImageVector,
    iconTint: Color,
    highlighted: Boolean,
    title: String,
    subtitle: String,
    status: String?,
    statusTint: Color = ImpTokens.TextSecondary,
    subtitleBelowTitle: Boolean = false,
    /** Preview do app, quando existir: vende melhor do que qualquer texto. */
    previewRes: Int? = null,
    extra: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(
                    width = 1.dp,
                    color = if (highlighted) ImpTokens.Accent else ImpTokens.Hairline,
                    shape = RoundedCornerShape(12.dp)
                ),
        colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(244.dp).padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (previewRes != null) {
                    Image(
                        painter = painterResource(previewRes),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier =
                            Modifier.fillMaxWidth()
                                .aspectRatio(640f / 209f)
                                .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier.size(40.dp).background(ImpTokens.TrackOff, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    }
                }
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (subtitleBelowTitle && subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        color = ImpTokens.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
                extra?.invoke()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    actions()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!subtitleBelowTitle && subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            color = ImpTokens.TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    if (status != null) {
                        Text(
                            status,
                            color = statusTint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** Botao de acao do card, com a mesma altura em todos para as linhas baterem. */
@Composable
fun CardButton(
    label: String,
    color: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = contentPadding
    ) { Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1) }
}

/** "Auto-montar ao iniciar", compacto o bastante para o card estreito. */
@Composable
fun AutoMountRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String = "Auto-montar",
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.6f),
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ImpTokens.Accent
                )
        )
        Text(label, color = ImpTokens.TextSecondary, fontSize = 11.sp)
    }
}

/**
 * Uma tela do "abrir ao ligar": o nome da tela e, ao lado, o icone e o nome do app escolhido.
 * O icone e o que faz a linha ser lida de relance — sem ele, o card vira duas linhas de texto.
 */
@Composable
fun StartupSlotRow(label: String, packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolved =
        if (packageName.isEmpty()) null
        else runCatching { DisplayAppLauncher.resolveAppInfo(context, packageName) }.getOrNull()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(ImpTokens.TrackOff, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (resolved?.icon != null) {
                    AsyncImage(
                        model = resolved.icon,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Text("—", color = ImpTokens.TextMuted, fontSize = 16.sp)
                }
            }
            Text(
                label,
                color = ImpTokens.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(
            resolved?.label ?: "nenhum",
            color = if (resolved != null) Color.White else ImpTokens.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
