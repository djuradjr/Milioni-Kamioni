package com.example.stayfree.ui.common

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.example.stayfree.R

/**
 * Animates a numeric TextView from its last shown value to [target] (count-up).
 * The previous value and the running animator are kept in view tags, so repeated
 * calls (e.g. flow re-emissions) transition smoothly instead of jumping.
 */
object CountUp {

    fun animate(view: TextView, target: Long, format: (Long) -> String) {
        (view.getTag(R.id.tag_count_animator) as? ValueAnimator)?.cancel()
        val from = (view.getTag(R.id.tag_count_value) as? Long) ?: 0L
        view.setTag(R.id.tag_count_value, target)
        if (from == target) {
            view.text = format(target)
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val value = from + ((target - from) * fraction).toLong()
                view.text = format(value)
            }
            start()
        }
        view.setTag(R.id.tag_count_animator, animator)
    }
}
