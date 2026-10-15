package dev.lorem.app.domain.sync

import dev.lorem.app.domain.*
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import kotlinx.coroutines.flow.first

enum class SyncWorkResult { SUCCESS, RETRY, FAILURE, OBSOLETE }

/** Runs independent stages even after partial failure. Successful stages retain their own TTL. */
class BackgroundSynchronization(
    private val repository: LoremRepository,
    private val coordinator: SynchronizationCoordinator,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val apiDeferred: suspend () -> Boolean = { false },
) {
    suspend fun profileDue(link: ProfileLink): Boolean =
        listOf(SyncDataset.HISTORY, SyncDataset.PENDING, SyncDataset.PROFILE).any { due(it, link) }

    suspend fun catalogDue(): Boolean = due(SyncDataset.CATALOG, null)

    private suspend fun due(dataset: SyncDataset, link: ProfileLink?): Boolean {
        val state = coordinator.store.read(syncKey(dataset, link))
        val ttl = if (dataset == SyncDataset.PROFILE || dataset == SyncDataset.CATALOG) DAY else QUARTER_HOUR
        return state.running || state.failure != null || state.lastSuccessEpochMillis?.let { nowMillis() - it >= ttl } != false
    }

    suspend fun profile(link: ProfileLink): SyncWorkResult {
        val results = mutableListOf<SyncWorkResult>()
        try {
            if (repository.profileLink.first() != link) return SyncWorkResult.OBSOLETE
            if (apiDeferred()) return SyncWorkResult.RETRY
            repository.activeIpsum.first()?.takeIf { ProfileLink.of(it.ownerHandle, link.version) == link }?.let {
                results += classify(coordinator.verifyActiveIpsum(link, it.id))
            }
            if (apiDeferred()) return SyncWorkResult.RETRY
            if (due(SyncDataset.HISTORY, link)) results += classify(coordinator.synchronizeHistory(link))
            if (apiDeferred()) return SyncWorkResult.RETRY
            if (due(SyncDataset.PENDING, link)) results += classify(coordinator.updatePendingIpsums(link))
            if (apiDeferred()) return SyncWorkResult.RETRY
            if (due(SyncDataset.PROFILE, link)) results += classify(coordinator.refreshProfile(link))
            if (repository.profileLink.first() != link) return SyncWorkResult.OBSOLETE
        } catch (_: ProfileLinkChanged) { return SyncWorkResult.OBSOLETE }
        return when {
            SyncWorkResult.RETRY in results -> SyncWorkResult.RETRY
            SyncWorkResult.FAILURE in results -> SyncWorkResult.FAILURE
            else -> SyncWorkResult.SUCCESS
        }
    }

    suspend fun catalog(): SyncWorkResult = if (catalogDue()) classify(coordinator.synchronizeCatalog()) else SyncWorkResult.SUCCESS

    private fun classify(result: Any): SyncWorkResult = when (result) {
        is ProblemHistorySyncResult.Success, is ProblemCatalogSyncResult.Success,
        is UpdatePendingIpsumsResult.Success, is UserLookupResult.Success,
        is VerifyIpsumResult.Success, VerifyIpsumResult.NoActiveIpsum -> SyncWorkResult.SUCCESS
        ProblemHistorySyncResult.NetworkFailure, ProblemHistorySyncResult.RateLimited,
        ProblemHistorySyncResult.PersistenceFailure, is ProblemCatalogSyncResult.NetworkFailure,
        is ProblemCatalogSyncResult.RateLimited, is ProblemCatalogSyncResult.PersistenceFailure,
        UpdatePendingIpsumsResult.NetworkFailure, UpdatePendingIpsumsResult.RateLimited,
        UpdatePendingIpsumsResult.PersistenceFailure, UserLookupResult.NetworkFailure,
        UserLookupResult.RateLimited, VerifyIpsumResult.NetworkFailure, VerifyIpsumResult.RateLimited,
        VerifyIpsumResult.PersistenceFailure -> SyncWorkResult.RETRY
        is ProblemHistorySyncResult.HttpFailure -> http(result.statusCode)
        is ProblemCatalogSyncResult.HttpFailure -> http(result.statusCode)
        is UpdatePendingIpsumsResult.HttpFailure -> http(result.statusCode)
        is VerifyIpsumResult.HttpFailure -> http(result.statusCode)
        else -> SyncWorkResult.FAILURE
    }

    private fun http(status: Int) = if (status == 408 || status == 429 || status in 500..599) SyncWorkResult.RETRY else SyncWorkResult.FAILURE

    companion object {
        const val QUARTER_HOUR = 15 * 60 * 1000L
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
