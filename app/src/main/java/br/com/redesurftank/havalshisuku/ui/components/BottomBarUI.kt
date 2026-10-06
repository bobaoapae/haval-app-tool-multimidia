package br.com.redesurftank.havalshisuku.ui.components

import br.com.redesurftank.havalshisuku.managers.UpdateNoticeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.compose.ui.graphics.painter.Painter
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupProperties
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.*
import br.com.redesurftank.havalshisuku.models.*
import br.com.redesurftank.havalshisuku.services.AlbumBackgroundService
import br.com.redesurftank.havalshisuku.services.BottomBarService
import android.graphics.drawable.Drawable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import java.util.Locale
import br.com.redesurftank.havalshisuku.ui.theme.Michroma
import br.com.redesurftank.havalshisuku.utils.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.core.content.edit
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger
private const val recycleIn = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADwAAAA8CAYAAAA6/NlyAAAJDElEQVR4AcTau5JcVxUG4NOjsUYSJVOYi7kURGROCKGIKV4AZbwAMVSR+gEg5gXIzAtQxBSEJM6IoLiYiymsQjePaM63p//26j379HTPjIRq/l5rr/u/9z6nNbJPptv5s5rLrKb1+kJO06J89Gh957YwTct9pmnj+2SmyZ+bEe6KTSs8ld3FTPAk2PXcbJWakcNqZlqvp3YY6/XquoRzmsMejBmCtF7CO+9Md47FUi29gp0YpDc4nvC8S62YAk3Z/Rg23ISMiG1cR4m+zih5aY7jCCOL6Hq+IrrQyRlLDepwc9gr+dnXo5/rGMKr7TNaiGKgKFmRIaptqH9+utvs5Aic7OQBWOprRjiE8GruA7PY/VEAdq3TpGlv264zPAkckX+fXlg2RI+PDFrA/GE9i9GPGaD37SOMJPQ506NH6/bWrQ7Fg2pvegbrZXOWj/iZqm5dwQdsvWQr6GeqhJGrKGkX6j6iFxHdp2GAOZK+gOf3p/tckfSDkNokDJJCHOGQHIRN06PNaZIJSDIZW5NpRkIzXv4IoV6ePZ2eskXSK1SyJvdCbxgEIXzJjFxQnQhCtV3SFxqJMyyEUGyRfNFJcSTQ47euuvUQg1l2CI9IKoQk0IdQGAbODEYaWgidvAo1rtfVYgsWa5kLNgGN8LWIKhJsilVhkKyjR8Z+E1lrVfKLNc06Oy+9bWdb+1pZPFGJkK8NCQV1kKqXkEV1fTY95Owl2z7UPtEjd/LmudsJx4gkZH1JzglbW9U3xjSJtPMb11Yg88HjJ+/+6V8f/fIPHz7+oOKPf3n8e2uS/89//egXJMgJ/vnsxXfU2RYtip76k8W8VRthJGFrrQpiQbVvdMWpkbVRbPwGNDAyzz9++YOX69U32JfAXyEn+M+T5z9Xx0YgX2ukJwnVRz/ZS1TEHiiIIJmwqrMhaigDGpjttmBDkB8RT49unqmdcJxNOs2mzB9Vn5d+UoDsyfJXIPu3fzz5oaGq/c5q/btPPTj7/le/9PDr+yCm4uyNOz8D+bXePuKZ0bxyLhNmHRBlhhSgpwi9B7KewXqqBkXgy19883tvffrub/ucfi2m4gufe/BTkG+jkK85lbj+fJnR3NYXhEMwkqcgSSQkuYRsVY0AWQPEgaxBEYjtphL5JeIeIe+M9DA3/YIwbQ9ZBCWQQulkD0TZerJOAlm+V4G3Hz54d0Tc7fJ8Zy5zn0x7iPbDSehtdf3hv198087Wk3WFnUSNu209hBDXz21KD7M4gKw/OeGNpZKKHrkJ2QqNwFvY9elfTppfdYVt0rHYDtApZvnsvbu/cpvcqriRNqP1JcKuLYKkgEAxSYi5JuA0AVHXJ7F2eB9ZBO16cuUfA3np1Utzsjltc9ArtoSRDATQK0FNDIWYHQNxPTSxw0529Xx63PvzNbWU38cfu05PxE9PT3/T528J1xPNKVaCfWLWCIIr5FSRjU/T6KSTtWF0SI68Y+AFJX+E2vP8/PxbfUwj7DSB01U11OgEKjEDaowguEJOVY3sMr3i2dNnP8parpeZnGORGlfJykEP8Y0wBVzhGsSWUzDgVz7z5ndDzMshxMjsbNXlV6S2mtX+OnRz6XOSk7WoJ2AoJEOQHxCTTFpXxE6yR9JdZxJO3zj9Nfn/wPaE+9N13QyMGAnRD5HIiCMhV4p+/vH5t8lj4YVXN+6Y/MyyJVyHOJv/kt4THBUfxSjMPor3DmD3jiCPxel8M7xIfaUdQjz99El8I2zIOoTCgjI4KaaXvY0f2Gs+He7dv/cTEnzN5cQMcwjkIeFdcBVxc9SvpTyujbBmioGCXkh0gwcK0KtMTGz8YB0fGbjWZ/PtydomG/wYIJt8utzRiZvDY1lj6Y1wvc7ZFQkZnMy6l4oE4qKT/ZrNEL7SbKz168V0+R8ATufnpCdV18cMmLw+x0n77kb8WNSNostXS82+T39z+S9OePA3Ek7I0JFsh8IJy1uKN+QxUMc1voqoOL3z3Frn5jbCDIEBBGdNN/RNpFryyZvAo7fvRGvt/mvWzeVvhO2aRcV1SaaGfHov2a4Lz78DOSTf5iTOjciLuBGOg/RVYXfohq0nk/U+GZ/8mmv9uuDZ9Q2QfvXrsBE+G3xV5HsSATA8RK9SA790yIH8Y3ok23XhADL4PmkGX0/6+KpKbD1dtkbYVbHoYZc0DNHqj00jDUaPRY2/rm4GRPblL82ArDd4cv0K3Agb3i8KXghOW2CCNHTFxeRU+egksuSrhM208Us96ts4MXgga+7YyBOsKRwebL8d+TWwkkbKPwrYSUTBAK6PXBBv07721sO3D4X4QE500trQaoON19MM1kA3gw2xhuTigRMbhGf79dACOAK7g0TWGiKuAVjHR3ox1AZsI9gsdlJ8kDUfm7VHbd8M5hEfuKFys47ELb8Gb084Bk6BEnvS7D0MZFfdjt43WqvLHklHLmsya7oZ6kmLHwHZfTOEV3uGFWCAEGfT0PVWTFPk2Elg52e7CfSp+dZIx+Z66lVn4DMD22jDcRETSYctYQtkBYB1pJ3TFDnPFgns4gxI3iZSM1IvV1xfM0DTTx/8ODF9/8xf7TuEExDiCYw96yWZuF6KZ4Po5LEYETNr6tT61R4/eTIN/teFJEqKToIkEqL3cilvZFcH1Lgu5ENff6cenjMuTnhWdpybhSJUhSKrLXpk4rKWUzGy1xz+66DWqP2ajhu0Rf19OMbITQBhiCp73RoSRz8UV+WEzJLUZ1gDD6j/sXAOvjjhWWk/CSCb4RV8qA0Hlg6ZJTksU+tXfQ7eJTwbts90AnspZh/6eOuK5FYbnZ0MsiavQnIiF+Lff396eeJjwT8NySsqYSRHNrE9umvW3MmNL2sSBI1kbMkT1wFHYG4nbAEMByFNeil5T2PuhuS1RffR+1Iv9l4mPfasN7Ln1QhvfBMnZH0tudB4VCu9Ikcx21s2dI6N6gU14r33Vv898VGN9ARHsh2CxB8q1RQbSa9gBzZyCfwVfRyOwN5O2CJg7FGL7dP7POvUvUqK7ZFe7NFHkn+E9Ky+RrgaEhRZfUt6YkdyKae3j3Jj62NH68RWOYq7RLgPqgWW9D7nttdLfav90J7/AwAA//83rYeYAAAABklEQVQDAMltCzwxszfdAAAAAElFTkSuQmCC"
private const val recycleOut = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADwAAAA8BAMAAADI0sRBAAAAJ1BMVEV6pf97nfh7o/12kO5bjPJlf+leVZZlk/ZHcExejvJgj/P//+0A/9FP0Or/AAAADXRSTlMeDBMG/QMBVQDLlQEBuyPuOwAAAtFJREFUOMt9Vc1q20AQHpaAcU6VdOpttQRMchPCL1AWjHw18RtYl/ZgjCEkTxBoDyEITG+mGIzzALkk51z6Uv1m9kerFvfDyDv7zcyORrMz9Ojw4+21BW4Dlix9PNLj93dZ/cZjdZuAFT7ovX1tv7bRsnaY8hp79NE6rBISWEyFp7a3XNQD8AGgl6uULASBp8GZVeZRVY6ntrcsiqwqooLwBM8LYSORKpAzraLbsiyNccuCaWY9lefG4AeFsoTI1otgCiOQeSkKePBeTT5e2UuRs4OCxJa9emitw1JoPlgkpUhddtba5ufLURk5H9ZZTkIq0pc2oFmzf9BZ9snu4ZJIjWyPZgP7jGCsoUuMg1uM7h5OnicO+t7OmR0HNXjCMQ3OJ45LH+wTjDp+BoCfm5xgrDWYNW2dDw8F/gnWiJp1G8S1jqTGS97bRmiOeotgZ9HSqRzshtg3jU78LsFY+/9r0Mi0I1PfTmsM2uibTtJw6iM7uj+hDdjmZc1JmXv77U4iuLCKDCLeud0OaneMrdu5sIbMtd17n6OuT/ku0FsbgwkxRhrOuzRXo9Q5h6b7bES40OjGPtMk/Q7DFyP7BSp7Ogeh1+fYEZxfn6VReb/+Q+MrbujKxi80hKIDvveVPZ49emboM2oKJT7IKL8LisU+G6SFRSXlEmvc17xCzpHoGemJTbHnjNo9qhDXp8PxKGUtkBqer3HZlDYZas2MxWLj7iFfUgljhksGOnfijinlLvClV8cd44s/OTU7lVx99dA1ezZmGrw2/yCXBkRJa8jzPDNeKtkW3aGSZmXEVexdxvWuoqbat6w8k2bFbMaauXQuik1tANHkvlYXKZ+0x6KoxLryW9yFpdGGjomhQK4ZV9KhK9eXnVTwyHA9ldd1kY6C0M//mhHTab/kabAK3X4aR9Ri6sVlS8lou20Hk44nGYWdpWchuNkjgh9UPb6lwtsfbTVCnXvwUeQAAAAASUVORK5CYII="
private const val BOTTOM_BAR_TAG = "BottomBarUI"
private const val BOTTOM_BAR_CARPLAY_PACKAGE = "com.ts.carplay.app"
private const val BOTTOM_BAR_ANDROID_AUTO_PACKAGE = "com.ts.androidauto.app"

/** OEM settings activity. Extra `position=0` opens the phone "Conectar" page. */
internal const val PHONE_CONNECT_PACKAGE = "com.beantechs.settings"
internal const val PHONE_CONNECT_ACTIVITY = "com.beantechs.settings.ui.activity.MainActivity"
internal const val PHONE_CONNECT_POSITION = 0
private const val STOCK_SETTINGS_PACKAGE = "com.android.settings"
private const val STOCK_SETTINGS_ACTIVITY = "com.android.settings.Settings"
/**
 * When Android Auto owns display 0, leave this many pixels at the left of the bottom bar clear so
 * the AA rail icon that sits under the bar stays visible and tappable.
 */
internal const val ANDROID_AUTO_BOTTOM_BAR_LEFT_PASSTHROUGH_PX = 100
internal const val DASHBOARD_FUEL_TANK_CAPACITY_LITERS = 55f
private const val DASHBOARD_MEDIA_VOLUME_MIN = 0
private const val DASHBOARD_MEDIA_VOLUME_MAX = 30
private const val DASHBOARD_COLLAPSE_DRAG_THRESHOLD_PX = 80f
private const val DASHBOARD_COLLAPSE_CONSUME_DRAG_PX = 20f
private const val DASHBOARD_COLLAPSE_VERTICAL_DOMINANCE_RATIO = 1.2f

private val DashboardReadableFont = FontFamily.SansSerif

private val DashboardSteeringWheelIcon: ImageVector =
        ImageVector.Builder(
                        name = "DashboardSteeringWheel",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 24f,
                        viewportHeight = 24f
                )
                .apply {
                        path(
                                fill = null,
                                stroke = SolidColor(Color.Black),
                                strokeLineWidth = 2f,
                                strokeLineCap = StrokeCap.Round,
                                strokeLineJoin = StrokeJoin.Round
                        ) {
                                moveTo(12f, 3.5f)
                                curveTo(7.3f, 3.5f, 3.5f, 7.3f, 3.5f, 12f)
                                curveTo(3.5f, 16.7f, 7.3f, 20.5f, 12f, 20.5f)
                                curveTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f)
                                curveTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f)
                                moveTo(5.2f, 12.5f)
                                curveTo(7.1f, 11.5f, 9.5f, 11f, 12f, 11f)
                                curveTo(14.5f, 11f, 16.9f, 11.5f, 18.8f, 12.5f)
                                moveTo(12f, 11f)
                                lineTo(12f, 20f)
                                moveTo(8.2f, 17.8f)
                                lineTo(12f, 14.4f)
                                lineTo(15.8f, 17.8f)
                        }
                }
                .build()

internal fun mergeBottomBarProjectionConfigs(
        savedConfigs: List<DisplayAppConfig>,
        predefinedConfigs: List<DisplayAppConfig>
): List<DisplayAppConfig> {
        val savedPackages = savedConfigs.mapTo(mutableSetOf()) { it.packageName }
        val isModernProjActive = BottomBarState.barVersion == BottomBarState.BarVersion.NEW.key &&
                BottomBarState.isProjectionShortcutEnabled

        val projectionDefaults = if (isModernProjActive) {
                emptyList()
        } else {
                predefinedConfigs.filter {
                        it.packageName == BOTTOM_BAR_CARPLAY_PACKAGE ||
                                it.packageName == BOTTOM_BAR_ANDROID_AUTO_PACKAGE
                }
        }

        val baseConfigs = if (isModernProjActive) {
                savedConfigs.filter { !br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isProjectionLikePackage(it.packageName) }
        } else {
                savedConfigs
        }

        return baseConfigs + projectionDefaults.filter { savedPackages.add(it.packageName) }
}

internal val HAVAL_H6_IGNORED_PACKAGES = setOf(
        "com.beantechs.hvac",
        "com.beantechs.btphone",
        "com.beantechs.drivinganalysisservice",
        "com.beantechs.personalcenter",
        "com.beantechs.operatorcenter",
        "com.beantechs.account",
        "com.beantechs.applist",
        "com.beantechs.guidance",
        "com.beantechs.fotaui",
        "com.beantechs.PKIMaintain",
        "com.beantechs.adaptertool.client",
        "com.beantechs.sshost.client",
        "com.android.car.media",
        "com.android.car.radio",
        "com.android.car.settings",
        "com.beantechs.mediacenter",
        "com.beantechs.mediacenter.h5.ui",
        "com.beantechs.mediacenter.h5.core",
        "com.android.support.car.lenspicker",
        "com.autolink.enginmode",
        "com.apical.cj1005",
        "com.google.android.car.kitchensink",
        "com.nextdoordeveloper.miperf.miperf",
        "com.android.systemui",
        "com.android.keyguard",
        "com.android.webview",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.gwm.hvac",
        "com.gwm.vehicle",
        "com.gwm.car",
        "com.android.vending",
        "app.revanced.android.gms",
        "com.google.android.gms",
        "moe.shizuku.privileged.api",
        "com.ts.androidauto.app",
        "com.ts.androidauto",
        "com.ts.androidauto.projectionservice",
        "com.ts.carplay.app",
        "com.ts.carplay",
        "com.google.android.projection.gearhead"
)

internal fun shouldHideFromAllApps(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        return HAVAL_H6_IGNORED_PACKAGES.contains(packageName)
}

data class SystemAppItem(
        val packageName: String,
        val activityName: String,
        val label: String,
        val icon: Drawable?
)

internal fun resolveDockApps(
        telasPackages: List<String>,
        sessionRecents: List<String> = emptyList(),
        maxApps: Int = 9
): List<String> {
        if (telasPackages.isEmpty()) return emptyList()
        return telasPackages.distinct().take(maxApps)
}

/**
 * The package the bar's centre icon stands for: whatever is on display 0 right now.
 *
 * A projection running on the cluster deliberately does not take part. The icon answers "what am I
 * looking at on the main screen", so claiming Android Auto while it is on display 3 - and the user
 * is on some other app on display 0 - is exactly the confusion this avoids.
 */
internal fun resolveBottomBarEffectivePackage(
        projectionPackageOnMain: String?,
        selectedPackage: String,
        firstConfiguredPackage: String
): String {
        return projectionPackageOnMain
                ?: selectedPackage.takeIf { it.isNotEmpty() }
                ?: firstConfiguredPackage
}

/** True when the top package on display 0 is Android Auto (not merely AA on the cluster). */
internal fun isAndroidAutoShownOnMainDisplay(currentPackage: String): Boolean {
        return br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                .resolveProjectionPackageOrNull(currentPackage) == BOTTOM_BAR_ANDROID_AUTO_PACKAGE
}

/** Transparent/click-through width at the left of the bar while AA owns display 0; 0 otherwise. */
internal fun resolveAndroidAutoBottomBarCutoutPx(
        androidAutoOnMainDisplay: Boolean,
        androidAutoPassthroughPx: Int = ANDROID_AUTO_BOTTOM_BAR_LEFT_PASSTHROUGH_PX
): Int = if (androidAutoOnMainDisplay) androidAutoPassthroughPx.coerceAtLeast(0) else 0

/**
 * Left edge of the bar window's touchable region. When AA is on screen, pass through the AA rail
 * cutout; otherwise leave the SystemUI gutter alone (the pane draws above us there).
 */
internal fun resolveBottomBarTouchableLeftPx(
        overlayLeftGutterPx: Int,
        androidAutoOnMainDisplay: Boolean,
        androidAutoPassthroughPx: Int = ANDROID_AUTO_BOTTOM_BAR_LEFT_PASSTHROUGH_PX
): Int {
        val cutout =
                resolveAndroidAutoBottomBarCutoutPx(
                        androidAutoOnMainDisplay = androidAutoOnMainDisplay,
                        androidAutoPassthroughPx = androidAutoPassthroughPx
                )
        if (cutout > 0) return cutout
        return overlayLeftGutterPx.coerceAtLeast(0)
}

/**
 * Start padding for the button Row inside a Surface that already has [surfaceCutoutPx] start
 * padding. Keeps content anchored at the SystemUI gutter so AA on/off does not reflow the bar.
 */
internal fun resolveBottomBarRowStartPadPx(
        overlayLeftGutterPx: Int,
        surfaceCutoutPx: Int
): Int = (overlayLeftGutterPx.coerceAtLeast(0) - surfaceCutoutPx.coerceAtLeast(0)).coerceAtLeast(0)

enum class BottomBarSwipeDecision {
        NONE,
        EXPAND,
        HIDE
}

/**
 * Determines whether a drag gesture should trigger an expand (swipe up) or hide (swipe down)
 * action on the bottom bar, strictly ignoring vertical actions if the gesture has horizontal intent
 * or if a child element consumed the event.
 */
internal fun resolveBottomBarSwipeAction(
        totalDragX: Float,
        totalDragY: Float,
        isHorizontalLocked: Boolean,
        isChildConsumed: Boolean,
        expandThresholdPx: Float = -45f,
        hideThresholdPx: Float = 20f
): BottomBarSwipeDecision {
        if (isHorizontalLocked || isChildConsumed) {
                return BottomBarSwipeDecision.NONE
        }
        val absX = kotlin.math.abs(totalDragX)
        val absY = kotlin.math.abs(totalDragY)
        if (absX > 15f && absX > absY) {
                return BottomBarSwipeDecision.NONE
        }
        if (totalDragY < expandThresholdPx) {
                return BottomBarSwipeDecision.EXPAND
        }
        if (totalDragY > hideThresholdPx) {
                return BottomBarSwipeDecision.HIDE
        }
        return BottomBarSwipeDecision.NONE
}

private fun getBottomBarAppConfigs(): List<DisplayAppConfig> {
        return mergeBottomBarProjectionConfigs(
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs(),
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.PREDEFINED_APPS
        )
}

/**
 * The projection app on display 0, or null when display 0 is showing something else.
 *
 * Read off [BottomBarState.currentPackage] - the top package the service polls - instead of
 * `resolveActiveProjectionPackageForDisplay(0)`, which also reports a projection that is only
 * parked in display 0's task stack. That is what left the bar stuck on the Android Auto icon after
 * AA came back from the cluster and another app was opened on top of it. Reading state also means
 * the icon actually recomposes when display 0 changes.
 */
private fun getProjectionPackageOnMainForBottomBar(): String? {
        return br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                .resolveProjectionPackageOrNull(BottomBarState.currentPackage)
}

private val commonTextStyle =
        TextStyle(
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = Michroma,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
        )

private val labelStyle =
        TextStyle(
                color = Color.LightGray,
                fontSize = 10.sp,
                fontFamily = Michroma,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
        )

private fun String?.toComposeColor(): Color {
        if (this == null || !this.startsWith("#")) return Color.White
        return try {
                Color(android.graphics.Color.parseColor(this))
        } catch (_: Exception) {
                Color.White
        }
}

/**
 * Runs whatever the user configured for a swipe up on the bar. Defaults to opening Impulse Drive,
 * which was the only behaviour before this became configurable.
 */
private fun performSwipeUpAction(context: Context) {
        val action = BottomBarState.SwipeUpAction.fromKey(BottomBarState.swipeUpAction)

        // Any action first collapses whatever the bar had open.
        BottomBarState.isVisible = true
        BottomBarState.isMenuExpanded = false
        BottomBarState.isSettingsMenuExpanded = false
        BottomBarState.isOverrideMenuExpanded = false

        val packageToLaunch =
                when (action) {
                        BottomBarState.SwipeUpAction.DASHBOARD -> null
                        BottomBarState.SwipeUpAction.HAVAL_HOME ->
                                BottomBarState.SwipeUpAction.HAVAL_HOME_PACKAGE
                        BottomBarState.SwipeUpAction.APP_LAUNCHER ->
                                BottomBarState.SwipeUpAction.APP_LAUNCHER_PACKAGE
                        BottomBarState.SwipeUpAction.CUSTOM_APP ->
                                BottomBarState.swipeUpPackage.takeIf { it.isNotBlank() }
                }

        if (packageToLaunch == null) {
                // Either the dashboard was chosen, or "specific app" was chosen without ever picking
                // one - fall back to the dashboard rather than swallowing the gesture.
                BottomBarState.isDashboardExpanded = true
                return
        }

        BottomBarState.isDashboardExpanded = false
        BottomBarState.selectedPackage = packageToLaunch
        br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.launchAnyAppDetached(
                context,
                packageToLaunch
        )
}

// ==========================================
// Bottom Bar Revamp (v2) Helper Composables
// ==========================================

@Composable
fun FanSpeedIcon(
        speed: Int,
        modifier: Modifier = Modifier.size(24.dp).padding(2.dp),
        activeColor: Color = Color.White
) {
        Canvas(modifier = modifier) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                val innerRadius = radius * 0.3f

                // Draw 7 segments around the circle
                val segmentGap = 10f
                val totalGap = segmentGap * 7
                val sweepAngle = (360f - totalGap) / 7f

                for (i in 0 until 7) {
                        val startAngle = i * (sweepAngle + segmentGap) - 90f
                        val isActive = i < speed
                        val color = if (isActive) activeColor else Color.Gray.copy(alpha = 0.3f)

                        drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = (radius * 0.28f).coerceIn(2.dp.toPx(), 4.dp.toPx()), cap = StrokeCap.Round)
                        )
                }

                // Draw a small fan hub in the center
                drawCircle(
                        color = (if (speed > 0) activeColor else Color.Gray).copy(alpha = 0.9f),
                        radius = innerRadius,
                        center = center
                )
        }
}

/**
 * CoffeeOS 3.0 Home icon: gabled roof house outline with arched doorway cutout.
 */
@Composable
fun CoffeeOsHomeIcon(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = Color.White
) {
        Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val strokeW = 1.8.dp.toPx()

                val path = Path().apply {
                        // Roof peak
                        moveTo(w * 0.50f, h * 0.14f)
                        // Right roof slope
                        lineTo(w * 0.88f, h * 0.46f)
                        // Right wall
                        lineTo(w * 0.88f, h * 0.86f)
                        quadraticTo(w * 0.88f, h * 0.90f, w * 0.84f, h * 0.90f)
                        // Doorway right side
                        lineTo(w * 0.62f, h * 0.90f)
                        lineTo(w * 0.62f, h * 0.64f)
                        quadraticTo(w * 0.50f, h * 0.54f, w * 0.38f, h * 0.64f)
                        lineTo(w * 0.38f, h * 0.90f)
                        // Doorway left side
                        lineTo(w * 0.16f, h * 0.90f)
                        quadraticTo(w * 0.12f, h * 0.90f, w * 0.12f, h * 0.86f)
                        lineTo(w * 0.12f, h * 0.46f)
                        close()
                }
                drawPath(
                        path = path,
                        color = tint,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
        }
}

/**
 * CoffeeOS 3.0 Car icon: front vehicle silhouette outline with headlights and roofline.
 */
@Composable
fun CoffeeOsCarIcon(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = Color.White
) {
        Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val strokeW = 1.8.dp.toPx()

                val carBody = Path().apply {
                        moveTo(w * 0.32f, h * 0.22f)
                        lineTo(w * 0.68f, h * 0.22f)
                        cubicTo(w * 0.74f, h * 0.24f, w * 0.80f, h * 0.42f, w * 0.88f, h * 0.48f)
                        lineTo(w * 0.88f, h * 0.72f)
                        lineTo(w * 0.84f, h * 0.80f)
                        lineTo(w * 0.76f, h * 0.80f)
                        lineTo(w * 0.72f, h * 0.74f)
                        lineTo(w * 0.28f, h * 0.74f)
                        lineTo(w * 0.24f, h * 0.80f)
                        lineTo(w * 0.16f, h * 0.80f)
                        lineTo(w * 0.12f, h * 0.72f)
                        lineTo(w * 0.12f, h * 0.48f)
                        cubicTo(w * 0.20f, h * 0.42f, w * 0.26f, h * 0.24f, w * 0.32f, h * 0.22f)
                        close()
                }
                drawPath(
                        path = carBody,
                        color = tint,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                val windshieldPath = Path().apply {
                        moveTo(w * 0.22f, h * 0.48f)
                        lineTo(w * 0.78f, h * 0.48f)
                }
                drawPath(
                        path = windshieldPath,
                        color = tint,
                        style = Stroke(width = strokeW * 0.85f, cap = StrokeCap.Round)
                )

                drawCircle(
                        color = tint,
                        radius = strokeW * 0.85f,
                        center = Offset(w * 0.24f, h * 0.58f)
                )
                drawCircle(
                        color = tint,
                        radius = strokeW * 0.85f,
                        center = Offset(w * 0.76f, h * 0.58f)
                )
        }
}

@Composable
fun BackButton(
        scope: CoroutineScope,
        modifier: Modifier = Modifier
) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val animatedBg by animateColorAsState(
                targetValue = if (isPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                animationSpec = tween(durationMillis = if (isPressed) 50 else 300),
                label = "backBg"
        )

        Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                        .dockPressScale(isPressed)
                        .size(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(animatedBg)
                        .clickable(
                                interactionSource = interactionSource,
                                indication = null
                        ) {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        ShizukuUtils.runCommandAndGetOutput(arrayOf("input", "keyevent", "4"))
                                }
                        }
        ) {
                Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = animatedDockTint(active = isPressed, idleColor = Color.White.copy(alpha = 0.85f)),
                        modifier = Modifier.size(26.dp)
                )
        }
}

@Composable
fun VehicleSettingsButton(
        modifier: Modifier = Modifier
) {
        val showSettings = BottomBarState.isSettingsMenuExpanded
        val serviceManager = br.com.redesurftank.havalshisuku.managers.ServiceManager.getInstance()
        var driveMode by remember {
                mutableStateOf(serviceManager.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue()))
        }
        DisposableEffect(Unit) {
                val listener =
                        object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                                override fun onDataChanged(key: String, value: String?) {
                                        if (value != null && key == CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue()) {
                                                driveMode = value
                                        }
                                }
                        }
                serviceManager.addDataChangedListener(listener)
                onDispose { serviceManager.removeDataChangedListener(listener) }
        }
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val conducaoColor by animateColorAsState(
                targetValue = if (isPressed || showSettings) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                animationSpec = tween(durationMillis = if (isPressed) 50 else 300),
                label = "conducaoColor"
        )

        Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                        .dockPressScale(isPressed)
                        .size(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(conducaoColor)
                        .onGloballyPositioned { coords ->
                                BottomBarState.conducaoCenterX = coords.positionInRoot().x + coords.size.width / 2f
                        }
                        .clickable(
                                interactionSource = interactionSource,
                                indication = null
                        ) {
                                BottomBarState.isSettingsMenuExpanded = !showSettings
                                if (BottomBarState.isSettingsMenuExpanded) {
                                        BottomBarState.isMenuExpanded = false
                                        BottomBarState.isOverrideMenuExpanded = false
                                        BottomBarState.isAcMenuExpanded = false
                                }
                        }
        ) {
                // Steering wheel + drive-mode badge (replaces the old gear-and-car PNG).
                DriveModeWheelIcon(
                        driveMode = driveMode,
                        tint = animatedDockTint(active = showSettings)
                )
        }
}

@Composable
fun CoffeeOsSeatVentilationButton(
        isDriver: Boolean,
        level: String,
        maxLevel: String,
        modifier: Modifier = Modifier,
        onLevelChange: (String) -> Unit
) {
        val parsedLevel = parseSeatVentilationLevel(level, maxLevel)
        val parsedMax = parseSeatVentilationMaxLevel(maxLevel).coerceAtMost(3)
        val isActive = parsedLevel > 0

        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val cyanColor = Color(0xFF00E5FF)

        // Icon: white while ventilating, gray when off. The level is shown only by the cyan dots.
        val iconTint = animatedDockTint(
                active = isActive,
                activeColor = Color.White,
                idleColor = Color.White.copy(alpha = 0.40f)
        )

        // Click feedback: the icon pops (spring) every time the level changes; the first composition
        // stays quiet.
        val iconPop = remember { Animatable(1f) }
        var seenLevel by remember { mutableStateOf(false) }
        LaunchedEffect(parsedLevel) {
                if (seenLevel) {
                        iconPop.snapTo(if (parsedLevel == 0) 0.82f else 1.22f)
                        iconPop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))
                } else {
                        seenLevel = true
                }
        }

        Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                        .dockPressScale(isPressed)
                        .size(width = 44.dp, height = 44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(
                                interactionSource = interactionSource,
                                indication = null
                        ) {
                                val next = nextSeatVentilationLevel(level, maxLevel)
                                onLevelChange(next)
                        }
        ) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                ) {
                        Icon(
                                painter = painterResource(id = R.drawable.ic_seat_ventilation),
                                contentDescription = if (isDriver) "Ventilação Motorista" else "Ventilação Passageiro",
                                tint = iconTint,
                                modifier = Modifier
                                        .size(23.dp)
                                        .graphicsLayer {
                                                scaleX = iconPop.value * (if (isDriver) -1f else 1f)
                                                scaleY = iconPop.value
                                        }
                        )
                        Spacer(modifier = Modifier.height(2.5.dp))
                        Row(
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                repeat(parsedMax) { index ->
                                        val step = index + 1
                                        val isLit = isActive && step <= parsedLevel
                                        // Springy fill: a dot overshoots slightly when it lights up.
                                        val lit by animateFloatAsState(
                                                targetValue = if (isLit) 1f else 0f,
                                                animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
                                                label = "seatDot"
                                        )
                                        Box(
                                                modifier = Modifier
                                                        .size(width = 5.dp, height = 2.5.dp)
                                                        .graphicsLayer {
                                                                val k = 0.85f + 0.15f * lit
                                                                scaleX = k
                                                                scaleY = k
                                                        }
                                                        .clip(RoundedCornerShape(1.dp))
                                                        .background(
                                                                androidx.compose.ui.graphics.lerp(
                                                                        Color.White.copy(alpha = 0.22f),
                                                                        cyanColor,
                                                                        lit.coerceIn(0f, 1f)
                                                                )
                                                        )
                                        )
                                }
                        }
                }
        }
}

@Composable
fun HomeButton(
        scope: CoroutineScope,
        context: Context,
        modifier: Modifier = Modifier
) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val animatedBg by animateColorAsState(
                targetValue = if (isPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                animationSpec = tween(durationMillis = if (isPressed) 50 else 300),
                label = "homeBg"
        )

        Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                        .dockPressScale(isPressed)
                        .size(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(animatedBg)
                        .clickable(
                                interactionSource = interactionSource,
                                indication = null
                        ) {
                                BottomBarState.isMenuExpanded = false
                                BottomBarState.isSettingsMenuExpanded = false
                                BottomBarState.isOverrideMenuExpanded = false
                                BottomBarState.isAcMenuExpanded = false

                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        ShizukuUtils.runCommandAndGetOutput(arrayOf("input", "keyevent", "3"))
                                }
                                val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                        addCategory(android.content.Intent.CATEGORY_HOME)
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                try {
                                        context.startActivity(homeIntent)
                                } catch (_: Exception) {}
                        }
        ) {
                CoffeeOsHomeIcon(
                        modifier = Modifier.size(24.dp),
                        tint = animatedDockTint(active = isPressed)
                )
        }
}


