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
            navArcs = prefs.getBoolean(SharedPreferencesKeys.HUD_NAV_ARCS.key, defaults.navArcs),
            arArcs = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_ARCS.key, defaults.arArcs),
            chevrons = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS.key, defaults.chevrons),
            arPlacement = HudArArrow.Placement.fromKey(prefs.getString(SharedPreferencesKeys.HUD_AR_PLACEMENT.key, null))
                ?: defaults.arPlacement
        )
    }

    /** Saves [config] and applies it to the HUD at once. */
    fun save(config: HudOverlayModel.Config, prefs: SharedPreferences = prefs()) {
        prefs.edit()
            .putString(SharedPreferencesKeys.HUD_LAYOUT.key, config.layout.key)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_ENABLED.key, config.navigation)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_SMALL_WHEN_FAR.key, config.smallArrowWhenFar)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_TRIP.key, config.trip)
            .putBoolean(SharedPreferencesKeys.HUD_POWER_BAR.key, config.powerBar)
            .putBoolean(SharedPreferencesKeys.HUD_RPM_BAR.key, config.rpmBar)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_ARCS.key, config.navArcs)
            .putBoolean(SharedPreferencesKeys.HUD_AR_ARCS.key, config.arArcs)
            .putBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS.key, config.chevrons)
            .putString(SharedPreferencesKeys.HUD_AR_PLACEMENT.key, config.arPlacement.key)
            .remove(LEGACY_ENABLED_KEY)
            .apply()
        HudOverlayController.setConfig(config)
    }
}
