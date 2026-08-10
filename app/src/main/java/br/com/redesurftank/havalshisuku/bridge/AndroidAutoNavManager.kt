package br.com.redesurftank.havalshisuku.bridge

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import com.ts.androidauto.sdk.aidl.data.IfNavigationDestDistanceData
import com.ts.androidauto.sdk.aidl.data.IfNavigationPositionData
import com.ts.androidauto.sdk.aidl.data.IfNavigationStateData
import com.ts.androidauto.sdk.aidl.data.IfNavigationStepData
import org.json.JSONArray
import org.json.JSONObject

/**
 * Captura PASSIVA dos dados de navegação (Waze / Google Maps / etc.) projetados via Android Auto.
 *
 * O host do AA (`com.ts.androidauto.projectionservice`) entrega os dados ESTRUTURADOS de navegação
 * por callbacks de um `LinkCallback` (AIDL `com.ts.androidauto.sdk.aidl.LinkCallback`). Registramos
 * o nosso callback via `LinkCommand.addLinkCallback` (transação 1) reaproveitando o binder que o
 * [br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher] já mantém vivo. Recebemos:
 *   - onNavigationCurrentPosition (txn 11): distância/tempo até a PRÓXIMA manobra + a lista
 *     [IfNavigationDestDistanceData] com ETA + distância/tempo até o DESTINO.
 *   - onNavigationState (txn 10): passos (próxima manobra: tipo, via, ângulo, faixas) + destino.
 *   - onNotifyNavigationState (txn 7): 1 = navegando; qualquer outro = parou (limpa).
 *
 * O snapshot é exposto como JSON via [getDirectionsJson] em
 * [VirtualTelemetryManager] -> canal `app.navigation.directions`, consumido pelo tema/dashboard.
 *
 * É SÓ LEITURA e não interfere no cluster: o host itera uma lista de callbacks, então o nosso
 * callback convive com o do próprio cluster (que continua recebendo os mesmos eventos).
 */
object AndroidAutoNavManager {
    private const val TAG = "AANavCapture"

    /** Descriptor do LinkCallback — precisa bater exatamente com o do host. */
    const val LINK_CALLBACK_DESCRIPTOR = "com.ts.androidauto.sdk.aidl.LinkCallback"

    // Códigos de transação que o host chama NO nosso callback (LinkCallback.Stub).
    private const val TXN_ON_NOTIFY_NAV_STATE = 7
    private const val TXN_ON_NAV_STATE = 10
    private const val TXN_ON_NAV_POSITION = 11

    private const val EMPTY = "{}"

    private val lock = Any()

    // ---- estado retido (mesclado dos dois callbacks) ----
    @Volatile private var active = false
    private var destinations: List<String>? = null
    private var currentRoad: String? = null
    // próxima manobra (de onNavigationState.stepData[0])
    private var stepEvent = -1
    private var stepRoad: String? = null
    private var stepTurnAngle = -1
    private var stepTurnNumber = -1
    private var stepLanes: JSONArray? = null
    // distância/tempo até a próxima manobra (de onNavigationCurrentPosition)
    private var nextMeters = 0
    private var nextValue: String? = null
    private var nextUnit = 0
    private var nextTimeSeconds = 0L
    // distância/tempo/ETA até o destino (de onNavigationCurrentPosition.destDistance[0])
    private var destMeters = 0
    private var destValue: String? = null
    private var destUnit = 0
    private var destEta: String? = null
    private var destTimeSeconds = 0L

    @Volatile private var directionsJson: String = EMPTY

    /** Toggle (default ON — captura passiva e aditiva). */
    fun isEnabled(): Boolean {
        return try {
            App.getDeviceProtectedContext()
                .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
                .getBoolean(SharedPreferencesKeys.ENABLE_AA_NAV_CAPTURE.key, true)
        } catch (e: Exception) {
            true
        }
    }

    /** JSON snapshot consumido pelo sink `app.navigation.directions`. */
    fun getDirectionsJson(): String = if (isEnabled()) directionsJson else EMPTY

