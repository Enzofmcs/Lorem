package dev.lorem.app.domain.repository

import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.ProblemHistory
import kotlinx.coroutines.flow.Flow

interface LoremRepository {
    val profile: Flow<LocalProfile?>
    val problemHistory: Flow<List<ProblemHistory>>
    val problemCatalog: Flow<List<CodeforcesProblem>>
    val catalogLastUpdatedEpochMillis: Flow<Long?>

    suspend fun saveProfile(profile: LocalProfile)

    suspend fun clearProfile()

    /** Persists history only for the Codeforces account identified by [ownerHandle]. */
    suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>)

    /** Atomically replaces the remote snapshot without touching Ipsum/history data. */
    suspend fun replaceProblemCatalog(problems: List<CodeforcesProblem>, updatedAtEpochMillis: Long)
}
