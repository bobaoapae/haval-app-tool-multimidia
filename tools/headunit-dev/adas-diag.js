Java.perform(function() {
    var ServiceManager = Java.use("br.com.redesurftank.havalshisuku.managers.ServiceManager");
    var instance = ServiceManager.getInstance();

    var keys = [
        // AEB / Frenagem emergência frontal
        ["FCW+AEB config",          "car.configure.fcw_and_aeb"],
        ["AEB Intersection config",  "car.configure.aeb_intersection_assist"],
        ["PCS state",                "car.intelligent_driving_setting.fas.pcs_state"],
        ["Intersection assist",      "car.intelligent_driving_setting.fas.intersection_assist_state"],
        ["Cross lateral BRAKE",      "car.intelligent_driving_setting.fas.front_cross_lateral_brake"],
        ["Cross lateral WARNING",    "car.intelligent_driving_setting.fas.front_cross_lateral_wraning"],
        ["Early warning mode",       "car.intelligent_driving_setting.fas.early_warning_mode"],
        ["Early warning sensitivity","car.intelligent_driving_setting.fas.early_warning_sensitivity"],
        ["Auto emergency turn",      "car.intelligent_driving_setting.fas.auto_emergency_turn"],
        // Estado em tempo real
        ["Hazard brake STATE",       "car.intelligent_driving_info.hazard_brake_state"],
        ["Hazard brake INFO",        "car.intelligent_driving_info.hazard_brake_info"],
        ["ACC state",                "car.intelligent_driving_info.acc_state"],
        // Faixa
        ["LKA state",                "car.intelligent_driving_setting.las.lka_state"],
        ["LDW state",                "car.intelligent_driving_setting.las.ldw_state"],
        ["ELK state",                "car.intelligent_driving_setting.las.elk_state"],
        // Smart dodge
        ["Smart dodge",              "car.intelligent_driving_setting.smart_dodge_state"],
        // HUD
        ["ADAS HUD display",         "car.hud_setting.adas_display_enable"],
    ];

    console.log("\n========== DIAGNOSTICO ADAS ==========");
    keys.forEach(function(pair) {
        var label = pair[0];
        var key   = pair[1];
        try {
            var val = instance.getUpdatedData(key);
            console.log(label + ": " + val + "  [" + key + "]");
        } catch(e) {
            console.log(label + ": ERRO  [" + key + "]");
        }
    });
    console.log("======================================\n");
});
