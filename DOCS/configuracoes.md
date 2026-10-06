# Sistema de Configurações

## SharedPreferences

O app usa `SharedPreferences` com **DeviceProtectedStorage** para que as configurações fiquem disponíveis mesmo antes do usuário desbloquear o dispositivo (importante para boot automático):

```kotlin
val prefs = App.getDeviceProtectedContext()
    .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
```

O nome do arquivo é sempre `haval_prefs`.

## PreferenceKeys (SharedPreferencesKeys.kt)

**Arquivo:** `models/SharedPreferencesKeys.kt`

Enum Kotlin que define todas as chaves de configuração. Cada entrada tem:
- `key` - string usada no SharedPreferences
- `description` - descrição em português (usada na UI)

```kotlin
enum class SharedPreferencesKeys(val key: String, val description: String) {
    DISABLE_MONITORING("disableMonitoring", "Manter desativado monitoramento de distrações"),
    // ...
}
```

### Lista de todas as chaves

| Chave | Key | Tipo | Descrição |
|-------|-----|------|-----------|
| `DISABLE_MONITORING` | `disableMonitoring` | Boolean | Manter desativado monitoramento de distrações |
| `CLOSE_WINDOW_ON_POWER_OFF` | `closeWindowOnPowerOff` | Boolean | Fechar janelas ao desligar o veículo |
| `CLOSE_WINDOW_ON_FOLD_MIRROR` | `closeWindowOnFoldMirror` | Boolean | Fechar janelas ao recolher retrovisores |
| `CLOSE_SUNROOF_ON_POWER_OFF` | `closeSunroofOnPowerOff` | Boolean | Fechar teto solar ao desligar |
| `CLOSE_SUNROOF_ON_FOLD_MIRROR` | `closeSunroofOnFoldMirror` | Boolean | Fechar teto solar ao recolher retrovisores |
| `CLOSE_WINDOWS_ON_SPEED` | `closeWindowsOnSpeed` | Boolean | Fechar janelas ao atingir velocidade |
| `CLOSE_SUNROOF_ON_SPEED` | `closeSunroofOnSpeed` | Boolean | Fechar teto solar ao atingir velocidade |
| `CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF` | `closeSunroofSunShadeOnCloseSunRoof` | Boolean | Fechar cortina ao fechar teto |
| `SET_STARTUP_VOLUME` | `setStartupVolume` | Boolean | Habilitar volume fixo ao ligar |
| `STARTUP_VOLUME` | `startupVolume` | Int | Volume ao ligar (0-40) |
| `SPEED_THRESHOLD` | `speedThreshold` | Float | Velocidade limite para janelas (km/h) |
| `SUNROOF_SPEED_THRESHOLD` | `sunroofSpeedThreshold` | Float | Velocidade limite para teto solar (km/h) |
| `NIGHT_START_HOUR` | `nightStartHour` | Int | Hora de início da noite |
| `NIGHT_START_MINUTE` | `nightStartMinute` | Int | Minuto de início da noite |
| `NIGHT_END_HOUR` | `nightEndHour` | Int | Hora de fim da noite |
| `NIGHT_END_MINUTE` | `nightEndMinute` | Int | Minuto de fim da noite |
| `ENABLE_AUTO_BRIGHTNESS` | `enableAutoBrightness` | Boolean | Brilho automático dia/noite |
| `AUTO_BRIGHTNESS_LEVEL_NIGHT` | `autoBrightnessLevelNight` | Int | Nível brilho noturno (1-10) |
| `AUTO_BRIGHTNESS_LEVEL_DAY` | `autoBrightnessLevelDay` | Int | Nível brilho diurno (1-10) |
| `ENABLE_FRIDA_HOOKS` | `enableFridaHooks` | Boolean | Ativar Frida hooks |
| `ENABLE_FRIDA_HOOK_SYSTEM_SERVER` | `enableFridaHookSystemServer` | Boolean | Ativar hook no system_server |
| `ENABLE_INSTRUMENT_PROJECTOR` | `enableInstrumentProjector` | Boolean | Habilitar dados no painel |
| `ENABLE_INSTRUMENT_REVISION_WARNING` | `enableInstrumentRevisionWarning` | Boolean | Aviso de revisão no painel |
| `ENABLE_INSTRUMENT_EV_BATTERY_PERCENTAGE` | `enableInstrumentEvBatteryPercentage` | Boolean | Bateria EV no painel |
| `ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION` | `enableInstrumentCustomMediaIntegration` | Boolean | Integração customizada de mídia |
| `ENABLE_CUSTOM_MENU` | `enableCustomMenu` | Boolean | Menu customizado no cluster |
| `INSTRUMENT_REVISION_KM` | `instrumentRevisionKm` | Int | Km da próxima revisão |
| `INSTRUMENT_REVISION_NEXT_DATE` | `instrumentRevisionNextDate` | Long | Data da próxima revisão (millis) |
| `DISABLE_AVAS` | `disableAvas` | Boolean | Desativar AVAS |
| `DISABLE_AVM_CAR_STOPPED` | `disableAvmCarStopped` | Boolean | Desativar câmera quando parado |
| `CAR_MONITOR_PROPERTIES` | `carMonitorProperties` | StringSet | Propriedades extras monitoradas |
| `BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK` | `bypassSelfInstallationIntegrityCheck` | Boolean | Bypass da verificação de UID |
| `SELF_INSTALLATION_INTEGRITY_CHECK` | `selfInstallationIntegrityCheck` | Boolean | Status da verificação |
| `ADVANCE_USE` | `advanceUse` | Boolean | Modo avançado (mostra Frida) |
| `CURRENT_USER` | `currentUser` | String | Perfil de usuário atual |
| `DISABLE_BLUETOOTH_ON_POWER_OFF` | `disableBluetoothOnPowerOff` | Boolean | Desligar BT ao desligar carro |
| `DISABLE_HOTSPOT_ON_POWER_OFF` | `disableHotspotOnPowerOff` | Boolean | Desligar hotspot ao desligar |
| `BLUETOOTH_STATE_ON_POWER_OFF` | `bluetoothStateOnPowerOff` | Boolean | BT estava ativo ao desligar (interno) |
| `ENABLE_SEAT_VENTILATION_ON_AC_ON` | `enableSeatVentilationOnAcOn` | Boolean | Ventilação do banco com A/C |
| `ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS` | `enableSteeringWheelCustomButtons` | Boolean | Botões personalizados no volante |
| `STEERING_WHEEL_CUSTOM_BUTON_1_ACTION` | `steeringWheelCustomButon1Action` | String | Ação do botão 1 |
| `STEERING_WHEEL_CUSTOM_BUTON_2_ACTION` | `steeringWheelCustomButon2Action` | String | Ação do botão 2 |
| `STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1` | `steeringWheelOpenAppPackageButton1` | String | Package do app (botão 1) |
| `STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2` | `steeringWheelOpenAppPackageButton2` | String | Package do app (botão 2) |
| `ENABLE_MAX_AC_ON_UNLOCK` | `enableMaxAcOnUnlock` | Boolean | Max AC ao ligar se quente |
| `MAX_AC_ON_UNLOCK_THRESHOLD` | `maxAcOnUnlockThreshold` | Float | Temperatura de ativação (°C) |
| `MAX_AC_TARGET_TEMP` | `maxAcTargetTemp` | Float | Temperatura alvo do Max AC (°C) |
| `MAX_AC_TIMEOUT` | `maxAcTimeout` | Int | Timeout do Max AC (minutos, 0=sem limite) |

