package br.com.redesurftank.havalshisuku.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.ambientlight.AmbientLightService
import br.com.redesurftank.havalshisuku.managers.AutoBrightnessManager
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import coil.compose.AsyncImage
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.SteeringWheelClimateCommandType
import br.com.redesurftank.havalshisuku.models.SteeringWheelCustomActionType
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.SettingItem
import br.com.redesurftank.havalshisuku.ui.components.GroupedSettingsLayout
import br.com.redesurftank.havalshisuku.ui.components.SettingsGroups
import br.com.redesurftank.havalshisuku.ui.components.TwoColumnSettingsLayout
import br.com.redesurftank.havalshisuku.managers.HotRouterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// HotRouter: formata o epoch do statefile em HH:mm:ss.
private fun formatHms(epochSeconds: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochSeconds * 1000L))
}

/**
 * Shows or hides the left navigation pane (the system NAVIGATION_BAR window, 128px on the left edge).
 *
 * Uses `policy_control`, the AOSP PolicyControl override, which exists on this head unit's Android 9
 * (it was removed in Android 11). It is a persistent global setting, so it survives reboots and needs
 * no service-side reapplication.
 *
 * Whether GWM's SystemUI honours the hide flag is not verified - if the pane stays put, this is the
 * lever that failed, not the bar layout, which is correct in both pane states either way.
 */
private fun applyLeftNavPaneVisibility(hidden: Boolean) {
        Thread {
                        val command =
                                if (hidden)
                                        arrayOf(
                                                "settings",
                                                "put",
                                                "global",
                                                "policy_control",
                                                "immersive.navigation=*"
                                        )
                                else arrayOf("settings", "delete", "global", "policy_control")
                        // CONFERE depois de aplicar, e insiste ate 3 vezes.
                        //
                        // Antes era disparo cego: mandava o comando e so registrava a saida, sem
                        // olhar se pegou. Quando o Shizuku esta fora naquele instante, o comando
                        // falha em silencio — e no DESLIGAR o estrago e grande: a politica
                        // `immersive.navigation` fica valendo, a barra de navegacao continua
                        // transitoria e o Android passa a IGNORAR o toque no botao home. Aconteceu
                        // no carro: funcao desligada e home morto, enquanto os outros icones da
                        // mesma faixa (grade de apps, temperatura) seguiam normais — sao interface
                        // da montadora, nao da barra de navegacao.
                        var aplicado = false
                        for (tentativa in 1..3) {
                                br.com.redesurftank.havalshisuku.utils.ShizukuUtils
                                        .runCommandAndGetOutput(command)
                                val agora =
                                        runCatching {
                                                        br.com.redesurftank.havalshisuku.utils
                                                                .ShizukuUtils
                                                                .runCommandAndGetOutput(
                                                                        arrayOf(
                                                                                "settings",
                                                                                "get",
                                                                                "global",
                                                                                "policy_control"
                                                                        )
                                                                )
                                                }
                                                .getOrDefault("")
                                                .trim()
                                // `settings get` devolve a string "null" quando a chave nao existe.
                                aplicado =
                                        if (hidden) agora.contains("immersive.navigation")
                                        else agora.isEmpty() || agora == "null"
                                android.util.Log.w(
                                        "BasicSettingsScreen",
                                        "[NAV_PANE] hidden=$hidden tentativa=$tentativa " +
                                                "policy_control='$agora' ok=$aplicado"
                                )
                                if (aplicado) break
                                runCatching { Thread.sleep(700) }
                        }
                        if (!aplicado) {
                                android.util.Log.e(
                                        "BasicSettingsScreen",
                                        "[NAV_PANE] NAO consegui deixar policy_control no estado " +
                                                "pedido (hidden=$hidden); com hidden=false isso " +
                                                "mantem o botao home inerte"
                                )
                        }
                }
                .start()
}

