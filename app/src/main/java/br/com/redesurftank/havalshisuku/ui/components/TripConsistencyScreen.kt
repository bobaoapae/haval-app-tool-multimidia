package br.com.redesurftank.havalshisuku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.managers.TripConsistencyManager
import br.com.redesurftank.havalshisuku.managers.TripConsistencyConfig
import br.com.redesurftank.havalshisuku.models.TripConsistencyClassification
import br.com.redesurftank.havalshisuku.models.TripConsistencyMetrics
import br.com.redesurftank.havalshisuku.models.TripConsistencyReport
import br.com.redesurftank.havalshisuku.models.TripConsistencySession
import br.com.redesurftank.havalshisuku.models.TripConsistencyStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TripConsistencyScreen(onBackToFeatures: (() -> Unit)? = null) {
    val manager = remember { TripConsistencyManager.getInstance() }
    LaunchedEffect(Unit) { manager.initialize() }

    val session = manager.currentSession
    val reportHistory = manager.reportHistory
    var selectedReport by remember { mutableStateOf<TripConsistencyReport?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var note by remember(session?.id) { mutableStateOf(session?.notes.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        TripScoreHeader(
            onShowRules = { showRules = true },
            onBackToFeatures = onBackToFeatures
        )

        when {
            showRules -> ScoreRulesView(onBack = { showRules = false })
            selectedReport != null -> TripReportView(
                report = selectedReport,
                onBack = { selectedReport = null },
                onDelete = {
                    selectedReport?.let { manager.deleteReport(it.id) }
                    selectedReport = null
                }
            )
            session == null -> IdleTripScoreView(
                reports = reportHistory,
                onStart = {
                    manager.startTrip()
                    selectedReport = null
                    showRules = false
                },
                onOpenReport = { selectedReport = it },
                onShowRules = { showRules = true }
            )
            session.status in setOf(
                TripConsistencyStatus.PAUSED_AFTER_IGNITION_OFF,
                TripConsistencyStatus.WAITING_USER_CONFIRMATION
            ) -> PausedTripView(
                session = session,
                onContinue = { manager.continueTrip() },
                onFinish = {
                    manager.addNote(note)
                    selectedReport = manager.finishTrip()
                },
                onLater = { manager.viewLater() }
            )
            else -> ActiveTripView(
                session = session,
                note = note,
                onNoteChange = {
                    note = it
                    manager.addNote(it)
                },
                onFinish = {
                    manager.addNote(note)
                    selectedReport = manager.finishTrip()
                }
            )
        }
    }
}

@Composable
private fun TripScoreHeader(
    onShowRules: () -> Unit,
    onBackToFeatures: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBackToFeatures != null) {
                    Surface(
                        modifier = Modifier
                            .width(78.dp)
                            .height(52.dp)
                            .clickable(onClick = onBackToFeatures),
                        shape = RoundedCornerShape(AppDimensions.ButtonCornerRadius),
                        color = AppColors.ButtonSecondary
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("<", color = AppColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    "Score de Consistência da Viagem",
                    color = AppColors.TextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            SecondaryButton(onClick = onShowRules, text = "Como funciona")
        }
        Text(
            "Avalia suavidade, estabilidade e eficiência usando a telemetria já monitorada pelo Impulse.",
            color = AppColors.TextSecondary,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun IdleTripScoreView(
    reports: List<TripConsistencyReport>,
    onStart: () -> Unit,
    onOpenReport: (TripConsistencyReport) -> Unit,
    onShowRules: () -> Unit
) {
    StyledCard {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Nenhuma viagem em análise", color = AppColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Inicie uma viagem para acompanhar o score em tempo real. O cluster exibirá apenas um indicador discreto enquanto a análise estiver ativa.",
                color = AppColors.TextSecondary,
                fontSize = 17.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(onClick = onStart, text = "Iniciar análise de viagem")
                SecondaryButton(onClick = onShowRules, text = "Entender score")
                if (reports.isNotEmpty()) {
                    SecondaryButton(onClick = { onOpenReport(reports.first()) }, text = "Ver último relatório")
                }
            }
        }
    }

    TripHistoryView(reports = reports, onOpenReport = onOpenReport)
}

@Composable
private fun ActiveTripView(
    session: TripConsistencySession,
    note: String,
    onNoteChange: (String) -> Unit,
    onFinish: () -> Unit
) {
    val scoreReady = session.metrics.samplesCount >= TripConsistencyConfig.MIN_VALID_SAMPLES_FOR_SCORE
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        StyledCard(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                TripScoreGauge(
                    score = session.currentScore.takeIf { scoreReady },
                    classification = session.currentClassification.takeIf { scoreReady }
                )
                ClassificationChips(session.currentClassification, enabled = scoreReady)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TripMetricPill(Icons.Default.Route, "Distância", formatDistance(session.distanceKm))
                    TripMetricPill(Icons.Default.Timeline, "Duração", formatDuration(session.elapsedSeconds))
                }
                if (!scoreReady || session.telemetryWarning) {
                    Text(
                        "Aguardando telemetria para iniciar o score.",
                        color = Color(0xFFFFC857),
                        fontSize = 14.sp
                    )
                }
            }
        }

        StyledCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Prévia em tempo real", color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                MetricsGrid(session.metrics, scoreReady = scoreReady)
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("Observações da viagem") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                PrimaryButton(onClick = onFinish, text = "Encerrar viagem e gerar relatório", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PausedTripView(
    session: TripConsistencySession,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    onLater: () -> Unit
) {
    val scoreReady = session.metrics.samplesCount >= TripConsistencyConfig.MIN_VALID_SAMPLES_FOR_SCORE
    StyledCard {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = Color(0xFFFFC857), modifier = Modifier.size(42.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Você ainda está em viagem?", color = AppColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("O veículo foi desligado durante uma análise ativa.", color = AppColors.TextSecondary, fontSize = 17.sp)
                }
            }
            TripScoreGauge(
                score = session.currentScore.takeIf { scoreReady },
                classification = session.currentClassification.takeIf { scoreReady },
                compact = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(onClick = onContinue, text = "Continuar viagem")
                SecondaryButton(onClick = onFinish, text = "Finalizar e gerar relatório")
                SecondaryButton(onClick = onLater, text = "Ver depois")
            }
        }
    }
}

@Composable
private fun TripReportView(
    report: TripConsistencyReport?,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    if (report == null) return

    StyledCard {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Relatório da viagem", color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${formatInstant(report.startedAt)} até ${formatInstant(report.endedAt)}", color = AppColors.TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(onClick = onBack, text = "Voltar")
                    SecondaryButton(onClick = onDelete, text = "Apagar relatório")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TripScoreGauge(report.score, report.classification, compact = true)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(report.classificationLabel, color = Color(0xFF00D8FF), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(report.summaryText, color = AppColors.TextSecondary, fontSize = 17.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TripMetricPill(Icons.Default.Route, "Distância", formatDistance(report.distanceKm))
                        TripMetricPill(Icons.Default.Timeline, "Duração", formatDuration(report.durationSeconds))
                        TripMetricPill(Icons.Default.Flag, "Eventos", report.events.size.toString())
                    }
                    MetricsGrid(report.metrics, scoreReady = true)
                }
            }
        }
    }
}

