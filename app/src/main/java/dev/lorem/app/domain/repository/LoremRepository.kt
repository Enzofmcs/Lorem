package dev.lorem.app.domain.repository

import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumSubmission
import kotlinx.coroutines.flow.Flow

interface LoremRepository {
    val profile: Flow<LocalProfile?>
    val problemHistory: Flow<List<ProblemHistory>>
    val problemCatalog: Flow<List<CodeforcesProblem>>
    val catalogLastUpdatedEpochMillis: Flow<Long?>
    val ipsums: Flow<List<Ipsum>>
    val activeIpsum: Flow<Ipsum?>

    suspend fun saveProfile(profile: LocalProfile)

    suspend fun clearProfile()

    /** Persists history only for the Codeforces account identified by [ownerHandle]. */
    suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>)

    /** Atomically replaces the remote snapshot without touching Ipsum/history data. */
    suspend fun replaceProblemCatalog(problems: List<CodeforcesProblem>, updatedAtEpochMillis: Long)

    /** The persistence layer must reject insertion when any active Ipsum already exists. */
    suspend fun createActiveIpsum(ipsum: Ipsum): Ipsum

    /** Records the first reveal only; subsequent and concurrent calls leave it unchanged. */
    suspend fun revealIpsumHint(ipsumId: Long, revealedAtEpochMillis: Long)

    /** Atomically deduplicates definitive submissions and completes an active Ipsum at its first AC. */
    suspend fun recordIpsumSubmissions(ipsumId: Long, submissions: List<IpsumSubmission>): IpsumUpdate =
        IpsumUpdate(0, 0, false)
}

data class IpsumUpdate(val insertedCount: Int, val errorCount: Int, val completed: Boolean)

class ActiveIpsumAlreadyExistsException(cause: Throwable? = null) : Exception(cause)
