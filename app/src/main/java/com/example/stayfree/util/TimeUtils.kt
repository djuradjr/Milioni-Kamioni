package com.example.stayfree.util

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Returns the "effective date" string based on the user-configured daily reset time.
     * If the current time is before today's reset, we're still in "yesterday's day".
     * [nowMs] is injectable for tests.
     */
    fun getEffectiveDate(resetTimeMinutes: Int, nowMs: Long = System.currentTimeMillis()): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMs
        calendar.set(Calendar.HOUR_OF_DAY, resetTimeMinutes / 60)
        calendar.set(Calendar.MINUTE, resetTimeMinutes % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayResetMs = calendar.timeInMillis
        return if (nowMs < todayResetMs) {
            dateFormat.format(Date(todayResetMs - 86_400_000L))
        } else {
            dateFormat.format(Date(nowMs))
        }
    }

    fun getTodayString(): String = dateFormat.format(Date())

    fun getDateString(epochMs: Long): String = dateFormat.format(Date(epochMs))

    fun getDateStringDaysAgo(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return dateFormat.format(cal.time)
    }

    /** Epoch ms of local midnight for a "yyyy-MM-dd" string, or null if unparsable. */
    fun getDayStartMs(date: String): Long? {
        val parsed = try {
            dateFormat.parse(date)
        } catch (e: java.text.ParseException) {
            null
        } ?: return null
        return Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Shifts a "yyyy-MM-dd" string by [days] (negative = past). Returns the input if unparsable. */
    fun addDays(date: String, days: Int): String {
        val startMs = getDayStartMs(date) ?: return date
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMs
            add(Calendar.DAY_OF_YEAR, days)
        }
        return dateFormat.format(cal.time)
    }

    /** Formats a "yyyy-MM-dd" string for display, e.g. "Fri, 4 Jul". Returns the input if unparsable. */
    fun formatDisplayDate(date: String): String {
        val startMs = getDayStartMs(date) ?: return date
        return SimpleDateFormat("EEE, d MMM", Locale.US).format(Date(startMs))
    }

    /** Hero-label date in the device language, e.g. "sreda 16. jul". Returns the input if unparsable. */
    fun formatHeroDate(date: String): String {
        val startMs = getDayStartMs(date) ?: return date
        return SimpleDateFormat("EEEE d. MMM", Locale.getDefault()).format(Date(startMs))
    }

    /** Narrow weekday initial (e.g. "M", "T") for a "yyyy-MM-dd" string. */
    fun dayInitial(date: String): String {
        val startMs = getDayStartMs(date) ?: return ""
        return SimpleDateFormat("EEEEE", Locale.US).format(Date(startMs))
    }

    /** Day-of-month number (e.g. "4", "18") for a "yyyy-MM-dd" string. */
    fun dayOfMonth(date: String): String {
        val startMs = getDayStartMs(date) ?: return ""
        return SimpleDateFormat("d", Locale.US).format(Date(startMs))
    }

    /** Format milliseconds to human-readable "Xh Ym" or "Ym Zs" */
    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /** Returns minutes from midnight for the current time */
    fun currentTimeMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** Returns the current day-of-week abbreviation (MON, TUE, ...) */
    fun currentDayAbbreviation(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "MON"
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            Calendar.SATURDAY -> "SAT"
            Calendar.SUNDAY -> "SUN"
            else -> "MON"
        }
    }

    /**
     * Checks whether the current time falls within a schedule window.
     * Handles overnight schedules (e.g. 23:00 – 07:00).
     * [now] and [currentDay] are injectable for tests.
     */
    fun isInScheduleWindow(
        daysOfWeek: String,
        startMinutes: Int,
        endMinutes: Int,
        now: Int = currentTimeMinutes(),
        currentDay: String = currentDayAbbreviation()
    ): Boolean {
        val days = daysOfWeek.split(",").map { it.trim() }

        if (startMinutes <= endMinutes) {
            // Same day window
            return currentDay in days && now in startMinutes until endMinutes
        } else {
            // Overnight window — e.g. 23:00 (1380) to 07:00 (420)
            val prevDay = getPreviousDayAbbreviation(currentDay)
            return (currentDay in days && now >= startMinutes) ||
                    (prevDay in days && now < endMinutes)
        }
    }

    private fun getPreviousDayAbbreviation(day: String): String {
        val order = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val idx = order.indexOf(day)
        return order[(idx - 1 + 7) % 7]
    }
}
