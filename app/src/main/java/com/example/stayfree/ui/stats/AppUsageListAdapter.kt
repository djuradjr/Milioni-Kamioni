package com.example.stayfree.ui.stats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stayfree.R
import com.example.stayfree.databinding.ItemAppUsageBinding
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.TimeUtils

class AppUsageListAdapter(
    private val onClick: (AppUsage) -> Unit
) : ListAdapter<AppUsage, AppUsageListAdapter.ViewHolder>(DIFF) {

    // Usage of the #1 app; each row's bar is drawn relative to it.
    private var maxTimeMs: Long = 1L

    override fun submitList(list: List<AppUsage>?) {
        maxTimeMs = (list?.maxOfOrNull { it.totalTimeMs } ?: 1L).coerceAtLeast(1L)
        super.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), maxTimeMs)
    }

    inner class ViewHolder(private val binding: ItemAppUsageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(appUsage: AppUsage, maxTimeMs: Long) {
            binding.tvAppName.text = appUsage.appName
            binding.tvUsageTime.text = TimeUtils.formatDuration(appUsage.totalTimeMs)
            binding.tvUnlockCount.text =
                binding.root.context.getString(R.string.stats_unlocks_count, appUsage.unlockCount)
            binding.progressUsage.setProgressCompat(
                ((appUsage.totalTimeMs * 100) / maxTimeMs).toInt().coerceIn(0, 100),
                true
            )
            val icon = AppInfoUtils.getAppIcon(binding.root.context, appUsage.packageName)
            if (icon != null) binding.ivAppIcon.setImageDrawable(icon)
            binding.root.setOnClickListener { onClick(appUsage) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppUsage>() {
            override fun areItemsTheSame(a: AppUsage, b: AppUsage) = a.packageName == b.packageName && a.date == b.date
            override fun areContentsTheSame(a: AppUsage, b: AppUsage) = a == b
        }
    }
}
