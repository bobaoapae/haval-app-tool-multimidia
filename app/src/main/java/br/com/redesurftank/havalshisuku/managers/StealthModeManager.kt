package br.com.redesurftank.havalshisuku.managers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.SplashActivity
import br.com.redesurftank.havalshisuku.ambientlight.AmbientLightService
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.services.BottomBarService
import br.com.redesurftank.havalshisuku.services.ForegroundService
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * MODO CONCESSIONÁRIA — devolve o carro ao comportamento de fábrica antes de levar à revisão.
 *
 * ARQUITETURA: DESLIGAR AS PREFERÊNCIAS DE VERDADE (não master switch).
 *
 * A primeira versão era um master switch: uma flag consultada em N pontos de ação. Falhou no
 * carro — o painel continuou com o tema porque `ProjectorManager` criava as Presentations do
 * cluster incondicionalmente (as prefs só decidiam o que era PINTADO dentro da janela). A lição:
 * gate espalhado obriga a caçar TODOS os caminhos, e sempre escapa um.
 *
 * Isso foi consertado na ORIGEM, não aqui: `ProjectorManager.initialize()` agora consulta
 * ENABLE_VIRTUAL_CLUSTER / ENABLE_INSTRUMENT_PROJECTOR antes de criar cada projector, e derruba o
 * que estiver vivo com a pref desligada. Ou seja: desligar o Virtual Cluster passou a devolver o
 * painel ao nativo para QUALQUER usuário, e este modo herda o comportamento de graça — sem gate.
 *
 * Agora o modo:
 *  1. grava um RETRATO completo de `haval_prefs` (JSON, com os tipos) em
 *     [SharedPreferencesKeys.STEALTH_PREFS_SNAPSHOT], com `commit()`, ANTES de mexer em nada;
 *  2. desliga as preferências que ativam funcionalidades (ver [featureKeysToDisable]);
 *  3. reaplica o estado desligado pelos MESMOS pontos que o boot usa.
 *
 * Com as preferências realmente desligadas, o app roda pelo caminho já testado de quem nunca ligou
 * nenhuma dessas features — em vez de um caminho novo cheio de gates. Sair = restaurar o retrato
 * (tipos originais, removendo o que não existia) e reaplicar.
 *
 * VOLTA: o ícone do launcher some, então não há como abrir o app. As portas de volta são:
 *  1. 3 toques CURTOS no botão 1 do volante em até 8s (ServiceManager.dispatchKeyEvent);
 *  2. o broadcast do StealthExitReceiver, por telnet/adb — o comando exato está no KDoc daquele
 *     receiver (precisa de `-n <componente>`; broadcast implícito não chega no Android 8+).
 *
 * Toda etapa é isolada em try/catch: uma etapa que falhe NUNCA pode impedir as outras — sobretudo
 * no [exit], que é o único caminho de recuperação.
 */
object StealthModeManager {
    private const val TAG = "StealthModeManager"
    private const val PREFS_NAME = "haval_prefs"

    /** Onde guardamos a lista do que escondemos, para saber o que devolver na volta. */
    private const val HIDDEN_PACKAGES_KEY = "stealthHiddenPackages"

    /**
     * Chaves DO PRÓPRIO MODO. Ficam fora do retrato e fora da restauração: são o estado que
     * controla a volta, não configuração do usuário. Se entrassem no retrato, restaurar apagaria
     * a lista de apps escondidos antes de devolvê-los.
     */
    private val INTERNAL_KEYS =
        setOf(
            SharedPreferencesKeys.STEALTH_MODE_ACTIVE.key,
            SharedPreferencesKeys.STEALTH_PREFS_SNAPSHOT.key,
            HIDDEN_PACKAGES_KEY
        )

    /**
     * Exceções ao desligamento automático: preferências cujo nome começa com `ENABLE_` mas que NÃO
     * são feature visível — são a infraestrutura privilegiada que o app usa para funcionar (e para
     * reverter este modo). Desligá-las deixaria o app sem braços.
     */
    private val NEVER_DISABLE =
        setOf(
            // Hooks do Frida: é por eles que o app instala/patcheia coisas no system_server.
            // Não aparecem na tela e desligar a pref não desfaz o que já está injetado — só tiraria
            // capacidade do app, sem ganho nenhum de disfarce.
            SharedPreferencesKeys.ENABLE_FRIDA_HOOKS,
            SharedPreferencesKeys.ENABLE_FRIDA_HOOK_SYSTEM_SERVER,
            // O PIN de saída. A varredura automática pega tudo que começa com ENABLE_, e isso
            // desligava justamente a trava da porta de saída ao entrar no modo: na hora de sair,
            // isEnabled() era false e a sequência saía direto, sem pedir nada. Não é disfarce
            // nenhum desligá-lo — ele só existe DENTRO do modo.
            SharedPreferencesKeys.ENABLE_STEALTH_EXIT_PIN
        )

