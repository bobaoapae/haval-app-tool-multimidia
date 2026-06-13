package br.com.redesurftank.havalshisuku.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.TAG
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.ReleaseInfo
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.UpdateCheckResult
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min



@Composable
fun InformacoesTab() {
        val context = LocalContext.current
        val prefs =
                App.getDeviceProtectedContext()
                        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        var isActive by remember {
                mutableStateOf(ServiceManager.getInstance().isServicesInitialized)
        }
        var bypassSelfInstallationCheck by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.key,
                                false
                        )
                )
        }
        var selfInstallationCheck by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                SharedPreferencesKeys.SELF_INSTALLATION_INTEGRITY_CHECK.key,
                                false
                        )
                )
        }
        var formattedTime by remember { mutableStateOf("Não inicializado") }
        var formattedTime2 by remember { mutableStateOf("Não inicializado") }
        var formattedTime3 by remember { mutableStateOf("Não inicializado") }
        var version by remember { mutableStateOf("Desconhecida") }
        var clickCount by remember { mutableIntStateOf(0) }
        var showAdvancedDialog by remember { mutableStateOf(false) }
        var showUpdateDialog by remember { mutableStateOf(false) }
        var updateMessage by remember { mutableStateOf("") }
        var isDownloading by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableFloatStateOf(0f) }
        var downloadError by remember { mutableStateOf<String?>(null) }
        var downloadJob by remember { mutableStateOf<Job?>(null) }
        var showUpdateCheckDialog by remember { mutableStateOf(false) }
        var updateCheckResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
        var isCheckingUpdates by remember { mutableStateOf(false) }
        var showBetaUpdates by remember {
                mutableStateOf(prefs.getBoolean(SharedPreferencesKeys.SHOW_BETA_UPDATES.key, false))
        }
        val scope = rememberCoroutineScope()
        val requestPermissionLauncher =
                rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                ) { /* Permission requested */}
        var showPermissionDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
                try {
                        val packageInfo =
                                context.packageManager.getPackageInfo(context.packageName, 0)
                        version = packageInfo.versionName ?: "Desconhecida"
                } catch (e: PackageManager.NameNotFoundException) {
                        version = "Erro"
                }
        }

        LaunchedEffect(Unit) {
                while (true) {
                        isActive = ServiceManager.getInstance().isServicesInitialized
                        val timeBoot = ServiceManager.getInstance().timeBootReceived
                        formattedTime =
                                if (isActive && timeBoot > 0) {
                                        val minutes = timeBoot / 60000
                                        val seconds = (timeBoot / 1000) % 60
                                        val millis = timeBoot % 1000
                                        String.format("%02d:%02d.%03d", minutes, seconds, millis)
                                } else {
                                        "Não inicializado"
                                }
                        val timeStart = ServiceManager.getInstance().timeStartInitialization
                        formattedTime2 =
                                if (isActive && timeStart > 0) {
                                        val minutes = timeStart / 60000
                                        val seconds = (timeStart / 1000) % 60
                                        val millis = timeStart % 1000
                                        String.format("%02d:%02d.%03d", minutes, seconds, millis)
                                } else {
                                        "Não inicializado"
                                }
                        val timeInit = ServiceManager.getInstance().timeInitialized
                        formattedTime3 =
                                if (isActive && timeInit > 0) {
                                        val minutes = timeInit / 60000
                                        val seconds = (timeInit / 1000) % 60
                                        val millis = timeInit % 1000
                                        String.format("%02d:%02d.%03d", minutes, seconds, millis)
                                } else {
                                        "Não inicializado"
                                }
                        delay(100)
                }
        }

        suspend fun getAllReleaseInfo(): UpdateCheckResult {
                return withContext(Dispatchers.IO) {
                        try {
                                val url =
                                        URL(
                                                "https://api.github.com/repos/bobaoapae/haval-app-tool-multimidia/releases"
                                        )
                                val conn = url.openConnection() as HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                                if (conn.responseCode == 200) {
                                        val reader =
                                                BufferedReader(InputStreamReader(conn.inputStream))
                                        val response = reader.use { it.readText() }
                                        val releases = JSONArray(response)

                                        var latestRelease: ReleaseInfo? = null
                                        var latestPreview: ReleaseInfo? = null

                                        for (i in 0 until releases.length()) {
                                                val release = releases.getJSONObject(i)
                                                val isPrerelease = release.getBoolean("prerelease")
                                                val tag = release.getString("tag_name")
                                                val assets = release.getJSONArray("assets")
                                                var dlUrl: String? = null
                                                for (j in 0 until assets.length()) {
                                                        val asset = assets.getJSONObject(j)
                                                        if (asset.getString("name").endsWith(".apk")
                                                        ) {
                                                                dlUrl =
                                                                        asset.getString(
                                                                                "browser_download_url"
                                                                        )
                                                                break
                                                        }
                                                }
                                                if (dlUrl != null) {
                                                        val info =
                                                                ReleaseInfo(
                                                                        tag,
                                                                        dlUrl,
                                                                        isPrerelease
                                                                )
                                                        if (isPrerelease && latestPreview == null) {
                                                                latestPreview = info
                                                        } else if (!isPrerelease &&
                                                                        latestRelease == null
                                                        ) {
                                                                latestRelease = info
                                                        }
                                                }
                                                if (latestRelease != null && latestPreview != null)
                                                        break
                                        }

                                        UpdateCheckResult(latestRelease, latestPreview)
                                } else UpdateCheckResult(null, null)
                        } catch (e: Exception) {
                                Log.w(TAG, "Error fetching release info", e)
                                UpdateCheckResult(null, null)
                        }
                }
        }

        fun compareVersions(v1: String, v2: String): Int {
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

        fun startDownload(url: String, resetTargetVersion: String? = null) {
                isDownloading = true
                downloadProgress = 0f
                downloadJob =
                        scope.launch(Dispatchers.IO) {
                                try {
                                        val file =
                                                File(
                                                        context.getExternalFilesDir(null),
                                                        "update.apk"
                                                )
                                        withContext(Dispatchers.IO) {
                                                val dlUrl = URL(url)
                                                val conn =
                                                        dlUrl.openConnection() as HttpURLConnection
                                                val length = conn.contentLength
                                                val input = BufferedInputStream(conn.inputStream)
                                                val output = FileOutputStream(file)
                                                val buffer = ByteArray(4096)
                                                var bytesRead: Int
                                                var total = 0
                                                while (input.read(buffer).also { bytesRead = it } !=
                                                        -1) {
                                                        output.write(buffer, 0, bytesRead)
                                                        total += bytesRead
                                                        if (length > 0)
                                                                downloadProgress =
                                                                        total.toFloat() / length
                                                }
                                                output.close()
                                                input.close()
                                        }
                                        isDownloading = false

                                        if (resetTargetVersion != null) {
                                                prefs.edit()
                                                        .putString(
                                                                SharedPreferencesKeys
                                                                        .PENDING_RESET_TARGET_VERSION
                                                                        .key,
                                                                resetTargetVersion
                                                        )
                                                        .apply()
                                        }

                                        withContext(Dispatchers.Main) {
                                                if (!context.packageManager
                                                                .canRequestPackageInstalls()
                                                ) {
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
                                                                setDataAndType(
                                                                        uri,
                                                                        "application/vnd.android.package-archive"
                                                                )
                                                                addFlags(
                                                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                                )
                                                                addFlags(
                                                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                                                )
                                                        }
                                                context.startActivity(intent)
                                        }
                                } catch (e: Exception) {
                                        Log.e(TAG, "Download failed", e)
                                        isDownloading = false
                                        downloadError = e.message ?: "Erro desconhecido"
                                }
                        }
        }

        val scrollState = rememberScrollState()

        Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // Seção de Status
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
                        shape = RoundedCornerShape(12.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                Text(
                                        "Status do Sistema",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                )

                                HorizontalDivider(color = Color(0xFF1D2430))

                                if (!bypassSelfInstallationCheck) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Text(
                                                        "Instalado corretamente:",
                                                        color = Color(0xFFB0B8C4)
                                                )
                                                Text(
                                                        if (selfInstallationCheck) "Sim" else "Não",
                                                        color =
                                                                if (selfInstallationCheck)
                                                                        Color(0xFF4ADE80)
                                                                else Color(0xFFEF4444)
                                                )
                                        }
                                }

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Text("Estado:", color = Color(0xFFB0B8C4))
                                        Text(
                                                if (isActive) "Ativo" else "Inativo",
                                                color =
                                                        if (isActive) Color(0xFF4ADE80)
                                                        else Color(0xFFEF4444),
                                                fontWeight = FontWeight.Medium
                                        )
                                }

                                if (isActive) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Text(
                                                        "Boot Completed:",
                                                        color = Color(0xFFB0B8C4),
                                                        fontSize = 14.sp
                                                )
                                                Text(
                                                        formattedTime,
                                                        color = Color.White,
                                                        fontSize = 14.sp
                                                )
                                        }
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Text(
                                                        "Início:",
                                                        color = Color(0xFFB0B8C4),
                                                        fontSize = 14.sp
                                                )
                                                Text(
                                                        formattedTime2,
                                                        color = Color.White,
                                                        fontSize = 14.sp
                                                )
                                        }
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Text(
                                                        "Inicialização:",
                                                        color = Color(0xFFB0B8C4),
                                                        fontSize = 14.sp
                                                )
                                                Text(
                                                        formattedTime3,
                                                        color = Color.White,
                                                        fontSize = 14.sp
                                                )
                                        }
                                }

                                HorizontalDivider(color = Color(0xFF1D2430))

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column {
                                                Text(
                                                        "Versão",
                                                        color = Color(0xFFB0B8C4),
                                                        fontSize = 14.sp
                                                )
                                                Text(
                                                        version,
                                                        color = Color.White,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier =
                                                                Modifier.clickable {
                                                                        clickCount++
                                                                        if (clickCount >= 5) {
                                                                                showAdvancedDialog =
                                                                                        true
                                                                                clickCount = 0
                                                                        }
                                                                }
                                                )
                                        }

                                        Button(
                                                onClick = {
                                                        isCheckingUpdates = true
                                                        scope.launch {
                                                                val result = getAllReleaseInfo()
                                                                updateCheckResult = result
                                                                isCheckingUpdates = false
                                                                showUpdateCheckDialog = true
                                                        }
                                                },
                                                modifier = Modifier.height(48.dp),
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor = AppColors.Primary
                                                        ),
                                                shape =
                                                        RoundedCornerShape(
                                                                AppDimensions.ButtonCornerRadius
                                                        )
                                        ) {
                                                Icon(
                                                        Icons.Default.Refresh,
                                                        contentDescription = "Buscar Atualizações",
                                                        modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Buscar Atualizações", fontSize = 14.sp)
                                        }
                                }

                                HorizontalDivider(color = Color(0xFF1D2430))

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                ) {
                                        Button(
                                                onClick = {
                                                        val intent =
                                                                Intent(Intent.ACTION_MAIN).apply {
                                                                        component =
                                                                                ComponentName(
                                                                                        "com.android.settings",
                                                                                        "com.android.settings.Settings"
                                                                                )
                                                                }
                                                        context.startActivity(intent)
                                                },
                                                modifier = Modifier.height(48.dp),
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor = AppColors.Primary
                                                        ),
                                                shape =
                                                        RoundedCornerShape(
                                                                AppDimensions.ButtonCornerRadius
                                                        )
                                        ) {
                                                Icon(
                                                        Icons.Default.Settings,
                                                        contentDescription = "Configurações",
                                                        modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                        "Abrir Configurações do Android",
                                                        color = Color.White
                                                )
                                        }
                                }
                        }
                }

                // Seção de Contribuição
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
                        shape = RoundedCornerShape(12.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Text(
                                        "Contribua para o Desenvolvimento",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                )

                                HorizontalDivider(color = Color(0xFF1D2430))

                                Text(
                                        "Ajude a manter este projeto ativo! Sua contribuição é muito importante para o desenvolvimento contínuo do app.",
                                        fontSize = 14.sp,
                                        color = Color(0xFFB0B8C4),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                )

                                // QR Code
                                Image(
                                        painter = painterResource(id = R.drawable.qrcode),
                                        contentDescription = "QR Code para contribuição",
                                        modifier = Modifier.size(200.dp).padding(8.dp),
                                        contentScale = ContentScale.Fit
                                )

                                Text(
                                        "Escaneie o QR Code ou use a chave PIX: joaovitorbor@gmail.com",
                                        fontSize = 16.sp,
                                        color = Color(0xFFB0B8C4),
                                        textAlign = TextAlign.Center
                                )

                                Text(
                                        "Obrigado pelo seu apoio! 🙏",
                                        fontSize = 14.sp,
                                        color = Color(0xFF4ADE80),
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                )
                        }
                }
        }

        if (showAdvancedDialog) {
                AlertDialog(
                        onDismissRequest = { showAdvancedDialog = false },
                        title = { Text("Confirmação") },
                        text = {
                                Text(
                                        "Quer ativar o uso avançado? Pode causar instabilidades, utilize por conta e risco."
                                )
                        },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                showAdvancedDialog = false
                                                prefs.edit {
                                                        putBoolean(
                                                                SharedPreferencesKeys.ADVANCE_USE
                                                                        .key,
                                                                true
                                                        )
                                                }
                                        }
                                ) { Text("Ativar") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showAdvancedDialog = false }) {
                                        Text("Cancelar")
                                }
                        }
                )
        }

        if (showUpdateDialog) {
                AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = { Text("Verificação de Atualização") },
                        text = { Text(updateMessage) },
                        confirmButton = {
                                TextButton(onClick = { showUpdateDialog = false }) { Text("OK") }
                        }
                )
        }

        if (isCheckingUpdates) {
                AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Verificando atualizações...") },
                        text = {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Text("Buscando versões disponíveis...")
                                }
                        },
                        confirmButton = {}
                )
        }

        if (showUpdateCheckDialog && updateCheckResult != null) {
                val result = updateCheckResult!!
                val isPreviewChannel = version.contains("-preview")
                val currentChannel = if (isPreviewChannel) "Beta" else "Estável"
                val currentClean = version.removePrefix("v")

                AlertDialog(
                        onDismissRequest = { showUpdateCheckDialog = false },
                        title = { Text("Atualizações") },
                        text = {
                                Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        // Canal atual
                                        Text(
                                                "Canal atual: $currentChannel ($version)",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                        )

                                        if (isPreviewChannel) {
                                                // --- Usuário está em Preview ---
                                                val hasPreviewUpdate =
                                                        result.latestPreview != null &&
                                                                compareVersions(
                                                                        result.latestPreview.tag
                                                                                .removePrefix("v"),
                                                                        currentClean
                                                                ) > 0
                                                val hasReleaseUpgrade =
                                                        result.latestRelease != null &&
                                                                compareVersions(
                                                                        result.latestRelease.tag
                                                                                .removePrefix("v"),
                                                                        currentClean
                                                                ) > 0

                                                // Preview mais nova?
                                                if (hasPreviewUpdate) {
                                                        Card(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                colors =
                                                                        CardDefaults.cardColors(
                                                                                containerColor =
                                                                                        Color(
                                                                                                0xFF1A1D24
                                                                                        )
                                                                        )
                                                        ) {
                                                                Column(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        12.dp
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                "Nova beta: ${result.latestPreview!!.tag}",
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                fontSize = 14.sp
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        Button(
                                                                                onClick = {
                                                                                        showUpdateCheckDialog =
                                                                                                false
                                                                                        startDownload(
                                                                                                result.latestPreview
                                                                                                        .downloadUrl
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.align(
                                                                                                Alignment
                                                                                                        .End
                                                                                        ),
                                                                                colors =
                                                                                        ButtonDefaults
                                                                                                .buttonColors(
                                                                                                        containerColor =
                                                                                                                AppColors
                                                                                                                        .Primary
                                                                                                ),
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                AppDimensions
                                                                                                        .ButtonCornerRadius
                                                                                        )
                                                                        ) { Text("Atualizar") }
                                                                }
                                                        }
                                                }

                                                // Release disponível para voltar ao estável (só se
                                                // build number maior —
                                                // Intent não permite downgrade)
                                                if (hasReleaseUpgrade) {
                                                        Card(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                colors =
                                                                        CardDefaults.cardColors(
                                                                                containerColor =
                                                                                        Color(
                                                                                                0xFF1A1D24
                                                                                        )
                                                                        )
                                                        ) {
                                                                Column(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        12.dp
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                "Estável: ${result.latestRelease!!.tag}",
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                fontSize = 14.sp
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                4.dp
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                "Os dados do app serão resetados ao voltar para estável.",
                                                                                fontSize = 12.sp,
                                                                                color =
                                                                                        Color(
                                                                                                0xFFFF9800
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        Button(
                                                                                onClick = {
                                                                                        showUpdateCheckDialog =
                                                                                                false
                                                                                        startDownload(
                                                                                                url =
                                                                                                        result.latestRelease
                                                                                                                .downloadUrl,
                                                                                                resetTargetVersion =
                                                                                                        result.latestRelease
                                                                                                                .tag
                                                                                                                .removePrefix(
                                                                                                                        "v"
                                                                                                                )
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.align(
                                                                                                Alignment
                                                                                                        .End
                                                                                        ),
                                                                                colors =
                                                                                        ButtonDefaults
                                                                                                .buttonColors(
                                                                                                        containerColor =
                                                                                                                Color(
                                                                                                                        0xFFFF9800
                                                                                                                )
                                                                                                ),
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                AppDimensions
                                                                                                        .ButtonCornerRadius
                                                                                        )
                                                                        ) {
                                                                                Text(
                                                                                        "Voltar para Estável"
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }

                                                if (!hasPreviewUpdate && !hasReleaseUpgrade) {
                                                        Text(
                                                                "Você está na versão mais recente",
                                                                fontSize = 14.sp,
                                                                color = Color(0xFF4ADE80)
                                                        )
                                                }
                                        } else {
                                                // --- Usuário está em Release (Estável) ---
                                                val hasReleaseUpdate =
                                                        result.latestRelease != null &&
                                                                compareVersions(
                                                                        result.latestRelease.tag
                                                                                .removePrefix("v"),
                                                                        currentClean
                                                                ) > 0
                                                val hasPreviewAvailable =
                                                        showBetaUpdates &&
                                                                result.latestPreview != null &&
                                                                compareVersions(
                                                                        result.latestPreview.tag
                                                                                .removePrefix("v"),
                                                                        currentClean
                                                                ) > 0

                                                // Update estável disponível?
                                                if (hasReleaseUpdate) {
                                                        Card(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                colors =
                                                                        CardDefaults.cardColors(
                                                                                containerColor =
                                                                                        Color(
                                                                                                0xFF1A1D24
                                                                                        )
                                                                        )
                                                        ) {
                                                                Column(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        12.dp
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                "Nova versão: ${result.latestRelease.tag}",
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                fontSize = 14.sp
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        Button(
                                                                                onClick = {
                                                                                        showUpdateCheckDialog =
                                                                                                false
                                                                                        startDownload(
                                                                                                result.latestRelease
                                                                                                        .downloadUrl
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.align(
                                                                                                Alignment
                                                                                                        .End
                                                                                        ),
                                                                                colors =
                                                                                        ButtonDefaults
                                                                                                .buttonColors(
                                                                                                        containerColor =
                                                                                                                AppColors
                                                                                                                        .Primary
                                                                                                ),
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                AppDimensions
                                                                                                        .ButtonCornerRadius
                                                                                        )
                                                                        ) { Text("Atualizar") }
                                                                }
                                                        }
                                                }

                                                // Toggle beta
                                                HorizontalDivider(color = Color(0xFF1D2430))
                                                Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Text(
                                                                "Mostrar versões Beta",
                                                                fontSize = 14.sp
                                                        )
                                                        Switch(
                                                                checked = showBetaUpdates,
                                                                onCheckedChange = {
                                                                        showBetaUpdates = it
                                                                        prefs.edit()
                                                                                .putBoolean(
                                                                                        SharedPreferencesKeys
                                                                                                .SHOW_BETA_UPDATES
                                                                                                .key,
                                                                                        it
                                                                                )
                                                                                .apply()
                                                                },
                                                                modifier = Modifier.scale(0.9f),
                                                                colors =
                                                                        SwitchDefaults.colors(
                                                                                checkedThumbColor =
                                                                                        br.com
                                                                                                .redesurftank
                                                                                                .havalshisuku
                                                                                                .ui
                                                                                                .components
                                                                                                .AppColors
                                                                                                .TextPrimary,
                                                                                checkedTrackColor =
                                                                                        br.com
                                                                                                .redesurftank
                                                                                                .havalshisuku
                                                                                                .ui
                                                                                                .components
                                                                                                .AppColors
                                                                                                .Primary,
                                                                                uncheckedThumbColor =
                                                                                        br.com
                                                                                                .redesurftank
                                                                                                .havalshisuku
                                                                                                .ui
                                                                                                .components
                                                                                                .AppColors
                                                                                                .TextSecondary,
                                                                                uncheckedTrackColor =
                                                                                        br.com
                                                                                                .redesurftank
                                                                                                .havalshisuku
                                                                                                .ui
                                                                                                .components
                                                                                                .AppColors
                                                                                                .ButtonSecondary,
                                                                                uncheckedBorderColor =
                                                                                        Color.Transparent,
                                                                                checkedBorderColor =
                                                                                        Color.Transparent
                                                                        )
                                                        )
                                                }

                                                // Preview disponível (só aparece se toggle ativo)
                                                if (hasPreviewAvailable) {
                                                        Text(
                                                                "Versões beta são para entusiastas e usuários com conhecimento técnico. Podem conter bugs, instabilidades e funcionalidades incompletas. Use por sua conta e risco.",
                                                                fontSize = 11.sp,
                                                                color = Color(0xFFFF9800),
                                                                lineHeight = 14.sp
                                                        )
                                                        Card(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                colors =
                                                                        CardDefaults.cardColors(
                                                                                containerColor =
                                                                                        Color(
                                                                                                0xFF1A1D24
                                                                                        )
                                                                        )
                                                        ) {
                                                                Column(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        12.dp
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                "Beta: ${result.latestPreview!!.tag}",
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                fontSize = 14.sp,
                                                                                color =
                                                                                        Color(
                                                                                                0xFFFF9800
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                4.dp
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                "Versão experimental. Pode conter bugs e instabilidades.",
                                                                                fontSize = 12.sp,
                                                                                color =
                                                                                        Color(
                                                                                                0xFFB0B8C4
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        Button(
                                                                                onClick = {
                                                                                        showUpdateCheckDialog =
                                                                                                false
                                                                                        startDownload(
                                                                                                result.latestPreview
                                                                                                        .downloadUrl
                                                                                        )
                                                                                },
                                                                                modifier =
                                                                                        Modifier.align(
                                                                                                Alignment
                                                                                                        .End
                                                                                        ),
                                                                                colors =
                                                                                        ButtonDefaults
                                                                                                .buttonColors(
                                                                                                        containerColor =
                                                                                                                Color(
                                                                                                                        0xFFFF9800
                                                                                                                )
                                                                                                ),
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                AppDimensions
                                                                                                        .ButtonCornerRadius
                                                                                        )
                                                                        ) { Text("Instalar Beta") }
                                                                }
                                                        }
                                                }

                                                if (!hasReleaseUpdate && !hasPreviewAvailable) {
                                                        Text(
                                                                "Você está na versão mais recente",
                                                                fontSize = 14.sp,
                                                                color = Color(0xFF4ADE80)
                                                        )
                                                }
                                        }
                                }
                        },
                        confirmButton = {
                                TextButton(onClick = { showUpdateCheckDialog = false }) {
                                        Text("Fechar")
                                }
                        }
                )
        }

        if (isDownloading) {
                AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Baixando atualização") },
                        text = {
                                Column {
                                        LinearProgressIndicator(progress = { downloadProgress })
                                        Text("${(downloadProgress * 100).toInt()}%")
                                }
                        },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                downloadJob?.cancel()
                                                isDownloading = false
                                        }
                                ) { Text("Cancelar") }
                        }
                )
        }

        if (downloadError != null) {
                AlertDialog(
                        onDismissRequest = { downloadError = null },
                        title = { Text("Erro no download") },
                        text = { Text(downloadError!!) },
                        confirmButton = {
                                TextButton(onClick = { downloadError = null }) { Text("OK") }
                        },
                        dismissButton = {
                                TextButton(onClick = { downloadError = null }) { Text("Cancelar") }
                        }
                )
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
                                                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                                intent.setData(Uri.parse("package:${context.packageName}"))
                                                requestPermissionLauncher.launch(intent)
                                        }
                                ) { Text("Configurações") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showPermissionDialog = false }) {
                                        Text("Cancelar")
                                }
                        }
                )
        }
}
