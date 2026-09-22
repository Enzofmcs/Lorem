package dev.lorem.app.data.fake

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.CodeforcesUser
import dev.lorem.app.domain.repository.UserLookupResult

/** Deterministic offline data for previews and tests. No network access is performed. */
class FakeCodeforcesRepository : CodeforcesRepository {
    override suspend fun user(handle: String): UserLookupResult = UserLookupResult.Success(
        CodeforcesUser(handle = handle, displayName = handle, rating = 1500),
    )

    override suspend fun problems(): List<CodeforcesProblem> = listOf(
        CodeforcesProblem(
            id = ProblemId(contestId = 4, index = "A"),
            name = "Watermelon",
            rating = 800,
            tags = setOf("brute force", "math"),
        ),
    )
}
