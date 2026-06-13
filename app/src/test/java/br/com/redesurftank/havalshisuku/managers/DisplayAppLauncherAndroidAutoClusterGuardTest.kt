package br.com.redesurftank.havalshisuku.managers

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAppLauncherAndroidAutoClusterGuardTest {
    private val impulsePackage = "br.com.redesurftank.havalshisuku"

    @Test
    fun normalDisplayZeroWindowUsesAndroidAutoFullscreenFocusGuard() {
        assertEquals(
            "FULLSCREEN_AND_FOCUS",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.android.settings",
                selfPackageName = impulsePackage
            )
        )
    }

    @Test
    fun nativeDisplayZeroPanelsUsePassiveAndroidAutoWindowGuard() {
        assertEquals(
            "VERIFY_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.beantechs.hvac",
                selfPackageName = impulsePackage
            )
        )
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
                packageName = "com.beantechs.launcher",
                selfPackageName = impulsePackage
            )
        )
        assertEquals(
            "VERIFY_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = "com.beantechs.applist",
                selfPackageName = impulsePackage
            )
        )
        assertEquals(
            "VERIFY_ONLY",
            DisplayAppLauncher.resolveAndroidAutoWindowFocusGuardActionForTest(
                packageName = impulsePackage,
                selfPackageName = impulsePackage
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
    fun androidAutoAccessibilityPassesToggleDownAndConsumesToggleUp() {
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
        assertTrue(
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
        assertTrue(
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
        assertTrue(
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
        assertTrue(
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
    fun androidAutoPhysicalSkipKeysUseAppCommandRouteOnlyOnActionUp() {
        assertTrue(
            DisplayAppLauncher.shouldUseAndroidAutoSteeringAppCommandRouteForTest(
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                action = KeyEvent.ACTION_UP,
                isSteeringInput = true
            )
        )
        assertTrue(
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
}
