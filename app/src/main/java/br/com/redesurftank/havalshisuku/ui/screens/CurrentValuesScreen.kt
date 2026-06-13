package br.com.redesurftank.havalshisuku.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.listeners.IDataChanged
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import br.com.redesurftank.havalshisuku.ui.components.StyledCard

@Composable
fun CurrentValuesTab() {
    val prefs = App.getDeviceProtectedContext()
        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    val advancedUse = prefs.getBoolean(SharedPreferencesKeys.ADVANCE_USE.key, false)
    val dataMap = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(ServiceManager.getInstance().allCurrentCachedData)
        }
    }
    var showConfigDialog by remember { mutableStateOf(false) }
    val allConstants = remember { CarConstants.values().map { it.value } }
    val defaultKeys = remember { ServiceManager.DEFAULT_KEYS.map { it.value } }
    val filteredConstants = remember { allConstants.filter { it !in defaultKeys } }
    val monitoredSet = remember {
        mutableStateOf(
            prefs.getStringSet(SharedPreferencesKeys.CAR_MONITOR_PROPERTIES.key, emptySet()) ?: emptySet()
        )
    }
    val tempChecked = remember {
        mutableStateMapOf<String, Boolean>().apply {
            allConstants.forEach { this[it] = monitoredSet.value.contains(it) }
        }
    }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var searchQueryValues by remember { mutableStateOf("") }
    var searchQueryConfig by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val listener = IDataChanged { key, value -> dataMap[key] = value ?: "" }
        ServiceManager.getInstance().addDataChangedListener(listener)
        onDispose { ServiceManager.getInstance().removeDataChangedListener(listener) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (advancedUse) {
            Button(
                onClick = { showConfigDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF))
            ) { Text("Configurar", color = Color.White) }
            Spacer(Modifier.height(8.dp))
        }

        TextField(
            value = searchQueryValues,
            onValueChange = { searchQueryValues = it },
            label = { Text("Pesquisar valores") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF2A2F37),
                unfocusedContainerColor = Color(0xFF2A2F37),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color(0xFFB0B8C4),
                focusedIndicatorColor = Color(0xFF4A9EFF),
                unfocusedIndicatorColor = Color(0xFF3A3F47),
                focusedLabelColor = Color(0xFF4A9EFF),
                unfocusedLabelColor = Color(0xFFB0B8C4)
            )
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val filteredData = dataMap.toList()
                .filter { it.first.lowercase().contains(searchQueryValues.lowercase()) }
                .sortedBy { it.first }
            items(filteredData) { (key, value) ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .then(
                            if (advancedUse) Modifier.clickable {
                                selectedKey = key
                                newValue = value
                                showUpdateDialog = true
                            } else Modifier
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text("$key: $value", modifier = Modifier.padding(8.dp), color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }

    if (showConfigDialog && advancedUse) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Configurar Monitoramento") },
            text = {
                Column {
                    TextField(
                        value = searchQueryConfig,
                        onValueChange = { searchQueryConfig = it },
                        label = { Text("Pesquisar constantes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    val sortedConstants = filteredConstants.filter {
                        it.lowercase().contains(searchQueryConfig.lowercase())
                    }.sortedBy { !(tempChecked[it] ?: false) }
                    
                    LazyColumn {
                        items(count = sortedConstants.size) { index: Int ->
                            val constant = sortedConstants[index]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = tempChecked[constant] ?: false,
                                    onCheckedChange = { tempChecked[constant] = it }
                                )
                                Text(constant, color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newSet = tempChecked.filterValues { it }.keys.toSet()
                    prefs.edit {
                        putStringSet(SharedPreferencesKeys.CAR_MONITOR_PROPERTIES.key, newSet)
                    }
                    monitoredSet.value = newSet
                    showConfigDialog = false
                    ServiceManager.getInstance().updateMonitoringProperties()
                    dataMap.clear()
                    dataMap.putAll(ServiceManager.getInstance().allCurrentCachedData)
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    allConstants.forEach { constantKey: String ->
                        tempChecked[constantKey] = monitoredSet.value.contains(constantKey)
                    }
                    showConfigDialog = false
                }) { Text("Cancelar") }
            }
        )
    }

    if (showUpdateDialog && advancedUse) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Atualizar $selectedKey") },
            text = {
                TextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = { Text("Novo valor") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ServiceManager.getInstance().updateData(selectedKey, newValue)
                    showUpdateDialog = false
                }) { Text("Atualizar") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
