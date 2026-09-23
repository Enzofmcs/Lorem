package dev.lorem.app.domain

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.ProblemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendIpsumTest {
    @Test
    fun `controlled category draw applies all three specified rating ranges`() {
        val catalog = listOf(problem(1, 1000), problem(2, 1200), problem(3, 1400))

        val fluency = RecommendIpsum(sequenceRandom(0, 0)).recommend(1200, catalog, emptyList(), emptyList())!!
        val current = RecommendIpsum(sequenceRandom(1, 0)).recommend(1200, catalog, emptyList(), emptyList())!!
        val challenge = RecommendIpsum(sequenceRandom(2, 0)).recommend(1200, catalog, emptyList(), emptyList())!!

        assertEquals(IpsumCategory.FLUENCY, fluency.category)
        assertTrue(fluency.problem.rating in setOf(1000, 1100))
        assertEquals(IpsumCategory.CURRENT_LEVEL, current.category)
        assertEquals(1200, current.problem.rating)
        assertEquals(IpsumCategory.CHALLENGE, challenge.category)
        assertTrue(challenge.problem.rating in setOf(1300, 1400))
    }

    @Test
    fun `rating is rounded to nearest hundred with minimum 800`() {
        val recommendation = RecommendIpsum(sequenceRandom(1, 0))
            .recommend(749, listOf(problem(1, 800)), emptyList(), emptyList())!!

        assertEquals(800, recommendation.normalizedLoremRating)
        assertEquals(800, recommendation.desiredRatingMin)
    }

    @Test
    fun `attempted solved used pending and active problems are excluded`() {
        val catalog = (1L..6L).map { problem(it, 1200) } + CodeforcesProblem(ProblemId(7, "A"), "Unrated", null, setOf("math"))
        val history = listOf(
            ProblemHistory(catalog[0].id, attempted = true, hasAcceptedSubmission = false),
            ProblemHistory(catalog[1].id, attempted = true, hasAcceptedSubmission = true),
        )
        val ipsums = listOf(
            ipsum(catalog[2], IpsumStatus.COMPLETED),
            ipsum(catalog[3], IpsumStatus.PENDING),
            ipsum(catalog[4], IpsumStatus.ACTIVE),
        )

        val selected = RecommendIpsum(sequenceRandom(1, 0)).recommend(1200, catalog, history, ipsums)!!

        assertEquals(catalog[5].id, selected.problem.id)
    }

    @Test
    fun `never practiced tags win then draw is limited to equally best candidates`() {
        val practiced = problem(1, 1100, setOf("math"))
        val frequent = problem(2, 1200, setOf("math"))
        val newA = problem(3, 1200, setOf("graphs"))
        val newB = problem(4, 1200, setOf("dp"))
        val history = listOf(ProblemHistory(practiced.id, true, true))

        val selected = RecommendIpsum(sequenceRandom(1, 1))
            .recommend(1200, listOf(practiced, frequent, newA, newB), history, emptyList())!!

        assertEquals(newB.id, selected.problem.id)
    }

    @Test
    fun `fallback uses nearest layer up to 300 and records its distance`() {
        val selected = RecommendIpsum(sequenceRandom(1, 0))
            .recommend(1249, listOf(problem(1, 1400), problem(2, 1500)), emptyList(), emptyList())!!

        assertEquals(1400, selected.problem.rating)
        assertEquals(200, selected.fallbackDistance)
        assertEquals(1200, selected.desiredRatingMin)
        assertEquals(1200, selected.desiredRatingMax)
    }

    @Test
    fun `no candidate outside fallback limit returns null`() {
        assertNull(
            RecommendIpsum(sequenceRandom(1)).recommend(
                1200,
                listOf(problem(1, 1600)),
                emptyList(),
                emptyList(),
            ),
        )
    }

    private fun problem(id: Long, rating: Int, tags: Set<String> = setOf("implementation")) =
        CodeforcesProblem(ProblemId(id, "A"), "Problem $id", rating, tags)

    private fun ipsum(problem: CodeforcesProblem, status: IpsumStatus) = Ipsum(
        id = problem.id.contestId,
        ownerHandle = "tourist",
        problem = problem,
        initialLoremRating = 1200,
        category = IpsumCategory.CURRENT_LEVEL,
        desiredRatingMin = 1200,
        desiredRatingMax = 1200,
        selectedRating = requireNotNull(problem.rating),
        fallbackDistance = 0,
        startedAtEpochMillis = 1,
        status = status,
    )

    private fun sequenceRandom(vararg values: Int): RandomSource {
        var index = 0
        return RandomSource { bound -> values[index++].mod(bound) }
    }
}
