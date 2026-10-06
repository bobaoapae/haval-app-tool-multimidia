package br.com.redesurftank.havalshisuku

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log

class AndroidSettingsShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.android.car.settings",
                    "com.android.car.settings.common.CarSettingActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.beantechs.settings",
                    "com.beantechs.settings.ui.activity.MainActivity"
                )
            )
        )

        for (candidate in candidates) {
            try {
                startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
                return
            } catch (exception: Exception) {
                Log.w(TAG, "Unable to open Android settings with $candidate", exception)
            }
        }

        finish()
    }
}