    /**
     * Preferências de ATIVAÇÃO que não começam com `ENABLE_`. Critério da lista: entra tudo que,
     * sozinho, LIGA um comportamento — automação do carro, algo desenhado na tela, patch montado,
     * app do sistema desativado, controle de rede. Ficam de fora: valores de ajuste (horários,
     * limites, cores, offsets), estado interno de bookkeeping (`*_DISABLED_BY_APP`, contadores de
     * tráfego, últimas telas) e preferências de UI do próprio app (canal beta, uso avançado) —
     * nenhum deles faz nada por conta própria, e o retrato devolve todos intactos na saída.
     */
    private val EXTRA_FEATURE_KEYS =
        listOf(
            // --- Automações do carro (janelas, teto, cortina, volume) ---
            SharedPreferencesKeys.CLOSE_WINDOW_ON_POWER_OFF,
            SharedPreferencesKeys.CLOSE_SUNROOF_ON_POWER_OFF,
            SharedPreferencesKeys.CLOSE_WINDOW_ON_LOCK,
            SharedPreferencesKeys.CLOSE_SUNROOF_ON_LOCK,
            SharedPreferencesKeys.CLOSE_WINDOWS_ON_SPEED,
            SharedPreferencesKeys.CLOSE_SUNROOF_ON_SPEED,
            SharedPreferencesKeys.CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF,
            SharedPreferencesKeys.SET_STARTUP_VOLUME,
            // --- Comportamentos do carro que o app SUPRIME (false = volta ao de fábrica) ---
            SharedPreferencesKeys.DISABLE_MONITORING,
            SharedPreferencesKeys.DISABLE_AVAS,
            SharedPreferencesKeys.DISABLE_AVM_CAR_STOPPED,
            // --- Debloat: false faz o boot reinstalar (pm install-existing) os apps do OEM ---
            SharedPreferencesKeys.DISABLE_NATIVE_NAVIGATION,
            SharedPreferencesKeys.DISABLE_NATIVE_VOICE,
            SharedPreferencesKeys.DISABLE_NATIVE_WEATHER,
            // --- Bluetooth / hotspot ao desligar ou trancar o carro ---
            SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF,
            SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF,
            SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_LOCK,
            SharedPreferencesKeys.DISABLE_HOTSPOT_ON_LOCK,
            // --- Barra inferior e painel lateral (desenhados por cima da UI do OEM) ---
            SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR,
            SharedPreferencesKeys.BOTTOM_BAR_AUTO_HIDE,
            SharedPreferencesKeys.HIDE_LEFT_NAV_PANE,
            // --- Cluster / projeção ---
            SharedPreferencesKeys.CLUSTER_PROJECTION_OPENS_DASHBOARD,
            SharedPreferencesKeys.CLUSTER_HIDE_SPEEDOMETER_ON_MAPS,
            SharedPreferencesKeys.CLUSTER_V2_TRIP_INFO,
            SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_ACTIVE,
            SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_SCORE,
            SharedPreferencesKeys.AUTO_MOVE_PROJECTION_TO_CLUSTER,
            // --- Patches montados no Android Auto / CarPlay (CarPlay tem default TRUE) ---
            SharedPreferencesKeys.AA_PATCH_AUTO_MOUNT,
            SharedPreferencesKeys.CARPLAY_PATCH_AUTO_MOUNT,
            // --- Luz ambiente (BLE/DMX) ---
            SharedPreferencesKeys.AMBIENT_LIGHT_BLE_ENABLED,
            SharedPreferencesKeys.AMBIENT_LIGHT_SYNC_DRIVE_MODE,
            SharedPreferencesKeys.AMBIENT_LIGHT_ANIMATIONS_ENABLED,
            SharedPreferencesKeys.AMBIENT_LIGHT_MUSIC_ANIMATION_ENABLED,
            SharedPreferencesKeys.AMBIENT_LIGHT_AUTO_RECONNECT,
            // --- Conectividade (roteamento, prioridade de WiFi, corte de 4G, telemetria OEM) ---
            SharedPreferencesKeys.MOBILE_DATA_CONTROL_ENABLED,
            SharedPreferencesKeys.BLOCK_CAR_MOBILE_DATA,
            SharedPreferencesKeys.MOBILE_DATA_AUTOBLOCK,
            SharedPreferencesKeys.MOBILE_DATA_BLOCK_ON_WIFI,
            SharedPreferencesKeys.MOBILE_DATA_BLOCK_ON_PROJECTION,
            SharedPreferencesKeys.BLOCK_DATATRACK_TELEMETRY,
            // --- Aviso de voz do cinto: o duck da música é ativação própria ---
        )

    /**
     * A única preferência de ativação que não é booleana: o pacote que o cluster abre sozinho no
     * startup (InstrumentProjector2.triggerAutoLaunch). Vazio = não abre nada. Sem isto, um app
     * configurado subiria no painel depois de um ciclo de ignição — o pior tipo de denúncia.
     */
    private val CLEAR_ON_ENTER = listOf(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE)

    /**
     * A ÚNICA exclusão da varredura de apps de terceiros: o próprio Impulse.
     *
     * Não existem mais duas listas (uma de "não mexer" e outra de "só o ícone"). Depois do teste
     * no carro o dono decidiu que TODO app de terceiro é tratado igual — só o ícone some, o app
     * continua rodando (ver [hideThirdPartyApps]). Com isso, o Shizuku e os apps de terceiros deixaram de ser
     * casos especiais: nada é suspenso, então nada tranca a porta por dentro nem derruba sessão
     * de ninguém.
     *
     * O Impulse fica de fora porque o ícone dele é tratado à parte, por
     * [setLauncherIconEnabled] (`setComponentEnabledSetting` no próprio processo, com
     * DONT_KILL_APP) — deixá-lo também na varredura genérica só duplicaria o comando.
     */
    private val NEVER_TOUCH = setOf("br.com.redesurftank.havalshisuku")

    // ===== Ensaio da saída, ANTES de ativar =====
    // Sugestão de um colega, e a proteção que faltava: em vez de explicar o gesto num texto
    // que ninguém lê, o dono EXECUTA a saída antes de entrar. Assim ele prova que sabe sair e, de
    // quebra, prova que o gesto funciona NESTE carro — foi exatamente aqui que a versão anterior
    // falhou (chave de seta não suportada) e o carro ficou preso com o modo ligado.
    @Volatile private var awaitingConfirmation = false
    @Volatile private var confirmationProgress = 0
    @Volatile private var confirmationListener: ((Int, Boolean) -> Unit)? = null

    @JvmStatic
    fun isAwaitingConfirmation(): Boolean = awaitingConfirmation

    /** Arma o ensaio. [onProgress] recebe (passos de 0 a 4, concluído). */
    @JvmStatic
    @Volatile private var confirmationBlockedListener: ((String) -> Unit)? = null

    /** Avisa a tela do ensaio que uma condição não foi atendida (ex.: carro fora de P). */
    @JvmStatic
    fun onConfirmationBlocked(reason: String) {
        if (!awaitingConfirmation) return
        val listener = confirmationBlockedListener ?: return
        Handler(Looper.getMainLooper()).post { listener.invoke(reason) }
    }

