package dev.lorem.app.data.cloud

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VirtualDuelTest {
    private fun catalog() = VirtualDuelCatalog(ApplicationProvider.getApplicationContext<Application>().assets
        .open("virtual-duel-v1.json").bufferedReader().use { it.readText() })
    @Test fun `Python fixtures match Kotlin integer calculation CDF snapshot and result serialization`() {
        val catalog = catalog()
        val fixtures = JSONObject(File("../study/results/virtual-duel-v1/scenarios.json").readText()).getJSONArray("parity_cases")
        for (i in 0 until fixtures.length()) {
            val c = fixtures.getJSONObject(i)
            val snap = catalog.select(c.getLong("rating_micros"), c.getInt("difficulty")).copy(k = c.getInt("k"))
            val result = catalog.evaluate(snap, c.getLong("elapsed_ms"), c.getBoolean("accepted"))
            assertEquals("case $i", c.getLong("delta_micros"), result.deltaMicros)
            assertEquals(c.getLong("left"), result.cdfLeft)
            assertEquals(c.getLong("at"), result.cdfAt)
            assertEquals(c.getLong("horizon"), result.cdfHorizon)
            assertEquals(snap, virtualDuelSnapshot(snap.toDocument()))
            assertEquals(result, virtualDuelResult(result.toDocument()))
        }
    }
    @Test fun `subpoint rating drives next expectation and invalid models domains versions and time fail closed`() {
        val c = catalog()
        assertEquals(400, c.select(800 * DUEL_SCALE, 1200).curveMin)
        assertEquals(0, c.select(800 * DUEL_SCALE + 1, 1200).curveMin)
        val s = c.select(800 * DUEL_SCALE, 800)
        val result = c.evaluate(s, 1000, true)
        assertEquals(result.afterMicros, c.select(result.afterMicros, 800).ratingMicros)
        assertThrows(IllegalArgumentException::class.java) { c.select(800 * DUEL_SCALE, 3501) }
        assertThrows(IllegalArgumentException::class.java) { c.select(3000 * DUEL_SCALE, 800) }
        assertThrows(IllegalArgumentException::class.java) { c.evaluate(s, -1, true) }
        assertThrows(IllegalArgumentException::class.java) { c.evaluate(s, TIME_SCORING_CAP_MS + 1, true) }
        assertThrows(IllegalArgumentException::class.java) { s.copy(k = 10) }
        assertThrows(IllegalArgumentException::class.java) { s.copy(sha256 = "a".repeat(64)) }
    }
    @Test fun `ties all success and all failure have symmetric bounds and no floor`() {
        val s = VirtualDuelSnapshot(800 * DUEL_SCALE, 800, 0)
        val curve = DuelCurve(listOf(1000, 2000), listOf(200000, DUEL_SCALE))
        assertEquals(0L to 200000L, curve.pair(1000))
        assertEquals(200000L to 200000L, curve.pair(1001))
        assertEquals(-16 * DUEL_SCALE, calculateVirtualDuel(s, curve, 2001, true).deltaMicros)
        assertEquals(-16 * DUEL_SCALE, calculateVirtualDuel(s, curve, 0, false).deltaMicros)
        assertEquals(0, virtualDuelDelta(0,0,0,32,false).toInt())
        assertThrows(IllegalArgumentException::class.java) { DuelCurve(listOf(1000, 1000),listOf(1,2)) }
    }
}
