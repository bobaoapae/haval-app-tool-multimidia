package br.com.redesurftank.havalshisuku.models

data class NativeMaskSpec(
    val enabled: Boolean = false,
    val image: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val opacity: Float = 1.0f
)

data class NativeMaskConfig(
    val fuelMask: NativeMaskSpec = NativeMaskSpec(x = 0, y = 520, width = 380, height = 200),
    val batteryMask: NativeMaskSpec = NativeMaskSpec(x = 1540, y = 520, width = 380, height = 200),
    val speedMask: NativeMaskSpec = NativeMaskSpec(x = 0, y = 0, width = 600, height = 720),
    val infoMask: NativeMaskSpec = NativeMaskSpec(x = 1320, y = 0, width = 600, height = 720),
    val topCenterMask: NativeMaskSpec = NativeMaskSpec(x = 710, y = 0, width = 500, height = 67)
) {
    fun hasAnyActiveMask(): Boolean {
        return fuelMask.enabled || batteryMask.enabled || speedMask.enabled || infoMask.enabled || topCenterMask.enabled
    }
}
