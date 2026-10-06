package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import br.com.redesurftank.App

/**
 * Pede o PIN de saída do Modo Concessionária numa JANELA SOBREPOSTA, não numa Activity.
 *
 * A primeira versão era uma Activity, e no carro ela simplesmente não abria: o Android bloqueia
 * abrir tela a partir de segundo plano, e como o app está escondido no modo, não há Activity em
 * primeiro plano para autorizar. O resultado foi o pior possível — a sequência saía do modo sem
 * pedir PIN nenhum. Uma janela de overlay não passa por essa restrição: é o mesmo mecanismo da
 * barra inferior e do indicador de CPU/RAM, que já sobem sobre qualquer app.
 *
 * Sem marca do app de propósito: quem estiver com o carro vê um teclado numérico, não uma pista de
 * que existe algo instalado.
 */
object StealthPinOverlay {

    private const val TAG = "StealthPinOverlay"

    private var root: View? = null
    private var onSuccess: (() -> Unit)? = null
    private val autoHide = Handler(Looper.getMainLooper())

    /**
     * A janela cobre a tela inteira: se ficar presa, a central vira um tijolo. Some sozinha depois
     * disso, e o dono simplesmente refaz a sequência. Ninguém fica sem central por causa de um
     * teclado esquecido na tela.
     */
    private const val AUTO_HIDE_MS = 90_000L

    @JvmStatic
    fun isShowing(): Boolean = root != null

    /** Mostra o teclado. [onOk] roda quando o PIN confere. */
    @JvmStatic
    fun show(onOk: () -> Unit): Boolean {
        val handler = Handler(Looper.getMainLooper())
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var ok = false
            handler.post { ok = show(onOk) }
            return true // otimista: o post não falha; o resultado real vai pro log
        }
        if (root != null) return true
        onSuccess = onOk
        return try {
            val ctx = App.getContext()
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = buildView(ctx)
            val lp =
                    WindowManager.LayoutParams(
                            // Do tamanho do teclado, centralizado — não a tela inteira. A versão
                            // anterior era MATCH_PARENT com FLAG_LAYOUT_NO_LIMITS, e essa flag deixa
                            // a janela ultrapassar os limites da tela: o resultado foi um painel
                            // preto inteiro com o teclado jogado na esquerda.
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            } else {
                                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                            },
                            // Sem FLAG_NOT_FOCUSABLE: sem foco o toque nos botões não chega.
                            // Sem NO_LIMITS: é ele que tira a janela do enquadramento da tela.
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            android.graphics.PixelFormat.TRANSLUCENT
                    )
            lp.gravity = Gravity.CENTER
            wm.addView(view, lp)
            root = view
            armAutoHide()
            Log.w(TAG, "teclado do PIN na tela")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "não deu pra mostrar o teclado do PIN", t)
            onSuccess = null
            false
        }
    }

    /** (Re)agenda o recolhimento. Chamado a cada toque pra ninguém ter o teclado puxado no meio. */
    private fun armAutoHide() {
        autoHide.removeCallbacksAndMessages(null)
        autoHide.postDelayed({
            if (root != null) {
                Log.w(TAG, "teclado do PIN sem uso; recolhendo pra não travar a central")
                hide()
            }
        }, AUTO_HIDE_MS)
    }

    @JvmStatic
    fun hide() {
        autoHide.removeCallbacksAndMessages(null)
        val view = root ?: return
        root = null
        onSuccess = null
        try {
            val wm = App.getContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (t: Throwable) {
            Log.w(TAG, "falha ao remover o teclado", t)
        }
    }

    private fun dp(v: Int): Int =
            (v * App.getContext().resources.displayMetrics.density).toInt()

    private fun buildView(ctx: Context): View {
        var typed = ""

        val dots = TextView(ctx).apply {
            textSize = 26f
            setTextColor(Color.parseColor("#4CA6FF"))
            letterSpacing = 0.4f
            gravity = Gravity.CENTER
        }
        val message = TextView(ctx).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#FF6B6B"))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val title = TextView(ctx).apply {
            text = "Digite o PIN"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        fun render() {
            dots.text = "•".repeat(typed.length).ifEmpty { " " }
        }
        render()

        val grid = GridLayout(ctx).apply {
            columnCount = 3
            setPadding(0, dp(12), 0, 0)
        }

        fun key(label: String, onClick: () -> Unit): Button =
                Button(ctx).apply {
                    text = label
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(Color.parseColor("#1A1F26"))
                    }
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = dp(78)
                        height = dp(52)
                        setMargins(dp(5), dp(5), dp(5), dp(5))
                    }
                    setOnClickListener {
                        armAutoHide()
                        onClick()
                    }
                }

        fun submit() {
            when (val r = StealthExitPin.verify(typed)) {
                is StealthExitPin.Result.Ok -> {
                    val cb = onSuccess
                    hide()
                    cb?.invoke()
                }
                is StealthExitPin.Result.Wrong -> {
                    typed = ""
                    render()
                    message.text = "PIN incorreto. Restam ${r.remainingFreeAttempts}."
                    message.visibility = View.VISIBLE
                }
                is StealthExitPin.Result.Locked -> {
                    typed = ""
                    render()
                    message.text = "Muitas tentativas. Espere ${r.remainingMs / 1000}s."
                    message.visibility = View.VISIBLE
                }
            }
        }

        for (d in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")) {
            grid.addView(key(d) {
                if (typed.length < StealthExitPin.MAX_LENGTH) {
                    typed += d
                    render()
                    message.visibility = View.GONE
                }
            })
        }
        grid.addView(key("<") {
            if (typed.isNotEmpty()) {
                typed = typed.dropLast(1)
                render()
            }
        })
        grid.addView(key("0") {
            if (typed.length < StealthExitPin.MAX_LENGTH) {
                typed += "0"
                render()
                message.visibility = View.GONE
            }
        })
        grid.addView(key("OK") {
            if (typed.length >= StealthExitPin.MIN_LENGTH) submit()
        })

        val cancel = TextView(ctx).apply {
            text = "Cancelar"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#6B7480"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(4))
            // Só fecha o teclado; o carro continua no modo. É a válvula de escape pra quem abriu
            // sem querer — sem ela, a tela ficaria bloqueada até o prazo automático vencer.
            setOnClickListener { hide() }
        }

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#14181D"))
                // Contorno discreto: sem o fundo escurecido atrás, o painel precisa se destacar
                // sozinho de qualquer tela que estiver embaixo.
                setStroke(dp(1), Color.parseColor("#2C3139"))
            }
            addView(title)
            addView(dots)
            addView(message)
            addView(grid)
            addView(cancel)
        }

        return panel
    }
}
