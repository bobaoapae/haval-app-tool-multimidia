package br.com.redesurftank.havalshisuku.gestures

import android.content.Context
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Action
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.EvdevTouchParser
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Geometry
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.Step
import br.com.redesurftank.havalshisuku.gestures.TouchGestureLogic.SwipeGestureRecognizer
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.CommandListener
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import java.util.Locale

/**
 * Gestos de tres dedos que valem em QUALQUER tela do carro.
 *
 * ## Por que ler `/dev/input` em vez de usar uma janela
 *
 * Uma janela overlay so tem dois estados uteis: ou ela e `NOT_TOUCHABLE` e nao ve toque nenhum, ou
 * ela e tocavel e ENGOLE o toque do app de baixo. Nao existe "ver e deixar passar". Como o gesto
 * precisa funcionar por cima do Waze, do Spotify e da UI da montadora sem quebrar nenhum deles, a
 * saida e ler o mesmo dispositivo de entrada que o Android le — passivamente, sem `EVIOCGRAB`.
 * Quem estiver na frente continua recebendo o toque exatamente como antes.
 *
 * O preco disso e que o app de baixo TAMBEM reage ao arraste. Por isso sao tres dedos: praticamente
 * nenhum app usa esse numero, entao nao ha disputa. Com dois, um arraste na metade esquerda com o
 * mapa projetado arrastaria o mapa e mexeria na temperatura ao mesmo tempo.
 *
 * ## Mapa dos gestos
 *
 * | Arraste (3 dedos) | Onde comeca      | Ajusta                     |
 * |-------------------|------------------|----------------------------|
 * | vertical          | terco esquerdo   | temperatura do motorista   |
 * | vertical          | terco central    | volume da midia            |
 * | vertical          | terco direito    | temperatura do passageiro  |
 * | horizontal        | tela toda        | ventilacao (fan)           |
 *
 * Para cima / para a direita aumenta.
 */
object ScreenGestureManager {

    private const val TAG = "ScreenGestures"

    /** Faixa de temperatura aceita pelo HVAC (a mesma do card de A/C do dashboard). */
    private const val TEMP_MIN = 16.0f
    private const val TEMP_MAX = 32.0f
    private const val TEMP_STEP = 0.5f
    private const val FAN_MAX = 7

    /**
     * Faixa do volume quando o carro nao publica a dele.
     *
     * NAO e o STREAM_MUSIC do Android (esse vai ate 15 e fica cravado no maximo — a montadora
     * atenua por conta propria, entao `adjustStreamVolume` nao muda nada que se ouca). O volume de
     * verdade e a chave `sys.settings.audio.media_volume`, a mesma que a funcao de volume inicial
     * escreve, e vai de 0 a 40.
     */
    private const val DEFAULT_VOLUME_MAX = 40

    /** Limites do ajuste de sensibilidade, em milesimos de altura de tela por passo. */
    val STEP_RANGE = GestureSensitivity.STEP_RANGE

    /** Depois disso, o valor guardado nao vale mais: alguem pode ter mexido pelo painel do carro. */
    private const val CACHE_TTL_MS = 3_000L

    private const val COLOR_VOLUME = 0xFF4A9EFF.toInt()
    private const val COLOR_HEATING = 0xFFFF7043.toInt()
    private const val COLOR_COOLING = 0xFF6FD3FF.toInt()
    private const val COLOR_FAN = 0xFF4DD0E1.toInt()

    private val parser = EvdevTouchParser()
    /** Recriado a cada start: a proporcao da tela so e conhecida depois de achar o digitalizador. */
    @Volatile private var recognizer = SwipeGestureRecognizer()

    @Volatile private var enabled = false
    @Volatile private var running = false
    /** Sobe a cada start/stop: o leitor antigo confere e se cala quando fica pra tras. */
    @Volatile private var generation = 0
    @Volatile private var geometry: Geometry? = null
    @Volatile private var readerPid: String? = null

    private val driverTemp = NumericChannel(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value)
    private val passengerTemp = NumericChannel(CarConstants.CAR_HVAC_PASS_TEMPERATURE.value)
    private val fan = NumericChannel(CarConstants.CAR_HVAC_FAN_SPEED.value)
    private val mediaVolume = NumericChannel(CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.value)

