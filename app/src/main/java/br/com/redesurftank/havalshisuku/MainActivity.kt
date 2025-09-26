package br.com.redesurftank.havalshisuku

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.listeners.IDataChanged
import br.com.redesurftank.havalshisuku.managers.AutoBrightnessManager
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.AppInfo
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.SteeringWheelCustomActionType
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import br.com.redesurftank.havalshisuku.ui.components.SettingCard
import br.com.redesurftank.havalshisuku.ui.components.SettingItem
import br.com.redesurftank.havalshisuku.ui.components.StyledCard
import br.com.redesurftank.havalshisuku.ui.components.TwoColumnSettingsLayout
import br.com.redesurftank.havalshisuku.ui.theme.HavalShisukuTheme
import br.com.redesurftank.havalshisuku.utils.FridaUtils
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import androidx.compose.foundation.lazy.grid.items as gridItems

const val TAG = "HavalShisuku"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HavalShisukuTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    val advancedUse = prefs.getBoolean(SharedPreferencesKeys.ADVANCE_USE.key, false)

    val menuItems = buildList {
        add(DrawerMenuItem("Configurações", Icons.Default.Settings))
        add(DrawerMenuItem("Telas", Icons.Default.SmartDisplay))
        add(DrawerMenuItem("Valores Atuais", Icons.Default.DeveloperMode))
        add(DrawerMenuItem("Instalar Apps", Icons.Default.ShoppingCart))
        add(DrawerMenuItem("Informações", Icons.Default.Info))
        if (advancedUse) {
            add(DrawerMenuItem("Frida Hooks", Icons.Default.Build))
        }
    }

    var selectedItem by remember { mutableStateOf(0) }

    Row(
        modifier = modifier
            .fillMaxSize()
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
            Column(
                modifier = Modifier
                    .fillMaxHeight()
            ) {
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
                when (selectedItem) {
                    0 -> BasicSettingsTab()
                    1 -> TelasTab()
                    2 -> CurrentValuesTab()
                    3 -> InstallAppsTab()
                    4 -> InformacoesTab()
                    5 -> FridaHooksTab()
                }
            }
        }
    }
}

