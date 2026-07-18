package com.example.stayfree.ui.blocking

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stayfree.R
import com.example.stayfree.data.local.entity.BlockRuleEntity
import com.example.stayfree.databinding.ItemBlockRuleBinding
import com.example.stayfree.domain.BlockRuleEvaluator
import com.example.stayfree.domain.model.BlockType
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
            val ctx = binding.root.context
            // The sleep-mode sentinel rule is not a real package.
            val isSleep = rule.packageName == BlockRuleEvaluator.SLEEP_MODE_PACKAGE
            binding.tvAppName.text =
                if (isSleep) ctx.getString(R.string.blocking_sleep_mode)
                else AppInfoUtils.getAppName(ctx, rule.packageName)
            val icon = if (isSleep) null else AppInfoUtils.getAppIcon(ctx, rule.packageName)
            if (icon != null) {
                binding.ivAppIcon.imageTintList = null
                binding.ivAppIcon.setImageDrawable(icon)
            } else {
                binding.ivAppIcon.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.dash_amber))
                binding.ivAppIcon.setImageResource(if (isSleep) R.drawable.ic_tool_moon else R.drawable.ic_block)
            }
            binding.tvRuleSub.text = subText(ctx, rule)
            // Clear the recycled listener before setting state or it fires for the old rule.
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = rule.isActive
            binding.switchActive.setOnCheckedChangeListener { _, checked ->
                onToggle(rule.id, checked)
            }
            binding.btnDelete.setOnClickListener { onDelete(rule.id) }
        }

        private fun subText(ctx: Context, rule: BlockRuleEntity): CharSequence = when (rule.blockType) {
            BlockType.DAILY_LIMIT.name ->
                limitSpan(ctx, R.string.blocking_rule_daily_limit, rule.dailyLimitMs)
            BlockType.SESSION.name ->
                limitSpan(ctx, R.string.blocking_rule_session, rule.sessionLimitMs)
            BlockType.SCHEDULED.name, BlockType.SLEEP.name ->
                ctx.getString(R.string.blocking_rule_scheduled)
            else -> ctx.getString(R.string.blocking_rule_block_now)
        }

        /** "Dnevni limit · 1h 30m" with the value in bold amber. */
        private fun limitSpan(ctx: Context, res: Int, ms: Long?): CharSequence {
            val value = TimeUtils.formatDuration(ms ?: 0)
            val full = ctx.getString(res, value)
            val start = full.lastIndexOf(value)
            if (start < 0) return full
            return SpannableString(full).apply {
                val end = start + value.length
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.dash_amber)),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BlockRuleEntity>() {
            override fun areItemsTheSame(a: BlockRuleEntity, b: BlockRuleEntity) = a.id == b.id
            override fun areContentsTheSame(a: BlockRuleEntity, b: BlockRuleEntity) = a == b
        }
    }
}
