package br.com.redesurftank.havalshisuku.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.BuildConfig
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.managers.ThemeManager
import br.com.redesurftank.havalshisuku.models.DisplayAppConfig
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.ThemeMetadata
import br.com.redesurftank.havalshisuku.ui.components.StyledCard
import br.com.redesurftank.havalshisuku.ui.components.StyledTextField
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RevisionEntry(val km: Int, val date: Long)

data class InstalledAppInfo(
        val packageName: String,
        val activityName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?
)

data class DisplayInfo(val id: Int, val name: String)

fun getRevisionHistory(prefs: SharedPreferences): List<RevisionEntry> {
    val json = prefs.getString(SharedPreferencesKeys.INSTRUMENT_REVISION_HISTORY.key, "[]")
    return try {
        val type = object : TypeToken<List<RevisionEntry>>() {}.type
        Gson().fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            Log.e("RevisionHistory", "Error parsing history: ${e.message}")
        }
        emptyList()
    }
}

fun saveRevisionHistory(prefs: SharedPreferences, history: List<RevisionEntry>) {
    val json = Gson().toJson(history)
    prefs.edit { putString(SharedPreferencesKeys.INSTRUMENT_REVISION_HISTORY.key, json) }
}

@Composable
fun CompactThemeCard(
        theme: ThemeMetadata,
        isDownloaded: Boolean,
        isSelected: Boolean,
        hasUpdate: Boolean,
        isDownloading: Boolean,
        canDelete: Boolean = true,
        onAction: () -> Unit,
        onUpdate: () -> Unit,
        onDelete: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF4A9EFF) else Color(0xFF2C3139)
    val backgroundColor = if (isSelected) Color(0xFF1E2638) else Color(0xFF1E2228)
    val context = LocalContext.current

    Card(
            modifier =
                    Modifier.width(300.dp)
                            .clickable { onAction() }
                            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            // Thumbnail / Icon space
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .height(106.dp)
                                    .background(Color(0xFF13151A), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
            ) {
                val model =
                        remember(theme.thumbnailUrl) {
                            if (theme.thumbnailUrl.isNotEmpty() &&
                                            !theme.thumbnailUrl.startsWith("http") &&
                                            !theme.thumbnailUrl.startsWith("/")
                            ) {
                                context.resources.getIdentifier(
                                                theme.thumbnailUrl,
                                                "drawable",
                                                context.packageName
                                        )
                                        .let { if (it != 0) it else theme.thumbnailUrl }
                            } else {
                                theme.thumbnailUrl
                            }
                        }

                if (theme.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(
                            model = model,
                            contentDescription = theme.name,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                            imageVector = Icons.Default.Style,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp)
                    )
                }

                // Selected indicator overlay
                if (isSelected) {
                    Box(
                            modifier =
                                    Modifier.fillMaxSize()
                                            .background(Color(0xFF4A9EFF).copy(alpha = 0.2f))
                    )
                    Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4A9EFF),
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Theme Name
            Text(
                    text = theme.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )

            // Description / Status
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (isDownloading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color(0xFF4A9EFF),
                                    strokeWidth = 1.5.dp
                            )
                            Text(
                                text = "Baixando...",
                                color = Color(0xFF4A9EFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (!isDownloaded) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { onAction() }
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color(0xFF4A9EFF),
                                    modifier = Modifier.size(14.dp)
                            )
                            Text(
                                    text = "Baixar",
                                    color = Color(0xFF4A9EFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                                text = if (theme.name == "Default") "Original" else "Instalado",
                                color = if (isSelected) Color(0xFF4A9EFF) else Color(0xFFB0B8C4),
                                fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Update Button in bottom right if update is available
                    if (hasUpdate && !isDownloading) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE5A93B), RoundedCornerShape(6.dp))
                                .clickable { onUpdate() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Update,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Atualizar",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Delete button for custom installed themes
                    if (canDelete) {
                        Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Excluir",
                                tint = Color(0xFFFF4B4B).copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp).clickable { onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelasTab() {
    val context = LocalContext.current
    val prefs =
            App.getDeviceProtectedContext()
                    .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    // Base properties
    var enableProjector by remember {
        mutableStateOf(
                prefs.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key, false)
        )
    }
    var enableOdometerAndRevision by remember {
        mutableStateOf(
                prefs.getBoolean(
                        SharedPreferencesKeys.ENABLE_INSTRUMENT_ODOMETER_AND_REVISION.key,
                        true
                )
        )
    }
    var enableCustomIntegration by remember {
        mutableStateOf(
                prefs.getBoolean(
                        SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.key,
                        false
                )
        )
    }
    var enableMask by remember {
        mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, false))
    }
    var enableCustomMenu by remember {
        mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_MENU.key, false))
    }
    var allClusterFunctionsEnabled by remember {
        mutableStateOf(enableProjector || enableCustomIntegration || enableCustomMenu)
    }
    var clusterFuelDisplayUnit by remember {
        mutableStateOf(
                prefs.getString(SharedPreferencesKeys.CLUSTER_FUEL_DISPLAY_UNIT.key, "liters")
                        ?: "liters"
        )
    }

    // Revision History States
    var revisionHistory by remember { mutableStateOf(getRevisionHistory(prefs)) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var expandedHistory by remember { mutableStateOf(false) }
    var tempKm by remember { mutableStateOf("") }
    var tempDate by remember { mutableLongStateOf(0L) }
    var showDatePickerForRegister by remember { mutableStateOf(false) }

    // Virtual Cluster States
    var selectedTheme by remember {
        mutableStateOf(
                prefs.getString(SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, "Default")
                        ?: "Default"
        )
    }
    var defaultApp by remember {
        mutableStateOf(
                prefs.getString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, "") ?: ""
        )
    }
    var appExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var configs by remember { mutableStateOf(DisplayAppLauncher.getAllConfigs()) }
    var activeEditConfig by remember { mutableStateOf<DisplayAppConfig?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showVirtualClusterWarningDialog by remember { mutableStateOf(false) }

    // GitHub Themes States
    var githubThemes by remember { mutableStateOf<List<ThemeMetadata>>(emptyList()) }
    var localThemes by remember {
        mutableStateOf(ThemeManager.getInstance(context).getLocalThemes())
    }
    var isFetchingThemes by remember { mutableStateOf(false) }
    var downloadingThemeName by remember { mutableStateOf<String?>(null) }
    var isThemesExpanded by remember { mutableStateOf(true) }

    // Date formatter
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    // Auto-calculate next revision
    val latestRevision = revisionHistory.maxByOrNull { it.km }
    val nextKm = latestRevision?.let { it.km + 12000 } ?: 0
    val nextDate =
            latestRevision?.let {
                val cal = Calendar.getInstance()
                cal.timeInMillis = it.date
                cal.add(Calendar.YEAR, 1)
                cal.timeInMillis
            }
                    ?: 0L

    // Sync calculated revision to prefs for display in projector
    LaunchedEffect(nextKm, nextDate) {
        prefs.edit {
            putInt(SharedPreferencesKeys.INSTRUMENT_REVISION_KM.key, nextKm)
            putLong(SharedPreferencesKeys.INSTRUMENT_REVISION_NEXT_DATE.key, nextDate)
        }
    }

    // Periodic app config update
    LaunchedEffect(Unit) {
        while (true) {
            configs = DisplayAppLauncher.getAllConfigs()
            delay(5000)
        }
    }

    // Refresh local themes on start just in case, and fetch from GitHub
    LaunchedEffect(Unit) {
        localThemes = ThemeManager.getInstance(context).getLocalThemes()
        if (githubThemes.isEmpty()) {
            isFetchingThemes = true
            try {
                githubThemes =
                        ThemeManager.getInstance(context)
                                .fetchThemesFromGithub(ThemeManager.THEME_REPO_URL)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("TelasTab", "Error fetching themes", e)
                }
            } finally {
                isFetchingThemes = false
            }
        }
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // MASTER TOGGLE CARD - Consolidates Projector, Media Integration and Custom Menu
        StyledCard(modifier = Modifier.padding(horizontal = 8.dp)) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                "Habilitar Funções do Cluster",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                "Habilitar projeção de um menu customizado no cluster de instrumentos.",
                                color = Color(0xFFB0B8C4),
                                fontSize = 14.sp
                        )
                    }
                    Switch(
                            checked = allClusterFunctionsEnabled,
                            onCheckedChange = {
                                allClusterFunctionsEnabled = it
                                enableProjector = it
                                enableCustomIntegration = it
                                enableCustomMenu = it

                                prefs.edit {
                                    putBoolean(
                                            SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key,
                                            it
                                    )
                                    putBoolean(
                                            SharedPreferencesKeys
                                                    .ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION
                                                    .key,
                                            it
                                    )
                                    putBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_MENU.key, it)
                                }

                                if (!it) {
                                    enableOdometerAndRevision = false
                                    prefs.edit {
                                        putBoolean(
                                                SharedPreferencesKeys
                                                        .ENABLE_INSTRUMENT_ODOMETER_AND_REVISION
                                                        .key,
                                                false
                                        )
                                    }
                                    enableMask = false
                                    prefs.edit {
                                        putBoolean(
                                                SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key,
                                                false
                                        )
                                    }
                                }

                                try {
                                    ServiceManager.getInstance().ensureSystemApps()
                                    if (it) {
                                        ServiceManager.getInstance().startClusterHeartbeat()
                                    }
                                } catch (e: Exception) {
                                    if (BuildConfig.DEBUG) {
                                        Log.e(
                                                "TelasTab",
                                                "Erro ao alterar funções do cluster: ${e.message}",
                                                e
                                        )
                                    }
                                }
                            },
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF4A9EFF)
                                    )
                    )
                }
            }
        }

        // VIRTUAL CLUSTER CARD
        val virtualClusterAlpha = if (allClusterFunctionsEnabled) 1f else 0.4f
        StyledCard(modifier = Modifier.padding(horizontal = 8.dp).alpha(virtualClusterAlpha)) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                "Painel Virtual",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                "Extende as funções do cluster para renderizar um painel customizado com suporte a temas.",
                                color = Color(0xFFB0B8C4),
                                fontSize = 14.sp
                        )
                    }
                    Switch(
                            checked = enableMask,
                            enabled = allClusterFunctionsEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showVirtualClusterWarningDialog = true
                                } else {
                                    enableMask = false
                                    prefs.edit {
                                        putBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, false)
                                    }
                                }
                            },
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF4A9EFF)
                                    )
                    )
                }

                if (enableMask) {
                    HorizontalDivider(color = Color(0xFF2C3139))

                    // Theme Selector - Horizontal compact carousel
                    Column {
                        Text(
                                "Tema do Painel (Toque para selecionar)",
                                color = Color(0xFFB0B8C4),
                                fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        val allThemes =
                                remember(githubThemes, localThemes) {
                                    val merged = mutableListOf<ThemeMetadata>()

                                    // Find remote "Default" and local "Default"
                                    val remoteDefault =
                                            githubThemes.firstOrNull {
                                                it.folderName == "Default" || it.name == "Default"
                                            }
                                    val localDefault =
                                            localThemes.firstOrNull {
                                                it.folderName == "Default" || it.name == "Default"
                                            }

                                    if (localDefault != null) {
                                        // Default theme is downloaded locally
                                        val hasUpdate =
                                                if (remoteDefault != null) {
                                                    ThemeManager.getInstance(context)
                                                            .isNewerVersion(
                                                                    localDefault.version,
                                                                    remoteDefault.version
                                                            )
                                                } else false

                                        merged.add(
                                                ThemeMetadata(
                                                        name = "Default",
                                                        description =
                                                                localDefault.description.ifEmpty {
                                                                    remoteDefault?.description
                                                                            ?: "Visual clássico do carro"
                                                                },
                                                        version = localDefault.version,
                                                        thumbnailUrl =
                                                                localDefault.thumbnailUrl.ifEmpty {
                                                                    remoteDefault?.thumbnailUrl
                                                                            ?: ""
                                                                },
                                                        mainFile = localDefault.mainFile,
                                                        folderName = "Default",
                                                        isLocal = true,
                                                        isDownloaded = true,
                                                        hasUpdate = hasUpdate
                                                )
                                        )
                                    } else {
                                        // Default theme is not downloaded locally, using embedded
                                        val hasUpdate =
                                                if (remoteDefault != null) {
                                                    ThemeManager.getInstance(context)
                                                            .isEmbeddedDifferent(
                                                                    remoteDefault.remoteSha,
                                                                    remoteDefault.remoteSize
                                                            )
                                                } else false

                                        merged.add(
                                                ThemeMetadata(
                                                        name = "Default",
                                                        description = remoteDefault?.description
                                                                        ?: "Visual clássico do carro",
                                                        version = remoteDefault?.version ?: "1.0.0",
                                                        thumbnailUrl = remoteDefault?.thumbnailUrl
                                                                        ?: "",
                                                        mainFile = remoteDefault?.mainFile ?: "",
                                                        folderName = "Default",
                                                        isLocal = true,
                                                        isDownloaded = true,
                                                        hasUpdate = hasUpdate
                                                )
                                        )
                                    }

                                    // Now add rest of remote themes (excluding Default)
                                    githubThemes.forEach { remote ->
                                        if (remote.folderName != "Default" &&
                                                        remote.name != "Default"
                                        ) {
                                            val local =
                                                    localThemes.firstOrNull {
                                                        it.folderName == remote.folderName
                                                    }
                                            if (local != null) {
                                                val hasUpdate =
                                                        ThemeManager.getInstance(context)
                                                                .isNewerVersion(
                                                                        local.version,
                                                                        remote.version
                                                                )
                                                merged.add(
                                                        remote.copy(
                                                                isLocal = true,
                                                                isDownloaded = true,
                                                                hasUpdate = hasUpdate
                                                        )
                                                )
                                            } else {
                                                merged.add(
                                                        remote.copy(
                                                                isLocal = false,
                                                                isDownloaded = false
                                                        )
                                                )
                                            }
                                        }
                                    }

                                    // Now add rest of local themes (excluding Default)
                                    localThemes.forEach { local ->
                                        if (local.name != "Default" &&
                                                        local.folderName != "Default" &&
                                                        githubThemes.none {
                                                            it.folderName == local.folderName
                                                        }
                                        ) {
                                            merged.add(
                                                    local.copy(isLocal = true, isDownloaded = true)
                                            )
                                        }
                                    }

                                    merged
                                }

                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            allThemes.forEach { theme ->
                                val isDefaultDownloaded =
                                        localThemes.any { it.folderName == "Default" }
                                CompactThemeCard(
                                        theme = theme,
                                        isDownloaded = theme.isDownloaded,
                                        isSelected = selectedTheme == theme.name,
                                        hasUpdate = theme.hasUpdate,
                                        isDownloading = downloadingThemeName == theme.folderName,
                                        canDelete =
                                                if (theme.folderName == "Default")
                                                        isDefaultDownloaded
                                                else
                                                        (theme.isDownloaded &&
                                                                theme.name != "Default"),
                                        onAction = {
                                            if (allClusterFunctionsEnabled) {
                                                if (theme.isDownloaded) {
                                                    selectedTheme = theme.name
                                                    prefs.edit {
                                                        putString(
                                                                SharedPreferencesKeys
                                                                        .VIRTUAL_CLUSTER_THEME
                                                                        .key,
                                                                theme.name
                                                        )
                                                        putString(
                                                                SharedPreferencesKeys
                                                                        .ACTIVE_CUSTOM_THEME
                                                                        .key,
                                                                if (theme.folderName == "Default" ||
                                                                                theme.name ==
                                                                                        "Default"
                                                                )
                                                                        ""
                                                                else theme.folderName
                                                        )
                                                    }
                                                } else {
                                                    downloadingThemeName = theme.folderName
                                                    scope.launch {
                                                        val ok =
                                                                ThemeManager.getInstance(context)
                                                                        .downloadTheme(theme)
                                                        downloadingThemeName = null
                                                        if (ok) {
                                                            localThemes =
                                                                    ThemeManager.getInstance(
                                                                                    context
                                                                            )
                                                                            .getLocalThemes()
                                                            if (selectedTheme == theme.name) {
                                                                prefs.edit {
                                                                    putString(
                                                                            SharedPreferencesKeys
                                                                                    .ACTIVE_CUSTOM_THEME
                                                                                    .key,
                                                                            if (theme.folderName ==
                                                                                            "Default" ||
                                                                                            theme.name ==
                                                                                                    "Default"
                                                                            )
                                                                                    ""
                                                                            else theme.folderName
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onUpdate = {
                                            if (allClusterFunctionsEnabled) {
                                                downloadingThemeName = theme.folderName
                                                scope.launch {
                                                    val ok =
                                                            ThemeManager.getInstance(context)
                                                                    .downloadTheme(theme)
                                                    downloadingThemeName = null
                                                    if (ok) {
                                                        localThemes =
                                                                ThemeManager.getInstance(context)
                                                                        .getLocalThemes()
                                                        if (selectedTheme == theme.name) {
                                                            prefs.edit {
                                                                putString(
                                                                        SharedPreferencesKeys
                                                                                .ACTIVE_CUSTOM_THEME
                                                                                .key,
                                                                        if (theme.folderName ==
                                                                                        "Default" ||
                                                                                        theme.name ==
                                                                                                "Default"
                                                                        )
                                                                                ""
                                                                        else theme.folderName
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onDelete = {
                                            if (allClusterFunctionsEnabled) {
                                                if (ThemeManager.getInstance(context)
                                                                .deleteTheme(theme.folderName)
                                                ) {
                                                    if (selectedTheme == theme.name) {
                                                        prefs.edit {
                                                            putString(
                                                                    SharedPreferencesKeys
                                                                            .ACTIVE_CUSTOM_THEME
                                                                            .key,
                                                                    ""
                                                            )
                                                        }
                                                    }
                                                    localThemes =
                                                            ThemeManager.getInstance(context)
                                                                    .getLocalThemes()
                                                }
                                            }
                                        }
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    HorizontalDivider(color = Color(0xFF2C3139))
                    Spacer(Modifier.height(16.dp))

                    // Default screen/app when cluster initializes and fuel consumption unit merged
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                    ) {
                        // 1. Default screen/app when cluster initializes
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                    "App Padrão na Inicialização",
                                    color = Color(0xFFB0B8C4),
                                    fontSize = 12.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .height(46.dp)
                                                    .background(
                                                            Color(0xFF2A2F37),
                                                            RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable(enabled = allClusterFunctionsEnabled) {
                                                        appExpanded = true
                                                    }
                                                    .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                            ) {
                                val resolvedApp = remember(defaultApp, configs) {
                                    if (defaultApp.isEmpty()) {
                                        br.com.redesurftank.havalshisuku.managers.ResolvedAppInfo("Nenhum", null)
                                    } else {
                                        val config = configs.firstOrNull { it.packageName == defaultApp }
                                        if (config != null) {
                                            DisplayAppLauncher.resolveAppInfo(context, config.packageName, config.customName)
                                        } else {
                                            val predefined = DisplayAppLauncher.PREDEFINED_APPS.firstOrNull { it.packageName == defaultApp }
                                            DisplayAppLauncher.resolveAppInfo(context, defaultApp, predefined?.customName)
                                        }
                                    }
                                }

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (resolvedApp.icon != null) {
                                            AsyncImage(
                                                model = resolvedApp.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (defaultApp.isEmpty()) Icons.Default.Block else Icons.Default.Apps,
                                                contentDescription = null,
                                                tint = if (defaultApp.isEmpty()) Color(0xFFB0B8C4) else Color(0xFF4A9EFF),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Text(resolvedApp.label, color = Color.White, fontSize = 14.sp)
                                    }
                                    Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Expandir",
                                            tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                        expanded = appExpanded && allClusterFunctionsEnabled,
                                        onDismissRequest = { appExpanded = false },
                                        modifier =
                                                Modifier.fillMaxWidth(0.45f)
                                                        .background(Color(0xFF1E2228))
                                ) {
                                    DropdownMenuItem(
                                            text = { Text("Nenhum", color = Color.White, fontSize = 14.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Block,
                                                    contentDescription = null,
                                                    tint = Color(0xFFB0B8C4),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            onClick = {
                                                defaultApp = ""
                                                prefs.edit {
                                                    putString(
                                                            SharedPreferencesKeys
                                                                    .DEFAULT_DISPLAY_APP_PACKAGE
                                                                    .key,
                                                            ""
                                                    )
                                                }
                                                appExpanded = false
                                            }
                                    )
                                    configs.forEach { config ->
                                        val resolved = remember(config.packageName, config.customName) {
                                            DisplayAppLauncher.resolveAppInfo(context, config.packageName, config.customName)
                                        }
                                        DropdownMenuItem(
                                                text = { Text(resolved.label, color = Color.White, fontSize = 14.sp) },
                                                leadingIcon = if (resolved.icon != null) {
                                                    {
                                                        AsyncImage(
                                                            model = resolved.icon,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                } else {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Default.Apps,
                                                            contentDescription = null,
                                                            tint = Color(0xFF4A9EFF),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    defaultApp = config.packageName
                                                    prefs.edit {
                                                        putString(
                                                                SharedPreferencesKeys
                                                                        .DEFAULT_DISPLAY_APP_PACKAGE
                                                                        .key,
                                                                config.packageName
                                                        )
                                                    }
                                                    appExpanded = false
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Personalizações de Exibição (Unidade de Consumo)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                    "Unidade de Consumo de Combustível",
                                    color = Color(0xFFB0B8C4),
                                    fontSize = 12.sp
                            )
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val units = listOf("liters" to "Litros", "percent" to "Porcentagem")
                                units.forEach { (unitId, label) ->
                                    val isSelected = clusterFuelDisplayUnit == unitId
                                    Box(
                                            modifier =
                                                    Modifier.weight(1f)
                                                            .height(46.dp)
                                                            .background(
                                                                    if (isSelected) Color(0xFF4A9EFF)
                                                                    else Color(0xFF2A2F37),
                                                                    RoundedCornerShape(8.dp)
                                                            )
                                                            .clickable(
                                                                    enabled = allClusterFunctionsEnabled
                                                            ) {
                                                                clusterFuelDisplayUnit = unitId
                                                                prefs.edit {
                                                                    putString(
                                                                            SharedPreferencesKeys
                                                                                    .CLUSTER_FUEL_DISPLAY_UNIT
                                                                                    .key,
                                                                            unitId
                                                                    )
                                                                }
                                                            }
                                                            .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                                label,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF2C3139))
                    Spacer(Modifier.height(16.dp))

                    // Odômetro e Aviso de Revisão
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Column 1 (Left Card): Switch & Title inside card (40% width)
                            val leftWeight = 0.40f

                            Row(
                                modifier = Modifier
                                    .weight(leftWeight)
                                    .height(104.dp)
                                    .background(
                                        Color(0xFF2A2F37),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = allClusterFunctionsEnabled) {
                                        val next = !enableOdometerAndRevision
                                        enableOdometerAndRevision = next
                                        prefs.edit {
                                            putBoolean(
                                                SharedPreferencesKeys
                                                    .ENABLE_INSTRUMENT_ODOMETER_AND_REVISION
                                                    .key,
                                                next
                                            )
                                        }
                                    }
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                            "Exibir Odômetro e Aviso de Revisão",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                            "Exibir total do veículo e acompanhamento de próxima revisão no painel",
                                            color = Color(0xFFB0B8C4),
                                            fontSize = 12.sp
                                    )
                                }
                                Switch(
                                        checked = enableOdometerAndRevision,
                                        enabled = allClusterFunctionsEnabled,
                                        onCheckedChange = null,
                                        colors =
                                                SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = Color(0xFF4A9EFF)
                                                ),
                                        modifier = Modifier
                                            .padding(start = 16.dp, end = 8.dp)
                                            .scale(1.0f)
                                )
                            }

                            // Column 2 (Right Card): Next Revision inside card (60% width, always visible but disabled if toggle off)
                            val isCard2Enabled = enableOdometerAndRevision && allClusterFunctionsEnabled
                            val textDisabledColor = Color(0xFF6B7280)

                            Row(
                                modifier = Modifier
                                    .weight(0.60f)
                                    .height(104.dp)
                                    .background(
                                        if (isCard2Enabled) Color(0xFF2A2F37) else Color(0xFF1E2229),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = isCard2Enabled) {
                                        tempKm = ServiceManager.getInstance().totalOdometer.toString()
                                        tempDate = System.currentTimeMillis()
                                        showRegisterDialog = true
                                    }
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Column 1: Alterar Button inside Box to ensure symmetry
                                Box(
                                    modifier = Modifier.width(210.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Button(
                                        onClick = {
                                            tempKm = ServiceManager.getInstance().totalOdometer.toString()
                                            tempDate = System.currentTimeMillis()
                                            showRegisterDialog = true
                                        },
                                        enabled = isCard2Enabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4A9EFF),
                                            disabledContainerColor = Color(0xFF2C3139)
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        modifier = Modifier.height(38.dp).fillMaxWidth()
                                    ) {
                                        Text(
                                            if (revisionHistory.isEmpty()) "Registrar compra ou revisão" else "Registrar revisão",
                                            color = if (isCard2Enabled) Color.White else textDisabledColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Column 2: Centered "Próxima Revisão" details
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "Próxima Revisão",
                                        color = if (isCard2Enabled) Color(0xFFB0B8C4) else textDisabledColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (nextKm == 0) {
                                        Text(
                                            "N/D - Cadastrar",
                                            color = if (isCard2Enabled) Color(0xFFFFB74D) else textDisabledColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        val nextKmLabel = String.format("%,d", nextKm) + " km"
                                        val nextDateLabel = dateFormatter.format(nextDate)
                                        Text(
                                            "$nextKmLabel ou $nextDateLabel",
                                            color = if (isCard2Enabled) Color.White else textDisabledColor,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Column 3: Spacer to perfectly balance the button width on the right
                                Spacer(modifier = Modifier.width(210.dp))
                            }
                        }

                        // Collapsible History
                        // Collapsible History Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2A2F37), RoundedCornerShape(8.dp))
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedHistory = !expandedHistory }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Histórico de Revisões (${revisionHistory.size})",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (expandedHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            AnimatedVisibility(visible = expandedHistory) {
                                Column(
                                    modifier = Modifier.padding(top = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (revisionHistory.isEmpty()) {
                                        Text(
                                            "Nenhuma revisão registrada",
                                            color = Color(0xFF636D77),
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        revisionHistory.sortedByDescending { it.km }.forEach { entry ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        Color(0xFF1E2228),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Column 1: Delete icon on the LEFT
                                                IconButton(
                                                    onClick = {
                                                        val newHistory = revisionHistory.filter { it != entry }
                                                        revisionHistory = newHistory
                                                        saveRevisionHistory(prefs, newHistory)
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Excluir",
                                                        tint = Color(0xFFFF5252),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                // Column 2: Entry description (mileage or compra) - increased font size
                                                Text(
                                                    text = if (entry.km != 0) {
                                                        "${String.format("%,d", entry.km)} km"
                                                    } else {
                                                        "Data de Compra"
                                                    },
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                Spacer(modifier = Modifier.weight(1f))

                                                // Column 3: Date on the RIGHT - increased font size
                                                Text(
                                                    text = dateFormatter.format(entry.date),
                                                    color = Color(0xFFB0B8C4),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // APP COORDINATORS SECTION
        val coordinatorsAlpha = if (allClusterFunctionsEnabled) 1f else 0.4f
        StyledCard(modifier = Modifier.padding(horizontal = 8.dp).alpha(coordinatorsAlpha)) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                        "Configuração de Telas Secundárias",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                )

                HorizontalDivider(color = Color(0xFF2C3139))

                Button(
                        onClick = {
                            activeEditConfig = null
                            showConfigDialog = true
                        },
                        enabled = allClusterFunctionsEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                            "Adicionar Atalho de Tela",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val totalItems = configs.size
                val columns = 3
                val rows = (totalItems + columns - 1) / columns

                Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (r in (rows - 1) downTo 0) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (c in 0 until columns) {
                                val index = r * columns + c
                                if (index < totalItems) {
                                    val config = configs[index]
                                    val resolved =
                                            remember(config.packageName, config.customName) {
                                                DisplayAppLauncher.resolveAppInfo(
                                                        context,
                                                        config.packageName,
                                                        config.customName
                                                )
                                            }

                                    Card(
                                            modifier =
                                                    Modifier.weight(1f)
                                                            .height(210.dp)
                                                            .border(
                                                                    1.5.dp,
                                                                    Color(0xFF2C3139),
                                                                    RoundedCornerShape(12.dp)
                                                            )
                                                            .clickable(
                                                                    enabled =
                                                                            allClusterFunctionsEnabled
                                                            ) {
                                                                activeEditConfig = config
                                                                showConfigDialog = true
                                                            },
                                            colors =
                                                    CardDefaults.cardColors(
                                                            containerColor =
                                                                    Color(0xFF2A2F37)
                                                                            .copy(alpha = 0.5f)
                                                    ),
                                            shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // 1. Edge Positioning Left Button (<)
                                            val isLeftEnabled =
                                                    index > 0 && allClusterFunctionsEnabled
                                            Box(
                                                    modifier =
                                                            Modifier.align(Alignment.CenterStart)
                                                                    .fillMaxHeight()
                                                                    .width(40.dp)
                                                                    .background(
                                                                            if (isLeftEnabled)
                                                                                    Color(
                                                                                                    0xFF2C3139
                                                                                            )
                                                                                            .copy(
                                                                                                    alpha =
                                                                                                            0.4f
                                                                                            )
                                                                            else Color.Transparent
                                                                    )
                                                                    .clickable(
                                                                            enabled = isLeftEnabled
                                                                    ) {
                                                                        DisplayAppLauncher
                                                                                .moveConfigUp(
                                                                                        config.packageName
                                                                                )
                                                                        configs =
                                                                                DisplayAppLauncher
                                                                                        .getAllConfigs()
                                                                    },
                                                    contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                        imageVector =
                                                                Icons.Default.KeyboardArrowLeft,
                                                        contentDescription = "Esquerda",
                                                        tint =
                                                                if (isLeftEnabled) Color.White
                                                                else
                                                                        Color.White.copy(
                                                                                alpha = 0.15f
                                                                        ),
                                                        modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            // 2. Edge Positioning Right Button (>)
                                            val isRightEnabled =
                                                    index < configs.size - 1 &&
                                                            allClusterFunctionsEnabled
                                            Box(
                                                    modifier =
                                                            Modifier.align(Alignment.CenterEnd)
                                                                    .fillMaxHeight()
                                                                    .width(40.dp)
                                                                    .background(
                                                                            if (isRightEnabled)
                                                                                    Color(
                                                                                                    0xFF2C3139
                                                                                            )
                                                                                            .copy(
                                                                                                    alpha =
                                                                                                            0.4f
                                                                                            )
                                                                            else Color.Transparent
                                                                    )
                                                                    .clickable(
                                                                            enabled = isRightEnabled
                                                                    ) {
                                                                        DisplayAppLauncher
                                                                                .moveConfigDown(
                                                                                        config.packageName
                                                                                )
                                                                        configs =
                                                                                DisplayAppLauncher
                                                                                        .getAllConfigs()
                                                                    },
                                                    contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                        imageVector =
                                                                Icons.Default.KeyboardArrowRight,
                                                        contentDescription = "Direita",
                                                        tint =
                                                                if (isRightEnabled) Color.White
                                                                else
                                                                        Color.White.copy(
                                                                                alpha = 0.15f
                                                                        ),
                                                        modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            // 3. Central Card Content
                                            Column(
                                                    modifier =
                                                            Modifier.padding(
                                                                            start = 48.dp,
                                                                            end = 48.dp,
                                                                            top = 12.dp,
                                                                            bottom = 12.dp
                                                                    )
                                                                    .fillMaxSize(),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // A. Top Row (Icon + Name on left, Kill + Delete on
                                                // right)
                                                Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                            horizontalArrangement =
                                                                    Arrangement.spacedBy(8.dp),
                                                            verticalAlignment =
                                                                    Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                    ) {
                                                        val substituteIconVector =
                                                                getSubstituteIconVector(
                                                                        config.substituteIcon
                                                                )
                                                        if (config.substituteIcon == "youtube" ||
                                                                        config.substituteIcon ==
                                                                                "youtube_music" ||
                                                                        config.substituteIcon ==
                                                                                "gwm"
                                                        ) {
                                                            Image(
                                                                    painter =
                                                                            painterResource(
                                                                                    id =
                                                                                            when (config.substituteIcon
                                                                                            ) {
                                                                                                "youtube" ->
                                                                                                        R.drawable
                                                                                                                .ic_youtube_default
                                                                                                "youtube_music" ->
                                                                                                        R.drawable
                                                                                                                .ic_youtube_music_default
                                                                                                "gwm" ->
                                                                                                        R.drawable
                                                                                                                .ic_gwm
                                                                                                else ->
                                                                                                        R.drawable
                                                                                                                .ic_youtube_default
                                                                                            }
                                                                            ),
                                                                    contentDescription = "App Icon",
                                                                    modifier = Modifier.size(34.dp)
                                                            )
                                                        } else if (substituteIconVector != null) {
                                                            val iconTint =
                                                                    config.iconColor
                                                                            .toComposeColor()
                                                            Icon(
                                                                    substituteIconVector,
                                                                    contentDescription = "App Icon",
                                                                    tint = iconTint,
                                                                    modifier = Modifier.size(34.dp)
                                                            )
                                                        } else {
                                                            if (resolved.icon != null) {
                                                                AsyncImage(
                                                                        model = resolved.icon,
                                                                        contentDescription =
                                                                                resolved.label,
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        34.dp
                                                                                ),
                                                                        contentScale =
                                                                                ContentScale.Fit
                                                                )
                                                            } else {
                                                                when {
                                                                    config.packageName.contains(
                                                                            "androidauto"
                                                                    ) -> {
                                                                        AsyncImage(
                                                                                model =
                                                                                        R.drawable
                                                                                                .ic_android_auto_default,
                                                                                contentDescription =
                                                                                        resolved.label,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                34.dp
                                                                                        ),
                                                                                contentScale =
                                                                                        ContentScale
                                                                                                .Fit
                                                                        )
                                                                    }
                                                                    config.packageName.contains(
                                                                            "carplay"
                                                                    ) -> {
                                                                        AsyncImage(
                                                                                model =
                                                                                        R.drawable
                                                                                                .ic_carplay_default,
                                                                                contentDescription =
                                                                                        resolved.label,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                34.dp
                                                                                        ),
                                                                                contentScale =
                                                                                        ContentScale
                                                                                                .Fit
                                                                        )
                                                                    }
                                                                    else -> {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Apps,
                                                                                contentDescription =
                                                                                        resolved.label,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                34.dp
                                                                                        ),
                                                                                tint = Color.White
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        Column {
                                                            Text(
                                                                    text =
                                                                            if (!config.customName
                                                                                            .isNullOrEmpty()
                                                                            )
                                                                                    config.customName
                                                                            else resolved.label,
                                                                    color = Color.White,
                                                                    fontSize = 15.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                    text =
                                                                            "Display: ${config.displayId}",
                                                                    color = Color(0xFFB0B8C4),
                                                                    fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    Row(
                                                            horizontalArrangement =
                                                                    Arrangement.spacedBy(10.dp),
                                                            verticalAlignment =
                                                                    Alignment.CenterVertically
                                                    ) {
                                                        // KILL BUTTON WITH TEXT
                                                        Box(
                                                                modifier =
                                                                        Modifier.height(40.dp)
                                                                                .background(
                                                                                        Color(
                                                                                                        0xFFFFB300
                                                                                                )
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.15f
                                                                                                ),
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                                )
                                                                                .border(
                                                                                        BorderStroke(
                                                                                                1.dp,
                                                                                                Color(
                                                                                                                0xFFFFB300
                                                                                                        )
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.3f
                                                                                                        )
                                                                                        ),
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                                )
                                                                                .clickable(
                                                                                        enabled =
                                                                                                allClusterFunctionsEnabled
                                                                                ) {
                                                                                    scope.launch {
                                                                                        DisplayAppLauncher
                                                                                                .killApp(
                                                                                                        config.packageName
                                                                                                )
                                                                                    }
                                                                                }
                                                                                .padding(
                                                                                        horizontal =
                                                                                                12.dp,
                                                                                        vertical =
                                                                                                0.dp
                                                                                ),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                    text = "Kill",
                                                                    color = Color(0xFFFFB300),
                                                                    fontSize = 14.sp,
                                                                    fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        // DELETE BUTTON (MATCHING HEIGHT AND STYLE)
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(
                                                                                        width =
                                                                                                40.dp,
                                                                                        height =
                                                                                                40.dp
                                                                                )
                                                                                .background(
                                                                                        Color(
                                                                                                        0xFFFF4B4B
                                                                                                )
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.15f
                                                                                                ),
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                                )
                                                                                .border(
                                                                                        BorderStroke(
                                                                                                1.dp,
                                                                                                Color(
                                                                                                                0xFFFF4B4B
                                                                                                        )
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.3f
                                                                                                        )
                                                                                        ),
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                                )
                                                                                .clickable(
                                                                                        enabled =
                                                                                                allClusterFunctionsEnabled
                                                                                ) {
                                                                                    DisplayAppLauncher
                                                                                            .deleteConfig(
                                                                                                    config.packageName
                                                                                            )
                                                                                    configs =
                                                                                            DisplayAppLauncher
                                                                                                    .getAllConfigs()
                                                                                },
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                    imageVector =
                                                                            Icons.Default.Delete,
                                                                    contentDescription = "Remover",
                                                                    tint = Color(0xFFFF4B4B),
                                                                    modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                // B. Middle Positioning / Dimension details
                                                Box(
                                                        modifier =
                                                                Modifier.background(
                                                                                Color(0xFF13151A),
                                                                                RoundedCornerShape(
                                                                                        6.dp
                                                                                )
                                                                        )
                                                                        .padding(
                                                                                horizontal = 8.dp,
                                                                                vertical = 4.dp
                                                                        )
                                                ) {
                                                    Text(
                                                            text =
                                                                    "Pos: ${config.x},${config.y} | Dim: ${config.width}x${config.height}",
                                                            color = Color(0xFFB0B8C4),
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                // C. Bottom Buttons Row (Trazer / Enviar)
                                                Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                            onClick = {
                                                                scope.launch {
                                                                    DisplayAppLauncher
                                                                            .launchOnMainDisplay(
                                                                                    config
                                                                            )
                                                                }
                                                            },
                                                            enabled = allClusterFunctionsEnabled,
                                                            modifier =
                                                                    Modifier.weight(1f)
                                                                            .height(44.dp),
                                                            shape = RoundedCornerShape(8.dp),
                                                            border =
                                                                    BorderStroke(
                                                                            1.dp,
                                                                            Color(0xFF4A9EFF)
                                                                    ),
                                                            contentPadding =
                                                                    PaddingValues(
                                                                            horizontal = 4.dp,
                                                                            vertical = 0.dp
                                                                    ),
                                                            colors =
                                                                    ButtonDefaults
                                                                            .outlinedButtonColors(
                                                                                    contentColor =
                                                                                            Color(
                                                                                                    0xFF4A9EFF
                                                                                            )
                                                                            )
                                                    ) {
                                                        Row(
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(4.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                    imageVector =
                                                                            Icons.Default.ArrowBack,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(20.dp)
                                                            )
                                                            Text(
                                                                    "Trazer",
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    Button(
                                                            onClick = {
                                                                scope.launch {
                                                                    DisplayAppLauncher
                                                                            .sendToDisplay(config)
                                                                }
                                                            },
                                                            enabled = allClusterFunctionsEnabled,
                                                            modifier =
                                                                    Modifier.weight(1f)
                                                                            .height(44.dp),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding =
                                                                    PaddingValues(
                                                                            horizontal = 4.dp,
                                                                            vertical = 0.dp
                                                                    ),
                                                            colors =
                                                                    ButtonDefaults.buttonColors(
                                                                            containerColor =
                                                                                    Color(
                                                                                            0xFF4A9EFF
                                                                                    ),
                                                                            contentColor =
                                                                                    Color.White
                                                                    )
                                                    ) {
                                                        Row(
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(4.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                    imageVector =
                                                                            Icons.Default
                                                                                    .ArrowForward,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(20.dp)
                                                            )
                                                            Text(
                                                                    "Enviar",
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // REGISTRATION DIALOG FOR REVISIONS
    if (showRegisterDialog) {
        AlertDialog(
                onDismissRequest = { showRegisterDialog = false },
                containerColor = Color(0xFF1E2228),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = {
                    Text(
                            if (revisionHistory.isEmpty()) {
                                "Registrar Compra ou Revisão"
                            } else {
                                "Registrar Revisão"
                            },
                            fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                                if (revisionHistory.isEmpty()) {
                                    "Informe os dados da revisão atual para calcular a próxima automaticamente. Caso deseje registrar a data da compra, insira a km como zero (0)."
                                } else {
                                    "Informe os dados da revisão atual para calcular a próxima automaticamente."
                                },
                                color = Color(0xFFB0B8C4),
                                fontSize = 14.sp
                        )

                        StyledTextField(
                                value = tempKm,
                                onValueChange = {
                                    if (it.isEmpty() || it.toIntOrNull() != null) tempKm = it
                                },
                                label = { Text("Kilometragem Atual") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions =
                                        KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Column {
                            Text(
                                    if (tempKm == "0") {
                                        "Data de Compra"
                                    } else {
                                        "Data de Revisão"
                                    },
                                    color = Color(0xFFB0B8C4),
                                    fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                    onClick = { showDatePickerForRegister = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                    contentColor = Color.White
                                            ),
                                    border = BorderStroke(1.dp, Color(0xFF3A3F47)),
                                    shape = RoundedCornerShape(8.dp)
                            ) {
                                val displayDate =
                                        if (tempDate > 0L) dateFormatter.format(tempDate)
                                        else "Clique para definir"
                                Text(displayDate)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                val km = tempKm.toIntOrNull() ?: 0
                                if (km >= 0 && tempDate > 0L) {
                                    val newEntry = RevisionEntry(km, tempDate)
                                    val updated = (revisionHistory + newEntry).sortedBy { it.km }
                                    revisionHistory = updated
                                    saveRevisionHistory(prefs, updated)
                                    showRegisterDialog = false
                                    tempKm = ""
                                    tempDate = 0L
                                }
                            },
                            colors =
                                    ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF)),
                            enabled =
                                    tempKm.isNotBlank() &&
                                            tempKm.toIntOrNull() != null &&
                                            tempDate > 0L
                    ) { Text("Confirmar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showRegisterDialog = false }) {
                        Text("Cancelar", color = Color(0xFFB0B8C4))
                    }
                }
        )
    }

    if (showDatePickerForRegister) {
        val calendar = Calendar.getInstance()
        if (tempDate > 0L) {
            calendar.timeInMillis = tempDate
        }
        LaunchedEffect(Unit) {
            val dialog =
                    DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val cal = Calendar.getInstance()
                                cal.set(year, month, day)
                                tempDate = cal.timeInMillis
                                showDatePickerForRegister = false
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                    )
            dialog.setOnDismissListener { showDatePickerForRegister = false }
            dialog.show()
        }
    }

    if (showVirtualClusterWarningDialog) {
        AlertDialog(
                onDismissRequest = { showVirtualClusterWarningDialog = false },
                containerColor = Color(0xFF1E2228),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = {
                    Text(
                            "Aviso Importante",
                            fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                                "Este painel virtual é renderizado pela multimidia, ficando sujeita a garglos de processamento causando eventuais discrepancias ou delays entre as informações reais e as disponibilizadas. Além disto, a velocidade informada pode ter uma pequena variação.",
                                color = Color(0xFFB0B8C4),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                        )
                        Text(
                                "Confirme e aceite os riscos e condições.",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                        )
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                enableMask = true
                                prefs.edit {
                                    putBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, true)
                                }
                                showVirtualClusterWarningDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF))
                    ) {
                        Text("Aceitar", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                showVirtualClusterWarningDialog = false
                            }
                    ) {
                        Text("Recusar", color = Color(0xFFFF4B4B), fontWeight = FontWeight.Bold)
                    }
                }
        )
    }

    if (showConfigDialog) {
        DisplayAppConfigDialog(
                existingConfig = activeEditConfig,
                onDismiss = { showConfigDialog = false },
                onSave = { updated ->
                    DisplayAppLauncher.saveConfig(updated)
                    configs = DisplayAppLauncher.getAllConfigs()
                    showConfigDialog = false
                }
        )
    }
}

// EDITOR COMPONENT FOR NEW OR EXISTING CONFIGURATIONS
@Composable
fun AppEditorSection(initialConfig: DisplayAppConfig?, onSave: (DisplayAppConfig) -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var selectedDisplay by remember {
        mutableStateOf(
                initialConfig?.let {
                    when (it.displayId) {
                        1 ->
                                DisplayInfo(
                                        1,
                                        "Display 1: Cluster de instumentos, atrás do ADAS e outras informações"
                                )
                        4 -> DisplayInfo(4, "HUD")
                        else ->
                                DisplayInfo(
                                        3,
                                        "Display 3: Cluster de instumentos, por cima do ADAS e outras informações"
                                )
                    }
                }
                        ?: DisplayInfo(
                                3,
                                "Display 3: Cluster de instumentos, por cima do ADAS e outras informações"
                        )
        )
    }
    var posX by remember { mutableStateOf(initialConfig?.x ?: 0) }
    var posY by remember { mutableStateOf(initialConfig?.y ?: 0) }
    var sizeW by remember { mutableStateOf(initialConfig?.width ?: 1920) }
    var sizeH by remember { mutableStateOf(initialConfig?.height ?: 720) }
    var customName by remember { mutableStateOf(initialConfig?.customName ?: "") }
    var overrideThemeDimensions by remember {
        mutableStateOf(initialConfig?.overrideThemeDimensions ?: false)
    }
    var selectedSubIcon by remember { mutableStateOf(initialConfig?.substituteIcon) }
    var selectedIconColor by remember { mutableStateOf(initialConfig?.iconColor ?: "#FFFFFF") }

    val substituteIcons = remember {
        listOf(
                "youtube" to "YouTube",
                "youtube_music" to "YT Music",
                "gwm" to "GWM",
                "nav" to "Navegação",
                "music" to "Música",
                "video" to "Vídeo",
                "settings" to "Configurações",
                "haval" to "Carro",
                "game" to "Jogo",
                "tv" to "TV",
                "phone" to "Telefone",
                "chat" to "Chat",
                "map_alt" to "Mapa Alternativo"
        )
    }

    LaunchedEffect(initialConfig) {
        initialConfig?.let {
            val context = br.com.redesurftank.App.getDeviceProtectedContext()
            val resolved = DisplayAppLauncher.resolveAppInfo(context, it.packageName, it.customName)
            selectedApp =
                    InstalledAppInfo(
                            it.packageName,
                            it.activityName ?: "",
                            resolved.label,
                            resolved.icon
                    )
        }
    }

    val displays = remember {
        listOf(
                DisplayInfo(
                        1,
                        "Display 1: Cluster de instumentos, atrás do ADAS e outras informações"
                ),
                DisplayInfo(
                        3,
                        "Display 3: Cluster de instumentos, por cima do ADAS e outras informações"
                ),
                DisplayInfo(4, "HUD")
        )
    }

    var displayExpanded by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showInterconnectionConfirmDialog by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var previewActive by remember { mutableStateOf(false) }

    val resolution =
            remember(selectedDisplay) {
                when (selectedDisplay.id) {
                    4 -> 854 to 480
                    else -> 1920 to 720
                }
            }

    // Auto-constrain bounds if display changes
    LaunchedEffect(selectedDisplay) {
        posX = posX.coerceIn(0, resolution.first)
        posY = posY.coerceIn(0, resolution.second)
        sizeW = sizeW.coerceIn(100, resolution.first)
        sizeH = sizeH.coerceIn(100, resolution.second)
    }

    val configuredPackages = remember {
        DisplayAppLauncher.getAllConfigs().map { it.packageName }.toSet()
    }

    val currentConfig = {
        selectedApp?.let { app ->
            DisplayAppConfig(
                    packageName = app.packageName,
                    activityName = app.activityName,
                    displayId = selectedDisplay.id,
                    x = posX,
                    y = posY,
                    width = sizeW,
                    height = sizeH,
                    forceFocus = false,
                    customName = customName,
                    overrideThemeDimensions = overrideThemeDimensions,
                    substituteIcon = selectedSubIcon,
                    iconColor = selectedIconColor
            )
        }
    }

    // Live resize effect while adjusting sliders
    LaunchedEffect(
            posX,
            posY,
            sizeW,
            sizeH,
            overrideThemeDimensions,
            selectedSubIcon,
            selectedIconColor
    ) {
        if (previewActive && selectedApp != null) {
            currentConfig()?.let { config -> DisplayAppLauncher.launchApp(config) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // App Select Row
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Aplicativo", color = Color(0xFFB0B8C4), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(Color(0xFF2A2F37), RoundedCornerShape(8.dp))
                                        .clickable { showAppPicker = true }
                                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                            selectedApp?.label ?: "Selecionar Aplicativo...",
                            color = if (selectedApp != null) Color.White else Color(0xFF808080),
                            fontSize = 14.sp
                    )
                }
            }

            // Custom name/rename button
            if (selectedApp != null) {
                IconButton(
                        onClick = { showRenameDialog = true },
                        modifier =
                                Modifier.padding(top = 18.dp)
                                        .size(44.dp)
                                        .background(Color(0xFF2A2F37), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                            imageVector =
                                    if (customName.isBlank()) Icons.Default.EditNote
                                    else Icons.Default.Label,
                            contentDescription = "Nome Customizado",
                            tint = if (customName.isBlank()) Color.White else Color(0xFF4A9EFF)
                    )
                }
            }
        }

        // Display Selection
        Column {
            Text("Tela de Destino", color = Color(0xFFB0B8C4), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Box {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(Color(0xFF2A2F37), RoundedCornerShape(8.dp))
                                        .clickable { displayExpanded = true }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedDisplay.name, color = Color.White, fontSize = 14.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                }
                DropdownMenu(
                        expanded = displayExpanded,
                        onDismissRequest = { displayExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.5f).background(Color(0xFF1E2228))
                ) {
                    displays.forEach { disp ->
                        DropdownMenuItem(
                                text = { Text(disp.name, color = Color.White) },
                                onClick = {
                                    selectedDisplay = disp
                                    displayExpanded = false
                                }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF2C3139))

        // Override Theme Dimensions
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Dimensões", color = Color(0xFFB0B8C4), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(Color(0xFF2A2F37), RoundedCornerShape(8.dp))
                                    .clickable {
                                        overrideThemeDimensions = !overrideThemeDimensions
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                        "Override de Dimensões",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                )
                Switch(
                        checked = overrideThemeDimensions,
                        onCheckedChange = { overrideThemeDimensions = it },
                        modifier = Modifier.scale(0.8f),
                        colors =
                                SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF4A9EFF)
                                )
                )
            }
        }

        if (overrideThemeDimensions || selectedDisplay.id != 3) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Resolution info
                Text(
                        "Resolução: ${resolution.first} x ${resolution.second} | Pos: $posX,$posY",
                        color = Color(0xFF808080),
                        fontSize = 11.sp
                )

                // Position sliders
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SliderWithLabel(
                                label = "Posição X",
                                value = posX,
                                range = 0..resolution.first,
                                onValueChange = { posX = it }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SliderWithLabel(
                                label = "Posição Y",
                                value = posY,
                                range = 0..resolution.second,
                                onValueChange = { posY = it },
                                specialSnap = 135
                        )
                    }
                }

                // Size sliders
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SliderWithLabel(
                                label = "Largura",
                                value = sizeW,
                                range = 100..resolution.first,
                                onValueChange = { sizeW = it }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SliderWithLabel(
                                label = "Altura",
                                value = sizeH,
                                range = 100..resolution.second,
                                onValueChange = { sizeH = it }
                        )
                    }
                }
            }
        }

        // Substitute Icon Selection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Ícone Substituto", color = Color(0xFFB0B8C4), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Box(
                            modifier =
                                    Modifier.size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                    if (selectedSubIcon == null) Color(0xFF4A9EFF)
                                                    else Color(0xFF2A2F37)
                                            )
                                            .clickable { selectedSubIcon = null }
                                            .padding(4.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                                "Padrão",
                                color = Color.White,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center
                        )
                    }
                }
                items(substituteIcons) { (id, label) ->
                    val isSelected = selectedSubIcon == id
                    Box(
                            modifier =
                                    Modifier.size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                    if (isSelected) Color(0xFF4A9EFF)
                                                    else Color(0xFF2A2F37)
                                            )
                                            .clickable { selectedSubIcon = id }
                                            .padding(4.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        if (id == "youtube" || id == "youtube_music" || id == "gwm") {
                            Image(
                                    painter =
                                            painterResource(
                                                    id =
                                                            when (id) {
                                                                "youtube" ->
                                                                        R.drawable
                                                                                .ic_youtube_default
                                                                "youtube_music" ->
                                                                        R.drawable
                                                                                .ic_youtube_music_default
                                                                "gwm" -> R.drawable.ic_gwm
                                                                else ->
                                                                        R.drawable
                                                                                .ic_youtube_default
                                                            }
                                            ),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Icon(
                                    imageVector =
                                            when (id) {
                                                "nav" -> Icons.Default.Place
                                                "music" -> Icons.Default.PlayArrow
                                                "video" -> Icons.Default.Movie
                                                "settings" -> Icons.Default.Settings
                                                "haval" -> Icons.Default.DirectionsCar
                                                "game" -> Icons.Default.SportsEsports
                                                "tv" -> Icons.Default.Tv
                                                "phone" -> Icons.Default.Phone
                                                "chat" -> Icons.Default.Chat
                                                "map_alt" -> Icons.Default.Map
                                                else -> Icons.Default.Android
                                            },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Consolidated Color Selector
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Cor de Destaque", color = Color(0xFFB0B8C4), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            val colorOptions =
                    listOf(
                            "#FFFFFF",
                            "#ECEFF1",
                            "#FF0000",
                            "#FF4B4B",
                            "#00FF00",
                            "#0000FF",
                            "#4A9EFF",
                            "#90CAF9",
                            "#FFFF00",
                            "#FF00FF",
                            "#00FFFF",
                            "#FFA500",
                            "#800080",
                            "#808080"
                    )
            LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                items(colorOptions) { colorHex ->
                    val color =
                            try {
                                Color(android.graphics.Color.parseColor(colorHex))
                            } catch (_: Exception) {
                                Color.White
                            }
                    Box(
                            modifier =
                                    Modifier.size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                    width =
                                                            if (selectedIconColor.uppercase() ==
                                                                            colorHex.uppercase()
                                                            )
                                                                    2.dp
                                                            else 1.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                            )
                                            .clickable { selectedIconColor = colorHex }
                    )
                }
            }
        }

        // Live preview status
        if (previewActive && selectedApp != null) {
            Text(
                    "Preview ativo — ajuste os sliders e veja em tempo real",
                    color = Color(0xFF4A9EFF),
                    fontSize = 12.sp
            )
        }

        // Action buttons
        Spacer(Modifier.height(8.dp))
        Button(
                onClick = { currentConfig()?.let { onSave(it) } },
                enabled = selectedApp != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
        ) { Text("Salvar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }

    if (showAppPicker) {
        AppPickerDialog(
                alreadyConfigured = configuredPackages,
                onDismiss = { showAppPicker = false },
                onAppSelected = { app ->
                    if (app.packageName == "com.ts.androidauto.app" ||
                                    app.packageName == "com.ts.carplay.app"
                    ) {
                        showInterconnectionConfirmDialog = app
                        showAppPicker = false
                    } else {
                        selectedApp = app
                        showAppPicker = false
                        previewActive = true
                        scope.launch {
                            DisplayAppLauncher.launchApp(
                                    DisplayAppConfig(
                                            packageName = app.packageName,
                                            activityName = app.activityName,
                                            displayId = selectedDisplay.id,
                                            x = posX,
                                            y = posY,
                                            width = sizeW,
                                            height = sizeH
                                    )
                            )
                        }
                    }
                }
        )
    }

    if (showInterconnectionConfirmDialog != null) {
        val app = showInterconnectionConfirmDialog!!
        val locale = Locale.getDefault().language
        val isEn = locale == "en"

        val title = if (isEn) "Compatibility Warning" else "Aviso de Compatibilidade"
        val proceedText = if (isEn) "Proceed" else "Prosseguir"
        val abortText = if (isEn) "Abort" else "Abortar"

        AlertDialog(
                onDismissRequest = { showInterconnectionConfirmDialog = null },
                containerColor = Color(0xFF1E2228),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = {
                    Text(
                            text = title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (app.packageName == "com.ts.androidauto.app") {
                            val mainWarning =
                                    if (isEn) {
                                        "Android Auto support is experimental and has some important limitations:"
                                    } else {
                                        "O suporte ao Android Auto é experimental e possui algumas limitações importantes:"
                                    }
                            val limit1 =
                                    if (isEn) {
                                        "Android Auto content does not resize; it is only cropped."
                                    } else {
                                        "O conteúdo do Android Auto não se redimensiona, apenas é recortado (crop)."
                                    }
                            val limit2 =
                                    if (isEn) {
                                        "Clicking anywhere on the MMI causes Android Auto to lose focus and the screen to go black. We have a workaround that attempts to restore focus automatically, but it may fail occasionally, requiring you to click the Android Auto icon in the car to restore its focus."
                                    } else {
                                        "Ao clicar em qualquer lugar na MMI, o Android Auto perde o foco e a tela fica preta. Temos uma solução alternativa para tentar restaurar o foco automaticamente, mas ela pode falhar às vezes, exigindo que você clique no ícone do Android Auto no carro para restaurar seu foco."
                                    }
                            val question =
                                    if (isEn) {
                                        "Do you want to proceed anyway?"
                                    } else {
                                        "Deseja prosseguir assim mesmo?"
                                    }

                            Text(
                                    text = mainWarning,
                                    color = Color(0xFFB0B8C4),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                            )
                            Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                            "•",
                                            color = Color(0xFF4A9EFF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                    )
                                    Text(
                                            text = limit1,
                                            color = Color(0xFFB0B8C4),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                    )
                                }
                                Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                            "•",
                                            color = Color(0xFF4A9EFF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                    )
                                    Text(
                                            text = limit2,
                                            color = Color(0xFFB0B8C4),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                    )
                                }
                            }
                            Text(
                                    text = question,
                                    color = Color(0xFFB0B8C4),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                            )
                        } else {
                            val carplayWarning =
                                    if (isEn) {
                                        "Apple CarPlay support has not been tested in this version and correct operation is not guaranteed. Are you sure you want to continue?"
                                    } else {
                                        "O suporte ao Apple CarPlay ainda não foi testado nesta versão e não garantimos seu correto funcionamento. Tem certeza que deseja continuar?"
                                    }
                            Text(
                                    text = carplayWarning,
                                    color = Color(0xFFB0B8C4),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                selectedApp = app
                                showInterconnectionConfirmDialog = null
                                previewActive = true
                                scope.launch {
                                    DisplayAppLauncher.launchApp(
                                            DisplayAppConfig(
                                                    packageName = app.packageName,
                                                    activityName = app.activityName,
                                                    displayId = selectedDisplay.id,
                                                    x = posX,
                                                    y = posY,
                                                    width = sizeW,
                                                    height = sizeH
                                            )
                                    )
                                }
                            },
                            colors =
                                    ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF)),
                            shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                                proceedText,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInterconnectionConfirmDialog = null }) {
                        Text(
                                abortText,
                                color = Color(0xFFFF4B4B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                        )
                    }
                }
        )
    }

    if (showRenameDialog) {
        var tempName by remember { mutableStateOf(customName) }
        AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = {
                    Text("Nome Customizado", color = Color.White, fontWeight = FontWeight.Bold)
                },
                containerColor = Color(0xFF1E2228),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                                "Defina um nome customizado para este atalho:",
                                color = Color(0xFFB0B8C4),
                                fontSize = 14.sp
                        )
                        TextField(
                                value = tempName,
                                onValueChange = { tempName = it },
                                placeholder = {
                                    Text(
                                            selectedApp?.label ?: "Nome original",
                                            color = Color(0xFF808080)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF2A2F37),
                                                unfocusedContainerColor = Color(0xFF2A2F37),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedIndicatorColor = Color(0xFF4A9EFF),
                                                unfocusedIndicatorColor = Color(0xFF3A3F47)
                                        )
                        )
                        if (tempName.isNotBlank()) {
                            TextButton(
                                    onClick = { tempName = "" },
                                    modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                        "Resetar para o padrão",
                                        color = Color(0xFFFF4B4B),
                                        fontSize = 12.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                customName = tempName
                                showRenameDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF))
                    ) { Text("OK", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancelar", color = Color(0xFFB0B8C4))
                    }
                }
        )
    }
}

@Composable
fun DisplayAppConfigDialog(
        existingConfig: DisplayAppConfig?,
        onDismiss: () -> Unit,
        onSave: (DisplayAppConfig) -> Unit
) {
    androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties =
                    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
                modifier =
                        Modifier.fillMaxWidth(0.5f)
                                .fillMaxHeight(0.8f)
                                .padding(16.dp)
                                .border(1.dp, Color(0xFF2C3139), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text =
                                    if (existingConfig != null) "Editar Configuração"
                                    else "Nova Configuração",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color(0xFF2C3139))

                Box(
                        modifier =
                                Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                ) { AppEditorSection(initialConfig = existingConfig, onSave = onSave) }
            }
        }
    }
}

@Composable
fun SliderWithLabel(
        label: String,
        value: Int,
        range: IntRange,
        onValueChange: (Int) -> Unit,
        step: Int = 1,
        specialSnap: Int? = null
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFFB0B8C4), fontSize = 12.sp)
            Text("$value", color = Color.White, fontSize = 12.sp)
        }
        Slider(
                value = value.toFloat(),
                onValueChange = {
                    var snapped = (kotlin.math.round(it / step) * step).toInt()
                    val snapTolerance = if (step == 1) 10 else step
                    if (specialSnap != null &&
                                    kotlin.math.abs(snapped - specialSnap) <= snapTolerance
                    ) {
                        snapped = specialSnap
                    }
                    onValueChange(snapped.coerceIn(range))
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors =
                        SliderDefaults.colors(
                                thumbColor = Color(0xFF4A9EFF),
                                activeTrackColor = Color(0xFF4A9EFF),
                                inactiveTrackColor = Color(0xFF2C3139)
                        )
        )
    }
}

@Composable
fun ActionButton(
        text: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        color: Color,
        onClick: () -> Unit
) {
    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                    Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2F37))
                            .clickable { onClick() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color,
                modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppPickerItem(app: InstalledAppInfo, onClick: (InstalledAppInfo) -> Unit) {
    Column(
            modifier = Modifier.clickable { onClick(app) }.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (app.icon != null) {
            AsyncImage(
                    model = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Fit
            )
        } else {
            when {
                app.packageName.contains("androidauto") -> {
                    AsyncImage(
                            model = R.drawable.ic_android_auto_default,
                            contentDescription = app.label,
                            modifier = Modifier.size(44.dp),
                            contentScale = ContentScale.Fit
                    )
                }
                app.packageName.contains("carplay") -> {
                    AsyncImage(
                            model = R.drawable.ic_carplay_default,
                            contentDescription = app.label,
                            modifier = Modifier.size(44.dp),
                            contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = app.label,
                            modifier = Modifier.size(44.dp),
                            tint = Color.White
                    )
                }
            }
        }

        Text(
                text = app.label,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 2,
                minLines = 2,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AppPickerDialog(
        alreadyConfigured: Set<String> = emptySet(),
        onDismiss: () -> Unit,
        onAppSelected: (InstalledAppInfo) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val predefinedApps = remember {
        DisplayAppLauncher.PREDEFINED_APPS.map { config ->
            val resolved =
                    DisplayAppLauncher.resolveAppInfo(
                            context,
                            config.packageName,
                            config.customName
                    )
            InstalledAppInfo(
                    packageName = config.packageName,
                    activityName = config.activityName,
                    label = resolved.label,
                    icon = resolved.icon
            )
        }
    }

    val installedApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps =
                pm.queryIntentActivities(intent, 0)
                        .map { resolveInfo ->
                            InstalledAppInfo(
                                    packageName = resolveInfo.activityInfo.packageName,
                                    activityName = resolveInfo.activityInfo.name,
                                    label = resolveInfo.loadLabel(pm).toString(),
                                    icon =
                                            try {
                                                resolveInfo.loadIcon(pm)
                                            } catch (_: Exception) {
                                                null
                                            }
                            )
                        }
                        .toMutableList()

        apps.sortedBy { it.label.lowercase() }
    }

    var showManualInput by remember { mutableStateOf(false) }
    var manualPkg by remember { mutableStateOf("") }
    var manualActivity by remember { mutableStateOf("") }
    var manualLabel by remember { mutableStateOf("") }

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
                modifier =
                        Modifier.fillMaxWidth(0.30f)
                                .wrapContentHeight()
                                .border(1.dp, Color(0xFF1D2430), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
                shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "Selecionar Aplicativo",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = Color.White
                        )
                    }
                }

                if (showManualInput) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                                value = manualLabel,
                                onValueChange = { manualLabel = it },
                                placeholder = {
                                    Text("Nome do App (ex: YouTube)", color = Color(0xFF808080))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF2A2F37),
                                                unfocusedContainerColor = Color(0xFF2A2F37),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                        )
                        )
                        TextField(
                                value = manualPkg,
                                onValueChange = { manualPkg = it },
                                placeholder = {
                                    Text(
                                            "Pacote (ex: com.google.android.youtube)",
                                            color = Color(0xFF808080)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF2A2F37),
                                                unfocusedContainerColor = Color(0xFF2A2F37),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                        )
                        )
                        TextField(
                                value = manualActivity,
                                onValueChange = { manualActivity = it },
                                placeholder = {
                                    Text("Atividade (opcional)", color = Color(0xFF808080))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF2A2F37),
                                                unfocusedContainerColor = Color(0xFF2A2F37),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                        )
                        )
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                    onClick = { showManualInput = false },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF2A2F37)
                                            )
                            ) { Text("Cancelar", color = Color.White) }
                            Button(
                                    onClick = {
                                        if (manualPkg.isNotBlank() && manualLabel.isNotBlank()) {
                                            onAppSelected(
                                                    InstalledAppInfo(
                                                            manualPkg,
                                                            manualActivity,
                                                            manualLabel,
                                                            null
                                                    )
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = manualPkg.isNotBlank() && manualLabel.isNotBlank(),
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF4A9EFF)
                                            )
                            ) { Text("Adicionar", color = Color.White) }
                        }
                    }
                } else {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Buscar...", color = Color(0xFF808080)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF2A2F37),
                                                unfocusedContainerColor = Color(0xFF2A2F37),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedIndicatorColor = Color(0xFF4A9EFF),
                                                unfocusedIndicatorColor = Color(0xFF3A3F47)
                                        )
                        )
                        Button(
                                onClick = { showManualInput = true },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF2A2F37)
                                        ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                    "MANUAL",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val allAvailableApps: List<InstalledAppInfo> = remember {
                    val combined = predefinedApps + installedApps
                    if (alreadyConfigured.isNotEmpty()) {
                        combined.filter { it.packageName !in alreadyConfigured }
                    } else {
                        combined
                    }
                }

                val filteredApps =
                        if (searchQuery.isBlank()) allAvailableApps
                        else
                                allAvailableApps.filter {
                                    it.label.contains(searchQuery, ignoreCase = true) ||
                                            it.packageName.contains(searchQuery, ignoreCase = true)
                                }

                LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        modifier = Modifier.heightIn(max = 315.dp),
                        contentPadding = PaddingValues(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { items(filteredApps) { app -> AppPickerItem(app, onAppSelected) } }
            }
        }
    }
}

private fun String?.toComposeColor(): Color {
    if (this == null || !this.startsWith("#")) return Color.White
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (_: Exception) {
        Color.White
    }
}

private fun getSubstituteIconVector(
        substituteIcon: String?
): androidx.compose.ui.graphics.vector.ImageVector? {
    return when (substituteIcon) {
        "nav" -> Icons.Default.Place
        "music" -> Icons.Default.PlayArrow
        "video" -> Icons.Default.Movie
        "settings" -> Icons.Default.Tune
        "haval" -> Icons.Default.DirectionsCar
        "game" -> Icons.Default.SportsEsports
        "tv" -> Icons.Default.Tv
        "phone" -> Icons.Default.Phone
        "chat" -> Icons.Default.Chat
        "map_alt" -> Icons.Default.Map
        else -> null
    }
}
