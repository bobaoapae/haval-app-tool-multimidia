package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayPatchManagerTest {

    @Test
    fun parseProcessIdsIgnoresShellNoiseAndInvalidTokens() {
        assertEquals(
            setOf(3620, 3916),
            CarPlayPatchManager.parseProcessIds(" 3620  noise  -1  3916 ")
        )
    }

    @Test
    fun replacementProcessIdsRequiresANewGeneration() {
        assertTrue(
            CarPlayPatchManager.replacementProcessIds(
                originalPids = setOf(3620),
                currentPids = setOf(3620)
            ).isEmpty()
        )
        assertEquals(
            setOf(5812),
            CarPlayPatchManager.replacementProcessIds(
                originalPids = setOf(3620),
                currentPids = setOf(5812)
            )
        )
    }

    @Test
    fun processStartedFromAbsentStateCountsAsReplacement() {
        assertEquals(
            setOf(5841),
            CarPlayPatchManager.replacementProcessIds(
                originalPids = emptySet(),
                currentPids = setOf(5841)
            )
        )
    }

    @Test
    fun parseOverlayEnabledReadsEnabledAndDisabledStates() {
        assertTrue(
            CarPlayPatchManager.parseOverlayEnabled(
                "[x] com.android.systemui.theme.dark",
                "com.android.systemui.theme.dark"
            ) == true
        )
        assertFalse(
            CarPlayPatchManager.parseOverlayEnabled(
                "[ ] com.android.systemui.theme.dark",
                "com.android.systemui.theme.dark"
            ) ?: true
        )
    }

    @Test
    fun parseOverlayEnabledDoesNotGuessWhenPackageIsMissing() {
        assertNull(
            CarPlayPatchManager.parseOverlayEnabled(
                "[x] com.android.systemui.skin.B03G.v17",
                "com.android.systemui.theme.dark"
            )
        )
    }
}