    /** 0 = ainda nao perguntamos ao carro. */
    @Volatile private var volumeMax = 0

    // ---------------------------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------------------------

    /** Chamado no boot do servico. Liga so se a preferencia mandar. */
    fun onServicesReady() {
        setEnabled(prefEnabled())
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        // Fora da thread de quem chamou: ligar passa por `getevent -pl` via Shizuku, que e uma
        // chamada BLOQUEANTE. Como isto vem da tela de configuracao, fazer isso na thread da UI
        // seria um ANR esperando acontecer.
        runOffMainThread { if (value) startBlocking() else stopBlocking() }
    }

    private fun runOffMainThread(block: () -> Unit) {
        Thread {
                runCatching(block)
                    .onFailure { Log.e(TAG, "falhou ao (re)configurar os gestos", it) }
            }
            .start()
    }

    private fun prefEnabled(): Boolean =
        runCatching {
                App.getDeviceProtectedContext()
                    .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
                    .getBoolean(SharedPreferencesKeys.ENABLE_SCREEN_GESTURES.key, false)
            }
            .getOrDefault(false)

    @Synchronized
    private fun startBlocking() {
        if (running) return
        if (!ShizukuUtils.isShizukuAvailable()) {
            Log.w(TAG, "Shizuku fora; gestos ficam pra quando ele subir")
            return
        }
        val device = findTouchDevice()
        if (device == null) {
            Log.e(TAG, "nenhum dispositivo multitoque encontrado; gestos desligados")
            return
        }
        geometry = buildGeometry(device)
        running = true
        val myGeneration = ++generation
        parser.reset()
        recognizer = SwipeGestureRecognizer(buildConfig(), loadBindings())
        killLeftoverReader()
        Log.w(TAG, "lendo o toque de ${device.path} (${device.maxX}x${device.maxY})")
        startReader(device.path, myGeneration)
    }

    @Synchronized
    private fun stopBlocking() {
        if (!running) return
        running = false
        generation++
        killLeftoverReader()
        GestureFeedbackOverlay.hide()
        Log.w(TAG, "gestos de tela desligados")
    }

    /**
     * `echo $$; exec getevent` e o truque que da um cabo pro processo.
     *
     * O `runCommandOnBackground` do Shizuku nao devolve handle nenhum — sem isso nao haveria como
     * parar o leitor depois, e desligar a funcao deixaria um `getevent` rodando pra sempre. Com o
     * `exec`, o shell VIRA o getevent, entao o pid que ele imprimiu na primeira linha e o pid certo
     * pra matar.
     */
    private fun startReader(devicePath: String, myGeneration: Int) {
        val command = arrayOf("sh", "-c", "echo $$; exec getevent -l $devicePath")
        ShizukuUtils.runCommandOnBackground(
            command,
            object : CommandListener {
                private var gotPid = false

                override fun onStdout(line: String) {
                    if (myGeneration != generation) return
                    if (!gotPid) {
                        gotPid = true
                        val pid = line.trim()
                        if (pid.toIntOrNull() != null) {
                            readerPid = pid
                            rememberReaderPid(pid)
                            return
                        }
                    }
                    handleLine(line)
                }

                override fun onStderr(line: String) {
                    Log.w(TAG, "getevent: $line")
                }

                override fun onFinished(exitCode: Int) {
                    if (myGeneration != generation) return
                    Log.w(TAG, "leitor de toque terminou (code=$exitCode)")
                    running = false
                    readerPid = null
                    // Morte inesperada com a funcao ligada: tenta de novo, sem martelar.
                    if (enabled) {
                        Thread {
                                runCatching { Thread.sleep(5_000) }
                                if (enabled && myGeneration == generation) startBlocking()
                            }
                            .start()
                    }
                }
            },
        )
    }

    private fun handleLine(line: String) {
        val frame = parser.feed(line) ?: return
        val geo = geometry ?: return
        val steps = recognizer.onFrame(frame.fingers.map { geo.normalize(it) })
        if (steps.isEmpty()) return
        for (step in steps) apply(step)
    }

