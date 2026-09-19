package com.example.stayfree.ui.website

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stayfree.data.local.entity.WebsiteBlockEntity
import com.example.stayfree.databinding.ItemWebsiteBlockBinding
import com.example.stayfree.util.TimeUtils

class WebsiteBlockAdapter(
    private val onToggle: (WebsiteBlockEntity) -> Unit,
    private val onDelete: (Long) -> Unit
) : ListAdapter<WebsiteBlockEntity, WebsiteBlockAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWebsiteBlockBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemWebsiteBlockBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: WebsiteBlockEntity) {
            binding.tvDomain.text = entity.domain
            binding.tvCapInfo.text = if (entity.dailyCapMs == null) {
                "Always blocked"
            } else {
                "Daily cap: ${TimeUtils.formatDuration(entity.dailyCapMs)} (used: ${TimeUtils.formatDuration(entity.timeUsedTodayMs)})"
            }
            // Clear the recycled listener first or setting the state toggles the previous row.
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = entity.isActive
            binding.switchActive.setOnCheckedChangeListener { _, _ -> onToggle(entity) }
            binding.btnDelete.setOnClickListener { onDelete(entity.id) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WebsiteBlockEntity>() {
            override fun areItemsTheSame(a: WebsiteBlockEntity, b: WebsiteBlockEntity) = a.id == b.id
            override fun areContentsTheSame(a: WebsiteBlockEntity, b: WebsiteBlockEntity) = a == b
        }
    }
}
