package dev.lorem.app

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemId

/** Stored snapshots for checking aggregation; the rating formula is tested separately. */
object StatisticsFixtures {
    val profile = LocalProfile("tourist", "Tourist", 1500, 1202, null, 1L)
    val catalog = listOf(CodeforcesProblem(ProblemId(999, "Z"), "Untried", 1600, setOf("greedy")))
    val finished = listOf(
        attempt(1, IpsumStatus.COMPLETED, 30, 1200, setOf("dp", "graphs"))
            .copy(errorCount = 1, initialLoremRating = 1200, ratingDelta = 24, finalLoremRating = 1224),
        attempt(2, IpsumStatus.COMPLETED, 45, 1200, setOf("graphs"))
            .copy(errorCount = 2, hintRevealedAtEpochMillis = 2_000,
                initialLoremRating = 1224, ratingDelta = 18, finalLoremRating = 1242),
        attempt(3, IpsumStatus.PENDING, 60, 1400, setOf("dp"))
            .copy(errorCount = 8, initialLoremRating = 1242, ratingDelta = -20, finalLoremRating = 1222),
        attempt(4, IpsumStatus.SOLVED_OUTSIDE, 25, 1400, setOf("dp", "math"))
            .copy(errorCount = 9, solvedOutsideAtEpochMillis = 20_000_000,
                initialLoremRating = 1222, ratingDelta = -20, finalLoremRating = 1202),
    )

    fun attempt(
        id: Long,
        status: IpsumStatus,
        minutes: Int = 10,
        rating: Int = 1200,
        tags: Set<String> = setOf("implementation"),
    ) = Ipsum(
        id = id,
        ownerHandle = "tourist",
        problem = CodeforcesProblem(ProblemId(100, id.toString()), "Problem $id", rating, tags),
        initialLoremRating = 1200,
        category = IpsumCategory.CURRENT_LEVEL,
        desiredRatingMin = rating,
        desiredRatingMax = rating,
        selectedRating = rating,
        fallbackDistance = 0,
        startedAtEpochMillis = id * 3_600_000,
        endedAtEpochMillis = if (status == IpsumStatus.ACTIVE) null else id * 3_600_000 + minutes * 60_000L,
        status = status,
    )
}