    // ---------------------------------------------------------------------------------------
    // Descoberta do dispositivo
    // ---------------------------------------------------------------------------------------

    /**
     * Monta a conversao "coordenada crua -> coordenada da tela".
     *
     * A troca de eixos sai das proporcoes (um painel montado de lado reporta retrato onde o display
     * e paisagem). O espelhamento nao da pra deduzir, entao fica em duas preferencias: se o gesto
     * responder ao contrario no carro, basta virar a chave por shell — sem build novo.
     */
    private fun buildGeometry(device: TouchGestureLogic.TouchDevice): Geometry {
        val metrics = App.getContext().resources.displayMetrics
        val swap =
            TouchGestureLogic.needsAxisSwap(
                device.maxX,
                device.maxY,
                metrics.widthPixels,
                metrics.heightPixels,
            )
        val p = prefs()
        return Geometry(
            maxX = device.maxX,
            maxY = device.maxY,
            swapXY = swap,
            invertX = p.getBoolean(SharedPreferencesKeys.SCREEN_GESTURE_INVERT_X.key, false),
            invertY = p.getBoolean(SharedPreferencesKeys.SCREEN_GESTURE_INVERT_Y.key, false),
        )
    }

    private fun findTouchDevice(): TouchGestureLogic.TouchDevice? =
        runCatching {
                val out = ShizukuUtils.runCommandAndGetOutput(arrayOf("getevent", "-pl"))
                TouchGestureLogic.parseTouchDevice(out.orEmpty())
            }
            .getOrNull()

    /**
     * Mata um leitor que tenha sobrado.
     *
     * Se o app for morto no meio do caminho, o `getevent` continua vivo do lado do Shizuku — e um
     * leitor orfao a mais a cada reinicio. Guardar o pid nas preferencias e o que permite limpar
     * isso mesmo depois de o app inteiro ter reiniciado.
     */
    private fun killLeftoverReader() {
        val pid = readerPid ?: rememberedReaderPid()
        readerPid = null
        rememberReaderPid(null)
        if (pid.isNullOrBlank()) return
        runCatching { ShizukuUtils.runCommandAndGetOutput(arrayOf("kill", pid)) }
    }

    private fun prefs() =
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    private fun rememberReaderPid(pid: String?) {
        runCatching {
            prefs().edit().apply {
                if (pid == null) remove(READER_PID_KEY) else putString(READER_PID_KEY, pid)
                apply()
            }
        }
    }

    private fun rememberedReaderPid(): String? =
        runCatching { prefs().getString(READER_PID_KEY, null) }.getOrNull()

    private const val READER_PID_KEY = "screenGestureReaderPid"

    // ---------------------------------------------------------------------------------------
    // Aplicacao dos passos
    // ---------------------------------------------------------------------------------------

    private fun apply(step: Step) {
        runCatching {
                when (step.action) {
                    Action.VOLUME -> adjustVolume(step.delta)
                    Action.DRIVER_TEMP -> adjustTemperature(driverTemp, "Motorista", step.delta)
                    Action.PASSENGER_TEMP -> adjustTemperature(passengerTemp, "Passageiro", step.delta)
                    Action.FAN -> adjustFan(step.delta)
                }
            }
            .onFailure { Log.e(TAG, "falhou ao aplicar ${step.action}", it) }
    }

    /**
     * Volume pelo canal do CARRO, nao pelo AudioManager.
     *
     * A primeira versao usava `adjustStreamVolume(STREAM_MUSIC, ...)` e nao mudava nada: o log de
     * audio do carro mostrava as chamadas chegando, mas o STREAM_MUSIC tem maximo 15 e a montadora o
     * mantem cravado nesse maximo, fazendo a atenuacao real por fora. O card mostrava "15" fixo. A
     * chave que vale e a mesma que a funcao de volume inicial ja escrevia.
     */
    private fun adjustVolume(delta: Int) {
        val sm = ServiceManager.getInstance() ?: return
        val max = resolveVolumeMax(sm)
        val current = mediaVolume.read(sm, 0f)
        val next = (current.toInt() + delta).coerceIn(0, max)
        if (next.toFloat() == current) return
        mediaVolume.write(sm, next.toFloat(), next.toString())
        GestureFeedbackOverlay.show(
            "Volume",
            "$next",
            next.toFloat() / max,
            COLOR_VOLUME,
            GestureFeedbackOverlay.Motif.VOLUME,
            delta,
        )
    }

