package br.com.redesurftank.havalshisuku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import coil.compose.AsyncImage

/**
 * Campo clicável que mostra o app escolhido (ícone + nome) e, ao tocar, abre o mesmo
 * [AppPickerDialog] da aba "Telas" para escolher um app de uma lista (com busca, apps
 * pré-definidos e entrada MANUAL de pacote). O valor entregue por [onPackageSelected]
 * é sempre o packageName (String), compatível com as prefs já existentes.
 */
@Composable
fun AppSelectorField(
        packageName: String,
        onPackageSelected: (String) -> Unit,
        label: String = "App a abrir",
) {
        val context = LocalContext.current
        var showPicker by remember { mutableStateOf(false) }

        // Resolve nome/ícone do app já escolhido (fallback p/ pacote vazio ou inválido/desinstalado).
        val resolved =
                remember(packageName) {
                        if (packageName.isBlank()) null
                        else
                                try {
                                        DisplayAppLauncher.resolveAppInfo(context, packageName)
                                } catch (e: Exception) {
                                        null
                                }
                }

        Column {
                Text(label, color = Color(0xFFB0B8C4), fontSize = 14.sp)
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .background(
                                                Color(0xFF2A2F37),
                                                RoundedCornerShape(6.dp)
                                        )
                                        .clickable { showPicker = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                        if (resolved?.icon != null) {
                                AsyncImage(
                                        model = resolved.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                )
                        } else {
                                Icon(
                                        Icons.Default.Apps,
                                        contentDescription = null,
                                        tint = Color(0xFFB0B8C4),
                                        modifier = Modifier.size(28.dp)
                                )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                                val title =
                                        when {
                                                resolved != null && resolved.label.isNotBlank() ->
                                                        resolved.label
                                                packageName.isNotBlank() -> packageName
                                                else -> "Selecionar app..."
                                        }
                                Text(
                                        text = title,
                                        color =
                                                if (packageName.isBlank()) Color(0xFF8A93A0)
                                                else Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                                if (resolved != null &&
                                                resolved.label.isNotBlank() &&
                                                packageName.isNotBlank()
                                ) {
                                        Text(
                                                text = packageName,
                                                color = Color(0xFF6F7884),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                }
                        }
                        Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFFB0B8C4)
                        )
                }
        }

        if (showPicker) {
                AppPickerDialog(
                        onDismiss = { showPicker = false },
                        onAppSelected = {
                                onPackageSelected(it.packageName)
                                showPicker = false
                        }
                )
        }
}