@Composable
private fun TripHistoryView(
    reports: List<TripConsistencyReport>,
    onOpenReport: (TripConsistencyReport) -> Unit
) {
    StyledCard {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF00D8FF), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Histórico de viagens", color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Últimas ${reports.size}/10 viagens finalizadas.", color = AppColors.TextSecondary, fontSize = 14.sp)
                }
            }

            if (reports.isEmpty()) {
                Text("Nenhuma viagem finalizada ainda.", color = AppColors.TextSecondary, fontSize = 16.sp)
            } else {
                reports.forEach { report ->
                    TripHistoryRow(report = report, onClick = { onOpenReport(report) })
                }
            }
        }
    }
}

@Composable
private fun TripHistoryRow(report: TripConsistencyReport, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF111B27),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TripScoreMiniBadge(report.score)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(report.classificationLabel, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("${formatInstant(report.startedAt)} - ${formatDistance(report.distanceKm)} - ${formatDuration(report.durationSeconds)}", color = AppColors.TextSecondary, fontSize = 14.sp)
                }
            }
            Text("Abrir", color = Color(0xFF00D8FF), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TripScoreMiniBadge(score: Int) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(Color(0xFF0C3044), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(score.toString(), color = Color(0xFF00D8FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScoreRulesView(onBack: () -> Unit) {
    StyledCard {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00D8FF), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Como funciona o score", color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Regras implementadas usando dados reais já lidos pelo Impulse.", color = AppColors.TextSecondary, fontSize = 16.sp)
                    }
                }
                SecondaryButton(onClick = onBack, text = "Voltar")
            }

            ScoreRuleSection(
                title = "Dados usados",
                lines = listOf(
                    "Velocidade, hodômetro e estado do veículo.",
                    "Consumo instantâneo de combustível e energia, quando disponíveis.",
                    "Nível/corrente de regeneração para detectar eventos relevantes.",
                    "Sem GPS: o score não usa rota, localização ou mapas."
                )
            )
            ScoreRuleSection(
                title = "Pesos do score",
                lines = listOf(
                    "Variação de velocidade: 25%.",
                    "Aceleração controlada: 20%.",
                    "Frenagem/regeneração: 20%.",
                    "Consumo: 15%.",
                    "Estabilidade: 15%.",
                    "Contexto de trânsito: 5%."
                )
            )
            ScoreRuleSection(
                title = "Classificações",
                lines = listOf(
                    "Viagem suave: score alto, poucas variações bruscas e condução previsível.",
                    "Viagem esportiva: várias acelerações/frenagens fortes ou muitas variações de velocidade.",
                    "Trânsito pesado: baixa velocidade média, velocidade máxima baixa e padrão de anda-e-para."
                )
            )
            ScoreRuleSection(
                title = "Persistência",
                lines = listOf(
                    "Viagem ativa fica salva se o veículo desligar.",
                    "Se religar em até 12 horas, a viagem pode continuar sem perder dados.",
                    "O histórico mantém as últimas 10 viagens finalizadas."
                )
            )
        }
    }
}