    /** O carro publica a propria faixa; so perguntamos uma vez. */
    private fun resolveVolumeMax(sm: ServiceManager): Int {
        val cached = volumeMax
        if (cached > 0) return cached
        val raw =
            runCatching { sm.getData(CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME_RANGE.value) }
                .getOrNull()
        val max = CarVolumeRange.parseMax(raw) ?: DEFAULT_VOLUME_MAX
        volumeMax = max
        Log.w(TAG, "faixa do volume do carro: 0..$max (bruto='$raw')")
        return max
    }

    /**
     * Monta os ajustes do reconhecimento, deixando a sensibilidade vir de preferencia quando ela
     * existir. O padrao do codigo e o ponto de partida; a preferencia so entra pra afinar no carro.
     */
    private fun buildConfig(): TouchGestureLogic.Config {
        val base = TouchGestureLogic.Config(aspect = screenAspect())
        val p = prefs()
        fun step(key: SharedPreferencesKeys, fallback: Float): Float {
            val thousandths = runCatching { p.getInt(key.key, 0) }.getOrDefault(0)
            // Fora dessa faixa nao e ajuste, e engano: passo minusculo dispara centenas de vezes
            // num arraste, e gigante nunca dispara.
            return if (thousandths in STEP_RANGE) thousandths / 1000f else fallback
        }
        return base.copy(
            volumeStep = step(SharedPreferencesKeys.SCREEN_GESTURE_STEP_VOLUME, base.volumeStep),
            temperatureStep =
                step(SharedPreferencesKeys.SCREEN_GESTURE_STEP_TEMPERATURE, base.temperatureStep),
            fanStep = step(SharedPreferencesKeys.SCREEN_GESTURE_STEP_FAN, base.fanStep),
        )
    }

    fun loadBindings(): GestureBindings =
        runCatching {
                GestureBindings.decode(
                    prefs().getString(SharedPreferencesKeys.SCREEN_GESTURE_BINDINGS.key, null)
                )
            }
            .getOrDefault(GestureBindings.DEFAULT)

    /** Grava os vinculos e ja passa a valer. */
    fun saveBindings(bindings: GestureBindings) {
        runCatching {
            prefs()
                .edit()
                .putString(SharedPreferencesKeys.SCREEN_GESTURE_BINDINGS.key, bindings.encode())
                .apply()
        }
        rebuildRecognizer()
    }

    /** Sensibilidade de uma acao, em milesimos de altura de tela por passo. */
    fun saveStepSensitivity(key: SharedPreferencesKeys, thousandths: Int) {
        runCatching { prefs().edit().putInt(key.key, thousandths).apply() }
        rebuildRecognizer()
    }

    fun stepSensitivity(key: SharedPreferencesKeys, fallback: Float): Int {
        val stored = runCatching { prefs().getInt(key.key, 0) }.getOrDefault(0)
        return if (stored in STEP_RANGE) stored else Math.round(fallback * 1000f)
    }

    /**
     * Troca o reconhecedor em memoria, sem mexer no leitor de toque.
     *
     * O reconhecedor guarda os ajustes e as contagens de dedos no construtor, entao mudar a
     * configuracao exige construir outro. Antes isso reiniciava o leitor inteiro — o que significava
     * derrubar e resubir um processo (com `getevent -pl` bloqueante no meio) a cada mexida de
     * slider. Como o campo e `@Volatile` e a thread de leitura so o LE, basta trocar a referencia.
     */
    private fun rebuildRecognizer() {
        if (!running) return
        runCatching { recognizer = SwipeGestureRecognizer(buildConfig(), loadBindings()) }
            .onFailure { Log.e(TAG, "falhou ao reconstruir o reconhecedor", it) }
    }

    /** largura / altura da tela — o reconhecedor precisa disso pra nao deformar os eixos. */
    private fun screenAspect(): Float =
        runCatching {
                val m = App.getContext().resources.displayMetrics
                if (m.heightPixels > 0) m.widthPixels.toFloat() / m.heightPixels else 1f
            }
            .getOrDefault(1f)

