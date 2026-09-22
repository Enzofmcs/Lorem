package dev.lorem.app.domain.repository

import dev.lorem.app.domain.model.CodeforcesProblem

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

interface CodeforcesRepository {
    suspend fun user(handle: String): UserLookupResult

    suspend fun problems(): List<CodeforcesProblem>
}
