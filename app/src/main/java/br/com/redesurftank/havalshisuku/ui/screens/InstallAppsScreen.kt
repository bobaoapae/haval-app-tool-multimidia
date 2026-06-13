package br.com.redesurftank.havalshisuku.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchManager
import br.com.redesurftank.havalshisuku.managers.CarPlayPatchManager
import br.com.redesurftank.havalshisuku.models.AppInfo
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.*
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

@Composable
fun InstallAppsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf(listOf<AppInfo>()) }
    var downloadingApp by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    val pm = context.packageManager
    val requestPermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
            ) { /* Permission requested */}
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
    var showDiagnostics by remember { mutableStateOf(false) }
    var diagnosticsText by remember { mutableStateOf("") }

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
            isPatchInstalled = AndroidAutoPatchManager.isPatchInstalled()
            isMounted = AndroidAutoPatchManager.isMounted()
            isCarPlayPatchInstalled = CarPlayPatchManager.isPatchInstalled()
            isCarPlayMounted = CarPlayPatchManager.isMounted()
            delay(2000)
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
        return try {
            val info = pm.getPackageInfo(packageName, 0)
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun compareVersions(v1: String?, v2: String): Int {
        if (v1 == null) return -1
        val clean1 = v1.removeSuffix("-preview")
        val clean2 = v2.removeSuffix("-preview")
        val parts1 = clean1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until min(parts1.size, parts2.size)) {
            if (parts1[i] > parts2[i]) return 1
            if (parts1[i] < parts2[i]) return -1
        }
        return parts1.size.compareTo(parts2.size)
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
        val intent = Intent(Intent.ACTION_DELETE).apply { data = Uri.parse("package:$packageName") }
        context.startActivity(intent)
    }

    LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(4) }) {
            Card(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(
                                            width = 1.dp,
                                            color =
                                                    if (isMounted) Color(0xFF4A9EFF)
                                                    else Color(0xFF1D2430),
                                            shape = RoundedCornerShape(12.dp)
                                    ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
                    shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                            modifier =
                                    Modifier.size(48.dp).background(Color(0xFF2A2F37), CircleShape),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (isMounted) Color(0xFF4A9EFF) else Color.White,
                                modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                "Android Auto (Patch Impulse) - Ajusta melhor às dimensoes do cluster e não perde o foco",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                text =
                                        when {
                                            isMounted -> "Status: Ativo (Mounted)"
                                            isPatchInstalled ->
                                                    "Status: Instalado (Pronto para ativar)"
                                            else -> "Status: Não instalado"
                                        },
                                color = if (isMounted) Color(0xFF4A9EFF) else Color(0xFFB0B8C4),
                                fontSize = 13.sp
                        )
                        if (isPatchInstalled) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Switch(
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
                                        },
                                        modifier = Modifier.scale(0.7f),
                                        colors =
                                                SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = Color(0xFF4A9EFF)
                                                )
                                )
                                Text(
                                        "Auto-montar ao iniciar",
                                        color = Color(0xFFB0B8C4),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                    Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isPatchInstalled) {
                            Button(
                                    onClick = {
                                        if (AndroidAutoPatchManager.installPatches(context))
                                                isPatchInstalled = true
                                    },
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF4A9EFF)
                                            ),
                                    shape = RoundedCornerShape(8.dp)
                            ) { Text("Instalar", color = Color.White) }
                        } else {
                            if (!isMounted) {
                                Button(
                                        onClick = {
                                            if (AndroidAutoPatchManager.applyMounts())
                                                    isMounted = true
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF4CAF50)
                                                ),
                                        shape = RoundedCornerShape(8.dp)
                                ) { Text("Ativar", color = Color.White) }
                            } else {
                                Button(
                                        onClick = {
                                            if (AndroidAutoPatchManager.removeMounts())
                                                    isMounted = false
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFF44336)
                                                ),
                                        shape = RoundedCornerShape(8.dp)
                                ) { Text("Desativar", color = Color.White) }
                            }
                            IconButton(
                                    onClick = {
                                        if (AndroidAutoPatchManager.uninstallPatches()) {
                                            isPatchInstalled = false
                                            isMounted = false
                                        }
                                    }
                            ) {
                                Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remover Patch",
                                        tint = Color.Gray
                                )
                            }
                            IconButton(
                                    onClick = {
                                        diagnosticsText = AndroidAutoPatchManager.getDiagnostics()
                                        showDiagnostics = true
                                    }
                            ) {
                                Icon(
                                        Icons.Default.BugReport,
                                        contentDescription = "Diagnóstico",
                                        tint = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(4) }) {
            Card(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(
                                            width = 1.dp,
                                            color =
                                                    if (isCarPlayMounted) Color(0xFF34C759)
                                                    else Color(0xFF1D2430),
                                            shape = RoundedCornerShape(12.dp)
                                    ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
                    shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                            modifier =
                                    Modifier.size(48.dp).background(Color(0xFF2A2F37), CircleShape),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Default.PhoneIphone,
                                contentDescription = null,
                                tint = if (isCarPlayMounted) Color(0xFF34C759) else Color.White,
                                modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                "Apple CarPlay (Patch HVAC D3) - Mantem o foco de video durante o painel de ar",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                text =
                                        when {
                                            isCarPlayMounted -> "Status: Ativo (Mounted)"
                                            isCarPlayPatchInstalled ->
                                                    "Status: Instalado (Pronto para ativar)"
                                            else -> "Status: Não instalado"
                                        },
                                color =
                                        if (isCarPlayMounted) Color(0xFF34C759)
                                        else Color(0xFFB0B8C4),
                                fontSize = 13.sp
                        )
                        if (isCarPlayPatchInstalled) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Switch(
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
                                        },
                                        modifier = Modifier.scale(0.7f),
                                        colors =
                                                SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = Color(0xFF34C759)
                                                )
                                )
                                Text(
                                        "Auto-montar ao iniciar",
                                        color = Color(0xFFB0B8C4),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                    Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isCarPlayPatchInstalled) {
                            Button(
                                    onClick = {
                                        if (CarPlayPatchManager.installPatches(context))
                                                isCarPlayPatchInstalled = true
                                    },
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF34C759)
                                            ),
                                    shape = RoundedCornerShape(8.dp)
                            ) { Text("Instalar", color = Color.White) }
                        } else {
                            if (!isCarPlayMounted) {
                                Button(
                                        onClick = {
                                            if (CarPlayPatchManager.applyMounts())
                                                    isCarPlayMounted = true
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF4CAF50)
                                                ),
                                        shape = RoundedCornerShape(8.dp)
                                ) { Text("Ativar", color = Color.White) }
                            } else {
                                Button(
                                        onClick = {
                                            if (CarPlayPatchManager.removeMounts())
                                                    isCarPlayMounted = false
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFF44336)
                                                ),
                                        shape = RoundedCornerShape(8.dp)
                                ) { Text("Desativar", color = Color.White) }
                            }
                            IconButton(
                                    onClick = {
                                        if (CarPlayPatchManager.uninstallPatches()) {
                                            isCarPlayPatchInstalled = false
                                            isCarPlayMounted = false
                                        }
                                    }
                            ) {
                                Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remover Patch CarPlay",
                                        tint = Color.Gray
                                )
                            }
                            IconButton(
                                    onClick = {
                                        diagnosticsText = CarPlayPatchManager.getDiagnostics()
                                        showDiagnostics = true
                                    }
                            ) {
                                Icon(
                                        Icons.Default.BugReport,
                                        contentDescription = "Diagnóstico CarPlay",
                                        tint = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(4) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("URL do APK") },
                            modifier = Modifier.weight(1f),
                            colors =
                                    TextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF2A2F37),
                                            unfocusedContainerColor = Color(0xFF2A2F37),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color(0xFFB0B8C4)
                                    )
                    )
                    if (!downloadingUrl) {
                        Button(
                                onClick = {
                                    if (urlInput.isNotEmpty()) startDownloadFromUrl(urlInput)
                                },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF4A9EFF)
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
                            color = Color(0xFF4A9EFF)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        "Aplicativos disponíveis:",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                )
            }
        }

        if (isLoading) {
            item(span = { GridItemSpan(4) }) {
                Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Color(0xFF4A9EFF)) }
            }
        } else {
            val sortedApps =
                    apps.sortedWith(
                            compareBy(
                                    { app ->
                                        val installedVersion = getInstalledVersion(app.packageName)
                                        val isInstalled = installedVersion != null
                                        val needsUpdate =
                                                isInstalled &&
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
                val needsUpdate = isInstalled && compareVersions(installedVersion, app.version) < 0
                val progress = downloadProgress[app.packageName] ?: 0f

                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(1.2f)
                                        .padding(8.dp)
                                        .border(1.dp, Color(0xFF1D2430), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
                        shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                    modifier = Modifier.size(80.dp),
                                    contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF2A2F37)
                                ) {
                                    if (!app.iconUrl.isNullOrEmpty()) {
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
                                                    tint = Color(0xFF4A9EFF),
                                                    modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    app.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                            Text("v${app.version}", fontSize = 12.sp, color = Color(0xFFB0B8C4))
                        }
                        if (downloadingApp == app.packageName) {
                            LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = Color(0xFF4A9EFF)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!isInstalled || needsUpdate) {
                                    AppActionButton(
                                            text = if (!isInstalled) "Instalar" else "Atualizar",
                                            onClick = { startDownload(app) },
                                            isPrimary = true
                                    )
                                }
                                if (isInstalled) {
                                    AppActionButton(
                                            text = "Desinstalar",
                                            onClick = { uninstall(app.packageName) },
                                            isPrimary = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
}
