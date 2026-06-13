package br.com.redesurftank.havalshisuku.ui.navigation

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import br.com.redesurftank.havalshisuku.ui.screens.*
import br.com.redesurftank.havalshisuku.ui.components.FeaturesHubScreen

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
            .background(AppColors.Background)
    ) {
        // Fixed Side Menu
        Surface(
            modifier = Modifier
                .width(AppDimensions.MenuWidth)
                .fillMaxHeight(),
            color = Color(0xFF13151A),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                menuItems.forEachIndexed { index, item ->
                    val animatedWidth by animateFloatAsState(
                        targetValue = if (selectedItem == index) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 200,
                            easing = FastOutSlowInEasing
                        ),
                        label = "backgroundWidth"
                    )

                    val borderAlpha by animateFloatAsState(
                        targetValue = if (selectedItem == index) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 0,
                            delayMillis = 0
                        ),
                        label = "borderAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppDimensions.MenuItemHeight)
                            .clickable { selectedItem = index },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Animated background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedWidth)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF152031),
                                            Color(0xFF13151A)
                                        )
                                    )
                                )
                                .drawBehind {
                                    drawLine(
                                        color = Color(0xFF0B84FF).copy(alpha = borderAlpha),
                                        start = Offset(0f, 0f),
                                        end = Offset(0f, size.height),
                                        strokeWidth = 10.dp.toPx()
                                    )
                                }
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (selectedItem == index) AppColors.MenuSelectedIcon else AppColors.MenuUnselectedIcon,
                                modifier = Modifier.size(AppDimensions.IconSize)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                item.title,
                                color = if (selectedItem == index) AppColors.TextPrimary else AppColors.MenuUnselectedText,
                                fontSize = 20.sp,
                                fontWeight = if (selectedItem == index) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Main Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(AppColors.Background)
        ) {
            // Content Area
            ContentArea {
                val currentItem = menuItems.getOrNull(selectedItem)
                when (currentItem?.title) {
                    "Configurações" -> BasicSettingsTab()
                    "Telas" -> TelasTab()
                    "Valores Atuais" -> CurrentValuesTab()
                    "Instalar Apps" -> InstallAppsTab()
                    "Recursos" -> FeaturesHubScreen()
                    "Informações" -> InformacoesTab()
                    "Frida Hooks" -> FridaHooksTab()
                    else -> BasicSettingsTab()
                }
            }
        }
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
