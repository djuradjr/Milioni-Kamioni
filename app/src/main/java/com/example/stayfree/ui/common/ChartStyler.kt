package com.example.stayfree.ui.common

import android.content.Context
import com.example.stayfree.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry

/** Shared orange-on-white bar chart styling for Stats and App Detail screens. */
object ChartStyler {

    fun applyBarData(chart: BarChart, context: Context, entries: List<BarEntry>, label: String) {
        val dataSet = BarDataSet(entries, label).apply {
            color = context.getColor(R.color.primary)
            valueTextColor = context.getColor(R.color.on_surface_variant)
            setDrawValues(false)
        }
        chart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setScaleEnabled(false)
            axisRight.isEnabled = false
            axisLeft.apply {
                setDrawGridLines(false)
                axisMinimum = 0f
                textColor = context.getColor(R.color.on_surface_variant)
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = context.getColor(R.color.on_surface_variant)
            }
            animateY(400)
            invalidate()
        }
    }
}
