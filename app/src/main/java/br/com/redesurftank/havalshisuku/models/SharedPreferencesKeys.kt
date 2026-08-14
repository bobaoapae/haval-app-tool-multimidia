package br.com.redesurftank.havalshisuku.models

enum class SharedPreferencesKeys(val key: String, val description: String) {
    DISABLE_MONITORING("disableMonitoring", "Manter desativado monitoramento de distrações"),
    CLOSE_WINDOW_ON_POWER_OFF("closeWindowOnPowerOff", "Fechar janela ao desligar o veículo"),
    CLOSE_WINDOW_ON_FOLD_MIRROR(
            "closeWindowOnFoldMirror",
            "Fechar janela ao recolher retrovisores"
    ),
    CLOSE_SUNROOF_ON_POWER_OFF("closeSunroofOnPowerOff", "Fechar teto solar ao desligar o veículo"),
    CLOSE_SUNROOF_ON_FOLD_MIRROR(
            "closeSunroofOnFoldMirror",
            "Fechar teto solar ao recolher retrovisores"
    ),
    CLOSE_WINDOWS_ON_SPEED("closeWindowsOnSpeed", "Fechar janelas ao atingir velocidade"),
    CLOSE_SUNROOF_ON_SPEED("closeSunroofOnSpeed", "Fechar teto solar ao atingir velocidade"),
    CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF(
            "closeSunroofSunShadeOnCloseSunRoof",
            "Fechar cortina do teto ao fechar teto solar"
    ),
    SET_STARTUP_VOLUME("setStartupVolume", "Definir volume ao ligar o veículo"),
    STARTUP_VOLUME("startupVolume", "Volume ao ligar o veículo"),
    SPEED_THRESHOLD("speedThreshold", "Velocidade limite para fechar janelas"),
    SUNROOF_SPEED_THRESHOLD("sunroofSpeedThreshold", "Velocidade limite para fechar teto solar"),
    NIGHT_START_HOUR("nightStartHour", "Hora de início da noite"),
    NIGHT_START_MINUTE("nightStartMinute", "Minuto de início da noite"),
    NIGHT_END_HOUR("nightEndHour", "Hora de fim da noite"),
    NIGHT_END_MINUTE("nightEndMinute", "Minuto de fim da noite"),
    ENABLE_AUTO_BRIGHTNESS("enableAutoBrightness", "Habilitar ajuste automático de brilho"),
    AUTO_BRIGHTNESS_LEVEL_NIGHT("autoBrightnessLevelNight", "Nível de brilho automático à noite"),
    AUTO_BRIGHTNESS_LEVEL_DAY("autoBrightnessLevelDay", "Nível de brilho automático durante o dia"),
    AUTO_BRIGHTNESS_USE_SUN("autoBrightnessUseSun", "Usar nascer/pôr do sol (transição suave) no brilho automático"),
    AUTO_BRIGHTNESS_STEP_MIN("autoBrightnessStepMin", "Minutos por degrau de 1 nível na transição de brilho (nascer/pôr do sol)"),
    AUTO_BRIGHTNESS_LAST_LAT("autoBrightnessLastLat", "Última latitude conhecida (cache p/ nascer/pôr do sol)"),
    AUTO_BRIGHTNESS_LAST_LON("autoBrightnessLastLon", "Última longitude conhecida (cache p/ nascer/pôr do sol)"),
    AUTO_BRIGHTNESS_CITY("autoBrightnessCity", "Cidade de referência (cache do GPS, p/ exibição)"),
    ENABLE_FRIDA_HOOKS("enableFridaHooks", "Habilitar hooks do Frida"),
    ENABLE_FRIDA_HOOK_SYSTEM_SERVER(
            "enableFridaHookSystemServer",
            "Habilitar hooks do Frida no System Server"
    ),
    ENABLE_INSTRUMENT_PROJECTOR(
            "enableInstrumentProjector",
            "Habilitar projeção de dados no painel de instrumentos"
    ),
    ENABLE_INSTRUMENT_ODOMETER(
            "enableInstrumentOdometer",
            "Exibir odômetro no painel de instrumentos"
    ),
    ENABLE_INSTRUMENT_REVISION_WARNING(
            "enableInstrumentRevisionWarning",
            "Habilitar aviso de revisão no painel de instrumentos"
    ),
    ENABLE_INSTRUMENT_ODOMETER_AND_REVISION(
            "enableOdometerAndRevision",
            "Exibir Odômetro e Aviso de Revisão no painel de instrumentos"
    ),
    ENABLE_INSTRUMENT_EV_BATTERY_PERCENTAGE(
            "enableInstrumentEvBatteryPercentage",
            "Habilitar porcentagem da bateria EV no painel de instrumentos"
    ),
    ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION(
            "enableInstrumentCustomMediaIntegration",
            "Habilitar integração personalizada de mídia no painel de instrumentos"
    ),
    ENABLE_CUSTOM_MENU(
            "enableCustomMenu",
            "Exibe um menu customizado no cluster controlado pelas teclas do volante"
    ),
    INSTRUMENT_REVISION_KM(
            "instrumentRevisionKm",
            "Quilometragem para aviso de revisão no painel de instrumentos"
    ),
    INSTRUMENT_REVISION_NEXT_DATE(
            "instrumentRevisionNextDate",
            "Data da próxima revisão no painel de instrumentos"
    ),
    DISABLE_AVAS("disableAvas", "Desativar AVAS (sistema de alerta de veículo silencioso)"),
    DISABLE_AVM_CAR_STOPPED(
            "disableAvmCarStopped",
            "Desativar camera AVM quando o carro está parado"
    ),
    // ===== Debloat: desativa apps do sistema (OEM) que rodam e consomem RAM/CPU da multimídia =====
    // Reversível (pm install-existing) e reaplicado no boot por ServiceManager.ensureDebloatedSystemApps().
    DISABLE_NATIVE_NAVIGATION("disableNativeNavigation", "Desativar navegador GPS nativo (Neusoft)"),
    DISABLE_NATIVE_VOICE("disableNativeVoice", "Desativar assistente de voz nativo (iFlyTek)"),
    DISABLE_NATIVE_WEATHER("disableNativeWeather", "Desativar serviço de previsão do tempo (OEM)"),
    // ===== Overlay flutuante de CPU/RAM =====
    ENABLE_RESOURCE_OVERLAY(
            "enableResourceOverlay",
            "Indicador flutuante de CPU e RAM por cima dos apps (some com a barra estendida aberta)"
    ),
    RESOURCE_OVERLAY_FONT_SP(
            "resourceOverlayFontSp",
            "Tamanho da fonte do indicador flutuante de CPU/RAM (sp)"
    ),
    RESOURCE_OVERLAY_CORNER(
            "resourceOverlayCorner",
            "Canto de ancoragem do indicador flutuante: 0=sup.esq 1=sup.dir 2=inf.esq 3=inf.dir"
    ),
    RESOURCE_OVERLAY_X(
            "resourceOverlayX",
            "Deslocamento horizontal (dp) do indicador flutuante a partir do canto escolhido"
    ),
    RESOURCE_OVERLAY_Y(
            "resourceOverlayY",
            "Deslocamento vertical (dp) do indicador flutuante a partir do canto escolhido"
    ),
    // ===== Deslocamento do Android Auto no cluster (contorna o crop do menu lateral) =====
    ENABLE_AA_CLUSTER_OFFSET("enableAaClusterOffset", "Habilitar deslocamento do Android Auto no cluster"),
    AA_CLUSTER_LEFT_OFFSET("aaClusterLeftOffset", "Deslocamento horizontal do Android Auto no cluster (px)"),
    CAR_MONITOR_PROPERTIES("carMonitorProperties", "Propriedades do monitoramento do carro"),
    BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK(
            "bypassSelfInstallationIntegrityCheck",
            "Ignorar verificação de integridade da instalação"
    ),
    SELF_INSTALLATION_INTEGRITY_CHECK(
            "selfInstallationIntegrityCheck",
            "Verificação de integridade da instalação"
    ),
    ADVANCE_USE("advanceUse", "Uso avançado"),
    CURRENT_USER("currentUser", "Usuário atual"),
    LAST_CLUSTER_AC_CONFIG(
            "lastClusterAcConfig",
            "Última configuração do ar-condicionado do cluster"
    ),
    DISABLE_BLUETOOTH_ON_POWER_OFF(
            "disableBluetoothOnPowerOff",
            "Desativar Bluetooth ao desligar o veículo"
    ),
    DISABLE_HOTSPOT_ON_POWER_OFF(
            "disableHotspotOnPowerOff",
            "Desativar ponto de acesso ao desligar o veículo"
    ),
    BLUETOOTH_STATE_ON_POWER_OFF(
            "bluetoothStateOnPowerOff",
            "Estado do Bluetooth ao desligar o veículo"
    ),
    HOTSPOT_STATE_ON_POWER_OFF(
            "hotspotStateOnPowerOff",
            "Estado do ponto de acesso ao desligar o veículo"
    ),
    DISABLE_BLUETOOTH_ON_FOLD_MIRROR(
            "disableBluetoothOnFoldMirror",
            "Desativar Bluetooth ao recolher retrovisores"
    ),
    DISABLE_HOTSPOT_ON_FOLD_MIRROR(
            "disableHotspotOnFoldMirror",
            "Desativar ponto de acesso ao recolher retrovisores"
    ),
    ENABLE_SEAT_VENTILATION_ON_AC_ON(
            "enableSeatVentilationOnAcOn",
            "Habilitar ventilação do banco do motorista ao ligar o ar-condicionado"
    ),
    ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS(
            "enableSteeringWheelCustomButtons",
            "Habilitar botões personalizados no volante"
    ),
    PERSISTENT_BOTTOM_BAR(
            "enable_persistent_bottom_bar",
            "Habilitar barra inferior persistente (Android 9)"
    ),
    STEERING_WHEEL_CUSTOM_BUTON_1_ACTION(
            "steeringWheelCustomButon1Action",
            "Ação do botão personalizado 1 do volante"
    ),
    STEERING_WHEEL_CUSTOM_BUTON_2_ACTION(
            "steeringWheelCustomButon2Action",
            "Ação do botão personalizado 2 do volante"
    ),
    STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_ORIGINAL(
            "steeringWheelCustomButon1ActionOriginal",
            "Ação original do botão personalizado 1 do volante"
    ),
    STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_ORIGINAL(
            "steeringWheelCustomButon2ActionOriginal",
            "Ação original do botão personalizado 2 do volante"
    ),
    STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1(
            "steeringWheelOpenAppPackageButton1",
            "Pacote do aplicativo para o botão personalizado 1 do volante"
    ),
    STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2(
            "steeringWheelOpenAppPackageButton2",
            "Pacote do aplicativo para o botão personalizado 2 do volante"
    ),
    STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_1(
            "steeringWheelClimateCommandButton1",
            "Comando do ar-condicionado para o botão personalizado 1 do volante"
    ),
    STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_2(
            "steeringWheelClimateCommandButton2",
            "Comando do ar-condicionado para o botão personalizado 2 do volante"
    ),
    STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_DOUBLE(
            "steeringWheelCustomButon1ActionDouble",
            "Ação do botão personalizado 1 do volante (toque duplo)"
    ),
    STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_DOUBLE(
            "steeringWheelCustomButon2ActionDouble",
            "Ação do botão personalizado 2 do volante (toque duplo)"
    ),
    STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_LONG(
            "steeringWheelCustomButon1ActionLong",
            "Ação do botão personalizado 1 do volante (toque longo)"
    ),
    STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_LONG(
            "steeringWheelCustomButon2ActionLong",
            "Ação do botão personalizado 2 do volante (toque longo)"
    ),
    STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_DOUBLE(
            "steeringWheelOpenAppPackageButton1Double",
            "Pacote do app para o botão 1 do volante (toque duplo)"
    ),
    STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_DOUBLE(
            "steeringWheelOpenAppPackageButton2Double",
            "Pacote do app para o botão 2 do volante (toque duplo)"
    ),
    STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_LONG(
            "steeringWheelOpenAppPackageButton1Long",
            "Pacote do app para o botão 1 do volante (toque longo)"
    ),
    STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_LONG(
            "steeringWheelOpenAppPackageButton2Long",
            "Pacote do app para o botão 2 do volante (toque longo)"
    ),
    LAST_CLUSTER_SCREEN("lastClusterScreen", "Última tela exibida no cluster"),
    LAST_CLUSTER_MENU_ITEM(
            "lastClusterMenuItem",
            "Último item selecionado no menu principal do cluster"
    ),
    ENABLE_MAX_AC_ON_UNLOCK(
            "enableMaxAcOnUnlock",
            "Habilitar A/C no máximo ao ligar o veículo se temperatura acima do configurado"
    ),
    MAX_AC_ON_UNLOCK_THRESHOLD(
            "maxAcOnUnlockThreshold",
            "Temperatura limite para ativar Max AC ao destravar"
    ),
    MAX_AC_TARGET_TEMP("maxAcTargetTemp", "Temperatura alvo para o Max AC"),
    MAX_AC_TIMEOUT("maxAcTimeout", "Tempo limite para desativar o Max AC (em minutos)"),
    ENABLE_OPEN_SUNROOF_CURTAIN_ON_START(
            "enableOpenSunroofCurtainOnStart",
            "Habilitar abertura da cortina do teto solar ao ligar"
    ),
    OPEN_SUNROOF_CURTAIN_START_HOUR(
            "openSunroofCurtainStartHour",
            "Hora de início para abrir cortina"
    ),
    OPEN_SUNROOF_CURTAIN_START_MINUTE(
            "openSunroofCurtainStartMinute",
            "Minuto de início para abrir cortina"
    ),
    OPEN_SUNROOF_CURTAIN_END_HOUR("openSunroofCurtainEndHour", "Hora fim para abrir cortina"),
    OPEN_SUNROOF_CURTAIN_END_MINUTE("openSunroofCurtainEndMinute", "Minuto fim para abrir cortina"),
    OPEN_SUNROOF_CURTAIN_MAX_TEMP(
            "openSunroofCurtainMaxTemp",
            "Temperatura externa máxima para abrir cortina"
    ),
    ENABLE_CLOSE_SUNROOF_CURTAIN_ON_TIME(
            "enableCloseSunroofCurtainOnTime",
            "Habilitar fechamento da cortina do teto solar por horário"
    ),
    CLOSE_SUNROOF_CURTAIN_START_HOUR("closeSunroofCurtainStartHour", "Hora de início para fechar cortina"),
    CLOSE_SUNROOF_CURTAIN_START_MINUTE("closeSunroofCurtainStartMinute", "Minuto de início para fechar cortina"),
    CLOSE_SUNROOF_CURTAIN_END_HOUR("closeSunroofCurtainEndHour", "Hora fim para fechar cortina"),
    CLOSE_SUNROOF_CURTAIN_END_MINUTE("closeSunroofCurtainEndMinute", "Minuto fim para fechar cortina"),
    PENDING_RESET_TARGET_VERSION(
            "pendingResetTargetVersion",
            "Versão alvo para resetar dados ao voltar para canal estável"
    ),
    SHOW_BETA_UPDATES("showBetaUpdates", "Mostrar atualizações do canal beta"),
    DISPLAY_APP_CONFIGS("displayAppConfigs", "Configurações de apps para telas secundárias"),
    ANDROID_SETTINGS_SHORTCUT_SEEDED(
            "androidSettingsShortcutSeeded",
            "Atalho das Configurações do Android já criado na área de trabalho padrão"
    ),
    ENABLE_VIRTUAL_CLUSTER(
            "enableVirtualCluster",
            "Habilitar Virtual Cluster no painel de instrumentos com a opção de temas diferenciados"
    ),
    VIRTUAL_CLUSTER_DISPLAY_ID(
            "instrumentMaskDisplayId",
            "ID da tela para o Virtual Cluster (Fixado em 3)"
    ),
    CURRENT_CLUSTER_TEMPLATE("currentClusterTemplate", "Template atual do cluster"),
    CURRENT_CLUSTER_DISPLAY("currentClusterDisplay", "Modo de exibição do cluster"),
    CURRENT_CLUSTER_COLOR("currentClusterColor", "Paleta de cores do cluster"),
    DEFAULT_DISPLAY_APP_PACKAGE(
            "defaultDisplayAppPackage",
            "Pacote do app padrão para abrir no cluster"
    ),
    INSTRUMENT_REVISION_HISTORY("instrumentRevisionHistory", "Histórico de revisões realizadas"),
    VIRTUAL_CLUSTER_THEME("virtualClusterTheme", "Tema do Virtual Cluster"),
    VIRTUAL_CLUSTER_NIGHT_MODE("virtualClusterNightMode", "Modo Noturno do Virtual Cluster"),
    PERSISTENT_BOTTOM_BAR_OVERSCAN(
            "persistentBottomBarOverscan",
            "Ajuste de margem inferior para a barra (overscan)"
    ),
    CLUSTER_FUEL_DISPLAY_UNIT("clusterFuelDisplayUnit", "Unidade de exibição do combustível no cluster"),
    CLUSTER_HIDE_SPEEDOMETER_ON_MAPS(
            "clusterHideSpeedometerOnMaps",
            "Ocultar velocímetro nos modos de mapa"
    ),
    CLUSTER_V2_TRIP_INFO(
            "clusterV2TripInfo",
            "Exibir dados de viagem e pressão dos pneus no Analógico V2"
    ),
    CUSTOM_THEME_REPO_URL_PROD("customThemeRepoUrlProd", "URL do Repositório de Temas (Prod)"),
    CUSTOM_THEME_REPO_URL_DEV("customThemeRepoUrlDev", "URL do Repositório de Temas (Dev)"),
    CUSTOM_THEME_REPO_ENV("customThemeRepoEnv", "Ambiente do Repositório (Prod/Dev)"),
    ACTIVE_CUSTOM_THEME("activeCustomTheme", "Tema Dinâmico Ativo"),
    THEME_RELOAD_NONCE("themeReloadNonce", "Nonce para recarregar WebView do tema"),
    BOTTOM_BAR_AUTO_HIDE("bottomBarAutoHide", "Esconder barra automaticamente após 30s"),
    HIDE_LEFT_NAV_PANE("hideLeftNavPane", "Manter o painel lateral esquerdo oculto"),
    BOTTOM_BAR_SWIPE_UP_ACTION("bottomBarSwipeUpAction", "Ação ao deslizar a barra para cima"),
    BOTTOM_BAR_SWIPE_UP_PACKAGE(
            "bottomBarSwipeUpPackage",
            "Pacote do app aberto ao deslizar a barra para cima"
    ),
    CLUSTER_PROJECTION_OPENS_DASHBOARD(
            "clusterProjectionOpensDashboard",
            "Abrir o Impulse Drive quando a projeção inicia no cluster"
    ),
    BOTTOM_BAR_OVERRIDES("bottomBarOverrides", "Overrides de aplicativos salvos (JSON)"),
    DASHBOARD_CARD_ORDER("dashboardCardOrder", "Ordem dos cards do dashboard"),
    ENABLE_SPEED_ADJUSTMENT("enableSpeedAdjustment", "Habilitar ajuste de velocidade no painel"),
    SPEED_ADJUSTMENT_OFFSET("speedAdjustmentOffset", "Fator de ajuste de velocidade (%)"),
    TRIP_CONSISTENCY_CLUSTER_ACTIVE("tripConsistencyClusterActive", "Indicador discreto de análise de viagem ativa no cluster"),
    TRIP_CONSISTENCY_CLUSTER_SCORE("tripConsistencyClusterScore", "Score de consistência em tempo real no cluster"),
    AA_PATCH_AUTO_MOUNT("aaPatchAutoMount", "Habilitar montagem automática dos patches do Android Auto ao iniciar"),
    CARPLAY_PATCH_AUTO_MOUNT("carPlayPatchAutoMount", "Habilitar montagem automática dos patches do CarPlay ao iniciar"),
    AMBIENT_LIGHT_BLE_ENABLED("ambientLightBleEnabled", "Ativar Ambient Light BLE"),
    AMBIENT_LIGHT_BLE_DEVICE_MAC("ambientLightBleDeviceMac", "MAC do dispositivo Ambient Light BLE"),
    AMBIENT_LIGHT_BLE_DEVICE_NAME("ambientLightBleDeviceName", "Nome do dispositivo Ambient Light BLE"),
    AMBIENT_LIGHT_SYNC_DRIVE_MODE(
            "ambientLightSyncDriveMode",
            "Sincronizar Ambient Light com modo de condução"
    ),
    AMBIENT_LIGHT_ANIMATIONS_ENABLED(
            "ambientLightAnimationsEnabled",
            "Ativar animações do Ambient Light"
    ),
    AMBIENT_LIGHT_MUSIC_ANIMATION_ENABLED(
            "ambientLightMusicAnimationEnabled",
            "Ativar animação do Ambient Light com música"
    ),
    AMBIENT_LIGHT_MUSIC_MODE(
            "ambientLightMusicMode",
            "Modo da animação do Ambient Light com música"
    ),
    AMBIENT_LIGHT_ALBUM_EFFECT(
            "ambientLightAlbumEffect",
            "Efeito da animação do Ambient Light com tons do álbum"
    ),
    AMBIENT_LIGHT_ALBUM_EFFECT_SPEED(
            "ambientLightAlbumEffectSpeed",
            "Velocidade do efeito do Ambient Light com tons do álbum"
    ),
    AMBIENT_LIGHT_ALBUM_EFFECT_OUTPUT(
            "ambientLightAlbumEffectOutput",
            "Saída do efeito do Ambient Light com tons do álbum"
    ),
    AMBIENT_LIGHT_ALBUM_BLE_MODE(
            "ambientLightAlbumBleMode",
            "Comportamento BLE do efeito do Ambient Light com tons do álbum"
    ),
    AMBIENT_LIGHT_ALBUM_DMX_MODE(
            "ambientLightAlbumDmxMode",
            "Comportamento DMX do efeito do Ambient Light com tons do álbum"
    ),
    AMBIENT_LIGHT_COLOR_ORDER(
            "ambientLightColorOrder",
            "Ordem dos canais de cor DMX do Ambient Light"
    ),
    AMBIENT_LIGHT_BLE_COLOR_ORDER(
            "ambientLightBleColorOrder",
            "Ordem dos canais de cor BLE do Ambient Light"
    ),
    AMBIENT_LIGHT_BRIGHTNESS_PERCENT(
            "ambientLightBrightnessPercent",
            "Brilho do Ambient Light"
    ),
    AMBIENT_LIGHT_OUTPUT(
            "ambientLightOutput",
            "Saída do Ambient Light"
    ),
    AMBIENT_LIGHT_AUTO_RECONNECT(
            "ambientLightAutoReconnect",
            "Reconectar automaticamente o Ambient Light"
    ),
    ENABLE_PERSIST_HEV_SOC_TARGET("enablePersistHevSocTarget", "Manter o % de bateria escolhido no HEV Prioritário"),
    HEV_SOC_TARGET_VALUE("hevSocTargetValue", "% de bateria a manter no HEV Prioritário (20-80)"),
    ENABLE_CUSTOM_BACKGROUND_D1("enableCustomBackgroundD1", "Habilitar fundo personalizado no Display 1"),
    CUSTOM_BACKGROUND_TYPE_D1("customBackgroundTypeD1", "Tipo de fundo do Display 1"),
    CUSTOM_BACKGROUND_VALUE_D1("customBackgroundValueD1", "Valor do fundo do Display 1"),
    AUTO_MOVE_PROJECTION_TO_CLUSTER(
            "autoMoveProjectionToCluster",
            "Mover AA/CarPlay para o cluster (D3) no auto-start — só se AA/CarPlay for o app inicial na tela Telas"
    ),
    ENABLE_HOT_ROUTER("enableHotRouter", "Roteia o hotspot pelo 4G ou WLAN (Starlink) quando disponível"),
    // ===== Controle de dados móveis do carro =====
    MOBILE_DATA_CONTROL_ENABLED("mobileDataControlEnabled", "Master: ativa o controle de dados móveis do carro"),
    BLOCK_CAR_MOBILE_DATA("blockCarMobileData", "Bloquear dados móveis do carro (usar só WiFi/Starlink)"),
    MOBILE_DATA_AUTOBLOCK("mobileDataAutoblock", "Bloquear dados móveis automaticamente perto do limite"),
    MOBILE_DATA_AUTOBLOCK_CAP_MB("mobileDataAutoblockCapMb", "Teto de dados da multimídia p/ auto-bloqueio (MB)"),
    MOBILE_DATA_BLOCK_ON_WIFI("mobileDataBlockOnWifi", "Bloquear dados móveis quando conectado ao WiFi"),
    MOBILE_DATA_BLOCK_ON_PROJECTION("mobileDataBlockOnProjection", "Bloquear dados móveis quando no Android Auto/CarPlay"),
    MOBILE_DATA_CYCLE_DAY("mobileDataCycleDay", "Dia de renovação do pacote de dados (1-31)"),
    BLOCK_DATATRACK_TELEMETRY("blockDatatrackTelemetry", "Congelar telemetria OEM DataTrack (envio pra nuvem)"),
    MOBILE_DATA_DISABLED_BY_APP("mobileDataDisabledByApp", "Controle interno: 4G desabilitado por este app"),
    DATATRACK_DISABLED_BY_APP("datatrackDisabledByApp", "Controle interno: DataTrack desabilitado por este app"),
    MOBILE_DATA_TRAFFIC_ACCUM_BYTES("mobileDataTrafficAccumBytes", "Acumulado de bytes móveis no ciclo (fallback TrafficStats)"),
    MOBILE_DATA_TRAFFIC_LAST_READING("mobileDataTrafficLastReading", "Última leitura do TrafficStats móvel (controle interno)"),
    MOBILE_DATA_TRAFFIC_CYCLE_TAG("mobileDataTrafficCycleTag", "Ciclo atual do acumulador de bytes (controle interno)")
}
