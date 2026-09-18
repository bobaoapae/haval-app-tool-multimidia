package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAppLauncherFreeformOverscanTest {
    @Test
    fun fullscreenOnlyOnDisplay0DoesNotSkipOverscan() {
        val stackList =
            """
            Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
             configuration={1.0  mcc0mnc0 [pt_BR] ldltr sw720dp w1920dp h700dp 160dpi lrg long land finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1920, 720) mAppBounds=Rect(0, 0 - 1920, 700) mWindowingMode=fullscreen mActivityType=home} s.2}
              taskId=1: com.beantechs.launcher/com.beantechs.launcher.Launcher bounds=[0,0][1920,720] userId=0 visible=true
            """.trimIndent()

        assertFalse(
            DisplayAppLauncher.hasVisibleFreeformWindowOnDisplayFromStackList(stackList, 0)
        )
    }

    @Test
    fun visibleFreeformPopupOnDisplay0SkipsOverscan() {
        val stackList =
            """
            Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
             configuration={winConfig={ mWindowingMode=fullscreen mActivityType=home}}
              taskId=1: com.havalh6.viewer/com.havalh6.viewer.Launcher bounds=[0,0][1920,720] userId=0 visible=true
            Stack id=12 bounds=[120,40][900,640] displayId=0 userId=0
             configuration={winConfig={ mWindowingMode=freeform mActivityType=standard}}
              taskId=88: com.havalh6.viewer/com.havalh6.viewer.PopupActivity bounds=[120,40][900,640] userId=0 visible=true
            """.trimIndent()

        assertTrue(
            DisplayAppLauncher.hasVisibleFreeformWindowOnDisplayFromStackList(stackList, 0)
        )
    }

    @Test
    fun freeformOnDisplay3DoesNotSkipDisplay0Overscan() {
        val stackList =
            """
            Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
             configuration={winConfig={ mWindowingMode=fullscreen mActivityType=standard}}
              taskId=1: com.beantechs.launcher/com.beantechs.launcher.Launcher bounds=[0,0][1920,720] userId=0 visible=true
            Stack id=41 bounds=[0,0][1920,720] displayId=3 userId=0
             configuration={winConfig={ mWindowingMode=freeform mActivityType=standard}}
              taskId=90: com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity bounds=[0,0][1920,720] userId=0 visible=true
            """.trimIndent()

        assertFalse(
            DisplayAppLauncher.hasVisibleFreeformWindowOnDisplayFromStackList(stackList, 0)
        )
        assertTrue(
            DisplayAppLauncher.hasVisibleFreeformWindowOnDisplayFromStackList(stackList, 3)
        )
    }

    @Test
    fun hiddenFreeformTaskDoesNotSkipOverscan() {
        val stackList =
            """
            Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
             configuration={winConfig={ mWindowingMode=fullscreen mActivityType=home}}
              taskId=1: com.beantechs.launcher/com.beantechs.launcher.Launcher bounds=[0,0][1920,720] userId=0 visible=true
            Stack id=12 bounds=[120,40][900,640] displayId=0 userId=0
             configuration={winConfig={ mWindowingMode=freeform mActivityType=standard}}
              taskId=88: com.example.popup/.MainActivity bounds=[120,40][900,640] userId=0 visible=false
            """.trimIndent()

        assertFalse(
            DisplayAppLauncher.hasVisibleFreeformWindowOnDisplayFromStackList(stackList, 0)
        )
    }

    @Test
    fun numericWindowingModeFiveCountsAsFreeform() {
        val stackList =
            """
            Stack id=12 bounds=[120,40][900,640] displayId=0 userId=0
             configuration={winConfig={ mWindowingMode=5 mActivityType=standard}}
              taskId=88: com.example.popup/.MainActivity bounds=[120,40][900,640] userId=0 visible=true
            """.trimIndent()

        assertTrue(DisplayAppLauncher.isFreeformWindowingModeForTest("5"))
        assertTrue(DisplayAppLauncher.isFreeformWindowingModeForTest("freeform}"))
        assertFalse(DisplayAppLauncher.isFreeformWindowingModeForTest("fullscreen"))
        assertTrue(
            DisplayAppLauncher.hasVisibleFreeformWindowOnDisplayFromStackList(stackList, 0)
        )
    }
}
