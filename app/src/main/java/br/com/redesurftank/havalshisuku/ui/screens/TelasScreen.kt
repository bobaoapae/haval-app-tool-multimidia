package br.com.redesurftank.havalshisuku.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
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
import br.com.redesurftank.havalshisuku.managers.BackgroundSyncServer
import br.com.redesurftank.havalshisuku.managers.WallpaperLibrary
import br.com.redesurftank.havalshisuku.managers.WebImage
import br.com.redesurftank.havalshisuku.managers.WebImageProvider
import br.com.redesurftank.havalshisuku.managers.WebImageSearch
import br.com.redesurftank.havalshisuku.models.DisplayAppConfig
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.SolidBackgroundSpec
import br.com.redesurftank.havalshisuku.models.ThemeMetadata
import br.com.redesurftank.havalshisuku.models.ThemeConfig
import br.com.redesurftank.havalshisuku.ui.components.HsvColorPicker
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.components.StyledCard
import br.com.redesurftank.havalshisuku.ui.components.StyledTextField
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Busca inicial da aba Web — abre o diálogo já com resultados úteis para o cluster. */
private const val DEFAULT_WEB_QUERY = "gradient background"

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
    val borderColor = if (isSelected) ImpTokens.Accent else ImpTokens.TrackOff
    val backgroundColor = if (isSelected) ImpTokens.Container else ImpTokens.Container
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
                                    .background(ImpTokens.Container, RoundedCornerShape(8.dp)),
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
                                            .background(ImpTokens.Accent.copy(alpha = 0.2f))
                    )
                    Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ImpTokens.Accent,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                    )
                }

                // Delete button: top-left of the thumbnail, on a dark scrim for visibility
                if (canDelete) {
                    Box(
                            modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .clickable { onDelete() },
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Excluir",
                                tint = Color(0xFFFF4B4B),
                                modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // [Name (row1) | Status (row2)]  ...far right...  [update badge] [gear]
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // Row 1: Theme Name
                    Text(
                            text = theme.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )

                    // Row 2: Description / Status
                    Spacer(modifier = Modifier.height(2.dp))
                    if (isDownloading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = ImpTokens.Accent,
                                    strokeWidth = 1.5.dp
                            )
                            Text(
                                text = "Baixando...",
                                color = ImpTokens.Accent,
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
                                    tint = ImpTokens.Accent,
                                    modifier = Modifier.size(14.dp)
                            )
                            Text(
                                    text = "Baixar",
                                    color = ImpTokens.Accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                                text = if (theme.name == "Default") "Original" else if (theme.version.isNotBlank()) "Instalado v${theme.version}" else "Instalado",
                                color = if (isSelected) ImpTokens.Accent else ImpTokens.TextSecondary,
                                fontSize = 11.sp
                        )
                    }
                }

                // Push the update badge + gear to the far right
                Spacer(modifier = Modifier.weight(1f))

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
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Config gear on the far right of the name/status row
                if (theme.configurations.isNotEmpty() && isDownloaded) {
                    var showSettingsDialog by remember { mutableStateOf(false) }
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configurar Tema",
                        tint = Color(0xFF4A9EFF),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { showSettingsDialog = true }
                    )

                    if (showSettingsDialog) {
                        ThemeSettingsDialog(
                            theme = theme,
                            onDismiss = { showSettingsDialog = false }
                        )
                    }
                }
            }
        }
    }
}



/**
 * Everything that has to be put back to stock when the virtual panel is switched off.
 *
 * ACTIVE_CUSTOM_THEME is the sole source of truth for what HTML the cluster loads
 * (see InstrumentProjector2.getActiveCustomThemeName()), and custom themes are
 * authored assuming the mask is painted. Leaving one selected with the panel off
 * can leave the menu unusable, so fall back to the embedded Default theme.
 *
 * Default's own colour settings go back to stock too: with the panel off the menu
 * is drawn over the car's native cluster, and a customised palette clashes with it.
 * The stock values are whatever assets/Default/theme.xml declares, so this stays
 * correct as configurations are added or their defaults change.
 *
 * Values are written explicitly rather than removed: PreferencePushListener pushes
 * `sharedPreferences.all[key]` to the theme, and a removed key would push an empty
 * string instead of the colour the theme expects.
 */
