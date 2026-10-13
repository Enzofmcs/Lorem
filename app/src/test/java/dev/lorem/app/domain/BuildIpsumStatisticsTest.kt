package dev.lorem.app.domain

import dev.lorem.app.StatisticsFixtures
import dev.lorem.app.StatisticsFixtures.attempt
import dev.lorem.app.StatisticsFixtures.finished
import dev.lorem.app.StatisticsFixtures.profile
import dev.lorem.app.domain.model.IpsumStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildIpsumStatisticsTest {
    @Test
    fun `reference history matches every global and grouped aggregate`() {
        val stats = buildIpsumStatistics(profile, finished, StatisticsFixtures.catalog)
        with(stats.overall) {
            assertEquals(4, total)
            assertEquals(2, accepted)
            assertEquals(1, pending)
            assertEquals(1, solvedOutside)
            assertEquals(1, hintCount)
            assertEquals(AcRate(2, 4), acRate)
            assertEquals(AcRate(1, 3), withoutHint)
            assertEquals(AcRate(1, 1), withHint)
            assertEquals(50.0, acRate.percent!!, 0.001)
            assertEquals(100.0 / 3, withoutHint.percent!!, 0.001)
            assertEquals(100.0, withHint.percent!!, 0.001)
            assertEquals(4, timedAttempts)
            assertEquals(40 * 60_000.0, averageTimeMillis!!, 0.001)
            assertEquals(3L, errorsBeforeAc)
            assertEquals(1.5, averageErrorsBeforeAc!!, 0.001)
        }
        val low = stats.byRating.single { it.problemRating == 1200 }.attempts
        val high = stats.byRating.single { it.problemRating == 1400 }.attempts
        assertEquals(AcRate(2, 2), low.acRate)
        assertEquals(37.5 * 60_000, low.averageTimeMillis!!, 0.001)
        assertEquals(AcRate(0, 2), high.acRate)
        assertEquals(42.5 * 60_000, high.averageTimeMillis!!, 0.001)
        val dp = stats.byTag.single { it.tag == "dp" }.attempts
        assertEquals(AcRate(1, 3), dp.acRate)
        assertEquals(2, dp.failures)
        assertEquals(1, dp.pending)
        assertEquals(1, dp.solvedOutside)
        assertEquals(0, dp.hintCount)
        assertEquals(115.0 / 3 * 60_000, dp.averageTimeMillis!!, 0.001)
        assertEquals(1L, dp.errorsBeforeAc)
        assertEquals(AcRate(2, 2), stats.byTag.single { it.tag == "graphs" }.attempts.acRate)
        assertEquals(AcRate(0, 1), stats.byTag.single { it.tag == "math" }.attempts.acRate)
        assertEquals(AcRate(0, 0), stats.byTag.single { it.tag == "greedy" }.attempts.acRate)
        assertEquals("dp", stats.mostPracticed.first().tag)
        assertEquals("greedy", stats.leastPracticed.first().tag)
        assertEquals(listOf("math", "dp"), stats.highestFailure.map { it.tag })
        assertEquals(1202, stats.currentRating)
        assertEquals(listOf(24, 18, -20, -20), stats.ratingEvolution.map { it.delta })
        assertEquals(listOf(1224, 1242, 1222, 1202), stats.ratingEvolution.map { it.after })
        assertEquals(0, stats.unratedAttempts)
    }

    @Test
    fun `empty samples remain unavailable rather than an observed zero`() {
        val stats = buildIpsumStatistics(profile, emptyList(), StatisticsFixtures.catalog)
        assertEquals(0, stats.overall.total)
        assertNull(stats.overall.acRate.percent)
        assertNull(stats.overall.withHint.percent)
        assertNull(stats.overall.withoutHint.percent)
        assertNull(stats.overall.averageTimeMillis)
        assertNull(stats.overall.averageErrorsBeforeAc)
        assertEquals(1202, stats.currentRating)
        assertTrue(stats.ratingEvolution.isEmpty())
        assertTrue(stats.mostPracticed.isEmpty())
        assertTrue(stats.highestFailure.isEmpty())
        assertEquals(0, stats.leastPracticed.single().attempts.total)
    }

    @Test
    fun `active and other owners do not contribute or reveal their tags`() {
        val active = attempt(5, IpsumStatus.ACTIVE, rating = 2500, tags = setOf("secret-active"))
        val other = finished.first().copy(id = 6, ownerHandle = "other",
            problem = finished.first().problem.copy(tags = setOf("other-tag")))
        val stats = buildIpsumStatistics(profile.copy(handle = " TOURIST "), finished + active + other)
        assertEquals(4, stats.overall.total)
        assertEquals(listOf("dp", "graphs", "math"), stats.byTag.map { it.tag })
        assertEquals(listOf(1200, 1400), stats.byRating.map { it.problemRating })
    }

    @Test
    fun `tags never multiply totals and attempts are counted by id rather than problem`() {
        val first = finished.first()
        val second = first.copy(id = 7)
        val stats = buildIpsumStatistics(profile, listOf(first, first, second))
        assertEquals(2, stats.overall.total)
        assertEquals(2, stats.byRating.single().attempts.total)
        assertEquals(2, stats.byTag.single { it.tag == "dp" }.attempts.total)
        assertEquals(2, stats.byTag.single { it.tag == "graphs" }.attempts.total)
        assertEquals(2, stats.ratingEvolution.size)
    }

    @Test
    fun `upsolve changes situation only and repeated aggregation is idempotent`() {
        val pending = finished.map { if (it.status == IpsumStatus.SOLVED_OUTSIDE)
            it.copy(status = IpsumStatus.PENDING, solvedOutsideAtEpochMillis = null) else it }
        val before = buildIpsumStatistics(profile, pending)
        val after = buildIpsumStatistics(profile, finished)
        assertEquals(before.overall.copy(pending = 1, solvedOutside = 1), after.overall)
        assertEquals(before.ratingEvolution, after.ratingEvolution)
        assertEquals(before.currentRating, after.currentRating)
        assertEquals(before.byRating.map { it.attempts.acRate }, after.byRating.map { it.attempts.acRate })
        assertEquals(before.byTag.map { it.attempts.acRate }, after.byTag.map { it.attempts.acRate })
        assertEquals(after, buildIpsumStatistics(profile, finished))
        assertEquals(IpsumStatus.PENDING, pending.last().status)
    }

    @Test
    fun `missing rating snapshots are not reconstructed and evolution uses closure chronology`() {
        val missing = finished[1].copy(ratingDelta = null, finalLoremRating = null)
        val stats = buildIpsumStatistics(profile, listOf(finished[3], missing, finished[2], finished[0]))
        assertEquals(4, stats.overall.total)
        assertEquals(1, stats.unratedAttempts)
        assertEquals(listOf(1L, 3L, 4L), stats.ratingEvolution.map { it.ipsumId })
        assertEquals(finished[2].initialLoremRating, stats.ratingEvolution[1].before)
        val sameTime = finished[0].copy(endedAtEpochMillis = finished[2].endedAtEpochMillis)
        assertEquals(listOf(1L, 3L), buildIpsumStatistics(profile, listOf(finished[2], sameTime))
            .ratingEvolution.map { it.ipsumId })
    }

    @Test
    fun `invalid and missing durations are excluded with their sample size preserved`() {
        val invalid = finished[1].copy(endedAtEpochMillis = -1)
        val missing = finished[2].copy(endedAtEpochMillis = null)
        val stats = buildIpsumStatistics(profile, listOf(finished[0], invalid, missing))
        assertEquals(3, stats.overall.total)
        assertEquals(1, stats.overall.timedAttempts)
        assertEquals(30 * 60_000.0, stats.overall.averageTimeMillis!!, 0.001)
        assertNull(buildIpsumStatistics(profile, listOf(invalid, missing)).overall.averageTimeMillis)
    }

    @Test
    fun `zero time and zero AC are real observations and error totals do not overflow Int`() {
        val failed = attempt(7, IpsumStatus.PENDING, minutes = 0)
        assertEquals(0.0, buildIpsumStatistics(profile, listOf(failed)).overall.acRate.percent!!, 0.001)
        assertEquals(0.0, buildIpsumStatistics(profile, listOf(failed)).overall.averageTimeMillis!!, 0.001)
        val errors = finished.take(2).map { it.copy(errorCount = Int.MAX_VALUE) }
        assertEquals(2L * Int.MAX_VALUE, buildIpsumStatistics(profile, errors).overall.errorsBeforeAc)
    }

    @Test
    fun `a hinted only history has no unhinted denominator and rankings break ties consistently`() {
        val a = attempt(1, IpsumStatus.COMPLETED, tags = setOf("graphs", "dp"))
            .copy(hintRevealedAtEpochMillis = 5_000)
        val b = attempt(2, IpsumStatus.PENDING, tags = setOf("graphs", "dp"))
            .copy(hintRevealedAtEpochMillis = 5_000)
        val stats = buildIpsumStatistics(profile, listOf(b, a))
        assertNull(stats.overall.withoutHint.percent)
        assertEquals(AcRate(1, 2), stats.overall.withHint)
        assertEquals(0.0, stats.overall.averageErrorsBeforeAc!!, 0.001)
        assertEquals(listOf("dp", "graphs"), stats.mostPracticed.map { it.tag })
        assertEquals(listOf("dp", "graphs"), stats.leastPracticed.map { it.tag })
        assertEquals(listOf("dp", "graphs"), stats.highestFailure.map { it.tag })
    }
}
