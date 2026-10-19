package dev.lorem.app.domain.handles

import dev.lorem.app.domain.sync.ProfileLink
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** Prefix keeps even historical dot-only names away from Firestore's reserved document IDs. */
@ConsistentCopyVisibility
data class HandleIdentity private constructor(val canonical: String, val key: String) {
    companion object {
        fun parse(value: String): HandleIdentity {
            val handle = value.trim()
            if (!Regex("[A-Za-z0-9_.-]{1,64}").matches(handle)) throw HandleException(HandleFailure.INVALID_HANDLE)
            return HandleIdentity(handle, "cf_" + handle.lowercase(Locale.ROOT))
        }
    }
}

data class AccountHandle(
    val uid: String,
    val identity: HandleIdentity,
    val bindingVersion: Long,
    val loremRating: Int,
    val consolidatedRating: Int?,
    val progressVersion: Long,
    val createdAtEpochMillis: Long,
)

data class HandlesSnapshot(
    val handles: List<AccountHandle> = emptyList(),
    val selection: ProfileLink? = null,
    val locked: Boolean = false,
    val serverConfirmed: Boolean = false,
) {
    val selected: AccountHandle? get() = selection?.let { link -> handles.firstOrNull {
        it.uid == link.uid && it.identity.canonical.lowercase(Locale.ROOT) == link.handle &&
            it.bindingVersion == link.bindingVersion
    } }
}

enum class HandleFailure {
    INVALID_HANDLE, NOT_FOUND, CODEFORCES, NETWORK, RATE_LIMITED, CONFIGURATION,
    INVALID_SESSION, QUOTA, IPSUM_ACTIVE,
}

class HandleException(val failure: HandleFailure) : Exception()

interface HandlesRepository {
    fun observe(uid: String): Flow<HandlesSnapshot>
    suspend fun add(uid: String, input: String): AccountHandle
    suspend fun select(uid: String, handleKey: String, bindingVersion: Long)
}
