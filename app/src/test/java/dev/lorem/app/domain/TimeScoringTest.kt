package dev.lorem.app.domain

import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TimeScoringTest {
    private val bounds = TimeLimits(60_000, 120_000, 180_000)
    private fun gain(t: Long, hint: Boolean = false, base: Int = 24) =
        applyTimeScoring(base, t, bounds, 1.25, .5, hint, TIME_CURVE_POLICY)
    private fun snapshot() = ScoringSnapshot(TIME_CURVE_POLICY, bounds, modelVersion = "frozen-test-v1",
        artifactSha256 = "a".repeat(64), inputRating = 1200, unavailableReason = null)
    private fun attempt() = CloudIpsum("test", "cf_test", 0, CloudIpsumState.ACTIVE,
        Ipsum(1, "test", CodeforcesProblem(ProblemId(2000, "A"), "Synthetic", 1600, setOf("math")),
            800, IpsumCategory.CHALLENGE, 1600, 1600, 1600, 0, 1_000_000, status = IpsumStatus.ACTIVE,
            scoringSnapshot = snapshot(), initialCodeforcesRating = 1200))

    @Test fun `before exactly and after every boundary`() {
        val expected = listOf(59_999L to (TimeBand.BONUS to 30), 60_000L to (TimeBand.NORMAL to 24),
            60_001L to (TimeBand.NORMAL to 24), 119_999L to (TimeBand.NORMAL to 24),
            120_000L to (TimeBand.REDUCED to 12), 120_001L to (TimeBand.REDUCED to 12),
            179_999L to (TimeBand.REDUCED to 12), 180_000L to (TimeBand.ZERO to 0),
            180_001L to (TimeBand.ZERO to 0), 7_200_000L to (TimeBand.ZERO to 0))
        expected.forEach { (t, result) -> assertEquals(result, gain(t).let { it.band to it.gain }) }
    }
    @Test fun `hint rounding zero base and hard problems never create negative accepted gain`() {
        assertEquals(22, gain(0, true).gain) // 30 - round(7.5)
        assertEquals(0, gain(180_000, true).gain)
        assertEquals(0, gain(0, true, 0).gain)
        assertEquals(0, gain(120_000, true, 1).gain)
        assertEquals(50, gain(0, false, 40).gain)
        for (b in 0..40) for (hint in listOf(false, true)) {
            val values = listOf(0L, 60_000L, 120_000L, 180_000L).map { gain(it, hint, b).gain }
            assertTrue(values.all { it >= 0 })
            assertTrue(values.zipWithNext().all { it.first >= it.second })
        }
    }
    @Test fun `invalid bounds versions multipliers and times are explicit errors`() {
        val invalid = listOf<() -> Any>({ TimeLimits(0, 2, 3) }, { TimeLimits(1, 1, 3) },
            { TimeLimits(1, 2, 7_200_001) }, { gain(-1) }, { gain(0, base = -1) },
            { applyTimeScoring(24, 0, bounds, Double.NaN, .5, false, TIME_CURVE_POLICY) },
            { applyTimeScoring(24, 0, bounds, 1.25, 2.0, false, TIME_CURVE_POLICY) },
            { applyTimeScoring(24, 0, bounds, 1.25, .5, false, "future") })
        invalid.forEach { action -> try { action(); fail("Accepted invalid input") } catch (_: IllegalArgumentException) {} }
    }
    @Test fun `finalization uses snapshot despite new model and failure stays D012`() {
        val old = attempt()
        val updatedPolicy = snapshot().copy(limits = TimeLimits(600_000, 700_000, 800_000))
        val ac = CloudVerification.Accepted(old.ipsum.startedAtEpochMillis + 180_000, 2)
        val original = finalizeCloudIpsum(old, ac, null)
        assertEquals(0, original.ratingDelta)
        assertEquals(800, original.finalLoremRating)
        assertEquals(snapshot(), original.scoringSnapshot)
        assertEquals(original, finalizeCloudIpsum(old, ac, null))
        assertTrue(finalizeCloudIpsum(old.copy(ipsum = old.ipsum.copy(scoringSnapshot = updatedPolicy)), ac, null).ratingDelta!! > 0)
        val closed = old.copy(cutoffMillis = old.expiresAtMillis, closeReason = CloudCloseReason.TIME_LIMIT_EXCEEDED)
        val fail = finalizeCloudIpsum(closed, CloudVerification.NoAccepted(0), null)
        assertNull(fail.timeGain)
        assertEquals(calculateLoremRating(800, 1600, false, IPSUM_LIMIT_MILLIS, false).delta, fail.ratingDelta)
    }
    @Test fun `old and fallback attempts preserve original D012`() {
        val a = attempt()
        val ac = CloudVerification.Accepted(a.ipsum.startedAtEpochMillis + 180_000, 0)
        val legacy = a.copy(ipsum = a.ipsum.copy(scoringSnapshot = null))
        val fallback = a.copy(ipsum = a.ipsum.copy(scoringSnapshot = ScoringSnapshot(LEGACY_SCORING_POLICY, null,
            modelVersion = "test", artifactSha256 = "a".repeat(64), inputRating = 1200, unavailableReason = "activation_disabled")))
        assertEquals(calculateLoremRating(800, 1600, true, 180_000, false).delta, finalizeCloudIpsum(legacy, ac, null).ratingDelta)
        assertEquals(finalizeCloudIpsum(legacy, ac, null).ratingDelta, finalizeCloudIpsum(fallback, ac, null).ratingDelta)
    }
}
