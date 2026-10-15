package dev.lorem.app.domain.sync

import dev.lorem.app.domain.*
import dev.lorem.app.domain.repository.UserLookupResult

enum class SyncFailureKind { NETWORK, RATE_LIMIT, REMOTE, PERSISTENCE, INTERRUPTED }

internal fun syncFailureKind(result: Any?): SyncFailureKind = when (result) {
    ProblemHistorySyncResult.NetworkFailure, is ProblemCatalogSyncResult.NetworkFailure,
    UpdatePendingIpsumsResult.NetworkFailure, VerifyIpsumResult.NetworkFailure,
    UserLookupResult.NetworkFailure -> SyncFailureKind.NETWORK
    ProblemHistorySyncResult.RateLimited, is ProblemCatalogSyncResult.RateLimited,
    UpdatePendingIpsumsResult.RateLimited, VerifyIpsumResult.RateLimited,
    UserLookupResult.RateLimited -> SyncFailureKind.RATE_LIMIT
    ProblemHistorySyncResult.PersistenceFailure, is ProblemCatalogSyncResult.PersistenceFailure,
    UpdatePendingIpsumsResult.PersistenceFailure, VerifyIpsumResult.PersistenceFailure -> SyncFailureKind.PERSISTENCE
    else -> SyncFailureKind.REMOTE
}
