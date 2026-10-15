package dev.lorem.app.domain.sync

import dev.lorem.app.domain.*
import dev.lorem.app.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One process-wide instance in AppContainer; callers share work, not a screen's lifetime. */
class SynchronizationCoordinator(
    private val repository: LoremRepository,
    private val codeforces: CodeforcesRepository,
    private val scope: CoroutineScope,
    val store: SynchronizationStore = MemorySynchronizationStore(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Operation(val dataset: SyncDataset, val link: ProfileLink?, val ipsumId: Long? = null, val lookupHandle: String? = null)
    private val mutex = Mutex()
    private val work = mutableMapOf<Operation, Deferred<Any>>()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.profileLink.collect { current ->
                mutex.withLock {
                    val obsolete = work.filterKeys { it.dataset != SyncDataset.CATALOG && it.link != current }
                    obsolete.forEach { (operation, job) ->
                        work.remove(operation)
                        job.cancel(ProfileLinkChanged())
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> single(operation: Operation, action: suspend () -> T): T {
        val deferred = mutex.withLock {
            work[operation]?.takeIf { !it.isCompleted } ?: scope.async(start = CoroutineStart.LAZY) {
                action()
            }.also { work[operation] = it }
        }
        try { return deferred.await() as T }
        finally {
            withContext(NonCancellable) {
                mutex.withLock { if (deferred.isCompleted && work[operation] === deferred) work.remove(operation) }
            }
        }
    }

    suspend fun synchronizeHistory(): ProblemHistorySyncResult {
        val link = repository.profileLink.first() ?: return ProblemHistorySyncResult.NoActiveProfile
        return single(Operation(SyncDataset.HISTORY, link)) {
            SynchronizeProblemHistory(repository, codeforces, store = store, nowMillis = nowMillis)(link)
        }
    }

    suspend fun synchronizeCatalog(): ProblemCatalogSyncResult = single(Operation(SyncDataset.CATALOG, null)) {
        track(SyncDataset.CATALOG, null, { it is ProblemCatalogSyncResult.Success },
            { ProblemCatalogSyncResult.PersistenceFailure(repository.problemCatalog.first().size) }) {
            SynchronizeProblemCatalog(repository, codeforces, nowMillis)()
        }
    }

    suspend fun verifyActiveIpsum(): VerifyIpsumResult {
        val link = repository.profileLink.first() ?: return VerifyIpsumResult.NoActiveIpsum
        val ipsum = repository.activeIpsum.first() ?: return VerifyIpsumResult.NoActiveIpsum
        return single(Operation(SyncDataset.SUBMISSIONS, link, ipsum.id)) {
            track(SyncDataset.SUBMISSIONS, link, { it is VerifyIpsumResult.Success }, { VerifyIpsumResult.PersistenceFailure }, ipsumId = ipsum.id) {
                VerifyIpsumSubmissions(repository, codeforces)(link, ipsum.id)
            }
        }
    }

    suspend fun updatePendingIpsums(): UpdatePendingIpsumsResult {
        val link = repository.profileLink.first() ?: return UpdatePendingIpsumsResult.NoActiveProfile
        return single(Operation(SyncDataset.PENDING, link)) {
            track(SyncDataset.PENDING, link, { it is UpdatePendingIpsumsResult.Success }, { UpdatePendingIpsumsResult.PersistenceFailure }) {
                UpdatePendingIpsums(repository, codeforces)(link)
            }
        }
    }

    suspend fun lookupUser(handle: String): UserLookupResult {
        val link = repository.profileLink.first()
        val normalized = ProfileLink.of(handle, 0).handle
        if (link?.handle == normalized) return refreshProfile()
        return single(Operation(SyncDataset.PROFILE, link, lookupHandle = normalized)) {
            codeforces.user(handle)
        }
    }

    suspend fun refreshProfile(): UserLookupResult {
        val link = repository.profileLink.first() ?: throw ProfileLinkChanged()
        return single(Operation(SyncDataset.PROFILE, link)) {
            track(SyncDataset.PROFILE, link, { it is UserLookupResult.Success },
                { UserLookupResult.ApiFailure("Não foi possível salvar os metadados do perfil.") }) {
                val result = codeforces.user(link.handle)
                if (result is UserLookupResult.Success) repository.withProfileLink(link) {
                    repository.updateProfileMetadata(result.user.handle, result.user.displayName, result.user.rating)
                }
                result
            }
        }
    }

    private suspend fun <T> track(
        dataset: SyncDataset,
        link: ProfileLink?,
        success: (T) -> Boolean,
        persistenceFailure: suspend () -> T,
        ipsumId: Long? = null,
        action: suspend () -> T,
    ): T {
        val key = syncKey(dataset, link, ipsumId)
        var state = SynchronizationState()
        suspend fun persist() {
            if (link == null) store.write(key, state)
            else repository.withProfileLink(link) { store.write(key, state) }
        }
        try {
            state = store.read(key)
            state = state.copy(running = true, failure = null)
            persist()
            val result = action()
            state = state.copy(running = false,
                lastSuccessEpochMillis = if (success(result)) nowMillis() else state.lastSuccessEpochMillis,
                failure = if (success(result)) null else result.toString())
            persist()
            return result
        } catch (error: Exception) {
            withContext(NonCancellable) {
                if (link == null || repository.profileLink.first() == link) {
                    state = state.copy(running = false, failure = if (error is CancellationException) "Interrompido" else "Falha de persistência")
                    try { persist() } catch (_: Exception) { /* Keep the last durable evidence. */ }
                }
            }
            if (error is CancellationException) throw error
            return persistenceFailure()
        }
    }
}
