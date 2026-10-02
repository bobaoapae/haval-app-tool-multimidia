package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import java.io.File

/**
 * Abre, no boot, o app escolhido para a TELA PRINCIPAL (display 0).
 *
 * Qualquer app pode ser escolhido — o pacote vem de [SharedPreferencesKeys.STARTUP_APP_MAIN_DISPLAY],
 * definido na aba "Instalar Apps". Vazio = não abrir nada, que é o padrão.
 *
 * As telas secundárias (1 e 3) NÃO passam por aqui: lá o app é lançado direto por
 * [DisplayAppLauncher.launchApp] a partir da config guardada, porque não há launcher de fábrica
 * disputando a tela. O D0 tem, e é disso que vem toda a complicação abaixo.
 *
 * Esta ROM não deixa trocar o app de HOME: `cmd package set-home-activity` é no-op, e tanto
 * `pm disable-user` quanto `IPackageManager.setComponentEnabledSetting` respondem "ok" e deixam a
 * home activity do launcher de fábrica ativa. Os três foram medidos no carro. Então aqui a gente
 * simplesmente abre o app por cima, o que não depende de cooperação do package manager.
 *
 * O tempo é o problema todo. `BOOT_COMPLETED` chega antes de o launcher de fábrica assentar, então
 * um único `startActivity` em t=0 é rotineiramente ultrapassado. Daí [ATTEMPT_DELAYS_MS]: dispara
 * de imediato para o melhor caso e reconfere nos primeiros segundos.
 *
 * A escada para quando:
 *
 *  * o app escolhido está no topo — pronto; ou
 *  * o topo MUDOU em relação ao que já estava lá quando a escada começou — o motorista abriu outra
 *    coisa no meio do boot, e roubar o foco de volta seria hostil.
 *
 * A regra de desistir olha para a MUDANÇA, não para uma lista fixa. A primeira versão presumia que
 * só o launcher de fábrica poderia estar no topo no boot; medido no carro, quem sobe é o
 * `com.beantechs.mediacenter` (a mídia retoma sozinha), então a escada desistia na tentativa 0 e
 * nunca chamava startActivity.
 *
 * Um token de boot (`/proc/sys/kernel/random/boot_id`, lido direto e não via Shizuku, porque isto
 * roda antes de o Shizuku necessariamente estar de pé) faz tudo rodar uma vez por boot, então um
 * restart do serviço no meio da viagem nunca puxa o motorista de volta para o app.
 */
object StartupAppManager {
    private const val TAG = "STARTUP_APP"

    private const val STOCK_LAUNCHER_PACKAGE = "com.beantechs.launcher"

    private const val PREF_BOOT_TOKEN = "viewerAutostartBootToken"

    /** Chave booleana antiga, de quando isto só sabia abrir o viewer. Só migração lê isto. */
    private const val LEGACY_AUTO_START_VIEWER = "autoStartViewerOnBoot"
    private const val LEGACY_VIEWER_PACKAGE = "com.havalh6.viewer"

    const val MAIN_DISPLAY_ID = 0

    /** Primeiro tiro imediato; os outros cobrem a janela em que o launcher ainda está assentando. */
    private val ATTEMPT_DELAYS_MS = longArrayOf(0, 1_500, 4_000, 8_000)

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    /** O que estava no topo quando a escada começou. Qualquer outra coisa depois = o motorista agiu. */
    private var baselineTop: String? = null
    private var baselineCaptured = false

    private fun prefs() =
        App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    /**
     * Pacote escolhido para abrir no D0, ou vazio.
     *
     * Migra a chave booleana antiga na primeira leitura: quem já tinha o viewer marcado continua
     * com ele, sem precisar reconfigurar.
     */
    fun mainDisplayPackage(): String {
        val p = prefs()
        val key = SharedPreferencesKeys.STARTUP_APP_MAIN_DISPLAY.key
        if (!p.contains(key) && p.getBoolean(LEGACY_AUTO_START_VIEWER, false)) {
            p.edit().putString(key, LEGACY_VIEWER_PACKAGE).remove(LEGACY_AUTO_START_VIEWER).apply()
            Log.w(TAG, "Migrado $LEGACY_AUTO_START_VIEWER -> $key=$LEGACY_VIEWER_PACKAGE")
            return LEGACY_VIEWER_PACKAGE
        }
        return p.getString(key, "").orEmpty()
    }

