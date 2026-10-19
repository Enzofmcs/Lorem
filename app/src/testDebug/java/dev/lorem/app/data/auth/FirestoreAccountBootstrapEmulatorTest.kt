package dev.lorem.app.data.auth

import android.app.Application
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import dev.lorem.app.domain.auth.AuthenticationFailure
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opt-in SDK integration; never uses the real project in google-services.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FirestoreAccountBootstrapEmulatorTest {
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    @Before fun connectOnlyToDemoEmulators() {
        assumeTrue(System.getenv("LOREM_FIREBASE_EMULATORS") == "true")
        val authHost = loopback("FIREBASE_AUTH_EMULATOR_HOST")
        val firestoreHost = loopback("FIRESTORE_EMULATOR_HOST")
        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FirebaseOptions.Builder()
            .setProjectId("demo-lorem-local")
            .setApplicationId("1:1234567890:android:0123456789abcdef")
            .setApiKey("AIza" + "0".repeat(35))
            .build(), "spark-${UUID.randomUUID()}")
        auth = FirebaseAuth.getInstance(app).apply { useEmulator(authHost.first, authHost.second) }
        db = FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            useEmulator(firestoreHost.first, firestoreHost.second)
        }
    }

    @After fun disconnect() = runBlocking {
        if (::db.isInitialized) db.terminate().await()
        if (::app.isInitialized) app.delete()
    }

    @Test(timeout = 60_000) fun `production Android bootstrap creates concurrently and never rewrites metadata`() = runBlocking {
        val uid = signInGoogle()
        val bootstrap = FirestoreAccountBootstrap(auth, db)
        (1..6).map { async(Dispatchers.IO) { bootstrap.ensureAccount(uid) } }.awaitAll()
        val ref = db.collection("users").document(uid)
        val original = ref.get(Source.SERVER).await().data!!
        assertEquals(setOf("schemaVersion", "createdAt"), original.keys)
        assertEquals(1L, original["schemaVersion"])
        assertTrue(original["createdAt"] is Timestamp)
        bootstrap.ensureAccount(uid)
        assertEquals(original, ref.get(Source.SERVER).await().data)
    }

    @Test(timeout = 60_000) fun `captured uid must match current identity before any document is created`() = runBlocking {
        val uid = signInGoogle()
        try {
            FirestoreAccountBootstrap(auth, db).ensureAccount("another-uid")
            fail("A stale request must not create metadata")
        } catch (error: AuthenticationException) {
            assertEquals(AuthenticationFailure.INVALID_SESSION, error.reason)
        }
        assertEquals(uid, auth.currentUser?.uid)
        assertFalse(db.collection("users").document(uid).get(Source.SERVER).await().exists())
    }

    private suspend fun signInGoogle(): String {
        val sub = "android-${UUID.randomUUID()}"
        fun encode(value: JSONObject) = Base64.encodeToString(value.toString().toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        // Unsigned tokens are accepted ONLY by the local Auth Emulator.
        val token = encode(JSONObject().put("alg", "none").put("typ", "JWT")) + "." +
            encode(JSONObject().put("sub", sub).put("email", "$sub@example.invalid")
                .put("email_verified", true).put("iss", "https://accounts.google.com")
                .put("aud", "emulator-only").put("iat", System.currentTimeMillis() / 1000)
                .put("exp", System.currentTimeMillis() / 1000 + 3600)) + "."
        return auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user!!.uid
    }

    private fun loopback(variable: String): Pair<String, Int> {
        val value = requireNotNull(System.getenv(variable))
        require(Regex("127\\.0\\.0\\.1:[0-9]+").matches(value)) { "Only loopback emulators are allowed" }
        return "127.0.0.1" to value.substringAfter(':').toInt()
    }
}
