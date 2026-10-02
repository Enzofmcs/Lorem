package dev.lorem.app.data.fake

import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.repository.ActiveIpsumAlreadyExistsException
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.model.IpsumSubmission
import dev.lorem.app.domain.repository.IpsumUpdate
import dev.lorem.app.domain.calculateLoremRating
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.IpsumResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLoremRepository(initialProfile: LocalProfile? = null) : LoremRepository {
    private val storedProfile = MutableStateFlow(initialProfile)
    private val storedProblemHistory = MutableStateFlow<List<ProblemHistory>>(emptyList())
    private val storedProblemCatalog = MutableStateFlow<List<CodeforcesProblem>>(emptyList())
    private val storedCatalogUpdatedAt = MutableStateFlow<Long?>(null)
    private val storedIpsums = MutableStateFlow<List<Ipsum>>(emptyList())

    override val profile: Flow<LocalProfile?> = storedProfile
    override val problemHistory: Flow<List<ProblemHistory>> = storedProblemHistory
    override val problemCatalog: Flow<List<CodeforcesProblem>> = storedProblemCatalog
    override val catalogLastUpdatedEpochMillis: Flow<Long?> = storedCatalogUpdatedAt
    override val ipsums: Flow<List<Ipsum>> = storedIpsums
    override val activeIpsum = MutableStateFlow<Ipsum?>(null)

    override suspend fun saveProfile(profile: LocalProfile) {
        storedProfile.value = profile
    }

    override suspend fun clearProfile() {
        storedProfile.value = null
    }

    override suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>) {
        storedProblemHistory.value = (storedProblemHistory.value + history)
            .associateBy(ProblemHistory::problemId)
            .values
            .toList()
    }

    override suspend fun replaceProblemCatalog(problems: List<CodeforcesProblem>, updatedAtEpochMillis: Long) {
        storedProblemCatalog.value = problems.filter { it.rating != null }.distinctBy { it.id }
        storedCatalogUpdatedAt.value = updatedAtEpochMillis
    }

    override suspend fun createActiveIpsum(ipsum: Ipsum): Ipsum {
        if (activeIpsum.value != null) throw ActiveIpsumAlreadyExistsException()
        val saved = ipsum.copy(id = (storedIpsums.value.maxOfOrNull(Ipsum::id) ?: 0) + 1)
        storedIpsums.value += saved
        activeIpsum.value = saved
        return saved
    }

    override suspend fun revealIpsumHint(ipsumId: Long, revealedAtEpochMillis: Long) {
        val current = activeIpsum.value ?: return
        if (current.id != ipsumId || current.hintRevealedAtEpochMillis != null) return
        val updated = current.copy(hintRevealedAtEpochMillis = revealedAtEpochMillis)
        activeIpsum.value = updated
        storedIpsums.value = storedIpsums.value.map { if (it.id == ipsumId) updated else it }
    }

    override suspend fun recordIpsumSubmissions(ipsumId: Long, submissions: List<IpsumSubmission>): IpsumUpdate {
        val current = activeIpsum.value ?: return IpsumUpdate(0, 0, false)
        val firstAc = submissions.firstOrNull { it.verdict == "OK" }
        val errors = submissions.takeWhile { it.verdict != "OK" }.size
        if (firstAc != null) {
            val rating = calculateLoremRating(
                current.initialLoremRating, current.selectedRating, true,
                (firstAc.createdAtEpochMillis - current.startedAtEpochMillis).coerceAtLeast(0),
                current.hintRevealedAtEpochMillis != null,
            )
            val updated = current.copy(
                status = dev.lorem.app.domain.model.IpsumStatus.COMPLETED,
                endedAtEpochMillis = firstAc.createdAtEpochMillis,
                errorCount = errors,
                ratingDelta = rating.delta,
                finalLoremRating = rating.ratingAfter,
                expectedTimeMillis = rating.expectedTimeMillis,
            )
            storedIpsums.value = storedIpsums.value.map { if (it.id == ipsumId) updated else it }
            activeIpsum.value = null
            storedProfile.value = storedProfile.value?.copy(loremRating = rating.ratingAfter)
        }
        return IpsumUpdate(submissions.size, errors, firstAc != null)
    }

    override suspend fun endIpsumWithoutAc(
        ipsumId: Long,
        reason: IpsumFailureReason,
        endedAtEpochMillis: Long,
    ): Boolean {
        val current = activeIpsum.value?.takeIf { it.id == ipsumId } ?: return false
        val rating = calculateLoremRating(
            current.initialLoremRating, current.selectedRating, false,
            (endedAtEpochMillis - current.startedAtEpochMillis).coerceAtLeast(0),
            current.hintRevealedAtEpochMillis != null,
        )
        val updated = current.copy(
            status = dev.lorem.app.domain.model.IpsumStatus.PENDING,
            endedAtEpochMillis = endedAtEpochMillis,
            failureReason = reason,
            ratingDelta = rating.delta,
            finalLoremRating = rating.ratingAfter,
            expectedTimeMillis = rating.expectedTimeMillis,
        )
        storedIpsums.value = storedIpsums.value.map { if (it.id == ipsumId) updated else it }
        activeIpsum.value = null
        storedProfile.value = storedProfile.value?.copy(loremRating = rating.ratingAfter)
        return true
    }

    override suspend fun getIpsumResult(ipsumId: Long): IpsumResult? = storedIpsums.value
        .firstOrNull { it.id == ipsumId && it.status != dev.lorem.app.domain.model.IpsumStatus.ACTIVE }
        ?.let { IpsumResult(it, emptyList()) }
}