// Seletor reutilizavel de acao do volante (usado p/ toque curto / duplo / longo de cada botao).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteeringActionPicker(
        label: String,
        actionKey: String,
        packageName: String,
        climateCommandKey: String,
        onActionSelected: (String) -> Unit,
        onPackageChanged: (String) -> Unit,
        onClimateCommandSelected: (SteeringWheelClimateCommandType) -> Unit,
) {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        var climateCommandExpanded by remember { mutableStateOf(false) }
        var showAppPicker by remember { mutableStateOf(false) }
        Text(label, color = Color(0xFFB0B8C4), fontSize = 14.sp)
        ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
        ) {
                TextField(
                        value = SteeringWheelCustomActionType.entries
                                .find { it.key == actionKey }?.description ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de Ação") },
                        trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                ) {
                        SteeringWheelCustomActionType.entries.forEach { type ->
                                DropdownMenuItem(
                                        text = { Text(type.description) },
                                        onClick = {
                                                onActionSelected(type.key)
                                                expanded = false
                                        }
                                )
                        }
                }
        }
        if (actionKey == SteeringWheelCustomActionType.OPEN_APP.key) {
                // Reaproveita o mesmo seletor de apps da tela "Telas" no lugar de digitar o pacote.
                val resolved =
                        remember(packageName) {
                                if (packageName.isBlank()) null
                                else DisplayAppLauncher.resolveAppInfo(context, packageName)
                        }
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2A2F37))
                                        .border(
                                                1.dp,
                                                Color(0xFF3A3F47),
                                                RoundedCornerShape(8.dp)
                                        )
                                        .clickable { showAppPicker = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        if (resolved?.icon != null) {
                                AsyncImage(
                                        model = resolved.icon,
                                        contentDescription = resolved.label,
                                        modifier = Modifier.size(32.dp),
                                        contentScale = ContentScale.Fit
                                )
                        } else {
                                Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color(0xFFB0B8C4)
                                )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = resolved?.label ?: "Selecionar aplicativo",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                        text =
                                                if (packageName.isBlank()) "Nenhum app escolhido"
                                                else packageName,
                                        color = Color(0xFFB0B8C4),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                        Text(
                                "ESCOLHER",
                                color = Color(0xFF4A9EFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                        )
                }
                if (showAppPicker) {
                        AppPickerDialog(
                                onDismiss = { showAppPicker = false },
                                onAppSelected = { app ->
                                        onPackageChanged(app.packageName)
                                        showAppPicker = false
                                }
                        )
                }
        }
        if (actionKey == SteeringWheelCustomActionType.CLIMATE_COMMAND.key) {
                SteeringWheelClimateCommandDropdown(
                        selectedCommandKey = climateCommandKey,
                        expanded = climateCommandExpanded,
                        onExpandedChange = { climateCommandExpanded = it },
                        onCommandSelected = { command ->
                                onClimateCommandSelected(command)
                                climateCommandExpanded = false
                        }
                )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicSettingsTab() {
        val context = LocalContext.current
        val prefs =
                App.getDeviceProtectedContext()
                        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        var isAdvancedUse by remember {
                mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ADVANCE_USE.key, false))
        }
        var selfInstallationCheck by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.SELF_INSTALLATION_INTEGRITY_CHECK.key,
                                false
                        )
                )
        }
        var bypassSelfInstallationCheck by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.key,
                                false
                        )
                )
        }
        var disableMonitoring by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.DISABLE_MONITORING.key, false)
                )
        }
        var disableAvas by remember {
                mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.DISABLE_AVAS.key, false))
        }
        var disableAvmCarStopped by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.DISABLE_AVM_CAR_STOPPED.key, false)
                )
        }
        var closeWindowOnPowerOff by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.CLOSE_WINDOW_ON_POWER_OFF.key, false)
                )
        }
        var closeSunroofOnPowerOff by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.CLOSE_SUNROOF_ON_POWER_OFF.key,
                                false
                        )
                )
        }
        var closeWindowOnLock by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.CLOSE_WINDOW_ON_LOCK.key, false)
                )
        }
        var closeSunroofOnLock by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_LOCK.key, false)
                )
        }
        var disableBluetoothOnLock by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_LOCK.key,
                                false
                        )
                )
        }
        var disableHotspotOnLock by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.DISABLE_HOTSPOT_ON_LOCK.key,
                                false
                        )
                )
        }
        var closeSunroofSunShadeOnCloseSunroof by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF.key,
                                false
                        )
                )
        }
        var setStartupVolume by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.SET_STARTUP_VOLUME.key, false)
                )
        }
        var volume by remember {
                mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.STARTUP_VOLUME.key, 1))
        }
        var closeWindowsOnSpeed by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.CLOSE_WINDOWS_ON_SPEED.key, false)
                )
        }
        var closeSunroofOnSpeed by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_SPEED.key, false)
                )
        }
        var speedThreshold by remember {
                mutableFloatStateOf(prefs.getFloat(SharedPreferencesKeys.SPEED_THRESHOLD.key, 15f))
        }
        var closeSunroofSpeedThreshold by remember {
                mutableFloatStateOf(
                        prefs.getFloat(SharedPreferencesKeys.SUNROOF_SPEED_THRESHOLD.key, 15f)
                )
        }
        var enableMaxAcOnUnlock by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.ENABLE_MAX_AC_ON_UNLOCK.key, false)
                )
        }
        var maxAcOnUnlockThreshold by remember {
                mutableFloatStateOf(
                        prefs.getFloat(SharedPreferencesKeys.MAX_AC_ON_UNLOCK_THRESHOLD.key, 34f)
                )
        }
        var maxAcTargetTemp by remember {
                mutableFloatStateOf(
                        prefs.getFloat(SharedPreferencesKeys.MAX_AC_TARGET_TEMP.key, 28f)
                )
        }
        var maxAcTimeout by remember {
                mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.MAX_AC_TIMEOUT.key, 0))
        }
        var enableAutoBrightness by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.ENABLE_AUTO_BRIGHTNESS.key, false)
                )
        }
        var autoBrightnessUseSun by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.AUTO_BRIGHTNESS_USE_SUN.key, false)
                )
        }
        var nightStartHour by remember {
                mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.NIGHT_START_HOUR.key, 20))
        }
        var nightStartMinute by remember {
                mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.NIGHT_START_MINUTE.key, 0))
        }
        var nightEndHour by remember {
                mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.NIGHT_END_HOUR.key, 6))
        }
        var nightEndMinute by remember {
                mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.NIGHT_END_MINUTE.key, 0))
        }
        var disableBluetoothOnPowerOff by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.key,
                                false
                        )
                )
        }
        var disableHotspotOnPowerOff by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF.key,
                                false
                        )
                )
        }
        var ambientLightBleEnabled by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_ENABLED.key, false)
                )
        }
        var nightBrightnessLevel by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.AUTO_BRIGHTNESS_LEVEL_NIGHT.key, 1)
                )
        }
        var dayBrightnessLevel by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.AUTO_BRIGHTNESS_LEVEL_DAY.key, 10)
                )
        }
        var enableSeatVentilationOnAcOn by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.key,
                                false
                        )
                )
        }
        var enableCustomSteeringWheelButtons by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.key,
                                false
                        )
                )
        }
        var steeringWheelButton1Action by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION.key,
                                SteeringWheelCustomActionType.DEFAULT.key
                        )
                                ?: SteeringWheelCustomActionType.DEFAULT.key
                )
        }
        var steeringWheelButton2Action by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION.key,
                                SteeringWheelCustomActionType.DEFAULT.key
                        )
                                ?: SteeringWheelCustomActionType.DEFAULT.key
                )
        }
        var steeringWheelButton1Package by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1.key,
                                ""
                        )
                                ?: ""
                )
        }
        var steeringWheelButton2Package by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2.key,
                                ""
                        )
                                ?: ""
                )
        }
        var steeringWheelButton1ClimateCommand by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_1.key,
                                SteeringWheelClimateCommandType.TOGGLE_AC.key
                        )
                                ?: SteeringWheelClimateCommandType.TOGGLE_AC.key
                )
        }
        var steeringWheelButton2ClimateCommand by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_2.key,
                                SteeringWheelClimateCommandType.TOGGLE_AC.key
                        )
                                ?: SteeringWheelClimateCommandType.TOGGLE_AC.key
                )
        }
        var steeringWheelButton1ActionDouble by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_DOUBLE
                                        .key,
                                SteeringWheelCustomActionType.DEFAULT.key
                        )
                                ?: SteeringWheelCustomActionType.DEFAULT.key
                )
        }
        var steeringWheelButton1ActionLong by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_LONG
                                        .key,
                                SteeringWheelCustomActionType.DEFAULT.key
                        )
                                ?: SteeringWheelCustomActionType.DEFAULT.key
                )
        }
        var steeringWheelButton2ActionDouble by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_DOUBLE
                                        .key,
                                SteeringWheelCustomActionType.DEFAULT.key
                        )
                                ?: SteeringWheelCustomActionType.DEFAULT.key
                )
        }
        var steeringWheelButton2ActionLong by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_LONG
                                        .key,
                                SteeringWheelCustomActionType.DEFAULT.key
                        )
                                ?: SteeringWheelCustomActionType.DEFAULT.key
                )
        }
        var steeringWheelButton1PackageDouble by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_DOUBLE
                                        .key,
                                ""
                        )
                                ?: ""
                )
        }
        var steeringWheelButton1PackageLong by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_LONG
                                        .key,
                                ""
                        )
                                ?: ""
                )
        }
        var steeringWheelButton2PackageDouble by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_DOUBLE
                                        .key,
                                ""
                        )
                                ?: ""
                )
        }
        var steeringWheelButton2PackageLong by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_LONG
                                        .key,
                                ""
                        )
                                ?: ""
                )
        }
        DisposableEffect(prefs) {
                val listener =
                        SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
                                when (key) {
                                        SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS
                                                .key ->
                                                enableCustomSteeringWheelButtons =
                                                        sharedPrefs.getBoolean(
                                                                SharedPreferencesKeys
                                                                        .ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS
                                                                        .key,
                                                                false
                                                        )
                                        SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION
                                                .key ->
                                                steeringWheelButton1Action =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_CUSTOM_BUTON_1_ACTION
                                                                        .key,
                                                                SteeringWheelCustomActionType.DEFAULT
                                                                        .key
                                                        )
                                                                ?: SteeringWheelCustomActionType
                                                                        .DEFAULT
                                                                        .key
                                        SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION
                                                .key ->
                                                steeringWheelButton2Action =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_CUSTOM_BUTON_2_ACTION
                                                                        .key,
                                                                SteeringWheelCustomActionType.DEFAULT
                                                                        .key
                                                        )
                                                                ?: SteeringWheelCustomActionType
                                                                        .DEFAULT
                                                                        .key
                                        SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1
                                                .key ->
                                                steeringWheelButton1Package =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1
                                                                        .key,
                                                                ""
                                                        )
                                                                ?: ""
                                        SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2
                                                .key ->
                                                steeringWheelButton2Package =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2
                                                                        .key,
                                                                ""
                                                        )
                                                                ?: ""
                                        SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_DOUBLE
                                                .key ->
                                                steeringWheelButton1ActionDouble =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_DOUBLE
                                                                        .key,
                                                                SteeringWheelCustomActionType.DEFAULT
                                                                        .key
                                                        )
                                                                ?: SteeringWheelCustomActionType
                                                                        .DEFAULT
                                                                        .key
                                        SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_LONG
                                                .key ->
                                                steeringWheelButton1ActionLong =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_LONG
                                                                        .key,
                                                                SteeringWheelCustomActionType.DEFAULT
                                                                        .key
                                                        )
                                                                ?: SteeringWheelCustomActionType
                                                                        .DEFAULT
                                                                        .key
                                        SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_DOUBLE
                                                .key ->
                                                steeringWheelButton2ActionDouble =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_DOUBLE
                                                                        .key,
                                                                SteeringWheelCustomActionType.DEFAULT
                                                                        .key
                                                        )
                                                                ?: SteeringWheelCustomActionType
                                                                        .DEFAULT
                                                                        .key
                                        SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_LONG
                                                .key ->
                                                steeringWheelButton2ActionLong =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_LONG
                                                                        .key,
                                                                SteeringWheelCustomActionType.DEFAULT
                                                                        .key
                                                        )
                                                                ?: SteeringWheelCustomActionType
                                                                        .DEFAULT
                                                                        .key
                                        SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_DOUBLE
                                                .key ->
                                                steeringWheelButton1PackageDouble =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_DOUBLE
                                                                        .key,
                                                                ""
                                                        )
                                                                ?: ""
                                        SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_LONG
                                                .key ->
                                                steeringWheelButton1PackageLong =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_LONG
                                                                        .key,
                                                                ""
                                                        )
                                                                ?: ""
                                        SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_DOUBLE
                                                .key ->
                                                steeringWheelButton2PackageDouble =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_DOUBLE
                                                                        .key,
                                                                ""
                                                        )
                                                                ?: ""
                                        SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_LONG
                                                .key ->
                                                steeringWheelButton2PackageLong =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_LONG
                                                                        .key,
                                                                ""
                                                        )
                                                                ?: ""
                                        SharedPreferencesKeys.STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_1
                                                .key ->
                                                steeringWheelButton1ClimateCommand =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_1
                                                                        .key,
                                                                SteeringWheelClimateCommandType
                                                                        .TOGGLE_AC
                                                                        .key
                                                        )
                                                                ?: SteeringWheelClimateCommandType
                                                                        .TOGGLE_AC
                                                                        .key
                                        SharedPreferencesKeys.STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_2
                                                .key ->
                                                steeringWheelButton2ClimateCommand =
                                                        sharedPrefs.getString(
                                                                SharedPreferencesKeys
                                                                        .STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_2
                                                                        .key,
                                                                SteeringWheelClimateCommandType
                                                                        .TOGGLE_AC
                                                                        .key
                                                        )
                                                                ?: SteeringWheelClimateCommandType
                                                                        .TOGGLE_AC
                                                                        .key
                                }
                        }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        var enablePersistentBottomBar by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, false)
                )
        }
        var autoHideEnabled by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.BOTTOM_BAR_AUTO_HIDE.key, false)
                )
        }
        var hideLeftNavPane by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.HIDE_LEFT_NAV_PANE.key, false)
                )
        }
        var swipeUpAction by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.BOTTOM_BAR_SWIPE_UP_ACTION.key,
                                null
                        )
                                ?: BottomBarState.SwipeUpAction.DASHBOARD.key
                )
        }
        var swipeUpPackage by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.BOTTOM_BAR_SWIPE_UP_PACKAGE.key,
                                null
                        )
                                ?: ""
                )
        }
        var showSwipeUpAppPicker by remember { mutableStateOf(false) }
        var clusterProjectionOpensDashboard by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.CLUSTER_PROJECTION_OPENS_DASHBOARD.key,
                                true
                        )
                )
        }
        var autoMoveProjectionToCluster by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.AUTO_MOVE_PROJECTION_TO_CLUSTER.key,
                                true
                        )
                )
        }
        var showStartPicker by remember { mutableStateOf(false) }
        var showEndPicker by remember { mutableStateOf(false) }
        var enableSpeedAdjustment by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.ENABLE_SPEED_ADJUSTMENT.key, false)
                )
        }
        var speedAdjustmentOffset by remember {
                mutableFloatStateOf(
                        prefs.getFloat(SharedPreferencesKeys.SPEED_ADJUSTMENT_OFFSET.key, 0f)
                )
        }

        var enableOpenSunroofCurtainOnStart by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.ENABLE_OPEN_SUNROOF_CURTAIN_ON_START.key,
                                false
                        )
                )
        }
        var curtainStartHour by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_START_HOUR.key, 18)
                )
        }
        var curtainStartMinute by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_START_MINUTE.key, 0)
                )
        }
        var curtainEndHour by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_END_HOUR.key, 9)
                )
        }
        var curtainEndMinute by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_END_MINUTE.key, 0)
                )
        }
        var openSunroofCurtainMaxTemp by remember {
                mutableFloatStateOf(
                        prefs.getFloat(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_MAX_TEMP.key, -1f)
                )
        }
        var enableCloseSunroofCurtainOnTime by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.ENABLE_CLOSE_SUNROOF_CURTAIN_ON_TIME.key,
                                false
                        )
                )
        }
        var closeCurtainStartHour by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.CLOSE_SUNROOF_CURTAIN_START_HOUR.key, 9)
                )
        }
        var closeCurtainStartMinute by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.CLOSE_SUNROOF_CURTAIN_START_MINUTE.key, 0)
                )
        }
        var closeCurtainEndHour by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.CLOSE_SUNROOF_CURTAIN_END_HOUR.key, 17)
                )
        }
        var closeCurtainEndMinute by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.CLOSE_SUNROOF_CURTAIN_END_MINUTE.key, 0)
                )
        }
        var enablePersistHevSoc by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.ENABLE_PERSIST_HEV_SOC_TARGET.key, false)
                )
        }
        var hevSocTarget by remember {
                mutableIntStateOf(
                        prefs.getInt(SharedPreferencesKeys.HEV_SOC_TARGET_VALUE.key, 50)
                )
        }
        var enableHotRouter by remember {
                mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_HOT_ROUTER.key, false))
        }
        // ===== Controle de dados móveis do carro (master + regras) =====
        val mdm = br.com.redesurftank.havalshisuku.managers.MobileDataManager
        var mobileControlEnabled by remember { mutableStateOf(mdm.isControlEnabled()) }
        var mobileManualBlock by remember { mutableStateOf(mdm.isManualBlock()) }
        var mobileAutoblock by remember { mutableStateOf(mdm.isAutoblockEnabled()) }
        var mobileBlockOnWifi by remember { mutableStateOf(mdm.isBlockOnWifi()) }
        var mobileBlockOnProjection by remember { mutableStateOf(mdm.isBlockOnProjection()) }
        var mobileDataCycleDay by remember {
                mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.MOBILE_DATA_CYCLE_DAY.key, 1).coerceIn(1, 31))
        }
        var mobileDataAutoblockCapMb by remember {
                mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.MOBILE_DATA_AUTOBLOCK_CAP_MB.key, 2048).coerceIn(512, 8192))
        }
        var blockDatatrack by remember { mutableStateOf(mdm.isDatatrackBlocked()) }
        var enableAaClusterOffset by remember {
                mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_AA_CLUSTER_OFFSET.key, false))
        }
        var aaClusterOffset by remember {
                mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.AA_CLUSTER_LEFT_OFFSET.key, 145))
        }
        var mobileDataUsedMb by remember { mutableStateOf(0L) }
        var mobileBlockReason by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(mobileControlEnabled, mobileDataCycleDay) {
                while (true) {
                        val ctx = br.com.redesurftank.App.getContext()
                        mobileDataUsedMb = withContext(Dispatchers.IO) {
                                mdm.getMobileUsedMbThisCycle(ctx, System.currentTimeMillis())
                        }
                        mobileBlockReason = withContext(Dispatchers.IO) { mdm.blockReason(ctx) }
                        delay(10000)
                }
        }

        val settingsList = mutableListOf<SettingItem>()

        // HotRouter: roteia o hotspot pela WLAN externa (Starlink) com fallback pro 4G.
        settingsList.add(
                SettingItem(
                        title = "HotRouter",
                        group = SettingsGroups.FEATURES,
                        description = SharedPreferencesKeys.ENABLE_HOT_ROUTER.description,
                        checked = enableHotRouter,
                        onCheckedChange = {
                                enableHotRouter = it
                                prefs.edit {
                                        putBoolean(SharedPreferencesKeys.ENABLE_HOT_ROUTER.key, it)
                                }
                                HotRouterManager.getInstance().setEnabled(it)
                        },
                        customContent =
                                if (enableHotRouter) {
                                        {
                                                var statusMode by remember { mutableStateOf(HotRouterManager.MODE_STARTING) }
                                                var statusEpoch by remember { mutableStateOf(0L) }
                                                // Hotspot do carro REALMENTE no ar: o daemon reporta WLAN/4G mesmo com o
                                                // hotspot desligado; sem isto o status mostrava "Ativo" à toa.
                                                var hotspotOnAir by remember { mutableStateOf(true) }
                                                LaunchedEffect(Unit) {
                                                        while (true) {
                                                                val s = withContext(Dispatchers.IO) {
                                                                        HotRouterManager.getInstance().readStatusBlocking()
                                                                }
                                                                statusMode = s.mode
                                                                statusEpoch = s.epochSeconds
                                                                hotspotOnAir = withContext(Dispatchers.IO) {
                                                                        try { ServiceManager.getInstance().isHotspotOnAir() } catch (t: Throwable) { true }
                                                                }
                                                                delay(3000)
                                                        }
                                                }
                                                val label = when {
                                                        statusMode == HotRouterManager.MODE_OFF -> "Desligado"
                                                        statusMode == HotRouterManager.MODE_STARTING -> "Iniciando…"
                                                        statusMode == HotRouterManager.MODE_ERROR -> "Erro"
                                                        // Daemon rodando (WLAN/4G) mas hotspot fora do ar: não há o que rotear.
                                                        !hotspotOnAir -> "Ligado (sem hotspot)"
                                                        statusMode == HotRouterManager.MODE_WLAN -> "Ativo (WLAN)"
                                                        statusMode == HotRouterManager.MODE_4G -> "Ativo (4G)"
                                                        else -> "—"
                                                }
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        HorizontalDivider(color = Color(0xFF3A3F47), thickness = 1.dp)
                                                        Text(text = "Status: $label", color = Color.White, fontSize = 16.sp)
                                                        if (statusEpoch > 0L) {
                                                                Text(text = "atualizado ${formatHms(statusEpoch)}", color = Color(0xFFB0B8C4), fontSize = 12.sp)
                                                        }
                                                }
                                        }
                                } else null
                )
        )

        // Controle de dados móveis do carro (master + 4 regras). Roteia por WiFi/Starlink e corta o 4G.
        settingsList.add(
                SettingItem(
                        title = "Controle de dados móveis",
                        group = SettingsGroups.FEATURES,
                        description =
                                "Liga o gerenciamento do 4G da multimídia. Desligado, o app não altera o estado definido pelo carro; se ele próprio havia bloqueado, libera uma vez.",
                        checked = mobileControlEnabled,
                        onCheckedChange = {
                                mobileControlEnabled = it
                                mdm.setControlEnabled(it)
                        },
                        customContent = {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                        val reason = mobileBlockReason
                                        Text(
                                                when {
                                                        reason != null -> "4G agora: BLOQUEADO ($reason)"
                                                        !mobileControlEnabled -> "4G agora: NÃO GERENCIADO PELO APP"
                                                        else -> "4G agora: LIBERADO"
                                                },
                                                color = if (reason != null) Color(0xFFE53935) else Color(0xFF34C759),
                                                fontSize = 15.sp
                                        )
                                        Text(
                                                String.format("Multimídia (esta tela): %.2f GB neste ciclo", mobileDataUsedMb / 1024f),
                                                color = AppColors.TextSecondary,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                        )
                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text("Bloquear manualmente agora", color = AppColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                                Switch(checked = mobileManualBlock, onCheckedChange = { mobileManualBlock = it; mdm.setManualBlock(it) })
                                        }
                                        Text("Corta o 4G na hora, independente do resto.", color = AppColors.TextSecondary, fontSize = 12.sp)
                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text("Bloquear conforme o gasto", color = AppColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                                Switch(checked = mobileAutoblock, onCheckedChange = { mobileAutoblock = it; mdm.setAutoblockEnabled(it) })
                                        }
                                        if (mobileAutoblock) {
                                                val usedGb = mobileDataUsedMb / 1024f
                                                val capGb = mobileDataAutoblockCapMb / 1024f
                                                val remainingGb = (capGb - usedGb).coerceAtLeast(0f)
                                                Text(
                                                        String.format("Bloquear ao passar de %.1f GB — faltam %.2f GB", capGb, remainingGb),
                                                        color = AppColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                                                )
                                                Slider(
                                                        value = (mobileDataAutoblockCapMb / 1024f).coerceIn(0.5f, 8f),
                                                        onValueChange = {
                                                                val gb = Math.round(it * 2f) / 2f
                                                                mobileDataAutoblockCapMb = (gb * 1024).toInt().coerceIn(512, 8192)
                                                        },
                                                        onValueChangeFinished = {
                                                                prefs.edit { putInt(SharedPreferencesKeys.MOBILE_DATA_AUTOBLOCK_CAP_MB.key, mobileDataAutoblockCapMb) }
                                                        },
                                                        valueRange = 0.5f..8f,
                                                        steps = 14
                                                )
                                                Text("Zera a contagem no dia $mobileDataCycleDay do mês", color = AppColors.TextSecondary, fontSize = 12.sp)
                                                Slider(
                                                        value = mobileDataCycleDay.toFloat(),
                                                        onValueChange = { mobileDataCycleDay = it.toInt().coerceIn(1, 31) },
                                                        onValueChangeFinished = {
                                                                prefs.edit { putInt(SharedPreferencesKeys.MOBILE_DATA_CYCLE_DAY.key, mobileDataCycleDay) }
                                                        },
                                                        valueRange = 1f..31f,
                                                        steps = 29
                                                )
                                        } else {
                                                Text("Bloqueia sozinho só quando a tela atinge o teto (GB) no ciclo.", color = AppColors.TextSecondary, fontSize = 12.sp)
                                        }
                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text("Bloquear quando conectado ao WiFi", color = AppColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                                Switch(checked = mobileBlockOnWifi, onCheckedChange = { mobileBlockOnWifi = it; mdm.setBlockOnWifi(it) })
                                        }
                                        Text("Com WiFi/Starlink no ar, desliga o 4G; volta quando o WiFi cai.", color = AppColors.TextSecondary, fontSize = 12.sp)
                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text("Bloquear no Android Auto/CarPlay", color = AppColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                                Switch(checked = mobileBlockOnProjection, onCheckedChange = { mobileBlockOnProjection = it; mdm.setBlockOnProjection(it) })
                                        }
                                        Text("Enquanto o celular estiver projetando, o 4G do carro fica desligado.", color = AppColors.TextSecondary, fontSize = 12.sp)
                                        Text(
                                                "A fatura da linha é maior: inclui o TBOX (não passa pela tela e não é medido aqui).",
                                                color = AppColors.TextSecondary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 12.dp)
                                        )
                                }
                        }
                )
        )

        settingsList.add(
                SettingItem(
                        title = "Manter % de bateria no HEV Prioritário",
                        group = SettingsGroups.DRIVE,
                        description = "Se o carro alterar sozinho o % a salvar, o app reaplica o valor que você escolheu. Só vale em HEV Prioritário.",
                        checked = enablePersistHevSoc,
                        onCheckedChange = {
                                enablePersistHevSoc = it
                                prefs.edit {
                                        putBoolean(SharedPreferencesKeys.ENABLE_PERSIST_HEV_SOC_TARGET.key, it)
                                }
                                if (it) ServiceManager.getInstance().applyHevSocTargetIfActive("UI_TOGGLE")
                        },
                        customContent = {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                        Text(
                                                "Salvar bateria em: $hevSocTarget%",
                                                color = AppColors.TextPrimary,
                                                fontSize = 14.sp
                                        )
                                        Slider(
                                                value = hevSocTarget.toFloat(),
                                                onValueChange = { hevSocTarget = it.toInt() },
                                                onValueChangeFinished = {
                                                        prefs.edit {
                                                                putInt(SharedPreferencesKeys.HEV_SOC_TARGET_VALUE.key, hevSocTarget)
                                                        }
                                                        ServiceManager.getInstance().applyHevSocTargetIfActive("UI_SLIDER")
                                                },
                                                valueRange = 20f..80f,
                                                steps = 11,
                                                colors = SliderDefaults.colors(
                                                        thumbColor = AppColors.Primary,
                                                        activeTrackColor = AppColors.Primary,
                                                        inactiveTrackColor = Color(0xFF2C3139)
                                                )
                                        )
                                }
                        }
                )
        )

        if (isAdvancedUse && !selfInstallationCheck) {
                settingsList.add(
                        SettingItem(
                                title = "Ignorar verificação de integridade",
                                group = SettingsGroups.FEATURES,
                                description =
                                        SharedPreferencesKeys
                                                .BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK
                                                .description,
                                checked = bypassSelfInstallationCheck,
                                onCheckedChange = {
                                        bypassSelfInstallationCheck = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        )
                )
        }

        settingsList.addAll(
                listOfNotNull(
                        SettingItem(
                                title = "Fechar janela ao desligar o veículo",
                                group = SettingsGroups.SHUTDOWN,
                                description =
                                        "Fecha automaticamente as janelas quando o motor é desligado",
                                checked = closeWindowOnPowerOff,
                                onCheckedChange = {
                                        closeWindowOnPowerOff = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .CLOSE_WINDOW_ON_POWER_OFF
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Fechar teto solar ao desligar",
                                group = SettingsGroups.SHUTDOWN,
                                description =
                                        SharedPreferencesKeys.CLOSE_SUNROOF_ON_POWER_OFF
                                                .description,
                                checked = closeSunroofOnPowerOff,
                                onCheckedChange = {
                                        closeSunroofOnPowerOff = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .CLOSE_SUNROOF_ON_POWER_OFF
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Fechar janela ao trancar o carro",
                                group = SettingsGroups.SHUTDOWN,
                                description =
                                        "Fecha os vidros quando o carro é trancado. Só age com o carro PARADO e DESLIGADO — o carro tranca sozinho ao atingir velocidade, e trancar com alguém dentro não pode fechar vidro na cara de ninguém.",
                                checked = closeWindowOnLock,
                                onCheckedChange = {
                                        closeWindowOnLock = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys.CLOSE_WINDOW_ON_LOCK
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Fechar teto solar ao trancar o carro",
                                group = SettingsGroups.SHUTDOWN,
                                description =
                                        "Fecha o teto quando o carro é trancado, com as mesmas condições de segurança: parado e desligado.",
                                checked = closeSunroofOnLock,
                                onCheckedChange = {
                                        closeSunroofOnLock = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys.CLOSE_SUNROOF_ON_LOCK
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Fechar cortina do teto solar",
                                group = SettingsGroups.SHUTDOWN,
                                description =
                                        SharedPreferencesKeys
                                                .CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF
                                                .description,
                                checked = closeSunroofSunShadeOnCloseSunroof,
                                onCheckedChange = {
                                        closeSunroofSunShadeOnCloseSunroof = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Fechar janelas com velocidade",
                                group = SettingsGroups.SPEED,
                                description =
                                        SharedPreferencesKeys.CLOSE_WINDOWS_ON_SPEED.description,
                                checked = closeWindowsOnSpeed,
                                onCheckedChange = {
                                        closeWindowsOnSpeed = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys.CLOSE_WINDOWS_ON_SPEED
                                                                .key,
                                                        it
                                                )
                                        }
                                },
                                sliderValue = speedThreshold.toInt(),
                                sliderRange = 10..120,
                                sliderStep = 1,
                                onSliderChange = { newSpeed ->
                                        speedThreshold = newSpeed.toFloat()
                                        prefs.edit {
                                                putFloat(
                                                        SharedPreferencesKeys.SPEED_THRESHOLD.key,
                                                        newSpeed.toFloat()
                                                )
                                        }
                                },
                                sliderLabel = "Velocidade: $speedThreshold km/h"
                        ),
                        SettingItem(
                                title = "Fechar teto solar com velocidade",
                                group = SettingsGroups.SPEED,
                                description =
                                        SharedPreferencesKeys.CLOSE_SUNROOF_ON_SPEED.description,
                                checked = closeSunroofOnSpeed,
                                onCheckedChange = {
                                        closeSunroofOnSpeed = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys.CLOSE_SUNROOF_ON_SPEED
                                                                .key,
                                                        it
                                                )
                                        }
                                },
                                sliderValue = closeSunroofSpeedThreshold.toInt(),
                                sliderRange = 10..120,
                                sliderStep = 1,
                                onSliderChange = { newSpeed ->
                                        closeSunroofSpeedThreshold = newSpeed.toFloat()
                                        prefs.edit {
                                                putFloat(
                                                        SharedPreferencesKeys
                                                                .SUNROOF_SPEED_THRESHOLD
                                                                .key,
                                                        newSpeed.toFloat()
                                                )
                                        }
                                },
                                sliderLabel =
                                        "Velocidade: ${closeSunroofSpeedThreshold.toInt()} km/h"
                        ),
                        SettingItem(
                                title = "A/C no máximo ao ligar o carro",
                                group = SettingsGroups.CLIMATE,
                                description =
                                        SharedPreferencesKeys.ENABLE_MAX_AC_ON_UNLOCK.description,
                                checked = enableMaxAcOnUnlock,
                                onCheckedChange = {
                                        enableMaxAcOnUnlock = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .ENABLE_MAX_AC_ON_UNLOCK
                                                                .key,
                                                        it
                                                )
                                        }
                                },
                                sliderValue = maxAcOnUnlockThreshold.toInt(),
                                sliderRange = 20..38,
                                sliderStep = 1,
                                onSliderChange = { newTemp ->
                                        maxAcOnUnlockThreshold = newTemp.toFloat()
                                        prefs.edit {
                                                putFloat(
                                                        SharedPreferencesKeys
                                                                .MAX_AC_ON_UNLOCK_THRESHOLD
                                                                .key,
                                                        newTemp.toFloat()
                                                )
                                        }
                                },
                                sliderLabel =
                                        "Temperatura de disparo: ${maxAcOnUnlockThreshold.toInt()}°C",
                                customContent =
                                        if (enableMaxAcOnUnlock) {
                                                {
                                                        val timeOptions =
                                                                mapOf(
                                                                        0 to "Sem limite",
                                                                        1 to "1 minuto",
                                                                        3 to "3 minutos",
                                                                        5 to "5 minutos"
                                                                )
                                                        var expanded by remember {
                                                                mutableStateOf(false)
                                                        }

                                                        Column(
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(12.dp)
                                                        ) {
                                                                Column {
                                                                        Text(
                                                                                text =
                                                                                        "Temperatura alvo: ${maxAcTargetTemp.toInt()}°C",
                                                                                fontSize = 14.sp,
                                                                                color = Color.White
                                                                        )
                                                                        Slider(
                                                                                value =
                                                                                        maxAcTargetTemp,
                                                                                onValueChange = {
                                                                                        newTemp ->
                                                                                        maxAcTargetTemp =
                                                                                                newTemp
                                                                                        prefs.edit {
                                                                                                putFloat(
                                                                                                        SharedPreferencesKeys
                                                                                                                .MAX_AC_TARGET_TEMP
                                                                                                                .key,
                                                                                                        newTemp
                                                                                                )
                                                                                        }
                                                                                },
                                                                                valueRange =
                                                                                        18f..34f,
                                                                                steps = 15,
                                                                                colors =
                                                                                        SliderDefaults
                                                                                                .colors(
                                                                                                        thumbColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        activeTrackColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        inactiveTrackColor =
                                                                                                                Color(
                                                                                                                        0xFF2C3139
                                                                                                                ),
                                                                                                        activeTickColor =
                                                                                                                Color.Transparent,
                                                                                                        inactiveTickColor =
                                                                                                                Color.Transparent
                                                                                                )
                                                                        )
                                                                }
                                                                Column {
                                                                        Text(
                                                                                text =
                                                                                        SharedPreferencesKeys
                                                                                                .MAX_AC_TIMEOUT
                                                                                                .description,
                                                                                fontSize = 14.sp,
                                                                                color = Color.White
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        Box {
                                                                                Text(
                                                                                        text =
                                                                                                timeOptions[
                                                                                                        maxAcTimeout]
                                                                                                        ?: "Sem limite",
                                                                                        color =
                                                                                                Color(
                                                                                                        0xFF4A9EFF
                                                                                                ),
                                                                                        fontSize =
                                                                                                16.sp,
                                                                                        modifier =
                                                                                                Modifier.background(
                                                                                                                Color(
                                                                                                                        0xFF2A2F37
                                                                                                                ),
                                                                                                                RoundedCornerShape(
                                                                                                                        4.dp
                                                                                                                )
                                                                                                        )
                                                                                                        .padding(
                                                                                                                horizontal =
                                                                                                                        12.dp,
                                                                                                                vertical =
                                                                                                                        8.dp
                                                                                                        )
                                                                                                        .clickable {
                                                                                                                expanded =
                                                                                                                        true
                                                                                                        }
                                                                                )
                                                                                DropdownMenu(
                                                                                        expanded =
                                                                                                expanded,
                                                                                        onDismissRequest = {
                                                                                                expanded =
                                                                                                        false
                                                                                        },
                                                                                        modifier =
                                                                                                Modifier.background(
                                                                                                        Color(
                                                                                                                0xFF2A2F37
                                                                                                        )
                                                                                                )
                                                                                ) {
                                                                                        timeOptions
                                                                                                .forEach {
                                                                                                        (
                                                                                                                value,
                                                                                                                label)
                                                                                                        ->
                                                                                                        DropdownMenuItem(
                                                                                                                text = {
                                                                                                                        Text(
                                                                                                                                label,
                                                                                                                                color =
                                                                                                                                        Color.White
                                                                                                                        )
                                                                                                                },
                                                                                                                onClick = {
                                                                                                                        maxAcTimeout =
                                                                                                                                value
                                                                                                                        prefs
                                                                                                                                .edit {
                                                                                                                                        putInt(
                                                                                                                                                SharedPreferencesKeys
                                                                                                                                                        .MAX_AC_TIMEOUT
                                                                                                                                                        .key,
                                                                                                                                                value
                                                                                                                                        )
                                                                                                                                }
                                                                                                                        expanded =
                                                                                                                                false
                                                                                                                }
                                                                                                        )
                                                                                                }
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                        } else null
                        ),
                        SettingItem(
                                title =
                                        SharedPreferencesKeys.ENABLE_OPEN_SUNROOF_CURTAIN_ON_START
                                                .description,
                                group = SettingsGroups.COMFORT,
                                description =
                                        "Abre automaticamente a cortina do teto solar ao ligar o veículo",
                                checked = enableOpenSunroofCurtainOnStart,
                                onCheckedChange = { checked ->
                                        enableOpenSunroofCurtainOnStart = checked
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .ENABLE_OPEN_SUNROOF_CURTAIN_ON_START
                                                                .key,
                                                        checked
                                                )
                                        }
                                },
                                customContent =
                                        if (enableOpenSunroofCurtainOnStart) {
                                                {
                                                        var showCurtainStartPicker by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var showCurtainEndPicker by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var expandedTemp by remember {
                                                                mutableStateOf(false)
                                                        }

                                                        val tempOptions =
                                                                mapOf(
                                                                        -1f to "Desabilitado",
                                                                        26f to "26°C",
                                                                        28f to "28°C",
                                                                        30f to "30°C",
                                                                        32f to "32°C",
                                                                        34f to "34°C",
                                                                        36f to "36°C"
                                                                )

                                                        if (showCurtainStartPicker) {
                                                                val timeSetListener =
                                                                        TimePickerDialog
                                                                                .OnTimeSetListener {
                                                                                        _,
                                                                                        hour,
                                                                                        minute ->
                                                                                        curtainStartHour =
                                                                                                hour
                                                                                        curtainStartMinute =
                                                                                                minute
                                                                                        prefs.edit {
                                                                                                putInt(
                                                                                                        SharedPreferencesKeys
                                                                                                                .OPEN_SUNROOF_CURTAIN_START_HOUR
                                                                                                                .key,
                                                                                                        hour
                                                                                                )
                                                                                                putInt(
                                                                                                        SharedPreferencesKeys
                                                                                                                .OPEN_SUNROOF_CURTAIN_START_MINUTE
                                                                                                                .key,
                                                                                                        minute
                                                                                                )
                                                                                        }
                                                                                        showCurtainStartPicker =
                                                                                                false
                                                                                }
                                                                TimePickerDialog(
                                                                                LocalContext
                                                                                        .current,
                                                                                timeSetListener,
                                                                                curtainStartHour,
                                                                                curtainStartMinute,
                                                                                true
                                                                        )
                                                                        .show()
                                                        }

                                                        if (showCurtainEndPicker) {
                                                                val timeSetListener =
                                                                        TimePickerDialog
                                                                                .OnTimeSetListener {
                                                                                        _,
                                                                                        hour,
                                                                                        minute ->
                                                                                        curtainEndHour =
                                                                                                hour
                                                                                        curtainEndMinute =
                                                                                                minute
                                                                                        prefs.edit {
                                                                                                putInt(
                                                                                                        SharedPreferencesKeys
                                                                                                                .OPEN_SUNROOF_CURTAIN_END_HOUR
                                                                                                                .key,
                                                                                                        hour
                                                                                                )
                                                                                                putInt(
                                                                                                        SharedPreferencesKeys
                                                                                                                .OPEN_SUNROOF_CURTAIN_END_MINUTE
                                                                                                                .key,
                                                                                                        minute
                                                                                                )
                                                                                        }
                                                                                        showCurtainEndPicker =
                                                                                                false
                                                                                }
                                                                TimePickerDialog(
                                                                                LocalContext
                                                                                        .current,
                                                                                timeSetListener,
                                                                                curtainEndHour,
                                                                                curtainEndMinute,
                                                                                true
                                                                        )
                                                                        .show()
                                                        }

                                                        Column(
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(12.dp)
                                                        ) {
                                                                HorizontalDivider(
                                                                        color = Color(0xFF3A3F47),
                                                                        thickness = 1.dp
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        12.dp
                                                                                )
                                                                )

                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceEvenly
                                                                ) {
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .clickable {
                                                                                                        showCurtainStartPicker =
                                                                                                                true
                                                                                                }
                                                                                                .background(
                                                                                                        Color(
                                                                                                                0xFF2A2F37
                                                                                                        ),
                                                                                                        RoundedCornerShape(
                                                                                                                8.dp
                                                                                                        )
                                                                                                )
                                                                                                .padding(
                                                                                                        16.dp
                                                                                                ),
                                                                                contentAlignment =
                                                                                        Alignment
                                                                                                .Center
                                                                        ) {
                                                                                Column(
                                                                                        horizontalAlignment =
                                                                                                Alignment
                                                                                                        .CenterHorizontally
                                                                                ) {
                                                                                        Text(
                                                                                                "Início",
                                                                                                color =
                                                                                                        Color.White,
                                                                                                fontSize =
                                                                                                        14.sp
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.height(
                                                                                                                4.dp
                                                                                                        )
                                                                                        )
                                                                                        Text(
                                                                                                "${String.format("%02d", curtainStartHour)}:${String.format("%02d", curtainStartMinute)}",
                                                                                                color =
                                                                                                        Color(
                                                                                                                0xFF4A9EFF
                                                                                                        ),
                                                                                                fontSize =
                                                                                                        18.sp,
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Medium
                                                                                        )
                                                                                }
                                                                        }
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                12.dp
                                                                                        )
                                                                        )
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .clickable {
                                                                                                        showCurtainEndPicker =
                                                                                                                true
                                                                                                }
                                                                                                .background(
                                                                                                        Color(
                                                                                                                0xFF2A2F37
                                                                                                        ),
                                                                                                        RoundedCornerShape(
                                                                                                                8.dp
                                                                                                        )
                                                                                                )
                                                                                                .padding(
                                                                                                        16.dp
                                                                                                ),
                                                                                contentAlignment =
                                                                                        Alignment
                                                                                                .Center
                                                                        ) {
                                                                                Column(
                                                                                        horizontalAlignment =
                                                                                                Alignment
                                                                                                        .CenterHorizontally
                                                                                ) {
                                                                                        Text(
                                                                                                "Fim",
                                                                                                color =
                                                                                                        Color.White,
                                                                                                fontSize =
                                                                                                        14.sp
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.height(
                                                                                                                4.dp
                                                                                                        )
                                                                                        )
                                                                                        Text(
                                                                                                "${String.format("%02d", curtainEndHour)}:${String.format("%02d", curtainEndMinute)}",
                                                                                                color =
                                                                                                        Color(
                                                                                                                0xFF4A9EFF
                                                                                                        ),
                                                                                                fontSize =
                                                                                                        18.sp,
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Medium
                                                                                        )
                                                                                }
                                                                        }
                                                                }

                                                                Row(
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Text(
                                                                                "Temp. Máxima:",
                                                                                color = Color.White,
                                                                                fontSize = 14.sp,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                end =
                                                                                                        8.dp
                                                                                        )
                                                                        )
                                                                        Box {
                                                                                Text(
                                                                                        text =
                                                                                                tempOptions[
                                                                                                        openSunroofCurtainMaxTemp]
                                                                                                        ?: "Desabilitado",
                                                                                        color =
                                                                                                Color(
                                                                                                        0xFF4A9EFF
                                                                                                ),
                                                                                        fontSize =
                                                                                                16.sp,
                                                                                        modifier =
                                                                                                Modifier.background(
                                                                                                                Color(
                                                                                                                        0xFF2A2F37
                                                                                                                ),
                                                                                                                RoundedCornerShape(
                                                                                                                        4.dp
                                                                                                                )
                                                                                                        )
                                                                                                        .padding(
                                                                                                                horizontal =
                                                                                                                        12.dp,
                                                                                                                vertical =
                                                                                                                        8.dp
                                                                                                        )
                                                                                                        .clickable {
                                                                                                                expandedTemp =
                                                                                                                        true
                                                                                                        }
                                                                                )
                                                                                DropdownMenu(
                                                                                        expanded =
                                                                                                expandedTemp,
                                                                                        onDismissRequest = {
                                                                                                expandedTemp =
                                                                                                        false
                                                                                        },
                                                                                        modifier =
                                                                                                Modifier.background(
                                                                                                        Color(
                                                                                                                0xFF2A2F37
                                                                                                        )
                                                                                                )
                                                                                ) {
                                                                                        tempOptions
                                                                                                .forEach {
                                                                                                        (
                                                                                                                value,
                                                                                                                label)
                                                                                                        ->
                                                                                                        DropdownMenuItem(
                                                                                                                text = {
                                                                                                                        Text(
                                                                                                                                label,
                                                                                                                                color =
                                                                                                                                        Color.White
                                                                                                                        )
                                                                                                                },
                                                                                                                onClick = {
                                                                                                                        openSunroofCurtainMaxTemp =
                                                                                                                                value
                                                                                                                        prefs
                                                                                                                                .edit {
                                                                                                                                        putFloat(
                                                                                                                                                SharedPreferencesKeys
                                                                                                                                                        .OPEN_SUNROOF_CURTAIN_MAX_TEMP
                                                                                                                                                        .key,
                                                                                                                                                value
                                                                                                                                        )
                                                                                                                                }
                                                                                                                        expandedTemp =
                                                                                                                                false
                                                                                                                }
                                                                                                        )
                                                                                                }
                                                                                }
                                                                 }
                                                         }
                                                 }
                                                 }
                                         } else null
                         ),

                        SettingItem(
                                title =
                                        SharedPreferencesKeys.ENABLE_CLOSE_SUNROOF_CURTAIN_ON_TIME
                                                .description,
                                group = SettingsGroups.COMFORT,
                                description =
                                        "Fecha automaticamente a cortina do teto solar por horário (ao ligar dentro da janela ou ao cruzá-la dirigindo)",
                                checked = enableCloseSunroofCurtainOnTime,
                                onCheckedChange = { checked ->
                                        enableCloseSunroofCurtainOnTime = checked
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .ENABLE_CLOSE_SUNROOF_CURTAIN_ON_TIME
                                                                .key,
                                                        checked
                                                )
                                        }
                                },
                                customContent =
                                        if (enableCloseSunroofCurtainOnTime) {
                                                {
                                                        var showCloseCurtainStartPicker by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var showCloseCurtainEndPicker by remember {
                                                                mutableStateOf(false)
                                                        }

                                                        if (showCloseCurtainStartPicker) {
                                                                val timeSetListener =
                                                                        TimePickerDialog.OnTimeSetListener {
                                                                                _,
                                                                                hour,
                                                                                minute ->
                                                                                closeCurtainStartHour = hour
                                                                                closeCurtainStartMinute = minute
                                                                                prefs.edit {
                                                                                        putInt(
                                                                                                SharedPreferencesKeys
                                                                                                        .CLOSE_SUNROOF_CURTAIN_START_HOUR
                                                                                                        .key,
                                                                                                hour
                                                                                        )
                                                                                        putInt(
                                                                                                SharedPreferencesKeys
                                                                                                        .CLOSE_SUNROOF_CURTAIN_START_MINUTE
                                                                                                        .key,
                                                                                                minute
                                                                                        )
                                                                                }
                                                                                showCloseCurtainStartPicker = false
                                                                        }
                                                                TimePickerDialog(
                                                                                LocalContext.current,
                                                                                timeSetListener,
                                                                                closeCurtainStartHour,
                                                                                closeCurtainStartMinute,
                                                                                true
                                                                        )
                                                                        .show()
                                                        }

                                                        if (showCloseCurtainEndPicker) {
                                                                val timeSetListener =
                                                                        TimePickerDialog.OnTimeSetListener {
                                                                                _,
                                                                                hour,
                                                                                minute ->
                                                                                closeCurtainEndHour = hour
                                                                                closeCurtainEndMinute = minute
                                                                                prefs.edit {
                                                                                        putInt(
                                                                                                SharedPreferencesKeys
                                                                                                        .CLOSE_SUNROOF_CURTAIN_END_HOUR
                                                                                                        .key,
                                                                                                hour
                                                                                        )
                                                                                        putInt(
                                                                                                SharedPreferencesKeys
                                                                                                        .CLOSE_SUNROOF_CURTAIN_END_MINUTE
                                                                                                        .key,
                                                                                                minute
                                                                                        )
                                                                                }
                                                                                showCloseCurtainEndPicker = false
                                                                        }
                                                                TimePickerDialog(
                                                                                LocalContext.current,
                                                                                timeSetListener,
                                                                                closeCurtainEndHour,
                                                                                closeCurtainEndMinute,
                                                                                true
                                                                        )
                                                                        .show()
                                                        }

                                                        Column(
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(12.dp)
                                                        ) {
                                                                HorizontalDivider(
                                                                        color = Color(0xFF3A3F47),
                                                                        thickness = 1.dp
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(12.dp)
                                                                )

                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement.SpaceEvenly
                                                                ) {
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.weight(1f)
                                                                                                .clickable {
                                                                                                        showCloseCurtainStartPicker =
                                                                                                                true
                                                                                                }
                                                                                                .background(
                                                                                                        Color(0xFF2A2F37),
                                                                                                        RoundedCornerShape(8.dp)
                                                                                                )
                                                                                                .padding(16.dp),
                                                                                contentAlignment =
                                                                                        Alignment.Center
                                                                        ) {
                                                                                Column(
                                                                                        horizontalAlignment =
                                                                                                Alignment
                                                                                                        .CenterHorizontally
                                                                                ) {
                                                                                        Text(
                                                                                                "Início",
                                                                                                color = Color.White,
                                                                                                fontSize = 14.sp
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.height(4.dp)
                                                                                        )
                                                                                        Text(
                                                                                                "${String.format("%02d", closeCurtainStartHour)}:${String.format("%02d", closeCurtainStartMinute)}",
                                                                                                color = Color(0xFF4A9EFF),
                                                                                                fontSize = 18.sp,
                                                                                                fontWeight =
                                                                                                        FontWeight.Medium
                                                                                        )
                                                                                }
                                                                        }
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(12.dp)
                                                                        )
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.weight(1f)
                                                                                                .clickable {
                                                                                                        showCloseCurtainEndPicker =
                                                                                                                true
                                                                                                }
                                                                                                .background(
                                                                                                        Color(0xFF2A2F37),
                                                                                                        RoundedCornerShape(8.dp)
                                                                                                )
                                                                                                .padding(16.dp),
                                                                                contentAlignment =
                                                                                        Alignment.Center
                                                                        ) {
                                                                                Column(
                                                                                        horizontalAlignment =
                                                                                                Alignment
                                                                                                        .CenterHorizontally
                                                                                ) {
                                                                                        Text(
                                                                                                "Fim",
                                                                                                color = Color.White,
                                                                                                fontSize = 14.sp
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.height(4.dp)
                                                                                        )
                                                                                        Text(
                                                                                                "${String.format("%02d", closeCurtainEndHour)}:${String.format("%02d", closeCurtainEndMinute)}",
                                                                                                color = Color(0xFF4A9EFF),
                                                                                                fontSize = 18.sp,
                                                                                                fontWeight =
                                                                                                        FontWeight.Medium
                                                                                        )
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                        } else null
                        ),

                        SettingItem(
                                title = "Manter desativado monitoramento de distrações",
                                group = SettingsGroups.SAFETY,
                                description = "Desabilita alertas de distração durante a condução",
                                checked = disableMonitoring,
                                onCheckedChange = {
                                        disableMonitoring = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys.DISABLE_MONITORING.key,
                                                        it
                                                )
                                        }
                                        ServiceManager.getInstance().setMonitoringEnabled(!it)
                                }
                        ),
                        SettingItem(
                                title = "Habilitar barra inferior de rápido acesso",
                                group = SettingsGroups.FEATURES,
                                description =
                                        "Cria uma barra inferior fixa com atalhos para ar condicionado e outras funções",
                                checked = enablePersistentBottomBar,
                                onCheckedChange = { checked ->
                                        if (checked && !Settings.canDrawOverlays(context)) {
                                                // Request overlay permission
                                                val intent =
                                                        Intent(
                                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                                Uri.parse(
                                                                        "package:${context.packageName}"
                                                                )
                                                        )
                                                context.startActivity(intent)
                                                android.widget.Toast.makeText(
                                                                context,
                                                                "Por favor, habilite a permissão de sobreposição para a barra inferior",
                                                                android.widget.Toast.LENGTH_LONG
                                                        )
                                                        .show()
                                                return@SettingItem
                                        }

                                        enablePersistentBottomBar = checked
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR
                                                                .key,
                                                        checked
                                                )
                                        }
                                        val serviceIntent =
                                                Intent(
                                                        context,
                                                        br.com.redesurftank.havalshisuku.services
                                                                        .BottomBarService::class
                                                                .java
                                                )
                                        if (checked) {
                                                context.startService(serviceIntent)
                                                Thread {
                                                                br.com.redesurftank.havalshisuku
                                                                        .utils.ShizukuUtils
                                                                        .runCommandAndGetOutput(
                                                                                arrayOf(
                                                                                        "sh",
                                                                                        "-c",
                                                                                        "wm size reset"
                                                                                )
                                                                        )
                                                                val overscan =
                                                                        prefs.getInt(
                                                                                SharedPreferencesKeys
                                                                                        .PERSISTENT_BOTTOM_BAR_OVERSCAN
                                                                                        .key,
                                                                                20
                                                                        )
                                                                br.com.redesurftank.havalshisuku
                                                                        .utils.ShizukuUtils
                                                                        .runCommandAndGetOutput(
                                                                                arrayOf(
                                                                                        "wm",
                                                                                        "overscan",
                                                                                        "0,0,0,$overscan"
                                                                                )
                                                                        )
                                                                // v2.3: reflow Impulse-managed Display 0 apps to
                                                                // honor the freshly-applied overscan.
                                                                br.com.redesurftank.havalshisuku
                                                                        .managers.DisplayAppLauncher
                                                                        .reapplyDisplay0BoundsForOverscanAsync()
                                                        }
                                                        .start()
                                        } else {
                                                context.stopService(serviceIntent)
                                                Thread {
                                                                br.com.redesurftank.havalshisuku
                                                                        .utils.ShizukuUtils
                                                                        .runCommandAndGetOutput(
                                                                                arrayOf(
                                                                                        "wm",
                                                                                        "overscan",
                                                                                        "0,0,0,0"
                                                                                )
                                                                        )
                                                                // v2.3: restore full Display 0 bounds for managed
                                                                // apps when the bar is disabled.
                                                                br.com.redesurftank.havalshisuku
                                                                        .managers.DisplayAppLauncher
                                                                        .reapplyDisplay0BoundsForOverscanAsync()
                                                        }
                                                        .start()
                                        }
                                },
                                customContent =
                                        if (enablePersistentBottomBar) {
                                                {
                                                        Column(
                                                                modifier =
                                                                        Modifier.padding(top = 8.dp)
                                                        ) {
                                                                HorizontalDivider(
                                                                        color = Color(0xFF1D2430),
                                                                        thickness = 1.dp
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        12.dp
                                                                                )
                                                                )

                                                                // Auto-hide row
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceBetween,
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Column(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        ) {
                                                                                Text(
                                                                                        "Auto-ocultar barra",
                                                                                        color =
                                                                                                Color.White,
                                                                                        fontSize =
                                                                                                16.sp
                                                                                )
                                                                                Text(
                                                                                        "Esconde após 30s de inatividade",
                                                                                        color =
                                                                                                Color.Gray,
                                                                                        fontSize =
                                                                                                12.sp
                                                                                )
                                                                        }
                                                                        Switch(
                                                                                checked =
                                                                                        autoHideEnabled,
                                                                                onCheckedChange = {
                                                                                        autoHideEnabled =
                                                                                                it
                                                                                        prefs.edit()
                                                                                                .putBoolean(
                                                                                                        SharedPreferencesKeys
                                                                                                                .BOTTOM_BAR_AUTO_HIDE
                                                                                                                .key,
                                                                                                        it
                                                                                                )
                                                                                                .apply()
                                                                                        BottomBarState
                                                                                                .autoHideEnabled =
                                                                                                it
                                                                                },
                                                                                modifier =
                                                                                        Modifier.scale(
                                                                                                0.9f
                                                                                        ),
                                                                                colors =
                                                                                        SwitchDefaults
                                                                                                .colors(
                                                                                                        checkedThumbColor =
                                                                                                                br.com
                                                                                                                        .redesurftank
                                                                                                                        .havalshisuku
                                                                                                                        .ui
                                                                                                                        .components
                                                                                                                        .AppColors
                                                                                                                        .TextPrimary,
                                                                                                        checkedTrackColor =
                                                                                                                br.com
                                                                                                                        .redesurftank
                                                                                                                        .havalshisuku
                                                                                                                        .ui
                                                                                                                        .components
                                                                                                                        .AppColors
                                                                                                                        .Primary,
                                                                                                        uncheckedThumbColor =
                                                                                                                br.com
                                                                                                                        .redesurftank
                                                                                                                        .havalshisuku
                                                                                                                        .ui
                                                                                                                        .components
                                                                                                                        .AppColors
                                                                                                                        .TextSecondary,
                                                                                                        uncheckedTrackColor =
                                                                                                                br.com
                                                                                                                        .redesurftank
                                                                                                                        .havalshisuku
                                                                                                                        .ui
                                                                                                                        .components
                                                                                                                        .AppColors
                                                                                                                        .ButtonSecondary,
                                                                                                        uncheckedBorderColor =
                                                                                                                Color.Transparent,
                                                                                                        checkedBorderColor =
                                                                                                                Color.Transparent
                                                                                                )
                                                                        )
                                                                }

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        12.dp
                                                                                )
                                                                )

                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceBetween,
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Column(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        ) {
                                                                                Text(
                                                                                        "Ocultar painel lateral",
                                                                                        color =
                                                                                                Color.White,
                                                                                        fontSize =
                                                                                                16.sp
                                                                                )
                                                                                Text(
                                                                                        "O painel fica oculto e libera os 128px da esquerda para a barra. Deslize da borda esquerda para trazê-lo de volta; ele se esconde de novo após 5s.\n\nAtenção: o botão home nativo fica nesse painel, então ele sai junto. Para usá-lo, deslize o painel de volta — ou configure o deslizar para cima da barra como \"Ir para a Home Haval\".",
                                                                                        color =
                                                                                                Color.Gray,
                                                                                        fontSize =
                                                                                                12.sp
                                                                                )
                                                                        }
                                                                        Switch(
                                                                                checked =
                                                                                        hideLeftNavPane,
                                                                                onCheckedChange = {
                                                                                        hideLeftNavPane =
                                                                                                it
                                                                                        prefs.edit()
                                                                                                .putBoolean(
                                                                                                        SharedPreferencesKeys
                                                                                                                .HIDE_LEFT_NAV_PANE
                                                                                                                .key,
                                                                                                        it
                                                                                                )
                                                                                                .apply()
                                                                                        BottomBarState
                                                                                                .leftNavPaneHidden =
                                                                                                it
                                                                                        applyLeftNavPaneVisibility(
                                                                                                it
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.scale(
                                                                                                0.9f
                                                                                        ),
                                                                                colors =
                                                                                        SwitchDefaults
                                                                                                .colors(
                                                                                                        checkedThumbColor =
                                                                                                                AppColors
                                                                                                                        .TextPrimary,
                                                                                                        checkedTrackColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        uncheckedThumbColor =
                                                                                                                AppColors
                                                                                                                        .TextSecondary,
                                                                                                        uncheckedTrackColor =
                                                                                                                AppColors
                                                                                                                        .ButtonSecondary,
                                                                                                        uncheckedBorderColor =
                                                                                                                Color.Transparent,
                                                                                                        checkedBorderColor =
                                                                                                                Color.Transparent
                                                                                                )
                                                                        )
                                                                }

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        12.dp
                                                                                )
                                                                )

                                                                // Swipe-up action
                                                                Text(
                                                                        "Ao deslizar a barra para cima",
                                                                        color = Color.White,
                                                                        fontSize = 16.sp
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        6.dp
                                                                                )
                                                                )
                                                                BottomBarState.SwipeUpAction.entries
                                                                        .forEach { option ->
                                                                                val selected =
                                                                                        swipeUpAction ==
                                                                                                option.key
                                                                                Row(
                                                                                        modifier =
                                                                                                Modifier.fillMaxWidth()
                                                                                                        .clickable {
                                                                                                                swipeUpAction =
                                                                                                                        option.key
                                                                                                                prefs.edit()
                                                                                                                        .putString(
                                                                                                                                SharedPreferencesKeys
                                                                                                                                        .BOTTOM_BAR_SWIPE_UP_ACTION
                                                                                                                                        .key,
                                                                                                                                option.key
                                                                                                                        )
                                                                                                                        .apply()
                                                                                                                BottomBarState
                                                                                                                        .swipeUpAction =
                                                                                                                        option.key
                                                                                                                if (option ==
                                                                                                                                BottomBarState
                                                                                                                                        .SwipeUpAction
                                                                                                                                        .CUSTOM_APP
                                                                                                                ) {
                                                                                                                        showSwipeUpAppPicker =
                                                                                                                                true
                                                                                                                }
                                                                                                        }
                                                                                                        .padding(
                                                                                                                vertical =
                                                                                                                        6.dp
                                                                                                        ),
                                                                                        verticalAlignment =
                                                                                                Alignment
                                                                                                        .CenterVertically
                                                                                ) {
                                                                                        RadioButton(
                                                                                                selected =
                                                                                                        selected,
                                                                                                onClick =
                                                                                                        null,
                                                                                                colors =
                                                                                                        RadioButtonDefaults
                                                                                                                .colors(
                                                                                                                        selectedColor =
                                                                                                                                AppColors
                                                                                                                                        .Primary,
                                                                                                                        unselectedColor =
                                                                                                                                AppColors
                                                                                                                                        .TextSecondary
                                                                                                                )
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.width(
                                                                                                                8.dp
                                                                                                        )
                                                                                        )
                                                                                        Column {
                                                                                                Text(
                                                                                                        option.label,
                                                                                                        color =
                                                                                                                Color.White,
                                                                                                        fontSize =
                                                                                                                14.sp
                                                                                                )
                                                                                                if (option ==
                                                                                                                BottomBarState
                                                                                                                        .SwipeUpAction
                                                                                                                        .CUSTOM_APP &&
                                                                                                                selected
                                                                                                ) {
                                                                                                        Text(
                                                                                                                if (swipeUpPackage
                                                                                                                                .isBlank()
                                                                                                                )
                                                                                                                        "Toque para escolher um app"
                                                                                                                else
                                                                                                                        swipeUpPackage,
                                                                                                                color =
                                                                                                                        AppColors
                                                                                                                                .Primary,
                                                                                                                fontSize =
                                                                                                                        12.sp
                                                                                                        )
                                                                                                }
                                                                                        }
                                                                                }
                                                                        }

                                                                if (showSwipeUpAppPicker) {
                                                                        AppPickerDialog(
                                                                                onDismiss = {
                                                                                        showSwipeUpAppPicker =
                                                                                                false
                                                                                },
                                                                                onAppSelected = {
                                                                                        app ->
                                                                                        swipeUpPackage =
                                                                                                app.packageName
                                                                                        prefs.edit()
                                                                                                .putString(
                                                                                                        SharedPreferencesKeys
                                                                                                                .BOTTOM_BAR_SWIPE_UP_PACKAGE
                                                                                                                .key,
                                                                                                        app.packageName
                                                                                                )
                                                                                                .apply()
                                                                                        BottomBarState
                                                                                                .swipeUpPackage =
                                                                                                app.packageName
                                                                                        showSwipeUpAppPicker =
                                                                                                false
                                                                                }
                                                                        )
                                                                }
                                                        }
                                                }
                                        } else null
                        ),
                        SettingItem(
                                title = "Mover navegação para o cluster ao iniciar",
                                description =
                                        "Enviar Android Auto ou CarPlay automaticamente para o cluster ao iniciar (só se AA/CarPlay estiver selecionado como app inicial na tela Telas)",
                                checked = autoMoveProjectionToCluster,
                                onCheckedChange = { checked ->
                                        autoMoveProjectionToCluster = checked
                                        prefs.edit()
                                                .putBoolean(
                                                        SharedPreferencesKeys
                                                                .AUTO_MOVE_PROJECTION_TO_CLUSTER
                                                                .key,
                                                        checked
                                                )
                                                .apply()
                                        if (!checked) {
                                                prefs.edit()
                                                        .remove("desiredAndroidAutoDisplayId")
                                                        .remove("desiredCarPlayDisplayId")
                                                        .apply()
                                        }
                                },
                                alwaysShowCustomContent = true,
                                customContent = {
                                        Column(
                                                verticalArrangement =
                                                        Arrangement.spacedBy(12.dp)
                                        ) {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth(),
                                                        horizontalArrangement =
                                                                Arrangement
                                                                        .SpaceBetween,
                                                        verticalAlignment =
                                                                Alignment
                                                                        .CenterVertically
                                                ) {
                                                        Column(
                                                                modifier =
                                                                        Modifier.weight(
                                                                                1f
                                                                        )
                                                        ) {
                                                                Text(
                                                                        "Abrir Impulse Drive na projeção do cluster",
                                                                        color =
                                                                                Color.White,
                                                                        fontSize =
                                                                                16.sp
                                                                )
                                                                Text(
                                                                        "Ao iniciar Android Auto ou CarPlay no cluster",
                                                                        color =
                                                                                Color.Gray,
                                                                        fontSize =
                                                                                12.sp
                                                                )
                                                        }
                                                        Switch(
                                                                checked =
                                                                        clusterProjectionOpensDashboard,
                                                                onCheckedChange = { checked ->
                                                                        clusterProjectionOpensDashboard =
                                                                                checked
                                                                        prefs.edit()
                                                                                .putBoolean(
                                                                                        SharedPreferencesKeys
                                                                                                .CLUSTER_PROJECTION_OPENS_DASHBOARD
                                                                                                .key,
                                                                                        checked
                                                                                )
                                                                                .apply()
                                                                },
                                                                modifier =
                                                                        Modifier.scale(
                                                                                0.9f
                                                                        ),
                                                                colors =
                                                                        SwitchDefaults
                                                                                .colors(
                                                                                        checkedThumbColor =
                                                                                                AppColors
                                                                                                        .TextPrimary,
                                                                                        checkedTrackColor =
                                                                                                AppColors
                                                                                                        .Primary,
                                                                                        uncheckedThumbColor =
                                                                                                AppColors
                                                                                                        .TextSecondary,
                                                                                        uncheckedTrackColor =
                                                                                                AppColors
                                                                                                        .ButtonSecondary,
                                                                                        uncheckedBorderColor =
                                                                                                Color.Transparent,
                                                                                        checkedBorderColor =
                                                                                                Color.Transparent
                                                                                )
                                                        )
                                                }
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Desativar AVAS",
                                group = SettingsGroups.SAFETY,
                                description = "Sistema de alerta de veículo silencioso",
                                checked = disableAvas,
                                onCheckedChange = {
                                        disableAvas = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys.DISABLE_AVAS.key,
                                                        it
                                                )
                                        }
                                        ServiceManager.getInstance().setAvasEnabled(!it)
                                }
                        ),
                        SettingItem(
                                title = "Desativar câmera AVM quando parado",
                                group = SettingsGroups.SAFETY,
                                description =
                                        "Desliga câmera de visão 360° quando o veículo está parado",
                                checked = disableAvmCarStopped,
                                onCheckedChange = {
                                        disableAvmCarStopped = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .DISABLE_AVM_CAR_STOPPED
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Ligar ventilação do banco do motorista com A/C ligado",
                                group = SettingsGroups.CLIMATE,
                                description =
                                        SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON
                                                .description,
                                checked = enableSeatVentilationOnAcOn,
                                onCheckedChange = {
                                        enableSeatVentilationOnAcOn = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .ENABLE_SEAT_VENTILATION_ON_AC_ON
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Desligar bluetooth ao desligar",
                                group = SettingsGroups.SHUTDOWN,
                                description =
                                        SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF
                                                .description,
                                checked = disableBluetoothOnPowerOff,
                                onCheckedChange = {
                                        disableBluetoothOnPowerOff = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .DISABLE_BLUETOOTH_ON_POWER_OFF
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Desligar ponto de acesso ao desligar",
                                group = SettingsGroups.SHUTDOWN,
                                description =
                                        SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF
                                                .description,
                                checked = disableHotspotOnPowerOff,
                                onCheckedChange = {
                                        disableHotspotOnPowerOff = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .DISABLE_HOTSPOT_ON_POWER_OFF
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Desativar Bluetooth ao trancar o carro",
                                group = SettingsGroups.SHUTDOWN,
                                description =
                                        SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_LOCK
                                                .description,
                                checked = disableBluetoothOnLock,
                                onCheckedChange = {
                                        disableBluetoothOnLock = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .DISABLE_BLUETOOTH_ON_LOCK
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Desativar ponto de acesso ao trancar o carro",
                                group = SettingsGroups.SHUTDOWN,
                                description =
                                        SharedPreferencesKeys.DISABLE_HOTSPOT_ON_LOCK
                                                .description,
                                checked = disableHotspotOnLock,
                                onCheckedChange = {
                                        disableHotspotOnLock = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .DISABLE_HOTSPOT_ON_LOCK
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Ativar Ambient Light BLE",
                                group = SettingsGroups.FEATURES,
                                description =
                                        "Exibe o recurso opcional para LEDs externos instalados pelo usuario",
                                checked = ambientLightBleEnabled,
                                onCheckedChange = {
                                        ambientLightBleEnabled = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .AMBIENT_LIGHT_BLE_ENABLED
                                                                .key,
                                                        it
                                                )
                                        }
                                        if (it) {
                                                AmbientLightService.startIfEnabled(context)
                                        } else {
                                                AmbientLightService.stop(context)
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Habilitar botões personalizados no volante",
                                group = SettingsGroups.FEATURES,
                                description =
                                        SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS
                                                .description,
                                checked = enableCustomSteeringWheelButtons,
                                onCheckedChange = {
                                        enableCustomSteeringWheelButtons = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS
                                                                .key,
                                                        it
                                                )
                                        }
                                        ServiceManager.getInstance()
                                                .ensureSteeringWheelButtonIntegration()
                                },
                                customContent =
                                        if (enableCustomSteeringWheelButtons) {
                                                {
                                                        Column(
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(12.dp)
                                                        ) {
                                                                HorizontalDivider(
                                                                        color = Color(0xFF3A3F47),
                                                                        thickness = 1.dp
                                                                )
                                                                Text(
                                                                        "Botão 1",
                                                                        color = Color.White,
                                                                        fontSize = 16.sp
                                                                )
                                                                SteeringActionPicker(
                                                                        label = "Toque curto",
                                                                        actionKey = steeringWheelButton1Action,
                                                                        packageName = steeringWheelButton1Package,
                                                                        climateCommandKey = steeringWheelButton1ClimateCommand,
                                                                        onActionSelected = { key ->
                                                                                steeringWheelButton1Action = key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CUSTOM_BUTON_1_ACTION
                                                                                                        .key,
                                                                                                key
                                                                                        )
                                                                                }
                                                                                ServiceManager.getInstance()
                                                                                        .ensureSteeringWheelButtonIntegration()
                                                                        },
                                                                        onPackageChanged = { packageName ->
                                                                                steeringWheelButton1Package = packageName
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1
                                                                                                        .key,
                                                                                                packageName
                                                                                        )
                                                                                }
                                                                        },
                                                                        onClimateCommandSelected = { command ->
                                                                                steeringWheelButton1ClimateCommand = command.key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_1
                                                                                                        .key,
                                                                                                command.key
                                                                                        )
                                                                                }
                                                                        }
                                                                )
                                                                SteeringActionPicker(
                                                                        label = "Toque duplo",
                                                                        actionKey = steeringWheelButton1ActionDouble,
                                                                        packageName = steeringWheelButton1PackageDouble,
                                                                        climateCommandKey = steeringWheelButton1ClimateCommand,
                                                                        onActionSelected = { key ->
                                                                                steeringWheelButton1ActionDouble = key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_DOUBLE
                                                                                                        .key,
                                                                                                key
                                                                                        )
                                                                                }
                                                                                ServiceManager.getInstance()
                                                                                        .ensureSteeringWheelButtonIntegration()
                                                                        },
                                                                        onPackageChanged = { packageName ->
                                                                                steeringWheelButton1PackageDouble = packageName
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_DOUBLE
                                                                                                        .key,
                                                                                                packageName
                                                                                        )
                                                                                }
                                                                        },
                                                                        onClimateCommandSelected = { command ->
                                                                                steeringWheelButton1ClimateCommand = command.key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_1
                                                                                                        .key,
                                                                                                command.key
                                                                                        )
                                                                                }
                                                                        }
                                                                )
                                                                SteeringActionPicker(
                                                                        label = "Toque longo (abre a config do carro junto)",
                                                                        actionKey = steeringWheelButton1ActionLong,
                                                                        packageName = steeringWheelButton1PackageLong,
                                                                        climateCommandKey = steeringWheelButton1ClimateCommand,
                                                                        onActionSelected = { key ->
                                                                                steeringWheelButton1ActionLong = key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_LONG
                                                                                                        .key,
                                                                                                key
                                                                                        )
                                                                                }
                                                                                ServiceManager.getInstance()
                                                                                        .ensureSteeringWheelButtonIntegration()
                                                                        },
                                                                        onPackageChanged = { packageName ->
                                                                                steeringWheelButton1PackageLong = packageName
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_LONG
                                                                                                        .key,
                                                                                                packageName
                                                                                        )
                                                                                }
                                                                        },
                                                                        onClimateCommandSelected = { command ->
                                                                                steeringWheelButton1ClimateCommand = command.key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_1
                                                                                                        .key,
                                                                                                command.key
                                                                                        )
                                                                                }
                                                                        }
                                                                )

                                                                HorizontalDivider(
                                                                        color = Color(0xFF3A3F47),
                                                                        thickness = 1.dp
                                                                )
                                                                Text(
                                                                        "Botão 2",
                                                                        color = Color.White,
                                                                        fontSize = 16.sp
                                                                )
                                                                SteeringActionPicker(
                                                                        label = "Toque curto",
                                                                        actionKey = steeringWheelButton2Action,
                                                                        packageName = steeringWheelButton2Package,
                                                                        climateCommandKey = steeringWheelButton2ClimateCommand,
                                                                        onActionSelected = { key ->
                                                                                steeringWheelButton2Action = key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CUSTOM_BUTON_2_ACTION
                                                                                                        .key,
                                                                                                key
                                                                                        )
                                                                                }
                                                                                ServiceManager.getInstance()
                                                                                        .ensureSteeringWheelButtonIntegration()
                                                                        },
                                                                        onPackageChanged = { packageName ->
                                                                                steeringWheelButton2Package = packageName
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2
                                                                                                        .key,
                                                                                                packageName
                                                                                        )
                                                                                }
                                                                        },
                                                                        onClimateCommandSelected = { command ->
                                                                                steeringWheelButton2ClimateCommand = command.key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_2
                                                                                                        .key,
                                                                                                command.key
                                                                                        )
                                                                                }
                                                                        }
                                                                )
                                                                SteeringActionPicker(
                                                                        label = "Toque duplo",
                                                                        actionKey = steeringWheelButton2ActionDouble,
                                                                        packageName = steeringWheelButton2PackageDouble,
                                                                        climateCommandKey = steeringWheelButton2ClimateCommand,
                                                                        onActionSelected = { key ->
                                                                                steeringWheelButton2ActionDouble = key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_DOUBLE
                                                                                                        .key,
                                                                                                key
                                                                                        )
                                                                                }
                                                                                ServiceManager.getInstance()
                                                                                        .ensureSteeringWheelButtonIntegration()
                                                                        },
                                                                        onPackageChanged = { packageName ->
                                                                                steeringWheelButton2PackageDouble = packageName
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_DOUBLE
                                                                                                        .key,
                                                                                                packageName
                                                                                        )
                                                                                }
                                                                        },
                                                                        onClimateCommandSelected = { command ->
                                                                                steeringWheelButton2ClimateCommand = command.key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_2
                                                                                                        .key,
                                                                                                command.key
                                                                                        )
                                                                                }
                                                                        }
                                                                )
                                                                SteeringActionPicker(
                                                                        label = "Toque longo (abre a config do carro junto)",
                                                                        actionKey = steeringWheelButton2ActionLong,
                                                                        packageName = steeringWheelButton2PackageLong,
                                                                        climateCommandKey = steeringWheelButton2ClimateCommand,
                                                                        onActionSelected = { key ->
                                                                                steeringWheelButton2ActionLong = key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_LONG
                                                                                                        .key,
                                                                                                key
                                                                                        )
                                                                                }
                                                                                ServiceManager.getInstance()
                                                                                        .ensureSteeringWheelButtonIntegration()
                                                                        },
                                                                        onPackageChanged = { packageName ->
                                                                                steeringWheelButton2PackageLong = packageName
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_LONG
                                                                                                        .key,
                                                                                                packageName
                                                                                        )
                                                                                }
                                                                        },
                                                                        onClimateCommandSelected = { command ->
                                                                                steeringWheelButton2ClimateCommand = command.key
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                SharedPreferencesKeys
                                                                                                        .STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_2
                                                                                                        .key,
                                                                                                command.key
                                                                                        )
                                                                                }
                                                                        }
                                                                )
                                                        }
                                                }
                                        } else null
                        ),
                        SettingItem(
                                title = "Ajustar brilho automaticamente",
                                group = SettingsGroups.DISPLAY,
                                description = "Ajusta o brilho da tela automaticamente",
                                checked = enableAutoBrightness,
                                onCheckedChange = {
                                        enableAutoBrightness = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys.ENABLE_AUTO_BRIGHTNESS
                                                                .key,
                                                        it
                                                )
                                        }
                                        AutoBrightnessManager.getInstance().setEnabled(it)
                                },
                                customContent =
                                        if (enableAutoBrightness) {
                                                {
                                                        Column(
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(12.dp)
                                                        ) {
                                                                HorizontalDivider(
                                                                        color = Color(0xFF3A3F47),
                                                                        thickness = 1.dp
                                                                )

                                                                // Toggle: transição por nascer/pôr do sol
                                                                Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                                Text(
                                                                                        "Transição por nascer/pôr do sol",
                                                                                        color = Color.White,
                                                                                        fontSize = 15.sp,
                                                                                        fontWeight = FontWeight.Medium
                                                                                )
                                                                                Text(
                                                                                        "Usa o GPS pra escurecer/clarear suave (1 nível a cada 5 min) no pôr/nascer do sol. Substitui os horários fixos.",
                                                                                        color = Color(0xFFB0B8C4),
                                                                                        fontSize = 12.sp
                                                                                )
                                                                        }
                                                                        Switch(
                                                                                checked = autoBrightnessUseSun,
                                                                                onCheckedChange = { on ->
                                                                                        autoBrightnessUseSun = on
                                                                                        prefs.edit {
                                                                                                putBoolean(
                                                                                                        SharedPreferencesKeys.AUTO_BRIGHTNESS_USE_SUN.key,
                                                                                                        on
                                                                                                )
                                                                                        }
                                                                                        AutoBrightnessManager.getInstance().updateSchedule()
                                                                                }
                                                                        )
                                                                }

                                                                if (autoBrightnessUseSun) {
                                                                        val sunInfo by produceState<AutoBrightnessManager.SunDisplayInfo?>(
                                                                                initialValue = null,
                                                                                autoBrightnessUseSun
                                                                        ) {
                                                                                // Fora da main; tenta de novo enquanto o GPS não tem fix
                                                                                // ou a cidade ainda é coordenada (geocode em background).
                                                                                var tries = 0
                                                                                while (tries < 15) {
                                                                                        val info = withContext(Dispatchers.IO) {
                                                                                                AutoBrightnessManager.getInstance().getSunInfoForDisplay()
                                                                                        }
                                                                                        value = info
                                                                                        if (info != null && info.cityResolved) break
                                                                                        tries++
                                                                                        delay(2000)
                                                                                }
                                                                        }
                                                                        Column {
                                                                                Text(
                                                                                        "Referência (GPS): ${sunInfo?.city ?: "obtendo localização..."}",
                                                                                        color = Color.White,
                                                                                        fontSize = 14.sp
                                                                                )
                                                                                sunInfo?.let {
                                                                                        Text(
                                                                                                "Nascer ${it.sunrise}  ·  Pôr ${it.sunset}",
                                                                                                color = Color(0xFFB0B8C4),
                                                                                                fontSize = 13.sp
                                                                                        )
                                                                                }
                                                                        }
                                                                }

                                                                if (!autoBrightnessUseSun) {
                                                                        Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceEvenly
                                                                        ) {
                                                                        // Início da noite
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .clickable {
                                                                                                        showStartPicker =
                                                                                                                true
                                                                                                }
                                                                                                .background(
                                                                                                        Color(
                                                                                                                0xFF2A2F37
                                                                                                        ),
                                                                                                        RoundedCornerShape(
                                                                                                                8.dp
                                                                                                        )
                                                                                                )
                                                                                                .padding(
                                                                                                        16.dp
                                                                                                ),
                                                                                contentAlignment =
                                                                                        Alignment
                                                                                                .Center
                                                                        ) {
                                                                                Column(
                                                                                        horizontalAlignment =
                                                                                                Alignment
                                                                                                        .CenterHorizontally
                                                                                ) {
                                                                                        Text(
                                                                                                "Início da noite",
                                                                                                color =
                                                                                                        Color.White,
                                                                                                fontSize =
                                                                                                        14.sp
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.height(
                                                                                                                4.dp
                                                                                                        )
                                                                                        )
                                                                                        Text(
                                                                                                "${String.format("%02d", nightStartHour)}:${String.format("%02d", nightStartMinute)}",
                                                                                                color =
                                                                                                        Color(
                                                                                                                0xFF4A9EFF
                                                                                                        ),
                                                                                                fontSize =
                                                                                                        18.sp,
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Medium
                                                                                        )
                                                                                }
                                                                        }

                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                12.dp
                                                                                        )
                                                                        )

                                                                        // Fim da noite
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .clickable {
                                                                                                        showEndPicker =
                                                                                                                true
                                                                                                }
                                                                                                .background(
                                                                                                        Color(
                                                                                                                0xFF2A2F37
                                                                                                        ),
                                                                                                        RoundedCornerShape(
                                                                                                                8.dp
                                                                                                        )
                                                                                                )
                                                                                                .padding(
                                                                                                        16.dp
                                                                                                ),
                                                                                contentAlignment =
                                                                                        Alignment
                                                                                                .Center
                                                                        ) {
                                                                                Column(
                                                                                        horizontalAlignment =
                                                                                                Alignment
                                                                                                        .CenterHorizontally
                                                                                ) {
                                                                                        Text(
                                                                                                "Fim da noite",
                                                                                                color =
                                                                                                        Color.White,
                                                                                                fontSize =
                                                                                                        14.sp
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.height(
                                                                                                                4.dp
                                                                                                        )
                                                                                        )
                                                                                        Text(
                                                                                                "${String.format("%02d", nightEndHour)}:${String.format("%02d", nightEndMinute)}",
                                                                                                color =
                                                                                                        Color(
                                                                                                                0xFF4A9EFF
                                                                                                        ),
                                                                                                fontSize =
                                                                                                        18.sp,
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Medium
                                                                                        )
                                                                                }
                                                                        }
                                                                        }
                                                                }

                                                                // Slider para nível de brilho
                                                                // diurno
                                                                Column {
                                                                        Text(
                                                                                "Nível de brilho diurno: $dayBrightnessLevel",
                                                                                color = Color.White,
                                                                                fontSize = 14.sp
                                                                        )
                                                                        Slider(
                                                                                value =
                                                                                        dayBrightnessLevel
                                                                                                .toFloat(),
                                                                                onValueChange = {
                                                                                        newValue ->
                                                                                        dayBrightnessLevel =
                                                                                                newValue.toInt()
                                                                                        prefs.edit {
                                                                                                putInt(
                                                                                                        SharedPreferencesKeys
                                                                                                                .AUTO_BRIGHTNESS_LEVEL_DAY
                                                                                                                .key,
                                                                                                        dayBrightnessLevel
                                                                                                )
                                                                                        }
                                                                                },
                                                                                valueRange =
                                                                                        1f..10f,
                                                                                steps = 9,
                                                                                colors =
                                                                                        SliderDefaults
                                                                                                .colors(
                                                                                                        thumbColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        activeTrackColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        inactiveTrackColor =
                                                                                                                Color(
                                                                                                                        0xFF2C3139
                                                                                                                ),
                                                                                                        activeTickColor =
                                                                                                                Color.Transparent,
                                                                                                        inactiveTickColor =
                                                                                                                Color.Transparent,
                                                                                                        disabledThumbColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        disabledActiveTrackColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        disabledInactiveTrackColor =
                                                                                                                Color(
                                                                                                                        0xFF2C3139
                                                                                                                )
                                                                                                )
                                                                        )
                                                                }

                                                                // Slider para nível de brilho
                                                                // noturno
                                                                Column {
                                                                        Text(
                                                                                "Nível de brilho noturno: $nightBrightnessLevel",
                                                                                color = Color.White,
                                                                                fontSize = 14.sp
                                                                        )
                                                                        Slider(
                                                                                value =
                                                                                        nightBrightnessLevel
                                                                                                .toFloat(),
                                                                                onValueChange = {
                                                                                        newValue ->
                                                                                        nightBrightnessLevel =
                                                                                                newValue.toInt()
                                                                                        prefs.edit {
                                                                                                putInt(
                                                                                                        SharedPreferencesKeys
                                                                                                                .AUTO_BRIGHTNESS_LEVEL_NIGHT
                                                                                                                .key,
                                                                                                        nightBrightnessLevel
                                                                                                )
                                                                                        }
                                                                                },
                                                                                valueRange =
                                                                                        1f..10f,
                                                                                steps = 9,
                                                                                colors =
                                                                                        SliderDefaults
                                                                                                .colors(
                                                                                                        thumbColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        activeTrackColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        inactiveTrackColor =
                                                                                                                Color(
                                                                                                                        0xFF2C3139
                                                                                                                ),
                                                                                                        activeTickColor =
                                                                                                                Color.Transparent,
                                                                                                        inactiveTickColor =
                                                                                                                Color.Transparent,
                                                                                                        disabledThumbColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        disabledActiveTrackColor =
                                                                                                                AppColors
                                                                                                                        .Primary,
                                                                                                        disabledInactiveTrackColor =
                                                                                                                Color(
                                                                                                                        0xFF2C3139
                                                                                                                )
                                                                                                )
                                                                        )
                                                                }
                                                        }
                                                }
                                        } else null
                        ),
                        SettingItem(
                                title = "Definir volume inicial",
                                group = SettingsGroups.DISPLAY,
                                description = SharedPreferencesKeys.SET_STARTUP_VOLUME.description,
                                checked = setStartupVolume,
                                onCheckedChange = {
                                        setStartupVolume = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys.SET_STARTUP_VOLUME
                                                                .key,
                                                        it
                                                )
                                        }
                                },
                                sliderValue = volume,
                                sliderRange = 0..40,
                                onSliderChange = { newVolume ->
                                        volume = newVolume
                                        prefs.edit {
                                                putInt(
                                                        SharedPreferencesKeys.STARTUP_VOLUME.key,
                                                        newVolume
                                                )
                                        }
                                },
                                sliderLabel = "Volume: $volume"
                        ),
                        SettingItem(
                                title = "Ajuste de velocidade",
                                group = SettingsGroups.DRIVE,
                                description =
                                        "Ajusta a velocidade exibida no painel (Virtual Cluster)",
                                checked = enableSpeedAdjustment,
                                onCheckedChange = {
                                        enableSpeedAdjustment = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .ENABLE_SPEED_ADJUSTMENT
                                                                .key,
                                                        it
                                                )
                                        }
                                },
                                sliderValue = speedAdjustmentOffset.toInt(),
                                sliderRange = -50..50,
                                sliderStep = 1,
                                onSliderChange = { newValue ->
                                        speedAdjustmentOffset = newValue.toFloat()
                                        prefs.edit {
                                                putFloat(
                                                        SharedPreferencesKeys
                                                                .SPEED_ADJUSTMENT_OFFSET
                                                                .key,
                                                        newValue.toFloat()
                                                )
                                        }
                                },
                                sliderLabel =
                                        "Ajuste: ${if (speedAdjustmentOffset > 0) "+" else ""}${speedAdjustmentOffset.toInt()}%"
                        )
                )
        )


        // Deslocamento do Android Auto no cluster (contorna o crop do menu lateral) — opt-in + slider ao vivo.
        settingsList.add(
                SettingItem(
                        title = "Deslocar Android Auto no cluster",
                        description = "Move a projeção do AA para a direita no cluster (display 3), pra não sobrepor a barra. Começa desligado; ligue e ajuste o slider olhando a tela (aplica ao vivo).",
                        group = SettingsGroups.DRIVE,
                        checked = enableAaClusterOffset,
                        onCheckedChange = {
                                enableAaClusterOffset = it
                                prefs.edit {
                                        putBoolean(SharedPreferencesKeys.ENABLE_AA_CLUSTER_OFFSET.key, it)
                                }
                                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.reapplyAndroidAutoClusterBounds()
                        },
                        customContent = {
                                if (enableAaClusterOffset) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                                Text(
                                                        "Deslocamento: $aaClusterOffset px",
                                                        color = AppColors.TextPrimary,
                                                        fontSize = 14.sp
                                                )
                                                Slider(
                                                        value = aaClusterOffset.toFloat(),
                                                        onValueChange = { aaClusterOffset = it.toInt() },
                                                        onValueChangeFinished = {
                                                                prefs.edit {
                                                                        putInt(SharedPreferencesKeys.AA_CLUSTER_LEFT_OFFSET.key, aaClusterOffset)
                                                                }
                                                                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.reapplyAndroidAutoClusterBounds()
                                                        },
                                                        valueRange = 0f..400f,
                                                        colors = SliderDefaults.colors(
                                                                thumbColor = AppColors.Primary,
                                                                activeTrackColor = AppColors.Primary,
                                                                inactiveTrackColor = Color(0xFF2C3139)
                                                        )
                                                )
                                        }
                                }
                        }
                )
        )

        // Aba "Performance" (SettingsGroups.PERFORMANCE): debloat + DataTrack + overlay CPU/RAM.
        settingsList.addAll(performanceSettingItems(prefs))

        GroupedSettingsLayout(items = settingsList)

        if (showStartPicker) {
                LaunchedEffect(Unit) {
                        val dialog =
                                TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                                nightStartHour = h
                                                nightStartMinute = m
                                                prefs.edit {
                                                        putInt(
                                                                SharedPreferencesKeys
                                                                        .NIGHT_START_HOUR
                                                                        .key,
                                                                h
                                                        )
                                                        putInt(
                                                                SharedPreferencesKeys
                                                                        .NIGHT_START_MINUTE
                                                                        .key,
                                                                m
                                                        )
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
                        val dialog =
                                TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                                nightEndHour = h
                                                nightEndMinute = m
                                                prefs.edit {
                                                        putInt(
                                                                SharedPreferencesKeys.NIGHT_END_HOUR
                                                                        .key,
                                                                h
                                                        )
                                                        putInt(
                                                                SharedPreferencesKeys
                                                                        .NIGHT_END_MINUTE
                                                                        .key,
                                                                m
                                                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteeringWheelClimateCommandDropdown(
        selectedCommandKey: String,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        onCommandSelected: (SteeringWheelClimateCommandType) -> Unit
) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
                TextField(
                        value =
                                SteeringWheelClimateCommandType.entries
                                        .find { it.key == selectedCommandKey }
                                        ?.description
                                        ?: SteeringWheelClimateCommandType.TOGGLE_AC.description,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Comando do ar-condicionado") },
                        trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { onExpandedChange(false) }
                ) {
                        SteeringWheelClimateCommandType.entries.forEach { command ->
                                DropdownMenuItem(
                                        text = { Text(command.description) },
                                        onClick = { onCommandSelected(command) }
                                )
                        }
                }
        }
}
