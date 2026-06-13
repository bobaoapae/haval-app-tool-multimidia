package br.com.redesurftank.havalshisuku.ui.screens

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.ThemeMetadata
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.SettingCard
import br.com.redesurftank.havalshisuku.utils.FridaUtils
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Composable
fun FridaHooksTab() {
        val prefs =
                App.getDeviceProtectedContext()
                        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        var enableFridaHooks by remember {
                mutableStateOf(
                        prefs.getBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.key, false)
                )
        }
        var enableFridaHookSystemServer by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.ENABLE_FRIDA_HOOK_SYSTEM_SERVER.key,
                                false
                        )
                )
        }
        var showFridaDialog by remember { mutableStateOf(false) }
        var showManualDialog by remember { mutableStateOf(false) }
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                item {
                        SettingCard(
                                title = "Habilitar Frida Hooks",
                                description = SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.description,
                                checked = enableFridaHooks,
                                onCheckedChange = { newValue ->
                                        if (!newValue) {
                                                enableFridaHooks = false
                                                prefs.edit {
                                                        putBoolean(
                                                                SharedPreferencesKeys
                                                                        .ENABLE_FRIDA_HOOKS
                                                                        .key,
                                                                false
                                                        )
                                                }
                                        } else {
                                                showFridaDialog = true
                                        }
                                }
                        )
                }
                item {
                        SettingCard(
                                title = "Hook System Server",
                                description =
                                        SharedPreferencesKeys.ENABLE_FRIDA_HOOK_SYSTEM_SERVER
                                                .description,
                                checked = enableFridaHookSystemServer,
                                onCheckedChange = { newValue ->
                                        prefs.edit {
                                                putBoolean(
                                                        SharedPreferencesKeys
                                                                .ENABLE_FRIDA_HOOK_SYSTEM_SERVER
                                                                .key,
                                                        newValue
                                                )
                                        }
                                        enableFridaHookSystemServer = newValue
                                        if (newValue) FridaUtils.injectSystemServer()
                                }
                        )
                }
                item {
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A))
                        ) {
                                Button(
                                        onClick = { showManualDialog = true },
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF4A9EFF)
                                                )
                                ) { Text("Injetar Código Manual", color = Color.White) }
                        }
                }
        }
        if (showFridaDialog) {
                AlertDialog(
                        onDismissRequest = { showFridaDialog = false },
                        title = { Text("Confirmação") },
                        text = {
                                Text(
                                        "Ativar scripts fridas é uma função experimental que pode causar instabilidades, utilize por conta e risco. Caso não saiba o que é essa função é melhor manter desativada"
                                )
                        },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                showFridaDialog = false
                                                enableFridaHooks = true
                                                prefs.edit {
                                                        putBoolean(
                                                                SharedPreferencesKeys
                                                                        .ENABLE_FRIDA_HOOKS
                                                                        .key,
                                                                true
                                                        )
                                                }
                                                ServiceManager.getInstance().initializeFrida()
                                        }
                                ) { Text("Ativar") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showFridaDialog = false }) {
                                        Text("Cancelar")
                                }
                        }
                )
        }
        if (showManualDialog) {
                AlertDialog(
                        onDismissRequest = { showManualDialog = false },
                        title = { Text("Hooks Manuais") },
                        text = {
                                val manuals =
                                        FridaUtils.ScriptProcess.entries.filter {
                                                it.injectMode == FridaUtils.InjectMode.MANUAL
                                        }
                                LazyColumn {
                                        items(manuals) { script ->
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Text(script.process)
                                                        Spacer(Modifier.width(8.dp))
                                                        Button(
                                                                onClick = {
                                                                        FridaUtils.injectScript(
                                                                                script,
                                                                                false
                                                                        )
                                                                }
                                                        ) { Text("Injetar") }
                                                }
                                        }
                                }
                        },
                        confirmButton = {
                                TextButton(onClick = { showManualDialog = false }) {
                                        Text("Fechar")
                                }
                        }
                )
        }
}
