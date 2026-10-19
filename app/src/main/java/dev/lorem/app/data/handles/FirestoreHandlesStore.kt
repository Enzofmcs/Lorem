package dev.lorem.app.data.handles

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import dev.lorem.app.data.auth.firebaseFailureOf
import dev.lorem.app.domain.auth.AuthenticationFailure
import dev.lorem.app.domain.handles.*
import dev.lorem.app.domain.sync.ProfileLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executor

internal class FirestoreHandlesStore(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : HandlesRemoteStore {
    override fun observe(uid: String): Flow<HandlesSnapshot> = combine(
        handles(uid), document(uid, "selection"), document(uid, "current"),
    ) { list, selection, lock ->
        requireUser(uid)
        val chosen = selection.value.takeIf { it.exists() }?.let { doc ->
            if (doc.number("schemaVersion") != 1L || doc.get("selectedAt") !is Timestamp) malformed()
            val key = doc.getString("handleKey") ?: malformed()
            requireKey(key)
            val binding = doc.number("bindingVersion")
            val version = doc.number("selectionVersion")
            if (binding < 1 || version < 1) malformed()
            list.value.firstOrNull { it.identity.key == key && it.bindingVersion == binding }
                ?.let { ProfileLink.forAccount(uid, it.identity.canonical, binding, version) }
        }
        HandlesSnapshot(list.value, chosen, lock.value.exists(),
            !list.cached && !selection.cached && !lock.cached &&
                (!selection.value.exists() || chosen != null))
    }

    override suspend fun find(uid: String, key: String): AccountHandle? = handleCall {
        requireUser(uid); requireKey(key)
        val snapshot = account(uid).collection("handles").document(key).get(Source.SERVER).await()
        requireUser(uid)
        snapshot.takeIf { it.exists() }?.toHandle(uid)
    }

    override suspend fun create(uid: String, identity: HandleIdentity): AccountHandle = handleCall {
        requireUser(uid)
        if (HandleIdentity.parse(identity.canonical) != identity) malformed()
        val ref = account(uid).collection("handles").document(identity.key)
        val previous = db.runTransaction { transaction ->
            requireUser(uid)
            val existing = transaction.get(ref)
            if (existing.exists()) existing.toHandle(uid) else {
                transaction.set(ref, mapOf(
                    "schemaVersion" to 1, "handle" to identity.canonical, "handleKey" to identity.key,
                    "bindingVersion" to 1L, "loremRating" to 800, "consolidatedRating" to null,
                    "progressVersion" to 0L, "createdAt" to FieldValue.serverTimestamp(),
                ))
                null
            }
        }.await()
        requireUser(uid)
        previous ?: ref.get(Source.SERVER).await().also { requireUser(uid) }.toHandle(uid)
    }

    override suspend fun select(uid: String, key: String, bindingVersion: Long): Unit = handleCall {
        requireUser(uid); requireKey(key)
        val root = account(uid)
        db.runTransaction { transaction ->
            requireUser(uid)
            val target = transaction.get(root.collection("handles").document(key))
            val selection = transaction.get(root.collection("runtime").document("selection"))
            val lock = transaction.get(root.collection("runtime").document("current"))
            if (!target.exists() || target.toHandle(uid).bindingVersion != bindingVersion) malformed()
            val version = if (selection.exists()) {
                if (selection.number("schemaVersion") != 1L || selection.get("selectedAt") !is Timestamp) malformed()
                selection.number("selectionVersion").also { if (it < 1) malformed() }
            } else 0L
            if (selection.exists() && selection.getString("handleKey") == key &&
                selection.number("bindingVersion") == bindingVersion) return@runTransaction
            if (lock.exists()) throw HandleException(HandleFailure.IPSUM_ACTIVE)
            if (version < 0 || version == Long.MAX_VALUE) malformed()
            transaction.set(root.collection("runtime").document("selection"), mapOf(
                "schemaVersion" to 1, "handleKey" to key, "bindingVersion" to bindingVersion,
                "selectionVersion" to version + 1, "selectedAt" to FieldValue.serverTimestamp(),
            ))
        }.await()
        requireUser(uid)
    }

    private fun handles(uid: String): Flow<Piece<List<AccountHandle>>> = callbackFlow {
        requireUser(uid)
        val listener = account(uid).collection("handles").addSnapshotListener(Executor { it.run() }, MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(handleError(error))
            else if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                try {
                    requireUser(uid)
                    trySend(Piece(snapshot.documents.map { it.toHandle(uid) }.sortedBy { it.identity.key }, snapshot.metadata.isFromCache))
                } catch (failure: Exception) { close(handleError(failure)) }
            }
        }
        awaitClose { listener.remove() }
    }

    private fun document(uid: String, name: String): Flow<Piece<DocumentSnapshot>> = callbackFlow {
        requireUser(uid)
        val listener = account(uid).collection("runtime").document(name)
            .addSnapshotListener(Executor { it.run() }, MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) close(handleError(error))
                else if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    try {
                        requireUser(uid)
                        trySend(Piece(snapshot, snapshot.metadata.isFromCache))
                    } catch (failure: Exception) { close(handleError(failure)) }
                }
            }
        awaitClose { listener.remove() }
    }

    private fun account(uid: String) = db.collection("users").document(uid)
    private fun requireUser(uid: String) {
        if (uid.isBlank() || auth.currentUser?.uid != uid) throw HandleException(HandleFailure.INVALID_SESSION)
    }
    private fun requireKey(key: String) { if (!Regex("cf_[a-z0-9_.-]{1,64}").matches(key)) malformed() }
    private data class Piece<T>(val value: T, val cached: Boolean)

    private fun DocumentSnapshot.toHandle(uid: String): AccountHandle {
        if (number("schemaVersion") != 1L) malformed()
        val identity = HandleIdentity.parse(getString("handle") ?: malformed())
        if (id != identity.key || getString("handleKey") != identity.key) malformed()
        val binding = number("bindingVersion")
        val progress = number("progressVersion")
        val rating = number("loremRating")
        val consolidated = get("consolidatedRating")?.let { it as? Long ?: malformed() }
        val created = (get("createdAt") as? Timestamp)?.toDate()?.time ?: malformed()
        if (binding < 1 || progress < 0 || rating !in 0..Int.MAX_VALUE.toLong() || created <= 0 ||
            (consolidated != null && consolidated !in 0..Int.MAX_VALUE.toLong())) malformed()
        return AccountHandle(uid, identity, binding, rating.toInt(), consolidated?.toInt(), progress, created)
    }
    private fun DocumentSnapshot.number(field: String) = get(field) as? Long ?: malformed()
    private fun malformed(): Nothing = throw HandleException(HandleFailure.CONFIGURATION)
}

private suspend fun <T> handleCall(block: suspend () -> T): T = try { block() } catch (error: Exception) {
    if (error is CancellationException) throw error
    throw handleError(error)
}

private fun handleError(error: Exception): HandleException {
    if (error is HandleException) return error
    return HandleException(when (firebaseFailureOf(error)) {
        AuthenticationFailure.NETWORK -> HandleFailure.NETWORK
        AuthenticationFailure.QUOTA -> HandleFailure.QUOTA
        AuthenticationFailure.INVALID_SESSION, AuthenticationFailure.PROVIDER_NOT_ALLOWED -> HandleFailure.INVALID_SESSION
        else -> HandleFailure.CONFIGURATION
    })
}