    /** Define (ou limpa, com vazio) o app que abre no D0. */
    fun setMainDisplayPackage(packageName: String) {
        prefs().edit()
            .putString(SharedPreferencesKeys.STARTUP_APP_MAIN_DISPLAY.key, packageName)
            .remove(LEGACY_AUTO_START_VIEWER)
            .apply()
        Log.w(TAG, "App do D0 ao ligar: ${packageName.ifEmpty { "(nenhum)" }}")
    }

    /**
     * Lido direto do procfs — [DisplayAppLauncher.currentBootToken] passa por um shell do Shizuku,
     * e isto roda no `BOOT_COMPLETED`, quando o Shizuku pode ainda não ter feito bind.
     */
    private fun currentBootToken(): String =
        runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: "unknown"

    /**
     * Ponto de entrada. Pode ser chamado mais de uma vez por boot e de mais de um lugar — o token
     * de boot faz toda chamada depois da primeira virar no-op.
     */
    fun onBootCompleted(reason: String) {
        val pkg = mainDisplayPackage()
        if (pkg.isEmpty()) {
            Log.d(TAG, "[$reason] Nenhum app configurado para abrir no D0")
            return
        }
        if (running) return

        val token = currentBootToken()
        if (prefs().getString(PREF_BOOT_TOKEN, "") == token) {
            Log.d(TAG, "[$reason] Já rodou neste boot")
            return
        }
        prefs().edit().putString(PREF_BOOT_TOKEN, token).apply()

        if (!canLaunch(pkg)) {
            Log.e(TAG, "[$reason] $pkg está configurado mas não pode ser aberto (desinstalado?)")
            return
        }

        running = true
        baselineTop = null
        baselineCaptured = false
        ClusterPersistentEventLogger.log(
            "startup_app_started",
            mapOf("reason" to reason, "package" to pkg)
        )
        ATTEMPT_DELAYS_MS.forEachIndexed { index, delay ->
            handler.postDelayed({ attempt(index, pkg) }, delay)
        }
    }

    /** Existe uma activity de launcher para este pacote? Cobre desinstalado e desativado. */
    fun canLaunch(packageName: String): Boolean =
        packageName.isNotEmpty() &&
            runCatching {
                App.getContext().packageManager.getLaunchIntentForPackage(packageName) != null
            }.getOrDefault(false)

    private fun attempt(index: Int, pkg: String) {
        if (!running) return

        // getTopPackageOnDisplay passa pelo Shizuku e devolve null quando não consegue saber; um
        // null não pode ser lido como "o motorista abriu outra coisa", então só um pacote
        // estranho e conhecido interrompe a escada.
        val top = runCatching { DisplayAppLauncher.getTopPackageOnDisplay(MAIN_DISPLAY_ID) }
            .getOrNull()

        // A primeira leitura é o estado do boot, seja ele qual for — o launcher de fábrica em
        // alguns boots, a central de mídia em outros. Só uma mudança em relação a isso conta.
        if (!baselineCaptured && top != null) {
            baselineTop = top
            baselineCaptured = true
            Log.w(TAG, "Topo no boot: '$top'")
        }

        when {
            top == pkg -> {
                Log.w(TAG, "$pkg está no topo na tentativa $index; pronto")
                ClusterPersistentEventLogger.log(
                    "startup_app_settled",
                    mapOf("attempt" to index, "package" to pkg)
                )
                running = false
                return
            }
            top != null && top != baselineTop && top != STOCK_LAUNCHER_PACKAGE -> {
                Log.w(TAG, "'$top' substituiu '$baselineTop'; deixando como está")
                ClusterPersistentEventLogger.log(
                    "startup_app_yielded",
                    mapOf("attempt" to index, "top" to top, "baseline" to baselineTop.orEmpty())
                )
                running = false
                return
            }
        }

        val started = start(pkg)
        Log.w(TAG, "Tentativa $index de abrir $pkg (top=$top started=$started)")
        if (index == ATTEMPT_DELAYS_MS.lastIndex) running = false
    }

    private fun start(packageName: String): Boolean =
        runCatching {
            val context = App.getContext()
            val intent =
                context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@runCatching false
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            context.startActivity(intent)
            true
        }
            .onFailure { Log.e(TAG, "startActivity de $packageName falhou", it) }
            .getOrDefault(false)

    /** "Abrir agora", para a interface — sem token de boot e sem repetições. */
    fun launchNow(): Boolean {
        running = false
        handler.removeCallbacksAndMessages(null)
        val pkg = mainDisplayPackage()
        return if (pkg.isEmpty()) false else start(pkg)
    }
}
