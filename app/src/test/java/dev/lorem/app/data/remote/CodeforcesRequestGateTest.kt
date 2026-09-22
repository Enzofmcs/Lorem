package dev.lorem.app.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeforcesRequestGateTest {
    @Test
    fun `consecutive calls wait until two seconds after previous start`() = runTest {
        var clock = 1_000L
        val waits = mutableListOf<Long>()
        val starts = mutableListOf<Long>()
        val gate = TwoSecondCodeforcesRequestGate(
            nowMillis = { clock },
            wait = { duration -> waits += duration; clock += duration },
        )

        gate.execute { starts += clock }
        clock += 500
        gate.execute { starts += clock }

        assertEquals(listOf(1_000L, 3_000L), starts)
        assertEquals(listOf(1_500L), waits)
    }
}
