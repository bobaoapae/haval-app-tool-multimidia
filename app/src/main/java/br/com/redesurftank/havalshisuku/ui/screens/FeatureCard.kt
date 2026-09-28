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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import coil.compose.AsyncImage

/**
 * Card de destaque da aba "Instalar Apps" — um por coluna da grade, para os quatro caberem na
 * mesma linha: os dois patches de projecao, o Impulse Home e o "abrir ao ligar".
 *
 * O formato e vertical de proposito: em 1/4 da largura nao cabe o desenho antigo, que era icone,
 * texto e botoes numa linha so. Icone e titulo em cima, estado no meio, acoes embaixo — e as acoes
 * ficam alinhadas entre os cards porque [Column] usa `SpaceBetween` com altura fixa.
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
    extra: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(
                    width = 1.dp,
                    color = if (highlighted) ImpTokens.Accent else ImpTokens.Hairline,
                    shape = RoundedCornerShape(12.dp)
                ),
        colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(208.dp).padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).background(ImpTokens.TrackOff, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = ImpTokens.TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                if (status != null) {
                    Text(status, color = statusTint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                extra?.invoke()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
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
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
    ) { Text(label, color = Color.White, fontSize = 13.sp) }
}

/** "Auto-montar ao iniciar", compacto o bastante para o card estreito. */
@Composable
fun AutoMountRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
        Text("Auto-montar", color = ImpTokens.TextSecondary, fontSize = 11.sp)
    }
}

/**
 * Uma tela do "abrir ao ligar": o nome da tela e, ao lado, o icone e o nome do app escolhido.
 * O icone e o que faz a linha ser lida de relance — sem ele, o card vira duas linhas de texto.
 */
@Composable
fun StartupSlotRow(label: String, packageName: String) {
    val context = LocalContext.current
    val resolved =
        if (packageName.isEmpty()) null
        else runCatching { DisplayAppLauncher.resolveAppInfo(context, packageName) }.getOrNull()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp).background(ImpTokens.TrackOff, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (resolved?.icon != null) {
                AsyncImage(
                    model = resolved.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text("—", color = ImpTokens.TextMuted, fontSize = 12.sp)
            }
        }
        Column {
            Text(label, color = ImpTokens.TextMuted, fontSize = 9.sp)
            Text(
                resolved?.label ?: "nenhum",
                color = if (resolved != null) Color.White else ImpTokens.TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
