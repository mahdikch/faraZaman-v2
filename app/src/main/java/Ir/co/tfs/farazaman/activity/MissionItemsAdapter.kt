package Ir.co.tfs.farazaman.activity

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import Ir.co.tfs.farazaman.R

class MissionItemsAdapter(
    private val items: List<String>,
    private val sectionType: String, // "planning" or "system"
    private val onMapClick: ((Int) -> Unit)? = null,
    private val onViolationClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<MissionItemsAdapter.ViewHolder>() {

    companion object {
        private var globalLastClickedSection: String? = null
        private var globalLastClickedPosition: Int = RecyclerView.NO_POSITION
        
        // Method to clear all highlights
        fun clearAllHighlights() {
            globalLastClickedSection = null
            globalLastClickedPosition = RecyclerView.NO_POSITION
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.item_title)
        val mapButton: android.widget.ImageView = view.findViewById(R.id.icon_map)
        val violationButton: android.widget.ImageView = view.findViewById(R.id.icon_violation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.expandable_item_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.titleTextView.text = items[position]
        
        // Highlight if this is the globally last clicked item in this section
        val isHighlighted = (globalLastClickedSection == sectionType && globalLastClickedPosition == position)
        
        if (isHighlighted) {
            holder.itemView.setBackgroundColor(Color.parseColor("#4f9a94"))
            holder.titleTextView.setTextColor(Color.parseColor("#ffffff"))
            // Set icon colors to white
            holder.mapButton.setColorFilter(Color.WHITE)
            holder.violationButton.setColorFilter(Color.WHITE)
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.titleTextView.setTextColor(Color.BLACK) // Reset text color
            // Reset icon colors to default
            holder.mapButton.clearColorFilter()
            holder.violationButton.clearColorFilter()
        }
        
        // Set up click listeners
        holder.mapButton.setOnClickListener {
            // Clear previous highlight
            val previousSection = globalLastClickedSection
            val previousPosition = globalLastClickedPosition
            
            // Set new global highlight
            globalLastClickedSection = sectionType
            globalLastClickedPosition = position
            
            // Notify previous item to remove highlight
            if (previousSection != null && previousPosition != RecyclerView.NO_POSITION) {
                // This will be handled by the other adapter when it rebinds
            }
            
            // Notify current item to add highlight
            notifyItemChanged(position)
            
            onMapClick?.invoke(position)
        }
        
        holder.violationButton.setOnClickListener {
            // Clear previous highlight
            val previousSection = globalLastClickedSection
            val previousPosition = globalLastClickedPosition
            
            // Set new global highlight
            globalLastClickedSection = sectionType
            globalLastClickedPosition = position
            
            // Notify previous item to remove highlight
            if (previousSection != null && previousPosition != RecyclerView.NO_POSITION) {
                // This will be handled by the other adapter when it rebinds
            }
            
            // Notify current item to add highlight
            notifyItemChanged(position)
            
            onViolationClick?.invoke(position)
        }
    }

    override fun getItemCount() = items.size
} 
