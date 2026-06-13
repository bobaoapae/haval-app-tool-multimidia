package br.com.redesurftank.havalshisuku.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.managers.AutoBrightnessManager
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.SteeringWheelCustomActionType
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.SettingItem
import br.com.redesurftank.havalshisuku.ui.components.TwoColumnSettingsLayout

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
        var closeWindowOnFoldMirror by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.CLOSE_WINDOW_ON_FOLD_MIRROR.key,
                                false
                        )
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
        var closeSunroofOnFoldMirror by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.CLOSE_SUNROOF_ON_FOLD_MIRROR.key,
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

        val settingsList = mutableListOf<SettingItem>()

        if (isAdvancedUse && !selfInstallationCheck) {
                settingsList.add(
                        SettingItem(
                                title = "Bypass de Verificação",
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
                                title = "Fechar janela ao recolher retrovisores",
                                description =
                                        "Sincroniza fechamento das janelas com o recolhimento dos retrovisores",
                                checked = closeWindowOnFoldMirror,
                                onCheckedChange = {
                                        closeWindowOnFoldMirror = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .CLOSE_WINDOW_ON_FOLD_MIRROR
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Fechar teto solar ao desligar",
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
                                title = "Fechar teto solar ao recolher retrovisores",
                                description =
                                        SharedPreferencesKeys.CLOSE_SUNROOF_ON_FOLD_MIRROR
                                                .description,
                                checked = closeSunroofOnFoldMirror,
                                onCheckedChange = {
                                        closeSunroofOnFoldMirror = it
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .CLOSE_SUNROOF_ON_FOLD_MIRROR
                                                                .key,
                                                        it
                                                )
                                        }
                                }
                        ),
                        SettingItem(
                                title = "Fechar cortina do teto solar",
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
                                                                        Arrangement.spacedBy(16.dp)
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
                                title = "Manter desativado monitoramento de distrações",
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
                                                        }
                                                }
                                        } else null
                        ),
                        SettingItem(
                                title = "Desativar AVAS",
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
                                title = "Ligar ventilação do banco do motorisca com A/C ligado",
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
                                title = "Habilitar botões personalizados no volante",
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
                                                        var action1 by remember {
                                                                mutableStateOf(
                                                                        prefs.getString(
                                                                                SharedPreferencesKeys
                                                                                        .STEERING_WHEEL_CUSTOM_BUTON_1_ACTION
                                                                                        .key,
                                                                                SteeringWheelCustomActionType
                                                                                        .DEFAULT
                                                                                        .key
                                                                        )
                                                                                ?: SteeringWheelCustomActionType
                                                                                        .DEFAULT
                                                                                        .key
                                                                )
                                                        }
                                                        var action2 by remember {
                                                                mutableStateOf(
                                                                        prefs.getString(
                                                                                SharedPreferencesKeys
                                                                                        .STEERING_WHEEL_CUSTOM_BUTON_2_ACTION
                                                                                        .key,
                                                                                SteeringWheelCustomActionType
                                                                                        .DEFAULT
                                                                                        .key
                                                                        )
                                                                                ?: SteeringWheelCustomActionType
                                                                                        .DEFAULT
                                                                                        .key
                                                                )
                                                        }
                                                        var package1 by remember {
                                                                mutableStateOf(
                                                                        prefs.getString(
                                                                                SharedPreferencesKeys
                                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1
                                                                                        .key,
                                                                                ""
                                                                        )
                                                                                ?: ""
                                                                )
                                                        }
                                                        var package2 by remember {
                                                                mutableStateOf(
                                                                        prefs.getString(
                                                                                SharedPreferencesKeys
                                                                                        .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2
                                                                                        .key,
                                                                                ""
                                                                        )
                                                                                ?: ""
                                                                )
                                                        }
                                                        var expanded1 by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var expanded2 by remember {
                                                                mutableStateOf(false)
                                                        }

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
                                                                ExposedDropdownMenuBox(
                                                                        expanded = expanded1,
                                                                        onExpandedChange = {
                                                                                expanded1 =
                                                                                        !expanded1
                                                                        }
                                                                ) {
                                                                        TextField(
                                                                                value =
                                                                                        SteeringWheelCustomActionType
                                                                                                .entries
                                                                                                .find {
                                                                                                        it.key ==
                                                                                                                action1
                                                                                                }
                                                                                                ?.description
                                                                                                ?: "",
                                                                                onValueChange = {},
                                                                                readOnly = true,
                                                                                label = {
                                                                                        Text(
                                                                                                "Tipo de Ação"
                                                                                        )
                                                                                },
                                                                                trailingIcon = {
                                                                                        ExposedDropdownMenuDefaults
                                                                                                .TrailingIcon(
                                                                                                        expanded =
                                                                                                                expanded1
                                                                                                )
                                                                                },
                                                                                colors =
                                                                                        ExposedDropdownMenuDefaults
                                                                                                .textFieldColors(),
                                                                                modifier =
                                                                                        Modifier.menuAnchor(
                                                                                                MenuAnchorType
                                                                                                        .PrimaryNotEditable
                                                                                        )
                                                                        )
                                                                        ExposedDropdownMenu(
                                                                                expanded =
                                                                                        expanded1,
                                                                                onDismissRequest = {
                                                                                        expanded1 =
                                                                                                false
                                                                                }
                                                                        ) {
                                                                                SteeringWheelCustomActionType
                                                                                        .entries
                                                                                        .forEach {
                                                                                                type
                                                                                                ->
                                                                                                DropdownMenuItem(
                                                                                                        text = {
                                                                                                                Text(
                                                                                                                        type.description
                                                                                                                )
                                                                                                        },
                                                                                                        onClick = {
                                                                                                                action1 =
                                                                                                                        type.key
                                                                                                                prefs
                                                                                                                        .edit {
                                                                                                                                putString(
                                                                                                                                        SharedPreferencesKeys
                                                                                                                                                .STEERING_WHEEL_CUSTOM_BUTON_1_ACTION
                                                                                                                                                .key,
                                                                                                                                        type.key
                                                                                                                                )
                                                                                                                        }
                                                                                                                expanded1 =
                                                                                                                        false
                                                                                                                ServiceManager
                                                                                                                        .getInstance()
                                                                                                                        .ensureSteeringWheelButtonIntegration()
                                                                                                        }
                                                                                                )
                                                                                        }
                                                                        }
                                                                }
                                                                if (action1 ==
                                                                                SteeringWheelCustomActionType
                                                                                        .OPEN_APP
                                                                                        .key
                                                                ) {
                                                                        TextField(
                                                                                value = package1,
                                                                                onValueChange = {
                                                                                        newPkg ->
                                                                                        package1 =
                                                                                                newPkg
                                                                                        prefs.edit {
                                                                                                putString(
                                                                                                        SharedPreferencesKeys
                                                                                                                .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1
                                                                                                                .key,
                                                                                                        newPkg
                                                                                                )
                                                                                        }
                                                                                },
                                                                                label = {
                                                                                        Text(
                                                                                                "Pacote do App"
                                                                                        )
                                                                                },
                                                                                colors =
                                                                                        TextFieldDefaults
                                                                                                .colors(
                                                                                                        focusedContainerColor =
                                                                                                                Color(
                                                                                                                        0xFF2A2F37
                                                                                                                ),
                                                                                                        unfocusedContainerColor =
                                                                                                                Color(
                                                                                                                        0xFF2A2F37
                                                                                                                ),
                                                                                                        focusedTextColor =
                                                                                                                Color.White,
                                                                                                        unfocusedTextColor =
                                                                                                                Color(
                                                                                                                        0xFFB0B8C4
                                                                                                                ),
                                                                                                        focusedIndicatorColor =
                                                                                                                Color(
                                                                                                                        0xFF4A9EFF
                                                                                                                ),
                                                                                                        unfocusedIndicatorColor =
                                                                                                                Color(
                                                                                                                        0xFF3A3F47
                                                                                                                )
                                                                                                )
                                                                        )
                                                                }

                                                                HorizontalDivider(
                                                                        color = Color(0xFF3A3F47),
                                                                        thickness = 1.dp
                                                                )

                                                                Text(
                                                                        "Botão 2",
                                                                        color = Color.White,
                                                                        fontSize = 16.sp
                                                                )
                                                                ExposedDropdownMenuBox(
                                                                        expanded = expanded2,
                                                                        onExpandedChange = {
                                                                                expanded2 =
                                                                                        !expanded2
                                                                        }
                                                                ) {
                                                                        TextField(
                                                                                value =
                                                                                        SteeringWheelCustomActionType
                                                                                                .entries
                                                                                                .find {
                                                                                                        it.key ==
                                                                                                                action2
                                                                                                }
                                                                                                ?.description
                                                                                                ?: "",
                                                                                onValueChange = {},
                                                                                readOnly = true,
                                                                                label = {
                                                                                        Text(
                                                                                                "Tipo de Ação"
                                                                                        )
                                                                                },
                                                                                trailingIcon = {
                                                                                        ExposedDropdownMenuDefaults
                                                                                                .TrailingIcon(
                                                                                                        expanded =
                                                                                                                expanded2
                                                                                                )
                                                                                },
                                                                                colors =
                                                                                        ExposedDropdownMenuDefaults
                                                                                                .textFieldColors(),
                                                                                modifier =
                                                                                        Modifier.menuAnchor(
                                                                                                MenuAnchorType
                                                                                                        .PrimaryNotEditable
                                                                                        )
                                                                        )
                                                                        ExposedDropdownMenu(
                                                                                expanded =
                                                                                        expanded2,
                                                                                onDismissRequest = {
                                                                                        expanded2 =
                                                                                                false
                                                                                }
                                                                        ) {
                                                                                SteeringWheelCustomActionType
                                                                                        .entries
                                                                                        .forEach {
                                                                                                type
                                                                                                ->
                                                                                                DropdownMenuItem(
                                                                                                        text = {
                                                                                                                Text(
                                                                                                                        type.description
                                                                                                                )
                                                                                                        },
                                                                                                        onClick = {
                                                                                                                action2 =
                                                                                                                        type.key
                                                                                                                prefs
                                                                                                                        .edit {
                                                                                                                                putString(
                                                                                                                                        SharedPreferencesKeys
                                                                                                                                                .STEERING_WHEEL_CUSTOM_BUTON_2_ACTION
                                                                                                                                                .key,
                                                                                                                                        type.key
                                                                                                                                )
                                                                                                                        }
                                                                                                                expanded2 =
                                                                                                                        false
                                                                                                                ServiceManager
                                                                                                                        .getInstance()
                                                                                                                        .ensureSteeringWheelButtonIntegration()
                                                                                                        }
                                                                                                )
                                                                                        }
                                                                        }
                                                                }
                                                                if (action2 ==
                                                                                SteeringWheelCustomActionType
                                                                                        .OPEN_APP
                                                                                        .key
                                                                ) {
                                                                        TextField(
                                                                                value = package2,
                                                                                onValueChange = {
                                                                                        newPkg ->
                                                                                        package2 =
                                                                                                newPkg
                                                                                        prefs.edit {
                                                                                                putString(
                                                                                                        SharedPreferencesKeys
                                                                                                                .STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2
                                                                                                                .key,
                                                                                                        newPkg
                                                                                                )
                                                                                        }
                                                                                },
                                                                                label = {
                                                                                        Text(
                                                                                                "Pacote do App"
                                                                                        )
                                                                                },
                                                                                colors =
                                                                                        TextFieldDefaults
                                                                                                .colors(
                                                                                                        focusedContainerColor =
                                                                                                                Color(
                                                                                                                        0xFF2A2F37
                                                                                                                ),
                                                                                                        unfocusedContainerColor =
                                                                                                                Color(
                                                                                                                        0xFF2A2F37
                                                                                                                ),
                                                                                                        focusedTextColor =
                                                                                                                Color.White,
                                                                                                        unfocusedTextColor =
                                                                                                                Color(
                                                                                                                        0xFFB0B8C4
                                                                                                                ),
                                                                                                        focusedIndicatorColor =
                                                                                                                Color(
                                                                                                                        0xFF4A9EFF
                                                                                                                ),
                                                                                                        unfocusedIndicatorColor =
                                                                                                                Color(
                                                                                                                        0xFF3A3F47
                                                                                                                )
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


        TwoColumnSettingsLayout(settingsList = settingsList)

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
