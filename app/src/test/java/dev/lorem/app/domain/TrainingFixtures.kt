package dev.lorem.app.domain

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemId

fun trainingProfile(rating: Int = 1200, consolidated: Int? = null, handle: String = "tourist") =
    LocalProfile(handle, handle, rating, rating, consolidated, 1L)

fun trainingIpsum(
    id: Long,
    status: IpsumStatus = IpsumStatus.COMPLETED,
    rating: Int = 1200,
    tags: Set<String> = setOf("dp"),
    hint: Boolean = false,
    minutes: Long = 30,
    owner: String = "tourist",
): Ipsum = Ipsum(
    id = id, ownerHandle = owner,
    problem = CodeforcesProblem(ProblemId(id, "A"), "Problem $id", rating, tags),
    initialLoremRating = 1200, category = IpsumCategory.CURRENT_LEVEL,
    desiredRatingMin = rating, desiredRatingMax = rating, selectedRating = rating,
    fallbackDistance = 0, startedAtEpochMillis = id * 10_000_000L - minutes * 60_000,
    endedAtEpochMillis = (id * 10_000_000L).takeIf { status != IpsumStatus.ACTIVE },
    hintRevealedAtEpochMillis = (id * 10_000_000L - 1).takeIf { hint },
    status = status, ratingDelta = 24.takeIf { status != IpsumStatus.ACTIVE },
    finalLoremRating = 1224.takeIf { status != IpsumStatus.ACTIVE },
    solvedOutsideAtEpochMillis = (id * 10_000_000L + 1).takeIf { status == IpsumStatus.SOLVED_OUTSIDE },
)

fun qualifiedBand(rating: Int = 1200, firstId: Long = 1L): List<Ipsum> = (0..7).map { index ->
    trainingIpsum(firstId + index, if (index < 6) IpsumStatus.COMPLETED else IpsumStatus.PENDING,
        rating, setOf(listOf("dp", "math", "greedy")[index % 3]), hint = index == 5)
}