    @JvmStatic
    @JvmOverloads
    fun armConfirmation(
            onProgress: (Int, Boolean) -> Unit,
            onBlocked: ((String) -> Unit)? = null
    ) {
        confirmationBlockedListener = onBlocked
        awaitingConfirmation = true
        confirmationProgress = 0
        confirmationListener = onProgress
        StealthExitSequence.reset()
        // O ensaio acontece ANTES de ativar, e é justamente na ativação que os botões do volante
        // passam a ser capturados. Sem isto, uma sequência com ① ou ② nunca completaria o ensaio:
        // o toque continuaria indo pra função nativa e o dono ficaria preso na tela de ensaio,
        // sem conseguir ativar — e sem entender por quê.
        try {
            ServiceManager.getInstance().ensureSteeringWheelButtonIntegration()
        } catch (t: Throwable) {
            Log.w(TAG, "não deu pra preparar os botões do volante pro ensaio", t)
        }
        onProgress(0, false)
    }

    @JvmStatic
    fun cancelConfirmation() {
        awaitingConfirmation = false
        confirmationProgress = 0
        confirmationListener = null
        confirmationBlockedListener = null
        StealthExitSequence.reset()
        // Desistiu de ativar: devolve os botões à configuração normal do dono.
        try {
            ServiceManager.getInstance().ensureSteeringWheelButtonIntegration()
        } catch (t: Throwable) {
            Log.w(TAG, "não deu pra restaurar os botões do volante", t)
        }
    }

    /** Chamado pelo detector de setas a cada troca de lado válida durante o ensaio. */
    @JvmStatic
    fun onConfirmationStep(step: Int, done: Boolean) {
        confirmationProgress = step
        val listener = confirmationListener
        if (done) {
            awaitingConfirmation = false
            confirmationListener = null
            confirmationBlockedListener = null
        }
        Handler(Looper.getMainLooper()).post { listener?.invoke(step, done) }
    }

    private fun prefs() =
        App.getDeviceProtectedContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Consultado pelos dois gates que sobraram (o central do OnDataChanged e a suspensão das ações
     * do volante), pela detecção da sequência de saída e pelo ensureSteeringWheelButtonIntegration
     * (que precisa manter o botão 1 nosso mesmo com as prefs desligadas). Lê a pref direto —
     * device-protected storage, igual aos outros managers — pra funcionar antes do unlock.
     */
    @JvmStatic
    fun isActive(): Boolean =
        try {
            prefs().getBoolean(SharedPreferencesKeys.STEALTH_MODE_ACTIVE.key, false)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha lendo a flag do Modo Concessionária; assumindo desligado", t)
            false
        }

    /** Grava a flag com commit(): é o estado que define se o app tem volta, não pode ficar em cache. */
    private fun setActive(active: Boolean) {
        prefs().edit().putBoolean(SharedPreferencesKeys.STEALTH_MODE_ACTIVE.key, active).commit()
    }

    // ---------------------------------------------------------------------------------------
    // Retrato das preferências
    // ---------------------------------------------------------------------------------------

    /**
     * Serializa TODO o conteúdo de `haval_prefs` num JSON `{ chave: {t: tipo, v: valor} }` e grava
     * em [SharedPreferencesKeys.STEALTH_PREFS_SNAPSHOT] com `commit()` — síncrono de propósito: o
     * retrato tem que estar no disco antes da primeira alteração, senão um kill no meio da entrada
     * levaria as configurações do usuário junto.
     *
     * @return true se o retrato foi gravado. false ABORTA a entrada no modo (ver [enter]).
     */
    private fun snapshotPreferences(): Boolean =
        try {
            val root = JSONObject()
            for ((key, value) in prefs().all) {
                if (key in INTERNAL_KEYS) continue
                val entry = JSONObject()
                when (value) {
                    is Boolean -> entry.put("t", "b").put("v", value)
                    is Int -> entry.put("t", "i").put("v", value)
                    is Long -> entry.put("t", "l").put("v", value)
                    is Float -> entry.put("t", "f").put("v", value.toDouble())
                    is String -> entry.put("t", "s").put("v", value)
                    is Set<*> -> {
                        val arr = JSONArray()
                        for (item in value) {
                            if (item is String) arr.put(item)
                        }
                        entry.put("t", "ss").put("v", arr)
                    }
                    null -> continue
                    else -> {
                        Log.w(TAG, "Tipo desconhecido em '$key' (${value.javaClass}); fora do retrato")
                        continue
                    }
                }
                root.put(key, entry)
            }
            val ok =
                prefs().edit()
                    .putString(SharedPreferencesKeys.STEALTH_PREFS_SNAPSHOT.key, root.toString())
                    .commit()
            Log.w(TAG, "Retrato das preferências gravado: ${root.length()} chaves (commit=$ok)")
            ClusterPersistentEventLogger.log(
                "stealth_prefs_snapshot",
                mapOf("keys" to root.length(), "committed" to ok)
            )
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao gravar o retrato das preferências", t)
            false
        }

    /**
     * A lista do que é desligado ao entrar.
     *
     * CRITÉRIO: (1) TODA preferência cujo nome no enum começa com `ENABLE_` — é a convenção do
     * projeto para "liga uma funcionalidade" — menos as de [NEVER_DISABLE]; (2) mais as de
     * ativação que não seguem a convenção, listadas em [EXTRA_FEATURE_KEYS].
     *
     * A parte automática é de propósito: uma feature nova que siga a convenção `ENABLE_` já nasce
     * coberta, sem ninguém precisar lembrar de vir aqui — que foi exatamente como a versão de
     * master switch deixou caminhos escapando.
     */
    private fun featureKeysToDisable(): List<String> {
        val keys = LinkedHashSet<String>()
        for (pref in SharedPreferencesKeys.values()) {
            if (pref in NEVER_DISABLE) continue
            if (pref.name.startsWith("ENABLE_")) keys.add(pref.key)
        }
        for (pref in EXTRA_FEATURE_KEYS) keys.add(pref.key)
        keys.removeAll(INTERNAL_KEYS)
        return keys.toList()
    }

