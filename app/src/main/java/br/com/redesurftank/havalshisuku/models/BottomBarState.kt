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

    /**
     * Measured width of the bar overlay window, in px. 0 until the first layout. Both overlay windows
     * share this frame, so it is also the coordinate space [sliderPositionX] is expressed in.
     */
    var overlayWindowWidthPx by mutableStateOf(0)

    /**
     * Left inset to apply to overlay *content*, in px, so it stays at a fixed physical position no
     * matter where the window itself was placed.
     *
     * The left navigation pane is a NAVIGATION_BAR window that draws above our APPLICATION_OVERLAY
     * windows and insets the frame they are laid out in. It is `paneWidth - windowLeft`: 0 while the
     * pane is up (the window already starts past it) and the pane's width once a fullscreen app hides
     * it. Either way content lands in the same place, so nothing reflows.
     */
    var overlayLeftGutterPx by mutableStateOf(0)

    /**
     * True when the user has asked to keep the left navigation pane hidden. The content gutter drops
     * to 0 so the bar and the menus get the full display width.
     */
    var leftNavPaneHidden by mutableStateOf(false)

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
    var mediaIsMuted by mutableStateOf(false)
    var mediaDurationMs by mutableLongStateOf(0L)
    var mediaElapsedMs by mutableLongStateOf(0L)
    var mediaProgressUpdatedAtMs by mutableLongStateOf(0L)
    var mediaCanSeek by mutableStateOf(false)
    var autoHideEnabled by mutableStateOf(false)
    var isFridaRunning by mutableStateOf(false)
    var isDeleteModeEnabled by mutableStateOf(false)
    val restoredApps = mutableStateListOf<String>()
}
