package ru.crmod.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View

/**
 * Прозрачный слой во весь экран: рисует рамки и подписи карт.
 *
 * Модель успевает считать несколько кадров в секунду, поэтому рамки
 * не перерисовываются рывками: каждая плавно догоняет новое положение,
 * появляется и исчезает через прозрачность.
 */
class DetectionView(context: Context) : View(context) {
    private class Track(val label: String, val enemy: Boolean) {
        var left = 0f
        var top = 0f
        var right = 0f
        var bottom = 0f
        var toLeft = 0f
        var toTop = 0f
        var toRight = 0f
        var toBottom = 0f
        var alpha = 0f
        var wanted = 1f
        var fresh = true

        fun aim(box: Detection) {
            toLeft = box.left
            toTop = box.top
            toRight = box.right
            toBottom = box.bottom
            if (fresh) {
                fresh = false
                // Новая рамка начинается чуть сжатой — так она «раскрывается».
                val midX = (box.left + box.right) / 2
                val midY = (box.top + box.bottom) / 2
                left = midX + (box.left - midX) * 0.82f
                right = midX + (box.right - midX) * 0.82f
                top = midY + (box.top - midY) * 0.82f
                bottom = midY + (box.bottom - midY) * 0.82f
            }
        }

        fun centerX() = (toLeft + toRight) / 2
        fun centerY() = (toTop + toBottom) / 2

        fun step() {
            left += (toLeft - left) * GLIDE
            top += (toTop - top) * GLIDE
            right += (toRight - right) * GLIDE
            bottom += (toBottom - bottom) * GLIDE
            alpha += (wanted - alpha) * FADE
        }

        fun gone() = wanted == 0f && alpha < 0.02f
    }

    private val tracks = mutableListOf<Track>()

    @Volatile
    private var pending: Triple<List<Detection>, Int, Int>? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var ticking = false

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val plate = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        isFakeBoldText = true
    }

    private val frames = Choreographer.FrameCallback { step() }

    /** Кадры приходят из потока захвата, поэтому только складываем их. */
    fun show(next: List<Detection>, width: Int, height: Int) {
        pending = Triple(next, width, height)
    }

    fun start() {
        if (ticking) return
        ticking = true
        Choreographer.getInstance().postFrameCallback(frames)
    }

    fun stop() {
        // Не обрываем резко: рамки угасают, и цикл останавливается сам.
        ticking = false
        pending = Triple(emptyList(), frameWidth, frameHeight)
    }

    override fun onDetachedFromWindow() {
        ticking = false
        Choreographer.getInstance().removeFrameCallback(frames)
        super.onDetachedFromWindow()
    }

    private fun step() {
        pending?.let { (boxes, width, height) ->
            pending = null
            frameWidth = width
            frameHeight = height
            adopt(boxes)
        }
        tracks.forEach { it.step() }
        tracks.removeAll { it.gone() }
        invalidate()
        if (ticking || tracks.isNotEmpty()) {
            Choreographer.getInstance().postFrameCallback(frames)
        }
    }

    /** Новые рамки привязываем к ближайшим прежним с тем же названием. */
    private fun adopt(boxes: List<Detection>) {
        val free = tracks.toMutableList()
        free.forEach { it.wanted = 0f }
        for (box in boxes) {
            val midX = (box.left + box.right) / 2
            val midY = (box.top + box.bottom) / 2
            val near = free
                .filter { it.label == box.label && it.enemy == box.enemy }
                .minByOrNull { distance(it, midX, midY) }
            val track = if (near != null && distance(near, midX, midY) < JUMP) {
                free.remove(near)
                near
            } else {
                Track(box.label, box.enemy).also { tracks.add(it) }
            }
            track.wanted = 1f
            track.aim(box)
        }
    }

    private fun distance(track: Track, x: Float, y: Float): Float {
        val dx = track.centerX() - x
        val dy = track.centerY() - y
        return dx * dx + dy * dy
    }

    override fun onDraw(canvas: Canvas) {
        if (tracks.isEmpty() || frameWidth == 0 || frameHeight == 0) return
        // Кадр захвата меньше экрана, поэтому растягиваем координаты.
        val scaleX = width.toFloat() / frameWidth
        val scaleY = height.toFloat() / frameHeight
        for (track in tracks) {
            val visible = track.alpha.coerceIn(0f, 1f)
            if (visible < 0.02f) continue
            val color = if (track.enemy) ENEMY else ALLY
            val left = track.left * scaleX
            val top = track.top * scaleY
            val right = track.right * scaleX
            val bottom = track.bottom * scaleY

            stroke.color = color
            stroke.alpha = (220 * visible).toInt()
            canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, stroke)

            val caption = "${track.label} (${if (track.enemy) "противник" else "свои"})"
            val captionWidth = text.measureText(caption)
            val plateHeight = text.textSize + 10f
            val plateTop = if (top - plateHeight < 0f) bottom else top - plateHeight
            plate.color = color
            plate.alpha = (220 * visible).toInt()
            canvas.drawRoundRect(left, plateTop, left + captionWidth + 14f, plateTop + plateHeight, 6f, 6f, plate)
            text.alpha = (255 * visible).toInt()
            canvas.drawText(caption, left + 7f, plateTop + text.textSize + 1f, text)
        }
    }

    companion object {
        val ENEMY = Color.rgb(229, 72, 77)
        val ALLY = Color.rgb(30, 136, 229)

        /** Доля пути к новому положению за один кадр экрана. */
        const val GLIDE = 0.32f
        const val FADE = 0.22f

        /** Дальше этого рамка считается новой картой, а не сдвигом прежней. */
        const val JUMP = 120f * 120f
    }
}
