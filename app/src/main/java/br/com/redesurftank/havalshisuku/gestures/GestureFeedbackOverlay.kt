package br.com.redesurftank.havalshisuku.gestures

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import br.com.redesurftank.App
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * O retorno visual dos gestos: um cartao no centro da tela com o que mudou, o valor novo e um
 * desenho animado do QUE esta acontecendo — gelo esfriando, chama esquentando, helice girando,
 * ondas saindo do alto-falante.
 *
 * Aparece SOBRE qualquer app, por isso e uma janela do WindowManager e nao um Composable nosso.
 * Duas flags fazem toda a diferenca:
 *
 *  - `FLAG_NOT_TOUCHABLE`: sem ela, o cartao engoliria o toque do app de baixo justamente durante o
 *    arraste que o invocou. Com ela, e vidro: desenha e deixa passar.
 *  - `FLAG_NOT_FOCUSABLE`: sem ela, a janela rouba o foco e o app de baixo perde o estado de
 *    entrada por um instante.
 *
 * Tudo e desenhado no Canvas: sem drawable, sem emoji e sem WebView. Nao e purismo — e uma view
 * efemera por cima de outro app, e depender de recurso ou fonte do sistema aqui e o caminho curto
 * pra ganhar um retangulo vazio numa unidade com fonte diferente.
 */
object GestureFeedbackOverlay {

    private const val TAG = "GestureOverlay"
    private const val HIDE_DELAY_MS = 1100L
    private const val POP_MIN_INTERVAL_MS = 140L

    /** O desenho que acompanha cada ajuste. */
    enum class Motif {
        /** Floco de neve girando — temperatura CAINDO. */
        COOLING,
        /** Chama tremulando — temperatura SUBINDO. */
        HEATING,
        /** Helice girando, mais rapido quanto maior a ventilacao. */
        FAN,
        /** Alto-falante com ondas saindo (ou recolhendo). */
        VOLUME,
    }

    private val handler = Handler(Looper.getMainLooper())
    private var view: HudView? = null

    private val hideRunnable = Runnable { hideNow() }

    /**
     * Mostra (ou atualiza) o cartao.
     *
     * @param title o que esta sendo ajustado ("Volume", "Motorista", ...)
     * @param value ja formatado pra leitura ("22.5°", "4", "12")
     * @param fraction 0..1 — onde o valor esta dentro da faixa possivel
     * @param accent cor do realce, uma por tipo de ajuste
     * @param motif o desenho animado
     * @param direction +1 subindo, -1 descendo — a animacao muda de sentido junto
     */
    fun show(
        title: String,
        value: String,
        fraction: Float,
        accent: Int,
        motif: Motif,
        direction: Int,
    ) {
        handler.post { showOnMain(title, value, fraction, accent, motif, direction) }
    }

    fun hide() {
        handler.post { hideNow() }
    }

