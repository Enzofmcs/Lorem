package dev.lorem.app.domain.sync

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class ProfileLink(
    val handle: String,
    val version: Long,
    val uid: String? = null,
    val bindingVersion: Long = 1L,
) {
    /** Legacy keys stay byte-for-byte stable; cloud work never shares their namespace. */
    val scopeKey: String get() = if (uid == null) "$handle:$version" else {
        fun encode(value: String) = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        "firebase:${encode(uid)}:${encode(handle)}:$bindingVersion:$version"
    }

    companion object {
        fun of(handle: String, version: Long) = ProfileLink(handle.trim().lowercase(Locale.ROOT), version)
        fun forAccount(uid: String, handle: String, bindingVersion: Long, version: Long): ProfileLink {
            require(uid.isNotBlank() && bindingVersion > 0 && version > 0)
            return ProfileLink(handle.trim().lowercase(Locale.ROOT), version, uid, bindingVersion)
        }
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
    else "${requireNotNull(link).scopeKey}:${dataset.name}" + (ipsumId?.let { ":$it" } ?: "")

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
