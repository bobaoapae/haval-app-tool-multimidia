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

    /**
     * The dumpsys fallback now parses one dump for every display; it must pick the same package
     * the old `sed -n '/Display #N/,/Display #/p' | grep ...` pipeline did. Dump shape as read on the
     * car (2026-10-02), already filtered on-device to headers + focus lines.
     */
    @Test
    fun activitiesDumpFallbackMatchesPerDisplaySedRange() {
        val dump =
            """
            Display #0 (activities from top to bottom):
                mResumedActivity: ActivityRecord{89286d3 u0 com.havalh6.viewer/.MainActivity t23739}
            Display #1 (activities from top to bottom):
            Display #2 (activities from top to bottom):
            Display #3 (activities from top to bottom):
                mResumedActivity: ActivityRecord{1a2b3c u0 com.ts.carplay.app/.CarPlayDisplayActivity t23801}
            Display #4096 (activities from top to bottom):
              mFocusedActivity: ActivityRecord{77 u0 com.beantechs.mediacenter/.Main t4}
            """.trimIndent()

        assertEquals("com.havalh6.viewer", DisplayAppLauncher.topPackageFromActivitiesDumpForTest(dump, 0))
        assertNull(DisplayAppLauncher.topPackageFromActivitiesDumpForTest(dump, 1))
        assertNull(DisplayAppLauncher.topPackageFromActivitiesDumpForTest(dump, 2))
        assertEquals("com.ts.carplay.app", DisplayAppLauncher.topPackageFromActivitiesDumpForTest(dump, 3))
        assertEquals(
            "com.beantechs.mediacenter",
            DisplayAppLauncher.topPackageFromActivitiesDumpForTest(dump, 4096)
        )
        assertNull(DisplayAppLauncher.topPackageFromActivitiesDumpForTest(dump, 5))
        assertNull(DisplayAppLauncher.topPackageFromActivitiesDumpForTest("", 0))
    }

    @Test
    fun activitiesDumpFallbackKeepsSedRangeQuirks() {
        // sed's start address is a substring match: "Display #1" also opens on "Display #10", and a
        // later header naming the display again opens a second range.
        val dump =
            """
            Display #10 (activities from top to bottom):
                mResumedActivity: ActivityRecord{1 u0 com.example.ten/.Main t1}
            Display #0 (activities from top to bottom):
                mResumedActivity: ActivityRecord{2 u0 com.example.zero/.Main t2}
            """.trimIndent()
        assertEquals("com.example.ten", DisplayAppLauncher.topPackageFromActivitiesDumpForTest(dump, 1))

        // The closing header line itself is printed by sed; a focus line is never on it, so it
        // contributes nothing, and the next display's activity is not leaked into the range.
        assertNull(
            DisplayAppLauncher.topPackageFromActivitiesDumpForTest(
                "Display #1 (x):\nDisplay #2 (x):\n    mResumedActivity: ActivityRecord{3 u0 com.example.two/.Main t3}",
                1
            )
        )
    }
}
