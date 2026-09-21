package br.com.redesurftank.havalshisuku.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O formato de `sys.settings.audio.media_volume_range` não está documentado, então a leitura tem que
 * aguentar as variações plausíveis em vez de assumir uma — e devolver null quando não dá pra
 * confiar, pra cair na faixa padrão do carro (0..40) em vez de num máximo inventado.
 */
class CarVolumeRangeTest {

    @Test
    fun readsTheMaximumInTheUsualShapes() {
        assertEquals(40, CarVolumeRange.parseMax("40"))
        assertEquals(40, CarVolumeRange.parseMax("0,40"))
        assertEquals(40, CarVolumeRange.parseMax("(0,40)"))
        assertEquals(40, CarVolumeRange.parseMax("0-40"))
        assertEquals(40, CarVolumeRange.parseMax("[0, 40]"))
        assertEquals(40, CarVolumeRange.parseMax(" min 0 max 40 "))
    }

    @Test
    fun refusesWhatItCannotTrust() {
        assertNull(CarVolumeRange.parseMax(null))
        assertNull(CarVolumeRange.parseMax(""))
        assertNull(CarVolumeRange.parseMax("   "))
        assertNull(CarVolumeRange.parseMax("desconhecido"))
        // Um máximo de zero deixaria o volume travado; melhor cair no padrão.
        assertNull(CarVolumeRange.parseMax("0"))
    }
}
