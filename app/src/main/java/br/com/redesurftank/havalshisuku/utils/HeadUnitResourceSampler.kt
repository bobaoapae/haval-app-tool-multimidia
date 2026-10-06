package br.com.redesurftank.havalshisuku.utils

import br.com.redesurftank.havalshisuku.projectors.ClusterPerfEventLogger
import java.io.File

/**
 * Amostra uso de recursos do head unit para o indicador da barra estendida.
 *
 * CUSTO: só leitura de arquivo em /proc — SEM shell (`top`/`dumpsys` custariam um processo cada, que
 * é exatamente o gargalo dominante que o mapeamento de performance apontou). Reusa os parsers já
 * testados de [ClusterPerfEventLogger] (parseProcStatLine/calculateCpuDelta) em vez de duplicar.
 *
 * GPU não entra aqui de propósito: este Android roda como VM convidada (`ro.hardware=msmnile_gvmq`)
 * e o hypervisor não expõe contador de GPU — verificado no carro, inclusive com root:
 * /sys/class/kgsl, /sys/class/devfreq, /sys/kernel/gpu e /d/kgsl não existem.
 *
 * Chamar SEMPRE fora da main thread. A primeira chamada devolve cpuPct=null (o % de CPU exige duas
 * amostras para calcular a variação); as seguintes vêm completas.
 */
object HeadUnitResourceSampler {

    data class Snapshot(
            /** CPU do SISTEMA em uso (0..100). Null só na primeira amostra. */
            val cpuPct: Int?,
            /** RAM total do head unit em uso (0..100). */
            val ramPct: Int?,
            /** Residente do NOSSO processo, em MB — vigia vazamento/re-create do serviço. */
            val appRamMb: Int?
    )

    private val lock = Any()
    private var lastTotalJiffies = 0L
    private var lastIdleJiffies = 0L
    private var hasPreviousSample = false

    fun sample(): Snapshot {
        return Snapshot(
                cpuPct = readSystemCpuPct(),
                ramPct = readSystemRamPct(),
                appRamMb = readAppResidentMb()
        )
    }

    /** Reseta a base do cálculo de CPU (usar quando o indicador sai de cena e volta). */
    fun reset() {
        synchronized(lock) { hasPreviousSample = false }
    }

    private fun readSystemCpuPct(): Int? {
        val line =
                runCatching {
                            File("/proc/stat").useLines { lines ->
                                lines.firstOrNull { it.startsWith("cpu ") }
                            }
                        }
                        .getOrNull()
                        ?: return null
        val totals = ClusterPerfEventLogger.parseProcStatLine(line) ?: return null
        synchronized(lock) {
            val previousTotal = lastTotalJiffies
            val previousIdle = lastIdleJiffies
            val had = hasPreviousSample
            lastTotalJiffies = totals.totalJiffies
            lastIdleJiffies = totals.idleJiffies
            hasPreviousSample = true
            if (!had) return null
            val totalDelta = totals.totalJiffies - previousTotal
            val idleDelta = totals.idleJiffies - previousIdle
            if (totalDelta <= 0L || idleDelta < 0L) return null
            val busy = (totalDelta - idleDelta).toDouble() / totalDelta.toDouble() * 100.0
            return busy.toInt().coerceIn(0, 100)
        }
    }

    private fun readSystemRamPct(): Int? {
        val info =
                runCatching {
                            val values = HashMap<String, Long>(4)
                            File("/proc/meminfo").useLines { lines ->
                                for (line in lines) {
                                    val key =
                                            when {
                                                line.startsWith("MemTotal:") -> "total"
                                                line.startsWith("MemAvailable:") -> "available"
                                                else -> null
                                            }
                                                    ?: continue
                                    values[key] = parseKbValue(line) ?: continue
                                    if (values.size == 2) break
                                }
                            }
                            values
                        }
                        .getOrNull()
                        ?: return null
        val total = info["total"]?.takeIf { it > 0L } ?: return null
        val available = info["available"] ?: return null
        val used = (total - available).coerceAtLeast(0L)
        return (used.toDouble() / total.toDouble() * 100.0).toInt().coerceIn(0, 100)
    }

    /**
     * VmRSS de /proc/self/status. Usa RSS e NÃO Debug.getMemoryInfo de propósito: o PSS do
     * getMemoryInfo custa dezenas de ms (varre os mapeamentos), inaceitável num indicador que
     * atualiza a cada segundo.
     */
    private fun readAppResidentMb(): Int? {
        val kb =
                runCatching {
                            File("/proc/self/status").useLines { lines ->
                                lines.firstOrNull { it.startsWith("VmRSS:") }
                            }
                        }
                        .getOrNull()
                        ?.let { parseKbValue(it) }
                        ?: return null
        return (kb / 1024L).toInt().coerceAtLeast(0)
    }

    /** "MemTotal:        6424112 kB" -> 6424112 */
    private fun parseKbValue(line: String): Long? {
        return line.split(Regex("\\s+")).firstNotNullOfOrNull { it.toLongOrNull() }
    }
}