    private fun adjustTemperature(channel: NumericChannel, label: String, delta: Int) {
        // A cor acompanha o sentido, nao o assento: subindo puxa pro laranja, descendo pro azul.
        val accent = if (delta > 0) COLOR_HEATING else COLOR_COOLING
        val sm = ServiceManager.getInstance() ?: return
        val current = channel.read(sm, 22.0f)
        val next = (current + delta * TEMP_STEP).coerceIn(TEMP_MIN, TEMP_MAX)
        if (next == current) return
        val text = String.format(Locale.US, "%.1f", next)
        channel.write(sm, next, text)
        GestureFeedbackOverlay.show(
            label,
            "$text°",
            (next - TEMP_MIN) / (TEMP_MAX - TEMP_MIN),
            accent,
            // O desenho conta o SENTIDO: floco quando esfria, chama quando esquenta.
            if (delta > 0) GestureFeedbackOverlay.Motif.HEATING
            else GestureFeedbackOverlay.Motif.COOLING,
            delta,
        )
    }

    private fun adjustFan(delta: Int) {
        val sm = ServiceManager.getInstance() ?: return
        val current = fan.read(sm, 0f)
        val next = (current.toInt() + delta).coerceIn(0, FAN_MAX)
        if (next.toFloat() == current) return
        fan.write(sm, next.toFloat(), next.toString())
        // Mesmo comportamento do card de A/C do dashboard: cruzar o zero liga/desliga o HVAC, senao
        // a ventilacao "zero" fica soprando e o "um" nao sopra nada.
        val power = runCatching { sm.getData(CarConstants.CAR_HVAC_POWER_MODE.value) }.getOrNull()
        if (next == 0 && power == "1") {
            sm.updateData(CarConstants.CAR_HVAC_POWER_MODE.value, "0")
        } else if (next > 0 && power == "0") {
            sm.updateData(CarConstants.CAR_HVAC_POWER_MODE.value, "1")
        }
        GestureFeedbackOverlay.show(
            "Ventilação",
            next.toString(),
            next.toFloat() / FAN_MAX,
            COLOR_FAN,
            GestureFeedbackOverlay.Motif.FAN,
            delta,
        )
    }

    /**
     * Um valor do carro que a gente le, ajusta e escreve de volta.
     *
     * A leitura fica guardada por poucos segundos porque um arraste emite varios passos seguidos e
     * reler o CAN a cada um deixaria o gesto travado. Passado o prazo o valor e relido — se alguem
     * mexeu pelo painel do carro no meio, a gente parte do numero certo.
     */
    private class NumericChannel(val key: String) {
        @Volatile private var cached = Float.NaN
        @Volatile private var cachedAt = 0L

        fun read(sm: ServiceManager, fallback: Float): Float {
            val now = System.currentTimeMillis()
            if (!cached.isNaN() && now - cachedAt < CACHE_TTL_MS) return cached
            val raw = runCatching { sm.getData(key) }.getOrNull()
            val value = raw?.trim()?.toFloatOrNull() ?: fallback
            cached = value
            cachedAt = now
            return value
        }

        fun write(sm: ServiceManager, value: Float, text: String) {
            cached = value
            cachedAt = System.currentTimeMillis()
            sm.updateData(key, text)
        }
    }
}

/**
 * Leitura da faixa de volume que o carro publica em `sys.settings.audio.media_volume_range`.
 *
 * Objeto separado (e sem nada de Android) so pra ser testavel na JVM: o formato exato do valor nao
 * esta documentado em lugar nenhum, entao a leitura precisa aguentar as variacoes plausiveis —
 * "40", "0,40", "(0,40)", "0-40" — em vez de assumir uma.
 */
internal object CarVolumeRange {
    private val NUMBERS = Regex("\\d+")

    /** O MAIOR numero que aparecer; null quando nao da pra confiar no que veio. */
    fun parseMax(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val max = NUMBERS.findAll(raw).mapNotNull { it.value.toIntOrNull() }.maxOrNull() ?: return null
        return if (max >= 1) max else null
    }
}
