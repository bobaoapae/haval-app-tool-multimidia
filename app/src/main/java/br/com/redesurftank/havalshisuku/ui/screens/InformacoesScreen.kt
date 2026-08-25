package br.com.redesurftank.havalshisuku.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
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
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.TAG
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import br.com.redesurftank.havalshisuku.managers.StealthExitPin
import br.com.redesurftank.havalshisuku.managers.StealthExitSequence
import br.com.redesurftank.havalshisuku.managers.StealthModeManager
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.UpdateCheckResult
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.AppDimensions
import br.com.redesurftank.havalshisuku.ui.components.ImpTokens
import br.com.redesurftank.havalshisuku.ui.theme.Michroma
import br.com.redesurftank.havalshisuku.utils.ApkUpdateInstaller
import br.com.redesurftank.havalshisuku.utils.ReleaseUpdateChecker
import kotlinx.coroutines.*



@Composable
fun InformacoesTab() {
        val context = LocalContext.current
        val prefs =
                App.getDeviceProtectedContext()
                        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        var isActive by remember {
                mutableStateOf(ServiceManager.getInstance().isServicesInitialized())
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
        // Modo Concessionária: nunca ativa direto do toque — o ícone do app some depois
        // disso, então passa por uma confirmação explícita.
        // O fluxo tem 3 etapas de propósito (sugestão de um colega): ENSAIO -> PRONTO ->
        // ativa e reinicia. Fazer o dono EXECUTAR a saída antes de entrar prova que ele sabe sair
        // e que o gesto funciona neste carro — foi justamente por isso que a versão anterior
        // deixou o carro preso, com uma sequência que o head unit nunca chegava a emitir.
        var showStealthConfirm by remember { mutableStateOf(false) }
        var stealthStep by remember { mutableStateOf(0) }      // 0 = ensaio, 1 = pronto
        var stealthProgress by remember { mutableStateOf(0) }  // passos já reconhecidos
        var stealthSeq by remember { mutableStateOf(StealthExitSequence.current()) }
        var stealthSeqError by remember { mutableStateOf<String?>(null) }
        var stealthPinOn by remember { mutableStateOf(StealthExitPin.isEnabled()) }
        var stealthPinTyped by remember { mutableStateOf("") }
        var showStealthPinSetup by remember { mutableStateOf(false) }
        var stealthCustomSeq by remember { mutableStateOf(StealthExitSequence.isCustom()) }
        // Resumo mostrado UMA vez após salvar: como só o hash do PIN fica gravado, este é o único
        // momento em que dá pra ver o número. Existe pra ser fotografado.
        var stealthSummaryPin by remember { mutableStateOf<String?>(null) }
        var stealthBlocked by remember { mutableStateOf<String?>(null) }

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
                        isActive = ServiceManager.getInstance().isServicesInitialized()
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
                        // Estes tempos são gravados uma vez na inicialização e não mudam mais;
                        // para de pollar quando tudo estiver pronto (evita o loop a 10Hz eterno).
                        if (isActive && timeBoot > 0 && timeStart > 0 && timeInit > 0) break
                        delay(1000)
                }
        }

        fun startDownload(url: String, resetTargetVersion: String? = null) {
                isDownloading = true
                downloadProgress = 0f
                downloadJob =
                        scope.launch {
                                try {
                                        val file =
                                                ApkUpdateInstaller.downloadUpdateApk(
                                                        context,
                                                        url
                                                ) { progress ->
                                                        downloadProgress = progress
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

                                        if (!ApkUpdateInstaller.canRequestPackageInstalls(context)) {
                                                showPermissionDialog = true
                                                return@launch
                                        }
                                        context.startActivity(
                                                ApkUpdateInstaller.buildInstallIntent(context, file)
                                        )
                                } catch (e: CancellationException) {
                                        isDownloading = false
                                        throw e
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
                        colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
                        shape = RoundedCornerShape(20.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                Text(
                                        "Status do Sistema",
                                        fontFamily = Michroma,
                                        fontSize = 17.sp,
                                        color = Color.White
                                )

                                HorizontalDivider(color = ImpTokens.Hairline)

                                if (!bypassSelfInstallationCheck) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Text(
                                                        "Instalado corretamente:",
                                                        color = ImpTokens.TextSecondary
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
                                        Text("Estado:", color = ImpTokens.TextSecondary)
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
                                                        color = ImpTokens.TextSecondary,
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
                                                        color = ImpTokens.TextSecondary,
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
                                                        color = ImpTokens.TextSecondary,
                                                        fontSize = 14.sp
                                                )
                                                Text(
                                                        formattedTime3,
                                                        color = Color.White,
                                                        fontSize = 14.sp
                                                )
                                        }
                                }

                                HorizontalDivider(color = ImpTokens.Hairline)

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column {
                                                Text(
                                                        "Versão",
                                                        color = ImpTokens.TextSecondary,
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
                                                                val result =
                                                                        ReleaseUpdateChecker
                                                                                .getAllReleaseInfo()
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

                                HorizontalDivider(color = ImpTokens.Hairline)


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

                                HorizontalDivider(color = ImpTokens.Hairline)

                                // ===== Modo Concessionária =====
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                                "Modo Concessionária",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                                "Deixa o carro como saiu de fábrica antes de levar à revisão: o ícone do Impulse some do menu, o painel volta ao nativo, a barra inferior e as luzes saem, os patches do Android Auto/CarPlay são desmontados e as automações param. Suas configurações são salvas e devolvidas na volta.",
                                                color = ImpTokens.TextSecondary,
                                                fontSize = 14.sp
                                        )
                                        Text(
                                                "Para voltar:",
                                                color = ImpTokens.TextSecondary,
                                                fontSize = 14.sp
                                        )
                                        Text(
                                                if (stealthSeq.isEmpty()) "—"
                                                else StealthExitSequence.describe(stealthSeq),
                                                color = Color.White,
                                                fontSize = 26.sp,
                                                fontWeight = FontWeight.Bold
                                        )

                                        // Editor da sequência. As setas não podem se repetir em
                                        // seguida (o carro não emite dois acendimentos distintos),
                                        // e o validador recusa isso em vez de deixar o dono montar
                                        // uma sequência que nunca seria reconhecida.
                                        Text(
                                                "Carro em P, e a sequência toda em até ${StealthExitSequence.TOTAL_WINDOW_MS / 1000}s.",
                                                color = Color(0xFFFFB74D),
                                                fontSize = 13.sp
                                        )

                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                listOf(false to "Padrão das setas", true to "Sequência própria").forEach { (custom, label) ->
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                RadioButton(
                                                                        selected = stealthCustomSeq == custom,
                                                                        onClick = {
                                                                                stealthCustomSeq = custom
                                                                                prefs.edit {
                                                                                        putBoolean(
                                                                                                SharedPreferencesKeys.STEALTH_EXIT_SEQUENCE_CUSTOM.key,
                                                                                                custom
                                                                                        )
                                                                                }
                                                                                // Marcar "própria" começa do ZERO. Herdar a padrão fazia o
                                                                                // dono só conseguir ACRESCENTAR nela, sem nunca substituir.
                                                                                stealthSeq =
                                                                                        if (custom) emptyList()
                                                                                        else StealthExitSequence.DEFAULT
                                                                                stealthSeqError = null
                                                                        }
                                                                )
                                                                Text(label, color = Color.White, fontSize = 13.sp)
                                                        }
                                                }
                                        }

                                        if (stealthCustomSeq) {
                                        Text(
                                                "Toque para adicionar (${StealthExitSequence.MIN_STEPS} a ${StealthExitSequence.MAX_STEPS} passos):",
                                                color = ImpTokens.TextSecondary,
                                                fontSize = 13.sp
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                StealthExitSequence.Step.values().forEach { step ->
                                                        Button(
                                                                onClick = {
                                                                        val next = stealthSeq + step
                                                                        // Enquanto monta, a sequência pode estar INCOMPLETA — o mínimo
                                                                        // de passos não é motivo pra recusar a digitação. Só recusa o
                                                                        // que nunca funcionaria: passo repetido e limite de tamanho.
                                                                        // (Antes o validador completo barrava tudo, e a tela ficava
                                                                        // presa na sequência padrão.)
                                                                        val impede = when {
                                                                                next.size > StealthExitSequence.MAX_STEPS ->
                                                                                        "No máximo ${StealthExitSequence.MAX_STEPS} passos."
                                                                                step.isLightSignal && stealthSeq.lastOrNull() == step ->
                                                                                        "${step.label.substringBefore(" (")} não pode aparecer duas vezes seguidas — o carro não distingue."
                                                                                else -> null
                                                                        }
                                                                        if (impede != null) {
                                                                                stealthSeqError = impede
                                                                        } else {
                                                                                stealthSeq = next
                                                                                stealthSeqError = null
                                                                                // Só grava quando já é utilizável; incompleta fica só na tela.
                                                                                if (StealthExitSequence.validate(next) == null) {
                                                                                        prefs.edit {
                                                                                                putString(
                                                                                                        SharedPreferencesKeys.STEALTH_EXIT_SEQUENCE.key,
                                                                                                        StealthExitSequence.format(next)
                                                                                                )
                                                                                        }
                                                                                }
                                                                        }
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                        ) { Text("${step.short}  ${step.label.substringBefore(" (")}", fontSize = 11.sp) }
                                                }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = {
                                                        // Pode apagar até esvaziar: travar no mínimo deixava o dono preso
                                                        // nos primeiros passos da sequência anterior.
                                                        if (stealthSeq.isNotEmpty()) {
                                                                stealthSeq = stealthSeq.dropLast(1)
                                                                stealthSeqError = null
                                                        }
                                                }) { Text("Apagar", color = ImpTokens.TextSecondary, fontSize = 13.sp) }
                                                TextButton(onClick = {
                                                        stealthSeq = emptyList()
                                                        stealthSeqError = null
                                                }) { Text("Limpar", color = ImpTokens.TextSecondary, fontSize = 13.sp) }
                                        }
                                        if (stealthSeq.size < StealthExitSequence.MIN_STEPS) {
                                                Text(
                                                        "Faltam ${StealthExitSequence.MIN_STEPS - stealthSeq.size} passo(s) para valer. Até lá, a sequência padrão continua ativa.",
                                                        color = ImpTokens.TextSecondary,
                                                        fontSize = 12.sp
                                                )
                                        }
                                        stealthSeqError?.let {
                                                Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp)
                                        }

                                        }

                                        // PIN: a sequência prova intenção, o PIN prova identidade.
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text("Pedir PIN depois da sequência", color = Color.White, fontSize = 14.sp)
                                                        Text(
                                                                if (stealthPinOn)
                                                                        "Ligado. Se esquecer o PIN com o modo ativo, só reinstalando o app — e as configurações se perdem."
                                                                else
                                                                        "Sem PIN, quem descobrir a sequência tira o carro do modo.",
                                                                color = ImpTokens.TextSecondary,
                                                                fontSize = 12.sp
                                                        )
                                                }
                                                Switch(
                                                        checked = stealthPinOn,
                                                        onCheckedChange = { want ->
                                                                if (want) {
                                                                        stealthPinTyped = ""
                                                                        showStealthPinSetup = true
                                                                } else {
                                                                        StealthExitPin.clearPin()
                                                                        prefs.edit {
                                                                                putBoolean(SharedPreferencesKeys.ENABLE_STEALTH_EXIT_PIN.key, false)
                                                                        }
                                                                        stealthPinOn = false
                                                                }
                                                        }
                                                )
                                        }
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                        ) {
                                                Button(
                                                        onClick = {
                                                                stealthStep = 0
                                                                stealthProgress = 0
                                                                stealthBlocked = null
                                                                StealthModeManager.armConfirmation(
                                                                        onProgress = { step, done ->
                                                                                stealthProgress = step
                                                                                stealthBlocked = null
                                                                                if (done) stealthStep = 1
                                                                        },
                                                                        onBlocked = { reason -> stealthBlocked = reason }
                                                                )
                                                                showStealthConfirm = true
                                                        },
                                                        modifier = Modifier.height(48.dp),
                                                        colors =
                                                                ButtonDefaults.buttonColors(
                                                                        containerColor =
                                                                                Color(0xFFB3261E)
                                                                ),
                                                        shape =
                                                                RoundedCornerShape(
                                                                        AppDimensions
                                                                                .ButtonCornerRadius
                                                                )
                                                ) {
                                                        Icon(
                                                                Icons.Default.Build,
                                                                contentDescription =
                                                                        "Modo Concessionária",
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                                "Ativar Modo Concessionária",
                                                                color = Color.White
                                                        )
                                                }
                                        }
                                }
                        }
                }

                stealthSummaryPin?.let { pin ->
                AlertDialog(
                        onDismissRequest = { stealthSummaryPin = null },
                        containerColor = ImpTokens.Container,
                        title = { Text("Guarde isto agora", color = Color.White, fontWeight = FontWeight.SemiBold) },
                        text = {
                                Column {
                                        Text(
                                                "Tire uma foto desta tela. O PIN não pode ser mostrado de novo: o app guarda só um resumo criptográfico dele, nunca o número.",
                                                color = Color(0xFFFFB74D),
                                                fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Sequência de saída", color = ImpTokens.TextSecondary, fontSize = 12.sp)
                                        Text(
                                                StealthExitSequence.describe(stealthSeq),
                                                color = Color.White,
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                                stealthSeq.joinToString("  →  ") { it.label.substringBefore(" (") },
                                                color = ImpTokens.TextSecondary,
                                                fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text("PIN", color = ImpTokens.TextSecondary, fontSize = 12.sp)
                                        Text(
                                                pin,
                                                color = Color.White,
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                                "Lembre: o carro precisa estar em P e a sequência inteira em até ${StealthExitSequence.TOTAL_WINDOW_MS / 1000}s.",
                                                color = ImpTokens.TextSecondary,
                                                fontSize = 12.sp
                                        )
                                }
                        },
                        confirmButton = {
                                TextButton(onClick = { stealthSummaryPin = null }) { Text("Já anotei") }
                        }
                )
        }

        if (showStealthPinSetup) {
                AlertDialog(
                        onDismissRequest = {
                                showStealthPinSetup = false
                                stealthPinTyped = ""
                        },
                        containerColor = ImpTokens.Container,
                        title = { Text("Definir PIN de saída", color = Color.White, fontWeight = FontWeight.SemiBold) },
                        text = {
                                Column {
                                        Text(
                                                "De ${StealthExitPin.MIN_LENGTH} a ${StealthExitPin.MAX_LENGTH} dígitos. Ele será pedido depois da sequência, na tela da central.",
                                                color = ImpTokens.TextSecondary,
                                                fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                                "Guarde bem: esquecer o PIN com o modo ativo só se resolve reinstalando o app, e as configurações se perdem. Não existe atalho de recuperação — é isso que faz o PIN valer alguma coisa.",
                                                color = Color(0xFFFFB74D),
                                                fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                                value = stealthPinTyped,
                                                onValueChange = { v ->
                                                        stealthPinTyped = v.filter { it.isDigit() }.take(StealthExitPin.MAX_LENGTH)
                                                },
                                                label = { Text("PIN") },
                                                singleLine = true,
                                                visualTransformation = PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                                        )
                                }
                        },
                        confirmButton = {
                                TextButton(
                                        enabled = stealthPinTyped.length >= StealthExitPin.MIN_LENGTH,
                                        onClick = {
                                                val err = StealthExitPin.setPin(stealthPinTyped)
                                                if (err == null) {
                                                        prefs.edit {
                                                                putBoolean(SharedPreferencesKeys.ENABLE_STEALTH_EXIT_PIN.key, true)
                                                        }
                                                        stealthPinOn = true
                                                        showStealthPinSetup = false
                                                        stealthSummaryPin = stealthPinTyped
                                                        stealthPinTyped = ""
                                                } else {
                                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                }
                                        }
                                ) { Text("Salvar") }
                        },
                        dismissButton = {
                                TextButton(onClick = {
                                        showStealthPinSetup = false
                                        stealthPinTyped = ""
                                }) { Text("Cancelar", color = ImpTokens.TextSecondary) }
                        }
                )
        }

        if (showStealthConfirm) {
                        AlertDialog(
                                onDismissRequest = {
                                        showStealthConfirm = false
                                        StealthModeManager.cancelConfirmation()
                                },
                                containerColor = ImpTokens.Container,
                                title = {
                                        Text(
                                                if (stealthStep == 0) "Ensaie a saída" else "Tudo certo!",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                        )
                                },
                                text = {
                                        Column {
                                                if (stealthStep == 0) {
                                                        Text(
                                                                "Antes de ativar, faça agora o gesto que devolve o carro ao normal — com o carro em P:\n\n" +
                                                                        StealthExitSequence.describe(stealthSeq) +
                                                                        "\n\n(← → = setas, ① ② = botões do volante)",
                                                                color = ImpTokens.TextSecondary,
                                                                fontSize = 14.sp
                                                        )
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        stealthBlocked?.let { reason ->
                                                                Text(
                                                                        reason,
                                                                        color = Color(0xFFFF8A80),
                                                                        fontSize = 14.sp
                                                                )
                                                                Spacer(modifier = Modifier.height(8.dp))
                                                        }
                                                        Text(
                                                                "$stealthProgress de ${stealthSeq.size}",
                                                                color = if (stealthProgress > 0) Color(0xFF4CAF50) else ImpTokens.TextSecondary,
                                                                fontSize = 20.sp,
                                                                fontWeight = FontWeight.Bold
                                                        )
                                                } else {
                                                        Text(
                                                                "Você fez o gesto corretamente — é assim que vai sair do modo.\n\nAo confirmar: o ícone do Impulse e os dos apps instalados somem, tudo que o app liga é desligado, e A CENTRAL VAI REINICIAR sozinha para aplicar.\n\nSuas configurações ficam salvas e voltam inteiras na saída.",
                                                                color = ImpTokens.TextSecondary,
                                                                fontSize = 14.sp
                                                        )
                                                }
                                        }
                                },
                                confirmButton = {
                                        if (stealthStep == 1) {
                                                TextButton(
                                                        onClick = {
                                                                showStealthConfirm = false
                                                                StealthModeManager.enter(context, "UI")
                                                        }
                                                ) { Text("Confirmar e reiniciar", color = Color(0xFFE05252)) }
                                        }
                                },
                                dismissButton = {
                                        TextButton(
                                                onClick = {
                                                        showStealthConfirm = false
                                                        StealthModeManager.cancelConfirmation()
                                                }
                                        ) { Text("Cancelar", color = ImpTokens.TextSecondary) }
                                }
                        )
                }

                // Seção de Contribuição
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
                        shape = RoundedCornerShape(20.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Text(
                                        "Contribua para o Desenvolvimento",
                                        fontFamily = Michroma,
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                )

                                HorizontalDivider(color = ImpTokens.Hairline)

                                Text(
                                        "Ajude a manter este projeto ativo! Sua contribuição é muito importante para o desenvolvimento contínuo do app.",
                                        fontSize = 14.sp,
                                        color = ImpTokens.TextSecondary,
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
                                        color = ImpTokens.TextSecondary,
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
                                                                ReleaseUpdateChecker.compareVersions(
                                                                        result.latestPreview.tag
                                                                                .removePrefix("v"),
                                                                        currentClean
                                                                ) > 0
                                                val hasReleaseUpgrade =
                                                        result.latestRelease != null &&
                                                                ReleaseUpdateChecker.compareVersions(
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
                                                                ReleaseUpdateChecker.compareVersions(
                                                                        result.latestRelease.tag
                                                                                .removePrefix("v"),
                                                                        currentClean
                                                                ) > 0
                                                val hasPreviewAvailable =
                                                        showBetaUpdates &&
                                                                result.latestPreview != null &&
                                                                ReleaseUpdateChecker.compareVersions(
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
                                                HorizontalDivider(color = ImpTokens.Hairline)
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
                                                                color = ImpTokens.Attention,
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
                                                requestPermissionLauncher.launch(
                                                        ApkUpdateInstaller
                                                                .buildUnknownSourcesIntent(context)
                                                )
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
