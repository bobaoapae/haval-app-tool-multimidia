package br.com.redesurftank.havalshisuku.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.BuildConfig
import br.com.redesurftank.havalshisuku.diagnostics.ClusterLogCaptureStatus
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.diagnostics.ProblemReportBuilder
import br.com.redesurftank.havalshisuku.diagnostics.ProblemReportInput
import br.com.redesurftank.havalshisuku.diagnostics.ProblemReportSubmitResult
import br.com.redesurftank.havalshisuku.diagnostics.ProblemReportSubmitter
import br.com.redesurftank.havalshisuku.models.ReleaseInfo
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.theme.Michroma
import br.com.redesurftank.havalshisuku.utils.ApkUpdateInstaller
import br.com.redesurftank.havalshisuku.utils.ReleaseUpdateChecker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ProblemReportAction {
    SUBMIT,
    COPY
}

@Composable
fun ProblemReportTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var description by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var latestPreviewRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var latestPreviewVersion by remember { mutableStateOf<String?>(null) }
    var isCheckingLatestPreview by remember { mutableStateOf(false) }
    var latestPreviewCheckFailed by remember { mutableStateOf(false) }
    var pendingReportAction by remember { mutableStateOf<ProblemReportAction?>(null) }
    var showOutdatedVersionDialog by remember { mutableStateOf(false) }
    var showUpdateImportanceDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableFloatStateOf(0f) }
    var updateDownloadError by remember { mutableStateOf<String?>(null) }
    var updateDownloadJob by remember { mutableStateOf<Job?>(null) }
    var logCaptureStatus by remember {
        mutableStateOf(
                ClusterPersistentEventLogger.getTodayLogCaptureStatus(context.applicationContext)
        )
    }
    val requestPermissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    val isCurrentVersionOutdated =
            latestPreviewVersion?.let {
                ReleaseUpdateChecker.isNewer(it, BuildConfig.VERSION_NAME)
            }
    val canCreateIssue = description.trim().length >= 10

    fun refreshLatestPreviewVersion() {
        scope.launch {
            isCheckingLatestPreview = true
            latestPreviewCheckFailed = false
            val result = ReleaseUpdateChecker.getAllReleaseInfo()
            latestPreviewRelease = result.latestPreview
            latestPreviewVersion = result.latestPreview?.tag
            latestPreviewCheckFailed = result.latestPreview == null
            isCheckingLatestPreview = false
        }
    }

    fun refreshLogCaptureStatus() {
        scope.launch {
            val appContext = context.applicationContext
            logCaptureStatus =
                    withContext(Dispatchers.IO) {
                        ClusterPersistentEventLogger.getTodayLogCaptureStatus(appContext)
                    }
        }
    }

    fun buildCurrentReportInput(): ProblemReportInput {
        return buildProblemReportInput(
                description = description,
                latestPreviewVersion = latestPreviewVersion,
                currentVersionOutdated = isCurrentVersionOutdated,
                latestPreviewCheckFailed = latestPreviewCheckFailed
        )
    }

    fun submitProblemReport() {
        val input = buildCurrentReportInput()
        isCreating = true
        statusMessage = null
        scope.launch {
            try {
                val appContext = context.applicationContext
                val report =
                        withContext(Dispatchers.IO) {
                            ClusterPersistentEventLogger.log(
                                    "problem_report_created",
                                    mapOf("descriptionChars" to description.trim().length)
                            )
                            ProblemReportBuilder.build(appContext, input)
                        }
                val result =
                        withContext(Dispatchers.IO) {
                            ProblemReportSubmitter.submit(appContext, report)
                        }
                val message = handleSubmitResult(context, report.fullBody, result)
                statusMessage = message
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } finally {
                isCreating = false
            }
        }
    }

    fun copyProblemReport() {
        val input = buildCurrentReportInput()
        scope.launch {
            val report =
                    withContext(Dispatchers.IO) {
                        ProblemReportBuilder.build(context.applicationContext, input)
                    }
            copyReportToClipboard(context, report.fullBody)
            val message = "Relatório copiado para a área de transferência."
            statusMessage = message
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun runReportAction(action: ProblemReportAction) {
        when (action) {
            ProblemReportAction.SUBMIT -> submitProblemReport()
            ProblemReportAction.COPY -> copyProblemReport()
        }
    }

    fun requestReportAction(action: ProblemReportAction) {
        if (isCurrentVersionOutdated == true) {
            pendingReportAction = action
            showOutdatedVersionDialog = true
            return
        }
        runReportAction(action)
    }

    fun startUpdateDownload(release: ReleaseInfo) {
        showOutdatedVersionDialog = false
        showUpdateImportanceDialog = false
        pendingReportAction = null
        isDownloadingUpdate = true
        updateDownloadProgress = 0f
        updateDownloadError = null
        updateDownloadJob =
                scope.launch {
                    try {
                        val file =
                                ApkUpdateInstaller.downloadUpdateApk(
                                        context,
                                        release.downloadUrl
                                ) { progress ->
                                    updateDownloadProgress = progress
                                }
                        isDownloadingUpdate = false
                        if (!ApkUpdateInstaller.canRequestPackageInstalls(context)) {
                            showPermissionDialog = true
                            return@launch
                        }
                        context.startActivity(ApkUpdateInstaller.buildInstallIntent(context, file))
                    } catch (e: CancellationException) {
                        isDownloadingUpdate = false
                        throw e
                    } catch (e: Exception) {
                        isDownloadingUpdate = false
                        updateDownloadError = e.message ?: "Erro desconhecido"
                    }
                }
    }

    LaunchedEffect(Unit) { refreshLatestPreviewVersion() }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                            modifier =
                                    Modifier.size(46.dp)
                                            .background(AppColors.SurfaceVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                                text = "Reportar problema",
                                color = AppColors.TextPrimary,
                                fontFamily = Michroma,
                                fontSize = 17.sp
                        )
                        Text(
                                text = "Inclua no relato a data, hora e minutos aproximados do incidente.",
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp
                        )
                    }
                }

                ProblemReportVersionStatus(
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestPreviewVersion = latestPreviewVersion,
                        isChecking = isCheckingLatestPreview,
                        checkFailed = latestPreviewCheckFailed,
                        isOutdated = isCurrentVersionOutdated,
                        onRefresh = { refreshLatestPreviewVersion() }
                )

                TextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descrição do ocorrido") },
                        placeholder = {
                            Text(
                                    "Informe data, hora aproximada, tela/app em uso e o que aconteceu."
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 8,
                        colors =
                                TextFieldDefaults.colors(
                                        focusedContainerColor = AppColors.SurfaceVariant,
                                        unfocusedContainerColor = AppColors.SurfaceVariant,
                                        focusedTextColor = AppColors.TextPrimary,
                                        unfocusedTextColor = AppColors.TextPrimary,
                                        focusedLabelColor = AppColors.Primary,
                                        unfocusedLabelColor = AppColors.TextSecondary,
                                        focusedPlaceholderColor = AppColors.TextSecondary,
                                        unfocusedPlaceholderColor = AppColors.TextSecondary,
                                        cursorColor = AppColors.Primary,
                                        focusedIndicatorColor = AppColors.Primary,
                                        unfocusedIndicatorColor = AppColors.BorderColor
                                )
                )

                Text(
                        text =
                                "Exemplo: 14/06/2026 às 13:32, usando CarPlay no D3 com Waze aberto. O display saiu de Mapa para Normal por cerca de 5 segundos e depois voltou sozinho. Antes disso, toquei no botão direito do volante para acessar o AC.",
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp
                )

                Button(
                        onClick = {
                            requestReportAction(ProblemReportAction.SUBMIT)
                        },
                        enabled = canCreateIssue && !isCreating && !isCheckingLatestPreview,
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = AppColors.Primary,
                                        disabledContainerColor = AppColors.ButtonSecondary
                                ),
                        shape = RoundedCornerShape(AppDimensions.ButtonCornerRadius),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                            if (isCheckingLatestPreview) "Verificando versão..."
                            else if (isCurrentVersionOutdated == true) "Enviar relatório mesmo assim"
                            else "Enviar relatório",
                            color = Color.White
                    )
                }

                if (!canCreateIssue) {
                    Text(
                            text = "A descrição é obrigatória e precisa ter pelo menos 10 caracteres.",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                    )
                }

                statusMessage?.let {
                    Text(text = it, color = AppColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }

        LogCaptureStatusCard(
                status = logCaptureStatus,
                onEnabledChange = { enabled ->
                    scope.launch {
                        val appContext = context.applicationContext
                        withContext(Dispatchers.IO) {
                            ClusterPersistentEventLogger.setPersistentCaptureEnabled(
                                    appContext,
                                    enabled
                            )
                        }
                        logCaptureStatus =
                                withContext(Dispatchers.IO) {
                                    ClusterPersistentEventLogger.getTodayLogCaptureStatus(appContext)
                                }
                    }
                },
                onRefresh = { refreshLogCaptureStatus() }
        )

        Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                        onClick = {
                            requestReportAction(ProblemReportAction.COPY)
                        },
                        enabled = canCreateIssue && !isCreating && !isCheckingLatestPreview,
                        shape = RoundedCornerShape(AppDimensions.ButtonCornerRadius),
                        modifier = Modifier.height(48.dp)
                ) {
                    Text("Copiar relatório", color = AppColors.TextPrimary)
                }
            }
        }
    }

    if (showOutdatedVersionDialog) {
        val release = latestPreviewRelease
        AlertDialog(
                onDismissRequest = {
                    showOutdatedVersionDialog = false
                    pendingReportAction = null
                },
                title = { Text("Versão desatualizada") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                                "Você está usando a versão ${BuildConfig.VERSION_NAME}. A versão atual é ${latestPreviewVersion ?: "não verificada"}."
                        )
                        Text(
                                "Atualize antes de reportar se possível; este problema pode já ter sido corrigido."
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                            enabled = release != null,
                            onClick = {
                                release?.let { startUpdateDownload(it) }
                            }
                    ) {
                        Text("Atualizar")
                    }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                showOutdatedVersionDialog = false
                                showUpdateImportanceDialog = true
                            }
                    ) {
                        Text("Cancelar")
                    }
                }
        )
    }

    if (showUpdateImportanceDialog) {
        AlertDialog(
                onDismissRequest = {
                    showUpdateImportanceDialog = false
                    pendingReportAction = null
                },
                title = { Text("Mantenha o Impulse atualizado") },
                text = {
                    Text(
                            "É importante manter o app atualizado para receber correções de bugs, melhorias e ajustes de estabilidade."
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showUpdateImportanceDialog = false
                                val action = pendingReportAction
                                pendingReportAction = null
                                action?.let { runReportAction(it) }
                            }
                    ) {
                        Text("Reportar mesmo assim")
                    }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                showUpdateImportanceDialog = false
                                pendingReportAction = null
                            }
                    ) {
                        Text("Fechar")
                    }
                }
        )
    }

    if (isDownloadingUpdate) {
        AlertDialog(
                onDismissRequest = {},
                title = { Text("Baixando atualização") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(progress = { updateDownloadProgress })
                        Text("${(updateDownloadProgress * 100).toInt()}%")
                    }
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                updateDownloadJob?.cancel()
                                isDownloadingUpdate = false
                            }
                    ) {
                        Text("Cancelar")
                    }
                }
        )
    }

    if (updateDownloadError != null) {
        AlertDialog(
                onDismissRequest = { updateDownloadError = null },
                title = { Text("Erro no download") },
                text = { Text(updateDownloadError ?: "Erro desconhecido") },
                confirmButton = {
                    TextButton(onClick = { updateDownloadError = null }) { Text("OK") }
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
                                requestPermissionLauncher.launch(
                                        ApkUpdateInstaller.buildUnknownSourcesIntent(context)
                                )
                            }
                    ) {
                        Text("Configurações")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancelar")
                    }
                }
        )
    }
}

