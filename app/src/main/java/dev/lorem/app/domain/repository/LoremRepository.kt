package dev.lorem.app.domain.repository

import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import kotlinx.coroutines.flow.Flow

interface LoremRepository {
    val profile: Flow<LocalProfile?>
    val problemHistory: Flow<List<ProblemHistory>>

    suspend fun saveProfile(profile: LocalProfile)

    suspend fun clearProfile()

    suspend fun saveProblemHistory(history: List<ProblemHistory>)
}
