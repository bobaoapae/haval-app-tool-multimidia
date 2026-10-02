package br.com.redesurftank.havalshisuku.managers

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ClusterBackgroundSyncTest {

    @Before
    fun setUp() {
        ClusterBackgroundSync.resetForTests()
    }

    @After
    fun tearDown() {
        ClusterBackgroundSync.resetForTests()
    }

    @Test
    fun holdSkipsWhenD1NotAttached() {
        assertFalse(
            ClusterBackgroundSync.shouldHoldNativeMasks(
                d1Attached = false,
                appOnDisplay1 = false,
                stillWallpaperExpected = true,
                d1Ready = false
            )
        )
    }

    @Test
    fun holdSkipsWhenAppCoversD1() {
        assertFalse(
            ClusterBackgroundSync.shouldHoldNativeMasks(
                d1Attached = true,
                appOnDisplay1 = true,
                stillWallpaperExpected = true,
                d1Ready = false
            )
        )
    }

    @Test
    fun holdSkipsWhenNoStillWallpaperExpected() {
        assertFalse(
            ClusterBackgroundSync.shouldHoldNativeMasks(
                d1Attached = true,
                appOnDisplay1 = false,
                stillWallpaperExpected = false,
                d1Ready = false
            )
        )
    }

    @Test
    fun holdActiveUntilD1Ready() {
        assertTrue(
            ClusterBackgroundSync.shouldHoldNativeMasks(
                d1Attached = true,
                appOnDisplay1 = false,
                stillWallpaperExpected = true,
                d1Ready = false
            )
        )
        assertFalse(
            ClusterBackgroundSync.shouldHoldNativeMasks(
                d1Attached = true,
                appOnDisplay1 = false,
                stillWallpaperExpected = true,
                d1Ready = true
            )
        )
    }

    @Test
    fun readyIdentityMustMatch() {
        ClusterBackgroundSync.markD1Attached()
        ClusterBackgroundSync.markD1Ready("a|THEME|car-bg.png|minimalist")
        assertTrue(ClusterBackgroundSync.isD1Ready("a|THEME|car-bg.png|minimalist"))
        assertFalse(ClusterBackgroundSync.isD1Ready("a|THEME|other.png|minimalist"))
    }

    @Test
    fun markNotReadyClearsAndNotifies() {
        val notifications = AtomicInteger(0)
        val listener = ClusterBackgroundSync.Listener { notifications.incrementAndGet() }
        ClusterBackgroundSync.addListener(listener)
        try {
            ClusterBackgroundSync.markD1Attached()
            ClusterBackgroundSync.markD1Ready("id-1")
            val afterReady = notifications.get()
            assertTrue(afterReady >= 1)
            ClusterBackgroundSync.markD1NotReady()
            assertFalse(ClusterBackgroundSync.isD1Ready("id-1"))
            assertTrue(notifications.get() > afterReady)
        } finally {
            ClusterBackgroundSync.removeListener(listener)
        }
    }

    @Test
    fun detachClearsReadyState() {
        ClusterBackgroundSync.markD1Attached()
        ClusterBackgroundSync.markD1Ready("id-1")
        ClusterBackgroundSync.markD1Detached()
        assertFalse(ClusterBackgroundSync.isD1Attached())
        assertFalse(ClusterBackgroundSync.isD1Ready("id-1"))
    }
}
