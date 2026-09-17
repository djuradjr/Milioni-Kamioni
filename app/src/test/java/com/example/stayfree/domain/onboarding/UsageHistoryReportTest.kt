package com.example.stayfree.domain.onboarding

import com.example.stayfree.domain.onboarding.UsageHistory.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UsageHistoryReportTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val boundaries = (0..7).map { it * day }

    private fun report(vararg sessions: Session, oldestEventMs: Long = -hour) =
        UsageHistory.buildReport(boundaries, sessions.toList(), oldestEventMs) { it }

    @Test
    fun `session across midnight is split between the two days`() {
        val r = report(Session("a", 1 * day - 1 * hour, 1 * day + 2 * hour))
        assertEquals(1 * hour, r.dailyTotals[0])
        assertEquals(2 * hour, r.dailyTotals[1])
        assertEquals(3 * hour, r.totalMs)
    }

    @Test
    fun `seven real days average to the real daily figure`() {
        val sessions = (0 until 7).map { Session("a", it * day + 8 * hour, it * day + 15 * hour) }
        val r = report(*sessions.toTypedArray())
        assertEquals(49 * hour, r.totalMs)
        assertEquals(7, r.measuredDays)
        assertEquals(7 * hour, r.dailyAverageMs)
    }

    @Test
    fun `days without any usage do not dilute the average`() {
        val r = report(
            Session("a", 4 * day + 10 * hour, 4 * day + 12 * hour),
            Session("a", 5 * day + 10 * hour, 5 * day + 14 * hour)
        )
        assertEquals(2, r.measuredDays)
        assertEquals(3 * hour, r.dailyAverageMs)
    }

    @Test
    fun `day only partly in the log is shown but left out of the average`() {
        val r = report(
            Session("a", 2 * day + 20 * hour, 2 * day + 21 * hour),
            Session("a", 3 * day + 9 * hour, 3 * day + 14 * hour),
            oldestEventMs = 2 * day + 19 * hour
        )
        assertEquals(1 * hour, r.dailyTotals[2])
        assertEquals(6 * hour, r.totalMs)
        assertEquals(1, r.measuredDays)
        assertEquals(5 * hour, r.dailyAverageMs)
    }

    @Test
    fun `sessions outside the window count nowhere`() {
        val r = report(Session("a", -2 * hour, 0L), Session("a", 7 * day, 7 * day + hour))
        assertFalse(r.hasData)
    }

    @Test
    fun `top apps are ranked and skip tap-throughs`() {
        val r = report(
            Session("small", 0L, 30 * hour / 60),
            Session("big", 1 * day, 1 * day + 3 * hour),
            Session("blip", 2 * day, 2 * day + 20_000L)
        )
        assertEquals(listOf("big", "small"), r.topApps.map { it.packageName })
    }
}
