package ru.crmod.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Круги вокруг кнопки запуска. В покое — тонкие серые,
 * в работе по ним бежит зелёная дуга и расходится мягкая волна.
 */
class PowerRingView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val quiet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = resources.getColor(R.color.ring, null)
    }
    private val live = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = resources.getColor(R.color.accent, null)
    }
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = resources.getColor(R.color.accent, null)
    }
    private val arc = RectF()

    /** 0 — выключено, 1 — работает. Между ними плавный переход. */
    private var level = 0f
    private var spin = 0f
    private var ripple = 0f
    private var loop: ValueAnimator? = null
    private var fade: ValueAnimator? = null

    fun setActive(active: Boolean) {
        val target = if (active) 1f else 0f
        if (level == target && fade == null) return
        fade?.cancel()
        fade = ValueAnimator.ofFloat(level, target).apply {
            duration = Motion.SLOW
            interpolator = Motion.ease
            addUpdateListener {
                level = it.animatedValue as Float
                invalidate()
            }
            doOnEnd { fade = null }
            start()
        }
        if (active) startLoop() else stopLoop()
    }

    private fun startLoop() {
        if (loop != null) return
        loop = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600
            repeatCount = ValueAnimator.INFINITE
            interpolator = null
            addUpdateListener {
                val value = it.animatedValue as Float
                spin = value * 360f
                ripple = (value * 2f) % 1f
                invalidate()
            }
            start()
        }
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        fade?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val outer = minOf(width, height) / 2f - dp(2f)
        val inner = outer - dp(26f)

        quiet.strokeWidth = dp(1.5f)
        canvas.drawCircle(centerX, centerY, outer, quiet)
        canvas.drawCircle(centerX, centerY, inner, quiet)

        if (level <= 0.01f) return

        // Волна расходится от внутреннего круга к внешнему и затухает.
        val spread = inner + (outer - inner + dp(14f)) * ripple
        wave.strokeWidth = dp(2f)
        wave.alpha = (90 * level * (1f - ripple)).toInt().coerceIn(0, 255)
        canvas.drawCircle(centerX, centerY, spread, wave)

        // Бегущая дуга по внешнему кругу.
        arc.set(centerX - outer, centerY - outer, centerX + outer, centerY + outer)
        live.strokeWidth = dp(3f)
        live.alpha = (255 * level).toInt().coerceIn(0, 255)
        canvas.drawArc(arc, spin - 90f, 64f, false, live)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}

private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = action()
    })
}
