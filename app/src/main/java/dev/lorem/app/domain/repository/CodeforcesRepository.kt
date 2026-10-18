package dev.lorem.app.domain.repository

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.CodeforcesSubmission

data class CodeforcesUser(
    val handle: String,
    val displayName: String,
    val rating: Int?,
)

sealed interface UserLookupResult {
    data class Success(val user: CodeforcesUser) : UserLookupResult
    data object UserNotFound : UserLookupResult
    data class ApiFailure(val message: String) : UserLookupResult
    data object RateLimited : UserLookupResult
    data object NetworkFailure : UserLookupResult
}

sealed interface SubmissionHistoryResult {
    data class Success(
        val submissions: List<CodeforcesSubmission>,
        val receivedCount: Int = submissions.size,
    ) : SubmissionHistoryResult
    data class Failed(val message: String) : SubmissionHistoryResult
    data object RateLimited : SubmissionHistoryResult
    data object NetworkFailure : SubmissionHistoryResult
    data class HttpFailure(val statusCode: Int) : SubmissionHistoryResult
    data object InvalidResponse : SubmissionHistoryResult
}

sealed interface ProblemCatalogResult {
    data class Success(val problems: List<CodeforcesProblem>) : ProblemCatalogResult
    data class Failed(val message: String) : ProblemCatalogResult
    data object RateLimited : ProblemCatalogResult
    data object NetworkFailure : ProblemCatalogResult
    data class HttpFailure(val statusCode: Int) : ProblemCatalogResult
    data object InvalidResponse : ProblemCatalogResult
}

interface CodeforcesRepository {
    suspend fun user(handle: String): UserLookupResult

    suspend fun submissionHistory(handle: String): SubmissionHistoryResult

    /** One request/page. receivedCount includes entries without a usable problem identity. */
    suspend fun submissionPage(handle: String, from: Int, count: Int): SubmissionHistoryResult =
        when (val result = submissionHistory(handle)) {
            is SubmissionHistoryResult.Success -> SubmissionHistoryResult.Success(result.submissions.drop(from - 1).take(count))
            else -> result
        }

    /** Returns submissions newer than [sinceEpochMillis], stopping pagination at that boundary. */
    suspend fun submissionHistorySince(handle: String, sinceEpochMillis: Long): SubmissionHistoryResult =
        submissionHistory(handle)

    /** Complete catalog with contest.list evidence; missing/ambiguous origins remain unverified. */
    suspend fun problems(): ProblemCatalogResult
}
