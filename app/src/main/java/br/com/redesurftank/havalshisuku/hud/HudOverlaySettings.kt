package br.com.redesurftank.havalshisuku.hud

import android.content.Context
import android.content.SharedPreferences
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys

/** Reads and writes [HudOverlayModel.Config] in the app prefs. */
object HudOverlaySettings {
    /** The on/off switch this replaced; a car that had it on starts on the Navigation layout. */
    private const val LEGACY_ENABLED_KEY = "hudOverlayEnabled"

    fun prefs(): SharedPreferences =
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    fun load(prefs: SharedPreferences = prefs()): HudOverlayModel.Config {
        val defaults = HudOverlayModel.Config()
        val layout = HudOverlayModel.Layout.fromKey(prefs.getString(SharedPreferencesKeys.HUD_LAYOUT.key, null))
            ?: if (prefs.getBoolean(LEGACY_ENABLED_KEY, false)) HudOverlayModel.Layout.NAVIGATION else defaults.layout
        return HudOverlayModel.Config(
            layout = layout,
            navigation = prefs.getBoolean(SharedPreferencesKeys.HUD_NAV_ENABLED.key, defaults.navigation),
            smallArrowWhenFar = prefs.getBoolean(SharedPreferencesKeys.HUD_NAV_SMALL_WHEN_FAR.key, defaults.smallArrowWhenFar),
            trip = prefs.getBoolean(SharedPreferencesKeys.HUD_NAV_TRIP.key, defaults.trip),
            powerBar = prefs.getBoolean(SharedPreferencesKeys.HUD_POWER_BAR.key, defaults.powerBar),
            rpmBar = prefs.getBoolean(SharedPreferencesKeys.HUD_RPM_BAR.key, defaults.rpmBar),
            navGauges = gauges(prefs, SharedPreferencesKeys.HUD_NAV_GAUGES, SharedPreferencesKeys.HUD_NAV_ARCS, defaults.navGauges),
            arGauges = gauges(prefs, SharedPreferencesKeys.HUD_AR_GAUGES, SharedPreferencesKeys.HUD_AR_ARCS, defaults.arGauges),
            chevrons = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS.key, defaults.chevrons),
            arPlacement = HudArArrow.Placement.fromKey(prefs.getString(SharedPreferencesKeys.HUD_AR_PLACEMENT.key, null))
                ?: defaults.arPlacement,
            mapStreets = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_MAP.key, defaults.mapStreets),
            mapHorizonTop = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_MAP_HORIZON_TOP.key, defaults.mapHorizonTop)
        )
    }

    /** The indicators pref, else the older arcs on/off one (arcs, or bars), else [default]. */
    private fun gauges(
        prefs: SharedPreferences,
        key: SharedPreferencesKeys,
        legacyArcs: SharedPreferencesKeys,
        default: HudOverlayModel.Gauges
    ): HudOverlayModel.Gauges =
        HudOverlayModel.Gauges.fromKey(prefs.getString(key.key, null))
            ?: if (prefs.contains(legacyArcs.key)) {
                if (prefs.getBoolean(legacyArcs.key, false)) HudOverlayModel.Gauges.ARCS else HudOverlayModel.Gauges.BARS
            } else default

    /** Saves [config] and applies it to the HUD at once. */
    fun save(config: HudOverlayModel.Config, prefs: SharedPreferences = prefs()) {
        prefs.edit()
            .putString(SharedPreferencesKeys.HUD_LAYOUT.key, config.layout.key)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_ENABLED.key, config.navigation)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_SMALL_WHEN_FAR.key, config.smallArrowWhenFar)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_TRIP.key, config.trip)
            .putBoolean(SharedPreferencesKeys.HUD_POWER_BAR.key, config.powerBar)
            .putBoolean(SharedPreferencesKeys.HUD_RPM_BAR.key, config.rpmBar)
            .putString(SharedPreferencesKeys.HUD_NAV_GAUGES.key, config.navGauges.key)
            .putString(SharedPreferencesKeys.HUD_AR_GAUGES.key, config.arGauges.key)
            .putBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS.key, config.chevrons)
            .putString(SharedPreferencesKeys.HUD_AR_PLACEMENT.key, config.arPlacement.key)
            .putBoolean(SharedPreferencesKeys.HUD_AR_MAP.key, config.mapStreets)
            .putBoolean(SharedPreferencesKeys.HUD_AR_MAP_HORIZON_TOP.key, config.mapHorizonTop)
            .remove(LEGACY_ENABLED_KEY)
            .apply()
        HudOverlayController.setConfig(config)
    }
}
