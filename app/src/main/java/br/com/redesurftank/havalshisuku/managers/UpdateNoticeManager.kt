package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.BuildConfig
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.utils.ReleaseUpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Verifica UMA vez por partida se saiu versão nova e avisa discretamente.
 *
 * **De propósito não sabe ONDE buscar.** Quem sabe é o [ReleaseUpdateChecker], e o endereço dele é
 * a única linha que difere entre este fork e o repositório de origem — cada um aponta pro seu
 * próprio local de releases. Mantendo essa decisão fora daqui, esta função fica idêntica nos dois
 * lados e nunca conflita num merge.
 *
 * Também não baixa nem instala nada: só avisa. Instalar envolve o caminho do Frida e é decisão do
 * dono, na tela de Informações.
 */
object UpdateNoticeManager {

    private const val TAG = "UpdateNoticeManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Versão nova disponível, ou null. Lido pela barra inferior (card do dashboard) e pelo menu.
     * É `mutableStateOf` porque quem observa é Compose.
     */
    var availableVersion by mutableStateOf<String?>(null)
        private set

    @Volatile private var alreadyChecked = false

    /**
     * Espera a rede aparecer antes de tentar.
     *
     * Verificar no instante da partida seria furada: ao ligar o carro o WiFi ainda está associando
     * e o 4G negociando, então a busca falharia quase sempre — e um "não consegui verificar"
     * repetido a cada ignição é pior que não avisar nada.
     */
    private const val NETWORK_POLL_MS = 15_000L
    private const val NETWORK_GIVEUP_MS = 5 * 60_000L

    private fun prefs() =
            App.getDeviceProtectedContext()
                    .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    private fun hasNetwork(): Boolean = try {
        val cm = App.getContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (t: Throwable) {
        false
    }

    /** Chamado no start do app. O app sobe uma vez por ignição, então isto é uma vez por partida. */
    @JvmStatic
    fun checkOnStart() {
        if (alreadyChecked) return
        if (!prefs().getBoolean(SharedPreferencesKeys.ENABLE_UPDATE_NOTICE.key, true)) return
        alreadyChecked = true
        scope.launch {
            try {
                var waited = 0L
                while (!hasNetwork()) {
                    if (waited >= NETWORK_GIVEUP_MS) {
                        Log.w(TAG, "sem rede após ${waited / 1000}s; desisto desta partida")
                        return@launch
                    }
                    delay(NETWORK_POLL_MS)
                    waited += NETWORK_POLL_MS
                }

                val current = BuildConfig.VERSION_NAME.removePrefix("v")
                // Mesma regra da tela de Informações: quem está numa build "-preview" acompanha o
                // canal beta; quem está numa estável, as estáveis. Se as duas discordassem, o card
                // apontaria uma novidade que a tela não confirma.
                val onPreview = BuildConfig.VERSION_NAME.contains("-preview")
                val result = ReleaseUpdateChecker.getAllReleaseInfo()
                val candidate = (if (onPreview) result.latestPreview else result.latestRelease)
                        ?: return@launch
                val candidateVersion = candidate.tag.removePrefix("v")

                if (ReleaseUpdateChecker.compareVersions(candidateVersion, current) <= 0) {
                    Log.w(TAG, "nenhuma versão nova (atual $current)")
                    return@launch
                }
                val dismissed = prefs()
                        .getString(SharedPreferencesKeys.UPDATE_NOTICE_DISMISSED_VERSION.key, "")
                if (candidateVersion == dismissed) {
                    // Já avisou e o dono dispensou: cala até sair OUTRA versão. Sem isto, o mesmo
                    // card voltaria toda manhã pra quem simplesmente não quer atualizar agora.
                    Log.w(TAG, "versão $candidateVersion já foi dispensada")
                    return@launch
                }
                Log.w(TAG, "versão nova disponível: $candidateVersion (atual $current)")
                availableVersion = candidateVersion
            } catch (t: Throwable) {
                Log.e(TAG, "falha ao verificar atualização", t)
            }
        }
    }

    /** O dono dispensou o aviso desta versão. Volta a avisar só quando sair outra. */
    @JvmStatic
    fun dismiss() {
        val v = availableVersion ?: return
        availableVersion = null
        prefs().edit {
            putString(SharedPreferencesKeys.UPDATE_NOTICE_DISMISSED_VERSION.key, v)
        }
        Log.w(TAG, "aviso da versão $v dispensado")
    }
}
