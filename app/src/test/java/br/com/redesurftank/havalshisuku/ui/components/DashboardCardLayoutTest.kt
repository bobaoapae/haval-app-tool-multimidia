package br.com.redesurftank.havalshisuku.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardCardLayoutTest {
    @Test
    fun normalizesMissingDuplicateAndUnknownCardOrder() {
        assertEquals(
                listOf(DashboardCardId.HVAC, DashboardCardId.MEDIA, DashboardCardId.DYNAMICS),
                normalizeDashboardCardOrder("hvac,unknown,media,hvac")
        )
    }

    @Test
    fun serializesNormalizedCardOrder() {
        assertEquals(
                "dynamics,media,hvac",
                dashboardCardOrderToStorage(
                        listOf(DashboardCardId.DYNAMICS, DashboardCardId.MEDIA, DashboardCardId.MEDIA)
                )
        )
    }

    @Test
    fun movesCardOneSlotAtATime() {
        val order =
                listOf(DashboardCardId.MEDIA, DashboardCardId.DYNAMICS, DashboardCardId.HVAC)

        assertEquals(
                listOf(DashboardCardId.DYNAMICS, DashboardCardId.MEDIA, DashboardCardId.HVAC),
                moveDashboardCard(order, DashboardCardId.MEDIA, 1)
        )

        assertEquals(
                listOf(DashboardCardId.MEDIA, DashboardCardId.HVAC, DashboardCardId.DYNAMICS),
                moveDashboardCard(order, DashboardCardId.HVAC, -1)
        )
    }

    @Test
    fun keepsCardAtEdgesWhenMoveWouldLeaveBounds() {
        val order =
                listOf(DashboardCardId.MEDIA, DashboardCardId.DYNAMICS, DashboardCardId.HVAC)

        assertEquals(order, moveDashboardCard(order, DashboardCardId.MEDIA, -1))
        assertEquals(order, moveDashboardCard(order, DashboardCardId.HVAC, 1))
    }

    @Test
    fun formatsFuelLitersAndRangeForDynamicsCard() {
        assertEquals("27.5 L - 420 km", formatDashboardFuelLitersAndRange("50", "420"))
        assertEquals("27.5 L", formatDashboardFuelLitersAndRange("50", "--"))
        assertEquals("420 km", formatDashboardFuelLitersAndRange("--", "420"))
        assertEquals("--", formatDashboardFuelLitersAndRange("--", "--"))
    }

    @Test
    fun clampsDashboardMediaVolumeDeltas() {
        assertEquals(11, resolveDashboardMediaVolumeAfterDelta(10, 1))
        assertEquals(9, resolveDashboardMediaVolumeAfterDelta(10, -1))
        assertEquals(30, resolveDashboardMediaVolumeAfterDelta(30, 1))
        assertEquals(0, resolveDashboardMediaVolumeAfterDelta(0, -1))
    }

    @Test
    fun dashboardCollapseRequiresDownwardDragPastThreshold() {
        assertEquals(false, shouldCollapseDashboardAfterDrag(80f))
        assertEquals(true, shouldCollapseDashboardAfterDrag(81f))
        assertEquals(false, shouldCollapseDashboardAfterDrag(-81f))
        assertEquals(false, shouldCollapseDashboardAfterDrag(120f, 120f))
        assertEquals(true, shouldCollapseDashboardAfterDrag(120f, 20f))
    }

    @Test
    fun dashboardCollapseDragRequiresVerticalDominance() {
        assertEquals(true, isDashboardCollapseDragMostlyVertical(120f, 0f))
        assertEquals(true, isDashboardCollapseDragMostlyVertical(120f, 80f))
        assertEquals(false, isDashboardCollapseDragMostlyVertical(120f, 120f))
        assertEquals(false, isDashboardCollapseDragMostlyVertical(-120f, 0f))
    }

    @Test
    fun resolvesDashboardShortcutButtonByAssignedAction() {
        assertEquals(
                1,
                resolveImpulseDashboardShortcutButton("toggle_impulse_dashboard", "default")
        )
        assertEquals(
                2,
                resolveImpulseDashboardShortcutButton("default", "toggle_impulse_dashboard")
        )
        assertEquals(null, resolveImpulseDashboardShortcutButton("default", "default"))
    }

    @Test
    fun selectingDashboardShortcutMovesDashboardActionToChosenButton() {
        assertEquals(
                DashboardShortcutActions(
                        button1Action = "toggle_impulse_dashboard",
                        button2Action = "default"
                ),
                resolveImpulseDashboardShortcutActions(
                        currentButton1Action = "default",
                        currentButton2Action = "toggle_impulse_dashboard",
                        selectedButton = 1
                )
        )
        assertEquals(
                DashboardShortcutActions(
                        button1Action = "open_app",
                        button2Action = "toggle_impulse_dashboard"
                ),
                resolveImpulseDashboardShortcutActions(
                        currentButton1Action = "open_app",
                        currentButton2Action = "default",
                        selectedButton = 2
                )
        )
    }
}