@Composable
fun FloatingSwipeHudOverlay(hud: BottomBarState.SwipeHudData) {
        val density = LocalDensity.current
        val hudWidthDp = 340.dp
        val hudHeightDp = 76.dp
        val hudWidthPx = with(density) { hudWidthDp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }

        val windowWidthPx =
                BottomBarState.overlayWindowWidthPx
                        .takeIf { it > 0 }
                        ?.toFloat()
                        ?: (LocalConfiguration.current.screenWidthDp * density.density)
        val gutterPx = BottomBarState.overlayLeftGutterPx.toFloat()

        val halfWidth = hudWidthPx / 2f
        val minCenterX = gutterPx + marginPx + halfWidth
        val maxCenterX = (windowWidthPx - marginPx - halfWidth).coerceAtLeast(minCenterX)
        val clampedCenterX = hud.targetCenterX.coerceIn(minCenterX, maxCenterX)
        val clampedStartXDp = with(density) { (clampedCenterX - halfWidth).toDp() }

        val arrowOffsetPx =
                (hud.targetCenterX - clampedCenterX).coerceIn(
                        -halfWidth + with(density) { 24.dp.toPx() },
                        halfWidth - with(density) { 24.dp.toPx() }
                )
        val arrowOffsetDp = with(density) { arrowOffsetPx.toDp() }

        Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.BottomStart
        ) {
                Column(
                        modifier = Modifier.offset(x = clampedStartXDp).width(hudWidthDp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Box(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .height(hudHeightDp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                                Brush.horizontalGradient(
                                                        0.0f to Color.Transparent,
                                                        0.10f to Color(0xF00C1017),
                                                        0.5f to Color(0xFC0C1017),
                                                        0.90f to Color(0xF00C1017),
                                                        1.0f to Color.Transparent
                                                )
                                        )
                        ) {
                                Column(
                                        modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                        // Centralized big value above bar (no title label)
                                        Text(
                                                text = hud.valueText,
                                                style = TextStyle(
                                                        fontFamily = Michroma,
                                                        fontSize = 22.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                ),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                        )

                                        if (hud.isFan) {
                                                Row(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                        for (step in 1..7) {
                                                                val active = step <= hud.fanSpeed
                                                                Box(
                                                                        modifier = Modifier
                                                                                .weight(1f)
                                                                                .height(7.dp)
                                                                                .clip(RoundedCornerShape(3.5.dp))
                                                                                .background(
                                                                                        if (active) Brush.horizontalGradient(
                                                                                                listOf(Color(0xFF00B0FF), Color(0xFF26D07C))
                                                                                        )
                                                                                        else SolidColor(Color.White.copy(alpha = 0.12f))
                                                                                )
                                                                )
                                                        }
                                                }
                                        } else {
                                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                                                        Box(
                                                                modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(6.dp)
                                                                        .clip(RoundedCornerShape(3.dp))
                                                                        .background(Color.White.copy(alpha = 0.14f))
                                                        ) {
                                                                Box(
                                                                        modifier = Modifier
                                                                                .fillMaxWidth(fraction = hud.fraction.coerceIn(0f, 1f))
                                                                                .fillMaxHeight()
                                                                                .clip(RoundedCornerShape(3.dp))
                                                                                .background(
                                                                                        Brush.horizontalGradient(
                                                                                                listOf(Color(0xFF1976D2), Color(0xFF00B0FF))
                                                                                        )
                                                                                )
                                                                )
                                                        }
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                                Text(
                                                                        text = hud.minLabel,
                                                                        style = labelStyle.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                                                )
                                                                Text(
                                                                        text = hud.maxLabel,
                                                                        style = labelStyle.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }

                        // Pointer arrow (colored with the solid center of the HUD, no blue border)
                        Box(
                                modifier = Modifier.offset(x = arrowOffsetDp).size(width = 14.dp, height = 7.dp)
                        ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                        val path = Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(size.width / 2f, size.height)
                                                close()
                                        }
                                        drawPath(path, color = Color(0xFC0C1017))
                                }
                        }
                }
        }
}

@Composable
fun AcQuickMenuContent() {
        val serviceManager = ServiceManager.getInstance()
        var fanSpeed by remember {
                mutableIntStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_FAN_SPEED.getValue())?.toIntOrNull() ?: 1
                )
        }
        var hvacPower by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_POWER_MODE.getValue()) ?: "1"
                )
        }
        var blowerMode by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_BLOWER_MODE.getValue()) ?: "0"
                )
        }
        var acSync by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue()) ?: "0"
                )
        }
        var acAuto by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue()) ?: "0"
                )
        }
        var acRecirc by remember {
                mutableStateOf(
                        if ((serviceManager.getData(CarConstants.CAR_HVAC_CYCLE_MODE.getValue()) ?: "0") == "0") "1" else "0"
                )
        }
        var acMax by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_ACMAX_ENABLE.getValue()) ?: "0"
                )
        }

        DisposableEffect(Unit) {
                val listener =
                        object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                                override fun onDataChanged(key: String, value: String?) {
                                        if (value == null) return
                                        when (key) {
                                                CarConstants.CAR_HVAC_FAN_SPEED.getValue() ->
                                                        fanSpeed = value.toIntOrNull() ?: fanSpeed
                                                CarConstants.CAR_HVAC_POWER_MODE.getValue() ->
                                                        hvacPower = value
                                                CarConstants.CAR_HVAC_BLOWER_MODE.getValue() ->
                                                        blowerMode = value
                                                CarConstants.CAR_HVAC_SYNC_ENABLE.getValue() ->
                                                        acSync = value
                                                CarConstants.CAR_HVAC_AUTO_ENABLE.getValue() ->
                                                        acAuto = value
                                                CarConstants.CAR_HVAC_CYCLE_MODE.getValue() ->
                                                        acRecirc = if (value == "0") "1" else "0"
                                                CarConstants.CAR_HVAC_ACMAX_ENABLE.getValue() ->
                                                        acMax = value
                                        }
                                }
                        }
                serviceManager.addDataChangedListener(listener)
                onDispose { serviceManager.removeDataChangedListener(listener) }
        }

        val isPowerOn = hvacPower == "1"

        fun ensurePowerOn() {
                if (!isPowerOn) {
                        hvacPower = "1"
                        serviceManager.updateDataAsync(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1")
                        if (fanSpeed == 0) {
                                fanSpeed = 1
                                serviceManager.updateDataAsync(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), "1")
                        }
                }
        }

        Box(
                modifier = Modifier
                        .width(420.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                                Brush.horizontalGradient(
                                        0.0f to Color.Transparent,
                                        0.08f to Color(0xF20F131A),
                                        0.92f to Color(0xF20F131A),
                                        1.0f to Color.Transparent
                                )
                        )
        ) {
                Column(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                        // Airflow Modes (4 modes directly from haval-h6-3d)
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                                val airflowModes = listOf(
                                        Triple("0", "Rosto", R.drawable.ic_hvac_blower_face),
                                        Triple("1", "Rosto/Pés", R.drawable.ic_hvac_blower_feet_and_face),
                                        Triple("2", "Pés", R.drawable.ic_hvac_blower_feet),
                                        Triple("3", "Pés/Vidro", R.drawable.ic_hvac_blower_feet_and_defrost)
                                )
                                for ((modeVal, modeLabel, modeRes) in airflowModes) {
                                        val isSelected = isPowerOn && blowerMode == modeVal
                                        Box(
                                                modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                                if (isSelected) Color(0xFF00B0FF).copy(alpha = 0.22f)
                                                                else Color.White.copy(alpha = 0.05f)
                                                        )
                                                        .border(
                                                                1.dp,
                                                                if (isSelected) Color(0xFF00B0FF).copy(alpha = 0.55f)
                                                                else Color.Transparent,
                                                                RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                                ensurePowerOn()
                                                                blowerMode = modeVal
                                                                serviceManager.updateDataAsync(
                                                                        CarConstants.CAR_HVAC_BLOWER_MODE.getValue(),
                                                                        modeVal
                                                                )
                                                        },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                ) {
                                                        Image(
                                                                painter = painterResource(modeRes),
                                                                contentDescription = modeLabel,
                                                                colorFilter = ColorFilter.tint(
                                                                        if (isSelected) Color(0xFF00B0FF)
                                                                        else if (isPowerOn) Color.White.copy(alpha = 0.75f)
                                                                        else Color.White.copy(alpha = 0.3f)
                                                                ),
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                                text = modeLabel,
                                                                style = labelStyle.copy(
                                                                        fontSize = 9.sp,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                        color = if (isSelected) Color(0xFF00B0FF)
                                                                        else if (isPowerOn) Color.White.copy(alpha = 0.75f)
                                                                        else Color.White.copy(alpha = 0.3f)
                                                                ),
                                                                maxLines = 1
                                                        )
                                                }
                                        }
                                }
                        }

                        // AC Control Options Grid (using authentic haval-h6-3d vector drawables)
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                                AcQuickMenuItem(
                                        modifier = Modifier.weight(1f),
                                        painter = painterResource(R.drawable.ic_hvac_recirc),
                                        label = "Circular",
                                        isActive = isPowerOn && acRecirc == "1",
                                        isEnabled = true
                                ) {
                                        ensurePowerOn()
                                        val next = if (acRecirc == "1") "0" else "1"
                                        val carValue = if (next == "0") "1" else "0"
                                        serviceManager.updateDataAsync(CarConstants.CAR_HVAC_CYCLE_MODE.getValue(), carValue)
                                }

                                AcQuickMenuItem(
                                        modifier = Modifier.weight(1f),
                                        painter = painterResource(R.drawable.ic_hvac_sync),
                                        label = "Sync",
                                        isActive = isPowerOn && acSync == "1",
                                        isEnabled = true
                                ) {
                                        ensurePowerOn()
                                        val next = if (acSync == "1") "0" else "1"
                                        serviceManager.updateDataAsync(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue(), next)
                                }

                                AcQuickMenuItem(
                                        modifier = Modifier.weight(1f),
                                        painter = painterResource(R.drawable.ic_hvac_auto),
                                        label = "Auto",
                                        isActive = isPowerOn && acAuto == "1",
                                        isEnabled = true
                                ) {
                                        ensurePowerOn()
                                        val next = if (acAuto == "1") "0" else "1"
                                        serviceManager.updateDataAsync(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(), next)
                                }

                                AcQuickMenuItem(
                                        modifier = Modifier.weight(1f),
                                        painter = painterResource(R.drawable.ic_hvac_ac),
                                        label = "A/C Max",
                                        isActive = isPowerOn && acMax == "1",
                                        isEnabled = true
                                ) {
                                        ensurePowerOn()
                                        val next = if (acMax == "1") "0" else "1"
                                        serviceManager.updateDataAsync(CarConstants.CAR_HVAC_ACMAX_ENABLE.getValue(), next)
                                }
                        }

                        // Fan Control at Bottom (Moved to bottom, matching haval-h6-3d model)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                text = "VELOCIDADE DO AR",
                                                style = labelStyle.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.LightGray
                                                )
                                        )
                                        Text(
                                                text = if (!isPowerOn || fanSpeed == 0) "OFF" else "Nível $fanSpeed",
                                                style = TextStyle(
                                                        fontFamily = Michroma,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isPowerOn && fanSpeed > 0) Color(0xFF26D07C) else Color.White.copy(alpha = 0.4f)
                                                )
                                        )
                                }

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        // Decrement button with small fan icon
                                        Box(
                                                modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.White.copy(alpha = 0.08f))
                                                        .clickable {
                                                                if (isPowerOn) {
                                                                        val nextSpeed = (fanSpeed - 1).coerceAtLeast(0)
                                                                        fanSpeed = nextSpeed
                                                                        serviceManager.updateDataAsync(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), nextSpeed.toString())
                                                                        if (nextSpeed == 0) {
                                                                                hvacPower = "0"
                                                                                serviceManager.updateDataAsync(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "0")
                                                                        }
                                                                }
                                                        },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        painter = painterResource(R.drawable.ic_hvac_fan),
                                                        contentDescription = "Diminuir ventilador",
                                                        tint = if (isPowerOn && fanSpeed > 0) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f),
                                                        modifier = Modifier.size(16.dp)
                                                )
                                        }

                                        // 7 capsule rounded bars (the haval-h6-3d model)
                                        Row(
                                                modifier = Modifier
                                                        .weight(1f)
                                                        .height(34.dp),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                for (step in 1..7) {
                                                        val active = isPowerOn && step <= fanSpeed
                                                        Box(
                                                                modifier = Modifier
                                                                        .weight(1f)
                                                                        .height(10.dp)
                                                                        .clip(RoundedCornerShape(5.dp))
                                                                        .background(
                                                                                if (active) Brush.horizontalGradient(
                                                                                        listOf(Color(0xFF00B0FF), Color(0xFF26D07C))
                                                                                )
                                                                                else SolidColor(Color.White.copy(alpha = 0.12f))
                                                                        )
                                                                        .clickable {
                                                                                if (!isPowerOn) {
                                                                                        hvacPower = "1"
                                                                                        serviceManager.updateDataAsync(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1")
                                                                                }
                                                                                fanSpeed = step
                                                                                serviceManager.updateDataAsync(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), step.toString())
                                                                        }
                                                        )
                                                }
                                        }

                                        // Increment button with big fan icon
                                        Box(
                                                modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.White.copy(alpha = 0.08f))
                                                        .clickable {
                                                                if (!isPowerOn) {
                                                                        hvacPower = "1"
                                                                        serviceManager.updateDataAsync(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1")
                                                                }
                                                                val nextSpeed = (fanSpeed + 1).coerceAtMost(7)
                                                                fanSpeed = nextSpeed
                                                                serviceManager.updateDataAsync(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), nextSpeed.toString())
                                                        },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        painter = painterResource(R.drawable.ic_hvac_fan),
                                                        contentDescription = "Aumentar ventilador",
                                                        tint = Color.White.copy(alpha = 0.9f),
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun AcQuickMenuItem(
        modifier: Modifier = Modifier,
        painter: Painter? = null,
        iconVector: ImageVector? = null,
        label: String,
        isActive: Boolean,
        isEnabled: Boolean,
        onClick: () -> Unit
) {
        val activeColor = Color(0xFF2196F3)
        val bgColor = if (isActive && isEnabled) activeColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f)
        val borderColor = if (isActive && isEnabled) activeColor.copy(alpha = 0.6f) else Color.Transparent

        Box(
                modifier =
                        modifier.height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                .background(bgColor)
                                .clickable(enabled = isEnabled) { onClick() }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
        ) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        val iconTint: Color = if (isActive && isEnabled) activeColor else if (isEnabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f)
                        if (painter != null) {
                                Icon(
                                        painter = painter,
                                        contentDescription = label,
                                        modifier = Modifier.size(22.dp),
                                        tint = iconTint
                                )
                        } else if (iconVector != null) {
                                Icon(
                                        imageVector = iconVector,
                                        contentDescription = label,
                                        modifier = Modifier.size(22.dp),
                                        tint = iconTint
                                )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                                text = label,
                                style =
                                        labelStyle.copy(
                                                fontSize = 10.sp,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isActive && isEnabled) activeColor else Color.White.copy(alpha = if (isEnabled) 0.85f else 0.35f)
                                        ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
        }
}

@Composable
fun FanButton(
	fanSpeed: Int,
	isPowerOn: Boolean,
	onSpeedChange: (Int) -> Unit,
	onClick: () -> Unit
) {
	val scope = rememberCoroutineScope()
	var dismissJob by remember { mutableStateOf<Job?>(null) }
	var centerX by remember { mutableFloatStateOf(0f) }
	val isAcMenuExpanded = BottomBarState.isAcMenuExpanded
	val isActive = isPowerOn && fanSpeed > 0

	val currentSpeedRef by rememberUpdatedState(fanSpeed)

	fun postHud(newSpeed: Int, autoDismiss: Boolean = true) {
		BottomBarState.activeSwipeHud = BottomBarState.SwipeHudData(
			type = BottomBarState.SliderType.FAN,
			title = "VENTILA\u00c7\u00c3O",
			valueText = if (newSpeed == 0) "OFF" else "N\u00edvel $newSpeed",
			fraction = (newSpeed / 7f).coerceIn(0f, 1f),
			targetCenterX = centerX,
			minLabel = "OFF",
			maxLabel = "MAX (7)",
			isFan = true,
			fanSpeed = newSpeed
		)
		dismissJob?.cancel()
		if (autoDismiss) {
			dismissJob = scope.launch {
				delay(1000)
				BottomBarState.activeSwipeHud = null
			}
		}
	}

	val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
	val isPressed by interactionSource.collectIsPressedAsState()
	val glowColor = Color(0xFF2196F3).copy(alpha = 0.35f)
	val animatedColor by animateColorAsState(
		targetValue = if (isPressed || isAcMenuExpanded) glowColor else Color.Transparent,
		animationSpec = tween(durationMillis = if (isPressed) 50 else 300)
	)

	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.size(46.dp)
			.clip(RoundedCornerShape(11.dp))
			.background(animatedColor)
			.onGloballyPositioned { coords ->
				centerX = coords.positionInRoot().x + coords.size.width / 2f
				BottomBarState.fanButtonCenterX = centerX
			}
			.pointerInput(Unit) {
				awaitPointerEventScope {
					while (true) {
						val down = awaitFirstDown(requireUnconsumed = false)
						val startX = down.position.x
						val startY = down.position.y
						var totalDragX = 0f
						var isDragging = false
						var accumulatedDragX = 0f
						var activeSpeed = currentSpeedRef

						do {
							val event = awaitPointerEvent()
							val change = event.changes.firstOrNull { it.id == down.id } ?: break
							if (!change.pressed) break

							val deltaX = change.position.x - change.previousPosition.x
							val deltaY = change.position.y - change.previousPosition.y
							totalDragX += deltaX

							if (!isDragging && abs(totalDragX) > 10.dp.toPx() && abs(totalDragX) > abs(change.position.y - startY)) {
								isDragging = true
								activeSpeed = currentSpeedRef
								postHud(activeSpeed, autoDismiss = false)
							}

							if (isDragging) {
								change.consume()
								accumulatedDragX += deltaX
								val stepPx = 27.dp.toPx()
								if (abs(accumulatedDragX) >= stepPx) {
									val numSteps = (abs(accumulatedDragX) / stepPx).toInt()
									val stepDirection = if (accumulatedDragX > 0) 1 else -1
									accumulatedDragX -= stepDirection * numSteps * stepPx
									val newSpeed = (activeSpeed + stepDirection * numSteps).coerceIn(0, 7)
									if (newSpeed != activeSpeed) {
										activeSpeed = newSpeed
										onSpeedChange(newSpeed)
										postHud(newSpeed, autoDismiss = false)
									}
								}
							}
						} while (change.pressed)

						if (isDragging) {
							dismissJob?.cancel()
							dismissJob = scope.launch {
								delay(1000)
								BottomBarState.activeSwipeHud = null
							}
						} else {
							onClick()
						}
					}
				}
			}
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.Center
		) {
			CoffeeOsFanIcon(
				modifier = Modifier.size(25.dp),
				tint = if (isAcMenuExpanded) Color(0xFF2196F3) else if (isActive) Color.White else Color.White.copy(alpha = 0.4f)
			)
			Spacer(modifier = Modifier.width(2.dp))
			Text(
				text = if (!isPowerOn || fanSpeed == 0) "OFF" else "$fanSpeed",
				style = TextStyle(
					fontFamily = Michroma,
					fontSize = if (!isPowerOn || fanSpeed == 0) 9.5.sp else 12.sp,
					fontWeight = FontWeight.Bold,
					color = if (isAcMenuExpanded) Color(0xFF2196F3) else if (isActive) Color.White else Color.White.copy(alpha = 0.4f)
				),
				modifier = Modifier.align(Alignment.Bottom).padding(bottom = 7.dp)
			)
		}
	}
}

enum class ChevronDirection {
	LEFT,
	RIGHT
}

/**
 * Enviar / Trazer: two fixed-size tiles (same 46dp footprint as the dock tiles). They no longer
 * change shape with the left navigation pane, so the left group keeps a constant width.
 */
@Composable
fun LeftArrowsSection(
	hasSecondaryApps: Boolean,
	widthProgress: Float,
	wideExtraDp: Float,
	scope: CoroutineScope,
	context: Context
) {
	val configs = getBottomBarAppConfigs()
	val activePkg = BottomBarState.currentPackage

	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		modifier = Modifier.fillMaxHeight()
	) {
		// [Enviar (<)] main display -> cluster
		ClusterTransferTile(
			direction = ChevronDirection.LEFT,
			enabled = true,
			description = "Enviar app para o painel",
			widthProgress = widthProgress,
			wideWidthDp = V2_TRANSFER_COMPACT_WIDTH_DP + wideExtraDp,
			onClick = {
				val targetPkg =
					if (activePkg.isNotEmpty()) activePkg
					else configs.firstOrNull()?.packageName ?: ""
				Log.w(BOTTOM_BAR_TAG, "Dock: Send to Cluster clicked for package=$targetPkg")
				scope.launch {
					DisplayAppLauncher.getOrCreateDefaultConfig(context, targetPkg)?.let { cfg ->
						DisplayAppLauncher.sendToDisplay(cfg)
						BottomBarState.hasAppsOnSecondaryDisplays = true
					}
				}
			}
		)

		// [Trazer (>)] cluster -> main display
		ClusterTransferTile(
			direction = ChevronDirection.RIGHT,
			enabled = hasSecondaryApps,
			description = "Trazer app para a central",
			widthProgress = widthProgress,
			wideWidthDp = V2_TRANSFER_COMPACT_WIDTH_DP + wideExtraDp,
			onClick = {
				Log.w(BOTTOM_BAR_TAG, "Dock: Bring to Main Display clicked")
				scope.launch {
					val moved = DisplayAppLauncher.bringAllToMainDisplay()
					Log.w(BOTTOM_BAR_TAG, "Dock: bringAllToMainDisplay moved=${moved.joinToString(",")}")
					BottomBarState.hasAppsOnSecondaryDisplays = false
				}
			}
		)
	}
}

enum class ProjectionType {
	NONE,
	CARPLAY,
	ANDROID_AUTO
}

/**
 * What the dock's phone-link tile does. An idle icon opens BeanTechs connectivity
 * even when CarPlay and Android Auto are installed; those packages only launch
 * while a session is already on screen.
 */
internal enum class ProjectionShortcutAction {
	OPEN_CARPLAY,
	OPEN_ANDROID_AUTO,
	OPEN_CONNECTIVITY
}

internal fun resolveProjectionShortcutAction(active: ProjectionType): ProjectionShortcutAction =
	when (active) {
		ProjectionType.CARPLAY -> ProjectionShortcutAction.OPEN_CARPLAY
		ProjectionType.ANDROID_AUTO -> ProjectionShortcutAction.OPEN_ANDROID_AUTO
		ProjectionType.NONE -> ProjectionShortcutAction.OPEN_CONNECTIVITY
	}

internal fun buildPhoneConnectIntent(): Intent =
	Intent().apply {
		component = ComponentName(PHONE_CONNECT_PACKAGE, PHONE_CONNECT_ACTIVITY)
		addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
		putExtra("position", PHONE_CONNECT_POSITION)
	}

/**
 * Same path as Impulse Home: explicit BeanTechs settings activity on the Conectar tab,
 * then that package's launcher activity, then the stock Settings root.
 */
internal fun launchPhoneConnectSettings(context: Context) {
	val pm = context.packageManager
	val beanInstalled = try {
		pm.getLaunchIntentForPackage(PHONE_CONNECT_PACKAGE) != null
	} catch (e: Exception) {
		Log.w(BOTTOM_BAR_TAG, "Unable to resolve $PHONE_CONNECT_PACKAGE", e)
		false
	}
	if (beanInstalled) {
		try {
			Log.w(BOTTOM_BAR_TAG, "Opening BeanTechs connectivity $PHONE_CONNECT_ACTIVITY position=$PHONE_CONNECT_POSITION")
			context.startActivity(buildPhoneConnectIntent())
			return
		} catch (e: Exception) {
			Log.w(BOTTOM_BAR_TAG, "Direct BeanTechs connectivity launch failed", e)
			br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
				.launchAnyAppDetached(context, PHONE_CONNECT_PACKAGE, PHONE_CONNECT_ACTIVITY)
			return
		}
	}
	try {
		val intent = Intent(Intent.ACTION_MAIN).apply {
			addCategory(Intent.CATEGORY_LAUNCHER)
			component = ComponentName(STOCK_SETTINGS_PACKAGE, STOCK_SETTINGS_ACTIVITY)
			setPackage(STOCK_SETTINGS_PACKAGE)
			addFlags(
				Intent.FLAG_ACTIVITY_NEW_TASK or
					Intent.FLAG_ACTIVITY_CLEAR_TASK or
					Intent.FLAG_ACTIVITY_CLEAR_TOP or
					Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
			)
		}
		Log.w(BOTTOM_BAR_TAG, "BeanTechs settings missing; opening stock Settings root")
		context.startActivity(intent)
	} catch (e: Exception) {
		Log.w(BOTTOM_BAR_TAG, "Stock Settings root launch failed", e)
	}
}

@Composable
private fun rememberActiveProjection(activePkg: String): ProjectionType {
	val activeClusterPkg = BottomBarState.activeClusterProjectionPackage
	return remember(activePkg, activeClusterPkg) {
		val projOnMain = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
			.resolveProjectionPackageOrNull(activePkg)
		val projOnCluster = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
			.resolveProjectionPackageOrNull(activeClusterPkg)
		when {
			projOnMain == BOTTOM_BAR_CARPLAY_PACKAGE ||
			projOnCluster == BOTTOM_BAR_CARPLAY_PACKAGE ||
			activePkg.contains("carplay", ignoreCase = true) ||
			br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isCarPlayOnDisplay(0) ||
			br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isCarPlayOnDisplay(3) -> ProjectionType.CARPLAY

			projOnMain == BOTTOM_BAR_ANDROID_AUTO_PACKAGE ||
			projOnCluster == BOTTOM_BAR_ANDROID_AUTO_PACKAGE ||
			activePkg.contains("androidauto", ignoreCase = true) ||
			activePkg.contains("projection.gearhead", ignoreCase = true) ||
			br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isAndroidAutoOnDisplay(0) ||
			br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isAndroidAutoOnDisplay(3) -> ProjectionType.ANDROID_AUTO

			else -> ProjectionType.NONE
		}
	}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CenteredAppLauncherSection(
	contentWidthDp: Float = Float.MAX_VALUE,
	leftGroupWidthDp: Float = V2_LEFT_GROUP_WIDTH_DP,
	fanSpeed: Int = 1,
	isPowerOn: Boolean = true,
	onSpeedChange: (Int) -> Unit = {},
	onFanClick: () -> Unit = {},
	scope: CoroutineScope,
	context: Context
) {
	val configsVersion = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.configsVersion
	val configs = remember(configsVersion) { getBottomBarAppConfigs() }
	val isAppMenuExpanded = BottomBarState.isMenuExpanded
	val isDockEditMode = BottomBarState.isDockEditMode
	val activePkg = BottomBarState.currentPackage



	var draggedPkg by remember { mutableStateOf<String?>(null) }
	var dragAccumulatedX by remember { mutableFloatStateOf(0f) }
	val density = LocalDensity.current
	val slotStepPx = with(density) { 58.dp.toPx() }

	val prefs = remember { context.getSharedPreferences("haval_prefs", Context.MODE_PRIVATE) }
	LaunchedEffect(Unit) {
		BottomBarState.isProjectionShortcutEnabled = prefs.getBoolean(
			SharedPreferencesKeys.BOTTOM_BAR_SHOW_PROJECTION_SHORTCUT.key,
			true
		)
	}
	val isProjectionShortcutEnabled = BottomBarState.isProjectionShortcutEnabled
	val activeProjection = rememberActiveProjection(activePkg)

	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		// Tiles that join/leave the dock (edit slots, dynamic app) change the row width; animating the
		// container makes the centred dock glide instead of re-centring in a single frame.
		modifier = Modifier
			.fillMaxHeight()
			.animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing))
	) {
	// 1. [ ← ] Back Button - FIRST IN DOCK!
		BackButton(scope = scope)

		// 2. [ ⊞ ] All Apps Button
		val appsInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
		val appsPressed by appsInteraction.collectIsPressedAsState()
		val appsColor by animateColorAsState(
			targetValue = if (appsPressed || isAppMenuExpanded) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
			animationSpec = tween(durationMillis = if (appsPressed) 50 else 300),
			label = "appsColor"
		)
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier
				.dockPressScale(appsPressed)
				.size(46.dp)
				.clip(RoundedCornerShape(11.dp))
				.background(appsColor)
				.onGloballyPositioned { coords ->
					BottomBarState.launcherCenterX = coords.positionInRoot().x + coords.size.width / 2f
				}
				.clickable(
					interactionSource = appsInteraction,
					indication = null
				) {
					BottomBarState.isMenuExpanded = !isAppMenuExpanded
					if (BottomBarState.isMenuExpanded) {
						BottomBarState.isSettingsMenuExpanded = false
						BottomBarState.isOverrideMenuExpanded = false
						BottomBarState.isAcMenuExpanded = false
						BottomBarState.isDockEditMode = false
						BottomBarState.dockAddSlotIndex = null
					}
				}
		) {
			Icon(
				imageVector = Icons.Default.Apps,
				contentDescription = "All Apps",
				tint = animatedDockTint(active = isAppMenuExpanded, idleColor = Color.White),
				modifier = Modifier.size(28.dp)
			)
		}

		// 2.5. Dedicated First Icon: Android Auto / CarPlay / Connectivity Shortcut
		if (isProjectionShortcutEnabled) {
			val projInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
			val projPressed by projInteraction.collectIsPressedAsState()
			val projColor by animateColorAsState(
				targetValue = if (projPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
				animationSpec = tween(durationMillis = if (projPressed) 50 else 300),
				label = "projColor"
			)

			val isProjectionOnMain = activeProjection != ProjectionType.NONE &&
				(activePkg.contains("carplay", ignoreCase = true) ||
				 activePkg.contains("androidauto", ignoreCase = true) ||
				 activePkg.contains("projection.gearhead", ignoreCase = true) ||
				 br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.resolveProjectionPackageOrNull(activePkg) != null)

			val projJiggle = rememberDockJiggle(isDockEditMode, phaseMs = 0)
			Box(
				modifier = Modifier
					.size(50.dp)
					.onGloballyPositioned { coordinates ->
						BottomBarState.projectionSlotCenterX = coordinates.positionInRoot().x + coordinates.size.width / 2f
					}
					.graphicsLayer {
						rotationZ = projJiggle.value
					},
				contentAlignment = Alignment.Center
			) {
				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier
						.dockPressScale(projPressed)
						.size(44.dp)
						.clip(RoundedCornerShape(11.dp))
						.background(projColor)
						.combinedClickable(
							interactionSource = projInteraction,
							indication = null,
							onClick = {
								when (resolveProjectionShortcutAction(activeProjection)) {
									ProjectionShortcutAction.OPEN_CARPLAY -> {
										scope.launch {
											br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
												.launchAnyApp(context, BOTTOM_BAR_CARPLAY_PACKAGE)
										}
									}
									ProjectionShortcutAction.OPEN_ANDROID_AUTO -> {
										scope.launch {
											br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
												.launchAnyApp(context, BOTTOM_BAR_ANDROID_AUTO_PACKAGE)
										}
									}
									ProjectionShortcutAction.OPEN_CONNECTIVITY -> {
										launchPhoneConnectSettings(context)
									}
								}
							},
							onLongClick = {
								BottomBarState.isDockEditMode = true
							}
						)
				) {
					when (activeProjection) {
						ProjectionType.CARPLAY -> {
							Image(
								painter = painterResource(id = R.drawable.ic_carplay_default),
								contentDescription = "Apple CarPlay",
								modifier = Modifier.size(36.dp)
							)
						}
						ProjectionType.ANDROID_AUTO -> {
							Image(
								painter = painterResource(id = R.drawable.ic_android_auto_default),
								contentDescription = "Android Auto",
								modifier = Modifier.size(36.dp)
							)
						}
						ProjectionType.NONE -> {
							Icon(
								painter = painterResource(id = R.drawable.ic_phone_link),
								contentDescription = "Conectividade",
								tint = Color.Unspecified,
								modifier = Modifier.size(36.dp)
							)
						}
					}

					DockActiveIndicator(
						active = isProjectionOnMain,
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.padding(bottom = 2.dp)
					)
				}

				// Badge de remoção no modo de edição (UNCROPPED)
				DockEditBadge(
					visible = isDockEditMode,
					color = Color(0xFFE53935),
					icon = Icons.Default.Close,
					contentDescription = "Desabilitar Atalho de Projeção",
					modifier = Modifier.align(Alignment.TopEnd),
					onClick = {
						BottomBarState.isProjectionShortcutEnabled = false
						prefs.edit()
							.putBoolean(
								SharedPreferencesKeys.BOTTOM_BAR_SHOW_PROJECTION_SHORTCUT.key,
								false
							)
							.apply()
					}
				)
			}
		} else if (isDockEditMode) {
			// Quando desabilitado, exibe slot com '+' para reabilitar no modo de edição
			Box(
				modifier = Modifier
					.dockAppear()
					.size(50.dp)
					.onGloballyPositioned { coordinates ->
						BottomBarState.projectionSlotCenterX = coordinates.positionInRoot().x + coordinates.size.width / 2f
					},
				contentAlignment = Alignment.Center
			) {
				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier
						.size(44.dp)
						.clip(RoundedCornerShape(11.dp))
						.border(
							width = 1.2.dp,
							color = Color(0xFF2196F3).copy(alpha = 0.5f),
							shape = RoundedCornerShape(11.dp)
						)
						.background(Color(0xFF2196F3).copy(alpha = 0.08f))
						.clickable {
							BottomBarState.isProjectionShortcutEnabled = true
							prefs.edit()
								.putBoolean(
									SharedPreferencesKeys.BOTTOM_BAR_SHOW_PROJECTION_SHORTCUT.key,
									true
								)
								.apply()
						}
				) {
					Icon(
						painter = painterResource(id = R.drawable.ic_phone_link),
						contentDescription = "Adicionar Atalho de Projeção",
						tint = Color.White.copy(alpha = 0.5f),
						modifier = Modifier.size(24.dp)
					)
					Box(
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(3.dp)
							.size(12.dp)
							.clip(CircleShape)
							.background(Color(0xFF2196F3)),
						contentAlignment = Alignment.Center
					) {
						Icon(
							imageVector = Icons.Default.Add,
							contentDescription = null,
							tint = Color.White,
							modifier = Modifier.size(8.dp)
						)
					}
				}
			}
		}

		// 3. Up to 7/8 Apps selected in Telas menu, strictly preserving Telas order
		val candidateConfigs = remember(configsVersion) {
			val telas = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs()
			if (telas.isNotEmpty()) telas else configs
		}
		val maxRegularApps = if (isProjectionShortcutEnabled) 7 else 8
		val validDockConfigs = remember(candidateConfigs, configsVersion, isProjectionShortcutEnabled) {
			val filtered = candidateConfigs.filter { cfg ->
				(cfg.substituteIcon != null || br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.resolveAppInfo(context, cfg.packageName, cfg.customName).icon != null) &&
				(!isProjectionShortcutEnabled || !br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isProjectionLikePackage(cfg.packageName))
			}
			val validPackages = resolveDockApps(
				telasPackages = filtered.map { it.packageName },
				maxApps = maxRegularApps
			)
			validPackages.mapNotNull { pkg -> filtered.firstOrNull { it.packageName == pkg } }
		}

		for ((index, config) in validDockConfigs.withIndex()) {
			val pkg = config.packageName
			val isCurrentApp = pkg == activePkg
			val isBeingDragged = draggedPkg == pkg
			val subIcon = config.substituteIcon
			val substituteIconVector = getSubstituteIconVector(subIcon)
			val iconTint = config.iconColor.toComposeColor()

			val appInfo = remember(pkg, config.customName, configsVersion) {
				br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.resolveAppInfo(context, pkg, config.customName)
			}
			val displayName = config.customName?.takeIf { it.isNotBlank() } ?: appInfo.label

			val appInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
			val appPressed by appInteraction.collectIsPressedAsState()
			val appJiggle = rememberDockJiggle(isDockEditMode && !isBeingDragged, phaseMs = (index % 4) * 40)
			val appColor by animateColorAsState(
				targetValue = if (appPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
				animationSpec = tween(durationMillis = if (appPressed) 50 else 300),
				label = "appColor"
			)

			val gestureModifier = if (isDockEditMode) {
				Modifier.pointerInput(pkg) {
					detectDragGestures(
						onDragStart = {
							draggedPkg = pkg
							dragAccumulatedX = 0f
						},
						onDrag = { change, dragAmount ->
							change.consume()
							dragAccumulatedX += dragAmount.x
							if (dragAccumulatedX > slotStepPx && index < validDockConfigs.lastIndex) {
								br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.moveConfigDown(pkg)
								dragAccumulatedX -= slotStepPx
							} else if (dragAccumulatedX < -slotStepPx && index > 0) {
								br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.moveConfigUp(pkg)
								dragAccumulatedX += slotStepPx
							}
						},
						onDragEnd = {
							draggedPkg = null
							dragAccumulatedX = 0f
						},
						onDragCancel = {
							draggedPkg = null
							dragAccumulatedX = 0f
						}
					)
				}
			} else {
				Modifier.combinedClickable(
					interactionSource = appInteraction,
					indication = null,
					onClick = {
						RecentAppsManager.recordAppLaunch(pkg)
						scope.launch {
							br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.launchAnyApp(context, pkg)
						}
					},
					onLongClick = {
						BottomBarState.isDockEditMode = true
					}
				)
			}

			Box(
				modifier = Modifier
					.size(50.dp)
					.graphicsLayer {
						rotationZ = if (isBeingDragged) 0f else appJiggle.value
						translationX = if (isBeingDragged) dragAccumulatedX else 0f
						scaleX = if (isBeingDragged) 1.1f else 1f
						scaleY = if (isBeingDragged) 1.1f else 1f
					}
					.zIndex(if (isBeingDragged) 10f else 1f),
				contentAlignment = Alignment.Center
			) {
				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier
						.dockPressScale(appPressed)
						.size(44.dp)
						.clip(RoundedCornerShape(11.dp))
						.background(appColor)
						.then(gestureModifier)
				) {
					if (subIcon == "youtube" || subIcon == "youtube_music" || subIcon == "gwm") {
						Image(
							painter = painterResource(
								id = when (subIcon) {
									"youtube" -> R.drawable.ic_youtube_default
									"youtube_music" -> R.drawable.ic_youtube_music_default
									"gwm" -> R.drawable.ic_gwm
									else -> R.drawable.ic_youtube_default
								}
							),
							contentDescription = displayName,
							modifier = Modifier.size(36.dp)
						)
					} else if (substituteIconVector != null) {
						Icon(
							imageVector = substituteIconVector,
							contentDescription = displayName,
							tint = iconTint,
							modifier = Modifier.size(36.dp)
						)
					} else {
						AsyncImage(
							model = ImageRequest.Builder(context).data(appInfo.icon).build(),
							contentDescription = displayName,
							modifier = Modifier.size(36.dp)
						)
					}

					DockActiveIndicator(
						active = isCurrentApp,
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.padding(bottom = 2.dp)
					)
				}

				// Badge de remoção no modo de edição (UNCROPPED)
				DockEditBadge(
					visible = isDockEditMode,
					color = Color(0xFFE53935),
					icon = Icons.Default.Close,
					contentDescription = "Remover",
					modifier = Modifier.align(Alignment.TopEnd),
					onClick = {
						scope.launch {
							br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.deleteConfig(pkg)
							br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.configsVersion++
						}
					}
				)
			}
		}

		// Slots vazios [+] visíveis no modo de edição
		if (isDockEditMode) {
			val emptyCount = (maxRegularApps - validDockConfigs.size).coerceAtLeast(0)
			for (i in 0 until emptyCount) {
				val slotIndex = validDockConfigs.size + i
				Box(
					modifier = Modifier.dockAppear().size(50.dp),
					contentAlignment = Alignment.Center
				) {
					Box(
						contentAlignment = Alignment.Center,
						modifier = Modifier
							.size(44.dp)
							.clip(RoundedCornerShape(11.dp))
							.border(
								width = 1.2.dp,
								color = Color(0xFF2196F3).copy(alpha = 0.5f),
								shape = RoundedCornerShape(11.dp)
							)
							.background(Color(0xFF2196F3).copy(alpha = 0.08f))
							.clickable {
								BottomBarState.dockAddSlotIndex = slotIndex
							}
					) {
						Icon(
							imageVector = Icons.Default.Add,
							contentDescription = "Adicionar ao Dock",
							tint = Color(0xFF64B5F6),
							modifier = Modifier.size(20.dp)
						)
					}
				}
			}
		}

		// 3.5. Slot Dinâmico para App Ativo que NÃO está fixado no Dock
		val isDynamicAppVisible = remember(activePkg, validDockConfigs, isProjectionShortcutEnabled) {
			activePkg.isNotBlank() &&
			!validDockConfigs.any { it.packageName == activePkg } &&
			!shouldHideFromAllApps(activePkg) &&
			activePkg != context.packageName &&
			(!isProjectionShortcutEnabled || !br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isProjectionLikePackage(activePkg))
		}

		// v2 uses fixed widths (no weights like v1): skip the extra tile when it would crowd the left group.
		val dockFitsDynamicSlot = resolveLeftGroupClearanceDp(
			contentWidthDp = contentWidthDp,
			dockWidthDp = estimateDockWidthDp(
				appTiles = if (isDockEditMode) maxRegularApps else validDockConfigs.size,
				showProjectionTile = isProjectionShortcutEnabled || isDockEditMode,
				showDynamicSlot = true,
				editMode = isDockEditMode
			),
			leftGroupWidthDp = leftGroupWidthDp
		) >= V2_MIN_LEFT_CLEARANCE_DP

		if (isDynamicAppVisible && dockFitsDynamicSlot) {
			// Separador vertical sutil
			Box(
				modifier = Modifier
					.dockAppear()
					.padding(horizontal = 2.dp)
					.width(1.dp)
					.height(26.dp)
					.clip(RoundedCornerShape(0.5.dp))
					.background(Color.White.copy(alpha = 0.25f))
			)

			val dynamicAppInfo = remember(activePkg, configsVersion) {
				br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.resolveAppInfo(context, activePkg)
			}
			val dynamicConfig = candidateConfigs.find { it.packageName == activePkg }
			val dynamicSubIcon = dynamicConfig?.substituteIcon
			val dynamicSubVector = getSubstituteIconVector(dynamicSubIcon)
			val dynamicIconTint = dynamicConfig?.iconColor.toComposeColor()
			val dynamicDisplayName = dynamicConfig?.customName?.takeIf { it.isNotBlank() } ?: dynamicAppInfo.label

			val dynamicInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
			val dynamicPressed by dynamicInteraction.collectIsPressedAsState()
			val dynamicColor by animateColorAsState(
				targetValue = if (dynamicPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
				animationSpec = tween(durationMillis = if (dynamicPressed) 50 else 300),
				label = "dynamicColor"
			)

			Box(
				modifier = Modifier.dockAppear().size(50.dp),
				contentAlignment = Alignment.Center
			) {
				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier
						.dockPressScale(dynamicPressed)
						.size(44.dp)
						.clip(RoundedCornerShape(11.dp))
						.background(dynamicColor)
						.combinedClickable(
							interactionSource = dynamicInteraction,
							indication = null,
							onClick = {
								RecentAppsManager.recordAppLaunch(activePkg)
								scope.launch {
									br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.launchAnyApp(context, activePkg)
								}
							},
							onLongClick = {
								BottomBarState.isDockEditMode = true
							}
						)
				) {
					if (dynamicSubIcon == "youtube" || dynamicSubIcon == "youtube_music" || dynamicSubIcon == "gwm") {
						Image(
							painter = painterResource(
								id = when (dynamicSubIcon) {
									"youtube" -> R.drawable.ic_youtube_default
									"youtube_music" -> R.drawable.ic_youtube_music_default
									"gwm" -> R.drawable.ic_gwm
									else -> R.drawable.ic_youtube_default
								}
							),
							contentDescription = dynamicDisplayName,
							modifier = Modifier.size(36.dp)
						)
					} else if (dynamicSubVector != null) {
						Icon(
							imageVector = dynamicSubVector,
							contentDescription = dynamicDisplayName,
							tint = dynamicIconTint,
							modifier = Modifier.size(36.dp)
						)
					} else {
						AsyncImage(
							model = ImageRequest.Builder(context).data(dynamicAppInfo.icon).build(),
							contentDescription = dynamicDisplayName,
							modifier = Modifier.size(36.dp)
						)
					}

					// Barra azul de app ativo
					DockActiveIndicator(
						active = true,
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.padding(bottom = 2.dp)
					)
				}

				// Badge [ 📌 ] no modo de edição para fixar direto no dock (UNCROPPED)
				DockEditBadge(
					visible = isDockEditMode,
					color = Color(0xFF4CAF50),
					icon = Icons.Default.PushPin,
					contentDescription = "Fixar no Dock",
					modifier = Modifier.align(Alignment.TopEnd),
					onClick = {
						scope.launch {
							br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
								.getOrCreateDefaultConfig(context, activePkg)
							br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
								.configsVersion++
						}
					}
				)
			}
		}

		// 4. Se em modo de edição, exibe botão [ ✓ Concluir ]; caso contrário, Condução e Home
		AnimatedContent(
			targetState = isDockEditMode,
			transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
			label = "dockTail"
		) { editing ->
			if (editing) {
				val doneInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
				val donePressed by doneInteraction.collectIsPressedAsState()
				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier
						.dockPressScale(donePressed)
						.height(46.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(Color(0xFF2196F3).copy(alpha = 0.25f))
						.border(1.dp, Color(0xFF2196F3), RoundedCornerShape(12.dp))
						.clickable(
							interactionSource = doneInteraction,
							indication = null
						) {
							BottomBarState.isDockEditMode = false
							BottomBarState.dockAddSlotIndex = null
						}
						.padding(horizontal = 14.dp)
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp)
					) {
						Icon(
							imageVector = Icons.Default.Check,
							contentDescription = "Concluir",
							tint = Color(0xFF64B5F6),
							modifier = Modifier.size(18.dp)
						)
						Text(
							text = "Concluir",
							style = TextStyle(
								color = Color.White,
								fontSize = 12.sp,
								fontFamily = Michroma,
								fontWeight = FontWeight.Bold
							)
						)
					}
				}
			} else {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp)
				) {
					// [ 🚗 ] Vehicle Settings (Condução - CoffeeOS 3.0 style)
					VehicleSettingsButton()

					// [ 🏠 ] Home Button (CoffeeOS 3.0 style)
					HomeButton(scope = scope, context = context)
				}
			}
		}
	}
}

