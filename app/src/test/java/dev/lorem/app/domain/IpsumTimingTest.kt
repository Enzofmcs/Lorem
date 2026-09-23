package dev.lorem.app.domain

import dev.lorem.app.domain.model.elapsedIpsumMillis
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.model.problemUrl
import dev.lorem.app.domain.model.totalTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Test

class IpsumTimingTest {
    @Test fun `elapsed time derives from persisted start`() {
        assertEquals(10 * 60_000L, elapsedIpsumMillis(1_000L, 601_000L))
    }

    @Test fun `clock before persisted start never produces negative time`() {
        assertEquals(0L, elapsedIpsumMillis(2_000L, 1_000L))
    }

    @Test fun `problem link derives from stable identity`() {
        val ipsum = Ipsum(
            id = 1, ownerHandle = "tourist",
            problem = CodeforcesProblem(ProblemId(123, "B2"), "Problem", 800, emptySet()),
            initialLoremRating = 800, category = IpsumCategory.CURRENT_LEVEL,
            desiredRatingMin = 800, desiredRatingMax = 800, selectedRating = 800,
            fallbackDistance = 0, startedAtEpochMillis = 1, status = IpsumStatus.ACTIVE,
        )
        assertEquals("https://codeforces.com/contest/123/problem/B2", ipsum.problemUrl())
    }

    @Test fun `total time is absent instead of crashing when legacy result has no end time`() {
        val ipsum = Ipsum(
            id = 1, ownerHandle = "tourist",
            problem = CodeforcesProblem(ProblemId(123, "A"), "Problem", 800, emptySet()),
            initialLoremRating = 800, category = IpsumCategory.CURRENT_LEVEL,
            desiredRatingMin = 800, desiredRatingMax = 800, selectedRating = 800,
            fallbackDistance = 0, startedAtEpochMillis = 1_000,
            endedAtEpochMillis = null, status = IpsumStatus.COMPLETED,
        )

        assertEquals(null, ipsum.totalTimeMillis())
        assertEquals(2_000L, ipsum.copy(endedAtEpochMillis = 3_000).totalTimeMillis())
    }
}
