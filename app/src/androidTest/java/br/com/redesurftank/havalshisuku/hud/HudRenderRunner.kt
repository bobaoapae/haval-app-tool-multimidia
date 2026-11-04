package br.com.redesurftank.havalshisuku.hud

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runs the HUD render tests with a plain Application: the app's own one starts Shizuku, the car
 * services and the cluster, none of which exist on an emulator, and the renderer needs none.
 */
class HudRenderRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
