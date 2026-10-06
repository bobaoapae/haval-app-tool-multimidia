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
    fun aFailedReadDoesNotReplaceTheExistingList() {
        val (ok, calls) =
                prepareWith { script ->
                    if (script.contains("'get'")) ""
                    else done("")
                }
        assertFalse(ok)
        assertFalse(calls.any { it.contains("put") })
    }

    @Test
    fun accessibilitySwitchIsRetriedWhenTheServiceIsAlreadyListed() {
        var switchTries = 0
        val calls = mutableListOf<String>()
        val first =
                ViewerFirstRun.prepare(
                        "com.havalh6.viewer",
                        requested = emptyList(),
                        services = launcherServices(),
                        isDangerous = { false },
                        shizukuReady = { true },
                        run = { command ->
                            val script = command.last()
                            calls.add(script)
                            when {
                                script.contains("get") && script.contains("enabled_accessibility_services") ->
                                        done(
                                                "br.com.redesurftank.havalshisuku/.services.AccessibilityService:" +
                                                        "com.havalh6.viewer/com.havalh6.viewer.DesktopSwitcherHitService"
                                        )
                                script.contains("get") && script.contains("enabled_notification_listeners") ->
                                        done("null")
                                script.contains("accessibility_enabled") -> {
                                    switchTries++
                                    done("", code = 1)
                                }
                                else -> done("")
                            }
                        }
                )
        assertFalse(first)
        assertFalse(calls.any { it.contains("put") && it.contains("enabled_accessibility_services") })
        assertEquals(1, switchTries)

        val second =
                ViewerFirstRun.prepare(
                        "com.havalh6.viewer",
                        requested = emptyList(),
                        services = launcherServices(),
                        isDangerous = { false },
                        shizukuReady = { true },
                        run = { command ->
                            val script = command.last()
                            when {
                                script.contains("get") && script.contains("enabled_accessibility_services") ->
                                        done(
                                                "br.com.redesurftank.havalshisuku/.services.AccessibilityService:" +
                                                        "com.havalh6.viewer/com.havalh6.viewer.DesktopSwitcherHitService"
                                        )
                                script.contains("get") && script.contains("enabled_notification_listeners") ->
                                        done(
                                                "com.havalh6.viewer/com.havalh6.viewer.MediaNotificationListener"
                                        )
                                script.contains("accessibility_enabled") -> {
                                    switchTries++
                                    done("")
                                }
                                else -> done("")
                            }
                        }
                )
        assertTrue(second)
        assertEquals(2, switchTries)
    }

    @Test
    fun prepareGrantsRuntimeOverlayAndServicesWithoutDroppingExisting() {
        val (ok, calls) =
                prepareWith { script ->
                    when {
                        script.contains("get") && script.contains("enabled_notification_listeners") ->
                                done(
                                        "br.com.redesurftank.havalshisuku/br.com.redesurftank.havalshisuku.services.BottomBarNotificationListenerService"
                                )
                        script.contains("get") && script.contains("enabled_accessibility_services") ->
                                done("br.com.redesurftank.havalshisuku/.services.AccessibilityService")
                        else -> done("")
                    }
                }
        assertTrue(ok)
        assertTrue(calls.any { it.contains("pm") && it.contains("grant") && it.contains("ACCESS_FINE_LOCATION") })
        assertTrue(calls.any { it.contains("pm") && it.contains("grant") && it.contains("RECORD_AUDIO") })
        assertFalse(calls.any { it.contains("grant") && it.contains("INTERNET") })
        assertTrue(calls.any { it.contains("SYSTEM_ALERT_WINDOW") && it.contains("allow") })
        val listener = calls.first { it.contains("put") && it.contains("enabled_notification_listeners") }
        assertTrue(listener.contains("BottomBarNotificationListenerService"))
        assertTrue(listener.contains("MediaNotificationListener"))
        val a11y = calls.first { it.contains("put") && it.contains("enabled_accessibility_services") }
        assertTrue(a11y.contains("AccessibilityService"))
        assertTrue(a11y.contains("DesktopSwitcherHitService"))
        assertTrue(calls.any { it.contains("accessibility_enabled") })
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

    @Test
    fun anUnmarkedResultIsNotASuccessfulRead() {
        val (body, ok) = ViewerFirstRun.splitCommandResult("")
        assertFalse(ok)
        assertEquals("", body)
        val (value, succeeded) = ViewerFirstRun.splitCommandResult("already-there\n${ViewerFirstRun.EXIT_MARKER}0")
        assertTrue(succeeded)
        assertEquals("already-there", value)
        assertFalse(ViewerFirstRun.splitCommandResult("already-there\n${ViewerFirstRun.EXIT_MARKER}1").second)
    }

    private fun launcherServices() =
            listOf(
                    "com.havalh6.viewer.MediaNotificationListener" to ViewerFirstRun.NOTIFICATION_LISTENER,
                    "com.havalh6.viewer.DesktopSwitcherHitService" to ViewerFirstRun.ACCESSIBILITY
            )

    private fun prepareWith(answer: (String) -> String): Pair<Boolean, List<String>> {
        val calls = mutableListOf<String>()
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
                        services = launcherServices(),
                        isDangerous = { it.endsWith("LOCATION") || it.endsWith("RECORD_AUDIO") },
                        shizukuReady = { true },
                        run = { command ->
                            val script = command.last()
                            calls.add(script)
                            answer(script)
                        }
                )
        return ok to calls
    }

    private fun done(body: String, code: Int = 0): String = "$body\n${ViewerFirstRun.EXIT_MARKER}$code"
}