@Composable
private fun BoxScope.BottomBarNewContent(
        driverTemp: String,
        onDriverTempChange: (String) -> Unit,
        passTemp: String,
        onPassTempChange: (String) -> Unit,
        volume: Int,
        onVolumeChange: (Int) -> Unit,
        fanSpeed: Int,
        onFanSpeedChange: (Int) -> Unit,
        hvacPower: String,
        onHvacPowerChange: (String) -> Unit,
        driverSeatVentilation: String,
        onDriverSeatVentilationChange: (String) -> Unit,
        passengerSeatVentilation: String,
        onPassengerSeatVentilationChange: (String) -> Unit,
        seatVentilationMaxLevel: String,
        contentWidthDp: Float,
        leftGapDp: Float,
        isLeftBarShown: Boolean,
        serviceManager: ServiceManager,
        scope: CoroutineScope,
        barContext: Context
) {
        val isACEnabled = hvacPower == "1"

        // Layout is absolute, like v1: from the driver temperature on, nothing moves when the left
        // navigation pane shows or hides. The strip the pane covers ([leftGapDp]) belongs to the left
        // cluster; only Enviar/Trazer use it. They are compact while the pane (or the AA/CarPlay rail)
        // is there and grow leftwards into the strip when it is hidden, while a spacer in front of them
        // shrinks by exactly the same amount.
        val wideCapable = leftGapDp >= V2_MIN_WIDE_GAP_DP
        val transferProgress by animateFloatAsState(
                targetValue = if (isLeftBarShown || !wideCapable) 0f else 1f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "transferProgress"
        )
        val leftClusterWidth = (V2_SIDE_CLUSTER_WIDTH_DP + leftGapDp).dp
        val rightClusterWidth = V2_SIDE_CLUSTER_WIDTH_DP.dp

        // Left cluster | fan | dock | volume | right cluster.
        // Both clusters have the same fixed width, so the dock stays exactly centred; fan and volume
        // are centred in the free space between each cluster and the dock (weights), which also
        // makes them glide when the dock grows or shrinks.
        Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
        ) {
                // 1. Left cluster: pane strip, Enviar/Trazer, driver temp, driver seat
                Row(
                        modifier = Modifier.width(leftClusterWidth),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Spacer(modifier = Modifier.width((leftGapDp * (1f - transferProgress)).dp))

                        LeftArrowsSection(
                                hasSecondaryApps = BottomBarState.hasAppsOnSecondaryDisplays,
                                widthProgress = transferProgress,
                                wideExtraDp = leftGapDp / 2f,
                                scope = scope,
                                context = barContext
                        )

                        Spacer(modifier = Modifier.width(V2_LEFT_TILES_GAP_DP.dp))

                        TempControlSection(
                                label = "Motorista",
                                temp = driverTemp,
                                isEnabled = isACEnabled,
                                sliderType = BottomBarState.SliderType.DRIVER_TEMP,
                                onTempChange = { newTemp ->
                                        val formatted = String.format(java.util.Locale.US, "%.1f", newTemp)
                                        onDriverTempChange(formatted)
                                        serviceManager.updateDataAsync(
                                                CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(),
                                                formatted
                                        )
                                        if (!isACEnabled) {
                                                onHvacPowerChange("1")
                                                serviceManager.updateDataAsync(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1")
                                        }
                                }
                        )

                        Spacer(modifier = Modifier.width(24.dp))

                        CoffeeOsSeatVentilationButton(
                                isDriver = true,
                                level = driverSeatVentilation,
                                maxLevel = seatVentilationMaxLevel,
                                onLevelChange = { nextLevel ->
                                        onDriverSeatVentilationChange(nextLevel)
                                        updateSeatVentilationLevel(
                                                serviceManager,
                                                CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL,
                                                nextLevel
                                        )
                                }
                        )
                }

                // 2. Fan, centred between the left cluster and the dock
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FanControlSection(
                                speed = fanSpeed,
                                isPowerOn = isACEnabled,
                                sliderType = BottomBarState.SliderType.FAN,
                                onSpeedChange = { newSpeed ->
                                        onFanSpeedChange(newSpeed)
                                        serviceManager.updateDataAsync(
                                                CarConstants.CAR_HVAC_FAN_SPEED.getValue(),
                                                newSpeed.toString()
                                        )
                                        if (newSpeed == 0 && isACEnabled) {
                                                onHvacPowerChange("0")
                                                serviceManager.updateDataAsync(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "0")
                                        } else if (newSpeed > 0 && !isACEnabled) {
                                                onHvacPowerChange("1")
                                                serviceManager.updateDataAsync(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1")
                                        }
                                },
                                onFanClick = {
                                        BottomBarState.isAcMenuExpanded = !BottomBarState.isAcMenuExpanded
                                        if (BottomBarState.isAcMenuExpanded) {
                                                BottomBarState.isMenuExpanded = false
                                                BottomBarState.isSettingsMenuExpanded = false
                                                BottomBarState.isOverrideMenuExpanded = false
                                        }
                                }
                        )
                }

                // 3. Centre: app dock (Back, Launcher, projection, apps, Condução, Home)
                CenteredAppLauncherSection(
                        contentWidthDp = contentWidthDp,
                        scope = scope,
                        context = barContext
                )

                // 4. Volume, centred between the dock and the right cluster
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        VolumeControlSection(
                                label = "Volume",
                                volume = volume,
                                sliderType = BottomBarState.SliderType.VOLUME,
                                onVolumeChange = { newVol ->
                                        onVolumeChange(newVol)
                                        serviceManager.updateDataAsync(
                                                CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue(),
                                                newVol.toString()
                                        )
                                }
                        )
                }

                // 5. Right cluster: passenger seat, passenger temp, Override
                Row(
                        modifier = Modifier.width(rightClusterWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                ) {
                        CoffeeOsSeatVentilationButton(
                                isDriver = false,
                                level = passengerSeatVentilation,
                                maxLevel = seatVentilationMaxLevel,
                                onLevelChange = { nextLevel ->
                                        onPassengerSeatVentilationChange(nextLevel)
                                        updateSeatVentilationLevel(
                                                serviceManager,
                                                CarConstants.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL,
                                                nextLevel
                                        )
                                }
                        )

                        Spacer(modifier = Modifier.width(24.dp))

                        TempControlSection(
                                label = "Passageiro",
                                temp = passTemp,
                                isEnabled = isACEnabled,
                                sliderType = BottomBarState.SliderType.PASS_TEMP,
                                onTempChange = { newTemp ->
                                        val formatted = String.format(java.util.Locale.US, "%.1f", newTemp)
                                        onPassTempChange(formatted)
                                        serviceManager.updateDataAsync(
                                                CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(),
                                                formatted
                                        )
                                        if (!isACEnabled) {
                                                onHvacPowerChange("1")
                                                serviceManager.updateDataAsync(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1")
                                        }
                                }
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Override Section
                        val isOverrideExpanded = BottomBarState.isOverrideMenuExpanded
                        val overrideInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val overridePressed by overrideInteraction.collectIsPressedAsState()
                        val overrideColor by animateColorAsState(
                                targetValue = if (overridePressed || isOverrideExpanded) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                                animationSpec = tween(durationMillis = if (overridePressed) 50 else 300)
                        )
                        Box(
                                modifier =
                                        Modifier.width(44.dp)
                                                .fillMaxHeight()
                                                .pointerInput(Unit) {
                                                        awaitPointerEventScope {
                                                                while (true) {
                                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                                        var totalDrag = 0f
                                                                        do {
                                                                                val event = awaitPointerEvent()
                                                                                val change = event.changes.first()
                                                                                totalDrag += (change.position - change.previousPosition).getDistance()
                                                                        } while (event.changes.any { it.pressed })
                                                                        if (totalDrag < 10f) {
                                                                                BottomBarState.isOverrideMenuExpanded = !BottomBarState.isOverrideMenuExpanded
                                                                                if (BottomBarState.isOverrideMenuExpanded) {
                                                                                        BottomBarState.isMenuExpanded = false
                                                                                        BottomBarState.isSettingsMenuExpanded = false
                                                                                        BottomBarState.isAcMenuExpanded = false
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                },
                                contentAlignment = Alignment.Center
                        ) {
                                Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(overrideColor)
                                ) {
                                        Icon(
                                                Icons.Default.SwapVert,
                                                contentDescription = "Override",
                                                tint = if (isOverrideExpanded) Color(0xFF2196F3) else Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                        }
                        Spacer(modifier = Modifier.width(82.dp))
                }
        }
}

@Composable
private fun BoxScope.BottomBarOldContent(
        driverTemp: String,
        onDriverTempChange: (String) -> Unit,
        passTemp: String,
        onPassTempChange: (String) -> Unit,
        volume: Int,
        onVolumeChange: (Int) -> Unit,
        fanSpeed: Int,
        onFanSpeedChange: (Int) -> Unit,
        hvacPower: String,
        onHvacPowerChange: (String) -> Unit,
        acSync: String,
        acAuto: String,
        acRecirc: String,
        serviceManager: ServiceManager,
        scope: CoroutineScope
) {
        val isACEnabled = hvacPower == "1"

        Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
        ) {
                // 1. App Switcher (11%)
                Box(modifier = Modifier.weight(0.11f)) {
                        AppSwitcherSection()
                }

                // 2. AC Driver (14%)
                Box(
                        modifier = Modifier.weight(0.14f),
                        contentAlignment = Alignment.Center
                ) {
                        ClassicTempControlSection(
                                label = "Motorista",
                                temp = driverTemp,
                                isEnabled = isACEnabled,
                                sliderType = BottomBarState.SliderType.DRIVER_TEMP,
                                onValueChange = { delta ->
                                        val newTemp = (driverTemp.toFloatOrNull() ?: 22.0f) + delta
                                        val formatted = String.format(java.util.Locale.US, "%.1f", newTemp)
                                        onDriverTempChange(formatted)
                                        serviceManager.updateData(
                                                CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(),
                                                formatted
                                        )
                                        if (!isACEnabled) {
                                                onHvacPowerChange("1")
                                                serviceManager.updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1")
                                        }
                                }
                        )
                }

                // 3. Controls Group (Back, Settings) (14%)
                Box(
                        modifier = Modifier.weight(0.14f),
                        contentAlignment = Alignment.Center
                ) {
                        ClassicControlsSection(scope)
                }

                // 4. AC Fan Speed (14%)
                Box(
                        modifier = Modifier.weight(0.14f),
                        contentAlignment = Alignment.Center
                ) {
                        ClassicFanControlSection(
                                speed = fanSpeed,
                                isEnabled = true,
                                sliderType = BottomBarState.SliderType.FAN,
                                onValueChange = { delta ->
                                        val calculatedSpeed = (fanSpeed + delta).coerceIn(0, 7)
                                        onFanSpeedChange(calculatedSpeed)
                                        serviceManager.updateData(
                                                CarConstants.CAR_HVAC_FAN_SPEED.getValue(),
                                                calculatedSpeed.toString()
                                        )
                                        if (calculatedSpeed == 0 && isACEnabled) {
                                                onHvacPowerChange("0")
                                                serviceManager.updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "0")
                                        } else if (calculatedSpeed > 0 && !isACEnabled) {
                                                onHvacPowerChange("1")
                                                serviceManager.updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1")
                                        }
                                }
                        )
                }

                // 5. AC Recirc/Sync/Auto (14%)
                Box(
                        modifier = Modifier.weight(0.14f),
                        contentAlignment = Alignment.Center
                ) {
                        Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                ACControlButton(
                                        icon = if (acRecirc == "1") recycleIn else recycleOut,
                                        label = "Circular",
                                        isActive = acRecirc == "1",
                                        isEnabled = isACEnabled
                                ) {
                                        val next = if (acRecirc == "1") "0" else "1"
                                        val carValue = if (next == "0") "1" else "0"
                                        serviceManager.updateData(CarConstants.CAR_HVAC_CYCLE_MODE.getValue(), carValue)
                                }
                                ACControlButton(
                                        icon = Icons.Default.Sync,
                                        label = "Sync",
                                        isActive = acSync == "1",
                                        isEnabled = isACEnabled
                                ) {
                                        val next = if (acSync == "1") "0" else "1"
                                        serviceManager.updateData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue(), next)
                                }
                                ACControlButton(
                                        icon = Icons.Default.AutoMode,
                                        label = "Auto",
                                        isActive = acAuto == "1",
                                        isEnabled = isACEnabled
                                ) {
                                        val next = if (acAuto == "1") "0" else "1"
                                        serviceManager.updateData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(), next)
                                }
                        }
                }

                // 6. Volume (14%)
                Box(
                        modifier = Modifier.weight(0.14f),
                        contentAlignment = Alignment.Center
                ) {
                        ClassicVolumeControlSection(
                                label = "Volume",
                                volume = volume,
                                sliderType = BottomBarState.SliderType.VOLUME,
                                onValueChange = { delta ->
                                        val newVol = (volume + delta).coerceIn(0, 30)
                                        onVolumeChange(newVol)
                                        serviceManager.updateData(
                                                CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue(),
                                                newVol.toString()
                                        )
                                }
                        )
                }

                // 7. AC Passenger (14%)
                Box(
                        modifier = Modifier.weight(0.14f),
                        contentAlignment = Alignment.Center
                ) {
                        ClassicTempControlSection(
                                label = "Passageiro",
                                temp = passTemp,
                                isEnabled = isACEnabled,
                                sliderType = BottomBarState.SliderType.PASS_TEMP,
                                onValueChange = { delta ->
                                        val newTemp = (passTemp.toFloatOrNull() ?: 22.0f) + delta
                                        val formatted = String.format(java.util.Locale.US, "%.1f", newTemp)
                                        onPassTempChange(formatted)
                                        serviceManager.updateData(
                                                CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(),
                                                formatted
                                        )
                                        if (!isACEnabled) {
                                                onHvacPowerChange("1")
                                                serviceManager.updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1")
                                        }
                                }
                        )
                }

                // 8. Override Section (5%)
                Box(
                        modifier =
                                Modifier.weight(0.05f)
                                        .pointerInput(Unit) {
                                                awaitPointerEventScope {
                                                        while (true) {
                                                                val down =
                                                                        awaitFirstDown(
                                                                                requireUnconsumed =
                                                                                        false
                                                                        )
                                                                var totalDrag = 0f
                                                                do {
                                                                        val event =
                                                                                awaitPointerEvent()
                                                                        val change =
                                                                                event.changes
                                                                                        .first()
                                                                        totalDrag +=
                                                                                (change.position -
                                                                                                change.previousPosition)
                                                                                        .getDistance()
                                                                } while (event.changes
                                                                        .any {
                                                                                it.pressed
                                                                        })
                                                                if (totalDrag < 30f) {
                                                                        BottomBarState
                                                                                .isOverrideMenuExpanded =
                                                                                !BottomBarState
                                                                                        .isOverrideMenuExpanded
                                                                        if (BottomBarState
                                                                                        .isOverrideMenuExpanded
                                                                        ) {
                                                                                BottomBarState
                                                                                        .isMenuExpanded =
                                                                                        false
                                                                                BottomBarState
                                                                                        .isSettingsMenuExpanded =
                                                                                        false
                                                                        }
                                                                }
                                                        }
                                                }
                                        },
                        contentAlignment = Alignment.Center
                ) {
                        Icon(
                                Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f)
                        )
                }
        }
}

@Composable
fun BottomBarContent() {
        val serviceManager = ServiceManager.getInstance()
        val scope = rememberCoroutineScope()

        // States for AC, Volume, etc.
        var driverTemp by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue())
                                ?: "--"
                )
        }
        var passTemp by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue())
                                ?: "--"
                )
        }
        var volume by remember {
                mutableIntStateOf(
                        serviceManager
                                .getData(CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue())
                                ?.toIntOrNull()
                                ?: 0
                )
        }
        var powerModel by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue()
                        )
                                ?: "0"
                )
        }
        var energyRecovery by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue()
                        )
                                ?: "0"
                )
        }
        var driveMode by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue())
                                ?: "0"
                )
        }
        var steeringMode by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue()
                        )
                                ?: "0"
                )
        }
        var fanSpeed by remember {
                mutableIntStateOf(
                        serviceManager
                                .getData(CarConstants.CAR_HVAC_FAN_SPEED.getValue())
                                ?.toIntOrNull()
                                ?: 1
                )
        }
        var hvacPower by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_POWER_MODE.getValue()) ?: "1"
                )
        }
        var acSync by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue()) ?: "0"
                )
        }
        var acAuto by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue()) ?: "0"
                )
        }
        var acRecirc by remember {
                mutableStateOf(
                        if ((serviceManager.getData(CarConstants.CAR_HVAC_CYCLE_MODE.getValue()) ?: "0") == "0") "1" else "0"
                )
        }

        var driverSeatVentilation by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.getValue()
                        ) ?: "0"
                )
        }
        var passengerSeatVentilation by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL.getValue()
                        ) ?: "0"
                )
        }
        var seatVentilationMaxLevel by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL.getValue()
                        ) ?: "3"
                )
        }

        // Update states when data changes
        DisposableEffect(Unit) {
                val listener =
                        object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                                override fun onDataChanged(key: String, value: String?) {
                                        if (value == null) return
                                        when (key) {
                                                CarConstants.CAR_HVAC_DRIVER_TEMPERATURE
                                                        .getValue() -> driverTemp = value
                                                CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue() ->
                                                        passTemp = value
                                                CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL
                                                        .getValue() -> driverSeatVentilation = value
                                                CarConstants.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL
                                                        .getValue() -> passengerSeatVentilation = value
                                                CarConstants.CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL
                                                        .getValue() -> seatVentilationMaxLevel = value
                                                CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME
                                                        .getValue() ->
                                                        volume = value.toIntOrNull() ?: volume
                                                CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG
                                                        .getValue() -> powerModel = value
                                                CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL
                                                        .getValue() -> energyRecovery = value
                                                CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE
                                                        .getValue() -> driveMode = value
                                                CarConstants
                                                        .CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE
                                                        .getValue() -> steeringMode = value
                                                CarConstants.CAR_HVAC_FAN_SPEED.getValue() ->
                                                        fanSpeed = value.toIntOrNull() ?: fanSpeed
                                                CarConstants.CAR_HVAC_POWER_MODE.getValue() ->
                                                        hvacPower = value
                                                CarConstants.CAR_HVAC_SYNC_ENABLE.getValue() ->
                                                        acSync = value
                                                CarConstants.CAR_HVAC_AUTO_ENABLE.getValue() ->
                                                        acAuto = value
                                                CarConstants.CAR_HVAC_CYCLE_MODE.getValue() ->
                                                        acRecirc = if (value == "0") "1" else "0"
                                                CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue() -> {
                                                        val spd = value.toFloatOrNull() ?: 0f
                                                        if (spd > 5f) {
                                                                BottomBarState.isDockEditMode = false
                                                                BottomBarState.dockAddSlotIndex = null
                                                        }
                                                }
                                        }
                                }
                        }
                serviceManager.addDataChangedListener(listener)
                onDispose { serviceManager.removeDataChangedListener(listener) }
        }

        // The window spans the physical display, so inset the content past the left navigation pane
        // (which draws above us). Constant at runtime, so the bar never reflows when a fullscreen app
        // hides the pane.
        val leftGutterPx = BottomBarState.overlayLeftGutterPx
        val androidAutoOnMain = isAndroidAutoShownOnMainDisplay(BottomBarState.currentPackage)
        val isLeftBarShown = androidAutoOnMain || leftGutterPx > 0
        // What Enviar/Trazer follow: the pane (or AA/CarPlay rail) actually on screen. The reserved gutter
        // above stays constant so the layout never reflows; this only switches wide/compact.
        val isLeftPaneOccupied = androidAutoOnMain || (leftGutterPx > 0 && BottomBarState.leftNavPaneShown)
        val aaCutoutPx = resolveAndroidAutoBottomBarCutoutPx(androidAutoOnMain)
        val density = LocalDensity.current
        // Punch only the AA rail hole in the black strip; keep the button Row at the same physical
        // gutter as when AA is off so weights/positions do not jump.
        val surfaceStartPad = with(density) { aaCutoutPx.toDp() }
        val rowStartPad =
                with(density) {
                        resolveBottomBarRowStartPadPx(leftGutterPx, aaCutoutPx).toDp()
                }
        val barContext = LocalContext.current
        // Safe left padding to comfortably accommodate the car's physical vertical rail without cramping the controls
        val effectiveStartPad = maxOf(rowStartPad + 16.dp, 36.dp)
        // v2 keeps ONE absolute layout (as v1 does): the gutter never changes with the pane's visibility.
        // The strip the pane covers is part of the left cluster instead ([v2LeftGap]); the content
        // itself only keeps a small edge margin in front of it.
        val v2TargetStartPad = if (isLeftBarShown) effectiveStartPad else DOCK_MIN_EDGE_PAD
        val v2LeftGap = (v2TargetStartPad - DOCK_MIN_EDGE_PAD).coerceAtLeast(0.dp)

        LaunchedEffect(Unit) {
                while (isActive) {
                        val hasSec = withContext(Dispatchers.IO) {
                                DisplayAppLauncher.hasAppsOnSecondaryDisplays()
                        }
                        BottomBarState.hasAppsOnSecondaryDisplays = hasSec
                        delay(2500)
                }
        }

        // Note the gutter is applied to the content Row below, NOT here: the black Surface has to span
        // the whole window so the bar reads as one continuous strip. Padding it here leaves the left
        // 128px transparent, which shows through as a gap whenever the app behind is not dark.
        // Exception: when Android Auto owns display 0 we pad the Surface by the AA cutout only (not
        // the full gutter) so the rail icon stays visible without reflowing the buttons.
        Box(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                contentAlignment = Alignment.BottomCenter
        ) {
                if (BottomBarState.isVisible && !BottomBarState.isDashboardExpanded) {
                        Surface(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(start = surfaceStartPad)
                                                .height(60.dp)
                                                // Swipe-down gesture: if user drags down > 20dp,
                                                // hide the bar.
                                                // Taps and small movements pass through to buttons
                                                // untouched.
                                                .pointerInput(Unit) {
                                                        awaitPointerEventScope {
                                                                while (true) {
                                                                        val down =
                                                                                awaitFirstDown(
                                                                                        requireUnconsumed =
                                                                                                false
                                                                                )
                                                                        var totalDragX = 0f
                                                                        var totalDragY = 0f
                                                                        var isHorizontalLocked = false

                                                                        do {
                                                                                val event =
                                                                                        awaitPointerEvent()
                                                                                val change =
                                                                                        event.changes
                                                                                                .firstOrNull() ?: break
                                                                                val deltaX =
                                                                                        change.position.x -
                                                                                                change.previousPosition.x
                                                                                val deltaY =
                                                                                        change.position.y -
                                                                                                change.previousPosition.y
                                                                                totalDragX += deltaX
                                                                                totalDragY += deltaY

                                                                                val absX = kotlin.math.abs(totalDragX)
                                                                                val absY = kotlin.math.abs(totalDragY)
                                                                                if (absX > 15f && absX > absY) {
                                                                                        isHorizontalLocked = true
                                                                                }

                                                                                val decision = resolveBottomBarSwipeAction(
                                                                                        totalDragX = totalDragX,
                                                                                        totalDragY = totalDragY,
                                                                                        isHorizontalLocked = isHorizontalLocked,
                                                                                        isChildConsumed = change.isConsumed
                                                                                )

                                                                                if (decision != BottomBarSwipeDecision.NONE) {
                                                                                        event.changes
                                                                                                .forEach {
                                                                                                        it.consume()
                                                                                                }
                                                                                        // Consume
                                                                                        // remaining
                                                                                        // pointer
                                                                                        // events
                                                                                        do {
                                                                                                val ev2 =
                                                                                                        awaitPointerEvent()
                                                                                                ev2.changes
                                                                                                        .forEach {
                                                                                                                it.consume()
                                                                                                        }
                                                                                        } while (ev2.changes
                                                                                                .any {
                                                                                                        it.pressed
                                                                                                })
                                                                                        if (decision == BottomBarSwipeDecision.EXPAND) {
                                                                                                performSwipeUpAction(
                                                                                                        barContext
                                                                                                )
                                                                                        } else {
                                                                                                BottomBarState
                                                                                                        .isDashboardExpanded =
                                                                                                        false
                                                                                                BottomBarState
                                                                                                        .isVisible =
                                                                                                        false
                                                                                        }
                                                                                        break
                                                                                }
                                                                        } while (event.changes.any {
                                                                                it.pressed
                                                                        })
                                                                        // If finger lifted without
                                                                        // crossing threshold → do
                                                                        // nothing (button handles
                                                                        // it)
                                                                }
                                                        }
                                                },
                                color = Color.Black,
                                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
                                tonalElevation = 0.dp
                        ) {
                                // The window is a fixed physical width now, so the layout no longer has
                                // to compensate for a measured width that changed with the left
                                // navigation pane - the constant gutter below does that job.
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterEnd
                                ) {
                                        val effectiveRowPad = if (BottomBarState.barVersion == BottomBarState.BarVersion.NEW.key) DOCK_MIN_EDGE_PAD else rowStartPad
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .fillMaxHeight()
                                                                .padding(
                                                                        start = effectiveRowPad,
                                                                        end = 8.dp
                                                                )
                                        ) {
                                                if (BottomBarState.barVersion == BottomBarState.BarVersion.NEW.key) {
                                                        BottomBarNewContent(
                                                                driverTemp = driverTemp,
                                                                onDriverTempChange = { driverTemp = it },
                                                                passTemp = passTemp,
                                                                onPassTempChange = { passTemp = it },
                                                                volume = volume,
                                                                onVolumeChange = { volume = it },
                                                                fanSpeed = fanSpeed,
                                                                onFanSpeedChange = { fanSpeed = it },
                                                                hvacPower = hvacPower,
                                                                onHvacPowerChange = { hvacPower = it },
                                                                driverSeatVentilation = driverSeatVentilation,
                                                                onDriverSeatVentilationChange = { driverSeatVentilation = it },
                                                                passengerSeatVentilation = passengerSeatVentilation,
                                                                onPassengerSeatVentilationChange = { passengerSeatVentilation = it },
                                                                seatVentilationMaxLevel = seatVentilationMaxLevel,
                                                                isLeftBarShown = isLeftPaneOccupied,
                                                                leftGapDp = v2LeftGap.value,
                                                                contentWidthDp = with(density) {
                                                                        val windowPx = BottomBarState.overlayWindowWidthPx
                                                                        if (windowPx > 0) {
                                                                                windowPx.toDp().value - v2TargetStartPad.value - 8f
                                                                        } else {
                                                                                Float.MAX_VALUE
                                                                        }
                                                                },
                                                                serviceManager = serviceManager,
                                                                scope = scope,
                                                                barContext = barContext
                                                        )
                                                } else {
                                                        BottomBarOldContent(
                                                                driverTemp = driverTemp,
                                                                onDriverTempChange = { driverTemp = it },
                                                                passTemp = passTemp,
                                                                onPassTempChange = { passTemp = it },
                                                                volume = volume,
                                                                onVolumeChange = { volume = it },
                                                                fanSpeed = fanSpeed,
                                                                onFanSpeedChange = { fanSpeed = it },
                                                                hvacPower = hvacPower,
                                                                onHvacPowerChange = { hvacPower = it },
                                                                acSync = acSync,
                                                                acAuto = acAuto,
                                                                acRecirc = acRecirc,
                                                                serviceManager = serviceManager,
                                                                scope = scope
                                                        )
                                                }
                                        }
                                }
                        }
                }

                if (!BottomBarState.isVisible && !BottomBarState.isDashboardExpanded) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(60.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(Color.Transparent)
                                                // Swipe-up gesture: if user drags upward > 30dp,
                                                // show the bar
                                                .pointerInput(Unit) {
                                                        awaitPointerEventScope {
                                                                while (true) {
                                                                        awaitFirstDown(
                                                                                requireUnconsumed =
                                                                                        false
                                                                        )
                                                                        var totalDragX = 0f
                                                                        var totalDragY = 0f
                                                                        var isHorizontalLocked = false

                                                                        do {
                                                                                val event =
                                                                                        awaitPointerEvent()
                                                                                val change =
                                                                                        event.changes
                                                                                                .firstOrNull() ?: break
                                                                                val deltaX =
                                                                                        change.position.x -
                                                                                                change.previousPosition.x
                                                                                val deltaY =
                                                                                        change.position.y -
                                                                                                change.previousPosition.y
                                                                                totalDragX += deltaX
                                                                                totalDragY += deltaY

                                                                                val absX = kotlin.math.abs(totalDragX)
                                                                                val absY = kotlin.math.abs(totalDragY)
                                                                                if (absX > 15f && absX > absY) {
                                                                                        isHorizontalLocked = true
                                                                                }

                                                                                // Confirmed upward swipe → show the bar (unless horizontal gesture was intended)
                                                                                if (!isHorizontalLocked && !change.isConsumed && totalDragY < -30f) {
                                                                                        event.changes
                                                                                                .forEach {
                                                                                                        it.consume()
                                                                                                }
                                                                                        do {
                                                                                                val ev2 =
                                                                                                        awaitPointerEvent()
                                                                                                ev2.changes
                                                                                                        .forEach {
                                                                                                                it.consume()
                                                                                                        }
                                                                                        } while (ev2.changes
                                                                                                .any {
                                                                                                        it.pressed
                                                                                                })
                                                                                        BottomBarState
                                                                                                .isVisible =
                                                                                                true
                                                                                        BottomBarState
                                                                                                .isDashboardExpanded =
                                                                                                false
                                                                                        break
                                                                                }
                                                                        } while (event.changes.any {
                                                                                it.pressed
                                                                        })
                                                                }
                                                        }
                                                }
                        )
                }
        }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun getSubstituteIconVector(substituteIcon: String?): ImageVector? {
        return when (substituteIcon) {
                "nav" -> Icons.Default.Place
                "music" -> Icons.Default.PlayArrow
                "video" -> Icons.Default.Movie
                "settings" -> Icons.Default.Tune
                "haval" -> Icons.Default.DirectionsCar
                "game" -> Icons.Default.SportsEsports
                "tv" -> Icons.Default.Tv
                "phone" -> Icons.Default.Phone
                "chat" -> Icons.Default.Chat
                "map_alt" -> Icons.Default.Map
                else -> null
        }
}

@Composable
fun AppSwitcherSection() {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val configsVersion = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.configsVersion
        val configs = remember(configsVersion) { getBottomBarAppConfigs() }

        // Initialize if empty
        if (br.com.redesurftank.havalshisuku.models.BottomBarState.selectedPackage.isEmpty()) {
                br.com.redesurftank.havalshisuku.models.BottomBarState.selectedPackage =
                        configs.firstOrNull()?.packageName ?: ""
        }

        val selectedPackage = br.com.redesurftank.havalshisuku.models.BottomBarState.selectedPackage
        val showMenu = br.com.redesurftank.havalshisuku.models.BottomBarState.isMenuExpanded
        val projectionPackageOnMain = getProjectionPackageOnMainForBottomBar()
        val effectiveSelectedPackage =
                resolveBottomBarEffectivePackage(
                        projectionPackageOnMain = projectionPackageOnMain,
                        selectedPackage = selectedPackage,
                        firstConfiguredPackage = configs.firstOrNull()?.packageName ?: ""
                )

        val selectedConfig = configs.find { it.packageName == effectiveSelectedPackage }
        val substituteIconVector = getSubstituteIconVector(selectedConfig?.substituteIcon)

        Row(verticalAlignment = Alignment.CenterVertically) {
                val leftNavInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val leftNavPressed by leftNavInteraction.collectIsPressedAsState()
                val leftNavColor by animateColorAsState(
                        targetValue = if (leftNavPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                        animationSpec = tween(durationMillis = if (leftNavPressed) 50 else 300)
                )
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .width(70.dp)
                                .fillMaxHeight()
                                .clickable(
                                        interactionSource = leftNavInteraction,
                                        indication = null
                                ) {
                                        Log.w(
                                                BOTTOM_BAR_TAG,
                                                "Cluster send click: selectedPackage=$selectedPackage, effectivePackage=$effectiveSelectedPackage"
                                        )
                                        scope.launch {
                                                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                                        .getOrCreateDefaultConfig(
                                                                context,
                                                                effectiveSelectedPackage
                                                        )
                                                        ?.let {
                                                                Log.w(
                                                                        BOTTOM_BAR_TAG,
                                                                        "Cluster send dispatch: package=${it.packageName}, display=${it.displayId}"
                                                                )
                                                                br.com.redesurftank.havalshisuku.managers
                                                                        .DisplayAppLauncher.sendToDisplay(
                                                                        it
                                                                )
                                                        }
                                        }
                                }
                ) {
                        Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(leftNavColor)
                        ) {
                                Icon(
                                        Icons.Default.KeyboardArrowLeft,
                                        contentDescription = "Cluster",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }
                Box(
                        modifier =
                                Modifier.size(55.dp)
                                        .background(Color.Black, RoundedCornerShape(4.dp))
                                        .pointerInput(showMenu, selectedPackage) {
                                                detectTapGestures(
                                                        onTap = {
                                                                br.com.redesurftank.havalshisuku
                                                                        .models.BottomBarState
                                                                        .isMenuExpanded = !showMenu
                                                        },
                                                        onDoubleTap = {
                                                                if (effectiveSelectedPackage.isNotEmpty()) {
                                                                        Log.w(
                                                                                BOTTOM_BAR_TAG,
                                                                                "Main display launch double tap: selectedPackage=$selectedPackage, effectivePackage=$effectiveSelectedPackage"
                                                                        )
                                                                        br.com.redesurftank
                                                                                .havalshisuku.models
                                                                                .BottomBarState
                                                                                .isMenuExpanded =
                                                                                false
                                                                        scope.launch {
                                                                                br.com.redesurftank
                                                                                        .havalshisuku
                                                                                        .managers
                                                                                        .DisplayAppLauncher
                                                                                        .getOrCreateDefaultConfig(
                                                                                                context,
                                                                                                effectiveSelectedPackage
                                                                                        )
                                                                                br.com.redesurftank
                                                                                        .havalshisuku
                                                                                        .managers
                                                                                        .DisplayAppLauncher
                                                                                        .launchAnyApp(
                                                                                                context,
                                                                                                effectiveSelectedPackage
                                                                                        )
                                                                        }
                                                                }
                                                        }
                                                )
                                        },
                        contentAlignment = Alignment.Center
                ) {
                        if (selectedConfig?.substituteIcon == "youtube" ||
                                        selectedConfig?.substituteIcon == "youtube_music" ||
                                        selectedConfig?.substituteIcon == "gwm"
                        ) {
                                Image(
                                        painter =
                                                painterResource(
                                                        id =
                                                                when (selectedConfig.substituteIcon
                                                                ) {
                                                                        "youtube" ->
                                                                                R.drawable
                                                                                        .ic_youtube_default
                                                                        "youtube_music" ->
                                                                                R.drawable
                                                                                        .ic_youtube_music_default
                                                                        "gwm" -> R.drawable.ic_gwm
                                                                        else ->
                                                                                R.drawable
                                                                                        .ic_youtube_default
                                                                }
                                                ),
                                        contentDescription = "App Icon",
                                        modifier = Modifier.size(32.dp)
                                )
                        } else if (substituteIconVector != null) {
                                val iconTint = selectedConfig?.iconColor.toComposeColor()
                                Icon(
                                        substituteIconVector,
                                        contentDescription = "App Icon",
                                        tint = iconTint,
                                        modifier = Modifier.size(32.dp)
                                )
                        } else if (effectiveSelectedPackage.isNotEmpty()) {
                                val appInfo =
                                        remember(effectiveSelectedPackage, selectedConfig?.customName, configsVersion) {
                                                br.com.redesurftank.havalshisuku.managers
                                                        .DisplayAppLauncher.resolveAppInfo(
                                                        context,
                                                        effectiveSelectedPackage,
                                                        selectedConfig?.customName
                                                )
                                        }

                                AsyncImage(
                                        model =
                                                ImageRequest.Builder(context)
                                                        .data(appInfo.icon)
                                                        .build(),
                                        contentDescription = "App Icon",
                                        modifier = Modifier.size(40.dp)
                                )
                        } else {
                                Icon(
                                        Icons.Default.Layers,
                                        contentDescription = "Apps",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                )
                        }
                }

                val rightNavInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val rightNavPressed by rightNavInteraction.collectIsPressedAsState()
                val rightNavColor by animateColorAsState(
                        targetValue = if (rightNavPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                        animationSpec = tween(durationMillis = if (rightNavPressed) 50 else 300)
                )
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .width(70.dp)
                                .fillMaxHeight()
                                .clickable(
                                        interactionSource = rightNavInteraction,
                                        indication = null
                                ) {
                                        Log.w(
                                                BOTTOM_BAR_TAG,
                                                "Main display restore click: selectedPackage=$selectedPackage, effectivePackage=$effectiveSelectedPackage"
                                        )
                                        scope.launch {
                                                // bringAllToMainDisplay() returns the list of packages it actually
                                                // moved back from secondary displays. If non-empty, those apps
                                                // deserve to keep focus on Display 0 — launching selectedPackage
                                                // on top would immediately steal it (the user just brought their
                                                // active app back, they don't want it bounced). Only fall through
                                                // to launching selectedPackage when nothing was moved.
                                                val movedBack =
                                                        br.com.redesurftank.havalshisuku.managers
                                                                .DisplayAppLauncher
                                                                .bringAllToMainDisplay()
                                                Log.w(
                                                        BOTTOM_BAR_TAG,
                                                        "Main display restore result: movedBack=${movedBack.joinToString(",")}"
                                                )
                                                if (movedBack.isEmpty() && effectiveSelectedPackage.isNotEmpty()) {
                                                        br.com.redesurftank.havalshisuku.managers
                                                                .DisplayAppLauncher
                                                                .getOrCreateDefaultConfig(
                                                                        context,
                                                                        effectiveSelectedPackage
                                                                )
                                                        br.com.redesurftank.havalshisuku.managers
                                                                .DisplayAppLauncher.launchAnyApp(
                                                                context,
                                                                effectiveSelectedPackage
                                                        )
                                                }
                                        }
                                }
                ) {
                        Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(rightNavColor)
                        ) {
                                Icon(
                                        Icons.Default.KeyboardArrowRight,
                                        contentDescription = "MMI",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }
        }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AllAppsGridItem(
        app: SystemAppItem,
        context: Context,
        scope: CoroutineScope,
        onClick: () -> Unit
) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val bgColor by animateColorAsState(
                targetValue = if (isPressed) Color(0xFF2196F3).copy(alpha = 0.25f) else Color(0x08FFFFFF),
                animationSpec = tween(50),
                label = "appGridBg"
        )

        Column(
                modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick,
                                onLongClick = {
                                        scope.launch {
                                                DisplayAppLauncher.getOrCreateDefaultConfig(context, app.packageName)
                                                DisplayAppLauncher.configsVersion++
                                        }
                                        BottomBarState.isDockEditMode = true
                                        BottomBarState.isMenuExpanded = false
                                }
                        )
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Box(
                        modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                ) {
                        if (app.icon != null) {
                                AsyncImage(
                                        model = ImageRequest.Builder(context).data(app.icon).build(),
                                        contentDescription = app.label,
                                        modifier = Modifier.size(36.dp)
                                )
                        } else {
                                Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = app.label,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp)
                                )
                        }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                        text = app.label,
                        color = Color(0xFFE0E4EC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                )
        }
}

