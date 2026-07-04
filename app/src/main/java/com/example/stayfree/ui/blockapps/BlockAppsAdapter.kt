package com.example.stayfree.ui.blockapps

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stayfree.databinding.ItemBlockAppBinding
import com.example.stayfree.util.AppInfoUtils

class BlockAppsAdapter(
    private val onToggle: (packageName: String, blocked: Boolean) -> Unit
) : ListAdapter<BlockAppItem, BlockAppsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlockAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBlockAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BlockAppItem) {
            binding.tvAppName.text = item.appName
            val icon = AppInfoUtils.getAppIcon(binding.root.context, item.packageName)
            if (icon != null) binding.ivAppIcon.setImageDrawable(icon)

            // Detach the recycled listener before setting state so restoring the
            // switch position doesn't fire a spurious toggle.
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = item.isBlocked
            binding.switchActive.setOnCheckedChangeListener { btn, checked ->
                if (btn.isPressed) onToggle(item.packageName, checked)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BlockAppItem>() {
            override fun areItemsTheSame(a: BlockAppItem, b: BlockAppItem) = a.packageName == b.packageName
            override fun areContentsTheSame(a: BlockAppItem, b: BlockAppItem) = a == b
        }
    }
}
