package br.com.redesurftank.havalshisuku.managers

import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys

/**
 * A sequência que tira o carro do Modo Concessionária.
 *
 * Antes era fixa (`L,R,L,R`). Agora o dono monta a dele, e cada passo pode ser uma seta ou um
 * botão do volante — misturar os dois é o que torna a sequência realmente pessoal.
 *
 * **Por que setas sozinhas não bastariam:** o carro só avisa quando a luz da seta ACENDE, e acender
 * a mesma seta duas vezes seguidas não produz dois eventos distinguíveis. Com só duas letras que
 * não podem se repetir, toda sequência é obrigada a alternar, e a única liberdade seria o lado
 * inicial e o tamanho. Os botões do volante quebram essa amarra: `B1,B1` é perfeitamente
 * detectável (são dois toques), então `L,B1,B1,R` existe.
 *
 * O mesmo motor serve ao ENSAIO (o dono prova que sabe sair, antes de ativar) e à SAÍDA real.
 */
object StealthExitSequence {

    private const val TAG = "StealthExitSequence";

    enum class Step(val token: String, val label: String, val short: String) {
        LEFT("L", "Seta esquerda", "←"),
        RIGHT("R", "Seta direita", "→"),
        BUTTON1("B1", "Botão 1 do volante", "①"),
        BUTTON2("B2", "Botão 2 do volante", "②"),
        HIGH_BEAM("HB", "Luz alta (passada de farol)", "⇈");

        /**
         * Passos que chegam como ESTADO DE LUZ não podem se repetir em seguida.
         *
         * A seta pisca sozinha enquanto a alavanca está acionada, então "acendeu" chega várias vezes
         * para um único gesto — por isso repetição não conta. A luz alta em tese daria para piscar
         * duas vezes seguidas, mas uma passada de farol costuma gerar mais de um evento, e contar
         * errado aqui significa o dono não conseguir sair do modo. Fica na mesma regra: sem repetir.
         * Os botões do volante são toques discretos e podem repetir à vontade.
         */
        val isLightSignal: Boolean
            get() = this == LEFT || this == RIGHT || this == HIGH_BEAM

        companion object {
            fun fromToken(token: String): Step? =
                    values().firstOrNull { it.token.equals(token.trim(), ignoreCase = true) }
        }
    }

    const val MIN_STEPS = 3
    const val MAX_STEPS = 8

    /** Continua sendo a sequência histórica: quem já usava o modo não é surpreendido. */
    val DEFAULT: List<Step> = listOf(Step.LEFT, Step.RIGHT, Step.LEFT, Step.RIGHT)

    /**
     * Prazo para a sequência INTEIRA, contado do primeiro passo. Antes era uma folga por passo, o
     * que deixava uma sequência de 6 passos durar minutos: quem observasse o dono teria tempo de
     * sobra para anotar. Com um prazo total, quem não sabe a sequência de cor não chega ao fim.
     */
    const val TOTAL_WINDOW_MS = 30_000L

    /** Repique do mesmo toque do botão (o volante repete o evento). */
    private const val BUTTON_DEBOUNCE_MS = 300L

    // ---- persistência ---------------------------------------------------------------------

    fun parse(raw: String?): List<Step> {
        if (raw.isNullOrBlank()) return DEFAULT
        val steps = raw.split(',').mapNotNull { Step.fromToken(it) }
        return if (validate(steps) == null) steps else DEFAULT
    }

    fun format(steps: List<Step>): String = steps.joinToString(",") { it.token }

    /** Texto amigável pra tela e pro ensaio: "← → ① ①". */
    fun describe(steps: List<Step>): String = steps.joinToString(" ") { it.short }

    /** Devolve null quando a sequência é válida, ou o motivo da recusa. */
    fun validate(steps: List<Step>): String? {
        if (steps.size < MIN_STEPS) return "Use pelo menos $MIN_STEPS passos."
        if (steps.size > MAX_STEPS) return "No máximo $MAX_STEPS passos."
        for (i in 1 until steps.size) {
            val prev = steps[i - 1]
            val cur = steps[i]
            if (cur.isLightSignal && cur == prev) {
                // Não é preciosismo: o carro não emite dois acendimentos distinguíveis, então uma
                // sequência assim nunca seria reconhecida e trancaria o dono do lado de fora.
                return "${cur.label} não pode aparecer duas vezes seguidas — o carro não distingue."
            }
        }
        return null
    }

    /** O dono optou por montar a própria sequência? Senão, vale a padrão. */
    @JvmStatic
    fun isCustom(): Boolean =
            App.getDeviceProtectedContext()
                    .getSharedPreferences("haval_prefs", android.content.Context.MODE_PRIVATE)
                    .getBoolean(SharedPreferencesKeys.STEALTH_EXIT_SEQUENCE_CUSTOM.key, false)

    @JvmStatic
    fun current(): List<Step> =
            if (!isCustom()) DEFAULT
            else parse(
                    App.getDeviceProtectedContext()
                            .getSharedPreferences("haval_prefs", android.content.Context.MODE_PRIVATE)
                            .getString(SharedPreferencesKeys.STEALTH_EXIT_SEQUENCE.key, null)
            )

    // ---- máquina de estados ---------------------------------------------------------------

    @Volatile private var index = 0
    @Volatile private var lastStep: Step? = null
    @Volatile private var lastAtMs = 0L
    @Volatile private var firstAtMs = 0L

    enum class Outcome { IGNORED, PROGRESS, COMPLETED }

    @JvmStatic
    @Synchronized
    fun reset() {
        index = 0
        lastStep = null
        lastAtMs = 0L
        firstAtMs = 0L
    }

    /** Quantos passos já foram reconhecidos (pro ensaio mostrar progresso). */
    @JvmStatic
    @Synchronized
    fun progress(): Int = index

    /**
     * Alimenta o motor com um passo executado pelo dono.
     *
     * Os guardas de contexto (pisca-alerta ligado, carro em movimento, modo inativo) ficam de fora
     * de propósito: quem sabe disso é o ServiceManager, que tem os dados do CAN em mãos.
     */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun feed(step: Step, expected: List<Step> = current()): Outcome {
        if (expected.isEmpty()) return Outcome.IGNORED
        val now = SystemClock.uptimeMillis()

        if (step.isLightSignal && step == lastStep) {
            // Mesma seta acendendo de novo: o carro repete o evento enquanto pisca.
            return Outcome.IGNORED
        }
        if (!step.isLightSignal && step == lastStep && now - lastAtMs < BUTTON_DEBOUNCE_MS) {
            return Outcome.IGNORED // repique do mesmo toque
        }
        if (firstAtMs != 0L && now - firstAtMs > TOTAL_WINDOW_MS) {
            index = 0 // estourou o prazo da sequência inteira
            firstAtMs = 0L
        }

        lastStep = step
        lastAtMs = now

        if (expected[index] != step) {
            // Passo fora de ordem: recomeça, mas aproveita este acionamento se ele serve de 1º.
            index = if (expected[0] == step) 1 else 0
            Log.w(TAG, "fora de ordem; recomeçando em $index")
            return if (index > 0) Outcome.PROGRESS else Outcome.IGNORED
        }

        index++
        if (index == 1) firstAtMs = now
        Log.w(TAG, "passo $index/${expected.size}")
        if (index < expected.size) return Outcome.PROGRESS

        reset()
        return Outcome.COMPLETED
    }
}
