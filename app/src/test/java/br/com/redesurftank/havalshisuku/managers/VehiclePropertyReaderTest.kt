package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehiclePropertyReaderTest {
    @Test
    fun plausibleTsrSpeedLimitIsPreserved() {
        assertEquals("4", VehiclePropertyReader.normalizeSpeedLimit("4"))
        assertEquals("80", VehiclePropertyReader.normalizeSpeedLimit(" 80 "))
    }

    @Test
    fun invalidOrImplausibleTsrValueNeverCreatesLimit() {
        assertNull(VehiclePropertyReader.normalizeSpeedLimit(null))
        assertNull(VehiclePropertyReader.normalizeSpeedLimit(""))
        assertNull(VehiclePropertyReader.normalizeSpeedLimit("0"))
        assertNull(VehiclePropertyReader.normalizeSpeedLimit("255"))
        assertNull(VehiclePropertyReader.normalizeSpeedLimit("unknown"))
    }
}
