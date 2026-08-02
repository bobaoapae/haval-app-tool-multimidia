package br.com.redesurftank.havalshisuku.ui.navigation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.screens.*
import br.com.redesurftank.havalshisuku.ui.components.FeaturesHubScreen
import br.com.redesurftank.havalshisuku.ui.theme.IbmPlexSans
import br.com.redesurftank.havalshisuku.ui.theme.Michroma

private val RailBackground = Color(0xFF0D0E12)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val prefs = App.getDeviceProtectedContext()
        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    val advancedUse = prefs.getBoolean(SharedPreferencesKeys.ADVANCE_USE.key, false)

    val menuItems = menuItems.filter { item ->
        if (item.title == "Frida Hooks") advancedUse else true
    }

    var selectedItem by remember { mutableStateOf(0) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    // Check if width is 1920px (with some tolerance)
    val isFullWidth = screenWidthPx >= 1918f && screenWidthPx <= 1922f
    val startPadding = if (isFullWidth) with(density) { 100.toDp() } else 0.dp

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = startPadding)
            .background(ImpTokens.Ground)
    ) {
        // Rail lateral fixo (redesign Rodada 15)
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(RailBackground)
                .drawBehind {
                    drawLine(
                        color = ImpTokens.Hairline,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(vertical = 18.dp)
        ) {
            Text(
                text = "IMPULSE",
                fontFamily = Michroma,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
                color = ImpTokens.TextPrimary,
                modifier = Modifier.padding(start = 24.dp, bottom = 24.dp)
            )

            val hasFrida = menuItems.lastOrNull()?.title == "Frida Hooks"
            val topItems = if (hasFrida) menuItems.dropLast(1) else menuItems
            topItems.forEachIndexed { index, item ->
                RailItem(
                    item = item,
                    selected = selectedItem == index,
                    dimmed = false,
                    onClick = { selectedItem = index }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (hasFrida) {
                val fridaIndex = menuItems.lastIndex
                RailItem(
                    item = menuItems.last(),
                    selected = selectedItem == fridaIndex,
                    dimmed = true,
                    onClick = { selectedItem = fridaIndex }
                )
            }
        }

        // Área de conteúdo — IBM Plex Sans como fonte padrão de todas as abas
        // (escopo só do app de configurações; NÃO afeta a barra inferior/cluster).
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(ImpTokens.Ground)
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = IbmPlexSans)
            ) {
                ContentArea {
                    val currentItem = menuItems.getOrNull(selectedItem)
                    when (currentItem?.title) {
                        "Configurações" -> BasicSettingsTab()
                        "Telas" -> TelasTab()
                        "Valores Atuais" -> CurrentValuesTab()
                        "Instalar Apps" -> InstallAppsTab()
                        "Recursos" -> FeaturesHubScreen()
                        "Reportar problema" -> ProblemReportTab()
                        "Informações" -> InformacoesTab()
                        "Frida Hooks" -> FridaHooksTab()
                        else -> BasicSettingsTab()
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    item: NavigationItem,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ImpTokens.IndexActiveTint else Color.Transparent)
            .clickable { onClick() }
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp)
            .then(if (dimmed && !selected) Modifier.alpha(0.6f) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = if (selected) ImpTokens.Accent else ImpTokens.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = item.title,
            fontFamily = IbmPlexSans,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 16.sp,
            color = if (selected) ImpTokens.Accent else ImpTokens.TextPrimary
        )
    }
}

@Composable
fun ContentArea(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppDimensions.ContentPadding)
    ) {
        content()
    }
}
