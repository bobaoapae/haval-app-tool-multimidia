package br.com.redesurftank.havalshisuku.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerFirstRunTest {

    @Test
    fun shortAndFullComponentNamesMatch() {
        assertTrue(
                ViewerFirstRun.sameComponent(
                        "com.havalh6.viewer/.DesktopSwitcherHitService",
                        "com.havalh6.viewer/com.havalh6.viewer.DesktopSwitcherHitService"
                )
        )
    }

    @Test
    fun withComponentKeepsImpulseAndSkipsDuplicates() {
        val current =
                "br.com.redesurftank.havalshisuku/.services.AccessibilityService:" +
                        "br.com.redesurftank.havalshisuku/br.com.redesurftank.havalshisuku.services.AccessibilityService"
        val added =
                ViewerFirstRun.withComponent(
                        current,
                        "com.havalh6.viewer/com.havalh6.viewer.DesktopSwitcherHitService"
                )
        assertTrue(added!!.startsWith(current))
        assertTrue(added.endsWith("com.havalh6.viewer/com.havalh6.viewer.DesktopSwitcherHitService"))
        assertNull(
                ViewerFirstRun.withComponent(
                        added,
                        "com.havalh6.viewer/.DesktopSwitcherHitService"
                )
        )
        assertEquals(
                "com.havalh6.viewer/com.havalh6.viewer.MediaNotificationListener",
                ViewerFirstRun.withComponent("null", "com.havalh6.viewer/com.havalh6.viewer.MediaNotificationListener")
        )
    }

    @Test
    fun prepareGrantsRuntimeOverlayAndServicesWithoutDroppingExisting() {
        val calls = mutableListOf<Array<String>>()
        val ok =
                ViewerFirstRun.prepare(
                        "com.havalh6.viewer",
                        requested =
                                listOf(
                                        "android.permission.INTERNET",
                                        "android.permission.ACCESS_FINE_LOCATION",
                                        ViewerFirstRun.OVERLAY,
                                        "android.permission.RECORD_AUDIO"
                                ),
                        services =
                                listOf(
                                        "com.havalh6.viewer.MediaNotificationListener" to
                                                ViewerFirstRun.NOTIFICATION_LISTENER,
                                        "com.havalh6.viewer.DesktopSwitcherHitService" to
                                                ViewerFirstRun.ACCESSIBILITY
                                ),
                        isDangerous = { it.endsWith("LOCATION") || it.endsWith("RECORD_AUDIO") },
                        shizukuReady = { true },
                        run = { command ->
                            calls.add(command)
                            when {
                                command.contentEquals(
                                        arrayOf("settings", "get", "secure", "enabled_notification_listeners")
                                ) -> "br.com.redesurftank.havalshisuku/br.com.redesurftank.havalshisuku.services.BottomBarNotificationListenerService"
                                command.contentEquals(
                                        arrayOf("settings", "get", "secure", "enabled_accessibility_services")
                                ) -> "br.com.redesurftank.havalshisuku/.services.AccessibilityService"
                                else -> ""
                            }
                        }
                )
        assertTrue(ok)
        assertTrue(calls.any { it.contentEquals(arrayOf("pm", "grant", "com.havalh6.viewer", "android.permission.ACCESS_FINE_LOCATION")) })
        assertTrue(calls.any { it.contentEquals(arrayOf("pm", "grant", "com.havalh6.viewer", "android.permission.RECORD_AUDIO")) })
        assertFalse(calls.any { it.contentEquals(arrayOf("pm", "grant", "com.havalh6.viewer", "android.permission.INTERNET")) })
        assertTrue(calls.any { it.contentEquals(arrayOf("appops", "set", "com.havalh6.viewer", "SYSTEM_ALERT_WINDOW", "allow")) })
        val listener = calls.first { it.size == 3 && it[2].contains("enabled_notification_listeners") }
        assertTrue(listener[2].contains("BottomBarNotificationListenerService"))
        assertTrue(listener[2].contains("MediaNotificationListener"))
        val a11y = calls.first { it.size == 3 && it[2].contains("enabled_accessibility_services") }
        assertTrue(a11y[2].contains("AccessibilityService"))
        assertTrue(a11y[2].contains("DesktopSwitcherHitService"))
        assertTrue(calls.any { it.contentEquals(arrayOf("settings", "put", "secure", "accessibility_enabled", "1")) })
    }

    @Test
    fun shizukuDownSkipsEveryCommand() {
        var ran = false
        val ok =
                ViewerFirstRun.prepare(
                        "com.havalh6.viewer",
                        requested = listOf("android.permission.ACCESS_FINE_LOCATION"),
                        services = emptyList(),
                        isDangerous = { true },
                        shizukuReady = { false },
                        run = {
                            ran = true
                            ""
                        }
                )
        assertFalse(ok)
        assertFalse(ran)
    }
}