    /** Binder que registramos no host via `LinkCommand.addLinkCallback`. */
    val callbackBinder: IBinder by lazy { NavLinkCallback() }

    // ---- handlers dos callbacks ----

    private fun handleNotifyNavState(status: Int) {
        if (status == 1) {
            synchronized(lock) { active = true; rebuild() }
        } else {
            clear()
        }
    }

    private fun handleNavState(s: IfNavigationStateData?) {
        if (s == null) { clear(); return }
        synchronized(lock) {
            active = true
            destinations = s.navigationDestinations
            val steps = s.stepData
            val first: IfNavigationStepData? = if (!steps.isNullOrEmpty()) steps[0] else null
            if (first != null) {
                stepEvent = first.event
                stepRoad = first.road
                stepTurnAngle = first.turnAngle
                stepTurnNumber = first.turnNumber
                stepLanes = buildLanes(first)
            }
            rebuild()
        }
    }

    private fun handleNavPosition(p: IfNavigationPositionData?) {
        if (p == null) { clear(); return }
        synchronized(lock) {
            active = true
            nextMeters = p.meters
            nextValue = p.displayValue
            nextUnit = p.displayUnits
            nextTimeSeconds = p.timeSeconds
            currentRoad = p.currentRoad
            val dests = p.ifNavigationDestDistanceData
            val d: IfNavigationDestDistanceData? = if (!dests.isNullOrEmpty()) dests[0] else null
            if (d != null) {
                destMeters = d.meters
                destValue = d.displayValue
                destUnit = d.displayUnits
                destEta = d.estimatedTime
                destTimeSeconds = d.timeSeconds
            }
            rebuild()
        }
    }

    private fun buildLanes(step: IfNavigationStepData): JSONArray? {
        val lanes = step.laneData ?: return null
        val arr = JSONArray()
        for (lane in lanes) {
            val dirs = lane?.ifNavigationDirectionData ?: continue
            for (dir in dirs) {
                if (dir == null) continue
                arr.put(JSONObject().apply {
                    put("shape", dir.shape)
                    put("highlighted", dir.highlighted)
                })
            }
        }
        return if (arr.length() > 0) arr else null
    }

    private fun clear() {
        synchronized(lock) {
            active = false
            destinations = null; currentRoad = null
            stepEvent = -1; stepRoad = null; stepTurnAngle = -1; stepTurnNumber = -1; stepLanes = null
            nextMeters = 0; nextValue = null; nextUnit = 0; nextTimeSeconds = 0L
            destMeters = 0; destValue = null; destUnit = 0; destEta = null; destTimeSeconds = 0L
            directionsJson = EMPTY
        }
    }

    /** Recompõe o JSON a partir do estado retido. Deve ser chamado sob [lock]. */
    private fun rebuild() {
        if (!active) { directionsJson = EMPTY; return }
        try {
            val o = JSONObject()
            o.put("active", true)
            destinations?.let { list ->
                if (list.isNotEmpty()) {
                    o.put("destination", list[0] ?: "")
                    o.put("destinations", JSONArray(list))
                }
            }
            currentRoad?.let { if (it.isNotEmpty()) o.put("currentRoad", it) }
            // destino: ETA + distância/tempo restante
            destEta?.let { if (it.isNotEmpty()) o.put("eta", it) }
            if (destTimeSeconds > 0L) o.put("remainingSeconds", destTimeSeconds)
            if (destMeters > 0) o.put("remainingMeters", destMeters)
            destValue?.let { if (it.isNotEmpty()) o.put("remainingText", it) }
            if (destUnit != 0) o.put("remainingUnit", unitLabel(destUnit))
            // próxima manobra
            if (stepEvent >= 0 || nextMeters > 0) {
                val n = JSONObject()
                if (stepEvent >= 0) {
                    n.put("event", stepEvent)
                    n.put("icon", maneuverIcon(stepEvent))
                }
                stepRoad?.let { if (it.isNotEmpty()) n.put("road", it) }
                if (stepTurnAngle >= 0) n.put("turnAngle", stepTurnAngle)
                if (stepTurnNumber > 0) n.put("turnNumber", stepTurnNumber)
                if (nextMeters > 0) n.put("distanceMeters", nextMeters)
                nextValue?.let { if (it.isNotEmpty()) n.put("distanceText", it) }
                if (nextUnit != 0) n.put("distanceUnit", unitLabel(nextUnit))
                if (nextTimeSeconds > 0L) n.put("timeSeconds", nextTimeSeconds)
                stepLanes?.let { n.put("lanes", it) }
                o.put("next", n)
            }
            directionsJson = o.toString()
        } catch (e: Exception) {
            Log.e(TAG, "rebuild failed", e)
        }
    }

