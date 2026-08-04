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
import androidx.compose.ui.draw.clip
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
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.components.StyledCard
import br.com.redesurftank.havalshisuku.ui.theme.Michroma

@Composable
fun CurrentValuesTab() {
    val prefs = App.getDeviceProtectedContext()
        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    val advancedUse = prefs.getBoolean(SharedPreferencesKeys.ADVANCE_USE.key, false)
    val dataMap = remember {
        mutableStateMapOf<String, String>().apply {
            // allCurrentCachedData vem do Java e pode ter valores null; coage pra ""
            // (senão Text(value) estoura NPE no prefetch da LazyColumn ao rolar).
            ServiceManager.getInstance().allCurrentCachedData.forEach { (k, v) -> put(k, v ?: "") }
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

    Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
        Text(
            "VALORES ATUAIS",
            fontFamily = Michroma,
            fontSize = 15.sp,
            letterSpacing = 1.8.sp,
            color = ImpTokens.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 14.dp)
        )
        if (advancedUse) {
            Button(
                onClick = { showConfigDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = ImpTokens.Accent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Configurar", color = ImpTokens.OnAccent, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(12.dp))
        }

        TextField(
            value = searchQueryValues,
            onValueChange = { searchQueryValues = it },
            label = { Text("Pesquisar valores") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ImpTokens.Container,
                unfocusedContainerColor = ImpTokens.Container,
                focusedTextColor = ImpTokens.TextPrimary,
                unfocusedTextColor = ImpTokens.TextPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedLabelColor = ImpTokens.Accent,
                unfocusedLabelColor = ImpTokens.TextMuted,
                cursorColor = ImpTokens.Accent
            )
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ImpTokens.Container)
        ) {
            val filteredData = dataMap.toList()
                .filter { it.first.lowercase().contains(searchQueryValues.lowercase()) }
                .sortedBy { it.first }
            itemsIndexed(filteredData) { index, pair ->
                val (key, value) = pair
                if (index > 0) HorizontalDivider(
                    color = ImpTokens.Hairline,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (advancedUse) Modifier.clickable {
                                selectedKey = key
                                newValue = value
                                showUpdateDialog = true
                            } else Modifier
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(key ?: "", color = ImpTokens.TextSecondary, fontSize = 12.5.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(value ?: "", color = ImpTokens.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
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
                    ServiceManager.getInstance().allCurrentCachedData.forEach { (k, v) -> dataMap[k] = v ?: "" }
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
