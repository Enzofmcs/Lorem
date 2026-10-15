package dev.lorem.app.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CodeforcesRequestGateTest {
    @Test
    fun `shared gate interleaves paginated import and screen queries without exceeding rate`() = runTest {
        val starts = mutableListOf<Pair<String, Long>>()
        val gate = TwoSecondCodeforcesRequestGate(nowMillis = { testScheduler.currentTime })
        val import = async {
            repeat(3) {
                gate.execute { starts += "page" to testScheduler.currentTime; delay(100) }
            }
        }
        val screen = async {
            delay(50)
            gate.execute { starts += "ipsum" to testScheduler.currentTime }
        }
        awaitAll(import, screen)
        assertEquals(4, starts.size)
        assertEquals("ipsum", starts[1].first)
        starts.zipWithNext().forEach { (a, b) -> org.junit.Assert.assertTrue(b.second - a.second >= 2_000) }
    }
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
