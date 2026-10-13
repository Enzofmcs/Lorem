package dev.lorem.app.domain

import dev.lorem.app.domain.model.IpsumStatus
import org.junit.Assert.*
import org.junit.Test

class CalculateConsolidatedRatingTest {
    @Test fun `eight attempts six AC five autonomous and three styles qualify separately from estimated rating`() {
        val result = calculateConsolidatedRating(qualifiedBand(1400), ownerHandle = "tourist", estimatedRating = 1518)
        assertEquals(1400, result.consolidatedRating)
        assertEquals(1500, result.nextBand.rating)
        assertEquals(0, result.nextBand.attempts)
    }

    @Test fun `every threshold is necessary and a single multitag AC cannot consolidate`() {
        val qualified = qualifiedBand()
        assertNull(calculateConsolidatedRating(qualified.dropLast(1)).consolidatedRating)
        assertNull(calculateConsolidatedRating(qualified.mapIndexed { index, it ->
            if (index == 0) it.copy(status = IpsumStatus.PENDING) else it
        }).consolidatedRating)
        assertNull(calculateConsolidatedRating(qualified.mapIndexed { index, it ->
            if (index == 0) it.copy(hintRevealedAtEpochMillis = it.startedAtEpochMillis) else it
        }).consolidatedRating)
        assertNull(calculateConsolidatedRating(qualified.map { it.copy(problem = it.problem.copy(tags = setOf("dp", "math", "greedy"))) }).consolidatedRating)
        assertNull(calculateConsolidatedRating(listOf(trainingIpsum(1, rating = 2000, tags = setOf("dp", "math", "greedy")))).consolidatedRating)
    }

    @Test fun `70 percent qualifies and below it does not`() {
        val values = (1L..10L).map { id -> trainingIpsum(id,
            if (id > 3) IpsumStatus.COMPLETED else IpsumStatus.PENDING,
            tags = setOf(listOf("dp", "math", "greedy")[id.toInt() % 3])) }
        assertEquals(1200, calculateConsolidatedRating(values).consolidatedRating)
        val tooManyFailures = values.map { if (it.id == 10L) it.copy(status = IpsumStatus.PENDING) else it }
        assertNull(calculateConsolidatedRating(tooManyFailures).consolidatedRating)
    }

    @Test fun `previous milestone survives failure and historical windows recover missed persistence`() {
        val failures = (9L..35L).map { trainingIpsum(it, IpsumStatus.PENDING) }
        assertEquals(1400, calculateConsolidatedRating(failures, 1400).consolidatedRating)
        assertEquals(1200, calculateConsolidatedRating(qualifiedBand() + failures).consolidatedRating)
        assertEquals(1500, calculateConsolidatedRating(qualifiedBand(), 1500).consolidatedRating)
    }

    @Test fun `owner active duplicates tags without evidence and upsolves cannot create qualification`() {
        val values = qualifiedBand().map { if (it.id == 1L) it.copy(status = IpsumStatus.SOLVED_OUTSIDE) else it }
        assertNull(calculateConsolidatedRating(values + trainingIpsum(20, IpsumStatus.ACTIVE)).consolidatedRating)
        assertNull(calculateConsolidatedRating(qualifiedBand(), ownerHandle = "other").consolidatedRating)
        assertNull(calculateConsolidatedRating(qualifiedBand().map { it.copy(problem = it.problem.copy(tags = emptySet())) }).consolidatedRating)
        assertEquals(1200, calculateConsolidatedRating(qualifiedBand().reversed() + qualifiedBand()).consolidatedRating)
        assertEquals(1200, calculateConsolidatedRating(emptyList(), estimatedRating = 1250).nextBand.rating)
    }
}
