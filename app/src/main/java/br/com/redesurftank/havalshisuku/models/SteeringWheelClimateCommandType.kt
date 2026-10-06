package br.com.redesurftank.havalshisuku.models

enum class SteeringWheelClimateCommandType(val key: String, val description: String) {
    TOGGLE_AC("toggle_ac", "AC"),
    TOGGLE_AUTO("toggle_auto", "Automático"),
    TOGGLE_POWER("toggle_power", "Ligar/desligar ar-condicionado"),
    FRONT_DEFROST("front_defrost", "Ventilação para o vidro (desembaçar)");

    companion object {
        fun fromKey(key: String): SteeringWheelClimateCommandType? {
            return entries.find { it.key == key }
        }
    }
}
