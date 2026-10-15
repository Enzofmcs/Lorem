package dev.lorem.app.domain

import dev.lorem.app.domain.sync.ProfileLinkChanged
import dev.lorem.app.domain.sync.ProfileLink
import kotlinx.coroutines.CancellationException
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import kotlinx.coroutines.flow.first

sealed interface UpdatePendingIpsumsResult {
    data class Success(val movedCount: Int) : UpdatePendingIpsumsResult
    data object NoActiveProfile : UpdatePendingIpsumsResult
    data class Failed(val message: String) : UpdatePendingIpsumsResult
    data object RateLimited : UpdatePendingIpsumsResult
    data object NetworkFailure : UpdatePendingIpsumsResult
    data class HttpFailure(val statusCode: Int) : UpdatePendingIpsumsResult
    data object InvalidResponse : UpdatePendingIpsumsResult
    data object PersistenceFailure : UpdatePendingIpsumsResult
}

class UpdatePendingIpsums(
    private val loremRepository: LoremRepository,
    private val codeforcesRepository: CodeforcesRepository,
) {
    suspend operator fun invoke(expectedLink: ProfileLink? = null): UpdatePendingIpsumsResult {
        val link = expectedLink ?: loremRepository.profileLink.first() ?: return UpdatePendingIpsumsResult.NoActiveProfile
        if (loremRepository.profileLink.first() != link) throw ProfileLinkChanged()
        val profile = loremRepository.profile.first() ?: return UpdatePendingIpsumsResult.NoActiveProfile
        val pending = loremRepository.ipsums.first().filter {
            it.status == IpsumStatus.PENDING && it.endedAtEpochMillis != null &&
                it.ownerHandle.trim().equals(profile.handle.trim(), ignoreCase = true)
        }
        if (pending.isEmpty()) return UpdatePendingIpsumsResult.Success(0)
        val oldestEnd = pending.minOf { requireNotNull(it.endedAtEpochMillis) }
        val submissions = when (
            val result = codeforcesRepository.submissionHistorySince(profile.handle, oldestEnd)
        ) {
            is SubmissionHistoryResult.Success -> result.submissions
            is SubmissionHistoryResult.Failed -> return UpdatePendingIpsumsResult.Failed(result.message)
            SubmissionHistoryResult.RateLimited -> return UpdatePendingIpsumsResult.RateLimited
            SubmissionHistoryResult.NetworkFailure -> return UpdatePendingIpsumsResult.NetworkFailure
            is SubmissionHistoryResult.HttpFailure -> return UpdatePendingIpsumsResult.HttpFailure(result.statusCode)
            SubmissionHistoryResult.InvalidResponse -> return UpdatePendingIpsumsResult.InvalidResponse
        }
        val firstAcceptedByIpsum = pending.mapNotNull { ipsum ->
            val endedAt = requireNotNull(ipsum.endedAtEpochMillis)
            submissions.asSequence()
                .filter { it.problemId == ipsum.problem.id && it.verdict == "OK" }
                .map { it.createdAt.toEpochMilli() }
                .filter { it > endedAt }
                .minOrNull()
                ?.let { ipsum.id to it }
        }.toMap()
        return runCatching { loremRepository.withProfileLink(link) { loremRepository.markIpsumsSolvedOutside(firstAcceptedByIpsum) } }
            .fold(
                onSuccess = { UpdatePendingIpsumsResult.Success(it) },
                onFailure = { if (it is CancellationException) throw it
                    UpdatePendingIpsumsResult.PersistenceFailure },
            )
    }
}
