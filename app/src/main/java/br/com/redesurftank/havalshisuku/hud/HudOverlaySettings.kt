package br.com.redesurftank.havalshisuku.hud

import android.content.Context
import android.content.SharedPreferences
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys

/**
 * The HUD style picked in Telas ([HudOverlayModel.Preset]) and the owner's own one,
 * "Personalizado", in the app prefs. The fixed styles live in code; only Personalizado's options
 * are saved, and it exists once [SharedPreferencesKeys.HUD_CUSTOM_BASE] is set (owner,
 * 2026-10-27: no migration from the old layouts, a car starts on Original).
 */
object HudOverlaySettings {
    fun prefs(): SharedPreferences =
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    /** The selected style; Personalizado only while it exists. */
    fun preset(prefs: SharedPreferences = prefs()): HudOverlayModel.Preset {
        val p = HudOverlayModel.Preset.fromKey(prefs.getString(SharedPreferencesKeys.HUD_PRESET.key, null))
            ?: return HudOverlayModel.Preset.ORIGINAL
        return if (p == HudOverlayModel.Preset.CUSTOM && customBase(prefs) == null) HudOverlayModel.Preset.ORIGINAL else p
    }

    /** The style Personalizado was made from, or null when there is no Personalizado. */
    fun customBase(prefs: SharedPreferences = prefs()): HudOverlayModel.Preset? =
        HudOverlayModel.Preset.fromKey(prefs.getString(SharedPreferencesKeys.HUD_CUSTOM_BASE.key, null))
            ?.takeIf { it in HudOverlayModel.Preset.FIXED }

    /** What the HUD draws: the selected style's options. */
    fun load(prefs: SharedPreferences = prefs()): HudOverlayModel.Config = configOf(preset(prefs), prefs)

    /** [preset]'s options (Personalizado: the saved ones, else Original's). */
    fun configOf(preset: HudOverlayModel.Preset, prefs: SharedPreferences = prefs()): HudOverlayModel.Config =
        preset.config ?: custom(prefs) ?: HudOverlayModel.Preset.ORIGINAL.config!!

