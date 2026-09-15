package com.example.oralacquisition.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.oralacquisition.R
import com.example.oralacquisition.data.OralArea
import com.example.oralacquisition.databinding.ItemOralAreaBinding
import com.example.oralacquisition.util.ThumbLoader

class OralAreaAdapter(
    private val areas: List<OralArea>,
    private val onCapture: (OralArea) -> Unit,
    private val onRemove: (OralArea) -> Unit,
    private val onAddExtra: (OralArea) -> Unit
) : RecyclerView.Adapter<OralAreaAdapter.OralAreaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OralAreaViewHolder {
        val binding = ItemOralAreaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OralAreaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OralAreaViewHolder, position: Int) {
        holder.bind(areas[position])
    }

    override fun getItemCount() = areas.size

    inner class OralAreaViewHolder(
        private val binding: ItemOralAreaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(area: OralArea) {
            binding.tvAreaName.text = area.name

            if (area.photoUri != null) {
                ThumbLoader.load(
                    binding.root.context, binding.ivAreaThumb, area.photoUri, 140
                )
                binding.btnCapture.text = "Retake"
                binding.btnRemove.visibility = ViewGroup.VISIBLE
                binding.btnAddExtra.visibility = ViewGroup.VISIBLE
            } else {
                binding.ivAreaThumb.setImageResource(R.drawable.ic_camera_placeholder)
                binding.btnCapture.text = "Capture"
                binding.btnRemove.visibility = ViewGroup.GONE
                binding.btnAddExtra.visibility = ViewGroup.GONE
            }

            binding.btnCapture.setOnClickListener { onCapture(area) }
            binding.btnRemove.setOnClickListener { onRemove(area) }
            binding.btnAddExtra.setOnClickListener { onAddExtra(area) }
            
            binding.ivAreaThumb.setOnClickListener {
                if (area.photoUri != null) {
                    val context = binding.root.context
                    val imageView = android.widget.ImageView(context).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        setPadding(0, 32, 0, 0)
                        setImageURI(area.photoUri)
                    }
                    androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle(area.name)
                        .setView(imageView)
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
        }
    }
}