    /**
     * Escreve `false` em todas as chaves de [featureKeysToDisable] — inclusive nas que nem existem
     * ainda, porque várias têm default TRUE (aviso de cinto, patch do CarPlay, fundo do display 1)
     * e "não existir" não as desliga. Chaves que hoje guardam outro tipo são puladas: sobrescrever
     * um Int com Boolean derrubaria quem as lê com getInt.
     */
    private fun disableFeaturePreferences() {
        val p = prefs()
        val current = p.all
        val editor = p.edit()
        var written = 0
        var skipped = 0
        for (key in featureKeysToDisable()) {
            val existing = current[key]
            if (existing != null && existing !is Boolean) {
                Log.w(TAG, "Pulando '$key': valor atual não é booleano (${existing.javaClass.simpleName})")
                skipped++
                continue
            }
            editor.putBoolean(key, false)
            written++
        }
        for (pref in CLEAR_ON_ENTER) {
            val existing = current[pref.key]
            if (existing != null && existing !is String) continue
            editor.putString(pref.key, "")
        }
        val ok = editor.commit()
        Log.w(TAG, "Preferências desligadas: $written (puladas: $skipped, commit=$ok)")
        ClusterPersistentEventLogger.log(
            "stealth_prefs_disabled",
            mapOf("written" to written, "skipped" to skipped, "committed" to ok)
        )
    }

