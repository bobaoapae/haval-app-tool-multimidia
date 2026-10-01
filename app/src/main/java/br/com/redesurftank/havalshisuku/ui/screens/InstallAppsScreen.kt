package br.com.redesurftank.havalshisuku.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.TAG
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchManager
import br.com.redesurftank.havalshisuku.managers.StartupAppManager
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import br.com.redesurftank.havalshisuku.managers.CarPlayPatchManager
import br.com.redesurftank.havalshisuku.models.AppInfo
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.*
import br.com.redesurftank.havalshisuku.ui.theme.Michroma
import br.com.redesurftank.havalshisuku.utils.ReleaseUpdateChecker
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import rikka.shizuku.Shizuku

/** O viewer 3D, distribuido pelo catalogo como qualquer outro app. */
const val IMPULSE_HOME_PACKAGE = "com.havalh6.viewer"

@Composable
fun InstallAppsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf(listOf<AppInfo>()) }
    var downloadingApp by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    val pm = context.packageManager
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val requestPermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
            ) { /* Permission requested */}
    val deletePackageLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
            ) {
                scope.launch {
                    delay(400)
                    refreshTrigger++
                }
            }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var installResult by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var downloadingUrl by remember { mutableStateOf(false) }
    var urlProgress by remember { mutableFloatStateOf(0f) }
    var isPatchInstalled by remember { mutableStateOf(AndroidAutoPatchManager.isPatchInstalled()) }
    var isMounted by remember { mutableStateOf(AndroidAutoPatchManager.isMounted()) }
    var isCarPlayPatchInstalled by remember {
        mutableStateOf(CarPlayPatchManager.isPatchInstalled())
    }
    var isCarPlayMounted by remember { mutableStateOf(CarPlayPatchManager.isMounted()) }
    var showStartupApps by remember { mutableStateOf(false) }
    var showHomeSetup by remember { mutableStateOf(false) }
    // A instalacao termina FORA daqui: startDownload entrega o APK ao instalador do sistema.
    // Entao a sugestao nao pode pendurar num callback - ela observa o pacote aparecer.
    var homeWasInstalled by remember { mutableStateOf(runCatching { pm.getPackageInfo(IMPULSE_HOME_PACKAGE, 0) }.isSuccess) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var diagnosticsText by remember { mutableStateOf("") }
    var appToUninstall by remember { mutableStateOf<String?>(null) }
    var appNameToUninstall by remember { mutableStateOf<String?>(null) }
    var isPatchUninstall by remember { mutableStateOf<String?>(null) }

    val prefs = remember {
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    }
    var aaPatchAutoMount by remember {
        mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.AA_PATCH_AUTO_MOUNT.key, false))
    }
    var carPlayPatchAutoMount by remember {
        mutableStateOf(
                prefs.getBoolean(SharedPreferencesKeys.CARPLAY_PATCH_AUTO_MOUNT.key, false)
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            // Estas checagens rodam shells Shizuku (ls/md5sum de APKs grandes). Rodar fora da
            // main thread (IO) e num intervalo maior — o estado dos patches muda raramente.
            val states = withContext(Dispatchers.IO) {
                listOf(
                    AndroidAutoPatchManager.isPatchInstalled(),
                    AndroidAutoPatchManager.isMounted(),
                    CarPlayPatchManager.isPatchInstalled(),
                    CarPlayPatchManager.isMounted()
                )
            }
            isPatchInstalled = states[0]
            isMounted = states[1]
            isCarPlayPatchInstalled = states[2]
            isCarPlayMounted = states[3]
            val homeNow = runCatching { pm.getPackageInfo(IMPULSE_HOME_PACKAGE, 0) }.isSuccess
            if (homeNow && !homeWasInstalled) showHomeSetup = true
            homeWasInstalled = homeNow
            refreshTrigger++
            delay(4000)
        }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val url =
                        URL(
                                "https://raw.githubusercontent.com/bobaoapae/haval-impulse-static-files/refs/heads/main/apps.json?rnd=${System.currentTimeMillis()}"
                        )
                val conn = url.openConnection() as HttpURLConnection
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val jsonString = reader.use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    val appList = mutableListOf<AppInfo>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val iconUrl = obj.optString("appIcon", "")
                        appList.add(
                                AppInfo(
                                        obj.getString("appName"),
                                        obj.getString("appVersion"),
                                        obj.getString("appPackageName"),
                                        obj.getString("appLink"),
                                        if (iconUrl.isNotEmpty() && iconUrl != "null") iconUrl
                                        else null
                                )
                        )
                    }
                    apps = appList
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading apps", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun getInstalledVersion(packageName: String): String? {
        @Suppress("UNUSED_VARIABLE")
        val trigger = refreshTrigger
        return try {
            val info = pm.getPackageInfo(packageName, 0)
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun compareVersions(v1: String?, v2: String): Int {
        // Delega pra impl canônica e testada (ReleaseUpdateChecker) — evita as semânticas
        // divergentes de comparação de versão que existiam espalhadas.
        if (v1 == null) return -1
        return ReleaseUpdateChecker.compareVersions(v1, v2)
    }

    fun startDownload(app: AppInfo) {
        downloadingApp = app.packageName
        downloadProgress = downloadProgress.toMutableMap().apply { put(app.packageName, 0f) }
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.getExternalFilesDir(null), "${app.packageName}.apk")
                val url = URL(app.link)
                val conn = url.openConnection() as HttpURLConnection
                val length = conn.contentLength
                val input = BufferedInputStream(conn.inputStream)
                val output = FileOutputStream(file)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var total = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    total += bytesRead
                    if (length > 0) {
                        downloadProgress =
                                downloadProgress.toMutableMap().apply {
                                    put(app.packageName, total.toFloat() / length)
                                }
                    }
                }
                output.close()
                input.close()
                withContext(Dispatchers.Main) {
                    if (!pm.canRequestPackageInstalls()) {
                        showPermissionDialog = true
                        return@withContext
                    }
                    val uri =
                            FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                            )
                    val intent =
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
            } finally {
                downloadingApp = null
            }
        }
    }

    fun startDownloadFromUrl(urlString: String) {
        downloadingUrl = true
        urlProgress = 0f
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.getExternalFilesDir(null), "custom.apk")
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                val length = conn.contentLength
                val input = BufferedInputStream(conn.inputStream)
                val output = FileOutputStream(file)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var total = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    total += bytesRead
                    if (length > 0) {
                        urlProgress = total.toFloat() / length
                    }
                }
                output.close()
                input.close()
                withContext(Dispatchers.Main) {
                    if (!pm.canRequestPackageInstalls()) {
                        showPermissionDialog = true
                        return@withContext
                    }
                    val uri =
                            FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                            )
                    val intent =
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
            } finally {
                downloadingUrl = false
            }
        }
    }

    fun uninstall(packageName: String) {
        scope.launch(Dispatchers.IO) {
            var success = false
            val hasShizuku = ShizukuUtils.isShizukuAvailable() && runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)

            if (hasShizuku) {
                Log.d(TAG, "Attempting Shizuku uninstall for $packageName")
                var out = ShizukuUtils.runCommandAndGetOutput(
                    arrayOf("pm", "uninstall", packageName)
                )
                Log.d(TAG, "pm uninstall output: $out")
                if (out.contains("Success", ignoreCase = true)) {
                    success = true
                } else {
                    out = ShizukuUtils.runCommandAndGetOutput(
                        arrayOf("pm", "uninstall", "--user", "0", packageName)
                    )
                    Log.d(TAG, "pm uninstall --user 0 output: $out")
                    if (out.contains("Success", ignoreCase = true)) {
                        success = true
                    }
                }
            }

            if (success) {
                runCatching {
                    ShizukuUtils.runCommandAndGetOutput(arrayOf("pkill", "-9", "-f", packageName))
                }
                delay(600)
                withContext(Dispatchers.Main) {
                    refreshTrigger++
                }
            } else {
                Log.d(TAG, "Falling back to system uninstaller for $packageName")
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        deletePackageLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch deletePackageLauncher with ACTION_DELETE", e)
                        try {
                            val altIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                data = Uri.parse("package:$packageName")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                            }
                            deletePackageLauncher.launch(altIntent)
                        } catch (e2: Exception) {
                            Log.e(TAG, "Failed fallback uninstaller", e2)
                        }
                    }
                }
            }
        }
    }

    val userInstalledApps = remember(refreshTrigger, apps) {
        try {
            val catalogPackages = apps.map { it.packageName }.toSet()
            val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            pm.getInstalledApplications(0)
                .filter { (it.flags and systemFlags) == 0 }
                .filter { it.packageName != context.packageName && it.packageName != IMPULSE_HOME_PACKAGE }
                .filter { it.packageName !in catalogPackages }
                .map { appInfo ->
                    val vName = try { pm.getPackageInfo(appInfo.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
                    val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { appInfo.packageName }
                    AppInfo(
                        name = label,
                        version = vName,
                        packageName = appInfo.packageName,
                        link = "",
                        iconUrl = null
                    )
                }
                .sortedBy { it.name.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }
    val allApps = apps + userInstalledApps

    LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(4) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                        "INSTALAR APPS",
                        fontFamily = Michroma,
                        fontSize = 15.sp,
                        letterSpacing = 1.8.sp,
                        color = ImpTokens.TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                )
                SectionHeader("Aplicativos Nativos")
            }
        }
        // Os quatro destaques da tela, lado a lado: os dois patches de projecao, o Impulse
        // Home e o "abrir ao ligar". A grade tem 4 colunas, entao cada um ocupa 1 e eles caem
        // sozinhos na mesma linha; os apps genericos seguem abaixo, 4 por linha.
        item {
            FeatureCard(
                    icon = Icons.Default.Shield,
                    iconTint = if (isMounted) ImpTokens.Accent else Color.White,
                    highlighted = isMounted,
                    title = "Android Auto Patch",
                    subtitle = "Melhora a projeção do Android Auto no cluster do carro, evitando interrupções e garantindo a melhor visualização do mapa na navegação.",
                    status =
                            when {
                                isMounted -> "Ativo"
                                isPatchInstalled -> "Instalado"
                                else -> "Nao instalado"
                            },
                    statusTint = if (isMounted) ImpTokens.Accent else ImpTokens.TextSecondary,
                    subtitleBelowTitle = true,
                    extra =
                            if (isPatchInstalled) {
                                {
                                    AutoMountRow(
                                            checked = aaPatchAutoMount,
                                            onCheckedChange = {
                                                aaPatchAutoMount = it
                                                prefs.edit()
                                                        .putBoolean(
                                                                SharedPreferencesKeys
                                                                        .AA_PATCH_AUTO_MOUNT
                                                                        .key,
                                                                it
                                                        )
                                                        .apply()
                                            }
                                    )
                                }
                            } else null
            ) {
                if (!isPatchInstalled) {
                    CardButton("Instalar", ImpTokens.Accent) {
                        if (AndroidAutoPatchManager.installPatches(context)) isPatchInstalled = true
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isMounted) {
                            CardButton("Ativar", Color(0xFF4CAF50)) {
                                if (AndroidAutoPatchManager.applyMounts()) isMounted = true
                            }
                        } else {
                            CardButton("Desativar", Color(0xFFF44336)) {
                                if (AndroidAutoPatchManager.removeMounts()) isMounted = false
                            }
                        }
                        IconButton(
                                onClick = {
                                    isPatchUninstall = "aa"
                                },
                                modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Desinstalar patch",
                                    tint = Color(0xFFEF5350)
                            )
                        }
                        IconButton(
                                onClick = {
                                    diagnosticsText = AndroidAutoPatchManager.getDiagnostics()
                                    showDiagnostics = true
                                },
                                modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = "Diagnostico",
                                    tint = ImpTokens.TextMuted
                            )
                        }
                    }
                }
            }
        }

        item {
            FeatureCard(
                    icon = Icons.Default.PhoneIphone,
                    iconTint = if (isCarPlayMounted) ImpTokens.Accent else Color.White,
                    highlighted = isCarPlayMounted,
                    title = "Apple CarPlay Patch",
                    subtitle = "Melhora a projeção do CarPlay no cluster do carro, evitando interrupções e garantindo a melhor visualização do mapa na navegação.",
                    status =
                            when {
                                isCarPlayMounted -> "Ativo"
                                isCarPlayPatchInstalled -> "Instalado"
                                else -> "Nao instalado"
                            },
                    statusTint =
                            if (isCarPlayMounted) ImpTokens.Accent else ImpTokens.TextSecondary,
                    subtitleBelowTitle = true,
                    extra =
                            if (isCarPlayPatchInstalled) {
                                {
                                    AutoMountRow(
                                            checked = carPlayPatchAutoMount,
                                            onCheckedChange = {
                                                carPlayPatchAutoMount = it
                                                prefs.edit()
                                                        .putBoolean(
                                                                SharedPreferencesKeys
                                                                        .CARPLAY_PATCH_AUTO_MOUNT
                                                                        .key,
                                                                it
                                                        )
                                                        .apply()
                                            }
                                    )
                                }
                            } else null
            ) {
                if (!isCarPlayPatchInstalled) {
                    CardButton("Instalar", ImpTokens.Accent) {
                        if (CarPlayPatchManager.installPatches(context))
                                isCarPlayPatchInstalled = true
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isCarPlayMounted) {
                            CardButton("Ativar", Color(0xFF4CAF50)) {
                                if (CarPlayPatchManager.applyMounts()) isCarPlayMounted = true
                            }
                        } else {
                            CardButton("Desativar", Color(0xFFF44336)) {
                                if (CarPlayPatchManager.removeMounts()) isCarPlayMounted = false
                            }
                        }
                        IconButton(
                                onClick = {
                                    isPatchUninstall = "carplay"
                                },
                                modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Desinstalar patch",
                                    tint = Color(0xFFEF5350)
                            )
                        }
                        IconButton(
                                onClick = {
                                    diagnosticsText = CarPlayPatchManager.getDiagnostics()
                                    showDiagnostics = true
                                },
                                modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = "Diagnostico",
                                    tint = ImpTokens.TextMuted
                            )
                        }
                    }
                }
            }
        }

        item {
            val homeInstalled = getInstalledVersion(IMPULSE_HOME_PACKAGE)
            val homeCatalog = apps.firstOrNull { it.packageName == IMPULSE_HOME_PACKAGE }
            val homeUpdate =
                    homeInstalled != null &&
                            homeCatalog != null &&
                            compareVersions(homeInstalled, homeCatalog.version) < 0
            val homeProgress = downloadProgress[IMPULSE_HOME_PACKAGE]
            FeatureCard(
                    icon = Icons.Default.DirectionsCar,
                    iconTint = if (homeInstalled != null) ImpTokens.Accent else Color.White,
                    highlighted = homeInstalled != null,
                    title = "Impulse Launcher",
                    subtitle = "Painel 3D do carro, com os widgets e os controles",
                    previewRes = R.drawable.impulse_home_preview,
                    status =
                            when {
                                homeProgress != null ->
                                        "Baixando " + (homeProgress * 100).toInt() + "%"
                                homeUpdate -> "Atualizacao disponivel"
                                homeInstalled != null -> "v" + homeInstalled
                                homeCatalog != null -> "Nao instalado"
                                else -> "Indisponivel no catalogo"
                            },
                    statusTint =
                            if (homeInstalled != null) ImpTokens.Accent else ImpTokens.TextSecondary
            ) {
                if (homeCatalog != null && (homeInstalled == null || homeUpdate)) {
                    CardButton(
                            if (homeUpdate) "Atualizar" else "Instalar",
                            ImpTokens.Accent,
                            enabled = homeProgress == null
                    ) { startDownload(homeCatalog) }
                } else if (homeInstalled != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardButton("Abrir", ImpTokens.Accent) {
                            context.packageManager
                                    .getLaunchIntentForPackage(IMPULSE_HOME_PACKAGE)
                                    ?.let { intent ->
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                        }
                        CardButton("Ajustar", ImpTokens.TrackOff) { showHomeSetup = true }
                        IconButton(
                            onClick = {
                                appToUninstall = IMPULSE_HOME_PACKAGE
                                appNameToUninstall = "Impulse Launcher"
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Desinstalar Impulse Launcher",
                                tint = Color(0xFFEF5350)
                            )
                        }
                    }
                }
            }
        }

        item {
            val mainPkg = StartupAppManager.mainDisplayPackage()
            val secondaryPkg =
                    prefs.getString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, "")
                            .orEmpty()
            FeatureCard(
                    icon = Icons.Default.PlayCircle,
                    iconTint =
                            if (mainPkg.isNotEmpty() || secondaryPkg.isNotEmpty()) ImpTokens.Accent
                            else Color.White,
                    highlighted = mainPkg.isNotEmpty() || secondaryPkg.isNotEmpty(),
                    title = "Abrir ao ligar",
                    subtitle = "Um app por tela quando o carro liga",
                    status = null,
                    extra = {
                        // Lado a lado: empilhadas, as duas linhas empurravam o botao para fora
                        // do card.
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StartupSlotRow("Principal", mainPkg, Modifier.weight(1f))
                            StartupSlotRow("Secundaria", secondaryPkg, Modifier.weight(1f))
                        }
                    }
            ) { CardButton("Alterar", ImpTokens.Accent) { showStartupApps = true } }
        }

        item(span = { GridItemSpan(4) }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeader("Baixar de URL")
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("URL do APK") },
                            modifier = Modifier.weight(1f),
                            colors =
                                    TextFieldDefaults.colors(
                                            focusedContainerColor = ImpTokens.TrackOff,
                                            unfocusedContainerColor = ImpTokens.TrackOff,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = ImpTokens.TextSecondary
                                    )
                    )
                    if (!downloadingUrl) {
                        Button(
                                onClick = {
                                    if (urlInput.isNotEmpty()) startDownloadFromUrl(urlInput)
                                },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = ImpTokens.Accent
                                        ),
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(8.dp)
                        ) { Text("Instalar via URL", color = Color.White) }
                    }
                }
                if (downloadingUrl) {
                    LinearProgressIndicator(
                            progress = { urlProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = ImpTokens.Accent
                    )
                }
            }
        }

        item(span = { GridItemSpan(4) }) {
            Box(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                SectionHeader("Outros aplicativos")
            }
        }

        if (isLoading) {
            item(span = { GridItemSpan(4) }) {
                Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = ImpTokens.Accent) }
            }
        } else {
            val sortedApps =
                    allApps.sortedWith(
                            compareBy(
                                    { app ->
                                        val installedVersion = getInstalledVersion(app.packageName)
                                        val isInstalled = installedVersion != null
                                        val needsUpdate =
                                                isInstalled &&
                                                        app.version.isNotEmpty() &&
                                                        compareVersions(
                                                                installedVersion,
                                                                app.version
                                                        ) < 0
                                        when {
                                            needsUpdate -> 0
                                            !isInstalled -> 1
                                            else -> 2
                                        }
                                    },
                                    { it.name.lowercase() }
                            )
                    )

            items(sortedApps) { app ->
                val installedVersion = getInstalledVersion(app.packageName)
                val isInstalled = installedVersion != null
                val needsUpdate = isInstalled && app.version.isNotEmpty() && compareVersions(installedVersion, app.version) < 0
                val progress = downloadProgress[app.packageName] ?: 0f

                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(230.dp)
                                        .padding(4.dp)
                                        .border(1.dp, ImpTokens.Hairline, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
                        shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                    modifier = Modifier.size(68.dp),
                                    contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = ImpTokens.TrackOff
                                ) {
                                    val localIcon = remember(app.packageName) {
                                        if (isInstalled) runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull() else null
                                    }
                                    if (localIcon != null) {
                                        AsyncImage(
                                                model = localIcon,
                                                contentDescription = app.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                        )
                                    } else if (!app.iconUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                                model =
                                                        ImageRequest.Builder(context)
                                                                .data(app.iconUrl)
                                                                .crossfade(true)
                                                                .build(),
                                                contentDescription = app.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                    Icons.Default.Build,
                                                    contentDescription = app.name,
                                                    tint = ImpTokens.Accent,
                                                    modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                    app.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                            val displayVersion = installedVersion ?: app.version
                            if (displayVersion.isNotEmpty()) {
                                Text("v$displayVersion", fontSize = 12.sp, color = ImpTokens.TextSecondary, maxLines = 1)
                            }
                        }
                        if (downloadingApp == app.packageName) {
                            LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = ImpTokens.Accent
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (needsUpdate && app.link.isNotEmpty()) {
                                    AppActionButton(
                                            text = "Atualizar",
                                            onClick = { startDownload(app) },
                                            isPrimary = true
                                    )
                                }
                                if (!isInstalled && app.link.isNotEmpty()) {
                                    AppActionButton(
                                            text = "Instalar",
                                            onClick = { startDownload(app) },
                                            isPrimary = true
                                    )
                                }
                                if (isInstalled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AppActionButton(
                                                text = "Abrir",
                                                onClick = {
                                                    context.packageManager
                                                            .getLaunchIntentForPackage(app.packageName)
                                                            ?.let { intent ->
                                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                context.startActivity(intent)
                                                            }
                                                },
                                                isPrimary = true,
                                                modifier = Modifier.weight(1f)
                                        )
                                        AppActionButton(
                                                text = "Desinstalar",
                                                onClick = {
                                                    appToUninstall = app.packageName
                                                    appNameToUninstall = app.name
                                                },
                                                isPrimary = false,
                                                modifier = Modifier.weight(1f)
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

    if (showHomeSetup) {
        ImpulseHomeSetupDialog(onDismiss = { showHomeSetup = false })
    }

    if (showStartupApps) {
        StartupAppsDialog(onDismiss = { showStartupApps = false })
    }

    if (showPermissionDialog) {
        AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Permissão necessária") },
                text = { Text("Permita a instalação de apps de fontes desconhecidas.") },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showPermissionDialog = false
                                val intent =
                                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                requestPermissionLauncher.launch(intent)
                            }
                    ) { Text("Configurações") }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) { Text("Cancelar") }
                }
        )
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
                showDiagnostics = showDiagnostics,
                onDismiss = { showDiagnostics = false },
                diagnosticsText = diagnosticsText
        )
    }

    if (appToUninstall != null || isPatchUninstall != null) {
        val titleText = if (isPatchUninstall != null) "Desinstalar Patch" else "Desinstalar Aplicativo"
        val messageText = when {
            isPatchUninstall == "aa" -> "Deseja remover as modificações do patch do Android Auto?"
            isPatchUninstall == "carplay" -> "Deseja remover as modificações do patch do Apple CarPlay?"
            else -> "Deseja realmente desinstalar ${appNameToUninstall ?: "o aplicativo"}?"
        }
        AlertDialog(
            onDismissRequest = {
                appToUninstall = null
                appNameToUninstall = null
                isPatchUninstall = null
            },
            containerColor = ImpTokens.Container,
            title = { Text(titleText, color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(messageText, color = ImpTokens.TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        val patch = isPatchUninstall
                        val pkg = appToUninstall
                        appToUninstall = null
                        appNameToUninstall = null
                        isPatchUninstall = null
                        when (patch) {
                            "aa" -> {
                                scope.launch(Dispatchers.IO) {
                                    if (AndroidAutoPatchManager.uninstallPatches()) {
                                        withContext(Dispatchers.Main) {
                                            isPatchInstalled = false
                                            isMounted = false
                                            refreshTrigger++
                                        }
                                    }
                                }
                            }
                            "carplay" -> {
                                scope.launch(Dispatchers.IO) {
                                    if (CarPlayPatchManager.uninstallPatches()) {
                                        withContext(Dispatchers.Main) {
                                            isCarPlayPatchInstalled = false
                                            isCarPlayMounted = false
                                            refreshTrigger++
                                        }
                                    }
                                }
                            }
                            else -> {
                                if (pkg != null) uninstall(pkg)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Desinstalar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        appToUninstall = null
                        appNameToUninstall = null
                        isPatchUninstall = null
                    }
                ) {
                    Text("Cancelar", color = ImpTokens.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}
