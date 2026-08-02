package br.com.redesurftank.havalshisuku.managers

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.util.Calendar

/**
 * Controle dos dados MÓVEIS (4G) do carro. O usuário roteia tudo por WiFi (Starlink/celular) e não
 * quer que a multimídia gaste o pacote com apps em segundo plano.
 *
 * Modelo: um MASTER (isControlEnabled) liga/desliga todo o controle. Com ele ligado, um conjunto de
 * REGRAS (OR) decide se o 4G da tela fica cortado — tudo converge no avaliador central
 * [computeDesiredBlocked], que aplica `svc data disable/enable` UMA vez (só quando o estado muda):
 *   a) manual (corta agora, independente do resto)
 *   b) por consumo (>= teto no ciclo)
 *   c) quando conectado ao WiFi
 *   d) quando no Android Auto/CarPlay (projeção)
 *
 * Contador: uso móvel no ciclo (dia de renovação configurável) via NetworkStatsManager; fallback pra
 * TrafficStats acumulado quando o NSM não estiver disponível. (A fatura inclui o TBOX, que a tela não
 * mede — o contador é só a fatia da multimídia.)
 */
object MobileDataManager {
    private const val TAG = "MobileDataManager"
    private const val MB = 1024L * 1024L

    private fun prefs(): SharedPreferences =
            App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    // ================= Master + regras (toggles) =================
    fun isControlEnabled(): Boolean =
            prefs().getBoolean(SharedPreferencesKeys.MOBILE_DATA_CONTROL_ENABLED.key, false)
    fun setControlEnabled(enabled: Boolean) = setRule(SharedPreferencesKeys.MOBILE_DATA_CONTROL_ENABLED.key, enabled)

    /** (a) Bloqueio manual imediato. */
    fun isManualBlock(): Boolean =
            prefs().getBoolean(SharedPreferencesKeys.BLOCK_CAR_MOBILE_DATA.key, false)
    fun setManualBlock(b: Boolean) = setRule(SharedPreferencesKeys.BLOCK_CAR_MOBILE_DATA.key, b)

    /** (b) Bloqueio por consumo (>= teto no ciclo). */
    fun isAutoblockEnabled(): Boolean =
            prefs().getBoolean(SharedPreferencesKeys.MOBILE_DATA_AUTOBLOCK.key, false)
    fun setAutoblockEnabled(b: Boolean) = setRule(SharedPreferencesKeys.MOBILE_DATA_AUTOBLOCK.key, b)

    /** (c) Bloquear quando conectado ao WiFi. */
    fun isBlockOnWifi(): Boolean =
            prefs().getBoolean(SharedPreferencesKeys.MOBILE_DATA_BLOCK_ON_WIFI.key, false)
    fun setBlockOnWifi(b: Boolean) = setRule(SharedPreferencesKeys.MOBILE_DATA_BLOCK_ON_WIFI.key, b)

    /** (d) Bloquear quando no Android Auto/CarPlay. */
    fun isBlockOnProjection(): Boolean =
            prefs().getBoolean(SharedPreferencesKeys.MOBILE_DATA_BLOCK_ON_PROJECTION.key, false)
    fun setBlockOnProjection(b: Boolean) = setRule(SharedPreferencesKeys.MOBILE_DATA_BLOCK_ON_PROJECTION.key, b)

    private fun setRule(key: String, value: Boolean) {
        prefs().edit().putBoolean(key, value).apply()
        recomputeAndApply(App.getContext())
    }

