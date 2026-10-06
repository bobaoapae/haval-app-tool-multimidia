package br.com.redesurftank.havalshisuku.ui.screens

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.havalshisuku.managers.MobileDataManager
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.SettingItem
import br.com.redesurftank.havalshisuku.ui.components.SettingsGroups

private const val SELECTED_CORNER_BACKGROUND_ALPHA = 0.22f

/**
 * Itens da subaba "Performance" (último grupo de Configurações, depois de "Recursos & integração"):
 * debloat de apps do sistema (OEM), bloqueio da telemetria DataTrack e o indicador flutuante de
 * CPU/RAM. Devolvidos como List<SettingItem> pra BasicSettingsTab juntar no settingsList — todos no
 * grupo SettingsGroups.PERFORMANCE. Mantém o bloco (com o customContent do overlay) fora do arquivo
 * gigante de Configurações.
 */
@Composable
fun performanceSettingItems(prefs: SharedPreferences): List<SettingItem> {
    // Modo avançado (5 cliques na versão — mesmo gate da aba Frida): esconde o overlay diagnóstico.
    val advancedUse = prefs.getBoolean(SharedPreferencesKeys.ADVANCE_USE.key, false)
    // --- Debloat: desativar apps do sistema (OEM) que rodam e consomem RAM/CPU (default OFF) ---
    // O toggle lê o estado real na 1ª vez (ServiceManager.reconcileDebloatPref): se o pacote já
    // está desativado por fora, nasce ON. Reversível e reaplicado no boot.
    var disableNativeNavigation by remember {
        mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.DISABLE_NATIVE_NAVIGATION.key, false))
    }
    var disableNativeVoice by remember {
        mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.DISABLE_NATIVE_VOICE.key, false))
    }
    var disableNativeWeather by remember {
        mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.DISABLE_NATIVE_WEATHER.key, false))
    }
    var blockDatatrack by remember { mutableStateOf(MobileDataManager.isDatatrackBlocked()) }

    // --- Monitor de CPU/RAM flutuante (default OFF: é poll permanente + janela extra) ---
    var enableResourceOverlay by remember {
        mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_RESOURCE_OVERLAY.key, false))
    }
    var overlayFontSp by remember {
        mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.RESOURCE_OVERLAY_FONT_SP.key, 14))
    }
    var overlayCorner by remember {
        mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.RESOURCE_OVERLAY_CORNER.key, 3))
    }
    var overlayX by remember {
        mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.RESOURCE_OVERLAY_X.key, 12))
    }
    var overlayY by remember {
        mutableIntStateOf(prefs.getInt(SharedPreferencesKeys.RESOURCE_OVERLAY_Y.key, 90))
    }

    val items = mutableListOf(
        SettingItem(
            title = "Desativar navegador GPS nativo (Neusoft)",
            description =
                "Remove pro usuário o app de navegação nativo (com.neusoft.na.navigation), que fica rodando e consome RAM/CPU da multimídia. Não afeta Android Auto / CarPlay / Waze. Reversível e reaplicado no boot.",
            group = SettingsGroups.PERFORMANCE,
            checked = disableNativeNavigation,
            onCheckedChange = {
                disableNativeNavigation = it
                prefs.edit().putBoolean(SharedPreferencesKeys.DISABLE_NATIVE_NAVIGATION.key, it).apply()
                ServiceManager.getInstance().ensureDebloatedSystemApps()
            }
        ),
        SettingItem(
            title = "Desativar assistente de voz nativo (iFlyTek)",
            description =
                "Remove pro usuário o assistente de voz nativo (com.iflytek.cutefly.speechclient.hmi + com.beantechs.voiceclient), que fica rodando e consome RAM/CPU. Você perde o comando de voz OEM (\"Olá Haval\"). Reversível e reaplicado no boot.",
            group = SettingsGroups.PERFORMANCE,
            checked = disableNativeVoice,
            onCheckedChange = {
                disableNativeVoice = it
                prefs.edit().putBoolean(SharedPreferencesKeys.DISABLE_NATIVE_VOICE.key, it).apply()
                ServiceManager.getInstance().ensureDebloatedSystemApps()
            }
        ),
        SettingItem(
            title = "Desativar previsão do tempo (OEM)",
            description =
                "Remove pro usuário o serviço de previsão do tempo (com.beantechs.weatherservice), que fica rodando e consome RAM/CPU. Reversível e reaplicado no boot.",
            group = SettingsGroups.PERFORMANCE,
            checked = disableNativeWeather,
            onCheckedChange = {
                disableNativeWeather = it
                prefs.edit().putBoolean(SharedPreferencesKeys.DISABLE_NATIVE_WEATHER.key, it).apply()
                ServiceManager.getInstance().ensureDebloatedSystemApps()
            }
        ),
        SettingItem(
            title = "Bloquear telemetria (DataTrack → nuvem)",
            description =
                "Congela o serviço OEM que manda telemetria pra nuvem (com.beantechs.datatrackservice). Reversível; não mexe no comando remoto. Não derruba o gasto do TBOX na fatura — ajuda na privacidade e no WiFi/Starlink.",
            group = SettingsGroups.PERFORMANCE,
            checked = blockDatatrack,
            onCheckedChange = {
                blockDatatrack = it
                MobileDataManager.setDatatrackBlocked(it)
            }
        )
    )

    // Overlay CPU/RAM: só no modo avançado (poll permanente + janela extra = ferramenta diagnóstica).
    if (advancedUse) items.add(
        SettingItem(
            title = "Indicador de CPU e RAM flutuante",
            description =
                "Mostra o uso de CPU e RAM da multimídia num quadradinho no canto superior, por cima de qualquer app. Some sozinho quando você abre a barra estendida (lá o dado já aparece no card de dinâmica). Começa desligado: enquanto está ligado, o app faz uma leitura a cada 2,5s.",
            group = SettingsGroups.PERFORMANCE,
            checked = enableResourceOverlay,
            onCheckedChange = {
                enableResourceOverlay = it
                prefs.edit {
                    putBoolean(SharedPreferencesKeys.ENABLE_RESOURCE_OVERLAY.key, it)
                }
                // Espelho observavel: faz o overlay aparecer/sumir NA HORA.
                br.com.redesurftank.havalshisuku.models.BottomBarState
                    .resourceOverlayEnabled = it
            },
            customContent = {
                if (enableResourceOverlay) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            "Canto: " +
                                when (overlayCorner) {
                                    0 -> "superior esquerdo"
                                    1 -> "superior direito"
                                    2 -> "inferior esquerdo"
                                    else -> "inferior direito"
                                },
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                    Triple(0, "↖", "Canto superior esquerdo"),
                                    Triple(1, "↗", "Canto superior direito"),
                                    Triple(2, "↙", "Canto inferior esquerdo"),
                                    Triple(3, "↘", "Canto inferior direito")
                                )
                                .forEach { (idx, label, accessibleLabel) ->
                                    val sel = overlayCorner == idx
                                    Surface(
                                        onClick = {
                                            overlayCorner = idx
                                            prefs.edit {
                                                putInt(SharedPreferencesKeys.RESOURCE_OVERLAY_CORNER.key, idx)
                                            }
                                            br.com.redesurftank.havalshisuku.models.BottomBarState
                                                .resourceOverlayCorner = idx
                                        },
                                        color =
                                            if (sel) {
                                                AppColors.Primary.copy(
                                                    alpha = SELECTED_CORNER_BACKGROUND_ALPHA
                                                )
                                            }
                                            else AppColors.CardBackground,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier =
                                            Modifier.weight(1f).semantics {
                                                contentDescription = accessibleLabel
                                                selected = sel
                                            }
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            textAlign = TextAlign.Center,
                                            color =
                                                if (sel) AppColors.Primary
                                                else AppColors.TextSecondary,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                        }
                        Text(
                            "Tamanho da fonte: $overlayFontSp sp",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Slider(
                            value = overlayFontSp.toFloat(),
                            onValueChange = {
                                overlayFontSp = it.toInt()
                                br.com.redesurftank.havalshisuku.models.BottomBarState
                                    .resourceOverlayFontSp = overlayFontSp
                            },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putInt(SharedPreferencesKeys.RESOURCE_OVERLAY_FONT_SP.key, overlayFontSp)
                                }
                            },
                            valueRange = 10f..30f,
                            steps = 19
                        )
                        Text(
                            "Distância da borda lateral: $overlayX dp",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                        Slider(
                            value = overlayX.toFloat(),
                            onValueChange = {
                                overlayX = it.toInt()
                                br.com.redesurftank.havalshisuku.models.BottomBarState
                                    .resourceOverlayX = overlayX
                            },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putInt(SharedPreferencesKeys.RESOURCE_OVERLAY_X.key, overlayX)
                                }
                            },
                            valueRange = 0f..400f
                        )
                        Text(
                            "Distância da borda de cima/baixo: $overlayY dp",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                        Slider(
                            value = overlayY.toFloat(),
                            onValueChange = {
                                overlayY = it.toInt()
                                br.com.redesurftank.havalshisuku.models.BottomBarState
                                    .resourceOverlayY = overlayY
                            },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putInt(SharedPreferencesKeys.RESOURCE_OVERLAY_Y.key, overlayY)
                                }
                            },
                            valueRange = 0f..300f
                        )
                        Text(
                            "Ajuste os controles acima para reposicionar ao vivo. Feche a barra estendida para visualizar o indicador.",
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        )
    )

    return items
}
