package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.ClusterKey
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun rightCyclesForwardThroughZeroOneAndThree() {
        assertEquals(1, ClusterCardNavigationPolicy.nextCard(0, ClusterKey.RIGHT))
        assertEquals(3, ClusterCardNavigationPolicy.nextCard(1, ClusterKey.RIGHT))
        assertEquals(0, ClusterCardNavigationPolicy.nextCard(3, ClusterKey.RIGHT))
    }

    @Test
    fun leftCyclesBackwardThroughZeroOneAndThree() {
        assertEquals(3, ClusterCardNavigationPolicy.nextCard(0, ClusterKey.LEFT))
        assertEquals(1, ClusterCardNavigationPolicy.nextCard(3, ClusterKey.LEFT))
        assertEquals(0, ClusterCardNavigationPolicy.nextCard(1, ClusterKey.LEFT))
    }
}
