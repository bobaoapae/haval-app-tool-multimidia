package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Esta migração decide o que acontece com a configuração de quem JÁ USA a função. Errar aqui é
 * tirar um comportamento sem avisar, ou ligar um que nunca foi pedido.
 */
class FoldMirrorTriggerMigrationTest {

    @Test
    fun aTurnedOnTriggerMovesToTheNewOne() {
        val plan =
            FoldMirrorTriggerMigration.plan(
                mapOf("closeWindowOnFoldMirror" to true, "disableBluetoothOnFoldMirror" to true)
            )
        assertEquals(listOf("closeWindowOnLock", "disableBluetoothOnLock"), plan.enable)
        assertEquals(
            listOf("closeWindowOnFoldMirror", "disableBluetoothOnFoldMirror"),
            plan.remove,
        )
    }

    @Test
    fun aTurnedOffTriggerIsErasedWithoutTurningAnythingOn() {
        // Quem não usava a função não pode ganhá-la de presente — ainda mais uma que mexe em vidro.
        val plan = FoldMirrorTriggerMigration.plan(mapOf("closeSunroofOnFoldMirror" to false))
        assertTrue(plan.enable.isEmpty())
        assertEquals(listOf("closeSunroofOnFoldMirror"), plan.remove)
    }

    @Test
    fun aKeyThatWasNeverSetIsLeftAlone() {
        assertTrue(FoldMirrorTriggerMigration.plan(emptyMap()).isEmpty)
    }

    @Test
    fun runningTwiceDoesNothingTheSecondTime() {
        // O plano apaga as antigas, então a segunda passada encontra o mapa vazio. É o que dispensa
        // uma flag de "já migrei" — mais um estado para ficar errado.
        val first = FoldMirrorTriggerMigration.plan(mapOf("closeWindowOnFoldMirror" to true))
        assertTrue(first.enable.isNotEmpty())
        val afterRemoval = emptyMap<String, Boolean>()
        assertTrue(FoldMirrorTriggerMigration.plan(afterRemoval).isEmpty)
    }

    @Test
    fun everyOldTriggerHasSomewhereToGo() {
        // Se um gatilho antigo ficasse sem destino, o comportamento sumiria na atualização.
        assertEquals(4, FoldMirrorTriggerMigration.MAPPING.size)
        assertTrue(FoldMirrorTriggerMigration.MAPPING.values.all { it.endsWith("OnLock") })
        assertTrue(FoldMirrorTriggerMigration.MAPPING.keys.all { it.endsWith("OnFoldMirror") })
    }
}
