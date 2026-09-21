package br.com.redesurftank.havalshisuku.gestures

import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Action
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Axis
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Zone

/**
 * Qual gesto faz o que — configuravel pelo dono, em vez de fixo no codigo.
 *
 * Um vinculo e a combinacao **numero de dedos + eixo + zona**. A tela sempre trabalha em TERCOS,
 * porque com a mesma acao podendo se repetir isso ja e superconjunto de tudo: a mesma acao nas tres
 * zonas e o que significa "tela toda", e em duas delas, "metade". Nao existe uma escolha separada de
 * "layout" — ela seria estado a mais pelo mesmo poder.
 *
 * Puro de proposito (nada de Android): a leitura de configuracao mal formada e exatamente o tipo de
 * coisa que precisa de teste, e um formato que so roda no aparelho nao se testa.
 */
data class GestureBindings(private val slots: Map<Slot, Action>) {

    data class Slot(val fingers: Int, val axis: Axis, val zone: Zone)

    fun actionFor(fingers: Int, axis: Axis, zone: Zone): Action? =
        slots[Slot(fingers, axis, zone)]

    /** Contagens de dedos que tem ao menos um vinculo — as unicas que armam gesto. */
    fun activeFingerCounts(): Set<Int> = slots.keys.map { it.fingers }.toSet()

    /** Acima disso e mao apoiada, nao gesto. 0 quando nada esta configurado. */
    fun maxFingers(): Int = activeFingerCounts().maxOrNull() ?: 0

    fun isEmpty(): Boolean = slots.isEmpty()

    /** Devolve uma copia com um vinculo trocado; `action` nulo remove. */
    fun with(fingers: Int, axis: Axis, zone: Zone, action: Action?): GestureBindings {
        val slot = Slot(fingers, axis, zone)
        val next = slots.toMutableMap()
        if (action == null) next.remove(slot) else next[slot] = action
        return GestureBindings(next)
    }

    /** Preenche as tres zonas de uma linha com a mesma acao — o atalho "tela toda". */
    fun withWholeRow(fingers: Int, axis: Axis, action: Action?): GestureBindings {
        var out = this
        for (zone in Zone.entries) out = out.with(fingers, axis, zone, action)
        return out
    }

    /**
     * Formato: `dedos:eixo:zona=acao`, separados por `;`. Ex.: `3:V:L=driver_temp;3:V:C=volume`.
     *
     * Texto simples em vez de JSON porque isso e lido e escrito tambem por quem estiver num shell no
     * carro, e porque a decodificacao precisa rodar em teste de unidade — o `org.json` do Android e
     * um esqueleto vazio na JVM.
     *
     * [EMPTY_MARKER] existe pra distinguir "o dono desligou tudo" de "nunca foi configurado": sem
     * ele, apagar o ultimo vinculo faria os padroes ressuscitarem no proximo boot.
     */
    fun encode(): String {
        if (slots.isEmpty()) return EMPTY_MARKER
        return slots.entries
            .sortedWith(
                compareBy({ it.key.fingers }, { it.key.axis.ordinal }, { it.key.zone.ordinal })
            )
            .joinToString(";") { (slot, action) ->
                "${slot.fingers}:${axisCode(slot.axis)}:${zoneCode(slot.zone)}=${action.name.lowercase()}"
            }
    }

    companion object {
        const val EMPTY_MARKER = "-"

        /**
         * Contagens que a tela oferece.
         *
         * DOIS ficou de fora por decisao do dono. Como nao roubamos o toque, dois dedos disputam
         * com o app que esta na frente — um arraste na metade esquerda com o mapa na tela mexeria
         * na temperatura E arrastaria o mapa. Entradas de dois dedos numa configuracao antiga sao
         * descartadas na leitura.
         */
        val FINGER_COUNTS = listOf(3, 4)

        /** O que sempre valeu antes de existir configuracao: tres dedos, clima e volume. */
        val DEFAULT: GestureBindings =
            GestureBindings(
                buildMap {
                    put(Slot(3, Axis.VERTICAL, Zone.LEFT), Action.DRIVER_TEMP)
                    put(Slot(3, Axis.VERTICAL, Zone.CENTER), Action.VOLUME)
                    put(Slot(3, Axis.VERTICAL, Zone.RIGHT), Action.PASSENGER_TEMP)
                    for (zone in Zone.entries) put(Slot(3, Axis.HORIZONTAL, zone), Action.FAN)
                }
            )

        /**
         * Le a configuracao gravada. Vazio/ausente cai no padrao; entradas estragadas sao
         * IGNORADAS uma a uma, em vez de derrubar o conjunto — configuracao meio lida e melhor que
         * gesto nenhum, e o dono conserta a que sumiu.
         */
        fun decode(raw: String?): GestureBindings {
            if (raw.isNullOrBlank()) return DEFAULT
            if (raw.trim() == EMPTY_MARKER) return GestureBindings(emptyMap())
            val out = mutableMapOf<Slot, Action>()
            for (entry in raw.split(";")) {
                val piece = entry.trim()
                if (piece.isEmpty()) continue
                val eq = piece.indexOf('=')
                if (eq <= 0) continue
                val parts = piece.substring(0, eq).split(":")
                if (parts.size != 3) continue
                val fingers = parts[0].trim().toIntOrNull() ?: continue
                if (fingers !in FINGER_COUNTS) continue
                val axis = parseAxis(parts[1].trim()) ?: continue
                val zone = parseZone(parts[2].trim()) ?: continue
                val action = parseAction(piece.substring(eq + 1).trim()) ?: continue
                out[Slot(fingers, axis, zone)] = action
            }
            return if (out.isEmpty()) DEFAULT else GestureBindings(out)
        }

        private fun axisCode(axis: Axis) = if (axis == Axis.VERTICAL) "V" else "H"

        private fun zoneCode(zone: Zone) =
            when (zone) {
                Zone.LEFT -> "L"
                Zone.CENTER -> "C"
                Zone.RIGHT -> "R"
            }

        private fun parseAxis(code: String): Axis? =
            when (code.uppercase()) {
                "V" -> Axis.VERTICAL
                "H" -> Axis.HORIZONTAL
                else -> null
            }

        private fun parseZone(code: String): Zone? =
            when (code.uppercase()) {
                "L" -> Zone.LEFT
                "C" -> Zone.CENTER
                "R" -> Zone.RIGHT
                else -> null
            }

        private fun parseAction(name: String): Action? =
            Action.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
