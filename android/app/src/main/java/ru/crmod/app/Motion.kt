package ru.crmod.app

import android.view.View
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/** Кривые и пружины, повторяющие ощущение от анимаций iOS. */
object Motion {
    /** Быстрый старт, долгое мягкое торможение — основная кривая. */
    val ease = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    /** Для исчезновения: наоборот, уходит резче. */
    val easeIn = PathInterpolator(0.4f, 0f, 0.6f, 1f)

    const val FAST = 160L
    const val NORMAL = 260L
    const val SLOW = 420L

    private fun spring(view: View, property: DynamicAnimation.ViewProperty, to: Float) =
        SpringAnimation(view, property, to).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = 0.62f
        }

    /** Нажатие: кнопка слегка проваливается и пружиной возвращается. */
    fun press(view: View, down: Boolean) {
        val to = if (down) 0.94f else 1f
        spring(view, SpringAnimation.SCALE_X, to).start()
        spring(view, SpringAnimation.SCALE_Y, to).start()
    }

    /** Появление: пружиной из уменьшенного состояния. */
    fun appear(view: View) {
        view.alpha = 0f
        view.scaleX = 0.84f
        view.scaleY = 0.84f
        view.animate().alpha(1f).setDuration(NORMAL).setInterpolator(ease).start()
        spring(view, SpringAnimation.SCALE_X, 1f).start()
        spring(view, SpringAnimation.SCALE_Y, 1f).start()
    }

    /** Исчезновение с тем же характером, что и появление. */
    fun disappear(view: View, then: () -> Unit) {
        view.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(FAST)
            .setInterpolator(easeIn)
            .withEndAction(then)
            .start()
    }

    /**
     * Подмена содержимого: гасим, меняем, зажигаем обратно.
     * Размер меняется в невидимой фазе, поэтому рывка не видно.
     */
    fun swap(view: View, change: () -> Unit) {
        view.animate()
            .alpha(0f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(110)
            .setInterpolator(easeIn)
            .withEndAction {
                change()
                view.animate()
                    .alpha(1f)
                    .setDuration(NORMAL)
                    .setInterpolator(ease)
                    .start()
                spring(view, SpringAnimation.SCALE_X, 1f).start()
                spring(view, SpringAnimation.SCALE_Y, 1f).start()
            }
            .start()
    }

    /** Перекрашивание иконки: цвет переливается, а не переключается. */
    fun recolor(view: android.widget.ImageView, from: Int, to: Int) {
        if (from == to) {
            view.setColorFilter(to)
            return
        }
        android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), from, to).apply {
            duration = NORMAL
            interpolator = ease
            addUpdateListener { view.setColorFilter(it.animatedValue as Int) }
            start()
        }
    }

    /** Текст меняется со сдвигом вверх — так это выглядит в iOS. */
    fun retext(view: android.widget.TextView, text: CharSequence) {
        if (view.text == text) return
        view.animate()
            .alpha(0f)
            .translationY(-6f)
            .setDuration(100)
            .setInterpolator(easeIn)
            .withEndAction {
                view.text = text
                view.translationY = 6f
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(NORMAL)
                    .setInterpolator(ease)
                    .start()
            }
            .start()
    }
}
