package br.com.redesurftank.havalshisuku.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.rememberCoroutineScope
import br.com.redesurftank.havalshisuku.utils.ViewerFirstRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.managers.StartupAppManager
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.services.BottomBarService
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens

/**
 * Oferecido logo depois de o Impulse Launcher ser instalado: opções essenciais de inicialização,
 * barra inferior persistente e gesto de deslizar para cima.
 */
@Composable
fun ImpulseHomeSetupDialog(canOpen: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    }
    val barAlreadyOn = remember {
        prefs.getBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, false)
    }

    var openOnBoot by remember { mutableStateOf(true) }
    var enableBarAndSwipe by remember { mutableStateOf(true) }
    var applying by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun applyChoices(openAfter: Boolean) {
        if (applying) return
        applying = true
        opening = openAfter
        if (openOnBoot) StartupAppManager.setMainDisplayPackage(IMPULSE_HOME_PACKAGE)
        val shouldEnableBar = if (barAlreadyOn) true else enableBarAndSwipe
        val shouldEnableSwipe = enableBarAndSwipe

        prefs.edit {
            putBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, shouldEnableBar)
            if (shouldEnableSwipe) {
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
        runCatching {
            if (shouldEnableBar) {
                context.startService(Intent(context, BottomBarService::class.java))
            } else {
                context.stopService(Intent(context, BottomBarService::class.java))
            }
        }
        scope.launch(Dispatchers.IO) {
            if (openAfter) ViewerFirstRun.prepare(context, IMPULSE_HOME_PACKAGE)
            withContext(Dispatchers.Main) {
                if (openAfter) {
                    context.packageManager.getLaunchIntentForPackage(IMPULSE_HOME_PACKAGE)?.let { launch ->
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launch)
                    }
                }
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!canOpen && !applying) onDismiss() },
        containerColor = ImpTokens.Container,
        title = {
            Text("Impulse Launcher instalado", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Demonstração visual dinâmica da Barra Inferior com gesto de arrastar
                SwipeUpTutorial(
                    enableBar = if (barAlreadyOn) true else enableBarAndSwipe,
                    enableSwipe = enableBarAndSwipe
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

                SetupOption(
                    checked = enableBarAndSwipe,
                    onCheckedChange = { enableBarAndSwipe = it },
                    title =
                        if (barAlreadyOn) "Deslizar a barra para cima abre o app"
                        else "Ativar barra inferior e gesto para abrir",
                    detail =
                        if (barAlreadyOn)
                            "A barra inferior já está ativa. Arraste-a para cima para abrir o Impulse Launcher rapidamente"
                        else
                            "Exibe a barra na base da tela e permite arrastá-la para cima para abrir o app"
                )
            }
        },
        confirmButton = {
            Button(enabled = !applying, onClick = { applyChoices(canOpen) }) {
                Text(
                        when {
                            canOpen && opening -> "Abrindo..."
                            canOpen -> "Salvar e abrir"
                            applying -> "Salvando..."
                            else -> "Salvar"
                        }
                )
            }
        },
        dismissButton = {
            if (canOpen) {
                TextButton(enabled = !applying, onClick = { applyChoices(false) }) {
                    Text(if (applying && !opening) "Salvando..." else "Só salvar")
                }
            } else {
                TextButton(enabled = !applying, onClick = onDismiss) {
                    Text("Cancelar", color = ImpTokens.TextSecondary)
                }
            }
        }
    )
}

@Composable
private fun SetupOption(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    detail: String,
    enabled: Boolean = true
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .alpha(if (enabled) 1f else 0.45f)
                .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = ImpTokens.Accent)
        )
        Column {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = ImpTokens.TextSecondary, fontSize = 11.sp)
        }
    }
}
