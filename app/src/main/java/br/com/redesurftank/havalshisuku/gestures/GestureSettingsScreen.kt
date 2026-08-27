package br.com.redesurftank.havalshisuku.gestures

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Action
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Axis
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Zone
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.components.StyledCard
import br.com.redesurftank.havalshisuku.ui.theme.Michroma

/**
 * Tela de "Gestos na tela", dentro de Recursos.
 *
 * Fica aqui, e nao em Configuracoes, porque a configuracao e uma GRADE — dedos x eixo x zona — e
 * nao um interruptor. A propria central de Recursos declara essa regra: funcao nova entra como card
 * ali, nao como item solto.
 *
 * A tela sempre trabalha em TERCOS. Nao existe escolha separada de "layout" (tela toda / metades /
 * tercos) porque, podendo repetir a mesma acao, tercos ja e superconjunto de todas: a mesma acao nas
 * tres zonas E "tela toda". Uma escolha a menos pro dono, um estado a menos pra guardar.
 */
@Composable
fun GestureSettingsScreen(onBackToFeatures: () -> Unit) {
    val prefs =
        App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    var enabled by remember {
        mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_SCREEN_GESTURES.key, false))
    }
    var bindings by remember { mutableStateOf(ScreenGestureManager.loadBindings()) }

    fun apply(next: GestureBindings) {
        bindings = next
        ScreenGestureManager.saveBindings(next)
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(AppColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(46.dp).clickable(onClick = onBackToFeatures),
                shape = RoundedCornerShape(14.dp),
                color = ImpTokens.Container,
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Voltar",
                    tint = ImpTokens.Accent,
                    modifier = Modifier.padding(11.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Gestos na tela",
                    color = AppColors.TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Arraste com vários dedos, por cima de qualquer app.",
                    color = AppColors.TextSecondary,
                    fontSize = 16.sp,
                )
            }
        }

        StyledCard {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Gestos ativos",
                            color = AppColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "O toque não é tirado do app que está na frente — ele continua recebendo o arraste normalmente.",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            prefs.edit {
                                putBoolean(SharedPreferencesKeys.ENABLE_SCREEN_GESTURES.key, it)
                            }
                            ScreenGestureManager.setEnabled(it)
                        },
                    )
                }
                Text(
                    "Para cima ou para a direita aumenta. A zona é a parte da tela onde o arraste COMEÇA.",
                    color = ImpTokens.TextMuted,
                    fontSize = 13.sp,
                )
            }
        }

        SensitivityCard()

        for (fingers in GestureBindings.FINGER_COUNTS) {
            FingerCountCard(
                fingers = fingers,
                bindings = bindings,
                onChange = { apply(it) },
            )
        }

        StyledCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Restaurar o padrão",
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Volta para três dedos: temperatura nas laterais, volume no centro e ventilação na horizontal.",
                        color = AppColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
                TextChip("Restaurar") { apply(GestureBindings.DEFAULT) }
            }
        }
    }
}

/**
 * Sensibilidade de cada ajuste.
 *
 * O slider NAO expõe a grandeza interna (distancia por passo, em altura de tela) — expõe o que a
 * pessoa realmente quer decidir: **quanto muda num arraste de meia tela**. A conversao fica aqui.
 *
 * A ventilacao e horizontal, entao "meia tela" pra ela e meia LARGURA; por isso entra a proporcao
 * da tela na conta, senao o numero mostrado mentiria numa central esticada como esta (1920x720).
 */
@Composable
private fun SensitivityCard() {
    val configuration = LocalConfiguration.current
    val aspect =
        if (configuration.screenHeightDp > 0) {
            configuration.screenWidthDp.toFloat() / configuration.screenHeightDp
        } else 1f

    var volumePerHalf by remember {
        mutableStateOf(
            perHalfScreen(SharedPreferencesKeys.SCREEN_GESTURE_STEP_VOLUME, 0.030f, 1f)
        )
    }
    var degreesPerHalf by remember {
        mutableStateOf(
            perHalfScreen(SharedPreferencesKeys.SCREEN_GESTURE_STEP_TEMPERATURE, 0.075f, 0.5f)
        )
    }
    var fanPerHalf by remember {
        mutableStateOf(
            perHalfScreen(SharedPreferencesKeys.SCREEN_GESTURE_STEP_FAN, 0.22f, 1f, aspect)
        )
    }

    StyledCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Sensibilidade",
                color = AppColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Quanto cada ajuste muda num arraste de meia tela.",
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
            )

            SensitivitySlider(
                label = "Volume",
                value = volumePerHalf,
                range = 3f..40f,
                readout = "${volumePerHalf.toInt()} pontos",
                onChange = { volumePerHalf = it },
                onCommit = {
                    ScreenGestureManager.saveStepSensitivity(
                        SharedPreferencesKeys.SCREEN_GESTURE_STEP_VOLUME,
                        stepThousandths(volumePerHalf, 1f),
                    )
                },
            )
            SensitivitySlider(
                label = "Temperatura",
                value = degreesPerHalf,
                range = 1f..12f,
                readout = "${degreesPerHalf.toInt()} °C",
                onChange = { degreesPerHalf = it },
                onCommit = {
                    ScreenGestureManager.saveStepSensitivity(
                        SharedPreferencesKeys.SCREEN_GESTURE_STEP_TEMPERATURE,
                        stepThousandths(degreesPerHalf, 0.5f),
                    )
                },
            )
            SensitivitySlider(
                label = "Ventilação",
                value = fanPerHalf,
                range = 1f..8f,
                readout = "${fanPerHalf.toInt()} posições",
                onChange = { fanPerHalf = it },
                onCommit = {
                    ScreenGestureManager.saveStepSensitivity(
                        SharedPreferencesKeys.SCREEN_GESTURE_STEP_FAN,
                        stepThousandths(fanPerHalf, 1f, aspect),
                    )
                },
            )
        }
    }
}

