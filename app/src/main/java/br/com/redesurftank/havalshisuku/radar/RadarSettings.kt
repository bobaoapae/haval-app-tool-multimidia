package br.com.redesurftank.havalshisuku.radar

import android.content.Context
import android.content.SharedPreferences
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys

/** Sounds the radar alert can play; generated in the app (RadarSoundPlayer), no assets. */
enum class RadarSound(val label: String) {
    NONE("Sem som"),
    BEEP("Bipe"),
    DOUBLE_BEEP("Bipe duplo"),
    CHIME("Sino"),
    ALARM("Alerta");

    companion object {
        fun fromStored(value: String?, default: RadarSound): RadarSound =
            entries.firstOrNull { it.name == value } ?: default
    }
}

/** Owner's choices for the radar alert (Recursos > Alerta de Radar). */
data class RadarConfig(
    val enabled: Boolean = false,
    val showOnHud: Boolean = true,
    val showOnTheme: Boolean = true,
    val approachM: Int = 500,
    val nearM: Int = 150,
    val approachSound: RadarSound = RadarSound.BEEP,
    val approachVolume: Int = 70,
    val nearSound: RadarSound = RadarSound.DOUBLE_BEEP,
    val nearVolume: Int = 90,
    /** Sounds only while the car is over the camera's limit; the visual alert always shows. */
    val soundOnlyOverLimit: Boolean = false
) {
    val engineConfig: RadarAlertEngine.Config get() = RadarAlertEngine.Config(approachM, nearM)

    companion object {
        val APPROACH_OPTIONS = listOf(300, 500, 800, 1_000)
        val NEAR_OPTIONS = listOf(100, 150, 200, 300)
    }
}

object RadarSettings {
    fun prefs(): SharedPreferences =
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    fun load(prefs: SharedPreferences = prefs()): RadarConfig {
        val d = RadarConfig()
        val approach = prefs.getInt(SharedPreferencesKeys.RADAR_APPROACH_M.key, d.approachM)
            .takeIf { it in RadarConfig.APPROACH_OPTIONS } ?: d.approachM
        val near = prefs.getInt(SharedPreferencesKeys.RADAR_NEAR_M.key, d.nearM)
            .takeIf { it in RadarConfig.NEAR_OPTIONS && it < approach }
            ?: RadarConfig.NEAR_OPTIONS.lastOrNull { it < approach && it <= d.nearM }
            ?: RadarConfig.NEAR_OPTIONS.first()
        return RadarConfig(
            enabled = prefs.getBoolean(SharedPreferencesKeys.RADAR_ENABLED.key, d.enabled),
            showOnHud = prefs.getBoolean(SharedPreferencesKeys.RADAR_SHOW_HUD.key, d.showOnHud),
            showOnTheme = prefs.getBoolean(SharedPreferencesKeys.RADAR_SHOW_THEME.key, d.showOnTheme),
            approachM = approach,
            nearM = near,
            approachSound = RadarSound.fromStored(prefs.getString(SharedPreferencesKeys.RADAR_APPROACH_SOUND.key, null), d.approachSound),
            approachVolume = prefs.getInt(SharedPreferencesKeys.RADAR_APPROACH_VOLUME.key, d.approachVolume).coerceIn(0, 100),
            nearSound = RadarSound.fromStored(prefs.getString(SharedPreferencesKeys.RADAR_NEAR_SOUND.key, null), d.nearSound),
            nearVolume = prefs.getInt(SharedPreferencesKeys.RADAR_NEAR_VOLUME.key, d.nearVolume).coerceIn(0, 100),
            soundOnlyOverLimit = prefs.getBoolean(SharedPreferencesKeys.RADAR_SOUND_ONLY_OVER_LIMIT.key, d.soundOnlyOverLimit)
        )
    }

    /** Saves [config] and applies it at once. */
    fun save(config: RadarConfig, prefs: SharedPreferences = prefs()) {
        prefs.edit()
            .putBoolean(SharedPreferencesKeys.RADAR_ENABLED.key, config.enabled)
            .putBoolean(SharedPreferencesKeys.RADAR_SHOW_HUD.key, config.showOnHud)
            .putBoolean(SharedPreferencesKeys.RADAR_SHOW_THEME.key, config.showOnTheme)
            .putInt(SharedPreferencesKeys.RADAR_APPROACH_M.key, config.approachM)
            .putInt(SharedPreferencesKeys.RADAR_NEAR_M.key, config.nearM)
            .putString(SharedPreferencesKeys.RADAR_APPROACH_SOUND.key, config.approachSound.name)
            .putInt(SharedPreferencesKeys.RADAR_APPROACH_VOLUME.key, config.approachVolume)
            .putString(SharedPreferencesKeys.RADAR_NEAR_SOUND.key, config.nearSound.name)
            .putInt(SharedPreferencesKeys.RADAR_NEAR_VOLUME.key, config.nearVolume)
            .putBoolean(SharedPreferencesKeys.RADAR_SOUND_ONLY_OVER_LIMIT.key, config.soundOnlyOverLimit)
            .apply()
        RadarManager.applyConfig(config)
    }
}
