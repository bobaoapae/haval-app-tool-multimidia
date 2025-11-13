package br.com.redesurftank.havalshisuku.models

enum class SteeringWheelCustomActionType(val key: String, val description: String) {
    DEFAULT("default", "Padrão da multimidia."),
    CHANGE_REGENERATION_LEVEL("change_regeneration_level", "Alterar nível de regeneração: Baixo, Médio, Alto."),
    CHANGE_POWER_MODE("power_mode", "Alterar modo de potência: HEV, EV, Prioridade EV."),
    TOGGLE_ANION("toggle_anion", "Alternar ionizador do ar-condicionado."),
    //TOGGLE_ESP("toggle_esp", "Alternar controle de estabilidade (ESP)."),
    TOGGLE_ONE_PEDAL_DRIVING("toggle_one_pedal_driving", "Alternar condução com um pedal."),
    OPEN_APP("open_app", "Abrir aplicativo de sua escolha."),
    TOGGLE_CAMERA_AVM("toggle_avm", "Alternar o modo de desabilitar a camera com o carro parado."),
    OPEN_AVM_ONCE("open_avm_once", "Abrir a camera sem interrupções.")
    ;

    companion object {
        fun fromKey(key: String): SteeringWheelCustomActionType? {
            return entries.find { it.key == key }
        }
    }
}