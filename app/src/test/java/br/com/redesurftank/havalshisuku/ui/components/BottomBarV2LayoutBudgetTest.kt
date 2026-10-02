package br.com.redesurftank.havalshisuku.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarV2LayoutBudgetTest {

    // 1920dp display (160dpi), 128dp navigation pane + 16dp margin, 8dp end padding.
    private val contentWidthWithPane = 1920f - 144f - 8f
    private val contentWidthPaneHidden = 1920f - 16f - 8f

    @Test
    fun estimateDockWidth_defaultDock_matchesMeasuredLayout() {
        // back + apps + projection + 7 apps + vehicle + home, 10dp gaps.
        val width = estimateDockWidthDp(
            appTiles = 7,
            showProjectionTile = true,
            showDynamicSlot = false,
            editMode = false
        )
        assertEquals(694f, width, 0.01f)
    }

    @Test
    fun estimateDockWidth_dynamicSlotAddsSeparatorTileAndTwoGaps() {
        val base = estimateDockWidthDp(7, true, showDynamicSlot = false, editMode = false)
        val withDynamic = estimateDockWidthDp(7, true, showDynamicSlot = true, editMode = false)
        assertEquals(base + 5f + 50f + 20f, withDynamic, 0.01f)
    }

    @Test
    fun clearance_defaultDockWithPane_leavesRoomForLeftGroup() {
        val dock = estimateDockWidthDp(7, true, false, false)
        assertTrue(resolveLeftGroupClearanceDp(contentWidthWithPane, dock) >= V2_MIN_LEFT_CLEARANCE_DP)
    }

    @Test
    fun clearance_worstCaseEditModeWithDynamicSlot_stillFitsAtFullHd() {
        val dock = estimateDockWidthDp(7, true, true, true)
        assertTrue(resolveLeftGroupClearanceDp(contentWidthWithPane, dock) >= V2_MIN_LEFT_CLEARANCE_DP)
    }

    @Test
    fun clearance_paneHiddenGivesMoreRoomThanPaneShown() {
        val dock = estimateDockWidthDp(7, true, true, false)
        assertTrue(
            resolveLeftGroupClearanceDp(contentWidthPaneHidden, dock) >
                resolveLeftGroupClearanceDp(contentWidthWithPane, dock)
        )
    }

    @Test
    fun clearance_narrowContent_reportsOverlapSoDynamicSlotCanBeSkipped() {
        val dock = estimateDockWidthDp(7, true, true, false)
        assertFalse(resolveLeftGroupClearanceDp(1400f, dock) >= V2_MIN_LEFT_CLEARANCE_DP)
    }

    @Test
    fun clearance_unknownWindowWidth_neverBlocksTheSlot() {
        val dock = estimateDockWidthDp(7, true, true, true)
        assertTrue(resolveLeftGroupClearanceDp(Float.MAX_VALUE, dock) >= V2_MIN_LEFT_CLEARANCE_DP)
    }
}
