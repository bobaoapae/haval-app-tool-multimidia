package br.com.redesurftank.havalshisuku.gestures

import kotlin.math.abs

/**
 * Logica PURA dos gestos de tela (testavel na JVM, sem Android).
 *
 * Divide-se em duas partes independentes:
 *
 *  1. [EvdevTouchParser] — le as linhas cruas do `getevent -l` e devolve, a cada SYN_REPORT, onde
 *     estao os dedos naquele instante. Isso e o que permite enxergar o toque SOBRE qualquer app:
 *     lemos o mesmo `/dev/input/eventX` que o Android le, sem uma janela nossa no caminho, entao o
 *     app de baixo continua recebendo o toque normalmente.
 *
 *  2. [SwipeGestureRecognizer] — recebe os quadros ja normalizados e decide se virou gesto, com
 *     quantos dedos, em qual eixo, em qual zona da tela, e quantos "passos" andou. O que cada
 *     combinacao FAZ vem de [GestureBindings], configurado pelo dono.
 *
 * Por que o padrao sao TRES dedos: com dois, um arraste na metade esquerda com o Waze projetado
 * arrastaria o mapa E mexeria na temperatura ao mesmo tempo — nao roubamos o toque de ninguem, logo
 * o app de baixo tambem reage. Tres dedos praticamente nenhum app usa, entao nao ha disputa. Quatro
 * existe como opcao, com o aviso correspondente na tela; dois ficou de fora justamente pela disputa.
 */
object TouchGestureLogic {

    /** Ponto normalizado: 0..1 na largura e na altura, origem no canto superior esquerdo. */
    data class Pt(val x: Float, val y: Float)

    /** Dedo com a coordenada CRUA do driver (antes de normalizar). */
    data class RawFinger(val x: Int, val y: Int)

    /** Onde estao os dedos no instante de um SYN_REPORT. */
    data class RawFrame(val fingers: List<RawFinger>)

    enum class Zone { LEFT, CENTER, RIGHT }

    enum class Axis { VERTICAL, HORIZONTAL }

    enum class Action { VOLUME, DRIVER_TEMP, PASSENGER_TEMP, FAN }

    /** Um passo de ajuste: +1 sobe, -1 desce. Um arraste longo emite varios. */
    data class Step(val action: Action, val delta: Int)

    /**
     * Ajustes do reconhecimento.
     *
     * ## Por que as distancias sao em "alturas de tela", e nao em fracao de cada eixo
     *
     * A tela central e 1920x720. Normalizar cada eixo por conta propria deforma o espaco: 100 px na
     * horizontal viram 0,052 e os mesmos 100 px na vertical viram 0,139. Com isso, um arraste
     * fisicamente diagonal parece vertical na conta, e um passo horizontal ficaria quase 3x mais
     * "longo" que o vertical. Entao a comparacao de eixos e a medida dos passos usam a MESMA unidade
     * — a altura da tela —, multiplicando o deslocamento horizontal pela proporcao [aspect]. So a
     * ZONA continua em fracao da largura, que e o que ela significa.
     */
    data class Config(
        /** Fronteiras das zonas verticais, em fracao da largura. */
        val leftEdge: Float = 0.33f,
        val rightEdge: Float = 0.67f,
        /** largura / altura da tela. 1 = espaco isotropico (usado nos testes). */
        val aspect: Float = 1f,
        /** Quanto o centroide precisa andar (em alturas de tela) pra gente decidir o eixo. */
        val axisThreshold: Float = 0.022f,
        /** Quanto o eixo vencedor precisa dominar o outro pra nao ser um arraste diagonal. */
        val axisDominance: Float = 1.5f,
        /**
         * Distancia entre um passo e o proximo, POR ACAO, em alturas de tela.
         *
         * Cada ajuste tem uma faixa diferente, e uma distancia unica deixaria uns arrastados e
         * outros nervosos: o volume vai de 0 a 40, a temperatura de 16 a 32 em degraus de meio grau
         * e a ventilacao tem so 8 posicoes.
         *
         * A primeira calibracao mirava "varrer a faixa inteira num arraste de tela cheia" e ficou
         * NERVOSA no carro — na temperatura, um arraste natural de uns 300px passava de 7 graus.
         * Os valores de agora sao mais grossos: nesses mesmos 300px, ~2,8 graus, ~14 de volume e
         * ~2 posicoes de ventilacao. Ajustaveis por preferencia (em milesimos de altura de tela),
         * pra afinar no carro sem build novo.
         */
        val volumeStep: Float = 0.030f,
        val temperatureStep: Float = 0.075f,
        val fanStep: Float = 0.22f,
        /** Teto de passos por quadro: um arraste MUITO rapido nao pode saltar a faixa inteira. */
        val maxStepsPerFrame: Int = 6,
    ) {
        fun stepFor(action: Action): Float = when (action) {
            Action.VOLUME -> volumeStep
            Action.DRIVER_TEMP, Action.PASSENGER_TEMP -> temperatureStep
            Action.FAN -> fanStep
        }
    }

