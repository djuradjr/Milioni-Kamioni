package com.example.stayfree.ui.blocking

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
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
    private val onDelete: (Long) -> Unit,
    private val onOpenApps: () -> Unit,
    private val onOpenSites: () -> Unit
) : ListAdapter<ActiveBlockItem, BlockRulesAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlockRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBlockRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ActiveBlockItem) {
            when (item) {
                is ActiveBlockItem.Rule -> bindRule(item.rule)
                is ActiveBlockItem.App -> bindStatusRow(
                    pkg = item.packageName,
                    sub = limitSub(
                        R.string.active_block_whole_app_always,
                        R.string.active_block_whole_app_limited,
                        item.limitMinutes
                    ),
                    onClick = onOpenApps
                )
                is ActiveBlockItem.Content -> {
                    val ctx = binding.root.context
                    bindStatusRow(
                        pkg = item.packageName,
                        sub = if (item.limitMinutes <= 0) {
                            ctx.getString(R.string.active_block_content_always, item.displayName)
                        } else {
                            ctx.getString(
                                R.string.active_block_content_limited,
                                item.displayName, item.limitMinutes
                            )
                        },
                        onClick = onOpenApps
                    )
                }
                is ActiveBlockItem.Site -> bindSite(item)
            }
        }

        private fun limitSub(alwaysRes: Int, limitedRes: Int, minutes: Int): CharSequence {
            val ctx = binding.root.context
            return if (minutes <= 0) ctx.getString(alwaysRes)
            else ctx.getString(limitedRes, minutes)
        }

        /** Non-rule blocks are managed on their own (PIN-gated) screens — the
         *  row here is a status card whose tap opens that screen. */
        private fun bindStatusRow(pkg: String, sub: CharSequence, onClick: () -> Unit) {
            val ctx = binding.root.context
            binding.tvAppName.text = AppInfoUtils.getAppName(ctx, pkg)
            setIcon(AppInfoUtils.getAppIcon(ctx, pkg), R.drawable.ic_block)
            binding.tvRuleSub.text = sub
            binding.switchActive.visibility = View.GONE
            binding.btnDelete.visibility = View.GONE
            binding.root.setOnClickListener { onClick() }
        }

        private fun bindSite(item: ActiveBlockItem.Site) {
            val ctx = binding.root.context
            binding.tvAppName.text = item.domain
            setIcon(null, R.drawable.ic_tool_globe)
            binding.tvRuleSub.text = if (item.dailyCapMs == null) {
                ctx.getString(R.string.active_block_site_always)
            } else {
                ctx.getString(R.string.active_block_site_limited, (item.dailyCapMs / 60_000L).toInt())
            }
            binding.switchActive.visibility = View.GONE
            binding.btnDelete.visibility = View.GONE
            binding.root.setOnClickListener { onOpenSites() }
        }

        private fun bindRule(rule: BlockRuleEntity) {
            val ctx = binding.root.context
            // The sleep-mode sentinel rule is not a real package.
            val isSleep = rule.packageName == BlockRuleEvaluator.SLEEP_MODE_PACKAGE
            binding.tvAppName.text =
                if (isSleep) ctx.getString(R.string.blocking_sleep_mode)
                else AppInfoUtils.getAppName(ctx, rule.packageName)
            val icon = if (isSleep) null else AppInfoUtils.getAppIcon(ctx, rule.packageName)
            setIcon(icon, if (isSleep) R.drawable.ic_tool_moon else R.drawable.ic_block)
            binding.tvRuleSub.text = subText(ctx, rule)
            binding.switchActive.visibility = View.VISIBLE
            binding.btnDelete.visibility = View.VISIBLE
            binding.root.setOnClickListener(null)
            binding.root.isClickable = false
            // Clear the recycled listener before setting state or it fires for the old rule.
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = rule.isActive
            binding.switchActive.setOnCheckedChangeListener { _, checked ->
                onToggle(rule.id, checked)
            }
            binding.btnDelete.setOnClickListener { onDelete(rule.id) }
        }

        private fun setIcon(icon: android.graphics.drawable.Drawable?, fallbackRes: Int) {
            if (icon != null) {
                binding.ivAppIcon.imageTintList = null
                binding.ivAppIcon.setImageDrawable(icon)
            } else {
                binding.ivAppIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, R.color.dash_amber)
                )
                binding.ivAppIcon.setImageResource(fallbackRes)
            }
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
        val DIFF = object : DiffUtil.ItemCallback<ActiveBlockItem>() {
            override fun areItemsTheSame(a: ActiveBlockItem, b: ActiveBlockItem) = a.key == b.key
            override fun areContentsTheSame(a: ActiveBlockItem, b: ActiveBlockItem) = a == b
        }
    }
}
