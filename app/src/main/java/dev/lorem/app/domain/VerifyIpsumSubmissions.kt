package dev.lorem.app.domain

import dev.lorem.app.domain.model.IpsumSubmission
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import kotlinx.coroutines.flow.first

sealed interface VerifyIpsumResult {
    data class Success(val newSubmissionCount: Int, val errorCount: Int, val completed: Boolean) : VerifyIpsumResult
    data object NoActiveIpsum : VerifyIpsumResult
    data class ApiFailure(val message: String) : VerifyIpsumResult
    data object RateLimited : VerifyIpsumResult
    data object NetworkFailure : VerifyIpsumResult
    data class HttpFailure(val statusCode: Int) : VerifyIpsumResult
    data object InvalidResponse : VerifyIpsumResult
    data object PersistenceFailure : VerifyIpsumResult
}

class VerifyIpsumSubmissions(
    private val loremRepository: LoremRepository,
    private val codeforcesRepository: CodeforcesRepository,
) {
    suspend operator fun invoke(): VerifyIpsumResult {
        val ipsum = loremRepository.activeIpsum.first() ?: return VerifyIpsumResult.NoActiveIpsum
        val remote = when (val result = codeforcesRepository.submissionHistory(ipsum.ownerHandle)) {
            is SubmissionHistoryResult.Success -> result.submissions
            is SubmissionHistoryResult.Failed -> return VerifyIpsumResult.ApiFailure(result.message)
            SubmissionHistoryResult.RateLimited -> return VerifyIpsumResult.RateLimited
            SubmissionHistoryResult.NetworkFailure -> return VerifyIpsumResult.NetworkFailure
            is SubmissionHistoryResult.HttpFailure -> return VerifyIpsumResult.HttpFailure(result.statusCode)
            SubmissionHistoryResult.InvalidResponse -> return VerifyIpsumResult.InvalidResponse
        }
        val definitive = remote.asSequence()
            .filter { it.problemId == ipsum.problem.id }
            .filter { it.createdAt.toEpochMilli() >= ipsum.startedAtEpochMillis }
            .filter { !it.verdict.isNullOrBlank() && it.verdict != "TESTING" }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
            .map { IpsumSubmission(it.id, ipsum.id, checkNotNull(it.verdict), it.createdAt.toEpochMilli()) }
            .toList()
        return try {
            val update = loremRepository.recordIpsumSubmissions(ipsum.id, definitive)
            VerifyIpsumResult.Success(update.insertedCount, update.errorCount, update.completed)
        } catch (_: Exception) {
            VerifyIpsumResult.PersistenceFailure
        }
    }
}
