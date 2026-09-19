package com.example.stayfree.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PremiumPlanTest {

    private fun plan(months: Int, micros: Long) =
        PremiumPlan(months, "", micros, "EUR", 0, "token")

    @Test
    fun `billing periods map to months`() {
        assertEquals(1, PremiumPlan.months("P1M"))
        assertEquals(3, PremiumPlan.months("P3M"))
        assertEquals(6, PremiumPlan.months("P6M"))
        assertEquals(12, PremiumPlan.months("P1Y"))
        assertNull(PremiumPlan.months("P1W"))
        assertNull(PremiumPlan.months("garbage"))
    }

    @Test
    fun `trial periods map to days`() {
        assertEquals(7, PremiumPlan.days("P7D"))
        assertEquals(7, PremiumPlan.days("P1W"))
        assertEquals(3, PremiumPlan.days("P3D"))
        assertNull(PremiumPlan.days("P1M"))
    }

    @Test
    fun `discount is the per-month saving against monthly`() {
        val monthly = plan(1, 3_990_000)
        assertEquals(17, PremiumPlan.discountPercent(plan(3, 9_990_000), monthly))
        assertEquals(29, PremiumPlan.discountPercent(plan(6, 16_990_000), monthly))
        assertEquals(0, PremiumPlan.discountPercent(monthly, monthly))
    }

    @Test
    fun `a longer plan that costs more per month shows no discount`() {
        assertEquals(0, PremiumPlan.discountPercent(plan(3, 15_000_000), plan(1, 3_990_000)))
    }
}
