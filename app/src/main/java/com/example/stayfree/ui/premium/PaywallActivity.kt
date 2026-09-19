package com.example.stayfree.ui.premium

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumPlan
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.databinding.ActivityPaywallBinding
import com.example.stayfree.databinding.ItemPaywallPlanBinding
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import javax.inject.Inject

@AndroidEntryPoint
class PaywallActivity : AppCompatActivity() {

    @Inject lateinit var premium: PremiumRepository
    private val viewModel: PaywallViewModel by viewModels()
    private lateinit var binding: ActivityPaywallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = resources.getBoolean(R.bool.light_status_bar)

        binding.btnClose.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.loadPlans() }
        binding.btnRestore.setOnClickListener { restore() }
        binding.btnSubscribe.setOnClickListener { subscribe() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.plans, viewModel.selected, ::Pair)
                        .collect { (plans, selected) -> bindPlans(plans, selected) }
                }
                launch { viewModel.purchasePending.collect { binding.tvPending.isVisible = it } }
                launch { viewModel.distractionMs.collect(::bindStat) }
                launch {
                    viewModel.premiumActive.first { it }
                    Toast.makeText(this@PaywallActivity, R.string.paywall_welcome, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A pending payment may have cleared while the user was in another app.
        premium.refresh()
    }

    private fun subscribe() {
        val plan = viewModel.selectedPlan() ?: return
        if (!premium.launchPurchase(this, plan)) {
            Toast.makeText(this, R.string.paywall_error_launch, Toast.LENGTH_SHORT).show()
        }
    }

    private fun restore() {
        lifecycleScope.launch {
            binding.btnRestore.isEnabled = false
            // An owned subscription closes the screen through the premiumActive collector.
            if (!viewModel.restore()) {
                Toast.makeText(this@PaywallActivity, R.string.paywall_restore_none, Toast.LENGTH_SHORT).show()
            }
            binding.btnRestore.isEnabled = true
        }
    }

    private fun bindStat(ms: Long?) {
        binding.tvStat.isVisible = ms != null
        if (ms == null) return
        val duration = TimeUtils.formatDuration(ms)
        val text = getString(R.string.paywall_stat, duration)
        val start = text.indexOf(duration)
        binding.tvStat.text = SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@PaywallActivity, R.color.skor_distraction)),
                start, start + duration.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun bindPlans(state: PaywallViewModel.Plans, selected: Int) {
        binding.progress.isVisible = state is PaywallViewModel.Plans.Loading
        binding.groupUnavailable.isVisible = state is PaywallViewModel.Plans.Unavailable
        binding.groupCta.isVisible = state is PaywallViewModel.Plans.Ready
        binding.plans.removeAllViews()
        if (state !is PaywallViewModel.Plans.Ready) return

        val plans = state.plans
        val monthly = plans.firstOrNull { it.months == 1 }
        val best = plans.takeIf { it.size > 1 }?.minByOrNull { it.perMonthMicros }
        plans.forEachIndexed { index, plan ->
            val row = ItemPaywallPlanBinding.inflate(LayoutInflater.from(this), binding.plans, true)
            row.root.isSelected = index == selected
            row.root.setOnClickListener { viewModel.select(index) }
            row.tvPlanTitle.text = resources.getQuantityString(R.plurals.paywall_plan_months, plan.months, plan.months)
            row.tvPlanPrice.text =
                if (plan.months == 1) getString(R.string.paywall_price_monthly, plan.price)
                else getString(R.string.paywall_price_per_month, plan.price, perMonth(plan))
            val discount = monthly?.let { PremiumPlan.discountPercent(plan, it) } ?: 0
            row.tvPlanBadge.isVisible = discount > 0
            row.tvPlanBadge.text =
                if (plan == best) getString(R.string.paywall_badge_best, discount)
                else getString(R.string.paywall_badge_discount, discount)
        }

        val plan = plans.getOrNull(selected) ?: return
        val every = resources.getQuantityString(R.plurals.paywall_every_months, plan.months, plan.months)
        if (plan.trialDays > 0) {
            binding.btnSubscribe.text =
                resources.getQuantityString(R.plurals.paywall_cta_trial, plan.trialDays, plan.trialDays)
            binding.tvTerms.text = getString(R.string.paywall_terms_trial, plan.trialDays, plan.price, every)
        } else {
            binding.btnSubscribe.setText(R.string.paywall_cta_subscribe)
            binding.tvTerms.text = getString(R.string.paywall_terms, plan.price, every)
        }
    }

    private fun perMonth(plan: PremiumPlan): String =
        NumberFormat.getCurrencyInstance().apply {
            currency = Currency.getInstance(plan.currencyCode)
        }.format(plan.perMonthMicros / 1_000_000.0)

    companion object {
        fun newIntent(context: Context) = Intent(context, PaywallActivity::class.java)
    }
}