@Composable
fun AppMenuContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val pm = context.packageManager

        val systemApps = remember(BottomBarState.isProjectionShortcutEnabled) {
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = try {
                        pm.queryIntentActivities(intent, 0)
                } catch (_: Exception) {
                        emptyList()
                }
                val seen = mutableSetOf<String>()
                val list = mutableListOf<SystemAppItem>()
                for (info in resolveInfos) {
                        val ai = info.activityInfo ?: continue
                        val pkg = ai.packageName ?: continue
                        if (pkg.isBlank() || shouldHideFromAllApps(pkg) || !seen.add(pkg)) continue
                        if (BottomBarState.isProjectionShortcutEnabled && br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isProjectionLikePackage(pkg)) continue

                        val label = try {
                                info.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
                        } catch (_: Exception) {
                                pkg
                        }
                        val icon = try {
                                info.loadIcon(pm)
                        } catch (_: Exception) {
                                null
                        }
                        list.add(SystemAppItem(packageName = pkg, activityName = ai.name, label = label, icon = icon))
                }
                list.sortBy { it.label.lowercase(Locale.getDefault()) }
                list
        }

        Box(
                modifier = Modifier
                        .background(
                                Color(0xF211141A),
                                RoundedCornerShape(16.dp)
                        )
                        .border(1.dp, Color(0xFF262C38), RoundedCornerShape(16.dp))
                        .width(580.dp)
                        .heightIn(max = 440.dp)
                        .padding(16.dp)
        ) {
                Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                        // Header
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Apps,
                                                contentDescription = null,
                                                tint = Color(0xFF2196F3),
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                                text = "Aplicativos",
                                                style = TextStyle(
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontFamily = Michroma,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        )
                                        Box(
                                                modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF2196F3).copy(alpha = 0.15f))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                                Text(
                                                        text = "${systemApps.size}",
                                                        color = Color(0xFF64B5F6),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                )
                                        }
                                }

                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        // Organizar / Modo de Edição button
                                        Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                        .height(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF2196F3).copy(alpha = 0.15f))
                                                        .border(1.dp, Color(0xFF2196F3).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                        .clickable {
                                                                BottomBarState.isMenuExpanded = false
                                                                BottomBarState.isDockEditMode = true
                                                        }
                                                        .padding(horizontal = 10.dp)
                                        ) {
                                                Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Tune,
                                                                contentDescription = "Organizar",
                                                                tint = Color(0xFF64B5F6),
                                                                modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                                text = "Organizar",
                                                                style = TextStyle(
                                                                        color = Color(0xFF64B5F6),
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.SemiBold
                                                                )
                                                        )
                                                }
                                        }

                                        // Close Button
                                        val closeInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                        val closePressed by closeInteraction.collectIsPressedAsState()
                                        val closeGlow by animateColorAsState(
                                                targetValue = if (closePressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                                                animationSpec = tween(50),
                                                label = "closeGlow"
                                        )
                                        Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(closeGlow)
                                                        .clickable(
                                                                interactionSource = closeInteraction,
                                                                indication = null
                                                        ) {
                                                                BottomBarState.isMenuExpanded = false
                                                        }
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Fechar",
                                                        tint = Color.White.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(20.dp)
                                                )
                                        }
                                }
                        }

                        HorizontalDivider(color = Color(0xFF1F2530), thickness = 1.dp)

                        // Grid of apps
                        if (systemApps.isEmpty()) {
                                Box(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = "Nenhum aplicativo encontrado",
                                                color = Color(0xFF8E95A2),
                                                fontSize = 13.sp
                                        )
                                }
                        } else {
                                LazyVerticalGrid(
                                        columns = GridCells.Fixed(4),
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 350.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                        items(systemApps, key = { it.packageName }) { app ->
                                                AllAppsGridItem(
                                                        app = app,
                                                        context = context,
                                                        scope = scope,
                                                        onClick = {
                                                                RecentAppsManager.recordAppLaunch(app.packageName)
                                                                scope.launch {
                                                                        DisplayAppLauncher.launchAnyApp(context, app.packageName)
                                                                }
                                                                BottomBarState.isMenuExpanded = false
                                                        }
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun AddAppGridItem(
        pkg: String,
        context: Context,
        scope: CoroutineScope,
        onAdded: () -> Unit
) {
        val appInfo =
                remember(pkg) {
                        DisplayAppLauncher.resolveAppInfo(
                                context,
                                pkg
                        )
                }

        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable {
                                        scope.launch {
                                                DisplayAppLauncher.getOrCreateDefaultConfig(context, pkg)
                                                onAdded()
                                        }
                                }
                                .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Box(
                        modifier =
                                Modifier.size(64.dp)
                                        .background(
                                                Color.White.copy(alpha = 0.05f),
                                                RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(12.dp)
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                )
                                if (appInfo.icon != null) {
                                        AsyncImage(
                                                model = appInfo.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp).alpha(0.4f)
                                        )
                                }
                        }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text = "Adicionar atual",
                        style = labelStyle.copy(fontSize = 11.sp),
                        maxLines = 1
                )
        }
}

@Composable
fun DockAddAppPicker(
        targetSlotIndex: Int,
        onDismiss: () -> Unit
) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val pm = context.packageManager

        val existingConfigs = remember(DisplayAppLauncher.configsVersion) {
                DisplayAppLauncher.getAllConfigs()
        }
        val pinnedPkgs = remember(existingConfigs) {
                existingConfigs.map { it.packageName }.toSet()
        }

        val availableApps = remember(pinnedPkgs, BottomBarState.isProjectionShortcutEnabled) {
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = try {
                        pm.queryIntentActivities(intent, 0)
                } catch (_: Exception) {
                        emptyList()
                }
                val seen = mutableSetOf<String>()
                val list = mutableListOf<SystemAppItem>()
                for (info in resolveInfos) {
                        val ai = info.activityInfo ?: continue
                        val pkg = ai.packageName ?: continue
                        if (pkg.isBlank() || shouldHideFromAllApps(pkg) || pinnedPkgs.contains(pkg) || !seen.add(pkg)) continue
                        if (BottomBarState.isProjectionShortcutEnabled && br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isProjectionLikePackage(pkg)) continue

                        val label = try {
                                info.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
                        } catch (_: Exception) {
                                pkg
                        }
                        val icon = try {
                                info.loadIcon(pm)
                        } catch (_: Exception) {
                                null
                        }
                        list.add(SystemAppItem(packageName = pkg, activityName = ai.name, label = label, icon = icon))
                }
                list.sortBy { it.label.lowercase(Locale.getDefault()) }
                list
        }

        Box(
                modifier = Modifier
                        .background(
                                Color(0xF211141A),
                                RoundedCornerShape(16.dp)
                        )
                        .border(1.dp, Color(0xFF262C38), RoundedCornerShape(16.dp))
                        .width(540.dp)
                        .heightIn(max = 380.dp)
                        .padding(16.dp)
        ) {
                Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        // Header
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color(0xFF2196F3),
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                                text = "Adicionar ao Dock",
                                                style = TextStyle(
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontFamily = Michroma,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        )
                                }

                                // Close Button
                                Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onDismiss() }
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Fechar",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                        )
                                }
                        }

                        HorizontalDivider(color = Color(0xFF1F2530), thickness = 1.dp)

                        if (availableApps.isEmpty()) {
                                Box(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = "Todos os aplicativos disponíveis já estão na barra.",
                                                color = Color(0xFF8E95A2),
                                                fontSize = 12.sp
                                        )
                                }
                        } else {
                                LazyVerticalGrid(
                                        columns = GridCells.Fixed(4),
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 280.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        items(availableApps, key = { it.packageName }) { app ->
                                                AllAppsGridItem(
                                                        app = app,
                                                        context = context,
                                                        scope = scope,
                                                        onClick = {
                                                                scope.launch {
                                                                        val newConfig = DisplayAppLauncher.getOrCreateDefaultConfig(context, app.packageName, save = false)
                                                                        if (newConfig != null) {
                                                                                val configs = DisplayAppLauncher.getAllConfigs().toMutableList()
                                                                                configs.removeAll { it.packageName == app.packageName }
                                                                                val insertIndex = targetSlotIndex.coerceIn(0, configs.size)
                                                                                configs.add(insertIndex, newConfig)
                                                                                DisplayAppLauncher.saveAllConfigs(configs)
                                                                        }
                                                                }
                                                                onDismiss()
                                                        }
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun ClassicAppMenuContent() {
        val configsVersion = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.configsVersion
        val configsList = remember {
                mutableStateListOf<DisplayAppConfig>()
                        .apply { addAll(getBottomBarAppConfigs()) }
        }
        LaunchedEffect(configsVersion) {
                val latestConfigs = getBottomBarAppConfigs()
                if (configsList.toList() != latestConfigs) {
                        configsList.clear()
                        configsList.addAll(latestConfigs)
                }
        }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val currentPkg = BottomBarState.currentPackage
        val isCurrentInConfigs = configsList.any { it.packageName == currentPkg }
        val showAddButton =
                !isCurrentInConfigs && currentPkg.isNotEmpty() && currentPkg != context.packageName

        // Track item positions for drag and drop
        val itemBounds = remember { mutableMapOf<Int, Rect>() }
        var draggedIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

        fun findIndexAtOffset(offset: Offset): Int? {
                val rootOffset = containerCoordinates?.localToRoot(offset) ?: offset
                return itemBounds.entries.find { it.value.contains(rootOffset) }?.key
        }

        Box(
                modifier =
                        Modifier.background(
                                        Color(0xFF13151A).copy(alpha = 0.95f),
                                        RoundedCornerShape(12.dp)
                                )
                                .border(1.dp, Color(0xFF1D2430), RoundedCornerShape(12.dp))
                                .fillMaxWidth(0.25f)
                                .padding(16.dp)
                                .pointerInput(Unit) {
                                        detectTapGestures {
                                                BottomBarState.isDeleteModeEnabled = false
                                        }
                                }
        ) {
                val totalItems = configsList.size + (if (showAddButton) 1 else 0)
                val columns = 3
                val rows = (totalItems + columns - 1) / columns

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Header
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        Text(
                                                text =
                                                        if (BottomBarState.isDeleteModeEnabled)
                                                                "Organizar"
                                                        else "Aplicativos",
                                                color =
                                                        if (BottomBarState.isDeleteModeEnabled)
                                                                Color(0xFF4CAF50)
                                                        else Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                        if (BottomBarState.isDeleteModeEnabled) {
                                                Box(
                                                        modifier =
                                                                Modifier.clip(
                                                                                RoundedCornerShape(
                                                                                        16.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                Color(0xFF4CAF50)
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.15f
                                                                                        )
                                                                        )
                                                                        .border(
                                                                                1.dp,
                                                                                Color(0xFF4CAF50)
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.5f
                                                                                        ),
                                                                                RoundedCornerShape(
                                                                                        16.dp
                                                                                )
                                                                        )
                                                                        .clickable {
                                                                                BottomBarState
                                                                                        .isDeleteModeEnabled =
                                                                                        false
                                                                        }
                                                                        .padding(
                                                                                horizontal = 12.dp,
                                                                                vertical = 6.dp
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(6.dp)
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default.Check,
                                                                        contentDescription = null,
                                                                        tint = Color(0xFF4CAF50),
                                                                        modifier =
                                                                                Modifier.size(14.dp)
                                                                )
                                                                Text(
                                                                        text = "CONCLUIR",
                                                                        color = Color(0xFF4CAF50),
                                                                        fontSize = 11.sp,
                                                                        fontWeight =
                                                                                FontWeight
                                                                                        .ExtraBold,
                                                                        letterSpacing = 0.5.sp
                                                                )
                                                        }
                                                }
                                        }
                                }
                                val closeInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val closePressed by closeInteraction.collectIsPressedAsState()
                                val closeGlow by animateColorAsState(
                                        targetValue = if (closePressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                                        animationSpec = tween(durationMillis = if (closePressed) 50 else 300)
                                )
                                Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(closeGlow)
                                                .clickable(
                                                        interactionSource = closeInteraction,
                                                        indication = null
                                                ) {
                                                        BottomBarState.isMenuExpanded = false
                                                        BottomBarState.isDeleteModeEnabled = false
                                                }
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Fechar",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                        }

                        // App Grid
                        Column(
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                modifier =
                                        Modifier.onGloballyPositioned { containerCoordinates = it }
                                                .pointerInput(BottomBarState.isDeleteModeEnabled) {
                                                        if (BottomBarState.isDeleteModeEnabled) {
                                                                detectDragGestures(
                                                                        onDragStart = { offset ->
                                                                                val index =
                                                                                        findIndexAtOffset(
                                                                                                offset
                                                                                        )
                                                                                if (index != null &&
                                                                                                index <
                                                                                                        configsList
                                                                                                                .size
                                                                                ) {
                                                                                        draggedIndex =
                                                                                                index
                                                                                        dragOffset =
                                                                                                Offset.Zero
                                                                                        Log.d(
                                                                                                "ClassicAppMenuContent",
                                                                                                "Drag started via container for index $index"
                                                                                        )
                                                                                }
                                                                        },
                                                                        onDrag = {
                                                                                change,
                                                                                dragAmount ->
                                                                                if (draggedIndex !=
                                                                                                null
                                                                                ) {
                                                                                        try {
                                                                                                change.consume()
                                                                                        } catch (_: Exception) {}

                                                                                        dragOffset +=
                                                                                                dragAmount

                                                                                        val currentItemBounds =
                                                                                                itemBounds[
                                                                                                        draggedIndex!!]
                                                                                        if (currentItemBounds !=
                                                                                                        null
                                                                                        ) {
                                                                                                val currentPos =
                                                                                                        currentItemBounds
                                                                                                                .center +
                                                                                                                dragOffset
                                                                                                itemBounds
                                                                                                        .entries
                                                                                                        .forEach {
                                                                                                                entry
                                                                                                                ->
                                                                                                                val targetIndex =
                                                                                                                        entry.key
                                                                                                                val bounds =
                                                                                                                        entry.value

                                                                                                                val hitZone =
                                                                                                                        Rect(
                                                                                                                                left =
                                                                                                                                        bounds.left +
                                                                                                                                                bounds.width *
                                                                                                                                                        0.2f,
                                                                                                                                top =
                                                                                                                                        bounds.top +
                                                                                                                                                bounds.height *
                                                                                                                                                        0.2f,
                                                                                                                                right =
                                                                                                                                        bounds.right -
                                                                                                                                                bounds.width *
                                                                                                                                                        0.2f,
                                                                                                                                bottom =
                                                                                                                                        bounds.bottom -
                                                                                                                                                bounds.height *
                                                                                                                                                        0.2f
                                                                                                                        )

                                                                                                                if (targetIndex !=
                                                                                                                                draggedIndex &&
                                                                                                                                targetIndex <
                                                                                                                                        configsList
                                                                                                                                                .size &&
                                                                                                                                hitZone.contains(
                                                                                                                                        currentPos
                                                                                                                                )
                                                                                                                ) {
                                                                                                                        val temp =
                                                                                                                                configsList[
                                                                                                                                        draggedIndex!!]
                                                                                                                        configsList[
                                                                                                                                draggedIndex!!] =
                                                                                                                                configsList[
                                                                                                                                        targetIndex]
                                                                                                                        configsList[
                                                                                                                                targetIndex] =
                                                                                                                                temp

                                                                                                                        draggedIndex =
                                                                                                                                targetIndex
                                                                                                                        dragOffset =
                                                                                                                                Offset.Zero
                                                                                                                        DisplayAppLauncher
                                                                                                                                .saveAllConfigs(
                                                                                                                                        configsList
                                                                                                                                                .toList()
                                                                                                                                )
                                                                                                                }
                                                                                                        }
                                                                                        }
                                                                                }
                                                                        },
                                                                        onDragEnd = {
                                                                                draggedIndex = null
                                                                                dragOffset =
                                                                                        Offset.Zero
                                                                        },
                                                                        onDragCancel = {
                                                                                draggedIndex = null
                                                                                dragOffset =
                                                                                        Offset.Zero
                                                                        }
                                                                )
                                                        }
                                                }
                        ) {
                                for (r in (rows - 1) downTo 0) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                                for (c in 0 until columns) {
                                                        val index = r * columns + c
                                                        if (index < totalItems) {
                                                                if (showAddButton &&
                                                                                index ==
                                                                                        totalItems -
                                                                                                1
                                                                ) {
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        ),
                                                                                contentAlignment =
                                                                                        Alignment
                                                                                                .Center
                                                                        ) {
                                                                                AddAppGridItem(
                                                                                        currentPkg,
                                                                                        context,
                                                                                        scope
                                                                                ) {
                                                                                        val newConfigs =
                                                                                                getBottomBarAppConfigs()
                                                                                        configsList
                                                                                                .clear()
                                                                                        configsList
                                                                                                .addAll(
                                                                                                        newConfigs
                                                                                                )
                                                                                }
                                                                        }
                                                                } else {
                                                                        val config =
                                                                                configsList[index]
                                                                        key(config.packageName) {
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.weight(
                                                                                                                1f
                                                                                                        )
                                                                                                        .onGloballyPositioned {
                                                                                                                layoutCoordinates
                                                                                                                ->
                                                                                                                itemBounds[
                                                                                                                        index] =
                                                                                                                        layoutCoordinates
                                                                                                                                .boundsInRoot()
                                                                                                        },
                                                                                        contentAlignment =
                                                                                                Alignment
                                                                                                        .Center
                                                                                ) {
                                                                                        AppGridItem(
                                                                                                pkg =
                                                                                                        config.packageName,
                                                                                                substituteIcon =
                                                                                                        config.substituteIcon,
                                                                                                context =
                                                                                                        context,
                                                                                                scope = scope,
                                                                                                onDelete = {
                                                                                                        configsList
                                                                                                                .removeAt(
                                                                                                                        index
                                                                                                                )
                                                                                                        DisplayAppLauncher
                                                                                                                .saveAllConfigs(
                                                                                                                        configsList
                                                                                                                                .toList()
                                                                                                                )
                                                                                                },
                                                                                                onDragStart = {
                                                                                                },
                                                                                                onDrag = {
                                                                                                        _
                                                                                                        ->
                                                                                                },
                                                                                                onDragEnd = {
                                                                                                },
                                                                                                isDragged =
                                                                                                        draggedIndex ==
                                                                                                                index,
                                                                                                dragOffset =
                                                                                                        if (draggedIndex ==
                                                                                                                        index
                                                                                                        )
                                                                                                                dragOffset
                                                                                                        else
                                                                                                                Offset
                                                                                                                        .Zero,
                                                                                                onClick = {
                                                                                                }
                                                                                        )
                                                                                }
                                                                        }
                                                                }
                                                        } else {
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
}

@Composable
fun CarSettingsSection() {
        val showSettings = BottomBarState.isSettingsMenuExpanded
        Box(
                modifier =
                        Modifier.fillMaxHeight().width(40.dp).clickable {
                                BottomBarState.isSettingsMenuExpanded = !showSettings
                        },
                contentAlignment = Alignment.Center
        ) {
                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Configurações do Carro",
                                tint = if (showSettings) Color(0xFF2196F3) else Color.White,
                                modifier = Modifier.size(32.dp)
                        )
                        // Smaller gear icon overlay
                        Box(
                                modifier =
                                        Modifier.size(16.dp)
                                                .offset(x = 10.dp, y = 10.dp)
                                                .background(Color.Black, RoundedCornerShape(4.dp))
                                                .padding(1.dp),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = if (showSettings) Color(0xFF2196F3) else Color.White,
                                        modifier = Modifier.size(14.dp)
                                )
                        }
                }
        }
}

@Composable
fun SettingsMenuContent(drive: String, ev: String, regen: String, steer: String) {
        val serviceManager = br.com.redesurftank.havalshisuku.managers.ServiceManager.getInstance()
        Box(
                modifier =
                        Modifier.background(
                                        Color(0xFF11141A).copy(alpha = 0.98f),
                                        RoundedCornerShape(14.dp)
                                )
                                .border(1.dp, Color(0xFF1F2530), RoundedCornerShape(14.dp))
                                .width(480.dp)
                                .padding(16.dp)
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        // Category: Drive Mode
                        SettingsCategoryRow(
                                "Modo de Condução",
                                drive,
                                listOf(
                                        "2" to "Eco",
                                        "0" to "Normal",
                                        "1" to "Sport",
                                        "3" to "Neve",
                                        "4" to "Areia",
                                        "5" to "Lama"
                                ),
                                columns = 3
                        ) { newVal ->
                                serviceManager.updateData(
                                        CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue(),
                                        newVal
                                )
                        }

                        // Category: EV Mode
                        SettingsCategoryRow(
                                "Modo EV",
                                ev,
                                listOf("0" to "HEV", "1" to "EV Prioritário", "3" to "EV")
                        ) { newVal ->
                                serviceManager.updateData(
                                        CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue(),
                                        newVal
                                )
                        }

                        // Category: Regen
                        SettingsCategoryRow(
                                "Modo de Regeneração",
                                regen,
                                listOf("2" to "Baixo", "0" to "Normal", "1" to "Alto")
                        ) { newVal ->
                                serviceManager.updateData(
                                        CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL
                                                .getValue(),
                                        newVal
                                )
                        }

                        // Category: Steering
                        SettingsCategoryRow(
                                "Modo de Direção",
                                steer,
                                listOf("2" to "Conforto", "0" to "Normal", "1" to "Esportiva")
                        ) { newVal ->
                                serviceManager.updateData(
                                        CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE
                                                .getValue(),
                                        newVal
                                )
                        }

                        Box(
                                modifier =
                                        Modifier.fillMaxWidth().height(24.dp).clickable {
                                                br.com.redesurftank.havalshisuku.models
                                                        .BottomBarState.isSettingsMenuExpanded =
                                                        false
                                        },
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Fechar",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(32.dp)
                                )
                        }
                }
        }
}

@Composable
fun BottomBarMenus() {
        val serviceManager = br.com.redesurftank.havalshisuku.managers.ServiceManager.getInstance()
        var appMenuBounds by remember { mutableStateOf<Rect?>(null) }
        var secondaryMenuBounds by remember { mutableStateOf<Rect?>(null) }
        var acMenuBounds by remember { mutableStateOf<Rect?>(null) }
        var dockAddPickerBounds by remember { mutableStateOf<Rect?>(null) }

        var driveMode by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue())
                                ?: "0"
                )
        }
        var powerModel by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue()
                        )
                                ?: "0"
                )
        }
        var energyRecovery by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue()
                        )
                                ?: "0"
                )
        }
        var steeringMode by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue()
                        )
                                ?: "0"
                )
        }

        // Update states when data changes
        DisposableEffect(Unit) {
                val listener =
                        object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                                override fun onDataChanged(key: String, value: String?) {
                                        if (value == null) return
                                        when (key) {
                                                CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG
                                                        .getValue() -> powerModel = value
                                                CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL
                                                        .getValue() -> energyRecovery = value
                                                CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE
                                                        .getValue() -> driveMode = value
                                                CarConstants
                                                        .CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE
                                                        .getValue() -> steeringMode = value
                                        }
                                }
                        }
                serviceManager.addDataChangedListener(listener)
                onDispose { serviceManager.removeDataChangedListener(listener) }
        }

        val dashboardExpanded = BottomBarState.isDashboardExpanded

        val isModern = BottomBarState.barVersion == BottomBarState.BarVersion.NEW.key
        val isFloatingProjOnly =
                isModern &&
                        BottomBarState.isDockEditMode &&
                        !BottomBarState.isProjectionShortcutEnabled

        // The window hosting these menus is resized from 0x0 to full screen when one opens, and it is
        // anchored to the bottom, so the scrim and the menu would otherwise appear to sweep up from the
        // bottom edge as the window grows. Fade in instead. (Closing stays instant - the window is
        // collapsed straight away, so there is nothing left to animate out.)
        val anyExpanded =
                dashboardExpanded ||
                        BottomBarState.isMenuExpanded ||
                        BottomBarState.isSettingsMenuExpanded ||
                        BottomBarState.isOverrideMenuExpanded ||
                        (isModern && BottomBarState.isAcMenuExpanded) ||
                        (isModern && BottomBarState.dockAddSlotIndex != null) ||
                        BottomBarState.activeSliderType != null ||
                        (isModern && BottomBarState.activeSwipeHud != null) ||
                        isFloatingProjOnly
        val contentAlpha by
                animateFloatAsState(
                        targetValue = if (anyExpanded) 1f else 0f,
                        animationSpec = tween(durationMillis = 120),
                        label = "menuFade"
                )

        val isInteractiveMenuOpen =
                dashboardExpanded ||
                        BottomBarState.isMenuExpanded ||
                        BottomBarState.isSettingsMenuExpanded ||
                        BottomBarState.isOverrideMenuExpanded ||
                        (isModern && BottomBarState.isAcMenuExpanded) ||
                        (isModern && BottomBarState.dockAddSlotIndex != null)

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .alpha(contentAlpha)
                                .background(
                                        if (dashboardExpanded) Color(0xFF05070A)
                                        else if (isInteractiveMenuOpen) Color.Black.copy(alpha = 0.4f)
                                        else Color.Transparent
                                )
                                .pointerInput(appMenuBounds, secondaryMenuBounds, acMenuBounds, dockAddPickerBounds, dashboardExpanded, isInteractiveMenuOpen, isModern) {
                                        if (!dashboardExpanded && isInteractiveMenuOpen) {
                                                detectTapGestures { offset ->
                                                        val insideAppMenu =
                                                                BottomBarState.isMenuExpanded &&
                                                                        appMenuBounds?.contains(
                                                                                offset
                                                                        ) == true
                                                        val insideSecondaryMenu =
                                                                (BottomBarState
                                                                                .isSettingsMenuExpanded ||
                                                                                BottomBarState
                                                                                        .isOverrideMenuExpanded) &&
                                                                        secondaryMenuBounds
                                                                                ?.contains(offset) ==
                                                                                true
                                                        val insideAcMenu =
                                                                isModern && BottomBarState.isAcMenuExpanded &&
                                                                        acMenuBounds?.contains(offset) == true
                                                        val insideDockPicker =
                                                                isModern && BottomBarState.dockAddSlotIndex != null &&
                                                                        dockAddPickerBounds?.contains(offset) == true
                                                        if (!insideAppMenu && !insideSecondaryMenu && !insideAcMenu && !insideDockPicker) {
                                                                BottomBarState.isMenuExpanded = false
                                                                BottomBarState.isSettingsMenuExpanded =
                                                                        false
                                                                BottomBarState.isOverrideMenuExpanded =
                                                                        false
                                                                BottomBarState.isAcMenuExpanded = false
                                                                BottomBarState.dockAddSlotIndex = null
                                                                BottomBarState.isDeleteModeEnabled =
                                                                        false
                                                                BottomBarState.activeSliderType =
                                                                        null
                                                        }
                                                }
                                        }
                                },
                contentAlignment = Alignment.BottomCenter
        ) {
                if (dashboardExpanded) {
                        ExpandedImpulseDashboard()
                } else {
                        // We use a Box with fillMaxWidth to contain our menus at the bottom
                        Box(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 60.dp),
                        ) {
                                // Custom Vertical Slider Overlay. Deliberately outside the gutter
                                // inset below: it positions itself from an absolute root coordinate
                                // captured in the bar window, so an extra inset would double-count.
                                if (BottomBarState.activeSliderType != null) {
                                        VerticalSliderOverlay()
                                }

                                // Floating Horizontal Swipe HUD Overlay (strictly Modern v2)
                                if (isModern && BottomBarState.activeSwipeHud != null) {
                                        FloatingSwipeHudOverlay(hud = BottomBarState.activeSwipeHud!!)
                                }

                                // The window spans the physical display; inset the menus past the
                                // left navigation pane, which draws above us. This also makes the
                                // menus measure against the app width, so their proportional widths
                                // are unchanged by the pin.
                                val leftGutter =
                                        with(LocalDensity.current) {
                                                BottomBarState.overlayLeftGutterPx.toDp()
                                         }

                                Box(modifier = Modifier.fillMaxWidth().padding(start = leftGutter)) {
                                        // App Menu (Positioned on the left for both Modern and Classic)
                                        if (br.com.redesurftank.havalshisuku.models.BottomBarState.isMenuExpanded) {
                                                Box(
                                                        modifier =
                                                                Modifier.padding(start = 16.dp, bottom = 10.dp)
                                                                        .align(Alignment.BottomStart)
                                                                        .onGloballyPositioned {
                                                                                appMenuBounds =
                                                                                        it.boundsInRoot()
                                                                        }
                                                ) {
                                                        if (BottomBarState.barVersion == BottomBarState.BarVersion.NEW.key) {
                                                                AppMenuContent()
                                                        } else {
                                                                ClassicAppMenuContent()
                                                        }
                                                }
                                        }

                                        // AC Quick Menu (Above Fan Button) - strictly Modern v2
                                        if (isModern && BottomBarState.isAcMenuExpanded) {
                                                val acMenuWidthDp = 420.dp
                                                val density = LocalDensity.current
                                                val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
                                                val acStartPad = if (BottomBarState.fanButtonCenterX > 0f) {
                                                        val fanCenterDp = with(density) { BottomBarState.fanButtonCenterX.toDp() }
                                                        (fanCenterDp - acMenuWidthDp / 2).coerceIn(16.dp, (screenWidthDp - acMenuWidthDp - 16.dp).coerceAtLeast(16.dp))
                                                } else {
                                                        450.dp
                                                }
                                                Box(
                                                        modifier =
                                                                Modifier.padding(start = acStartPad, bottom = 10.dp)
                                                                        .align(Alignment.BottomStart)
                                                                        .onGloballyPositioned {
                                                                                acMenuBounds =
                                                                                        it.boundsInRoot()
                                                                        }
                                                ) { AcQuickMenuContent() }
                                        }

                                        // Car Settings Menu (Moved to the left)
                                        if (BottomBarState.isSettingsMenuExpanded) {
                                                Box(
                                                        modifier =
                                                                Modifier.padding(start = 16.dp, bottom = 10.dp)
                                                                        .align(Alignment.BottomStart)
                                                                        .onGloballyPositioned {
                                                                                secondaryMenuBounds =
                                                                                        it.boundsInRoot()
                                                                        }
                                                ) {
                                                        SettingsMenuContent(
                                                                driveMode,
                                                                powerModel,
                                                                energyRecovery,
                                                                steeringMode
                                                        )
                                                }
                                        }

                                        // Override Menu (Bottom End / Right)
                                        if (BottomBarState.isOverrideMenuExpanded) {
                                                Box(
                                                        modifier =
                                                                Modifier.align(Alignment.BottomEnd)
                                                                        .padding(end = 16.dp, bottom = 10.dp)
                                                                        .onGloballyPositioned {
                                                                                secondaryMenuBounds =
                                                                                        it.boundsInRoot()
                                                                        }
                                                ) {
                                                        OverrideMenuContent()
                                                }
                                        }

                                        // Mini Dock App Picker (when an empty [+] slot is clicked in edit mode) - strictly Modern v2
                                        if (isModern && BottomBarState.dockAddSlotIndex != null) {
                                                Box(
                                                        modifier =
                                                                Modifier.padding(bottom = 10.dp)
                                                                        .align(Alignment.BottomCenter)
                                                                        .onGloballyPositioned {
                                                                                dockAddPickerBounds =
                                                                                        it.boundsInRoot()
                                                                        }
                                                ) {
                                                        DockAddAppPicker(
                                                                targetSlotIndex = BottomBarState.dockAddSlotIndex ?: 0,
                                                                onDismiss = {
                                                                        BottomBarState.dockAddSlotIndex = null
                                                                }
                                                        )
                                                }
                                        }

                                        // Floating Button "Adicionar link para AA/Carplay" (Above Slot 0 in Edit Mode when disabled)
                                        if (isModern &&
                                                BottomBarState.isDockEditMode &&
                                                !BottomBarState.isProjectionShortcutEnabled &&
                                                BottomBarState.dockAddSlotIndex == null &&
                                                !BottomBarState.isMenuExpanded &&
                                                !BottomBarState.isSettingsMenuExpanded &&
                                                !BottomBarState.isOverrideMenuExpanded
                                        ) {
                                                var buttonWidthPx by remember { mutableStateOf(0f) }
                                                val density = LocalDensity.current
                                                val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
                                                val projCenterDp = with(density) { BottomBarState.projectionSlotCenterX.toDp() }
                                                val buttonWidthDp = if (buttonWidthPx > 0f) with(density) { buttonWidthPx.toDp() } else 260.dp
                                                val projStartPad = if (BottomBarState.projectionSlotCenterX > 0f) {
                                                        (projCenterDp - buttonWidthDp / 2 - leftGutter).coerceIn(16.dp, (screenWidthDp - buttonWidthDp - 16.dp).coerceAtLeast(16.dp))
                                                } else {
                                                        200.dp
                                                }
                                                val context = LocalContext.current
                                                val prefs = remember {
                                                        context.getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
                                                }
                                                Box(
                                                        modifier = Modifier
                                                                .padding(start = projStartPad, bottom = 4.dp)
                                                                .align(Alignment.BottomStart)
                                                                .onGloballyPositioned {
                                                                        buttonWidthPx = it.size.width.toFloat()
                                                                }
                                                ) {
                                                        ProjectionAddFloatingButton(
                                                                onClick = {
                                                                        BottomBarState.isProjectionShortcutEnabled = true
                                                                        prefs.edit()
                                                                                .putBoolean(
                                                                                        SharedPreferencesKeys.BOTTOM_BAR_SHOW_PROJECTION_SHORTCUT.key,
                                                                                        true
                                                                                )
                                                                                .apply()
                                                                }
                                                        )
                                                }
                                        }
                                }
                        }
                }
        }
}

@Composable
fun ProjectionAddFloatingButton(
        onClick: () -> Unit
) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val bgColor by animateColorAsState(
                targetValue = if (isPressed) Color(0xFF1E88E5) else Color(0xF2161A22),
                animationSpec = tween(50),
                label = "projBtnBg"
        )

        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.wrapContentWidth()
        ) {
                Surface(
                        onClick = onClick,
                        interactionSource = interactionSource,
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor,
                        border = BorderStroke(1.2.dp, Color(0xFF2196F3).copy(alpha = 0.8f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier.height(38.dp)
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(16.dp)
                                )
                                Text(
                                        text = "Adicionar link para AA/Carplay",
                                        style = TextStyle(
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontFamily = Michroma,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.3.sp
                                        )
                                )
                        }
                }
                Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFF2196F3).copy(alpha = 0.8f),
                        modifier = Modifier
                                .size(16.dp)
                                .offset(y = (-4).dp)
                )
        }
}