    /** Personalizado's options, or null when there is none. */
    fun custom(prefs: SharedPreferences = prefs()): HudOverlayModel.Config? {
        val base = customBase(prefs)?.config ?: return null
        val layout = HudOverlayModel.Layout.fromKey(prefs.getString(SharedPreferencesKeys.HUD_LAYOUT.key, null))
            ?.takeIf { it != HudOverlayModel.Layout.ORIGINAL } ?: base.layout
        return HudOverlayModel.Config(
            layout = layout,
            navigation = prefs.getBoolean(SharedPreferencesKeys.HUD_NAV_ENABLED.key, base.navigation),
            smallArrowWhenFar = prefs.getBoolean(SharedPreferencesKeys.HUD_NAV_SMALL_WHEN_FAR.key, base.smallArrowWhenFar),
            // Distance and time come with navigation (owner, 2026-10-28: no switch any more).
            trip = true,
            powerBar = prefs.getBoolean(SharedPreferencesKeys.HUD_POWER_BAR.key, base.powerBar),
            rpmBar = prefs.getBoolean(SharedPreferencesKeys.HUD_RPM_BAR.key, base.rpmBar),
            gauges = HudOverlayModel.Gauges.fromKey(prefs.getString(SharedPreferencesKeys.HUD_GAUGES.key, null)) ?: base.gauges,
            chevrons = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS.key, base.chevrons),
            chevronsAlways = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS_ALWAYS.key, base.chevronsAlways),
            chevronsEvenHidden = prefs.getBoolean(SharedPreferencesKeys.HUD_CHEVRONS_EVEN_HIDDEN.key, base.chevronsEvenHidden),
            arPlacement = HudArArrow.Placement.fromKey(prefs.getString(SharedPreferencesKeys.HUD_AR_PLACEMENT.key, null))
                ?: base.arPlacement,
            mapStreets = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_MAP.key, base.mapStreets),
            mapEdges = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_MAP_EDGES.key, base.mapEdges),
            mapTilt = prefs.getFloat(SharedPreferencesKeys.HUD_MAP_TILT.key, base.mapTilt)
                .coerceIn(HudArArrow.MAP_TILT_MIN, HudArArrow.MAP_TILT_MAX),
            mapZoom = prefs.getFloat(SharedPreferencesKeys.HUD_MAP_ZOOM.key, base.mapZoom)
                .coerceIn(HudArArrow.MAP_ZOOM_MIN, HudArArrow.MAP_ZOOM_MAX),
            font = HudOverlayModel.Font.fromKey(prefs.getString(SharedPreferencesKeys.HUD_FONT.key, null)) ?: base.font,
            iconStyle = HudOverlayModel.IconStyle.fromKey(prefs.getString(SharedPreferencesKeys.HUD_ICON_STYLE.key, null)) ?: base.iconStyle,
            showTurnFromM = prefs.getInt(SharedPreferencesKeys.HUD_NAV_SHOW_TURN_FROM_M.key, base.showTurnFromM)
                .takeIf { it in HudOverlayModel.SHOW_TURN_FROM_OPTIONS } ?: base.showTurnFromM,
            colors = colors(prefs, base.colors)
        )
    }

    private fun colors(prefs: SharedPreferences, d: HudOverlayModel.Colors) = HudOverlayModel.Colors(
        mode = HudOverlayModel.ColorMode.fromKey(prefs.getString(SharedPreferencesKeys.HUD_COLOR_MODE.key, null)) ?: d.mode,
        drive = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_DRIVE.key, d.drive),
        regen = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_REGEN.key, d.regen),
        rpm = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_RPM.key, d.rpm),
        street = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_STREET.key, d.street),
        route = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_ROUTE.key, d.route),
        text = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_TEXT.key, d.text),
        chevron = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_CHEVRON.key, d.chevron)
    )

    /** Selects [preset] and applies it to the HUD at once. */
    fun select(preset: HudOverlayModel.Preset, prefs: SharedPreferences = prefs()) {
        prefs.edit().putString(SharedPreferencesKeys.HUD_PRESET.key, preset.key).apply()
        HudOverlayController.setConfig(load(prefs))
    }

    /**
     * Saves [config] as Personalizado (made from [base]), selects it and applies it to the HUD
     * at once. Replaces any Personalizado there was.
     */
    fun saveCustom(config: HudOverlayModel.Config, base: HudOverlayModel.Preset, prefs: SharedPreferences = prefs()) {
        prefs.edit()
            .putString(SharedPreferencesKeys.HUD_PRESET.key, HudOverlayModel.Preset.CUSTOM.key)
            .putString(SharedPreferencesKeys.HUD_CUSTOM_BASE.key, base.key)
            .putString(SharedPreferencesKeys.HUD_LAYOUT.key, config.layout.key)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_ENABLED.key, config.navigation)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_SMALL_WHEN_FAR.key, config.smallArrowWhenFar)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_TRIP.key, config.trip)
            .putBoolean(SharedPreferencesKeys.HUD_POWER_BAR.key, config.powerBar)
            .putBoolean(SharedPreferencesKeys.HUD_RPM_BAR.key, config.rpmBar)
            .putString(SharedPreferencesKeys.HUD_GAUGES.key, config.gauges.key)
            .putBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS.key, config.chevrons)
            .putBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS_ALWAYS.key, config.chevronsAlways)
            .putBoolean(SharedPreferencesKeys.HUD_CHEVRONS_EVEN_HIDDEN.key, config.chevronsEvenHidden)
            .putString(SharedPreferencesKeys.HUD_AR_PLACEMENT.key, config.arPlacement.key)
            .putBoolean(SharedPreferencesKeys.HUD_AR_MAP.key, config.mapStreets)
            .putBoolean(SharedPreferencesKeys.HUD_AR_MAP_EDGES.key, config.mapEdges)
            .putFloat(SharedPreferencesKeys.HUD_MAP_TILT.key, config.mapTilt)
            .putFloat(SharedPreferencesKeys.HUD_MAP_ZOOM.key, config.mapZoom)
            .putString(SharedPreferencesKeys.HUD_FONT.key, config.font.key)
            .putString(SharedPreferencesKeys.HUD_ICON_STYLE.key, config.iconStyle.key)
            .putInt(SharedPreferencesKeys.HUD_NAV_SHOW_TURN_FROM_M.key, config.showTurnFromM)
            .putString(SharedPreferencesKeys.HUD_COLOR_MODE.key, config.colors.mode.key)
            .putInt(SharedPreferencesKeys.HUD_COLOR_DRIVE.key, config.colors.drive)
            .putInt(SharedPreferencesKeys.HUD_COLOR_REGEN.key, config.colors.regen)
            .putInt(SharedPreferencesKeys.HUD_COLOR_RPM.key, config.colors.rpm)
            .putInt(SharedPreferencesKeys.HUD_COLOR_STREET.key, config.colors.street)
            .putInt(SharedPreferencesKeys.HUD_COLOR_ROUTE.key, config.colors.route)
            .putInt(SharedPreferencesKeys.HUD_COLOR_TEXT.key, config.colors.text)
            .putInt(SharedPreferencesKeys.HUD_COLOR_CHEVRON.key, config.colors.chevron)
            .apply()
        HudOverlayController.setConfig(config)
    }

    /** Removes Personalizado; if it was selected, its base style takes over. */
    fun discardCustom(prefs: SharedPreferences = prefs()) {
        val base = customBase(prefs) ?: HudOverlayModel.Preset.ORIGINAL
        val wasSelected = preset(prefs) == HudOverlayModel.Preset.CUSTOM
        prefs.edit().remove(SharedPreferencesKeys.HUD_CUSTOM_BASE.key).apply()
        if (wasSelected) select(base, prefs)
    }
}
