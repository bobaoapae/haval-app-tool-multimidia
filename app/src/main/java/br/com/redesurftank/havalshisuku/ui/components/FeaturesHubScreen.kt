package br.com.redesurftank.havalshisuku.ui.components

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
import androidx.compose.material.icons.filled.Construction
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

@Composable
fun FeaturesHubScreen() {
    var selectedFeature by remember { mutableStateOf<String?>(null) }

    when (selectedFeature) {
        "score" -> TripConsistencyScreen(onBackToFeatures = { selectedFeature = null })
        else -> FeaturesHome(onOpenScore = { selectedFeature = "score" })
    }
}

@Composable
private fun FeaturesHome(onOpenScore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Recursos", color = AppColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "Central para recursos inteligentes do Impulse.",
                color = AppColors.TextSecondary,
                fontSize = 18.sp
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            FeatureCard(
                title = "Score de Consistência",
                description = "Acompanhe score da viagem, histórico das últimas 10 viagens e regras de classificação.",
                status = "Disponível",
                icon = Icons.Default.Speed,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onOpenScore
            )
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

        StyledCard {
            Row(modifier = Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Construction, contentDescription = null, tint = Color(0xFFFFC857), modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Padrão para próximas features", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Quando surgirem novas funções, elas devem entrar como cards nesta central e não como itens soltos no menu lateral.",
                        color = AppColors.TextSecondary,
                        fontSize = 15.sp
                    )
                }
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
        color = if (enabled) Color(0xFF111B27) else Color(0xFF15171C),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = if (enabled) Color(0xFF00D8FF) else AppColors.TextDisabled, modifier = Modifier.size(38.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, color = if (enabled) Color(0xFF14FF5A) else AppColors.TextDisabled, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (enabled) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF00D8FF), modifier = Modifier.size(22.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = AppColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(description, color = AppColors.TextSecondary, fontSize = 15.sp)
            }
        }
    }
}