private data class DashboardVehicleSnapshot(
        val speed: String,
        val gear: String,
        val driveMode: String,
        val powerModel: String,
        val powerReserve: String,
        val socTarget: String,
        val energyRecovery: String,
        val steeringMode: String,
        val driverTemp: String,
        val passTemp: String,
        val fanSpeed: String,
        val hvacPower: String,
        val blowerMode: String,
        val acSync: String,
        val acAuto: String,
        val acRecirc: String,
        val driverSeatVentilation: String,
        val passengerSeatVentilation: String,
        val seatVentilationMaxLevel: String,
        val insideTemp: String,
        val outsideTemp: String,
        val batteryPercent: String,
        val fuelPercent: String,
        val batteryRange: String,
        val fuelRange: String,
        val odometer: String,
        val avgFuel: String,
        val avgEnergy: String,
        val batteryVoltage: String,
        val batteryCurrent: String,
        val volume: String,
        val readyState: String
)

@Composable
private fun rememberDashboardVehicleSnapshot(
        serviceManager: ServiceManager
): DashboardVehicleSnapshot {
        var speed by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue())
                                ?: "--"
                )
        }
        var gear by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_BASIC_GEAR_STATUS.getValue()) ?: "--"
                )
        }
        var driveMode by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue())
                                ?: "0"
                )
        }
        var powerModel by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue()
                        )
                                ?: "0"
                )
        }
        var powerReserve by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_SETTING_POWER_RESERVE_CONFIG.getValue()
                        )
                                ?: "1"
                )
        }
        var socTarget by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue()
                        )
                                ?: "50"
                )
        }
        var energyRecovery by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue()
                        )
                                ?: "0"
                )
        }
        var steeringMode by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE
                                        .getValue()
                        )
                                ?: "0"
                )
        }
        var driverTemp by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue())
                                ?: "--"
                )
        }
        var passTemp by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue())
                                ?: "--"
                )
        }
        var fanSpeed by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_FAN_SPEED.getValue()) ?: "0"
                )
        }
        var hvacPower by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_POWER_MODE.getValue()) ?: "1"
                )
        }
        var blowerMode by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_BLOWER_MODE.getValue()) ?: "0"
                )
        }
        var acSync by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue()) ?: "0"
                )
        }
        var acAuto by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue()) ?: "0"
                )
        }
        var acRecirc by remember {
                mutableStateOf(
                        if ((serviceManager.getData(CarConstants.CAR_HVAC_CYCLE_MODE.getValue())
                                                        ?: "0") == "0"
                        )
                                "1"
                        else "0"
                )
        }
        var driverSeatVentilation by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL
                                        .getValue()
                        )
                                ?: "0"
                )
        }
        var passengerSeatVentilation by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL
                                        .getValue()
                        )
                                ?: "0"
                )
        }
        var seatVentilationMaxLevel by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL
                                        .getValue()
                        )
                                ?: "3"
                )
        }
        var insideTemp by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_BASIC_INSIDE_TEMP.getValue())
                                ?: "--"
                )
        }
        var outsideTemp by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.getValue())
                                ?: "--"
                )
        }
        var batteryPercent by remember {
                mutableStateOf(readDashboardBatteryPercent(serviceManager))
        }
        var fuelPercent by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE.getValue()
                        )
                                ?: "--"
                )
        }
        var batteryRange by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER.getValue()
                        )
                                ?: "--"
                )
        }
        var fuelRange by remember {
                mutableStateOf(readDashboardFuelRange(serviceManager))
        }
        var odometer by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_BASIC_TOTAL_ODOMETER.getValue())
                                ?: "--"
                )
        }
        var avgFuel by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_BASIC_CUR_JOURNEY_AVG_FUEL_CONSUME.getValue()
                        )
                                ?: serviceManager.getData(
                                        CarConstants.CAR_BASIC_AVG_FUEL_CONSUMPTION.getValue()
                                )
                                ?: "--"
                )
        }
        var avgEnergy by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_INFO_AVG_ENERGY_CONSUME_INFO_SINCE_STARTUP
                                        .getValue()
                        )
                                ?: "--"
                )
        }
        var batteryVoltage by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.getValue()
                        )
                                ?: "0"
                )
        }
        var batteryCurrent by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.CAR_EV_INFO_POWER_BATTERY_CURRENT.getValue()
                        )
                                ?: "0"
                )
        }
        var volume by remember {
                mutableStateOf(
                        serviceManager.getData(
                                CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue()
                        )
                                ?: "0"
                )
        }
        var readyState by remember {
                mutableStateOf(
                        serviceManager.getData(CarConstants.CAR_BASIC_DRIVING_READY_STATE.getValue())
                                ?: "--"
                )
        }

        DisposableEffect(Unit) {
                val listener =
                        object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                                override fun onDataChanged(key: String, value: String?) {
                                        if (value == null) return
                                        when (key) {
                                                CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue() ->
                                                        speed = value
                                                CarConstants.CAR_BASIC_GEAR_STATUS.getValue() ->
                                                        gear = value
                                                CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE
                                                        .getValue() -> driveMode = value
                                                CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG
                                                        .getValue() -> powerModel = value
                                                CarConstants.CAR_EV_SETTING_POWER_RESERVE_CONFIG
                                                        .getValue() -> powerReserve = value
                                                CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG
                                                        .getValue() -> socTarget = value
                                                CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL
                                                        .getValue() -> energyRecovery = value
                                                CarConstants
                                                        .CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE
                                                        .getValue() -> steeringMode = value
                                                CarConstants.CAR_HVAC_DRIVER_TEMPERATURE
                                                        .getValue() -> driverTemp = value
                                                CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue() ->
                                                        passTemp = value
                                                CarConstants.CAR_HVAC_FAN_SPEED.getValue() ->
                                                        fanSpeed = value
                                                CarConstants.CAR_HVAC_POWER_MODE.getValue() ->
                                                        hvacPower = value
                                                CarConstants.CAR_HVAC_BLOWER_MODE.getValue() ->
                                                        blowerMode = value
                                                CarConstants.CAR_HVAC_SYNC_ENABLE.getValue() ->
                                                        acSync = value
                                                CarConstants.CAR_HVAC_AUTO_ENABLE.getValue() ->
                                                        acAuto = value
                                                CarConstants.CAR_HVAC_CYCLE_MODE.getValue() ->
                                                        acRecirc = if (value == "0") "1" else "0"
                                                CarConstants
                                                        .CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL
                                                        .getValue() ->
                                                        driverSeatVentilation = value
                                                CarConstants
                                                        .CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL
                                                        .getValue() ->
                                                        passengerSeatVentilation = value
                                                CarConstants
                                                        .CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL
                                                        .getValue() ->
                                                        seatVentilationMaxLevel = value
                                                CarConstants.CAR_BASIC_INSIDE_TEMP.getValue() ->
                                                        insideTemp = value
                                                CarConstants.CAR_BASIC_OUTSIDE_TEMP.getValue() ->
                                                        outsideTemp = value
                                                CarConstants
                                                        .CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE
                                                        .getValue(),
                                                CarConstants
                                                        .CAR_EV_INFO_CAR_EV_INFO_SOC_OF_BATTERY
                                                        .getValue(),
                                                CarConstants.CAR_EV_INFO_BATTERY_POWER_PERCENTAGE
                                                        .getValue() -> {
                                                        batteryPercent =
                                                                readDashboardBatteryPercent(
                                                                        serviceManager,
                                                                        key,
                                                                        value
                                                                )
                                                }
                                                CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE
                                                        .getValue() -> fuelPercent = value
                                                CarConstants
                                                        .CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER
                                                        .getValue() -> batteryRange = value
                                                CarConstants
                                                        .CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER
                                                        .getValue(),
                                                CarConstants.CAR_BASIC_REMAIN_ODOMETER.getValue() ->
                                                        fuelRange =
                                                                readDashboardFuelRange(
                                                                        serviceManager,
                                                                        key,
                                                                        value
                                                                )
                                                CarConstants.CAR_BASIC_TOTAL_ODOMETER.getValue() ->
                                                        odometer = value
                                                CarConstants.CAR_BASIC_CUR_JOURNEY_AVG_FUEL_CONSUME
                                                        .getValue() -> avgFuel = value
                                                CarConstants.CAR_BASIC_AVG_FUEL_CONSUMPTION
                                                        .getValue() -> avgFuel = value
                                                CarConstants
                                                        .CAR_EV_INFO_AVG_ENERGY_CONSUME_INFO_SINCE_STARTUP
                                                        .getValue() -> avgEnergy = value
                                                CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE
                                                        .getValue() -> batteryVoltage = value
                                                CarConstants.CAR_EV_INFO_POWER_BATTERY_CURRENT
                                                        .getValue() -> batteryCurrent = value
                                                CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME
                                                        .getValue() -> volume = value
                                                CarConstants.CAR_BASIC_DRIVING_READY_STATE
                                                        .getValue() -> readyState = value
                                        }
                                }
                        }
                serviceManager.addDataChangedListener(listener)
                onDispose { serviceManager.removeDataChangedListener(listener) }
        }

        return DashboardVehicleSnapshot(
                speed = speed,
                gear = gear,
                driveMode = driveMode,
                powerModel = powerModel,
                powerReserve = powerReserve,
                socTarget = socTarget,
                energyRecovery = energyRecovery,
                steeringMode = steeringMode,
                driverTemp = driverTemp,
                passTemp = passTemp,
                fanSpeed = fanSpeed,
                hvacPower = hvacPower,
                blowerMode = blowerMode,
                acSync = acSync,
                acAuto = acAuto,
                acRecirc = acRecirc,
                driverSeatVentilation = driverSeatVentilation,
                passengerSeatVentilation = passengerSeatVentilation,
                seatVentilationMaxLevel = seatVentilationMaxLevel,
                insideTemp = insideTemp,
                outsideTemp = outsideTemp,
                batteryPercent = batteryPercent,
                fuelPercent = fuelPercent,
                batteryRange = batteryRange,
                fuelRange = fuelRange,
                odometer = odometer,
                avgFuel = avgFuel,
                avgEnergy = avgEnergy,
                batteryVoltage = batteryVoltage,
                batteryCurrent = batteryCurrent,
                volume = volume,
                readyState = readyState
        )
}

private fun readDashboardBatteryPercent(
        serviceManager: ServiceManager,
        overrideKey: String? = null,
        overrideValue: String? = null
): String {
        val currentKey = CarConstants.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE.getValue()
        val socKey = CarConstants.CAR_EV_INFO_CAR_EV_INFO_SOC_OF_BATTERY.getValue()
        val chargeKey = CarConstants.CAR_EV_INFO_BATTERY_POWER_PERCENTAGE.getValue()
        val values =
                listOf(
                        valueForDashboardBatteryKey(serviceManager, currentKey, overrideKey, overrideValue),
                        valueForDashboardBatteryKey(serviceManager, socKey, overrideKey, overrideValue),
                        valueForDashboardBatteryKey(serviceManager, chargeKey, overrideKey, overrideValue)
                )
        return selectDashboardBatteryPercent(values) ?: "--"
}

private fun readDashboardFuelRange(
        serviceManager: ServiceManager,
        overrideKey: String? = null,
        overrideValue: String? = null
): String {
        val fuelModeKey = CarConstants.CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER.getValue()
        val totalRemainKey = CarConstants.CAR_BASIC_REMAIN_ODOMETER.getValue()
        val values =
                listOf(
                        valueForDashboardFuelRangeKey(
                                serviceManager,
                                fuelModeKey,
                                overrideKey,
                                overrideValue
                        ),
                        valueForDashboardFuelRangeKey(
                                serviceManager,
                                totalRemainKey,
                                overrideKey,
                                overrideValue
                        )
                )
        return selectDashboardRange(values) ?: "--"
}

private fun valueForDashboardBatteryKey(
        serviceManager: ServiceManager,
        key: String,
        overrideKey: String?,
        overrideValue: String?
): String? {
        return if (key == overrideKey) overrideValue else serviceManager.getData(key)
}

private fun valueForDashboardFuelRangeKey(
        serviceManager: ServiceManager,
        key: String,
        overrideKey: String?,
        overrideValue: String?
): String? {
        return if (key == overrideKey) overrideValue else serviceManager.getData(key)
}

private fun selectDashboardBatteryPercent(values: List<String?>): String? {
        val normalized = values.mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
        return normalized.firstOrNull { isValidDashboardPercent(it, allowZero = false) }
                ?: normalized.firstOrNull { isValidDashboardPercent(it, allowZero = true) }
}

private fun selectDashboardRange(values: List<String?>): String? {
        val normalized = values.mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
        return normalized.firstOrNull { (it.toFloatOrNull() ?: -1f) > 0f }
                ?: normalized.firstOrNull { (it.toFloatOrNull() ?: -1f) >= 0f }
}

private fun isValidDashboardPercent(value: String, allowZero: Boolean): Boolean {
        val parsed = value.toFloatOrNull() ?: return false
        return parsed in 0f..100f && (allowZero || parsed > 0f)
}

@Composable
fun ImpulseDashboardFullscreenContent() {
        ExpandedImpulseDashboard()
}

@Composable
private fun ExpandedImpulseDashboard() {
        val serviceManager = ServiceManager.getInstance()
        val context = LocalContext.current
        val snapshot = rememberDashboardVehicleSnapshot(serviceManager)
        val entryProgress = remember { Animatable(0f) }
        var currentTime by remember { mutableStateOf(formatDashboardClock()) }
        val prefs =
                remember {
                        br.com.redesurftank.App.getDeviceProtectedContext()
                                .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
                }
        var dashboardCardOrder by remember {
                mutableStateOf(
                        normalizeDashboardCardOrder(
                                prefs.getString(SharedPreferencesKeys.DASHBOARD_CARD_ORDER.key, null)
                        )
                )
        }
        var layoutEditMode by remember { mutableStateOf(false) }
        var shortcutMenuExpanded by remember { mutableStateOf(false) }
        val albumBackground = rememberDashboardAlbumBackgroundState()
        var dashboardShortcutButton1Action by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION.key,
                                SteeringWheelCustomActionType.DEFAULT.key
                        )
                                ?: SteeringWheelCustomActionType.DEFAULT.key
                )
        }
        var dashboardShortcutButton2Action by remember {
                mutableStateOf(
                        prefs.getString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION.key,
                                SteeringWheelCustomActionType.DEFAULT.key
                        )
                                ?: SteeringWheelCustomActionType.DEFAULT.key
                )
        }

        fun updateDashboardCardOrder(nextOrder: List<DashboardCardId>) {
                val normalized = normalizeDashboardCardOrder(dashboardCardOrderToStorage(nextOrder))
                dashboardCardOrder = normalized
                prefs.edit {
                        putString(
                                SharedPreferencesKeys.DASHBOARD_CARD_ORDER.key,
                                dashboardCardOrderToStorage(normalized)
                        )
                }
        }

        fun setDashboardShortcutButton(button: Int) {
                val actions =
                        resolveImpulseDashboardShortcutActions(
                                currentButton1Action = dashboardShortcutButton1Action,
                                currentButton2Action = dashboardShortcutButton2Action,
                                selectedButton = button
                        )
                dashboardShortcutButton1Action = actions.button1Action
                dashboardShortcutButton2Action = actions.button2Action
                prefs.edit(commit = true) {
                        putBoolean(
                                SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.key,
                                true
                        )
                        putString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION.key,
                                actions.button1Action
                        )
                        putString(
                                SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION.key,
                                actions.button2Action
                        )
                }
                shortcutMenuExpanded = false
                ServiceManager.getInstance().ensureSteeringWheelButtonIntegration()
        }

        LaunchedEffect(Unit) {
                entryProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                )
        }

        LaunchedEffect(Unit) {
                while (true) {
                        currentTime = formatDashboardClock()
                        delay(30000)
                }
        }

        val activeProjectionPackage =
                BottomBarState.activeClusterProjectionPackage.takeIf { it.isNotEmpty() }
        val effectivePackage =
                activeProjectionPackage
                        ?: BottomBarState.selectedPackage.takeIf { it.isNotEmpty() }
                        ?: getBottomBarAppConfigs().firstOrNull()?.packageName
        val effectiveConfig =
                remember(effectivePackage) {
                        getBottomBarAppConfigs().find { it.packageName == effectivePackage }
                }
        val appInfo =
                remember(effectivePackage, effectiveConfig?.customName) {
                        effectivePackage?.let {
                                DisplayAppLauncher.resolveAppInfo(
                                        context,
                                        it,
                                        effectiveConfig?.customName
                                )
                        }
                }

        fun collapseDashboard() {
                BottomBarState.isDashboardExpanded = false
                BottomBarState.isVisible = true
                BottomBarState.isMenuExpanded = false
                BottomBarState.isSettingsMenuExpanded = false
                BottomBarState.isOverrideMenuExpanded = false
                BottomBarState.activeSliderType = null
        }

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(
                                        Brush.linearGradient(
                                                colors =
                                                        listOf(
                                                                Color(0xFF05070A),
                                                                Color(0xFF0D1318),
                                                                Color(0xFF12120F)
                                                        )
                                        )
                                )
                                .pointerInput(Unit) {
                                        awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                var totalDragY = 0f
                                                var totalDragX = 0f
                                                do {
                                                        val event = awaitPointerEvent()
                                                        event.changes.forEach { change ->
                                                                val deltaX =
                                                                        change.position.x -
                                                                                change.previousPosition.x
                                                                val deltaY =
                                                                        change.position.y -
                                                                                change.previousPosition.y
                                                                totalDragX += deltaX
                                                                if (deltaY > 0f) {
                                                                        totalDragY += deltaY
                                                                        if (totalDragY >
                                                                                        DASHBOARD_COLLAPSE_CONSUME_DRAG_PX
                                                                                && isDashboardCollapseDragMostlyVertical(
                                                                                        totalDragY,
                                                                                        totalDragX
                                                                                )
                                                                        ) {
                                                                                change.consume()
                                                                        }
                                                                }
                                                        }
                                                } while (event.changes.any { it.pressed })

                                                if (shouldCollapseDashboardAfterDrag(totalDragY, totalDragX)) {
                                                        collapseDashboard()
                                                }
                                        }
                                }
        ) {
                DashboardAlbumDynamicBackground(
                        primary = albumBackground.primary,
                        secondary = albumBackground.secondary,
                        accent = albumBackground.accent,
                        dark = albumBackground.dark,
                        hasArtwork = albumBackground.hasArtwork,
                        modifier = Modifier.matchParentSize(),
                        cornerRadius = 0.dp,
                        artworkAlpha = 0.9f,
                        fallbackAlpha = 0.42f,
                        artworkScrimAlpha = 0.5f,
                        fallbackScrimAlpha = 0.66f
                )
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .graphicsLayer {
                                                alpha = 0.82f + (0.18f * entryProgress.value)
                                                translationY = (1f - entryProgress.value) * 180f
                                        }
                                        .padding(
                                                start = 18.dp,
                                                top = 8.dp,
                                                end = 18.dp,
                                                bottom = 18.dp
                                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        DashboardTopDragHandle(
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        DashboardHeader(
                                time = currentTime,
                                snapshot = snapshot,
                                activeProjectionPackage = activeProjectionPackage,
                                layoutEditMode = layoutEditMode,
                                shortcutSelectedButton =
                                        resolveImpulseDashboardShortcutButton(
                                                dashboardShortcutButton1Action,
                                                dashboardShortcutButton2Action
                                        ),
                                shortcutMenuExpanded = shortcutMenuExpanded,
                                onToggleLayoutEditMode = { layoutEditMode = !layoutEditMode },
                                onShortcutExpandedChange = { shortcutMenuExpanded = it },
                                onShortcutButtonSelected = { setDashboardShortcutButton(it) },
                                onShowNativeMenu = { collapseDashboard() }
                        )
                        Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                dashboardCardOrder.forEachIndexed { index, cardId ->
                                        DashboardCardSlot(
                                                cardId = cardId,
                                                order = dashboardCardOrder,
                                                layoutEditMode = layoutEditMode,
                                                snapshot = snapshot,
                                                serviceManager = serviceManager,
                                                appLabel = appInfo?.label,
                                                appIcon = appInfo?.icon,
                                                activeProjectionPackage = activeProjectionPackage,
                                                albumBackground = albumBackground,
                                                modifier =
                                                        Modifier.weight(dashboardSlotWeight(index))
                                                                .fillMaxHeight(),
                                                onMoveCard = { movedCardId, direction ->
                                                        updateDashboardCardOrder(
                                                                moveDashboardCard(
                                                                        dashboardCardOrder,
                                                                        movedCardId,
                                                                        direction
                                                                )
                                                        )
                                                }
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun DashboardHeader(
        time: String,
        snapshot: DashboardVehicleSnapshot,
        activeProjectionPackage: String?,
        layoutEditMode: Boolean,
        shortcutSelectedButton: Int?,
        shortcutMenuExpanded: Boolean,
        onToggleLayoutEditMode: () -> Unit,
        onShortcutExpandedChange: (Boolean) -> Unit,
        onShortcutButtonSelected: (Int) -> Unit,
        onShowNativeMenu: () -> Unit
) {
        Row(
                modifier = Modifier.fillMaxWidth().height(62.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color(0xFF66E3FF),
                                modifier = Modifier.size(34.dp)
                        )
                        Column {
                                Text(
                                        text = "IMPULSE DRIVE",
                                        color = Color.White,
                                        fontFamily = DashboardReadableFont,
                                        fontSize = 25.sp,
                                        fontWeight = FontWeight.Bold
                                )
                                Text(
                                        text = projectionLabel(activeProjectionPackage),
                                        color = Color.White.copy(alpha = 0.62f),
                                        fontSize = 13.sp,
                                        fontFamily = DashboardReadableFont
                                )
                        }
                }
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                        DashboardUpdateChip()
                        DashboardStatusChip(
                                icon = Icons.Default.DeviceThermostat,
                                text = "Cabine ${formatTemperature(snapshot.insideTemp)}"
                        )
                        DashboardStatusChip(
                                icon = Icons.Default.WbSunny,
                                text = "Externa ${formatTemperature(snapshot.outsideTemp)}"
                        )
                        DashboardStatusChip(icon = Icons.Default.AccessTime, text = time)
                        DashboardHeaderControlButton(
                                icon = Icons.Default.Tune,
                                text = if (layoutEditMode) "Pronto" else "Layout",
                                active = layoutEditMode,
                                onClick = onToggleLayoutEditMode
                        )
                        DashboardShortcutSelectorButton(
                                selectedButton = shortcutSelectedButton,
                                expanded = shortcutMenuExpanded,
                                onExpandedChange = onShortcutExpandedChange,
                                onSelectButton = onShortcutButtonSelected,
                                modifier = Modifier.zIndex(6f)
                        )
                        DashboardNativeMenuButton(onClick = onShowNativeMenu)
                }
        }
}

@Composable
private fun DashboardHeaderControlButton(
        icon: ImageVector,
        text: String,
        active: Boolean,
        onClick: () -> Unit
) {
        val accent = if (active) Color(0xFF78E08F) else Color(0xFF66E3FF)
        Surface(
                onClick = onClick,
                modifier = Modifier.height(44.dp),
                color = accent.copy(alpha = if (active) 0.18f else 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = if (active) 0.48f else 0.30f))
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Icon(
                                icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                        )
                        Text(
                                text = text,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = DashboardReadableFont,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                        )
                }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardUpdateChip() {
        // Só existe quando há versão nova. Nada de placeholder "tudo atualizado": o dashboard é
        // olhado de relance dirigindo, e um chip permanente que quase sempre diz "nada a fazer" só
        // gasta espaço e atenção.
        val version = UpdateNoticeManager.availableVersion ?: return
        val context = LocalContext.current
        val accent = Color(0xFF4A9EFF)
        Surface(
                modifier = Modifier
                        .height(44.dp)
                        .combinedClickable(
                                onClick = { openInformacoesScreen(context) },
                                // Toque longo dispensa: quem não quer atualizar agora silencia esta
                                // versão e só volta a ser avisado quando sair OUTRA.
                                onLongClick = { UpdateNoticeManager.dismiss() }
                        ),
                color = accent.copy(alpha = 0.20f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Icon(
                                Icons.Default.FileDownload,
                                contentDescription = "Nova versão disponível",
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                        )
                        Text(
                                text = version,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = DashboardReadableFont,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                        )
                }
        }
}

/** Abre o app já na tela de Informações, onde fica o botão de atualizar. */
private fun openInformacoesScreen(context: android.content.Context) {
        try {
                context.startActivity(
                        android.content.Intent(
                                        context,
                                        br.com.redesurftank.havalshisuku.MainActivity::class.java
                                )
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(
                                        br.com.redesurftank.havalshisuku.MainActivity.EXTRA_SCREEN,
                                        "Informações"
                                )
                )
        } catch (t: Throwable) {
                android.util.Log.e("BottomBarUI", "não deu pra abrir Informações", t)
        }
}



@Composable
private fun DashboardNativeMenuButton(onClick: () -> Unit) {
        Surface(
                onClick = onClick,
                modifier = Modifier.height(44.dp),
                color = Color(0xFF66E3FF).copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF66E3FF).copy(alpha = 0.34f))
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF66E3FF),
                                modifier = Modifier.size(20.dp)
                        )
                        Text(
                                text = "Menu nativo",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = DashboardReadableFont,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                        )
                }
        }
}

@Composable
private fun DashboardShortcutSelectorButton(
        selectedButton: Int?,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        onSelectButton: (Int) -> Unit,
        modifier: Modifier = Modifier
) {
        val accent = if (selectedButton == null) Color(0xFFFF7A7A) else Color(0xFF66E3FF)
        Box(modifier = modifier) {
                Surface(
                        onClick = { onExpandedChange(!expanded) },
                        modifier =
                                if (selectedButton == null) Modifier.height(44.dp)
                                else Modifier.size(44.dp),
                        color = accent.copy(alpha = if (selectedButton == null) 0.16f else 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border =
                                BorderStroke(
                                        1.dp,
                                        accent.copy(alpha = if (selectedButton == null) 0.42f else 0.34f)
                                )
                ) {
                        Row(
                                modifier =
                                        if (selectedButton == null) {
                                                Modifier.padding(horizontal = 14.dp)
                                        } else {
                                                Modifier.fillMaxSize()
                                        },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                        ) {
                                if (selectedButton == null) {
                                        Text(
                                                text = "Definir atalho",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontFamily = DashboardReadableFont,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                        )
                                } else {
                                        Icon(
                                                dashboardShortcutButtonIcon(selectedButton),
                                                contentDescription = "Atalho no botão $selectedButton",
                                                tint = accent,
                                                modifier = Modifier.size(21.dp)
                                        )
                                }
                        }
                }
                DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { onExpandedChange(false) },
                        modifier = Modifier.background(Color(0xFF121A22)),
                        properties = PopupProperties(focusable = false)
                ) {
                        DashboardShortcutDropdownItem(
                                text = "Botão 1",
                                icon = Icons.Default.Add,
                                selected = selectedButton == 1,
                                onClick = { onSelectButton(1) }
                        )
                        DashboardShortcutDropdownItem(
                                text = "Botão 2",
                                icon = Icons.Default.Star,
                                selected = selectedButton == 2,
                                onClick = { onSelectButton(2) }
                        )
                }
        }
}

@Composable
private fun DashboardShortcutDropdownItem(
        text: String,
        icon: ImageVector,
        selected: Boolean,
        onClick: () -> Unit
) {
        DropdownMenuItem(
                text = {
                        Text(
                                text = text,
                                color = Color.White,
                                fontFamily = DashboardReadableFont,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                },
                leadingIcon = {
                        Icon(
                                icon,
                                contentDescription = null,
                                tint = if (selected) Color(0xFF66E3FF) else Color.White.copy(alpha = 0.76f)
                        )
                },
                onClick = onClick
        )
}

private fun dashboardShortcutButtonIcon(button: Int): ImageVector {
        return if (button == 1) Icons.Default.Add else Icons.Default.Star
}

@Composable
private fun DashboardTopDragHandle(modifier: Modifier = Modifier) {
        Box(
                modifier = modifier.width(148.dp).height(24.dp),
                contentAlignment = Alignment.Center
        ) {
                Box(
                        modifier =
                                Modifier.width(86.dp)
                                        .height(5.dp)
                                        .background(
                                                Color.White.copy(alpha = 0.42f),
                                                RoundedCornerShape(50)
                                        )
                )
        }
}

@Composable
private fun DashboardTopDragTouchTarget(modifier: Modifier = Modifier, onCollapse: () -> Unit) {
        Spacer(
                modifier =
                        modifier.fillMaxWidth()
                                .height(44.dp)
                                .zIndex(2f)
                                .pointerInput(onCollapse) {
                                        var totalDragY = 0f
                                        detectDragGestures(
                                                onDragStart = { totalDragY = 0f },
                                                onDragCancel = { totalDragY = 0f },
                                                onDragEnd = {
                                                        if (shouldCollapseDashboardAfterDrag(totalDragY)) {
                                                                onCollapse()
                                                        }
                                                        totalDragY = 0f
                                                }
                                        ) { change, dragAmount ->
                                                if (dragAmount.y > 0f) {
                                                        totalDragY += dragAmount.y
                                                        change.consume()
                                                }
                                        }
                                }
        )
}

internal fun shouldCollapseDashboardAfterDrag(totalDragY: Float, totalDragX: Float = 0f): Boolean {
        return totalDragY > DASHBOARD_COLLAPSE_DRAG_THRESHOLD_PX &&
                isDashboardCollapseDragMostlyVertical(totalDragY, totalDragX)
}

internal fun isDashboardCollapseDragMostlyVertical(totalDragY: Float, totalDragX: Float): Boolean {
        return totalDragY > 0f &&
                totalDragY >= abs(totalDragX) * DASHBOARD_COLLAPSE_VERTICAL_DOMINANCE_RATIO
}

private fun dashboardSlotWeight(index: Int): Float {
        return when (index) {
                0 -> 1.12f
                1 -> 0.90f
                else -> 1.03f
        }
}

@Composable
private fun DashboardCardSlot(
        cardId: DashboardCardId,
        order: List<DashboardCardId>,
        layoutEditMode: Boolean,
        snapshot: DashboardVehicleSnapshot,
        serviceManager: ServiceManager,
        appLabel: String?,
        appIcon: android.graphics.drawable.Drawable?,
        activeProjectionPackage: String?,
        albumBackground: DashboardAlbumBackgroundState,
        modifier: Modifier,
        onMoveCard: (DashboardCardId, Int) -> Unit
) {
        val controls: @Composable (() -> Unit)? =
                if (layoutEditMode) {
                        {
                                DashboardCardPositionControls(
                                        cardId = cardId,
                                        order = order,
                                        onMoveCard = onMoveCard
                                )
                        }
                } else {
                        null
                }

        val content: @Composable (Modifier) -> Unit = { contentModifier ->
                when (cardId) {
                        DashboardCardId.MEDIA ->
                                DashboardMediaPanel(
                                        appLabel = appLabel,
                                        appIcon = appIcon,
                                        activeProjectionPackage = activeProjectionPackage,
                                        albumBackground = albumBackground,
                                        volume = snapshot.volume,
                                        serviceManager = serviceManager,
                                        layoutControls = controls,
                                        modifier = contentModifier
                                )
                        DashboardCardId.DYNAMICS ->
                                DashboardSettingsPanel(
                                        snapshot = snapshot,
                                        serviceManager = serviceManager,
                                        albumBackground = albumBackground,
                                        layoutControls = controls,
                                        modifier = contentModifier
                                )
                        DashboardCardId.HVAC ->
                                DashboardHvacPanel(
                                        snapshot = snapshot,
                                        serviceManager = serviceManager,
                                        albumBackground = albumBackground,
                                        layoutControls = controls,
                                        modifier = contentModifier
                                )
                }
        }

        content(modifier)
}

@Composable
private fun DashboardCardPositionControls(
        cardId: DashboardCardId,
        order: List<DashboardCardId>,
        onMoveCard: (DashboardCardId, Int) -> Unit
) {
        val index = order.indexOf(cardId)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                DashboardMiniIconButton(
                        icon = Icons.Default.KeyboardArrowLeft,
                        enabled = index > 0,
                        contentDescription = "Mover card para esquerda"
                ) {
                        onMoveCard(cardId, -1)
                }
                DashboardMiniIconButton(
                        icon = Icons.Default.KeyboardArrowRight,
                        enabled = index in 0 until order.lastIndex,
                        contentDescription = "Mover card para direita"
                ) {
                        onMoveCard(cardId, 1)
                }
        }
}

@Composable
private fun DashboardMiniIconButton(
        icon: ImageVector,
        enabled: Boolean,
        contentDescription: String,
        onClick: () -> Unit
) {
        Surface(
                onClick = onClick,
                enabled = enabled,
                color = Color.White.copy(alpha = if (enabled) 0.10f else 0.04f),
                shape = RoundedCornerShape(8.dp),
                border =
                        BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = if (enabled) 0.18f else 0.06f)
                        ),
                modifier = Modifier.size(34.dp)
        ) {
                Box(contentAlignment = Alignment.Center) {
                        Icon(
                                icon,
                                contentDescription = contentDescription,
                                tint = Color.White.copy(alpha = if (enabled) 0.92f else 0.28f),
                                modifier = Modifier.size(22.dp)
                        )
                }
        }
}

@Composable
private fun DashboardDrivePanel(snapshot: DashboardVehicleSnapshot, modifier: Modifier) {
        DashboardPanel(modifier = modifier) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                        ) {
                                Column {
                                        Text(
                                                text = "Velocidade",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 15.sp,
                                                fontFamily = DashboardReadableFont
                                        )
                                        Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                        text = formatSpeed(snapshot.speed),
                                                        color = Color.White,
                                                        fontSize = 98.sp,
                                                        fontFamily = DashboardReadableFont,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                        text = "km/h",
                                                        color = Color.White.copy(alpha = 0.55f),
                                                        fontSize = 18.sp,
                                                        fontFamily = DashboardReadableFont,
                                                        modifier = Modifier.padding(start = 8.dp, bottom = 19.dp)
                                                )
                                        }
                                }
                                Box(
                                        modifier =
                                                Modifier.size(90.dp)
                                                        .background(
                                                                Color(0xFF66E3FF).copy(alpha = 0.12f),
                                                                RoundedCornerShape(8.dp)
                                                        )
                                                        .border(
                                                                1.dp,
                                                                Color(0xFF66E3FF).copy(alpha = 0.42f),
                                                                RoundedCornerShape(8.dp)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = formatGear(snapshot.gear),
                                                color = Color(0xFF66E3FF),
                                                fontSize = 44.sp,
                                                fontFamily = DashboardReadableFont,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DashboardStatPill(
                                        label = "Condução",
                                        value = driveModeLabel(snapshot.driveMode),
                                        icon = Icons.Default.Speed,
                                        modifier = Modifier.weight(1f)
                                )
                                DashboardStatPill(
                                        label = "Direção",
                                        value = steeringModeLabel(snapshot.steeringMode),
                                        icon = DashboardSteeringWheelIcon,
                                        modifier = Modifier.weight(1f)
                                )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DashboardStatPill(
                                        label = "EV",
                                        value = powerModelLabel(snapshot.powerModel, snapshot.powerReserve, snapshot.socTarget),
                                        icon = Icons.Default.ElectricBolt,
                                        accent = Color(0xFF78E08F),
                                        modifier = Modifier.weight(1f)
                                )
                                DashboardStatPill(
                                        label = "Regen",
                                        value = regenLabel(snapshot.energyRecovery),
                                        icon = Icons.Default.Autorenew,
                                        accent = Color(0xFFFFC857),
                                        modifier = Modifier.weight(1f)
                                )
                        }
                        DashboardReadinessStrip(snapshot.readyState)
                }
        }
}

@Composable
private fun DashboardEnergyPanel(snapshot: DashboardVehicleSnapshot, modifier: Modifier) {
        val evPowerKw = calculateEvPowerKw(snapshot.batteryVoltage, snapshot.batteryCurrent)
        DashboardPanel(modifier = modifier) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardPanelTitle(Icons.Default.ElectricBolt, "Energia e autonomia")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DashboardLinearMeter(
                                        label = "Bateria",
                                        value = formatPercent(snapshot.batteryPercent),
                                        fraction = percentFraction(snapshot.batteryPercent),
                                        accent = Color(0xFF78E08F),
                                        icon = Icons.Default.BatteryChargingFull,
                                        modifier = Modifier.weight(1f)
                                )
                                DashboardLinearMeter(
                                        label = "Combustível",
                                        value = formatPercent(snapshot.fuelPercent),
                                        fraction = percentFraction(snapshot.fuelPercent),
                                        accent = Color(0xFFFFC857),
                                        icon = Icons.Default.LocalGasStation,
                                        modifier = Modifier.weight(1f)
                                )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DashboardMetricTile(
                                        label = "Autonomia EV",
                                        value = formatDistance(snapshot.batteryRange),
                                        icon = Icons.Default.ElectricCar,
                                        modifier = Modifier.weight(1f)
                                )
                                DashboardMetricTile(
                                        label = "Autonomia HEV",
                                        value = formatDistance(snapshot.fuelRange),
                                        icon = Icons.Default.Route,
                                        modifier = Modifier.weight(1f)
                                )
                                DashboardMetricTile(
                                        label = "Potência",
                                        value = evPowerKw,
                                        icon = Icons.Default.Bolt,
                                        modifier = Modifier.weight(1f)
                                )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DashboardMetricTile(
                                        label = "Odômetro",
                                        value = formatDistance(snapshot.odometer),
                                        icon = Icons.Default.Timeline,
                                        modifier = Modifier.weight(1f)
                                )
                                DashboardMetricTile(
                                        label = "Consumo",
                                        value = formatConsumption(snapshot.avgFuel, "L/100"),
                                        icon = Icons.Default.LocalGasStation,
                                        modifier = Modifier.weight(1f)
                                )
                                DashboardMetricTile(
                                        label = "Elétrico",
                                        value = formatConsumption(snapshot.avgEnergy, "kWh"),
                                        icon = Icons.Default.ElectricBolt,
                                        modifier = Modifier.weight(1f)
                                )
                        }
                }
        }
}

