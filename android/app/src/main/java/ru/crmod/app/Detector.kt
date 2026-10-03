package ru.crmod.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

/** Запускает YOLOv8 в формате ONNX прямо на телефоне. */
class Detector(model: File, private val labels: List<String>) : Closeable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        model.readBytes(),
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(THREADS)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        },
    )
    private val inputName: String = session.inputNames.first()
    private val square = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(square)
    private val fill = Paint()
    private val pixels = IntArray(SIDE * SIDE)
    private val input = FloatBuffer.allocate(3 * SIDE * SIDE)
    private val shape = longArrayOf(1, 3, SIDE.toLong(), SIDE.toLong())

    // Выход модели — около 3 МБ на кадр, поэтому массив переиспользуем, а не создаём заново.
    private var raw = FloatArray(0)

    fun detect(frame: Bitmap): List<Detection> {
        // Впихиваем кадр в квадрат 640x640 без искажения пропорций.
        val scale = minOf(SIDE.toFloat() / frame.width, SIDE.toFloat() / frame.height)
        val width = (frame.width * scale).toInt()
        val height = (frame.height * scale).toInt()
        val padX = (SIDE - width) / 2
        val padY = (SIDE - height) / 2
        canvas.drawColor(PAD_COLOR)
        canvas.drawBitmap(frame, null, Rect(padX, padY, padX + width, padY + height), fill)

        square.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE)
        val plane = SIDE * SIDE
        val array = input.array()
        for (index in 0 until plane) {
            val pixel = pixels[index]
            array[index] = ((pixel shr 16) and 0xFF) / 255f
            array[plane + index] = ((pixel shr 8) and 0xFF) / 255f
            array[2 * plane + index] = (pixel and 0xFF) / 255f
        }
        input.rewind()

        val anchors: Int
        val rows: Int
        OnnxTensor.createTensor(env, input, shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val dims = output.info.shape
                rows = dims[1].toInt()
                anchors = dims[2].toInt()
                val buffer = output.floatBuffer
                if (raw.size != buffer.remaining()) raw = FloatArray(buffer.remaining())
                buffer.get(raw)
            }
        }

        val classes = rows - 4
        val found = mutableListOf<Detection>()
        for (anchor in 0 until anchors) {
            var best = 0
            var score = 0f
            for (index in 0 until classes) {
                val value = raw[(4 + index) * anchors + anchor]
                if (value > score) {
                    score = value
                    best = index
                }
            }
            if (score < CONFIDENCE) continue
            val centerX = raw[anchor]
            val centerY = raw[anchors + anchor]
            val boxWidth = raw[2 * anchors + anchor]
            val boxHeight = raw[3 * anchors + anchor]
            // Из квадрата обратно в координаты кадра.
            val left = (centerX - boxWidth / 2 - padX) / scale
            val top = (centerY - boxHeight / 2 - padY) / scale
            val right = (centerX + boxWidth / 2 - padX) / scale
            val bottom = (centerY + boxHeight / 2 - padY) / scale
            found += Detection(
                left = left.coerceIn(0f, frame.width.toFloat()),
                top = top.coerceIn(0f, frame.height.toFloat()),
                right = right.coerceIn(0f, frame.width.toFloat()),
                bottom = bottom.coerceIn(0f, frame.height.toFloat()),
                score = score,
                label = labels.getOrElse(best) { "класс $best" },
                // Сторону определяем по месту на арене: верх — противник.
                enemy = (top + bottom) / 2 < frame.height * ENEMY_EDGE,
            )
        }
        return suppress(found)
    }

    private fun suppress(items: List<Detection>): List<Detection> {
        val sorted = items.sortedByDescending { it.score }
        val kept = mutableListOf<Detection>()
        for (candidate in sorted) {
            if (kept.size >= MAX_BOXES) break
            if (kept.none { overlap(it, candidate) > IOU }) kept += candidate
        }
        return kept
    }

    private fun overlap(first: Detection, second: Detection): Float {
        val left = maxOf(first.left, second.left)
        val top = maxOf(first.top, second.top)
        val right = minOf(first.right, second.right)
        val bottom = minOf(first.bottom, second.bottom)
        if (right <= left || bottom <= top) return 0f
        val shared = (right - left) * (bottom - top)
        val whole = first.area() + second.area() - shared
        return if (whole <= 0f) 0f else shared / whole
    }

    override fun close() {
        session.close()
        square.recycle()
    }

    companion object {
        const val SIDE = 640
        const val CONFIDENCE = 0.3f
        const val IOU = 0.45f
        const val MAX_BOXES = 40
        const val THREADS = 4
        const val ENEMY_EDGE = 0.46f
        val PAD_COLOR = Color.rgb(114, 114, 114)
    }
}

data class Detection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val label: String,
    val enemy: Boolean,
) {
    fun area() = (right - left) * (bottom - top)

    fun caption() = "$label (${if (enemy) "противник" else "свои"})"
}
