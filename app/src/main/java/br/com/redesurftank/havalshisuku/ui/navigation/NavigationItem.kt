package br.com.redesurftank.havalshisuku.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class NavigationItem(
    val title: String,
    val icon: ImageVector
)

val menuItems = listOf(
    NavigationItem("Configurações", Icons.Default.Settings),
    NavigationItem("Telas", Icons.Default.SmartDisplay),
    NavigationItem("Valores Atuais", Icons.Default.DeveloperMode),
    NavigationItem("Instalar Apps", Icons.Default.ShoppingCart),
    NavigationItem("Recursos", Icons.Default.Apps),
    NavigationItem("Informações", Icons.Default.Info),
    NavigationItem("Frida Hooks", Icons.Default.Build)
)
