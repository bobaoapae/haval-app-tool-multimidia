package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAppLauncherUsbStateTest {
    @Test
    fun disconnectedStateIsNotReady() {
        assertFalse(DisplayAppLauncher.isProjectionUsbStateReady("DISCONNECTED"))
    }

    @Test
    fun configuredStateIsReady() {
        assertTrue(DisplayAppLauncher.isProjectionUsbStateReady("CONFIGURED"))
    }

    @Test
    fun connectedStateIsReady() {
        assertTrue(DisplayAppLauncher.isProjectionUsbStateReady("CONNECTED"))
    }

    @Test
    fun multilineOutputOnlyAcceptsExactReadyStates() {
        assertFalse(
            DisplayAppLauncher.isProjectionUsbStateReady(
                """
                command echo
                DISCONNECTED
                """.trimIndent()
            )
        )
        assertTrue(
            DisplayAppLauncher.isProjectionUsbStateReady(
                """
                command echo
                CONFIGURED
                """.trimIndent()
            )
        )
    }

    @Test
    fun usbReconnectGraceRequiresObservedDisconnectBeforeConfigure() {
        assertFalse(
            DisplayAppLauncher.isWithinCarPlayUsbReconnectGraceForTest(
                now = 12_000L,
                lastDisconnectedAt = 0L,
                lastConfiguredAt = 10_000L,
                graceMs = 4_000L
            )
        )

        assertFalse(
            DisplayAppLauncher.isWithinCarPlayUsbReconnectGraceForTest(
                now = 12_000L,
                lastDisconnectedAt = 11_000L,
                lastConfiguredAt = 10_000L,
                graceMs = 4_000L
            )
        )
    }

    @Test
    fun usbReconnectGraceExpiresAfterConfiguredWindow() {
        assertTrue(
            DisplayAppLauncher.isWithinCarPlayUsbReconnectGraceForTest(
                now = 12_500L,
                lastDisconnectedAt = 9_000L,
                lastConfiguredAt = 10_000L,
                graceMs = 4_000L
            )
        )

        assertFalse(
            DisplayAppLauncher.isWithinCarPlayUsbReconnectGraceForTest(
                now = 15_000L,
                lastDisconnectedAt = 9_000L,
                lastConfiguredAt = 10_000L,
                graceMs = 4_000L
            )
        )
    }

    @Test
    fun usbReconnectGraceDefersAutomaticClusterRestore() {
        assertTrue(
            DisplayAppLauncher.shouldDeferCarPlayReconnectRestoreForTest(
                now = 12_500L,
                lastDisconnectedAt = 9_000L,
                lastConfiguredAt = 10_000L,
                graceMs = 4_000L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldDeferCarPlayReconnectRestoreForTest(
                now = 15_000L,
                lastDisconnectedAt = 9_000L,
                lastConfiguredAt = 10_000L,
                graceMs = 4_000L
            )
        )
    }

    @Test
    fun projectionProcessPidOutputRequiresPositivePid() {
        assertFalse(DisplayAppLauncher.isProjectionProcessPidOutputAliveForTest(""))
        assertFalse(DisplayAppLauncher.isProjectionProcessPidOutputAliveForTest("pidof: not found"))
        assertFalse(DisplayAppLauncher.isProjectionProcessPidOutputAliveForTest("0"))

        assertTrue(DisplayAppLauncher.isProjectionProcessPidOutputAliveForTest("32141"))
        assertTrue(DisplayAppLauncher.isProjectionProcessPidOutputAliveForTest("28494 32141"))
    }

    @Test
    fun mainCarPlayStackMoveToClusterRequiresExclusiveD0Stack() {
        assertTrue(
            DisplayAppLauncher.shouldMoveMainCarPlayStackToClusterForTest(
                mainTaskDisplayId = 0,
                tasksInStack = 1,
                hasClusterTask = false
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldMoveMainCarPlayStackToClusterForTest(
                mainTaskDisplayId = 0,
                tasksInStack = 2,
                hasClusterTask = false
            )
        )
    }

    @Test
    fun mainCarPlayStackMoveToClusterRequiresNoExistingClusterTask() {
        assertFalse(
            DisplayAppLauncher.shouldMoveMainCarPlayStackToClusterForTest(
                mainTaskDisplayId = 0,
                tasksInStack = 1,
                hasClusterTask = true
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldMoveMainCarPlayStackToClusterForTest(
                mainTaskDisplayId = 3,
                tasksInStack = 1,
                hasClusterTask = false
            )
        )
    }
}