    private fun showOnMain(
        title: String,
        value: String,
        fraction: Float,
        accent: Int,
        motif: Motif,
        direction: Int,
    ) {
        try {
            val existing = view
            if (existing == null) {
                val ctx = App.getContext()
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val hud = HudView(ctx)
                hud.update(title, value, fraction, accent, motif, direction)
                val lp =
                    WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        } else {
                            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                        },
                        // As duas flags que impedem o cartao de atrapalhar o app de baixo.
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        android.graphics.PixelFormat.TRANSLUCENT,
                    )
                lp.gravity = Gravity.CENTER
                wm.addView(hud, lp)
                view = hud
                hud.animateIn()
            } else {
                existing.update(title, value, fraction, accent, motif, direction)
                existing.pop()
            }
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, HIDE_DELAY_MS)
        } catch (t: Throwable) {
            Log.e(TAG, "nao deu pra mostrar o retorno do gesto", t)
            view = null
        }
    }

    private fun hideNow() {
        val hud = view ?: return
        handler.removeCallbacks(hideRunnable)
        hud.animateOut {
            try {
                val wm = App.getContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(hud)
            } catch (t: Throwable) {
                Log.w(TAG, "nao deu pra remover o cartao do gesto", t)
            }
            view = null
        }
    }

    private class HudView(context: Context) : View(context) {

        private val density = context.resources.displayMetrics.density
        private val metrics = context.resources.displayMetrics

        // Tamanho proporcional a TELA, nao em dp fixo: a constante que fica boa numa central de
        // 1920x720 fica minuscula numa maior. Os limites cortam os extremos dos dois lados.
        private val cardH = (metrics.heightPixels * 0.30f).coerceIn(dp(120f), dp(280f))
        private val cardW = (cardH * 2.6f).coerceAtMost(metrics.widthPixels * 0.45f)
        private val pad = cardH * 0.16f
        private val glyphSize = cardH * 0.46f
        private val textLeft = pad + glyphSize + cardH * 0.10f
        private val barH = cardH * 0.055f

        // Fundo translucido: o cartao sobe por cima de outro app, e um bloco quase opaco esconde
        // demais do que esta embaixo. O preco da transparencia e a legibilidade sobre conteudo
        // claro — dai a sombra atras dos textos e a borda mais marcada, que sustentam o contorno.
        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xA6121620.toInt() }
        private val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1.5f)
                color = 0x4DFFFFFF
            }
        private val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFC6CEDA.toInt()
                textSize = cardH * 0.115f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                setShadowLayer(dp(5f), 0f, dp(1.5f), 0xCC000000.toInt())
            }
        private val valuePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = cardH * 0.40f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setShadowLayer(dp(7f), 0f, dp(2f), 0xE6000000.toInt())
            }
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3DFFFFFF }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glyphStroke =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        private var title = ""
        private var value = ""
        private var fraction = 0f
        private var accent = Color.WHITE
        private var motif = Motif.VOLUME
        private var direction = 1
        private var popAnimator: ValueAnimator? = null
        private var lastPopAt = 0L

        private val startedAt = SystemClock.uptimeMillis()
        /** Angulo acumulado da helice: a velocidade muda, mas a pa nao pode saltar de posicao. */
        private var fanAngle = 0f
        private var lastFrameAt = startedAt

        private val rect = RectF()
        private val path = Path()

        fun update(
            title: String,
            value: String,
            fraction: Float,
            accent: Int,
            motif: Motif,
            direction: Int,
        ) {
            this.title = title
            this.value = value
            this.fraction = fraction.coerceIn(0f, 1f)
            this.accent = accent
            this.motif = motif
            this.direction = if (direction >= 0) 1 else -1
            fillPaint.color = accent
            invalidate()
        }

        fun animateIn() {
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
            translationY = dp(20f)
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(160)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        /**
         * Batidinha que confirma que o gesto continua sendo lido.
         *
         * NAO pode rodar a cada passo: num arraste os passos chegam de poucos em poucos
         * milissegundos, e reiniciar uma animacao de 130ms a essa altura faz o cartao TREMER em vez
         * de animar — foi exatamente a queixa de "pouco fluido". O texto e a barra seguem
         * atualizando em todo passo; so a batidinha e que respeita um intervalo minimo.
         */
        fun pop() {
            val now = SystemClock.uptimeMillis()
            if (now - lastPopAt < POP_MIN_INTERVAL_MS) return
            lastPopAt = now
            popAnimator?.cancel()
            popAnimator =
                ValueAnimator.ofFloat(1f, 1.04f, 1f).apply {
                    duration = 130
                    addUpdateListener {
                        val s = it.animatedValue as Float
                        scaleX = s
                        scaleY = s
                    }
                    start()
                }
        }

        fun animateOut(onDone: () -> Unit) {
            popAnimator?.cancel()
            animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(200)
                .withEndAction(onDone)
                .start()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(cardW.toInt(), cardH.toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            val t = (now - startedAt) / 1000f
            val dt = ((now - lastFrameAt).coerceIn(0L, 100L)) / 1000f
            lastFrameAt = now

            val r = cardH * 0.16f
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, r, r, cardPaint)
            rect.inset(borderPaint.strokeWidth / 2f, borderPaint.strokeWidth / 2f)
            canvas.drawRoundRect(rect, r, r, borderPaint)

            val barTop = cardH - pad - barH
            drawGlyph(canvas, pad + glyphSize / 2f, (pad + barTop) / 2f, glyphSize / 2f, t, dt)

            val titleBaseline = pad + titlePaint.textSize * 0.9f
            canvas.drawText(title, textLeft, titleBaseline, titlePaint)
            canvas.drawText(value, textLeft, titleBaseline + valuePaint.textSize * 0.95f, valuePaint)

            rect.set(pad, barTop, cardW - pad, barTop + barH)
            canvas.drawRoundRect(rect, barH / 2f, barH / 2f, trackPaint)
            if (fraction > 0f) {
                rect.set(pad, barTop, pad + (cardW - 2 * pad) * fraction, barTop + barH)
                canvas.drawRoundRect(rect, barH / 2f, barH / 2f, fillPaint)
            }

            // Redesenha no proximo vsync enquanto o cartao existir. Ele vive ~1s por gesto, entao
            // isso nao e um loop permanente: quando a janela sai, o desenho para junto.
            postInvalidateOnAnimation()
        }

        private fun drawGlyph(canvas: Canvas, cx: Float, cy: Float, radius: Float, t: Float, dt: Float) {
            when (motif) {
                Motif.COOLING -> drawSnowflake(canvas, cx, cy, radius, t)
                Motif.HEATING -> drawFlame(canvas, cx, cy, radius, t)
                Motif.FAN -> drawFan(canvas, cx, cy, radius, dt)
                Motif.VOLUME -> drawSpeaker(canvas, cx, cy, radius, t)
            }
        }

        /** Floco girando devagar, com um leve pulsar — leitura imediata de "esfriando". */
        private fun drawSnowflake(canvas: Canvas, cx: Float, cy: Float, radius: Float, t: Float) {
            val pulse = 1f + 0.06f * sin(t * 3.4f)
            val r = radius * 0.92f * pulse
            glyphStroke.color = accent
            glyphStroke.strokeWidth = radius * 0.13f
            canvas.save()
            canvas.rotate(t * 22f, cx, cy)
            for (i in 0 until 6) {
                val a = Math.toRadians((i * 60).toDouble())
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                canvas.drawLine(cx - dx * r, cy - dy * r, cx + dx * r, cy + dy * r, glyphStroke)
                // Ramos: sem eles vira uma estrela generica, com eles le-se floco na hora.
                for (at in floatArrayOf(0.52f, 0.80f)) {
                    val bx = cx + dx * r * at
                    val by = cy + dy * r * at
                    val len = r * 0.24f * (1f - at * 0.5f)
                    for (side in intArrayOf(-1, 1)) {
                        val ba = a + side * 0.9f
                        canvas.drawLine(
                            bx,
                            by,
                            bx + cos(ba).toFloat() * len,
                            by + sin(ba).toFloat() * len,
                            glyphStroke,
                        )
                    }
                }
            }
            canvas.restore()
        }

        /** Chama tremulando: duas camadas, a de dentro mais clara e mais nervosa. */
        private fun drawFlame(canvas: Canvas, cx: Float, cy: Float, radius: Float, t: Float) {
            drawFlameLayer(canvas, cx, cy, radius * 1.0f, t * 6.0f, accent)
            drawFlameLayer(canvas, cx, cy + radius * 0.16f, radius * 0.55f, t * 9.0f, 0xFFFFD54F.toInt())
        }

        private fun drawFlameLayer(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            phase: Float,
            color: Int,
        ) {
            val wobble = sin(phase) * radius * 0.16f
            val top = cy - radius
            val bottom = cy + radius * 0.85f
            val half = radius * 0.62f
            path.reset()
            path.moveTo(cx + wobble * 0.5f, top)
            path.cubicTo(
                cx + half * 1.15f,
                top + radius * 0.75f,
                cx + half,
                bottom - radius * 0.35f,
                cx,
                bottom,
            )
            path.cubicTo(
                cx - half,
                bottom - radius * 0.35f,
                cx - half * 1.15f,
                top + radius * 0.75f,
                cx + wobble * 0.5f,
                top,
            )
            path.close()
            glyphFill.color = color
            canvas.drawPath(path, glyphFill)
        }

        /**
         * Helice de tres pas. A velocidade acompanha o nivel da ventilacao e o angulo e ACUMULADO,
         * nao calculado do tempo absoluto — senao a pa saltaria de posicao a cada mudanca de nivel.
         */
        private fun drawFan(canvas: Canvas, cx: Float, cy: Float, radius: Float, dt: Float) {
            val degreesPerSecond = (110f + fraction * 620f) * direction
            fanAngle = (fanAngle + degreesPerSecond * dt) % 360f
            glyphFill.color = accent
            canvas.save()
            canvas.rotate(fanAngle, cx, cy)
            for (i in 0 until 3) {
                canvas.save()
                canvas.rotate((i * 120).toFloat(), cx, cy)
                path.reset()
                path.moveTo(cx, cy)
                path.quadTo(cx + radius * 0.62f, cy - radius * 0.30f, cx + radius * 0.95f, cy)
                path.quadTo(cx + radius * 0.55f, cy + radius * 0.42f, cx, cy)
                path.close()
                canvas.drawPath(path, glyphFill)
                canvas.restore()
            }
            canvas.restore()
            glyphFill.color = 0xFF121620.toInt()
            canvas.drawCircle(cx, cy, radius * 0.17f, glyphFill)
        }

        /**
         * Alto-falante com tres ondas. Subindo, elas acendem de dentro pra fora; descendo, de fora
         * pra dentro — o sentido do gesto fica legivel sem ler o numero.
         */
        private fun drawSpeaker(canvas: Canvas, cx: Float, cy: Float, radius: Float, t: Float) {
            val left = cx - radius * 0.92f
            glyphFill.color = accent
            path.reset()
            path.moveTo(left, cy - radius * 0.26f)
            path.lineTo(left + radius * 0.34f, cy - radius * 0.26f)
            path.lineTo(left + radius * 0.78f, cy - radius * 0.66f)
            path.lineTo(left + radius * 0.78f, cy + radius * 0.66f)
            path.lineTo(left + radius * 0.34f, cy + radius * 0.26f)
            path.lineTo(left, cy + radius * 0.26f)
            path.close()
            canvas.drawPath(path, glyphFill)

            glyphStroke.color = accent
            glyphStroke.strokeWidth = radius * 0.13f
            val lit = Math.ceil((fraction * 3f).toDouble()).toInt().coerceIn(if (fraction > 0f) 1 else 0, 3)
            // A onda "viajante" corre pra fora quando sobe e pra dentro quando desce.
            val travel = if (direction > 0) (t * 2.2f) % 3f else 3f - (t * 2.2f) % 3f
            for (i in 0 until 3) {
                val on = i < lit
                val closeness = 1f - (abs(travel - i) / 1.2f).coerceIn(0f, 1f)
                val alpha = if (on) (110 + 145 * closeness).toInt() else (30 + 45 * closeness).toInt()
                glyphStroke.alpha = alpha.coerceIn(0, 255)
                val rr = radius * (0.42f + i * 0.30f)
                rect.set(
                    left + radius * 0.55f - rr,
                    cy - rr,
                    left + radius * 0.55f + rr,
                    cy + rr,
                )
                canvas.drawArc(rect, -46f, 92f, false, glyphStroke)
            }
            glyphStroke.alpha = 255
        }

        private fun dp(v: Float) = v * density
    }
}
