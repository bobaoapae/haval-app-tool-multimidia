package br.com.redesurftank.havalshisuku.gestures

import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Action
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Axis
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Config
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Pt
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Step
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.SwipeGestureRecognizer
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureBindingsTest {

    // ---------------------------------------------------------------------------------------
    // Modelo e persistência
    // ---------------------------------------------------------------------------------------

    @Test
    fun defaultIsWhatAlwaysWorkedBeforeTheScreenExisted() {
        val d = GestureBindings.DEFAULT
        assertEquals(Action.DRIVER_TEMP, d.actionFor(3, Axis.VERTICAL, Zone.LEFT))
        assertEquals(Action.VOLUME, d.actionFor(3, Axis.VERTICAL, Zone.CENTER))
        assertEquals(Action.PASSENGER_TEMP, d.actionFor(3, Axis.VERTICAL, Zone.RIGHT))
        for (zone in Zone.entries) {
            assertEquals(Action.FAN, d.actionFor(3, Axis.HORIZONTAL, zone))
        }
        assertEquals(setOf(3), d.activeFingerCounts())
        assertNull(d.actionFor(2, Axis.VERTICAL, Zone.LEFT))
    }

    @Test
    fun survivesARoundTrip() {
        val custom =
            GestureBindings.DEFAULT
                .with(4, Axis.HORIZONTAL, Zone.RIGHT, Action.VOLUME)
                .with(4, Axis.VERTICAL, Zone.CENTER, Action.FAN)
        assertEquals(custom, GestureBindings.decode(custom.encode()))
    }

    @Test
    fun turningEverythingOffIsNotTheSameAsNeverConfiguring() {
        // Sem o marcador, apagar o último vínculo faria os padrões ressuscitarem no próximo boot.
        var empty = GestureBindings.DEFAULT
        for (zone in Zone.entries) {
            empty = empty.with(3, Axis.VERTICAL, zone, null).with(3, Axis.HORIZONTAL, zone, null)
        }
        assertTrue(empty.isEmpty())
        assertEquals(GestureBindings.EMPTY_MARKER, empty.encode())
        assertTrue(GestureBindings.decode(empty.encode()).isEmpty())

        // Ausente ou em branco continua caindo no padrão.
        assertEquals(GestureBindings.DEFAULT, GestureBindings.decode(null))
        assertEquals(GestureBindings.DEFAULT, GestureBindings.decode("   "))
    }

    @Test
    fun aBrokenEntryIsSkippedInsteadOfLosingEverything() {
        // Configuração meio lida é melhor que gesto nenhum: o dono conserta a que sumiu.
        // "2:H:R" entra na lista do lixo de proposito: dois dedos saiu das opcoes, entao uma
        // configuracao antiga que o tenha precisa ser descartada em vez de reviver.
        val raw = "3:V:C=volume;lixo;9:V:L=fan;3:X:L=volume;3:V:Z=fan;3:V:L=inexistente;2:H:R=fan;4:H:R=fan"
        val b = GestureBindings.decode(raw)
        assertEquals(Action.VOLUME, b.actionFor(3, Axis.VERTICAL, Zone.CENTER))
        assertEquals(Action.FAN, b.actionFor(4, Axis.HORIZONTAL, Zone.RIGHT))
        assertNull(b.actionFor(2, Axis.HORIZONTAL, Zone.RIGHT))
        assertEquals(setOf(3, 4), b.activeFingerCounts())
        assertNull(b.actionFor(3, Axis.VERTICAL, Zone.LEFT))
    }

    @Test
    fun wholeRowIsJustTheSameActionInEveryZone() {
        val b = GestureBindings.DEFAULT.withWholeRow(4, Axis.VERTICAL, Action.VOLUME)
        for (zone in Zone.entries) {
            assertEquals(Action.VOLUME, b.actionFor(4, Axis.VERTICAL, zone))
        }
        assertEquals(setOf(3, 4), b.activeFingerCounts())
        assertEquals(4, b.maxFingers())
    }

    // ---------------------------------------------------------------------------------------
    // Reconhecimento com mais de uma contagem configurada
    // ---------------------------------------------------------------------------------------

    private fun fingersAt(center: Pt, n: Int): List<Pt> =
        (0 until n).map { Pt(center.x + (it - (n - 1) / 2f) * 0.01f, center.y) }

    /** Encosta os dedos UM A UM (como acontece de verdade) e depois arrasta. */
    private fun landAndSwipe(
        r: SwipeGestureRecognizer,
        n: Int,
        from: Pt,
        to: Pt,
    ): List<Step> {
        val out = mutableListOf<Step>()
        for (k in 1..n) out += r.onFrame(fingersAt(from, k))
        for (i in 1..40) {
            val t = i.toFloat() / 40
            out += r.onFrame(fingersAt(Pt(from.x, from.y + (to.y - from.y) * t), n))
        }
        return out
    }

    @Test
    fun landingFourFingersDoesNotFireTheThreeFingerAction() {
        // Ao encostar quatro dedos passa-se por três. Enquanto nenhum passo saiu, isso é só a mão
        // pousando — tem que re-armar na contagem nova, não disparar a ação de três dedos.
        val bindings = GestureBindings.DEFAULT.withWholeRow(4, Axis.VERTICAL, Action.FAN)
        val steps = landAndSwipe(SwipeGestureRecognizer(Config(), bindings), 4, Pt(0.5f, 0.15f), Pt(0.5f, 0.85f))
        assertTrue(steps.isNotEmpty())
        assertTrue("saiu $steps", steps.all { it.action == Action.FAN })
    }

    @Test
    fun fourFingersWorkWhenTheOwnerBindsThem() {
        val bindings = GestureBindings.DEFAULT.withWholeRow(4, Axis.VERTICAL, Action.FAN)
        val steps = landAndSwipe(SwipeGestureRecognizer(Config(), bindings), 4, Pt(0.5f, 0.15f), Pt(0.5f, 0.85f))
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.all { it.action == Action.FAN && it.delta == -1 })
    }

    @Test
    fun anUnboundCombinationIsInert() {
        // Três dedos na horizontal sem vínculo: não vira nada, e não vira outra coisa por engano.
        var bindings = GestureBindings.DEFAULT
        for (zone in Zone.entries) bindings = bindings.with(3, Axis.HORIZONTAL, zone, null)
        val r = SwipeGestureRecognizer(Config(), bindings)
        val out = mutableListOf<Step>()
        for (k in 1..3) out += r.onFrame(fingersAt(Pt(0.15f, 0.5f), k))
        for (i in 1..40) {
            out += r.onFrame(fingersAt(Pt(0.15f + 0.7f * i / 40f, 0.5f), 3))
        }
        assertTrue("saiu $out", out.isEmpty())
    }

    @Test
    fun aCountAboveEverythingConfiguredCancels() {
        // Só três dedos configurados: quatro é mão apoiada, não gesto.
        val steps =
            landAndSwipe(
                SwipeGestureRecognizer(Config(), GestureBindings.DEFAULT),
                4,
                Pt(0.5f, 0.15f),
                Pt(0.5f, 0.85f),
            )
        assertTrue(steps.isEmpty())
    }

    @Test
    fun withNothingConfiguredNoGestureEverFires() {
        val steps =
            landAndSwipe(
                SwipeGestureRecognizer(Config(), GestureBindings.decode(GestureBindings.EMPTY_MARKER)),
                3,
                Pt(0.5f, 0.15f),
                Pt(0.5f, 0.85f),
            )
        assertTrue(steps.isEmpty())
    }
}
