package br.com.redesurftank.havalshisuku.ambientlight

enum class AmbientLightMusicMode(val label: String) {
    BASS("Graves"),
    ALBUM_WAVE("Onda do album");

    companion object {
        val DEFAULT = BASS

        fun fromStored(value: String?): AmbientLightMusicMode =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}
