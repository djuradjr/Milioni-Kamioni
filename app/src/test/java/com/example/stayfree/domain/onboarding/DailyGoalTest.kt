package com.example.stayfree.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyGoalTest {

    @Test
    fun `ten hour average with a five hour goal is five hours less, half`() {
        assertEquals(DailyGoal.Cut(300, 50), DailyGoal.cut(averageMinutes = 600, goalMinutes = 300))
    }

    @Test
    fun `goal plus the cut always adds back up to the average`() {
        for (average in listOf(95, 446, 600, 719)) {
            var goal = DailyGoal.MIN_MINUTES
            while (goal < average) {
                assertEquals(average, goal + DailyGoal.cut(average, goal)!!.minutes)
                goal = DailyGoal.nudge(goal, 1)
            }
        }
    }

    @Test
    fun `goal at or above the average has no cut`() {
        assertNull(DailyGoal.cut(averageMinutes = 450, goalMinutes = 450))
        assertNull(DailyGoal.cut(averageMinutes = 450, goalMinutes = 460))
    }

    @Test
    fun `suggestion is a quarter under the average on the ten minute grid`() {
        assertEquals(340, DailyGoal.suggest(450))
        assertEquals(450, DailyGoal.suggest(600))
    }

    @Test
    fun `suggestion and stepper stay inside the allowed range`() {
        assertEquals(DailyGoal.MIN_MINUTES, DailyGoal.suggest(3))
        assertEquals(DailyGoal.MAX_MINUTES, DailyGoal.suggest(20 * 60))
        assertEquals(DailyGoal.MIN_MINUTES, DailyGoal.nudge(DailyGoal.MIN_MINUTES, -1))
        assertEquals(DailyGoal.MAX_MINUTES, DailyGoal.nudge(DailyGoal.MAX_MINUTES, 1))
    }
}
