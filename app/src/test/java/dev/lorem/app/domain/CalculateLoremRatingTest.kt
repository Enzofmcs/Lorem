package dev.lorem.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateLoremRatingTest {
    @Test fun `approved scenario table is stable`() {
        assertEquals(24, calculate(1200, 1200, true, 45, false).delta)
        assertEquals(18, calculate(1200, 1200, true, 45, true).delta)
        assertEquals(37, calculate(1200, 1600, true, 90, false).delta)
        assertEquals(12, calculate(1200, 1200, true, 150, false).delta)
        assertEquals(-20, calculate(1200, 1200, false, 60, false).delta)
        assertEquals(-20, calculate(1200, 1200, false, 1, false).delta)
        assertEquals(-10, calculate(1200, 1600, false, 100, false).delta)
        assertEquals(-30, calculate(1200, 800, false, 30, false).delta)
    }

    @Test fun `hint difficulty time and result invariants hold`() {
        val plain = calculate(1200, 1200, true, 60, false)
        val hinted = calculate(1200, 1200, true, 60, true)
        val harder = calculate(1200, 1600, true, 60, false)
        val verySlow = calculate(1200, 1200, true, 600, false)
        assertTrue(plain.delta > hinted.delta)
        assertTrue(harder.delta > plain.delta)
        assertTrue(verySlow.delta < plain.delta)
        assertTrue(calculate(1200, 1200, false, 1, false).delta <= 0)
    }

    @Test fun `expected time and caps are respected`() {
        assertEquals(30 * 60_000L, calculate(2000, 800, true, 1, false).expectedTimeMillis)
        assertEquals(120 * 60_000L, calculate(800, 2400, true, 1, false).expectedTimeMillis)
        assertTrue(calculate(800, 4000, true, 1, false).delta <= 40)
        assertTrue(calculate(4000, 800, false, 1, false).delta >= -32)
    }

    private fun calculate(before: Int, problem: Int, accepted: Boolean, minutes: Int, hint: Boolean) =
        calculateLoremRating(before, problem, accepted, minutes * 60_000L, hint)
}
