package com.ts.androidauto.sdk.common;

public final class VehicleConst {
    private VehicleConst() {}

    public enum AapEvConnectorType {
        AAP_EV_CONNECTOR_J1772,
        AAP_EV_CONNECTOR_MENNEKES,
        AAP_EV_CONNECTOR_CHADEMO,
        AAP_EV_CONNECTOR_COMBO_1,
        AAP_EV_CONNECTOR_COMBO_2,
        AAP_EV_CONNECTOR_TESLA_ROADSTER,
        AAP_EV_CONNECTOR_TESLA_HPWC,
        AAP_EV_CONNECTOR_TESLA_SUPERCHARGER,
        AAP_EV_CONNECTOR_GBT,
        AAP_EV_CONNECTOR_OTHER,
        AAP_EV_CONNECTOR_UNKNOWN
    }

    public enum AapHardkeyEvent {
        AAP_KEYCODE_MEDIA,
        AAP_KEYCODE_TEL,
        AAP_KEYCODE_NAVIGATION,
        AAP_KEYCODE_HOME,
        AAP_KEYCODE_SEARCH,
        AAP_KEYCODE_BACK,
        AAP_KEYCODE_MEDIA_PLAY_PAUSE,
        AAP_KEYCODE_MEDIA_PLAY,
        AAP_KEYCODE_MEDIA_PAUSE,
        AAP_KEYCODE_MEDIA_PREVIOUS,
        AAP_KEYCODE_MEDIA_NEXT,
        AAP_KEYCODE_DPAD_RIGHT,
        AAP_KEYCODE_DPAD_LEFT,
        AAP_KEYCODE_DPAD_UP,
        AAP_KEYCODE_DPAD_DOWN,
        AAP_KEYCODE_DPAD_CENTER,
        AAP_KEYCODE_ROTARY_CONTROLLER
    }

    public enum AapVehicleDrivePosition {
        AAP_DRIVE_LEFT,
        AAP_DRIVE_RIGHT,
        AAP_DRIVE_CENTER,
        AAP_DRIVE_UNKNOWN
    }

    public enum AapVehicleFuelType {
        AAP_FUEL_UNLEADED_GASOLINE,
        AAP_FUEL_LEADED_GASOLINE,
        AAP_FUEL_DIESEL_1,
        AAP_FUEL_DIESEL_2,
        AAP_FUEL_BIODIESEL,
        AAP_FUEL_E85,
        AAP_FUEL_LPG,
        AAP_FUEL_CNG,
        AAP_FUEL_LNG,
        AAP_FUEL_ELECTRIC,
        AAP_FUEL_HYDROGEN,
        AAP_FUEL_OTHER,
        AAP_FUEL_UNKNOWN
    }
}
