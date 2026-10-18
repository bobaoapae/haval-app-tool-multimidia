package br.com.redesurftank.havalshisuku.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.components.StyledCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Recursos > Alerta de Radar > Configurar. Every change is saved and applied at once. */
@Composable
fun RadarSettingsScreen(onBackToFeatures: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(RadarSettings.load()) }
    var approachVolume by remember { mutableStateOf(config.approachVolume.toFloat()) }
    var nearVolume by remember { mutableStateOf(config.nearVolume.toFloat()) }
    var status by remember { mutableStateOf(RadarRepository.status) }
    var updating by remember { mutableStateOf(RadarUpdater.running) }
    var lastUpdate by remember { mutableStateOf(0L) }
    var lastError by remember { mutableStateOf("") }

    fun save(next: RadarConfig) {
        config = next
        RadarSettings.save(next)
    }

    // The update runs on its own thread for up to a few minutes: refresh the figures meanwhile.
    LaunchedEffect(Unit) {
        while (true) {
            val prefs = RadarSettings.prefs()
            status = RadarRepository.status
            updating = RadarUpdater.running
            lastUpdate = prefs.getLong(SharedPreferencesKeys.RADAR_LAST_UPDATE_MS.key, 0L)
            lastError = prefs.getString(SharedPreferencesKeys.RADAR_LAST_ERROR.key, "").orEmpty()
            delay(1_500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToFeatures) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = AppColors.TextPrimary)
            }
            Column {
                Text("Alerta de Radar", color = AppColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Avisa radares fixos à frente pelo GPS da central, no HUD e no painel.",
                    color = AppColors.TextSecondary,
                    fontSize = 16.sp
                )
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow("Alerta de radar", "Usa o GPS da central para avisar radares à frente.", config.enabled) {
                    save(config.copy(enabled = it))
                }
                SwitchRow("Mostrar no HUD", "Placa com o limite e a distância no lugar das setas.", config.showOnHud) {
                    save(config.copy(showOnHud = it))
                }
                SwitchRow("Mostrar no painel (tema)", "No card de navegação do cluster.", config.showOnTheme) {
                    save(config.copy(showOnTheme = it))
                }
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Distâncias")
                Text("Aviso de aproximação (longe)", color = AppColors.TextPrimary, fontSize = 17.sp)
                ChipRow(RadarConfig.APPROACH_OPTIONS, config.approachM, label = { "$it m" }) {
                    save(config.copy(approachM = it))
                }
                Text("Alerta próximo (perto)", color = AppColors.TextPrimary, fontSize = 17.sp)
                ChipRow(RadarConfig.NEAR_OPTIONS, config.nearM, label = { "$it m" }) { save(config.copy(nearM = it)) }
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Sons")
                SoundBlock(
                    title = "Aproximação",
                    sound = config.approachSound,
                    volume = approachVolume,
                    onSound = { save(config.copy(approachSound = it)) },
                    onVolume = { approachVolume = it },
                    onVolumeDone = { save(config.copy(approachVolume = approachVolume.toInt())) },
                    onTest = { RadarSoundPlayer.play(context, config.approachSound, approachVolume.toInt()) }
                )
                SoundBlock(
                    title = "Alerta próximo / acima do limite",
                    sound = config.nearSound,
                    volume = nearVolume,
                    onSound = { save(config.copy(nearSound = it)) },
                    onVolume = { nearVolume = it },
                    onVolumeDone = { save(config.copy(nearVolume = nearVolume.toInt())) },
                    onTest = { RadarSoundPlayer.play(context, config.nearSound, nearVolume.toInt()) }
                )
                SwitchRow(
                    "Som só acima do limite",
                    "A placa aparece sempre; o som toca só se estiver acima da velocidade do radar.",
                    config.soundOnlyOverLimit
                ) { save(config.copy(soundOnlyOverLimit = it)) }
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Testar")
                Text(
                    "Simula a passagem por um radar de 60 km/h a 700 m numa via reta (cerca de 50 s), no HUD, no painel e com os sons.",
                    color = AppColors.TextSecondary,
                    fontSize = 14.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { RadarManager.demo(over = false) }) { Text("Simular a 50 km/h") }
                    OutlinedButton(onClick = { RadarManager.demo(over = true) }) { Text("Simular a 72 km/h") }
                }
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Base de radares")
                val s = status
                if (s == null) {
                    Text("Carregando…", color = AppColors.TextSecondary, fontSize = 15.sp)
                } else {
                    Text("${s.total} radares", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("OpenStreetMap: ${s.osm}${dateSuffix(s.osmUpdated)}", color = AppColors.TextSecondary, fontSize = 15.sp)
                    Text("ANTT (rodovias federais concedidas): ${s.antt}${dateSuffix(s.anttUpdated)}", color = AppColors.TextSecondary, fontSize = 15.sp)
                    Text(
                        "Na via certa: ${s.withRoad} de ${s.total} ligados à sua rua/rodovia${dateSuffix(s.roadsUpdated)} (vias atualizadas 1x por mês)",
                        color = AppColors.TextSecondary,
                        fontSize = 15.sp
                    )
                    if (s.fromApk) Text("Usando a cópia que veio com o app.", color = AppColors.TextSecondary, fontSize = 15.sp)
                }
                Text(
                    if (lastUpdate > 0) "Última atualização: ${formatTime(lastUpdate)} (no máximo uma vez por dia)"
                    else "Ainda não atualizada pela internet.",
                    color = AppColors.TextSecondary,
                    fontSize = 15.sp
                )
                if (lastError.isNotEmpty() && !updating) Text("Último erro: $lastError", color = ImpTokens.Attention, fontSize = 14.sp)
                Spacer(Modifier.width(1.dp))
                Button(
                    enabled = !updating,
                    onClick = {
                        updating = true
                        RadarUpdater.maybeUpdate(force = true)
                    }
                ) { Text(if (updating) "Atualizando… (até alguns minutos)" else "Atualizar agora") }
                Text(
                    "Dados: © colaboradores do OpenStreetMap (ODbL) e ANTT – Dados dos Radares (CC-BY). " +
                        "Só radares fixos; a cobertura varia por cidade.",
                    color = ImpTokens.TextMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = AppColors.TextSecondary, fontSize = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (option in options) {
            val isOn = option == selected
            val isEnabled = enabled(option)
            val shape = RoundedCornerShape(12.dp)
            Text(
                text = label(option),
                color = when {
                    isOn -> ImpTokens.OnAccent
                    isEnabled -> AppColors.TextPrimary
                    else -> ImpTokens.TextMuted
                },
                fontSize = 16.sp,
                fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(shape)
                    .background(if (isOn) ImpTokens.Accent else ImpTokens.Container)
                    .border(1.dp, if (isOn) ImpTokens.Accent else ImpTokens.TrackOff, shape)
                    .then(if (isEnabled) Modifier.clickable { onSelect(option) } else Modifier)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun SoundBlock(
    title: String,
    sound: RadarSound,
    volume: Float,
    onSound: (RadarSound) -> Unit,
    onVolume: (Float) -> Unit,
    onVolumeDone: () -> Unit,
    onTest: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = AppColors.TextPrimary, fontSize = 17.sp)
        ChipRow(RadarSound.entries, sound, label = { it.label }, onSelect = onSound)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Volume ${volume.toInt()}%", color = AppColors.TextSecondary, fontSize = 15.sp, modifier = Modifier.width(120.dp))
            Slider(
                value = volume,
                onValueChange = onVolume,
                onValueChangeFinished = onVolumeDone,
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onTest, enabled = sound != RadarSound.NONE) { Text("Testar") }
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))

/** " (17/10/2026)" from an ISO date, "" when unknown. */
private fun dateSuffix(iso: String): String {
    if (iso.length < 10) return ""
    val (y, m, d) = iso.substring(0, 10).split('-').takeIf { it.size == 3 } ?: return ""
    return " ($d/$m/$y)"
}
