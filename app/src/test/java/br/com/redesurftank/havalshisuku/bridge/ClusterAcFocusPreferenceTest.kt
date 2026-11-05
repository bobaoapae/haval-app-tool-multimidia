package br.com.redesurftank.havalshisuku.bridge

import org.junit.Assert.*
import org.junit.Test
import android.content.SharedPreferences
import java.lang.reflect.Proxy
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys

class ClusterAcFocusPreferenceTest {
    @Test fun restoresBothFocusValuesWithoutWrites() {
        assertEquals("temp", ClusterAcFocusPreference.read { "TEMPERATURE" })
        assertEquals("fan", ClusterAcFocusPreference.read { "FAN_SPEED" })
    }
    @Test fun missingWrongTypeAndUnknownValuesFallBackToFan() {
        for (value in listOf(null, "", "temp", " TEMPERATURE", "unknown"))
            assertEquals("fan", ClusterAcFocusPreference.read { value })
        assertEquals("fan", ClusterAcFocusPreference.read { throw ClassCastException() })
    }
    @Test fun savesOnlyExactAcceptedValues() {
        val writes = mutableListOf<String>()
        assertTrue(ClusterAcFocusPreference.save("fan", writes::add))
        assertTrue(ClusterAcFocusPreference.save("temp", writes::add))
        assertEquals(listOf("FAN_SPEED", "TEMPERATURE"), writes)
        for (value in listOf(null, "", "TEMP", " temp", "FAN_SPEED"))
            assertFalse(ClusterAcFocusPreference.save(value, writes::add))
        assertEquals(2, writes.size)
    }
    @Test(expected = IllegalStateException::class)
    fun unexpectedReadFailuresAreNotHidden() {
        ClusterAcFocusPreference.read { throw IllegalStateException() }
    }
    @Test(expected = IllegalStateException::class)
    fun failedPersistenceIsNotReportedAsAccepted() {
        ClusterAcFocusPreference.save("temp") { throw IllegalStateException() }
    }
    @Test fun actualBridgeUsesExistingPreferenceAndAppliesOnlyValidFocus() {
        var stored: Any? = "TEMPERATURE"
        var applies = 0
        lateinit var editor: SharedPreferences.Editor
        editor = fake { name, args ->
            when (name) {
                "putString" -> {
                    assertEquals(SharedPreferencesKeys.LAST_CLUSTER_AC_CONFIG.key, args!![0])
                    stored = args[1]
                    editor
                }
                "apply" -> { applies++; null }
                else -> error("Unexpected editor call: $name")
            }
        }
        val preferences: SharedPreferences = fake { name, args ->
            when (name) {
                "getString" -> {
                    assertEquals(SharedPreferencesKeys.LAST_CLUSTER_AC_CONFIG.key, args!![0])
                    assertNull(args[1])
                    stored as String?
                }
                "edit" -> editor
                else -> error("Unexpected preference call: $name")
            }
        }
        val context: IBridgeContext = fake { name, _ ->
            if (name == "getPreferences") preferences else error("Unexpected context call: $name")
        }
        val bridge = ThemeBridgeImpl(context)
        assertEquals("temp", bridge.getClusterAcFocus())
        stored = 123
        assertEquals("fan", bridge.getClusterAcFocus())
        assertEquals(0, applies)
        assertFalse(bridge.saveClusterAcFocus(null))
        assertEquals(0, applies)
        assertTrue(bridge.saveClusterAcFocus("fan"))
        assertEquals("FAN_SPEED", stored)
        assertEquals(1, applies)
        assertTrue(bridge.saveClusterAcFocus("temp"))
        assertEquals("TEMPERATURE", stored)
        assertEquals(2, applies)
    }

    @Test fun sharesNativeSportPreferenceWithoutChangingItsControllerContract() {
        // Source contract, not an instrumented native-screen interaction test.
        val candidates = listOf("src/main/java/", "app/src/main/java/")
        val relative = "br/com/redesurftank/havalshisuku/models/screens/AcControlScreen.java"
        val path = candidates.map { java.nio.file.Paths.get(it + relative) }
            .first { java.nio.file.Files.isRegularFile(it) }
        val source = String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8)
        assertTrue(source.contains("getString(SharedPreferencesKeys.LAST_CLUSTER_AC_CONFIG.getKey()"))
        assertTrue(source.contains("putString(SharedPreferencesKeys.LAST_CLUSTER_AC_CONFIG.getKey(), steeringWheelAcControlType.name())"))
        assertTrue(source.contains("SteeringWheelAcControlType.valueOf("))
        val writes = mutableListOf<String>()
        ClusterAcFocusPreference.save("fan", writes::add)
        ClusterAcFocusPreference.save("temp", writes::add)
        assertEquals(
            listOf(br.com.redesurftank.havalshisuku.models.SteeringWheelAcControlType.FAN_SPEED,
                br.com.redesurftank.havalshisuku.models.SteeringWheelAcControlType.TEMPERATURE),
            writes.map { br.com.redesurftank.havalshisuku.models.SteeringWheelAcControlType.valueOf(it) }
        )
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> fake(crossinline call: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args)
        } as T

}
