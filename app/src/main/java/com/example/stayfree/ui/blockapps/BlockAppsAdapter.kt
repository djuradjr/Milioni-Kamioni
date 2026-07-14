package com.example.stayfree.ui.blockapps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stayfree.R
import com.example.stayfree.databinding.ItemBlockAppBinding
import com.example.stayfree.databinding.ItemContentTargetRowBinding
import com.example.stayfree.util.AppInfoUtils

class BlockAppsAdapter(
    private val onToggle: (packageName: String, blocked: Boolean) -> Unit,
    private val onLimitChange: (packageName: String, minutes: Int, increase: Boolean) -> Unit,
    private val onContentToggle: (targetId: String, enabled: Boolean) -> Unit,
    private val onContentLimitChange: (targetId: String, minutes: Int, increase: Boolean) -> Unit
) : ListAdapter<BlockAppItem, BlockAppsAdapter.ViewHolder>(DIFF) {

    // Expanded app rows — UI-only state, intentionally not persisted.
    private val expandedPkgs = mutableSetOf<String>()

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

            val expandable = item.contentTargets.isNotEmpty()
            val expanded = expandable && item.packageName in expandedPkgs
            binding.btnExpand.visibility = if (expandable) View.VISIBLE else View.GONE
            binding.switchActive.visibility = if (expandable) View.GONE else View.VISIBLE
            binding.expandableContent.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.btnExpand.rotation = if (expanded) 180f else 0f
            binding.btnExpand.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (item.packageName in expandedPkgs) expandedPkgs.remove(item.packageName)
                else expandedPkgs.add(item.packageName)
                notifyItemChanged(pos)
            }

            // The full-block switch lives in the header for plain apps and inside
            // the expansion for apps with content targets — same preference key.
            val fullBlockSwitch = if (expandable) binding.switchFullBlock else binding.switchActive
            // Detach the recycled listener before setting state so restoring the
            // switch position doesn't fire a spurious toggle.
            fullBlockSwitch.setOnCheckedChangeListener(null)
            fullBlockSwitch.isChecked = item.isBlocked
            fullBlockSwitch.setOnCheckedChangeListener { btn, checked ->
                if (btn.isPressed) onToggle(item.packageName, checked)
            }

            bindLimitStepper(item, expanded)
            if (expanded) bindContentTargets(item) else binding.contentTargetsContainer.removeAllViews()
        }

        private fun bindLimitStepper(item: BlockAppItem, expanded: Boolean) {
            val expandable = item.contentTargets.isNotEmpty()
            val visible = item.isBlocked && (!expandable || expanded)
            binding.cardLimit.visibility = if (visible) View.VISIBLE else View.GONE
            if (!visible) return

            binding.tvLimitValue.text = formatLimit(item.limitMinutes)

            // A stored value that fell off the ladder still displays as-is; stepping
            // from it snaps to the nearest preset.
            val idx = nearestStepIndex(LIMIT_STEPS, item.limitMinutes)
            bindArrow(binding.btnLimitDown, enabled = idx > 0) {
                onLimitChange(item.packageName, LIMIT_STEPS[idx - 1], false)
            }
            bindArrow(binding.btnLimitUp, enabled = idx < LIMIT_STEPS.lastIndex) {
                onLimitChange(item.packageName, LIMIT_STEPS[idx + 1], true)
            }
        }

        private fun bindContentTargets(item: BlockAppItem) {
            val container = binding.contentTargetsContainer
            container.removeAllViews()
            val inflater = LayoutInflater.from(container.context)
            for (target in item.contentTargets) {
                val row = ItemContentTargetRowBinding.inflate(inflater, container, true)
                row.tvTargetName.text =
                    container.context.getString(R.string.block_content_toggle, target.displayName)

                row.switchTarget.setOnCheckedChangeListener(null)
                row.switchTarget.isChecked = target.enabled
                row.switchTarget.setOnCheckedChangeListener { btn, checked ->
                    if (btn.isPressed) onContentToggle(target.id, checked)
                }

                row.cardTargetLimit.visibility = if (target.enabled) View.VISIBLE else View.GONE
                if (target.enabled) {
                    row.tvTargetLimitValue.text = formatLimit(target.limitMinutes)
                    val idx = nearestStepIndex(CONTENT_LIMIT_STEPS, target.limitMinutes)
                    bindArrow(row.btnTargetLimitDown, enabled = idx > 0) {
                        onContentLimitChange(target.id, CONTENT_LIMIT_STEPS[idx - 1], false)
                    }
                    bindArrow(row.btnTargetLimitUp, enabled = idx < CONTENT_LIMIT_STEPS.lastIndex) {
                        onContentLimitChange(target.id, CONTENT_LIMIT_STEPS[idx + 1], true)
                    }
                }
            }
        }

        private fun formatLimit(minutes: Int): String = if (minutes <= 0) {
            binding.root.context.getString(R.string.block_app_limit_block_now)
        } else {
            binding.root.context.getString(R.string.block_app_limit_minutes, minutes)
        }

        private fun bindArrow(button: ImageButton, enabled: Boolean, onClick: () -> Unit) {
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.3f
            button.setOnClickListener { if (enabled) onClick() }
        }
    }

    companion object {
        /** Allowed whole-app daily-limit values in minutes; 0 = block immediately. */
        val LIMIT_STEPS = listOf(0, 5, 10, 15, 30, 45, 60, 90, 120, 180)

        /** Allowed per-content-target daily-limit values in minutes. */
        val CONTENT_LIMIT_STEPS = listOf(0, 5, 10, 15, 30, 45, 60)

        private fun nearestStepIndex(steps: List<Int>, minutes: Int): Int {
            val exact = steps.indexOf(minutes)
            if (exact >= 0) return exact
            return steps.indices.minByOrNull { kotlin.math.abs(steps[it] - minutes) } ?: 0
        }

        val DIFF = object : DiffUtil.ItemCallback<BlockAppItem>() {
            override fun areItemsTheSame(a: BlockAppItem, b: BlockAppItem) = a.packageName == b.packageName
            override fun areContentsTheSame(a: BlockAppItem, b: BlockAppItem) = a == b
        }
    }
}