@Composable
private fun ScoreRuleSection(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFF00D8FF), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        lines.forEach { line ->
            Text("- $line", color = AppColors.TextSecondary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun TripScoreGauge(
    score: Int?,
    classification: TripConsistencyClassification?,
    compact: Boolean = false
) {
    val size = if (compact) 190.dp else 260.dp
    val stroke = if (compact) 12.dp else 18.dp
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val arcSize = Size(size.toPx() - strokePx, size.toPx() - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(
                color = Color(0xFF263244),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFF00D8FF), Color(0xFF4A9EFF), Color(0xFF00D8FF))),
                startAngle = 135f,
                sweepAngle = 270f * ((score ?: 0).coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: "--", color = AppColors.TextPrimary, fontSize = if (compact) 54.sp else 82.sp, fontWeight = FontWeight.Bold)
            Text("/100", color = Color(0xFF4A9EFF), fontSize = if (compact) 18.sp else 24.sp)
            Text(
                formatGaugeClassificationLabel(classification),
                color = Color(0xFF00D8FF),
                fontSize = if (compact) 14.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = if (compact) 16.sp else 20.sp
            )
        }
    }
}

private fun formatGaugeClassificationLabel(classification: TripConsistencyClassification?): String =
    when (classification) {
        TripConsistencyClassification.SMOOTH -> "VIAGEM\nSUAVE"
        TripConsistencyClassification.SPORTY -> "VIAGEM\nESPORTIVA"
        TripConsistencyClassification.HEAVY_TRAFFIC -> classification.label.uppercase(Locale.getDefault())
        null -> "AGUARDANDO"
    }

@Composable
private fun ClassificationChips(current: TripConsistencyClassification, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TripConsistencyClassification.values().forEach { item ->
            val selected = enabled && item == current
            AssistChip(
                onClick = {},
                label = { Text(item.label) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) Color(0xFF0C3044) else AppColors.SurfaceVariant,
                    labelColor = if (selected) Color(0xFF00D8FF) else AppColors.TextSecondary,
                    leadingIconContentColor = Color(0xFF00D8FF)
                )
            )
        }
    }
}

@Composable
private fun MetricsGrid(metrics: TripConsistencyMetrics, scoreReady: Boolean, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)) {
        MetricProgress("Variação de velocidade", metrics.speedVariationScore.takeIf { scoreReady })
        MetricProgress("Aceleração controlada", metrics.accelerationScore.takeIf { scoreReady })
        MetricProgress("Frenagem/regeneração", metrics.brakeRegenScore.takeIf { scoreReady })
        MetricProgress("Consumo", (metrics.consumptionScore ?: metrics.energyEfficiencyScore).takeIf { scoreReady })
        MetricProgress("Estabilidade", metrics.stabilityScore.takeIf { scoreReady })
        Divider(color = AppColors.BorderColor)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TripMetricPill(Icons.Default.Speed, "Média", formatKmh(metrics.averageSpeedKmh), compact = compact)
            TripMetricPill(Icons.Default.Bolt, "Acel. fortes", metrics.strongAccelerationCount.toString(), compact = compact)
            TripMetricPill(Icons.Default.LocalGasStation, "Paradas", metrics.stopCount.toString(), compact = compact)
        }
    }
}

@Composable
private fun MetricProgress(label: String, score: Int?) {
    val progress = (score ?: 0).coerceIn(0, 100) / 100f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = AppColors.TextSecondary, fontSize = 14.sp)
            Text(score?.toString() ?: "--", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0xFF243044), RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .background(Color(0xFF00D8FF), RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
private fun TripMetricPill(icon: ImageVector, label: String, value: String, compact: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF111B27),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 7.dp else 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF00D8FF), modifier = Modifier.size(if (compact) 19.dp else 22.dp))
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            Column {
                Text(label, color = AppColors.TextSecondary, fontSize = if (compact) 11.sp else 12.sp)
                Text(value, color = AppColors.TextPrimary, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatDistance(value: Double?): String = value?.let { String.format(Locale.US, "%.1f km", it) } ?: "-- km"

private fun formatKmh(value: Double?): String = value?.let { String.format(Locale.US, "%.0f km/h", it) } ?: "-- km/h"

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

private fun formatInstant(value: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrDefault("--")
