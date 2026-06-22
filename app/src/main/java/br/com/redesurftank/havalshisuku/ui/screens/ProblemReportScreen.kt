package br.com.redesurftank.havalshisuku.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import br.com.redesurftank.havalshisuku.diagnostics.ClusterLogCaptureStatus
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.diagnostics.ProblemReportBuilder
import br.com.redesurftank.havalshisuku.diagnostics.ProblemReportInput
import br.com.redesurftank.havalshisuku.diagnostics.ProblemReportSubmitResult
import br.com.redesurftank.havalshisuku.diagnostics.ProblemReportSubmitter
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProblemReportTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var description by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var logCaptureStatus by remember {
        mutableStateOf(
                ClusterPersistentEventLogger.getTodayLogCaptureStatus(context.applicationContext)
        )
    }

    val canCreateIssue = description.trim().length >= 10

    fun refreshLogCaptureStatus() {
        scope.launch {
            val appContext = context.applicationContext
            logCaptureStatus =
                    withContext(Dispatchers.IO) {
                        ClusterPersistentEventLogger.getTodayLogCaptureStatus(appContext)
                    }
        }
    }

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
                shape = RoundedCornerShape(AppDimensions.CardCornerRadius)
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
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                text = "Inclua no relato a data, hora e minutos aproximados do incidente.",
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp
                        )
                    }
                }

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
                            val input = buildProblemReportInput(description)
                            isCreating = true
                            statusMessage = null
                            scope.launch {
                                try {
                                    val appContext = context.applicationContext
                                    val report =
                                            withContext(Dispatchers.IO) {
                                                ClusterPersistentEventLogger.log(
                                                        "problem_report_created",
                                                        mapOf(
                                                                "descriptionChars" to
                                                                        description.trim().length
                                                        )
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
                        },
                        enabled = canCreateIssue && !isCreating,
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
                    Text("Enviar relatório", color = Color.White)
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
                shape = RoundedCornerShape(AppDimensions.CardCornerRadius)
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                        onClick = {
                            val input = buildProblemReportInput(description)
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
                        },
                        enabled = canCreateIssue && !isCreating,
                        shape = RoundedCornerShape(AppDimensions.ButtonCornerRadius),
                        modifier = Modifier.height(48.dp)
                ) {
                    Text("Copiar relatório", color = AppColors.TextPrimary)
                }
            }
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
                        enabled = status.debugBuild,
                        onCheckedChange = onEnabledChange,
                        colors =
                                SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AppColors.Primary,
                                        uncheckedThumbColor = AppColors.TextSecondary,
                                        uncheckedTrackColor = AppColors.SurfaceVariant
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
private fun LogStatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = AppColors.TextSecondary, fontSize = 13.sp)
        Text(text = value, color = AppColors.TextPrimary, fontSize = 13.sp)
    }
}

private fun logCaptureStatusText(status: ClusterLogCaptureStatus): String {
    if (!status.debugBuild) return "Indisponível nesta build."
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

private fun buildProblemReportInput(description: String): ProblemReportInput {
    return ProblemReportInput(
            description = description,
            carPlayConnected = false,
            androidAutoConnected = false,
            mirroredOnD3 = false,
            notMirroredOnD3 = false,
            mapReturnedToNormal = false,
            acReturnedToMainMenu = false
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
            if (result.githubIssueUrl.isNullOrBlank()) {
                "Relatório enviado. ID: ${result.reportId}"
            } else {
                "Relatório enviado e issue criada: ${result.githubIssueUrl}"
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