data class DrawerMenuItem(
    val title: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicSettingsTab() {
    val context = LocalContext.current
    val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    var isAdvancedUse by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ADVANCE_USE.key, false)) }
    var selfInstallationCheck by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.SELF_INSTALLATION_INTEGRITY_CHECK.key, false)) }
    var bypassSelfInstallationCheck by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.key, false)) }
    var disableMonitoring by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.DISABLE_MONITORING.key, false)) }
    var disableAvas by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.DISABLE_AVAS.key, false)) }
    var disableAvmCarStopped by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.DISABLE_AVM_CAR_STOPPED.key, false)) }
    var closeWindowOnPowerOff by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.CLOSE_WINDOW_ON_POWER_OFF.key, false)) }
    var closeWindowOnFoldMirror by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.CLOSE_WINDOW_ON_FOLD_MIRROR.key, false)) }
    var closeSunroofOnPowerOff by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_POWER_OFF.key, false)) }
    var closeSunroofOnFoldMirror by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_FOLD_MIRROR.key, false)) }
    var closeSunroofSunShadeOnCloseSunroof by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF.key, false)) }
    var enableControlAcViaSteeringWheel by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_AC_CONTROL_VIA_STEERING_WHEEL.key, false)) }
    var setStartupVolume by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.SET_STARTUP_VOLUME.key, false)) }
    var volume by remember { mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.STARTUP_VOLUME.key, 1)) }
    var closeWindowsOnSpeed by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.CLOSE_WINDOWS_ON_SPEED.key, false)) }
    var closeSunroofOnSpeed by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_SPEED.key, false)) }
    var speedThreshold by remember { mutableFloatStateOf(prefs.getFloat(SharedPreferencesKeys.SPEED_THRESHOLD.key, 15f)) }
    var closeSunroofSpeedThreshold by remember { mutableFloatStateOf(prefs.getFloat(SharedPreferencesKeys.SUNROOF_SPEED_THRESHOLD.key, 15f)) }
    var enableAutoBrightness by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_AUTO_BRIGHTNESS.key, false)) }
    var nightStartHour by remember { mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.NIGHT_START_HOUR.key, 20)) }
    var nightStartMinute by remember { mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.NIGHT_START_MINUTE.key, 0)) }
    var nightEndHour by remember { mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.NIGHT_END_HOUR.key, 6)) }
    var nightEndMinute by remember { mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.NIGHT_END_MINUTE.key, 0)) }
    var disableBluetoothOnPowerOff by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.key, false)) }
    var disableHotspotOnPowerOff by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF.key, false)) }
    var nightBrightnessLevel by remember { mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.AUTO_BRIGHTNESS_LEVEL_NIGHT.key, 1)) }
    var dayBrightnessLevel by remember { mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.AUTO_BRIGHTNESS_LEVEL_DAY.key, 10)) }
    var enableSeatVentilationOnAcOn by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.key, false)) }
    var enableCustomSteeringWheelButtons by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.key, false)) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val settingsList = mutableListOf<SettingItem>()

    if (isAdvancedUse && !selfInstallationCheck) {
        settingsList.add(
            SettingItem(
                title = "Bypass de Verificação",
                description = SharedPreferencesKeys.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.description,
                checked = bypassSelfInstallationCheck,
                onCheckedChange = {
                    bypassSelfInstallationCheck = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.key, it) }
                }
            )
        )
    }

    settingsList.addAll(
        listOf(
            SettingItem(
                title = "Fechar janela ao desligar o veículo",
                description = "Fecha automaticamente as janelas quando o motor é desligado",
                checked = closeWindowOnPowerOff,
                onCheckedChange = {
                    closeWindowOnPowerOff = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.CLOSE_WINDOW_ON_POWER_OFF.key, it) }
                }
            ),
            SettingItem(
                title = "Fechar janela ao recolher retrovisores",
                description = "Sincroniza fechamento das janelas com o recolhimento dos retrovisores",
                checked = closeWindowOnFoldMirror,
                onCheckedChange = {
                    closeWindowOnFoldMirror = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.CLOSE_WINDOW_ON_FOLD_MIRROR.key, it) }
                }
            ),
            SettingItem(
                title = "Fechar teto solar ao desligar",
                description = SharedPreferencesKeys.CLOSE_SUNROOF_ON_POWER_OFF.description,
                checked = closeSunroofOnPowerOff,
                onCheckedChange = {
                    closeSunroofOnPowerOff = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_POWER_OFF.key, it) }
                }
            ),
            SettingItem(
                title = "Fechar teto solar ao recolher retrovisores",
                description = SharedPreferencesKeys.CLOSE_SUNROOF_ON_FOLD_MIRROR.description,
                checked = closeSunroofOnFoldMirror,
                onCheckedChange = {
                    closeSunroofOnFoldMirror = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_FOLD_MIRROR.key, it) }
                }
            ),
            SettingItem(
                title = "Fechar cortina do teto solar",
                description = SharedPreferencesKeys.CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF.description,
                checked = closeSunroofSunShadeOnCloseSunroof,
                onCheckedChange = {
                    closeSunroofSunShadeOnCloseSunroof = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF.key, it) }
                }
            ),
            SettingItem(
                title = "Fechar janelas com velocidade",
                description = SharedPreferencesKeys.CLOSE_WINDOWS_ON_SPEED.description,
                checked = closeWindowsOnSpeed,
                onCheckedChange = {
                    closeWindowsOnSpeed = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.CLOSE_WINDOWS_ON_SPEED.key, it) }
                },
                sliderValue = speedThreshold.toInt(),
                sliderRange = 10..120,
                onSliderChange = { newSpeed ->
                    speedThreshold = newSpeed.toFloat()
                    prefs.edit { putFloat(SharedPreferencesKeys.SPEED_THRESHOLD.key, newSpeed.toFloat()) }
                },
                sliderLabel = "Velocidade: $speedThreshold km/h"
            ),
            SettingItem(
                title = "Fechar teto solar com velocidade",
                description = SharedPreferencesKeys.CLOSE_SUNROOF_ON_SPEED.description,
                checked = closeSunroofOnSpeed,
                onCheckedChange = {
                    closeSunroofOnSpeed = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_SPEED.key, it) }
                },
                sliderValue = closeSunroofSpeedThreshold.toInt(),
                sliderRange = 10..120,
                onSliderChange = { newSpeed ->
                    closeSunroofSpeedThreshold = newSpeed.toFloat()
                    prefs.edit { putFloat(SharedPreferencesKeys.SUNROOF_SPEED_THRESHOLD.key, newSpeed.toFloat()) }
                },
                sliderLabel = "Velocidade: ${closeSunroofSpeedThreshold.toInt()} km/h"
            ),
            SettingItem(
                title = "Manter desativado monitoramento de distrações",
                description = "Desabilita alertas de distração durante a condução",
                checked = disableMonitoring,
                onCheckedChange = {
                    disableMonitoring = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.DISABLE_MONITORING.key, it) }
                    ServiceManager.getInstance().setMonitoringEnabled(!it)
                }
            ),
            SettingItem(
                title = "Desativar AVAS",
                description = "Sistema de alerta de veículo silencioso",
                checked = disableAvas,
                onCheckedChange = {
                    disableAvas = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.DISABLE_AVAS.key, it) }
                    ServiceManager.getInstance().setAvasEnabled(!it)
                }
            ),
            SettingItem(
                title = "Desativar câmera AVM quando parado",
                description = "Desliga câmera de visão 360° quando o veículo está parado",
                checked = disableAvmCarStopped,
                onCheckedChange = {
                    disableAvmCarStopped = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.DISABLE_AVM_CAR_STOPPED.key, it) }
                }
            ),
            SettingItem(
                title = "Controle do A/C pelo volante",
                description = SharedPreferencesKeys.ENABLE_AC_CONTROL_VIA_STEERING_WHEEL.description,
                checked = enableControlAcViaSteeringWheel,
                onCheckedChange = {
                    enableControlAcViaSteeringWheel = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_AC_CONTROL_VIA_STEERING_WHEEL.key, it) }
                }
            ),
            SettingItem(
                title = "Ligar ventilação do banco do motorisca com A/C ligado",
                description = SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.description,
                checked = enableSeatVentilationOnAcOn,
                onCheckedChange = {
                    enableSeatVentilationOnAcOn = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.key, it) }
                }
            ),
            SettingItem(
                title = "Desligar bluetooth ao desligar",
                description = SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.description,
                checked = disableBluetoothOnPowerOff,
                onCheckedChange = {
                    disableBluetoothOnPowerOff = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.key, it) }
                }
            ),
            SettingItem(
                title = "Desligar ponto de acesso ao desligar",
                description = SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF.description,
                checked = disableHotspotOnPowerOff,
                onCheckedChange = {
                    disableHotspotOnPowerOff = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF.key, it) }
                }
            ),
            SettingItem(
                title = "Habilitar botões personalizados no volante",
                description = SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.description,
                checked = enableCustomSteeringWheelButtons,
                onCheckedChange = {
                    enableCustomSteeringWheelButtons = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.key, it) }
                    ServiceManager.getInstance().ensureSteeringWheelButtonIntegration()
                },
                customContent = if (enableCustomSteeringWheelButtons) {
                    {
                        var action1 by remember { mutableStateOf(prefs.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION.key, SteeringWheelCustomActionType.DEFAULT.key)!!) }
                        var action2 by remember { mutableStateOf(prefs.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION.key, SteeringWheelCustomActionType.DEFAULT.key)!!) }
                        var package1 by remember { mutableStateOf(prefs.getString(SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1.key, "")!!) }
                        var package2 by remember { mutableStateOf(prefs.getString(SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2.key, "")!!) }
                        var expanded1 by remember { mutableStateOf(false) }
                        var expanded2 by remember { mutableStateOf(false) }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = Color(0xFF3A3F47), thickness = 1.dp)

                            Text("Botão 1", color = Color.White, fontSize = 16.sp)
                            ExposedDropdownMenuBox(
                                expanded = expanded1,
                                onExpandedChange = { expanded1 = !expanded1 }
                            ) {
                                TextField(
                                    value = SteeringWheelCustomActionType.entries.find { it.key == action1 }?.description ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Tipo de Ação") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded1) },
                                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                                    modifier = Modifier.menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded1,
                                    onDismissRequest = { expanded1 = false }
                                ) {
                                    SteeringWheelCustomActionType.entries.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type.description) },
                                            onClick = {
                                                action1 = type.key
                                                prefs.edit { putString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION.key, type.key) }
                                                expanded1 = false
                                                ServiceManager.getInstance().ensureSteeringWheelButtonIntegration()
                                            }
                                        )
                                    }
                                }
                            }
                            if (action1 == SteeringWheelCustomActionType.OPEN_APP.key) {
                                TextField(
                                    value = package1,
                                    onValueChange = { newPkg ->
                                        package1 = newPkg
                                        prefs.edit { putString(SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1.key, newPkg) }
                                    },
                                    label = { Text("Pacote do App") },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF2A2F37),
                                        unfocusedContainerColor = Color(0xFF2A2F37),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color(0xFFB0B8C4),
                                        focusedIndicatorColor = Color(0xFF4A9EFF),
                                        unfocusedIndicatorColor = Color(0xFF3A3F47)
                                    )
                                )
                            }

                            HorizontalDivider(color = Color(0xFF3A3F47), thickness = 1.dp)

                            Text("Botão 2", color = Color.White, fontSize = 16.sp)
                            ExposedDropdownMenuBox(
                                expanded = expanded2,
                                onExpandedChange = { expanded2 = !expanded2 }
                            ) {
                                TextField(
                                    value = SteeringWheelCustomActionType.entries.find { it.key == action2 }?.description ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Tipo de Ação") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded2) },
                                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                                    modifier = Modifier.menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded2,
                                    onDismissRequest = { expanded2 = false }
                                ) {
                                    SteeringWheelCustomActionType.entries.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type.description) },
                                            onClick = {
                                                action2 = type.key
                                                prefs.edit { putString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION.key, type.key) }
                                                expanded2 = false
                                                ServiceManager.getInstance().ensureSteeringWheelButtonIntegration()
                                            }
                                        )
                                    }
                                }
                            }
                            if (action2 == SteeringWheelCustomActionType.OPEN_APP.key) {
                                TextField(
                                    value = package2,
                                    onValueChange = { newPkg ->
                                        package2 = newPkg
                                        prefs.edit { putString(SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2.key, newPkg) }
                                    },
                                    label = { Text("Pacote do App") },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF2A2F37),
                                        unfocusedContainerColor = Color(0xFF2A2F37),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color(0xFFB0B8C4),
                                        focusedIndicatorColor = Color(0xFF4A9EFF),
                                        unfocusedIndicatorColor = Color(0xFF3A3F47)
                                    )
                                )
                            }
                        }
                    }
                } else null
            ),
            SettingItem(
                title = "Ajustar brilho automaticamente",
                description = "Ajusta o brilho da tela automaticamente",
                checked = enableAutoBrightness,
                onCheckedChange = {
                    enableAutoBrightness = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_AUTO_BRIGHTNESS.key, it) }
                    AutoBrightnessManager.getInstance().setEnabled(it)
                },
                customContent = if (enableAutoBrightness) {
                    {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(color = Color(0xFF3A3F47), thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // Início da noite
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showStartPicker = true }
                                        .background(
                                            Color(0xFF2A2F37),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Início da noite",
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "${String.format("%02d", nightStartHour)}:${String.format("%02d", nightStartMinute)}",
                                            color = Color(0xFF4A9EFF),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Fim da noite
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showEndPicker = true }
                                        .background(
                                            Color(0xFF2A2F37),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Fim da noite",
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "${String.format("%02d", nightEndHour)}:${String.format("%02d", nightEndMinute)}",
                                            color = Color(0xFF4A9EFF),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Slider para nível de brilho diurno
                            Column {
                                Text("Nível de brilho diurno: $dayBrightnessLevel", color = Color.White, fontSize = 14.sp)
                                Slider(
                                    value = dayBrightnessLevel.toFloat(),
                                    onValueChange = { newValue ->
                                        dayBrightnessLevel = newValue.toInt()
                                        prefs.edit { putInt(SharedPreferencesKeys.AUTO_BRIGHTNESS_LEVEL_DAY.key, dayBrightnessLevel) }
                                    },
                                    valueRange = 1f..10f,
                                    steps = 9,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AppColors.Primary,
                                        activeTrackColor = AppColors.Primary,
                                        inactiveTrackColor = Color(0xFF2C3139),
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent,
                                        disabledThumbColor = AppColors.Primary,
                                        disabledActiveTrackColor = AppColors.Primary,
                                        disabledInactiveTrackColor = Color(0xFF2C3139)
                                    )
                                )
                            }

                            // Slider para nível de brilho noturno
                            Column {
                                Text("Nível de brilho noturno: $nightBrightnessLevel", color = Color.White, fontSize = 14.sp)
                                Slider(
                                    value = nightBrightnessLevel.toFloat(),
                                    onValueChange = { newValue ->
                                        nightBrightnessLevel = newValue.toInt()
                                        prefs.edit { putInt(SharedPreferencesKeys.AUTO_BRIGHTNESS_LEVEL_NIGHT.key, nightBrightnessLevel) }
                                    },
                                    valueRange = 1f..10f,
                                    steps = 9,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AppColors.Primary,
                                        activeTrackColor = AppColors.Primary,
                                        inactiveTrackColor = Color(0xFF2C3139),
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent,
                                        disabledThumbColor = AppColors.Primary,
                                        disabledActiveTrackColor = AppColors.Primary,
                                        disabledInactiveTrackColor = Color(0xFF2C3139)
                                    )
                                )
                            }
                        }
                    }
                } else null
            ),
            SettingItem(
                title = "Definir volume inicial",
                description = SharedPreferencesKeys.SET_STARTUP_VOLUME.description,
                checked = setStartupVolume,
                onCheckedChange = {
                    setStartupVolume = it
                    prefs.edit { putBoolean(SharedPreferencesKeys.SET_STARTUP_VOLUME.key, it) }
                },
                sliderValue = volume,
                sliderRange = 0..40,
                onSliderChange = { newVolume ->
                    volume = newVolume
                    prefs.edit { putInt(SharedPreferencesKeys.STARTUP_VOLUME.key, newVolume) }
                },
                sliderLabel = "Volume: $volume"
            )
        )
    )

    TwoColumnSettingsLayout(settingsList = settingsList)

    if (showStartPicker) {
        LaunchedEffect(Unit) {
            val dialog = TimePickerDialog(
                context,
                { _, h, m ->
                    nightStartHour = h
                    nightStartMinute = m
                    prefs.edit {
                        putInt(SharedPreferencesKeys.NIGHT_START_HOUR.key, h)
                        putInt(SharedPreferencesKeys.NIGHT_START_MINUTE.key, m)
                    }
                    AutoBrightnessManager.getInstance().updateSchedule()
                },
                nightStartHour,
                nightStartMinute,
                true
            )
            dialog.setOnDismissListener { showStartPicker = false }
            dialog.show()
        }
    }
    if (showEndPicker) {
        LaunchedEffect(Unit) {
            val dialog = TimePickerDialog(
                context,
                { _, h, m ->
                    nightEndHour = h
                    nightEndMinute = m
                    prefs.edit {
                        putInt(SharedPreferencesKeys.NIGHT_END_HOUR.key, h)
                        putInt(SharedPreferencesKeys.NIGHT_END_MINUTE.key, m)
                    }
                    AutoBrightnessManager.getInstance().updateSchedule()
                },
                nightEndHour,
                nightEndMinute,
                true
            )
            dialog.setOnDismissListener { showEndPicker = false }
            dialog.show()
        }
    }
}

