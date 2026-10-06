package br.com.redesurftank.havalshisuku.ambientlight

enum class AmbientLightEventType {
    DOOR_OPEN,
    REVERSE,
    TURN_SIGNAL,
    ALERT,
    ADAS,
    SPEED,
    RPM,
    MUSIC
}

data class AmbientLightEvent(
    val type: AmbientLightEventType,
    val value: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

interface AmbientLightEventHandler {
    fun onAmbientLightEvent(event: AmbientLightEvent)
}

class AmbientLightEventRouter {
    private val handlers = mutableSetOf<AmbientLightEventHandler>()

    fun register(handler: AmbientLightEventHandler) {
        handlers += handler
    }

    fun unregister(handler: AmbientLightEventHandler) {
        handlers -= handler
    }

    fun dispatch(event: AmbientLightEvent) {
        handlers.forEach { it.onAmbientLightEvent(event) }
    }
}
