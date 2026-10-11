package br.com.redesurftank.havalshisuku.projectors

import android.view.Surface
import br.com.redesurftank.havalshisuku.managers.ClusterSurfaceOutput

/** Records controller output demand only; it performs no Android rendering. */
object AaClusterVideoHost {
    private var shown = false
    private var generation = 0L
    private val output = ClusterSurfaceOutput()
    fun peekSurface(): Surface? = if (shown) Surface() else null
    fun peekOutput(): ClusterSurfaceOutput? = if (shown) output else null
    fun surfaceGeneration() = generation
    fun isShown() = shown
    fun show(context: Any): Boolean {
        shown = true
        generation++
        return true
    }
    fun hide() { shown = false }
}
