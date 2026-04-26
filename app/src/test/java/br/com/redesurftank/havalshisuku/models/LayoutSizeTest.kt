package br.com.redesurftank.havalshisuku.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayoutSizeTest {

    @Test
    fun fromValue_mapsKnownValues() {
        assertEquals(LayoutSizeMode.SMALL, LayoutSizeMode.fromValue("small"))
        assertEquals(LayoutSizeMode.MEDIUM, LayoutSizeMode.fromValue("medium"))
        assertEquals(LayoutSizeMode.LARGE, LayoutSizeMode.fromValue("large"))
        assertEquals(LayoutSizeMode.CUSTOM, LayoutSizeMode.fromValue("custom"))
    }

    @Test
    fun fromValue_fallsBackToSmall_forUnknownOrNull() {
        assertEquals(LayoutSizeMode.SMALL, LayoutSizeMode.fromValue(null))
        assertEquals(LayoutSizeMode.SMALL, LayoutSizeMode.fromValue(""))
        assertEquals(LayoutSizeMode.SMALL, LayoutSizeMode.fromValue("garbage"))
    }

    @Test
    fun presetScales_haveExpectedValues() {
        assertEquals(1.0f, LayoutSizeMode.SMALL.presetScale!!, 0.0001f)
        assertEquals(1.15f, LayoutSizeMode.MEDIUM.presetScale!!, 0.0001f)
        assertEquals(1.30f, LayoutSizeMode.LARGE.presetScale!!, 0.0001f)
        assertNull(LayoutSizeMode.CUSTOM.presetScale)
    }

    @Test
    fun scaleBounds_matchContract() {
        // Contract: slider goes from 1.0x to 2.0x.
        assertEquals(1.0f, LayoutSizeMode.MIN_SCALE, 0.0001f)
        assertEquals(2.0f, LayoutSizeMode.MAX_SCALE, 0.0001f)
        assertEquals(1.0f, LayoutSizeMode.DEFAULT_CUSTOM_SCALE, 0.0001f)
    }

    @Test
    fun resolveLayoutScale_presetModes_ignoreCustomValue() {
        // Preset modes must use their fixed scale regardless of custom value.
        assertEquals(1.0f, resolveLayoutScale("small", customScale = 1.8f), 0.0001f)
        assertEquals(1.15f, resolveLayoutScale("medium", customScale = 1.8f), 0.0001f)
        assertEquals(1.30f, resolveLayoutScale("large", customScale = 1.8f), 0.0001f)
    }

    @Test
    fun resolveLayoutScale_customMode_returnsCustomValue() {
        assertEquals(1.5f, resolveLayoutScale("custom", customScale = 1.5f), 0.0001f)
        assertEquals(1.0f, resolveLayoutScale("custom", customScale = 1.0f), 0.0001f)
        assertEquals(2.0f, resolveLayoutScale("custom", customScale = 2.0f), 0.0001f)
    }

    @Test
    fun resolveLayoutScale_customMode_clampsOutOfRange() {
        // Below minimum.
        assertEquals(1.0f, resolveLayoutScale("custom", customScale = 0.5f), 0.0001f)
        assertEquals(1.0f, resolveLayoutScale("custom", customScale = -3f), 0.0001f)
        // Above maximum.
        assertEquals(2.0f, resolveLayoutScale("custom", customScale = 2.5f), 0.0001f)
        assertEquals(2.0f, resolveLayoutScale("custom", customScale = 100f), 0.0001f)
    }

    @Test
    fun resolveLayoutScale_unknownMode_fallsBackToSmall() {
        // Unknown/null mode should behave like SMALL, ignoring custom scale.
        assertEquals(1.0f, resolveLayoutScale(null, customScale = 1.7f), 0.0001f)
        assertEquals(1.0f, resolveLayoutScale("bogus", customScale = 1.7f), 0.0001f)
    }
}
