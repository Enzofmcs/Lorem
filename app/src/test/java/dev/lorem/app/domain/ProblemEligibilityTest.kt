package dev.lorem.app.domain

import dev.lorem.app.withVerifiedTestOrigin
import dev.lorem.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ProblemEligibilityTest {
    private val cutoff = VerifiedProblemOrigin.CUTOFF_EPOCH_MILLIS
    private fun problem(id: Long = 1, rating: Int = 1200) =
        CodeforcesProblem(ProblemId(id, "A"), "Candidate", rating, setOf("dp")).withVerifiedTestOrigin()
    private fun select(catalog: List<CodeforcesProblem>, history: List<ProblemHistory> = emptyList(), ipsums: List<Ipsum> = emptyList()): IpsumRecommendation? {
        var calls = 0
        return RecommendIpsum(RandomSource { if (calls++ == 0) 1 else 0 }).recommend(1200, catalog, history, ipsums)
    }

    @Test fun `UTC cutoff is inclusive and identity does not imply a date`() {
        assertTrue(problem(1).withVerifiedTestOrigin(cutoff).isEligibleForNewRecommendation())
        assertFalse(problem(999999).withVerifiedTestOrigin(cutoff - 1).isEligibleForNewRecommendation())
        assertFalse(problem().copy(origin = null).isEligibleForNewRecommendation())
        assertEquals(1L, select(listOf(problem(999999).withVerifiedTestOrigin(cutoff-1), problem(1)))!!.problem.id.contestId)
    }

    @Test fun `missing mismatched unverifiable and future rule metadata is ineligible`() {
        val candidate = problem()
        val origin = candidate.origin!!
        for (invalid in listOf(origin.copy(contestId = 2), origin.copy(source = "name"),
            origin.copy(eligibilityVersion = "unknown"), origin.copy(verifiedAtEpochMillis = 0))) {
            assertNull(select(listOf(candidate.copy(origin = invalid))))
        }
    }

    @Test fun `fallback never relaxes cutoff and absence remains recoverable`() {
        assertNull(select(listOf(problem(1, 1200).copy(origin = null), problem(2, 1300).withVerifiedTestOrigin(cutoff-1))))
        val eligible = problem(3, 1400)
        assertEquals(eligible.id, select(listOf(problem(1).copy(origin = null), eligible))!!.problem.id)
        assertNull(select(listOf(eligible), listOf(ProblemHistory(eligible.id, true, false))))
    }

    @Test fun `old attempts and active snapshots remain excluded and unchanged`() {
        val old = problem(1).withVerifiedTestOrigin(cutoff-1)
        val active = Ipsum(7, "tourist", old, 1200, IpsumCategory.CURRENT_LEVEL, 1200, 1200, 1200, 0, 1234,
            status = IpsumStatus.ACTIVE)
        assertEquals(2L, select(listOf(problem(1), problem(2)), ipsums = listOf(active))!!.problem.id.contestId)
        assertEquals(old, active.problem)
        assertEquals(1234L, active.startedAtEpochMillis)
    }
}
