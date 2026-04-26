package br.com.redesurftank.havalshisuku.models

import android.content.SharedPreferences

enum class LayoutSizeMode(val value: String, val label: String, val presetScale: Float?) {
    SMALL("small", "Pequeno", 1.0f),
    MEDIUM("medium", "Médio", 1.15f),
    LARGE("large", "Grande", 1.30f),
    CUSTOM("custom", "Outro", null);

    companion object {
        const val MIN_SCALE = 1.0f
        const val MAX_SCALE = 2.0f
        const val DEFAULT_CUSTOM_SCALE = 1.0f

        fun fromValue(v: String?): LayoutSizeMode = values().firstOrNull { it.value == v } ?: SMALL
    }
}

/**
 * Pure logic, testable without Android. Given the stored mode value and the stored custom scale,
 * returns the effective layout scale clamped to [MIN_SCALE, MAX_SCALE].
 */
fun resolveLayoutScale(modeValue: String?, customScale: Float): Float {
    val mode = LayoutSizeMode.fromValue(modeValue)
    return mode.presetScale ?: customScale.coerceIn(
        LayoutSizeMode.MIN_SCALE,
        LayoutSizeMode.MAX_SCALE
    )
}

fun resolveLayoutScale(prefs: SharedPreferences): Float = resolveLayoutScale(
    modeValue = prefs.getString(
        SharedPreferencesKeys.LAYOUT_SIZE_MODE.key,
        LayoutSizeMode.SMALL.value
    ),
    customScale = prefs.getFloat(
        SharedPreferencesKeys.LAYOUT_SIZE_CUSTOM_SCALE.key,
        LayoutSizeMode.DEFAULT_CUSTOM_SCALE
    )
)
