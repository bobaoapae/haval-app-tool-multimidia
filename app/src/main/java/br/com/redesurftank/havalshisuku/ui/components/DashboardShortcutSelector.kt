package br.com.redesurftank.havalshisuku.ui.components

import br.com.redesurftank.havalshisuku.models.SteeringWheelCustomActionType

internal data class DashboardShortcutActions(
        val button1Action: String,
        val button2Action: String
)

internal fun resolveImpulseDashboardShortcutButton(
        button1Action: String?,
        button2Action: String?
): Int? {
        val dashboardAction = SteeringWheelCustomActionType.TOGGLE_IMPULSE_DASHBOARD.key
        return when {
                button1Action == dashboardAction -> 1
                button2Action == dashboardAction -> 2
                else -> null
        }
}

internal fun resolveImpulseDashboardShortcutActions(
        currentButton1Action: String?,
        currentButton2Action: String?,
        selectedButton: Int
): DashboardShortcutActions {
        val defaultAction = SteeringWheelCustomActionType.DEFAULT.key
        val dashboardAction = SteeringWheelCustomActionType.TOGGLE_IMPULSE_DASHBOARD.key
        val button1Action = currentButton1Action ?: defaultAction
        val button2Action = currentButton2Action ?: defaultAction

        return when (selectedButton) {
                1 ->
                        DashboardShortcutActions(
                                button1Action = dashboardAction,
                                button2Action =
                                        if (button2Action == dashboardAction) defaultAction
                                        else button2Action
                        )
                2 ->
                        DashboardShortcutActions(
                                button1Action =
                                        if (button1Action == dashboardAction) defaultAction
                                        else button1Action,
                                button2Action = dashboardAction
                        )
                else -> DashboardShortcutActions(button1Action, button2Action)
        }
}
