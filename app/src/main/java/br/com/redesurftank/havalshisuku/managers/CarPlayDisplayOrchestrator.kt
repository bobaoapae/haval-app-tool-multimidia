package br.com.redesurftank.havalshisuku.managers

import android.util.Log
import br.com.redesurftank.havalshisuku.models.DisplayAppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class CarPlayDisplayState {
    DISCONNECTED,
    CONNECTED_ON_D0,
    PREPARING_D3,
    MIRRORED_ON_D3,
    RETURNING_TO_D0,
    ERROR_RECOVERY
}

object CarPlayDisplayOrchestrator {
    private const val TAG = "CarPlayOrchestrator"

    // Short, cancelable settle window so the cluster WebView can apply the Mapa
    // preparation state before the native CarPlay Activity is recreated on D3.
    private const val D3_MAPA_PREPARE_SETTLE_MS = 260L

    private val transitionMutex = Mutex()

    @Volatile
    var currentState: CarPlayDisplayState = CarPlayDisplayState.DISCONNECTED
        private set

    @Volatile
    private var preparingD3 = false

    internal fun resolveObservedState(
        carPlayOnD0: Boolean,
        carPlayOnD3: Boolean
    ): CarPlayDisplayState {
        return when {
            carPlayOnD3 -> CarPlayDisplayState.MIRRORED_ON_D3
            carPlayOnD0 -> CarPlayDisplayState.CONNECTED_ON_D0
            else -> CarPlayDisplayState.DISCONNECTED
        }
    }

    fun isPreparingD3(): Boolean = preparingD3

    fun isClusterHandoffInProgress(): Boolean {
        return isClusterHandoffInProgressForTest(currentState, preparingD3)
    }

    fun isMainHandoffInProgress(): Boolean {
        return isMainHandoffInProgressForTest(currentState)
    }

    internal fun isClusterHandoffInProgressForTest(
        state: CarPlayDisplayState,
        preparingD3Flag: Boolean
    ): Boolean {
        return preparingD3Flag || state == CarPlayDisplayState.PREPARING_D3
    }

    internal fun isMainHandoffInProgressForTest(state: CarPlayDisplayState): Boolean {
        return state == CarPlayDisplayState.RETURNING_TO_D0
    }

    fun refreshObservedState(
        reason: String,
        reconcileTransition: Boolean = false
    ): CarPlayDisplayState {
        val observed = resolveObservedState(
            carPlayOnD0 = DisplayAppLauncher.isCarPlayOnDisplay(0),
            carPlayOnD3 = DisplayAppLauncher.isCarPlayOnDisplay(3)
        )
        if (currentState != observed && (reconcileTransition || !isTransitionState(currentState))) {
            transitionTo(observed, "${reason}_OBSERVED")
        }
        return observed
    }

    suspend fun start(config: DisplayAppConfig, reason: String) {
        if (config.displayId == 0) {
            openOnMain(config, reason)
        } else {
            sendToCluster(config, reason)
        }
    }

    suspend fun openOnMain(
        sourceConfig: DisplayAppConfig,
        reason: String,
        rememberTarget: Boolean = true
    ) = withContext(Dispatchers.IO) {
        transitionMutex.withLock {
            val observed = refreshObservedState("${reason}_BEFORE_D0")
            if (observed == CarPlayDisplayState.CONNECTED_ON_D0) {
                if (rememberTarget) {
                    DisplayAppLauncher.rememberCarPlayDisplayTargetForOrchestrator(
                        0,
                        "${reason}_IDEMPOTENT_D0"
                    )
                }
                Log.w(TAG, "[$reason] Idempotent D0 request; CarPlay already connected on display 0")
                return@withLock
            }

            setPreparingD3(false, "${reason}_OPEN_MAIN")
            transitionTo(CarPlayDisplayState.RETURNING_TO_D0, reason)
            runCatching {
                DisplayAppLauncher.startCarPlayOnDisplay(
                    sourceConfig.copy(displayId = 0),
                    reason,
                    rememberTarget
                )
            }.onFailure { error ->
                transitionTo(CarPlayDisplayState.ERROR_RECOVERY, "${reason}_FAILED")
                Log.e(TAG, "[$reason] Failed to open CarPlay on D0", error)
                throw error
            }

            val after = refreshObservedState("${reason}_AFTER_D0", reconcileTransition = true)
            if (after != CarPlayDisplayState.CONNECTED_ON_D0) {
                transitionTo(CarPlayDisplayState.ERROR_RECOVERY, "${reason}_D0_NOT_CONFIRMED")
            }
        }
    }

    suspend fun sendToCluster(sourceConfig: DisplayAppConfig, reason: String) =
        withContext(Dispatchers.IO) {
            transitionMutex.withLock {
                val observed = refreshObservedState("${reason}_BEFORE_D3")
                if (observed == CarPlayDisplayState.MIRRORED_ON_D3) {
                    DisplayAppLauncher.rememberCarPlayDisplayTargetForOrchestrator(
                        3,
                        "${reason}_IDEMPOTENT_D3"
                    )
                    DisplayAppLauncher.preserveCarPlayClusterContract("${reason}_IDEMPOTENT_D3")
                    Log.w(TAG, "[$reason] Idempotent D3 request; CarPlay already mirrored on cluster")
                    return@withLock
                }

                transitionTo(CarPlayDisplayState.PREPARING_D3, reason)
                setPreparingD3(true, "${reason}_PREPARE_MAPA")
                DisplayAppLauncher.rememberCarPlayDisplayTargetForOrchestrator(
                    3,
                    "${reason}_PREPARE_TARGET"
                )
                delay(D3_MAPA_PREPARE_SETTLE_MS)

                try {
                    runCatching {
                        DisplayAppLauncher.startCarPlayOnDisplay(
                            sourceConfig.copy(displayId = 3),
                            reason,
                            rememberTarget = true
                        )
                    }.onFailure { error ->
                        transitionTo(CarPlayDisplayState.ERROR_RECOVERY, "${reason}_FAILED")
                        Log.e(TAG, "[$reason] Failed to mirror CarPlay on D3", error)
                        throw error
                    }
                } finally {
                    setPreparingD3(false, "${reason}_PREPARE_DONE")
                }

                val after = refreshObservedState("${reason}_AFTER_D3", reconcileTransition = true)
                if (after != CarPlayDisplayState.MIRRORED_ON_D3) {
                    transitionTo(CarPlayDisplayState.ERROR_RECOVERY, "${reason}_D3_NOT_CONFIRMED")
                }
            }
        }

    private fun setPreparingD3(active: Boolean, reason: String) {
        if (preparingD3 == active) return
        preparingD3 = active
        Log.w(TAG, "[$reason] projectionPreparingD3=$active")
        DisplayAppLauncher.notifyDisplayStateChanged(3)
    }

    private fun transitionTo(next: CarPlayDisplayState, reason: String) {
        val previous = currentState
        if (previous == next) return
        currentState = next
        Log.w(TAG, "[$reason] state $previous -> $next")
    }

    private fun isTransitionState(state: CarPlayDisplayState): Boolean {
        return state == CarPlayDisplayState.PREPARING_D3 ||
                state == CarPlayDisplayState.RETURNING_TO_D0
    }
}
