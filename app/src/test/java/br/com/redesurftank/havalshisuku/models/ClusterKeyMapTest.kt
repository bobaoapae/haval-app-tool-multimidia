package br.com.redesurftank.havalshisuku.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterKeyMapTest {
    @Test
    fun mapsUpDownCodes() {
        assertEquals(ClusterKey.UP, ClusterKeyMap.fromKeyCode(1024))
        assertEquals(ClusterKey.DOWN, ClusterKeyMap.fromKeyCode(1025))
        assertEquals(Integer.valueOf(1024), ClusterKeyMap.toKeyCode(ClusterKey.UP))
    }

    @Test
    fun parsesNameOrCode() {
        assertEquals(ClusterKey.UP, ClusterKeyMap.fromNameOrCode("UP"))
        assertEquals(ClusterKey.DOWN, ClusterKeyMap.fromNameOrCode("down"))
        assertEquals(ClusterKey.ENTER, ClusterKeyMap.fromNameOrCode("1028"))
        assertNull(ClusterKeyMap.fromNameOrCode("nope"))
        assertNull(ClusterKeyMap.fromKeyCode(1))
    }

    @Test
    fun coversAllEnumValuesExceptUnmapped() {
        for (key in ClusterKey.values()) {
            assertTrue("missing mapping for $key", ClusterKeyMap.toKeyCode(key) != null)
        }
    }
}
