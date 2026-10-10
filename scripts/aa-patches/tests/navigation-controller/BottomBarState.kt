package br.com.redesurftank.havalshisuku.models

/** Records the existing v9 dock publication; no Android UI is modeled. */
object BottomBarState {
    val linked = mutableListOf<Boolean>()
    fun publishAndroidAutoLinked(value: Boolean) { linked += value }
}
