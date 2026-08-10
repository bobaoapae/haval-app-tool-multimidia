package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.os.SystemClock
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils

/**
 * FONTE ÚNICA do estado de conectividade do carro (roteamento do hotspot + dados móveis), consumido
 * pelo card unificado da barra estendida (BottomBarImpulseDashboard).
 *
 * A regra de apresentação (texto/cor/ícone) vive só aqui ([buildStatus]). Prioridade:
 *  - hotspot roteando via WLAN externa -> "Roteando · <SSID>" (verde)
 *  - hotspot roteando via 4G           -> "Roteando · 4G" (âmbar; 4G ligado e gastando)
 *  - hotspot subindo / erro/travado    -> "Hotspot · conectando" / "Hotspot · erro"
 *  --- HotRouter off/parado: por ONDE a própria tela (multimídia) está navegando ---
 *  - tela conectada via WiFi           -> "Tela · <SSID>" (verde)
 *  - tela sem WiFi e 4G cortado        -> "4G off · <motivo>" (vermelho)
 *  - tela sem WiFi, navegando pelo 4G  -> "Tela · 4G" (âmbar; gastando o pacote)
 */
object ConnectivityStatusManager {
    private const val CACHE_TTL_MS = 1000L
    private const val WLAN_IF = "wlan0"

    data class Status(
            val hotspotRouting: Boolean,      // HotRouter roteando (mode != OFF)
            val routingMode: String,          // OFF / WLAN / 4G / STARTING / ERROR
            val routingWifiName: String?,     // SSID quando WLAN
            val mobileControlEnabled: Boolean,
            val mobile4gOn: Boolean,          // 4G utilizável (não cortado pelo controle)
            val mobileBlockReason: String?,   // manual / consumo / WiFi / AA/CarPlay (null se não cortado)
            val displayText: String?,         // pronto pra desenhar; null = esconder o card
            val displayLevel: String,         // good | warn | bad | muted
            val displayIcon: String           // satellite | wifi | cell | cell_off | loader | alert
    )

    /** Regras de apresentação — a lógica de texto/cor/ícone pro card da barra. */
    fun buildStatus(
            mode: String,
            wifiName: String?,
            controlEnabled: Boolean,
            blockReason: String?,
            hotspotActive: Boolean,
            headUnitOnWifi: Boolean
    ): Status {
        // "Roteando" só quando o hotspot está de FATO no ar. CUIDADO: o daemon reporta WLAN/4G pelo
        // UPLINK (ele pinga a wlan0), NÃO pelo AP — então mode==WLAN NÃO implica hotspot ligado (a
        // wlan0 pode ter internet com o hotspot desligado). Por isso o gate é SÓ no hotspot real
        // (isHotspotOnAir via carrier do sysfs); senão o card fica "Roteando" à toa / piscando.
        val hotspotOn = hotspotActive
        val routing = mode != HotRouterManager.MODE_OFF && hotspotOn
        val blocked = blockReason != null
        val ssid = wifiName?.takeIf { it.isNotBlank() }
        val text: String?
        val level: String
        val icon: String
        when {
            // --- HotRouter roteando o hotspot: mostra o uplink do roteamento ---
            hotspotOn && mode == HotRouterManager.MODE_WLAN -> {
                text = "Roteando · " + (ssid ?: "WiFi"); level = "good"; icon = "satellite"
            }
            hotspotOn && mode == HotRouterManager.MODE_4G -> {
                if (blocked) {
                    // HotRouter caiu no 4G (Starlink fora), mas o 4G está CORTADO pelo controle -> o
                    // hotspot ficou sem uplink. Honesto: não gasta 4G, não está "roteando" de verdade.
                    text = "Hotspot · 4G off · $blockReason"; level = "bad"; icon = "cell_off"
                } else {
                    text = "Roteando · 4G"; level = "warn"; icon = "cell"
                }
            }
            hotspotOn && mode == HotRouterManager.MODE_STARTING -> {
                text = "Hotspot · conectando"; level = "muted"; icon = "loader"
            }
            hotspotOn && mode == HotRouterManager.MODE_ERROR -> {
                text = "Hotspot · erro"; level = "bad"; icon = "alert"
            }
            // --- HotRouter off/parado: mostra por ONDE a multimídia (tela) navega + o corte do 4G ---
            headUnitOnWifi -> {
                // Tela conectada via WiFi (não gasta o pacote 4G) — SSID quando dá.
                text = "Tela · " + (ssid ?: "WiFi"); level = "good"; icon = "wifi"
            }
            controlEnabled && blocked -> {
                // Sem WiFi e o 4G foi cortado pelo controle -> tela sem internet.
                text = "4G off · $blockReason"; level = "bad"; icon = "cell_off"
            }
            else -> {
                // Sem WiFi e 4G disponível -> tela navegando pelo 4G (gastando o pacote).
                text = "Tela · 4G"; level = "warn"; icon = "cell"
            }
        }
        return Status(routing, mode, ssid, controlEnabled, !blocked, blockReason, text, level, icon)
    }

