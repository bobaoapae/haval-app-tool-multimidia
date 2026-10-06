package br.com.redesurftank.havalshisuku.models

/**
 * Represents the vehicle's engine or ignition/power states. Under GWM's architecture, specific
 * numeric states map to the CAN bus ignition logic.
 *
 * TODO: Check if this is the correct mapping - for now its best effort guessing :)
 */
enum class EngineState(val rawValue: String, val isScreenOn: Boolean) {
    UNKNOWN("-1", false),
    SLEEP("15", false), // Power completely off / deep sleep
    STANDBY("14", false), // Car unlocked / system pre-heating / initializing
    ACC("10", false), // Accessories only (cluster is starting or in standby)
    IGNITION_ON("11", true),
    CRANKING("12", true),
    RUNNING("13", true); // Fully on / Ready to drive

    companion object {
        @JvmStatic
        fun fromRawValue(value: String?): EngineState {
            return values().find { it.rawValue == value } ?: UNKNOWN
        }

        /**
         * Returns true if the state corresponds to the main multimedia system being fully active.
         * Treats any state that is not explicitly known as OFF/STANDBY as ON.
         */
        @JvmStatic
        fun isMainScreenOn(value: String?): Boolean {
            if (value == null) return false
            return value != "-1" && value != "15" && value != "14" && value != "10"
        }
    }
}
