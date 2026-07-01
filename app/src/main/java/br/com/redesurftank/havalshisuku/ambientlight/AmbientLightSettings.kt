package br.com.redesurftank.havalshisuku.ambientlight

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.StyledCard
import kotlin.math.roundToInt

data class AmbientLightConfig(
    val enabled: Boolean,
    val deviceAddress: String?,
    val deviceName: String?,
    val syncDriveMode: Boolean,
    val animationsEnabled: Boolean,
    val musicAnimationEnabled: Boolean,
    val musicMode: AmbientLightMusicMode,
    val albumEffect: AmbientLightAlbumEffect,
    val albumEffectSpeed: Int,
    val albumEffectOutput: AmbientLightOutput,
    val albumBleMode: AmbientLightAlbumOutputMode,
    val albumDmxMode: AmbientLightAlbumOutputMode,
    val colorOrder: ColorOrderMapper,
    val bleColorOrder: ColorOrderMapper,
    val brightnessPercent: Int,
    val output: AmbientLightOutput,
    val autoReconnect: Boolean
)

object AmbientLightSettings {
    private fun prefs() =
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    fun load(): AmbientLightConfig {
        val prefs = prefs()
        return AmbientLightConfig(
            enabled = prefs.getBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_ENABLED.key, false),
            deviceAddress = prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_DEVICE_MAC.key, null),
            deviceName = prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_DEVICE_NAME.key, null),
            syncDriveMode = prefs.getBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_SYNC_DRIVE_MODE.key, false),
            animationsEnabled = prefs.getBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_ANIMATIONS_ENABLED.key, false),
            musicAnimationEnabled =
                prefs.getBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_MUSIC_ANIMATION_ENABLED.key, false),
            musicMode =
                AmbientLightMusicMode.fromStored(
                    prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_MUSIC_MODE.key, null)
                ),
            albumEffect =
                AmbientLightAlbumEffect.fromStored(
                    prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_EFFECT.key, null)
                ),
            albumEffectSpeed =
                prefs.getInt(
                    SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_EFFECT_SPEED.key,
                    DEFAULT_ALBUM_EFFECT_SPEED
                ).coerceIn(MIN_ALBUM_EFFECT_SPEED, MAX_ALBUM_EFFECT_SPEED),
            albumEffectOutput =
                AmbientLightOutput.fromStored(
                    prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_EFFECT_OUTPUT.key, null)
                ),
            albumBleMode =
                AmbientLightAlbumOutputMode.fromStored(
                    prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_BLE_MODE.key, null)
                ),
            albumDmxMode =
                AmbientLightAlbumOutputMode.fromStored(
                    prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_DMX_MODE.key, null)
                ),
            colorOrder =
                ColorOrderMapper.fromStored(
                    prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_COLOR_ORDER.key, null),
                    ColorOrderMapper.DEFAULT_DMX
                ),
            bleColorOrder =
                ColorOrderMapper.fromStored(
                    prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_COLOR_ORDER.key, null),
                    ColorOrderMapper.DEFAULT_BLE
                ),
            brightnessPercent =
                prefs.getInt(SharedPreferencesKeys.AMBIENT_LIGHT_BRIGHTNESS_PERCENT.key, 100)
                    .coerceIn(0, 100),
            output =
                AmbientLightOutput.fromStored(
                    prefs.getString(SharedPreferencesKeys.AMBIENT_LIGHT_OUTPUT.key, null)
                ),
            autoReconnect = prefs.getBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_AUTO_RECONNECT.key, true)
        )
    }

    fun isEnabled(): Boolean =
        prefs().getBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_ENABLED.key, false)

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_ENABLED.key, enabled).apply()
    }

    fun saveDevice(address: String, name: String?) {
        Log.w(TAG, "saving BLE device address=$address name=${name ?: "-"}")
        prefs()
            .edit()
            .putString(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_DEVICE_MAC.key, address)
            .putString(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_DEVICE_NAME.key, name)
            .commit()
    }

    fun forgetDevice() {
        prefs()
            .edit()
            .remove(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_DEVICE_MAC.key)
            .remove(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_DEVICE_NAME.key)
            .apply()
    }

    fun setSyncDriveMode(enabled: Boolean) {
        prefs().edit().putBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_SYNC_DRIVE_MODE.key, enabled).apply()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_ANIMATIONS_ENABLED.key, enabled).apply()
    }

    fun setMusicAnimationEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_MUSIC_ANIMATION_ENABLED.key, enabled).apply()
    }

    fun setMusicMode(mode: AmbientLightMusicMode) {
        prefs().edit().putString(SharedPreferencesKeys.AMBIENT_LIGHT_MUSIC_MODE.key, mode.name).apply()
    }

    fun setAlbumEffect(effect: AmbientLightAlbumEffect) {
        prefs().edit().putString(SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_EFFECT.key, effect.name).apply()
    }

    fun setAlbumEffectSpeed(speed: Int) {
        prefs()
            .edit()
            .putInt(
                SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_EFFECT_SPEED.key,
                speed.coerceIn(MIN_ALBUM_EFFECT_SPEED, MAX_ALBUM_EFFECT_SPEED)
            )
            .apply()
    }

    fun setAlbumEffectOutput(output: AmbientLightOutput) {
        prefs().edit().putString(SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_EFFECT_OUTPUT.key, output.name).apply()
    }

    fun setAlbumBleMode(mode: AmbientLightAlbumOutputMode) {
        prefs().edit().putString(SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_BLE_MODE.key, mode.name).apply()
    }

    fun setAlbumDmxMode(mode: AmbientLightAlbumOutputMode) {
        prefs().edit().putString(SharedPreferencesKeys.AMBIENT_LIGHT_ALBUM_DMX_MODE.key, mode.name).apply()
    }

    fun setColorOrder(colorOrder: ColorOrderMapper) {
        prefs().edit().putString(SharedPreferencesKeys.AMBIENT_LIGHT_COLOR_ORDER.key, colorOrder.name).apply()
    }

    fun setBleColorOrder(colorOrder: ColorOrderMapper) {
        prefs().edit().putString(SharedPreferencesKeys.AMBIENT_LIGHT_BLE_COLOR_ORDER.key, colorOrder.name).apply()
    }

    fun setBrightnessPercent(percent: Int) {
        prefs()
            .edit()
            .putInt(SharedPreferencesKeys.AMBIENT_LIGHT_BRIGHTNESS_PERCENT.key, percent.coerceIn(0, 100))
            .apply()
    }

    fun setOutput(output: AmbientLightOutput) {
        prefs().edit().putString(SharedPreferencesKeys.AMBIENT_LIGHT_OUTPUT.key, output.name).apply()
    }

    fun setAutoReconnect(enabled: Boolean) {
        prefs().edit().putBoolean(SharedPreferencesKeys.AMBIENT_LIGHT_AUTO_RECONNECT.key, enabled).apply()
    }

    private const val TAG = "AmbientLight"
    const val DEFAULT_ALBUM_EFFECT_SPEED = 50
    const val MIN_ALBUM_EFFECT_SPEED = 1
    const val MAX_ALBUM_EFFECT_SPEED = 100
}

