package Ir.co.tfs.farazaman.presentation.login

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import Ir.co.tfs.farazaman.databinding.ItemCitySelectionBinding

class CitySelectionAdapter(
    private val cities: List<LoginActivity.ProvinceInstance>,
    private val onCityClick: (LoginActivity.ProvinceInstance) -> Unit,
) : RecyclerView.Adapter<CitySelectionAdapter.CityViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CityViewHolder {
        val binding = ItemCitySelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return CityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CityViewHolder, position: Int) {
        holder.bind(cities[position])
    }

    override fun getItemCount(): Int = cities.size

    inner class CityViewHolder(
        private val binding: ItemCitySelectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(city: LoginActivity.ProvinceInstance) {
            binding.cityName.text = city.title
            binding.root.setOnClickListener { onCityClick(city) }
        }
    }
}
