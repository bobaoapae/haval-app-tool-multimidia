package br.com.redesurftank.havalshisuku.managers

import android.content.SharedPreferences
import android.util.Log

/**
 * Leva quem usava o gatilho do RECOLHIMENTO DOS RETROVISORES para o gatilho de TRANCAR o carro.
 *
 * O gatilho antigo foi retirado. Sem esta migracao, quem tinha a funcao ligada simplesmente pararia
 * de te-la, sem aviso e sem nada na tela explicando — o interruptor sumiria junto com o
 * comportamento. Migrar e o que transforma a remocao em troca.
 *
 * ## Idempotente por construcao
 *
 * O plano remove as chaves antigas depois de aplicar. Rodar de novo encontra o mapa vazio e nao faz
 * nada — nao precisa de flag de "ja migrei", que seria mais um estado para ficar errado.
 *
 * ## Nunca liga o que estava desligado
 *
 * So a chave antiga com valor `true` acende a nova. Antiga `false` apenas some: quem nao usava a
 * funcao nao pode ganha-la de presente, ainda mais uma que mexe em vidro.
 *
 * A parte pura ([plan]) e separada do acesso as preferencias porque e ela que decide o que acontece
 * com a configuracao de quem ja usa — e isso merece teste, nao confianca.
 */
object FoldMirrorTriggerMigration {

    private const val TAG = "FoldMirrorMigration"

    /** Chave antiga -> chave nova. Literais de proposito: as antigas nao existem mais no enum. */
    val MAPPING =
        linkedMapOf(
            "closeWindowOnFoldMirror" to "closeWindowOnLock",
            "closeSunroofOnFoldMirror" to "closeSunroofOnLock",
            "disableBluetoothOnFoldMirror" to "disableBluetoothOnLock",
            "disableHotspotOnFoldMirror" to "disableHotspotOnLock",
        )

    /**
     * @param enable chaves NOVAS a ligar
     * @param remove chaves ANTIGAS a apagar
     */
    data class Plan(val enable: List<String>, val remove: List<String>) {
        val isEmpty: Boolean
            get() = enable.isEmpty() && remove.isEmpty()
    }

    /** @param old valor de cada chave antiga; ausente do mapa = nunca foi configurada. */
    fun plan(old: Map<String, Boolean>): Plan {
        val enable = mutableListOf<String>()
        val remove = mutableListOf<String>()
        for ((from, to) in MAPPING) {
            val value = old[from] ?: continue
            remove.add(from)
            if (value) enable.add(to)
        }
        return Plan(enable, remove)
    }

    /**
     * Aplica a migracao, se houver o que migrar.
     *
     * @param stealthModeActive com o Modo Concessionaria LIGADO a migracao nao roda. Naquele estado
     *   as preferencias estao desligadas de proposito e os valores de verdade vivem no retrato que
     *   sera restaurado na saida — migrar ali leria `false` de todas, apagaria as antigas, e o
     *   retrato devolveria chaves que ninguem mais le. O dono perderia a configuracao em silencio.
     *   Rodando depois da saida, o retrato ja voltou e a migracao ve os valores reais.
     */
    @JvmStatic
    fun run(prefs: SharedPreferences, stealthModeActive: Boolean) {
        if (stealthModeActive) return
        val old =
            MAPPING.keys
                .filter { prefs.contains(it) }
                .associateWith { runCatching { prefs.getBoolean(it, false) }.getOrDefault(false) }
        val plan = plan(old)
        if (plan.isEmpty) return
        runCatching {
                prefs.edit().apply {
                    plan.enable.forEach { putBoolean(it, true) }
                    plan.remove.forEach { remove(it) }
                    apply()
                }
                Log.w(TAG, "migrado p/ gatilho de trancar: ligou=${plan.enable} removeu=${plan.remove}")
            }
            .onFailure { Log.e(TAG, "falha ao migrar o gatilho do retrovisor", it) }
    }
}
