package dev.lorem.app.domain

import dev.lorem.app.withVerifiedTestOrigin
import dev.lorem.app.StatisticsFixtures
import dev.lorem.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class RecommendVarietyTest {
    private fun candidate(id: Long, tag: String? = "dp", rating: Int = 1200) =
        CodeforcesProblem(ProblemId(id, "A"), "Candidate $id", rating, tag?.let { setOf(it) }.orEmpty()).withVerifiedTestOrigin()
    private fun attempt(id: Long, tag: String, status: IpsumStatus = IpsumStatus.COMPLETED, hint: Boolean = false) =
        StatisticsFixtures.attempt(id, status, tags = setOf(tag))
            .copy(hintRevealedAtEpochMillis = 1L.takeIf { hint })
    private fun recommend(catalog: List<CodeforcesProblem>, ipsums: List<Ipsum>, history: List<ProblemHistory> = emptyList(), draw: Int = 0): IpsumRecommendation? {
        var calls = 0
        return RecommendIpsum(RandomSource { bound -> (if (calls++ == 0) 1 else draw).mod(bound) })
            .recommend(1200, catalog, history, ipsums, "tourist")
    }

    @Test fun `failure then hint use have priority among equally exposed tags`() {
        val catalog = listOf(candidate(100, "math"), candidate(101))
        val failure = recommend(catalog, listOf(attempt(1, "math", IpsumStatus.PENDING), attempt(2, "dp")))!!
        assertEquals("math", failure.problem.tags.single())
        assertEquals(1.0, failure.factors.averageFailure, 0.0)
        val hint = recommend(catalog, listOf(attempt(1, "math"), attempt(2, "dp", hint = true)))!!
        assertEquals("dp", hint.problem.tags.single())
        assertEquals(1.0, hint.factors.averageHintUse, 0.0)
    }

    @Test fun `snapshot and remote history count the same problem once even outside catalog`() {
        val practiced = attempt(1, "dp")
        val history = listOf(ProblemHistory(practiced.problem.id, true, true), ProblemHistory(practiced.problem.id, true, true))
        val selected = recommend(listOf(candidate(100)), listOf(practiced, practiced), history)!!
        assertEquals(1, selected.factors.tags.single().exposure)
        assertEquals(1, selected.factors.tags.single().attempts)
    }

    @Test fun `new tags and lower exposure precede failure while upsolve remains failure`() {
        val practiced = listOf(attempt(1, "dp", IpsumStatus.SOLVED_OUTSIDE), attempt(2, "dp"), attempt(3, "math"))
        assertEquals("graphs", recommend(listOf(candidate(100), candidate(101, "graphs")), practiced)!!.problem.tags.single())
        assertEquals("math", recommend(listOf(candidate(100), candidate(101, "math")), practiced)!!.problem.tags.single())
        val upsolve = recommend(listOf(candidate(100), candidate(101, "math")), listOf(practiced.first(), practiced.last()))!!
        assertEquals("dp", upsolve.problem.tags.single())
    }

    @Test fun `recency breaks remaining ties and controlled draws reach each equal candidate`() {
        val practiced = listOf(attempt(1, "dp"), attempt(2, "math"))
        assertEquals("dp", recommend(listOf(candidate(100), candidate(101, "math")), practiced)!!.problem.tags.single())
        val alternatives = listOf(candidate(100, "graphs"), candidate(101, "greedy"))
        assertEquals(alternatives[0], recommend(alternatives, practiced, draw = 0)!!.problem)
        assertEquals(alternatives[1], recommend(alternatives, practiced, draw = 1)!!.problem)
    }

    @Test fun `tags never bypass prior attempts or rating fallback and tagless candidate has no artificial advantage`() {
        val attempted = candidate(100, "graphs")
        val candidates = listOf(attempted, candidate(101), candidate(102, "graphs", 1600), candidate(103, null))
        val selected = recommend(candidates, listOf(attempt(1, "dp")), listOf(ProblemHistory(attempted.id, true, false)))!!
        assertEquals(101L, selected.problem.id.contestId)
        assertEquals(0, selected.fallbackDistance)
        assertNull(recommend(listOf(candidate(102, "graphs", 1600)), emptyList()))
        assertEquals(103L, recommend(listOf(candidate(103, null)), emptyList())!!.problem.id.contestId)
    }

    @Test fun `another profile cannot supply weakness exposure or exclusion`() {
        val other = attempt(1, "dp", IpsumStatus.PENDING).copy(ownerHandle = "other")
        val eligible = other.problem.copy(rating = 1200).withVerifiedTestOrigin()
        val selected = recommend(listOf(eligible), listOf(other))!!
        assertEquals(eligible.id, selected.problem.id)
        assertEquals(0, selected.factors.tags.single().exposure)
        assertNull(selected.factors.tags.single().failureRate)
    }
}
