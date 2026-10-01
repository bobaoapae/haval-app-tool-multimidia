package br.com.redesurftank.havalshisuku.managers

/**
 * Invalidates delayed automatic AA window recovery across native camera transitions.
 * A camera window is useful before AVM telemetry arrives; an active AVM signal remains
 * authoritative even when transient SystemUI/launcher windows appear over the camera.
 * No camera controls, display geometry or CarPlay state belong to this policy.
 */
internal class AndroidAutoCameraGuardPolicy {
    private var currentGeneration = 0L
    private var previewActive = false
    private var cameraWindowActive = false

    @Synchronized
    fun generation(): Long = currentGeneration

    @Synchronized
    fun onWindowChanged(isCamera: Boolean?) {
        if (isCamera == null) return // Projection/SystemUI events do not prove camera exit.
        if (cameraWindowActive != isCamera) {
            cameraWindowActive = isCamera
            currentGeneration++
        }
    }

    @Synchronized
    fun onPreviewStatus(value: String) {
        val active = when (value.trim()) {
            "1" -> true
            "0" -> false
            else -> return // Unknown telemetry must not release known camera ownership.
        }
        if (previewActive != active || (!active && cameraWindowActive)) {
            previewActive = active
            if (!active) cameraWindowActive = false
            currentGeneration++
        }
    }

    @Synchronized
    fun canRun(generation: Long): Boolean =
        generation == currentGeneration && !previewActive && !cameraWindowActive
}
