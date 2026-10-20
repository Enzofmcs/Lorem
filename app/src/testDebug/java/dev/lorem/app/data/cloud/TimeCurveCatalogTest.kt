package dev.lorem.app.data.cloud

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.domain.*
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TimeCurveCatalogTest {
    private fun bundled(): String = ApplicationProvider.getApplicationContext<Application>().assets
        .open("time-curves-v1.json").bufferedReader().use { it.readText() }
    private fun synthetic(): JSONObject = JSONObject(bundled()).apply {
        put("data_kind", "synthetic").put("activation", "enabled")
        getJSONArray("regions").getJSONObject(0).apply {
            put("unavailable_reason", JSONObject.NULL)
            put("limits", JSONObject().put("t1_ms", 1_800_000).put("t2_ms", 3_600_000).put("t3_ms", 7_200_000))
            put("quantiles_minutes", JSONObject().put("0.5", 30).put("0.75", 60).put("0.9", 120))
            put("support", JSONObject().put("attempts", 100).put("events", 90)
                .put("minimum_attempts", 100).put("minimum_events", 30).put("max_followup_seconds", 7200))
        }
    }
    @Test fun `bundled real partial query never invents missing quantiles or replaces Lorem 800`() {
        val catalog = TimeCurveCatalog(bundled())
        assertEquals("quantile_not_identified", catalog.select(1200, 1600).unavailableReason)
        assertEquals("missing_prior_rating", catalog.select(null, 1600).unavailableReason)
        assertEquals("unsupported_region", catalog.select(800, 1600).unavailableReason)
    }
    @Test fun `replacing artifact cannot enable scoring without independent activation`() {
        assertEquals("activation_disabled", TimeCurveCatalog(synthetic().toString()).select(1200, 1600).unavailableReason)
        val selected = TimeCurveCatalog(synthetic().toString(), activationEnabled = true).select(1200, 1600)
        assertEquals(TIME_CURVE_POLICY, selected.policyVersion)
        assertEquals(1200, selected.inputRating)
        val saved = scoringSnapshot(selected.toDocument())
        assertEquals(selected, saved)
        val mutated = synthetic().apply { getJSONArray("regions").getJSONObject(0).getJSONObject("limits").put("t1_ms", 5) }
        assertEquals("invalid_limits_or_support", TimeCurveCatalog(mutated.toString(), true).select(1200, 1600).unavailableReason)
        assertEquals(selected, scoringSnapshot(saved.toDocument()))
    }
    @Test fun `Python exported delta bins enforce support domain and exact upper edge`() {
        val artifact = JSONObject(File("../study/tests/fixtures/time-curves-synthetic.json").readText())
        artifact.put("activation", "enabled")
        val catalog = TimeCurveCatalog(artifact.toString(), true)
        assertEquals(TIME_CURVE_POLICY, catalog.select(1200, 1600).policyVersion)
        assertEquals(TIME_CURVE_POLICY, catalog.select(1200, 1699).policyVersion)
        assertEquals("unsupported_region", catalog.select(1200, 1700).unavailableReason)
        assertEquals("out_of_domain", catalog.select(2500, 2900).unavailableReason)
        artifact.getJSONArray("regions").getJSONObject(0).getJSONObject("support").put("minimum_attempts", 100000)
        assertEquals("invalid_limits_or_support", TimeCurveCatalog(artifact.toString(), true).select(1200, 1600).unavailableReason)
        assertEquals("invalid_artifact", TimeCurveCatalog("{broken").select(1200, 1600).unavailableReason)
    }
    @Test fun `Python scenario artifact agrees with Kotlin boundaries rounding hint and zero`() {
        val data = JSONObject(File("../study/results/time-scoring-v1/scenarios.json").readText())
        val b = data.getJSONArray("bounds_ms")
        val limits = TimeLimits(b.getLong(0), b.getLong(1), b.getLong(2))
        val cases = data.getJSONArray("parity_cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val gain = applyTimeScoring(c.getInt("base"), c.getLong("elapsed_ms"), limits, 1.25, .5,
                c.getBoolean("hint"), TIME_CURVE_POLICY)
            assertEquals("case $i", c.getInt("gain"), gain.gain)
            assertEquals(c.getInt("timed_gain"), gain.timedGain)
            assertEquals(c.getInt("hint_deduction"), gain.hintDeduction)
            assertEquals(gain, timeGain(gain.toDocument()))
        }
    }
}