@Composable
private fun ProblemReportVersionStatus(
        currentVersion: String,
        latestPreviewVersion: String?,
        isChecking: Boolean,
        checkFailed: Boolean,
        isOutdated: Boolean?,
        onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
                text = "Versão do app",
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
        )
        VersionStatusLine(label = "Instalada", value = currentVersion)
        VersionStatusLine(
                label = "Última preview",
                value =
                        when {
                            isChecking -> "Verificando..."
                            latestPreviewVersion != null -> latestPreviewVersion
                            else -> "Não verificada"
                        }
        )
        Text(
                text =
                        when {
                            isOutdated == true ->
                                    "Esta versão está desatualizada. Atualize antes de enviar se possível; o problema pode já ter sido corrigido."
                            isOutdated == false -> "Você está na última preview conhecida."
                            checkFailed ->
                                    "Não foi possível verificar a última preview agora. Se possível, confira Atualizações em Informações antes de enviar."
                            else -> "Verificando se existe preview mais recente antes do envio."
                        },
                color = if (isOutdated == true) ImpTokens.Attention else AppColors.TextSecondary,
                fontSize = 13.sp
        )
        OutlinedButton(
                onClick = onRefresh,
                enabled = !isChecking,
                shape = RoundedCornerShape(AppDimensions.ButtonCornerRadius),
                modifier = Modifier.height(38.dp)
        ) {
            Text("Verificar novamente", color = AppColors.TextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LogCaptureStatusCard(
        status: ClusterLogCaptureStatus,
        onEnabledChange: (Boolean) -> Unit,
        onRefresh: () -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
            shape = RoundedCornerShape(AppDimensions.CardCornerRadius)
    ) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = "Captura de logs",
                            color = AppColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                    )
                    Text(
                            text = logCaptureStatusText(status),
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                    )
                }
                Switch(
                        checked = status.captureActive,
                        enabled = status.diagnosticLoggingAvailable,
                        onCheckedChange = onEnabledChange,
                        colors =
                                SwitchDefaults.colors(
                                        checkedThumbColor = ImpTokens.OnAccent,
                                        checkedTrackColor = ImpTokens.Accent,
                                        uncheckedThumbColor = ImpTokens.ThumbOff,
                                        uncheckedTrackColor = ImpTokens.TrackOff
                                )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LogStatusLine(label = "Arquivo do dia", value = status.fileName)
                LogStatusLine(
                        label = "Status",
                        value = if (status.fileExists) "Criado" else "Ainda sem arquivo hoje"
                )
                LogStatusLine(label = "Tamanho", value = formatLogSize(status.fileSizeBytes))
                LogStatusLine(
                        label = "Última atualização",
                        value = formatLastModified(status.lastModifiedMs)
                )
            }

            Text(
                    text =
                            "Logcat recente: capturado somente ao enviar ou copiar o relatório, naquele momento. Não fica rodando em segundo plano.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp
            )
            Text(
                    text = "Caminho: ${status.filePath}",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp
            )

            OutlinedButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(AppDimensions.ButtonCornerRadius),
                    modifier = Modifier.height(42.dp)
            ) {
                Text("Atualizar status", color = AppColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun VersionStatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = AppColors.TextSecondary, fontSize = 13.sp)
        Text(text = value, color = AppColors.TextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun LogStatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = AppColors.TextSecondary, fontSize = 13.sp)
        Text(text = value, color = AppColors.TextPrimary, fontSize = 13.sp)
    }
}