    // ================= Estado de runtime (WiFi / projeção) =================
    /** WiFi é a rede ativa (default)? No caso do carro, quando a Starlink/celular está no ar, ela é a default. */
    fun isWifiConnected(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        val caps = if (net != null) cm.getNetworkCapabilities(net) else null
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (t: Throwable) {
        false
    }

    /** AA ou CarPlay projetando em qualquer display (main 0 ou cluster 3). */
    fun isProjectionActive(): Boolean = try {
        DisplayAppLauncher.isProjectionMirrorOnDisplay(0) || DisplayAppLauncher.isProjectionMirrorOnDisplay(3)
    } catch (t: Throwable) {
        false
    }

    // ================= Avaliador central =================
    /** Regra que está cortando o 4G agora (pra UI), ou null se não está cortado. */
    fun blockReason(context: Context): String? {
        if (!isControlEnabled()) return null
        if (isManualBlock()) return "manual"
        if (isAutoblockEnabled() &&
                getMobileUsedMbThisCycle(context, System.currentTimeMillis()) >= getAutoblockThresholdMb())
            return "consumo"
        if (isBlockOnWifi() && isWifiConnected(context)) return "WiFi"
        if (isBlockOnProjection() && isProjectionActive()) return "AA/CarPlay"
        return null
    }

    fun computeDesiredBlocked(context: Context): Boolean = blockReason(context) != null

    @Volatile private var lastAppliedBlock: Boolean? = null

    /** Reavalia todas as regras e aplica `svc data` (só se o estado desejado mudou). Boot / toggles / WiFi / periódico. */
    fun recomputeAndApply(context: Context) {
        try {
            applyMobileData(computeDesiredBlocked(context))
        } catch (e: Throwable) {
            Log.w(TAG, "recomputeAndApply falhou: ${e.message}")
        }
    }

    @Synchronized
    private fun applyMobileData(block: Boolean) {
        if (lastAppliedBlock == block) return
        try {
            ShizukuUtils.runCommandAndGetOutput(arrayOf("svc", "data", if (block) "disable" else "enable"))
            lastAppliedBlock = block
            Log.w(TAG, "Dado movel do carro -> ${if (block) "BLOQUEADO" else "liberado"}")

        } catch (e: Exception) {
            Log.e(TAG, "Falha ao aplicar dado movel", e)
        }
    }

    // ================= Telemetria OEM (DataTrack -> nuvem) =================
    private const val DATATRACK_PKG = "com.beantechs.datatrackservice"

    fun isDatatrackBlocked(): Boolean =
            prefs().getBoolean(SharedPreferencesKeys.BLOCK_DATATRACK_TELEMETRY.key, false)

    fun setDatatrackBlocked(blocked: Boolean) {
        prefs().edit().putBoolean(SharedPreferencesKeys.BLOCK_DATATRACK_TELEMETRY.key, blocked).apply()
        applyDatatrackState()
    }

    /**
     * Congela/reativa o servico OEM de telemetria (com.beantechs.datatrackservice -> nuvem) via Shizuku.
     * Reversivel (pm enable). NAO toca no remoto/OTA (operatorcenter/tbox). Reaplicado no boot.
     */
    fun applyDatatrackState() {
        val block = isDatatrackBlocked()
        try {
            if (block) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("am", "force-stop", DATATRACK_PKG))
                ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "disable-user", "--user", "0", DATATRACK_PKG))
            } else {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "enable", DATATRACK_PKG))
            }
            Log.w(TAG, "Telemetria DataTrack -> ${if (block) "CONGELADA" else "reativada"}")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao aplicar estado do DataTrack", e)
        }
    }

    // ================= Config (teto / ciclo) =================
    fun getCycleDay(): Int =
            prefs().getInt(SharedPreferencesKeys.MOBILE_DATA_CYCLE_DAY.key, 1).coerceIn(1, 31)
    /** Teto absoluto (MB) da MULTIMÍDIA (esta tela) em que o auto-bloqueio dispara (default 2 GB). */
    fun getAutoblockCapMb(): Int =
            prefs().getInt(SharedPreferencesKeys.MOBILE_DATA_AUTOBLOCK_CAP_MB.key, 2048).coerceAtLeast(0)
    /**
     * Limiar do auto-bloqueio = teto absoluto da tela. NÃO é % da linha: o eSIM fica no TBOX e a
     * multimídia é só um dos consumidores (via vlan13); a fatura inclui o TBOX, que a tela não mede.
     */
    fun getAutoblockThresholdMb(): Int = getAutoblockCapMb()

    // ================= Ciclo (dia de renovação configurável) =================
    /** Início do ciclo atual (dia de renovação, 00:00), em millis. */
    fun getCycleStartMillis(nowMillis: Long): Long {
        val day = getCycleDay()
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        val today = cal.get(Calendar.DAY_OF_MONTH)
        // se hoje ainda nao chegou no dia de renovacao, o ciclo comecou no mes passado
        if (today < day) cal.add(Calendar.MONTH, -1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH) // ex.: dia 31 em fev -> ultimo dia
        cal.set(Calendar.DAY_OF_MONTH, minOf(day, maxDay))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Tag do ciclo (ex.: "2026-7"), pra saber quando resetar o acumulador do fallback. */
    fun getCycleTag(nowMillis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = getCycleStartMillis(nowMillis)
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}"
    }

    // ================= Uso =================
    /** Bytes moveis usados no ciclo. NetworkStatsManager (por range); fallback TrafficStats acumulado. */
    fun getMobileUsedBytesThisCycle(context: Context, nowMillis: Long): Long {
        val start = getCycleStartMillis(nowMillis)
        try {
            val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            if (nsm != null) {
                val bucket = nsm.querySummaryForDevice(
                        ConnectivityManager.TYPE_MOBILE, null, start, nowMillis)
                if (bucket != null) {
                    val total = bucket.rxBytes + bucket.txBytes
                    if (total >= 0) return total
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "NetworkStatsManager indisponivel (${e.message}); fallback TrafficStats")
        }
        return getTrafficStatsAccumBytes(nowMillis)
    }

    fun getMobileUsedMbThisCycle(context: Context, nowMillis: Long): Long =
            getMobileUsedBytesThisCycle(context, nowMillis) / MB

    /** Acumula deltas do TrafficStats movel entre chamadas (robusto a reboot), resetando por ciclo. */
    @Synchronized
    fun getTrafficStatsAccumBytes(nowMillis: Long): Long {
        val p = prefs()
        val cycleTag = getCycleTag(nowMillis)
        var accum = p.getLong(SharedPreferencesKeys.MOBILE_DATA_TRAFFIC_ACCUM_BYTES.key, 0L)
        var last = p.getLong(SharedPreferencesKeys.MOBILE_DATA_TRAFFIC_LAST_READING.key, -1L)
        if (p.getString(SharedPreferencesKeys.MOBILE_DATA_TRAFFIC_CYCLE_TAG.key, "") != cycleTag) {
            accum = 0L
            last = -1L
        }
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()
        val current = if (rx < 0 || tx < 0) -1L else rx + tx // UNSUPPORTED em alguns aparelhos
        if (current >= 0) {
            if (last in 0..current) accum += (current - last) // delta normal
            else if (last > current) accum += current // reboot (contador do SO zerou)
            last = current
        }
        p.edit()
                .putString(SharedPreferencesKeys.MOBILE_DATA_TRAFFIC_CYCLE_TAG.key, cycleTag)
                .putLong(SharedPreferencesKeys.MOBILE_DATA_TRAFFIC_ACCUM_BYTES.key, accum)
                .putLong(SharedPreferencesKeys.MOBILE_DATA_TRAFFIC_LAST_READING.key, last)
                .apply()
        return accum
    }

    /** Concede a permissao de uso de rede via Shizuku (pro NetworkStatsManager). Best-effort. */
    fun ensureUsageStatsPermission(context: Context) {
        try {
            val pkg = context.packageName
            ShizukuUtils.runCommandAndGetOutput(arrayOf("appops", "set", pkg, "GET_USAGE_STATS", "allow"))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "grant", pkg, "android.permission.PACKAGE_USAGE_STATS"))
            ShizukuUtils.runCommandAndGetOutput(arrayOf("pm", "grant", pkg, "android.permission.READ_NETWORK_USAGE_HISTORY"))
        } catch (e: Exception) {
            Log.w(TAG, "ensureUsageStatsPermission: ${e.message}")
        }
    }
}
