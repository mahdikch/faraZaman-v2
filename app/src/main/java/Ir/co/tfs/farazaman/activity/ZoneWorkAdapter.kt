package Ir.co.tfs.farazaman.activity

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.model.VehicleZoneWorkItem

class ZoneWorkAdapter(
    private val zones: List<VehicleZoneWorkItem>,
    private val onZoneSelected: (VehicleZoneWorkItem) -> Unit
) : RecyclerView.Adapter<ZoneWorkAdapter.ZoneViewHolder>() {

    inner class ZoneViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val organName: TextView = itemView.findViewById(R.id.zone_organ_name)
        val vehiclePlate: TextView = itemView.findViewById(R.id.zone_vehicle_plate)
        val contractInfo: TextView = itemView.findViewById(R.id.zone_contract_info)
        val shiftName: TextView = itemView.findViewById(R.id.zone_shift_name)

        fun bind(zone: VehicleZoneWorkItem) {
            organName.text = "منطقه: ${zone.organ.organName ?: "نامشخص"}"
            vehiclePlate.text = "پلاک: ${zone.assetVehicle.plakNumber ?: "نامشخص"}"
            
            // Show contract if available
            val contractCode = zone.contract?.firstOrNull()?.contractCode
            if (contractCode != null && contractCode.isNotEmpty()) {
                contractInfo.visibility = View.VISIBLE
                contractInfo.text = "قرارداد: $contractCode"
            } else {
                contractInfo.visibility = View.GONE
            }
            
            shiftName.text = "شیفت: ${zone.assetShift?.shiftName ?: "نامشخص"}"

            itemView.setOnClickListener {
                onZoneSelected(zone)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZoneViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_zone_work, parent, false)
        return ZoneViewHolder(view)
    }

    override fun onBindViewHolder(holder: ZoneViewHolder, position: Int) {
        holder.bind(zones[position])
    }

    override fun getItemCount(): Int = zones.size
}

