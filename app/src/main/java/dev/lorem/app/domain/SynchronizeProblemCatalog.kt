package dev.lorem.app.domain

import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.ProblemCatalogResult
import kotlinx.coroutines.flow.first

sealed interface ProblemCatalogSyncResult {
    data class Success(val eligibleProblemCount: Int, val updatedAtEpochMillis: Long) : ProblemCatalogSyncResult
    data class Failed(val message: String, val savedProblemCount: Int) : ProblemCatalogSyncResult
    data class RateLimited(val savedProblemCount: Int) : ProblemCatalogSyncResult
    data class NetworkFailure(val savedProblemCount: Int) : ProblemCatalogSyncResult
    data class HttpFailure(val statusCode: Int, val savedProblemCount: Int) : ProblemCatalogSyncResult
    data class InvalidResponse(val savedProblemCount: Int) : ProblemCatalogSyncResult
    data class PersistenceFailure(val savedProblemCount: Int) : ProblemCatalogSyncResult
}

class SynchronizeProblemCatalog(
    private val loremRepository: LoremRepository,
    private val codeforcesRepository: CodeforcesRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(): ProblemCatalogSyncResult {
        val savedCount = loremRepository.problemCatalog.first().size
        return when (val result = codeforcesRepository.problems()) {
            is ProblemCatalogResult.Success -> {
                val eligible = result.problems.filter { it.rating != null }.distinctBy { it.id }
                val updatedAt = nowMillis()
                try {
                    loremRepository.replaceProblemCatalog(eligible, updatedAt)
                    ProblemCatalogSyncResult.Success(eligible.size, updatedAt)
                } catch (_: Exception) {
                    ProblemCatalogSyncResult.PersistenceFailure(savedCount)
                }
            }
            is ProblemCatalogResult.Failed -> ProblemCatalogSyncResult.Failed(result.message, savedCount)
            ProblemCatalogResult.RateLimited -> ProblemCatalogSyncResult.RateLimited(savedCount)
            ProblemCatalogResult.NetworkFailure -> ProblemCatalogSyncResult.NetworkFailure(savedCount)
            is ProblemCatalogResult.HttpFailure -> ProblemCatalogSyncResult.HttpFailure(result.statusCode, savedCount)
            ProblemCatalogResult.InvalidResponse -> ProblemCatalogSyncResult.InvalidResponse(savedCount)
        }
    }
}