    private fun unitLabel(u: Int): String = when (u) {
        1 -> "m"
        2, 3 -> "km"
        4, 5 -> "mi"
        6 -> "ft"
        7 -> "yd"
        else -> ""
    }

    /**
     * Mapa do tipo de manobra (`mEvent`) -> rótulo de ícone, portado da tabela do host
     * (NavigationProxy.initWhiteBitMap). 32-35 = rotatória (o ângulo detalha o setor).
     */
    private fun maneuverIcon(event: Int): String = MANEUVER_ICONS[event] ?: when (event) {
        32, 33, 34, 35 -> "roundabout"
        else -> "unknown"
    }

    private val MANEUVER_ICONS: Map<Int, String> = mapOf(
        1 to "depart", 2 to "name_change", 3 to "keep_l", 4 to "keep_r",
        5 to "slight_turn_l", 6 to "slight_turn_r", 7 to "turn_l", 8 to "turn_r",
        9 to "sharp_turn_l", 10 to "sharp_turn_r", 11 to "u_turn_l", 12 to "u_turn_r",
        13 to "slight_turn_l", 14 to "slight_turn_r", 15 to "turn_l", 16 to "turn_r",
        17 to "sharp_turn_l", 18 to "sharp_turn_r", 19 to "u_turn_l", 20 to "u_turn_r",
        21 to "slight_turn_l", 22 to "slight_turn_r", 23 to "turn_l", 24 to "turn_r",
        25 to "fork_l", 26 to "fork_r", 27 to "merge", 28 to "merge", 29 to "merge",
        36 to "name_change", 37 to "ferry_boat", 39 to "destination", 40 to "destination",
        41 to "destination", 42 to "destination", 43 to "roundabout_enter_l",
        44 to "roundabout_exit_l", 45 to "roundabout_enter_r", 46 to "roundabout_exit_r",
        47 to "ferry_boat", 48 to "ferry_boat"
    )

    /**
     * Binder local registrado no host como um `LinkCallback`. Implementa [IInterface] só pra
     * `attachInterface`/`queryLocalInterface` (espelha o Stub gerado). Só tratamos os callbacks de
     * navegação; o resto cai no super. Callbacks do host são ONEWAY, então não escrevemos reply.
     */
    private class NavLinkCallback : Binder(), IInterface {
        init { attachInterface(this, LINK_CALLBACK_DESCRIPTOR) }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return try {
                when (code) {
                    TXN_ON_NOTIFY_NAV_STATE -> {
                        data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                        handleNotifyNavState(data.readInt())
                        true
                    }
                    TXN_ON_NAV_STATE -> {
                        data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                        val s = if (data.readInt() != 0)
                            IfNavigationStateData.CREATOR.createFromParcel(data) else null
                        handleNavState(s)
                        true
                    }
                    TXN_ON_NAV_POSITION -> {
                        data.enforceInterface(LINK_CALLBACK_DESCRIPTOR)
                        val p = if (data.readInt() != 0)
                            IfNavigationPositionData.CREATOR.createFromParcel(data) else null
                        handleNavPosition(p)
                        true
                    }
                    else -> super.onTransact(code, data, reply, flags)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onTransact code=$code failed", e)
                true
            }
        }
    }
}
