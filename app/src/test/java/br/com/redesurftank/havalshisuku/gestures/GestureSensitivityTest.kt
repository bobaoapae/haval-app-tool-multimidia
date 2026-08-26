package br.com.redesurftank.havalshisuku.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A conversão tem divisão dos dois lados — é o tipo de conta que se inverte errado sem ninguém
 * perceber até o ajuste ficar ao contrário no carro.
 */
class GestureSensitivityTest {

    @Test
    fun theSliderValueSurvivesTheRoundTrip() {
        // Temperatura: meio grau por passo.
        for (degrees in listOf(2f, 3f, 6f, 10f)) {
            val step = GestureSensitivity.stepThousandths(degrees, 0.5f)
            assertEquals(degrees, GestureSensitivity.perHalfScreen(step, 0.5f), 0.25f)
        }
        // Volume: um ponto por passo.
        for (points in listOf(5f, 17f, 30f)) {
            val step = GestureSensitivity.stepThousandths(points, 1f)
            assertEquals(points, GestureSensitivity.perHalfScreen(step, 1f), 1f)
        }
    }

    @Test
    fun horizontalUsesHalfTheWIDTH() {
        // Numa central 1920x720, ignorar a proporção faria o número mostrado mentir por quase 3x.
        val aspect = 1920f / 720f
        val step = GestureSensitivity.stepThousandths(6f, 1f, aspect)
        assertEquals(6f, GestureSensitivity.perHalfScreen(step, 1f, aspect), 0.5f)
        // O mesmo passo lido SEM a proporção daria um número bem menor — é a prova de que ela pesa.
        assertTrue(GestureSensitivity.perHalfScreen(step, 1f) < 3f)
    }

    @Test
    fun theCurrentDefaultsLandOnSaneNumbers() {
        // Os padrões do código, traduzidos para o que a tela mostra.
        assertEquals(17f, GestureSensitivity.perHalfScreen(30, 1f), 1f)
        assertEquals(3.3f, GestureSensitivity.perHalfScreen(75, 0.5f), 0.3f)
        assertEquals(6f, GestureSensitivity.perHalfScreen(220, 1f, 1920f / 720f), 0.5f)
    }

    @Test
    fun absurdValuesAreClampedInsteadOfAccepted() {
        // Passo minúsculo dispararia centenas de vezes num arraste; gigante nunca dispararia.
        assertEquals(GestureSensitivity.STEP_RANGE.last, GestureSensitivity.stepThousandths(0.01f, 1f))
        assertEquals(GestureSensitivity.STEP_RANGE.first, GestureSensitivity.stepThousandths(9999f, 1f))
    }
}
