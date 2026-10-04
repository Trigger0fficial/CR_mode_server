package ru.crmod.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.crmod.app.databinding.ItemModelBinding

/** Что можно сделать с моделью прямо сейчас. */
enum class ModelState {
    DOWNLOAD,
    DOWNLOADING,
    TURN_ON,
    ON,
    RUNNING,
}

class ModelsAdapter(
    private val context: Context,
    private val onClick: (ModelItem) -> Unit,
) : RecyclerView.Adapter<ModelsAdapter.Holder>() {
    private val items = mutableListOf<ModelItem>()
    var selectedId: Int = -1
    var busyId: Int = -1
    var progress: Int = 0

    @SuppressLint("NotifyDataSetChanged")
    fun submit(next: List<ModelItem>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    /** Обновляем одну строку, чтобы список не мигал целиком. */
    fun refresh(id: Int) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) notifyItemChanged(index, CHANGED)
    }

    fun refreshAll() {
        if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size, CHANGED)
    }

    private fun stateOf(item: ModelItem) = when {
        busyId == item.id -> ModelState.DOWNLOADING
        !ModelStore.isReady(context, item.id) -> ModelState.DOWNLOAD
        selectedId != item.id -> ModelState.TURN_ON
        Battle.active -> ModelState.RUNNING
        else -> ModelState.ON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemModelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position], false)

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        holder.bind(items[position], payloads.contains(CHANGED))
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class Holder(private val binding: ItemModelBinding) : RecyclerView.ViewHolder(binding.root) {
        private var current: ModelItem? = null

        init {
            binding.root.setOnClickListener { current?.let(onClick) }
            binding.root.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> Motion.press(view, true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> Motion.press(view, false)
                }
                false
            }
        }

        fun bind(item: ModelItem, animated: Boolean) {
            current = item
            binding.name.text = item.name
            val state = stateOf(item)
            val stop = state == ModelState.RUNNING
            val accent = context.getColor(if (stop) R.color.danger else R.color.accent)

            val caption = when (state) {
                ModelState.DOWNLOAD -> "Скачать · ${item.size / 1024 / 1024} МБ"
                ModelState.DOWNLOADING -> "Скачивание · $progress%"
                ModelState.TURN_ON -> "Включить · ${item.labels.size} карт"
                ModelState.ON -> "Включена · ${item.labels.size} карт"
                ModelState.RUNNING -> "Работает · распознаёт карты"
            }
            if (animated) Motion.retext(binding.state, caption) else binding.state.text = caption

            val icon = when (state) {
                ModelState.DOWNLOAD, ModelState.DOWNLOADING -> R.drawable.ic_download
                ModelState.TURN_ON -> R.drawable.ic_play
                ModelState.ON -> R.drawable.ic_check
                ModelState.RUNNING -> R.drawable.ic_stop
            }
            val repaint = {
                binding.actionIcon.setImageResource(icon)
                binding.actionIcon.setColorFilter(accent)
                binding.action.setBackgroundResource(
                    if (stop) R.drawable.bg_action_danger else R.drawable.bg_action
                )
            }
            if (animated) Motion.swap(binding.actionIcon, repaint) else repaint()

            binding.tile.setColorFilter(accent)
            binding.tileBox.setBackgroundResource(
                if (stop) R.drawable.bg_tile_danger else R.drawable.bg_tile
            )

            binding.actionIcon.show(state != ModelState.DOWNLOADING)
            binding.progress.show(state == ModelState.DOWNLOADING)
            if (state == ModelState.DOWNLOADING) {
                binding.progress.isIndeterminate = progress <= 0
                if (progress > 0) binding.progress.setProgressCompat(progress, true)
            }

            // Полоска слева отмечает включённую модель и выезжает плавно.
            val marked = state == ModelState.ON || state == ModelState.RUNNING
            binding.stripe.setBackgroundColor(accent)
            if (animated) {
                binding.stripe.animate()
                    .alpha(if (marked) 1f else 0f)
                    .scaleY(if (marked) 1f else 0.3f)
                    .setDuration(Motion.NORMAL)
                    .setInterpolator(Motion.ease)
                    .start()
            } else {
                binding.stripe.alpha = if (marked) 1f else 0f
                binding.stripe.scaleY = if (marked) 1f else 0.3f
            }
        }
    }

    companion object {
        private const val CHANGED = "changed"
    }
}

private fun View.show(on: Boolean) {
    visibility = if (on) View.VISIBLE else View.GONE
}
