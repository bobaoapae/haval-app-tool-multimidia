package br.com.redesurftank.havalshisuku.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatTranslationLayerTest {
    @Test
    fun acceptsCurrentAndOlderBridgeRequirements() {
        assertTrue(CompatTranslationLayer.isBridgeVersionSupported(null))
        assertTrue(CompatTranslationLayer.isBridgeVersionSupported("0.9.0"))
        assertTrue(CompatTranslationLayer.isBridgeVersionSupported("1.0"))
        assertTrue(CompatTranslationLayer.isBridgeVersionSupported("v1.0.0"))
    }

    @Test
    fun rejectsFutureOrMalformedBridgeRequirements() {
        assertFalse(CompatTranslationLayer.isBridgeVersionSupported("1.0.1"))
        assertFalse(CompatTranslationLayer.isBridgeVersionSupported("2.0.0"))
        assertFalse(CompatTranslationLayer.isBridgeVersionSupported("future"))
    }
}
