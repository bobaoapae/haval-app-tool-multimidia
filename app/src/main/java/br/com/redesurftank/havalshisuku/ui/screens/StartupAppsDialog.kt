package br.com.redesurftank.havalshisuku.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.managers.StartupAppManager
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.theme.ImpulseTextSizes

/**
 * "Abrir ao ligar": escolhe qual app abre em qual tela quando o carro liga. Até dois — um na tela
 * principal e um na secundária.
 *
 * As duas metades usam caminhos diferentes por baixo, e é por isso que elas existem separadas:
 *
 *  * **Tela principal (0)** — o launcher de fábrica disputa a tela e esta ROM não deixa trocar o
 *    app de HOME, então [StartupAppManager] abre o app por cima com uma escada de tentativas e
 *    desiste se o motorista abrir outra coisa antes.
 *  * **Tela secundária (1 ou 3)** — não há disputa: o app é lançado direto a partir da config
 *    guardada (`DisplayAppConfig`), pelo mesmo caminho que o cluster já usava.
 *
 * Android Auto e CarPlay não aparecem aqui como "abrir ao ligar" porque já sobem sozinhos quando o
 * telefone conecta; escolher um deles aqui só faria sentido para deixá-lo fixo na tela.
 */
@Composable
fun StartupAppsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    }

    var mainApp by remember { mutableStateOf(StartupAppManager.mainDisplayPackage()) }
    var secondaryApp by remember {
        mutableStateOf(
            prefs.getString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, "").orEmpty()
        )
    }
    // A tela da secundária vem da própria config do app; 3 (cluster) é o padrão histórico.
    var secondaryDisplay by remember {
        mutableStateOf(
            DisplayAppLauncher.getAllConfigs()
                .firstOrNull { it.packageName == secondaryApp }
                ?.displayId
                ?: 3
        )
    }

    fun saveSecondary(pkg: String, displayId: Int) {
        prefs.edit { putString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, pkg) }
        if (pkg.isEmpty()) return
        // Garante que existe uma config para o app e grava a tela escolhida nela: é dela que o
        // lançamento no boot tira displayId, posição e tamanho.
        DisplayAppLauncher.getOrCreateDefaultConfig(context, pkg, save = false)?.let { config ->
            DisplayAppLauncher.saveConfig(config.copy(displayId = displayId))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ImpTokens.Container,
        title = { Text("Abrir ao ligar", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    "Escolha até dois apps: um para a tela principal e um para a secundária. " +
                        "Android Auto e CarPlay já abrem sozinhos quando o telefone conecta.",
                    color = ImpTokens.TextSecondary,
                    fontSize = ImpulseTextSizes.BodyCompact
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppSelectorField(
                        packageName = mainApp,
                        onPackageSelected = {
                            mainApp = it
                            StartupAppManager.setMainDisplayPackage(it)
                        },
                        label = "Tela principal"
                    )
                    if (mainApp.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    mainApp = ""
                                    StartupAppManager.setMainDisplayPackage("")
                                }
                            ) { Text("Não abrir nada", color = ImpTokens.TextSecondary) }
                            TextButton(onClick = { StartupAppManager.launchNow() }) {
                                Text("Abrir agora", color = ImpTokens.Accent)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppSelectorField(
                        packageName = secondaryApp,
                        onPackageSelected = {
                            secondaryApp = it
                            saveSecondary(it, secondaryDisplay)
                        },
                        label = "Tela secundária"
                    )
                    if (secondaryApp.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1 to "Atras do Cluster", 3 to "Na frente do cluster").forEach { (id, label) ->
                                val selected = secondaryDisplay == id
                                Box(
                                    selected = selected,
                                    label = label,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    secondaryDisplay = id
                                    saveSecondary(secondaryApp, id)
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                secondaryApp = ""
                                saveSecondary("", secondaryDisplay)
                            }
                        ) { Text("Não abrir nada", color = ImpTokens.TextSecondary) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Pronto") } }
    )
}

/** Botão de escolha de tela, no mesmo visual dos seletores da aba Telas. */
@Composable
private fun Box(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .height(40.dp)
            .background(
                if (selected) ImpTokens.Accent else ImpTokens.Container,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else ImpTokens.TextSecondary,
            fontSize = ImpulseTextSizes.BodyCompact,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
