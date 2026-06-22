package br.com.redesurftank.havalshisuku.ui.components

import java.util.Locale
import kotlin.math.roundToInt

internal enum class DashboardCardId(val storageKey: String) {
    MEDIA("media"),
    DYNAMICS("dynamics"),
    HVAC("hvac");

    companion object {
        fun fromStorageKey(value: String): DashboardCardId? {
            return entries.firstOrNull { it.storageKey == value.trim().lowercase(Locale.US) }
        }
    }
}

internal val defaultDashboardCardOrder =
        listOf(DashboardCardId.MEDIA, DashboardCardId.DYNAMICS, DashboardCardId.HVAC)

internal fun normalizeDashboardCardOrder(rawOrder: String?): List<DashboardCardId> {
    val parsed =
            rawOrder
                    ?.split(',')
                    ?.mapNotNull { DashboardCardId.fromStorageKey(it) }
                    ?.distinct()
                    .orEmpty()
    return (parsed + defaultDashboardCardOrder.filterNot { it in parsed })
            .take(defaultDashboardCardOrder.size)
}

internal fun dashboardCardOrderToStorage(order: List<DashboardCardId>): String {
    return normalizeDashboardCardOrder(order.joinToString(",") { it.storageKey })
            .joinToString(",") { it.storageKey }
}

internal fun moveDashboardCard(
        order: List<DashboardCardId>,
        cardId: DashboardCardId,
        direction: Int
): List<DashboardCardId> {
    if (direction == 0) return normalizeDashboardCardOrder(dashboardCardOrderToStorage(order))
    val next = normalizeDashboardCardOrder(dashboardCardOrderToStorage(order)).toMutableList()
    val currentIndex = next.indexOf(cardId)
    if (currentIndex == -1) return next
    val targetIndex = (currentIndex + direction).coerceIn(next.indices)
    if (targetIndex == currentIndex) return next
    val item = next.removeAt(currentIndex)
    next.add(targetIndex, item)
    return next
}

internal fun formatDashboardFuelLiters(percent: String): String {
    val parsed = percent.toFloatOrNull() ?: return "--"
    if (parsed < 0f || parsed > 100f) return "--"
    val liters = parsed.coerceIn(0f, 100f) * DASHBOARD_FUEL_TANK_CAPACITY_LITERS / 100f
    return String.format(Locale.US, "%.1f L", liters)
}

internal fun formatDashboardDistance(value: String): String {
    return value.toFloatOrNull()?.roundToInt()?.let { "$it km" } ?: "--"
}

internal fun formatDashboardFuelLitersAndRange(percent: String, range: String): String {
    val liters = formatDashboardFuelLiters(percent)
    val distance = formatDashboardDistance(range)
    return when {
        liters != "--" && distance != "--" -> "$liters - $distance"
        liters != "--" -> liters
        distance != "--" -> distance
        else -> "--"
    }
}
