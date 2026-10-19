package dev.lorem.app.domain.sync

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class ProfileLink(val handle: String, val version: Long, val uid: String? = null) {
    companion object {
        fun of(handle: String, version: Long, uid: String? = null) = ProfileLink(handle.trim().lowercase(Locale.ROOT), version, uid)
    }
}

class ProfileLinkChanged : CancellationException("Profile link changed")

enum class SyncDataset { HISTORY, CATALOG, PROFILE, SUBMISSIONS, PENDING }

/** Each dataset has its own evidence of success. A checkpoint is never readiness. */
data class SynchronizationState(
    val running: Boolean = false,
    val retryNotBeforeEpochMillis: Long = 0,
    val lastSuccessEpochMillis: Long? = null,
    val failure: String? = null,
    val failureKind: SyncFailureKind? = null,
    val nextFrom: Int = 1,
    val importedCount: Int = 0,
    val watermarkEpochMillis: Long = 0,
    val querySinceEpochMillis: Long? = null,
    val newestSeenEpochMillis: Long = 0,
    val catchingUp: Boolean = false,
    val pendingSubmissions: Map<Long, Long> = emptyMap(),
)

fun syncKey(dataset: SyncDataset, link: ProfileLink?, ipsumId: Long? = null): String =
    if (dataset == SyncDataset.CATALOG) "catalog"
    else requireNotNull(link).let { (it.uid?.let { uid -> "uid${uid.length}:$uid:" } ?: "") +
        "${it.handle}:${it.version}:${dataset.name}" } + (ipsumId?.let { ":$it" } ?: "")

interface SynchronizationStore {
    fun observe(key: String): Flow<SynchronizationState>
    suspend fun read(key: String): SynchronizationState
    suspend fun write(key: String, state: SynchronizationState)
}

class MemorySynchronizationStore : SynchronizationStore {
    private val states = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<SynchronizationState>>()
    private fun state(key: String) = states.computeIfAbsent(key) { MutableStateFlow(SynchronizationState()) }
    override fun observe(key: String): Flow<SynchronizationState> = state(key)
    override suspend fun read(key: String) = state(key).value
    override suspend fun write(key: String, state: SynchronizationState) { state(key).value = state }
}
