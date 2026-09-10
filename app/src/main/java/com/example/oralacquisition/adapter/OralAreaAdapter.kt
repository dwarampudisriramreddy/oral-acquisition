package com.example.oralacquisition.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.oralacquisition.R
import com.example.oralacquisition.data.OralArea
import com.example.oralacquisition.databinding.ItemOralAreaBinding

class OralAreaAdapter(
    private val areas: List<OralArea>,
    private val onCapture: (OralArea) -> Unit,
    private val onMacro: (OralArea) -> Unit,
    private val onRemove: (OralArea) -> Unit
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
                binding.ivAreaThumb.setImageURI(area.photoUri)
                binding.btnCapture.text = "Retake"
                binding.btnRemove.visibility = ViewGroup.VISIBLE
            } else {
                binding.ivAreaThumb.setImageResource(R.drawable.ic_camera_placeholder)
                binding.btnCapture.text = "Capture"
                binding.btnRemove.visibility = ViewGroup.GONE
            }

            binding.btnCapture.setOnClickListener { onCapture(area) }
            binding.btnMacro.setOnClickListener { onMacro(area) }
            binding.btnRemove.setOnClickListener { onRemove(area) }
        }
    }
}
