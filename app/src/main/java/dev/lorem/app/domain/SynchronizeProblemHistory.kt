package dev.lorem.app.domain

import dev.lorem.app.domain.sync.SyncFailureKind
import dev.lorem.app.domain.sync.SynchronizationState
import dev.lorem.app.domain.sync.SynchronizationStore
import dev.lorem.app.domain.sync.MemorySynchronizationStore
import dev.lorem.app.domain.sync.ProfileLinkChanged
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.domain.sync.SyncDataset
import dev.lorem.app.domain.sync.syncKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

sealed interface ProblemHistorySyncResult {
    data class Success(val problemCount: Int) : ProblemHistorySyncResult
    data object NoActiveProfile : ProblemHistorySyncResult
    data class Failed(val message: String) : ProblemHistorySyncResult
    data object RateLimited : ProblemHistorySyncResult
    data object NetworkFailure : ProblemHistorySyncResult
    data class HttpFailure(val statusCode: Int) : ProblemHistorySyncResult
    data object InvalidResponse : ProblemHistorySyncResult
    data object PersistenceFailure : ProblemHistorySyncResult
}

class SynchronizeProblemHistory(
    private val loremRepository: LoremRepository,
    private val codeforcesRepository: CodeforcesRepository,
    private val store: SynchronizationStore = MemorySynchronizationStore(),
    private val pageSize: Int = 1_000,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    init { require(pageSize >= 2) }

    suspend operator fun invoke(expectedLink: ProfileLink? = null): ProblemHistorySyncResult {
        val link = expectedLink ?: loremRepository.profileLink.first() ?: return ProblemHistorySyncResult.NoActiveProfile
        if (loremRepository.profileLink.first() != link) throw ProfileLinkChanged()
        val key = syncKey(SyncDataset.HISTORY, link)
        var state = SynchronizationState()
        suspend fun persist() = loremRepository.withProfileLink(link) { store.write(key, state) }
        try {
            state = store.read(key)
            // Keep the original boundary on resume. New runs overlap timestamps
            // and revisit all provisional verdicts, regardless of their age.
            if (state.nextFrom == 1 && state.importedCount == 0 && !state.catchingUp) {
                val boundary = listOfNotNull(
                    state.watermarkEpochMillis.takeIf { it > 0 }?.minus(60_000L),
                    state.pendingSubmissions.values.minOrNull()?.minus(1L),
                ).minOrNull()
                state = state.copy(querySinceEpochMillis = boundary)
            }
            state = state.copy(running = true, failure = null, failureKind = null)
            persist()
            while (true) {
                if (loremRepository.profileLink.first() != link) throw ProfileLinkChanged()
                val wasFirstPage = state.nextFrom == 1 && state.importedCount == 0
                val result = codeforcesRepository.submissionPage(link.handle, state.nextFrom, pageSize)
                if (result !is SubmissionHistoryResult.Success) {
                    state = state.copy(running = false, failure = result.toString(), failureKind = when (result) {
                        SubmissionHistoryResult.NetworkFailure -> SyncFailureKind.NETWORK
                        SubmissionHistoryResult.RateLimited -> SyncFailureKind.RATE_LIMIT
                        else -> SyncFailureKind.REMOTE
                    })
                    persist()
                    return when (result) {
                        is SubmissionHistoryResult.Failed -> ProblemHistorySyncResult.Failed(result.message)
                        SubmissionHistoryResult.RateLimited -> ProblemHistorySyncResult.RateLimited
                        SubmissionHistoryResult.NetworkFailure -> ProblemHistorySyncResult.NetworkFailure
                        is SubmissionHistoryResult.HttpFailure -> ProblemHistorySyncResult.HttpFailure(result.statusCode)
                        else -> ProblemHistorySyncResult.InvalidResponse
                    }
                }
                val submissions = result.submissions.distinctBy { it.id }
                val pending = state.pendingSubmissions.toMutableMap()
                submissions.forEach {
                    if (it.verdict.isNullOrBlank() || it.verdict == "TESTING") pending[it.id] = it.createdAt.toEpochMilli()
                    else pending.remove(it.id)
                }
                val newest = maxOf(state.newestSeenEpochMillis, submissions.maxOfOrNull { it.createdAt.toEpochMilli() } ?: 0)
                val reachedBoundary = state.querySinceEpochMillis?.let { boundary ->
                    submissions.any { it.createdAt.toEpochMilli() < boundary }
                } ?: false
                val finished = result.receivedCount < pageSize || reachedBoundary
                // Room commits before the cursor. A failed DataStore write replays
                // this batch; monotonic upserts cannot erase AC or duplicate evidence.
                loremRepository.withProfileLink(link) {
                    loremRepository.saveProblemHistory(link.handle, buildProblemHistory(submissions))
                    state = state.copy(
                        nextFrom = state.nextFrom + pageSize / 2,
                        importedCount = state.importedCount + submissions.size,
                        newestSeenEpochMillis = newest,
                        pendingSubmissions = pending,
                    )
                    store.write(key, state)
                }
                if (!finished) continue
                if (!state.catchingUp && !wasFirstPage) {
                    // Offset pagination can move while importing. Sweep the head
                    // again before readiness; the next run also overlaps this watermark.
                    state = state.copy(nextFrom = 1, catchingUp = true,
                        querySinceEpochMillis = maxOf(0, newest - 60_000L))
                    persist()
                    continue
                }
                val at = nowMillis()
                loremRepository.withProfileLink(link) {
                    loremRepository.markHistorySynchronized(link.handle, at)
                    state = state.copy(running = false, failure = null, failureKind = null, lastSuccessEpochMillis = at,
                        nextFrom = 1, importedCount = 0, watermarkEpochMillis = maxOf(state.watermarkEpochMillis, newest),
                        querySinceEpochMillis = null, newestSeenEpochMillis = 0, catchingUp = false)
                    store.write(key, state)
                }
                return ProblemHistorySyncResult.Success(loremRepository.problemHistory.first().size)
            }
        } catch (error: Exception) {
            if (error is CancellationException) {
                withContext(NonCancellable) {
                    if (loremRepository.profileLink.first() == link) {
                        state = state.copy(running = false, failure = "Interrompido; retomada disponível", failureKind = SyncFailureKind.INTERRUPTED)
                        try { persist() } catch (_: Exception) { /* Retain the prior durable cursor. */ }
                    }
                }
                throw error
            }
            state = state.copy(running = false, failure = "Não foi possível salvar o histórico", failureKind = SyncFailureKind.PERSISTENCE)
            try { persist() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The previous durable checkpoint is still safe. */ }
            return ProblemHistorySyncResult.PersistenceFailure
        }
    }
}