@Composable
fun AmbientLightSettingsScreen(onBackToFeatures: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { AmbientLightBleController.getInstance(context) }
    val scanner = remember { AmbientLightDeviceScanner(context) }
    val connectionState by controller.state.collectAsState()
    val debugState by controller.debugState.collectAsState()
    val devices = remember { mutableStateListOf<AmbientLightScanResult>() }
    var config by remember { mutableStateOf(AmbientLightSettings.load()) }
    var brightnessDraft by remember { mutableStateOf(config.brightnessPercent.toFloat()) }
    var albumEffectSpeedDraft by remember { mutableStateOf(config.albumEffectSpeed.toFloat()) }
    var scanning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshConfig() {
        config = AmbientLightSettings.load()
    }

    fun startBleScan() {
        if (!AmbientLightDeviceScanner.hasScanPermission(context)) {
            statusMessage = "Permissao BLE necessaria para buscar dispositivos"
            return
        }
        devices.clear()
        scanning = true
        statusMessage = "Buscando dispositivos BLE..."
        scanner.startScan(
            onResult = { result ->
                val current = devices.indexOfFirst { it.address == result.address }
                if (current < 0) {
                    devices.add(result)
                    devices.sortWith(
                        compareByDescending<AmbientLightScanResult> { it.isLikelyLedLamp }
                            .thenByDescending { it.rssi }
                    )
                }
            },
            onError = { statusMessage = it },
            onFinished = {
                scanning = false
                if (devices.isEmpty()) statusMessage = "Nenhum dispositivo BLE encontrado"
            }
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                startBleScan()
            } else {
                statusMessage = "Permissao BLE negada"
            }
        }

    val audioPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AmbientLightSettings.setMusicAnimationEnabled(granted)
            if (granted) {
                AmbientLightSettings.setMusicMode(AmbientLightMusicMode.BASS)
            }
            refreshConfig()
            if (granted) {
                AmbientLightService.startIfEnabled(context)
                statusMessage = "Efeito com graves ativado"
            } else {
                statusMessage = "Permissao de audio negada"
            }
        }

    DisposableEffect(Unit) {
        onDispose { scanner.stopScan() }
    }

    LaunchedEffect(config.brightnessPercent) {
        brightnessDraft = config.brightnessPercent.toFloat()
    }

    LaunchedEffect(config.albumEffectSpeed) {
        albumEffectSpeedDraft = config.albumEffectSpeed.toFloat()
    }

    Column(
        modifier =
            Modifier
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
                Text("Ambient Light BLE", color = AppColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Controle opcional para LEDs externos instalados pelo usuario.", color = AppColors.TextSecondary, fontSize = 16.sp)
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Modulo opcional", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("Desative para esconder o card e parar conexoes BLE.", color = AppColors.TextSecondary, fontSize = 14.sp)
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { checked ->
                            AmbientLightSettings.setEnabled(checked)
                            refreshConfig()
                            if (checked) {
                                AmbientLightService.startIfEnabled(context)
                            } else {
                                AmbientLightService.stop(context)
                                onBackToFeatures()
                            }
                        }
                    )
                }
                Text("Status: ${connectionState.label}", color = statusColor(connectionState), fontSize = 15.sp)
                statusMessage?.let { Text(it, color = AppColors.TextSecondary, fontSize = 14.sp) }
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Dispositivo LED", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    config.deviceAddress?.let { "${config.deviceName ?: "Sem nome"} - $it" }
                        ?: "Nenhum LED selecionado",
                    color = AppColors.TextSecondary,
                    fontSize = 15.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !scanning,
                        onClick = {
                            if (AmbientLightDeviceScanner.hasScanPermission(context)) {
                                startBleScan()
                            } else {
                                permissionLauncher.launch(AmbientLightDeviceScanner.requiredPermissions())
                            }
                        }
                    ) {
                        Text(if (scanning) "Buscando..." else "Buscar BLE")
                    }
                    OutlinedButton(
                        enabled = !config.deviceAddress.isNullOrBlank(),
                        onClick = {
                            context.startService(AmbientLightService.createConnectIntent(context))
                            statusMessage = "Conectando no LED salvo..."
                        }
                    ) {
                        Text("Testar conexao")
                    }
                    OutlinedButton(
                        enabled = !config.deviceAddress.isNullOrBlank(),
                        onClick = {
                            context.startService(AmbientLightService.createDisconnectIntent(context))
                            statusMessage = "Desconectando LED..."
                        }
                    ) {
                        Text("Desconectar LED")
                    }
                    OutlinedButton(
                        enabled = !config.deviceAddress.isNullOrBlank(),
                        onClick = {
                            AmbientLightSettings.forgetDevice()
                            context.startService(AmbientLightService.createDisconnectIntent(context))
                            refreshConfig()
                            statusMessage = "LED esquecido"
                        }
                    ) {
                        Text("Esquecer")
                    }
                }

                if (devices.isNotEmpty()) {
                    Column(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        devices.take(12).forEach { device ->
                            DeviceResultRow(device = device) {
                                scanner.stopScan()
                                scanning = false
                                AmbientLightSettings.saveDevice(device.address, device.name)
                                refreshConfig()
                                context.startService(
                                    AmbientLightService.createConnectIntent(
                                        context = context,
                                        address = device.address,
                                        name = device.name
                                    )
                                )
                                statusMessage = "Conectando em ${device.address}"
                            }
                        }
                    }
                }
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Controlador", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "DMX: ${config.colorOrder.label}. BLE: ${config.bleColorOrder.label}. Vermelho permanece no canal R; G/B ficam ajustaveis.",
                    color = AppColors.TextSecondary,
                    fontSize = 14.sp
                )
                Text("Ordem DMX", color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                OptionButtonWrap(
                    options = ColorOrderMapper.values().map { it.label to it },
                    selected = config.colorOrder,
                    enabled = true,
                    onSelect = {
                        AmbientLightSettings.setColorOrder(it)
                        refreshConfig()
                        statusMessage = "Ordem de cor: ${it.label}"
                    }
                )
                Text("Ordem BLE", color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                OptionButtonWrap(
                    options = ColorOrderMapper.values().map { it.label to it },
                    selected = config.bleColorOrder,
                    enabled = true,
                    onSelect = {
                        AmbientLightSettings.setBleColorOrder(it)
                        refreshConfig()
                        statusMessage = "Ordem BLE: ${it.label}"
                    }
                )
                Text("Saida: ${config.output.label}", color = AppColors.TextSecondary, fontSize = 14.sp)
                OptionButtonWrap(
                    options = AmbientLightOutput.values().map { it.label to it },
                    selected = config.output,
                    enabled = true,
                    onSelect = {
                        AmbientLightSettings.setOutput(it)
                        refreshConfig()
                        statusMessage = "Saida Ambient Light: ${it.label}"
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Brilho: ${brightnessDraft.roundToInt().coerceIn(0, 100)}%", color = AppColors.TextPrimary, fontSize = 16.sp)
                    Slider(
                        value = brightnessDraft,
                        onValueChange = { brightnessDraft = it.coerceIn(0f, 100f) },
                        onValueChangeFinished = {
                            val percent = brightnessDraft.roundToInt().coerceIn(0, 100)
                            AmbientLightSettings.setBrightnessPercent(percent)
                            refreshConfig()
                            context.startService(AmbientLightService.createBrightnessIntent(context, percent))
                            statusMessage = "Enviando brilho ${AmbientLightProtocol.setBrightnessHex(percent)}"
                        },
                        valueRange = 0f..100f,
                        steps = 99
                    )
                }
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Teste de cores", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                ColorButtonRow(
                    listOf(
                        "Vermelho" to AmbientLightProtocol.RED,
                        "Verde" to AmbientLightProtocol.GREEN,
                        "Azul" to AmbientLightProtocol.BLUE
                    ),
                    enabled = !config.deviceAddress.isNullOrBlank()
                ) { color ->
                    sendTestColor(context, color)
                    statusMessage = "Enviando ${previewRgbHex(color, config)}"
                }
                ColorButtonRow(
                    listOf(
                        "Branco" to AmbientLightProtocol.WHITE,
                        "Amarelo" to AmbientLightProtocol.YELLOW,
                        "Roxo" to AmbientLightProtocol.PURPLE
                    ),
                    enabled = !config.deviceAddress.isNullOrBlank()
                ) { color ->
                    sendTestColor(context, color)
                    statusMessage = "Enviando ${previewRgbHex(color, config)}"
                }
                OutlinedButton(
                    enabled = !config.deviceAddress.isNullOrBlank(),
                    onClick = {
                        context.startService(
                            AmbientLightService.createSendHexIntent(context, "7B0007FF000000FFBF")
                        )
                        statusMessage = "Enviando HEX validado 7B0007FF000000FFBF"
                    }
                ) {
                    Text("Enviar HEX validado")
                }
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Automacao", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                SettingSwitchRow(
                    title = "Reconectar automaticamente",
                    description = "Tenta reconectar se o LED cair ou a central perder o GATT.",
                    checked = config.autoReconnect,
                    onCheckedChange = {
                        AmbientLightSettings.setAutoReconnect(it)
                        refreshConfig()
                        if (it) AmbientLightService.startIfEnabled(context)
                    }
                )
                SettingSwitchRow(
                    title = "Sincronizar com modo de conducao",
                    description = "Eco, Normal, Sport, Neve e Offroad mudam a cor automaticamente.",
                    checked = config.syncDriveMode,
                    onCheckedChange = {
                        AmbientLightSettings.setSyncDriveMode(it)
                        refreshConfig()
                        AmbientLightService.startIfEnabled(context)
                    }
                )
                SettingSwitchRow(
                    title = "Ativar animacoes",
                    description = "Fade entre cores, Sport pulse, Eco breathing e welcome.",
                    checked = config.animationsEnabled,
                    onCheckedChange = {
                        AmbientLightSettings.setAnimationsEnabled(it)
                        refreshConfig()
                        AmbientLightService.startIfEnabled(context)
                    }
                )
                SettingSwitchRow(
                    title = "Efeito com musica",
                    description = "Anima os LEDs quando houver musica tocando.",
                    checked = config.musicAnimationEnabled,
                    onCheckedChange = { checked ->
                        if (checked && config.musicMode == AmbientLightMusicMode.BASS && !hasAudioCapturePermission(context)) {
                            statusMessage = "Permissao de audio necessaria para capturar graves"
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            AmbientLightSettings.setMusicAnimationEnabled(checked)
                            refreshConfig()
                            AmbientLightService.startIfEnabled(context)
                            statusMessage =
                                if (checked) {
                                    "Efeito com musica ativado"
                                } else {
                                    "Efeito com musica desativado"
                                }
                        }
                    }
                )
                Text("Modo do efeito: ${config.musicMode.label}", color = AppColors.TextSecondary, fontSize = 14.sp)
                OptionButtonWrap(
                    options = AmbientLightMusicMode.values().map { it.label to it },
                    selected = config.musicMode,
                    enabled = true,
                    onSelect = { mode ->
                        AmbientLightSettings.setMusicMode(mode)
                        if (config.musicAnimationEnabled && mode == AmbientLightMusicMode.BASS && !hasAudioCapturePermission(context)) {
                            AmbientLightSettings.setMusicAnimationEnabled(false)
                            refreshConfig()
                            statusMessage = "Permissao de audio necessaria para capturar graves"
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            refreshConfig()
                            AmbientLightService.startIfEnabled(context)
                            statusMessage = "Modo musical: ${mode.label}"
                        }
                    }
                )
                if (config.musicMode == AmbientLightMusicMode.ALBUM_WAVE) {
                    Text("Efeito: ${config.albumEffect.label}", color = AppColors.TextSecondary, fontSize = 14.sp)
                    OptionButtonWrap(
                        options = AmbientLightAlbumEffect.values().map { it.shortLabel to it },
                        selected = config.albumEffect,
                        enabled = true,
                        onSelect = { effect ->
                            AmbientLightSettings.setAlbumEffect(effect)
                            refreshConfig()
                            AmbientLightService.startIfEnabled(context)
                            statusMessage = "Efeito: ${effect.label}"
                        }
                    )
                    Text("Aplicar em: ${config.albumEffectOutput.label}", color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    OptionButtonWrap(
                        options = AmbientLightOutput.values().map { it.label to it },
                        selected = config.albumEffectOutput,
                        enabled = true,
                        onSelect = { output ->
                            AmbientLightSettings.setAlbumEffectOutput(output)
                            refreshConfig()
                            AmbientLightService.startIfEnabled(context)
                            statusMessage = "Efeito aplicado em ${output.label}"
                        }
                    )
                    Text("BLE: ${config.albumBleMode.label}", color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    OptionButtonWrap(
                        options = AmbientLightAlbumOutputMode.values().map { it.label to it },
                        selected = config.albumBleMode,
                        enabled = true,
                        onSelect = { mode ->
                            AmbientLightSettings.setAlbumBleMode(mode)
                            refreshConfig()
                            AmbientLightService.startIfEnabled(context)
                            statusMessage = "BLE ${mode.label}"
                        }
                    )
                    Text("DMX: ${config.albumDmxMode.label}", color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    OptionButtonWrap(
                        options = AmbientLightAlbumOutputMode.values().map { it.label to it },
                        selected = config.albumDmxMode,
                        enabled = true,
                        onSelect = { mode ->
                            AmbientLightSettings.setAlbumDmxMode(mode)
                            refreshConfig()
                            AmbientLightService.startIfEnabled(context)
                            statusMessage = "DMX ${mode.label}"
                        }
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Speed: ${albumEffectSpeedDraft.roundToInt().coerceIn(AmbientLightSettings.MIN_ALBUM_EFFECT_SPEED, AmbientLightSettings.MAX_ALBUM_EFFECT_SPEED)}",
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp
                        )
                        Slider(
                            value = albumEffectSpeedDraft,
                            onValueChange = {
                                albumEffectSpeedDraft =
                                    it.coerceIn(
                                        AmbientLightSettings.MIN_ALBUM_EFFECT_SPEED.toFloat(),
                                        AmbientLightSettings.MAX_ALBUM_EFFECT_SPEED.toFloat()
                                    )
                            },
                            onValueChangeFinished = {
                                val speed =
                                    albumEffectSpeedDraft.roundToInt()
                                        .coerceIn(
                                            AmbientLightSettings.MIN_ALBUM_EFFECT_SPEED,
                                            AmbientLightSettings.MAX_ALBUM_EFFECT_SPEED
                                        )
                                AmbientLightSettings.setAlbumEffectSpeed(speed)
                                refreshConfig()
                                AmbientLightService.startIfEnabled(context)
                                statusMessage = "Speed: $speed"
                            },
                            valueRange =
                                AmbientLightSettings.MIN_ALBUM_EFFECT_SPEED.toFloat()..
                                    AmbientLightSettings.MAX_ALBUM_EFFECT_SPEED.toFloat(),
                            steps = AmbientLightSettings.MAX_ALBUM_EFFECT_SPEED - AmbientLightSettings.MIN_ALBUM_EFFECT_SPEED - 1
                        )
                    }
                }
            }
        }

        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ambient Light Debug", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                DebugLine("Status", connectionState.label)
                DebugLine("RSSI", debugState.rssi?.let { "$it dBm" } ?: "A confirmar")
                DebugLine("Service", debugState.serviceUuid)
                DebugLine("Characteristic", debugState.characteristicUuid)
                DebugLine("Payload", debugState.lastPayloadHex ?: "Nenhum")
                DebugLine("Ultimo erro", debugState.lastError ?: "Nenhum")
                DebugLine("Musica", if (debugState.musicVisualizerActive) "Ativo" else "Inativo")
                DebugLine("Modo musica", config.musicMode.label)
                if (config.musicMode == AmbientLightMusicMode.ALBUM_WAVE) {
                    DebugLine("Efeito", config.albumEffect.label)
                    DebugLine("Efeito saida", config.albumEffectOutput.label)
                    DebugLine("BLE modo", config.albumBleMode.label)
                    DebugLine("DMX modo", config.albumDmxMode.label)
                    DebugLine("Efeito speed", config.albumEffectSpeed.toString())
                }
                DebugLine("Fonte musica", debugState.musicCaptureSource)
                DebugLine(
                    if (config.musicMode == AmbientLightMusicMode.BASS) "Grave" else "Onda",
                    "${(debugState.musicBassLevel * 100).roundToInt().coerceIn(0, 100)}%"
                )
                DebugLine(
                    if (config.musicMode == AmbientLightMusicMode.BASS) "Ultima batida" else "Ultimo passo",
                    musicBeatAge(debugState.musicLastBeatElapsedMs)
                )
                DebugLine("Erro musica", debugState.musicLastError ?: "Nenhum")
            }
        }
    }
}

