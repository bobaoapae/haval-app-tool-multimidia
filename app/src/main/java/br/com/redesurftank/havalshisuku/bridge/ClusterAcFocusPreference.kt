package br.com.redesurftank.havalshisuku.bridge

/** Global cluster focus preference mapping; selecting focus never writes HVAC state. */
internal object ClusterAcFocusPreference {
    fun read(readStored: () -> String?): String {
        val stored = try { readStored() } catch (_: ClassCastException) { null }
        return if (stored == "TEMPERATURE") "temp" else "fan"
    }

    fun save(focus: String?, writeStored: (String) -> Unit): Boolean {
        val stored = when (focus) {
            "fan" -> "FAN_SPEED"
            "temp" -> "TEMPERATURE"
            else -> return false
        }
        writeStored(stored)
        return true
    }
}
