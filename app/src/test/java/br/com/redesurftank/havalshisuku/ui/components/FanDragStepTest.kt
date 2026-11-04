package br.com.redesurftank.havalshisuku.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FanDragStepTest {
    private val step = 40f

    @Test
    fun belowOneStepKeepsSpeed() {
        assertEquals(3 to 39f, resolveFanDrag(39f, step, 3))
    }

    @Test
    fun oneStepMovesOneLevel() {
        assertEquals(4 to 0f, resolveFanDrag(40f, step, 3))
        assertEquals(2 to 0f, resolveFanDrag(-40f, step, 3))
    }

    @Test
    fun levelOneToOffNeedsTwoSteps() {
        assertEquals(1 to -79f, resolveFanDrag(-79f, step, 1))
        assertEquals(0 to 0f, resolveFanDrag(-80f, step, 1))
    }

    @Test
    fun levelOneGoingUpStaysSingleStep() {
        assertEquals(2 to 0f, resolveFanDrag(40f, step, 1))
    }

    @Test
    fun pinnedAtOffDropsLeftover() {
        assertEquals(0 to 0f, resolveFanDrag(-200f, step, 2))
    }

    @Test
    fun pinnedAtMaxDropsLeftover() {
        assertEquals(7 to 0f, resolveFanDrag(300f, step, 6))
    }
}