@Composable
private fun DeviceResultRow(device: AmbientLightScanResult, onSelect: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name ?: "Dispositivo BLE", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${device.address}  RSSI ${device.rssi}", color = AppColors.TextSecondary, fontSize = 13.sp)
            Text("Tipo: ${deviceType(device)}", color = AppColors.TextSecondary, fontSize = 12.sp)
        }
        OutlinedButton(onClick = onSelect) {
            Text("Conectar")
        }
    }
}

@Composable
private fun <T> OptionButtonWrap(
    options: List<Pair<String, T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (label, value) ->
                    if (value == selected) {
                        Button(enabled = enabled, onClick = { onSelect(value) }) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(enabled = enabled, onClick = { onSelect(value) }) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorButtonRow(
    colors: List<Pair<String, LedColor>>,
    enabled: Boolean,
    onClick: (LedColor) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { (label, color) ->
            Button(enabled = enabled, onClick = { onClick(color) }) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = AppColors.TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun sendTestColor(context: Context, color: LedColor) {
    context.startService(AmbientLightService.createTestColorIntent(context, color))
}

private fun hasAudioCapturePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun musicBeatAge(lastBeatElapsedMs: Long?): String {
    val elapsed = lastBeatElapsedMs ?: return "Nenhuma"
    val ageMs = (SystemClock.elapsedRealtime() - elapsed).coerceAtLeast(0L)
    return "${ageMs / 1000}.${((ageMs % 1000) / 100)}s"
}

private fun previewRgbHex(color: LedColor, config: AmbientLightConfig): String =
    AmbientLightProtocol.rgbPayloads(
        color.r,
        color.g,
        color.b,
        config.colorOrder,
        config.bleColorOrder,
        config.output
    )
        .joinToString(separator = " + ") { AmbientLightProtocol.bytesToHex(it) }

private fun deviceType(device: AmbientLightScanResult): String {
    val name = device.name.orEmpty()
    return when {
        device.hasLedLampService -> "LEDLAMP FFE0"
        name.contains("LEDCAR", ignoreCase = true) -> "LEDCAR"
        name.contains("LEDDMX", ignoreCase = true) -> "LEDDMX"
        name.contains("LED", ignoreCase = true) -> "LED"
        else -> "BLE nao identificado"
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", color = AppColors.TextSecondary, fontSize = 13.sp)
        Text(value, color = AppColors.TextPrimary, fontSize = 13.sp)
    }
}

private fun statusColor(state: AmbientLightConnectionState): Color {
    return when (state) {
        is AmbientLightConnectionState.Connected -> Color(0xFF14FF5A)
        is AmbientLightConnectionState.Error -> Color(0xFFFF6B6B)
        is AmbientLightConnectionState.Connecting,
        is AmbientLightConnectionState.Reconnecting -> Color(0xFFFFC857)
        AmbientLightConnectionState.Idle -> AppColors.TextSecondary
    }
}