private fun logCaptureStatusText(status: ClusterLogCaptureStatus): String {
    if (!status.diagnosticLoggingAvailable) return "Indisponível nesta build."
    return if (status.captureActive) {
        "Ativa: eventos novos entram no log persistente do dia."
    } else {
        "Desativada: eventos novos não serão gravados."
    }
}

private fun formatLogSize(bytes: Long): String {
    return when {
        bytes <= 0L -> "0 B"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> "${bytes / (1024L * 1024L)} MB"
    }
}

private fun formatLastModified(lastModifiedMs: Long): String {
    if (lastModifiedMs <= 0L) return "--"
    return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(lastModifiedMs))
}

private fun buildProblemReportInput(
        description: String,
        latestPreviewVersion: String?,
        currentVersionOutdated: Boolean?,
        latestPreviewCheckFailed: Boolean
): ProblemReportInput {
    return ProblemReportInput(
            description = description,
            carPlayConnected = false,
            androidAutoConnected = false,
            mirroredOnD3 = false,
            notMirroredOnD3 = false,
            mapReturnedToNormal = false,
            acReturnedToMainMenu = false,
            latestPreviewVersionName = latestPreviewVersion,
            currentVersionOutdated = currentVersionOutdated,
            latestPreviewCheckFailed = latestPreviewCheckFailed
    )
}

private fun copyReportToClipboard(context: Context, body: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Relatorio Impulse", body))
}

private fun handleSubmitResult(
        context: Context,
        reportBody: String,
        result: ProblemReportSubmitResult
): String {
    return when (result) {
        is ProblemReportSubmitResult.Success -> {
            val prefix =
                    if (result.chunked) "Relatório enviado com logs completos"
                    else "Relatório enviado"
            if (result.githubIssueUrl.isNullOrBlank()) {
                "$prefix. ID: ${result.reportId}"
            } else {
                "$prefix e issue criada: ${result.githubIssueUrl}"
            }
        }
        ProblemReportSubmitResult.BackendNotConfigured -> {
            copyReportToClipboard(context, reportBody)
            "Backend Supabase não configurado nesta build. Relatório copiado."
        }
        is ProblemReportSubmitResult.HttpError -> {
            copyReportToClipboard(context, reportBody)
            "Falha no envio (${result.code}). Relatório copiado."
        }
        is ProblemReportSubmitResult.NetworkError -> {
            copyReportToClipboard(context, reportBody)
            "Falha de rede: ${result.message}. Relatório copiado."
        }
    }
}
