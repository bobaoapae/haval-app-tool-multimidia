package br.com.redesurftank.havalshisuku.projectors

internal object ProjectionD3StateHoldPolicy {
    fun shouldHoldCarPlayInDash(
            lastHealthyD3AtMs: Long,
            nowMs: Long,
            desiredCluster: Boolean,
            preparingD3: Boolean,
            holdMs: Long
    ): Boolean {
        if (!desiredCluster && !preparingD3) return false
        if (lastHealthyD3AtMs <= 0L) return false

        val elapsedMs = nowMs - lastHealthyD3AtMs
        return elapsedMs in 0..holdMs
    }
}
