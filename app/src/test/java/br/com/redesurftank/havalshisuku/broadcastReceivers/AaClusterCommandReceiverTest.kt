package br.com.redesurftank.havalshisuku.broadcastReceivers

import br.com.redesurftank.havalshisuku.api.ImpulseApiCallers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AaClusterCommandReceiverTest {
    @Test
    fun parsesBooleanStrings() {
        assertEquals(true, parse("true"))
        assertEquals(true, parse("1"))
        assertEquals(true, parse("ON"))
        assertEquals(false, parse("false"))
        assertEquals(false, parse("0"))
        assertEquals(false, parse("off"))
        assertNull(parse("maybe"))
        assertNull(parse(null))
    }

    @Test
    fun allowlistAcceptsViewerAndSelf() {
        assertTrue(ImpulseApiCallers.isAllowedPackage("com.havalh6.viewer"))
        assertTrue(ImpulseApiCallers.isAllowedPackage("br.com.redesurftank.havalshisuku"))
        assertFalse(ImpulseApiCallers.isAllowedPackage("com.example.random"))
        assertFalse(ImpulseApiCallers.isAllowedPackage(null))
    }

    private fun parse(value: String?): Boolean? =
        AaClusterCommandReceiver.parseEnabledString(value)
}