@Composable
private fun DashboardSettingsPanel(
        snapshot: DashboardVehicleSnapshot,
        serviceManager: ServiceManager,
        albumBackground: DashboardAlbumBackgroundState? = null,
        layoutControls: @Composable (() -> Unit)? = null,
        modifier: Modifier
) {
        DashboardPanel(modifier = modifier, albumBackground = albumBackground) {
                val driveOptions = listOf("2" to "Eco", "0" to "Normal", "1" to "Sport")
                val powerOptions = listOf("0" to "HEV", "1" to "EV Prior.", "3" to "EV")
                val regenOptions = listOf("2" to "Baixo", "0" to "Normal", "1" to "Alto")
                val steeringOptions = listOf("2" to "Conforto", "0" to "Normal", "1" to "Sport")
                var showHevDialog by remember { mutableStateOf(false) }
                if (showHevDialog) {
                        HevModeDialog(
                                snapshot = snapshot,
                                serviceManager = serviceManager,
                                onDismiss = { showHevDialog = false }
                        )
                }

                Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                DashboardPanelTitle(Icons.Default.Tune, "Dinâmica")
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        layoutControls?.invoke()
                                        DashboardDynamicsReadyBadge(snapshot.readyState)
                                }
                        }
                        Row(
                                modifier = Modifier.fillMaxWidth().height(138.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                DashboardCircularResourceGauge(
                                        label = "Bateria",
                                        value = formatPercent(snapshot.batteryPercent),
                                        fraction = percentFraction(snapshot.batteryPercent),
                                        detail = formatDistance(snapshot.batteryRange),
                                        accent = Color(0xFF78E08F),
                                        icon = Icons.Default.BatteryChargingFull,
                                        modifier = Modifier.weight(1f)
                                )
                                DashboardCircularResourceGauge(
                                        label = "Combustível",
                                        value = formatPercent(snapshot.fuelPercent),
                                        fraction = percentFraction(snapshot.fuelPercent),
                                        detail =
                                                formatDashboardFuelLitersAndRange(
                                                        snapshot.fuelPercent,
                                                        snapshot.fuelRange
                                                ),
                                        accent = Color(0xFFFFC857),
                                        icon = Icons.Default.LocalGasStation,
                                        modifier = Modifier.weight(1f)
                                )
                        }
                        Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        DashboardPremiumCycleControl(
                                                label = "Condução",
                                                value = driveModeLabel(snapshot.driveMode),
                                                nextValue =
                                                        nextDashboardOptionLabel(
                                                                snapshot.driveMode,
                                                                driveOptions
                                                        ),
                                                icon = Icons.Default.Speed,
                                                accent = Color(0xFF66E3FF),
                                                modifier = Modifier.weight(1f)
                                        ) {
                                                serviceManager.updateData(
                                                        CarConstants
                                                                .CAR_DRIVE_SETTING_DRIVE_MODE
                                                                .getValue(),
                                                        nextDashboardOption(
                                                                snapshot.driveMode,
                                                                driveOptions
                                                        )
                                                )
                                        }
                                        DashboardPremiumCycleControl(
                                                label = "Energia",
                                                value = powerModelLabel(snapshot.powerModel, snapshot.powerReserve, snapshot.socTarget),
                                                nextValue =
                                                        nextDashboardOptionLabel(
                                                                snapshot.powerModel,
                                                                powerOptions
                                                        ),
                                                icon = Icons.Default.ElectricBolt,
                                                accent = Color(0xFF78E08F),
                                                modifier = Modifier.weight(1f),
                                                onLongClick = {
                                                        // Toque longo em HEV -> popup do sub-modo
                                                        // (Inteligente/Prioritário + % de bateria).
                                                        if (snapshot.powerModel.trim() == "0") {
                                                                showHevDialog = true
                                                        }
                                                }
                                        ) {
                                                serviceManager.updateData(
                                                        CarConstants
                                                                .CAR_EV_SETTING_POWER_MODEL_CONFIG
                                                                .getValue(),
                                                        nextDashboardOption(
                                                                snapshot.powerModel,
                                                                powerOptions
                                                        )
                                                )
                                        }
                                }
                                Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        DashboardPremiumCycleControl(
                                                label = "Regeneração",
                                                value = regenLabel(snapshot.energyRecovery),
                                                nextValue =
                                                        nextDashboardOptionLabel(
                                                                snapshot.energyRecovery,
                                                                regenOptions
                                                        ),
                                                icon = Icons.Default.Autorenew,
                                                accent = Color(0xFFFFC857),
                                                modifier = Modifier.weight(1f)
                                        ) {
                                                serviceManager.updateData(
                                                        CarConstants
                                                                .CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL
                                                                .getValue(),
                                                        nextDashboardOption(
                                                                snapshot.energyRecovery,
                                                                regenOptions
                                                        )
                                                )
                                        }
                                        DashboardPremiumCycleControl(
                                                label = "Direção",
                                                value = steeringModeLabel(snapshot.steeringMode),
                                                nextValue =
                                                        nextDashboardOptionLabel(
                                                                snapshot.steeringMode,
                                                                steeringOptions
                                                        ),
                                                icon = DashboardSteeringWheelIcon,
                                                accent = Color(0xFFB7A6FF),
                                                modifier = Modifier.weight(1f)
                                        ) {
                                                serviceManager.updateData(
                                                        CarConstants
                                                                .CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE
                                                                .getValue(),
                                                        nextDashboardOption(
                                                                snapshot.steeringMode,
                                                                steeringOptions
                                                        )
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun DashboardMediaPanel(
        appLabel: String?,
        appIcon: android.graphics.drawable.Drawable?,
        activeProjectionPackage: String?,
        albumBackground: DashboardAlbumBackgroundState,
        volume: String,
        serviceManager: ServiceManager,
        layoutControls: @Composable (() -> Unit)? = null,
        modifier: Modifier
) {
        val context = LocalContext.current
        val mediaTitle = BottomBarState.mediaTitle?.takeIf { it.isNotBlank() }
        val mediaArtist = BottomBarState.mediaArtist?.takeIf { it.isNotBlank() }
        val mediaAlbum = BottomBarState.mediaAlbum?.takeIf { it.isNotBlank() }
        val mediaArtwork = BottomBarState.mediaArtwork
        val mediaPackageName = BottomBarState.mediaPackageName
        val isPlaying = BottomBarState.mediaIsPlaying
        val durationMs = BottomBarState.mediaDurationMs
        val elapsedMs = BottomBarState.mediaElapsedMs
        val progressUpdatedAtMs = BottomBarState.mediaProgressUpdatedAtMs
        val canSeek = BottomBarState.mediaCanSeek
        val displayedElapsedMs =
                rememberMediaElapsedMs(
                        elapsedMs = elapsedMs,
                        durationMs = durationMs,
                        progressUpdatedAtMs = progressUpdatedAtMs,
                        isPlaying = isPlaying
                )
        val dynamicPrimary = albumBackground.primary
        val dynamicSecondary = albumBackground.secondary
        val dynamicAccent = albumBackground.accent
        val dynamicDark = albumBackground.dark
        val title =
                mediaTitle
                        ?: appLabel
                        ?: shortProjectionLabel(activeProjectionPackage)
                        ?: "Audio"
        val mediaSubtitle =
                listOfNotNull(mediaArtist, mediaAlbum).distinct().joinToString(" • ")
                        .takeIf { it.isNotBlank() }
        val subtitle =
                mediaSubtitle
                        ?: when {
                                activeProjectionPackage != null -> "Projecao ativa no cluster"
                                mediaPackageName != null -> "Midia do sistema"
                                else -> "Sistema de audio"
                        }
        var visibleVolume by remember {
                mutableIntStateOf(
                        volume.toIntOrNull()
                                ?.coerceIn(DASHBOARD_MEDIA_VOLUME_MIN, DASHBOARD_MEDIA_VOLUME_MAX)
                                ?: DASHBOARD_MEDIA_VOLUME_MIN
                )
        }
        var pendingVolume by remember { mutableStateOf<Int?>(null) }
        var pendingVolumeAtMs by remember { mutableLongStateOf(0L) }

        LaunchedEffect(volume) {
                val remoteVolume =
                        volume.toIntOrNull()
                                ?.coerceIn(DASHBOARD_MEDIA_VOLUME_MIN, DASHBOARD_MEDIA_VOLUME_MAX)
                                ?: return@LaunchedEffect
                val pending = pendingVolume
                val pendingAgeMs = SystemClock.elapsedRealtime() - pendingVolumeAtMs
                if (pending == null || remoteVolume == pending || pendingAgeMs > 1800L) {
                        visibleVolume = remoteVolume
                        if (remoteVolume == pending || pendingAgeMs > 1800L) {
                                pendingVolume = null
                        }
                }
        }

        fun updateVolume(delta: Int) {
                if (delta == 0) return
                val serviceVolume =
                        serviceManager
                                .getData(CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue())
                                ?.toIntOrNull()
                                ?.coerceIn(DASHBOARD_MEDIA_VOLUME_MIN, DASHBOARD_MEDIA_VOLUME_MAX)
                val base = pendingVolume ?: serviceVolume ?: visibleVolume
                val next = resolveDashboardMediaVolumeAfterDelta(base, delta)
                adjustDashboardSystemMediaVolume(context, delta)
                visibleVolume = next
                pendingVolume = next
                pendingVolumeAtMs = SystemClock.elapsedRealtime()
                serviceManager.updateData(
                        CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue(),
                        next.toString()
                )
        }

        fun runMediaControl(action: () -> Unit) {
                BottomBarService.suppressDashboardControlFocusRestore("dashboard_media_control")
                action()
        }

        Box(
                modifier =
                        modifier.clip(RoundedCornerShape(8.dp))
                                .background(
                                        Brush.linearGradient(
                                                colors =
                                                        listOf(
                                                                dynamicPrimary.copy(alpha = 0.38f),
                                                                dynamicSecondary.copy(alpha = 0.28f),
                                                                dynamicDark.copy(alpha = 0.98f)
                                                        ),
                                                start = Offset.Zero,
                                                end = Offset(900f, 620f)
                                        )
                                )
                                .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(8.dp)
                                )
        ) {
                DashboardAlbumDynamicBackground(
                        primary = dynamicPrimary,
                        secondary = dynamicSecondary,
                        accent = dynamicAccent,
                        dark = dynamicDark,
                        hasArtwork = mediaArtwork != null,
                        modifier = Modifier.matchParentSize()
                )
                Column(
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                DashboardPanelTitle(Icons.Default.Album, "Midia")
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        layoutControls?.invoke()
                                        DashboardMediaBadge(
                                                isPlaying = isPlaying,
                                                hasMetadata = mediaTitle != null
                                        )
                                }
                        }
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.06f))
                                                .border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.12f),
                                                        RoundedCornerShape(8.dp)
                                                )
                        ) {
                                if (mediaArtwork != null) {
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .background(
                                                                        Brush.linearGradient(
                                                                                colors =
                                                                                        listOf(
                                                                                                dynamicPrimary,
                                                                                                dynamicSecondary,
                                                                                                dynamicDark
                                                                                        ),
                                                                                start = Offset.Zero,
                                                                                end = Offset(900f, 620f)
                                                                        )
                                                                )
                                        )
                                        Image(
                                                bitmap = mediaArtwork.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                        )
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .background(
                                                                        Brush.verticalGradient(
                                                                                colors =
                                                                                        listOf(
                                                                                                Color.Transparent,
                                                                                                Color.Black.copy(alpha = 0.72f)
                                                                                        ),
                                                                                startY = 120f
                                                                        )
                                                                )
                                        )
                                } else {
                                        DashboardArtworkFallback(
                                                appIcon = appIcon,
                                                activeProjectionPackage = activeProjectionPackage,
                                                modifier = Modifier.fillMaxSize()
                                        )
                                }
                                Column(
                                        modifier =
                                                Modifier.align(Alignment.BottomStart)
                                                        .fillMaxWidth()
                                                        .padding(20.dp)
                                ) {
                                        Text(
                                                text = title,
                                                color = Color.White,
                                                fontSize = 31.sp,
                                                fontFamily = DashboardReadableFont,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                                text = subtitle,
                                                color = Color.White.copy(alpha = 0.72f),
                                                fontSize = 16.sp,
                                                fontFamily = DashboardReadableFont,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 6.dp)
                                        )
                                }
                        }
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                DashboardIconButton(
                                        Icons.Default.SkipPrevious,
                                        size = 66.dp,
                                        contentDescription = "Musica anterior"
                                ) {
                                        runMediaControl {
                                                BottomBarService.skipCurrentMediaPrevious()
                                        }
                                }
                                DashboardIconButton(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        size = 66.dp,
                                        contentDescription =
                                                if (isPlaying) "Pausar musica" else "Reproduzir musica"
                                ) {
                                        runMediaControl {
                                                BottomBarService.toggleCurrentMediaPlayback()
                                        }
                                }
                                DashboardMediaProgressMeter(
                                        elapsedMs = displayedElapsedMs,
                                        durationMs = durationMs,
                                        canSeek = canSeek,
                                        accent = dynamicAccent,
                                        modifier = Modifier.weight(1f),
                                        onSeek = { positionMs ->
                                                runMediaControl {
                                                        BottomBarService.seekCurrentMediaTo(positionMs)
                                                }
                                        }
                                )
                                DashboardIconButton(
                                        Icons.Default.SkipNext,
                                        size = 66.dp,
                                        contentDescription = "Proxima musica"
                                ) {
                                        runMediaControl {
                                                BottomBarService.skipCurrentMediaNext()
                                        }
                                }
                        }
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                DashboardIconButton(
                                        Icons.Default.Remove,
                                        size = 70.dp,
                                        contentDescription = "Diminuir volume"
                                ) {
                                        runMediaControl { updateVolume(-1) }
                                }
                                DashboardLinearMeter(
                                        label = "Volume",
                                        value = visibleVolume.toString(),
                                        fraction =
                                                (visibleVolume / DASHBOARD_MEDIA_VOLUME_MAX.toFloat())
                                                        .coerceIn(0f, 1f),
                                        accent = Color(0xFF66E3FF),
                                        icon = Icons.Default.VolumeUp,
                                        modifier = Modifier.weight(1f)
                                )
                                DashboardIconButton(
                                        Icons.Default.Add,
                                        size = 70.dp,
                                        contentDescription = "Aumentar volume"
                                ) {
                                        runMediaControl { updateVolume(1) }
                                }
                        }
                }
        }
}

@Composable
private fun rememberMediaElapsedMs(
        elapsedMs: Long,
        durationMs: Long,
        progressUpdatedAtMs: Long,
        isPlaying: Boolean
): Long {
        var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(elapsedMs, durationMs, progressUpdatedAtMs, isPlaying) {
                nowMs = SystemClock.elapsedRealtime()
                while (isPlaying && durationMs > 0L) {
                        delay(1000)
                        nowMs = SystemClock.elapsedRealtime()
                }
        }
        val deltaMs =
                if (isPlaying && durationMs > 0L && progressUpdatedAtMs > 0L) {
                        (nowMs - progressUpdatedAtMs).coerceAtLeast(0L)
                } else {
                        0L
                }
        return if (durationMs > 0L) {
                (elapsedMs + deltaMs).coerceIn(0L, durationMs)
        } else {
                elapsedMs.coerceAtLeast(0L)
        }
}

private data class DashboardAlbumBackgroundState(
        val primary: Color,
        val secondary: Color,
        val accent: Color,
        val dark: Color,
        val hasArtwork: Boolean
)

@Composable
private fun rememberDashboardAlbumBackgroundState(): DashboardAlbumBackgroundState {
        val mediaTitle = BottomBarState.mediaTitle?.takeIf { it.isNotBlank() }
        val mediaArtist = BottomBarState.mediaArtist?.takeIf { it.isNotBlank() }
        val mediaAlbum = BottomBarState.mediaAlbum?.takeIf { it.isNotBlank() }
        val mediaArtwork = BottomBarState.mediaArtwork
        val mediaPackageName = BottomBarState.mediaPackageName
        val artworkKey =
                remember(mediaPackageName, mediaTitle, mediaArtist, mediaAlbum, mediaArtwork) {
                        listOfNotNull(
                                        mediaPackageName,
                                        mediaTitle,
                                        mediaArtist,
                                        mediaAlbum,
                                        mediaArtwork?.width?.toString(),
                                        mediaArtwork?.height?.toString()
                                )
                                .joinToString("|")
                }
        var albumColors by remember { mutableStateOf(AlbumBackgroundService.fallbackColors) }
        LaunchedEffect(artworkKey, mediaArtwork) {
                albumColors =
                        withContext(Dispatchers.Default) {
                                AlbumBackgroundService.extractColors(mediaArtwork, artworkKey)
                        }
        }
        val dynamicPrimary by
                animateColorAsState(
                        targetValue = Color(albumColors.primary),
                        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                        label = "dashboardAlbumPrimary"
                )
        val dynamicSecondary by
                animateColorAsState(
                        targetValue = Color(albumColors.secondary),
                        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                        label = "dashboardAlbumSecondary"
                )
        val dynamicAccent by
                animateColorAsState(
                        targetValue = Color(albumColors.accent),
                        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                        label = "dashboardAlbumAccent"
                )
        val dynamicDark by
                animateColorAsState(
                        targetValue = Color(albumColors.dark),
                        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                        label = "dashboardAlbumDark"
                )
        return DashboardAlbumBackgroundState(
                primary = dynamicPrimary,
                secondary = dynamicSecondary,
                accent = dynamicAccent,
                dark = dynamicDark,
                hasArtwork = mediaArtwork != null
        )
}

@Composable
private fun DashboardAlbumDynamicBackground(
        primary: Color,
        secondary: Color,
        accent: Color,
        dark: Color,
        hasArtwork: Boolean,
        modifier: Modifier = Modifier,
        cornerRadius: Dp = 8.dp,
        artworkAlpha: Float = 1f,
        fallbackAlpha: Float = 0.56f,
        artworkScrimAlpha: Float = 0.42f,
        fallbackScrimAlpha: Float = 0.58f
) {
        Canvas(
                modifier =
                        modifier.fillMaxSize()
                                .clip(RoundedCornerShape(cornerRadius))
                                .alpha(if (hasArtwork) artworkAlpha else fallbackAlpha)
        ) {
                drawRect(
                        brush =
                                Brush.linearGradient(
                                        colors =
                                                listOf(
                                                        primary.copy(alpha = 0.34f),
                                                        secondary.copy(alpha = 0.3f),
                                                        dark.copy(alpha = 0.72f)
                                                ),
                                        start = Offset.Zero,
                                        end = Offset(size.width, size.height)
                                )
                )
                drawCircle(
                        brush =
                                Brush.radialGradient(
                                        colors =
                                                listOf(
                                                        primary.copy(alpha = 0.58f),
                                                        primary.copy(alpha = 0.08f),
                                                        Color.Transparent
                                                ),
                                        center = Offset(size.width * 0.22f, size.height * 0.18f),
                                        radius = size.maxDimension * 0.72f
                                ),
                        radius = size.maxDimension * 0.72f,
                        center = Offset(size.width * 0.22f, size.height * 0.18f)
                )
                drawCircle(
                        brush =
                                Brush.radialGradient(
                                        colors =
                                                listOf(
                                                        secondary.copy(alpha = 0.48f),
                                                        secondary.copy(alpha = 0.08f),
                                                        Color.Transparent
                                                ),
                                        center = Offset(size.width * 0.82f, size.height * 0.72f),
                                        radius = size.maxDimension * 0.82f
                                ),
                        radius = size.maxDimension * 0.82f,
                        center = Offset(size.width * 0.82f, size.height * 0.72f)
                )
                drawCircle(
                        brush =
                                Brush.radialGradient(
                                        colors =
                                                listOf(
                                                        accent.copy(alpha = 0.22f),
                                                        Color.Transparent
                                                ),
                                        center = Offset(size.width * 0.62f, size.height * 0.12f),
                                        radius = size.maxDimension * 0.48f
                                ),
                        radius = size.maxDimension * 0.48f,
                        center = Offset(size.width * 0.62f, size.height * 0.12f)
                )
                drawRect(Color.Black.copy(alpha = if (hasArtwork) artworkScrimAlpha else fallbackScrimAlpha))
        }
}

@Composable
private fun DashboardMediaProgressMeter(
        elapsedMs: Long,
        durationMs: Long,
        canSeek: Boolean,
        accent: Color,
        modifier: Modifier = Modifier,
        onSeek: (Long) -> Unit
) {
        var trackWidthPx by remember { mutableFloatStateOf(1f) }
        var dragFraction by remember { mutableStateOf<Float?>(null) }
        val progressFraction =
                if (durationMs > 0L) {
                        elapsedMs.toFloat() / durationMs.toFloat()
                } else {
                        0f
                }
        val activeFraction = (dragFraction ?: progressFraction).coerceIn(0f, 1f)
        val trackModifier =
                Modifier.fillMaxWidth()
                        .height(14.dp)
                        .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                        .pointerInput(canSeek, durationMs, trackWidthPx) {
                                if (!canSeek || durationMs <= 0L) return@pointerInput

                                fun updateFraction(positionX: Float) {
                                        dragFraction = (positionX / trackWidthPx).coerceIn(0f, 1f)
                                }

                                awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        updateFraction(down.position.x)
                                        down.consume()

                                        var pressed: Boolean
                                        do {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull()
                                                if (change != null) {
                                                        updateFraction(change.position.x)
                                                        change.consume()
                                                }
                                                pressed = event.changes.any { it.pressed }
                                        } while (pressed)

                                        val target =
                                                ((dragFraction ?: activeFraction) * durationMs)
                                                        .roundToLong()
                                                        .coerceIn(0L, durationMs)
                                        dragFraction = null
                                        onSeek(target)
                                }
                        }

        Column(
                modifier =
                        modifier.fillMaxWidth()
                                .height(72.dp)
                                .background(Color.White.copy(alpha = 0.065f), RoundedCornerShape(8.dp))
                                .border(
                                        1.dp,
                                        accent.copy(alpha = if (durationMs > 0L) 0.24f else 0.1f),
                                        RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                )
                                Text(
                                        text = "Tempo",
                                        color = Color.White.copy(alpha = 0.68f),
                                        fontSize = 13.sp,
                                        fontFamily = DashboardReadableFont,
                                        fontWeight = FontWeight.Medium
                                )
                        }
                        Text(
                                text =
                                        "${formatMediaTime(elapsedMs)} / ${
                                                formatMediaTime(durationMs, unknownWhenZero = true)
                                        }",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = DashboardReadableFont,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                        )
                }
                Box(
                        modifier =
                                trackModifier.background(
                                        Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(50)
                                )
                ) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth(activeFraction)
                                                .fillMaxHeight()
                                                .background(accent, RoundedCornerShape(50))
                        )
                        if (durationMs > 0L) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth(
                                                                activeFraction.coerceIn(0.01f, 1f)
                                                        )
                                                        .fillMaxHeight(),
                                        contentAlignment = Alignment.CenterEnd
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.size(if (canSeek) 20.dp else 14.dp)
                                                                .background(
                                                                        Color.White,
                                                                        CircleShape
                                                                )
                                                                .border(
                                                                        2.dp,
                                                                        accent.copy(alpha = 0.86f),
                                                                        CircleShape
                                                                )
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun DashboardMediaBadge(isPlaying: Boolean, hasMetadata: Boolean) {
        Row(
                modifier =
                        Modifier.background(
                                        if (isPlaying) Color(0xFF78E08F).copy(alpha = 0.16f)
                                        else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(8.dp)
                                )
                                .border(
                                        1.dp,
                                        if (isPlaying) Color(0xFF78E08F).copy(alpha = 0.42f)
                                        else Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
                Icon(
                        if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (isPlaying) Color(0xFF78E08F) else Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(20.dp)
                )
                Text(
                        text =
                                when {
                                        isPlaying -> "PLAY"
                                        hasMetadata -> "MIDIA"
                                        else -> "AUDIO"
                                },
                        color = if (isPlaying) Color(0xFF78E08F) else Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        fontFamily = DashboardReadableFont,
                        fontWeight = FontWeight.Bold
                )
        }
}

@Composable
private fun DashboardArtworkFallback(
        appIcon: android.graphics.drawable.Drawable?,
        activeProjectionPackage: String?,
        modifier: Modifier = Modifier
) {
        Box(
                modifier =
                        modifier.background(
                                Brush.linearGradient(
                                        colors =
                                                listOf(
                                                        Color(0xFF1B2A31),
                                                        Color(0xFF11161A),
                                                        Color(0xFF2A2214)
                                                ),
                                        start = Offset.Zero,
                                        end = Offset(900f, 620f)
                                )
                        ),
                contentAlignment = Alignment.Center
        ) {
                Box(
                        modifier =
                                Modifier.size(168.dp)
                                        .background(
                                                Color.White.copy(alpha = 0.08f),
                                                RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.16f),
                                                RoundedCornerShape(8.dp)
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        if (appIcon != null) {
                                AsyncImage(
                                        model = appIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(104.dp)
                                )
                        } else {
                                Icon(
                                        if (activeProjectionPackage == BOTTOM_BAR_CARPLAY_PACKAGE)
                                                Icons.Default.DirectionsCar
                                        else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.78f),
                                        modifier = Modifier.size(82.dp)
                                )
                        }
                }
                Text(
                        text = shortProjectionLabel(activeProjectionPackage) ?: "IMPULSE AUDIO",
                        color = Color.White.copy(alpha = 0.1f),
                        fontSize = 34.sp,
                        fontFamily = DashboardReadableFont,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(18.dp),
                        maxLines = 1
                )
        }
}

@Composable
private fun DashboardHvacPanel(
        snapshot: DashboardVehicleSnapshot,
        serviceManager: ServiceManager,
        albumBackground: DashboardAlbumBackgroundState? = null,
        layoutControls: @Composable (() -> Unit)? = null,
        modifier: Modifier
) {
        val hvacEnabled = snapshot.hvacPower == "1"
        var blowerMode by remember { mutableStateOf(snapshot.blowerMode) }
        var driverSeatVentilation by remember {
                mutableStateOf(snapshot.driverSeatVentilation)
        }
        var passengerSeatVentilation by remember {
                mutableStateOf(snapshot.passengerSeatVentilation)
        }

        LaunchedEffect(snapshot.blowerMode) { blowerMode = snapshot.blowerMode }
        LaunchedEffect(snapshot.driverSeatVentilation) {
                driverSeatVentilation = snapshot.driverSeatVentilation
        }
        LaunchedEffect(snapshot.passengerSeatVentilation) {
                passengerSeatVentilation = snapshot.passengerSeatVentilation
        }

        DashboardPanel(modifier = modifier, albumBackground = albumBackground) {
                Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                DashboardPanelTitle(Icons.Default.AcUnit, "Climatização")
                                layoutControls?.invoke()
                        }
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                DashboardTempAdjuster(
                                        label = "Motorista",
                                        temp = snapshot.driverTemp,
                                        enabled = hvacEnabled,
                                        modifier = Modifier.weight(1f)
                                ) {
                                        updateTemperature(
                                                serviceManager,
                                                CarConstants.CAR_HVAC_DRIVER_TEMPERATURE,
                                                snapshot.driverTemp,
                                                it
                                        )
                                }
                                DashboardTempAdjuster(
                                        label = "Passageiro",
                                        temp = snapshot.passTemp,
                                        enabled = hvacEnabled,
                                        modifier = Modifier.weight(1f)
                                ) {
                                        updateTemperature(
                                                serviceManager,
                                                CarConstants.CAR_HVAC_PASS_TEMPERATURE,
                                                snapshot.passTemp,
                                                it
                                        )
                                }
                        }
                        DashboardFanAdjuster(
                                speed = snapshot.fanSpeed,
                                enabled = true,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                val next =
                                        (snapshot.fanSpeed.toIntOrNull() ?: 0).plus(it).coerceIn(0, 7)
                                serviceManager.updateData(
                                        CarConstants.CAR_HVAC_FAN_SPEED.getValue(),
                                        next.toString()
                                )
                                if (next == 0 && snapshot.hvacPower == "1") {
                                        serviceManager.updateData(
                                                CarConstants.CAR_HVAC_POWER_MODE.getValue(),
                                                "0"
                                        )
                                } else if (next > 0 && snapshot.hvacPower == "0") {
                                        serviceManager.updateData(
                                                CarConstants.CAR_HVAC_POWER_MODE.getValue(),
                                                "1"
                                        )
                                }
                        }
                        DashboardAirflowModeSelector(
                                mode = blowerMode,
                                enabled = hvacEnabled,
                                modifier = Modifier.fillMaxWidth()
                        ) { nextMode ->
                                blowerMode = nextMode
                                serviceManager.updateData(
                                        CarConstants.CAR_HVAC_BLOWER_MODE.getValue(),
                                        nextMode
                                )
                        }
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                                DashboardSeatVentilationButton(
                                        label = "Motorista",
                                        level = driverSeatVentilation,
                                        maxLevel = snapshot.seatVentilationMaxLevel,
                                        modifier = Modifier.weight(1f)
                                ) {
                                        val nextLevel =
                                                nextSeatVentilationLevel(
                                                        driverSeatVentilation,
                                                        snapshot.seatVentilationMaxLevel
                                                )
                                        driverSeatVentilation = nextLevel
                                        updateSeatVentilationLevel(
                                                serviceManager,
                                                CarConstants
                                                        .CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL,
                                                nextLevel
                                        )
                                }
                                DashboardSeatVentilationButton(
                                        label = "Passageiro",
                                        level = passengerSeatVentilation,
                                        maxLevel = snapshot.seatVentilationMaxLevel,
                                        modifier = Modifier.weight(1f)
                                ) {
                                        val nextLevel =
                                                nextSeatVentilationLevel(
                                                        passengerSeatVentilation,
                                                        snapshot.seatVentilationMaxLevel
                                                )
                                        passengerSeatVentilation = nextLevel
                                        updateSeatVentilationLevel(
                                                serviceManager,
                                                CarConstants
                                                        .CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL,
                                                nextLevel
                                        )
                                }
                        }
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                                DashboardToggleButton(
                                        label = "AC",
                                        icon = Icons.Default.PowerSettingsNew,
                                        active = hvacEnabled,
                                        modifier = Modifier.weight(1f)
                                ) {
                                        serviceManager.updateData(
                                                CarConstants.CAR_HVAC_POWER_MODE.getValue(),
                                                if (hvacEnabled) "0" else "1"
                                        )
                                }
                                DashboardToggleButton(
                                        label = "Auto",
                                        icon = Icons.Default.AutoMode,
                                        active = snapshot.acAuto == "1",
                                        enabled = hvacEnabled,
                                        modifier = Modifier.weight(1f)
                                ) {
                                        serviceManager.updateData(
                                                CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(),
                                                if (snapshot.acAuto == "1") "0" else "1"
                                        )
                                }
                                DashboardToggleButton(
                                        label = "Sync",
                                        icon = Icons.Default.Sync,
                                        active = snapshot.acSync == "1",
                                        enabled = hvacEnabled,
                                        modifier = Modifier.weight(1f)
                                ) {
                                        serviceManager.updateData(
                                                CarConstants.CAR_HVAC_SYNC_ENABLE.getValue(),
                                                if (snapshot.acSync == "1") "0" else "1"
                                        )
                                }
                                DashboardToggleButton(
                                        label = "Recirc",
                                        icon = Icons.Default.Autorenew,
                                        active = snapshot.acRecirc == "1",
                                        enabled = hvacEnabled,
                                        modifier = Modifier.weight(1f)
                                ) {
                                        val next = if (snapshot.acRecirc == "1") "0" else "1"
                                        val carValue = if (next == "0") "1" else "0"
                                        serviceManager.updateData(
                                                CarConstants.CAR_HVAC_CYCLE_MODE.getValue(),
                                                carValue
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun DashboardPanel(
        modifier: Modifier = Modifier,
        albumBackground: DashboardAlbumBackgroundState? = null,
        content: @Composable BoxScope.() -> Unit
) {
        val shape = RoundedCornerShape(8.dp)
        Box(
                modifier =
                        modifier.clip(shape)
                                .background(
                                        Brush.linearGradient(
                                                colors =
                                                        listOf(
                                                                Color(0xFF171D22).copy(alpha = 0.86f),
                                                                Color(0xFF0E1115).copy(alpha = 0.9f)
                                                        )
                                        ),
                                        shape
                                )
                                .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.12f),
                                        shape
                                )
        ) {
                if (albumBackground != null) {
                        DashboardAlbumDynamicBackground(
                                primary = albumBackground.primary,
                                secondary = albumBackground.secondary,
                                accent = albumBackground.accent,
                                dark = albumBackground.dark,
                                hasArtwork = albumBackground.hasArtwork,
                                modifier = Modifier.matchParentSize(),
                                cornerRadius = 8.dp,
                                artworkAlpha = 0.86f,
                                fallbackAlpha = 0.34f,
                                artworkScrimAlpha = 0.58f,
                                fallbackScrimAlpha = 0.72f
                        )
                }
                Box(
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                        content = content
                )
        }
}

@Composable
private fun DashboardPanelTitle(icon: ImageVector, title: String, compact: Boolean = false) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Icon(
                        icon,
                        contentDescription = null,
                        tint = Color(0xFF66E3FF),
                        modifier = Modifier.size(if (compact) 22.dp else 26.dp)
                )
                Text(
                        text = title,
                        color = Color.White,
                        fontSize = if (compact) 15.sp else 18.sp,
                        fontFamily = DashboardReadableFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
        }
}

@Composable
private fun DashboardStatusChip(icon: ImageVector, text: String) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                        Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF66E3FF), modifier = Modifier.size(20.dp))
                Text(text = text, color = Color.White, fontSize = 14.sp, fontFamily = DashboardReadableFont)
        }
}

@Composable
private fun DashboardDynamicsReadyBadge(readyState: String) {
        val ready = readyState == "1" || readyState.equals("true", ignoreCase = true)
        val accent = if (ready) Color(0xFF78E08F) else Color.White.copy(alpha = 0.72f)
        Row(
                modifier =
                        Modifier.height(34.dp)
                                .background(accent.copy(alpha = if (ready) 0.15f else 0.08f), RoundedCornerShape(8.dp))
                                .border(1.dp, accent.copy(alpha = if (ready) 0.38f else 0.14f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
                Box(
                        modifier = Modifier.size(8.dp).background(accent, CircleShape)
                )
                Text(
                        text = if (ready) "READY" else "STBY",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = DashboardReadableFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                )
        }
}

@Composable
private fun DashboardCircularResourceGauge(
        label: String,
        value: String,
        fraction: Float,
        detail: String,
        accent: Color,
        icon: ImageVector,
        modifier: Modifier = Modifier
) {
        Row(
                modifier =
                        modifier.fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(8.dp))
                                .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
                Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 8.dp.toPx()
                                val diameter = size.minDimension - strokeWidth
                                val topLeft =
                                        Offset(
                                                (size.width - diameter) / 2f,
                                                (size.height - diameter) / 2f
                                        )
                                val arcSize = Size(diameter, diameter)
                                drawArc(
                                        color = Color.White.copy(alpha = 0.10f),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                drawArc(
                                        color = accent,
                                        startAngle = -90f,
                                        sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                        text = value,
                                        color = Color.White,
                                        fontSize = 23.sp,
                                        fontFamily = DashboardReadableFont,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                )
                                Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = accent.copy(alpha = 0.88f),
                                        modifier = Modifier.size(18.dp)
                                )
                        }
                }
                Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                        Text(
                                text = label,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontFamily = DashboardReadableFont,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Text(
                                text = detail,
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 13.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(4.dp)
                                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        ) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                                                        .fillMaxHeight()
                                                        .background(accent.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                                )
                        }
                }
        }
}

@Composable
private fun DashboardCompactStatusTile(
        label: String,
        value: String,
        accent: Color,
        icon: ImageVector,
        modifier: Modifier = Modifier
) {
        Column(
                modifier =
                        modifier.height(74.dp)
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.SpaceBetween
        ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.58f),
                                fontSize = 9.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
                Text(
                        text = value,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = DashboardReadableFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
        }
}

@Composable
private fun DashboardStatPill(
        label: String,
        value: String,
        icon: ImageVector,
        modifier: Modifier = Modifier,
        accent: Color = Color(0xFF66E3FF)
) {
        Row(
                modifier =
                        modifier.background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                                .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Column {
                        Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.54f),
                                fontSize = 11.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1
                        )
                        Text(
                                text = value,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
        }
}

@Composable
private fun DashboardReadinessStrip(readyState: String) {
        val ready = readyState == "1" || readyState.equals("true", ignoreCase = true)
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(46.dp)
                                .background(
                                        if (ready) Color(0xFF78E08F).copy(alpha = 0.13f)
                                        else Color.White.copy(alpha = 0.07f),
                                        RoundedCornerShape(8.dp)
                                )
                                .border(
                                        1.dp,
                                        if (ready) Color(0xFF78E08F).copy(alpha = 0.4f)
                                        else Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Text(
                        text = if (ready) "READY" else "STANDBY",
                        color = if (ready) Color(0xFF78E08F) else Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontFamily = DashboardReadableFont,
                        fontWeight = FontWeight.Bold
                )
                Text(
                        text = "Display 0",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontFamily = DashboardReadableFont
                )
        }
}

@Composable
private fun DashboardLinearMeter(
        label: String,
        value: String,
        fraction: Float,
        accent: Color,
        icon: ImageVector,
        modifier: Modifier = Modifier
) {
        Column(
                        modifier =
                                modifier.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                                Text(
                                        text = label,
                                        color = Color.White.copy(alpha = 0.58f),
                                        fontSize = 12.sp,
                                        fontFamily = DashboardReadableFont
                                )
                        }
                        Text(text = value, color = Color.White, fontSize = 15.sp, fontFamily = DashboardReadableFont)
                }
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(9.dp)
                                        .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(4.dp))
                ) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                                                .fillMaxHeight()
                                                .background(accent, RoundedCornerShape(4.dp))
                        )
                }
        }
}

@Composable
private fun DashboardMetricTile(
        label: String,
        value: String,
        icon: ImageVector,
        modifier: Modifier = Modifier
) {
        Row(
                modifier =
                        modifier.background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF66E3FF), modifier = Modifier.size(19.dp))
                Column {
                        Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.52f),
                                fontSize = 9.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1
                        )
                        Text(
                                text = value,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
        }
}

