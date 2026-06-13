package br.com.redesurftank.havalshisuku.models

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

object BottomBarState {
    enum class SliderType {
        DRIVER_TEMP,
        PASS_TEMP,
        FAN,
        VOLUME
    }

    var activeSliderType by mutableStateOf<SliderType?>(null)
    var sliderPositionX by mutableStateOf(0f)
    var sliderInteractionTrigger by mutableStateOf(0)
    var isSliderDragging by mutableStateOf(false)
    var isVisible by mutableStateOf(true)
    var isDashboardExpanded by mutableStateOf(false)
    var isMenuExpanded by mutableStateOf(false)
    var isSettingsMenuExpanded by mutableStateOf(false)
    var isOverrideMenuExpanded by mutableStateOf(false)
    var selectedPackage by mutableStateOf("")
    var currentPackage by mutableStateOf("")
    var activeClusterProjectionPackage by mutableStateOf("")
    var mediaTitle by mutableStateOf<String?>(null)
    var mediaArtist by mutableStateOf<String?>(null)
    var mediaAlbum by mutableStateOf<String?>(null)
    var mediaPackageName by mutableStateOf<String?>(null)
    var mediaArtwork by mutableStateOf<Bitmap?>(null)
    var mediaIsPlaying by mutableStateOf(false)
    var mediaDurationMs by mutableLongStateOf(0L)
    var mediaElapsedMs by mutableLongStateOf(0L)
    var mediaProgressUpdatedAtMs by mutableLongStateOf(0L)
    var mediaCanSeek by mutableStateOf(false)
    var autoHideEnabled by mutableStateOf(false)
    var isFridaRunning by mutableStateOf(false)
    var isDeleteModeEnabled by mutableStateOf(false)
    val restoredApps = mutableStateListOf<String>()
}
