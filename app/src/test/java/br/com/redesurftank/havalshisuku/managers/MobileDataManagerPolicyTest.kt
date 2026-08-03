package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileDataManagerPolicyTest {
    @Test
    fun defaultOffWithoutOwnershipDoesNothing() {
        assertEquals(
            MobileDataManager.OwnedBlockAction.NONE,
            MobileDataManager.decideOwnedBlockActionForTest(
                desiredBlocked = false,
                disabledByApp = false,
                lastApplied = null
            )
        )
    }

    @Test
    fun releasesOnlyStatePreviouslyDisabledByApp() {
        assertEquals(
            MobileDataManager.OwnedBlockAction.ENABLE,
            MobileDataManager.decideOwnedBlockActionForTest(
                desiredBlocked = false,
                disabledByApp = true,
                lastApplied = true
            )
        )
    }

    @Test
    fun reassertsPersistedBlockAfterProcessRestart() {
        assertEquals(
            MobileDataManager.OwnedBlockAction.DISABLE,
            MobileDataManager.decideOwnedBlockActionForTest(
                desiredBlocked = true,
                disabledByApp = true,
                lastApplied = null
            )
        )
    }

    @Test
    fun reassertsConfirmedBlockToCounterHotspotReenable() {
        assertEquals(
            MobileDataManager.OwnedBlockAction.DISABLE,
            MobileDataManager.decideOwnedBlockActionForTest(
                desiredBlocked = true,
                disabledByApp = true,
                lastApplied = true,
                reassertBlock = true
            )
        )
    }

    @Test
    fun doesNotRepeatOwnedBlockWithoutReassertPolicy() {
        assertEquals(
            MobileDataManager.OwnedBlockAction.NONE,
            MobileDataManager.decideOwnedBlockActionForTest(
                desiredBlocked = true,
                disabledByApp = true,
                lastApplied = true
            )
        )
    }
}
