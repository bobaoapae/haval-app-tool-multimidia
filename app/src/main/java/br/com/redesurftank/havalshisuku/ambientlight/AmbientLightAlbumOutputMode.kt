package br.com.redesurftank.havalshisuku.ambientlight

enum class AmbientLightAlbumOutputMode(val label: String) {
    ANIMATED("Animado"),
    STATIC("Estatico");

    companion object {
        val DEFAULT = ANIMATED

        fun fromStored(value: String?): AmbientLightAlbumOutputMode =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}
