package br.com.redesurftank.havalshisuku.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.ambientlight.AmbientLightSettingsScreen
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.theme.Michroma
import br.com.redesurftank.havalshisuku.ui.theme.ImpulseTextSizes

@Composable
fun FeaturesHubScreen() {
    var selectedFeature by remember { mutableStateOf<String?>(null) }

    when (selectedFeature) {
        "score" -> TripConsistencyScreen(onBackToFeatures = { selectedFeature = null })
        "ambient_light" -> AmbientLightSettingsScreen(onBackToFeatures = { selectedFeature = null })
        else -> FeaturesHome(
            onOpenScore = { selectedFeature = "score" },
            onOpenAmbientLight = { selectedFeature = "ambient_light" }
        )
    }
}

@Composable
private fun FeaturesHome(onOpenScore: () -> Unit, onOpenAmbientLight: () -> Unit) {
    val prefs =
        App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    val ambientLightEnabled =
        prefs.getBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_ENABLED.key, false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Recursos", color = AppColors.TextPrimary, fontFamily = Michroma, fontSize = 22.sp, letterSpacing = 0.5.sp)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            FeatureCard(
                title = "Score de Consistência",
                description = "Score e histórico de viagens.",
                status = "Disponível",
                icon = Icons.Default.Speed,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onOpenScore
            )
            if (ambientLightEnabled) {
                FeatureCard(
                    title = "Ambient Light BLE",
                    description = "Controle LEDs externos por Bluetooth.",
                    status = "Opcional",
                    icon = Icons.Default.Settings,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenAmbientLight
                )
            } else {
                FeatureCard(
                    title = "Vallet",
                    description = "Área reservada para controles e regras de uso em modo manobrista.",
                    status = "Em breve",
                    icon = Icons.Default.AdminPanelSettings,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
        }

        if (ambientLightEnabled) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                FeatureCard(
                    title = "Vallet",
                    description = "Área reservada para controles e regras de uso em modo manobrista.",
                    status = "Em breve",
                    icon = Icons.Default.AdminPanelSettings,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    status: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(210.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        color = ImpTokens.Container,
        border = null
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = if (enabled) ImpTokens.Accent else ImpTokens.TextMuted, modifier = Modifier.size(38.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, color = if (enabled) ImpTokens.TextSecondary else ImpTokens.TextDisabled, fontSize = ImpulseTextSizes.Label, fontWeight = FontWeight.SemiBold)
                    if (enabled) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ImpTokens.Accent, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = AppColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(description, color = AppColors.TextSecondary, fontSize = ImpulseTextSizes.Label)
            }
        }
    }
}
