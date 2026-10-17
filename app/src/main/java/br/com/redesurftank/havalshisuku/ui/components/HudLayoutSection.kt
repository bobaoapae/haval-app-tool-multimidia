package br.com.redesurftank.havalshisuku.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import br.com.redesurftank.havalshisuku.hud.previewScenarios
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    /** Layout whose gear was tapped (its settings dialog is open), or null. */
    var settingsFor by remember { mutableStateOf<HudOverlayModel.Layout?>(null) }
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
                    onSettings = { settingsFor = HudOverlayModel.Layout.NAVIGATION }
                )
                HudLayoutCard(
                    title = "Navegação AR",
                    subtitle = "Seta azul em perspectiva que vira a manobra ao chegar",
                    preview = config.copy(layout = HudOverlayModel.Layout.AR),
                    selected = config.layout == HudOverlayModel.Layout.AR,
                    onSelect = { apply(config.copy(layout = HudOverlayModel.Layout.AR)) },
                    onSettings = { settingsFor = HudOverlayModel.Layout.AR }
                )
            }
        }
    }

    settingsFor?.let { layout ->
        HudNavigationSettingsDialog(
            layout = layout,
            config = config,
            onChange = apply,
            onDismiss = { settingsFor = null }
        )
    }
}

/** How long each sample scenario stays up in a preview before the next one fades in. */
private const val PREVIEW_CYCLE_MS = 3_000L

/**
 * The layout preview, cycling through its sample scenarios (town turn, highway exit, roundabout
 * regen...) with a crossfade. Bitmaps are rendered once per [config] by the HUD's own renderer.
 */
@Composable
private fun HudPreview(config: HudOverlayModel.Config, description: String, modifier: Modifier = Modifier) {
    val frames = remember(config) {
        val renderer = HudOverlayRenderer()
        previewScenarios(config.layout).map { renderer.preview(config, it).asImageBitmap() }
    }
    var index by remember(config) { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        while (true) {
            delay(PREVIEW_CYCLE_MS)
            index = (index + 1) % frames.size
        }
    }
    Crossfade(targetState = index, animationSpec = tween(600), label = "hudPreview", modifier = modifier) { i ->
        Image(
            bitmap = frames[i],
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f)
        )
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
        HudPreview(
            config = preview,
            description = "Prévia do layout $title",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
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

/** Tabs of the Navegação settings dialog. */
private enum class HudSettingsTab(val title: String) { NAVIGATION("Navegação"), BARS("Barras") }

/**
 * Wide two-pane dialog for the 1920x720 head unit: the options on the left split into tabs so
 * each fits without scrolling, live preview on the right. (A tall single column got clipped on
 * this screen, with the bar switches drawn on top of each other.)
 */
@Composable
private fun HudNavigationSettingsDialog(
    layout: HudOverlayModel.Layout,
    config: HudOverlayModel.Config,
    onChange: (HudOverlayModel.Config) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(HudSettingsTab.NAVIGATION) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.82f).padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, ImpTokens.Accent)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (layout == HudOverlayModel.Layout.AR) "Layout Navegação AR" else "Layout Navegação",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Fechar", color = ImpTokens.Accent, fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // Left: one tab of options at a time.
                    Column(modifier = Modifier.weight(1f)) {
                        HudTabs(selected = tab, onSelect = { tab = it })
                        Spacer(Modifier.height(8.dp))
                        when (tab) {
                            HudSettingsTab.NAVIGATION -> {
                                OptionRow("Mostrar navegação", "Seta, distância e faixas enquanto o Android Auto guia.", config.navigation) {
                                    onChange(config.copy(navigation = it))
                                }
                                // The AR arrow already shows distance by itself (straight when far).
                                if (layout != HudOverlayModel.Layout.AR) {
                                    OptionRow(
                                        "Seta menor quando longe",
                                        "Seta pequena até faltar 1 km para a manobra; depois fica grande.",
                                        config.smallArrowWhenFar,
                                        enabled = config.navigation
                                    ) { onChange(config.copy(smallArrowWhenFar = it)) }
                                }
                                OptionRow(
                                    "Distância e tempo restantes",
                                    "Quanto falta até o destino, ex.: 4.8 km · 12 min.",
                                    config.trip,
                                    enabled = config.navigation
                                ) { onChange(config.copy(trip = it)) }
                            }
                            HudSettingsTab.BARS -> {
                                OptionRow("Potência / regeneração", "Canto inferior esquerdo. Azul acelerando, verde regenerando.", config.powerBar) {
                                    onChange(config.copy(powerBar = it))
                                }
                                OptionRow("Rotação do motor", "Canto inferior direito, só com o motor ligado.", config.rpmBar) {
                                    onChange(config.copy(rpmBar = it))
                                }
                            }
                        }
                    }
                    // Right: what the HUD will show with these options.
                    Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HudPreview(
                            config = config.copy(layout = layout),
                            description = "Prévia com estas opções",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            "Prévia com estas opções, alternando cidade, rodovia e rotatória. O Impulse desenha só nos cantos livres do HUD.",
                            color = ImpTokens.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudTabs(selected: HudSettingsTab, onSelect: (HudSettingsTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in HudSettingsTab.entries) {
            val on = t == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) ImpTokens.Accent.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.dp, if (on) ImpTokens.Accent else ImpTokens.TrackOff, RoundedCornerShape(8.dp))
                    .clickable { onSelect(t) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    t.title,
                    color = if (on) Color.White else ImpTokens.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
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
