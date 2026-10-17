package br.com.redesurftank.havalshisuku.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.hud.HudOverlayModel
import br.com.redesurftank.havalshisuku.hud.HudOverlayRenderer
import br.com.redesurftank.havalshisuku.hud.HudOverlaySettings

/**
 * Telas tab: pick what the physical HUD shows. Each layout is a card with a preview drawn by the
 * same renderer the HUD uses (road scene + the car's own HUD + Impulse's overlay), so the picture
 * matches the windshield. Layouts with options get a gear.
 */
@Composable
fun HudLayoutSection(modifier: Modifier = Modifier) {
    var config by remember { mutableStateOf(HudOverlaySettings.load()) }
    var showSettings by remember { mutableStateOf(false) }
    val apply: (HudOverlayModel.Config) -> Unit = {
        config = it
        HudOverlaySettings.save(it)
    }

    StyledCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("HUD", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Escolha o que aparece no HUD do para-brisa. O Impulse só desenha nos cantos livres, sem cobrir a velocidade.",
                    color = ImpTokens.TextSecondary,
                    fontSize = 14.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HudLayoutCard(
                    title = "Original",
                    subtitle = "Só o HUD do carro",
                    preview = config.copy(layout = HudOverlayModel.Layout.ORIGINAL),
                    selected = config.layout == HudOverlayModel.Layout.ORIGINAL,
                    onSelect = { apply(config.copy(layout = HudOverlayModel.Layout.ORIGINAL)) }
                )
                HudLayoutCard(
                    title = "Navegação",
                    subtitle = "Manobras do Android Auto, potência e rotação",
                    preview = config.copy(layout = HudOverlayModel.Layout.NAVIGATION),
                    selected = config.layout == HudOverlayModel.Layout.NAVIGATION,
                    onSelect = { apply(config.copy(layout = HudOverlayModel.Layout.NAVIGATION)) },
                    onSettings = { showSettings = true }
                )
            }
        }
    }

    if (showSettings) {
        HudNavigationSettingsDialog(config = config, onChange = apply, onDismiss = { showSettings = false })
    }
}

@Composable
private fun HudLayoutCard(
    title: String,
    subtitle: String,
    preview: HudOverlayModel.Config,
    selected: Boolean,
    onSelect: () -> Unit,
    onSettings: (() -> Unit)? = null
) {
    // Re-rendered only when the options it shows change.
    val image = remember(preview) { HudOverlayRenderer().preview(preview).asImageBitmap() }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(shape)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ImpTokens.Accent else ImpTokens.TrackOff), shape)
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            bitmap = image,
            contentDescription = "Prévia do layout $title",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(6.dp))
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (selected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selecionado",
                            tint = ImpTokens.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(subtitle, color = ImpTokens.TextSecondary, fontSize = 12.sp)
            }
            if (onSettings != null) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Configurar layout $title",
                        tint = ImpTokens.Accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HudNavigationSettingsDialog(
    config: HudOverlayModel.Config,
    onChange: (HudOverlayModel.Config) -> Unit,
    onDismiss: () -> Unit
) {
    val preview = remember(config) {
        HudOverlayRenderer().preview(config.copy(layout = HudOverlayModel.Layout.NAVIGATION)).asImageBitmap()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ImpTokens.Container,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Layout Navegação", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Image(
                    bitmap = preview,
                    contentDescription = "Prévia com estas opções",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.height(8.dp))
                SectionLabel("Navegação")
                OptionRow("Mostrar navegação", "Seta, distância e faixas enquanto o Android Auto guia.", config.navigation) {
                    onChange(config.copy(navigation = it))
                }
                OptionRow(
                    "Seta menor quando longe",
                    "Seta pequena até faltar 1 km para a manobra; depois volta a ficar grande.",
                    config.smallArrowWhenFar,
                    enabled = config.navigation
                ) { onChange(config.copy(smallArrowWhenFar = it)) }
                OptionRow("Horário de chegada", "Mostra a chegada e o tempo restante, ex.: 10:40 · 12 min.", config.eta, enabled = config.navigation) {
                    onChange(config.copy(eta = it))
                }
                HorizontalDivider(color = ImpTokens.TrackOff, modifier = Modifier.padding(vertical = 6.dp))
                SectionLabel("Barras")
                OptionRow("Potência / regeneração", "Canto inferior esquerdo. Azul acelerando, verde regenerando.", config.powerBar) {
                    onChange(config.copy(powerBar = it))
                }
                OptionRow("Rotação do motor", "Canto inferior direito, só com o motor ligado.", config.rpmBar) {
                    onChange(config.copy(rpmBar = it))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = ImpTokens.Accent, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = ImpTokens.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun OptionRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color.White else ImpTokens.TextMuted, fontSize = 14.sp)
            Text(description, color = if (enabled) ImpTokens.TextSecondary else ImpTokens.TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ImpTokens.Accent)
        )
    }
}
