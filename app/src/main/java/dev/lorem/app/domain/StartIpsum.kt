package dev.lorem.app.domain

import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.repository.ActiveIpsumAlreadyExistsException
import dev.lorem.app.domain.repository.LoremRepository
import kotlinx.coroutines.flow.first

sealed interface StartIpsumResult {
    data class Success(val ipsum: Ipsum) : StartIpsumResult
    data object AlreadyActive : StartIpsumResult
    data object NoCandidate : StartIpsumResult
    data object NoProfile : StartIpsumResult
    data object PersistenceFailure : StartIpsumResult
}

class StartIpsum(
    private val repository: LoremRepository,
    private val recommender: RecommendIpsum,
    private val nowMillis: () -> Long,
) {
    suspend operator fun invoke(): StartIpsumResult {
        val profile = repository.profile.first() ?: return StartIpsumResult.NoProfile
        if (repository.activeIpsum.first() != null) return StartIpsumResult.AlreadyActive
        val recommendation = recommender.recommend(
            loremRating = profile.loremRating,
            catalog = repository.problemCatalog.first(),
            history = repository.problemHistory.first(),
            ipsums = repository.ipsums.first(),
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
        )
        return try {
            StartIpsumResult.Success(repository.createActiveIpsum(ipsum))
        } catch (_: ActiveIpsumAlreadyExistsException) {
            StartIpsumResult.AlreadyActive
        } catch (_: Exception) {
            StartIpsumResult.PersistenceFailure
        }
    }
}
