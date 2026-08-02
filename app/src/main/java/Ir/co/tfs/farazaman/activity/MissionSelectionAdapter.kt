package Ir.co.tfs.farazaman.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import Ir.co.tfs.farazaman.databinding.ItemCitySelectionBinding

class MissionSelectionAdapter(
    private val items: List<MissionSelectionOption>,
    private val onItemClick: (MissionSelectionOption) -> Unit,
) : RecyclerView.Adapter<MissionSelectionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCitySelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemCitySelectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MissionSelectionOption) {
            binding.cityName.text = item.text
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
