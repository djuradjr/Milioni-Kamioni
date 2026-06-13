package com.example.stayfree.ui.blocking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stayfree.data.local.entity.BlockRuleEntity
import com.example.stayfree.databinding.ItemBlockRuleBinding
import com.example.stayfree.domain.BlockRuleEvaluator
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.TimeUtils

class BlockRulesAdapter(
    private val onToggle: (Long, Boolean) -> Unit,
    private val onDelete: (Long) -> Unit
) : ListAdapter<BlockRuleEntity, BlockRulesAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlockRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBlockRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: BlockRuleEntity) {
            // The sleep-mode sentinel rule is not a real package.
            val appName = if (rule.packageName == BlockRuleEvaluator.SLEEP_MODE_PACKAGE) {
                "Sleep mode"
            } else {
                AppInfoUtils.getAppName(binding.root.context, rule.packageName)
            }
            val icon = AppInfoUtils.getAppIcon(binding.root.context, rule.packageName)
            binding.tvAppName.text = appName
            binding.tvBlockType.text = rule.blockType.replace("_", " ")
            if (icon != null) binding.ivAppIcon.setImageDrawable(icon)
            binding.switchActive.isChecked = rule.isActive
            binding.switchActive.setOnCheckedChangeListener { _, checked ->
                onToggle(rule.id, checked)
            }
            binding.btnDelete.setOnClickListener { onDelete(rule.id) }

            // Show limit info
            val limitText = when (rule.blockType) {
                "DAILY_LIMIT" -> rule.dailyLimitMs?.let { "Limit: ${TimeUtils.formatDuration(it)}" } ?: ""
                "SESSION" -> rule.sessionLimitMs?.let { "Session: ${TimeUtils.formatDuration(it)}" } ?: ""
                else -> ""
            }
            binding.tvLimitInfo.text = limitText
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BlockRuleEntity>() {
            override fun areItemsTheSame(a: BlockRuleEntity, b: BlockRuleEntity) = a.id == b.id
            override fun areContentsTheSame(a: BlockRuleEntity, b: BlockRuleEntity) = a == b
        }
    }
}