@Composable
fun FridaHooksTab() {
    val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    var enableFridaHooks by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.key, false)) }
    var enableFridaHookSystemServer by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOK_SYSTEM_SERVER.key, false)) }
    var showFridaDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SettingCard(
                title = "Habilitar Frida Hooks",
                description = SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.description,
                checked = enableFridaHooks,
                onCheckedChange = { newValue ->
                    if (!newValue) {
                        enableFridaHooks = false
                        prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.key, false) }
                    } else {
                        showFridaDialog = true
                    }
                }
            )
        }
        item {
            SettingCard(
                title = "Hook System Server",
                description = SharedPreferencesKeys.ENABLE_FRIDA_HOOK_SYSTEM_SERVER.description,
                checked = enableFridaHookSystemServer,
                onCheckedChange = { newValue ->
                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOK_SYSTEM_SERVER.key, newValue) }
                    enableFridaHookSystemServer = newValue
                    if (newValue)
                        FridaUtils.injectSystemServer()
                }
            )
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF13151A)
                )
            ) {
                Button(
                    onClick = { showManualDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4A9EFF)
                    )
                ) {
                    Text("Injetar Código Manual", color = Color.White)
                }
            }
        }
    }
    if (showFridaDialog) {
        AlertDialog(
            onDismissRequest = { showFridaDialog = false },
            title = { Text("Confirmação") },
            text = { Text("Ativar scripts fridas é uma função experimental que pode causar instabilidades, utilize por conta e risco. Caso não saiba o que é essa função é melhor manter desativada") },
            confirmButton = {
                TextButton(onClick = {
                    showFridaDialog = false
                    enableFridaHooks = true
                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.key, true) }
                    ServiceManager.getInstance().initializeFrida()
                }) {
                    Text("Ativar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFridaDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Hooks Manuais") },
            text = {
                val manuals = FridaUtils.ScriptProcess.entries.filter { it.injectMode == FridaUtils.InjectMode.MANUAL }
                LazyColumn {
                    items(manuals) { script ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(script.process)
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { FridaUtils.injectScript(script, false) }) {
                                Text("Injetar")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
fun TelasTab() {
    val context = LocalContext.current
    val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    var enableProjector by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key, false)) }
    var enableWarning by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_REVISION_WARNING.key, false)) }
    var enableCustomIntegration by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.key, false)) }
    var nextKmText by remember { mutableStateOf(prefs.getInt(SharedPreferencesKeys.INSTRUMENT_REVISION_KM.key, 12000).toString()) }
    var nextDateMillis by remember { mutableLongStateOf(prefs.getLong(SharedPreferencesKeys.INSTRUMENT_REVISION_NEXT_DATE.key, 0L)) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val formattedNextDate = if (nextDateMillis > 0) dateFormatter.format(nextDateMillis) else "Não definido"

    val settingsList = listOf(
        SettingItem(
            title = "Projetor do painel",
            description = SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.description,
            checked = enableProjector,
            onCheckedChange = {
                enableProjector = it
                prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key, it) }
                if (!it) {
                    enableWarning = false
                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_REVISION_WARNING.key, false) }
                    enableCustomIntegration = false
                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.key, false) }

                    try {
                        ServiceManager.getInstance().ensureSystemApps()
                    } catch (e: Exception) {
                        Log.e("TelasTab", "Erro ao desabilitar projetor: ${e.message}", e)
                    }
                }
            }
        ),
        SettingItem(
            title = "Aviso de revisão",
            description = SharedPreferencesKeys.ENABLE_INSTRUMENT_REVISION_WARNING.description,
            checked = enableWarning,
            onCheckedChange = {
                enableWarning = it
                prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_REVISION_WARNING.key, it) }
            },
            enabled = enableProjector
        ),
        SettingItem(
            title = "Integração de mídia customizada",
            description = SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.description,
            checked = enableCustomIntegration,
            onCheckedChange = {
                enableCustomIntegration = it
                prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.key, it) }

                try {
                    ServiceManager.getInstance().ensureSystemApps()
                    if (enableCustomIntegration) {
                        ServiceManager.getInstance().startClusterHeartbeat()
                    }
                } catch (e: Exception) {
                    // Log do erro e desabilitar a opção se falhar
                    Log.e("TelasTab", "Erro ao configurar integração de mídia: ${e.message}", e)
                    enableCustomIntegration = false
                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.key, false) }
                }
            },
            enabled = enableProjector
        )
    )

    TwoColumnSettingsLayout(
        settingsList = settingsList,
        bottomContent = {
            if (enableWarning) {
                StyledCard(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                "Próxima KM:",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                TextField(
                                    value = nextKmText,
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                                            nextKmText = newValue
                                            newValue.toIntOrNull()?.let {
                                                prefs.edit { putInt(SharedPreferencesKeys.INSTRUMENT_REVISION_KM.key, it) }
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF2A2F37),
                                        unfocusedContainerColor = Color(0xFF2A2F37),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color(0xFFB0B8C4),
                                        focusedIndicatorColor = Color(0xFF4A9EFF),
                                        unfocusedIndicatorColor = Color(0xFF3A3F47)
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val currentKm = ServiceManager.getInstance().totalOdometer
                                        val newNextKm = currentKm + 12000
                                        nextKmText = newNextKm.toString()
                                        prefs.edit { putInt(SharedPreferencesKeys.INSTRUMENT_REVISION_KM.key, newNextKm) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4A9EFF)
                                    )
                                ) {
                                    Text("Resetar", color = Color.White)
                                }
                            }
                        }

                        Column {
                            Text(
                                "Próxima data: $formattedNextDate",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                Button(
                                    onClick = { showDatePicker = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4A9EFF)
                                    )
                                ) {
                                    Text("Informar manual", color = Color.White)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        cal.add(Calendar.YEAR, 1)
                                        nextDateMillis = cal.timeInMillis
                                        prefs.edit { putLong(SharedPreferencesKeys.INSTRUMENT_REVISION_NEXT_DATE.key, nextDateMillis) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4A9EFF)
                                    )
                                ) {
                                    Text("Resetar", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    )


    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        if (nextDateMillis > 0) calendar.timeInMillis = nextDateMillis

        LaunchedEffect(showDatePicker) {
            if (showDatePicker) {
                val dialog = DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val cal = Calendar.getInstance()
                        cal.set(year, month, day)
                        nextDateMillis = cal.timeInMillis
                        prefs.edit { putLong(SharedPreferencesKeys.INSTRUMENT_REVISION_NEXT_DATE.key, nextDateMillis) }
                        showDatePicker = false
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                dialog.setOnDismissListener { showDatePicker = false }
                dialog.show()
            }
        }
    }
}

