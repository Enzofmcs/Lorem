package dev.lorem.app.data.handles

import dev.lorem.app.domain.handles.*
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.UserLookupResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** SDK boundary. Implementations must validate the captured uid before/after every request. */
interface HandlesRemoteStore {
    fun observe(uid: String): Flow<HandlesSnapshot>
    suspend fun find(uid: String, key: String): AccountHandle?
    suspend fun create(uid: String, identity: HandleIdentity): AccountHandle
    suspend fun select(uid: String, key: String, bindingVersion: Long)
}

interface HandlesCache {
    suspend fun read(uid: String): HandlesSnapshot
    suspend fun save(uid: String, snapshot: HandlesSnapshot): HandlesSnapshot
}

class AccountHandlesRepository(
    private val remote: HandlesRemoteStore,
    private val codeforces: CodeforcesRepository,
    private val cache: HandlesCache,
) : HandlesRepository {
    override fun observe(uid: String): Flow<HandlesSnapshot> = flow {
        require(uid.isNotBlank())
        var saved = cache.read(uid).copy(serverConfirmed = false)
        emit(saved)
        remote.observe(uid).collect { snapshot ->
            if (snapshot.serverConfirmed) {
                saved = cache.save(uid, snapshot)
                emit(saved)
            } else {
                emit(saved.copy(serverConfirmed = false, locked = saved.locked || snapshot.locked))
            }
        }
    }

    override suspend fun add(uid: String, input: String): AccountHandle {
        val requested = HandleIdentity.parse(input)
        // Re-adding a registered handle does not depend on CF availability or replace its progress.
        remote.find(uid, requested.key)?.let { return it }
        val identity = when (val result = codeforces.user(requested.canonical)) {
            is UserLookupResult.Success -> HandleIdentity.parse(result.user.handle).also {
                if (it.key != requested.key) throw HandleException(HandleFailure.CODEFORCES)
            }
            UserLookupResult.UserNotFound -> throw HandleException(HandleFailure.NOT_FOUND)
            UserLookupResult.NetworkFailure -> throw HandleException(HandleFailure.NETWORK)
            UserLookupResult.RateLimited -> throw HandleException(HandleFailure.RATE_LIMITED)
            is UserLookupResult.ApiFailure -> throw HandleException(HandleFailure.CODEFORCES)
        }
        // Official CF rating never enters the creation command. Every new binding starts at 800.
        return remote.create(uid, identity)
    }

    override suspend fun select(uid: String, handleKey: String, bindingVersion: Long) =
        remote.select(uid, handleKey, bindingVersion)
}