## MainActivity - Tela de Configurações

**Arquivo:** `MainActivity.kt`

Interface construída com Jetpack Compose. Possui um menu lateral fixo e conteúdo principal.

### Estrutura de Tabs

| Tab | Composable | Descrição |
|-----|------------|-----------|
| Configurações | `BasicSettingsTab()` | Toggles e sliders de automações |
| Telas | `TelasTab()` | Configurações de projetores/displays |
| Valores Atuais | `CurrentValuesTab()` | Debug: mostra valores do veículo em tempo real |
| Instalar Apps | `InstallAppsTab()` | Download e instalação de APKs |
| Informações | `InformacoesTab()` | Informações do app e veículo |
| Frida Hooks | `FridaHooksTab()` | Configuração de scripts Frida (só visível com `advanceUse`) |

### Como os settings são criados

A tab "Configurações" (`BasicSettingsTab`) usa o componente `SettingCard` e a data class `SettingItem`:

```kotlin
data class SettingItem(
    val title: String,                           // Título exibido
    val description: String,                     // Descrição secundária
    val checked: Boolean,                        // Estado do toggle
    val onCheckedChange: (Boolean) -> Unit,      // Callback de mudança
    val enabled: Boolean = true,                 // Habilitado/desabilitado
    val sliderValue: Int? = null,                // Valor do slider (opcional)
    val sliderRange: IntRange? = null,           // Range do slider
    val sliderStep: Int? = null,                 // Step do slider
    val onSliderChange: ((Int) -> Unit)? = null,  // Callback do slider
    val sliderLabel: String? = null,             // Label do slider
    val hideSwitch: Boolean = false,             // Ocultar switch
    val customContent: (@Composable () -> Unit)? = null  // Conteúdo extra
)
```

