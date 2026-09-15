package com.example.oralacquisition.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.oralacquisition.databinding.ItemGalleryBinding
import com.example.oralacquisition.util.StorageUtil
import com.example.oralacquisition.util.ThumbLoader

class GalleryAdapter(
    private val items: List<StorageUtil.PhotoItem>,
    private val onItemClick: (StorageUtil.PhotoItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = ItemGalleryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class GalleryViewHolder(
        private val binding: ItemGalleryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StorageUtil.PhotoItem) {
            ThumbLoader.load(
                binding.root.context, binding.ivGalleryThumb, item.uri, 160
            )
            binding.tvGalleryLabel.text = item.label
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}