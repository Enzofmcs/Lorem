package dev.lorem.app.domain

import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.repository.ActiveIpsumAlreadyExistsException
import dev.lorem.app.domain.repository.LoremRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.domain.sync.ProfileLinkChanged

sealed interface StartIpsumResult {
    data class Success(val ipsum: Ipsum) : StartIpsumResult
    data object AlreadyActive : StartIpsumResult
    data object NoCandidate : StartIpsumResult
    data object NoProfile : StartIpsumResult
    data object NeedsHistorySync : StartIpsumResult
    data object PersistenceFailure : StartIpsumResult
}

class StartIpsum(
    private val repository: LoremRepository,
    private val recommender: RecommendIpsum,
    private val nowMillis: () -> Long,
) {
    suspend operator fun invoke(expectedLink: ProfileLink? = null): StartIpsumResult {
        val link = expectedLink ?: repository.profileLink.first() ?: return StartIpsumResult.NoProfile
        if (repository.profileLink.first() != link) throw ProfileLinkChanged()
        val profile = repository.profile.first() ?: return StartIpsumResult.NoProfile
        if (repository.activeIpsum.first() != null) return StartIpsumResult.AlreadyActive
        if (profile.lastSyncEpochMillis <= 0L) return StartIpsumResult.NeedsHistorySync
        val recommendation = recommender.recommend(
            loremRating = profile.loremRating,
            catalog = repository.problemCatalog.first(),
            history = repository.problemHistory.first(),
            ipsums = repository.ipsums.first(),
            ownerHandle = profile.handle,
        ) ?: return StartIpsumResult.NoCandidate
        val ipsum = Ipsum(
            id = 0,
            ownerHandle = profile.handle,
            problem = recommendation.problem,
            initialLoremRating = profile.loremRating,
            category = recommendation.category,
            desiredRatingMin = recommendation.desiredRatingMin,
            desiredRatingMax = recommendation.desiredRatingMax,
            selectedRating = requireNotNull(recommendation.problem.rating),
            fallbackDistance = recommendation.fallbackDistance,
            startedAtEpochMillis = nowMillis(),
            status = IpsumStatus.ACTIVE,
            recommendationFactors = recommendation.factors,
        )
        return try {
            StartIpsumResult.Success(repository.createActiveIpsumForLink(ipsum, link))
        } catch (_: ActiveIpsumAlreadyExistsException) {
            StartIpsumResult.AlreadyActive
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            StartIpsumResult.PersistenceFailure
        }
    }
}
