package dev.lorem.app.domain

import dev.lorem.app.domain.sync.ProfileLinkChanged
import dev.lorem.app.domain.sync.ProfileLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    suspend operator fun invoke(expectedLink: ProfileLink? = null, expectedIpsumId: Long? = null): VerifyIpsumResult {
        val link = expectedLink ?: loremRepository.profileLink.first() ?: return VerifyIpsumResult.NoActiveIpsum
        if (loremRepository.profileLink.first() != link) throw ProfileLinkChanged()
        val ipsum = loremRepository.activeIpsum.first() ?: return VerifyIpsumResult.NoActiveIpsum
        if (expectedIpsumId != null && ipsum.id != expectedIpsumId) return VerifyIpsumResult.NoActiveIpsum
        if (ProfileLink.of(ipsum.ownerHandle, link.version) != link) return VerifyIpsumResult.NoActiveIpsum
        val remote = when (val result = codeforcesRepository.submissionHistorySince(ipsum.ownerHandle, ipsum.startedAtEpochMillis - 1)) {
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
            // Once the response is known, finish the short local transaction even if its
            // completion removes the active screen and cancels the polling consumer.
            val update = withContext(NonCancellable) {
                loremRepository.withProfileLink(link) { loremRepository.recordIpsumSubmissions(ipsum.id, definitive) }
            }
            VerifyIpsumResult.Success(update.insertedCount, update.errorCount, update.completed)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            VerifyIpsumResult.PersistenceFailure
        }
    }
}
