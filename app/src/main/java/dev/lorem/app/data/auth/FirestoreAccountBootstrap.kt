package dev.lorem.app.data.auth

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dev.lorem.app.domain.auth.AuthenticationFailure
import kotlinx.coroutines.tasks.await

/** Spark-compatible: the server commits this transaction under the user's Firestore Rules. */
internal class FirestoreAccountBootstrap(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AccountBootstrap {
    override suspend fun ensureAccount(expectedUid: String) = firebaseCall {
        requireCurrentUser(expectedUid)
        val account = firestore.collection("users").document(expectedUid)
        // Transactions require the server: neither cached reads nor queued offline writes authorize entry.
        firestore.runTransaction { transaction ->
            // Firestore can re-run this callback after contention or an account change.
            requireCurrentUser(expectedUid)
            val existing = transaction.get(account)
            if (!existing.exists()) {
                transaction.set(account, mapOf(
                    "schemaVersion" to 1,
                    "createdAt" to FieldValue.serverTimestamp(),
                ))
            } else if (existing.get("schemaVersion") != 1L || existing.get("createdAt") !is Timestamp) {
                throw AuthenticationException(AuthenticationFailure.CONFIGURATION)
            }
            Unit
        }.await()
        requireCurrentUser(expectedUid)
    }

    private fun requireCurrentUser(expectedUid: String) {
        if (expectedUid.isBlank() || auth.currentUser?.uid != expectedUid) {
            throw AuthenticationException(AuthenticationFailure.INVALID_SESSION)
        }
    }
}
