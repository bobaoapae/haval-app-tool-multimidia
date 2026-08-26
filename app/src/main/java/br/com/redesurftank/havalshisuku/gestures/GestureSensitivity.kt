package br.com.redesurftank.havalshisuku.gestures

/**
 * Conversao entre o que o dono ajusta e o que o reconhecedor usa.
 *
 * O reconhecedor pensa em "distancia por passo, em alturas de tela" — grandeza util pra ele e
 * incompreensivel numa tela de configuracao. O slider expoe **quanto muda num arraste de meia
 * tela**, que e a pergunta que a pessoa de fato responde. Este objeto e a ponte entre os dois.
 *
 * Puro pra ser testavel: e uma conversao com divisao dos dois lados, exatamente o tipo de conta que
 * se inverte errado sem ninguem perceber ate o ajuste ficar ao contrario no carro.
 *
 * [aspect] entra quando o gesto e HORIZONTAL: pra ele "meia tela" e meia largura, e numa central
 * esticada (1920x720) ignorar isso faria o numero mostrado mentir por quase 3 vezes.
 */
object GestureSensitivity {

    /** Limites do passo, em milesimos de altura de tela. Fora disso nao e ajuste, e engano. */
    val STEP_RANGE = 8..400

    /** Passo (em milesimos) -> quanto muda num arraste de meia tela. */
    fun perHalfScreen(stepThousandths: Int, unitPerStep: Float, aspect: Float = 1f): Float {
        val step = stepThousandths / 1000f
        if (step <= 0f) return unitPerStep
        return (0.5f * aspect / step) * unitPerStep
    }

    /** O caminho de volta: quanto muda em meia tela -> passo (em milesimos), ja limitado. */
    fun stepThousandths(perHalf: Float, unitPerStep: Float, aspect: Float = 1f): Int {
        val steps = (perHalf / unitPerStep).coerceAtLeast(0.5f)
        val step = 0.5f * aspect / steps
        return Math.round(step * 1000f).coerceIn(STEP_RANGE.first, STEP_RANGE.last)
    }
}
