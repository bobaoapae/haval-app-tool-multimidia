package br.com.redesurftank.havalshisuku.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.StartupAppManager
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens

/**
 * Oferecido UMA vez, logo depois de o Impulse Home ser instalado: as três coisas que o deixam
 * utilizável de verdade e que, soltas pelas telas de ajuste, ninguém encontraria.
 *
 * Tudo vem marcado, mas nada é aplicado sem o "Aplicar" — instalar um app não é permissão para
 * mudar como o carro se comporta ao ligar. Quem fechar no "Agora não" não vê de novo; as mesmas
 * opções continuam na aba de Apps e em Configurações.
 */
@Composable
fun ImpulseHomeSetupDialog(onDismiss: () -> Unit) {
    val prefs = remember {
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    }
    val barAlreadyOn = remember {
        prefs.getBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, false)
    }

    var openOnBoot by remember { mutableStateOf(true) }
    var enableBar by remember { mutableStateOf(!barAlreadyOn) }
    var swipeOpensHome by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ImpTokens.Container,
        title = {
            Text("Impulse Home instalado", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(
                    painter = painterResource(R.drawable.impulse_home_preview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(10.dp))
                )
                Text(
                    "Quer deixar ele à mão? Dá para mudar depois na aba de Apps.",
                    color = ImpTokens.TextSecondary,
                    fontSize = 12.sp
                )

                SetupOption(
                    checked = openOnBoot,
                    onCheckedChange = { openOnBoot = it },
                    title = "Abrir ao ligar o carro",
                    detail = "Na tela principal, assim que o carro liga"
                )
                if (!barAlreadyOn) {
                    SetupOption(
                        checked = enableBar,
                        onCheckedChange = { enableBar = it },
                        title = "Ativar a barra inferior",
                        detail = "A barra do Impulse que fica sempre visível embaixo"
                    )
                }
                SetupOption(
                    checked = swipeOpensHome,
                    onCheckedChange = { swipeOpensHome = it },
                    title = "Deslizar a barra para cima abre o Impulse Home",
                    detail =
                        if (barAlreadyOn) "Substitui a ação atual da barra"
                        else "Só funciona com a barra inferior ativada"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (openOnBoot) StartupAppManager.setMainDisplayPackage(IMPULSE_HOME_PACKAGE)
                    if (enableBar && !barAlreadyOn) {
                        prefs.edit {
                            putBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, true)
                        }
                    }
                    if (swipeOpensHome) {
                        prefs.edit {
                            putString(
                                SharedPreferencesKeys.BOTTOM_BAR_SWIPE_UP_ACTION.key,
                                BottomBarState.SwipeUpAction.CUSTOM_APP.key
                            )
                            putString(
                                SharedPreferencesKeys.BOTTOM_BAR_SWIPE_UP_PACKAGE.key,
                                IMPULSE_HOME_PACKAGE
                            )
                        }
                    }
                    onDismiss()
                }
            ) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Agora não", color = ImpTokens.TextSecondary)
            }
        }
    )
}

@Composable
private fun SetupOption(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = ImpTokens.Accent)
        )
        Column {
            Text(title, color = Color.White, fontSize = 13.sp)
            Text(detail, color = ImpTokens.TextSecondary, fontSize = 11.sp)
        }
    }
}