private fun resetToStockDefaultTheme(context: Context, prefs: SharedPreferences) {
    val defaultTheme = ThemeManager.getInstance(context).getEmbeddedDefaultTheme()
    // A colour setting is either a literal swatch or the drive-mode override that
    // repaints them — leaving the latter on would re-tint the dials anyway.
    val colorConfigs =
            defaultTheme.configurations.filter {
                it.type.equals("color", ignoreCase = true) ||
                        it.stateVariable == "driveModeColors"
            }

    prefs.edit {
        putString(SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "")
        putString(SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, "Default")
        colorConfigs.forEach { config ->
            // Must match the key ThemeSettingsDialog scopes its saves with.
            putString(
                    "theme_config_${defaultTheme.folderName}_${config.stateVariable}",
                    config.defaultValue
            )
        }
        putLong(SharedPreferencesKeys.THEME_RELOAD_NONCE.key, System.currentTimeMillis())
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

    // Custom Display 1 Background States (default ON — prefer theme wallpaper)
    var enableCustomBg by remember {
        mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true))
    }
    var showBackgroundSettingsDialog by remember { mutableStateOf(false) }
    var localAssetList by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        // Seed first-run defaults: background ON + mode THEME
        if (!prefs.contains(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key)) {
            prefs.edit {
                putBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
                putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME")
                putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "")
            }
            enableCustomBg = true
        } else if (!prefs.contains(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key)) {
            prefs.edit {
                putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME")
            }
        }
        try {
            val list = context.assets.list("backgrounds")?.toList() ?: emptyList()
            localAssetList = list.filter {
                it.endsWith(".png", true) || it.endsWith(".jpg", true) || it.endsWith(".jpeg", true)
            }
        } catch (e: Exception) {
            Log.e("TelasTab", "Error listing background assets", e)
        }
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
        ThemeManager.getInstance(context).sanitizeActiveThemeContract(context)
        selectedTheme = prefs.getString(SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, "Default") ?: "Default"
        localThemes = ThemeManager.getInstance(context).getLocalThemes()
        if (githubThemes.isEmpty()) {
            isFetchingThemes = true
            try {
                githubThemes =
                        ThemeManager.getInstance(context)
                                .fetchThemesFromGithub(ThemeManager.THEME_REPO_URL)
            } catch (e: Exception) {
                // Startup fetch stays silent (no toast on screen entry), but always logs:
                // this used to be DEBUG-gated, which is why the car showed nothing at all.
                Log.e("TelasTab", "Error fetching themes on start", e)
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
                                color = ImpTokens.TextSecondary,
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
                                    // Same invariant as the Painel Virtual switch: whenever
                                    // the virtual cluster goes off, the theme goes back to
                                    // stock Default. Otherwise re-enabling the cluster
                                    // functions later would restore the projector with a
                                    // custom theme still selected and the mask off.
                                    enableMask = false
                                    selectedTheme = "Default"
                                    resetToStockDefaultTheme(context, prefs)
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
                                            checkedTrackColor = ImpTokens.Accent
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
                                color = ImpTokens.TextSecondary,
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
                                    selectedTheme = "Default"
                                    resetToStockDefaultTheme(context, prefs)
                                    prefs.edit {
                                        putBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, false)
                                    }
                                }
                            },
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = ImpTokens.Accent
                                    )
                    )
                }

                if (enableMask) {
                    HorizontalDivider(color = ImpTokens.TrackOff)

                    // Theme Selector - Horizontal compact carousel
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    "Tema do Painel (Toque para selecionar)",
                                    color = ImpTokens.TextSecondary,
                                    fontSize = 12.sp
                            )
                            if (isFetchingThemes) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = ImpTokens.Accent,
                                            strokeWidth = 2.dp
                                    )
                                    Text(
                                            text = "Buscando...",
                                            color = ImpTokens.Accent,
                                            fontSize = 12.sp
                                    )
                                }
                            } else {
                                // Labelled, full-height tap target: the bare 20.dp icon gave no
                                // hint of its purpose and was hard to hit while driving.
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    isFetchingThemes = true
                                                    scope.launch {
                                                        try {
                                                            localThemes = ThemeManager.getInstance(context).getLocalThemes()
                                                            val fetched = ThemeManager.getInstance(context)
                                                                    .fetchThemesFromGithub(ThemeManager.THEME_REPO_URL)
                                                            githubThemes = fetched
                                                            val updates = fetched.count { remote ->
                                                                localThemes.any { local ->
                                                                    local.folderName == remote.folderName &&
                                                                            local.version != remote.version
                                                                }
                                                            }
                                                            Toast.makeText(
                                                                    context,
                                                                    if (updates > 0) "$updates tema(s) com atualização disponível"
                                                                    else "Todos os temas estão atualizados",
                                                                    Toast.LENGTH_SHORT
                                                            ).show()
                                                        } catch (e: ThemeManager.ThemeFetchException) {
                                                            Log.e("TelasTab", "Error refreshing themes", e)
                                                            Toast.makeText(context, e.userMessage, Toast.LENGTH_LONG).show()
                                                        } catch (e: Exception) {
                                                            Log.e("TelasTab", "Error refreshing themes", e)
                                                            Toast.makeText(
                                                                    context,
                                                                    "Falha ao buscar atualizações.",
                                                                    Toast.LENGTH_LONG
                                                            ).show()
                                                        } finally {
                                                            isFetchingThemes = false
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = ImpTokens.Accent,
                                            modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                            text = "Buscar atualizações",
                                            color = ImpTokens.Accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val allThemes =
                                remember(githubThemes, localThemes) {
                                    val merged = mutableListOf<ThemeMetadata>()

                                    // Default ships embedded in the APK (res/raw/app.html) and its
                                    // metadata (theme.xml <configurations>) is dynamically parsed from res/raw/theme_default.xml.
                                    merged.add(ThemeManager.getInstance(context).getEmbeddedDefaultTheme())

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
                                                                version = local.version,
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

                                    merged.filter {
                                        ThemeManager.getInstance(context).isContractCompatible(it.contractVersion)
                                    }
                                }

                        val applyThemeWithBgCheck: (ThemeMetadata) -> Unit = { themeToApply ->
                            selectedTheme = themeToApply.name
                            val folderName = if (themeToApply.folderName == "Default" || themeToApply.name == "Default") "" else themeToApply.folderName
                            val hasThemeBg = themeToApply.background.isNotBlank() || themeToApply.backgroundAbsolutePath.isNotBlank()
                            val bgDisabled = !prefs.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
                            val relativeBg = themeToApply.background.ifBlank {
                                if (themeToApply.backgroundAbsolutePath.isNotBlank()) java.io.File(themeToApply.backgroundAbsolutePath).name else ""
                            }

                            prefs.edit {
                                putString(SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, themeToApply.name)
                                putString(SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, folderName)
                                putLong(SharedPreferencesKeys.THEME_RELOAD_NONCE.key, System.currentTimeMillis())

                                if (hasThemeBg) {
                                    val currentType = prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME") ?: "THEME"
                                    if (bgDisabled) {
                                        putBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
                                        putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME")
                                        if (relativeBg.isNotBlank()) {
                                            putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, relativeBg)
                                        }
                                    } else if (currentType.equals("THEME", ignoreCase = true) && relativeBg.isNotBlank()) {
                                        putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, relativeBg)
                                    }
                                }
                            }

                            if (hasThemeBg && bgDisabled) {
                                enableCustomBg = true
                            }
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
                                                    applyThemeWithBgCheck(theme)
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
                                                            val downloadedMeta =
                                                                    localThemes.firstOrNull {
                                                                        it.folderName == theme.folderName || it.name == theme.name
                                                                    } ?: theme
                                                            applyThemeWithBgCheck(downloadedMeta)
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
                                                            val downloadedMeta =
                                                                    localThemes.firstOrNull {
                                                                        it.folderName == theme.folderName || it.name == theme.name
                                                                    } ?: theme
                                                            applyThemeWithBgCheck(downloadedMeta)
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
                      // ── SIDE-BY-SIDE CARDS (50% / 50% width) ──
                HorizontalDivider(color = Color(0xFF2C3139))
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // ── LEFT CARD (40%): Background do Cluster Card (aligned with Exibir Odômetro below) ──
                    Column(
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxHeight()
                            .background(ImpTokens.TrackOff, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Background do Cluster",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (enableCustomBg) "Papel de parede do painel" else "Padrão do sistema",
                                    color = ImpTokens.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = enableCustomBg,
                                enabled = allClusterFunctionsEnabled,
                                onCheckedChange = { checked ->
                                    enableCustomBg = checked
                                    prefs.edit { putBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, checked) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF4A9EFF)
                                )
                            )
                        }

                        val activeFolder = remember(selectedTheme) {
                            prefs.getString(SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "") ?: ""
                        }
                        val themeMeta = remember(activeFolder, selectedTheme, localThemes) {
                            ThemeManager.getInstance(context).getThemeMetadata(activeFolder)
                                ?: localThemes.firstOrNull {
                                    it.name == selectedTheme || it.folderName == activeFolder
                                }
                        }

                        val bgThumb = remember(enableCustomBg, showBackgroundSettingsDialog, selectedTheme, activeFolder) {
                            if (!enableCustomBg) {
                                ClusterBgThumb.Empty(
                                    Icons.Default.VisibilityOff,
                                    "Fundo personalizado desligado",
                                    "Ative a chave acima para escolher um fundo"
                                )
                            } else {
                                val bgType = prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME") ?: "THEME"
                                val bgValue = prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "") ?: ""
                                when (bgType) {
                                    "THEME" -> {
                                        val bgPath = themeMeta?.backgroundAbsolutePath.orEmpty()
                                        if (bgPath.isNotEmpty() && java.io.File(bgPath).exists()) {
                                            ClusterBgThumb.Picture(java.io.File(bgPath))
                                        } else {
                                            // O projetor deixa o cluster transparente nesse caso: a miniatura
                                            // não deve fingir que existe um papel de parede.
                                            ClusterBgThumb.Empty(
                                                Icons.Default.ImageNotSupported,
                                                "O tema ativo não tem imagem de fundo",
                                                "Escolha uma cor ou imagem em TROCAR"
                                            )
                                        }
                                    }
                                    "PRESET" -> {
                                        if (bgValue.isNotBlank()) {
                                            val uploadedFile = java.io.File(BackgroundSyncServer.getUploadsDir(), bgValue)
                                            if (uploadedFile.exists()) ClusterBgThumb.Picture(uploadedFile)
                                            else ClusterBgThumb.Picture("file:///android_asset/backgrounds/$bgValue")
                                        } else {
                                            ClusterBgThumb.Empty(
                                                Icons.Default.Wallpaper,
                                                "Nenhum preset selecionado",
                                                "Escolha em TROCAR › Presets"
                                            )
                                        }
                                    }
                                    SolidBackgroundSpec.TYPE -> {
                                        val spec = SolidBackgroundSpec.parse(bgValue)
                                        if (spec != null) ClusterBgThumb.Solid(spec)
                                        else ClusterBgThumb.Empty(
                                            Icons.Default.Palette,
                                            "Cor inválida",
                                            "Escolha novamente em TROCAR › Cor"
                                        )
                                    }
                                    "FILE" -> {
                                        val file = bgValue.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
                                        when {
                                            file == null -> ClusterBgThumb.Empty(
                                                Icons.Default.Wallpaper,
                                                "Nenhuma imagem selecionada",
                                                "Escolha em TROCAR › Biblioteca"
                                            )
                                            !file.exists() -> ClusterBgThumb.Empty(
                                                Icons.Default.BrokenImage,
                                                "Imagem da biblioteca não encontrada",
                                                "Ela pode ter sido excluída — escolha outra"
                                            )
                                            else -> ClusterBgThumb.Picture(file)
                                        }
                                    }
                                    "IMAGE_URL" -> {
                                        if (bgValue.isNotBlank()) ClusterBgThumb.Picture(bgValue)
                                        else ClusterBgThumb.Empty(
                                            Icons.Default.Wallpaper,
                                            "Nenhuma imagem selecionada",
                                            "Escolha em TROCAR › Web"
                                        )
                                    }
                                    else -> ClusterBgThumb.Empty(
                                        Icons.Default.Wallpaper,
                                        "Nenhuma imagem selecionada",
                                        "Escolha um fundo em TROCAR"
                                    )
                                }
                            }
                        }

                        // Ultrawide Thumbnail Box (1920x720 ratio, filling 40% card width, right edge aligned with Exibir Odômetro below)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1920f / 720f)
                                .background(Color(0xFF13151A), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF3F4652), RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            ClusterBackgroundThumbContent(
                                state = bgThumb,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Overlay Button "TROCAR" pinned to Bottom-Right corner inside image
                            if (allClusterFunctionsEnabled && enableCustomBg) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color(0xD91E2228), RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFF4A9EFF), RoundedCornerShape(6.dp))
                                        .clickable { showBackgroundSettingsDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = Color(0xFF4A9EFF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "TROCAR",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── RIGHT CARD (60%): Inicialização & Consumo Card (aligned with Próxima Revisão below) ──
                    Column(
                        modifier = Modifier
                            .weight(0.60f)
                            .fillMaxHeight()
                            .background(Color(0xFF2A2F37), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Inicialização & Exibição",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "App de arranque e unidade de consumo",
                                color = Color(0xFFB0B8C4),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 1. App Padrão na Inicialização
                        Column {
                            Text(
                                "App Padrão na Inicialização",
                                color = Color(0xFFB0B8C4),
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .background(ImpTokens.Container, RoundedCornerShape(8.dp))
                                    .clickable(enabled = allClusterFunctionsEnabled) {
                                        appExpanded = true
                                    }
                                    .padding(horizontal = 14.dp),
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
                                                modifier = Modifier.size(22.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (defaultApp.isEmpty()) Icons.Default.Block else Icons.Default.Apps,
                                                contentDescription = null,
                                                tint = if (defaultApp.isEmpty()) ImpTokens.TextSecondary else ImpTokens.Accent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Text(resolvedApp.label, color = Color.White, fontSize = 13.sp)
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
                                    modifier = Modifier
                                        .fillMaxWidth(0.45f)
                                        .background(ImpTokens.Container)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Nenhum", color = Color.White, fontSize = 14.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = null,
                                                tint = ImpTokens.TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            defaultApp = ""
                                            prefs.edit { putString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, "") }
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
                                                prefs.edit { putString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, config.packageName) }
                                                appExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Unidade de Consumo de Combustível
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Unidade de Consumo",
                                color = ImpTokens.TextSecondary,
                                fontSize = 11.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val units = listOf("liters" to "Litros", "percent" to "Porcentagem")
                                units.forEach { (unitId, label) ->
                                    val isSelected = clusterFuelDisplayUnit == unitId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .background(
                                                if (isSelected) ImpTokens.Accent else ImpTokens.Container,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable(enabled = allClusterFunctionsEnabled) {
                                                clusterFuelDisplayUnit = unitId
                                                prefs.edit { putString(SharedPreferencesKeys.CLUSTER_FUEL_DISPLAY_UNIT.key, unitId) }
                                            }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = ImpTokens.TrackOff)
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
                                        ImpTokens.TrackOff,
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
                                            color = ImpTokens.TextSecondary,
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
                                                        checkedTrackColor = ImpTokens.Accent
                                                ),
                                        modifier = Modifier
                                            .padding(start = 16.dp, end = 8.dp)
                                            .scale(1.0f)
                                )
                            }

                            // Column 2 (Right Card): Next Revision inside card (60% width, always visible but disabled if toggle off)
                            val isCard2Enabled = enableOdometerAndRevision && allClusterFunctionsEnabled
                            val textDisabledColor = ImpTokens.ThumbOff

                            Row(
                                modifier = Modifier
                                    .weight(0.60f)
                                    .height(104.dp)
                                    .background(
                                        if (isCard2Enabled) ImpTokens.TrackOff else ImpTokens.Container,
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
                                            containerColor = ImpTokens.Accent,
                                            disabledContainerColor = ImpTokens.TrackOff
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
                                        color = if (isCard2Enabled) ImpTokens.TextSecondary else textDisabledColor,
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
                                .background(ImpTokens.TrackOff, RoundedCornerShape(8.dp))
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
                                            color = ImpTokens.TextMuted,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        revisionHistory.sortedByDescending { it.km }.forEach { entry ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        ImpTokens.Container,
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
                                                    color = ImpTokens.TextSecondary,
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

                HorizontalDivider(color = ImpTokens.TrackOff)

                Button(
                        onClick = {
                            activeEditConfig = null
                            showConfigDialog = true
                        },
                        enabled = allClusterFunctionsEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = ImpTokens.Accent),
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
                                                                    ImpTokens.TrackOff,
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
                                                                    ImpTokens.TrackOff
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
                                                                    color = ImpTokens.TextSecondary,
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
                                                                                ImpTokens.Container,
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
                                                            color = ImpTokens.TextSecondary,
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
                                                                            ImpTokens.Accent
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
                containerColor = ImpTokens.Container,
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
                                color = ImpTokens.TextSecondary,
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
                                    color = ImpTokens.TextSecondary,
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
                                    border = BorderStroke(1.dp, ImpTokens.TrackOff),
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
                                    ButtonDefaults.buttonColors(containerColor = ImpTokens.Accent),
                            enabled =
                                    tempKm.isNotBlank() &&
                                            tempKm.toIntOrNull() != null &&
                                            tempDate > 0L
                    ) { Text("Confirmar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showRegisterDialog = false }) {
                        Text("Cancelar", color = ImpTokens.TextSecondary)
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
                containerColor = ImpTokens.Container,
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
                                color = ImpTokens.TextSecondary,
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
                            colors = ButtonDefaults.buttonColors(containerColor = ImpTokens.Accent)
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

    if (showBackgroundSettingsDialog) {
        val activeFolder = prefs.getString(SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "") ?: ""
        val themeMeta = remember(activeFolder, selectedTheme, localThemes) {
            ThemeManager.getInstance(context).getThemeMetadata(activeFolder)
                ?: localThemes.firstOrNull {
                    it.name == selectedTheme || it.folderName == activeFolder
                }
        }
        ClusterBackgroundSettingsDialog(
            prefs = prefs,
            localAssetList = localAssetList,
            themeBackgroundPath = themeMeta?.backgroundAbsolutePath.orEmpty(),
            themeBackgroundLabel = themeMeta?.background?.ifBlank { null }
                ?: themeMeta?.name?.let { "$it (tema)" }
                ?: "Tema ativo",
            scope = scope,
            onDismiss = { showBackgroundSettingsDialog = false }
        )
    }
}

@Composable
fun ClusterBackgroundSettingsDialog(
    prefs: SharedPreferences,
    localAssetList: List<String>,
    themeBackgroundPath: String = "",
    themeBackgroundLabel: String = "Tema",
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    var customBgType by remember {
        mutableStateOf(prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME") ?: "THEME")
    }
    var customBgValue by remember {
        mutableStateOf(prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "") ?: "")
    }

    val initialBgType = remember { customBgType }
    val initialBgValue = remember { customBgValue }

    // Aba sendo navegada — independente do que está aplicado, para que apenas abrir uma aba
    // nunca mude o cluster; só clicar em uma miniatura muda.
    var selectedTab by remember { mutableStateOf(customBgType) }

    var currentView by remember { mutableStateOf("SETTINGS") } // "SETTINGS" or "SYNC"
    var uploadedRefreshToken by remember { mutableIntStateOf(0) }
    val libraryItems = remember(uploadedRefreshToken) {
        WallpaperLibrary.list()
    }
    val libraryFileNames = remember(libraryItems) {
        libraryItems.map { it.file.name }.toSet()
    }

    // ── Preview ao vivo: toda seleção é aplicada no cluster na hora e só vira definitiva
    // no OK. Cancelar / fechar restaura o que estava valendo ao abrir o diálogo.
    LaunchedEffect(customBgType, customBgValue) {
        if (customBgType == "THEME" || customBgValue.isNotBlank()) {
            prefs.edit {
                putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, customBgType)
                putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, customBgValue)
            }
        }
    }

    val revertPreview: () -> Unit = {
        customBgType = initialBgType
        customBgValue = initialBgValue
        prefs.edit {
            putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, initialBgType)
            putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, initialBgValue)
        }
    }

    // ── Cor sólida + vinheta ──
    val initialSolid = remember {
        SolidBackgroundSpec.parse(initialBgValue)
            ?.takeIf { initialBgType == SolidBackgroundSpec.TYPE }
            ?: SolidBackgroundSpec.DEFAULT
    }
    val initialHsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialSolid.color, it) }
    }
    var solidHue by remember { mutableFloatStateOf(initialHsv[0]) }
    var solidSat by remember { mutableFloatStateOf(initialHsv[1]) }
    var solidVal by remember { mutableFloatStateOf(initialHsv[2]) }
    var solidVignette by remember { mutableIntStateOf(initialSolid.vignette) }

    // Só grava (e portanto só aplica no cluster) ao soltar o dedo: arrastar no mapa
    // dispararia dezenas de escritas de preferência por segundo.
    val commitSolidColor: () -> Unit = {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(solidHue, solidSat, solidVal))
        customBgType = SolidBackgroundSpec.TYPE
        customBgValue = SolidBackgroundSpec(argb, solidVignette).encode()
    }

    // ── Busca na web (estado no nível do diálogo para sobreviver à troca de abas) ──
    val ioScope = rememberCoroutineScope()
    var webProvider by remember { mutableStateOf(WebImageProvider.WALLHAVEN) }
    var webQuery by remember { mutableStateOf(DEFAULT_WEB_QUERY) }
    var webResults by remember { mutableStateOf<List<WebImage>>(emptyList()) }
    var webPage by remember { mutableIntStateOf(1) }
    var webHasMore by remember { mutableStateOf(false) }
    var webLoading by remember { mutableStateOf(false) }
    var webLoadingMore by remember { mutableStateOf(false) }
    var webError by remember { mutableStateOf<String?>(null) }
    var webRetryToken by remember { mutableIntStateOf(0) }
    var webLoadedSignature by remember { mutableStateOf("") }
    var apiKeyRefreshToken by remember { mutableIntStateOf(0) }
    var favoriteBusyKey by remember { mutableStateOf<String?>(null) }
    var libraryMessage by remember { mutableStateOf<String?>(null) }
    val webGridState = rememberLazyGridState()

    val webApiKey = remember(webProvider, apiKeyRefreshToken) {
        WebImageSearch.getApiKey(prefs, webProvider)
    }
    val lockedProviders = remember(apiKeyRefreshToken) {
        WebImageProvider.entries
            .filter { it.requiresKey && WebImageSearch.getApiKey(prefs, it).isBlank() }
            .map { it.id }
            .toSet()
    }

    suspend fun loadWebPage(page: Int) {
        if (page == 1) {
            webLoading = true
            webError = null
        } else {
            webLoadingMore = true
        }
        try {
            val result = WebImageSearch.search(webProvider, webQuery, page, webApiKey)
            webResults = if (page == 1) result.images else webResults + result.images
            webPage = result.page
            webHasMore = result.hasMore && result.images.isNotEmpty()
            webError = null
        } catch (e: Exception) {
            if (page == 1) webResults = emptyList()
            webHasMore = false
            webError = e.message ?: "Falha ao buscar imagens."
        } finally {
            webLoading = false
            webLoadingMore = false
        }
    }

    val webTabActive = selectedTab == "IMAGE_URL"
    val webSignature = "${webProvider.id}|${webQuery.trim()}|$webRetryToken|${webApiKey.isNotBlank()}"

    LaunchedEffect(webTabActive, webSignature) {
        if (!webTabActive) return@LaunchedEffect
        if (webSignature == webLoadedSignature && webResults.isNotEmpty()) return@LaunchedEffect
        if (webProvider.requiresKey && webApiKey.isBlank()) {
            webResults = emptyList()
            webError = null
            return@LaunchedEffect
        }
        if (webQuery.isNotBlank()) delay(500) // debounce da digitação
        webLoadedSignature = webSignature
        loadWebPage(1)
        // Só depois de ter resultados: scrollToItem espera o primeiro layout da grade e
        // ficaria suspenso para sempre enquanto a grade não estiver composta.
        if (webResults.isNotEmpty()) webGridState.scrollToItem(0)
    }

    // Scroll infinito: carrega a próxima página ao chegar perto do fim da grade.
    val shouldLoadMoreWeb by remember {
        derivedStateOf {
            val lastVisible = webGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 && lastVisible >= webResults.size - 6
        }
    }
    // Os flags de carregamento não entram nas chaves: mudá-los aqui reiniciaria o próprio
    // efeito e cancelaria a requisição em andamento.
    LaunchedEffect(shouldLoadMoreWeb, webHasMore) {
        if (shouldLoadMoreWeb && webHasMore && !webLoading && !webLoadingMore) {
            loadWebPage(webPage + 1)
        }
    }

    LaunchedEffect(libraryMessage) {
        if (libraryMessage != null) {
            delay(2500)
            libraryMessage = null
        }
    }

    val localIp = remember { BackgroundSyncServer.getLocalIpAddress() ?: "127.0.0.1" }
    val portalUrl = "http://$localIp:8080"

    val syncServer = remember {
        BackgroundSyncServer {
            scope.launch {
                delay(500)
                customBgType = prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "PRESET") ?: "PRESET"
                customBgValue = prefs.getString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, "") ?: ""
                uploadedRefreshToken++
            }
        }
    }

    DisposableEffect(currentView) {
        if (currentView == "SYNC") {
            syncServer.start()
        } else {
            syncServer.stop()
        }
        onDispose {
            syncServer.stop()
        }
    }

    Dialog(
        onDismissRequest = {
            // Fechar sem confirmar equivale a cancelar: desfaz o preview.
            revertPreview()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.84f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFF4A9EFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentView == "SYNC") {
                    Text(
                        "Sincronizar pelo Celular",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    HorizontalDivider(color = Color(0xFF2C3139))
                    
                    Text(
                        "Conecte o seu celular na mesma rede Wi-Fi do carro (ou roteador) e escaneie o código abaixo:",
                        color = Color(0xFFB0B8C4),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${java.net.URLEncoder.encode(portalUrl, "UTF-8")}",
                            contentDescription = "QR Code de Sincronização",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Text(
                        text = "Ou acesse no navegador:\n$portalUrl",
                        color = Color(0xFF4A9EFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { currentView = "SETTINGS" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3139)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Voltar", color = Color.White)
                    }
                } else {
                    Text(
                        "Ajustes: Background do Cluster",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    HorizontalDivider(color = Color(0xFF2C3139))
                    
                    // Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "THEME" to "Tema",
                            SolidBackgroundSpec.TYPE to "Cor",
                            "PRESET" to "Presets",
                            "IMAGE_URL" to "Web",
                            "FILE" to "Biblioteca"
                        ).forEach { (type, label) ->
                            val isBrowsing = selectedTab == type
                            val isApplied = customBgType == type
                            Button(
                                onClick = { selectedTab = type },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBrowsing) Color(0xFF4A9EFF) else Color(0xFF2C3139),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isApplied) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Em uso",
                                            tint = if (isBrowsing) Color.White else Color(0xFF4A9EFF),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(label, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (selectedTab) {
                            "THEME" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Usa o papel de parede declarado pelo tema ativo (<background> no theme.xml). Padrão recomendado.",
                                        color = Color(0xFFB0B8C4),
                                        fontSize = 12.sp
                                    )
                                    val hasThemeBg = themeBackgroundPath.isNotBlank()
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .border(
                                                2.dp,
                                                if (customBgType == "THEME") Color(0xFF4A9EFF) else Color(0xFF2C3139),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                customBgType = "THEME"
                                                customBgValue = ""
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (hasThemeBg) {
                                                AsyncImage(
                                                    model = java.io.File(themeBackgroundPath),
                                                    contentDescription = themeBackgroundLabel,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "Tema ativo sem imagem de fundo",
                                                        color = Color(0xFFB0B8C4),
                                                        fontSize = 12.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.55f))
                                                    .align(Alignment.BottomCenter)
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (hasThemeBg) themeBackgroundLabel else "Sem background no tema",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            SolidBackgroundSpec.TYPE -> {
                                SolidColorTab(
                                    hue = solidHue,
                                    saturation = solidSat,
                                    brightness = solidVal,
                                    vignette = solidVignette,
                                    onHueChange = { solidHue = it },
                                    onSatValChange = { s, v ->
                                        solidSat = s
                                        solidVal = v
                                    },
                                    onArgbChange = { argb ->
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(argb, hsv)
                                        solidHue = hsv[0]
                                        solidSat = hsv[1]
                                        solidVal = hsv[2]
                                    },
                                    onVignetteChange = { solidVignette = it },
                                    onCommit = commitSolidColor
                                )
                            }
                            "PRESET" -> {
                                Column {
                                    Text("Presets Locais (assets/backgrounds/)", color = Color(0xFFB0B8C4), fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 150.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(localAssetList) { fileName ->
                                            val isSelected = customBgType == "PRESET" && customBgValue == fileName
                                            val borderColor = if (isSelected) Color(0xFF4A9EFF) else Color.Transparent
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(88.dp)
                                                    .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        customBgType = "PRESET"
                                                        customBgValue = fileName
                                                    },
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A))
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    AsyncImage(
                                                        model = "file:///android_asset/backgrounds/$fileName",
                                                        contentDescription = fileName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color.Black.copy(alpha = 0.5f))
                                                            .align(Alignment.BottomCenter)
                                                            .padding(vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = fileName.substringBeforeLast("."),
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "IMAGE_URL" -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Busca ao vivo no provedor selecionado
                                    OutlinedTextField(
                                        value = webQuery,
                                        onValueChange = { webQuery = it },
                                        placeholder = {
                                            Text(
                                                "Buscar no ${webProvider.label}: carros, paisagem, abstrato...",
                                                fontSize = 12.sp,
                                                color = Color(0xFFB0B8C4),
                                                maxLines = 1
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = Color(0xFF4A9EFF),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            if (webQuery.isNotEmpty()) {
                                                IconButton(onClick = { webQuery = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = "Limpar busca",
                                                        tint = Color(0xFFB0B8C4),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF4A9EFF),
                                            unfocusedBorderColor = Color(0xFF2C3139),
                                            focusedContainerColor = Color(0xFF13151A),
                                            unfocusedContainerColor = Color(0xFF13151A),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Provedores de imagens públicas
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        items(WebImageProvider.entries.toList()) { provider ->
                                            val isSelected = webProvider == provider
                                            val locked = provider.id in lockedProviders
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isSelected) Color(0xFF4A9EFF) else Color(0xFF2C3139),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable { webProvider = provider }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (locked) Icons.Default.Lock else Icons.Default.Public,
                                                        contentDescription = null,
                                                        tint = if (locked) Color(0xFFB0B8C4) else Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        provider.label,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        libraryMessage
                                            ?: "Toque para pré-visualizar no cluster · ♥ salva na Biblioteca para usar sem internet.",
                                        color = if (libraryMessage != null) Color(0xFF4A9EFF) else Color(0xFF718096),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val needsKey = webProvider.requiresKey && webApiKey.isBlank()

                                    when {
                                        needsKey -> {
                                            var keyInput by remember(webProvider) { mutableStateOf("") }
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(top = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    "${webProvider.label} exige uma chave de API gratuita.",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "Crie a sua em ${webProvider.keyPortal} e cole abaixo. Ela fica salva no carro e vale para as próximas buscas. Wallhaven e Openverse funcionam sem chave.",
                                                    color = Color(0xFFB0B8C4),
                                                    fontSize = 12.sp
                                                )
                                                OutlinedTextField(
                                                    value = keyInput,
                                                    onValueChange = { keyInput = it },
                                                    placeholder = {
                                                        Text("Cole aqui a chave de API", fontSize = 12.sp, color = Color(0xFF718096))
                                                    },
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = Color(0xFF4A9EFF),
                                                        unfocusedBorderColor = Color(0xFF2C3139),
                                                        focusedContainerColor = Color(0xFF13151A),
                                                        unfocusedContainerColor = Color(0xFF13151A),
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Button(
                                                    onClick = {
                                                        WebImageSearch.setApiKey(prefs, webProvider, keyInput)
                                                        keyInput = ""
                                                        apiKeyRefreshToken++
                                                    },
                                                    enabled = keyInput.isNotBlank(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Salvar chave", fontSize = 12.sp, color = Color.White)
                                                }
                                            }
                                        }

                                        webLoading && webResults.isEmpty() -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    CircularProgressIndicator(color = Color(0xFF4A9EFF))
                                                    Spacer(Modifier.height(8.dp))
                                                    Text("Buscando em ${webProvider.label}...", color = Color(0xFFB0B8C4), fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        webError != null && webResults.isEmpty() -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudOff,
                                                        contentDescription = null,
                                                        tint = Color(0xFF718096),
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                    Text(
                                                        webError ?: "",
                                                        color = Color(0xFFB0B8C4),
                                                        fontSize = 12.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Button(
                                                        onClick = { webRetryToken++ },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3139)),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Refresh,
                                                            contentDescription = null,
                                                            tint = Color(0xFF4A9EFF),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Tentar novamente", color = Color.White, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }

                                        webResults.isEmpty() -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    if (webQuery.isBlank()) "Digite algo para buscar em ${webProvider.label}."
                                                    else "Nenhuma imagem encontrada para \"$webQuery\".",
                                                    color = Color(0xFFB0B8C4),
                                                    fontSize = 12.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        else -> {
                                            LazyVerticalGrid(
                                                state = webGridState,
                                                columns = GridCells.Adaptive(minSize = 150.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                items(webResults) { image ->
                                                    val isSelected = customBgType == "IMAGE_URL" &&
                                                            customBgValue == image.fullUrl
                                                    val isSaved = libraryFileNames.contains(
                                                        WallpaperLibrary.fileNameFor(image)
                                                    )
                                                    val isSaving = favoriteBusyKey == image.libraryKey
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(92.dp)
                                                            .border(
                                                                2.dp,
                                                                if (isSelected) Color(0xFF4A9EFF) else Color.Transparent,
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .clickable {
                                                                // Preview imediato no cluster; só persiste no OK.
                                                                customBgType = "IMAGE_URL"
                                                                customBgValue = image.fullUrl
                                                            },
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A))
                                                    ) {
                                                        Box(modifier = Modifier.fillMaxSize()) {
                                                            AsyncImage(
                                                                model = image.thumbUrl,
                                                                contentDescription = image.title,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                            // Favoritar: baixa para a Biblioteca (offline)
                                                            IconButton(
                                                                onClick = {
                                                                    if (isSaving) return@IconButton
                                                                    favoriteBusyKey = image.libraryKey
                                                                    ioScope.launch {
                                                                        val result = WallpaperLibrary.favorite(image)
                                                                        favoriteBusyKey = null
                                                                        result
                                                                            .onSuccess {
                                                                                uploadedRefreshToken++
                                                                                libraryMessage = "Imagem salva na Biblioteca."
                                                                            }
                                                                            .onFailure {
                                                                                libraryMessage = it.message
                                                                                    ?: "Não foi possível salvar a imagem."
                                                                            }
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .align(Alignment.TopEnd)
                                                                    .size(28.dp)
                                                                    .background(
                                                                        Color.Black.copy(alpha = 0.55f),
                                                                        RoundedCornerShape(bottomStart = 8.dp)
                                                                    )
                                                            ) {
                                                                if (isSaving) {
                                                                    CircularProgressIndicator(
                                                                        color = Color(0xFF4A9EFF),
                                                                        strokeWidth = 2.dp,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                } else {
                                                                    Icon(
                                                                        imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                                        contentDescription = if (isSaved) "Já está na Biblioteca" else "Favoritar na Biblioteca",
                                                                        tint = if (isSaved) Color(0xFFFF6B81) else Color.White,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(Color.Black.copy(alpha = 0.65f))
                                                                    .align(Alignment.BottomCenter)
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = image.title.ifBlank { image.provider.label },
                                                                        color = Color.White,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Medium,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        modifier = Modifier.weight(1f)
                                                                    )
                                                                    Text(
                                                                        text = image.resolutionLabel,
                                                                        color = Color(0xFF4A9EFF),
                                                                        fontSize = 9.sp,
                                                                        maxLines = 1
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                if (webLoadingMore) {
                                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 8.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            CircularProgressIndicator(
                                                                color = Color(0xFF4A9EFF),
                                                                strokeWidth = 2.dp,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                } else if (!webHasMore) {
                                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                                        Text(
                                                            "Fim dos resultados de ${webProvider.label}.",
                                                            color = Color(0xFF718096),
                                                            fontSize = 11.sp,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "FILE" -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Sua biblioteca: imagens enviadas pelo celular e favoritas da Web (ficam salvas no carro e funcionam sem internet).",
                                        color = Color(0xFFB0B8C4),
                                        fontSize = 12.sp
                                    )
                                    libraryMessage?.let { message ->
                                        Text(message, color = Color(0xFF4A9EFF), fontSize = 11.sp, maxLines = 1)
                                    }
                                    if (libraryItems.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Biblioteca vazia. Favorite uma imagem na aba Web (ícone de coração) ou use \"Carregar pelo Celular\" para enviar uma foto.",
                                                color = Color(0xFF718096),
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(minSize = 150.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(libraryItems, key = { it.file.absolutePath }) { item ->
                                                val isSelected = customBgType == "FILE" &&
                                                        customBgValue == item.file.absolutePath
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(92.dp)
                                                        .border(
                                                            2.dp,
                                                            if (isSelected) Color(0xFF4A9EFF) else Color.Transparent,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            customBgType = "FILE"
                                                            customBgValue = item.file.absolutePath
                                                        },
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A))
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize()) {
                                                        AsyncImage(
                                                            model = item.file,
                                                            contentDescription = item.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                val wasApplied = customBgType == "FILE" &&
                                                                        customBgValue == item.file.absolutePath
                                                                if (WallpaperLibrary.delete(item.file)) {
                                                                    if (wasApplied) revertPreview()
                                                                    uploadedRefreshToken++
                                                                }
                                                            },
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .size(28.dp)
                                                                .background(
                                                                    Color.Black.copy(alpha = 0.55f),
                                                                    RoundedCornerShape(bottomStart = 8.dp)
                                                                )
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Excluir da biblioteca",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(Color.Black.copy(alpha = 0.65f))
                                                                .align(Alignment.BottomCenter)
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = item.title,
                                                                    color = Color.White,
                                                                    fontSize = 10.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    modifier = Modifier.weight(1f)
                                                                )
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (item.isFromWeb) Icons.Default.Public else Icons.Default.PhoneAndroid,
                                                                        contentDescription = null,
                                                                        tint = Color(0xFF4A9EFF),
                                                                        modifier = Modifier.size(10.dp)
                                                                    )
                                                                    Text(
                                                                        text = item.providerLabel ?: "Celular",
                                                                        color = Color(0xFF4A9EFF),
                                                                        fontSize = 9.sp,
                                                                        maxLines = 1
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
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ── 3 ACTION BUTTONS: OK, Cancelar, Carregar pelo Celular ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. OK (Saves & Closes)
                        Button(
                            onClick = {
                                prefs.edit {
                                    putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, customBgType)
                                    putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, customBgValue)
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A9EFF),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("OK", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        // 2. Cancelar (Reverts & Closes)
                        Button(
                            onClick = {
                                revertPreview()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3139)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar", color = Color.White, fontSize = 13.sp)
                        }

                        // 3. Carregar pelo Celular (Opens Sync View)
                        Button(
                            onClick = { currentView = "SYNC" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2C3139),
                                contentColor = Color(0xFF4A9EFF)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF4A9EFF)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Carregar pelo Celular", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** O que a miniatura 1920x720 da tela Telas deve mostrar para o fundo atual do cluster. */
private sealed interface ClusterBgThumb {
    data class Picture(val model: Any) : ClusterBgThumb
    data class Solid(val spec: SolidBackgroundSpec) : ClusterBgThumb
    data class Empty(val icon: ImageVector, val title: String, val hint: String) : ClusterBgThumb
}

@Composable
private fun ClusterBackgroundThumbContent(state: ClusterBgThumb, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        when (state) {
            is ClusterBgThumb.Solid -> SolidBackgroundPreview(
                color = Color(state.spec.color),
                vignette = state.spec.vignette,
                modifier = Modifier.fillMaxSize()
            )

            is ClusterBgThumb.Picture -> SubcomposeAsyncImage(
                model = state.model,
                contentDescription = "Miniatura do fundo do cluster (1920x720)",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4A9EFF),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                error = {
                    // Fundos "Web" dependem da rede do carro a cada carregamento.
                    ClusterBgThumbPlaceholder(
                        icon = Icons.Default.CloudOff,
                        title = "Não foi possível carregar a imagem",
                        hint = "Verifique a conexão do carro ou salve na Biblioteca"
                    )
                }
            )

            is ClusterBgThumb.Empty -> ClusterBgThumbPlaceholder(
                icon = state.icon,
                title = state.title,
                hint = state.hint
            )
        }
    }
}

/** Placeholder hachurado: deixa claro que não há imagem, sem parecer um fundo preto válido. */
@Composable
private fun ClusterBgThumbPlaceholder(icon: ImageVector, title: String, hint: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color(0xFF13151A))
            val step = 18.dp.toPx()
            val stripe = Color(0xFF1C212B)
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = stripe,
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = 7f
                )
                x += step
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                color = Color(0xFFB0B8C4),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                hint,
                color = Color(0xFF6B7280),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Desenha a cor sólida com a mesma vinheta elíptica que o projetor aplica no cluster,
 * para que a miniatura e a pré-visualização batam com o que aparece no painel.
 */
@Composable
private fun SolidBackgroundPreview(color: Color, vignette: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(color)
        if (vignette > 0) {
            scale(scaleX = 1f, scaleY = size.height / size.width, pivot = center) {
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = vignette / 100f),
                        center = center,
                        radius = size.width * 0.62f
                    ),
                    topLeft = Offset(0f, center.y - size.width),
                    size = Size(size.width, size.width * 2)
                )
            }
        }
    }
}

/** Paleta rápida pensada para o cluster: tons escuros que não ofuscam à noite. */
private val SOLID_COLOR_SWATCHES = listOf(
    0xFF000000.toInt() to "Preto",
    0xFF101820.toInt() to "Azul noite",
    0xFF1C1F26.toInt() to "Grafite",
    0xFF123A63.toInt() to "Azul GWM",
    0xFF0F2A1D.toInt() to "Verde escuro",
    0xFF3A0D14.toInt() to "Vinho",
    0xFF241533.toInt() to "Roxo",
    0xFF3A4048.toInt() to "Cinza"
)

/**
 * Aba "Cor": mapa saturação/brilho + faixa de matiz (o mapa RGB), sliders R/G/B para ajuste
 * fino e vinheta opcional, com pré-visualização no formato do cluster (1920x720).
 */
@Composable
private fun SolidColorTab(
    hue: Float,
    saturation: Float,
    brightness: Float,
    vignette: Int,
    onHueChange: (Float) -> Unit,
    onSatValChange: (Float, Float) -> Unit,
    onArgbChange: (Int) -> Unit,
    onVignetteChange: (Int) -> Unit,
    onCommit: () -> Unit
) {
    val argb = remember(hue, saturation, brightness) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    }
    val color = Color(argb)
    val red = android.graphics.Color.red(argb)
    val green = android.graphics.Color.green(argb)
    val blue = android.graphics.Color.blue(argb)
    val hex = remember(argb) { "#%06X".format(argb and 0x00FFFFFF) }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Mapa de cores ──
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Mapa de cores", color = Color(0xFFB0B8C4), fontSize = 12.sp)

            HsvColorPicker(
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                onHueChange = onHueChange,
                onSatValChange = onSatValChange,
                onCommit = onCommit
            )

            Text("Atalhos", color = Color(0xFFB0B8C4), fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SOLID_COLOR_SWATCHES) { (swatch, name) ->
                    val isSelected = (swatch and 0x00FFFFFF) == (argb and 0x00FFFFFF)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(swatch))
                            .border(
                                2.dp,
                                if (isSelected) Color(0xFF4A9EFF) else Color(0xFF2C3139),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                onArgbChange(swatch)
                                onCommit()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = name,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Pré-visualização e ajustes ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SolidBackgroundPreview(
                color = color,
                vignette = vignette,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1920f / 720f)
                    .clip(RoundedCornerShape(8.dp))
            )

            Text(
                "$hex · R $red  G $green  B $blue",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            listOf(
                Triple("R", red, 0),
                Triple("G", green, 1),
                Triple("B", blue, 2)
            ).forEach { (label, value, channel) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(label, color = Color(0xFFB0B8C4), fontSize = 11.sp, modifier = Modifier.width(12.dp))
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { raw ->
                            val channelValue = raw.roundToInt().coerceIn(0, 255)
                            val updated = when (channel) {
                                0 -> android.graphics.Color.rgb(channelValue, green, blue)
                                1 -> android.graphics.Color.rgb(red, channelValue, blue)
                                else -> android.graphics.Color.rgb(red, green, channelValue)
                            }
                            onArgbChange(updated)
                        },
                        onValueChangeFinished = onCommit,
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF4A9EFF),
                            activeTrackColor = Color(0xFF4A9EFF),
                            inactiveTrackColor = Color(0xFF2C3139)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF2C3139))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vinheta", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (vignette > 0) "$vignette%" else "desligada",
                    color = Color(0xFF4A9EFF),
                    fontSize = 11.sp
                )
            }
            Slider(
                value = vignette.toFloat(),
                onValueChange = { onVignetteChange(it.roundToInt().coerceIn(0, 100)) },
                onValueChangeFinished = onCommit,
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4A9EFF),
                    activeTrackColor = Color(0xFF4A9EFF),
                    inactiveTrackColor = Color(0xFF2C3139)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
            Text(
                "Escurece as bordas do cluster, deixando os mostradores em destaque.",
                color = Color(0xFF718096),
                fontSize = 11.sp
            )
        }
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
                Text("Aplicativo", color = ImpTokens.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(ImpTokens.TrackOff, RoundedCornerShape(8.dp))
                                        .clickable { showAppPicker = true }
                                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                            selectedApp?.label ?: "Selecionar Aplicativo...",
                            color = if (selectedApp != null) Color.White else ImpTokens.TextMuted,
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
                                        .background(ImpTokens.TrackOff, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                            imageVector =
                                    if (customName.isBlank()) Icons.Default.EditNote
                                    else Icons.Default.Label,
                            contentDescription = "Nome Customizado",
                            tint = if (customName.isBlank()) Color.White else ImpTokens.Accent
                    )
                }
            }
        }

        // Display Selection
        Column {
            Text("Tela de Destino", color = ImpTokens.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Box {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(ImpTokens.TrackOff, RoundedCornerShape(8.dp))
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
                        modifier = Modifier.fillMaxWidth(0.5f).background(ImpTokens.Container)
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

        HorizontalDivider(color = ImpTokens.TrackOff)

        // Override Theme Dimensions
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Dimensões", color = ImpTokens.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(ImpTokens.TrackOff, RoundedCornerShape(8.dp))
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
                                        checkedTrackColor = ImpTokens.Accent
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
                        color = ImpTokens.TextMuted,
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
            Text("Ícone Substituto", color = ImpTokens.TextSecondary, fontSize = 12.sp)
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
                                                    if (selectedSubIcon == null) ImpTokens.Accent
                                                    else ImpTokens.TrackOff
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
                                                    if (isSelected) ImpTokens.Accent
                                                    else ImpTokens.TrackOff
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
            Text("Cor de Destaque", color = ImpTokens.TextSecondary, fontSize = 12.sp)
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
                    color = ImpTokens.Accent,
                    fontSize = 12.sp
            )
        }

        // Action buttons
        Spacer(Modifier.height(8.dp))
        Button(
                onClick = { currentConfig()?.let { onSave(it) } },
                enabled = selectedApp != null,
                colors = ButtonDefaults.buttonColors(containerColor = ImpTokens.Accent),
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
                containerColor = ImpTokens.Container,
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
                                    color = ImpTokens.TextSecondary,
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
                                            color = ImpTokens.Accent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                    )
                                    Text(
                                            text = limit1,
                                            color = ImpTokens.TextSecondary,
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
                                            color = ImpTokens.Accent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                    )
                                    Text(
                                            text = limit2,
                                            color = ImpTokens.TextSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                    )
                                }
                            }
                            Text(
                                    text = question,
                                    color = ImpTokens.TextSecondary,
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
                                    color = ImpTokens.TextSecondary,
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
                                    ButtonDefaults.buttonColors(containerColor = ImpTokens.Accent),
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
                containerColor = ImpTokens.Container,
                titleContentColor = Color.White,
                textContentColor = Color.White,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                                "Defina um nome customizado para este atalho:",
                                color = ImpTokens.TextSecondary,
                                fontSize = 14.sp
                        )
                        TextField(
                                value = tempName,
                                onValueChange = { tempName = it },
                                placeholder = {
                                    Text(
                                            selectedApp?.label ?: "Nome original",
                                            color = ImpTokens.TextMuted
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = ImpTokens.TrackOff,
                                                unfocusedContainerColor = ImpTokens.TrackOff,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedIndicatorColor = ImpTokens.Accent,
                                                unfocusedIndicatorColor = ImpTokens.TrackOff
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
                            colors = ButtonDefaults.buttonColors(containerColor = ImpTokens.Accent)
                    ) { Text("OK", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancelar", color = ImpTokens.TextSecondary)
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
                                .border(1.dp, ImpTokens.TrackOff, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
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

                HorizontalDivider(color = ImpTokens.TrackOff)

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
            Text(label, color = ImpTokens.TextSecondary, fontSize = 12.sp)
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
                                thumbColor = ImpTokens.Accent,
                                activeTrackColor = ImpTokens.Accent,
                                inactiveTrackColor = ImpTokens.TrackOff
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
                            .background(ImpTokens.TrackOff)
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
                                .border(1.dp, ImpTokens.Hairline, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
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
                                    Text("Nome do App (ex: YouTube)", color = ImpTokens.TextMuted)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = ImpTokens.TrackOff,
                                                unfocusedContainerColor = ImpTokens.TrackOff,
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
                                            color = ImpTokens.TextMuted
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = ImpTokens.TrackOff,
                                                unfocusedContainerColor = ImpTokens.TrackOff,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                        )
                        )
                        TextField(
                                value = manualActivity,
                                onValueChange = { manualActivity = it },
                                placeholder = {
                                    Text("Atividade (opcional)", color = ImpTokens.TextMuted)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = ImpTokens.TrackOff,
                                                unfocusedContainerColor = ImpTokens.TrackOff,
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
                                                    containerColor = ImpTokens.TrackOff
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
                                                    containerColor = ImpTokens.Accent
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
                                placeholder = { Text("Buscar...", color = ImpTokens.TextMuted) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = ImpTokens.TrackOff,
                                                unfocusedContainerColor = ImpTokens.TrackOff,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedIndicatorColor = ImpTokens.Accent,
                                                unfocusedIndicatorColor = ImpTokens.TrackOff
                                        )
                        )
                        Button(
                                onClick = { showManualInput = true },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = ImpTokens.TrackOff
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

private val DEFAULT_THEME_COLOR_PRESETS = listOf(
    "#00A0FF", "#FF0033", "#00E676", "#FF6D00", "#0033CC", "#000000", "#FFFFFF", "#808080"
)

/** Coerces user/theme-supplied text to "#RRGGBB", or returns [fallback] if it isn't one. */
private fun normalizeHexColor(raw: String?, fallback: String): String {
    val trimmed = raw?.trim().orEmpty()
    val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    val digits = withHash.drop(1)
    return if (digits.length == 6 && digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
        withHash.uppercase()
    } else {
        fallback
    }
}

/**
 * Control for a <type>multi</type> theme configuration: the same pills as a combo, but
 * any number can be active at once. The value is persisted to the usual scoped key as a
 * comma separated list, so it needs no storage format of its own and older app builds
 * that do not know this type simply fall through to the theme's <default>.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeMultiConfigControl(
    options: List<String>,
    initialCsv: String,
    onCommit: (String) -> Unit
) {
    var selected by remember {
        mutableStateOf(initialCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }

    // FlowRow rather than the combo's Row of weight(1f) cells: splitting the width evenly
    // already crowds the labels at four options and truncates them beyond that. These keep
    // their natural width and wrap to a second line instead.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            val isSelected = selected.any { it.equals(option, ignoreCase = true) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (isSelected) Color(0xFF4A9EFF) else Color(0xFF13151A))
                    .clickable {
                        val next = if (isSelected) {
                            selected.filterNot { it.equals(option, ignoreCase = true) }.toSet()
                        } else {
                            selected + option
                        }
                        selected = next
                        // Emit in the order theme.xml declares, not tap order, so the stored
                        // string stays stable and readable. Everything unticked writes an
                        // empty string, which themes must read as "nothing kept" — not as
                        // "unset", since an absent key already resolves to <default>.
                        onCommit(
                            options.filter { o -> next.any { it.equals(o, ignoreCase = true) } }
                                .joinToString(", ")
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    color = if (isSelected) Color.White else Color(0xFFB0B8C4),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Control for a <type>color</type> theme configuration: a row of preset swatches plus an
 * option to open a free-form HSV color picker popup dialog.
 */
@Composable
private fun ThemeColorConfigControl(
    initialHex: String,
    presets: List<String>,
    onCommit: (String) -> Unit
) {
    val swatches = remember(presets) {
        presets.mapNotNull { option ->
            val normalized = normalizeHexColor(option, "")
            normalized.ifEmpty { null }
        }.ifEmpty { DEFAULT_THEME_COLOR_PRESETS }
    }
    val fallback = swatches.first()

    var hex by remember(initialHex) { mutableStateOf(normalizeHexColor(initialHex, fallback)) }
    var showCustomPickerModal by remember { mutableStateOf(false) }

    fun selectSwatch(swatch: String) {
        hex = swatch
        onCommit(swatch)
    }

    // Single row: presets, then a chip showing the live color that opens the full picker.
    // The old layout stacked a color+hex header, a "Personalizado" text link and a swatch
    // strip - three rows per color setting, which does not fit once a tab has several.
    val currentArgb = android.graphics.Color.parseColor(hex)
    val isCustom = swatches.none { it.equals(hex, ignoreCase = true) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            swatches.forEach { swatch ->
                val isSelected = swatch.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(android.graphics.Color.parseColor(swatch)))
                        .border(
                            2.dp,
                            if (isSelected) Color.White else Color(0xFF2C3139),
                            RoundedCornerShape(7.dp)
                        )
                        .clickable { selectSwatch(swatch) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = contrastOn(android.graphics.Color.parseColor(swatch)),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(22.dp)
                .background(Color(0xFF2C3139))
        )

        // Doubles as the current-color readout and the entry point to the HSV picker,
        // and carries the selection ring when the value is not one of the presets.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(currentArgb))
                .border(
                    2.dp,
                    if (isCustom) Color.White else Color(0xFF2C3139),
                    RoundedCornerShape(7.dp)
                )
                .clickable { showCustomPickerModal = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Colorize,
                contentDescription = "Cor personalizada",
                tint = contrastOn(currentArgb),
                modifier = Modifier.size(15.dp)
            )
        }

        Text(hex, color = Color(0xFF8A93A0), fontSize = 11.sp)
    }

    if (showCustomPickerModal) {
        CustomColorPickerDialog(
            initialHex = hex,
            swatches = swatches,
            onDismiss = { showCustomPickerModal = false },
            onColorSelected = { selectedHex ->
                selectSwatch(selectedHex)
                showCustomPickerModal = false
            },
            // Writing the pref is what pushes the colour to the running cluster, via
            // PreferencePushListener - so a preview is just an early commit that Cancel
            // rewinds. `hex` is intentionally not updated here, so the row behind the
            // dialog keeps showing the value that is actually saved.
            onPreview = { previewHex -> onCommit(previewHex) }
        )
    }
}

/** Black or white, whichever stays legible on top of [argb]. */
private fun contrastOn(argb: Int): Color {
    val luminance = (0.299f * android.graphics.Color.red(argb) +
        0.587f * android.graphics.Color.green(argb) +
        0.114f * android.graphics.Color.blue(argb)) / 255f
    return if (luminance > 0.6f) Color.Black else Color.White
}

@Composable
private fun CustomColorPickerDialog(
    initialHex: String,
    swatches: List<String>,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
    /**
     * Pushes a colour to the live cluster without treating it as the final choice.
     * Debounced below, and rewound to [initialHex] if the user cancels.
     */
    onPreview: (String) -> Unit = {}
) {
    val startHsv = remember(initialHex) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(initialHex), it) }
    }
    var hue by remember { mutableStateOf(startHsv[0]) }
    var saturation by remember { mutableStateOf(startHsv[1]) }
    var brightness by remember { mutableStateOf(startHsv[2]) }

    val currentArgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    val currentHex = "#%06X".format(currentArgb and 0x00FFFFFF)

    // Live preview: the cluster is a second display, so it IS the preview. Debounced
    // because each distinct colour costs the theme a full artwork repaint - the restart
    // on every change means we only push once the drag settles.
    var previewed by remember { mutableStateOf(false) }
    LaunchedEffect(currentHex) {
        if (currentHex.equals(initialHex, ignoreCase = true)) return@LaunchedEffect
        delay(250)
        previewed = true
        onPreview(currentHex)
    }

    // Cancelling must put the cluster back; otherwise a preview the user rejected sticks.
    val cancel = {
        if (previewed) onPreview(initialHex)
        onDismiss()
    }

    Dialog(
        onDismissRequest = cancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(360.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF191C21)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFF4A9EFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cor Personalizada",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = cancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color(0xFFB0B8C4)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF2C3139))

                // Preview & Hex value display
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101216), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(android.graphics.Color.parseColor(currentHex)))
                                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = currentHex,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // HSV Canvas map & hue slider
                HsvColorPicker(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onHueChange = { hue = it },
                    onSatValChange = { s, v -> saturation = s; brightness = v },
                    onCommit = { },
                    mapHeight = 130.dp
                )

                // Swatch list inside popup for quick picking
                Text(
                    text = "Paleta de Cores",
                    color = Color(0xFFB0B8C4),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(swatches) { swatch ->
                        val isSelected = swatch.equals(currentHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(android.graphics.Color.parseColor(swatch)))
                                .border(
                                    2.dp,
                                    if (isSelected) Color.White else Color(0xFF2C3139),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(swatch), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    brightness = hsv[2]
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = cancel) {
                        Text("Cancelar", color = Color(0xFFB0B8C4))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onColorSelected(currentHex) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Aplicar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsDialog(
    theme: ThemeMetadata,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    val listState = rememberLazyListState()

    val defaultGroup = "Geral"
    // "Geral" always leads; every other tab follows the order it first appears in
    // theme.xml. Themes with no <group> at all collapse to a single tab and the tab
    // row is hidden entirely, so they look exactly as they did before groups existed.
    val groupedConfigs = remember(theme.configurations) {
        theme.configurations.groupBy { it.group.ifBlank { defaultGroup } }
    }
    val groups = remember(groupedConfigs) {
        groupedConfigs.keys.sortedBy { if (it == defaultGroup) 0 else 1 }
    }
    var selectedGroup by remember(groups) { mutableStateOf(groups.firstOrNull() ?: defaultGroup) }
    val visibleConfigs = groupedConfigs[selectedGroup].orEmpty()

    // One shared listState across tabs, so a tab switch has to rewind it - otherwise
    // arriving on a short tab leaves it scrolled past its own content.
    LaunchedEffect(selectedGroup) { listState.scrollToItem(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.width(640.dp).heightIn(max = 720.dp).padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFF4A9EFF))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Ajustes: ${theme.name}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (groups.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.forEach { group ->
                            val isSelected = group == selectedGroup
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(if (isSelected) Color(0xFF4A9EFF) else Color(0xFF13151A))
                                    .clickable { selectedGroup = group }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = group,
                                    color = if (isSelected) Color.White else Color(0xFFB0B8C4),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF2C3139))

                Box(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(end = 6.dp)
                    ) {
                        // Keyed by id so the per-control remember{} state belongs to the
                        // config itself, not to a list slot that another tab reuses.
                        items(visibleConfigs, key = { it.id }) { config ->
                            val scopedKey = "theme_config_${theme.folderName}_${config.stateVariable}"
                            
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(config.label, color = Color(0xFFB0B8C4), fontSize = 13.sp)
                                
                                when (config.type) {
                                    "boolean" -> {
                                        var checked by remember {
                                            mutableStateOf(prefs.getString(scopedKey, config.defaultValue) == "true")
                                        }
                                        
                                        val options = listOf("Ativado", "Desativado")
                                        val selectedOption = if (checked) "Ativado" else "Desativado"
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF13151A), RoundedCornerShape(50.dp))
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            options.forEach { option ->
                                                val isSelected = selectedOption == option
                                                val backgroundColor = if (isSelected) Color(0xFF4A9EFF) else Color.Transparent
                                                val textColor = if (isSelected) Color.White else Color(0xFFB0B8C4)
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(50.dp))
                                                        .background(backgroundColor)
                                                        .clickable {
                                                            val newVal = option == "Ativado"
                                                            checked = newVal
                                                            prefs.edit().putString(scopedKey, newVal.toString()).apply()
                                                        }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = option,
                                                        color = textColor,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    "text", "number" -> {
                                        var textVal by remember {
                                            mutableStateOf(prefs.getString(scopedKey, config.defaultValue) ?: "")
                                        }
                                        OutlinedTextField(
                                            value = textVal,
                                            onValueChange = { newVal ->
                                                textVal = newVal
                                                prefs.edit().putString(scopedKey, newVal).apply()
                                            },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = if (config.type == "number") KeyboardType.Number else KeyboardType.Text
                                            ),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF4A9EFF),
                                                unfocusedBorderColor = Color(0xFF2C3139)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    "color" -> {
                                        ThemeColorConfigControl(
                                            initialHex = prefs.getString(scopedKey, config.defaultValue) ?: config.defaultValue,
                                            // <options> doubles as the preset swatch list for color configs.
                                            presets = config.options,
                                            onCommit = { hex ->
                                                prefs.edit().putString(scopedKey, hex).apply()
                                            }
                                        )
                                    }
                                    "multi" -> {
                                        ThemeMultiConfigControl(
                                            options = config.options,
                                            initialCsv = prefs.getString(scopedKey, config.defaultValue)
                                                ?: config.defaultValue,
                                            onCommit = { csv ->
                                                prefs.edit().putString(scopedKey, csv).apply()
                                            }
                                        )
                                    }
                                    "combo" -> {
                                        var selectedOption by remember {
                                            mutableStateOf(prefs.getString(scopedKey, config.defaultValue) ?: config.options.firstOrNull() ?: "")
                                        }
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF13151A), RoundedCornerShape(50.dp))
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            config.options.forEach { option ->
                                                val isSelected = selectedOption == option
                                                val backgroundColor = if (isSelected) Color(0xFF4A9EFF) else Color.Transparent
                                                val textColor = if (isSelected) Color.White else Color(0xFFB0B8C4)
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(50.dp))
                                                        .background(backgroundColor)
                                                        .clickable {
                                                            selectedOption = option
                                                            prefs.edit().putString(scopedKey, option).apply()
                                                        }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = option,
                                                        color = textColor,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // Unknown type: label only. Themes declaring a type this
                                    // app build does not know still fall back to their
                                    // <default>, which the bridge serves unchanged.
                                    else -> {}
                                }
                            }
                        }
                    }

                    if (listState.canScrollForward) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xEC1E2228))
                                    )
                                )
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Role para ver mais",
                                tint = Color(0xFF4A9EFF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Role para ver mais",
                                color = Color(0xFF4A9EFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (listState.canScrollBackward) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xEC1E2228), Color.Transparent)
                                    )
                                )
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Mais opções acima",
                                tint = Color(0xFF4A9EFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = Color(0xFF2C3139))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar", color = Color(0xFF4A9EFF), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
