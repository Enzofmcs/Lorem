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
    private data class SharedWork(val job: Deferred<Any>, var callers: Int = 0)
    private val work = mutableMapOf<Operation, SharedWork>()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.profileLink.collect { current ->
                mutex.withLock {
                    val obsolete = work.filterKeys { it.dataset != SyncDataset.CATALOG && it.link != current }
                    obsolete.forEach { (operation, job) ->
                        work.remove(operation)
                        job.job.cancel(ProfileLinkChanged())
                    }
                }
            }
        }
    }

    private suspend fun <T : Any> acquire(operation: Operation, action: suspend () -> T): SharedWork {
        while (true) {
            val (shared, stopping) = mutex.withLock {
                val previous = work[operation]
                if (previous != null && previous.job.isCancelled && !previous.job.isCompleted) {
                    null to previous.job
                } else {
                    val entry = previous?.takeIf { !it.job.isCompleted } ?: SharedWork(scope.async(start = CoroutineStart.LAZY) {
                        action()
                    }).also { work[operation] = it }
                    entry.callers++
                    entry to null
                }
            }
            if (shared != null) return shared
            stopping?.join()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> single(operation: Operation, action: suspend () -> T): T {
        val shared = acquire(operation, action)
        try { return shared.job.await() as T }
        finally {
            withContext(NonCancellable) {
                val lastCaller = mutex.withLock {
                    shared.callers--
                    (shared.callers == 0).also { if (it && !shared.job.isCompleted) shared.job.cancel() }
                }
                if (lastCaller) {
                    shared.job.join() // Finish durable cancellation/checkpoint cleanup before retrying.
                    mutex.withLock { if (work[operation] === shared) work.remove(operation) }
                }
            }
        }
    }

    private suspend fun currentLink(expected: ProfileLink?): ProfileLink? {
        val current = repository.profileLink.first()
        if (expected != null && current != expected) throw ProfileLinkChanged()
        return current
    }

    /** Only at process startup; never reset a dataset owned by a live operation. */
    suspend fun recoverInterrupted() = mutex.withLock {
        val link = repository.profileLink.first()
        val keys = mutableListOf(syncKey(SyncDataset.CATALOG, null))
        if (link != null) {
            keys += listOf(SyncDataset.HISTORY, SyncDataset.PROFILE, SyncDataset.PENDING).map { syncKey(it, link) }
            repository.activeIpsum.first()?.let { keys += syncKey(SyncDataset.SUBMISSIONS, link, it.id) }
        }
        for (key in keys) {
            if (work.keys.any { it.lookupHandle == null && syncKey(it.dataset, it.link, it.ipsumId) == key }) continue
            val state = store.read(key)
            if (state.running) {
                val recovered = state.copy(running = false, failure = "Interrompido; retomada disponível", failureKind = SyncFailureKind.INTERRUPTED)
                if (key == syncKey(SyncDataset.CATALOG, null)) store.write(key, recovered)
                else if (link != null) repository.withProfileLink(link) { store.write(key, recovered) }
            }
        }
    }

    suspend fun synchronizeHistory(expectedLink: ProfileLink? = null): ProblemHistorySyncResult {
        val link = currentLink(expectedLink) ?: return ProblemHistorySyncResult.NoActiveProfile
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

    suspend fun verifyActiveIpsum(expectedLink: ProfileLink? = null, expectedIpsumId: Long? = null): VerifyIpsumResult {
        val link = currentLink(expectedLink) ?: return VerifyIpsumResult.NoActiveIpsum
        val ipsum = repository.activeIpsum.first() ?: return VerifyIpsumResult.NoActiveIpsum
        if (expectedIpsumId != null && ipsum.id != expectedIpsumId) return VerifyIpsumResult.NoActiveIpsum
        return single(Operation(SyncDataset.SUBMISSIONS, link, ipsum.id)) {
            track(SyncDataset.SUBMISSIONS, link, { it is VerifyIpsumResult.Success }, { VerifyIpsumResult.PersistenceFailure }, ipsumId = ipsum.id) {
                VerifyIpsumSubmissions(repository, codeforces)(link, ipsum.id)
            }
        }
    }

    suspend fun updatePendingIpsums(expectedLink: ProfileLink? = null): UpdatePendingIpsumsResult {
        val link = currentLink(expectedLink) ?: return UpdatePendingIpsumsResult.NoActiveProfile
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

    suspend fun refreshProfile(expectedLink: ProfileLink? = null): UserLookupResult {
        val link = currentLink(expectedLink) ?: throw ProfileLinkChanged()
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
        var outcomeKnown = false
        suspend fun persist() {
            if (link == null) store.write(key, state)
            else repository.withProfileLink(link) { store.write(key, state) }
        }
        try {
            state = store.read(key)
            state = state.copy(running = true, failure = null, failureKind = null)
            persist()
            val result = action()
            state = state.copy(running = false,
                lastSuccessEpochMillis = if (success(result)) nowMillis() else state.lastSuccessEpochMillis,
                failure = if (success(result)) null else result.toString(),
                failureKind = if (success(result)) null else syncFailureKind(result))
            outcomeKnown = true
            withContext(NonCancellable) { persist() }
            return result
        } catch (error: Exception) {
            withContext(NonCancellable) {
                if (link == null || repository.profileLink.first() == link) {
                    // Cancellation may resume after the outcome's durable write (for example,
                    // when AC removes its screen). Finish that known outcome instead of replacing
                    // it with Interrupted. Cancellation before an outcome remains recoverable.
                    if (error !is CancellationException || !outcomeKnown) {
                        state = state.copy(running = false, failure = if (error is CancellationException) "Interrompido" else "Falha de persistência",
                            failureKind = if (error is CancellationException) SyncFailureKind.INTERRUPTED else SyncFailureKind.PERSISTENCE)
                    }
                    try { persist() } catch (_: Exception) { /* Keep the last durable evidence. */ }
                }
            }
            if (error is CancellationException) throw error
            return persistenceFailure()
        }
    }
}