@Composable
private fun SensitivitySlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AppColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("meia tela ≈ $readout", color = ImpTokens.Accent, fontSize = 15.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            // Grava so ao SOLTAR: enquanto arrasta, cada quadro do slider reconstruiria o
            // reconhecedor e gravaria a preferencia sem necessidade.
            onValueChangeFinished = onCommit,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
        )
    }
}

private fun perHalfScreen(
    key: SharedPreferencesKeys,
    fallbackStep: Float,
    unitPerStep: Float,
    aspect: Float = 1f,
): Float =
    GestureSensitivity.perHalfScreen(
        ScreenGestureManager.stepSensitivity(key, fallbackStep),
        unitPerStep,
        aspect,
    )

private fun stepThousandths(perHalf: Float, unitPerStep: Float, aspect: Float = 1f): Int =
    GestureSensitivity.stepThousandths(perHalf, unitPerStep, aspect)

@Composable
private fun FingerCountCard(
    fingers: Int,
    bindings: GestureBindings,
    onChange: (GestureBindings) -> Unit,
) {
    // O aviso existe porque a opcao tem custo REAL, e o dono precisa saber por que ficou estranho
    // depois — nao porque a gente queira desencorajar.
    val warning =
        when (fingers) {
            4 -> "Com três dedos, o quarto CANCELA o gesto — é o que impede a mão apoiada de virar comando. Usar quatro enfraquece essa proteção."
            else -> null
        }

    StyledCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$fingers dedos",
                    color = AppColors.TextPrimary,
                    fontSize = 20.sp,
                    fontFamily = Michroma,
                )
                Spacer(Modifier.width(12.dp))
                if (warning != null) {
                    Badge("atenção", ImpTokens.Attention)
                } else {
                    Badge("recomendado", ImpTokens.Accent)
                }
            }
            if (warning != null) {
                Text(warning, color = ImpTokens.TextMuted, fontSize = 13.sp)
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(132.dp))
                for (label in listOf("esquerda", "centro", "direita")) {
                    Text(
                        label,
                        color = ImpTokens.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.width(104.dp))
            }

            AxisRow(fingers, Axis.VERTICAL, "↕  vertical", bindings, onChange)
            AxisRow(fingers, Axis.HORIZONTAL, "↔  horizontal", bindings, onChange)
        }
    }
}

@Composable
private fun AxisRow(
    fingers: Int,
    axis: Axis,
    label: String,
    bindings: GestureBindings,
    onChange: (GestureBindings) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = AppColors.TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.width(132.dp),
        )
        for (zone in Zone.entries) {
            Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                ActionPicker(bindings.actionFor(fingers, axis, zone)) { action ->
                    onChange(bindings.with(fingers, axis, zone, action))
                }
            }
        }
        // "Tela toda" nao e um layout separado: e preencher as tres zonas com a mesma acao.
        Box(modifier = Modifier.width(104.dp)) {
            ActionPicker(null, placeholder = "tela toda") { action ->
                onChange(bindings.withWholeRow(fingers, axis, action))
            }
        }
    }
}

@Composable
private fun ActionPicker(
    current: Action?,
    placeholder: String? = null,
    onPick: (Action?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = ImpTokens.Ground,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    placeholder ?: labelOf(current),
                    color = if (current == null) ImpTokens.TextMuted else AppColors.TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = ImpTokens.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(ImpTokens.Container),
        ) {
            for (option in listOf(null) + Action.entries) {
                DropdownMenuItem(
                    text = {
                        Text(
                            labelOf(option),
                            color =
                                if (option == current) ImpTokens.Accent else AppColors.TextPrimary,
                            fontSize = 15.sp,
                        )
                    },
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, tint: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = tint.copy(alpha = 0.16f)) {
        Text(
            text,
            color = tint,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TextChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = ImpTokens.Accent.copy(alpha = 0.16f),
    ) {
        Text(
            text,
            color = ImpTokens.Accent,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

private fun labelOf(action: Action?): String =
    when (action) {
        null -> "nenhuma"
        Action.VOLUME -> "volume"
        Action.DRIVER_TEMP -> "temp. motorista"
        Action.PASSENGER_TEMP -> "temp. passageiro"
        Action.FAN -> "ventilação"
    }
