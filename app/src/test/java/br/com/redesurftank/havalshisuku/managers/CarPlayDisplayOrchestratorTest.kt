package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayDisplayOrchestratorTest {
    @Test
    fun disconnectedWhenNoVisualTaskExists() {
        assertEquals(
            CarPlayDisplayState.DISCONNECTED,
            CarPlayDisplayOrchestrator.resolveObservedState(
                carPlayOnD0 = false,
                carPlayOnD3 = false
            )
        )
    }

    @Test
    fun connectedOnD0WhenOnlyMainDisplayHasCarPlay() {
        assertEquals(
            CarPlayDisplayState.CONNECTED_ON_D0,
            CarPlayDisplayOrchestrator.resolveObservedState(
                carPlayOnD0 = true,
                carPlayOnD3 = false
            )
        )
    }

    @Test
    fun mirroredOnD3TakesPriorityOverDuplicateD0Task() {
        assertEquals(
            CarPlayDisplayState.MIRRORED_ON_D3,
            CarPlayDisplayOrchestrator.resolveObservedState(
                carPlayOnD0 = true,
                carPlayOnD3 = true
            )
        )
    }

    @Test
    fun clusterHandoffInProgressWhilePreparingD3StateIsActive() {
        assertTrue(
            CarPlayDisplayOrchestrator.isClusterHandoffInProgressForTest(
                state = CarPlayDisplayState.PREPARING_D3,
                preparingD3Flag = false
            )
        )
    }

    @Test
    fun clusterHandoffInProgressWhilePreparingD3FlagIsActive() {
        assertTrue(
            CarPlayDisplayOrchestrator.isClusterHandoffInProgressForTest(
                state = CarPlayDisplayState.CONNECTED_ON_D0,
                preparingD3Flag = true
            )
        )
    }

    @Test
    fun stableClusterStateDoesNotDeferGuards() {
        assertFalse(
            CarPlayDisplayOrchestrator.isClusterHandoffInProgressForTest(
                state = CarPlayDisplayState.MIRRORED_ON_D3,
                preparingD3Flag = false
            )
        )
    }

    @Test
    fun mainHandoffInProgressWhileReturningToD0() {
        assertTrue(
            CarPlayDisplayOrchestrator.isMainHandoffInProgressForTest(
                state = CarPlayDisplayState.RETURNING_TO_D0
            )
        )
    }

    @Test
    fun stableD3StateIsNotMainHandoff() {
        assertFalse(
            CarPlayDisplayOrchestrator.isMainHandoffInProgressForTest(
                state = CarPlayDisplayState.MIRRORED_ON_D3
            )
        )
    }
}
