package dev.lorem.app.data.handles

import android.app.Application
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.*
import dev.lorem.app.data.auth.FirestoreAccountBootstrap
import dev.lorem.app.data.local.LoremDatabase
import dev.lorem.app.data.local.RoomHandlesCache
import dev.lorem.app.domain.handles.*
import dev.lorem.app.domain.repository.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real production SDK/repository/cache; only the public CF API is replaced with a test fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FirestoreHandlesEmulatorTest {
    private val clients = mutableListOf<Client>()
    private lateinit var cacheDatabase: LoremDatabase
    private lateinit var authHost: String
    private lateinit var firestoreHost: String

    private class Client(val app: FirebaseApp, val auth: FirebaseAuth, val db: FirebaseFirestore) {
        val store = FirestoreHandlesStore(auth, db)
        suspend fun google(subject: String): String {
            fun encode(json: JSONObject) = Base64.encodeToString(json.toString().toByteArray(),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val token = encode(JSONObject().put("alg", "none").put("typ", "JWT")) + "." +
                encode(JSONObject().put("sub", subject).put("email", "$subject@example.invalid")
                    .put("email_verified", true).put("iss", "https://accounts.google.com").put("aud", "emulator-only")
                    .put("iat", System.currentTimeMillis() / 1000).put("exp", System.currentTimeMillis() / 1000 + 3600)) + "."
            val uid = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user!!.uid
            // Deliver Auth/IdToken notifications on Robolectric's paused Android main looper.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            FirestoreAccountBootstrap(auth, db).ensureAccount(uid)
            return uid
        }
    }
    private class Cf(private val rating: Int?) : CodeforcesRepository {
        var calls = 0
        override suspend fun user(handle: String): UserLookupResult {
            calls++
            val canonical = if (handle.equals("tourist", true)) "Tourist" else "Petr"
            return UserLookupResult.Success(CodeforcesUser(canonical, canonical, rating))
        }
        override suspend fun submissionHistory(handle: String): SubmissionHistoryResult = error("Not part of 35")
        override suspend fun problems(): ProblemCatalogResult = error("Not part of 35")
    }

    @Before fun setup() {
        assumeTrue(System.getenv("LOREM_FIREBASE_EMULATORS") == "true")
        authHost = loopback("FIREBASE_AUTH_EMULATOR_HOST")
        firestoreHost = loopback("FIRESTORE_EMULATOR_HOST")
        cacheDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LoremDatabase::class.java).build()
    }
    private fun client(): Client {
        val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FirebaseOptions.Builder()
            .setProjectId("demo-lorem-local").setApplicationId("1:1234567890:android:0123456789abcdef")
            .setApiKey("AIza" + "0".repeat(35)).build(), "handles-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", authHost.substringAfter(':').toInt()) }
        val db = FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            useEmulator("127.0.0.1", firestoreHost.substringAfter(':').toInt())
        }
        return Client(app, auth, db).also(clients::add)
    }
    @After fun close() = runBlocking {
        for (client in clients) { client.db.terminate().await(); client.app.delete() }
        if (::cacheDatabase.isInitialized) cacheDatabase.close()
    }

    @Test(timeout = 60_000) fun `Kotlin SDK concurrent creates persist one binding with stable server time`() = runBlocking {
        val c = client(); val uid = c.google("concurrent-${UUID.randomUUID()}")
        val identity = HandleIdentity.parse("Tourist")
        val results = (1..6).map { async(Dispatchers.IO) { c.store.create(uid, identity) } }.awaitAll()
        assertEquals(setOf(800), results.map { it.loremRating }.toSet())
        assertEquals(1, results.map { it.createdAtEpochMillis }.distinct().size)
        assertEquals(results.first(), c.store.create(uid, HandleIdentity.parse("TOURIST")))
        val collection = c.db.collection("users").document(uid).collection("handles").get(Source.SERVER).await()
        assertEquals(1, collection.size())
        assertEquals(setOf("schemaVersion", "handle", "handleKey", "bindingVersion", "loremRating",
            "consolidatedRating", "progressVersion", "createdAt"), collection.documents.single().data!!.keys)
    }

    @Test(timeout = 60_000) fun `two accounts retain independent progress and fresh installation restores the cloud selection`() = runBlocking {
        val subject = "account-a-${UUID.randomUUID()}"
        val a = client(); val uidA = a.google(subject)
        val b = client(); val uidB = b.google("account-b-${UUID.randomUUID()}")
        val cfA = Cf(3900); val cfB = Cf(null)
        val repoA = AccountHandlesRepository(a.store, cfA, RoomHandlesCache(cacheDatabase.accountHandlesDao()))
        val repoB = AccountHandlesRepository(b.store, cfB, RoomHandlesCache(cacheDatabase.accountHandlesDao()))
        assertEquals(800, repoA.add(uidA, "TOURIST").loremRating)
        assertEquals(800, repoB.add(uidB, "tourist").loremRating)
        adminFields("users/$uidA/handles/cf_tourist", mapOf("loremRating" to 1377, "consolidatedRating" to 1200, "progressVersion" to 9))
        assertEquals(1377, repoA.add(uidA, "tourist").loremRating)
        assertEquals(1, cfA.calls)
        repoA.add(uidA, "Petr"); repoA.select(uidA, "cf_tourist", 1); repoA.select(uidA, "cf_petr", 1)
        val selected = withTimeout(15_000) { repoA.observe(uidA).filter { it.serverConfirmed }.first() }
        assertEquals("petr", selected.selection!!.handle)
        val fresh = client(); assertEquals(uidA, fresh.google(subject))
        val freshCf = Cf(9999)
        val freshDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LoremDatabase::class.java).build()
        try {
            val restored = AccountHandlesRepository(fresh.store, freshCf, RoomHandlesCache(freshDatabase.accountHandlesDao()))
            val snapshot = withTimeout(15_000) { restored.observe(uidA).filter { it.serverConfirmed }.first() }
            assertEquals("petr", snapshot.selection!!.handle)
            assertEquals(1377, snapshot.handles.first { it.identity.key == "cf_tourist" }.loremRating)
            assertEquals(1200, snapshot.handles.first { it.identity.key == "cf_tourist" }.consolidatedRating)
            assertEquals(0, freshCf.calls)
            restored.select(uidA, "cf_tourist", 1)
            val back = withTimeout(15_000) { restored.observe(uidA).filter { it.serverConfirmed }.first() }
            assertEquals(1377, back.selected!!.loremRating)
            assertEquals(800, repoB.add(uidB, "TOURIST").loremRating)
        } finally { freshDatabase.close() }
    }

    @Test(timeout = 60_000) fun `SDK selection respects account lock and stale uid is rejected after switching Google accounts`() = runBlocking {
        val c = client(); val uid = c.google("lock-${UUID.randomUUID()}")
        c.store.create(uid, HandleIdentity.parse("Tourist")); c.store.create(uid, HandleIdentity.parse("Petr"))
        c.store.select(uid, "cf_tourist", 1)
        adminFields("users/$uid/runtime/current", mapOf("handleKey" to "cf_tourist", "bindingVersion" to 1, "ipsumId" to "fixture-active"))
        try { c.store.select(uid, "cf_petr", 1); fail("Active lock must block switching") }
        catch (error: HandleException) { assertEquals(HandleFailure.IPSUM_ACTIVE, error.failure) }
        c.store.select(uid, "cf_tourist", 1) // already selected: no write or version bump
        val stored = c.db.document("users/$uid/runtime/selection").get(Source.SERVER).await()
        assertEquals("cf_tourist", stored.getString("handleKey")); assertEquals(1L, stored.getLong("selectionVersion"))
        val other = c.google("different-${UUID.randomUUID()}"); assertNotEquals(uid, other)
        try { c.store.find(uid, "cf_tourist"); fail("Stale uid must not query the account") }
        catch (error: HandleException) { assertEquals(HandleFailure.INVALID_SESSION, error.failure) }
        assertTrue(c.db.collection("users").document(other).collection("handles").get(Source.SERVER).await().isEmpty)
    }

    /** Fixture seeding only: emulator's owner token, loopback and hardcoded demo project. Never a production Admin credential. */
    private fun adminFields(path: String, fields: Map<String, Any>) {
        val values = JSONObject()
        for ((key, value) in fields) values.put(key, JSONObject().put(if (value is Number) "integerValue" else "stringValue", value.toString()))
        val write = JSONObject().put("update", JSONObject()
            .put("name", "projects/demo-lorem-local/databases/(default)/documents/$path").put("fields", values))
            .put("updateMask", JSONObject().put("fieldPaths", org.json.JSONArray(fields.keys.toList())))
        val connection = URL("http://$firestoreHost/v1/projects/demo-lorem-local/databases/(default)/documents:commit").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(JSONObject().put("writes", org.json.JSONArray().put(write)).toString().toByteArray()) }
            assertEquals(200, connection.responseCode)
        } finally { connection.disconnect() }
    }
    private fun loopback(variable: String): String = requireNotNull(System.getenv(variable)).also {
        require(Regex("127\\.0\\.0\\.1:[0-9]+").matches(it)) { "Only local emulators are allowed" }
    }
}
