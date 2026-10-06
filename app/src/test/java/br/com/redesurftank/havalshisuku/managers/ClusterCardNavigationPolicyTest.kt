package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.ClusterKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterCardNavigationPolicyTest {
    @Test
    fun beanTechActionUpIsHandledButActionDownIsIgnored() {
        assertTrue(ClusterCardNavigationPolicy.shouldHandleInputAction(1))
        assertFalse(ClusterCardNavigationPolicy.shouldHandleInputAction(0))
    }

    @Test
    fun onlyLeftAndRightOwnCardNavigation() {
        assertTrue(ClusterCardNavigationPolicy.isCardNavigationKey(ClusterKey.LEFT))
        assertTrue(ClusterCardNavigationPolicy.isCardNavigationKey(ClusterKey.RIGHT))
        assertFalse(ClusterCardNavigationPolicy.isCardNavigationKey(ClusterKey.DOWN))
        assertFalse(ClusterCardNavigationPolicy.isCardNavigationKey(ClusterKey.ENTER))
        assertFalse(ClusterCardNavigationPolicy.isCardNavigationKey(ClusterKey.HOME))
    }

    // No nextCard() tests: the policy no longer predicts the next card. The car owns the
    // active card and reports it via msgId=133. The removed {0, 1, 3} ring did not match
    // the car anyway — card 0 has vehicle-state-dependent sub-pages (two while charging)
    // that all report cardId 0, so any fixed ring drifts out of sync.
}
