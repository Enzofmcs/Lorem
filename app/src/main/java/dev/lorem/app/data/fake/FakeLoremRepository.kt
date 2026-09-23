package dev.lorem.app.data.fake

import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.repository.ActiveIpsumAlreadyExistsException
import dev.lorem.app.domain.repository.LoremRepository
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
}
