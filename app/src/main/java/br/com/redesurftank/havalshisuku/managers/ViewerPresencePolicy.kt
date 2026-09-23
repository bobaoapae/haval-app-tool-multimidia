package br.com.redesurftank.havalshisuku.managers

/**
 * The decisions behind [ViewerPresence], kept free of Android types so they can be unit tested.
 *
 * Everything here answers one question: given what the PackageManager reports about
 * `com.havalh6.viewer`, may a given Impulse feature be offered? Impulse ships ahead of the viewer,
 * so "is it installed" is never enough on its own — an older viewer is installed and still cannot
 * answer a handshake that did not exist when it was built.
 */
object ViewerPresencePolicy {

    /**
     * Meta-data the viewer declares in its own manifest:
     *
     * ```xml
     * <meta-data android:name="impulse.api" android:value="1" />
     * ```
     *
     * Read with `GET_META_DATA` — no launching, no binding. A viewer without it reads as [API_NONE],
     * which is correct: every build that predates the declaration also predates the features that
     * test for it.
     */
    const val META_API_LEVEL = "impulse.api"

    /** No declaration at all. Presence-only features still work; anything that talks does not. */
    const val API_NONE = 0

    /**
     * The viewer is on the car and can be launched. Nothing is asked of it, so no declared level is
     * required — this is what the boot autostart needs.
     */
    const val API_PRESENT_ONLY = API_NONE

    /**
     * The viewer answers the Impulse client API: vehicle commands, setting writes, the climate
     * popup's keys. Declared by every viewer that carries `impulse.api`.
     */
    const val API_CLIENT = 1

    /**
     * The viewer takes over the climate popup: it binds to Impulse so the OEM HVAC app can be held
     * disabled, and opens its own popup on `car.hvac.panel_display_notify` and on the nav-bar AC
     * tab. Not implemented on either side yet; declared here so the gate exists before the feature.
     */
    const val API_CLIMATE_HANDOFF = 2

    /**
     * Manifest meta-data comes back as whatever type the value was written as: `android:value="1"`
     * parses as Int, `android:value="1"` on a string resource as String. Anything unreadable is
     * [API_NONE] rather than an exception — a malformed declaration must not take the settings
     * screen down.
     */
    fun parseApiLevel(raw: Any?): Int = when (raw) {
        is Int -> raw.coerceAtLeast(API_NONE)
        is Long -> raw.coerceIn(API_NONE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        is String -> raw.trim().toIntOrNull()?.coerceAtLeast(API_NONE) ?: API_NONE
        else -> API_NONE
    }

    /**
     * Is this the viewer we mean, or something that merely took the name?
     *
     * [pinned] EMPTY means "accept any signer" — which is where this ships, because the viewer's
     * release signing key is not settled yet and a wrong pin would silently hide every option on a
     * car that has the app. [ViewerPresence] logs the observed digest on every refresh so the pin
     * can be filled in from a real install; once [pinned] is non-empty this is a real check and an
     * unknown signer is refused.
     */
    fun isTrusted(observed: String?, pinned: Set<String>): Boolean {
        if (pinned.isEmpty()) return true
        if (observed == null) return false
        return pinned.any { it.equals(observed, ignoreCase = true) }
    }

    /**
     * The gate every caller uses. Installed, not disabled, signed by someone we accept, and
     * declaring at least [minApiLevel].
     *
     * Note this is about OFFERING a feature. Whether it currently ACTS is a separate question the
     * runtime answers on its own — the climate hand-off, for instance, only holds the OEM HVAC app
     * disabled while the viewer is actually bound, so a viewer that is installed but not running
     * changes nothing.
     */
    fun supports(
        installed: Boolean,
        enabled: Boolean,
        trusted: Boolean,
        apiLevel: Int,
        minApiLevel: Int
    ): Boolean = installed && enabled && trusted && apiLevel >= minApiLevel
}
