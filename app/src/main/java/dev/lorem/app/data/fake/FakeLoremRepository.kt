package dev.lorem.app.data.fake

import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.repository.LoremRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLoremRepository(initialProfile: LocalProfile? = null) : LoremRepository {
    private val storedProfile = MutableStateFlow(initialProfile)
    private val storedProblemHistory = MutableStateFlow<List<ProblemHistory>>(emptyList())

    override val profile: Flow<LocalProfile?> = storedProfile
    override val problemHistory: Flow<List<ProblemHistory>> = storedProblemHistory

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
}