    /**
     * Devolve o retrato: cada chave volta com o TIPO original e o que não estava no retrato é
     * REMOVIDO (voltando ao default do código). O retrato é apagado logo depois, pelo [exit].
     *
     * Nunca trava: retrato ausente ou corrompido só loga e retorna false — o [exit] segue com as
     * outras etapas. Ficar preso no modo é muito pior do que ter que reconfigurar.
     */
    private fun restorePreferences(): Boolean {
        val raw =
            try {
                prefs().getString(SharedPreferencesKeys.STEALTH_PREFS_SNAPSHOT.key, null)
            } catch (t: Throwable) {
                Log.e(TAG, "Falha lendo o retrato das preferências", t)
                null
            }
        if (raw.isNullOrBlank()) {
            Log.e(TAG, "Sem retrato das preferências para restaurar; seguindo com a saída")
            ClusterPersistentEventLogger.log("stealth_prefs_restore_missing")
            return false
        }
        return try {
            val root = JSONObject(raw)
            val p = prefs()
            val editor = p.edit()

            // 1) Some com o que nasceu depois do retrato (e não é chave interna do modo).
            for (key in p.all.keys.toList()) {
                if (key in INTERNAL_KEYS) continue
                if (!root.has(key)) editor.remove(key)
            }

            // 2) Devolve cada chave do retrato com o tipo original.
            var restored = 0
            val names = root.keys()
            while (names.hasNext()) {
                val key = names.next()
                if (key in INTERNAL_KEYS) continue
                val entry = root.optJSONObject(key) ?: continue
                when (entry.optString("t")) {
                    "b" -> editor.putBoolean(key, entry.optBoolean("v", false))
                    "i" -> editor.putInt(key, entry.optInt("v", 0))
                    "l" -> editor.putLong(key, entry.optLong("v", 0L))
                    "f" -> editor.putFloat(key, entry.optDouble("v", 0.0).toFloat())
                    "s" -> editor.putString(key, entry.optString("v", ""))
                    "ss" -> {
                        val arr = entry.optJSONArray("v") ?: JSONArray()
                        val set = LinkedHashSet<String>()
                        for (i in 0 until arr.length()) set.add(arr.optString(i))
                        editor.putStringSet(key, set)
                    }
                    else -> {
                        Log.w(TAG, "Entrada '$key' do retrato tem tipo desconhecido; ignorada")
                        continue
                    }
                }
                restored++
            }

            val ok = editor.commit()
            Log.w(TAG, "Preferências restauradas: $restored (commit=$ok)")
            ClusterPersistentEventLogger.log(
                "stealth_prefs_restored",
                mapOf("restored" to restored, "committed" to ok)
            )
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "Retrato das preferências corrompido; seguindo com a saída assim mesmo", t)
            ClusterPersistentEventLogger.log(
                "stealth_prefs_restore_failed",
                mapOf("error" to t.toString())
            )
            false
        }
    }

    /** Apaga o retrato. O modo não deve deixar lixo para trás. */
    private fun clearSnapshot() {
        prefs().edit().remove(SharedPreferencesKeys.STEALTH_PREFS_SNAPSHOT.key).commit()
    }

    // ---------------------------------------------------------------------------------------
    // Ícones dos apps
    // ---------------------------------------------------------------------------------------

    /**
     * Activity de launcher de [pkg], ou null se o app não tiver ícone.
     *
     * [includeDisabled] existe porque, depois de `pm disable`, o componente SOME das buscas
     * normais do PackageManager — procurar o ícone para devolvê-lo daria "esse app nunca teve
     * ícone". Toda leitura feita no caminho de VOLTA precisa da flag.
     */
    private fun launcherActivityOf(pkg: String, includeDisabled: Boolean = false): String? = try {
        val pm = App.getContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(pkg)
        val flags = if (includeDisabled) PackageManager.MATCH_DISABLED_COMPONENTS else 0
        pm.queryIntentActivities(intent, flags)
            .firstOrNull()
            ?.activityInfo
            ?.name
    } catch (t: Throwable) {
        Log.e(TAG, "Não consegui resolver a activity de launcher de $pkg", t)
        null
    }

    /**
     * Some com o ícone de [pkg] sem tocar no app: `pm disable` mira SÓ a activity do launcher.
     * O processo, os serviços e as permissões do app continuam exatamente como estavam.
     */
    private fun setThirdPartyIconEnabled(pkg: String, enabled: Boolean) {
        // Ao reabilitar, a activity já está desabilitada — sem MATCH_DISABLED_COMPONENTS a busca
        // volta vazia e o ícone ficaria escondido para sempre.
        val activity = launcherActivityOf(pkg, includeDisabled = enabled)
        if (activity == null) {
            Log.w(TAG, "$pkg não tem activity de launcher; nada a esconder")
            return
        }
        val verb = if (enabled) "enable" else "disable"
        ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", verb, "$pkg/$activity"))
        Log.w(TAG, "Ícone de $pkg: $verb ($activity)")
    }

    /**
     * Esconde o ÍCONE dos apps instalados pelo dono, deixando na tela só os nativos do sistema.
     * Os apps continuam rodando normalmente — só somem da área de trabalho e da gaveta.
     *
     * POR QUE NUNCA `pm hide`: a versão anterior suspendia cada app de terceiro. Suspender não é
     * "esconder": o app fica impedido de rodar enquanto durar o modo — não recebe broadcast, não
     * sobe serviço, nada. No carro isso matou a sessão de um app de terceiros e exigiu reautorizar o Shizuku
     * na volta. O disfarce que o dono quer é visual, então o comando certo é
     * `pm disable <pkg>/<activityDeLauncher>` — mira só o ícone.
     *
     * RESSALVA HONESTA sobre o `pm disable`: o shell não expõe DONT_KILL_APP, então trocar o
     * estado do componente MATA o processo do app uma vez, na hora. A diferença para o `pm hide`
     * é que aqui a morte é pontual e reversível sozinha: o app não fica suspenso, então serviço
     * com START_STICKY, alarme ou broadcast o trazem de volta em seguida. Não é "zero impacto",
     * é "cai e levanta" em vez de "fica no chão até sair do modo".
     *
     * `pm list packages -3` é exatamente a distinção pedida: lista apenas o que não veio de
     * fábrica.
     *
     * O REGISTRO GUARDA `pkg/activity`, não só o pacote. Dois motivos: depois do disable a
     * activity não aparece mais numa busca comum (ver [launcherActivityOf]), e ela pode mudar
     * numa atualização do app — guardar o par é o que garante devolver exatamente o que foi
     * tirado. Ele é gravado ANTES do primeiro disable: se o processo morrer no meio do laço, a
     * volta ainda sabe o que devolver.
     */
    private fun hideThirdPartyApps() {
        val raw = ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "list", "packages", "-3"))
        val packages = raw.lineSequence()
            .map { it.trim().removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it !in NEVER_TOUCH }
            .distinct()
            .toList()
        if (packages.isEmpty()) {
            Log.w(TAG, "Nenhum app de terceiros para esconder")
            return
        }

        // Resolve TODAS as activities antes de desabilitar a primeira: uma vez desabilitada, a
        // activity some da busca e o par pkg/activity não seria mais recuperável.
        val targets = packages.mapNotNull { pkg ->
            val activity = launcherActivityOf(pkg)
            if (activity == null) {
                Log.w(TAG, "$pkg não tem activity de launcher; nada a esconder")
                null
            } else {
                "$pkg/$activity"
            }
        }
        if (targets.isEmpty()) {
            Log.w(TAG, "Nenhum ícone de app de terceiros para esconder")
            return
        }

        prefs().edit().putString(HIDDEN_PACKAGES_KEY, targets.joinToString(",")).commit()
        var hidden = 0
        for (target in targets) {
            try {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "disable", target))
                hidden++
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao esconder o ícone de $target", t)
            }
        }
        Log.w(TAG, "Ícones de apps de terceiros escondidos: $hidden/${targets.size}")
        ClusterPersistentEventLogger.log(
            "stealth_hide_apps",
            mapOf("requested" to targets.size, "hidden" to hidden)
        )
    }

    /**
     * Reinicia a central logo depois de ativar o modo.
     *
     * Aplicar tudo com o sistema em pe deixa a tela travada em preto: sao janelas derrubadas,
     * icones desabilitados e um punhado de servicos parados ao mesmo tempo, e o launcher nao se
     * recompoe sozinho. O dono reiniciou na mao e o carro voltou exatamente como devia — com cara
     * de fabrica — entao o reinicio passa a fazer parte da ativacao, nao ser tarefa dele.
     *
     * Reiniciar tambem e o que faz o painel voltar ao nativo de verdade: no boot seguinte, com as
     * preferencias ja desligadas, os projetores do cluster simplesmente nao sobem.
     *
     * So com o carro PARADO: reiniciar a central em movimento tiraria camera de re, ar e som de
     * quem esta dirigindo. Velocidade ilegivel conta como em movimento.
     */
    private fun rebootHeadUnitAfterEnter(context: Context) {
        try {
            val raw = ServiceManager.getInstance()
                .getData(br.com.redesurftank.havalshisuku.models.CarConstants.CAR_BASIC_VEHICLE_SPEED.value)
            val speed = raw?.trim()?.toFloatOrNull() ?: Float.MAX_VALUE
            if (speed > 0.5f) {
                Log.w(TAG, "Carro em movimento (speed=$speed); NAO reiniciando a central")
                toast(context, "Modo Concessionária ativo — reinicie a central quando parar")
                return
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Velocidade ilegivel; nao reiniciando a central", t)
            toast(context, "Modo Concessionária ativo — reinicie a central quando parar")
            return
        }

        toast(context, "Modo Concessionária ativo — reiniciando a central…")
        ClusterPersistentEventLogger.log("stealth_reboot", mapOf("reason" to "ENTER"))
        // Folga para o toast aparecer e para as preferências assentarem em disco (o snapshot e a
        // flag já foram gravados com commit(), então um corte aqui não perde o retrato).
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("svc", "power", "reboot"))
                ShizukuUtils.runCommandAndGetOutput(arrayOf("reboot"))
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao reiniciar a central", t)
            }
        }, 4000L)
    }

    /**
     * Reinicia os apps de terceiros na saida do modo.
     *
     * Desabilitar a activity de launcher derruba o processo do app uma vez, e ele costuma voltar
     * num estado meio-vivo: um app de terceiros ficava com a tela do celular presa em dados antigos ate
     * levar um force-stop na mao. Um `am force-stop` limpa isso — o app sobe de novo pelo proprio
     * servico/alarme, ou no primeiro toque do dono.
     *
     * O proprio Impulse e o Shizuku ficam de fora: derrubar o primeiro mataria quem esta
     * executando esta restauracao, e o segundo e a fonte do privilegio que a executa.
     */
    private fun restartThirdPartyApps() {
        val keepAlive = setOf(
            "br.com.redesurftank.havalshisuku",
            "moe.shizuku.privileged.api"
        )
        val raw = ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "list", "packages", "-3"))
        val packages = raw.lineSequence()
            .map { it.trim().removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it !in keepAlive }
            .distinct()
            .toList()
        var restarted = 0
        for (pkg in packages) {
            try {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("am", "force-stop", pkg))
                restarted++
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao reiniciar $pkg", t)
            }
        }
        Log.w(TAG, "Apps de terceiros reiniciados: $restarted/${packages.size}")
        ClusterPersistentEventLogger.log(
            "stealth_restart_apps",
            mapOf("requested" to packages.size, "restarted" to restarted)
        )
    }

    /**
     * Devolve os ícones. Best-effort, um a um, e TODAS as etapas rodam SEMPRE.
     *
     * BUG CORRIGIDO: aqui havia um `return` quando a lista salva estava em branco, colocado ANTES
     * das etapas seguintes de restauração. Bastava a lista faltar (modo entrado por outra versão,
     * preferência perdida, entrada abortada no meio) para a função ir embora sem devolver nada —
     * foi assim que o ícone do Shizuku ficou para trás no carro. Agora a lista vazia só significa
     * "não tenho registro", e a varredura por estado observado continua.
     */
    private fun restoreThirdPartyApps() {
        val stored = prefs().getString(HIDDEN_PACKAGES_KEY, "").orEmpty()
        val entries = stored.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        var restored = 0
        for (entry in entries) {
            try {
                if (entry.contains('/')) {
                    ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "enable", entry))
                } else {
                    // Formato ANTIGO: versões anteriores gravavam só o pacote e usavam `pm hide`.
                    // Se o app foi atualizado com o modo ativo, é este o lixo que sobrou — desfaz
                    // a suspensão e, por garantia, reabilita o ícone.
                    ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "unhide", entry))
                    setThirdPartyIconEnabled(entry, true)
                }
                restored++
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao restaurar $entry", t)
            }
        }

        // Rede de segurança — roda mesmo sem lista nenhuma. Ver [reenableDisabledLauncherIcons].
        var swept = 0
        step("sweep_disabled_icons") { swept = reenableDisabledLauncherIcons() }

        // Só limpa o registro depois de tentar todos — se algo falhou, uma nova saída retenta.
        prefs().edit().remove(HIDDEN_PACKAGES_KEY).commit()
        Log.w(TAG, "Ícones restaurados: $restored/${entries.size} (varredura devolveu $swept)")
        ClusterPersistentEventLogger.log(
            "stealth_restore_apps",
            mapOf("requested" to entries.size, "restored" to restored, "swept" to swept)
        )
    }

    /**
     * RESTAURAÇÃO POR ESTADO OBSERVADO: reabilita qualquer ícone de app de terceiro que esteja
     * desabilitado, mesmo que a lista salva não saiba dele.
     *
     * Por que existe: até agora a saída só desfazia o que a versão ATUAL sabia ter feito. Quando o
     * app foi atualizado com o modo ligado, o registro veio de outra versão (ou de um formato
     * diferente) e sobrou lixo — o ícone de um app de terceiros ficou desabilitado e nada o devolvia. Olhar o
     * estado real do sistema conserta independentemente de quem escondeu, e de qual versão.
     *
     * LIMITES DE PRECAUÇÃO, para não "consertar" o que o dono desligou de propósito:
     *  - só apps de terceiro (`pm list packages -3`); nada do sistema é tocado;
     *  - só a activity de LAUNCHER; nenhum outro componente entra na varredura;
     *  - só o estado COMPONENT_ENABLED_STATE_DISABLED, que é exatamente o que `pm disable` grava.
     *    COMPONENT_ENABLED_STATE_DISABLED_USER (o caminho do usuário / `pm disable-user`) e
     *    DISABLED_UNTIL_USED (do sistema) ficam intocados de propósito.
     *
     * @return quantos ícones foram devolvidos.
     */
    private fun reenableDisabledLauncherIcons(): Int {
        val pm = App.getContext().packageManager
        val raw = ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "list", "packages", "-3"))
        var fixed = 0
        for (line in raw.lineSequence()) {
            val pkg = line.trim().removePrefix("package:").trim()
            if (pkg.isEmpty() || pkg in NEVER_TOUCH) continue
            try {
                val activity = launcherActivityOf(pkg, includeDisabled = true) ?: continue
                val state = pm.getComponentEnabledSetting(ComponentName(pkg, activity))
                if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) continue
                ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "enable", "$pkg/$activity"))
                Log.w(TAG, "Varredura: ícone de $pkg estava desabilitado; devolvido ($activity)")
                fixed++
            } catch (t: Throwable) {
                Log.e(TAG, "Varredura: falha ao checar/devolver o ícone de $pkg", t)
            }
        }
        Log.w(TAG, "Varredura de ícones desabilitados: $fixed devolvido(s)")
        return fixed
    }

    // ---------------------------------------------------------------------------------------
    // Entrada / saída
    // ---------------------------------------------------------------------------------------

    fun enter(context: Context, reason: String) {
        val appContext = context.applicationContext
        Log.w(TAG, "Entrando no Modo Concessionária (reason=$reason)")

        // ANTES de qualquer coisa: garantir que os botões do volante cheguem até nós. Sem a
        // integração custom habilitada, o head unit trata o botão como função nativa e os toques
        // NUNCA chegam ao app — a sequência de saída simplesmente não existiria. Forçamos
        // independentemente das preferências; o exit() devolve o estado do usuário.
        // (A sequência usa o botão 1; o 2 vai junto por ser inofensivo e simétrico.)
        //
        // ORDEM IMPORTA — este passo vem ANTES do retrato de propósito: enableSteeringWheelButtonN
        // grava a config NATIVA do botão em STEERING_WHEEL_CUSTOM_BUTON_N_ACTION_ORIGINAL antes de
        // trocá-la por 99. Se o retrato fosse tirado antes, essa chave não estaria nele e a saída a
        // removeria — aí disableNativeSteeringWheelButtonN não teria o valor original pra devolver e
        // o botão ficaria preso em 99 (nosso) sem nenhuma ação configurada, ou seja, morto.
        step("force_steering_integration") {
            ServiceManager.getInstance().enableSteeringWheelButton1Integration()
            ServiceManager.getInstance().enableSteeringWheelButton2Integration()
        }

        // O retrato é a condição de segurança da entrada: sem ele, desligar as preferências
        // significaria perder a configuração do usuário. Falhou => não entra.
        if (!snapshotPreferences()) {
            Log.e(TAG, "Não foi possível salvar as preferências; ABORTANDO a entrada no modo")
            ClusterPersistentEventLogger.log("stealth_mode_enter_aborted", mapOf("reason" to reason))
            toast(appContext, "Não consegui salvar suas configurações — Modo Concessionária NÃO foi ativado")
            return
        }

        step("set_flag") { setActive(true) }
        step("disable_feature_prefs") { disableFeaturePreferences() }
        step("hide_launcher_icon") { setLauncherIconEnabled(appContext, false) }

        // Agora força a aplicação do estado desligado pelos mesmos pontos do boot.
        //
        // O refresh() sozinho já basta desde que ProjectorManager.initialize() passou a respeitar
        // ENABLE_VIRTUAL_CLUSTER / ENABLE_INSTRUMENT_PROJECTOR: com as prefs em false ele derruba a
        // Presentation viva e não recria nenhuma — o painel volta a ser o nativo. O stopProjectors()
        // logo depois é redundância barata, para o caso de o refresh falhar no meio.
        // Presentation.dismiss() exige a UI thread; por isso o onMain.
        onMain {
            step("refresh_projectors") { ProjectorManager.getInstance().refresh() }
            step("stop_projectors") { ProjectorManager.getInstance().stopProjectors() }
        }
        // A barra inferior e o overlay flutuante de CPU/RAM são desenhados pelo MESMO serviço
        // (BottomBarService); o onDestroy dele já devolve o `wm overscan` para 0,0,0,0.
        step("stop_bottom_bar") {
            appContext.stopService(Intent(appContext, BottomBarService::class.java))
        }
        step("stop_ambient_light") { AmbientLightService.stop(appContext) }
        // Com a pref já em false, updateSchedule() cancela os alarmes e não reagenda nada.
        step("cancel_auto_brightness") { AutoBrightnessManager.getInstance().updateSchedule() }
        // A notificação persistente denuncia o app pelo nome. Troca AGORA para a versão neutra —
        // sem isto ela só mudaria no próximo start do serviço. (A flag já está em true acima, então
        // o serviço monta a discreta.)
        step("quiet_notification") { ForegroundService.refreshNotificationForStealth() }

        // Trabalho de shell via Shizuku: nunca na thread chamadora (a UI).
        //
        // hide_third_party_apps entrou aqui (era síncrono): cada `pm` é um processo novo pelo
        // Shizuku, e agora são dois por app (resolver a activity + desabilitar). Numa lista de
        // uma dúzia de apps isso é segundos de bloqueio — de pé na thread que chamou enter(),
        // que é a da UI. Vai primeiro na fila porque é o efeito que o dono vê na tela.
        background("apply_off_state") {
            step("hide_third_party_apps") { hideThirdPartyApps() }
            step("unmount_android_auto") { AndroidAutoPatchManager.removeMounts() }
            step("unmount_carplay") { CarPlayPatchManager.removeMounts() }
            // onServicesReady() só LIGA; para desligar é o setEnabled(false) (a pref já está false).
            step("stop_hot_router") { HotRouterManager.getInstance().setEnabled(false) }
            // Devolve o 4G e a telemetria OEM: no modo o carro tem que se comportar como de fábrica.
            step("release_mobile_data") { MobileDataManager.recomputeAndApply(appContext) }
            step("release_datatrack") { MobileDataManager.applyDatatrackState() }
            // Reinstala (pm install-existing) os apps do OEM que o debloat tinha removido.
            step("restore_native_apps") { ServiceManager.getInstance().ensureDebloatedSystemApps() }
        }

        step("log") {
            ClusterPersistentEventLogger.log("stealth_mode_enter", mapOf("reason" to reason))
        }
        // O toast final e o reinicio ficam a cargo de rebootHeadUnitAfterEnter: aplicar tudo com
        // o sistema em pe deixa a tela travada em preto, e o reinicio e o que faz o painel voltar
        // ao nativo de verdade (no boot seguinte os projetores nem sobem).
        step("reboot_head_unit") { rebootHeadUnitAfterEnter(appContext) }
    }

    /**
     * A sequência fechou. Se o dono ligou o PIN, ainda falta provar QUEM é: a sequência mostra
     * intenção (não foi sem querer), o PIN mostra identidade. Sem PIN, sai direto como antes.
     */
    @JvmStatic
    fun onExitSequenceCompleted(context: Context, reason: String) {
        if (!StealthExitPin.isEnabled()) {
            exit(context, reason)
            return
        }
        Log.w(TAG, "[$reason] sequência ok; pedindo PIN")
        // Janela sobreposta, NÃO Activity: com o app escondido não há tela em primeiro plano, e o
        // Android recusa abrir Activity a partir de segundo plano. A primeira versão usava Activity
        // e no carro ela nunca apareceu — a saída acontecia sem pedir PIN nenhum, que é o oposto do
        // que a feature promete.
        val shown = StealthPinOverlay.show { exit(context, "${reason}_PIN_OK") }
        if (!shown) {
            // Nem o overlay subiu (sem permissão de sobreposição, por exemplo). Ficar preso no modo
            // sem nenhum recurso é pior que o modo ser burlável: sai e registra.
            Log.e(TAG, "[$reason] teclado do PIN não subiu; saindo sem ele")
            exit(context, "${reason}_PIN_UI_FALHOU")
        }
    }

    @JvmStatic
    fun exit(context: Context, reason: String) {
        val appContext = context.applicationContext
        Log.w(TAG, "Saindo do Modo Concessionária (reason=$reason)")

        // A flag cai PRIMEIRO: tudo que é reaplicado abaixo tem que enxergar o app já normal.
        step("clear_flag") { setActive(false) }
        // Depois as preferências: todas as reaplicações abaixo leem delas. Dentro de step() como
        // todo o resto — nada nesta função pode abortar a saída pela metade.
        var restored = false
        step("restore_prefs") { restored = restorePreferences() }
        step("clear_snapshot") { clearSnapshot() }
        // Devolve os botões ao que as preferências do usuário mandam (pode ser função nativa).
        step("restore_steering_integration") {
            ServiceManager.getInstance().ensureSteeringWheelButtonIntegration()
        }
        step("show_launcher_icon") { setLauncherIconEnabled(appContext, true) }
        // Flag já em false lá em cima, então isto reemite a notificação normal do serviço.
        step("restore_notification") { ForegroundService.refreshNotificationForStealth() }
        onMain { step("restart_projectors") { ProjectorManager.getInstance().refresh() } }
        step("restart_bottom_bar") { restartBottomBarLikeBoot(appContext) }
        step("restart_ambient_light") { AmbientLightService.startIfEnabled(appContext) }
        // updateSchedule() já retorna cedo se a pref do usuário estiver desligada.
        step("restore_auto_brightness") { AutoBrightnessManager.getInstance().updateSchedule() }

        background("reapply_user_state") {
            // restore_third_party_apps entrou aqui (era síncrono) e vai PRIMEIRO: é o que o dono
            // precisa ver de volta. Um dos caminhos de saída é o StealthExitReceiver, cujo
            // onReceive roda na main thread com o prazo de ANR do broadcast — e a devolução dos
            // ícones agora varre a lista instalada além da lista salva, ou seja, mais `pm` ainda.
            // Bloquear ali arriscaria o sistema derrubar justo o caminho de recuperação.
            step("restore_third_party_apps") { restoreThirdPartyApps() }
            step("restart_third_party_apps") { restartThirdPartyApps() }
            // Mesma rotina e mesmo gate por pref que o ForegroundService usa no boot.
            step("remount_android_auto") {
                if (prefs().getBoolean(SharedPreferencesKeys.AA_PATCH_AUTO_MOUNT.key, false)) {
                    AndroidAutoPatchManager.ensureMounted()
                }
            }
            step("remount_carplay") {
                if (prefs().getBoolean(SharedPreferencesKeys.CARPLAY_PATCH_AUTO_MOUNT.key, true)) {
                    CarPlayPatchManager.ensureMounted()
                }
            }
            // onServicesReady() é o ponto do boot: liga só se a pref restaurada mandar.
            step("restart_hot_router") { HotRouterManager.getInstance().onServicesReady() }
            step("reapply_mobile_data") { MobileDataManager.recomputeAndApply(appContext) }
            step("reapply_datatrack") { MobileDataManager.applyDatatrackState() }
            step("reapply_debloat") { ServiceManager.getInstance().ensureDebloatedSystemApps() }
        }

        step("log") {
            ClusterPersistentEventLogger.log(
                "stealth_mode_exit",
                mapOf("reason" to reason, "prefsRestored" to restored)
            )
        }
        toast(
            appContext,
            if (restored) "Impulse reativado"
            else "Impulse reativado — não achei o retrato das configurações, confira os ajustes"
        )
    }

    // ---------------------------------------------------------------------------------------
    // Etapas
    // ---------------------------------------------------------------------------------------

    /**
     * Some/volta com o ícone do launcher. SplashActivity é quem carrega o intent-filter LAUNCHER
     * (ver AndroidManifest.xml); DONT_KILL_APP mantém o processo vivo — é ele que ainda escuta o
     * volante pra poder desfazer isso.
     */
    private fun setLauncherIconEnabled(context: Context, enabled: Boolean) {
        val component = ComponentName(context, SplashActivity::class.java)
        val state =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP
        )
        Log.w(TAG, "Ícone do launcher " + (if (enabled) "reativado" else "escondido"))
    }

    /** Mesmo caminho do BottomBarBootReceiver: sobe o serviço e reaplica o overscan salvo. */
    private fun restartBottomBarLikeBoot(context: Context) {
        val p = prefs()
        if (!p.getBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.key, false)) {
            Log.d(TAG, "Barra inferior desligada nas preferências; nada a religar")
            return
        }
        if (!android.provider.Settings.canDrawOverlays(context)) {
            Log.e(TAG, "Sem permissão de overlay; não dá pra religar a barra inferior")
            return
        }
        context.startService(Intent(context, BottomBarService::class.java))
        val overscan = p.getInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 20)
        background("reapply_overscan") {
            ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,$overscan"))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Utilitários — nenhuma etapa pode derrubar as seguintes
    // ---------------------------------------------------------------------------------------

    private inline fun step(name: String, body: () -> Unit) {
        try {
            body()
        } catch (t: Throwable) {
            Log.e(TAG, "Etapa '$name' do Modo Concessionária falhou (seguindo adiante)", t)
            try {
                ClusterPersistentEventLogger.log(
                    "stealth_mode_step_failed",
                    mapOf("step" to name, "error" to t.toString())
                )
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onMain(body: () -> Unit) {
        try {
            Handler(Looper.getMainLooper()).post(body)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao postar etapa na UI thread", t)
        }
    }

    private fun background(name: String, body: () -> Unit) {
        try {
            Thread({ step(name, body) }, "StealthMode-$name").start()
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao iniciar a thread da etapa '$name'", t)
        }
    }

    private fun toast(context: Context, message: String) {
        onMain {
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao exibir o toast do Modo Concessionária", t)
            }
        }
    }
}
