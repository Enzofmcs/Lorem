package dev.lorem.app.data.fake

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.CodeforcesSubmission
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.CodeforcesUser
import dev.lorem.app.domain.repository.ProblemCatalogResult
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.UserLookupResult
import java.time.Instant

/** Deterministic offline data for previews and tests. No network access is performed. */
class FakeCodeforcesRepository : CodeforcesRepository {
    override suspend fun user(handle: String): UserLookupResult = UserLookupResult.Success(
        CodeforcesUser(handle = handle, displayName = handle, rating = 1500),
    )

    override suspend fun submissionHistory(handle: String): SubmissionHistoryResult =
        SubmissionHistoryResult.Success(
            listOf(
                CodeforcesSubmission(
                    id = 1L,
                    problemId = ProblemId(contestId = 4, index = "A"),
                    verdict = "WRONG_ANSWER",
                    createdAt = Instant.ofEpochSecond(1_700_000_000L),
                ),
                CodeforcesSubmission(
                    id = 2L,
                    problemId = ProblemId(contestId = 4, index = "A"),
                    verdict = "OK",
                    createdAt = Instant.ofEpochSecond(1_700_000_120L),
                ),
            ),
        )

    override suspend fun problems(): ProblemCatalogResult = ProblemCatalogResult.Success(listOf(
        CodeforcesProblem(
            id = ProblemId(contestId = 4, index = "A"),
            name = "Watermelon",
            rating = 800,
            tags = setOf("brute force", "math"),
        ),
    ))
}
