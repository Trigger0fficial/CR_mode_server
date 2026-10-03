package ru.crmod.app

/**
 * Связь между захватом экрана и плашкой поверх игры.
 * Захват считает карты, плашка показывает состояние и рисует рамки.
 */
object Battle {
    @Volatile
    var active = false
        private set

    @Volatile
    var onState: ((Boolean) -> Unit)? = null

    @Volatile
    var onBoxes: ((List<Detection>, Int, Int) -> Unit)? = null

    fun started() {
        active = true
        onState?.invoke(true)
    }

    fun stopped() {
        active = false
        onBoxes?.invoke(emptyList(), 0, 0)
        onState?.invoke(false)
    }

    fun publish(boxes: List<Detection>, width: Int, height: Int) {
        if (active) onBoxes?.invoke(boxes, width, height)
    }
}
