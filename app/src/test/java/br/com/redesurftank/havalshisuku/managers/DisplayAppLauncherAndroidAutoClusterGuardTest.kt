package br.com.redesurftank.havalshisuku.managers

import android.view.KeyEvent
import br.com.redesurftank.havalshisuku.services.ForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAppLauncherAndroidAutoClusterGuardTest {
    private val impulsePackage = "br.com.redesurftank.havalshisuku"

    @Test
    fun normalDisplayZeroWindowUsesPassiveAndroidAutoWindowGuard() {
        assertEquals(
            "VIDEO_FOCUS_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.android.settings",
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun nativeDisplayZeroPanelsUseVideoFocusAndroidAutoWindowGuard() {
        assertEquals(
            "VIDEO_FOCUS_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.beantechs.hvac",
                selfPackageName = impulsePackage
            )
        )
        assertEquals(
            "VIDEO_FOCUS_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.beantechs.launcher",
                selfPackageName = impulsePackage
            )
        )
        assertEquals(
            "VIDEO_FOCUS_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.beantechs.applist",
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun cameraDisplayZeroPanelsUsePassiveAndroidAutoWindowGuard() {
        assertEquals(
            "VERIFY_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.beantechs.avm",
                selfPackageName = impulsePackage
            )
        )
        assertEquals(
            "VERIFY_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.beantechs.backcamera",
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun impulseSelfWindowUsesPassiveAndroidAutoWindowGuard() {
        assertEquals(
            "VERIFY_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = impulsePackage,
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun androidAutoWindowFocusCooldownOnlySkipsSamePackageAndAction() {
        assertTrue(
            DisplayAppLauncher.shouldSkipAndroidAutoWindowFocusGuardForTest(
                now = 2_000L,
                lastGuardAt = 1_000L,
                packageName = "com.beantechs.mediacenter",
                lastPackageName = "com.beantechs.mediacenter",
                actionName = "VIDEO_FOCUS_ONLY",
                lastActionName = "VIDEO_FOCUS_ONLY",
                cooldownMs = 4_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldSkipAndroidAutoWindowFocusGuardForTest(
                now = 2_000L,
                lastGuardAt = 1_000L,
                packageName = "com.beantechs.settings",
                lastPackageName = "com.beantechs.mediacenter",
                actionName = "VIDEO_FOCUS_ONLY",
                lastActionName = "VIDEO_FOCUS_ONLY",
                cooldownMs = 4_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldSkipAndroidAutoWindowFocusGuardForTest(
                now = 2_000L,
                lastGuardAt = 1_000L,
                packageName = "com.beantechs.hvac",
                lastPackageName = "com.beantechs.hvac",
                actionName = "VIDEO_FOCUS_ONLY",
                lastActionName = "VERIFY_ONLY",
                cooldownMs = 4_000L
            )
        )
    }

    @Test
    fun projectionPackagesDoNotTriggerAndroidAutoWindowGuard() {
        assertNull(
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.ts.androidauto.app",
                selfPackageName = impulsePackage
            )
        )
        assertNull(
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.ts.carplay.app",
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun androidAutoProjectionWindowChangeRestoresOnlyWhenDesiredClusterTargetWasLost() {
        assertTrue(
            DisplayAppLauncher.shouldRestoreAndroidAutoClusterAfterProjectionWindowChangeForTest(
                packageName = "com.ts.androidauto.app",
                desiredOnCluster = true,
                androidAutoOnCluster = false,
                androidAutoCurrentDisplayId = 0
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldRestoreAndroidAutoClusterAfterProjectionWindowChangeForTest(
                packageName = "com.ts.androidauto.projectionservice",
                desiredOnCluster = true,
                androidAutoOnCluster = false,
                androidAutoCurrentDisplayId = 1
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRestoreAndroidAutoClusterAfterProjectionWindowChangeForTest(
                packageName = "com.ts.androidauto.app",
                desiredOnCluster = false,
                androidAutoOnCluster = false,
                androidAutoCurrentDisplayId = 0
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRestoreAndroidAutoClusterAfterProjectionWindowChangeForTest(
                packageName = "com.ts.androidauto.app",
                desiredOnCluster = true,
                androidAutoOnCluster = true,
                androidAutoCurrentDisplayId = 0
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRestoreAndroidAutoClusterAfterProjectionWindowChangeForTest(
                packageName = "com.ts.androidauto.app",
                desiredOnCluster = true,
                androidAutoOnCluster = false,
                androidAutoCurrentDisplayId = 3
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRestoreAndroidAutoClusterAfterProjectionWindowChangeForTest(
                packageName = "com.ts.androidauto.app",
                desiredOnCluster = true,
                androidAutoOnCluster = false,
                androidAutoCurrentDisplayId = null
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRestoreAndroidAutoClusterAfterProjectionWindowChangeForTest(
                packageName = "com.beantechs.applist",
                desiredOnCluster = true,
                androidAutoOnCluster = false,
                androidAutoCurrentDisplayId = 0
            )
        )
    }

    @Test
    fun androidAutoVisualTaskIsActiveProjectionEvidenceWhenTopNativePanelOrAudioIsActive() {
        assertTrue(
            DisplayAppLauncher.shouldTreatAndroidAutoVisualTaskAsActiveProjectionForTest(
                visualOnDisplay = true,
                topPackageOnDisplay = true,
                nativeMediaCenterActive = false,
                audioPlaybackActive = false
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldTreatAndroidAutoVisualTaskAsActiveProjectionForTest(
                visualOnDisplay = true,
                topPackageOnDisplay = false,
                nativeMediaCenterActive = true,
                audioPlaybackActive = false
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldTreatAndroidAutoVisualTaskAsActiveProjectionForTest(
                visualOnDisplay = true,
                topPackageOnDisplay = false,
                nativeMediaCenterActive = false,
                audioPlaybackActive = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldTreatAndroidAutoVisualTaskAsActiveProjectionForTest(
                visualOnDisplay = true,
                topPackageOnDisplay = false,
                nativeMediaCenterActive = false,
                audioPlaybackActive = false
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldTreatAndroidAutoVisualTaskAsActiveProjectionForTest(
                visualOnDisplay = false,
                topPackageOnDisplay = true,
                nativeMediaCenterActive = true,
                audioPlaybackActive = true
            )
        )
    }

    @Test
    fun androidAutoClusterPreservationRequiresExplicitClusterTarget() {
        assertTrue(
            DisplayAppLauncher.isAndroidAutoClusterPreservationEligibleForState(
                activeProjectionPackage = "com.ts.androidauto.app",
                desiredOnCluster = true
            )
        )
        assertFalse(
            DisplayAppLauncher.isAndroidAutoClusterPreservationEligibleForState(
                activeProjectionPackage = "com.ts.androidauto.app",
                desiredOnCluster = false
            )
        )
        assertFalse(
            DisplayAppLauncher.isAndroidAutoClusterPreservationEligibleForState(
                activeProjectionPackage = "com.ts.carplay.app",
                desiredOnCluster = true
            )
        )
        assertFalse(
            DisplayAppLauncher.isAndroidAutoClusterPreservationEligibleForState(
                activeProjectionPackage = null,
                desiredOnCluster = true
            )
        )
    }

    @Test
    fun androidAutoVisualProjectionToggleWaitsForActivatedSessionOrDcmActiveState() {
        assertFalse(
            DisplayAppLauncher.shouldAllowAndroidAutoVisualProjectionToggleForTest(
                linkStatus = null,
                dcmProjectionActive = false
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldAllowAndroidAutoVisualProjectionToggleForTest(
                linkStatus = 2,
                dcmProjectionActive = false
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldAllowAndroidAutoVisualProjectionToggleForTest(
                linkStatus = 3,
                dcmProjectionActive = false
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldAllowAndroidAutoVisualProjectionToggleForTest(
                linkStatus = 7,
                dcmProjectionActive = false
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldAllowAndroidAutoVisualProjectionToggleForTest(
                linkStatus = 8,
                dcmProjectionActive = false
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldAllowAndroidAutoVisualProjectionToggleForTest(
                linkStatus = null,
                dcmProjectionActive = true
            )
        )
    }

    @Test
    fun recentAndroidAutoDcmProjectionEvidenceExpiresAfterCacheWindow() {
        assertTrue(
            DisplayAppLauncher.hasRecentAndroidAutoDcmProjectionActiveEvidenceForTest(
                lastActiveAtMs = 1_000L,
                nowMs = 21_000L,
                cacheMs = 20_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.hasRecentAndroidAutoDcmProjectionActiveEvidenceForTest(
                lastActiveAtMs = 1_000L,
                nowMs = 21_001L,
                cacheMs = 20_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.hasRecentAndroidAutoDcmProjectionActiveEvidenceForTest(
                lastActiveAtMs = 0L,
                nowMs = 10_000L,
                cacheMs = 20_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.hasRecentAndroidAutoDcmProjectionActiveEvidenceForTest(
                lastActiveAtMs = 10_000L,
                nowMs = 9_999L,
                cacheMs = 20_000L
            )
        )
    }

    @Test
    fun shizukuBinderTimeoutRestartsOnlyAfterConsecutiveTimeouts() {
        assertFalse(ForegroundService.shouldRestartAfterShizukuBinderTimeoutForTest(1, 3))
        assertFalse(ForegroundService.shouldRestartAfterShizukuBinderTimeoutForTest(2, 3))
        assertTrue(ForegroundService.shouldRestartAfterShizukuBinderTimeoutForTest(3, 3))
    }

    @Test
    fun staleAndroidAutoVisualStackCleanupPreservesPersistedTargetAndMediaCenter() {
        assertFalse(
            DisplayAppLauncher.shouldRemoveStaleAndroidAutoVisualStacksForTest(
                usbConfigured = false,
                androidAutoLinkActive = false,
                hasAndroidAutoTasks = true,
                desiredOnCluster = true,
                nativeMediaCenterActive = true,
                firstSeenAtMs = 1_000L,
                nowMs = 12_000L,
                graceMs = 10_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRemoveStaleAndroidAutoVisualStacksForTest(
                usbConfigured = false,
                androidAutoLinkActive = false,
                hasAndroidAutoTasks = true,
                desiredOnCluster = true,
                nativeMediaCenterActive = true,
                firstSeenAtMs = 1_000L,
                nowMs = 5_000L,
                graceMs = 10_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRemoveStaleAndroidAutoVisualStacksForTest(
                usbConfigured = true,
                androidAutoLinkActive = false,
                hasAndroidAutoTasks = true,
                desiredOnCluster = true,
                nativeMediaCenterActive = true,
                firstSeenAtMs = 1_000L,
                nowMs = 12_000L,
                graceMs = 10_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRemoveStaleAndroidAutoVisualStacksForTest(
                usbConfigured = false,
                androidAutoLinkActive = true,
                hasAndroidAutoTasks = true,
                desiredOnCluster = true,
                nativeMediaCenterActive = true,
                firstSeenAtMs = 1_000L,
                nowMs = 12_000L,
                graceMs = 10_000L
            )
        )
    }

    @Test
    fun androidAutoMediaCommandDisplayPrefersVisibleProjectionDisplay() {
        assertEquals(
            3,
            DisplayAppLauncher.resolveAndroidAutoMediaCommandDisplayIdForState(
                androidAutoOnCluster = true,
                androidAutoOnMain = true,
                desiredDisplayId = 0
            )
        )
        assertEquals(
            0,
            DisplayAppLauncher.resolveAndroidAutoMediaCommandDisplayIdForState(
                androidAutoOnCluster = false,
                androidAutoOnMain = true,
                desiredDisplayId = 3
            )
        )
        assertEquals(
            0,
            DisplayAppLauncher.resolveAndroidAutoMediaCommandDisplayIdForState(
                androidAutoOnCluster = false,
                androidAutoOnMain = false,
                desiredDisplayId = 0
            )
        )
        assertEquals(
            0,
            DisplayAppLauncher.resolveAndroidAutoMediaCommandDisplayIdForState(
                androidAutoOnCluster = false,
                androidAutoOnMain = false,
                desiredDisplayId = -1
            )
        )
    }

    @Test
    fun androidAutoCommandsPreferAapRouteWhenLinkStatusIsFalseNegative() {
        assertTrue(
            DisplayAppLauncher.shouldPreferAndroidAutoAapMediaKeyRouteForCommandForTest(
                linkActive = false,
                mediaSessionReady = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPreferAndroidAutoAapMediaKeyRouteForCommandForTest(
                linkActive = true,
                mediaSessionReady = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPreferAndroidAutoAapMediaKeyRouteForCommandForTest(
                linkActive = false,
                mediaSessionReady = false
            )
        )
    }

    @Test
    fun androidAutoPlaybackTargetsRequestVideoFocusDuringMediaPreparation() {
        assertTrue(
            DisplayAppLauncher.shouldRequestAndroidAutoMediaCommandVideoFocusForPlaybackTargetForTest(
                targetPlaying = false
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldRequestAndroidAutoMediaCommandVideoFocusForPlaybackTargetForTest(
                targetPlaying = true
            )
        )
    }

    @Test
    fun androidAutoMediaKeysRequireClusterProjectionAndMediaAction() {
        assertTrue(
            DisplayAppLauncher.shouldHandleAndroidAutoMediaControlKeyForTest(
                isAndroidAutoClusterActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_DOWN,
                now = 1_000L,
                lastKeyCode = 0,
                lastHandledAt = 0L
            )
        )

        assertTrue(
            DisplayAppLauncher.shouldHandleAndroidAutoMediaControlKeyForTest(
                isAndroidAutoClusterActive = true,
                keyCode = 1004,
                action = KeyEvent.ACTION_UP,
                now = 1_000L,
                lastKeyCode = 0,
                lastHandledAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldHandleAndroidAutoMediaControlKeyForTest(
                isAndroidAutoClusterActive = false,
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_DOWN,
                now = 1_000L,
                lastKeyCode = 0,
                lastHandledAt = 0L
            )
        )

        assertTrue(
            DisplayAppLauncher.shouldHandleAndroidAutoMediaControlKeyForTest(
                isAndroidAutoClusterActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                now = 1_000L,
                lastKeyCode = 0,
                lastHandledAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldHandleAndroidAutoMediaControlKeyForTest(
                isAndroidAutoClusterActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = 2,
                now = 1_000L,
                lastKeyCode = 0,
                lastHandledAt = 0L
            )
        )

        assertFalse(
            DisplayAppLauncher.shouldHandleAndroidAutoMediaControlKeyForTest(
                isAndroidAutoClusterActive = true,
                keyCode = KeyEvent.KEYCODE_ENTER,
                action = KeyEvent.ACTION_DOWN,
                now = 1_000L,
                lastKeyCode = 0,
                lastHandledAt = 0L
            )
        )
    }

    @Test
    fun androidAutoMediaKeysAreDebouncedByKeyCode() {
        assertFalse(
            DisplayAppLauncher.shouldHandleAndroidAutoMediaControlKeyForTest(
                isAndroidAutoClusterActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                action = KeyEvent.ACTION_DOWN,
                now = 1_300L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                lastHandledAt = 1_000L,
                cooldownMs = 650L
            )
        )

        assertTrue(
            DisplayAppLauncher.shouldHandleAndroidAutoMediaControlKeyForTest(
                isAndroidAutoClusterActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_DOWN,
                now = 1_300L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                lastHandledAt = 1_000L,
                cooldownMs = 650L
            )
        )
    }

    @Test
    fun nativeRadioPlayingDefersAndroidAutoMediaControl() {
        assertTrue(
            DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMediaForTest("1")
        )
        assertTrue(
            DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMediaForTest(" 1 ")
        )
        assertFalse(
            DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMediaForTest("0")
        )
        assertFalse(
            DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMediaForTest("-1")
        )
        assertFalse(
            DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMediaForTest(null)
        )
        assertTrue(
            DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMediaForTest(
                radioPlayState = "0",
                audioSourceApp = "com.beantechs.radio"
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMediaForTest(
                radioPlayState = "0",
                audioSourceApp = "fm_radio"
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldDeferAndroidAutoMediaControlToNativeMediaForTest(
                radioPlayState = "0",
                audioSourceApp = "com.ts.androidauto.projectionservice"
            )
        )
    }

    @Test
    fun androidAutoAccessibilityDoesNotConsumeToggleKeyEvents() {
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_DOWN,
                now = 1_000L,
                lastKeyCode = 0,
                lastHandledAt = 0L,
                cooldownMs = 650L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                now = 1_120L,
                lastKeyCode = 0,
                lastHandledAt = 0L,
                cooldownMs = 650L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_DOWN,
                now = 1_300L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                lastHandledAt = 1_120L,
                cooldownMs = 650L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                now = 1_420L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                lastHandledAt = 1_120L,
                blockedKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                cooldownMs = 650L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = true,
                keyCode = KeyEvent.KEYCODE_VOLUME_MUTE,
                action = KeyEvent.ACTION_DOWN,
                now = 1_300L,
                lastKeyCode = KeyEvent.KEYCODE_VOLUME_MUTE,
                lastHandledAt = 1_120L,
                cooldownMs = 650L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = true,
                keyCode = KeyEvent.KEYCODE_VOLUME_MUTE,
                action = KeyEvent.ACTION_UP,
                now = 1_420L,
                lastKeyCode = KeyEvent.KEYCODE_VOLUME_MUTE,
                lastHandledAt = 1_120L,
                cooldownMs = 650L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = true,
                keyCode = 1004,
                action = KeyEvent.ACTION_DOWN,
                now = 1_300L,
                lastKeyCode = 1004,
                lastHandledAt = 1_120L,
                cooldownMs = 650L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = true,
                keyCode = 1004,
                action = KeyEvent.ACTION_UP,
                now = 1_420L,
                lastKeyCode = 1004,
                lastHandledAt = 1_120L,
                cooldownMs = 650L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                now = 1_120L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                lastHandledAt = 1_000L,
                cooldownMs = 650L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoAccessibilityToggleKeyForTest(
                isAndroidAutoActive = false,
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                now = 1_120L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                lastHandledAt = 1_000L,
                cooldownMs = 650L
            )
        )
    }

    @Test
    fun androidAutoSteeringFocusKeepAliveRequiresPlayingAndUnmutedAndroidAutoMedia() {
        assertTrue(
            DisplayAppLauncher.shouldPulseAndroidAutoSteeringMediaFocusKeepAliveForState(
                mediaPackageName = "com.ts.androidauto",
                mediaIsPlaying = true,
                mediaIsMuted = false
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldPulseAndroidAutoSteeringMediaFocusKeepAliveForState(
                mediaPackageName = "com.ts.androidauto.app",
                mediaIsPlaying = true,
                mediaIsMuted = false
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPulseAndroidAutoSteeringMediaFocusKeepAliveForState(
                mediaPackageName = "com.ts.androidauto",
                mediaIsPlaying = false,
                mediaIsMuted = false
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPulseAndroidAutoSteeringMediaFocusKeepAliveForState(
                mediaPackageName = "com.ts.androidauto",
                mediaIsPlaying = true,
                mediaIsMuted = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPulseAndroidAutoSteeringMediaFocusKeepAliveForState(
                mediaPackageName = "com.ts.carplay",
                mediaIsPlaying = true,
                mediaIsMuted = false
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPulseAndroidAutoSteeringMediaFocusKeepAliveForState(
                mediaPackageName = null,
                mediaIsPlaying = true,
                mediaIsMuted = false
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldPulseAndroidAutoSteeringMediaFocusKeepAliveForState(
                mediaPackageName = "com.ts.androidauto",
                mediaIsPlaying = true,
                mediaIsMuted = false,
                lastToggleObservedAtMs = 1_000L,
                nowMs = 3_000L
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldPulseAndroidAutoSteeringMediaFocusKeepAliveForState(
                mediaPackageName = "com.ts.androidauto",
                mediaIsPlaying = true,
                mediaIsMuted = false,
                lastToggleObservedAtMs = 1_000L,
                nowMs = 5_500L
            )
        )
    }

    @Test
    fun clusterMediaCommandMapsToAndroidAutoMediaKeys() {
        assertEquals(
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            DisplayAppLauncher.mapAndroidAutoClusterMediaCommandForTest(1)
        )
        assertEquals(
            KeyEvent.KEYCODE_MEDIA_NEXT,
            DisplayAppLauncher.mapAndroidAutoClusterMediaCommandForTest(2)
        )
        assertNull(DisplayAppLauncher.mapAndroidAutoClusterMediaCommandForTest(99))
    }

    @Test
    fun androidAutoClusterMediaCallbacksAreConsumedWithoutSendingMediaCommand() {
        assertTrue(
            DisplayAppLauncher.shouldConsumeAndroidAutoClusterMediaCallbackForTest(
                isAndroidAutoActive = true,
                command = 1
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldConsumeAndroidAutoClusterMediaCallbackForTest(
                isAndroidAutoActive = true,
                command = 2
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoClusterMediaCallbackForTest(
                isAndroidAutoActive = false,
                command = 1
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldConsumeAndroidAutoClusterMediaCallbackForTest(
                isAndroidAutoActive = true,
                command = 99
            )
        )
    }

    @Test
    fun androidAutoAudioPlaybackDumpDetectsStartedMediaPlayer() {
        val dump = """
            match usage USAGE_AAUTO_MEDIA
            ID:111 -- type:android.media.AudioTrack -- u/pid:1000/3728 -- state:started -- attr:AudioAttributes: usage=USAGE_AAUTO_MEDIA content=CONTENT_TYPE_UNKNOWN flags=0x100 tags= bundle=null
            ID:119 -- type:android.media.AudioTrack -- u/pid:1000/3728 -- state:idle -- attr:AudioAttributes: usage=USAGE_AAUTO_VR content=CONTENT_TYPE_UNKNOWN flags=0x100 tags= bundle=null
        """.trimIndent()

        assertTrue(DisplayAppLauncher.hasActiveAndroidAutoAudioPlaybackInDumpForTest(dump))
    }

    @Test
    fun androidAutoAudioPlaybackDumpRejectsIdleOrNonMediaPlayers() {
        val idleDump = """
            match usage USAGE_AAUTO_MEDIA
            ID:111 -- type:android.media.AudioTrack -- u/pid:1000/3728 -- state:idle -- attr:AudioAttributes: usage=USAGE_AAUTO_MEDIA content=CONTENT_TYPE_UNKNOWN flags=0x100 tags= bundle=null
            ID:103 -- type:android.media.AudioTrack -- u/pid:1000/3728 -- state:started -- attr:AudioAttributes: usage=USAGE_AAUTO_GUIDANCE content=CONTENT_TYPE_UNKNOWN flags=0x100 tags= bundle=null
        """.trimIndent()

        assertFalse(DisplayAppLauncher.hasActiveAndroidAutoAudioPlaybackInDumpForTest(idleDump))
    }

    @Test
    fun androidAutoMediaKeysMapToNativeAapHardkeyOrdinals() {
        assertEquals(
            10,
            DisplayAppLauncher.mapAndroidAutoMediaKeyToAapHardkeyOrdinalForTest(
                KeyEvent.KEYCODE_MEDIA_NEXT
            )
        )
        assertEquals(
            9,
            DisplayAppLauncher.mapAndroidAutoMediaKeyToAapHardkeyOrdinalForTest(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS
            )
        )
        assertEquals(
            6,
            DisplayAppLauncher.mapAndroidAutoMediaKeyToAapHardkeyOrdinalForTest(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            )
        )
        assertEquals(
            7,
            DisplayAppLauncher.mapAndroidAutoMediaKeyToAapHardkeyOrdinalForTest(
                KeyEvent.KEYCODE_MEDIA_PLAY
            )
        )
        assertEquals(
            8,
            DisplayAppLauncher.mapAndroidAutoMediaKeyToAapHardkeyOrdinalForTest(
                KeyEvent.KEYCODE_MEDIA_PAUSE
            )
        )
        assertNull(
            DisplayAppLauncher.mapAndroidAutoMediaKeyToAapHardkeyOrdinalForTest(
                KeyEvent.KEYCODE_ENTER
            )
        )
    }

    @Test
    fun androidAutoDashboardPlaybackAapUsesExplicitPlayPauseKeys() {
        assertEquals(
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            DisplayAppLauncher.resolveAndroidAutoDashboardPlaybackAapKeyCodeForTest(
                mediaIsPlayingHint = true
            )
        )
        assertEquals(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            DisplayAppLauncher.resolveAndroidAutoDashboardPlaybackAapKeyCodeForTest(
                mediaIsPlayingHint = false
            )
        )
    }

    @Test
    fun androidAutoMediaKeysMapToOemInputFallbackCodes() {
        assertEquals(
            1003,
            DisplayAppLauncher.mapAndroidAutoMediaKeyToOemInputKeyCodeForTest(
                KeyEvent.KEYCODE_MEDIA_NEXT
            )
        )
        assertEquals(
            1002,
            DisplayAppLauncher.mapAndroidAutoMediaKeyToOemInputKeyCodeForTest(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS
            )
        )
        assertEquals(
            1004,
            DisplayAppLauncher.mapAndroidAutoMediaKeyToOemInputKeyCodeForTest(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            )
        )
        assertEquals(
            1004,
            DisplayAppLauncher.mapAndroidAutoMediaKeyToOemInputKeyCodeForTest(1004)
        )
        assertNull(
            DisplayAppLauncher.mapAndroidAutoMediaKeyToOemInputKeyCodeForTest(
                KeyEvent.KEYCODE_ENTER
            )
        )
        assertNull(
            DisplayAppLauncher.mapAndroidAutoMediaKeyToOemInputKeyCodeForTest(
                KeyEvent.KEYCODE_VOLUME_MUTE
            )
        )
    }

    @Test
    fun androidAutoOemFallbackStaysDisabledForPhysicalMediaKeys() {
        assertFalse(DisplayAppLauncher.isAndroidAutoOemInputMediaFallbackEnabledForTest())
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoOemOnlyMediaRouteForTest(
                KeyEvent.KEYCODE_MEDIA_NEXT
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoOemOnlyMediaRouteForTest(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoOemOnlyMediaRouteForTest(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            )
        )
    }

    @Test
    fun androidAutoPhysicalSkipKeysDoNotUseAppRoute() {
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_DOWN,
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = false
            )
        )
    }

    @Test
    fun androidAutoPhysicalPlaybackKeysDoNotUseAppRoute() {
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_VOLUME_MUTE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = 1004,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_ENTER,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true
            )
        )
    }

    @Test
    fun androidAutoPhysicalMediaKeysKeepImmediateAppRouteDisabledWhenNativeMediaCenterSourceIsActive() {
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                nativeMediaCenterActive = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                nativeMediaCenterActive = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                nativeMediaCenterActive = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = 1004,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                nativeMediaCenterActive = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_DOWN,
                isSteeringInput = true,
                nativeMediaCenterActive = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = false,
                nativeMediaCenterActive = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_VOLUME_MUTE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                nativeMediaCenterActive = true
            )
        )
    }

    @Test
    fun androidAutoPhysicalMediaKeysSuppressAppSideInjection() {
        assertTrue(
            DisplayAppLauncher.shouldSuppressAndroidAutoSteeringMediaInjectionForTest(
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldSuppressAndroidAutoSteeringMediaInjectionForTest(
                isSteeringInput = false
            )
        )
    }

    @Test
    fun androidAutoSteeringInputDedupesImmediateIdenticalCallbacks() {
        assertTrue(
            DisplayAppLauncher.shouldSkipDuplicateAndroidAutoSteeringInputKeyForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                now = 1_004L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                lastAction = KeyEvent.ACTION_UP,
                lastHandledAt = 1_000L
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldSkipDuplicateAndroidAutoSteeringInputKeyForTest(
                keyCode = KeyEvent.KEYCODE_MUTE,
                action = KeyEvent.ACTION_UP,
                now = 1_001L,
                lastKeyCode = KeyEvent.KEYCODE_MUTE,
                lastAction = KeyEvent.ACTION_UP,
                lastHandledAt = 1_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldSkipDuplicateAndroidAutoSteeringInputKeyForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                now = 1_400L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                lastAction = KeyEvent.ACTION_UP,
                lastHandledAt = 1_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldSkipDuplicateAndroidAutoSteeringInputKeyForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_DOWN,
                now = 1_004L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                lastAction = KeyEvent.ACTION_UP,
                lastHandledAt = 1_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldSkipDuplicateAndroidAutoSteeringInputKeyForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                now = 1_004L,
                lastKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                lastAction = KeyEvent.ACTION_UP,
                lastHandledAt = 1_000L
            )
        )
    }

    @Test
    fun androidAutoPhysicalPlaybackKeysDoNotUseAppRouteOnDownOrNonSteering() {
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_DOWN,
                isSteeringInput = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = false
            )
        )
    }

    @Test
    fun androidAutoSteeringPlaybackTargetIsDerivedFromCurrentState() {
        assertFalse(
            DisplayAppLauncher.resolveAndroidAutoSteeringPlaybackTargetForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                mediaIsPlaying = true
            )!!
        )
        assertTrue(
            DisplayAppLauncher.resolveAndroidAutoSteeringPlaybackTargetForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                mediaIsPlaying = false
            )!!
        )
        assertTrue(
            DisplayAppLauncher.resolveAndroidAutoSteeringPlaybackTargetForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                mediaIsPlaying = true,
                nativeMediaCenterIsPlaying = false
            )!!
        )
        assertFalse(
            DisplayAppLauncher.resolveAndroidAutoSteeringPlaybackTargetForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                mediaIsPlaying = false,
                nativeMediaCenterIsPlaying = true
            )!!
        )
        assertTrue(
            DisplayAppLauncher.resolveAndroidAutoSteeringPlaybackTargetForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY,
                mediaIsPlaying = false
            )!!
        )
        assertFalse(
            DisplayAppLauncher.resolveAndroidAutoSteeringPlaybackTargetForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE,
                mediaIsPlaying = true
            )!!
        )
        assertNull(
            DisplayAppLauncher.resolveAndroidAutoSteeringPlaybackTargetForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                mediaIsPlaying = true
            )
        )
    }

    @Test
    fun androidAutoSteeringPlaybackReconcileUsesNativeMediaCenterForPhysicalPlaybackKeys() {
        assertTrue(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                useAppCommandRoute = false,
                nativeMediaCenterActive = true,
                androidAutoDesiredOnCluster = true
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                useAppCommandRoute = false,
                nativeMediaCenterActive = true,
                androidAutoDesiredOnCluster = true
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                useAppCommandRoute = false,
                nativeMediaCenterActive = true,
                androidAutoDesiredOnCluster = true
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = 1004,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                useAppCommandRoute = false,
                nativeMediaCenterActive = true,
                androidAutoDesiredOnCluster = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                useAppCommandRoute = false,
                nativeMediaCenterActive = true,
                androidAutoDesiredOnCluster = false
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_DOWN,
                isSteeringInput = true,
                useAppCommandRoute = false,
                nativeMediaCenterActive = true,
                androidAutoDesiredOnCluster = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                useAppCommandRoute = false,
                nativeMediaCenterActive = true,
                androidAutoDesiredOnCluster = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = false,
                useAppCommandRoute = false,
                nativeMediaCenterActive = true,
                androidAutoDesiredOnCluster = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                useAppCommandRoute = true,
                nativeMediaCenterActive = true,
                androidAutoDesiredOnCluster = true
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldScheduleAndroidAutoSteeringPlaybackTargetReconcileForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true,
                useAppCommandRoute = false,
                nativeMediaCenterActive = false,
                androidAutoDesiredOnCluster = true
            )
        )
    }

    @Test
    fun androidAutoStaleVisualCleanupKeepsActiveRecentStacksAndDropsStaleHints() {
        assertFalse(
            DisplayAppLauncher.shouldRemoveStaleAndroidAutoVisualStacksForTest(
                usbConfigured = false,
                androidAutoLinkActive = false,
                hasAndroidAutoTasks = true,
                desiredOnCluster = true,
                nativeMediaCenterActive = false,
                firstSeenAtMs = 1_000L,
                nowMs = 40_000L,
                graceMs = 30_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRemoveStaleAndroidAutoVisualStacksForTest(
                usbConfigured = false,
                androidAutoLinkActive = false,
                hasAndroidAutoTasks = true,
                desiredOnCluster = false,
                nativeMediaCenterActive = true,
                firstSeenAtMs = 1_000L,
                nowMs = 40_000L,
                graceMs = 30_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldRemoveStaleAndroidAutoVisualStacksForTest(
                usbConfigured = false,
                androidAutoLinkActive = false,
                hasAndroidAutoTasks = true,
                desiredOnCluster = false,
                nativeMediaCenterActive = false,
                firstSeenAtMs = 1_000L,
                nowMs = 20_000L,
                graceMs = 30_000L
            )
        )
        assertTrue(
            DisplayAppLauncher.shouldRemoveStaleAndroidAutoVisualStacksForTest(
                usbConfigured = false,
                androidAutoLinkActive = false,
                hasAndroidAutoTasks = true,
                desiredOnCluster = false,
                nativeMediaCenterActive = false,
                firstSeenAtMs = 1_000L,
                nowMs = 40_000L,
                graceMs = 30_000L
            )
        )
    }

    @Test
    fun androidAutoPlaybackToggleUsesDirectPauseOrPlayTransaction() {
        assertEquals(
            0x1d,
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                musicStatus = 1,
                mediaIsPlaying = false
            )
        )
        assertNull(
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                musicStatus = 2,
                mediaIsPlaying = true
            )
        )
        assertEquals(
            0x1d,
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                musicStatus = 0,
                mediaIsPlaying = true
            )
        )
        assertEquals(
            0x1c,
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY,
                musicStatus = 1,
                mediaIsPlaying = true
            )
        )
        assertEquals(
            0x1d,
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE,
                musicStatus = 2,
                mediaIsPlaying = false
            )
        )
        assertNull(
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                musicStatus = 1,
                mediaIsPlaying = true
            )
        )
    }

    @Test
    fun androidAutoPlaybackToggleDoesNotReplayAfterNativePause() {
        assertNull(
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForObservedStateForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                musicStatusBefore = 1,
                musicStatusAfterNative = 2,
                progressBefore = 181,
                progressAfterNative = 181,
                mediaIsPlaying = true
            )
        )
        assertNull(
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForObservedStateForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                musicStatusBefore = 0,
                musicStatusAfterNative = 2,
                progressBefore = 181,
                progressAfterNative = 181,
                mediaIsPlaying = false
            )
        )
        assertEquals(
            0x1c,
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForObservedStateForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                musicStatusBefore = 2,
                musicStatusAfterNative = 2,
                progressBefore = 181,
                progressAfterNative = 181,
                mediaIsPlaying = false
            )
        )
    }

    @Test
    fun androidAutoPlaybackToggleCanPauseAlreadyPlayingMusicWithIncompleteStatus() {
        assertEquals(
            0x1d,
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForObservedStateForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                musicStatusBefore = 0,
                musicStatusAfterNative = 0,
                progressBefore = 181,
                progressAfterNative = 181,
                mediaIsPlaying = false
            )
        )
        assertEquals(
            0x1d,
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForObservedStateForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                musicStatusBefore = null,
                musicStatusAfterNative = null,
                progressBefore = 181,
                progressAfterNative = 182,
                mediaIsPlaying = false
            )
        )
    }

    @Test
    fun androidAutoPlaybackToggleDoesNotPauseAgainAfterNativeResume() {
        assertNull(
            DisplayAppLauncher.resolveAndroidAutoPlaybackTransactionForObservedStateForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                musicStatusBefore = 2,
                musicStatusAfterNative = 1,
                progressBefore = 181,
                progressAfterNative = 182,
                mediaIsPlaying = false
            )
        )
    }

    @Test
    fun androidAutoMediaControlCanUseProjectionMediaStateWhenNoClusterTaskIsVisible() {
        assertTrue(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = "com.ts.androidauto",
                activeClusterProjectionPackage = null
            )
        )
        assertTrue(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = null,
                activeClusterProjectionPackage = "com.ts.androidauto.app"
            )
        )
        assertFalse(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = "com.ts.carplay",
                activeClusterProjectionPackage = null
            )
        )
        assertFalse(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = "com.ts.androidauto",
                activeClusterProjectionPackage = null,
                androidAutoSessionReady = false
            )
        )
        assertFalse(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = null,
                activeClusterProjectionPackage = "com.ts.androidauto.app",
                androidAutoSessionReady = false
            )
        )
        assertFalse(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = "com.ts.androidauto",
                mediaPackageName = null,
                activeClusterProjectionPackage = null,
                androidAutoSessionReady = false
            )
        )
    }

    @Test
    fun androidAutoMediaControlCanUseActiveLinkWhenMetadataIsMissing() {
        assertTrue(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = null,
                activeClusterProjectionPackage = null,
                androidAutoLinkStatus = 3
            )
        )
        assertTrue(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = null,
                activeClusterProjectionPackage = null,
                androidAutoLinkStatus = 7
            )
        )
        assertTrue(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = null,
                activeClusterProjectionPackage = null,
                androidAutoTaskPresent = true
            )
        )
        assertFalse(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = null,
                activeClusterProjectionPackage = null,
                androidAutoTaskPresent = true,
                androidAutoSessionReady = false
            )
        )
        assertFalse(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = null,
                activeClusterProjectionPackage = null,
                androidAutoLinkStatus = 1,
                androidAutoTaskPresent = false
            )
        )
    }

    @Test
    fun androidAutoMediaControlCanUseAudioPlaybackEvidenceWhenUiStateIsMissing() {
        assertTrue(
            DisplayAppLauncher.isAndroidAutoActiveForMediaControlForTest(
                activeProjectionPackage = null,
                mediaPackageName = null,
                activeClusterProjectionPackage = null,
                androidAutoLinkStatus = null,
                androidAutoTaskPresent = false,
                androidAutoSessionReady = false,
                androidAutoAudioPlaybackActive = true
            )
        )
    }

    @Test
    fun androidAutoNativeStatusDescriptionsAreStable() {
        assertEquals(
            "ACTIVATED(3)",
            DisplayAppLauncher.describeAndroidAutoLinkStatusForTest(3)
        )
        assertEquals(
            "AAP_FRX(8)",
            DisplayAppLauncher.describeAndroidAutoLinkStatusForTest(8)
        )
        assertEquals(
            "PLAYING(1)",
            DisplayAppLauncher.describeAndroidAutoMusicStatusForTest(1)
        )
        assertEquals(
            "PAUSED(2)",
            DisplayAppLauncher.describeAndroidAutoMusicStatusForTest(2)
        )
    }

    @Test
    fun androidAutoOemInputFallbackEchoIsSkippedDuringBlockWindow() {
        assertTrue(
            DisplayAppLauncher.shouldSkipAndroidAutoOemInputFallbackEchoForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                now = 2_000L,
                echoKeyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                echoBlockUntil = 3_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldSkipAndroidAutoOemInputFallbackEchoForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                now = 3_001L,
                echoKeyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                echoBlockUntil = 3_000L
            )
        )
        assertFalse(
            DisplayAppLauncher.shouldSkipAndroidAutoOemInputFallbackEchoForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                action = KeyEvent.ACTION_UP,
                now = 2_000L,
                echoKeyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                echoBlockUntil = 3_000L
            )
        )
    }

    @Test
    fun androidAutoHardKeyPolicyDebugCommandsMapToNativePolicyKeyCodes() {
        assertEquals(
            1003,
            DisplayAppLauncher.mapAndroidAutoHardKeyPolicyDebugCommandForTest(
                "aa_hardkey_next"
            )?.keyCode
        )
        assertEquals(
            1002,
            DisplayAppLauncher.mapAndroidAutoHardKeyPolicyDebugCommandForTest(
                "aa_hardkey_prev_d3"
            )?.keyCode
        )
        assertEquals(
            3,
            DisplayAppLauncher.mapAndroidAutoHardKeyPolicyDebugCommandForTest(
                "aa_hardkey_prev_d3"
            )?.targetDisplayId
        )
        assertEquals(
            1004,
            DisplayAppLauncher.mapAndroidAutoHardKeyPolicyDebugCommandForTest(
                "aa_hardkey_pause_d0"
            )?.keyCode
        )
        assertEquals(
            0,
            DisplayAppLauncher.mapAndroidAutoHardKeyPolicyDebugCommandForTest(
                "aa_hardkey_pause_d0"
            )?.targetDisplayId
        )
    }

    @Test
    fun androidAutoHardKeyPolicyDebugCommandsRejectMuteAndUnknownTargets() {
        assertEquals(
            null,
            DisplayAppLauncher.mapAndroidAutoHardKeyPolicyDebugCommandForTest(
                "aa_hardkey_mute"
            )
        )
        assertEquals(
            null,
            DisplayAppLauncher.mapAndroidAutoHardKeyPolicyDebugCommandForTest(
                "aa_hardkey_next_d2"
            )
        )
    }
}
