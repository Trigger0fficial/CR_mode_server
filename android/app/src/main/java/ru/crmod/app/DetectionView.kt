package ru.crmod.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Прозрачный слой во весь экран: рисует рамки и подписи карт. */
class DetectionView(context: Context) : View(context) {
    private var boxes: List<Detection> = emptyList()
    private var frameWidth = 0
    private var frameHeight = 0

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

    fun show(next: List<Detection>, width: Int, height: Int) {
        boxes = next
        frameWidth = width
        frameHeight = height
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        if (boxes.isEmpty() || frameWidth == 0 || frameHeight == 0) return
        // Кадр захвата меньше экрана, поэтому растягиваем координаты.
        val scaleX = width.toFloat() / frameWidth
        val scaleY = height.toFloat() / frameHeight
        for (box in boxes) {
            val color = if (box.enemy) ENEMY else ALLY
            stroke.color = color
            val left = box.left * scaleX
            val top = box.top * scaleY
            val right = box.right * scaleX
            val bottom = box.bottom * scaleY
            canvas.drawRoundRect(left, top, right, bottom, 6f, 6f, stroke)

            val caption = box.caption()
            val captionWidth = text.measureText(caption)
            val plateHeight = text.textSize + 10f
            val plateTop = if (top - plateHeight < 0f) bottom else top - plateHeight
            plate.color = color
            canvas.drawRoundRect(left, plateTop, left + captionWidth + 14f, plateTop + plateHeight, 5f, 5f, plate)
            canvas.drawText(caption, left + 7f, plateTop + text.textSize + 1f, text)
        }
    }

    companion object {
        val ENEMY = Color.argb(220, 229, 57, 53)
        val ALLY = Color.argb(220, 30, 136, 229)
    }
}