    /**
     * Como transformar a coordenada crua do driver na coordenada da TELA.
     *
     * O eixo do digitalizador nem sempre bate com o do display (ha paineis montados girados ou
     * espelhados). Os tres ajustes existem pra corrigir isso sem recompilar: da pra conferir no
     * carro arrastando e vendo se o gesto responde no sentido certo.
     */
    data class Geometry(
        val maxX: Int,
        val maxY: Int,
        val swapXY: Boolean = false,
        val invertX: Boolean = false,
        val invertY: Boolean = false,
    ) {
        fun normalize(f: RawFinger): Pt {
            val rawX = if (swapXY) f.y else f.x
            val rawY = if (swapXY) f.x else f.y
            val spanX = (if (swapXY) maxY else maxX).coerceAtLeast(1)
            val spanY = (if (swapXY) maxX else maxY).coerceAtLeast(1)
            var nx = rawX.toFloat() / spanX
            var ny = rawY.toFloat() / spanY
            if (invertX) nx = 1f - nx
            if (invertY) ny = 1f - ny
            return Pt(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
        }
    }

    // -----------------------------------------------------------------------------------------
    // 1. Leitura do evdev
    // -----------------------------------------------------------------------------------------

    /**
     * Monta os quadros de toque a partir das linhas do `getevent -l`.
     *
     * Suporta os DOIS protocolos multitoque do Linux:
     *  - Tipo B (o normal hoje): cada dedo mora num "slot"; `ABS_MT_TRACKING_ID` com valor -1
     *    (0xffffffff) e o dedo saindo. So o que MUDA e reportado, entao o estado dos outros slots
     *    precisa sobreviver entre um SYN_REPORT e o proximo.
     *  - Tipo A (paineis antigos): sem slot; os dedos vem em sequencia separados por SYN_MT_REPORT.
     *
     * O tipo e detectado pelo que aparece na pratica, nao por configuracao.
     */
    class EvdevTouchParser {
        private val slots = LinkedHashMap<Int, RawFinger>()
        private var currentSlot = 0
        private var typeB = false

        // Tipo A: acumula os dedos do quadro corrente.
        private val typeAFingers = mutableListOf<RawFinger>()
        private var typeAPendingX: Int? = null
        private var typeAPendingY: Int? = null

        /** Devolve um quadro quando a linha fecha um SYN_REPORT; caso contrario, null. */
        fun feed(rawLine: String): RawFrame? {
            // Caminho quente: um arraste de tres dedos passa perto de 1500 linhas por segundo por
            // aqui. Descartar de cara o que nao interessa (pressao, area, BTN_TOUCH) evita pagar o
            // split por regex em cada uma delas.
            if (!rawLine.contains("ABS_MT_") &&
                !rawLine.contains("SYN_") &&
                !rawLine.contains("BTN_TOUCH")
            ) {
                return null
            }
            // Sem argumento de device o getevent prefixa "/dev/input/eventN:"; com argumento, nao.
            val line =
                if (rawLine.startsWith("/dev/input/")) rawLine.substringAfter(": ") else rawLine
            val parts = line.trim().split(WHITESPACE)
            if (parts.size < 3) return null
            val code = parts[1]
            val value = parts[2]

            when (code) {
                "ABS_MT_SLOT" -> {
                    typeB = true
                    currentSlot = parseHex(value) ?: currentSlot
                }
                "ABS_MT_TRACKING_ID" -> {
                    typeB = true
                    val id = parseHex(value) ?: return null
                    // 0xffffffff (-1) = dedo saiu.
                    if (id == -1) slots.remove(currentSlot)
                    else slots.getOrPut(currentSlot) { RawFinger(0, 0) }
                }
                "ABS_MT_POSITION_X" -> {
                    val v = parseHex(value) ?: return null
                    if (typeB) {
                        val f = slots[currentSlot] ?: RawFinger(0, 0)
                        slots[currentSlot] = f.copy(x = v)
                    } else typeAPendingX = v
                }
                "ABS_MT_POSITION_Y" -> {
                    val v = parseHex(value) ?: return null
                    if (typeB) {
                        val f = slots[currentSlot] ?: RawFinger(0, 0)
                        slots[currentSlot] = f.copy(y = v)
                    } else typeAPendingY = v
                }
                "BTN_TOUCH" -> {
                    // Rede de seguranca contra dedo fantasma. O caminho normal de saida e o
                    // TRACKING_ID = -1 por slot; se um driver deixar de mandar algum, o slot ficaria
                    // ocupado PRA SEMPRE e todo gesto seguinte contaria um dedo a mais — ou seja, a
                    // funcao inteira morreria ate reiniciar o app. BTN_TOUCH UP significa "nao ha
                    // mais nenhum dedo na tela", entao limpa tudo sem depender de ninguem.
                    if (value.equals("UP", ignoreCase = true)) slots.clear()
                }
                "SYN_MT_REPORT" -> {
                    val x = typeAPendingX
                    val y = typeAPendingY
                    if (x != null && y != null) typeAFingers.add(RawFinger(x, y))
                    typeAPendingX = null
                    typeAPendingY = null
                }
                "SYN_REPORT" -> {
                    return if (typeB) {
                        RawFrame(slots.values.toList())
                    } else {
                        val frame = RawFrame(typeAFingers.toList())
                        typeAFingers.clear()
                        typeAPendingX = null
                        typeAPendingY = null
                        frame
                    }
                }
            }
            return null
        }

        /** Esquece o estado — usado quando o processo do getevent cai e volta. */
        fun reset() {
            slots.clear()
            typeAFingers.clear()
            typeAPendingX = null
            typeAPendingY = null
            currentSlot = 0
        }

        private companion object {
            val WHITESPACE = Regex("\\s+")

            /** getevent imprime valores em hex de 8 digitos; 0xffffffff e o -1 do tracking id. */
            fun parseHex(v: String): Int? = v.toLongOrNull(16)?.toInt()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 2. Reconhecimento do gesto
    // -----------------------------------------------------------------------------------------

    /**
     * Detecta arrastes de tres dedos e os converte em passos de ajuste.
     *
     * Regras que valem a pena saber:
     *  - a ZONA e decidida no momento em que o terceiro dedo encosta, pelo centroide inicial. Assim
     *    o gesto nao "troca de funcao" no meio se a mao derivar pro lado.
     *  - o EIXO tambem so e decidido uma vez, e so quando um dos dois dominar o outro — arraste
     *    diagonal nao vira nada, em vez de virar as duas coisas.
     *  - um quarto dedo CANCELA. Mao apoiada na tela nao pode virar comando.
     *  - depois de um gesto (ou de um cancelamento) e preciso TIRAR todos os dedos pra comecar
     *    outro: sem isso, levantar e reencostar um dedo no meio do arraste dispararia de novo.
     */
    class SwipeGestureRecognizer(
        private val config: Config = Config(),
        private val bindings: GestureBindings = GestureBindings.DEFAULT,
    ) {

        private enum class State { IDLE, ARMED, CANCELLED }

        private val activeCounts = bindings.activeFingerCounts()
        private val maxFingers = bindings.maxFingers()

        private var state = State.IDLE
        private var zone = Zone.CENTER
        private var axis: Axis? = null
        private var armedFingers = 0
        private var emittedAnyStep = false
        private var start = Pt(0f, 0f)
        private var lastStep = Pt(0f, 0f)

        /** Eixo escolhido no gesto corrente (null enquanto ainda nao deu pra decidir). */
        val currentAxis: Axis?
            get() = axis

        fun onFrame(fingers: List<Pt>): List<Step> {
            val n = fingers.size

            if (n == 0) {
                state = State.IDLE
                axis = null
                return emptyList()
            }

            if (activeCounts.isEmpty()) return emptyList()

            if (n > maxFingers) {
                // Mao apoiada / dedo a mais do que qualquer gesto configurado: cancela e so libera
                // quando a tela ficar limpa.
                state = State.CANCELLED
                axis = null
                return emptyList()
            }

            if (state == State.CANCELLED) return emptyList()

            val centroid = centroidOf(fingers)

            if (n !in activeCounts) {
                // Ou os dedos ainda estao encostando, ou um saiu no meio do gesto.
                if (state == State.ARMED) state = State.CANCELLED
                return emptyList()
            }

            if (state == State.IDLE) {
                arm(n, centroid)
                return emptyList()
            }

            if (n != armedFingers) {
                // Com mais de uma contagem configurada, encostar tres dedos passa por dois. Enquanto
                // NENHUM passo saiu, isso e so a mao pousando: re-arma na contagem nova em vez de
                // disparar a acao errada. Depois do primeiro passo, mudar de contagem cancela.
                if (emittedAnyStep) {
                    state = State.CANCELLED
                    return emptyList()
                }
                arm(n, centroid)
                return emptyList()
            }

            val dxTotal = centroid.x - start.x
            val dyTotal = centroid.y - start.y

            if (axis == null) {
                // Ambos em alturas de tela, senao a tela larga faz todo arraste parecer vertical.
                val ax = abs(dxTotal) * config.aspect
                val ay = abs(dyTotal)
                axis = when {
                    ay >= config.axisThreshold && ay >= ax * config.axisDominance -> Axis.VERTICAL
                    ax >= config.axisThreshold && ax >= ay * config.axisDominance -> Axis.HORIZONTAL
                    else -> null
                }
                if (axis == null) return emptyList()
                // O limiar ja "gastou" o caminho ate aqui: o primeiro passo conta a partir do
                // ponto onde o eixo ficou claro, senao o gesto nasce ja com um passo de brinde.
                lastStep = centroid
                return emptyList()
            }

            val action = bindings.actionFor(armedFingers, axis!!, zone)
            if (action == null) {
                // Combinacao sem acao configurada: o gesto e inerte ate a mao sair da tela.
                state = State.CANCELLED
                return emptyList()
            }
            val stepDistance = config.stepFor(action)
            // Deslocamento desde o ultimo passo, sempre em alturas de tela.
            val travelled = when (axis) {
                Axis.VERTICAL -> centroid.y - lastStep.y
                else -> (centroid.x - lastStep.x) * config.aspect
            }
            val whole = (abs(travelled) / stepDistance).toInt()
            if (whole <= 0) return emptyList()

            val count = whole.coerceAtMost(config.maxStepsPerFrame)
            // Consome a distancia INTEIRA mesmo quando o teto corta os passos: o excedente de um
            // arraste violento e pra ser descartado, nao pra ficar guardado e sair depois.
            val consumed = whole * stepDistance * (if (travelled < 0) -1f else 1f)
            lastStep = when (axis) {
                Axis.VERTICAL -> lastStep.copy(y = lastStep.y + consumed)
                else -> lastStep.copy(x = lastStep.x + consumed / config.aspect)
            }

            // Y cresce pra BAIXO na tela: arrastar pra baixo DIMINUI, pra cima aumenta.
            // Na horizontal, pra direita aumenta.
            val forward = travelled > 0
            val delta = when (axis) {
                Axis.VERTICAL -> if (forward) -1 else 1
                else -> if (forward) 1 else -1
            }
            emittedAnyStep = true
            return List(count) { Step(action, delta) }
        }

        fun reset() {
            state = State.IDLE
            axis = null
            emittedAnyStep = false
        }

        private fun arm(fingers: Int, centroid: Pt) {
            state = State.ARMED
            armedFingers = fingers
            axis = null
            emittedAnyStep = false
            start = centroid
            lastStep = centroid
            zone = zoneOf(centroid.x)
        }

        private fun zoneOf(x: Float): Zone = when {
            x < config.leftEdge -> Zone.LEFT
            x > config.rightEdge -> Zone.RIGHT
            else -> Zone.CENTER
        }

        private fun centroidOf(fingers: List<Pt>): Pt {
            var sx = 0f
            var sy = 0f
            for (f in fingers) {
                sx += f.x
                sy += f.y
            }
            return Pt(sx / fingers.size, sy / fingers.size)
        }
    }

    // -----------------------------------------------------------------------------------------
    // 3. Descoberta do device da tela
    // -----------------------------------------------------------------------------------------

    data class TouchDevice(val path: String, val maxX: Int, val maxY: Int)

    /**
     * Acha a tela na saida do `getevent -pl`.
     *
     * Nao da pra cravar `/dev/input/event3` no codigo: a numeracao muda entre firmwares e ate entre
     * boots. Procuramos o device que reporta `ABS_MT_POSITION_X` e, havendo mais de um (algumas
     * unidades expoem um digitalizador secundario), fica o de maior resolucao — que e o painel
     * principal.
     */
    fun parseTouchDevice(geteventPl: String): TouchDevice? {
        var path: String? = null
        var maxX = 0
        var maxY = 0
        var best: TouchDevice? = null

        fun flush() {
            val p = path ?: return
            if (maxX > 0 && maxY > 0) {
                val candidate = TouchDevice(p, maxX, maxY)
                val current = best
                if (current == null || candidate.maxX.toLong() * candidate.maxY >
                    current.maxX.toLong() * current.maxY
                ) {
                    best = candidate
                }
            }
        }

        for (raw in geteventPl.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("add device")) {
                flush()
                path = line.substringAfter(": ", "").trim().ifEmpty { null }
                maxX = 0
                maxY = 0
                continue
            }
            if (line.startsWith("ABS_MT_POSITION_X")) maxX = maxOf(maxX, parseMax(line))
            if (line.startsWith("ABS_MT_POSITION_Y")) maxY = maxOf(maxY, parseMax(line))
        }
        flush()
        return best
    }

    /**
     * O digitalizador esta girado 90 graus em relacao ao display?
     *
     * Ha paineis montados de lado, em que `ABS_MT_POSITION_X` corre na ALTURA da tela. Comparar as
     * proporcoes resolve o caso comum sem precisar de ninguem no carro: se um lado e paisagem e o
     * outro e retrato, os eixos estao trocados. Espelhamento (crescer pro lado errado) isso NAO
     * detecta — pra isso existem os ajustes de inversao, que se conferem com um gesto.
     */
    fun needsAxisSwap(deviceMaxX: Int, deviceMaxY: Int, displayW: Int, displayH: Int): Boolean {
        if (deviceMaxX <= 0 || deviceMaxY <= 0 || displayW <= 0 || displayH <= 0) return false
        val deviceLandscape = deviceMaxX >= deviceMaxY
        val displayLandscape = displayW >= displayH
        return deviceLandscape != displayLandscape
    }

    /** "ABS_MT_POSITION_X : value 0, min 0, max 1919, fuzz 0, flat 0, resolution 0" -> 1919 */
    private fun parseMax(line: String): Int {
        val idx = line.indexOf("max ")
        if (idx < 0) return 0
        val tail = line.substring(idx + 4).trimStart()
        val end = tail.indexOfFirst { !it.isDigit() && it != '-' }
        val number = if (end < 0) tail else tail.substring(0, end)
        return number.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }
}
