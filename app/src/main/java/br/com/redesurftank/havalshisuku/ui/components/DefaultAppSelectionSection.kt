package br.com.redesurftank.havalshisuku.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAppSelectionSection() {
    val context = LocalContext.current
    val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    var configs by remember { mutableStateOf(DisplayAppLauncher.getAllConfigs()) }
    var selectedPackage by remember { mutableStateOf(prefs.getString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, "") ?: "") }
    var expanded by remember { mutableStateOf(false) }

    // Update configs periodically or when needed
    LaunchedEffect(Unit) {
        while(true) {
            configs = DisplayAppLauncher.getAllConfigs()
            delay(5000)
        }
    }

    StyledCard(
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "App Padrão do Cluster",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Selecione o app que abrirá automaticamente quando o cluster carregar",
                color = Color(0xFFB0B8C4),
                fontSize = 14.sp
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                val selectedAppName = if (selectedPackage.isEmpty()) "Nenhum" else {
                    DisplayAppLauncher.resolveAppInfo(context, selectedPackage).label
                }

                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFF3A3F47)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedAppName)
                        Icon(if (expanded) Icons.Default.Close else Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color(0xFF1E2228))
                        .border(1.dp, Color(0xFF3A3F47), RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Nenhum", color = Color.White) },
                        onClick = {
                            selectedPackage = ""
                            prefs.edit { putString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, "") }
                            expanded = false
                        }
                    )
                    for (config in configs) {
                        val appInfo = DisplayAppLauncher.resolveAppInfo(context, config.packageName, config.customName)

                        DropdownMenuItem(
                            text = { Text(appInfo.label, color = Color.White) },
                            onClick = {
                                selectedPackage = config.packageName
                                prefs.edit { putString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, config.packageName) }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