@Composable
private fun DashboardQuickCycleControl(
        label: String,
        value: String,
        icon: ImageVector,
        accent: Color,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
) {
        Surface(
                onClick = onClick,
                modifier = modifier.fillMaxWidth(),
                color = accent.copy(alpha = 0.13f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
        ) {
                Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                        Box(
                                modifier =
                                        Modifier.size(58.dp)
                                                .background(
                                                        accent.copy(alpha = 0.18f),
                                                        RoundedCornerShape(8.dp)
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(30.dp)
                                )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = label,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        fontFamily = DashboardReadableFont,
                                        maxLines = 1
                                )
                                Text(
                                        text = value,
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontFamily = DashboardReadableFont,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                }
        }
}

@Composable
private fun HevModeDialog(
        snapshot: DashboardVehicleSnapshot,
        serviceManager: ServiceManager,
        onDismiss: () -> Unit
) {
        val reserve = snapshot.powerReserve
        val pct = snapshot.socTarget.trim().toIntOrNull()?.coerceIn(20, 80) ?: 50
        var dragging by remember { mutableStateOf(false) }
        var sliderPos by remember { mutableFloatStateOf(pct.toFloat()) }
        LaunchedEffect(pct) { if (!dragging) sliderPos = pct.toFloat() }
        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = Color(0xFF161B24),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                confirmButton = {
                        TextButton(onClick = onDismiss) {
                                Text("Fechar", color = Color(0xFF78E08F))
                        }
                },
                title = { Text("Modo HEV") },
                text = {
                        Column {
                                SettingsCategoryRow(
                                        "Reserva de bateria",
                                        reserve,
                                        listOf("1" to "Inteligente", "2" to "Prioritário")
                                ) { newVal ->
                                        serviceManager.updateData(
                                                CarConstants.CAR_EV_SETTING_POWER_RESERVE_CONFIG
                                                        .getValue(),
                                                newVal
                                        )
                                }
                                if (reserve.trim() == "2") {
                                        Spacer(Modifier.height(14.dp))
                                        Text(
                                                text = "Bateria a manter: ${sliderPos.toInt()}%",
                                                style =
                                                        labelStyle.copy(
                                                                fontWeight = FontWeight.Bold
                                                        )
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Slider(
                                                value = sliderPos,
                                                onValueChange = { v ->
                                                        dragging = true
                                                        sliderPos = v
                                                },
                                                onValueChangeFinished = {
                                                        dragging = false
                                                        serviceManager.setHevSocTargetValue(
                                                                sliderPos.toInt()
                                                        )
                                                },
                                                valueRange = 20f..80f,
                                                steps = 11
                                        )
                                }
                        }
                }
        )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardPremiumCycleControl(
        label: String,
        value: String,
        nextValue: String,
        icon: ImageVector,
        accent: Color,
        modifier: Modifier = Modifier,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit
) {
        Surface(
                modifier =
                        modifier.fillMaxHeight()
                                .combinedClickable(
                                        onClick = onClick,
                                        onLongClick = onLongClick
                                ),
                color = Color.White.copy(alpha = 0.052f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
                                                                        accent.copy(alpha = 0.13f),
                                                                        Color.Transparent,
                                                                        Color.Black.copy(alpha = 0.10f)
                                                                ),
                                                        start = Offset.Zero,
                                                        end = Offset(420f, 180f)
                                                )
                                        )
                                        .padding(14.dp)
                ) {
                        Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(42.dp)
                                                        .background(
                                                                accent.copy(alpha = 0.18f),
                                                                RoundedCornerShape(8.dp)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                icon,
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                                text = label,
                                                color = Color.White.copy(alpha = 0.62f),
                                                fontSize = 12.sp,
                                                fontFamily = DashboardReadableFont,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                                text = value,
                                                color = Color.White,
                                                fontSize = 24.sp,
                                                fontFamily = DashboardReadableFont,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                                text = "Prox. $nextValue",
                                                color = accent.copy(alpha = 0.86f),
                                                fontSize = 11.sp,
                                                fontFamily = DashboardReadableFont,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun DashboardOptionGroup(
        label: String,
        currentValue: String,
        options: List<Pair<String, String>>,
        columns: Int,
        modifier: Modifier = Modifier,
        onSelect: (String) -> Unit
) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                        text = label,
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 10.sp,
                        fontFamily = DashboardReadableFont
                )
                options.chunked(columns).forEach { rowOptions ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowOptions.forEach { (value, text) ->
                                        val selected = currentValue == value
                                        Surface(
                                                onClick = { onSelect(value) },
                                                modifier = Modifier.weight(1f).height(34.dp),
                                                color =
                                                        if (selected)
                                                                Color(0xFF66E3FF).copy(alpha = 0.2f)
                                                        else Color.White.copy(alpha = 0.06f),
                                                shape = RoundedCornerShape(8.dp),
                                                border =
                                                        BorderStroke(
                                                                1.dp,
                                                                if (selected) Color(0xFF66E3FF)
                                                                else Color.White.copy(alpha = 0.08f)
                                                        )
                                        ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                                text = text,
                                                                color =
                                                                        if (selected)
                                                                                Color(0xFF66E3FF)
                                                                        else Color.White,
                                                                fontSize = 10.sp,
                                                                fontFamily = DashboardReadableFont,
                                                                textAlign = TextAlign.Center,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                }
                                        }
                                }
                                repeat(columns - rowOptions.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                }
                        }
                }
        }
}

@Composable
private fun DashboardIconButton(
        icon: ImageVector,
        size: Dp = 58.dp,
        contentDescription: String? = null,
        onClick: () -> Unit
) {
        Surface(
                onClick = onClick,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.size(size)
        ) {
                Box(contentAlignment = Alignment.Center) {
                        Icon(
                                icon,
                                contentDescription = contentDescription,
                                tint = Color.White,
                                modifier = Modifier.size((size.value * 0.48f).dp)
                        )
                }
        }
}

@Composable
private fun DashboardTempAdjuster(
        label: String,
        temp: String,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        onDelta: (Float) -> Unit
) {
        Row(
                modifier =
                        modifier.alpha(if (enabled) 1f else 0.45f)
                                .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(8.dp))
                                .height(92.dp)
                                .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                DashboardIconButton(Icons.Default.Remove, size = 62.dp) {
                        if (enabled) onDelta(-0.5f)
                        else logDashboardTemperatureDisabled(label, temp, -0.5f)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.58f),
                                fontSize = 12.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1
                        )
                        Text(
                                text = if (enabled) formatTemperature(temp) else "--",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1
                        )
                }
                DashboardIconButton(Icons.Default.Add, size = 62.dp) {
                        if (enabled) onDelta(0.5f)
                        else logDashboardTemperatureDisabled(label, temp, 0.5f)
                }
        }
}

@Composable
private fun DashboardFanAdjuster(
        speed: String,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        onDelta: (Int) -> Unit
) {
        Row(
                modifier =
                        modifier.alpha(if (enabled) 1f else 0.45f)
                                .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(8.dp))
                                .height(84.dp)
                                .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                DashboardIconButton(Icons.Default.Remove, size = 62.dp) { if (enabled) onDelta(-1) }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                                text = "Ventilação",
                                color = Color.White.copy(alpha = 0.58f),
                                fontSize = 12.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1
                        )
                        Text(
                                text = speed.toIntOrNull()?.toString() ?: "--",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1
                        )
                }
                DashboardIconButton(Icons.Default.Add, size = 62.dp) { if (enabled) onDelta(1) }
        }
}

private data class DashboardAirflowModeOption(
        val value: String,
        val label: String,
        val iconRes: Int
)

private val DashboardAirflowModeOptions =
        listOf(
                DashboardAirflowModeOption("2", "Pés", R.drawable.ic_hvac_blower_feet),
                DashboardAirflowModeOption("1", "Rosto/Pés", R.drawable.ic_hvac_blower_feet_and_face),
                DashboardAirflowModeOption("0", "Rosto", R.drawable.ic_hvac_blower_face),
                DashboardAirflowModeOption("3", "Vidro/Pés", R.drawable.ic_hvac_blower_feet_and_defrost)
        )

@Composable
private fun DashboardAirflowModeSelector(
        mode: String,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        onSelect: (String) -> Unit
) {
        Row(
                modifier = modifier.alpha(if (enabled) 1f else 0.45f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                DashboardAirflowModeOptions.forEach { option ->
                        val active = mode == option.value
                        Surface(
                                onClick = { onSelect(option.value) },
                                enabled = enabled,
                                modifier = Modifier.weight(1f).height(82.dp),
                                color =
                                        if (active && enabled) Color(0xFF66E3FF).copy(alpha = 0.16f)
                                        else Color.White.copy(alpha = 0.055f),
                                shape = RoundedCornerShape(8.dp),
                                border =
                                        BorderStroke(
                                                1.dp,
                                                if (active && enabled)
                                                        Color(0xFF66E3FF).copy(alpha = 0.55f)
                                                else Color.White.copy(alpha = 0.08f)
                                        )
                        ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                        Icon(
                                                painter = painterResource(option.iconRes),
                                                contentDescription = null,
                                                tint =
                                                        if (active && enabled) Color(0xFF66E3FF)
                                                        else Color.White.copy(
                                                                alpha = if (enabled) 0.82f else 0.35f
                                                        ),
                                                modifier = Modifier.size(46.dp)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                                text = option.label,
                                                color =
                                                        if (active && enabled) Color(0xFF66E3FF)
                                                        else Color.White.copy(
                                                                alpha = if (enabled) 0.76f else 0.35f
                                                        ),
                                                fontSize = 11.sp,
                                                fontFamily = DashboardReadableFont,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun DashboardSeatVentilationButton(
        label: String,
        level: String,
        maxLevel: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
) {
        val parsedLevel = parseSeatVentilationLevel(level, maxLevel)
        val active = parsedLevel > 0
        Surface(
                onClick = onClick,
                modifier = modifier.height(82.dp),
                color =
                        if (active) Color(0xFF78E08F).copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.055f),
                shape = RoundedCornerShape(8.dp),
                border =
                        BorderStroke(
                                1.dp,
                                if (active) Color(0xFF78E08F).copy(alpha = 0.42f)
                                else Color.White.copy(alpha = 0.1f)
                        )
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Icon(
                                Icons.Default.EventSeat,
                                contentDescription = null,
                                tint =
                                        if (active) Color(0xFF78E08F)
                                        else Color.White.copy(alpha = 0.62f),
                                modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = label,
                                        color = Color.White.copy(alpha = 0.58f),
                                        fontSize = 13.sp,
                                        fontFamily = DashboardReadableFont,
                                        maxLines = 1
                                )
                                Text(
                                        text = if (active) "Nível $parsedLevel" else "Desligado",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontFamily = DashboardReadableFont,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                        DashboardSeatVentilationLevelIndicator(
                                level = parsedLevel,
                                maxLevel = parseSeatVentilationMaxLevel(maxLevel),
                                active = active
                        )
                }
        }
}

@Composable
private fun DashboardSeatVentilationLevelIndicator(level: Int, maxLevel: Int, active: Boolean) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                repeat(maxLevel.coerceIn(1, 3)) { index ->
                        val step = index + 1
                        val isFilled = active && step <= level
                        Box(
                                modifier =
                                        Modifier.width(5.dp)
                                                .height((10 + index * 5).dp)
                                                .background(
                                                        if (isFilled) Color(0xFF78E08F)
                                                        else Color.White.copy(alpha = 0.16f),
                                                        RoundedCornerShape(99.dp)
                                                )
                        )
                }
        }
}

@Composable
private fun DashboardToggleButton(
        label: String,
        icon: ImageVector,
        active: Boolean,
        enabled: Boolean = true,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
) {
        Surface(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier.height(82.dp),
                color =
                        if (active && enabled) Color(0xFF66E3FF).copy(alpha = 0.16f)
                        else Color.White.copy(alpha = 0.055f),
                shape = RoundedCornerShape(8.dp),
                border =
                        BorderStroke(
                                1.dp,
                                if (active && enabled) Color(0xFF66E3FF).copy(alpha = 0.55f)
                                else Color.White.copy(alpha = 0.08f)
                        )
        ) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        Icon(
                                icon,
                                contentDescription = null,
                                tint =
                                        if (active && enabled) Color(0xFF66E3FF)
                                        else Color.White.copy(alpha = if (enabled) 0.82f else 0.35f),
                                modifier = Modifier.size(31.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                                text = label,
                                color =
                                        if (active && enabled) Color(0xFF66E3FF)
                                        else Color.White.copy(alpha = if (enabled) 0.76f else 0.35f),
                                fontSize = 12.sp,
                                fontFamily = DashboardReadableFont,
                                maxLines = 1
                        )
                }
        }
}

@Composable
private fun DashboardTinyReadout(label: String, value: String, modifier: Modifier = Modifier) {
        Row(
                modifier =
                        modifier.fillMaxWidth()
                                .height(40.dp)
                                .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                        text = label,
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 10.sp,
                        fontFamily = DashboardReadableFont
                )
                Text(text = value, color = Color.White, fontSize = 12.sp, fontFamily = DashboardReadableFont)
        }
}

private fun updateTemperature(
        serviceManager: ServiceManager,
        key: CarConstants,
        currentValue: String,
        delta: Float
) {
        val current = currentValue.toFloatOrNull() ?: 22.0f
        val next = (current + delta).coerceIn(16.0f, 32.0f)
        val nextValue = String.format(java.util.Locale.US, "%.1f", next)
        ClusterPersistentEventLogger.log(
                "dashboard_hvac_temperature_command",
                mapOf(
                        "key" to key.getValue(),
                        "current" to currentValue,
                        "delta" to delta,
                        "next" to nextValue
                )
        )
        serviceManager.updateData(key.getValue(), nextValue)
}

private fun logDashboardTemperatureDisabled(label: String, temp: String, delta: Float) {
        ClusterPersistentEventLogger.log(
                "dashboard_hvac_temperature_disabled",
                mapOf(
                        "label" to label,
                        "temp" to temp,
                        "delta" to delta,
                        "reason" to "hvac_power_off"
                )
        )
}

internal fun resolveDashboardMediaVolumeAfterDelta(current: Int, delta: Int): Int {
        return (current + delta).coerceIn(DASHBOARD_MEDIA_VOLUME_MIN, DASHBOARD_MEDIA_VOLUME_MAX)
}

private fun adjustDashboardSystemMediaVolume(context: Context, delta: Int) {
        val direction =
                if (delta > 0) {
                        AudioManager.ADJUST_RAISE
                } else {
                        AudioManager.ADJUST_LOWER
                }
        try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        } catch (e: Exception) {
                Log.w(BOTTOM_BAR_TAG, "Unable to adjust system media volume", e)
        }
}

private fun updateSeatVentilationLevel(
        serviceManager: ServiceManager,
        key: CarConstants,
        nextLevel: String
) {
        serviceManager.updateDataAsync(key.getValue(), nextLevel)
}

private fun nextSeatVentilationLevel(currentLevel: String, maxLevel: String): String {
        val max = parseSeatVentilationMaxLevel(maxLevel).coerceAtMost(3)
        val current = parseSeatVentilationLevel(currentLevel, max.toString())
        return if (current >= max) "0" else (current + 1).toString()
}

private fun parseSeatVentilationLevel(value: String, maxLevel: String): Int {
        val max = parseSeatVentilationMaxLevel(maxLevel)
        return value.toIntOrNull()?.coerceIn(0, max) ?: 0
}

private fun parseSeatVentilationMaxLevel(value: String): Int {
        return value.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(5) ?: 3
}

private fun formatMediaTime(valueMs: Long, unknownWhenZero: Boolean = false): String {
        if (unknownWhenZero && valueMs <= 0L) return "--:--"
        val totalSeconds = (valueMs.coerceAtLeast(0L) / 1000L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatDashboardClock(): String {
        return java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
}

private fun projectionLabel(packageName: String?): String {
        return when (packageName) {
                BOTTOM_BAR_CARPLAY_PACKAGE -> "Apple CarPlay ativo no cluster"
                BOTTOM_BAR_ANDROID_AUTO_PACKAGE -> "Android Auto ativo no cluster"
                null -> "Dashboard do display 0"
                else -> "Projeção ativa"
        }
}

private fun shortProjectionLabel(packageName: String?): String? {
        return when (packageName) {
                BOTTOM_BAR_CARPLAY_PACKAGE -> "Apple CarPlay"
                BOTTOM_BAR_ANDROID_AUTO_PACKAGE -> "Android Auto"
                null -> null
                else -> "Projecao"
        }
}

private fun driveModeLabel(value: String): String {
        return when (value) {
                "2" -> "Eco"
                "1" -> "Sport"
                "3" -> "Neve"
                "4" -> "Areia"
                "5" -> "Lama"
                else -> "Normal"
        }
}

private fun powerModelLabel(
        value: String,
        reserve: String = "1",
        socTarget: String = "50"
): String {
        return when (value) {
                "1" -> "EV Prior."
                "3" -> "EV"
                else -> {
                        // HEV: mostra o sub-modo; se Prioritário, anexa o % de bateria alvo.
                        if (reserve.trim() == "2") {
                                val pct = socTarget.trim().toIntOrNull()?.coerceIn(20, 80) ?: 50
                                "HEV Prior. $pct%"
                        } else {
                                "HEV Intel."
                        }
                }
        }
}

private fun regenLabel(value: String): String {
        return when (value) {
                "2" -> "Baixo"
                "1" -> "Alto"
                else -> "Normal"
        }
}

private fun nextDashboardOption(currentValue: String, options: List<Pair<String, String>>): String {
        val currentIndex = options.indexOfFirst { it.first == currentValue }
        return options[(currentIndex + 1).coerceAtLeast(0) % options.size].first
}

private fun nextDashboardOptionLabel(
        currentValue: String,
        options: List<Pair<String, String>>
): String {
        val nextValue = nextDashboardOption(currentValue, options)
        return options.firstOrNull { it.first == nextValue }?.second ?: nextValue
}

private fun steeringModeLabel(value: String): String {
        return when (value) {
                "2" -> "Conforto"
                "1" -> "Sport"
                else -> "Normal"
        }
}

private fun formatGear(value: String): String {
        return when (value.toIntOrNull()) {
                2 -> "D"
                3 -> "P"
                4 -> "R"
                else -> "N"
        }
}

private fun formatSpeed(value: String): String {
        return value.toFloatOrNull()?.roundToInt()?.toString() ?: "--"
}

private fun formatTemperature(value: String): String {
        val parsed = value.toFloatOrNull() ?: return "--"
        if (parsed <= -40f || parsed >= 85f || parsed == -1f || parsed == 255f) return "--"
        return String.format(java.util.Locale.US, "%.1f°C", parsed)
}

private fun formatPercent(value: String): String {
        return value.toFloatOrNull()?.roundToInt()?.coerceIn(0, 100)?.let { "$it%" } ?: "--"
}

private fun percentFraction(value: String): Float {
        return ((value.toFloatOrNull() ?: 0f) / 100f).coerceIn(0f, 1f)
}

private fun formatDistance(value: String): String {
        return value.toFloatOrNull()?.roundToInt()?.let { "$it km" } ?: "--"
}

private fun formatConsumption(value: String, suffix: String): String {
        val parsed = value.toFloatOrNull() ?: return "--"
        if (parsed <= 0f) return "--"
        return String.format(java.util.Locale.US, "%.1f %s", parsed, suffix)
}

private fun calculateEvPowerKw(voltage: String, current: String): String {
        val volts = voltage.toFloatOrNull() ?: return "--"
        val amps = current.toFloatOrNull() ?: return "--"
        val kw = volts * amps / 1000f
        val label = if (kw < -0.5f) "REGEN" else "EV"
        val displayKw = if (kw < -0.5f) -kw else kw
        return String.format(java.util.Locale.US, "%s %.1f kW", label, displayKw)
}

@Composable
fun SettingsCategoryRow(
        label: String,
        currentValue: String,
        options: List<Pair<String, String>>,
        columns: Int = 0,
        onSelect: (String) -> Unit
) {
        Column {
                Text(text = label, style = labelStyle.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                if (columns > 0) {
                        val rows = (options.size + columns - 1) / columns
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (r in 0 until rows) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                for (c in 0 until columns) {
                                                        val index = r * columns + c
                                                        if (index < options.size) {
                                                                val (valKey, valLabel) =
                                                                        options[index]
                                                                val isSelected =
                                                                        currentValue == valKey
                                                                Surface(
                                                                        onClick = {
                                                                                onSelect(valKey)
                                                                        },
                                                                        modifier =
                                                                                Modifier.weight(1f),
                                                                        color =
                                                                                if (isSelected)
                                                                                        Color(
                                                                                                        0xFF2196F3
                                                                                                )
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.2f
                                                                                                )
                                                                                else
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.05f
                                                                                                ),
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                ),
                                                                        border =
                                                                                BorderStroke(
                                                                                        width =
                                                                                                1.dp,
                                                                                        color =
                                                                                                if (isSelected
                                                                                                )
                                                                                                        Color(
                                                                                                                0xFF2196F3
                                                                                                        )
                                                                                                else
                                                                                                        Color.Transparent
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                text = valLabel,
                                                                                color =
                                                                                        if (isSelected
                                                                                        )
                                                                                                Color(
                                                                                                        0xFF2196F3
                                                                                                )
                                                                                        else
                                                                                                Color.White,
                                                                                fontSize = 12.sp,
                                                                                fontWeight =
                                                                                        if (isSelected
                                                                                        )
                                                                                                FontWeight
                                                                                                        .Bold
                                                                                        else
                                                                                                FontWeight
                                                                                                        .Normal,
                                                                                fontFamily =
                                                                                        Michroma,
                                                                                textAlign =
                                                                                        TextAlign
                                                                                                .Center,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                vertical =
                                                                                                        16.dp
                                                                                        )
                                                                        )
                                                                }
                                                        } else {
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                } else {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                options.forEach { (valKey, valLabel) ->
                                        val isSelected = currentValue == valKey
                                        Surface(
                                                onClick = { onSelect(valKey) },
                                                modifier = Modifier.weight(1f),
                                                color =
                                                        if (isSelected)
                                                                Color(0xFF2196F3).copy(alpha = 0.2f)
                                                        else Color.White.copy(alpha = 0.05f),
                                                shape = RoundedCornerShape(8.dp),
                                                border =
                                                        BorderStroke(
                                                                width = 1.dp,
                                                                color =
                                                                        if (isSelected)
                                                                                Color(0xFF2196F3)
                                                                        else Color.Transparent
                                                        )
                                        ) {
                                                Text(
                                                        text = valLabel,
                                                        color =
                                                                if (isSelected) Color(0xFF2196F3)
                                                                else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight =
                                                                if (isSelected) FontWeight.Bold
                                                                else FontWeight.Normal,
                                                        fontFamily = Michroma,
                                                        textAlign = TextAlign.Center,
                                                        modifier =
                                                                Modifier.padding(vertical = 16.dp)
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun ClassicSmallButton(
        icon: ImageVector,
        enabled: Boolean = true,
        iconSize: androidx.compose.ui.unit.Dp = 20.dp,
        onClick: () -> Unit
) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val glowColor = Color(0xFF2196F3).copy(alpha = 0.35f)
        val animatedColor by animateColorAsState(
                targetValue = if (isPressed) glowColor else Color.Transparent,
                animationSpec = tween(durationMillis = if (isPressed) 50 else 300)
        )

        Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                        .size(70.dp, 60.dp)
                        .background(Color.Black.copy(alpha = 0.95f))
                        .clickable(
                                enabled = enabled,
                                interactionSource = interactionSource,
                                indication = null
                        ) { onClick() }
        ) {
                val alpha = if (enabled) 1f else 0.4f
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .fillMaxSize()
                                .alpha(alpha)
                ) {
                        Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(animatedColor)
                        ) {
                                Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(iconSize)
                                )
                        }
                }
        }
}

@Composable
fun ClassicTempControlSection(
        label: String,
        temp: String,
        isEnabled: Boolean,
        sliderType: BottomBarState.SliderType? = null,
        onValueChange: (Float) -> Unit
) {
        val floatTemp = temp.toFloatOrNull() ?: -200f
        val isAbnormal = floatTemp >= 85f || floatTemp <= -40f || floatTemp == -1f || temp == "--"
        val isTempValid = isEnabled && !isAbnormal
        val alpha = if (isTempValid) 1f else 0.4f
        var centerX by remember { mutableFloatStateOf(0f) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(alpha)) {
                ClassicSmallButton(Icons.Default.Remove, isTempValid) { onValueChange(-0.5f) }
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                                .width(120.dp)
                                .onGloballyPositioned { coordinates ->
                                        centerX = coordinates.positionInRoot().x + coordinates.size.width / 2f
                                }
                                .clickable(
                                        enabled = isTempValid && sliderType != null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                ) {
                                        if (sliderType != null) {
                                                BottomBarState.sliderPositionX = centerX
                                                BottomBarState.activeSliderType = sliderType
                                        }
                                }
                ) {
                        Text(text = label, style = labelStyle)
                        val displayTemp = if (!isTempValid) "--" else temp
                        val tempColor = if (floatTemp > 30f) Color.Red else Color.White
                        Text(
                                text =
                                        buildAnnotatedString {
                                                withStyle(style = SpanStyle(color = tempColor)) {
                                                        append(displayTemp)
                                                }
                                                if (displayTemp != "--") append("°C")
                                        },
                                style = commonTextStyle.copy(fontSize = 18.sp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                        )
                }
                ClassicSmallButton(Icons.Default.Add, isTempValid) { onValueChange(0.5f) }
        }
}

@Composable
fun ClassicFanControlSection(
        speed: Int,
        isEnabled: Boolean,
        sliderType: BottomBarState.SliderType? = null,
        onValueChange: (Int) -> Unit
) {
        val alpha = if (isEnabled) 1f else 0.4f
        var centerX by remember { mutableFloatStateOf(0f) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(alpha)) {
                ClassicSmallButton(Icons.Default.Remove, isEnabled) { onValueChange(-1) }
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                                .width(120.dp)
                                .onGloballyPositioned { coordinates ->
                                        centerX = coordinates.positionInRoot().x + coordinates.size.width / 2f
                                }
                                .clickable(
                                        enabled = isEnabled && sliderType != null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                ) {
                                        if (sliderType != null) {
                                                BottomBarState.sliderPositionX = centerX
                                                BottomBarState.activeSliderType = sliderType
                                        }
                                }
                ) {
                        Text(text = "Ventilação", style = labelStyle.copy(fontSize = 10.sp))
                        Text(
                                text = speed.toString(),
                                style = commonTextStyle.copy(fontSize = 18.sp)
                        )
                }
                ClassicSmallButton(Icons.Default.Add, isEnabled) { onValueChange(1) }
        }
}

@Composable
fun ClassicVolumeControlSection(
        label: String,
        volume: Int,
        sliderType: BottomBarState.SliderType? = null,
        onValueChange: (Int) -> Unit
) {
        var centerX by remember { mutableFloatStateOf(0f) }
        Row(verticalAlignment = Alignment.CenterVertically) {
                ClassicSmallButton(Icons.Default.Remove) { onValueChange(-1) }
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                                .width(60.dp)
                                .onGloballyPositioned { coordinates ->
                                        centerX = coordinates.positionInRoot().x + coordinates.size.width / 2f
                                }
                                .clickable(
                                        enabled = sliderType != null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                ) {
                                        if (sliderType != null) {
                                                BottomBarState.sliderPositionX = centerX
                                                BottomBarState.activeSliderType = sliderType
                                        }
                                }
                ) {
                        Text(text = label, style = labelStyle)
                        Text(
                                text = volume.toString(),
                                style = commonTextStyle,
                                modifier = Modifier.padding(horizontal = 4.dp)
                        )
                }
                ClassicSmallButton(Icons.Default.Add) { onValueChange(1) }
        }
}

@Composable
fun ClassicControlsSection(scope: CoroutineScope) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                val voltarInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val voltarPressed by voltarInteraction.collectIsPressedAsState()
                val voltarColor by animateColorAsState(
                        targetValue = if (voltarPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                        animationSpec = tween(durationMillis = if (voltarPressed) 50 else 300)
                )
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                                .width(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(voltarColor)
                                .clickable(
                                        interactionSource = voltarInteraction,
                                        indication = null
                                ) {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                ShizukuUtils.runCommandAndGetOutput(
                                                        arrayOf("input", "keyevent", "4")
                                                )
                                        }
                                }
                                .padding(vertical = 6.dp)
                ) {
                        Text(
                                text = "Voltar",
                                style = labelStyle.copy(fontSize = 10.sp, color = Color.White)
                        )
                        Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                        )
                }

                val showSettings = BottomBarState.isSettingsMenuExpanded
                val conducaoInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val conducaoPressed by conducaoInteraction.collectIsPressedAsState()
                val conducaoColor by animateColorAsState(
                        targetValue = if (conducaoPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                        animationSpec = tween(durationMillis = if (conducaoPressed) 50 else 300)
                )
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                                .width(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(conducaoColor)
                                .clickable(
                                        interactionSource = conducaoInteraction,
                                        indication = null
                                ) {
                                        BottomBarState.isSettingsMenuExpanded = !showSettings
                                }
                                .padding(vertical = 6.dp)
                ) {
                        Text(
                                text = "Condução",
                                style =
                                        labelStyle.copy(
                                                fontSize = 10.sp,
                                                color =
                                                        if (showSettings) Color(0xFF2196F3)
                                                        else Color.White
                                        )
                        )
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = if (showSettings) Color(0xFF2196F3) else Color.White,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }
        }
}

@Composable
fun TempControlSection(
        label: String,
        temp: String,
        isEnabled: Boolean,
        sliderType: BottomBarState.SliderType? = null,
        buttonWidth: androidx.compose.ui.unit.Dp = 48.dp,
        onTempChange: ((Float) -> Unit)? = null,
        onValueChange: ((Float) -> Unit)? = null
) {
        val floatTemp = temp.toFloatOrNull() ?: 22.0f
        val isAbnormal = floatTemp >= 85f || (floatTemp <= -40f && temp != "--") || floatTemp == -1f
        val isTempValid = isEnabled && !isAbnormal
        val canInteract = !isAbnormal
        val alpha = if (isTempValid) 1f else 0.5f
        var centerX by remember { mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()
        var dismissJob by remember { mutableStateOf<Job?>(null) }
        val currentTempRef by rememberUpdatedState(temp)
        // Read from pointerInput(Unit): always call the latest callbacks (see FanControlSection).
        val currentOnTempChange by rememberUpdatedState(onTempChange)
        val currentOnValueChange by rememberUpdatedState(onValueChange)

        fun notifyChange(newTemp: Float, delta: Float) {
                currentOnTempChange?.invoke(newTemp)
                currentOnValueChange?.invoke(delta)
        }

        fun postHud(newTemp: Float, autoDismiss: Boolean = true) {
                BottomBarState.activeSwipeHud = BottomBarState.SwipeHudData(
                        type = sliderType ?: BottomBarState.SliderType.DRIVER_TEMP,
                        title = if (label == "Motorista") "TEMP. MOTORISTA" else "TEMP. PASSAGEIRO",
                        valueText = String.format(java.util.Locale.US, "%.1f\u00b0C", newTemp),
                        fraction = ((newTemp - 16.0f) / (30.0f - 16.0f)).coerceIn(0f, 1f),
                        targetCenterX = centerX,
                        minLabel = "16.0\u00b0C",
                        maxLabel = "30.0\u00b0C",
                        isFan = false
                )
                dismissJob?.cancel()
                if (autoDismiss) {
                        dismissJob = scope.launch {
                                delay(1000)
                                BottomBarState.activeSwipeHud = null
                        }
                }
        }

        val leftInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val leftPressed by leftInteraction.collectIsPressedAsState()
        val rightInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val rightPressed by rightInteraction.collectIsPressedAsState()

        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                        .width(172.dp)
                        .height(44.dp)
                        .alpha(alpha)
        ) {
                // Left Chevron < (Decrease temp)
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .size(width = 38.dp, height = 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                        enabled = canInteract,
                                        interactionSource = leftInteraction,
                                        indication = null
                                ) {
                                        val cur = currentTempRef.toFloatOrNull() ?: 22.0f
                                        val next = (cur - 0.5f).coerceIn(16.0f, 30.0f)
                                        if (next != cur) {
                                                notifyChange(next, -0.5f)
                                                postHud(next, autoDismiss = true)
                                        }
                                }
                ) {
                        Canvas(modifier = Modifier.size(18.dp)) {
                                val strokeW = 2.dp.toPx()
                                val path = Path().apply {
                                        moveTo(size.width * 0.65f, size.height * 0.15f)
                                        lineTo(size.width * 0.35f, size.height * 0.50f)
                                        lineTo(size.width * 0.65f, size.height * 0.85f)
                                }
                                drawPath(
                                        path = path,
                                        color = if (leftPressed) Color(0xFF2196F3) else Color.White.copy(alpha = if (canInteract) 0.85f else 0.35f),
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                        }
                }

                // Center Temperature Display (e.g. 21.5℃)
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .width(96.dp)
                                .fillMaxHeight()
                                .onGloballyPositioned { coordinates ->
                                        centerX = coordinates.positionInRoot().x + coordinates.size.width / 2f
                                }
                                .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                                while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        val startX = down.position.x
                                                        val startY = down.position.y
                                                        var totalDragX = 0f
                                                        var isDragging = false
                                                        var accumulatedDragX = 0f
                                                        var activeTemp = currentTempRef.toFloatOrNull() ?: 22.0f

                                                        do {
                                                                val event = awaitPointerEvent()
                                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                                if (!change.pressed) break

                                                                val deltaX = change.position.x - change.previousPosition.x
                                                                totalDragX += deltaX

                                                                if (!isDragging && kotlin.math.abs(totalDragX) > 10.dp.toPx() && kotlin.math.abs(totalDragX) > kotlin.math.abs(change.position.y - startY)) {
                                                                        isDragging = true
                                                                        activeTemp = currentTempRef.toFloatOrNull() ?: 22.0f
                                                                        postHud(activeTemp, autoDismiss = false)
                                                                }

                                                                if (isDragging) {
                                                                        change.consume()
                                                                        accumulatedDragX += deltaX
                                                                        val stepPx = 32.dp.toPx()
                                                                        if (kotlin.math.abs(accumulatedDragX) >= stepPx) {
                                                                                val numSteps = (kotlin.math.abs(accumulatedDragX) / stepPx).toInt()
                                                                                val stepDirection = if (accumulatedDragX > 0) 1 else -1
                                                                                accumulatedDragX -= stepDirection * numSteps * stepPx

                                                                                val deltaTotal = stepDirection * numSteps * 0.5f
                                                                                val next = (activeTemp + deltaTotal).coerceIn(16.0f, 30.0f)
                                                                                if (next != activeTemp) {
                                                                                        val appliedDelta = next - activeTemp
                                                                                        activeTemp = next
                                                                                        notifyChange(next, appliedDelta)
                                                                                        postHud(next, autoDismiss = false)
                                                                                }
                                                                        }
                                                                }
                                                        } while (change.pressed)

                                                        if (isDragging) {
                                                                dismissJob?.cancel()
                                                                dismissJob = scope.launch {
                                                                        delay(1000)
                                                                        BottomBarState.activeSwipeHud = null
                                                                }
                                                        } else {
                                                                val cur = currentTempRef.toFloatOrNull() ?: 22.0f
                                                                postHud(cur, autoDismiss = true)
                                                        }
                                                }
                                        }
                                }
                ) {
                        val displayTemp = if (temp == "--") "--" else temp
                        val tempColor = if (floatTemp > 30f) Color.Red else Color.White

                        Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.Center
                        ) {
                                Text(
                                        text = displayTemp,
                                        style = TextStyle(
                                                fontFamily = Michroma,
                                                fontSize = 23.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isTempValid) tempColor else Color.White.copy(alpha = 0.4f)
                                        )
                                )
                                if (displayTemp != "--") {
                                        Text(
                                                text = "\u2103",
                                                style = TextStyle(
                                                        fontFamily = Michroma,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        color = if (isTempValid) tempColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f)
                                                ),
                                                modifier = Modifier.padding(start = 1.dp, top = 1.dp)
                                        )
                                }
                        }
                }

                // Right Chevron > (Increase temp)
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .size(width = 38.dp, height = 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                        enabled = canInteract,
                                        interactionSource = rightInteraction,
                                        indication = null
                                ) {
                                        val cur = currentTempRef.toFloatOrNull() ?: 22.0f
                                        val next = (cur + 0.5f).coerceIn(16.0f, 30.0f)
                                        if (next != cur) {
                                                notifyChange(next, 0.5f)
                                                postHud(next, autoDismiss = true)
                                        }
                                }
                ) {
                        Canvas(modifier = Modifier.size(18.dp)) {
                                val strokeW = 2.dp.toPx()
                                val path = Path().apply {
                                        moveTo(size.width * 0.35f, size.height * 0.15f)
                                        lineTo(size.width * 0.65f, size.height * 0.50f)
                                        lineTo(size.width * 0.35f, size.height * 0.85f)
                                }
                                drawPath(
                                        path = path,
                                        color = if (rightPressed) Color(0xFF2196F3) else Color.White.copy(alpha = if (canInteract) 0.85f else 0.35f),
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                        }
                }
        }
}
@Composable
fun FanControlSection(
        speed: Int,
        isPowerOn: Boolean = true,
        sliderType: BottomBarState.SliderType? = BottomBarState.SliderType.FAN,
        onSpeedChange: (Int) -> Unit = {},
        onFanClick: () -> Unit = {}
) {
        val scope = rememberCoroutineScope()
        var dismissJob by remember { mutableStateOf<Job?>(null) }
        var centerX by remember { mutableFloatStateOf(0f) }
        val isAcMenuExpanded = BottomBarState.isAcMenuExpanded
        val isActive = isPowerOn && speed > 0
        val currentSpeedRef by rememberUpdatedState(speed)
        // The gesture block below is created once (pointerInput(Unit)); without these it keeps calling the
        // callbacks from the first composition, whose captured A/C power state is long stale.
        val currentOnSpeedChange by rememberUpdatedState(onSpeedChange)
        val currentOnFanClick by rememberUpdatedState(onFanClick)

        fun postHud(newSpeed: Int, autoDismiss: Boolean = true) {
                BottomBarState.activeSwipeHud = BottomBarState.SwipeHudData(
                        type = BottomBarState.SliderType.FAN,
                        title = "VENTILA\u00c7\u00c3O",
                        valueText = if (newSpeed == 0) "OFF" else "N\u00edvel $newSpeed",
                        fraction = (newSpeed / 7f).coerceIn(0f, 1f),
                        targetCenterX = centerX,
                        minLabel = "OFF",
                        maxLabel = "MAX (7)",
                        isFan = true,
                        fanSpeed = newSpeed
                )
                dismissJob?.cancel()
                if (autoDismiss) {
                        dismissJob = scope.launch {
                                delay(1000)
                                BottomBarState.activeSwipeHud = null
                        }
                }
        }

        var isCenterPressed by remember { mutableStateOf(false) }
        var spinKick by remember { mutableIntStateOf(0) }
        val glowColor = Color(0xFF2196F3).copy(alpha = 0.35f)
        val centerBg by animateColorAsState(
                targetValue = if (isCenterPressed || isAcMenuExpanded) glowColor else Color.Transparent,
                animationSpec = tween(durationMillis = if (isCenterPressed) 50 else 300),
                label = "fanCenterBg"
        )

        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                        .width(88.dp)
                        .height(44.dp)
                        .onGloballyPositioned { coords ->
                                centerX = coords.positionInRoot().x + coords.size.width / 2f
                                BottomBarState.fanButtonCenterX = centerX
                        }
        ) {
                // Center Fan Icon + Speed (e.g. 🪭 1 or 🪭 OFF)
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .dockPressScale(isCenterPressed)
                                .width(88.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(centerBg)
                                .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                                while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        isCenterPressed = true
                                                        spinKick++
                                                        val startX = down.position.x
                                                        val startY = down.position.y
                                                        var totalDragX = 0f
                                                        var isDragging = false
                                                        var accumulatedDragX = 0f
                                                        var activeSpeed = currentSpeedRef

                                                        do {
                                                                val event = awaitPointerEvent()
                                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                                if (!change.pressed) break

                                                                val deltaX = change.position.x - change.previousPosition.x
                                                                val deltaY = change.position.y - change.previousPosition.y
                                                                totalDragX += deltaX

                                                                if (!isDragging && abs(totalDragX) > 10.dp.toPx() && abs(totalDragX) > abs(change.position.y - startY)) {
                                                                        isDragging = true
                                                                        activeSpeed = currentSpeedRef
                                                                        postHud(activeSpeed, autoDismiss = false)
                                                                }

                                                                if (isDragging) {
                                                                        change.consume()
                                                                        accumulatedDragX += deltaX
                                                                        val stepPx = 27.dp.toPx()
                                                                        if (abs(accumulatedDragX) >= stepPx) {
                                                                                val numSteps = (abs(accumulatedDragX) / stepPx).toInt()
                                                                                val stepDirection = if (accumulatedDragX > 0) 1 else -1
                                                                                accumulatedDragX -= stepDirection * numSteps * stepPx
                                                                                val next = (activeSpeed + stepDirection * numSteps).coerceIn(0, 7)
                                                                                if (next != activeSpeed) {
                                                                                        activeSpeed = next
                                                                                        currentOnSpeedChange(next)
                                                                                        postHud(next, autoDismiss = false)
                                                                                }
                                                                        }
                                                                }
                                                        } while (change.pressed)

                                                        isCenterPressed = false

                                                        if (isDragging) {
                                                                dismissJob?.cancel()
                                                                dismissJob = scope.launch {
                                                                        delay(1000)
                                                                        BottomBarState.activeSwipeHud = null
                                                                }
                                                        } else {
                                                                currentOnFanClick()
                                                        }
                                                }
                                        }
                                }
                ) {
                        Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                        ) {
                                val fanTint = animatedDockTint(
                                        active = isAcMenuExpanded,
                                        idleColor = if (isActive) Color.White else Color.White.copy(alpha = 0.4f)
                                )
                                SpinningFanIndicator(
                                        speed = speed,
                                        isPowerOn = isPowerOn,
                                        tint = fanTint,
                                        kick = spinKick
                                )
                                FanLevelLabel(
                                        speed = speed,
                                        isPowerOn = isPowerOn,
                                        color = fanTint,
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                )
                        }
                }

        }
}


