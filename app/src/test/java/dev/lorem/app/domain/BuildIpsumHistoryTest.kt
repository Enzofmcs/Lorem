package dev.lorem.app.domain

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BuildIpsumHistoryTest {
    @Test
    fun `classifies finished Ipsums once and ignores active attempts`() {
        val solved = ipsum(1, ProblemId(100, "A"), IpsumStatus.COMPLETED)
        val pending = ipsum(2, ProblemId(200, "B"), IpsumStatus.PENDING)
        val upsolved = ipsum(3, ProblemId(300, "C"), IpsumStatus.PENDING)
        val active = ipsum(4, ProblemId(400, "D"), IpsumStatus.ACTIVE)

        val result = buildIpsumHistory(listOf(solved, pending, upsolved, active), setOf(upsolved.problem.id))

        assertEquals(3, result.size)
        assertEquals(IpsumHistorySituation.SOLVED_IN_IPSUM, result.single { it.ipsum.id == 1L }.situation)
        assertEquals(IpsumHistorySituation.PENDING, result.single { it.ipsum.id == 2L }.situation)
        assertEquals(IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM, result.single { it.ipsum.id == 3L }.situation)
        assertEquals(result.size, result.map { it.ipsum.problem.id }.distinct().size)
    }

    @Test
    fun `upsolve classification preserves the original Ipsum data`() {
        val original = ipsum(7, ProblemId(777, "F"), IpsumStatus.PENDING).copy(
            hintRevealedAtEpochMillis = 2_000,
            endedAtEpochMillis = 65_000,
            errorCount = 4,
            failureReason = IpsumFailureReason.IMPLEMENTATION_ERROR,
            ratingDelta = -17,
            finalLoremRating = 1183,
        )

        val item = buildIpsumHistory(listOf(original), setOf(original.problem.id)).single()

        assertSame(original, item.ipsum)
        assertEquals(IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM, item.situation)
        assertEquals(-17, item.ipsum.ratingDelta)
        assertEquals(65_000L, item.ipsum.endedAtEpochMillis)
        assertEquals(4, item.ipsum.errorCount)
    }

    @Test
    fun `combines situation rating and tag filters without changing source`() {
        val matching = ipsum(1, ProblemId(1, "A"), IpsumStatus.COMPLETED, 1400, setOf("dp", "graphs"))
        val wrongRating = ipsum(2, ProblemId(2, "B"), IpsumStatus.COMPLETED, 1500, setOf("dp"))
        val wrongTag = ipsum(3, ProblemId(3, "C"), IpsumStatus.COMPLETED, 1400, setOf("math"))
        val wrongSituation = ipsum(4, ProblemId(4, "D"), IpsumStatus.PENDING, 1400, setOf("dp"))
        val source = buildIpsumHistory(listOf(matching, wrongRating, wrongTag, wrongSituation))

        val result = filterIpsumHistory(
            source,
            IpsumHistorySituation.SOLVED_IN_IPSUM,
            IpsumHistoryFilters(rating = 1400, tag = "dp"),
        )

        assertEquals(listOf(matching), result.map { it.ipsum })
        assertEquals(4, source.size)
        assertEquals(emptyList<IpsumHistoryItem>(), filterIpsumHistory(
            source, IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM, IpsumHistoryFilters(),
        ))
    }

    private fun ipsum(
        id: Long,
        problemId: ProblemId,
        status: IpsumStatus,
        rating: Int = 1200,
        tags: Set<String> = setOf("implementation"),
    ) = Ipsum(
        id = id,
        ownerHandle = "tourist",
        problem = CodeforcesProblem(problemId, "Problem $id", rating, tags),
        initialLoremRating = 1200,
        category = IpsumCategory.CURRENT_LEVEL,
        desiredRatingMin = rating,
        desiredRatingMax = rating,
        selectedRating = rating,
        fallbackDistance = 0,
        startedAtEpochMillis = id * 1_000,
        endedAtEpochMillis = if (status == IpsumStatus.ACTIVE) null else id * 1_000 + 60_000,
        status = status,
    )
}
