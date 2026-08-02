package Ir.co.tfs.farazaman.activity

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import Ir.co.tfs.farazaman.R

class SelectedImagesAdapter(
    private val images: List<Uri>,
    private val onImageClick: (Uri) -> Unit,
    private val onImageRemove: (Int) -> Unit
) : RecyclerView.Adapter<SelectedImagesAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.selected_image)
        val removeButton: ImageView = itemView.findViewById(R.id.remove_image_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUri = images[position]
        
        holder.imageView.setImageURI(imageUri)
        
        holder.imageView.setOnClickListener {
            onImageClick(imageUri)
        }
        
        holder.removeButton.setOnClickListener {
            onImageRemove(position)
        }
    }

    override fun getItemCount(): Int = images.size
} 