@Composable
fun CurrentValuesTab() {
    val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    val advancedUse = prefs.getBoolean(SharedPreferencesKeys.ADVANCE_USE.key, false)
    val dataMap = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(ServiceManager.getInstance().allCurrentCachedData)
        }
    }
    var showConfigDialog by remember { mutableStateOf(false) }
    val allConstants = remember { CarConstants.entries.map { it.value } }
    val defaultKeys = remember { ServiceManager.DEFAULT_KEYS.map { it.value } } // Assuming DEFAULT_KEYS is Array<CarConstants>
    val filteredConstants = remember { allConstants.filter { it !in defaultKeys } }
    val monitoredSet = remember {
        mutableStateOf(prefs.getStringSet(SharedPreferencesKeys.CAR_MONITOR_PROPERTIES.key, emptySet()) ?: emptySet())
    }
    val tempChecked = remember {
        mutableStateMapOf<String, Boolean>().apply {
            allConstants.forEach { this[it] = monitoredSet.value.contains(it) }
        }
    }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var searchQueryValues by remember { mutableStateOf("") }
    var searchQueryConfig by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        val listener = IDataChanged { key, value -> dataMap[key] = value }
        ServiceManager.getInstance().addDataChangedListener(listener)
        onDispose {
            ServiceManager.getInstance().removeDataChangedListener(listener)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        if (advancedUse) {
            Button(
                onClick = { showConfigDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A9EFF)
                )
            ) {
                Text("Configurar", color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
        }
        TextField(
            value = searchQueryValues,
            onValueChange = { searchQueryValues = it },
            label = { Text("Pesquisar valores") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF2A2F37),
                unfocusedContainerColor = Color(0xFF2A2F37),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color(0xFFB0B8C4),
                focusedIndicatorColor = Color(0xFF4A9EFF),
                unfocusedIndicatorColor = Color(0xFF3A3F47),
                focusedLabelColor = Color(0xFF4A9EFF),
                unfocusedLabelColor = Color(0xFFB0B8C4)
            )
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val filteredData = dataMap.toList()
                .filter { it.first.lowercase().contains(searchQueryValues.lowercase()) }
                .sortedBy { it.first }
            items(filteredData) { (key, value) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .then(
                            if (advancedUse) Modifier.clickable {
                                selectedKey = key
                                newValue = value
                                showUpdateDialog = true
                            } else Modifier
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF13151A)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        "$key: $value",
                        modifier = Modifier.padding(8.dp),
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
    if (showConfigDialog && advancedUse) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Configurar Monitoramento") },
            text = {
                Column {
                    TextField(
                        value = searchQueryConfig,
                        onValueChange = { searchQueryConfig = it },
                        label = { Text("Pesquisar constantes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    val checked = filteredConstants.filter { tempChecked[it] ?: false }.sorted()
                    val unchecked = filteredConstants.filter { !(tempChecked[it] ?: false) }.sorted()
                    val sortedConstants = (checked + unchecked)
                        .filter { it.lowercase().contains(searchQueryConfig.lowercase()) }
                    LazyColumn {
                        items(sortedConstants) { constant ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = tempChecked[constant] ?: false,
                                    onCheckedChange = { tempChecked[constant] = it }
                                )
                                Text(constant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newSet = tempChecked.filterValues { it }.keys.toSet()
                    prefs.edit { putStringSet(SharedPreferencesKeys.CAR_MONITOR_PROPERTIES.key, newSet) }
                    monitoredSet.value = newSet
                    showConfigDialog = false
                    ServiceManager.getInstance().updateMonitoringProperties()
                    dataMap.clear()
                    dataMap.putAll(ServiceManager.getInstance().allCurrentCachedData)
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    allConstants.forEach { tempChecked[it] = monitoredSet.value.contains(it) }
                    showConfigDialog = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
    if (showUpdateDialog && advancedUse) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Atualizar $selectedKey") },
            text = {
                TextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = { Text("Novo valor") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ServiceManager.getInstance().updateData(selectedKey, newValue)
                    showUpdateDialog = false
                }) {
                    Text("Atualizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ContentArea(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(AppDimensions.ContentPadding)
    ) {
        content()
    }
}

@Composable
fun AppActionButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) AppColors.Primary else AppColors.ButtonSecondary
        ),
        shape = RoundedCornerShape(AppDimensions.ButtonCornerRadius),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            color = AppColors.TextPrimary,
            fontSize = if (isPrimary) 14.sp else 13.sp,
            fontWeight = if (isPrimary) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun InstallAppsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf(listOf<AppInfo>()) }
    var downloadingApp by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    val pm = context.packageManager
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Permission requested */ }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var installResult by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var downloadingUrl by remember { mutableStateOf(false) }
    var urlProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://raw.githubusercontent.com/bobaoapae/haval-impulse-static-files/refs/heads/main/apps.json?rnd=${System.currentTimeMillis()}")
                val conn = url.openConnection() as HttpURLConnection
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val jsonString = reader.use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    val appList = mutableListOf<AppInfo>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val iconUrl = obj.optString("appIcon", null)
                        appList.add(
                            AppInfo(
                                obj.getString("appName"),
                                obj.getString("appVersion"),
                                obj.getString("appPackageName"),
                                obj.getString("appLink"),
                                if (!iconUrl.isNullOrEmpty() && iconUrl != "null") iconUrl else null
                            )
                        )
                        // Debug log
                        Log.d(TAG, "App: ${obj.getString("appName")}, Icon URL: $iconUrl")
                    }
                    apps = appList
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading apps", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun getInstalledVersion(packageName: String): String? {
        return try {
            val info = pm.getPackageInfo(packageName, 0)
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun compareVersions(v1: String?, v2: String): Int {
        if (v1 == null) return -1
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until min(parts1.size, parts2.size)) {
            if (parts1[i] > parts2[i]) return 1
            if (parts1[i] < parts2[i]) return -1
        }
        return parts1.size.compareTo(parts2.size)
    }

    fun startDownload(app: AppInfo) {
        downloadingApp = app.packageName
        downloadProgress = downloadProgress.toMutableMap().apply { put(app.packageName, 0f) }
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.getExternalFilesDir(null), "${app.packageName}.apk")
                val url = URL(app.link)
                val conn = url.openConnection() as HttpURLConnection
                val length = conn.contentLength
                val input = BufferedInputStream(conn.inputStream)
                val output = FileOutputStream(file)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var total = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    total += bytesRead
                    if (length > 0) {
                        downloadProgress = downloadProgress.toMutableMap().apply { put(app.packageName, total.toFloat() / length) }
                    }
                }
                output.close()
                input.close()
                withContext(Dispatchers.Main) {
                    if (!pm.canRequestPackageInstalls()) {
                        showPermissionDialog = true
                        return@withContext
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
            } finally {
                downloadingApp = null
            }
        }
    }

    fun startDownloadFromUrl(urlString: String) {
        downloadingUrl = true
        urlProgress = 0f
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.getExternalFilesDir(null), "custom.apk")
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                val length = conn.contentLength
                val input = BufferedInputStream(conn.inputStream)
                val output = FileOutputStream(file)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var total = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    total += bytesRead
                    if (length > 0) {
                        urlProgress = total.toFloat() / length
                    }
                }
                output.close()
                input.close()
                withContext(Dispatchers.Main) {
                    if (!pm.canRequestPackageInstalls()) {
                        showPermissionDialog = true
                        return@withContext
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
            } finally {
                downloadingUrl = false
            }
        }
    }

    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        }
        context.startActivity(intent)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // URL Input Section
        item(span = { GridItemSpan(4) }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("URL do APK") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF2A2F37),
                            unfocusedContainerColor = Color(0xFF2A2F37),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFB0B8C4),
                            focusedIndicatorColor = Color(0xFF4A9EFF),
                            unfocusedIndicatorColor = Color(0xFF3A3F47),
                            focusedLabelColor = Color(0xFF4A9EFF),
                            unfocusedLabelColor = Color(0xFFB0B8C4)
                        )
                    )
                    if (!downloadingUrl) {
                        Button(
                            onClick = { if (urlInput.isNotEmpty()) startDownloadFromUrl(urlInput) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A9EFF)
                            ),
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(0.dp)
                        ) {
                            Text("Instalar via URL", color = Color.White)
                        }
                    }
                }

                if (downloadingUrl) {
                    LinearProgressIndicator(
                        progress = { urlProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4A9EFF)
                    )
                }

                if (installResult.isNotEmpty()) {
                    Text(installResult, color = Color.White, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Aplicativos disponíveis:",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Loading indicator
        if (isLoading) {
            item(span = { GridItemSpan(4) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF4A9EFF)
                    )
                }
            }
        } else {
            // Apps Grid - Ordenados por prioridade: Atualizar > Instalar > Instalados
            // Dentro de cada grupo, ordena alfabeticamente
            val sortedApps = apps.sortedWith(
                compareBy(
                    { app ->
                        val installedVersion = getInstalledVersion(app.packageName)
                        val isInstalled = installedVersion != null
                        val needsUpdate = isInstalled && compareVersions(installedVersion, app.version) < 0

                        when {
                            needsUpdate -> 0  // Prioridade máxima: precisa atualizar
                            !isInstalled -> 1 // Segunda prioridade: disponível para instalar
                            else -> 2         // Última prioridade: já instalado e atualizado
                        }
                    },
                    { app -> app.name.lowercase() } // Ordenação alfabética dentro de cada grupo
                ))

            gridItems(sortedApps) { app ->
                val installedVersion = getInstalledVersion(app.packageName)
                val isInstalled = installedVersion != null
                val needsUpdate = isInstalled && compareVersions(installedVersion, app.version) < 0
                val progress = downloadProgress[app.packageName] ?: 0f

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .padding(vertical = 16.dp, horizontal = 16.dp)
                        .border(
                            width = 1.dp,
                            color = Color(0xFF1D2430),
                            shape = RoundedCornerShape(0.dp),
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF13151A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // App Icon Container with padding
                            Box(
                                modifier = Modifier.size(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF2A2F37)
                                ) {
                                    if (!app.iconUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(app.iconUrl)
                                                .crossfade(true)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .allowHardware(false)
                                                .build(),
                                            contentDescription = app.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            onError = {
                                                Log.e(TAG, "Error loading icon for ${app.name}: ${it.result.throwable}")
                                            }
                                        )
                                    } else {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                Icons.Default.Build,
                                                contentDescription = app.name,
                                                tint = Color(0xFF4A9EFF),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // App Name
                            Text(
                                app.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            // Version info
                            Text(
                                "v${app.version}",
                                fontSize = 12.sp,
                                color = Color(0xFFB0B8C4),
                                lineHeight = 14.sp
                            )

                            if (isInstalled) {
                                Text(
                                    "Inst: v${installedVersion}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF808080),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        // Action Button Section
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (downloadingApp == app.packageName) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp),
                                        color = Color(0xFF4A9EFF),
                                        trackColor = Color(0xFF3A3F47)
                                    )
                                    Text(
                                        "${(progress * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Botão principal (Instalar ou Atualizar)
                                    if (!isInstalled || needsUpdate) {
                                        AppActionButton(
                                            text = if (!isInstalled) "Instalar" else "Atualizar",
                                            onClick = { startDownload(app) },
                                            isPrimary = true
                                        )
                                    }

                                    // Botão de desinstalar
                                    if (isInstalled) {
                                        AppActionButton(
                                            text = "Desinstalar",
                                            onClick = { uninstall(app.packageName) },
                                            isPrimary = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permissão necessária") },
            text = { Text("Permita a instalação de apps de fontes desconhecidas.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    requestPermissionLauncher.launch(intent)
                }) {
                    Text("Configurações")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun InformacoesTab() {
    val context = LocalContext.current
    val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    var isActive by remember { mutableStateOf(ServiceManager.getInstance().isServicesInitialized) }
    var bypassSelfInstallationCheck by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.key, false)) }
    var selfInstallationCheck by remember { mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.SELF_INSTALLATION_INTEGRITY_CHECK.key, false)) }
    var formattedTime by remember { mutableStateOf("Não inicializado") }
    var formattedTime2 by remember { mutableStateOf("Não inicializado") }
    var formattedTime3 by remember { mutableStateOf("Não inicializado") }
    var version by remember { mutableStateOf("Desconhecida") }
    var clickCount by remember { mutableIntStateOf(0) }
    var showAdvancedDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var updateAvailable by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Permission requested */ }
    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            version = packageInfo.versionName ?: "Desconhecida"
        } catch (e: PackageManager.NameNotFoundException) {
            version = "Erro"
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            isActive = ServiceManager.getInstance().isServicesInitialized
            val timeBoot = ServiceManager.getInstance().timeBootReceived
            formattedTime = if (isActive && timeBoot > 0) {
                val minutes = timeBoot / 60000
                val seconds = (timeBoot / 1000) % 60
                val millis = timeBoot % 1000
                String.format("%02d:%02d.%03d", minutes, seconds, millis)
            } else {
                "Não inicializado"
            }
            val timeStart = ServiceManager.getInstance().timeStartInitialization
            formattedTime2 = if (isActive && timeStart > 0) {
                val minutes = timeStart / 60000
                val seconds = (timeStart / 1000) % 60
                val millis = timeStart % 1000
                String.format("%02d:%02d.%03d", minutes, seconds, millis)
            } else {
                "Não inicializado"
            }
            val timeInit = ServiceManager.getInstance().timeInitialized
            formattedTime3 = if (isActive && timeInit > 0) {
                val minutes = timeInit / 60000
                val seconds = (timeInit / 1000) % 60
                val millis = timeInit % 1000
                String.format("%02d:%02d.%03d", minutes, seconds, millis)
            } else {
                "Não inicializado"
            }
            delay(100)
        }
    }

    suspend fun getLatestReleaseInfo(): Pair<String?, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/bobaoapae/haval-app-tool-multimidia/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.use { it.readText() }
                    val json = JSONObject(response)
                    val tag = json.getString("tag_name")
                    val assets = json.getJSONArray("assets")
                    var dlUrl: String? = null
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            dlUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                    tag to dlUrl
                } else null to null
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching latest release info", e)
                null to null
            }
        }
    }

    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until min(parts1.size, parts2.size)) {
            if (parts1[i] > parts2[i]) return 1
            if (parts1[i] < parts2[i]) return -1
        }
        return parts1.size.compareTo(parts2.size)
    }

    fun startDownload() {
        isDownloading = true
        downloadProgress = 0f
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.getExternalFilesDir(null), "update.apk")
                withContext(Dispatchers.IO) {
                    val url = URL(downloadUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    val length = conn.contentLength
                    val input = BufferedInputStream(conn.inputStream)
                    val output = FileOutputStream(file)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var total = 0
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        total += bytesRead
                        if (length > 0) downloadProgress = total.toFloat() / length
                    }
                    output.close()
                    input.close()
                }
                isDownloading = false
                withContext(Dispatchers.Main) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        showPermissionDialog = true
                        return@withContext
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                isDownloading = false
                downloadError = e.message ?: "Erro desconhecido"
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Seção de Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF13151A)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Status do Sistema",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                HorizontalDivider(color = Color(0xFF1D2430))

                if (!bypassSelfInstallationCheck) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Instalado corretamente:", color = Color(0xFFB0B8C4))
                        Text(
                            if (selfInstallationCheck) "Sim" else "Não",
                            color = if (selfInstallationCheck) Color(0xFF4ADE80) else Color(0xFFEF4444)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Estado:", color = Color(0xFFB0B8C4))
                    Text(
                        if (isActive) "Ativo" else "Inativo",
                        color = if (isActive) Color(0xFF4ADE80) else Color(0xFFEF4444),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Boot Completed:", color = Color(0xFFB0B8C4), fontSize = 14.sp)
                        Text(formattedTime, color = Color.White, fontSize = 14.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Início:", color = Color(0xFFB0B8C4), fontSize = 14.sp)
                        Text(formattedTime2, color = Color.White, fontSize = 14.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Inicialização:", color = Color(0xFFB0B8C4), fontSize = 14.sp)
                        Text(formattedTime3, color = Color.White, fontSize = 14.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFF1D2430))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Versão", color = Color(0xFFB0B8C4), fontSize = 14.sp)
                        Text(
                            version,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                clickCount++
                                if (clickCount >= 5) {
                                    showAdvancedDialog = true
                                    clickCount = 0
                                }
                            }
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val (latest, dlUrl) = getLatestReleaseInfo()
                                if (latest != null && dlUrl != null) {
                                    val currentClean = version.removePrefix("v")
                                    val latestClean = latest.removePrefix("v")
                                    // Se a versão atual for 99.99, sempre permitir instalação da versão mais recente
                                    if (currentClean == "99.99" || compareVersions(latestClean, currentClean) > 0) {
                                        latestVersion = latest
                                        downloadUrl = dlUrl
                                        updateAvailable = true
                                    } else {
                                        updateMessage = "Você está na versão mais recente"
                                        showUpdateDialog = true
                                    }
                                } else {
                                    updateMessage = "Erro ao verificar atualizações"
                                    showUpdateDialog = true
                                }
                            }
                        },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Primary
                        ),
                        shape = RoundedCornerShape(AppDimensions.ButtonCornerRadius)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Buscar Atualizações",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscar Atualizações", fontSize = 14.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFF1D2430))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_MAIN).apply {
                                component = ComponentName("com.android.settings", "com.android.settings.Settings")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Primary
                        ),
                        shape = RoundedCornerShape(AppDimensions.ButtonCornerRadius)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configurações",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir Configurações do Android", color = Color.White)
                    }
                }
            }
        }

        // Seção de Contribuição
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF13151A)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Contribua para o Desenvolvimento",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = Color(0xFF1D2430))

                Text(
                    "Ajude a manter este projeto ativo! Sua contribuição é muito importante para o desenvolvimento contínuo do app.",
                    fontSize = 14.sp,
                    color = Color(0xFFB0B8C4),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                // QR Code
                Image(
                    painter = painterResource(id = R.drawable.qrcode),
                    contentDescription = "QR Code para contribuição",
                    modifier = Modifier
                        .size(200.dp)
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )

                Text(
                    "Escaneie o QR Code ou use a chave PIX: joaovitorbor@gmail.com",
                    fontSize = 16.sp,
                    color = Color(0xFFB0B8C4),
                    textAlign = TextAlign.Center
                )

                Text(
                    "Obrigado pelo seu apoio! 🙏",
                    fontSize = 14.sp,
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showAdvancedDialog) {
        AlertDialog(
            onDismissRequest = { showAdvancedDialog = false },
            title = { Text("Confirmação") },
            text = { Text("Quer ativar o uso avançado? Pode causar instabilidades, utilize por conta e risco.") },
            confirmButton = {
                TextButton(onClick = {
                    showAdvancedDialog = false
                    prefs.edit { putBoolean(SharedPreferencesKeys.ADVANCE_USE.key, true) }
                }) {
                    Text("Ativar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvancedDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Verificação de Atualização") },
            text = { Text(updateMessage) },
            confirmButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (updateAvailable) {
        AlertDialog(
            onDismissRequest = { updateAvailable = false },
            title = { Text("Atualização disponível: $latestVersion") },
            text = { Text("Deseja baixar?") },
            confirmButton = {
                TextButton(onClick = {
                    updateAvailable = false
                    startDownload()
                }) {
                    Text("Sim")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateAvailable = false }) {
                    Text("Não")
                }
            }
        )
    }

    if (isDownloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Baixando atualização") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { downloadProgress })
                    Text("${(downloadProgress * 100).toInt()}%")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    downloadJob?.cancel()
                    isDownloading = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (downloadError != null) {
        AlertDialog(
            onDismissRequest = { downloadError = null },
            title = { Text("Erro no download") },
            text = { Text(downloadError!!) },
            confirmButton = {
                TextButton(onClick = {
                    downloadError = null
                    startDownload()
                }) {
                    Text("Tentar novamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadError = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permissão necessária") },
            text = { Text("Permita a instalação de apps de fontes desconhecidas.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    requestPermissionLauncher.launch(intent)
                }) {
                    Text("Configurações")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    HavalShisukuTheme {
        MainScreen()
    }
}