    /**
     * Hotspot (AP) do carro está de FATO no ar? Delega pro ServiceManager, que lê o carrier do sysfs
     * (wlan2) — confiável neste OEM, onde getWifiApState() retorna -1 (por isso o reflexo cru dava
     * falso-positivo). Fallback pro reflexo só se o ServiceManager ainda não subiu.
     */
    fun isHotspotActive(context: Context): Boolean = try {
        ServiceManager.getInstance().isHotspotOnAir()
    } catch (t: Throwable) {
        try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            (wm?.javaClass?.getMethod("getWifiApState")?.invoke(wm) as? Int) == 13
        } catch (t2: Throwable) {
            false
        }
    }

    @Volatile private var cached: Status? = null
    @Volatile private var cachedAtMs: Long = 0L

    /**
     * Lê tudo (HotRouter via shell + estado do 4G) e monta o Status. Cache curto (1s) pra deduplicar
     * rajadas (a barra e o EcoTrip podem consultar quase juntos). CHAMAR FORA DA MAIN THREAD.
     */
    fun computeFresh(context: Context, useCache: Boolean = true): Status {
        val now = SystemClock.uptimeMillis()
        val c = cached
        if (useCache && c != null && now - cachedAtMs < CACHE_TTL_MS) return c
        val hr = HotRouterManager.getInstance()
        val mode = try { hr.readStatusBlocking().mode } catch (t: Throwable) { HotRouterManager.MODE_OFF }
        val headUnitOnWifi = MobileDataManager.isWifiConnected(context)
        // O mesmo SSID (wlan0) serve pra "Roteando · <SSID>" (WLAN) e pra "Tela · <SSID>" (tela no WiFi).
        val wifi = if (mode == HotRouterManager.MODE_WLAN || headUnitOnWifi) {
            try { readWifiSsidBlocking() } catch (t: Throwable) { null }
        } else null
        val controlEnabled = MobileDataManager.isControlEnabled()
        val reason = MobileDataManager.blockReason(context)
        val hotspotActive = isHotspotActive(context)
        val s = buildStatus(mode, wifi, controlEnabled, reason, hotspotActive, headUnitOnWifi)
        cached = s
        cachedAtMs = now
        return s
    }

    /** Invalida o cache (após uma mudança conhecida) pra a próxima consulta recalcular na hora. */
    fun invalidate() {
        cached = null
    }

    /**
     * Lê o SSID do WiFi conectado (wlan0) via shell root, com fallback entre ferramentas
     * (iw -> wpa_cli -> iwconfig -> dumpsys netstats). Retorna null se não determinar. OFF da main.
     */
    private fun readWifiSsidBlocking(): String? {
        return try {
            // 1) iw dev wlan0 link -> "SSID: <nome>"
            var out = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "iw dev $WLAN_IF link 2>/dev/null"))
            for (line in out.split("\n")) {
                val i = line.indexOf("SSID:")
                if (i >= 0) {
                    val v = line.substring(i + 5).trim()
                    if (v.isNotEmpty()) return v
                }
            }
            // 2) wpa_cli -i wlan0 status -> "ssid=<nome>"
            out = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "wpa_cli -i $WLAN_IF status 2>/dev/null"))
            for (line in out.split("\n")) {
                val t = line.trim()
                if (t.startsWith("ssid=")) {
                    val v = t.substring(5).trim()
                    if (v.isNotEmpty()) return v
                }
            }
            // 3) iwconfig wlan0 -> ESSID:"<nome>"
            out = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "iwconfig $WLAN_IF 2>/dev/null"))
            val q = out.indexOf("ESSID:\"")
            if (q >= 0) {
                val end = out.indexOf('"', q + 7)
                if (end > q + 7) {
                    val v = out.substring(q + 7, end).trim()
                    if (v.isNotEmpty()) return v
                }
            }
            // 4) dumpsys netstats -> networkId="<nome>" (fonte confiável neste head unit)
            out = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "dumpsys netstats 2>/dev/null | grep -m1 networkId"))
            val n = out.indexOf("networkId=\"")
            if (n >= 0) {
                val end2 = out.indexOf('"', n + 11)
                if (end2 > n + 11) {
                    val v2 = out.substring(n + 11, end2).trim()
                    if (v2.isNotEmpty()) return v2
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