Os settings são montados como uma lista de `SettingItem` e renderizados por `TwoColumnSettingsLayout()` em grid de 2 colunas.

### Fluxo: UI → SharedPreferences → Automação

```
1. Usuário toca no toggle
   │
   ▼
2. onCheckedChange é chamado
   │
   ├─ Atualiza estado local (remember/mutableStateOf)
   ├─ Salva em SharedPreferences: prefs.edit { putBoolean(key, value) }
   │
   └─ (opcional) Executa ação imediata:
      ├─ ServiceManager.getInstance().setMonitoringEnabled(!it)
      ├─ ServiceManager.getInstance().setAvasEnabled(!it)
      ├─ ServiceManager.getInstance().ensureSteeringWheelButtonIntegration()
      ├─ AutoBrightnessManager.getInstance().setEnabled(it)
      └─ etc.

3. No próximo boot, ServiceManager.initializeServices() lê as prefs e aplica
```

## Guia: Como Adicionar uma Nova Configuração

### Passo 1: Adicionar a chave em SharedPreferencesKeys

```kotlin
// Em SharedPreferencesKeys.kt
enum class SharedPreferencesKeys(val key: String, val description: String) {
    // ... existentes ...
    MINHA_NOVA_CONFIG("minhaNovaConfig", "Descrição da minha nova configuração"),
}
```

### Passo 2: Adicionar o toggle na UI (MainActivity)

Em `BasicSettingsTab()` dentro de `MainActivity.kt`:

```kotlin
// 1. Declarar estado local
var minhaConfig by remember {
    mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.MINHA_NOVA_CONFIG.key, false))
}

// 2. Adicionar à lista de settings
settingsList.add(
    SettingItem(
        title = "Minha Nova Configuração",
        description = SharedPreferencesKeys.MINHA_NOVA_CONFIG.description,
        checked = minhaConfig,
        onCheckedChange = {
            minhaConfig = it
            prefs.edit { putBoolean(SharedPreferencesKeys.MINHA_NOVA_CONFIG.key, it) }
            // Opcional: executar ação imediata
        }
    )
)
```

### Passo 3: Com slider (opcional)

```kotlin
var meuValor by remember {
    mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.MEU_VALOR.key, 50))
}

SettingItem(
    title = "Configuração com Slider",
    description = "Ajuste o valor",
    checked = minhaConfig,
    onCheckedChange = { /* toggle */ },
    sliderValue = meuValor,
    sliderRange = 0..100,
    onSliderChange = { newValue ->
        meuValor = newValue
        prefs.edit { putInt(SharedPreferencesKeys.MEU_VALOR.key, newValue) }
    },
    sliderLabel = "Valor: $meuValor"
)
```

### Passo 4: Com conteúdo customizado (opcional)

```kotlin
SettingItem(
    title = "Config com Dropdown",
    description = "Escolha uma opção",
    checked = enabled,
    onCheckedChange = { /* toggle */ },
    customContent = if (enabled) {
        {
            // Qualquer Composable aqui
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(...) { ... }
        }
    } else null
)
```

### Passo 5: Usar a configuração no ServiceManager

Em `ServiceManager.initializeServices()` ou em `OnDataChanged()`:

```java
// Ler na inicialização
boolean minhaConfig = sharedPreferences.getBoolean(
    SharedPreferencesKeys.MINHA_NOVA_CONFIG.getKey(), false
);
if (minhaConfig) {
    // Executar lógica
}

// Reagir a eventos
private void OnDataChanged(String key, String value) {
    if (key.equals(CarConstants.ALGUMA_COISA.getValue())) {
        boolean minhaConfig = sharedPreferences.getBoolean(
            SharedPreferencesKeys.MINHA_NOVA_CONFIG.getKey(), false
        );
        if (minhaConfig) {
            // Automação
        }
    }
}
```

### Passo 6: Ação imediata (opcional)

Se a configuração precisa ter efeito imediato quando o usuário muda (não só no próximo boot):

```kotlin
onCheckedChange = {
    minhaConfig = it
    prefs.edit { putBoolean(SharedPreferencesKeys.MINHA_NOVA_CONFIG.key, it) }
    // Ação imediata
    ServiceManager.getInstance().algumaAcao(it)
}
```
