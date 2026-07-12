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
import com.example.stayfree.util.AppInfoUtils

class BlockAppsAdapter(
    private val onToggle: (packageName: String, blocked: Boolean) -> Unit,
    private val onLimitChange: (packageName: String, minutes: Int, increase: Boolean) -> Unit
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

            bindLimitStepper(item)
        }

        private fun bindLimitStepper(item: BlockAppItem) {
            binding.cardLimit.visibility = if (item.isBlocked) View.VISIBLE else View.GONE
            if (!item.isBlocked) return

            binding.tvLimitValue.text = if (item.limitMinutes <= 0) {
                binding.root.context.getString(R.string.block_app_limit_block_now)
            } else {
                binding.root.context.getString(R.string.block_app_limit_minutes, item.limitMinutes)
            }

            // A stored value that fell off the ladder still displays as-is; stepping
            // from it snaps to the nearest preset.
            val idx = nearestStepIndex(item.limitMinutes)
            bindArrow(binding.btnLimitDown, enabled = idx > 0) {
                onLimitChange(item.packageName, LIMIT_STEPS[idx - 1], false)
            }
            bindArrow(binding.btnLimitUp, enabled = idx < LIMIT_STEPS.lastIndex) {
                onLimitChange(item.packageName, LIMIT_STEPS[idx + 1], true)
            }
        }

        private fun bindArrow(button: ImageButton, enabled: Boolean, onClick: () -> Unit) {
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.3f
            button.setOnClickListener { if (enabled) onClick() }
        }
    }

    companion object {
        /** Allowed daily-limit values in minutes; 0 = block immediately. */
        val LIMIT_STEPS = listOf(0, 5, 10, 15, 30, 45, 60, 90, 120, 180)

        private fun nearestStepIndex(minutes: Int): Int {
            val exact = LIMIT_STEPS.indexOf(minutes)
            if (exact >= 0) return exact
            return LIMIT_STEPS.indices.minByOrNull { kotlin.math.abs(LIMIT_STEPS[it] - minutes) } ?: 0
        }

        val DIFF = object : DiffUtil.ItemCallback<BlockAppItem>() {
            override fun areItemsTheSame(a: BlockAppItem, b: BlockAppItem) = a.packageName == b.packageName
            override fun areContentsTheSame(a: BlockAppItem, b: BlockAppItem) = a == b
        }
    }
}
