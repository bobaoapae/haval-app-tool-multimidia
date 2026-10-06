package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAppLauncherCarPlaySystemUiIconWatchdogTest {

    @Test
    fun serviceDumpWithLiveSystemUiConnectionIsHealthy() {
        val dump =
            """
            * ServiceRecord{c2141 u0 com.ts.carplay/.CarPlayService}
              ConnectionRecord{a2be535 u0 CR com.ts.carplay/.CarPlayService:com.android.systemui}
            """.trimIndent()

        assertEquals(
            "HEALTHY",
            DisplayAppLauncher.resolveCarPlaySystemUiServiceConnectionStateForTest(dump)
        )
    }

    @Test
    fun serviceDumpWithDeadSystemUiConnectionIsDead() {
        val dump =
            """
            * ServiceRecord{c2141 u0 com.ts.carplay/.CarPlayService}
              ConnectionRecord{f71c883 DEAD com.ts.carplay/.CarPlayService:com.android.systemui}
              ConnectionRecord{f71c884 u0 CR com.ts.carplay/.CarPlayService:com.beantechs.applist}
            """.trimIndent()

        assertEquals(
            "DEAD",
            DisplayAppLauncher.resolveCarPlaySystemUiServiceConnectionStateForTest(dump)
        )
    }

    @Test
    fun realServiceDumpWithDeadConnectionLineBeforeSystemUiBindingIsDead() {
        val dump =
            """
            * ServiceRecord{eb85307 u0 com.ts.carplay/.CarPlayService}
              Bindings:
              * IntentBindRecord{4f68032 CREATE}:
                intent={act=com.ts.carplay.action.CarPlayService pkg=com.ts.carplay}
              Connection bindings to services:
              * ConnectionRecord{69f80d8 u0 CR DEAD com.ts.carplay/.CarPlayService:@c3c3bb}
                binding=AppBindRecord{5e732df com.ts.carplay/.CarPlayService:com.beantechs.mediacenter}
                conn=android.os.BinderProxy@c3c3bb flags=0x1
              * ConnectionRecord{78a0c06 u0 CR DEAD com.ts.carplay/.CarPlayService:@34de6e1}
                binding=AppBindRecord{c6acb2c com.ts.carplay/.CarPlayService:com.android.systemui}
                conn=android.os.BinderProxy@34de6e1 flags=0x1
              * ConnectionRecord{6c3bf8 u0 CR com.ts.carplay/.CarPlayService:@86ff75b}
                binding=AppBindRecord{9b8bf00 com.ts.carplay/.CarPlayService:br.com.redesurftank.havalshisuku}
            """.trimIndent()

        assertEquals(
            "DEAD",
            DisplayAppLauncher.resolveCarPlaySystemUiServiceConnectionStateForTest(dump)
        )
    }

    @Test
    fun realServiceDumpWithLiveConnectionLineBeforeSystemUiBindingIsHealthy() {
        val dump =
            """
            * ServiceRecord{eb85307 u0 com.ts.carplay/.CarPlayService}
              Connection bindings to services:
              * ConnectionRecord{78a0c06 u0 CR com.ts.carplay/.CarPlayService:@34de6e1}
                binding=AppBindRecord{c6acb2c com.ts.carplay/.CarPlayService:com.android.systemui}
                conn=android.os.BinderProxy@34de6e1 flags=0x1
            """.trimIndent()

        assertEquals(
            "HEALTHY",
            DisplayAppLauncher.resolveCarPlaySystemUiServiceConnectionStateForTest(dump)
        )
    }

    @Test
    fun emptyServiceDumpMeansCarPlayServiceIsNotReady() {
        assertEquals(
            "SERVICE_NOT_READY",
            DisplayAppLauncher.resolveCarPlaySystemUiServiceConnectionStateForTest("")
        )
    }

    @Test
    fun liveCarPlayServiceWithoutSystemUiConnectionIsMissing() {
        val dump =
            """
            * ServiceRecord{c2141 u0 com.ts.carplay/.CarPlayService}
              intent={cmp=com.ts.carplay/.CarPlayService}
            """.trimIndent()

        assertEquals(
            "MISSING",
            DisplayAppLauncher.resolveCarPlaySystemUiServiceConnectionStateForTest(dump)
        )
    }

    @Test
    fun missingSystemUiConnectionRequiresRepeatedStationarySamples() {
        assertFalse(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "MISSING",
                carPlayRelevant = true,
                speedKmh = 0.0,
                missingBindSamples = 2,
                now = 200_000L,
                lastRecoveryAt = 0L
            )
        )

        assertTrue(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "MISSING",
                carPlayRelevant = true,
                speedKmh = 0.0,
                missingBindSamples = 3,
                now = 200_000L,
                lastRecoveryAt = 0L
            )
        )
    }

    @Test
    fun missingBindUsesFastRechecksOnlyWhileConfirmationIsPending() {
        assertEquals(
            2_000L,
            DisplayAppLauncher.carPlaySystemUiIconWatchdogDelayForTest(
                connectedMissingBindSamples = 1,
                prewarmMissingBindSamples = 0
            )
        )
        assertEquals(
            2_000L,
            DisplayAppLauncher.carPlaySystemUiIconWatchdogDelayForTest(
                connectedMissingBindSamples = 0,
                prewarmMissingBindSamples = 2
            )
        )
        assertEquals(
            10_000L,
            DisplayAppLauncher.carPlaySystemUiIconWatchdogDelayForTest(
                connectedMissingBindSamples = 3,
                prewarmMissingBindSamples = 0
            )
        )
        assertEquals(
            10_000L,
            DisplayAppLauncher.carPlaySystemUiIconWatchdogDelayForTest(
                connectedMissingBindSamples = 0,
                prewarmMissingBindSamples = 0
            )
        )
    }

    @Test
    fun automaticSystemUiRestartPolicyBlocksBootPrewarm() {
        assertFalse(DisplayAppLauncher.isCarPlaySystemUiAutomaticRestartEnabledForTest())
        assertFalse(
            DisplayAppLauncher.shouldPrewarmCarPlaySystemUiBindForTest(
                usbConfigured = false,
                hadConnectedCarPlayContext = false,
                prewarmAttempted = false,
                hostPidAlive = true,
                connectionStateName = "MISSING"
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPrewarmCarPlaySystemUiBindForTest(
                usbConfigured = false,
                hadConnectedCarPlayContext = false,
                prewarmAttempted = false,
                hostPidAlive = true,
                connectionStateName = "SERVICE_NOT_READY"
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPrewarmCarPlaySystemUiBindForTest(
                usbConfigured = true,
                hadConnectedCarPlayContext = false,
                prewarmAttempted = false,
                hostPidAlive = true,
                connectionStateName = "MISSING"
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPrewarmCarPlaySystemUiBindForTest(
                usbConfigured = false,
                hadConnectedCarPlayContext = true,
                prewarmAttempted = false,
                hostPidAlive = true,
                connectionStateName = "MISSING"
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPrewarmCarPlaySystemUiBindForTest(
                usbConfigured = false,
                hadConnectedCarPlayContext = false,
                bootPrewarmEligible = false,
                prewarmAttempted = false,
                hostPidAlive = true,
                connectionStateName = "MISSING"
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPrewarmCarPlaySystemUiBindForTest(
                usbConfigured = false,
                hadConnectedCarPlayContext = false,
                prewarmAttempted = true,
                hostPidAlive = true,
                connectionStateName = "MISSING"
            )
        )
    }

    @Test
    fun bootPrewarmIsClaimedOnlyOnceAndOnlyInsideBootstrapWindow() {
        val bootToken = "87d9be65-616b-4e5b-bff8-97c2b7052528"
        assertTrue(
            DisplayAppLauncher.isCarPlaySystemUiIconBootPrewarmEligibleForTest(
                bootUptimeMs = 60_000L,
                bootToken = bootToken,
                claimedBootToken = ""
            )
        )
        assertFalse(
            DisplayAppLauncher.isCarPlaySystemUiIconBootPrewarmEligibleForTest(
                bootUptimeMs = 120_001L,
                bootToken = bootToken,
                claimedBootToken = ""
            )
        )
        assertFalse(
            DisplayAppLauncher.isCarPlaySystemUiIconBootPrewarmEligibleForTest(
                bootUptimeMs = 60_000L,
                bootToken = bootToken,
                claimedBootToken = bootToken
            )
        )
        assertFalse(
            DisplayAppLauncher.isCarPlaySystemUiIconBootPrewarmEligibleForTest(
                bootUptimeMs = 60_000L,
                bootToken = "unknown-1234",
                claimedBootToken = ""
            )
        )
    }

    @Test
    fun missingSamplesResetWhenCarPlayOrSystemUiGenerationChanges() {
        assertEquals(
            3,
            DisplayAppLauncher.nextCarPlayMissingBindSampleCountForTest(
                previousCount = 2,
                previousGeneration = "host-10|systemui-20|service-a",
                currentGeneration = "host-10|systemui-20|service-a",
                connectionStateName = "MISSING"
            )
        )
        assertEquals(
            1,
            DisplayAppLauncher.nextCarPlayMissingBindSampleCountForTest(
                previousCount = 2,
                previousGeneration = "host-10|systemui-20|service-a",
                currentGeneration = "host-11|systemui-20|service-b",
                connectionStateName = "MISSING"
            )
        )
        assertEquals(
            1,
            DisplayAppLauncher.nextCarPlayMissingBindSampleCountForTest(
                previousCount = 2,
                previousGeneration = "host-10|systemui-20|service-a",
                currentGeneration = "host-10|systemui-21|service-a",
                connectionStateName = "MISSING"
            )
        )
        assertEquals(
            0,
            DisplayAppLauncher.nextCarPlayMissingBindSampleCountForTest(
                previousCount = 2,
                previousGeneration = "host-10|systemui-20|service-a",
                currentGeneration = "host-10|systemui-20|service-a",
                connectionStateName = "HEALTHY"
            )
        )
    }

    @Test
    fun recoveryRevalidationRequiresSameStateAndAnOpenBootWindow() {
        assertTrue(
            DisplayAppLauncher.isCarPlaySystemUiRecoveryConfirmationValidForTest(
                originalConnectionStateName = "MISSING",
                confirmedConnectionStateName = "MISSING",
                requireBootPrewarmWindow = true,
                bootUptimeMs = 120_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.isCarPlaySystemUiRecoveryConfirmationValidForTest(
                originalConnectionStateName = "DEAD",
                confirmedConnectionStateName = "MISSING",
                requireBootPrewarmWindow = false,
                bootUptimeMs = 60_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.isCarPlaySystemUiRecoveryConfirmationValidForTest(
                originalConnectionStateName = "MISSING",
                confirmedConnectionStateName = "HEALTHY",
                requireBootPrewarmWindow = true,
                bootUptimeMs = 60_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.isCarPlaySystemUiRecoveryConfirmationValidForTest(
                originalConnectionStateName = "MISSING",
                confirmedConnectionStateName = "MISSING",
                requireBootPrewarmWindow = true,
                bootUptimeMs = 120_001L
            )
        )
        assertTrue(
            DisplayAppLauncher.isCarPlaySystemUiRecoveryConfirmationValidForTest(
                originalConnectionStateName = "DEAD",
                confirmedConnectionStateName = "DEAD",
                requireBootPrewarmWindow = false,
                bootUptimeMs = 500_000L
            )
        )
    }

    @Test
    fun iconIsRelevantWhenNativeCarPlayServiceIsAliveBeforeVisualAppOpens() {
        val dump =
            """
            * ServiceRecord{c2141 u0 com.ts.carplay/.CarPlayService}
              intent={cmp=com.ts.carplay/.CarPlayService}
            """.trimIndent()

        assertTrue(
            DisplayAppLauncher.isCarPlaySystemUiIconRelevantForTest(
                hasVisualTask = false,
                appPidAlive = false,
                hostPidAlive = true,
                serviceDump = dump,
                linkStatus = null
            )
        )
    }

    @Test
    fun iconIsRelevantWhenCarPlayLinkIsActivatedWithoutVisualTask() {
        assertTrue(
            DisplayAppLauncher.isCarPlaySystemUiIconRelevantForTest(
                hasVisualTask = false,
                appPidAlive = false,
                hostPidAlive = false,
                serviceDump = "",
                linkStatus = 2
            )
        )
    }

    @Test
    fun iconIsNotRelevantWithoutVisualServiceProcessOrActivatedLink() {
        assertFalse(
            DisplayAppLauncher.isCarPlaySystemUiIconRelevantForTest(
                hasVisualTask = false,
                appPidAlive = false,
                hostPidAlive = false,
                serviceDump = "",
                linkStatus = 0
            )
        )
    }

    @Test
    fun deadConnectionRecoversOnlyWhenCarPlayRelevantStationaryAndCooldownExpired() {
        assertTrue(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "DEAD",
                carPlayRelevant = true,
                speedKmh = 0.0,
                missingBindSamples = 0,
                now = 200_000L,
                lastRecoveryAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "DEAD",
                carPlayRelevant = true,
                speedKmh = -1.0,
                missingBindSamples = 0,
                now = 200_000L,
                lastRecoveryAt = 0L
            )
        )

        assertTrue(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "DEAD",
                carPlayRelevant = true,
                speedKmh = 0.5,
                missingBindSamples = 0,
                now = 200_000L,
                lastRecoveryAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "DEAD",
                carPlayRelevant = true,
                speedKmh = 0.6,
                missingBindSamples = 0,
                now = 200_000L,
                lastRecoveryAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "DEAD",
                carPlayRelevant = true,
                speedKmh = 12.0,
                missingBindSamples = 0,
                now = 200_000L,
                lastRecoveryAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "DEAD",
                carPlayRelevant = false,
                speedKmh = 0.0,
                missingBindSamples = 0,
                now = 200_000L,
                lastRecoveryAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "DEAD",
                carPlayRelevant = true,
                speedKmh = 0.0,
                missingBindSamples = 0,
                now = 200_000L,
                lastRecoveryAt = 150_000L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRecoverCarPlaySystemUiIconForTest(
                connectionStateName = "SERVICE_NOT_READY",
                carPlayRelevant = true,
                speedKmh = 0.0,
                missingBindSamples = 3,
                now = 200_000L,
                lastRecoveryAt = 0L
            )
        )
    }

    @Test
    fun usbDisconnectRefreshOnlyAfterConnectedOrRecentCarPlayContext() {
        assertFalse(
            DisplayAppLauncher.shouldRefreshCarPlaySystemUiIconAfterUsbDisconnectForTest(
                previousUsbConfigured = null,
                lastRelevantAt = 0L,
                speedKmh = 0.0,
                now = 200_000L,
                lastDisconnectRefreshAt = 0L
            )
        )

        assertTrue(
            DisplayAppLauncher.shouldRefreshCarPlaySystemUiIconAfterUsbDisconnectForTest(
                previousUsbConfigured = true,
                lastRelevantAt = 0L,
                speedKmh = 0.0,
                now = 200_000L,
                lastDisconnectRefreshAt = 0L
            )
        )

        assertTrue(
            DisplayAppLauncher.shouldRefreshCarPlaySystemUiIconAfterUsbDisconnectForTest(
                previousUsbConfigured = false,
                lastRelevantAt = 180_000L,
                speedKmh = 0.0,
                now = 200_000L,
                lastDisconnectRefreshAt = 0L
            )
        )
    }

    @Test
    fun usbDisconnectRefreshRespectsSpeedAndCooldown() {
        assertFalse(
            DisplayAppLauncher.shouldRefreshCarPlaySystemUiIconAfterUsbDisconnectForTest(
                previousUsbConfigured = true,
                lastRelevantAt = 180_000L,
                speedKmh = 3.0,
                now = 200_000L,
                lastDisconnectRefreshAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRefreshCarPlaySystemUiIconAfterUsbDisconnectForTest(
                previousUsbConfigured = true,
                lastRelevantAt = 180_000L,
                speedKmh = 0.0,
                now = 200_000L,
                lastDisconnectRefreshAt = 190_000L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRefreshCarPlaySystemUiIconAfterUsbDisconnectForTest(
                previousUsbConfigured = false,
                lastRelevantAt = 1L,
                speedKmh = 0.0,
                now = 400_000L,
                lastDisconnectRefreshAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRefreshCarPlaySystemUiIconAfterUsbDisconnectForTest(
                previousUsbConfigured = true,
                lastRelevantAt = 180_000L,
                speedKmh = 0.0,
                now = 200_000L,
                lastDisconnectRefreshAt = 0L,
                lastRecoveryAt = 150_000L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldRefreshCarPlaySystemUiIconAfterUsbDisconnectForTest(
                previousUsbConfigured = true,
                lastRelevantAt = 180_000L,
                speedKmh = -1.0,
                now = 200_000L,
                lastDisconnectRefreshAt = 0L,
                lastRecoveryAt = 0L
            )
        )
    }

    @Test
    fun speedParserAcceptsPlainAndLocalizedValues() {
        assertEquals(0.0, DisplayAppLauncher.parseVehicleSpeedKmh("0.0")!!, 0.001)
        assertEquals(14.5, DisplayAppLauncher.parseVehicleSpeedKmh("speed=14,5 km/h")!!, 0.001)
        assertNull(DisplayAppLauncher.parseVehicleSpeedKmh("--"))
    }
}
