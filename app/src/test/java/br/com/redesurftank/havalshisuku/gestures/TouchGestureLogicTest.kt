package br.com.redesurftank.havalshisuku.gestures

import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Action
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Config
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.EvdevTouchParser
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Geometry
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Pt
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.RawFinger
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Step
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.SwipeGestureRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchGestureLogicTest {

    // ---------------------------------------------------------------------------------------
    // Leitura do evdev
    // ---------------------------------------------------------------------------------------

    @Test
    fun typeBParserBuildsFrameFromSlots() {
        val p = EvdevTouchParser()
        val lines = listOf(
            "EV_ABS       ABS_MT_SLOT          00000000",
            "EV_ABS       ABS_MT_TRACKING_ID   0000001a",
            "EV_ABS       ABS_MT_POSITION_X    00000064", // 100
            "EV_ABS       ABS_MT_POSITION_Y    000000c8", // 200
            "EV_ABS       ABS_MT_SLOT          00000001",
            "EV_ABS       ABS_MT_TRACKING_ID   0000001b",
            "EV_ABS       ABS_MT_POSITION_X    000000c8", // 200
            "EV_ABS       ABS_MT_POSITION_Y    000000c8",
            "EV_SYN       SYN_REPORT           00000000",
        )
        var frame: TouchGestureLogic.RawFrame? = null
        lines.forEach { l -> p.feed(l)?.let { frame = it } }
        assertEquals(listOf(RawFinger(100, 200), RawFinger(200, 200)), frame!!.fingers)
    }

    @Test
    fun typeBParserKeepsUnchangedSlotsBetweenReports() {
        // O driver so reporta o que MUDA: no segundo quadro so o slot 1 se move, e o slot 0 tem
        // que continuar existindo com a posicao antiga.
        val p = EvdevTouchParser()
        listOf(
            "EV_ABS       ABS_MT_SLOT          00000000",
            "EV_ABS       ABS_MT_TRACKING_ID   00000001",
            "EV_ABS       ABS_MT_POSITION_X    0000000a",
            "EV_ABS       ABS_MT_POSITION_Y    0000000a",
            "EV_ABS       ABS_MT_SLOT          00000001",
            "EV_ABS       ABS_MT_TRACKING_ID   00000002",
            "EV_ABS       ABS_MT_POSITION_X    00000014",
            "EV_ABS       ABS_MT_POSITION_Y    00000014",
            "EV_SYN       SYN_REPORT           00000000",
        ).forEach { p.feed(it) }

        var frame: TouchGestureLogic.RawFrame? = null
        listOf(
            "EV_ABS       ABS_MT_POSITION_Y    0000001e",
            "EV_SYN       SYN_REPORT           00000000",
        ).forEach { l -> p.feed(l)?.let { frame = it } }

        assertEquals(listOf(RawFinger(10, 10), RawFinger(20, 30)), frame!!.fingers)
    }

    @Test
    fun trackingIdMinusOneRemovesFinger() {
        val p = EvdevTouchParser()
        listOf(
            "EV_ABS       ABS_MT_SLOT          00000000",
            "EV_ABS       ABS_MT_TRACKING_ID   00000001",
            "EV_ABS       ABS_MT_POSITION_X    0000000a",
            "EV_ABS       ABS_MT_POSITION_Y    0000000a",
            "EV_SYN       SYN_REPORT           00000000",
        ).forEach { p.feed(it) }

        var frame: TouchGestureLogic.RawFrame? = null
        listOf(
            "EV_ABS       ABS_MT_TRACKING_ID   ffffffff",
            "EV_SYN       SYN_REPORT           00000000",
        ).forEach { l -> p.feed(l)?.let { frame = it } }

        assertTrue(frame!!.fingers.isEmpty())
    }

    @Test
    fun btnTouchUpClearsEveryFinger() {
        // Rede de seguranca: se a saida por slot falhasse, um dedo fantasma travaria todo gesto
        // seguinte. O painel deste carro manda os dois sinais (confirmado no proprio hardware).
        val p = EvdevTouchParser()
        listOf(
            "EV_KEY       BTN_TOUCH            DOWN",
            "EV_ABS       ABS_MT_TRACKING_ID   00000006",
            "EV_ABS       ABS_MT_POSITION_X    0000000a",
            "EV_ABS       ABS_MT_POSITION_Y    0000000a",
            "EV_SYN       SYN_REPORT           00000000",
        ).forEach { p.feed(it) }

        var frame: TouchGestureLogic.RawFrame? = null
        listOf(
            "EV_KEY       BTN_TOUCH            UP",
            "EV_SYN       SYN_REPORT           00000000",
        ).forEach { l -> p.feed(l)?.let { frame = it } }

        assertTrue(frame!!.fingers.isEmpty())
    }

    @Test
    fun firstFingerWithoutAnExplicitSlotLandsOnSlotZero() {
        // O driver deste carro OMITE o ABS_MT_SLOT do primeiro dedo (slot 0 e o corrente).
        // Capturado no carro: BTN_TOUCH DOWN vem direto seguido do TRACKING_ID.
        val p = EvdevTouchParser()
        var frame: TouchGestureLogic.RawFrame? = null
        listOf(
            "EV_KEY       BTN_TOUCH            DOWN",
            "EV_ABS       ABS_MT_TRACKING_ID   00000003",
            "EV_ABS       ABS_MT_POSITION_X    00000341",
            "EV_ABS       ABS_MT_POSITION_Y    000000bc",
            "EV_SYN       SYN_REPORT           00000000",
        ).forEach { l -> p.feed(l)?.let { frame = it } }
        assertEquals(listOf(RawFinger(0x341, 0xbc)), frame!!.fingers)
    }

    @Test
    fun parserAcceptsLinesWithDevicePrefix() {
        val p = EvdevTouchParser()
        var frame: TouchGestureLogic.RawFrame? = null
        listOf(
            "/dev/input/event2: EV_ABS       ABS_MT_SLOT          00000000",
            "/dev/input/event2: EV_ABS       ABS_MT_TRACKING_ID   00000005",
            "/dev/input/event2: EV_ABS       ABS_MT_POSITION_X    00000032",
            "/dev/input/event2: EV_ABS       ABS_MT_POSITION_Y    00000032",
            "/dev/input/event2: EV_SYN       SYN_REPORT           00000000",
        ).forEach { l -> p.feed(l)?.let { frame = it } }
        assertEquals(listOf(RawFinger(50, 50)), frame!!.fingers)
    }

    @Test
    fun typeAParserSplitsFingersBySynMtReport() {
        val p = EvdevTouchParser()
        var frame: TouchGestureLogic.RawFrame? = null
        listOf(
            "EV_ABS       ABS_MT_POSITION_X    0000000a",
            "EV_ABS       ABS_MT_POSITION_Y    00000014",
            "EV_SYN       SYN_MT_REPORT        00000000",
            "EV_ABS       ABS_MT_POSITION_X    0000001e",
            "EV_ABS       ABS_MT_POSITION_Y    00000028",
            "EV_SYN       SYN_MT_REPORT        00000000",
            "EV_SYN       SYN_REPORT           00000000",
        ).forEach { l -> p.feed(l)?.let { frame = it } }
        assertEquals(listOf(RawFinger(10, 20), RawFinger(30, 40)), frame!!.fingers)
    }

    // ---------------------------------------------------------------------------------------
    // Descoberta do device
    // ---------------------------------------------------------------------------------------

    @Test
    fun picksTheLargestMultitouchDevice() {
        val out = """
            add device 1: /dev/input/event0
              name:     "gpio-keys"
                KEY (0001): 0072  0073
            add device 2: /dev/input/event3
              name:     "secondary-digitizer"
                ABS_MT_POSITION_X    : value 0, min 0, max 479, fuzz 0, flat 0, resolution 0
                ABS_MT_POSITION_Y    : value 0, min 0, max 319, fuzz 0, flat 0, resolution 0
            add device 3: /dev/input/event2
              name:     "main-touch"
                ABS_MT_POSITION_X    : value 0, min 0, max 1919, fuzz 0, flat 0, resolution 0
                ABS_MT_POSITION_Y    : value 0, min 0, max 1079, fuzz 0, flat 0, resolution 0
        """.trimIndent()
        val dev = TouchGestureLogic.parseTouchDevice(out)!!
        assertEquals("/dev/input/event2", dev.path)
        assertEquals(1919, dev.maxX)
        assertEquals(1079, dev.maxY)
    }

    @Test
    fun deviceWithoutMultitouchIsIgnored() {
        val out = """
            add device 1: /dev/input/event0
              name:     "gpio-keys"
                KEY (0001): 0072
        """.trimIndent()
        assertNull(TouchGestureLogic.parseTouchDevice(out))
    }

    // ---------------------------------------------------------------------------------------
    // Geometria
    // ---------------------------------------------------------------------------------------

    @Test
    fun geometryNormalizesAndCanCorrectARotatedPanel() {
        val g = Geometry(maxX = 1000, maxY = 500)
        assertEquals(0.5f, g.normalize(RawFinger(500, 250)).x, 0.001f)
        assertEquals(0.5f, g.normalize(RawFinger(500, 250)).y, 0.001f)

        val swapped = Geometry(maxX = 1000, maxY = 500, swapXY = true, invertY = true)
        val p = swapped.normalize(RawFinger(x = 250, y = 100))
        assertEquals(100f / 500f, p.x, 0.001f)
        assertEquals(1f - 250f / 1000f, p.y, 0.001f)
    }

    @Test
    fun axisSwapIsDetectedWhenTheDigitizerIsMountedSideways() {
        // Painel paisagem + digitalizador paisagem: nada a trocar.
        assertTrue(!TouchGestureLogic.needsAxisSwap(1919, 1079, 1920, 1080))
        // Digitalizador reportando retrato num display paisagem: eixos girados.
        assertTrue(TouchGestureLogic.needsAxisSwap(1079, 1919, 1920, 1080))
        // Sem dados confiaveis, nao inventa.
        assertTrue(!TouchGestureLogic.needsAxisSwap(0, 0, 1920, 1080))
    }

    // ---------------------------------------------------------------------------------------
    // Reconhecimento do gesto
    // ---------------------------------------------------------------------------------------

    /** Arrasta [n] dedos de [from] ate [to] em passos pequenos, devolvendo tudo que saiu. */
    private fun swipe(
        r: SwipeGestureRecognizer,
        n: Int,
        from: Pt,
        to: Pt,
        frames: Int = 40,
    ): List<Step> {
        val out = mutableListOf<Step>()
        out += r.onFrame(fingersAt(from, n))
        for (i in 1..frames) {
            val t = i.toFloat() / frames
            val p = Pt(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
            out += r.onFrame(fingersAt(p, n))
        }
        return out
    }

    /** Espalha [n] dedos em volta do centro, sem sair da zona. */
    private fun fingersAt(center: Pt, n: Int): List<Pt> =
        (0 until n).map { Pt(center.x + (it - (n - 1) / 2f) * 0.01f, center.y) }

    @Test
    fun threeFingersDownInTheCenterLowersVolume() {
        val r = SwipeGestureRecognizer()
        val steps = swipe(r, 3, Pt(0.5f, 0.2f), Pt(0.5f, 0.8f))
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.all { it.action == Action.VOLUME })
        assertTrue(steps.all { it.delta == -1 })
    }

    @Test
    fun threeFingersUpInTheCenterRaisesVolume() {
        val r = SwipeGestureRecognizer()
        val steps = swipe(r, 3, Pt(0.5f, 0.8f), Pt(0.5f, 0.2f))
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.all { it.action == Action.VOLUME && it.delta == 1 })
    }

    @Test
    fun leftThirdControlsDriverTemperatureAndRightThirdThePassenger() {
        val left = swipe(SwipeGestureRecognizer(), 3, Pt(0.15f, 0.8f), Pt(0.15f, 0.2f))
        assertTrue(left.isNotEmpty())
        assertTrue(left.all { it.action == Action.DRIVER_TEMP && it.delta == 1 })

        val right = swipe(SwipeGestureRecognizer(), 3, Pt(0.85f, 0.2f), Pt(0.85f, 0.8f))
        assertTrue(right.isNotEmpty())
        assertTrue(right.all { it.action == Action.PASSENGER_TEMP && it.delta == -1 })
    }

    @Test
    fun horizontalIsFanAnywhereOnTheScreen() {
        val toTheRight = swipe(SwipeGestureRecognizer(), 3, Pt(0.2f, 0.3f), Pt(0.9f, 0.3f))
        assertTrue(toTheRight.isNotEmpty())
        assertTrue(toTheRight.all { it.action == Action.FAN && it.delta == 1 })

        val toTheLeft = swipe(SwipeGestureRecognizer(), 3, Pt(0.9f, 0.9f), Pt(0.2f, 0.9f))
        assertTrue(toTheLeft.isNotEmpty())
        assertTrue(toTheLeft.all { it.action == Action.FAN && it.delta == -1 })
    }

    @Test
    fun twoFingersDoNothing() {
        val steps = swipe(SwipeGestureRecognizer(), 2, Pt(0.5f, 0.2f), Pt(0.5f, 0.9f))
        assertTrue(steps.isEmpty())
    }

    @Test
    fun aFourthFingerCancelsTheGesture() {
        // Mao apoiada na tela nao pode virar comando.
        val steps = swipe(SwipeGestureRecognizer(), 4, Pt(0.5f, 0.2f), Pt(0.5f, 0.9f))
        assertTrue(steps.isEmpty())
    }

    @Test
    fun diagonalDragIsIgnored() {
        // Nem vira volume nem vira ventilacao: o eixo so e aceito quando domina o outro.
        val steps = swipe(SwipeGestureRecognizer(), 3, Pt(0.4f, 0.2f), Pt(0.9f, 0.7f))
        assertTrue(steps.isEmpty())
    }

    @Test
    fun zoneIsLockedWhenTheGestureStartsSoDriftDoesNotSwitchTheAction() {
        // Comeca no terco esquerdo (motorista) e deriva ate o centro; segue motorista.
        val steps = swipe(SwipeGestureRecognizer(), 3, Pt(0.10f, 0.85f), Pt(0.45f, 0.15f))
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.all { it.action == Action.DRIVER_TEMP })
    }

    @Test
    fun longerDragProducesMoreSteps() {
        val short = swipe(SwipeGestureRecognizer(), 3, Pt(0.5f, 0.5f), Pt(0.5f, 0.7f))
        val long = swipe(SwipeGestureRecognizer(), 3, Pt(0.5f, 0.1f), Pt(0.5f, 0.95f))
        assertTrue(long.size > short.size)
    }

    @Test
    fun liftingAFingerEndsTheGestureUntilTheScreenIsClear() {
        val r = SwipeGestureRecognizer()
        swipe(r, 3, Pt(0.5f, 0.2f), Pt(0.5f, 0.5f))
        // Um dedo sai: acabou. Voltar pra tres NAO recomeca — precisa limpar a tela antes, senao
        // um dedo que perde contato no meio do arraste dispararia um gesto novo.
        r.onFrame(fingersAt(Pt(0.5f, 0.5f), 2))
        val after = swipe(r, 3, Pt(0.5f, 0.5f), Pt(0.5f, 0.9f))
        assertTrue(after.isEmpty())

        r.onFrame(emptyList())
        val fresh = swipe(r, 3, Pt(0.5f, 0.2f), Pt(0.5f, 0.8f))
        assertTrue(fresh.isNotEmpty())
    }

    @Test
    fun onAWideScreenTheAxisIsJudgedInRealDistanceNotInEachAxisOwnFraction() {
        // 1920x720: sem corrigir a proporcao, andar a MESMA distancia fisica nos dois eixos daria
        // 0,3 na largura contra 0,8 na altura, e todo arraste pareceria vertical.
        val wide = Config(aspect = 1920f / 720f)
        val steps =
            swipe(SwipeGestureRecognizer(wide), 3, Pt(0.2f, 0.30f), Pt(0.5f, 0.60f))
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.all { it.action == Action.FAN })
    }

    @Test
    fun eachAdjustmentHasItsOwnStepSoTheRangesFeelAlike() {
        // A ventilacao tem 8 posicoes e o volume 41: com um passo unico, um dos dois sairia
        // inutilizavel. O mesmo arraste tem que render MENOS passos de ventilacao que de volume.
        val volume = swipe(SwipeGestureRecognizer(), 3, Pt(0.5f, 0.15f), Pt(0.5f, 0.85f))
        val fan = swipe(SwipeGestureRecognizer(), 3, Pt(0.15f, 0.5f), Pt(0.85f, 0.5f))
        assertTrue(volume.all { it.action == Action.VOLUME })
        assertTrue(fan.all { it.action == Action.FAN })
        assertTrue("volume=${volume.size} fan=${fan.size}", volume.size > fan.size)
    }

    @Test
    fun aVeryFastDragIsCappedPerFrame() {
        val r = SwipeGestureRecognizer(Config(maxStepsPerFrame = 2))
        r.onFrame(fingersAt(Pt(0.5f, 0.05f), 3))
        r.onFrame(fingersAt(Pt(0.5f, 0.15f), 3)) // decide o eixo
        val jump = r.onFrame(fingersAt(Pt(0.5f, 0.95f), 3))
        assertEquals(2, jump.size)
    }
}