@Composable
fun VolumeControlSection(
        label: String,
        volume: Int,
        sliderType: BottomBarState.SliderType? = null,
        buttonWidth: androidx.compose.ui.unit.Dp = 48.dp,
        onVolumeChange: ((Int) -> Unit)? = null,
        onValueChange: ((Int) -> Unit)? = null
) {
        var centerX by remember { mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()
        var dismissJob by remember { mutableStateOf<Job?>(null) }
        val currentVolRef by rememberUpdatedState(volume)
        val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
        val currentOnValueChange by rememberUpdatedState(onValueChange)

        fun notifyChange(newVol: Int, delta: Int) {
                currentOnVolumeChange?.invoke(newVol)
                currentOnValueChange?.invoke(delta)
        }

        fun postHud(newVol: Int, autoDismiss: Boolean = true) {
                BottomBarState.activeSwipeHud = BottomBarState.SwipeHudData(
                        type = BottomBarState.SliderType.VOLUME,
                        title = "VOLUME DE M\u00cdDIA",
                        valueText = if (newVol == 0) "MUDO" else newVol.toString(),
                        fraction = (newVol / 30f).coerceIn(0f, 1f),
                        targetCenterX = centerX,
                        minLabel = "0",
                        maxLabel = "30",
                        isFan = false
                )
                dismissJob?.cancel()
                if (autoDismiss) {
                        dismissJob = scope.launch {
                                delay(1000)
                                BottomBarState.activeSwipeHud = null
                        }
                }
        }

        var isCenterPressed by remember { mutableStateOf(false) }
        val glowColor = Color(0xFF2196F3).copy(alpha = 0.35f)
        val centerBg by animateColorAsState(
                targetValue = if (isCenterPressed) glowColor else Color.Transparent,
                animationSpec = tween(durationMillis = if (isCenterPressed) 50 else 300),
                label = "volCenterBg"
        )

        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                        .width(64.dp)
                        .height(44.dp)
                        .onGloballyPositioned { coordinates ->
                                centerX = coordinates.positionInRoot().x + coordinates.size.width / 2f
                        }
        ) {
                // Center Volume Icon + Value (with subscript text)
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .width(64.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(centerBg)
                                .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                                while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        isCenterPressed = true
                                                        val startX = down.position.x
                                                        val startY = down.position.y
                                                        var totalDragX = 0f
                                                        var isDragging = false
                                                        var accumulatedDragX = 0f
                                                        var activeVol = currentVolRef

                                                        do {
                                                                val event = awaitPointerEvent()
                                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                                if (!change.pressed) break

                                                                val deltaX = change.position.x - change.previousPosition.x
                                                                val deltaY = change.position.y - change.previousPosition.y
                                                                totalDragX += deltaX

                                                                if (!isDragging && kotlin.math.abs(totalDragX) > 10.dp.toPx() && kotlin.math.abs(totalDragX) > kotlin.math.abs(deltaY)) {
                                                                        isDragging = true
                                                                        activeVol = currentVolRef
                                                                        postHud(activeVol, autoDismiss = false)
                                                                }

                                                                if (isDragging) {
                                                                        change.consume()
                                                                        accumulatedDragX += deltaX
                                                                        val stepPx = 21.dp.toPx()
                                                                        if (kotlin.math.abs(accumulatedDragX) >= stepPx) {
                                                                                val numSteps = (kotlin.math.abs(accumulatedDragX) / stepPx).toInt()
                                                                                val stepDirection = if (accumulatedDragX > 0) 1 else -1
                                                                                accumulatedDragX -= stepDirection * numSteps * stepPx
                                                                                val next = (activeVol + stepDirection * numSteps).coerceIn(0, 30)
                                                                                if (next != activeVol) {
                                                                                        val appliedDelta = next - activeVol
                                                                                        activeVol = next
                                                                                        notifyChange(next, appliedDelta)
                                                                                        postHud(next, autoDismiss = false)
                                                                                }
                                                                        }
                                                                }
                                                        } while (change.pressed)

                                                        isCenterPressed = false
                                                        if (isDragging) {
                                                                dismissJob?.cancel()
                                                                dismissJob = scope.launch {
                                                                        delay(1000)
                                                                        BottomBarState.activeSwipeHud = null
                                                                }
                                                        } else {
                                                                postHud(currentVolRef, autoDismiss = true)
                                                        }
                                                }
                                        }
                                }
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                        ) {
                                Icon(
                                        imageVector = if (volume == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                        contentDescription = "Volume",
                                        tint = if (isCenterPressed) Color(0xFF2196F3) else Color.White.copy(alpha = 0.88f),
                                        modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                        text = if (volume == 0) "OFF" else volume.toString(),
                                        style = TextStyle(
                                                fontFamily = Michroma,
                                                fontSize = if (volume == 0) 8.sp else 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCenterPressed) Color(0xFF2196F3) else Color.White.copy(alpha = 0.90f)
                                        ),
                                        modifier = Modifier.offset(y = 8.dp)
                                )
                        }
                }

        }
}

@Composable
fun ControlsSection(scope: CoroutineScope) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                val voltarInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val voltarPressed by voltarInteraction.collectIsPressedAsState()
                val voltarColor by animateColorAsState(
                        targetValue = if (voltarPressed) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                        animationSpec = tween(durationMillis = if (voltarPressed) 50 else 300)
                )
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(voltarColor)
                                .clickable(
                                        interactionSource = voltarInteraction,
                                        indication = null
                                ) {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                ShizukuUtils.runCommandAndGetOutput(
                                                        arrayOf("input", "keyevent", "4")
                                                )
                                        }
                                }
                                .padding(vertical = 4.dp)
                ) {
                        Text(
                                text = "Voltar",
                                style = labelStyle.copy(fontSize = 10.sp, color = Color.White)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Voltar",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                        )
                }

                val showSettings = BottomBarState.isSettingsMenuExpanded
                val conducaoInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val conducaoPressed by conducaoInteraction.collectIsPressedAsState()
                val conducaoColor by animateColorAsState(
                        targetValue = if (conducaoPressed || showSettings) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.Transparent,
                        animationSpec = tween(durationMillis = if (conducaoPressed) 50 else 300)
                )
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                                .width(70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(conducaoColor)
                                .onGloballyPositioned { coords ->
                                        BottomBarState.conducaoCenterX = coords.positionInRoot().x + coords.size.width / 2f
                                }
                                .clickable(
                                        interactionSource = conducaoInteraction,
                                        indication = null
                                ) {
                                        BottomBarState.isSettingsMenuExpanded = !showSettings
                                        if (BottomBarState.isSettingsMenuExpanded) {
                                                BottomBarState.isMenuExpanded = false
                                                BottomBarState.isOverrideMenuExpanded = false
                                                BottomBarState.isAcMenuExpanded = false
                                        }
                                }
                                .padding(vertical = 4.dp)
                ) {
                        Text(
                                text = "Condu\u00e7\u00e3o",
                                style = labelStyle.copy(
                                        fontSize = 10.sp,
                                        color = if (showSettings) Color(0xFF2196F3) else Color.White
                                )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Icon(
                                imageVector = Icons.Default.ElectricCar,
                                contentDescription = "Condu\u00e7\u00e3o",
                                tint = if (showSettings) Color(0xFF2196F3) else Color.White,
                                modifier = Modifier.size(22.dp)
                        )
                }
        }
}
@Composable
fun NavIcon(icon: ImageVector, onClick: () -> Unit) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val glowColor = Color(0xFF2196F3).copy(alpha = 0.35f)
        val animatedColor by animateColorAsState(
                targetValue = if (isPressed) glowColor else Color.Transparent,
                animationSpec = tween(durationMillis = if (isPressed) 50 else 300)
        )

        Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                        .size(48.dp) // standard nav icon container width/height
                        .clip(RoundedCornerShape(8.dp))
                        .background(animatedColor)
                        .clickable(
                                interactionSource = interactionSource,
                                indication = null
                        ) { onClick() }
        ) {
                Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                )
        }
}

private fun cropBitmapTransparentMargins(source: android.graphics.Bitmap): android.graphics.Bitmap {
        var firstX = source.width
        var firstY = source.height
        var lastX = -1
        var lastY = -1

        for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                        val alpha = (source.getPixel(x, y) shr 24) and 0xFF
                        if (alpha > 10) { // non-transparent
                                if (x < firstX) firstX = x
                                if (y < firstY) firstY = y
                                if (x > lastX) lastX = x
                                if (y > lastY) lastY = y
                        }
                }
        }

        if (lastX < firstX || lastY < firstY) {
                return source // empty or fully transparent
        }

        val width = lastX - firstX + 1
        val height = lastY - firstY + 1
        return android.graphics.Bitmap.createBitmap(source, firstX, firstY, width, height)
}

private fun decodeBase64ToBitmap(base64Str: String): ImageBitmap? {
        return try {
                val cleanStr = if (base64Str.startsWith("data:image")) {
                        base64Str.substringAfter(",")
                } else {
                        base64Str
                }
                val decodedBytes = android.util.Base64.decode(cleanStr, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                        val cropped = cropBitmapTransparentMargins(bitmap)
                        android.util.Log.d("BottomBarUI", "Successfully decoded and cropped. Orig size: ${bitmap.width}x${bitmap.height}, Cropped size: ${cropped.width}x${cropped.height}")
                        cropped.asImageBitmap()
                } else {
                        android.util.Log.e("BottomBarUI", "decodeByteArray returned null for base64: ${base64Str.take(50)}...")
                        null
                }
        } catch (e: Exception) {
                android.util.Log.e("BottomBarUI", "Failed to decode base64: ${base64Str.take(50)}...", e)
                null
        }
}

@Composable
fun ACControlButton(
        icon: Any,
        label: String,
        isActive: Boolean,
        isEnabled: Boolean,
        onClick: () -> Unit
) {
        val context = LocalContext.current
        val alpha = if (isEnabled) 1f else 0.4f
        val activeColor = Color(0xFF2196F3) // Vibrant blue for active state
        val contentColor = if (isActive && isEnabled) activeColor else Color.White

        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val glowColor = Color(0xFF2196F3).copy(alpha = 0.35f)
        val animatedColor by animateColorAsState(
                targetValue = if (isPressed) glowColor else Color.Transparent,
                animationSpec = tween(durationMillis = if (isPressed) 50 else 300)
        )

        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                        .alpha(alpha)
                        .width(68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(animatedColor)
                        .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = isEnabled
                        ) { onClick() }
                        .padding(vertical = 6.dp)
        ) {
                Text(
                        text = label,
                        style =
                                labelStyle.copy(
                                        fontSize = 10.sp,
                                        color = contentColor,
                                        fontWeight =
                                                if (isActive) FontWeight.Bold else FontWeight.Medium
                                )
                )
                if (icon is ImageVector) {
                        Icon(
                                icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                        )
                } else if (icon is String) {
                        val bitmap = remember(icon) { decodeBase64ToBitmap(icon) }
                        if (bitmap != null) {
                                Image(
                                        bitmap = bitmap,
                                        contentDescription = label,
                                        colorFilter = ColorFilter.tint(contentColor),
                                        modifier = Modifier.size(width = 36.dp, height = 24.dp)
                                )
                        }
                }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmallButton(
        icon: ImageVector,
        enabled: Boolean = true,
        iconSize: androidx.compose.ui.unit.Dp = 20.dp,
        width: androidx.compose.ui.unit.Dp = 48.dp,
        height: androidx.compose.ui.unit.Dp = 60.dp,
        onClick: () -> Unit
) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val glowColor = Color(0xFF2196F3).copy(alpha = 0.35f)
        val animatedColor by animateColorAsState(
                targetValue = if (isPressed) glowColor else Color.Transparent,
                animationSpec = tween(durationMillis = if (isPressed) 50 else 300)
        )

        Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                        .size(width, height)
                        .background(Color.Black.copy(alpha = 0.95f))
                        .clickable(
                                enabled = enabled,
                                interactionSource = interactionSource,
                                indication = null
                        ) { onClick() }
        ) {
                val alpha = if (enabled) 1f else 0.4f
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .fillMaxSize()
                                .alpha(alpha)
                ) {
                        Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(animatedColor)
                        ) {
                                Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(iconSize)
                                )
                        }
                }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGridItem(
        pkg: String,
        substituteIcon: String?,
        context: Context,
        scope: CoroutineScope,
        onDelete: () -> Unit,
        onDragStart: () -> Unit,
        onDrag: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        isDragged: Boolean,
        dragOffset: Offset,
        onClick: () -> Unit
) {
        val appConfig =
                remember(pkg) {
                        br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAppConfig(
                                pkg
                        )
                }
        val appInfo =
                remember(pkg) {
                        br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.resolveAppInfo(
                                context,
                                pkg,
                                appConfig?.customName
                        )
                }

        val substituteIconVector = getSubstituteIconVector(substituteIcon)

        val iconTint = appConfig?.iconColor.toComposeColor()
        val displayName = appInfo.label
        val isDeleteMode = BottomBarState.isDeleteModeEnabled

        // Cached reflection for gesture consumption
        val consumeMethod = remember {
                try {
                        PointerInputChange::class.java.methods.find {
                                it.name == "consume" || it.name == "consumeAllChanges"
                        }
                } catch (e: Exception) {
                        null
                }
        }

        // Shaking animation for delete mode
        val infiniteTransition = rememberInfiniteTransition(label = "shake")
        val rotation by
                infiniteTransition.animateFloat(
                        initialValue = -1.5f,
                        targetValue = 1.5f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(120, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "rotation"
                )

        val baseModifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragged) 1f else 0f)

        val itemModifier = if (isDeleteMode || isDragged) {
                baseModifier.graphicsLayer {
                        rotationZ = if (isDeleteMode && !isDragged) rotation else 0f
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                        scaleX = 1f
                        scaleY = 1f
                        alpha = if (isDragged) 0.8f else 1f
                }
        } else {
                baseModifier
        }

        Column(
                modifier =
                        itemModifier
                                .pointerInput(isDeleteMode) {
                                        // Container handles drag logic now
                                }
                                .combinedClickable(
                                        onClick = {
                                                if (isDeleteMode) {
                                                        BottomBarState.isDeleteModeEnabled = false
                                                } else {
                                                        onClick()
                                                        // Update shared selection state
                                                        BottomBarState.selectedPackage = pkg
                                                        // Launch on the manager's own scope, not this
                                                        // composition's - closing the drawer below
                                                        // disposes it and would cancel the launch.
                                                        br.com.redesurftank.havalshisuku.managers
                                                                .DisplayAppLauncher
                                                                .launchAnyAppDetached(context, pkg)
                                                        // Close now instead of lingering until the
                                                        // launched app reaches the foreground and a
                                                        // monitor collapses the menu for us.
                                                        BottomBarState.isMenuExpanded = false
                                                }
                                        },
                                        onLongClick = { BottomBarState.isDeleteModeEnabled = true }
                                )
                                .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Box(
                                modifier =
                                        Modifier.size(60.dp)
                                                .background(
                                                        Color.White.copy(alpha = 0.1f),
                                                        RoundedCornerShape(12.dp)
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                if (substituteIcon == "youtube" ||
                                                substituteIcon == "youtube_music" ||
                                                substituteIcon == "gwm"
                                ) {
                                        Image(
                                                painter =
                                                        painterResource(
                                                                id =
                                                                        when (substituteIcon) {
                                                                                "youtube" ->
                                                                                        R.drawable
                                                                                                .ic_youtube_default
                                                                                "youtube_music" ->
                                                                                        R.drawable
                                                                                                .ic_youtube_music_default
                                                                                "gwm" ->
                                                                                        R.drawable
                                                                                                .ic_gwm
                                                                                else ->
                                                                                        R.drawable
                                                                                                .ic_youtube_default
                                                                        }
                                                        ),
                                                contentDescription = null,
                                                modifier = Modifier.size(40.dp)
                                        )
                                } else if (substituteIconVector != null) {
                                        Icon(
                                                substituteIconVector,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(40.dp)
                                        )
                                } else if (appInfo.icon != null) {
                                        AsyncImage(
                                                model = appInfo.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp)
                                        )
                                }
                        }

                        if (isDeleteMode) {
                                Box(
                                        modifier =
                                                Modifier.size(24.dp)
                                                        .align(Alignment.TopEnd)
                                                        .background(Color.Red, CircleShape)
                                                        .clickable {
                                                                scope.launch {
                                                                        br.com.redesurftank
                                                                                .havalshisuku
                                                                                .managers
                                                                                .DisplayAppLauncher
                                                                                .deleteConfig(pkg)
                                                                        onDelete()
                                                                }
                                                        },
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remover",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                        )
                                }
                        }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                        text = displayName,
                        style =
                                labelStyle.copy(
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 13.sp
                                ),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                )
        }
}

@Composable
fun OverrideMenuContent() {
        val context = LocalContext.current
        val pkg = BottomBarState.currentPackage
        val prefs = remember {
                br.com.redesurftank.App.getDeviceProtectedContext()
                        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        }

        // Load current app settings from SharedPreferences
        val overridesJson = prefs.getString(SharedPreferencesKeys.BOTTOM_BAR_OVERRIDES.key, null)
        val gson = com.google.gson.Gson()
        val type =
                object :
                                com.google.gson.reflect.TypeToken<
                                        MutableMap<String, Map<String, Int>>>() {}
                        .type
        val overrides: MutableMap<String, Map<String, Int>> =
                if (overridesJson != null) {
                        try {
                                gson.fromJson(overridesJson, type)
                        } catch (e: Exception) {
                                mutableMapOf()
                        }
                } else {
                        mutableMapOf()
                }

        val currentSettings = overrides[pkg]
        val overscanValues = listOf(0, 15, 20, 30, 45, 60, 75, 90, 105, 120)
        val currentOverscan = currentSettings?.get("overscan") ?: 20
        var overscanIndex by
                remember(pkg) {
                        mutableIntStateOf(overscanValues.indexOf(currentOverscan).coerceAtLeast(0))
                }
        var offset by remember(pkg) { mutableIntStateOf(currentSettings?.get("offset") ?: 0) }

        // Helper to auto-apply and save
        val updateSettings = { newOverscan: Int, newOffset: Int ->
                val density = context.resources.displayMetrics.density
                val overscanPx = (newOverscan * density).toInt()

                // Apply immediately
                br.com.redesurftank.havalshisuku.utils.ShizukuUtils.runCommandAndGetOutput(
                        arrayOf("wm", "overscan", "0,0,0,$overscanPx")
                )
                context.sendBroadcast(
                        android.content.Intent(
                                        "br.com.redesurftank.havalshisuku.UPDATE_BAR_POSITION"
                                )
                                .apply {
                                        setPackage(context.packageName)
                                        putExtra("overscan", newOverscan)
                                        putExtra("offset", newOffset)
                                }
                )

                // Save to preferences
                val newOverrides = overrides.toMutableMap()
                newOverrides[pkg] = mapOf("overscan" to newOverscan, "offset" to newOffset)
                prefs.edit()
                        .putString(
                                SharedPreferencesKeys.BOTTOM_BAR_OVERRIDES.key,
                                gson.toJson(newOverrides)
                        )
                        .apply()

                // v2.3: Re-resize Impulse-managed Display 0 apps so their
                // windows shrink for the new overscan. wm overscan above is
                // the system-level fallback for unmanaged apps; managed apps
                // (e.g. AA) need an explicit am stack resize because Impulse's
                // bounds otherwise override the system overscan.
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                        .reapplyDisplay0BoundsForOverscanAsync()
        }

        Box(
                modifier =
                        Modifier.background(
                                        Color(0xFF11141A).copy(alpha = 0.98f),
                                        RoundedCornerShape(14.dp)
                                )
                                .border(1.dp, Color(0xFF1F2530), RoundedCornerShape(14.dp))
                                .width(360.dp)
                                .padding(16.dp)
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                                text = "Ajuste Real-time: $pkg",
                                style =
                                        labelStyle.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                        )
                        )

                        // Versão da Barra
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                        text = "Versão da Barra",
                                        style = labelStyle.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                        )
                                )
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        BottomBarState.BarVersion.entries.forEach { option ->
                                                val isSelected = BottomBarState.barVersion == option.key
                                                Surface(
                                                        onClick = {
                                                                BottomBarState.barVersion = option.key
                                                                prefs.edit()
                                                                        .putString(
                                                                                SharedPreferencesKeys.BOTTOM_BAR_VERSION.key,
                                                                                option.key
                                                                        )
                                                                        .apply()
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        color = if (isSelected) Color(0xFF2196F3).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(
                                                                1.dp,
                                                                if (isSelected) Color(0xFF2196F3) else Color.Transparent
                                                        )
                                                ) {
                                                        Box(
                                                                contentAlignment = Alignment.Center,
                                                                modifier = Modifier.padding(vertical = 8.dp)
                                                        ) {
                                                                Text(
                                                                        text = option.label,
                                                                        color = if (isSelected) Color.White else Color.Gray,
                                                                        fontSize = 12.sp,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }

                        OverrideControlRow(
                                "Overscan (Value: ${overscanValues[overscanIndex]})",
                                overscanIndex,
                                0..9,
                                steps = 8
                        ) {
                                overscanIndex = it
                                updateSettings(overscanValues[it], offset)
                        }

                        OverrideControlRow(
                                "Offset (Move a barra para baixo)",
                                offset,
                                -150..150,
                                steps = 59
                        ) {
                                offset = it
                                updateSettings(overscanValues[overscanIndex], it)
                        }

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                Button(
                                        onClick = { BottomBarState.isOverrideMenuExpanded = false },
                                        modifier = Modifier.weight(1f),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF2196F3)
                                                )
                                ) { Text("Fechar", color = Color.White) }

                                Button(
                                        onClick = {
                                                // Reset everything
                                                overscanIndex = overscanValues.indexOf(20).coerceAtLeast(0)
                                                offset = 0
                                                updateSettings(20, 0)

                                                val newOverrides = overrides.toMutableMap()
                                                newOverrides.remove(pkg)
                                                prefs.edit()
                                                        .putString(
                                                                SharedPreferencesKeys
                                                                        .BOTTOM_BAR_OVERRIDES
                                                                        .key,
                                                                gson.toJson(newOverrides)
                                                        )
                                                        .apply()

                                                BottomBarState.isOverrideMenuExpanded = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color.DarkGray
                                                )
                                ) { Text("Resetar", color = Color.White) }
                        }
                }
        }
}

@Composable
fun OverrideControlRow(
        label: String,
        value: Int,
        range: IntRange,
        steps: Int = 0,
        onValueChange: (Int) -> Unit
) {
        Column {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Text(text = label, style = labelStyle)
                        Text(
                                text = value.toString(),
                                style = commonTextStyle.copy(fontSize = 14.sp)
                        )
                }
                Slider(
                        value = value.toFloat(),
                        onValueChange = { onValueChange(it.toInt()) },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = steps,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White
                                )
                )
        }
}

enum class VisualAidType {
        TEMP,
        FAN,
        VOLUME
}

@Composable
fun VerticalSliderOverlay() {
        val activeSlider = BottomBarState.activeSliderType ?: return
        val positionX = BottomBarState.sliderPositionX
        val serviceManager = ServiceManager.getInstance()
        val isACEnabled = serviceManager.getData(CarConstants.CAR_HVAC_POWER_MODE.getValue()) == "1"

        LaunchedEffect(activeSlider, BottomBarState.sliderInteractionTrigger) {
                delay(3000)
                if (!BottomBarState.isSliderDragging) {
                        BottomBarState.activeSliderType = null
                }
        }

        val density = LocalDensity.current
        val sliderWidthDp = 80.dp
        val sliderWidthPx = with(density) { sliderWidthDp.toPx() }
        // positionX is a root coordinate from the bar window, which is pinned to the physical display
        // - so clamp against the window width, not the (inset) app width, and keep the slider out of
        // the left navigation pane's gutter.
        val windowWidthPx =
                BottomBarState.overlayWindowWidthPx
                        .takeIf { it > 0 }
                        ?.toFloat()
                        ?: (LocalConfiguration.current.screenWidthDp * density.density)
        val gutterPx = BottomBarState.overlayLeftGutterPx.toFloat()
        val finalX =
                (positionX - sliderWidthPx / 2f)
                        .coerceIn(gutterPx, (windowWidthPx - sliderWidthPx).coerceAtLeast(gutterPx))
        val finalXDp = with(density) { finalX.toDp() }

        Box(
                modifier = Modifier.fillMaxSize()
        ) {
                Box(
                        modifier = Modifier
                                .offset(x = finalXDp)
                                .align(Alignment.BottomStart)
                                .padding(bottom = 8.dp)
                ) {
                        when (activeSlider) {
                                BottomBarState.SliderType.DRIVER_TEMP -> {
                                        var tempStr by remember {
                                                mutableStateOf(
                                                        serviceManager.getData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue())
                                                                ?: "22.0"
                                                )
                                        }
                                        DisposableEffect(Unit) {
                                                val listener = object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                                                        override fun onDataChanged(key: String, value: String?) {
                                                                if (key == CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue() && value != null) {
                                                                        tempStr = value
                                                                }
                                                        }
                                                }
                                                serviceManager.addDataChangedListener(listener)
                                                onDispose { serviceManager.removeDataChangedListener(listener) }
                                        }
                                        val tempVal = tempStr.toFloatOrNull() ?: 22.0f
                                        VerticalSlider(
                                                label = "Motorista",
                                                value = tempVal.coerceIn(16.0f, 32.0f),
                                                range = 16.0f..32.0f,
                                                step = 0.5f,
                                                displayValue = if (!isACEnabled || tempStr == "--" || tempVal <= -1) "--" else String.format(java.util.Locale.US, "%.1f°C", tempVal),
                                                visualAidType = VisualAidType.TEMP,
                                                modifier = Modifier.height(380.dp),
                                                isEnabled = isACEnabled,
                                                onValueChange = { newValue ->
                                                        serviceManager.updateData(
                                                                CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(),
                                                                String.format(java.util.Locale.US, "%.1f", newValue)
                                                        )
                                                }
                                        )
                                }
                                BottomBarState.SliderType.PASS_TEMP -> {
                                        var tempStr by remember {
                                                mutableStateOf(
                                                        serviceManager.getData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue())
                                                                ?: "22.0"
                                                )
                                        }
                                        DisposableEffect(Unit) {
                                                val listener = object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                                                        override fun onDataChanged(key: String, value: String?) {
                                                                if (key == CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue() && value != null) {
                                                                        tempStr = value
                                                                }
                                                        }
                                                }
                                                serviceManager.addDataChangedListener(listener)
                                                onDispose { serviceManager.removeDataChangedListener(listener) }
                                        }
                                        val tempVal = tempStr.toFloatOrNull() ?: 22.0f
                                        VerticalSlider(
                                                label = "Passageiro",
                                                value = tempVal.coerceIn(16.0f, 32.0f),
                                                range = 16.0f..32.0f,
                                                step = 0.5f,
                                                displayValue = if (!isACEnabled || tempStr == "--" || tempVal <= -1) "--" else String.format(java.util.Locale.US, "%.1f°C", tempVal),
                                                visualAidType = VisualAidType.TEMP,
                                                modifier = Modifier.height(380.dp),
                                                isEnabled = isACEnabled,
                                                onValueChange = { newValue ->
                                                        serviceManager.updateData(
                                                                CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(),
                                                                String.format(java.util.Locale.US, "%.1f", newValue)
                                                        )
                                                }
                                        )
                                }
                                BottomBarState.SliderType.FAN -> {
                                        var speedVal by remember {
                                                mutableIntStateOf(
                                                        serviceManager.getData(CarConstants.CAR_HVAC_FAN_SPEED.getValue())?.toIntOrNull()
                                                                ?: 1
                                                )
                                        }
                                        var hvacPower by remember {
                                                mutableStateOf(
                                                        serviceManager.getData(CarConstants.CAR_HVAC_POWER_MODE.getValue()) ?: "1"
                                                )
                                        }
                                        DisposableEffect(Unit) {
                                                val listener = object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                                                        override fun onDataChanged(key: String, value: String?) {
                                                                if (value == null) return
                                                                if (key == CarConstants.CAR_HVAC_FAN_SPEED.getValue()) {
                                                                        speedVal = value.toIntOrNull() ?: speedVal
                                                                } else if (key == CarConstants.CAR_HVAC_POWER_MODE.getValue()) {
                                                                        hvacPower = value
                                                                }
                                                        }
                                                }
                                                serviceManager.addDataChangedListener(listener)
                                                onDispose { serviceManager.removeDataChangedListener(listener) }
                                        }
                                        VerticalSlider(
                                                label = "Ventilação",
                                                value = speedVal.toFloat(),
                                                range = 0f..7f,
                                                step = 1f,
                                                displayValue = speedVal.toString(),
                                                visualAidType = VisualAidType.FAN,
                                                onValueChange = { newValue ->
                                                        val calculatedSpeed = newValue.toInt().coerceIn(0, 7)
                                                        serviceManager.updateData(
                                                                CarConstants.CAR_HVAC_FAN_SPEED.getValue(),
                                                                calculatedSpeed.toString()
                                                        )
                                                        if (calculatedSpeed == 0 && hvacPower == "1") {
                                                                serviceManager.updateData(
                                                                        CarConstants.CAR_HVAC_POWER_MODE.getValue(),
                                                                        "0"
                                                                )
                                                        } else if (calculatedSpeed > 0 && hvacPower == "0") {
                                                                serviceManager.updateData(
                                                                        CarConstants.CAR_HVAC_POWER_MODE.getValue(),
                                                                        "1"
                                                                )
                                                        }
                                                }
                                        )
                                }
                                BottomBarState.SliderType.VOLUME -> {
                                        var volVal by remember {
                                                mutableIntStateOf(
                                                        serviceManager.getData(CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue())?.toIntOrNull()
                                                                ?: 10
                                                )
                                        }
                                        DisposableEffect(Unit) {
                                                val listener = object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                                                        override fun onDataChanged(key: String, value: String?) {
                                                                if (key == CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue() && value != null) {
                                                                        volVal = value.toIntOrNull() ?: volVal
                                                                }
                                                        }
                                                }
                                                serviceManager.addDataChangedListener(listener)
                                                onDispose { serviceManager.removeDataChangedListener(listener) }
                                        }
                                        VerticalSlider(
                                                label = "Volume",
                                                value = volVal.toFloat(),
                                                range = 0f..30f,
                                                step = 1f,
                                                displayValue = volVal.toString(),
                                                visualAidType = VisualAidType.VOLUME,
                                                onValueChange = { newValue ->
                                                        val calculatedVol = newValue.toInt().coerceIn(0, 30)
                                                        volVal = calculatedVol
                                                        serviceManager.updateData(
                                                                CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue(),
                                                                calculatedVol.toString()
                                                        )
                                                }
                                        )
                                }
                        }
                }
        }
}

@Composable
fun VerticalSlider(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        step: Float,
        displayValue: String,
        visualAidType: VisualAidType,
        onValueChange: (Float) -> Unit,
        modifier: Modifier = Modifier,
        isEnabled: Boolean = true
) {
        var trackHeightPx by remember { mutableFloatStateOf(0f) }
        Box(
                modifier = Modifier
                        .width(80.dp)
                        .height(240.dp)
                        .then(modifier)
                        .background(Color(0xFF13151A).copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF1D2430), RoundedCornerShape(20.dp))
                        .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
        ) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize()
                ) {
                        Box(
                                modifier = Modifier
                                        .width(48.dp)
                                        .weight(1f)
                                        .padding(vertical = 10.dp)
                                        .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
                                        .pointerInput(range, step, isEnabled) {
                                                 if (!isEnabled) return@pointerInput
                                                 awaitPointerEventScope {
                                                         while (true) {
                                                                 val down = awaitFirstDown(requireUnconsumed = false)
                                                                 BottomBarState.isSliderDragging = true
                                                                 BottomBarState.sliderInteractionTrigger++
                                                                 val startY = down.position.y
                                                                 val fractionStart = ((trackHeightPx - startY) / trackHeightPx).coerceIn(0f..1f)
                                                                 val rawValueStart = range.start + fractionStart * (range.endInclusive - range.start)
                                                                 val steppedStart = (rawValueStart / step).roundToInt() * step
                                                                 onValueChange(steppedStart.coerceIn(range))

                                                                 do {
                                                                         val event = awaitPointerEvent()
                                                                         val change = event.changes.firstOrNull() ?: break
                                                                         if (change.pressed) {
                                                                                 change.consume()
                                                                                 val currentY = change.position.y
                                                                                 val fraction = ((trackHeightPx - currentY) / trackHeightPx).coerceIn(0f..1f)
                                                                                 val rawValue = range.start + fraction * (range.endInclusive - range.start)
                                                                                 val stepped = (rawValue / step).roundToInt() * step
                                                                                 onValueChange(stepped.coerceIn(range))
                                                                                 BottomBarState.sliderInteractionTrigger++
                                                                         }
                                                                 } while (event.changes.any { it.pressed })

                                                                 BottomBarState.isSliderDragging = false
                                                                 BottomBarState.sliderInteractionTrigger++
                                                         }
                                                 }
                                         }
                        ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

                                        when (visualAidType) {
                                                VisualAidType.TEMP -> {
                                                        val trackWidth = 10.dp.toPx()
                                                        val trackRect = Rect(
                                                                left = (w - trackWidth) / 2f,
                                                                top = 0f,
                                                                right = (w + trackWidth) / 2f,
                                                                bottom = h
                                                        )
                                                        drawRoundRect(
                                                                color = Color.White.copy(alpha = 0.1f),
                                                                topLeft = Offset(trackRect.left, trackRect.top),
                                                                size = Size(trackRect.width, trackRect.height),
                                                                cornerRadius = CornerRadius(trackWidth / 2f)
                                                        )

                                                        // Draw sleek step ticks flanking the temperature track
                                                        val numTicks = 9 // every 2°C from 16 to 32
                                                        for (i in 0 until numTicks) {
                                                                val tickY = h - (i.toFloat() / (numTicks - 1)) * h
                                                                // Left tick
                                                                drawLine(
                                                                        color = Color.White.copy(alpha = 0.25f),
                                                                        start = Offset(trackRect.left - 8.dp.toPx(), tickY),
                                                                        end = Offset(trackRect.left - 3.dp.toPx(), tickY),
                                                                        strokeWidth = 1.dp.toPx()
                                                                )
                                                                // Right tick
                                                                drawLine(
                                                                        color = Color.White.copy(alpha = 0.25f),
                                                                        start = Offset(trackRect.right + 3.dp.toPx(), tickY),
                                                                        end = Offset(trackRect.right + 8.dp.toPx(), tickY),
                                                                        strokeWidth = 1.dp.toPx()
                                                                )
                                                        }

                                                        if (isEnabled) {
                                                                val activeHeight = h * fraction
                                                                val gradientBrush = Brush.verticalGradient(
                                                                        colors = listOf(Color(0xFFFF4B4B), Color(0xFF4A9EFF)),
                                                                        startY = 0f,
                                                                        endY = h
                                                                )
                                                                drawRoundRect(
                                                                        brush = gradientBrush,
                                                                        topLeft = Offset(trackRect.left, h - activeHeight),
                                                                        size = Size(trackRect.width, activeHeight),
                                                                        cornerRadius = CornerRadius(trackWidth / 2f)
                                                                )
                                                                drawCircle(
                                                                        color = Color.White,
                                                                        radius = 7.dp.toPx(),
                                                                        center = Offset(w / 2f, h - activeHeight)
                                                                )
                                                        }
                                                }
                                                VisualAidType.FAN, VisualAidType.VOLUME -> {
                                                        val numSteps = if (visualAidType == VisualAidType.FAN) 7 else 15
                                                        val spacing = 3.dp.toPx()
                                                        val stepHeight = (h - (numSteps - 1) * spacing) / numSteps

                                                        for (i in 0 until numSteps) {
                                                                val active = (i + 1).toFloat() / numSteps <= fraction || (fraction == 0f && i == 0 && value > 0)
                                                                val minStepWidth = 12.dp.toPx()
                                                                val maxStepWidth = 36.dp.toPx()
                                                                val stepWidth = minStepWidth + (maxStepWidth - minStepWidth) * (i.toFloat() / (numSteps - 1))

                                                                val stepTop = h - (i + 1) * (stepHeight + spacing) + spacing
                                                                val stepLeft = (w - stepWidth) / 2f

                                                                val color = if (active && isEnabled) {
                                                                        Color(0xFF2196F3)
                                                                } else {
                                                                        Color.White.copy(alpha = 0.15f)
                                                                }

                                                                drawRoundRect(
                                                                        color = color,
                                                                        topLeft = Offset(stepLeft, stepTop),
                                                                        size = Size(stepWidth, stepHeight),
                                                                        cornerRadius = CornerRadius(2.dp.toPx())
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
}
