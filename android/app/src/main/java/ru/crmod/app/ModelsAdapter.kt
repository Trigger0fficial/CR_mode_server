package ru.crmod.app

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.crmod.app.databinding.ItemModelBinding

class ModelsAdapter(
    private val onClick: (ModelItem) -> Unit,
) : RecyclerView.Adapter<ModelsAdapter.Holder>() {
    private val items = mutableListOf<ModelItem>()
    var selectedId: Int = -1
    var busyId: Int = -1
    var context: Context? = null

    fun submit(next: List<ModelItem>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemModelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemModelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ModelItem) {
            binding.name.text = item.name
            binding.description.text = item.description.ifBlank { "Без описания" }
            val ready = context?.let { ModelStore.isReady(it, item.id) } == true
            binding.state.text = when {
                busyId == item.id -> "скачивается"
                selectedId == item.id && ready -> "включена"
                ready -> "скачана"
                else -> "скачать (${item.size / 1024 / 1024} МБ)"
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
