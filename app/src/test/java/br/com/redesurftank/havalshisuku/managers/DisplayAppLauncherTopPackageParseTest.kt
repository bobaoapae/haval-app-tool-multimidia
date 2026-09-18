package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression: configuration lines also contain displayId=, and matching every
 * displayId= wrongly reassigned the current display so a live D3 YouTube stack
 * looked absent (native-mask hole closed).
 */
class DisplayAppLauncherTopPackageParseTest {
    @Test
    fun stackHeaderDisplayIdIgnoresConfigurationLines() {
        val stackList =
            """
            Stack id=14 bounds=[0,0][1920,720] displayId=3 userId=0
             configuration={1.0  mcc0mnc0 [pt_BR] ldltr sw720dp w1920dp h720dp 160dpi lrg long land finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1920, 720) mAppBounds=Rect(0, 0 - 1920, 720) mWindowingMode=fullscreen mActivityType=standard displayId=0} s.2}
              taskId=22315: app.rvx.android.youtube/com.google.android.youtube.app.honeycomb.Shell${'$'}HomeActivity bounds=[0,0][1920,720] userId=0 visible=true
            Stack id=19 bounds=[0,0][1920,720] displayId=0 userId=0
              taskId=22320: com.havalh6.viewer/com.havalh6.viewer.MainActivity bounds=[0,0][1920,720] userId=0 visible=true
            """.trimIndent()

        assertEquals(
            "app.rvx.android.youtube",
            DisplayAppLauncher.topPackageFromStackListForTest(stackList, 3)
        )
        assertEquals(
            "com.havalh6.viewer",
            DisplayAppLauncher.topPackageFromStackListForTest(stackList, 0)
        )
        assertNull(DisplayAppLauncher.topPackageFromStackListForTest(stackList, 1))
    }
}
