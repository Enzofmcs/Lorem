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
    data class Success(val submissions: List<CodeforcesSubmission>) : SubmissionHistoryResult
    data class Failed(val message: String) : SubmissionHistoryResult
    data object RateLimited : SubmissionHistoryResult
    data object NetworkFailure : SubmissionHistoryResult
    data class HttpFailure(val statusCode: Int) : SubmissionHistoryResult
    data object InvalidResponse : SubmissionHistoryResult
}

interface CodeforcesRepository {
    suspend fun user(handle: String): UserLookupResult

    suspend fun submissionHistory(handle: String): SubmissionHistoryResult

    suspend fun problems(): List<CodeforcesProblem>
}
