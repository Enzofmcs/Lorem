package dev.lorem.app.domain

import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

sealed interface ProblemHistorySyncResult {
    data class Success(val problemCount: Int) : ProblemHistorySyncResult
    data object NoActiveProfile : ProblemHistorySyncResult
    data class Failed(val message: String) : ProblemHistorySyncResult
    data object RateLimited : ProblemHistorySyncResult
    data object NetworkFailure : ProblemHistorySyncResult
    data class HttpFailure(val statusCode: Int) : ProblemHistorySyncResult
    data object InvalidResponse : ProblemHistorySyncResult
    data object PersistenceFailure : ProblemHistorySyncResult
}

class SynchronizeProblemHistory(
    private val loremRepository: LoremRepository,
    private val codeforcesRepository: CodeforcesRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(): ProblemHistorySyncResult {
        val profile = loremRepository.profile.first() ?: return ProblemHistorySyncResult.NoActiveProfile
        return when (val result = codeforcesRepository.submissionHistory(profile.handle)) {
            is SubmissionHistoryResult.Success -> {
                val history = buildProblemHistory(result.submissions)
                try {
                    loremRepository.saveProblemHistory(profile.handle, history)
                    loremRepository.saveProfile(profile.copy(lastSyncEpochMillis = nowMillis()))
                    ProblemHistorySyncResult.Success(history.size)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    ProblemHistorySyncResult.PersistenceFailure
                }
            }

            is SubmissionHistoryResult.Failed -> ProblemHistorySyncResult.Failed(result.message)
            SubmissionHistoryResult.RateLimited -> ProblemHistorySyncResult.RateLimited
            SubmissionHistoryResult.NetworkFailure -> ProblemHistorySyncResult.NetworkFailure
            is SubmissionHistoryResult.HttpFailure -> ProblemHistorySyncResult.HttpFailure(result.statusCode)
            SubmissionHistoryResult.InvalidResponse -> ProblemHistorySyncResult.InvalidResponse
        }
    }
}
